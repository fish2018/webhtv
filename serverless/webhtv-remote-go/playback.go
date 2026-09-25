package main

import (
	"encoding/json"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
)

const (
	playbackSchema         = "webhtv.playback.v1"
	playbackRetentionMs    = int64(90 * 24 * 60 * 60 * 1000)
	playbackCleanupMs      = int64(24 * 60 * 60 * 1000)
	playbackMaxBodyBytes   = int64(128 * 1024)
	playbackMaxBatchItems  = 100
	playbackDefaultLimit   = 100
	playbackMaxLimit       = 1000
	playbackStorageVersion = 1
)

type playbackService struct {
	mu         sync.Mutex
	path       string
	spaces     map[string]*playbackSpace
	identities map[string]*playbackIdentityRegistry
	loadErr    error
}

type playbackDiskState struct {
	Version    int                                  `json:"version"`
	Spaces     map[string]*playbackSpace            `json:"spaces"`
	Identities map[string]*playbackIdentityRegistry `json:"identities,omitempty"`
}

type playbackSpace struct {
	Sequence    int64                         `json:"sequence"`
	LastCleanup int64                         `json:"lastCleanup"`
	Items       map[string]*playbackItem      `json:"items"`
	Tombstones  map[string]*playbackTombstone `json:"tombstones"`
	Events      map[string]int64              `json:"events"`
}

type playbackItem struct {
	HistoryKey string         `json:"historyKey"`
	SiteKey    string         `json:"siteKey"`
	VodID      string         `json:"vodId"`
	UpdatedAt  int64          `json:"updatedAt"`
	Sequence   int64          `json:"seq"`
	Payload    map[string]any `json:"payload"`
}

type playbackTombstone struct {
	Scope      string         `json:"scope"`
	HistoryKey string         `json:"historyKey"`
	SiteKey    string         `json:"siteKey"`
	VodID      string         `json:"vodId"`
	DeletedAt  int64          `json:"deletedAt"`
	Sequence   int64          `json:"seq"`
	Payload    map[string]any `json:"payload"`
}

type playbackEvent struct {
	Kind       string
	ConfigKey  string
	EventID    string
	HistoryKey string
	SiteKey    string
	VodID      string
	Scope      string
	UpdatedAt  int64
	DeletedAt  int64
	ItemKey    string
	MarkerKey  string
	Payload    map[string]any
}

type playbackChange struct {
	Sequence int64
	Payload  map[string]any
}

var playback = newPlaybackService(playbackDataPath())

func playbackDataPath() string {
	if value := strings.TrimSpace(os.Getenv("WEBHTV_PLAYBACK_DATA")); value != "" {
		return value
	}
	return "webhtv-playback.json"
}

func newPlaybackService(path string) *playbackService {
	service := &playbackService{path: path, spaces: map[string]*playbackSpace{}, identities: map[string]*playbackIdentityRegistry{}}
	if err := service.load(); err != nil {
		service.loadErr = err
		log.Printf("WebHTV playback storage unavailable: %v", err)
	}
	return service
}

func (s *playbackService) available() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.loadErr == nil
}

func (s *playbackService) persistent() bool {
	return s.path != "" && s.path != ":memory:"
}

func isPlaybackSyncPath(path string) bool {
	path = strings.TrimRight(path, "/")
	return path == "/api/playback/sync" || path == "/playback/sync" ||
		path == "/api/playback/sync/status" || path == "/playback/sync/status" ||
		path == "/api/playback/identity/resolve" || path == "/playback/identity/resolve"
}

func (s *playbackService) handle(w http.ResponseWriter, r *http.Request, path string) error {
	if !s.available() {
		return httpErrorf(http.StatusServiceUnavailable, "Playback persistent storage is unavailable")
	}
	token := playbackToken(r)
	if token == "" {
		return httpErrorf(http.StatusUnauthorized, "Missing X-WebHTV-Token")
	}
	if len(token) > 512 {
		return httpErrorf(http.StatusBadRequest, "X-WebHTV-Token is too long")
	}
	if strings.HasSuffix(strings.TrimRight(path, "/"), "/playback/identity/resolve") {
		if r.Method != http.MethodPost {
			return httpErrorf(http.StatusMethodNotAllowed, "Method not allowed")
		}
		return s.identityResolve(w, r, token)
	}
	statusPath := strings.HasSuffix(path, "/status")
	if statusPath && r.Method != http.MethodGet {
		return httpErrorf(http.StatusMethodNotAllowed, "Method not allowed")
	}
	if r.Method == http.MethodPost && !statusPath {
		return s.ingest(w, r, token)
	}
	if r.Method != http.MethodGet {
		return httpErrorf(http.StatusMethodNotAllowed, "Method not allowed")
	}
	configKey, err := requirePlaybackConfigKey(r, nil)
	if err != nil {
		return err
	}
	configType := playbackConfigType(r.Header.Get("x-webhtv-config-type"))
	aliases := playbackRequestAliases(r, nil)
	s.mu.Lock()
	configKey = s.resolvePlaybackConfigKeyLocked(token, configType, configKey, aliases)
	spaceKey := playbackSpaceKeyType(token, configKey, configType)
	defer s.mu.Unlock()
	space := s.spaces[spaceKey]
	if space == nil {
		space = newPlaybackSpace()
	}
	if statusPath {
		return writeJSON(w, http.StatusOK, playbackStatus(space, configKey, r))
	}
	payload, err := pullPlayback(space, r)
	if err != nil {
		return err
	}
	return writeJSON(w, http.StatusOK, payload)
}

func (s *playbackService) ingest(w http.ResponseWriter, r *http.Request, token string) error {
	body, err := readPlaybackBody(r)
	if err != nil {
		return err
	}
	configKey, err := requirePlaybackConfigKey(r, body)
	if err != nil {
		return err
	}
	configType := playbackConfigType(r.Header.Get("x-webhtv-config-type"))
	if object, ok := body.(map[string]any); ok && r.Header.Get("x-webhtv-config-type") == "" {
		configType = playbackConfigType(playbackString(object["configType"]))
	}
	configKey = s.resolvePlaybackConfigKey(token, configType, configKey, playbackRequestAliases(r, body))
	rawEvents := extractPlaybackEvents(body)
	if len(rawEvents) == 0 {
		return httpErrorf(http.StatusBadRequest, "Playback event is empty")
	}
	if len(rawEvents) > playbackMaxBatchItems {
		return httpErrorf(http.StatusRequestEntityTooLarge, "Too many playback events; maximum is %d", playbackMaxBatchItems)
	}
	fallbackEventID := ""
	if len(rawEvents) == 1 {
		fallbackEventID = playbackCleanString(firstNonEmpty(r.Header.Get("x-webhtv-webhook-id"), r.Header.Get("idempotency-key")), 160)
	}
	now := nowMs()
	events := make([]*playbackEvent, 0, len(rawEvents))
	for _, raw := range rawEvents {
		event, err := normalizePlaybackEvent(raw, configKey, now, fallbackEventID)
		if err != nil {
			return err
		}
		events = append(events, event)
	}

	spaceKey := playbackSpaceKeyType(token, configKey, configType)
	s.mu.Lock()
	defer s.mu.Unlock()
	previous := s.spaces[spaceKey]
	working, err := clonePlaybackSpace(previous)
	if err != nil {
		return err
	}
	cleanupPlaybackSpace(working, now)
	results := make([]map[string]any, 0, len(events))
	for _, event := range events {
		if event.Kind == "delete" {
			results = append(results, applyPlaybackDelete(working, event, now))
		} else {
			results = append(results, applyPlaybackUpsert(working, event, now))
		}
	}
	s.spaces[spaceKey] = working
	if err := s.saveLocked(); err != nil {
		if previous == nil {
			delete(s.spaces, spaceKey)
		} else {
			s.spaces[spaceKey] = previous
		}
		return httpErrorf(http.StatusServiceUnavailable, "Playback persistent storage write failed")
	}
	applied, skipped := 0, 0
	for _, result := range results {
		action := playbackString(result["action"])
		if action == "created" || action == "updated" || action == "deleted" {
			applied++
		} else if action == "skipped" || action == "duplicate" {
			skipped++
		}
	}
	return writeJSON(w, http.StatusOK, map[string]any{
		"ok": true, "received": len(results), "applied": applied, "skipped": skipped, "results": results,
	})
}

func applyPlaybackUpsert(space *playbackSpace, event *playbackEvent, receivedAt int64) map[string]any {
	if event.EventID != "" && playbackHasEvent(space, event.EventID) {
		return playbackResult(event, "duplicate", 0, "Event already processed")
	}
	current := space.Items[event.ItemKey]
	var newest *playbackTombstone
	for _, tombstone := range space.Tombstones {
		if !playbackTombstoneMatches(tombstone, event) {
			continue
		}
		if newest == nil || tombstone.DeletedAt > newest.DeletedAt {
			newest = tombstone
		}
	}
	if newest != nil && event.UpdatedAt <= newest.DeletedAt {
		playbackRecordEvent(space, event.EventID, receivedAt)
		return playbackResult(event, "skipped", newest.Sequence, "A newer deletion exists")
	}
	if current != nil && event.UpdatedAt <= current.UpdatedAt {
		playbackRecordEvent(space, event.EventID, receivedAt)
		return playbackResult(event, "skipped", current.Sequence, "A newer progress record exists")
	}
	sequence := playbackNextSequence(space)
	space.Items[event.ItemKey] = &playbackItem{
		HistoryKey: event.HistoryKey, SiteKey: event.SiteKey, VodID: event.VodID,
		UpdatedAt: event.UpdatedAt, Sequence: sequence, Payload: event.Payload,
	}
	playbackRecordEvent(space, event.EventID, receivedAt)
	action := "created"
	if current != nil {
		action = "updated"
	}
	return playbackResult(event, action, sequence, "")
}

func applyPlaybackDelete(space *playbackSpace, event *playbackEvent, receivedAt int64) map[string]any {
	if event.EventID != "" && playbackHasEvent(space, event.EventID) {
		return playbackResult(event, "duplicate", 0, "Event already processed")
	}
	current := space.Tombstones[event.MarkerKey]
	if current != nil && event.DeletedAt <= current.DeletedAt {
		playbackRecordEvent(space, event.EventID, receivedAt)
		return playbackResult(event, "skipped", current.Sequence, "A newer deletion exists")
	}
	sequence := playbackNextSequence(space)
	space.Tombstones[event.MarkerKey] = &playbackTombstone{
		Scope: event.Scope, HistoryKey: event.HistoryKey, SiteKey: event.SiteKey, VodID: event.VodID,
		DeletedAt: event.DeletedAt, Sequence: sequence, Payload: event.Payload,
	}
	affected := 0
	for key, item := range space.Items {
		if item.UpdatedAt > event.DeletedAt || !playbackDeleteMatches(event, item, key) {
			continue
		}
		delete(space.Items, key)
		affected++
	}
	playbackRecordEvent(space, event.EventID, receivedAt)
	result := playbackResult(event, "deleted", sequence, "")
	result["affected"] = affected
	return result
}

func pullPlayback(space *playbackSpace, r *http.Request) (map[string]any, error) {
	since, err := parsePlaybackCursor(firstNonEmpty(r.Header.Get("x-webhtv-since"), r.URL.Query().Get("since")))
	if err != nil {
		return nil, err
	}
	limit := parsePlaybackLimit(firstNonEmpty(r.Header.Get("x-webhtv-limit"), r.URL.Query().Get("limit")))
	cutoff := nowMs() - playbackRetentionMs
	changes := make([]playbackChange, 0, len(space.Items)+len(space.Tombstones))
	for _, item := range space.Items {
		if item.Sequence > since {
			changes = append(changes, playbackChange{Sequence: item.Sequence, Payload: item.Payload})
		}
	}
	for _, item := range space.Tombstones {
		if item.DeletedAt >= cutoff && item.Sequence > since {
			changes = append(changes, playbackChange{Sequence: item.Sequence, Payload: item.Payload})
		}
	}
	sort.Slice(changes, func(i, j int) bool { return changes[i].Sequence < changes[j].Sequence })
	hasMore := len(changes) > limit
	if len(changes) > limit {
		changes = changes[:limit]
	}
	payloads := make([]map[string]any, 0, len(changes))
	nextSince := since
	for _, change := range changes {
		payloads = append(payloads, clonePlaybackMap(change.Payload))
		nextSince = change.Sequence
	}
	return map[string]any{"changes": payloads, "nextSince": strconv.FormatInt(nextSince, 10), "hasMore": hasMore}, nil
}

func playbackStatus(space *playbackSpace, configKey string, r *http.Request) map[string]any {
	cutoff := nowMs() - playbackRetentionMs
	tombstones, latest := 0, int64(0)
	for _, item := range space.Items {
		if item.Sequence > latest {
			latest = item.Sequence
		}
	}
	for _, item := range space.Tombstones {
		if item.DeletedAt < cutoff {
			continue
		}
		tombstones++
		if item.Sequence > latest {
			latest = item.Sequence
		}
	}
	base := "/api/playback/sync"
	if strings.HasPrefix(r.URL.Path, "/playback/") {
		base = "/playback/sync"
	}
	return map[string]any{
		"ok": true, "configKey": configKey, "identityProtocol": playbackIdentitySchema, "addressMatchVersion": 1,
		"capabilities": map[string]any{"playbackSync": true, "identityResolve": true, "identityAliases": true, "legacyUrlHashMigration": true, "identityMerge": true},
		"items":        len(space.Items), "tombstones": tombstones,
		"nextSince": strconv.FormatInt(latest, 10), "retentionDays": 90, "endpoint": serverOrigin(r) + base,
	}
}

func normalizePlaybackEvent(input map[string]any, configKey string, now int64, fallbackEventID string) (*playbackEvent, error) {
	raw := unwrapPlaybackEvent(input)
	bodyConfigKey := normalizePlaybackConfigKey(playbackFirstString(raw, "configKey", "config_key"))
	if len(bodyConfigKey) > 256 {
		return nil, httpErrorf(http.StatusBadRequest, "configKey is too long")
	}
	if bodyConfigKey != "" && bodyConfigKey != configKey {
		return nil, httpErrorf(http.StatusBadRequest, "configKey does not match X-WebHTV-Config-Key")
	}
	eventName := strings.ToLower(playbackCleanString(playbackFirstString(raw, "event"), 80))
	action := strings.ToLower(playbackCleanString(playbackFirstString(raw, "action", "op", "operation"), 32))
	deletion := playbackBool(raw["deleted"]) || eventName == "playback.deleted" || action == "delete" || action == "deleted" || action == "remove" || action == "removed"
	eventID := playbackCleanString(firstNonEmpty(playbackFirstString(raw, "eventId", "event_id"), fallbackEventID), 160)
	historyKey := playbackCleanString(playbackFirstString(raw, "historyKey", "key"), 4096)
	parts := playbackHistoryParts(historyKey)
	siteKey := playbackCleanString(firstNonEmpty(playbackFirstString(raw, "siteKey", "site", "site_key"), parts[0]), 1024)
	vodID := playbackCleanString(firstNonEmpty(playbackFirstString(raw, "vodId", "vod_id", "videoId", "itemId"), parts[1]), 8192)

	if deletion {
		requestedScope := strings.ToLower(playbackCleanString(playbackFirstString(raw, "scope"), 16))
		if requestedScope != "" && requestedScope != "all" && requestedScope != "site" && requestedScope != "item" {
			return nil, httpErrorf(http.StatusBadRequest, "scope must be item, site, or all")
		}
		scope := normalizePlaybackScope(requestedScope, historyKey, siteKey, vodID)
		if scope == "" {
			return nil, httpErrorf(http.StatusBadRequest, "scope=all must be explicit when no item or site identity is provided")
		}
		if scope == "site" && siteKey == "" {
			return nil, httpErrorf(http.StatusBadRequest, "siteKey is required for a site deletion")
		}
		if scope == "item" && historyKey == "" && (siteKey == "" || vodID == "") {
			return nil, httpErrorf(http.StatusBadRequest, "historyKey or siteKey + vodId is required for an item deletion")
		}
		if scope == "all" {
			historyKey, siteKey, vodID = "", "", ""
		} else if scope == "site" {
			historyKey, vodID = "", ""
		}
		deletedAt := playbackTimestamp(playbackFirst(raw, "deletedAt", "deleted_at", "timestamp", "updatedAt"), 0)
		if deletedAt <= 0 {
			return nil, httpErrorf(http.StatusBadRequest, "deletedAt or timestamp is required for a deletion")
		}
		itemKey := playbackItemKey(historyKey, siteKey, vodID)
		markerKey := playbackMarkerKey(scope, itemKey, siteKey)
		payload := playbackCompact(map[string]any{
			"schema": playbackSchema, "action": "delete", "event": "playback.deleted", "eventId": eventID,
			"configKey": configKey, "historyKey": historyKey, "siteKey": siteKey, "vodId": vodID,
			"scope": scope, "deletedAt": deletedAt,
		})
		return &playbackEvent{Kind: "delete", ConfigKey: configKey, EventID: eventID, HistoryKey: historyKey, SiteKey: siteKey, VodID: vodID, Scope: scope, DeletedAt: deletedAt, ItemKey: itemKey, MarkerKey: markerKey, Payload: payload}, nil
	}

	if siteKey == "" {
		return nil, httpErrorf(http.StatusBadRequest, "siteKey is required")
	}
	if vodID == "" {
		return nil, httpErrorf(http.StatusBadRequest, "vodId is required")
	}
	vodName := playbackCleanString(playbackFirstString(raw, "vodName", "vod_name", "name", "title"), 2048)
	episodeName := playbackCleanString(playbackFirstString(raw, "episodeName", "episode", "episodeTitle", "vodRemarks", "remarks"), 2048)
	position := playbackPositiveNumber(playbackFirst(raw, "positionMs", "position", "position_ms", "pos"))
	duration := playbackPositiveNumber(playbackFirst(raw, "durationMs", "duration", "duration_ms"))
	if vodName == "" {
		return nil, httpErrorf(http.StatusBadRequest, "vodName is required")
	}
	if episodeName == "" {
		return nil, httpErrorf(http.StatusBadRequest, "episodeName is required")
	}
	if position <= 0 {
		return nil, httpErrorf(http.StatusBadRequest, "positionMs must be greater than 0")
	}
	if duration <= 0 {
		return nil, httpErrorf(http.StatusBadRequest, "durationMs must be greater than 0")
	}
	updatedAt := playbackTimestamp(playbackFirst(raw, "updatedAt", "updated_at", "timestamp", "updateTime"), now)
	completed := eventName == "playback.ended" || playbackBool(raw["completed"])
	clampedPosition := position
	if clampedPosition > duration {
		clampedPosition = duration
	}
	progress := playbackBoundedNumber(raw["progress"], 0, 1)
	if progress <= 0 {
		progress = clampedPosition / duration
	}
	payload := playbackCompact(map[string]any{
		"schema": playbackSchema, "action": "upsert", "event": eventName, "eventId": eventID,
		"configKey": configKey, "configName": playbackCleanString(playbackFirstString(raw, "configName", "config_name"), 2048),
		"historyKey": historyKey, "siteKey": siteKey, "siteName": playbackCleanString(playbackFirstString(raw, "siteName", "site_name"), 2048),
		"vodId": vodID, "vodName": vodName, "vodPic": playbackCleanString(playbackFirstString(raw, "vodPic", "vod_pic", "pic", "poster"), 8192),
		"flag":        playbackCleanString(playbackFirstString(raw, "flag", "vodFlag", "line", "source"), 2048),
		"episodeName": episodeName, "episodeUrl": playbackCleanString(playbackFirstString(raw, "episodeUrl", "episode_url", "url", "playUrl"), 8192),
		"positionMs": clampedPosition, "durationMs": duration, "progress": progress,
		"speed": playbackPositiveOr(playbackFirst(raw, "speed"), 1), "completed": completed, "updatedAt": updatedAt,
		"clientKey": playbackCleanString(playbackFirstString(raw, "clientKey", "client_key"), 256),
	})
	return &playbackEvent{Kind: "upsert", ConfigKey: configKey, EventID: eventID, HistoryKey: historyKey, SiteKey: siteKey, VodID: vodID, ItemKey: playbackItemKey(historyKey, siteKey, vodID), UpdatedAt: updatedAt, Payload: payload}, nil
}

func readPlaybackBody(r *http.Request) (any, error) {
	if r.Body == nil {
		return nil, httpErrorf(http.StatusBadRequest, "Playback payload is empty")
	}
	defer r.Body.Close()
	data, err := io.ReadAll(io.LimitReader(r.Body, playbackMaxBodyBytes+1))
	if err != nil {
		return nil, err
	}
	if int64(len(data)) > playbackMaxBodyBytes {
		return nil, httpErrorf(http.StatusRequestEntityTooLarge, "Playback payload is too large")
	}
	if len(strings.TrimSpace(string(data))) == 0 {
		return nil, httpErrorf(http.StatusBadRequest, "Playback payload is empty")
	}
	var body any
	decoder := json.NewDecoder(strings.NewReader(string(data)))
	decoder.UseNumber()
	if err := decoder.Decode(&body); err != nil {
		return nil, httpErrorf(http.StatusBadRequest, "Invalid JSON body")
	}
	return body, nil
}

func extractPlaybackEvents(body any) []map[string]any {
	if array, ok := body.([]any); ok {
		return playbackMaps(array, nil)
	}
	object, ok := body.(map[string]any)
	if !ok {
		return nil
	}
	if array := playbackFirstArray(object, "changes", "operations"); array != nil {
		return playbackMaps(array, object)
	}
	result := []map[string]any{}
	if array := playbackFirstArray(object, "deleted", "deletions", "tombstones", "removed", "deletedItems"); array != nil {
		for _, item := range array {
			value := map[string]any{}
			if text, ok := item.(string); ok {
				value["historyKey"] = text
			} else if object, ok := item.(map[string]any); ok {
				value = clonePlaybackMap(object)
			}
			value["action"] = "delete"
			result = append(result, inheritPlaybackFields(value, object))
		}
	}
	if array := playbackFirstArray(object, "items", "records", "upserts", "list"); array != nil {
		result = append(result, playbackMaps(array, object)...)
	}
	if len(result) > 0 {
		return result
	}
	if array, ok := object["data"].([]any); ok {
		return playbackMaps(array, object)
	}
	return []map[string]any{object}
}

func playbackMaps(items []any, parent map[string]any) []map[string]any {
	result := []map[string]any{}
	for _, item := range items {
		if object, ok := item.(map[string]any); ok {
			result = append(result, inheritPlaybackFields(object, parent))
		}
	}
	return result
}

func unwrapPlaybackEvent(input map[string]any) map[string]any {
	if data, ok := input["data"].(map[string]any); ok {
		return inheritPlaybackFields(data, input)
	}
	return input
}

func inheritPlaybackFields(input, parent map[string]any) map[string]any {
	result := clonePlaybackMap(input)
	if parent == nil {
		return result
	}
	for _, key := range []string{"action", "op", "operation", "event", "eventId", "deleted", "scope", "deletedAt", "timestamp", "updatedAt", "configKey", "configName"} {
		if _, exists := result[key]; exists {
			continue
		}
		value, exists := parent[key]
		if !exists {
			continue
		}
		switch value.(type) {
		case map[string]any, []any:
			continue
		}
		result[key] = value
	}
	return result
}

func requirePlaybackConfigKey(r *http.Request, body any) (string, error) {
	header := normalizePlaybackConfigKey(r.Header.Get("x-webhtv-config-key"))
	bodyKey := ""
	if object, ok := body.(map[string]any); ok {
		bodyKey = normalizePlaybackConfigKey(playbackFirstString(object, "configKey", "config_key"))
	}
	if len(header) > 256 || len(bodyKey) > 256 {
		return "", httpErrorf(http.StatusBadRequest, "configKey is too long")
	}
	if header != "" && bodyKey != "" && header != bodyKey {
		return "", httpErrorf(http.StatusBadRequest, "configKey does not match X-WebHTV-Config-Key")
	}
	configKey := header
	if configKey == "" {
		configKey = bodyKey
	}
	if configKey == "" {
		return "", httpErrorf(http.StatusBadRequest, "Missing X-WebHTV-Config-Key")
	}
	return configKey, nil
}

func normalizePlaybackConfigKey(value string) string {
	return strings.ToLower(strings.TrimSpace(value))
}

func normalizePlaybackScope(scope, historyKey, siteKey, vodID string) string {
	if scope == "all" || scope == "site" || scope == "item" {
		return scope
	}
	if historyKey != "" || (siteKey != "" && vodID != "") {
		return "item"
	}
	if siteKey != "" {
		return "site"
	}
	return ""
}

func playbackItemKey(historyKey, siteKey, vodID string) string {
	if siteKey != "" && vodID != "" {
		return sha256Hex("site-vod\x00" + siteKey + "\x00" + vodID)
	}
	return sha256Hex("history\x00" + historyKey)
}

func playbackMarkerKey(scope, itemKey, siteKey string) string {
	switch scope {
	case "all":
		return "all"
	case "site":
		return "site:" + sha256Hex(siteKey)
	default:
		return "item:" + itemKey
	}
}

func playbackSpaceKey(token, configKey string) string {
	return sha256Hex(token) + ":" + sha256Hex(configKey)
}

func playbackTombstoneMatches(tombstone *playbackTombstone, event *playbackEvent) bool {
	if tombstone == nil {
		return false
	}
	if tombstone.Scope == "all" {
		return true
	}
	if tombstone.Scope == "site" {
		return tombstone.SiteKey == event.SiteKey
	}
	return (tombstone.SiteKey != "" && tombstone.VodID != "" && tombstone.SiteKey == event.SiteKey && tombstone.VodID == event.VodID) ||
		(tombstone.HistoryKey != "" && tombstone.HistoryKey == event.HistoryKey)
}

func playbackDeleteMatches(event *playbackEvent, item *playbackItem, itemKey string) bool {
	if event.Scope == "all" {
		return true
	}
	if event.Scope == "site" {
		return item.SiteKey == event.SiteKey
	}
	return itemKey == event.ItemKey || (event.HistoryKey != "" && item.HistoryKey == event.HistoryKey)
}

func playbackResult(event *playbackEvent, action string, sequence int64, message string) map[string]any {
	return playbackCompact(map[string]any{
		"action": action, "sequence": sequence, "message": message, "eventId": event.EventID,
		"configKey": event.ConfigKey, "historyKey": event.HistoryKey, "siteKey": event.SiteKey,
		"vodId": event.VodID, "updatedAt": event.UpdatedAt, "deletedAt": event.DeletedAt,
	})
}

func playbackNextSequence(space *playbackSpace) int64 {
	space.Sequence++
	return space.Sequence
}

func playbackHasEvent(space *playbackSpace, eventID string) bool {
	_, ok := space.Events["event:"+eventID]
	return ok
}

func playbackRecordEvent(space *playbackSpace, eventID string, receivedAt int64) {
	if eventID != "" {
		space.Events["event:"+eventID] = receivedAt
	}
}

func cleanupPlaybackSpace(space *playbackSpace, now int64) {
	if now-space.LastCleanup < playbackCleanupMs {
		return
	}
	cutoff := now - playbackRetentionMs
	for key, item := range space.Tombstones {
		if item.DeletedAt < cutoff {
			delete(space.Tombstones, key)
		}
	}
	for key, receivedAt := range space.Events {
		if receivedAt < cutoff {
			delete(space.Events, key)
		}
	}
	space.LastCleanup = now
}

func newPlaybackSpace() *playbackSpace {
	return &playbackSpace{Items: map[string]*playbackItem{}, Tombstones: map[string]*playbackTombstone{}, Events: map[string]int64{}}
}

func normalizePlaybackSpace(space *playbackSpace) *playbackSpace {
	if space == nil {
		return newPlaybackSpace()
	}
	if space.Items == nil {
		space.Items = map[string]*playbackItem{}
	}
	if space.Tombstones == nil {
		space.Tombstones = map[string]*playbackTombstone{}
	}
	if space.Events == nil {
		space.Events = map[string]int64{}
	}
	return space
}

func clonePlaybackSpace(space *playbackSpace) (*playbackSpace, error) {
	if space == nil {
		return newPlaybackSpace(), nil
	}
	data, err := json.Marshal(space)
	if err != nil {
		return nil, err
	}
	var cloned playbackSpace
	if err := json.Unmarshal(data, &cloned); err != nil {
		return nil, err
	}
	return normalizePlaybackSpace(&cloned), nil
}

func clonePlaybackMap(input map[string]any) map[string]any {
	result := map[string]any{}
	for key, value := range input {
		result[key] = value
	}
	return result
}

func (s *playbackService) load() error {
	if !s.persistent() {
		return nil
	}
	data, err := os.ReadFile(s.path)
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return err
	}
	var disk playbackDiskState
	if err := json.Unmarshal(data, &disk); err != nil {
		return err
	}
	if disk.Version != playbackStorageVersion {
		return httpErrorf(http.StatusServiceUnavailable, "Unsupported playback storage version")
	}
	if disk.Spaces != nil {
		s.spaces = disk.Spaces
	}
	if disk.Identities != nil {
		s.identities = disk.Identities
	}
	if s.identities == nil {
		s.identities = map[string]*playbackIdentityRegistry{}
	}
	for key, space := range s.spaces {
		s.spaces[key] = normalizePlaybackSpace(space)
	}
	return nil
}

func (s *playbackService) saveLocked() error {
	if !s.persistent() {
		return nil
	}
	disk := playbackDiskState{Version: playbackStorageVersion, Spaces: s.spaces, Identities: s.identities}
	data, err := json.Marshal(disk)
	if err != nil {
		return err
	}
	dir := filepath.Dir(s.path)
	if dir != "." {
		if err := os.MkdirAll(dir, 0o700); err != nil {
			return err
		}
	}
	file, err := os.CreateTemp(dir, ".webhtv-playback-*.tmp")
	if err != nil {
		return err
	}
	tempPath := file.Name()
	defer os.Remove(tempPath)
	if err := file.Chmod(0o600); err != nil {
		file.Close()
		return err
	}
	if _, err := file.Write(data); err != nil {
		file.Close()
		return err
	}
	if err := file.Sync(); err != nil {
		file.Close()
		return err
	}
	if err := file.Close(); err != nil {
		return err
	}
	return os.Rename(tempPath, s.path)
}

func parsePlaybackCursor(value string) (int64, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return 0, nil
	}
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil || parsed < 0 {
		return 0, httpErrorf(http.StatusBadRequest, "Invalid X-WebHTV-Since cursor")
	}
	return parsed, nil
}

func parsePlaybackLimit(value string) int {
	value = strings.TrimSpace(value)
	parsed, err := strconv.Atoi(value)
	if value == "" || err != nil || parsed <= 0 {
		return playbackDefaultLimit
	}
	if parsed > playbackMaxLimit {
		return playbackMaxLimit
	}
	return parsed
}

func playbackToken(r *http.Request) string {
	if token := strings.TrimSpace(r.Header.Get("x-webhtv-token")); token != "" {
		return token
	}
	authorization := strings.TrimSpace(r.Header.Get("authorization"))
	if len(authorization) > 7 && strings.EqualFold(authorization[:7], "Bearer ") {
		return strings.TrimSpace(authorization[7:])
	}
	return ""
}

func playbackFirst(object map[string]any, keys ...string) any {
	for _, key := range keys {
		if value, ok := object[key]; ok && value != nil {
			return value
		}
	}
	return nil
}

func playbackFirstString(object map[string]any, keys ...string) string {
	return playbackString(playbackFirst(object, keys...))
}

func playbackFirstArray(object map[string]any, keys ...string) []any {
	for _, key := range keys {
		if value, ok := object[key].([]any); ok {
			return value
		}
	}
	return nil
}

func playbackString(value any) string {
	if value == nil {
		return ""
	}
	if text, ok := value.(string); ok {
		return strings.TrimSpace(text)
	}
	return strings.TrimSpace(str(value))
}

func playbackCleanString(value string, max int) string {
	value = strings.TrimSpace(value)
	if len(value) > max {
		return value[:max]
	}
	return value
}

func playbackHistoryParts(historyKey string) [2]string {
	parts := strings.Split(historyKey, "@@@")
	result := [2]string{}
	if len(parts) > 0 {
		result[0] = parts[0]
	}
	if len(parts) > 1 {
		result[1] = parts[1]
	}
	return result
}

func playbackTimestamp(value any, fallback int64) int64 {
	if number, ok := playbackInt64(value); ok && number > 0 {
		return number
	}
	return fallback
}

func playbackInt64(value any) (int64, bool) {
	switch number := value.(type) {
	case json.Number:
		parsed, err := number.Int64()
		return parsed, err == nil
	case float64:
		parsed := int64(number)
		return parsed, float64(parsed) == number
	case float32:
		parsed := int64(number)
		return parsed, float32(parsed) == number
	case int:
		return int64(number), true
	case int64:
		return number, true
	case string:
		parsed, err := strconv.ParseInt(strings.TrimSpace(number), 10, 64)
		return parsed, err == nil
	default:
		return 0, false
	}
}

func playbackPositiveNumber(value any) float64 {
	number := playbackFloat(value)
	if number > 0 {
		return number
	}
	return 0
}

func playbackPositiveOr(value any, fallback float64) float64 {
	if number := playbackPositiveNumber(value); number > 0 {
		return number
	}
	return fallback
}

func playbackBoundedNumber(value any, min, max float64) float64 {
	number := playbackFloat(value)
	if number < min {
		return min
	}
	if number > max {
		return max
	}
	return number
}

func playbackFloat(value any) float64 {
	switch number := value.(type) {
	case json.Number:
		parsed, _ := number.Float64()
		return parsed
	case float64:
		return number
	case float32:
		return float64(number)
	case int:
		return float64(number)
	case int64:
		return float64(number)
	case string:
		parsed, _ := strconv.ParseFloat(strings.TrimSpace(number), 64)
		return parsed
	default:
		return 0
	}
}

func playbackBool(value any) bool {
	switch item := value.(type) {
	case bool:
		return item
	case float64:
		return item != 0
	case json.Number:
		return item.String() != "0"
	case string:
		item = strings.ToLower(strings.TrimSpace(item))
		return item == "true" || item == "1" || item == "yes"
	default:
		return false
	}
}

func playbackCompact(input map[string]any) map[string]any {
	result := map[string]any{}
	for key, value := range input {
		if value == nil {
			continue
		}
		if text, ok := value.(string); ok && text == "" {
			continue
		}
		if number, ok := value.(int64); ok && number == 0 && (key == "updatedAt" || key == "deletedAt") {
			continue
		}
		result[key] = value
	}
	return result
}

func playbackRequestAliases(r *http.Request, body any) []string {
	result := []string{}
	for _, item := range strings.Split(r.Header.Get("x-webhtv-config-aliases"), ",") {
		if value := strings.TrimSpace(item); value != "" {
			result = append(result, value)
		}
	}
	if object, ok := body.(map[string]any); ok {
		if values, ok := object["configAliases"].([]any); ok {
			for _, item := range values {
				if value := strings.TrimSpace(playbackString(item)); value != "" {
					result = append(result, value)
				}
			}
		}
	}
	return result
}

func (s *playbackService) resolvePlaybackConfigKey(token, configType, configKey string, aliases []string) string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.resolvePlaybackConfigKeyLocked(token, configType, configKey, aliases)
}

func (s *playbackService) resolvePlaybackConfigKeyLocked(token, configType, configKey string, aliases []string) string {
	registry := normalizePlaybackIdentityRegistry(s.identities[playbackIdentityRegistryKey(token, configType)])
	candidates := map[string]bool{}
	for _, key := range append([]string{configKey}, aliases...) {
		if identity := registry.Aliases[key]; identity != nil {
			candidates[identity.CanonicalInterfaceKey] = true
		}
		if registry.Identities[key] != nil {
			candidates[key] = true
		}
	}
	if len(candidates) != 1 {
		return configKey
	}
	for key := range candidates {
		return key
	}
	return configKey
}

func playbackSpaceKeyType(token, configKey, configType string) string {
	base := playbackSpaceKey(token, configKey)
	if playbackConfigType(configType) == "vod" {
		return base
	}
	return base + ":" + playbackConfigType(configType)
}
