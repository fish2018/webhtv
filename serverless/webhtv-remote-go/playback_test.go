package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
)

const (
	testPlaybackToken  = "test-token-a"
	testPlaybackConfig = "config-a"
)

func playbackTestRequest(t *testing.T, service *playbackService, method, path string, body any, token, configKey string, headers map[string]string) (int, map[string]any) {
	t.Helper()
	var data []byte
	if body != nil {
		var err error
		data, err = json.Marshal(body)
		if err != nil {
			t.Fatal(err)
		}
	}
	request := httptest.NewRequest(method, path, bytes.NewReader(data))
	if body != nil {
		request.Header.Set("Content-Type", "application/json")
	}
	request.Header.Set("X-WebHTV-Token", token)
	request.Header.Set("X-WebHTV-Config-Key", configKey)
	for key, value := range headers {
		request.Header.Set(key, value)
	}
	recorder := httptest.NewRecorder()
	err := service.handle(recorder, request, path)
	if err != nil {
		status := http.StatusInternalServerError
		var httpErr *httpError
		if errors.As(err, &httpErr) {
			status = httpErr.Status
		}
		return status, map[string]any{"ok": false, "error": err.Error()}
	}
	result := map[string]any{}
	if err := json.Unmarshal(recorder.Body.Bytes(), &result); err != nil {
		t.Fatalf("decode response: %v; body=%s", err, recorder.Body.String())
	}
	return recorder.Code, result
}

func playbackResultAt(t *testing.T, body map[string]any, index int) map[string]any {
	t.Helper()
	results, ok := body["results"].([]any)
	if !ok || index >= len(results) {
		t.Fatalf("missing result %d: %#v", index, body)
	}
	result, ok := results[index].(map[string]any)
	if !ok {
		t.Fatalf("invalid result %d: %#v", index, results[index])
	}
	return result
}

func playbackChanges(t *testing.T, body map[string]any) []any {
	t.Helper()
	changes, ok := body["changes"].([]any)
	if !ok {
		t.Fatalf("missing changes: %#v", body)
	}
	return changes
}

func testProgress(eventID string, timestamp int64, position int) map[string]any {
	return map[string]any{
		"event": "playback.progress", "eventId": eventID, "timestamp": timestamp,
		"historyKey": "site-a@@@vod-1@@@1", "siteKey": "site-a", "vodId": "vod-1",
		"vodName": "影片 A", "episodeName": "第 1 集", "positionMs": position, "durationMs": 600000,
	}
}

func TestPlaybackProgressDeletionAndRestore(t *testing.T) {
	service := newPlaybackService(":memory:")
	status, body := playbackTestRequest(t, service, http.MethodPost, "/api/playback/sync", testProgress("progress-1", nowMs() + 1000, 120000), testPlaybackToken, testPlaybackConfig, nil)
	if status != http.StatusOK || playbackResultAt(t, body, 0)["action"] != "created" {
		t.Fatalf("create failed: status=%d body=%#v", status, body)
	}

	_, body = playbackTestRequest(t, service, http.MethodPost, "/api/playback/sync", testProgress("progress-1", nowMs() + 1000, 120000), testPlaybackToken, testPlaybackConfig, nil)
	if playbackResultAt(t, body, 0)["action"] != "duplicate" {
		t.Fatalf("expected duplicate: %#v", body)
	}

	_, body = playbackTestRequest(t, service, http.MethodGet, "/api/playback/sync", nil, testPlaybackToken, testPlaybackConfig, map[string]string{"X-WebHTV-Since": "0"})
	if len(playbackChanges(t, body)) != 1 || body["nextSince"] != "1" {
		t.Fatalf("unexpected initial pull: %#v", body)
	}

	deletion := map[string]any{
		"event": "playback.deleted", "eventId": "delete-1", "scope": "item",
		"historyKey": "site-a@@@vod-1@@@1", "siteKey": "site-a", "vodId": "vod-1", "deletedAt": int64(nowMs() + 6000),
	}
	_, body = playbackTestRequest(t, service, http.MethodPost, "/api/playback/sync", deletion, testPlaybackToken, testPlaybackConfig, nil)
	result := playbackResultAt(t, body, 0)
	if result["action"] != "deleted" || result["affected"] != float64(1) {
		t.Fatalf("delete failed: %#v", body)
	}

	_, body = playbackTestRequest(t, service, http.MethodPost, "/api/playback/sync", testProgress("progress-stale", nowMs() + 5000, 180000), testPlaybackToken, testPlaybackConfig, nil)
	if playbackResultAt(t, body, 0)["action"] != "skipped" {
		t.Fatalf("stale progress revived a deletion: %#v", body)
	}

	_, body = playbackTestRequest(t, service, http.MethodPost, "/api/playback/sync", testProgress("progress-fresh", nowMs() + 7000, 240000), testPlaybackToken, testPlaybackConfig, nil)
	if playbackResultAt(t, body, 0)["action"] != "created" {
		t.Fatalf("fresh progress did not restore: %#v", body)
	}

	_, body = playbackTestRequest(t, service, http.MethodGet, "/api/playback/sync", nil, testPlaybackToken, testPlaybackConfig, map[string]string{"X-WebHTV-Since": "0"})
	changes := playbackChanges(t, body)
	if len(changes) != 2 || changes[0].(map[string]any)["action"] != "delete" || changes[1].(map[string]any)["action"] != "upsert" || body["nextSince"] != "3" {
		t.Fatalf("unexpected final pull: %#v", body)
	}

	status, body = playbackTestRequest(t, service, http.MethodGet, "/api/playback/sync?since=bad", nil, testPlaybackToken, testPlaybackConfig, nil)
	if status != http.StatusBadRequest {
		t.Fatalf("invalid cursor must return 400: status=%d body=%#v", status, body)
	}
}

func TestPlaybackExplicitAllAndIsolation(t *testing.T) {
	service := newPlaybackService(":memory:")
	status, body := playbackTestRequest(t, service, http.MethodPost, "/api/playback/sync", map[string]any{
		"event": "playback.deleted", "eventId": "unsafe", "deletedAt": int64(nowMs() + 1000),
	}, testPlaybackToken, testPlaybackConfig, nil)
	if status != http.StatusBadRequest {
		t.Fatalf("implicit all deletion must fail: status=%d body=%#v", status, body)
	}

	status, body = playbackTestRequest(t, service, http.MethodPost, "/api/playback/sync", map[string]any{
		"event": "playback.deleted", "eventId": "all-1", "scope": "all", "deletedAt": int64(nowMs() + 1000),
	}, testPlaybackToken, testPlaybackConfig, nil)
	if status != http.StatusOK || playbackResultAt(t, body, 0)["action"] != "deleted" {
		t.Fatalf("explicit all deletion failed: status=%d body=%#v", status, body)
	}

	_, body = playbackTestRequest(t, service, http.MethodGet, "/api/playback/sync", nil, "other-token", testPlaybackConfig, nil)
	if len(playbackChanges(t, body)) != 0 {
		t.Fatalf("token spaces leaked: %#v", body)
	}
	_, body = playbackTestRequest(t, service, http.MethodGet, "/api/playback/sync", nil, testPlaybackToken, "other-config", nil)
	if len(playbackChanges(t, body)) != 0 {
		t.Fatalf("config spaces leaked: %#v", body)
	}
}

func TestPlaybackBatchValidationIsAtomic(t *testing.T) {
	service := newPlaybackService(":memory:")
	status, body := playbackTestRequest(t, service, http.MethodPost, "/api/playback/sync", map[string]any{
		"changes": []any{
			testProgress("valid-first", nowMs() + 1000, 1000),
			map[string]any{"event": "playback.deleted", "eventId": "invalid-second", "deletedAt": int64(nowMs() + 2000)},
		},
	}, testPlaybackToken, testPlaybackConfig, nil)
	if status != http.StatusBadRequest {
		t.Fatalf("invalid batch must fail: status=%d body=%#v", status, body)
	}
	_, body = playbackTestRequest(t, service, http.MethodGet, "/api/playback/sync", nil, testPlaybackToken, testPlaybackConfig, nil)
	if len(playbackChanges(t, body)) != 0 {
		t.Fatalf("invalid batch was partially applied: %#v", body)
	}
}

func TestPlaybackPersistsAcrossServiceInstances(t *testing.T) {
	path := filepath.Join(t.TempDir(), "playback.json")
	first := newPlaybackService(path)
	status, body := playbackTestRequest(t, first, http.MethodPost, "/api/playback/sync", testProgress("persisted", nowMs() + 1000, 120000), testPlaybackToken, testPlaybackConfig, nil)
	if status != http.StatusOK {
		t.Fatalf("persist write failed: status=%d body=%#v", status, body)
	}
	second := newPlaybackService(path)
	if !second.available() {
		t.Fatal("reloaded playback service is unavailable")
	}
	_, body = playbackTestRequest(t, second, http.MethodGet, "/api/playback/sync", nil, testPlaybackToken, testPlaybackConfig, nil)
	if len(playbackChanges(t, body)) != 1 {
		t.Fatalf("persisted progress was not reloaded: %#v", body)
	}
}
