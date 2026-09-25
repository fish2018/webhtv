package main

import (
	"fmt"
	"net/http"
	"sort"
	"strings"
)

const playbackIdentitySchema = "webhtv.playback.identity.v1"

type playbackIdentityRegistry struct {
	Epoch      int64                             `json:"epoch"`
	Identities map[string]*playbackIdentity      `json:"identities"`
	Aliases    map[string]*playbackIdentityAlias `json:"aliases"`
	Requests   map[string]map[string]any         `json:"requests,omitempty"`
	UpdatedAt  int64                             `json:"updatedAt"`
}

type playbackIdentity struct {
	CanonicalInterfaceKey string   `json:"canonicalInterfaceKey"`
	StrictAddressKeys     []string `json:"strictAddressKeys"`
	EndpointMatchKeys     []string `json:"endpointMatchKeys"`
	HostMatchKeys         []string `json:"hostMatchKeys"`
	LegacyConfigKeys      []string `json:"legacyConfigKeys"`
	CreatedAt             int64    `json:"createdAt"`
	UpdatedAt             int64    `json:"updatedAt"`
}

type playbackIdentityAlias struct {
	CanonicalInterfaceKey string `json:"canonicalInterfaceKey"`
	Kind                  string `json:"kind"`
}

type playbackIdentityInput struct {
	Schema            string
	Operation         string
	ConfigType        string
	InterfaceKey      string
	StrictAddressKeys []string
	EndpointMatchKeys []string
	HostMatchKeys     []string
	LegacyConfigKeys  []string
	SourceDataState   string
	Confirm           bool
	RequestID         string
}

func newPlaybackIdentityRegistry() *playbackIdentityRegistry {
	return &playbackIdentityRegistry{Identities: map[string]*playbackIdentity{}, Aliases: map[string]*playbackIdentityAlias{}, Requests: map[string]map[string]any{}}
}

func normalizePlaybackIdentityRegistry(registry *playbackIdentityRegistry) *playbackIdentityRegistry {
	if registry == nil {
		return newPlaybackIdentityRegistry()
	}
	if registry.Identities == nil {
		registry.Identities = map[string]*playbackIdentity{}
	}
	if registry.Aliases == nil {
		registry.Aliases = map[string]*playbackIdentityAlias{}
	}
	if registry.Requests == nil {
		registry.Requests = map[string]map[string]any{}
	}
	for key, identity := range registry.Identities {
		if !playbackValidIdentityKey(key) {
			delete(registry.Identities, key)
			continue
		}
		if identity == nil {
			identity = &playbackIdentity{CanonicalInterfaceKey: key}
			registry.Identities[key] = identity
		}
		identity.CanonicalInterfaceKey = key
		identity.StrictAddressKeys = playbackUniqueIdentityKeys(identity.StrictAddressKeys)
		identity.EndpointMatchKeys = playbackUniqueIdentityKeys(identity.EndpointMatchKeys)
		identity.HostMatchKeys = playbackUniqueIdentityKeys(identity.HostMatchKeys)
		identity.LegacyConfigKeys = playbackUniqueIdentityKeys(identity.LegacyConfigKeys)
	}
	for key, alias := range registry.Aliases {
		if !playbackValidIdentityKey(key) || alias == nil || !playbackValidIdentityKey(alias.CanonicalInterfaceKey) || registry.Identities[alias.CanonicalInterfaceKey] == nil {
			delete(registry.Aliases, key)
		}
	}
	return registry
}

func parsePlaybackIdentityInput(body map[string]any, r *http.Request) (playbackIdentityInput, error) {
	input := playbackIdentityInput{
		Schema:            playbackString(playbackFirst(body, "schema")),
		Operation:         strings.ToLower(playbackString(playbackFirst(body, "operation"))),
		ConfigType:        playbackConfigType(playbackString(playbackFirst(body, "configType"))),
		InterfaceKey:      playbackString(playbackFirst(body, "interfaceKey")),
		StrictAddressKeys: playbackIdentityArray(body, "strictAddressKeys"),
		EndpointMatchKeys: playbackIdentityArray(body, "endpointMatchKeys"),
		HostMatchKeys:     playbackIdentityArray(body, "hostMatchKeys"),
		LegacyConfigKeys:  playbackIdentityArray(body, "legacyConfigKeys"),
		SourceDataState:   strings.ToLower(playbackString(playbackFirst(body, "sourceDataState"))),
		Confirm:           playbackBool(playbackFirst(body, "confirm")),
		RequestID:         playbackCleanString(firstNonEmpty(r.Header.Get("X-WebHTV-Request-Id"), playbackString(playbackFirst(body, "requestId"))), 160),
	}
	if input.Schema == "" {
		input.Schema = playbackIdentitySchema
	}
	if input.Operation == "" {
		input.Operation = "resolve"
	}
	if input.SourceDataState != "empty" && input.SourceDataState != "has_data" {
		input.SourceDataState = "unknown"
	}
	if input.InterfaceKey == "" {
		input.InterfaceKey = strings.TrimSpace(r.Header.Get("X-WebHTV-Config-Key"))
	}
	var err error
	input.InterfaceKey, err = playbackValidateIdentityKey(input.InterfaceKey, "interfaceKey")
	if err != nil {
		return input, err
	}
	for _, item := range [][]string{input.StrictAddressKeys, input.EndpointMatchKeys, input.HostMatchKeys, input.LegacyConfigKeys} {
		if len(item) > 32 {
			return input, httpErrorf(http.StatusBadRequest, "identity key list is too long")
		}
		for _, key := range item {
			if _, err := playbackValidateIdentityKey(key, "identity key"); err != nil {
				return input, err
			}
		}
	}
	return input, nil
}

func (s *playbackService) identityResolve(w http.ResponseWriter, r *http.Request, token string) error {
	body, err := readPlaybackBody(r)
	if err != nil {
		return err
	}
	object, ok := body.(map[string]any)
	if !ok {
		return httpErrorf(http.StatusBadRequest, "Invalid identity request")
	}
	input, err := parsePlaybackIdentityInput(object, r)
	if err != nil {
		return err
	}
	if input.Schema != playbackIdentitySchema || input.Operation != "resolve" {
		return writeJSON(w, http.StatusBadRequest, playbackIdentityResponse("invalid", input, map[string]any{"error": "Unsupported identity request"}))
	}
	registryKey := playbackIdentityRegistryKey(token, input.ConfigType)
	s.mu.Lock()
	defer s.mu.Unlock()
	registry := normalizePlaybackIdentityRegistry(s.identities[registryKey])
	if input.RequestID != "" {
		if cached := registry.Requests[input.RequestID]; cached != nil {
			status := int(playbackFloat(cached["status"]))
			if status == 0 {
				status = http.StatusOK
			}
			return writeJSON(w, status, cached["body"])
		}
	}
	resolution := playbackChooseIdentity(registry, input)
	if resolution["action"] == "conflict" {
		body := playbackIdentityResponse("conflict", input, resolution)
		if input.RequestID != "" {
			registry.Requests[input.RequestID] = map[string]any{"status": http.StatusConflict, "body": body}
			s.identities[registryKey] = registry
			_ = s.saveLocked()
		}
		return writeJSON(w, http.StatusConflict, body)
	}
	if resolution["action"] == "confirm_required" {
		body := playbackIdentityResponse("confirm_required", input, resolution)
		if input.RequestID != "" {
			registry.Requests[input.RequestID] = map[string]any{"status": http.StatusOK, "body": body}
			s.identities[registryKey] = registry
			_ = s.saveLocked()
		}
		return writeJSON(w, http.StatusOK, body)
	}
	canonical := playbackString(resolution["canonicalInterfaceKey"])
	identity := registry.Identities[canonical]
	if identity == nil {
		identity = &playbackIdentity{CanonicalInterfaceKey: canonical, CreatedAt: nowMs()}
		registry.Identities[canonical] = identity
	}
	playbackAddIdentityKeys(identity, input)
	if (playbackString(resolution["action"]) == "adopt" || playbackString(resolution["action"]) == "merge") && input.InterfaceKey != canonical {
		playbackAddIdentityAlias(registry, input.InterfaceKey, canonical, "interface")
	}
	for _, key := range append(append(append(identity.StrictAddressKeys, identity.EndpointMatchKeys...), identity.HostMatchKeys...), identity.LegacyConfigKeys...) {
		playbackAddIdentityAlias(registry, key, canonical, playbackIdentityKeyKind(identity, key))
	}
	registry.Epoch++
	registry.UpdatedAt = nowMs()
	migration := s.migratePlaybackIdentityLocked(token, input.ConfigType, canonical, append(append([]string{}, input.LegacyConfigKeys...), input.InterfaceKey))
	action := playbackString(resolution["action"])
	if migration {
		action = "migration_pending"
	}
	s.identities[registryKey] = registry
	if err := s.saveLocked(); err != nil {
		return httpErrorf(http.StatusServiceUnavailable, "Playback identity registry write failed")
	}
	response := map[string]any{
		"migrationRequired": migration,
		"migrationDone":     migration,
		"resetSince":        migration,
		"nextSince": func() string {
			if migration {
				return "0"
			}
			return ""
		}(),
		"identityEpoch":         playbackIntString(registry.Epoch),
		"canonicalInterfaceKey": canonical,
		"sourceInterfaceKey":    input.InterfaceKey,
	}
	for key, value := range resolution {
		if _, exists := response[key]; !exists {
			response[key] = value
		}
	}
	responseBody := playbackIdentityResponse(action, input, response)
	if input.RequestID != "" {
		registry.Requests[input.RequestID] = map[string]any{"status": http.StatusOK, "body": responseBody}
		s.identities[registryKey] = registry
		_ = s.saveLocked()
	}
	return writeJSON(w, http.StatusOK, responseBody)
}

func playbackChooseIdentity(registry *playbackIdentityRegistry, input playbackIdentityInput) map[string]any {
	bound := playbackBoundCanonical(registry, input.InterfaceKey)
	strict := playbackIdentityCandidates(registry, input.StrictAddressKeys, "strict")
	endpoint := playbackIdentityCandidates(registry, input.EndpointMatchKeys, "endpoint")
	legacy := playbackIdentityCandidates(registry, input.LegacyConfigKeys, "legacy")
	strong := strict
	matchedBy := "strictAddressKey"
	matchedKeys := input.StrictAddressKeys
	if len(strong) == 0 {
		strong, matchedBy, matchedKeys = endpoint, "endpointMatchKey", input.EndpointMatchKeys
	}
	if len(strong) == 0 {
		strong, matchedBy, matchedKeys = legacy, "legacyConfigKey", input.LegacyConfigKeys
	}
	if bound != "" {
		if len(strong) > 0 && (len(strong) != 1 || strong[0] != bound) {
			return map[string]any{"action": "conflict", "matchedBy": matchedBy, "matchedKeys": matchedKeys, "candidates": strong, "error": "Submitted interface is bound to a different identity"}
		}
		return map[string]any{"action": "keep", "canonicalInterfaceKey": bound, "matchedBy": "interfaceKey"}
	}
	if len(strong) > 1 {
		return map[string]any{"action": "conflict", "matchedBy": matchedBy, "matchedKeys": matchedKeys, "candidates": strong, "error": "Address clues belong to multiple identities"}
	}
	if len(strong) == 1 {
		if input.SourceDataState != "empty" {
			if input.Confirm {
				return map[string]any{"action": "merge", "canonicalInterfaceKey": strong[0], "matchedBy": matchedBy, "matchedKeys": matchedKeys}
			}
			return map[string]any{"action": "confirm_required", "canonicalInterfaceKey": strong[0], "matchedBy": matchedBy, "matchedKeys": matchedKeys, "candidates": strong, "error": "Existing source data requires explicit merge confirmation"}
		}
		return map[string]any{"action": "adopt", "canonicalInterfaceKey": strong[0], "matchedBy": matchedBy, "matchedKeys": matchedKeys}
	}
	weak := playbackIdentityCandidates(registry, input.HostMatchKeys, "host")
	if len(weak) == 1 && input.Confirm {
		return map[string]any{"action": "merge", "canonicalInterfaceKey": weak[0], "matchedBy": "hostMatchKey", "matchedKeys": input.HostMatchKeys}
	}
	if len(weak) > 0 {
		return map[string]any{"action": "confirm_required", "matchedBy": "hostMatchKey", "matchedKeys": input.HostMatchKeys, "candidates": weak, "error": "Host-only match requires confirmation"}
	}
	return map[string]any{"action": "create", "canonicalInterfaceKey": input.InterfaceKey, "matchedBy": "none", "matchedKeys": []string{}}
}

func playbackIdentityCandidates(registry *playbackIdentityRegistry, keys []string, kind string) []string {
	seen := map[string]bool{}
	for _, key := range keys {
		if alias := registry.Aliases[key]; alias != nil {
			seen[alias.CanonicalInterfaceKey] = true
		}
		for canonical, identity := range registry.Identities {
			if playbackIdentityHasKey(identity, key, kind) {
				seen[canonical] = true
			}
		}
	}
	result := make([]string, 0, len(seen))
	for key := range seen {
		result = append(result, key)
	}
	sort.Strings(result)
	return result
}

func playbackIdentityHasKey(identity *playbackIdentity, key, kind string) bool {
	if identity == nil {
		return false
	}
	var values []string
	switch kind {
	case "strict":
		values = identity.StrictAddressKeys
	case "endpoint":
		values = identity.EndpointMatchKeys
	case "host":
		values = identity.HostMatchKeys
	default:
		values = identity.LegacyConfigKeys
	}
	for _, value := range values {
		if value == key {
			return true
		}
	}
	return false
}

func playbackBoundCanonical(registry *playbackIdentityRegistry, key string) string {
	if registry.Identities[key] != nil {
		return key
	}
	if alias := registry.Aliases[key]; alias != nil {
		return alias.CanonicalInterfaceKey
	}
	return ""
}

func playbackAddIdentityKeys(identity *playbackIdentity, input playbackIdentityInput) {
	identity.UpdatedAt = nowMs()
	identity.StrictAddressKeys = playbackPushKeys(identity.StrictAddressKeys, input.StrictAddressKeys)
	identity.EndpointMatchKeys = playbackPushKeys(identity.EndpointMatchKeys, input.EndpointMatchKeys)
	identity.HostMatchKeys = playbackPushKeys(identity.HostMatchKeys, input.HostMatchKeys)
	identity.LegacyConfigKeys = playbackPushKeys(identity.LegacyConfigKeys, input.LegacyConfigKeys)
}

func playbackAddIdentityAlias(registry *playbackIdentityRegistry, key, canonical, kind string) {
	if key == "" || registry.Identities[canonical] == nil {
		return
	}
	if current := registry.Aliases[key]; current != nil && current.CanonicalInterfaceKey != canonical {
		return
	}
	registry.Aliases[key] = &playbackIdentityAlias{CanonicalInterfaceKey: canonical, Kind: kind}
}

func playbackIdentityKeyKind(identity *playbackIdentity, key string) string {
	if playbackIdentityHasKey(identity, key, "strict") {
		return "strict"
	}
	if playbackIdentityHasKey(identity, key, "endpoint") {
		return "endpoint"
	}
	if playbackIdentityHasKey(identity, key, "host") {
		return "host"
	}
	return "legacy"
}

func playbackPushKeys(values, additions []string) []string {
	seen := map[string]bool{}
	for _, value := range values {
		seen[value] = true
	}
	for _, value := range additions {
		if value != "" && len(values) < 32 && !seen[value] {
			values = append(values, value)
			seen[value] = true
		}
	}
	return values
}

func playbackUniqueIdentityKeys(values []string) []string {
	return playbackPushKeys(nil, values)
}

func playbackIdentityArray(body map[string]any, key string) []string {
	values := playbackFirstArray(body, key)
	result := make([]string, 0, len(values))
	for _, value := range values {
		if text := strings.TrimSpace(playbackString(value)); text != "" {
			result = append(result, text)
		}
	}
	return result
}

func playbackConfigType(value string) string {
	value = strings.ToLower(strings.TrimSpace(value))
	switch value {
	case "1", "live":
		return "live"
	case "2", "wall":
		return "wall"
	default:
		return "vod"
	}
}

func playbackIdentityRegistryKey(token, configType string) string {
	return sha256Hex(token) + ":" + playbackConfigType(configType)
}

func playbackValidIdentityKey(value string) bool {
	value = strings.ToLower(strings.TrimSpace(value))
	if value == "" || len(value) > 128 {
		return false
	}
	for _, char := range value {
		if !(char >= 'a' && char <= 'z' || char >= '0' && char <= '9' || strings.ContainsRune("._:-", char)) {
			return false
		}
	}
	return true
}

func playbackValidateIdentityKey(value, label string) (string, error) {
	value = strings.ToLower(strings.TrimSpace(value))
	if !playbackValidIdentityKey(value) {
		return "", httpErrorf(http.StatusBadRequest, "%s is invalid", label)
	}
	return value, nil
}

func playbackIdentityResponse(action string, input playbackIdentityInput, extra map[string]any) map[string]any {
	response := map[string]any{
		"ok":                    action != "conflict" && action != "invalid" && action != "unavailable",
		"schema":                playbackIdentitySchema,
		"identityProtocol":      playbackIdentitySchema,
		"addressMatchVersion":   1,
		"action":                action,
		"canonicalInterfaceKey": "",
		"sourceInterfaceKey":    input.InterfaceKey,
		"matchedBy":             "none",
		"matchedKeys":           []string{},
		"migrationRequired":     false,
		"migrationDone":         false,
		"resetSince":            false,
		"nextSince":             "",
		"identityEpoch":         "",
		"capabilities":          map[string]any{"playbackSync": true, "identityResolve": true, "identityAliases": true, "legacyUrlHashMigration": true, "identityMerge": true},
	}
	for key, value := range extra {
		response[key] = value
	}
	return response
}

func playbackIntString(value int64) string { return strings.TrimSpace(fmt.Sprintf("%d", value)) }

func (s *playbackService) migratePlaybackIdentityLocked(token, configType, canonical string, sourceKeys []string) bool {
	migrated := false
	canonicalSpace := playbackSpaceKeyType(token, canonical, configType)
	working := s.spaces[canonicalSpace]
	if working == nil {
		working = newPlaybackSpace()
	}
	for _, source := range sourceKeys {
		if source == "" || source == canonical {
			continue
		}
		sourceSpace := s.spaces[playbackSpaceKeyType(token, source, configType)]
		if sourceSpace == nil {
			continue
		}
		for key, item := range sourceSpace.Items {
			current := working.Items[key]
			if current != nil && current.UpdatedAt >= item.UpdatedAt {
				continue
			}
			working.Sequence++
			copy := *item
			copy.Sequence = working.Sequence
			working.Items[key] = &copy
			migrated = true
		}
		for key, tombstone := range sourceSpace.Tombstones {
			current := working.Tombstones[key]
			if current != nil && current.DeletedAt >= tombstone.DeletedAt {
				continue
			}
			working.Sequence++
			copy := *tombstone
			copy.Sequence = working.Sequence
			working.Tombstones[key] = &copy
			for itemKey, item := range working.Items {
				if item.UpdatedAt <= copy.DeletedAt && playbackTombstoneMatches(&copy, &playbackEvent{HistoryKey: item.HistoryKey, SiteKey: item.SiteKey, VodID: item.VodID, ItemKey: itemKey}) {
					delete(working.Items, itemKey)
				}
			}
			migrated = true
		}
		for event, timestamp := range sourceSpace.Events {
			if _, exists := working.Events[event]; !exists {
				working.Events[event] = timestamp
			}
		}
	}
	if migrated {
		s.spaces[canonicalSpace] = working
	}
	return migrated
}
