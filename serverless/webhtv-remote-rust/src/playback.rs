use axum::{
    body::Bytes,
    extract::{OriginalUri, State},
    http::{HeaderMap, StatusCode, Uri},
};
use serde::{Deserialize, Serialize};
use serde_json::{json, Map, Value};
use std::{
    collections::{HashMap, HashSet},
    env,
    fs::{self, OpenOptions},
    io::Write,
    path::{Path, PathBuf},
    sync::atomic::{AtomicBool, Ordering},
};

#[cfg(unix)]
use std::os::unix::fs::OpenOptionsExt;

use crate::{
    header_string, json_response, now_ms, server_origin, sha256_hex, AppError, AppResult, JsonMap,
    SharedState,
};

const PLAYBACK_SCHEMA: &str = "webhtv.playback.v1";
const RETENTION_MS: i64 = 90 * 24 * 60 * 60 * 1000;
const CLEANUP_INTERVAL_MS: i64 = 24 * 60 * 60 * 1000;
const MAX_BODY_BYTES: usize = 128 * 1024;
const MAX_BATCH_ITEMS: usize = 100;
const DEFAULT_LIMIT: usize = 100;
const MAX_LIMIT: usize = 1000;
const STORAGE_VERSION: u32 = 1;

static PLAYBACK_AVAILABLE: AtomicBool = AtomicBool::new(false);
static PLAYBACK_PERSISTENT: AtomicBool = AtomicBool::new(false);

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PlaybackDiskState {
    version: u32,
    spaces: HashMap<String, PlaybackSpace>,
    #[serde(default)]
    identities: HashMap<String, IdentityRegistry>,
}

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PlaybackSpace {
    #[serde(default)]
    sequence: i64,
    #[serde(default)]
    last_cleanup: i64,
    #[serde(default)]
    items: HashMap<String, PlaybackItem>,
    #[serde(default)]
    tombstones: HashMap<String, PlaybackTombstone>,
    #[serde(default)]
    events: HashMap<String, i64>,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PlaybackItem {
    history_key: String,
    site_key: String,
    vod_id: String,
    updated_at: i64,
    seq: i64,
    payload: Value,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PlaybackTombstone {
    scope: String,
    history_key: String,
    site_key: String,
    vod_id: String,
    deleted_at: i64,
    seq: i64,
    payload: Value,
}

struct PlaybackEvent {
    kind: &'static str,
    config_key: String,
    event_id: String,
    history_key: String,
    site_key: String,
    vod_id: String,
    scope: String,
    updated_at: i64,
    deleted_at: i64,
    item_key: String,
    marker_key: String,
    payload: Value,
}

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct IdentityRegistry {
    #[serde(default)]
    epoch: i64,
    #[serde(default)]
    identities: HashMap<String, IdentityEntry>,
    #[serde(default)]
    aliases: HashMap<String, IdentityAlias>,
    #[serde(default)]
    updated_at: i64,
    #[serde(default)]
    requests: HashMap<String, Value>,
}

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct IdentityEntry {
    canonical_interface_key: String,
    #[serde(default)]
    strict_address_keys: Vec<String>,
    #[serde(default)]
    endpoint_match_keys: Vec<String>,
    #[serde(default)]
    host_match_keys: Vec<String>,
    #[serde(default)]
    legacy_config_keys: Vec<String>,
    #[serde(default)]
    created_at: i64,
    #[serde(default)]
    updated_at: i64,
}

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct IdentityAlias {
    canonical_interface_key: String,
    kind: String,
}

#[derive(Clone, Default)]
struct IdentityInput {
    schema: String,
    operation: String,
    config_type: String,
    interface_key: String,
    strict_address_keys: Vec<String>,
    endpoint_match_keys: Vec<String>,
    host_match_keys: Vec<String>,
    legacy_config_keys: Vec<String>,
    source_data_state: String,
    confirm: bool,
    request_id: String,
}

pub struct PlaybackService {
    path: Option<PathBuf>,
    spaces: HashMap<String, PlaybackSpace>,
    identities: HashMap<String, IdentityRegistry>,
    load_error: Option<String>,
}

impl PlaybackService {
    pub fn from_env() -> Self {
        let path = env::var("WEBHTV_PLAYBACK_DATA")
            .ok()
            .map(|value| value.trim().to_string())
            .filter(|value| !value.is_empty())
            .unwrap_or_else(|| "webhtv-playback.json".to_string());
        Self::new(path)
    }

    fn new(path: String) -> Self {
        let path = if path == ":memory:" {
            None
        } else {
            Some(PathBuf::from(path))
        };
        let mut service = Self {
            path,
            spaces: HashMap::new(),
            identities: HashMap::new(),
            load_error: None,
        };
        if let Err(error) = service.load() {
            tracing::error!(error = %error, "WebHTV playback storage unavailable");
            service.load_error = Some(error);
        }
        PLAYBACK_AVAILABLE.store(service.available(), Ordering::Relaxed);
        PLAYBACK_PERSISTENT.store(
            service.available() && service.persistent(),
            Ordering::Relaxed,
        );
        service
    }

    fn available(&self) -> bool {
        self.load_error.is_none()
    }

    fn persistent(&self) -> bool {
        self.path.is_some()
    }


    fn resolve_config_key(&self, token: &str, config_type: &str, config_key: &str, aliases: Vec<String>) -> Result<String, AppError> {
        let registry = self.identities.get(&identity_registry_key(token, config_type));
        let Some(registry) = registry else { return Ok(config_key.to_string()); };
        let mut candidates = HashSet::new();
        for key in std::iter::once(config_key.to_string()).chain(aliases.into_iter()) {
            if let Some(alias) = registry.aliases.get(&key) { candidates.insert(alias.canonical_interface_key.clone()); }
            if registry.identities.contains_key(&key) { candidates.insert(key); }
        }
        if candidates.len() > 1 { return Err(AppError::new(StatusCode::CONFLICT, "Identity alias maps to multiple canonical interfaces")); }
        Ok(candidates.into_iter().next().unwrap_or_else(|| config_key.to_string()))
    }

    fn resolve_identity(&mut self, token: &str, headers: &HeaderMap, payload: &Value) -> Result<Value, AppError> {
        let object = payload.as_object().ok_or_else(|| AppError::new(StatusCode::BAD_REQUEST, "Invalid identity request"))?;
        let input = parse_identity_input(object, headers)?;
        if input.schema != "webhtv.playback.identity.v1" || input.operation != "resolve" {
            return Ok(identity_response("invalid", &input, json!({"error": "Unsupported identity request"}), 400));
        }
        let registry_key = format!("{}:{}", sha256_hex(token), input.config_type);
        let mut registry = self.identities.remove(&registry_key).unwrap_or_default();
        if !input.request_id.is_empty() {
            if let Some(cached) = registry.requests.get(&input.request_id).cloned() {
                self.identities.insert(registry_key, registry);
                return Ok(cached);
            }
        }
        let resolution = choose_identity(&registry, &input);
        let action = resolution.get("action").and_then(Value::as_str).unwrap_or("invalid").to_string();
        if action == "conflict" || action == "confirm_required" {
            let response = identity_response(&action, &input, resolution, if action == "conflict" { 409 } else { 200 });
            if !input.request_id.is_empty() { registry.requests.insert(input.request_id.clone(), response.clone()); }
            self.identities.insert(registry_key, registry);
            return Ok(response);
        }
        let canonical = resolution.get("canonicalInterfaceKey").and_then(Value::as_str).unwrap_or(&input.interface_key).to_string();
        let now = now_ms();
        {
            let identity = registry.identities.entry(canonical.clone()).or_insert_with(|| IdentityEntry { canonical_interface_key: canonical.clone(), created_at: now, ..Default::default() });
            add_identity_keys(identity, &input);
        }
        if (action == "adopt" || action == "merge") && input.interface_key != canonical { registry.aliases.insert(input.interface_key.clone(), IdentityAlias { canonical_interface_key: canonical.clone(), kind: "interface".to_string() }); }
        let keys = registry.identities.get(&canonical).map(|identity| identity.strict_address_keys.iter().chain(identity.endpoint_match_keys.iter()).chain(identity.host_match_keys.iter()).chain(identity.legacy_config_keys.iter()).cloned().collect::<Vec<_>>()).unwrap_or_default();
        for key in keys { registry.aliases.entry(key).or_insert_with(|| IdentityAlias { canonical_interface_key: canonical.clone(), kind: "alias".to_string() }); }
        registry.epoch += 1; registry.updated_at = now;
        let migrated = self.migrate_identity_spaces(token, &input.config_type, &canonical, [&input.legacy_config_keys[..], std::slice::from_ref(&input.interface_key)].concat());
        let final_action = if migrated { "migration_pending" } else { action.as_str() };
        let response = identity_response(final_action, &input, json!({"canonicalInterfaceKey": canonical, "identityEpoch": registry.epoch.to_string(), "migrationRequired": migrated, "migrationDone": migrated, "resetSince": migrated, "nextSince": if migrated { "0" } else { "" }, "sourceInterfaceKey": input.interface_key}), 200);
        if !input.request_id.is_empty() { registry.requests.insert(input.request_id.clone(), response.clone()); }
        self.identities.insert(registry_key, registry.clone());
        self.save().map_err(|_| AppError::new(StatusCode::SERVICE_UNAVAILABLE, "Playback identity registry write failed"))?;
        Ok(response)
    }

    fn migrate_identity_spaces(&mut self, token: &str, config_type: &str, canonical: &str, sources: Vec<String>) -> bool {
        let canonical_key = playback_space_key_type(token, config_type, canonical);
        let mut target = self.spaces.remove(&canonical_key).unwrap_or_default();
        let mut migrated = false;
        for source in sources.into_iter().filter(|key| !key.is_empty() && key != canonical) {
            let source_key = playback_space_key_type(token, config_type, &source);
            let Some(source_space) = self.spaces.get(&source_key).cloned() else { continue };
            for (key, item) in source_space.items { if target.items.get(&key).map(|old| old.updated_at >= item.updated_at).unwrap_or(false) { continue; } let mut item = item; target.sequence += 1; item.seq = target.sequence; target.items.insert(key, item); migrated = true; }
            for (key, tombstone) in source_space.tombstones { if target.tombstones.get(&key).map(|old| old.deleted_at >= tombstone.deleted_at).unwrap_or(false) { continue; } let mut tombstone = tombstone; target.sequence += 1; tombstone.seq = target.sequence; target.tombstones.insert(key, tombstone); migrated = true; }
            for (key, value) in source_space.events { target.events.entry(key).or_insert(value); }
        }
        if migrated { self.spaces.insert(canonical_key, target); }
        migrated
    }

    fn ensure_available(&self) -> Result<(), AppError> {
        if self.available() {
            Ok(())
        } else {
            Err(AppError::new(
                StatusCode::SERVICE_UNAVAILABLE,
                "Playback persistent storage is unavailable",
            ))
        }
    }

    fn ingest(&mut self, token: &str, config_key: &str, body: &Value, fallback_event_id: &str, now: i64) -> Result<Value, AppError> {
        self.ingest_type(token, "vod", config_key, body, fallback_event_id, now)
    }

    fn pull(&self, token: &str, config_key: &str, since: i64, limit: usize) -> Value {
        self.pull_type(token, "vod", config_key, since, limit)
    }

    fn status(&self, token: &str, config_key: &str, endpoint: String) -> Value {
        self.status_type(token, "vod", config_key, endpoint)
    }

    fn ingest_type(
        &mut self,
        token: &str,
        config_type: &str,
        config_key: &str,
        body: &Value,
        fallback_event_id: &str,
        now: i64,
    ) -> Result<Value, AppError> {
        self.ensure_available()?;
        let raw_events = extract_playback_events(body);
        if raw_events.is_empty() {
            return Err(AppError::new(
                StatusCode::BAD_REQUEST,
                "Playback event is empty",
            ));
        }
        if raw_events.len() > MAX_BATCH_ITEMS {
            return Err(AppError::new(
                StatusCode::PAYLOAD_TOO_LARGE,
                format!("Too many playback events; maximum is {MAX_BATCH_ITEMS}"),
            ));
        }
        let events = raw_events
            .iter()
            .map(|raw| normalize_playback_event(raw, config_key, now, fallback_event_id))
            .collect::<Result<Vec<_>, _>>()?;

        let space_key = playback_space_key_type(token, config_type, config_key);
        let previous = self.spaces.get(&space_key).cloned();
        let mut working = previous.clone().unwrap_or_default();
        cleanup_playback_space(&mut working, now);
        let results = events
            .iter()
            .map(|event| {
                if event.kind == "delete" {
                    apply_delete(&mut working, event, now)
                } else {
                    apply_upsert(&mut working, event, now)
                }
            })
            .collect::<Vec<_>>();

        self.spaces.insert(space_key.clone(), working);
        if let Err(error) = self.save() {
            tracing::error!(error = %error, "Playback persistent storage write failed");
            if let Some(previous) = previous {
                self.spaces.insert(space_key, previous);
            } else {
                self.spaces.remove(&space_key);
            }
            return Err(AppError::new(
                StatusCode::SERVICE_UNAVAILABLE,
                "Playback persistent storage write failed",
            ));
        }

        let applied = results
            .iter()
            .filter(|result| {
                matches!(
                    result.get("action").and_then(Value::as_str),
                    Some("created" | "updated" | "deleted")
                )
            })
            .count();
        let skipped = results
            .iter()
            .filter(|result| {
                matches!(
                    result.get("action").and_then(Value::as_str),
                    Some("skipped" | "duplicate")
                )
            })
            .count();
        Ok(json!({
            "ok": true,
            "received": results.len(),
            "applied": applied,
            "skipped": skipped,
            "results": results
        }))
    }

    fn pull_type(&self, token: &str, config_type: &str, config_key: &str, since: i64, limit: usize) -> Value {
        let empty = PlaybackSpace::default();
        let space = self
            .spaces
            .get(&playback_space_key_type(token, config_type, config_key))
            .unwrap_or(&empty);
        let cutoff = now_ms() - RETENTION_MS;
        let mut changes = space
            .items
            .values()
            .filter(|item| item.seq > since)
            .map(|item| (item.seq, item.payload.clone()))
            .chain(
                space
                    .tombstones
                    .values()
                    .filter(|item| item.deleted_at >= cutoff && item.seq > since)
                    .map(|item| (item.seq, item.payload.clone())),
            )
            .collect::<Vec<_>>();
        changes.sort_by_key(|(sequence, _)| *sequence);
        let has_more = changes.len() > limit;
        changes.truncate(limit);
        let next_since = changes
            .last()
            .map(|(sequence, _)| *sequence)
            .unwrap_or(since);
        json!({
            "changes": changes.into_iter().map(|(_, payload)| payload).collect::<Vec<_>>(),
            "nextSince": next_since.to_string(),
            "hasMore": has_more
        })
    }

    fn status_type(&self, token: &str, config_type: &str, config_key: &str, endpoint: String) -> Value {
        let empty = PlaybackSpace::default();
        let space = self
            .spaces
            .get(&playback_space_key_type(token, config_type, config_key))
            .unwrap_or(&empty);
        let cutoff = now_ms() - RETENTION_MS;
        let tombstones = space
            .tombstones
            .values()
            .filter(|item| item.deleted_at >= cutoff)
            .collect::<Vec<_>>();
        let latest_item = space.items.values().map(|item| item.seq).max().unwrap_or(0);
        let latest_tombstone = tombstones.iter().map(|item| item.seq).max().unwrap_or(0);
        json!({
            "ok": true,
            "configKey": config_key,
            "identityProtocol": "webhtv.playback.identity.v1",
            "addressMatchVersion": 1,
            "capabilities": {"playbackSync": true, "identityResolve": true, "identityAliases": true, "legacyUrlHashMigration": true, "identityMerge": true},
            "items": space.items.len(),
            "tombstones": tombstones.len(),
            "nextSince": latest_item.max(latest_tombstone).to_string(),
            "retentionDays": 90,
            "endpoint": endpoint
        })
    }

    fn load(&mut self) -> Result<(), String> {
        let Some(path) = &self.path else {
            return Ok(());
        };
        let data = match fs::read(path) {
            Ok(data) => data,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
            Err(error) => return Err(error.to_string()),
        };
        let disk = serde_json::from_slice::<PlaybackDiskState>(&data)
            .map_err(|error| error.to_string())?;
        if disk.version != STORAGE_VERSION {
            return Err("Unsupported playback storage version".to_string());
        }
        self.spaces = disk.spaces;
        self.identities = disk.identities;
        Ok(())
    }

    fn save(&self) -> Result<(), String> {
        let Some(path) = &self.path else {
            return Ok(());
        };
        let disk = PlaybackDiskState {
            version: STORAGE_VERSION,
            spaces: self.spaces.clone(),
            identities: self.identities.clone(),
        };
        let data = serde_json::to_vec(&disk).map_err(|error| error.to_string())?;
        let parent = path.parent().filter(|value| !value.as_os_str().is_empty());
        if let Some(parent) = parent {
            fs::create_dir_all(parent).map_err(|error| error.to_string())?;
        }
        let directory = parent.unwrap_or_else(|| Path::new("."));
        let temp_path = directory.join(format!(
            ".webhtv-playback-{}-{}.tmp",
            std::process::id(),
            now_ms()
        ));
        let mut options = OpenOptions::new();
        options.write(true).create_new(true);
        #[cfg(unix)]
        options.mode(0o600);
        let mut file = options
            .open(&temp_path)
            .map_err(|error| error.to_string())?;
        let result = (|| {
            file.write_all(&data).map_err(|error| error.to_string())?;
            file.sync_all().map_err(|error| error.to_string())?;
            drop(file);
            fs::rename(&temp_path, path).map_err(|error| error.to_string())
        })();
        if result.is_err() {
            let _ = fs::remove_file(&temp_path);
        }
        result
    }
}

pub fn playback_available() -> bool {
    PLAYBACK_AVAILABLE.load(Ordering::Relaxed)
}

pub fn playback_persistent() -> bool {
    PLAYBACK_PERSISTENT.load(Ordering::Relaxed)
}


pub async fn post_identity_resolve(
    State(state): State<SharedState>,
    headers: HeaderMap,
    body: Bytes,
) -> AppResult {
    if body.len() > MAX_BODY_BYTES {
        return Err(AppError::new(StatusCode::PAYLOAD_TOO_LARGE, "Identity payload is too large"));
    }
    let payload = serde_json::from_slice::<Value>(&body)
        .map_err(|_| AppError::new(StatusCode::BAD_REQUEST, "Invalid JSON body"))?;
    let token = require_playback_token(&headers)?;
    let mut service = state.playback.lock().await;
    let response = service.resolve_identity(&token, &headers, &payload)?;
    let status = response.get("status").and_then(Value::as_u64).unwrap_or(200) as u16;
    let body = response.get("body").cloned().unwrap_or_else(|| json!({"ok": false}));
    Ok(json_response(StatusCode::from_u16(status).unwrap_or(StatusCode::OK), body))
}

pub async fn post_sync(
    State(state): State<SharedState>,
    headers: HeaderMap,
    body: Bytes,
) -> AppResult {
    if body.len() > MAX_BODY_BYTES {
        return Err(AppError::new(
            StatusCode::PAYLOAD_TOO_LARGE,
            "Playback payload is too large",
        ));
    }
    if body.iter().all(u8::is_ascii_whitespace) {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "Playback payload is empty",
        ));
    }
    let payload = serde_json::from_slice::<Value>(&body)
        .map_err(|_| AppError::new(StatusCode::BAD_REQUEST, "Invalid JSON body"))?;
    let token = require_playback_token(&headers)?;
    let submitted_config_key = require_config_key(&headers, Some(&payload))?;
    let config_type = normalize_config_type(&first_non_empty(&[
        header_string(&headers, "x-webhtv-config-type"),
        payload.get("configType").map(value_string).unwrap_or_default(),
    ]));
    let fallback_event_id = first_header(&headers, &["x-webhtv-webhook-id", "idempotency-key"]);
    let mut service = state.playback.lock().await;
    let config_key = service.resolve_config_key(&token, &config_type, &submitted_config_key, request_aliases(&headers, &payload))?;
    let response = service.ingest_type(
        &token,
        &config_type,
        &config_key,
        &payload,
        &clean_string(&fallback_event_id, 160),
        now_ms(),
    )?;
    Ok(json_response(StatusCode::OK, response))
}

pub async fn get_sync(
    State(state): State<SharedState>,
    headers: HeaderMap,
    OriginalUri(uri): OriginalUri,
) -> AppResult {
    let token = require_playback_token(&headers)?;
    let submitted_config_key = require_config_key(&headers, None)?;
    let config_type = normalize_config_type(&first_non_empty(&[
        header_string(&headers, "x-webhtv-config-type"),
        query_param(&uri, "configType"),
    ]));
    let service = state.playback.lock().await;
    let config_key = service.resolve_config_key(&token, &config_type, &submitted_config_key, request_aliases(&headers, &Value::Null))?;
    let since = parse_cursor(&first_non_empty(&[
        header_string(&headers, "x-webhtv-since"),
        query_param(&uri, "since"),
    ]))?;
    let limit = parse_limit(&first_non_empty(&[
        header_string(&headers, "x-webhtv-limit"),
        query_param(&uri, "limit"),
    ]));
    service.ensure_available()?;
    Ok(json_response(
        StatusCode::OK,
        service.pull_type(&token, &config_type, &config_key, since, limit),
    ))
}

pub async fn get_status(
    State(state): State<SharedState>,
    headers: HeaderMap,
    OriginalUri(uri): OriginalUri,
) -> AppResult {
    let token = require_playback_token(&headers)?;
    let submitted_config_key = require_config_key(&headers, None)?;
    let config_type = normalize_config_type(&first_non_empty(&[
        header_string(&headers, "x-webhtv-config-type"),
        query_param(&uri, "configType"),
    ]));
    let service = state.playback.lock().await;
    let config_key = service.resolve_config_key(&token, &config_type, &submitted_config_key, request_aliases(&headers, &Value::Null))?;
    let base = if uri.path().starts_with("/playback/") {
        "/playback/sync"
    } else {
        "/api/playback/sync"
    };
    let endpoint = format!("{}{}", server_origin(&headers), base);
    service.ensure_available()?;
    Ok(json_response(
        StatusCode::OK,
        service.status_type(&token, &config_type, &config_key, endpoint),
    ))
}

fn apply_upsert(space: &mut PlaybackSpace, event: &PlaybackEvent, received_at: i64) -> Value {
    if has_event(space, &event.event_id) {
        return result_for(event, "duplicate", 0, "Event already processed");
    }
    let current = space.items.get(&event.item_key).cloned();
    let newest_deletion = space
        .tombstones
        .values()
        .filter(|tombstone| matches_tombstone(tombstone, event))
        .max_by_key(|tombstone| tombstone.deleted_at)
        .cloned();
    if let Some(tombstone) = newest_deletion {
        if event.updated_at <= tombstone.deleted_at {
            record_event(space, &event.event_id, received_at);
            return result_for(event, "skipped", tombstone.seq, "A newer deletion exists");
        }
    }
    if let Some(item) = &current {
        if event.updated_at <= item.updated_at {
            record_event(space, &event.event_id, received_at);
            return result_for(event, "skipped", item.seq, "A newer progress record exists");
        }
    }
    let sequence = next_sequence(space);
    space.items.insert(
        event.item_key.clone(),
        PlaybackItem {
            history_key: event.history_key.clone(),
            site_key: event.site_key.clone(),
            vod_id: event.vod_id.clone(),
            updated_at: event.updated_at,
            seq: sequence,
            payload: event.payload.clone(),
        },
    );
    record_event(space, &event.event_id, received_at);
    result_for(
        event,
        if current.is_some() {
            "updated"
        } else {
            "created"
        },
        sequence,
        "",
    )
}

fn apply_delete(space: &mut PlaybackSpace, event: &PlaybackEvent, received_at: i64) -> Value {
    if has_event(space, &event.event_id) {
        return result_for(event, "duplicate", 0, "Event already processed");
    }
    if let Some(current) = space.tombstones.get(&event.marker_key) {
        if event.deleted_at <= current.deleted_at {
            let sequence = current.seq;
            record_event(space, &event.event_id, received_at);
            return result_for(event, "skipped", sequence, "A newer deletion exists");
        }
    }
    let sequence = next_sequence(space);
    space.tombstones.insert(
        event.marker_key.clone(),
        PlaybackTombstone {
            scope: event.scope.clone(),
            history_key: event.history_key.clone(),
            site_key: event.site_key.clone(),
            vod_id: event.vod_id.clone(),
            deleted_at: event.deleted_at,
            seq: sequence,
            payload: event.payload.clone(),
        },
    );
    let before = space.items.len();
    space.items.retain(|key, item| {
        item.updated_at > event.deleted_at || !matches_delete(event, item, key)
    });
    let affected = before - space.items.len();
    record_event(space, &event.event_id, received_at);
    let mut result = result_for(event, "deleted", sequence, "");
    result["affected"] = json!(affected);
    result
}

fn normalize_playback_event(
    input: &Value,
    config_key: &str,
    now: i64,
    fallback_event_id: &str,
) -> Result<PlaybackEvent, AppError> {
    let raw = unwrap_playback_event(input);
    let object = raw
        .as_object()
        .ok_or_else(|| AppError::new(StatusCode::BAD_REQUEST, "Invalid playback event"))?;
    let body_config_key = normalize_config_key(&first_string(object, &["configKey", "config_key"]));
    if body_config_key.chars().count() > 256 {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "configKey is too long",
        ));
    }
    if !body_config_key.is_empty() && body_config_key != config_key {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "configKey does not match X-WebHTV-Config-Key",
        ));
    }
    let event_name = clean_string(&first_string(object, &["event"]), 80).to_lowercase();
    let action =
        clean_string(&first_string(object, &["action", "op", "operation"]), 32).to_lowercase();
    let deletion = bool_value(object.get("deleted"))
        || event_name == "playback.deleted"
        || matches!(action.as_str(), "delete" | "deleted" | "remove" | "removed");
    let event_id = clean_string(
        &first_non_empty(&[
            first_string(object, &["eventId", "event_id"]),
            fallback_event_id.to_string(),
        ]),
        160,
    );
    let mut history_key = clean_string(&first_string(object, &["historyKey", "key"]), 4096);
    let parts = history_parts(&history_key);
    let mut site_key = clean_string(
        &first_non_empty(&[
            first_string(object, &["siteKey", "site", "site_key"]),
            parts.0,
        ]),
        1024,
    );
    let mut vod_id = clean_string(
        &first_non_empty(&[
            first_string(object, &["vodId", "vod_id", "videoId", "itemId"]),
            parts.1,
        ]),
        8192,
    );

    if deletion {
        let requested_scope = clean_string(&first_string(object, &["scope"]), 16).to_lowercase();
        if !requested_scope.is_empty()
            && !matches!(requested_scope.as_str(), "all" | "site" | "item")
        {
            return Err(AppError::new(
                StatusCode::BAD_REQUEST,
                "scope must be item, site, or all",
            ));
        }
        let scope = normalize_scope(&requested_scope, &history_key, &site_key, &vod_id);
        if scope.is_empty() {
            return Err(AppError::new(
                StatusCode::BAD_REQUEST,
                "scope=all must be explicit when no item or site identity is provided",
            ));
        }
        if scope == "site" && site_key.is_empty() {
            return Err(AppError::new(
                StatusCode::BAD_REQUEST,
                "siteKey is required for a site deletion",
            ));
        }
        if scope == "item" && history_key.is_empty() && (site_key.is_empty() || vod_id.is_empty()) {
            return Err(AppError::new(
                StatusCode::BAD_REQUEST,
                "historyKey or siteKey + vodId is required for an item deletion",
            ));
        }
        if scope == "all" {
            history_key.clear();
            site_key.clear();
            vod_id.clear();
        } else if scope == "site" {
            history_key.clear();
            vod_id.clear();
        }
        let deleted_at = positive_timestamp(
            first_value(
                object,
                &["deletedAt", "deleted_at", "timestamp", "updatedAt"],
            ),
            0,
        );
        if deleted_at <= 0 {
            return Err(AppError::new(
                StatusCode::BAD_REQUEST,
                "deletedAt or timestamp is required for a deletion",
            ));
        }
        let item_key = playback_item_key(&history_key, &site_key, &vod_id);
        let marker_key = playback_marker_key(&scope, &item_key, &site_key);
        let mut payload = Map::new();
        insert_string(&mut payload, "schema", PLAYBACK_SCHEMA);
        insert_string(&mut payload, "action", "delete");
        insert_string(&mut payload, "event", "playback.deleted");
        insert_string(&mut payload, "eventId", &event_id);
        insert_string(&mut payload, "configKey", config_key);
        insert_string(&mut payload, "historyKey", &history_key);
        insert_string(&mut payload, "siteKey", &site_key);
        insert_string(&mut payload, "vodId", &vod_id);
        insert_string(&mut payload, "scope", &scope);
        payload.insert("deletedAt".to_string(), json!(deleted_at));
        return Ok(PlaybackEvent {
            kind: "delete",
            config_key: config_key.to_string(),
            event_id,
            history_key,
            site_key,
            vod_id,
            scope,
            updated_at: 0,
            deleted_at,
            item_key,
            marker_key,
            payload: Value::Object(payload),
        });
    }

    if site_key.is_empty() {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "siteKey is required",
        ));
    }
    if vod_id.is_empty() {
        return Err(AppError::new(StatusCode::BAD_REQUEST, "vodId is required"));
    }
    let vod_name = clean_string(
        &first_string(object, &["vodName", "vod_name", "name", "title"]),
        2048,
    );
    let episode_name = clean_string(
        &first_string(
            object,
            &[
                "episodeName",
                "episode",
                "episodeTitle",
                "vodRemarks",
                "remarks",
            ],
        ),
        2048,
    );
    let position = positive_number(first_value(
        object,
        &["positionMs", "position", "position_ms", "pos"],
    ));
    let duration = positive_number(first_value(
        object,
        &["durationMs", "duration", "duration_ms"],
    ));
    if vod_name.is_empty() {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "vodName is required",
        ));
    }
    if episode_name.is_empty() {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "episodeName is required",
        ));
    }
    if position <= 0.0 {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "positionMs must be greater than 0",
        ));
    }
    if duration <= 0.0 {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "durationMs must be greater than 0",
        ));
    }
    let updated_at = positive_timestamp(
        first_value(
            object,
            &["updatedAt", "updated_at", "timestamp", "updateTime"],
        ),
        now,
    );
    let completed = event_name == "playback.ended" || bool_value(object.get("completed"));
    let clamped_position = position.min(duration);
    let supplied_progress = bounded_number(object.get("progress"), 0.0, 1.0);
    let progress = if supplied_progress > 0.0 {
        supplied_progress
    } else {
        clamped_position / duration
    };
    let mut payload = Map::new();
    insert_string(&mut payload, "schema", PLAYBACK_SCHEMA);
    insert_string(&mut payload, "action", "upsert");
    insert_string(&mut payload, "event", &event_name);
    insert_string(&mut payload, "eventId", &event_id);
    insert_string(&mut payload, "configKey", config_key);
    insert_string(
        &mut payload,
        "configName",
        &clean_string(&first_string(object, &["configName", "config_name"]), 2048),
    );
    insert_string(&mut payload, "historyKey", &history_key);
    insert_string(&mut payload, "siteKey", &site_key);
    insert_string(
        &mut payload,
        "siteName",
        &clean_string(&first_string(object, &["siteName", "site_name"]), 2048),
    );
    insert_string(&mut payload, "vodId", &vod_id);
    insert_string(&mut payload, "vodName", &vod_name);
    insert_string(
        &mut payload,
        "vodPic",
        &clean_string(
            &first_string(object, &["vodPic", "vod_pic", "pic", "poster"]),
            8192,
        ),
    );
    insert_string(
        &mut payload,
        "flag",
        &clean_string(
            &first_string(object, &["flag", "vodFlag", "line", "source"]),
            2048,
        ),
    );
    insert_string(&mut payload, "episodeName", &episode_name);
    insert_string(
        &mut payload,
        "episodeUrl",
        &clean_string(
            &first_string(object, &["episodeUrl", "episode_url", "url", "playUrl"]),
            8192,
        ),
    );
    payload.insert("positionMs".to_string(), json!(clamped_position));
    payload.insert("durationMs".to_string(), json!(duration));
    payload.insert("progress".to_string(), json!(progress));
    let supplied_speed = positive_number(object.get("speed"));
    payload.insert(
        "speed".to_string(),
        json!(if supplied_speed > 0.0 {
            supplied_speed
        } else {
            1.0
        }),
    );
    payload.insert("completed".to_string(), json!(completed));
    payload.insert("updatedAt".to_string(), json!(updated_at));
    insert_string(
        &mut payload,
        "clientKey",
        &clean_string(&first_string(object, &["clientKey", "client_key"]), 256),
    );
    Ok(PlaybackEvent {
        kind: "upsert",
        config_key: config_key.to_string(),
        event_id,
        history_key: history_key.clone(),
        site_key: site_key.clone(),
        vod_id: vod_id.clone(),
        scope: String::new(),
        updated_at,
        deleted_at: 0,
        item_key: playback_item_key(&history_key, &site_key, &vod_id),
        marker_key: String::new(),
        payload: Value::Object(payload),
    })
}

fn result_for(event: &PlaybackEvent, action: &str, sequence: i64, message: &str) -> Value {
    let mut result = Map::new();
    insert_string(&mut result, "action", action);
    result.insert("sequence".to_string(), json!(sequence));
    insert_string(&mut result, "message", message);
    insert_string(&mut result, "eventId", &event.event_id);
    insert_string(&mut result, "configKey", &event.config_key);
    insert_string(&mut result, "historyKey", &event.history_key);
    insert_string(&mut result, "siteKey", &event.site_key);
    insert_string(&mut result, "vodId", &event.vod_id);
    if event.updated_at != 0 {
        result.insert("updatedAt".to_string(), json!(event.updated_at));
    }
    if event.deleted_at != 0 {
        result.insert("deletedAt".to_string(), json!(event.deleted_at));
    }
    Value::Object(result)
}

fn extract_playback_events(body: &Value) -> Vec<Value> {
    if let Some(array) = body.as_array() {
        return array.clone();
    }
    let Some(object) = body.as_object() else {
        return Vec::new();
    };
    if let Some(array) = first_array(object, &["changes", "operations"]) {
        return array
            .iter()
            .map(|item| inherit_playback_fields(item, object))
            .collect();
    }
    let mut result = Vec::new();
    if let Some(array) = first_array(
        object,
        &[
            "deleted",
            "deletions",
            "tombstones",
            "removed",
            "deletedItems",
        ],
    ) {
        for item in array {
            let mut value = if let Some(text) = item.as_str() {
                let mut value = Map::new();
                value.insert("historyKey".to_string(), json!(text));
                value
            } else {
                item.as_object().cloned().unwrap_or_default()
            };
            value.insert("action".to_string(), json!("delete"));
            result.push(inherit_playback_fields(&Value::Object(value), object));
        }
    }
    if let Some(array) = first_array(object, &["items", "records", "upserts", "list"]) {
        result.extend(
            array
                .iter()
                .map(|item| inherit_playback_fields(item, object)),
        );
    }
    if !result.is_empty() {
        return result;
    }
    if let Some(array) = object.get("data").and_then(Value::as_array) {
        return array
            .iter()
            .map(|item| inherit_playback_fields(item, object))
            .collect();
    }
    vec![body.clone()]
}

fn unwrap_playback_event(input: &Value) -> Value {
    let Some(object) = input.as_object() else {
        return input.clone();
    };
    if let Some(data) = object.get("data").and_then(Value::as_object) {
        inherit_playback_fields(&Value::Object(data.clone()), object)
    } else {
        input.clone()
    }
}

fn inherit_playback_fields(input: &Value, parent: &JsonMap) -> Value {
    let Some(input) = input.as_object() else {
        return input.clone();
    };
    let mut output = input.clone();
    for key in [
        "action",
        "op",
        "operation",
        "event",
        "eventId",
        "deleted",
        "scope",
        "deletedAt",
        "timestamp",
        "updatedAt",
        "configKey",
        "configName",
    ] {
        if output.get(key).is_some_and(|value| !value.is_null()) {
            continue;
        }
        if let Some(value) = parent
            .get(key)
            .filter(|value| !value.is_object() && !value.is_array())
        {
            output.insert(key.to_string(), value.clone());
        }
    }
    Value::Object(output)
}

fn cleanup_playback_space(space: &mut PlaybackSpace, now: i64) {
    if now - space.last_cleanup < CLEANUP_INTERVAL_MS {
        return;
    }
    let cutoff = now - RETENTION_MS;
    space
        .tombstones
        .retain(|_, tombstone| tombstone.deleted_at >= cutoff);
    space.events.retain(|_, received_at| *received_at >= cutoff);
    space.last_cleanup = now;
}

fn matches_tombstone(tombstone: &PlaybackTombstone, event: &PlaybackEvent) -> bool {
    match tombstone.scope.as_str() {
        "all" => true,
        "site" => tombstone.site_key == event.site_key,
        _ => {
            (!tombstone.site_key.is_empty()
                && !tombstone.vod_id.is_empty()
                && tombstone.site_key == event.site_key
                && tombstone.vod_id == event.vod_id)
                || (!tombstone.history_key.is_empty() && tombstone.history_key == event.history_key)
        }
    }
}

fn matches_delete(event: &PlaybackEvent, item: &PlaybackItem, item_key: &str) -> bool {
    match event.scope.as_str() {
        "all" => true,
        "site" => item.site_key == event.site_key,
        _ => {
            item_key == event.item_key
                || (!event.history_key.is_empty() && item.history_key == event.history_key)
        }
    }
}

fn next_sequence(space: &mut PlaybackSpace) -> i64 {
    space.sequence += 1;
    space.sequence
}

fn has_event(space: &PlaybackSpace, event_id: &str) -> bool {
    !event_id.is_empty() && space.events.contains_key(&format!("event:{event_id}"))
}

fn record_event(space: &mut PlaybackSpace, event_id: &str, received_at: i64) {
    if !event_id.is_empty() {
        space
            .events
            .insert(format!("event:{event_id}"), received_at);
    }
}


fn parse_identity_input(object: &JsonMap, headers: &HeaderMap) -> Result<IdentityInput, AppError> {
    let mut input = IdentityInput { schema: first_string(object, &["schema"]), operation: first_string(object, &["operation"]).to_lowercase(), config_type: normalize_config_type(&first_string(object, &["configType"])), interface_key: first_string(object, &["interfaceKey"]), strict_address_keys: string_array(object.get("strictAddressKeys")), endpoint_match_keys: string_array(object.get("endpointMatchKeys")), host_match_keys: string_array(object.get("hostMatchKeys")), legacy_config_keys: string_array(object.get("legacyConfigKeys")), source_data_state: first_string(object, &["sourceDataState"]).to_lowercase(), confirm: object.get("confirm").and_then(Value::as_bool).unwrap_or(false), request_id: first_string(object, &["requestId"]) };
    if input.schema.is_empty() { input.schema = "webhtv.playback.identity.v1".to_string(); }
    if input.operation.is_empty() { input.operation = "resolve".to_string(); }
    if input.source_data_state != "empty" && input.source_data_state != "has_data" { input.source_data_state = "unknown".to_string(); }
    if input.interface_key.is_empty() { input.interface_key = header_string(headers, "x-webhtv-config-key"); }
    if input.request_id.is_empty() { input.request_id = header_string(headers, "x-webhtv-request-id"); }
    input.interface_key = validate_identity_key(&input.interface_key, "interfaceKey")?;
    for keys in [&input.strict_address_keys, &input.endpoint_match_keys, &input.host_match_keys, &input.legacy_config_keys] { if keys.len() > 32 { return Err(AppError::new(StatusCode::BAD_REQUEST, "identity key list is too long")); } for key in keys.iter() { validate_identity_key(key, "identity key")?; } }
    Ok(input)
}

fn string_array(value: Option<&Value>) -> Vec<String> { value.and_then(Value::as_array).map(|values| values.iter().map(value_string).filter(|value| !value.is_empty()).collect()).unwrap_or_default() }
fn validate_identity_key(value: &str, label: &str) -> Result<String, AppError> { let value = value.trim().to_lowercase(); if value.is_empty() || value.len() > 128 || !value.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || ".:_-".contains(c)) { return Err(AppError::new(StatusCode::BAD_REQUEST, format!("{label} is invalid"))); } Ok(value) }
fn request_aliases(headers: &HeaderMap, payload: &Value) -> Vec<String> { let mut result = header_string(headers, "x-webhtv-config-aliases").split(',').map(|value| value.trim().to_string()).filter(|value| !value.is_empty()).collect::<Vec<_>>(); if let Some(values) = payload.get("configAliases").and_then(Value::as_array) { result.extend(values.iter().map(value_string).filter(|value| !value.is_empty())); } result.sort(); result.dedup(); result.truncate(16); result }
fn identity_registry_key(token: &str, config_type: &str) -> String { format!("{}:{}", sha256_hex(token), normalize_config_type(config_type)) }
fn identity_response(action: &str, input: &IdentityInput, extra: Value, status: u16) -> Value { let mut body = json!({"ok": status < 400, "schema":"webhtv.playback.identity.v1", "identityProtocol":"webhtv.playback.identity.v1", "addressMatchVersion":1, "action":action, "canonicalInterfaceKey":"", "sourceInterfaceKey":input.interface_key, "matchedBy":"none", "matchedKeys":[], "migrationRequired":false, "migrationDone":false, "resetSince":false, "nextSince":"", "identityEpoch":"", "capabilities":{"playbackSync":true,"identityResolve":true,"identityAliases":true,"legacyUrlHashMigration":true,"identityMerge":true}}); if let (Some(target), Some(source)) = (body.as_object_mut(), extra.as_object()) { for (key, value) in source { target.insert(key.clone(), value.clone()); } } json!({"status": status, "body": body}) }
fn add_identity_keys(identity: &mut IdentityEntry, input: &IdentityInput) { identity.updated_at = now_ms(); for (target, source) in [(&mut identity.strict_address_keys, &input.strict_address_keys), (&mut identity.endpoint_match_keys, &input.endpoint_match_keys), (&mut identity.host_match_keys, &input.host_match_keys), (&mut identity.legacy_config_keys, &input.legacy_config_keys)] { for value in source { if target.len() < 32 && !target.contains(value) { target.push(value.clone()); } } } }
fn choose_identity(registry: &IdentityRegistry, input: &IdentityInput) -> Value { let bound = registry.identities.get(&input.interface_key).map(|_| input.interface_key.clone()).or_else(|| registry.aliases.get(&input.interface_key).map(|alias| alias.canonical_interface_key.clone())); let candidates = |keys: &Vec<String>| -> Vec<String> { let mut output = HashSet::new(); for key in keys { if let Some(alias) = registry.aliases.get(key) { output.insert(alias.canonical_interface_key.clone()); } for (canonical, identity) in &registry.identities { if identity.strict_address_keys.contains(key) || identity.endpoint_match_keys.contains(key) || identity.host_match_keys.contains(key) || identity.legacy_config_keys.contains(key) { output.insert(canonical.clone()); } } } let mut result = output.into_iter().collect::<Vec<_>>(); result.sort(); result }; let strict = candidates(&input.strict_address_keys); let endpoint = candidates(&input.endpoint_match_keys); let strong = if !strict.is_empty() { (strict, "strictAddressKey") } else if !endpoint.is_empty() { (endpoint, "endpointMatchKey") } else { (candidates(&input.legacy_config_keys), "legacyConfigKey") }; if let Some(bound) = bound { return json!({"action":"keep","canonicalInterfaceKey":bound,"matchedBy":"interfaceKey"}); } if strong.0.len() > 1 { return json!({"action":"conflict","matchedBy":strong.1,"candidates":strong.0,"error":"Address clues belong to multiple identities"}); } if strong.0.len() == 1 { if input.source_data_state != "empty" { if input.confirm { return json!({"action":"merge","canonicalInterfaceKey":strong.0[0],"matchedBy":strong.1}); } return json!({"action":"confirm_required","canonicalInterfaceKey":strong.0[0],"matchedBy":strong.1,"candidates":strong.0,"error":"Existing source data requires explicit merge confirmation"}); } return json!({"action":"adopt","canonicalInterfaceKey":strong.0[0],"matchedBy":strong.1}); } let weak = candidates(&input.host_match_keys); if weak.len() == 1 && input.confirm { return json!({"action":"merge","canonicalInterfaceKey":weak[0],"matchedBy":"hostMatchKey"}); } if !weak.is_empty() { return json!({"action":"confirm_required","matchedBy":"hostMatchKey","candidates":weak,"error":"Host-only match requires confirmation"}); } json!({"action":"create","canonicalInterfaceKey":input.interface_key,"matchedBy":"none"}) }

fn require_playback_token(headers: &HeaderMap) -> Result<String, AppError> {
    let direct = header_string(headers, "x-webhtv-token");
    let token = if !direct.is_empty() {
        direct
    } else {
        let authorization = header_string(headers, "authorization");
        if authorization
            .get(..7)
            .is_some_and(|prefix| prefix.eq_ignore_ascii_case("bearer "))
        {
            authorization[7..].trim().to_string()
        } else {
            String::new()
        }
    };
    if token.is_empty() {
        return Err(AppError::new(
            StatusCode::UNAUTHORIZED,
            "Missing X-WebHTV-Token",
        ));
    }
    if token.len() > 512 {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "X-WebHTV-Token is too long",
        ));
    }
    Ok(token)
}

fn require_config_key(headers: &HeaderMap, body: Option<&Value>) -> Result<String, AppError> {
    let header = normalize_config_key(&header_string(headers, "x-webhtv-config-key"));
    let body_key = body
        .and_then(Value::as_object)
        .map(|object| normalize_config_key(&first_string(object, &["configKey", "config_key"])))
        .unwrap_or_default();
    if header.chars().count() > 256 || body_key.chars().count() > 256 {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "configKey is too long",
        ));
    }
    if !header.is_empty() && !body_key.is_empty() && header != body_key {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "configKey does not match X-WebHTV-Config-Key",
        ));
    }
    let config_key = if header.is_empty() { body_key } else { header };
    if config_key.is_empty() {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "Missing X-WebHTV-Config-Key",
        ));
    }
    Ok(config_key)
}

fn parse_cursor(value: &str) -> Result<i64, AppError> {
    let value = value.trim();
    if value.is_empty() {
        return Ok(0);
    }
    value
        .parse::<i64>()
        .ok()
        .filter(|value| *value >= 0)
        .ok_or_else(|| AppError::new(StatusCode::BAD_REQUEST, "Invalid X-WebHTV-Since cursor"))
}

fn parse_limit(value: &str) -> usize {
    value
        .trim()
        .parse::<usize>()
        .ok()
        .filter(|value| *value > 0)
        .unwrap_or(DEFAULT_LIMIT)
        .min(MAX_LIMIT)
}

fn query_param(uri: &Uri, name: &str) -> String {
    uri.query()
        .unwrap_or("")
        .split('&')
        .filter_map(|part| part.split_once('='))
        .find_map(|(key, value)| (key == name).then(|| value.to_string()))
        .unwrap_or_default()
}

fn normalize_scope(scope: &str, history_key: &str, site_key: &str, vod_id: &str) -> String {
    if matches!(scope, "all" | "site" | "item") {
        return scope.to_string();
    }
    if !history_key.is_empty() || (!site_key.is_empty() && !vod_id.is_empty()) {
        "item".to_string()
    } else if !site_key.is_empty() {
        "site".to_string()
    } else {
        String::new()
    }
}

fn playback_item_key(history_key: &str, site_key: &str, vod_id: &str) -> String {
    if !site_key.is_empty() && !vod_id.is_empty() {
        sha256_hex(&format!("site-vod\0{site_key}\0{vod_id}"))
    } else {
        sha256_hex(&format!("history\0{history_key}"))
    }
}

fn playback_marker_key(scope: &str, item_key: &str, site_key: &str) -> String {
    match scope {
        "all" => "all".to_string(),
        "site" => format!("site:{}", sha256_hex(site_key)),
        _ => format!("item:{item_key}"),
    }
}

fn playback_space_key(token: &str, config_key: &str) -> String {
    format!("{}:{}", sha256_hex(token), sha256_hex(config_key))
}

fn playback_space_key_type(token: &str, config_type: &str, config_key: &str) -> String {
    let base = playback_space_key(token, config_key);
    if normalize_config_type(config_type) == "vod" { base } else { format!("{}:{}", base, normalize_config_type(config_type)) }
}

fn normalize_config_type(value: &str) -> String {
    match value.trim().to_lowercase().as_str() { "1" | "live" => "live".to_string(), "2" | "wall" => "wall".to_string(), _ => "vod".to_string() }
}

fn history_parts(history_key: &str) -> (String, String) {
    let mut parts = history_key.split("@@@");
    (
        parts.next().unwrap_or("").to_string(),
        parts.next().unwrap_or("").to_string(),
    )
}

fn first_value<'a>(object: &'a JsonMap, keys: &[&str]) -> Option<&'a Value> {
    keys.iter()
        .find_map(|key| object.get(*key).filter(|value| !value.is_null()))
}

fn first_string(object: &JsonMap, keys: &[&str]) -> String {
    first_value(object, keys)
        .map(value_string)
        .unwrap_or_default()
}

fn first_array<'a>(object: &'a JsonMap, keys: &[&str]) -> Option<&'a Vec<Value>> {
    keys.iter().find_map(|key| object.get(*key)?.as_array())
}

fn first_non_empty(values: &[String]) -> String {
    values
        .iter()
        .map(|value| value.trim())
        .find(|value| !value.is_empty())
        .unwrap_or("")
        .to_string()
}

fn first_header(headers: &HeaderMap, keys: &[&str]) -> String {
    keys.iter()
        .map(|key| header_string(headers, key))
        .find(|value| !value.is_empty())
        .unwrap_or_default()
}

fn value_string(value: &Value) -> String {
    match value {
        Value::String(value) => value.trim().to_string(),
        Value::Number(value) => value.to_string(),
        Value::Bool(value) => value.to_string(),
        Value::Null => String::new(),
        value => value.to_string(),
    }
}

fn clean_string(value: &str, max: usize) -> String {
    value.trim().chars().take(max).collect()
}

fn normalize_config_key(value: &str) -> String {
    value.trim().to_lowercase()
}

fn positive_timestamp(value: Option<&Value>, fallback: i64) -> i64 {
    value
        .and_then(|value| {
            value
                .as_i64()
                .or_else(|| value.as_str()?.trim().parse::<i64>().ok())
        })
        .filter(|value| *value > 0)
        .unwrap_or(fallback)
}

fn positive_number(value: Option<&Value>) -> f64 {
    value
        .and_then(|value| {
            value
                .as_f64()
                .or_else(|| value.as_str()?.trim().parse::<f64>().ok())
        })
        .filter(|value| value.is_finite() && *value > 0.0)
        .unwrap_or(0.0)
}

fn bounded_number(value: Option<&Value>, min: f64, max: f64) -> f64 {
    value
        .and_then(|value| {
            value
                .as_f64()
                .or_else(|| value.as_str()?.trim().parse::<f64>().ok())
        })
        .filter(|value| value.is_finite())
        .map(|value| value.clamp(min, max))
        .unwrap_or(0.0)
}

fn bool_value(value: Option<&Value>) -> bool {
    match value {
        Some(Value::Bool(value)) => *value,
        Some(Value::Number(value)) => value.as_i64().is_some_and(|value| value != 0),
        Some(Value::String(value)) => {
            matches!(value.trim().to_lowercase().as_str(), "true" | "1" | "yes")
        }
        _ => false,
    }
}

fn insert_string(object: &mut JsonMap, key: &str, value: &str) {
    if !value.is_empty() {
        object.insert(key.to_string(), json!(value));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const TOKEN: &str = "test-token-a";
    const CONFIG: &str = "config-a";

    fn progress(event_id: &str, timestamp: i64, position: i64) -> Value {
        json!({
            "event": "playback.progress",
            "eventId": event_id,
            "timestamp": timestamp,
            "historyKey": "site-a@@@vod-1@@@1",
            "siteKey": "site-a",
            "vodId": "vod-1",
            "vodName": "影片 A",
            "episodeName": "第 1 集",
            "positionMs": position,
            "durationMs": 600000
        })
    }

    fn action(response: &Value) -> &str {
        response["results"][0]["action"].as_str().unwrap_or("")
    }

    #[test]
    fn syncs_progress_deletion_and_newer_restore() {
        let now = now_ms();
        let mut service = PlaybackService::new(":memory:".to_string());
        let first = service
            .ingest(
                TOKEN,
                CONFIG,
                &progress("progress-1", now - 6000, 120000),
                "",
                now,
            )
            .unwrap();
        assert_eq!(action(&first), "created");
        let duplicate = service
            .ingest(
                TOKEN,
                CONFIG,
                &progress("progress-1", now - 6000, 120000),
                "",
                now,
            )
            .unwrap();
        assert_eq!(action(&duplicate), "duplicate");

        let deletion = json!({
            "event": "playback.deleted",
            "eventId": "delete-1",
            "scope": "item",
            "historyKey": "site-a@@@vod-1@@@1",
            "siteKey": "site-a",
            "vodId": "vod-1",
            "deletedAt": now - 5000
        });
        let deleted = service.ingest(TOKEN, CONFIG, &deletion, "", now).unwrap();
        assert_eq!(action(&deleted), "deleted");
        assert_eq!(deleted["results"][0]["affected"], 1);

        let stale = service
            .ingest(
                TOKEN,
                CONFIG,
                &progress("progress-stale", now - 5500, 180000),
                "",
                now,
            )
            .unwrap();
        assert_eq!(action(&stale), "skipped");
        let fresh = service
            .ingest(
                TOKEN,
                CONFIG,
                &progress("progress-fresh", now - 4000, 240000),
                "",
                now,
            )
            .unwrap();
        assert_eq!(action(&fresh), "created");

        let pull = service.pull(TOKEN, CONFIG, 0, DEFAULT_LIMIT);
        assert_eq!(pull["changes"].as_array().unwrap().len(), 2);
        assert_eq!(pull["changes"][0]["action"], "delete");
        assert_eq!(pull["changes"][1]["action"], "upsert");
        assert_eq!(pull["nextSince"], "3");
    }

    #[test]
    fn requires_explicit_all_and_isolates_spaces() {
        let now = now_ms();
        let mut service = PlaybackService::new(":memory:".to_string());
        let unsafe_delete = json!({
            "event": "playback.deleted",
            "eventId": "unsafe",
            "deletedAt": now
        });
        let error = service
            .ingest(TOKEN, CONFIG, &unsafe_delete, "", now)
            .unwrap_err();
        assert_eq!(error.status, StatusCode::BAD_REQUEST);

        let all = json!({
            "event": "playback.deleted",
            "eventId": "all-1",
            "scope": "all",
            "deletedAt": now
        });
        assert_eq!(
            action(&service.ingest(TOKEN, CONFIG, &all, "", now).unwrap()),
            "deleted"
        );
        assert!(
            service.pull("other-token", CONFIG, 0, DEFAULT_LIMIT)["changes"]
                .as_array()
                .unwrap()
                .is_empty()
        );
        assert!(
            service.pull(TOKEN, "other-config", 0, DEFAULT_LIMIT)["changes"]
                .as_array()
                .unwrap()
                .is_empty()
        );
    }

    #[test]
    fn validates_batch_before_applying() {
        let now = now_ms();
        let mut service = PlaybackService::new(":memory:".to_string());
        let batch = json!({
            "changes": [
                progress("valid-first", now, 1000),
                { "event": "playback.deleted", "eventId": "invalid-second", "deletedAt": now }
            ]
        });
        assert!(service.ingest(TOKEN, CONFIG, &batch, "", now).is_err());
        assert!(service.pull(TOKEN, CONFIG, 0, DEFAULT_LIMIT)["changes"]
            .as_array()
            .unwrap()
            .is_empty());
    }

    #[test]
    fn persists_across_instances() {
        let now = now_ms();
        let path = env::temp_dir().join(format!(
            "webhtv-playback-rust-test-{}-{now}.json",
            std::process::id()
        ));
        let path_text = path.to_string_lossy().to_string();
        let _ = fs::remove_file(&path);
        let mut first = PlaybackService::new(path_text.clone());
        first
            .ingest(TOKEN, CONFIG, &progress("persisted", now, 120000), "", now)
            .unwrap();
        let second = PlaybackService::new(path_text);
        assert_eq!(
            second.pull(TOKEN, CONFIG, 0, DEFAULT_LIMIT)["changes"]
                .as_array()
                .unwrap()
                .len(),
            1
        );
        let _ = fs::remove_file(path);
    }
}
