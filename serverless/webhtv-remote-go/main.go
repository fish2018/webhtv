package main

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"mime"
	"net/http"
	"os"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

const (
	serverMode       = "go"
	bindTTL          = 10 * time.Minute
	commandTTL       = time.Hour
	syncTTL          = 2 * time.Hour
	maxSyncPartBytes = 25 * 1024 * 1024
)

var validParts = map[string]bool{
	"backup":          true,
	"remoteRelay":     true,
	"syncFiles":       true,
	"loginStateFiles": true,
	"manifest":        true,
}

type relayState struct {
	mu           sync.Mutex
	devices      map[string]*device
	bindCodes    map[string]*bindCode
	groupDevices map[string]map[string]bool
	commands     map[string]*command
	queues       map[string][]string
	syncs        map[string]*syncTask
	parts        map[string]*syncPart
	sockets      map[string]map[*wsClient]bool
	lastCleanup  time.Time
}

type device struct {
	DeviceID     string         `json:"deviceId"`
	GroupID      string         `json:"groupId,omitempty"`
	GroupIDs     []string       `json:"groupIds,omitempty"`
	Name         string         `json:"name"`
	Alias        string         `json:"alias,omitempty"`
	Role         string         `json:"role,omitempty"`
	Type         any            `json:"type,omitempty"`
	AppVersion   string         `json:"appVersion,omitempty"`
	Capabilities map[string]any `json:"capabilities,omitempty"`
	LastSeen     int64          `json:"lastSeen"`
	CreatedAt    int64          `json:"createdAt"`
	UpdatedAt    int64          `json:"updatedAt"`
}

type publicDevice struct {
	DeviceID     string         `json:"deviceId"`
	Name         string         `json:"name"`
	RawName      string         `json:"rawName,omitempty"`
	Role         string         `json:"role,omitempty"`
	Type         any            `json:"type,omitempty"`
	AppVersion   string         `json:"appVersion,omitempty"`
	LastSeen     int64          `json:"lastSeen"`
	Online       bool           `json:"online"`
	Capabilities map[string]any `json:"capabilities,omitempty"`
}

type groupInfo struct {
	GroupID        string `json:"groupId"`
	GroupToken     string `json:"groupToken"`
	FamilyToken    string `json:"familyToken"`
	GroupTokenHash string `json:"groupTokenHash"`
}

type bindCode struct {
	Code           string
	DeviceID       string
	GrantID        string
	BindGrantToken string
	ExpiresAt      time.Time
}

type command struct {
	ID             string         `json:"id"`
	GroupID        string         `json:"groupId"`
	GroupTokenHash string         `json:"groupTokenHash"`
	TargetDeviceID string         `json:"targetDeviceId"`
	Type           string         `json:"type"`
	Payload        map[string]any `json:"payload"`
	Status         string         `json:"status"`
	Result         map[string]any `json:"result,omitempty"`
	CreatedAt      int64          `json:"createdAt"`
	DeliveredAt    int64          `json:"deliveredAt,omitempty"`
	FinishedAt     int64          `json:"finishedAt,omitempty"`
}

type syncTask struct {
	ID               string                    `json:"id"`
	GroupID          string                    `json:"groupId"`
	GroupTokenHash   string                    `json:"groupTokenHash"`
	SourceDeviceID   string                    `json:"sourceDeviceId"`
	TargetDeviceID   string                    `json:"targetDeviceId"`
	Options          map[string]any            `json:"options"`
	Status           string                    `json:"status"`
	Parts            map[string]map[string]any `json:"parts"`
	ExportCommandID  string                    `json:"exportCommandId,omitempty"`
	RestoreCommandID string                    `json:"restoreCommandId,omitempty"`
	ExportResult     map[string]any            `json:"exportResult,omitempty"`
	RestoreResult    map[string]any            `json:"restoreResult,omitempty"`
	CreatedAt        int64                     `json:"createdAt"`
	UpdatedAt        int64                     `json:"updatedAt"`
}

type syncPart struct {
	Bytes       []byte
	ContentType string
	Size        int64
	UploadedAt  int64
}

type wsClient struct {
	state    *relayState
	conn     *websocket.Conn
	deviceID string
	send     chan any
	done     chan struct{}
	once     sync.Once
}

var (
	state = &relayState{
		devices:      map[string]*device{},
		bindCodes:    map[string]*bindCode{},
		groupDevices: map[string]map[string]bool{},
		commands:     map[string]*command{},
		queues:       map[string][]string{},
		syncs:        map[string]*syncTask{},
		parts:        map[string]*syncPart{},
		sockets:      map[string]map[*wsClient]bool{},
	}
	cleanIDRe = regexp.MustCompile(`[^A-Za-z0-9_.:-]`)
	upgrader  = websocket.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}
)

func main() {
	addr := os.Getenv("WEBHTV_REMOTE_ADDR")
	if addr == "" {
		addr = ":8787"
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/", handle)
	log.Printf("WebHTV Go remote relay listening on %s", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatal(err)
	}
}

func handle(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodOptions {
		writeCORS(w)
		w.WriteHeader(http.StatusNoContent)
		return
	}
	if r.URL.Path == "/api/device/ws" {
		handleWebSocket(w, r)
		return
	}
	writeCORS(w)
	state.cleanup()
	if err := route(w, r); err != nil {
		status := http.StatusInternalServerError
		var httpErr *httpError
		if errors.As(err, &httpErr) {
			status = httpErr.Status
		}
		writeJSON(w, status, map[string]any{"ok": false, "error": err.Error()})
	}
}

func route(w http.ResponseWriter, r *http.Request) error {
	path := strings.TrimRight(r.URL.Path, "/")
	if path == "" {
		path = "/"
	}
	method := strings.ToUpper(r.Method)
	if isPlaybackSyncPath(path) {
		return playback.handle(w, r, path)
	}

	if method == http.MethodGet && path == "/api/health" {
		return writeJSON(w, http.StatusOK, map[string]any{"ok": true, "time": nowMs()})
	}
	if method == http.MethodGet && path == "/api/server/capabilities" {
		return writeJSON(w, http.StatusOK, capabilities())
	}
	if method == http.MethodPost && path == "/api/device/register" {
		return registerDevice(w, r)
	}
	if method == http.MethodPost && path == "/api/device/bind-code" {
		return createBindCode(w, r)
	}
	if method == http.MethodPost && path == "/api/device/poll" {
		return pollDevice(w, r)
	}
	if method == http.MethodPost && (path == "/api/groups/claim" || path == "/api/family/claim") {
		return claimDevice(w, r)
	}
	if method == http.MethodGet && path == "/api/devices" {
		return listDevices(w, r)
	}
	if method == http.MethodPost && path == "/api/commands" {
		return createCommand(w, r)
	}
	if method == http.MethodGet && strings.HasPrefix(path, "/api/commands/") {
		return getCommand(w, r, strings.TrimPrefix(path, "/api/commands/"))
	}
	if method == http.MethodPost && strings.HasPrefix(path, "/api/commands/") && strings.HasSuffix(path, "/result") {
		id := strings.TrimSuffix(strings.TrimPrefix(path, "/api/commands/"), "/result")
		return commandResult(w, r, id)
	}
	if method == http.MethodPost && path == "/api/sync/create" {
		return createSync(w, r)
	}
	if method == http.MethodGet && strings.HasPrefix(path, "/api/sync/") && strings.Count(strings.TrimPrefix(path, "/api/sync/"), "/") == 0 {
		return getSync(w, r, strings.TrimPrefix(path, "/api/sync/"))
	}
	if strings.HasPrefix(path, "/api/sync/") {
		return routeSync(w, r, path, method)
	}
	return httpErrorf(http.StatusNotFound, "Not found")
}

func routeSync(w http.ResponseWriter, r *http.Request, path, method string) error {
	rest := strings.TrimPrefix(path, "/api/sync/")
	parts := strings.Split(rest, "/")
	if len(parts) == 3 && parts[1] == "part" {
		if method == http.MethodPost {
			return uploadSyncPart(w, r, parts[0], parts[2])
		}
		if method == http.MethodGet {
			return downloadSyncPart(w, r, parts[0], parts[2])
		}
	}
	if len(parts) == 2 && parts[1] == "export-complete" && method == http.MethodPost {
		return exportComplete(w, r, parts[0])
	}
	if len(parts) == 2 && parts[1] == "restore-complete" && method == http.MethodPost {
		return restoreComplete(w, r, parts[0])
	}
	return httpErrorf(http.StatusNotFound, "Not found")
}

func capabilities() map[string]any {
	return map[string]any{
		"ok":               true,
		"serverMode":       serverMode,
		"serverName":       "WebHTV Go Remote Relay",
		"relayMode":        "go-memory-websocket",
		"time":             nowMs(),
		"maxSyncPartBytes": maxSyncPartBytes,
		"capabilities": map[string]any{
			"configManage":              true,
			"remoteSync":                true,
			"pushAction":                true,
			"recentLog":                 true,
			"deviceBackup":              false,
			"fileManage":                false,
			"webHomeManage":             false,
			"shellProxyManage":          false,
			"siteInjectManage":          false,
			"webHomeExtensionManage":    false,
			"playbackSync":              playback.available(),
			"playbackPersistentStorage": playback.available() && playback.persistent(),
			"multiDeviceBatch":          false,
			"webSocket":                 true,
			"persistentStorage":         false,
			"externalObjectStorage":     false,
			"deviceRevoke":              false,
		},
	}
}

func registerDevice(w http.ResponseWriter, r *http.Request) error {
	body, err := readJSON(r)
	if err != nil {
		return err
	}
	device, token, err := state.registerDevice(r, body)
	if err != nil {
		return err
	}
	return writeJSON(w, http.StatusOK, map[string]any{
		"ok":           true,
		"deviceId":     device.DeviceID,
		"deviceToken":  token,
		"deviceSecret": token,
		"groupIds":     device.GroupIDs,
		"server":       capabilities(),
	})
}

func createBindCode(w http.ResponseWriter, r *http.Request) error {
	body, err := readJSON(r)
	if err != nil {
		return err
	}
	device, err := state.requireDevice(r, body)
	if err != nil {
		return err
	}
	origin := serverOrigin(r)
	bindGrantToken := strings.TrimSpace(str(body["bindGrantToken"]))
	if bindGrantToken == "" {
		return httpErrorf(http.StatusBadRequest, "Missing bindGrantToken")
	}
	grantID, err := requireDerivedID("bnd", origin, bindGrantToken, str(body["grantId"]), "Invalid bind grant token")
	if err != nil {
		return err
	}
	code := ""
	state.mu.Lock()
	for i := 0; i < 8; i++ {
		code = fmt.Sprintf("%06d", 100000+mustRandomInt(900000))
		if _, ok := state.bindCodes[code]; !ok {
			break
		}
	}
	state.bindCodes[code] = &bindCode{Code: code, DeviceID: device.DeviceID, GrantID: grantID, BindGrantToken: bindGrantToken, ExpiresAt: time.Now().Add(bindTTL)}
	state.mu.Unlock()
	return writeJSON(w, http.StatusOK, map[string]any{"ok": true, "code": code, "grantId": grantID, "expiresIn": int(bindTTL.Seconds()), "server": capabilities()})
}

func claimDevice(w http.ResponseWriter, r *http.Request) error {
	body, err := readJSON(r)
	if err != nil {
		return err
	}
	requester, err := state.requireDevice(r, body)
	if err != nil {
		return err
	}
	code := strings.TrimSpace(str(body["code"]))
	if code == "" {
		return httpErrorf(http.StatusNotFound, "Bind code expired")
	}
	state.mu.Lock()
	bind := state.bindCodes[code]
	if bind == nil || time.Now().After(bind.ExpiresAt) {
		state.mu.Unlock()
		return httpErrorf(http.StatusNotFound, "Bind code expired")
	}
	if requester.DeviceID == bind.DeviceID {
		state.mu.Unlock()
		return httpErrorf(http.StatusBadRequest, "Cannot bind local device")
	}
	device := state.devices[bind.DeviceID]
	if device == nil {
		state.mu.Unlock()
		return httpErrorf(http.StatusNotFound, "Device not found")
	}
	state.mu.Unlock()

	token := readGroupToken(r, body)
	if token == "" {
		token = randomCapability("gtk")
	}
	group, err := groupFromToken(r, token)
	if err != nil {
		return err
	}
	alias := str(body["alias"])

	state.mu.Lock()
	addGroupToDevice(device, group.GroupID)
	device.Alias = firstNonEmpty(alias, device.Alias, device.Name)
	device.UpdatedAt = nowMs()
	state.devices[device.DeviceID] = device
	state.linkDeviceLocked(group.GroupID, device.DeviceID)
	delete(state.bindCodes, code)
	state.mu.Unlock()

	cmd := state.enqueueCommand(group.GroupID, device.DeviceID, "remote.profile.addGroup", map[string]any{
		"groupId":        group.GroupID,
		"groupToken":     group.GroupToken,
		"groupTokenHash": group.GroupTokenHash,
		"grantId":        bind.GrantID,
		"bindGrantToken": bind.BindGrantToken,
		"alias":          alias,
	}, group.GroupTokenHash)
	return writeJSON(w, http.StatusOK, map[string]any{
		"ok":             true,
		"deviceId":       device.DeviceID,
		"groupId":        group.GroupID,
		"groupToken":     group.GroupToken,
		"familyToken":    group.GroupToken,
		"groupTokenHash": group.GroupTokenHash,
		"grantId":        bind.GrantID,
		"bindGrantToken": bind.BindGrantToken,
		"commandId":      cmd.ID,
		"device":         state.publicDevice(device),
		"server":         capabilities(),
	})
}

func listDevices(w http.ResponseWriter, r *http.Request) error {
	group, err := requireGroup(r, nil)
	if err != nil {
		return err
	}
	state.mu.Lock()
	ids := state.groupDevices[group.GroupID]
	devices := make([]publicDevice, 0, len(ids))
	for id := range ids {
		if device := state.devices[id]; device != nil {
			devices = append(devices, state.publicDeviceLocked(device))
		}
	}
	state.mu.Unlock()
	sort.Slice(devices, func(i, j int) bool { return devices[i].Name < devices[j].Name })
	return writeJSON(w, http.StatusOK, map[string]any{"ok": true, "devices": devices, "server": capabilities()})
}

func pollDevice(w http.ResponseWriter, r *http.Request) error {
	body, err := readJSON(r)
	if err != nil {
		return err
	}
	device, err := state.requireDevice(r, body)
	if err != nil {
		return err
	}
	if err := state.updateDeviceGroups(r, device, body); err != nil {
		return err
	}
	cmd := state.nextQueuedCommand(device.DeviceID)
	return writeJSON(w, http.StatusOK, map[string]any{"ok": true, "command": cmd, "server": capabilities()})
}

func createCommand(w http.ResponseWriter, r *http.Request) error {
	body, err := readJSON(r)
	if err != nil {
		return err
	}
	group, err := requireGroup(r, body)
	if err != nil {
		return err
	}
	targetDeviceID := cleanID(str(body["targetDeviceId"]))
	commandType := str(body["type"])
	payload := obj(body["payload"])
	if commandType == "remote.profile.addGroup" {
		if str(payload["bindGrantToken"]) == "" || str(payload["groupToken"]) == "" {
			return httpErrorf(http.StatusBadRequest, "Missing bootstrap payload")
		}
		bootstrap, err := groupFromToken(r, str(payload["groupToken"]))
		if err != nil {
			return err
		}
		if bootstrap.GroupID != group.GroupID {
			return httpErrorf(http.StatusBadRequest, "Bootstrap group token mismatch")
		}
		payload["groupId"] = bootstrap.GroupID
		payload["groupTokenHash"] = bootstrap.GroupTokenHash
	} else {
		if err := state.requireTargetIfKnown(group.GroupID, targetDeviceID); err != nil {
			return err
		}
		payload["groupId"] = group.GroupID
		payload["groupTokenHash"] = group.GroupTokenHash
	}
	cmd := state.enqueueCommand(group.GroupID, targetDeviceID, commandType, payload, group.GroupTokenHash)
	return writeJSON(w, http.StatusOK, map[string]any{"ok": true, "commandId": cmd.ID, "command": cmd})
}

func getCommand(w http.ResponseWriter, r *http.Request, commandID string) error {
	group, err := requireGroup(r, nil)
	if err != nil {
		return err
	}
	commandID = cleanID(commandID)
	state.mu.Lock()
	cmd := state.commands[commandID]
	state.mu.Unlock()
	if cmd == nil || cmd.GroupID != group.GroupID {
		return httpErrorf(http.StatusNotFound, "Command not found")
	}
	return writeJSON(w, http.StatusOK, map[string]any{"ok": true, "command": cmd, "server": capabilities()})
}

func commandResult(w http.ResponseWriter, r *http.Request, commandID string) error {
	body, err := readJSON(r)
	if err != nil {
		return err
	}
	device, err := state.requireDevice(r, body)
	if err != nil {
		return err
	}
	commandID = cleanID(commandID)
	state.mu.Lock()
	cmd := state.commands[commandID]
	if cmd == nil {
		state.mu.Unlock()
		return httpErrorf(http.StatusNotFound, "Command not found")
	}
	if cmd.TargetDeviceID != device.DeviceID {
		state.mu.Unlock()
		return httpErrorf(http.StatusForbidden, "Command target mismatch")
	}
	if boolVal(body["ok"], true) {
		cmd.Status = "done"
	} else {
		cmd.Status = "failed"
	}
	cmd.Result = body
	cmd.FinishedAt = nowMs()
	state.commands[cmd.ID] = cmd
	state.mu.Unlock()
	return writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func createSync(w http.ResponseWriter, r *http.Request) error {
	body, err := readJSON(r)
	if err != nil {
		return err
	}
	group, err := requireGroup(r, body)
	if err != nil {
		return err
	}
	source := cleanID(str(body["sourceDeviceId"]))
	target := cleanID(str(body["targetDeviceId"]))
	if err := state.requireTargetIfKnown(group.GroupID, source); err != nil {
		return err
	}
	if err := state.requireTargetIfKnown(group.GroupID, target); err != nil {
		return err
	}
	if source == target {
		return httpErrorf(http.StatusBadRequest, "Source and target device must be different")
	}
	syncID := "sync_" + randomID(20)
	origin := serverOrigin(r)
	sync := &syncTask{
		ID:             syncID,
		GroupID:        group.GroupID,
		GroupTokenHash: group.GroupTokenHash,
		SourceDeviceID: source,
		TargetDeviceID: target,
		Options:        normalizeSyncOptions(obj(body["options"])),
		Status:         "created",
		Parts:          map[string]map[string]any{},
		CreatedAt:      nowMs(),
		UpdatedAt:      nowMs(),
	}
	state.mu.Lock()
	state.syncs[syncID] = sync
	state.mu.Unlock()
	cmd := state.enqueueCommand(group.GroupID, source, "remoteSync.export", map[string]any{
		"syncId":         syncID,
		"targetDeviceId": target,
		"options":        sync.Options,
		"uploadBase":     origin + "/api/sync/" + syncID + "/part",
		"completeUrl":    origin + "/api/sync/" + syncID + "/export-complete",
		"groupId":        group.GroupID,
		"groupTokenHash": group.GroupTokenHash,
	}, group.GroupTokenHash)
	state.mu.Lock()
	sync.ExportCommandID = cmd.ID
	state.mu.Unlock()
	return writeJSON(w, http.StatusOK, map[string]any{"ok": true, "sync": sync, "exportCommandId": cmd.ID, "server": capabilities()})
}

func getSync(w http.ResponseWriter, r *http.Request, syncID string) error {
	group, err := requireGroup(r, nil)
	if err != nil {
		return err
	}
	state.mu.Lock()
	sync := state.syncs[cleanID(syncID)]
	state.mu.Unlock()
	if sync == nil || sync.GroupID != group.GroupID {
		return httpErrorf(http.StatusNotFound, "Sync not found")
	}
	return writeJSON(w, http.StatusOK, map[string]any{"ok": true, "sync": sync})
}

func uploadSyncPart(w http.ResponseWriter, r *http.Request, syncID, part string) error {
	part, err := normalizePart(part)
	if err != nil {
		return err
	}
	device, err := state.requireDevice(r, nil)
	if err != nil {
		return err
	}
	sync, err := state.getSyncForDevice(cleanID(syncID), device.DeviceID)
	if err != nil {
		return err
	}
	if sync.SourceDeviceID != device.DeviceID {
		return httpErrorf(http.StatusForbidden, "Only source device can upload sync parts")
	}
	if r.ContentLength > maxSyncPartBytes {
		return httpErrorf(http.StatusRequestEntityTooLarge, "Sync part is too large for online relay")
	}
	limited := io.LimitReader(r.Body, maxSyncPartBytes+1)
	bytes, err := io.ReadAll(limited)
	if err != nil {
		return err
	}
	if len(bytes) > maxSyncPartBytes {
		return httpErrorf(http.StatusRequestEntityTooLarge, "Sync part is too large for online relay")
	}
	contentType := r.Header.Get("content-type")
	if contentType == "" {
		contentType = contentTypeForPart(part)
	}
	key := "sync:" + sync.ID + ":" + part
	now := nowMs()
	state.mu.Lock()
	state.parts[key] = &syncPart{Bytes: bytes, ContentType: contentType, Size: int64(len(bytes)), UploadedAt: now}
	sync.Parts[part] = map[string]any{"key": key, "size": len(bytes), "contentType": contentType, "uploadedAt": now}
	sync.Status = "exporting"
	sync.UpdatedAt = now
	state.syncs[sync.ID] = sync
	state.mu.Unlock()
	return writeJSON(w, http.StatusOK, map[string]any{"ok": true, "part": part, "size": len(bytes)})
}

func downloadSyncPart(w http.ResponseWriter, r *http.Request, syncID, part string) error {
	part, err := normalizePart(part)
	if err != nil {
		return err
	}
	device, err := state.requireDevice(r, nil)
	if err != nil {
		return err
	}
	sync, err := state.getSyncForDevice(cleanID(syncID), device.DeviceID)
	if err != nil {
		return err
	}
	state.mu.Lock()
	info := sync.Parts[part]
	var stored *syncPart
	if info != nil {
		stored = state.parts[str(info["key"])]
	}
	state.mu.Unlock()
	if stored == nil {
		return httpErrorf(http.StatusNotFound, "Sync part not found")
	}
	w.Header().Set("content-type", firstNonEmpty(stored.ContentType, contentTypeForPart(part)))
	w.Header().Set("content-length", fmt.Sprintf("%d", stored.Size))
	w.Header().Set("cache-control", "no-store")
	_, err = w.Write(stored.Bytes)
	return err
}

func exportComplete(w http.ResponseWriter, r *http.Request, syncID string) error {
	body, err := readJSON(r)
	if err != nil {
		return err
	}
	device, err := state.requireDevice(r, body)
	if err != nil {
		return err
	}
	sync, err := state.getSyncForDevice(cleanID(syncID), device.DeviceID)
	if err != nil {
		return err
	}
	if sync.SourceDeviceID != device.DeviceID {
		return httpErrorf(http.StatusForbidden, "Only source device can finish export")
	}
	origin := serverOrigin(r)
	downloads := map[string]any{}
	state.mu.Lock()
	sync.Status = "exported"
	sync.ExportResult = body
	sync.UpdatedAt = nowMs()
	for part := range sync.Parts {
		downloads[part] = origin + "/api/sync/" + sync.ID + "/part/" + part
	}
	state.syncs[sync.ID] = sync
	state.mu.Unlock()
	cmd := state.enqueueCommand(sync.GroupID, sync.TargetDeviceID, "remoteSync.restore", map[string]any{
		"syncId":         sync.ID,
		"sourceDeviceId": sync.SourceDeviceID,
		"options":        sync.Options,
		"parts":          sync.Parts,
		"downloads":      downloads,
		"completeUrl":    origin + "/api/sync/" + sync.ID + "/restore-complete",
		"groupId":        sync.GroupID,
		"groupTokenHash": sync.GroupTokenHash,
	}, sync.GroupTokenHash)
	state.mu.Lock()
	sync.RestoreCommandID = cmd.ID
	state.syncs[sync.ID] = sync
	state.mu.Unlock()
	return writeJSON(w, http.StatusOK, map[string]any{"ok": true, "restoreCommandId": cmd.ID, "server": capabilities()})
}

func restoreComplete(w http.ResponseWriter, r *http.Request, syncID string) error {
	body, err := readJSON(r)
	if err != nil {
		return err
	}
	device, err := state.requireDevice(r, body)
	if err != nil {
		return err
	}
	sync, err := state.getSyncForDevice(cleanID(syncID), device.DeviceID)
	if err != nil {
		return err
	}
	if sync.TargetDeviceID != device.DeviceID {
		return httpErrorf(http.StatusForbidden, "Only target device can finish restore")
	}
	state.mu.Lock()
	if boolVal(body["ok"], true) {
		sync.Status = "done"
	} else {
		sync.Status = "restore_failed"
	}
	sync.RestoreResult = body
	sync.UpdatedAt = nowMs()
	state.syncs[sync.ID] = sync
	if boolVal(body["ok"], true) {
		for _, info := range sync.Parts {
			delete(state.parts, str(info["key"]))
		}
	}
	state.mu.Unlock()
	return writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func handleWebSocket(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	conn.SetReadLimit(64 * 1024)
	if err := conn.SetReadDeadline(time.Now().Add(15 * time.Second)); err != nil {
		_ = conn.Close()
		return
	}
	var hello map[string]any
	if err := conn.ReadJSON(&hello); err != nil {
		_ = conn.Close()
		return
	}
	if str(hello["messageType"]) == "hello" || str(hello["type"]) == "hello" {
		delete(hello, "type")
		delete(hello, "messageType")
	}
	device, token, err := state.registerDevice(r, hello)
	if err != nil {
		_ = conn.WriteJSON(map[string]any{"type": "error", "ok": false, "error": err.Error(), "server": capabilities()})
		_ = conn.Close()
		return
	}
	_ = conn.SetReadDeadline(time.Time{})
	client := &wsClient{state: state, conn: conn, deviceID: device.DeviceID, send: make(chan any, 16), done: make(chan struct{})}
	state.registerSocket(client)
	client.sendJSON(map[string]any{"type": "ready", "ok": true, "deviceId": device.DeviceID, "deviceToken": token, "deviceSecret": token, "groupIds": device.GroupIDs, "server": capabilities()})
	go client.writeLoop()
	go client.readLoop(r)
	state.deliverPending(device.DeviceID)
}

func (s *relayState) registerDevice(r *http.Request, body map[string]any) (*device, string, error) {
	origin := serverOrigin(r)
	token := readDeviceToken(r, body)
	if token == "" {
		token = randomCapability("dtk")
	}
	deviceID, err := requireDerivedID("dev", origin, token, str(body["deviceId"]), "Invalid device token")
	if err != nil {
		return nil, "", err
	}
	now := nowMs()
	s.mu.Lock()
	existing := s.devices[deviceID]
	groupIDs := map[string]bool{}
	for _, id := range deviceGroupIDs(existing) {
		groupIDs[id] = true
	}
	s.mu.Unlock()
	for _, token := range readGroupTokens(nil, body) {
		group, err := groupFromToken(r, token)
		if err != nil {
			return nil, "", err
		}
		groupIDs[group.GroupID] = true
	}
	ids := keys(groupIDs)
	dev := &device{
		DeviceID:     deviceID,
		GroupIDs:     ids,
		Name:         firstNonEmpty(str(body["name"]), field(existing, "name"), "WebHTV"),
		Alias:        firstNonEmpty(str(body["alias"]), field(existing, "alias")),
		Role:         firstNonEmpty(str(body["role"]), field(existing, "role"), "app"),
		Type:         firstNonNil(body["type"], existingType(existing), 0),
		AppVersion:   firstNonEmpty(str(body["appVersion"]), field(existing, "appVersion")),
		Capabilities: firstCaps(obj(body["capabilities"]), existing),
		LastSeen:     now,
		CreatedAt:    now,
		UpdatedAt:    now,
	}
	if existing != nil && existing.CreatedAt > 0 {
		dev.CreatedAt = existing.CreatedAt
	}
	if len(ids) > 0 {
		dev.GroupID = ids[0]
	}
	s.mu.Lock()
	s.devices[deviceID] = dev
	for _, id := range ids {
		s.linkDeviceLocked(id, deviceID)
	}
	s.mu.Unlock()
	return dev, token, nil
}

func (s *relayState) requireDevice(r *http.Request, body map[string]any) (*device, error) {
	if body == nil {
		body = map[string]any{}
	}
	origin := serverOrigin(r)
	token := readDeviceToken(r, body)
	if token == "" {
		return nil, httpErrorf(http.StatusUnauthorized, "Missing device credentials")
	}
	deviceID, err := requireDerivedID("dev", origin, token, firstNonEmpty(str(body["deviceId"]), r.Header.Get("x-device-id")), "Invalid device token")
	if err != nil {
		return nil, err
	}
	now := nowMs()
	s.mu.Lock()
	dev := s.devices[deviceID]
	if dev == nil {
		dev = &device{DeviceID: deviceID, GroupIDs: []string{}, Name: firstNonEmpty(str(body["name"]), "WebHTV"), Alias: str(body["alias"]), Role: firstNonEmpty(str(body["role"]), "app"), Type: firstNonNil(body["type"], 0), Capabilities: obj(body["capabilities"]), LastSeen: now, CreatedAt: now, UpdatedAt: now}
		s.devices[deviceID] = dev
	} else {
		dev.LastSeen = now
		dev.UpdatedAt = now
	}
	s.mu.Unlock()
	return dev, nil
}

func (s *relayState) updateDeviceGroups(r *http.Request, dev *device, body map[string]any) error {
	groupIDs := map[string]bool{}
	for _, id := range deviceGroupIDs(dev) {
		groupIDs[id] = true
	}
	for _, token := range readGroupTokens(nil, body) {
		group, err := groupFromToken(r, token)
		if err != nil {
			return err
		}
		groupIDs[group.GroupID] = true
	}
	ids := keys(groupIDs)
	s.mu.Lock()
	dev.GroupIDs = ids
	if len(ids) > 0 {
		dev.GroupID = ids[0]
	}
	dev.LastSeen = nowMs()
	dev.UpdatedAt = dev.LastSeen
	s.devices[dev.DeviceID] = dev
	for _, id := range ids {
		s.linkDeviceLocked(id, dev.DeviceID)
	}
	s.mu.Unlock()
	return nil
}

func (s *relayState) enqueueCommand(groupID, targetDeviceID, commandType string, payload map[string]any, groupTokenHash string) *command {
	if commandType == "" {
		commandType = "unknown"
	}
	cmd := &command{ID: "cmd_" + randomID(20), GroupID: groupID, GroupTokenHash: groupTokenHash, TargetDeviceID: targetDeviceID, Type: commandType, Payload: payload, Status: "queued", CreatedAt: nowMs()}
	s.mu.Lock()
	s.commands[cmd.ID] = cmd
	s.queues[targetDeviceID] = append(s.queues[targetDeviceID], cmd.ID)
	s.mu.Unlock()
	s.deliverPending(targetDeviceID)
	return cmd
}

func (s *relayState) nextQueuedCommand(deviceID string) *command {
	s.mu.Lock()
	defer s.mu.Unlock()
	queue := s.queues[deviceID]
	for len(queue) > 0 {
		id := queue[0]
		queue = queue[1:]
		cmd := s.commands[id]
		if cmd == nil || expired(cmd.CreatedAt, commandTTL) {
			continue
		}
		cmd.Status = "delivered"
		cmd.DeliveredAt = nowMs()
		s.commands[cmd.ID] = cmd
		s.queues[deviceID] = queue
		return cmd
	}
	s.queues[deviceID] = queue
	return nil
}

func (s *relayState) deliverPending(deviceID string) {
	for {
		cmd := s.nextQueuedCommand(deviceID)
		if cmd == nil {
			return
		}
		s.mu.Lock()
		clients := make([]*wsClient, 0, len(s.sockets[deviceID]))
		for client := range s.sockets[deviceID] {
			clients = append(clients, client)
		}
		s.mu.Unlock()
		if len(clients) == 0 {
			s.mu.Lock()
			cmd.Status = "queued"
			cmd.DeliveredAt = 0
			s.commands[cmd.ID] = cmd
			s.queues[deviceID] = append([]string{cmd.ID}, s.queues[deviceID]...)
			s.mu.Unlock()
			return
		}
		sent := false
		for _, client := range clients {
			if client.sendJSON(map[string]any{"type": "command", "ok": true, "command": cmd, "server": capabilities()}) {
				sent = true
			}
		}
		if !sent {
			s.mu.Lock()
			cmd.Status = "queued"
			cmd.DeliveredAt = 0
			s.commands[cmd.ID] = cmd
			s.queues[deviceID] = append([]string{cmd.ID}, s.queues[deviceID]...)
			s.mu.Unlock()
			return
		}
	}
}

func (s *relayState) registerSocket(client *wsClient) {
	s.mu.Lock()
	if s.sockets[client.deviceID] == nil {
		s.sockets[client.deviceID] = map[*wsClient]bool{}
	}
	s.sockets[client.deviceID][client] = true
	if dev := s.devices[client.deviceID]; dev != nil {
		dev.LastSeen = nowMs()
		dev.UpdatedAt = dev.LastSeen
	}
	s.mu.Unlock()
}

func (s *relayState) unregisterSocket(client *wsClient) {
	s.mu.Lock()
	if set := s.sockets[client.deviceID]; set != nil {
		delete(set, client)
		if len(set) == 0 {
			delete(s.sockets, client.deviceID)
		}
	}
	s.mu.Unlock()
}

func (s *relayState) requireTargetIfKnown(groupID, deviceID string) error {
	if deviceID == "" {
		return httpErrorf(http.StatusBadRequest, "Missing deviceId")
	}
	s.mu.Lock()
	dev := s.devices[deviceID]
	s.mu.Unlock()
	if dev != nil && !deviceInGroup(dev, groupID) {
		return httpErrorf(http.StatusNotFound, "Device is not bound to this group")
	}
	return nil
}

func (s *relayState) getSyncForDevice(syncID, deviceID string) (*syncTask, error) {
	s.mu.Lock()
	sync := s.syncs[syncID]
	s.mu.Unlock()
	if sync == nil {
		return nil, httpErrorf(http.StatusNotFound, "Sync not found")
	}
	if sync.SourceDeviceID != deviceID && sync.TargetDeviceID != deviceID {
		return nil, httpErrorf(http.StatusForbidden, "Device is not part of this sync")
	}
	return sync, nil
}

func (s *relayState) linkDeviceLocked(groupID, deviceID string) {
	if groupID == "" || deviceID == "" {
		return
	}
	if s.groupDevices[groupID] == nil {
		s.groupDevices[groupID] = map[string]bool{}
	}
	s.groupDevices[groupID][deviceID] = true
}

func (s *relayState) publicDevice(dev *device) publicDevice {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.publicDeviceLocked(dev)
}

func (s *relayState) publicDeviceLocked(dev *device) publicDevice {
	name := firstNonEmpty(dev.Alias, dev.Name)
	online := nowMs()-dev.LastSeen < 45_000 || len(s.sockets[dev.DeviceID]) > 0
	return publicDevice{DeviceID: dev.DeviceID, Name: name, RawName: dev.Name, Role: dev.Role, Type: dev.Type, AppVersion: dev.AppVersion, LastSeen: dev.LastSeen, Online: online, Capabilities: dev.Capabilities}
}

func (s *relayState) cleanup() {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := time.Now()
	if now.Sub(s.lastCleanup) < 30*time.Second {
		return
	}
	s.lastCleanup = now
	for code, bind := range s.bindCodes {
		if now.After(bind.ExpiresAt) {
			delete(s.bindCodes, code)
		}
	}
	for id, cmd := range s.commands {
		if expired(cmd.CreatedAt, commandTTL) {
			delete(s.commands, id)
		}
	}
	for id, sync := range s.syncs {
		if !expired(sync.CreatedAt, syncTTL) {
			continue
		}
		for _, info := range sync.Parts {
			delete(s.parts, str(info["key"]))
		}
		delete(s.syncs, id)
	}
	for deviceID, queue := range s.queues {
		next := queue[:0]
		for _, id := range queue {
			if s.commands[id] != nil {
				next = append(next, id)
			}
		}
		s.queues[deviceID] = next
	}
}

func (c *wsClient) sendJSON(value any) bool {
	select {
	case <-c.done:
		return false
	default:
	}
	select {
	case c.send <- value:
		return true
	case <-c.done:
		return false
	default:
		c.close()
		return false
	}
}

func (c *wsClient) readLoop(r *http.Request) {
	defer c.close()
	c.conn.SetPongHandler(func(string) error {
		c.touch()
		return nil
	})
	for {
		var msg map[string]any
		if err := c.conn.ReadJSON(&msg); err != nil {
			return
		}
		c.touch()
		if str(msg["type"]) == "result" {
			commandID := cleanID(str(msg["commandId"]))
			if commandID != "" {
				state.applyWSCommandResult(c.deviceID, commandID, msg)
			}
		}
	}
}

func (c *wsClient) writeLoop() {
	ticker := time.NewTicker(25 * time.Second)
	defer ticker.Stop()
	defer c.close()
	for {
		select {
		case msg, ok := <-c.send:
			if !ok {
				return
			}
			if err := c.conn.WriteJSON(msg); err != nil {
				return
			}
		case <-ticker.C:
			if err := c.conn.WriteControl(websocket.PingMessage, []byte("ping"), time.Now().Add(5*time.Second)); err != nil {
				return
			}
			c.touch()
		case <-c.done:
			return
		}
	}
}

func (c *wsClient) touch() {
	c.state.mu.Lock()
	if dev := c.state.devices[c.deviceID]; dev != nil {
		dev.LastSeen = nowMs()
		dev.UpdatedAt = dev.LastSeen
	}
	c.state.mu.Unlock()
}

func (c *wsClient) close() {
	c.once.Do(func() {
		close(c.done)
		c.state.unregisterSocket(c)
		_ = c.conn.Close()
	})
}

func (s *relayState) applyWSCommandResult(deviceID, commandID string, body map[string]any) {
	s.mu.Lock()
	defer s.mu.Unlock()
	cmd := s.commands[commandID]
	if cmd == nil || cmd.TargetDeviceID != deviceID {
		return
	}
	if boolVal(body["ok"], true) {
		cmd.Status = "done"
	} else {
		cmd.Status = "failed"
	}
	cmd.Result = body
	cmd.FinishedAt = nowMs()
	s.commands[cmd.ID] = cmd
}

func requireGroup(r *http.Request, body map[string]any) (*groupInfo, error) {
	token := readGroupToken(r, body)
	if token == "" {
		return nil, httpErrorf(http.StatusUnauthorized, "Missing group token")
	}
	return groupFromToken(r, token)
}

func groupFromToken(r *http.Request, token string) (*groupInfo, error) {
	token = strings.TrimSpace(token)
	if token == "" {
		return nil, httpErrorf(http.StatusUnauthorized, "Missing group token")
	}
	origin := serverOrigin(r)
	hash := sha256Hex(origin + ":" + token)
	return &groupInfo{GroupID: "grp_" + hash[:24], GroupToken: token, FamilyToken: token, GroupTokenHash: hash}, nil
}

func requireDerivedID(prefix, origin, token, id, message string) (string, error) {
	expected := deriveID(prefix, origin, token)
	actual := cleanID(id)
	if actual != "" && actual != expected {
		return "", httpErrorf(http.StatusUnauthorized, message)
	}
	return expected, nil
}

func deriveID(prefix, origin, token string) string {
	hash := sha256Hex(origin + ":" + token)
	return prefix + "_" + hash[:24]
}

func readJSON(r *http.Request) (map[string]any, error) {
	if r.Body == nil {
		return map[string]any{}, nil
	}
	defer r.Body.Close()
	bytes, err := io.ReadAll(io.LimitReader(r.Body, 2*1024*1024))
	if err != nil {
		return nil, err
	}
	if len(bytes) == 0 {
		return map[string]any{}, nil
	}
	var body map[string]any
	if err := json.Unmarshal(bytes, &body); err != nil {
		return nil, httpErrorf(http.StatusBadRequest, "Invalid JSON body")
	}
	if body == nil {
		body = map[string]any{}
	}
	return body, nil
}

func writeJSON(w http.ResponseWriter, status int, data any) error {
	w.Header().Set("content-type", "application/json; charset=utf-8")
	w.Header().Set("cache-control", "no-store")
	w.WriteHeader(status)
	return json.NewEncoder(w).Encode(data)
}

func writeCORS(w http.ResponseWriter) {
	w.Header().Set("access-control-allow-origin", "*")
	w.Header().Set("access-control-allow-methods", "GET,POST,OPTIONS")
	w.Header().Set("access-control-allow-headers", "authorization,content-type,idempotency-key,x-device-id,x-device-token,x-group-token,x-family-token,x-webhtv-token,x-webhtv-config-key,x-webhtv-config-name,x-webhtv-timestamp,x-webhtv-since,x-webhtv-limit,x-webhtv-webhook-id,x-webhtv-dedupe-key")
	w.Header().Set("access-control-max-age", "86400")
}

type httpError struct {
	Status int
	Text   string
}

func (e *httpError) Error() string { return e.Text }

func httpErrorf(status int, format string, args ...any) error {
	return &httpError{Status: status, Text: fmt.Sprintf(format, args...)}
}

func serverOrigin(r *http.Request) string {
	if origin := normalizeOrigin(r.Header.Get("x-webhtv-origin")); origin != "" {
		return origin
	}
	scheme := r.Header.Get("x-forwarded-proto")
	if scheme == "" {
		if r.TLS != nil {
			scheme = "https"
		} else {
			scheme = "http"
		}
	}
	host := r.Header.Get("x-forwarded-host")
	if host == "" {
		host = r.Host
	}
	return strings.ToLower(scheme + "://" + host)
}

func normalizeOrigin(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	lower := strings.ToLower(value)
	if !strings.HasPrefix(lower, "http://") && !strings.HasPrefix(lower, "https://") {
		return ""
	}
	rest := strings.TrimPrefix(strings.TrimPrefix(lower, "https://"), "http://")
	if rest == "" {
		return ""
	}
	if index := strings.IndexAny(rest, "/?#"); index >= 0 {
		rest = rest[:index]
	}
	if rest == "" {
		return ""
	}
	if strings.HasPrefix(lower, "https://") {
		return "https://" + rest
	}
	return "http://" + rest
}

func readDeviceToken(r *http.Request, body map[string]any) string {
	return strings.TrimSpace(firstNonEmpty(str(body["deviceToken"]), str(body["deviceSecret"]), header(r, "x-device-token"), bearer(r)))
}

func readGroupToken(r *http.Request, body map[string]any) string {
	if body == nil {
		body = map[string]any{}
	}
	return strings.TrimSpace(firstNonEmpty(str(body["groupToken"]), str(body["familyToken"]), header(r, "x-group-token"), header(r, "x-family-token"), bearer(r)))
}

func readGroupTokens(r *http.Request, body map[string]any) []string {
	var result []string
	if token := readGroupToken(r, body); token != "" {
		result = append(result, token)
	}
	if raw, ok := body["groups"].([]any); ok {
		for _, item := range raw {
			if text, ok := item.(string); ok && strings.TrimSpace(text) != "" {
				result = append(result, strings.TrimSpace(text))
			} else if obj := obj(item); len(obj) > 0 {
				token := firstNonEmpty(str(obj["groupToken"]), str(obj["familyToken"]))
				if token == "" {
					continue
				}
				result = append(result, token)
			}
		}
	}
	seen := map[string]bool{}
	out := result[:0]
	for _, token := range result {
		if token == "" || seen[token] {
			continue
		}
		seen[token] = true
		out = append(out, token)
	}
	return out
}

func bearer(r *http.Request) string {
	if r == nil {
		return ""
	}
	value := r.Header.Get("authorization")
	if strings.HasPrefix(strings.ToLower(value), "bearer ") {
		return strings.TrimSpace(value[7:])
	}
	return ""
}

func header(r *http.Request, key string) string {
	if r == nil {
		return ""
	}
	return r.Header.Get(key)
}

func normalizeSyncOptions(options map[string]any) map[string]any {
	return map[string]any{
		"config":      boolVal(options["config"], true),
		"loginState":  boolVal(options["loginState"], true),
		"spider":      boolVal(options["spider"], true),
		"webHome":     boolVal(options["webHome"], true),
		"search":      boolVal(options["search"], true),
		"keep":        boolVal(options["keep"], true),
		"history":     boolVal(options["history"], true),
		"follow":      boolVal(options["follow"], false),
		"settings":    boolVal(options["settings"], false),
		"remoteRelay": boolVal(options["remoteRelay"], false),
		"paths":       optionalString(options["paths"]),
	}
}

func normalizePart(part string) (string, error) {
	part = strings.TrimSuffix(strings.TrimSuffix(part, ".zip"), ".json")
	if !validParts[part] {
		return "", httpErrorf(http.StatusBadRequest, "Invalid sync part")
	}
	return part, nil
}

func contentTypeForPart(part string) string {
	if part == "backup" || part == "manifest" || part == "remoteRelay" {
		return "application/json; charset=utf-8"
	}
	return "application/zip"
}

func cleanID(value string) string {
	clean := cleanIDRe.ReplaceAllString(value, "")
	if len(clean) > 80 {
		return clean[:80]
	}
	return clean
}

func deviceGroupIDs(dev *device) []string {
	if dev == nil {
		return nil
	}
	ids := map[string]bool{}
	for _, id := range dev.GroupIDs {
		if id != "" {
			ids[id] = true
		}
	}
	if dev.GroupID != "" {
		ids[dev.GroupID] = true
	}
	return keys(ids)
}

func addGroupToDevice(dev *device, groupID string) {
	ids := map[string]bool{}
	for _, id := range deviceGroupIDs(dev) {
		ids[id] = true
	}
	if groupID != "" {
		ids[groupID] = true
	}
	dev.GroupIDs = keys(ids)
	if len(dev.GroupIDs) > 0 {
		dev.GroupID = dev.GroupIDs[0]
	}
}

func deviceInGroup(dev *device, groupID string) bool {
	for _, id := range deviceGroupIDs(dev) {
		if id == groupID {
			return true
		}
	}
	return false
}

func str(value any) string {
	switch v := value.(type) {
	case string:
		return strings.TrimSpace(v)
	case fmt.Stringer:
		return strings.TrimSpace(v.String())
	case nil:
		return ""
	default:
		return strings.TrimSpace(fmt.Sprint(v))
	}
}

func obj(value any) map[string]any {
	if value == nil {
		return map[string]any{}
	}
	if m, ok := value.(map[string]any); ok {
		return m
	}
	return map[string]any{}
}

func boolVal(value any, fallback bool) bool {
	switch v := value.(type) {
	case bool:
		return v
	case string:
		if v == "" {
			return fallback
		}
		return strings.EqualFold(v, "true") || v == "1"
	case nil:
		return fallback
	default:
		return fallback
	}
}

func optionalString(value any) any {
	if text := str(value); text != "" {
		return text
	}
	return nil
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return strings.TrimSpace(value)
		}
	}
	return ""
}

func firstNonNil(values ...any) any {
	for _, value := range values {
		if value != nil {
			return value
		}
	}
	return nil
}

func firstCaps(current map[string]any, existing *device) map[string]any {
	if len(current) > 0 {
		return current
	}
	if existing != nil && existing.Capabilities != nil {
		return existing.Capabilities
	}
	return map[string]any{}
}

func field(existing *device, key string) string {
	if existing == nil {
		return ""
	}
	switch key {
	case "name":
		return existing.Name
	case "alias":
		return existing.Alias
	case "role":
		return existing.Role
	case "appVersion":
		return existing.AppVersion
	default:
		return ""
	}
}

func existingType(existing *device) any {
	if existing == nil {
		return nil
	}
	return existing.Type
}

func keys(input map[string]bool) []string {
	result := make([]string, 0, len(input))
	for key := range input {
		if key != "" {
			result = append(result, key)
		}
	}
	sort.Strings(result)
	return result
}

func sha256Hex(text string) string {
	sum := sha256.Sum256([]byte(text))
	return hex.EncodeToString(sum[:])
}

func randomID(bytes int) string {
	buf := make([]byte, bytes)
	if _, err := rand.Read(buf); err != nil {
		panic(err)
	}
	return hex.EncodeToString(buf)
}

func randomCapability(prefix string) string {
	return prefix + "_" + randomID(32)
}

func mustRandomInt(max int) int {
	buf := make([]byte, 4)
	if _, err := rand.Read(buf); err != nil {
		return int(time.Now().UnixNano() % int64(max))
	}
	value := int(buf[0])<<24 | int(buf[1])<<16 | int(buf[2])<<8 | int(buf[3])
	if value < 0 {
		value = -value
	}
	return value % max
}

func expired(ms int64, ttl time.Duration) bool {
	return time.Since(time.UnixMilli(ms)) > ttl
}

func nowMs() int64 {
	return time.Now().UnixMilli()
}

func init() {
	_ = mime.TypeByExtension(".zip")
}
