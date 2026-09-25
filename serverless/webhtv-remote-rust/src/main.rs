mod playback;

use axum::{
    body::Bytes,
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        DefaultBodyLimit, Path, State,
    },
    http::{header, HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    routing::{get, post},
    Json, Router,
};
use futures_util::{SinkExt, StreamExt};
use rand::{rngs::OsRng, Rng, RngCore};
use serde::{Deserialize, Serialize};
use serde_json::{json, Map, Value};
use sha2::{Digest, Sha256};
use std::{
    collections::{BTreeSet, HashMap, VecDeque},
    env,
    net::SocketAddr,
    sync::Arc,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tokio::sync::{mpsc, Mutex};
use tower_http::{
    cors::{Any, CorsLayer},
    trace::TraceLayer,
};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

use playback::PlaybackService;

const SERVER_MODE: &str = "rust";
const BIND_TTL_MS: i64 = 10 * 60 * 1000;
const COMMAND_TTL_MS: i64 = 60 * 60 * 1000;
const SYNC_TTL_MS: i64 = 2 * 60 * 60 * 1000;
const MAX_SYNC_PART_BYTES: usize = 25 * 1024 * 1024;

type SharedState = Arc<AppState>;
type JsonMap = Map<String, Value>;
type WsSender = mpsc::UnboundedSender<Value>;

struct AppState {
    relay: Mutex<RelayState>,
    playback: Mutex<PlaybackService>,
}

impl Default for AppState {
    fn default() -> Self {
        Self {
            relay: Mutex::new(RelayState::default()),
            playback: Mutex::new(PlaybackService::from_env()),
        }
    }
}

#[derive(Default)]
struct RelayState {
    devices: HashMap<String, Device>,
    bind_codes: HashMap<String, BindCode>,
    group_devices: HashMap<String, BTreeSet<String>>,
    commands: HashMap<String, Command>,
    queues: HashMap<String, VecDeque<String>>,
    syncs: HashMap<String, SyncTask>,
    parts: HashMap<String, SyncPart>,
    sockets: HashMap<String, Vec<WsSender>>,
    last_cleanup_ms: i64,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Device {
    device_id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    group_id: Option<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    group_ids: Vec<String>,
    name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    alias: Option<String>,
    role: String,
    #[serde(rename = "type")]
    kind: Value,
    #[serde(skip_serializing_if = "Option::is_none")]
    app_version: Option<String>,
    #[serde(default)]
    capabilities: Value,
    last_seen: i64,
    created_at: i64,
    updated_at: i64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct PublicDevice {
    device_id: String,
    name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    raw_name: Option<String>,
    role: String,
    #[serde(rename = "type")]
    kind: Value,
    #[serde(skip_serializing_if = "Option::is_none")]
    app_version: Option<String>,
    last_seen: i64,
    online: bool,
    capabilities: Value,
}

#[derive(Clone)]
struct BindCode {
    device_id: String,
    grant_id: String,
    bind_grant_token: String,
    expires_at: i64,
}

#[derive(Clone)]
struct GroupInfo {
    group_id: String,
    group_token: String,
    group_token_hash: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Command {
    id: String,
    group_id: String,
    group_token_hash: String,
    target_device_id: String,
    #[serde(rename = "type")]
    command_type: String,
    payload: Value,
    status: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    result: Option<Value>,
    created_at: i64,
    #[serde(skip_serializing_if = "Option::is_none")]
    delivered_at: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    finished_at: Option<i64>,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SyncTask {
    id: String,
    group_id: String,
    group_token_hash: String,
    source_device_id: String,
    target_device_id: String,
    options: Value,
    status: String,
    parts: JsonMap,
    #[serde(skip_serializing_if = "Option::is_none")]
    export_command_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    restore_command_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    export_result: Option<Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    restore_result: Option<Value>,
    created_at: i64,
    updated_at: i64,
}

struct SyncPart {
    bytes: Vec<u8>,
    content_type: String,
    size: usize,
}

#[derive(Debug)]
struct AppError {
    status: StatusCode,
    message: String,
}

impl AppError {
    fn new(status: StatusCode, message: impl Into<String>) -> Self {
        Self {
            status,
            message: message.into(),
        }
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        json_response(self.status, json!({ "ok": false, "error": self.message }))
    }
}

type AppResult = Result<Response, AppError>;

#[tokio::main]
async fn main() {
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "info,tower_http=warn".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    let state = Arc::new(AppState::default());
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any)
        .max_age(Duration::from_secs(86400));

    let app = Router::new()
        .route("/api/health", get(health))
        .route("/api/server/capabilities", get(server_capabilities))
        .route(
            "/api/playback/sync",
            get(playback::get_sync).post(playback::post_sync),
        )
        .route(
            "/playback/sync",
            get(playback::get_sync).post(playback::post_sync),
        )
        .route("/api/playback/sync/status", get(playback::get_status))
        .route("/playback/sync/status", get(playback::get_status))
        .route("/api/playback/identity/resolve", post(playback::post_identity_resolve))
        .route("/playback/identity/resolve", post(playback::post_identity_resolve))
        .route("/api/device/register", post(register_device))
        .route("/api/device/bind-code", post(create_bind_code))
        .route("/api/device/poll", post(poll_device))
        .route("/api/device/ws", get(websocket_handler))
        .route("/api/groups/claim", post(claim_device))
        .route("/api/family/claim", post(claim_device))
        .route("/api/devices", get(list_devices))
        .route("/api/commands", post(create_command))
        .route("/api/commands/:command_id", get(get_command))
        .route("/api/commands/:command_id/result", post(command_result))
        .route("/api/sync/create", post(create_sync))
        .route("/api/sync/:sync_id", get(get_sync))
        .route(
            "/api/sync/:sync_id/part/:part",
            post(upload_sync_part).get(download_sync_part),
        )
        .route("/api/sync/:sync_id/export-complete", post(export_complete))
        .route(
            "/api/sync/:sync_id/restore-complete",
            post(restore_complete),
        )
        .fallback(not_found)
        .layer(DefaultBodyLimit::max(MAX_SYNC_PART_BYTES + 1024 * 1024))
        .layer(cors)
        .layer(TraceLayer::new_for_http())
        .with_state(state);

    let addr = env::var("WEBHTV_REMOTE_ADDR").unwrap_or_else(|_| "0.0.0.0:8787".to_string());
    let addr: SocketAddr = addr.parse().expect("WEBHTV_REMOTE_ADDR must be host:port");
    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .expect("bind listener");
    tracing::info!("WebHTV Rust remote relay listening on {}", addr);
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
        .expect("serve relay");
}

async fn shutdown_signal() {
    let _ = tokio::signal::ctrl_c().await;
}

async fn health() -> AppResult {
    Ok(json_response(
        StatusCode::OK,
        json!({ "ok": true, "time": now_ms() }),
    ))
}

async fn server_capabilities() -> AppResult {
    Ok(json_response(StatusCode::OK, capabilities()))
}

async fn register_device(
    State(state): State<SharedState>,
    headers: HeaderMap,
    body: Bytes,
) -> AppResult {
    cleanup(&state).await;
    let body = read_json(body)?;
    let (device, token) = register_device_internal(&state, &headers, &body).await?;
    Ok(json_response(
        StatusCode::OK,
        json!({
            "ok": true,
            "deviceId": device.device_id,
            "deviceToken": token,
            "deviceSecret": token,
            "groupIds": device.group_ids,
            "server": capabilities()
        }),
    ))
}

async fn create_bind_code(
    State(state): State<SharedState>,
    headers: HeaderMap,
    body: Bytes,
) -> AppResult {
    cleanup(&state).await;
    let body = read_json(body)?;
    let device = require_device(&state, &headers, Some(&body)).await?;
    let origin = server_origin(&headers);
    let bind_grant_token = string_value(body.get("bindGrantToken")).trim().to_string();
    if bind_grant_token.is_empty() {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "Missing bindGrantToken",
        ));
    }
    let grant_id = require_derived_id(
        "bnd",
        &origin,
        &bind_grant_token,
        &string_value(body.get("grantId")),
        "Invalid bind grant token",
    )?;
    let mut code = String::new();
    let expires_at = now_ms() + BIND_TTL_MS;
    {
        let mut relay = state.relay.lock().await;
        for _ in 0..8 {
            let candidate = rand::thread_rng().gen_range(100_000..1_000_000).to_string();
            code = candidate;
            if !relay.bind_codes.contains_key(&code) {
                break;
            }
        }
        relay.bind_codes.insert(
            code.clone(),
            BindCode {
                device_id: device.device_id,
                grant_id: grant_id.clone(),
                bind_grant_token,
                expires_at,
            },
        );
    }
    Ok(json_response(
        StatusCode::OK,
        json!({
            "ok": true,
            "code": code,
            "grantId": grant_id,
            "expiresIn": BIND_TTL_MS / 1000,
            "server": capabilities()
        }),
    ))
}

async fn claim_device(
    State(state): State<SharedState>,
    headers: HeaderMap,
    body: Bytes,
) -> AppResult {
    cleanup(&state).await;
    let body = read_json(body)?;
    let requester = require_device(&state, &headers, Some(&body)).await?;
    let code = string_value(body.get("code")).trim().to_string();
    if code.is_empty() {
        return Err(AppError::new(StatusCode::NOT_FOUND, "Bind code expired"));
    }

    let (bind, mut device) = {
        let relay = state.relay.lock().await;
        let bind = relay
            .bind_codes
            .get(&code)
            .cloned()
            .ok_or_else(|| AppError::new(StatusCode::NOT_FOUND, "Bind code expired"))?;
        if bind.expires_at < now_ms() {
            return Err(AppError::new(StatusCode::NOT_FOUND, "Bind code expired"));
        }
        if requester.device_id == bind.device_id {
            return Err(AppError::new(
                StatusCode::BAD_REQUEST,
                "Cannot bind local device",
            ));
        }
        let device = relay
            .devices
            .get(&bind.device_id)
            .cloned()
            .ok_or_else(|| AppError::new(StatusCode::NOT_FOUND, "Device not found"))?;
        (bind, device)
    };

    let token = {
        let current = read_group_token(Some(&headers), &body);
        if current.is_empty() {
            random_capability("gtk")
        } else {
            current
        }
    };
    let group = group_from_token(&headers, &token)?;
    let alias = string_value(body.get("alias"));
    add_group_to_device(&mut device, &group.group_id);
    if !alias.trim().is_empty() {
        device.alias = Some(alias.trim().to_string());
    } else if device.alias.as_deref().unwrap_or("").is_empty() {
        device.alias = Some(device.name.clone());
    }
    device.updated_at = now_ms();
    let public = {
        let mut relay = state.relay.lock().await;
        relay
            .devices
            .insert(device.device_id.clone(), device.clone());
        link_device_locked(&mut relay, &group.group_id, &device.device_id);
        relay.bind_codes.remove(&code);
        public_device_locked(&relay, &device)
    };
    let command = enqueue_command(
        &state,
        &group.group_id,
        &device.device_id,
        "remote.profile.addGroup",
        json!({
            "groupId": group.group_id,
            "groupToken": group.group_token,
            "groupTokenHash": group.group_token_hash,
            "grantId": bind.grant_id,
            "bindGrantToken": bind.bind_grant_token,
            "alias": alias
        }),
        &group.group_token_hash,
    )
    .await;

    Ok(json_response(
        StatusCode::OK,
        json!({
            "ok": true,
            "deviceId": device.device_id,
            "groupId": group.group_id,
            "groupToken": group.group_token,
            "familyToken": group.group_token,
            "groupTokenHash": group.group_token_hash,
            "grantId": bind.grant_id,
            "bindGrantToken": bind.bind_grant_token,
            "commandId": command.id,
            "device": public,
            "server": capabilities()
        }),
    ))
}

async fn list_devices(State(state): State<SharedState>, headers: HeaderMap) -> AppResult {
    cleanup(&state).await;
    let group = require_group(&headers, None)?;
    let mut devices = {
        let relay = state.relay.lock().await;
        relay
            .group_devices
            .get(&group.group_id)
            .into_iter()
            .flat_map(|ids| ids.iter())
            .filter_map(|id| relay.devices.get(id))
            .map(|device| public_device_locked(&relay, device))
            .collect::<Vec<_>>()
    };
    devices.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(json_response(
        StatusCode::OK,
        json!({ "ok": true, "devices": devices, "server": capabilities() }),
    ))
}

async fn poll_device(
    State(state): State<SharedState>,
    headers: HeaderMap,
    body: Bytes,
) -> AppResult {
    cleanup(&state).await;
    let body = read_json(body)?;
    let device = require_device(&state, &headers, Some(&body)).await?;
    update_device_groups(&state, &headers, &device, &body).await?;
    let command = next_queued_command(&state, &device.device_id).await;
    Ok(json_response(
        StatusCode::OK,
        json!({ "ok": true, "command": command, "server": capabilities() }),
    ))
}

async fn create_command(
    State(state): State<SharedState>,
    headers: HeaderMap,
    body: Bytes,
) -> AppResult {
    cleanup(&state).await;
    let body = read_json(body)?;
    let group = require_group(&headers, Some(&body))?;
    let target_device_id = clean_id(&string_value(body.get("targetDeviceId")));
    let mut command_type = string_value(body.get("type"));
    if command_type.is_empty() {
        command_type = "unknown".to_string();
    }
    let mut payload = object_value(body.get("payload"));
    if command_type == "remote.profile.addGroup" {
        let bind_grant = string_value(payload.get("bindGrantToken"));
        let group_token = string_value(payload.get("groupToken"));
        if bind_grant.is_empty() || group_token.is_empty() {
            return Err(AppError::new(
                StatusCode::BAD_REQUEST,
                "Missing bootstrap payload",
            ));
        }
        let bootstrap = group_from_token(&headers, &group_token)?;
        if bootstrap.group_id != group.group_id {
            return Err(AppError::new(
                StatusCode::BAD_REQUEST,
                "Bootstrap group token mismatch",
            ));
        }
        payload.insert("groupId".to_string(), Value::String(bootstrap.group_id));
        payload.insert(
            "groupTokenHash".to_string(),
            Value::String(bootstrap.group_token_hash),
        );
    } else {
        require_target_if_known(&state, &group.group_id, &target_device_id).await?;
        payload.insert("groupId".to_string(), Value::String(group.group_id.clone()));
        payload.insert(
            "groupTokenHash".to_string(),
            Value::String(group.group_token_hash.clone()),
        );
    }
    let command = enqueue_command(
        &state,
        &group.group_id,
        &target_device_id,
        &command_type,
        Value::Object(payload),
        &group.group_token_hash,
    )
    .await;
    Ok(json_response(
        StatusCode::OK,
        json!({ "ok": true, "commandId": command.id, "command": command }),
    ))
}

async fn get_command(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Path(command_id): Path<String>,
) -> AppResult {
    cleanup(&state).await;
    let group = require_group(&headers, None)?;
    let command_id = clean_id(&command_id);
    let command = {
        let relay = state.relay.lock().await;
        relay.commands.get(&command_id).cloned()
    };
    match command {
        Some(command) if command.group_id == group.group_id => Ok(json_response(
            StatusCode::OK,
            json!({ "ok": true, "command": command, "server": capabilities() }),
        )),
        _ => Err(AppError::new(StatusCode::NOT_FOUND, "Command not found")),
    }
}

async fn command_result(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Path(command_id): Path<String>,
    body: Bytes,
) -> AppResult {
    cleanup(&state).await;
    let body = read_json(body)?;
    let device = require_device(&state, &headers, Some(&body)).await?;
    let command_id = clean_id(&command_id);
    {
        let mut relay = state.relay.lock().await;
        let command = relay
            .commands
            .get_mut(&command_id)
            .ok_or_else(|| AppError::new(StatusCode::NOT_FOUND, "Command not found"))?;
        if command.target_device_id != device.device_id {
            return Err(AppError::new(
                StatusCode::FORBIDDEN,
                "Command target mismatch",
            ));
        }
        command.status = if bool_value(body.get("ok"), true) {
            "done"
        } else {
            "failed"
        }
        .to_string();
        command.result = Some(Value::Object(body));
        command.finished_at = Some(now_ms());
    }
    Ok(json_response(StatusCode::OK, json!({ "ok": true })))
}

async fn create_sync(
    State(state): State<SharedState>,
    headers: HeaderMap,
    body: Bytes,
) -> AppResult {
    cleanup(&state).await;
    let body = read_json(body)?;
    let group = require_group(&headers, Some(&body))?;
    let source = clean_id(&string_value(body.get("sourceDeviceId")));
    let target = clean_id(&string_value(body.get("targetDeviceId")));
    require_target_if_known(&state, &group.group_id, &source).await?;
    require_target_if_known(&state, &group.group_id, &target).await?;
    if source == target {
        return Err(AppError::new(
            StatusCode::BAD_REQUEST,
            "Source and target device must be different",
        ));
    }
    let sync_id = format!("sync_{}", random_id(20));
    let origin = server_origin(&headers);
    let sync = SyncTask {
        id: sync_id.clone(),
        group_id: group.group_id.clone(),
        group_token_hash: group.group_token_hash.clone(),
        source_device_id: source.clone(),
        target_device_id: target.clone(),
        options: normalize_sync_options(body.get("options")),
        status: "created".to_string(),
        parts: Map::new(),
        export_command_id: None,
        restore_command_id: None,
        export_result: None,
        restore_result: None,
        created_at: now_ms(),
        updated_at: now_ms(),
    };
    {
        let mut relay = state.relay.lock().await;
        relay.syncs.insert(sync_id.clone(), sync.clone());
    }
    let command = enqueue_command(
        &state,
        &group.group_id,
        &source,
        "remoteSync.export",
        json!({
            "syncId": sync_id,
            "targetDeviceId": target,
            "options": sync.options,
            "uploadBase": format!("{}/api/sync/{}/part", origin, sync.id),
            "completeUrl": format!("{}/api/sync/{}/export-complete", origin, sync.id),
            "groupId": group.group_id,
            "groupTokenHash": group.group_token_hash
        }),
        &group.group_token_hash,
    )
    .await;
    let sync = {
        let mut relay = state.relay.lock().await;
        let sync = relay.syncs.get_mut(&sync.id).expect("sync exists");
        sync.export_command_id = Some(command.id.clone());
        sync.clone()
    };
    Ok(json_response(
        StatusCode::OK,
        json!({ "ok": true, "sync": sync, "exportCommandId": command.id, "server": capabilities() }),
    ))
}

async fn get_sync(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Path(sync_id): Path<String>,
) -> AppResult {
    cleanup(&state).await;
    let group = require_group(&headers, None)?;
    let sync_id = clean_id(&sync_id);
    let sync = {
        let relay = state.relay.lock().await;
        relay.syncs.get(&sync_id).cloned()
    };
    match sync {
        Some(sync) if sync.group_id == group.group_id => Ok(json_response(
            StatusCode::OK,
            json!({ "ok": true, "sync": sync }),
        )),
        _ => Err(AppError::new(StatusCode::NOT_FOUND, "Sync not found")),
    }
}

async fn upload_sync_part(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Path((sync_id, part)): Path<(String, String)>,
    body: Bytes,
) -> AppResult {
    cleanup(&state).await;
    let part = normalize_part(&part)?;
    let device = require_device(&state, &headers, None).await?;
    let sync_id = clean_id(&sync_id);
    let sync = get_sync_for_device(&state, &sync_id, &device.device_id).await?;
    if sync.source_device_id != device.device_id {
        return Err(AppError::new(
            StatusCode::FORBIDDEN,
            "Only source device can upload sync parts",
        ));
    }
    if body.len() > MAX_SYNC_PART_BYTES {
        return Err(AppError::new(
            StatusCode::PAYLOAD_TOO_LARGE,
            "Sync part is too large for online relay",
        ));
    }
    let content_type = headers
        .get(header::CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        .filter(|value| !value.is_empty())
        .map(ToString::to_string)
        .unwrap_or_else(|| content_type_for_part(&part).to_string());
    let key = format!("sync:{}:{}", sync.id, part);
    let uploaded_at = now_ms();
    let info = json!({
        "key": key,
        "size": body.len(),
        "contentType": content_type,
        "uploadedAt": uploaded_at
    });
    {
        let mut relay = state.relay.lock().await;
        relay.parts.insert(
            key.clone(),
            SyncPart {
                bytes: body.to_vec(),
                content_type: content_type.clone(),
                size: body.len(),
            },
        );
        if let Some(sync) = relay.syncs.get_mut(&sync.id) {
            sync.parts.insert(part.clone(), info);
            sync.status = "exporting".to_string();
            sync.updated_at = uploaded_at;
        }
    }
    Ok(json_response(
        StatusCode::OK,
        json!({ "ok": true, "part": part, "size": body.len() }),
    ))
}

async fn download_sync_part(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Path((sync_id, part)): Path<(String, String)>,
) -> AppResult {
    cleanup(&state).await;
    let part = normalize_part(&part)?;
    let device = require_device(&state, &headers, None).await?;
    let sync = get_sync_for_device(&state, &clean_id(&sync_id), &device.device_id).await?;
    let (stored, content_type, size) = {
        let relay = state.relay.lock().await;
        let info = sync
            .parts
            .get(&part)
            .and_then(|value| value.as_object())
            .ok_or_else(|| AppError::new(StatusCode::NOT_FOUND, "Sync part not found"))?;
        let key = string_value(info.get("key"));
        let stored = relay
            .parts
            .get(&key)
            .ok_or_else(|| AppError::new(StatusCode::NOT_FOUND, "Sync part expired"))?;
        (
            stored.bytes.clone(),
            stored.content_type.clone(),
            stored.size,
        )
    };
    let mut response = Response::new(stored.into());
    *response.status_mut() = StatusCode::OK;
    response.headers_mut().insert(
        header::CONTENT_TYPE,
        header_value(&content_type_for_download(&content_type, &part))?,
    );
    response
        .headers_mut()
        .insert(header::CONTENT_LENGTH, header_value(&size.to_string())?);
    response
        .headers_mut()
        .insert(header::CACHE_CONTROL, header_value("no-store")?);
    Ok(response)
}

async fn export_complete(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Path(sync_id): Path<String>,
    body: Bytes,
) -> AppResult {
    cleanup(&state).await;
    let body = read_json(body)?;
    let device = require_device(&state, &headers, Some(&body)).await?;
    let sync = get_sync_for_device(&state, &clean_id(&sync_id), &device.device_id).await?;
    if sync.source_device_id != device.device_id {
        return Err(AppError::new(
            StatusCode::FORBIDDEN,
            "Only source device can finish export",
        ));
    }
    let origin = server_origin(&headers);
    let (parts, downloads) = {
        let mut relay = state.relay.lock().await;
        let sync = relay.syncs.get_mut(&sync.id).expect("sync exists");
        sync.status = "exported".to_string();
        sync.export_result = Some(Value::Object(body));
        sync.updated_at = now_ms();
        let parts = sync.parts.clone();
        let mut downloads = Map::new();
        for part in parts.keys() {
            downloads.insert(
                part.clone(),
                Value::String(format!("{}/api/sync/{}/part/{}", origin, sync.id, part)),
            );
        }
        (parts, Value::Object(downloads))
    };
    let command = enqueue_command(
        &state,
        &sync.group_id,
        &sync.target_device_id,
        "remoteSync.restore",
        json!({
            "syncId": sync.id,
            "sourceDeviceId": sync.source_device_id,
            "options": sync.options,
            "parts": parts,
            "downloads": downloads,
            "completeUrl": format!("{}/api/sync/{}/restore-complete", origin, sync.id),
            "groupId": sync.group_id,
            "groupTokenHash": sync.group_token_hash
        }),
        &sync.group_token_hash,
    )
    .await;
    {
        let mut relay = state.relay.lock().await;
        if let Some(sync) = relay.syncs.get_mut(&sync.id) {
            sync.restore_command_id = Some(command.id.clone());
        }
    }
    Ok(json_response(
        StatusCode::OK,
        json!({ "ok": true, "restoreCommandId": command.id, "server": capabilities() }),
    ))
}

async fn restore_complete(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Path(sync_id): Path<String>,
    body: Bytes,
) -> AppResult {
    cleanup(&state).await;
    let body = read_json(body)?;
    let device = require_device(&state, &headers, Some(&body)).await?;
    let sync = get_sync_for_device(&state, &clean_id(&sync_id), &device.device_id).await?;
    if sync.target_device_id != device.device_id {
        return Err(AppError::new(
            StatusCode::FORBIDDEN,
            "Only target device can finish restore",
        ));
    }
    let ok = bool_value(body.get("ok"), true);
    {
        let mut relay = state.relay.lock().await;
        let part_keys = relay
            .syncs
            .get(&sync.id)
            .map(sync_part_keys)
            .unwrap_or_default();
        if let Some(sync) = relay.syncs.get_mut(&sync.id) {
            sync.status = if ok { "done" } else { "restore_failed" }.to_string();
            sync.restore_result = Some(Value::Object(body));
            sync.updated_at = now_ms();
        }
        if ok {
            for key in part_keys {
                relay.parts.remove(&key);
            }
        }
    }
    Ok(json_response(StatusCode::OK, json!({ "ok": true })))
}

async fn websocket_handler(
    State(state): State<SharedState>,
    headers: HeaderMap,
    ws: WebSocketUpgrade,
) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_socket(socket, state, headers))
}

async fn handle_socket(mut socket: WebSocket, state: SharedState, headers: HeaderMap) {
    let hello = match tokio::time::timeout(Duration::from_secs(15), socket.recv()).await {
        Ok(Some(Ok(Message::Text(text)))) => text,
        Ok(Some(Ok(Message::Binary(bytes)))) => String::from_utf8_lossy(&bytes).to_string(),
        _ => return,
    };
    let mut body = match serde_json::from_str::<JsonMap>(&hello) {
        Ok(body) => body,
        Err(_) => {
            let _ = socket.send(Message::Text(json!({ "type": "error", "ok": false, "error": "Invalid JSON body", "server": capabilities() }).to_string())).await;
            return;
        }
    };
    if matches!(string_value(body.get("messageType")).as_str(), "hello")
        || matches!(string_value(body.get("type")).as_str(), "hello")
    {
        body.remove("messageType");
        body.remove("type");
    }
    let (device, token) = match register_device_internal(&state, &headers, &body).await {
        Ok(value) => value,
        Err(error) => {
            let _ = socket.send(Message::Text(json!({ "type": "error", "ok": false, "error": error.message, "server": capabilities() }).to_string())).await;
            return;
        }
    };
    let (mut ws_tx, mut ws_rx) = socket.split();
    let (tx, mut rx) = mpsc::unbounded_channel::<Value>();
    register_socket(&state, &device.device_id, tx.clone()).await;
    let _ = tx.send(json!({
        "type": "ready",
        "ok": true,
        "deviceId": device.device_id,
        "deviceToken": token,
        "deviceSecret": token,
        "groupIds": device.group_ids,
        "server": capabilities()
    }));
    let writer = tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(25));
        loop {
            tokio::select! {
                Some(value) = rx.recv() => {
                    if ws_tx.send(Message::Text(value.to_string())).await.is_err() {
                        break;
                    }
                }
                _ = interval.tick() => {
                    if ws_tx.send(Message::Ping(b"ping".to_vec())).await.is_err() {
                        break;
                    }
                }
            }
        }
    });
    deliver_pending(&state, &device.device_id).await;
    let reader_state = state.clone();
    let reader_device = device.device_id.clone();
    let reader = async move {
        while let Some(Ok(message)) = ws_rx.next().await {
            touch_device(&reader_state, &reader_device).await;
            let text = match message {
                Message::Text(text) => text,
                Message::Binary(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Message::Close(_) => break,
                _ => continue,
            };
            if let Ok(body) = serde_json::from_str::<JsonMap>(&text) {
                if string_value(body.get("type")) == "result" {
                    let command_id = clean_id(&string_value(body.get("commandId")));
                    if !command_id.is_empty() {
                        apply_ws_command_result(
                            &reader_state,
                            &reader_device,
                            &command_id,
                            Value::Object(body),
                        )
                        .await;
                    }
                }
            }
        }
    };
    tokio::select! {
        _ = writer => {}
        _ = reader => {}
    }
    unregister_socket(&state, &device.device_id, &tx).await;
}

async fn not_found() -> Response {
    json_response(
        StatusCode::NOT_FOUND,
        json!({ "ok": false, "error": "Not found" }),
    )
}

fn capabilities() -> Value {
    json!({
        "ok": true,
        "serverMode": SERVER_MODE,
        "serverName": "WebHTV Rust Remote Relay",
        "relayMode": "rust-memory-websocket",
        "time": now_ms(),
        "maxSyncPartBytes": MAX_SYNC_PART_BYTES,
        "capabilities": {
            "configManage": true,
            "remoteSync": true,
            "pushAction": true,
            "recentLog": true,
            "deviceBackup": false,
            "fileManage": false,
            "webHomeManage": false,
            "shellProxyManage": false,
            "siteInjectManage": false,
            "webHomeExtensionManage": false,
            "playbackSync": playback::playback_available(),
            "playbackPersistentStorage": playback::playback_available() && playback::playback_persistent(),
            "multiDeviceBatch": false,
            "webSocket": true,
            "persistentStorage": false,
            "externalObjectStorage": false,
            "deviceRevoke": false
        }
    })
}

async fn register_device_internal(
    state: &SharedState,
    headers: &HeaderMap,
    body: &JsonMap,
) -> Result<(Device, String), AppError> {
    let origin = server_origin(headers);
    let mut token = read_device_token(Some(headers), body);
    if token.is_empty() {
        token = random_capability("dtk");
    }
    let device_id = require_derived_id(
        "dev",
        &origin,
        &token,
        &string_value(body.get("deviceId")),
        "Invalid device token",
    )?;
    let existing = {
        let relay = state.relay.lock().await;
        relay.devices.get(&device_id).cloned()
    };
    let mut group_ids = BTreeSet::new();
    for id in device_group_ids(existing.as_ref()) {
        group_ids.insert(id);
    }
    for group_token in read_group_tokens(None, body) {
        let group = group_from_token(headers, &group_token)?;
        group_ids.insert(group.group_id);
    }
    let ids = group_ids.into_iter().collect::<Vec<_>>();
    let now = now_ms();
    let device = Device {
        device_id: device_id.clone(),
        group_id: ids.first().cloned(),
        group_ids: ids,
        name: first_non_empty(&[
            string_value(body.get("name")),
            existing
                .as_ref()
                .map(|d| d.name.clone())
                .unwrap_or_default(),
            "WebHTV".to_string(),
        ]),
        alias: optional_string(first_non_empty(&[
            string_value(body.get("alias")),
            existing
                .as_ref()
                .and_then(|d| d.alias.clone())
                .unwrap_or_default(),
        ])),
        role: first_non_empty(&[
            string_value(body.get("role")),
            existing
                .as_ref()
                .map(|d| d.role.clone())
                .unwrap_or_default(),
            "app".to_string(),
        ]),
        kind: body
            .get("type")
            .cloned()
            .or_else(|| existing.as_ref().map(|d| d.kind.clone()))
            .unwrap_or_else(|| json!(0)),
        app_version: optional_string(first_non_empty(&[
            string_value(body.get("appVersion")),
            existing
                .as_ref()
                .and_then(|d| d.app_version.clone())
                .unwrap_or_default(),
        ])),
        capabilities: body
            .get("capabilities")
            .cloned()
            .or_else(|| existing.as_ref().map(|d| d.capabilities.clone()))
            .unwrap_or_else(|| json!({})),
        last_seen: now,
        created_at: existing
            .as_ref()
            .map(|d| d.created_at)
            .filter(|v| *v > 0)
            .unwrap_or(now),
        updated_at: now,
    };
    {
        let mut relay = state.relay.lock().await;
        relay.devices.insert(device_id.clone(), device.clone());
        for id in &device.group_ids {
            link_device_locked(&mut relay, id, &device_id);
        }
    }
    Ok((device, token))
}

async fn require_device(
    state: &SharedState,
    headers: &HeaderMap,
    body: Option<&JsonMap>,
) -> Result<Device, AppError> {
    let empty = Map::new();
    let body = body.unwrap_or(&empty);
    let origin = server_origin(headers);
    let token = read_device_token(Some(headers), body);
    if token.is_empty() {
        return Err(AppError::new(
            StatusCode::UNAUTHORIZED,
            "Missing device credentials",
        ));
    }
    let id = first_non_empty(&[
        string_value(body.get("deviceId")),
        header_string(headers, "x-device-id"),
    ]);
    let device_id = require_derived_id("dev", &origin, &token, &id, "Invalid device token")?;
    let now = now_ms();
    let mut relay = state.relay.lock().await;
    if let Some(device) = relay.devices.get_mut(&device_id) {
        device.last_seen = now;
        device.updated_at = now;
        return Ok(device.clone());
    }
    let device = Device {
        device_id: device_id.clone(),
        group_id: None,
        group_ids: Vec::new(),
        name: first_non_empty(&[string_value(body.get("name")), "WebHTV".to_string()]),
        alias: optional_string(string_value(body.get("alias"))),
        role: first_non_empty(&[string_value(body.get("role")), "app".to_string()]),
        kind: body.get("type").cloned().unwrap_or_else(|| json!(0)),
        app_version: optional_string(string_value(body.get("appVersion"))),
        capabilities: body
            .get("capabilities")
            .cloned()
            .unwrap_or_else(|| json!({})),
        last_seen: now,
        created_at: now,
        updated_at: now,
    };
    relay.devices.insert(device_id, device.clone());
    Ok(device)
}

async fn update_device_groups(
    state: &SharedState,
    headers: &HeaderMap,
    device: &Device,
    body: &JsonMap,
) -> Result<(), AppError> {
    let mut group_ids = BTreeSet::new();
    for id in device_group_ids(Some(device)) {
        group_ids.insert(id);
    }
    for token in read_group_tokens(None, body) {
        let group = group_from_token(headers, &token)?;
        group_ids.insert(group.group_id);
    }
    let ids = group_ids.into_iter().collect::<Vec<_>>();
    let now = now_ms();
    let mut relay = state.relay.lock().await;
    if let Some(current) = relay.devices.get_mut(&device.device_id) {
        current.group_ids = ids.clone();
        current.group_id = ids.first().cloned();
        current.last_seen = now;
        current.updated_at = now;
    }
    for id in &ids {
        link_device_locked(&mut relay, id, &device.device_id);
    }
    Ok(())
}

async fn enqueue_command(
    state: &SharedState,
    group_id: &str,
    target_device_id: &str,
    command_type: &str,
    payload: Value,
    group_token_hash: &str,
) -> Command {
    let command = Command {
        id: format!("cmd_{}", random_id(20)),
        group_id: group_id.to_string(),
        group_token_hash: group_token_hash.to_string(),
        target_device_id: target_device_id.to_string(),
        command_type: if command_type.is_empty() {
            "unknown".to_string()
        } else {
            command_type.to_string()
        },
        payload,
        status: "queued".to_string(),
        result: None,
        created_at: now_ms(),
        delivered_at: None,
        finished_at: None,
    };
    {
        let mut relay = state.relay.lock().await;
        relay.commands.insert(command.id.clone(), command.clone());
        relay
            .queues
            .entry(target_device_id.to_string())
            .or_default()
            .push_back(command.id.clone());
    }
    deliver_pending(state, target_device_id).await;
    command
}

async fn next_queued_command(state: &SharedState, device_id: &str) -> Option<Command> {
    let mut relay = state.relay.lock().await;
    loop {
        let next_id = {
            let queue = relay.queues.entry(device_id.to_string()).or_default();
            queue.pop_front()
        }?;
        let Some(command) = relay.commands.get_mut(&next_id) else {
            continue;
        };
        if is_expired(command.created_at, COMMAND_TTL_MS) {
            continue;
        }
        command.status = "delivered".to_string();
        command.delivered_at = Some(now_ms());
        return Some(command.clone());
    }
}

async fn deliver_pending(state: &SharedState, device_id: &str) {
    loop {
        let Some(command) = next_queued_command(state, device_id).await else {
            return;
        };
        let senders = {
            let relay = state.relay.lock().await;
            relay.sockets.get(device_id).cloned().unwrap_or_default()
        };
        if senders.is_empty() {
            requeue_command(state, device_id, &command).await;
            return;
        }
        let message =
            json!({ "type": "command", "ok": true, "command": command, "server": capabilities() });
        let mut sent = false;
        for sender in senders {
            sent |= sender.send(message.clone()).is_ok();
        }
        if !sent {
            requeue_command(state, device_id, &command).await;
            return;
        }
    }
}

async fn requeue_command(state: &SharedState, device_id: &str, command: &Command) {
    let mut relay = state.relay.lock().await;
    if let Some(current) = relay.commands.get_mut(&command.id) {
        current.status = "queued".to_string();
        current.delivered_at = None;
    }
    relay
        .queues
        .entry(device_id.to_string())
        .or_default()
        .push_front(command.id.clone());
}

async fn register_socket(state: &SharedState, device_id: &str, sender: WsSender) {
    let mut relay = state.relay.lock().await;
    relay
        .sockets
        .entry(device_id.to_string())
        .or_default()
        .push(sender);
    if let Some(device) = relay.devices.get_mut(device_id) {
        device.last_seen = now_ms();
        device.updated_at = device.last_seen;
    }
}

async fn unregister_socket(state: &SharedState, device_id: &str, sender: &WsSender) {
    let mut relay = state.relay.lock().await;
    if let Some(list) = relay.sockets.get_mut(device_id) {
        list.retain(|candidate| !candidate.same_channel(sender));
        if list.is_empty() {
            relay.sockets.remove(device_id);
        }
    }
}

async fn touch_device(state: &SharedState, device_id: &str) {
    let mut relay = state.relay.lock().await;
    if let Some(device) = relay.devices.get_mut(device_id) {
        device.last_seen = now_ms();
        device.updated_at = device.last_seen;
    }
}

async fn apply_ws_command_result(
    state: &SharedState,
    device_id: &str,
    command_id: &str,
    result: Value,
) {
    let mut relay = state.relay.lock().await;
    let Some(command) = relay.commands.get_mut(command_id) else {
        return;
    };
    if command.target_device_id != device_id {
        return;
    }
    let ok = result
        .as_object()
        .map(|object| bool_value(object.get("ok"), true))
        .unwrap_or(true);
    command.status = if ok { "done" } else { "failed" }.to_string();
    command.result = Some(result);
    command.finished_at = Some(now_ms());
}

async fn require_target_if_known(
    state: &SharedState,
    group_id: &str,
    device_id: &str,
) -> Result<(), AppError> {
    if device_id.is_empty() {
        return Err(AppError::new(StatusCode::BAD_REQUEST, "Missing deviceId"));
    }
    let relay = state.relay.lock().await;
    if let Some(device) = relay.devices.get(device_id) {
        if !device_in_group(device, group_id) {
            return Err(AppError::new(
                StatusCode::NOT_FOUND,
                "Device is not bound to this group",
            ));
        }
    }
    Ok(())
}

async fn get_sync_for_device(
    state: &SharedState,
    sync_id: &str,
    device_id: &str,
) -> Result<SyncTask, AppError> {
    let relay = state.relay.lock().await;
    let sync = relay
        .syncs
        .get(sync_id)
        .cloned()
        .ok_or_else(|| AppError::new(StatusCode::NOT_FOUND, "Sync not found"))?;
    if sync.source_device_id != device_id && sync.target_device_id != device_id {
        return Err(AppError::new(
            StatusCode::FORBIDDEN,
            "Device is not part of this sync",
        ));
    }
    Ok(sync)
}

async fn cleanup(state: &SharedState) {
    let mut relay = state.relay.lock().await;
    let now = now_ms();
    if now - relay.last_cleanup_ms < 30_000 {
        return;
    }
    relay.last_cleanup_ms = now;
    relay.bind_codes.retain(|_, bind| bind.expires_at >= now);
    let expired_commands = relay
        .commands
        .iter()
        .filter(|(_, command)| is_expired(command.created_at, COMMAND_TTL_MS))
        .map(|(id, _)| id.clone())
        .collect::<Vec<_>>();
    for id in expired_commands {
        relay.commands.remove(&id);
    }
    let expired_syncs = relay
        .syncs
        .iter()
        .filter(|(_, sync)| is_expired(sync.created_at, SYNC_TTL_MS))
        .map(|(id, _)| id.clone())
        .collect::<Vec<_>>();
    for id in expired_syncs {
        if let Some(sync) = relay.syncs.remove(&id) {
            for key in sync_part_keys(&sync) {
                relay.parts.remove(&key);
            }
        }
    }
    let existing = relay.commands.keys().cloned().collect::<BTreeSet<_>>();
    for queue in relay.queues.values_mut() {
        queue.retain(|id| existing.contains(id));
    }
}

fn require_group(headers: &HeaderMap, body: Option<&JsonMap>) -> Result<GroupInfo, AppError> {
    let empty = Map::new();
    let token = read_group_token(Some(headers), body.unwrap_or(&empty));
    if token.is_empty() {
        return Err(AppError::new(
            StatusCode::UNAUTHORIZED,
            "Missing group token",
        ));
    }
    group_from_token(headers, &token)
}

fn group_from_token(headers: &HeaderMap, token: &str) -> Result<GroupInfo, AppError> {
    let token = token.trim();
    if token.is_empty() {
        return Err(AppError::new(
            StatusCode::UNAUTHORIZED,
            "Missing group token",
        ));
    }
    let origin = server_origin(headers);
    let hash = sha256_hex(&format!("{}:{}", origin, token));
    Ok(GroupInfo {
        group_id: format!("grp_{}", &hash[..24]),
        group_token: token.to_string(),
        group_token_hash: hash,
    })
}

fn require_derived_id(
    prefix: &str,
    origin: &str,
    token: &str,
    id: &str,
    message: &str,
) -> Result<String, AppError> {
    let expected = derive_id(prefix, origin, token);
    let actual = clean_id(id);
    if !actual.is_empty() && actual != expected {
        return Err(AppError::new(StatusCode::UNAUTHORIZED, message));
    }
    Ok(expected)
}

fn derive_id(prefix: &str, origin: &str, token: &str) -> String {
    let hash = sha256_hex(&format!("{}:{}", origin, token));
    format!("{}_{}", prefix, &hash[..24])
}

fn read_json(body: Bytes) -> Result<JsonMap, AppError> {
    if body.is_empty() {
        return Ok(Map::new());
    }
    serde_json::from_slice::<JsonMap>(&body)
        .map_err(|_| AppError::new(StatusCode::BAD_REQUEST, "Invalid JSON body"))
}

fn json_response(status: StatusCode, value: Value) -> Response {
    let mut response = Json(value).into_response();
    *response.status_mut() = status;
    response.headers_mut().insert(
        header::CACHE_CONTROL,
        header::HeaderValue::from_static("no-store"),
    );
    response
}

fn header_value(value: &str) -> Result<header::HeaderValue, AppError> {
    header::HeaderValue::from_str(value)
        .map_err(|_| AppError::new(StatusCode::INTERNAL_SERVER_ERROR, "Invalid response header"))
}

fn server_origin(headers: &HeaderMap) -> String {
    if let Some(origin) = normalize_origin(&header_string(headers, "x-webhtv-origin")) {
        return origin;
    }
    let scheme = header_string(headers, "x-forwarded-proto")
        .split(',')
        .next()
        .map(|value| value.trim().to_lowercase())
        .filter(|value| !value.is_empty())
        .unwrap_or_else(|| "http".to_string());
    let host = first_non_empty(&[
        header_string(headers, "x-forwarded-host"),
        header_string(headers, "host"),
    ])
    .split(',')
    .next()
    .unwrap_or("")
    .trim()
    .to_lowercase();
    format!("{}://{}", scheme, host)
}

fn normalize_origin(value: &str) -> Option<String> {
    let value = value.trim().to_lowercase();
    if value.is_empty() || (!value.starts_with("http://") && !value.starts_with("https://")) {
        return None;
    }
    let scheme = if value.starts_with("https://") {
        "https://"
    } else {
        "http://"
    };
    let mut rest = value
        .trim_start_matches("https://")
        .trim_start_matches("http://");
    if let Some(index) = rest.find(&['/', '?', '#'][..]) {
        rest = &rest[..index];
    }
    if rest.is_empty() {
        return None;
    }
    Some(format!("{}{}", scheme, rest))
}

fn read_device_token(headers: Option<&HeaderMap>, body: &JsonMap) -> String {
    first_non_empty(&[
        string_value(body.get("deviceToken")),
        string_value(body.get("deviceSecret")),
        headers
            .map(|h| header_string(h, "x-device-token"))
            .unwrap_or_default(),
        headers.map(bearer).unwrap_or_default(),
    ])
}

fn read_group_token(headers: Option<&HeaderMap>, body: &JsonMap) -> String {
    first_non_empty(&[
        string_value(body.get("groupToken")),
        string_value(body.get("familyToken")),
        headers
            .map(|h| header_string(h, "x-group-token"))
            .unwrap_or_default(),
        headers
            .map(|h| header_string(h, "x-family-token"))
            .unwrap_or_default(),
        headers.map(bearer).unwrap_or_default(),
    ])
}

fn read_group_tokens(headers: Option<&HeaderMap>, body: &JsonMap) -> Vec<String> {
    let mut result = Vec::new();
    let direct = read_group_token(headers, body);
    if !direct.is_empty() {
        result.push(direct);
    }
    if let Some(Value::Array(groups)) = body.get("groups") {
        for item in groups {
            if let Some(text) = item.as_str() {
                if !text.trim().is_empty() {
                    result.push(text.trim().to_string());
                }
            } else if let Some(object) = item.as_object() {
                let token = first_non_empty(&[
                    string_value(object.get("groupToken")),
                    string_value(object.get("familyToken")),
                ]);
                if !token.is_empty() {
                    result.push(token);
                }
            }
        }
    }
    let mut seen = BTreeSet::new();
    result
        .into_iter()
        .filter(|token| !token.is_empty() && seen.insert(token.clone()))
        .collect()
}

fn bearer(headers: &HeaderMap) -> String {
    let value = header_string(headers, "authorization");
    let lower = value.to_lowercase();
    if lower.starts_with("bearer ") {
        value[7..].trim().to_string()
    } else {
        String::new()
    }
}

fn normalize_sync_options(options: Option<&Value>) -> Value {
    let object = options.and_then(|value| value.as_object());
    json!({
        "config": bool_value(object.and_then(|m| m.get("config")), true),
        "loginState": bool_value(object.and_then(|m| m.get("loginState")), true),
        "spider": bool_value(object.and_then(|m| m.get("spider")), true),
        "webHome": bool_value(object.and_then(|m| m.get("webHome")), true),
        "search": bool_value(object.and_then(|m| m.get("search")), true),
        "keep": bool_value(object.and_then(|m| m.get("keep")), true),
        "history": bool_value(object.and_then(|m| m.get("history")), true),
        "follow": bool_value(object.and_then(|m| m.get("follow")), false),
        "settings": bool_value(object.and_then(|m| m.get("settings")), false),
        "remoteRelay": bool_value(object.and_then(|m| m.get("remoteRelay")), false),
        "paths": object
            .and_then(|m| m.get("paths"))
            .and_then(Value::as_str)
            .map(|value| Value::String(value.to_string()))
            .unwrap_or(Value::Null)
    })
}

fn normalize_part(part: &str) -> Result<String, AppError> {
    let part = part
        .trim_end_matches(".zip")
        .trim_end_matches(".json")
        .to_string();
    match part.as_str() {
        "backup" | "remoteRelay" | "syncFiles" | "loginStateFiles" | "manifest" => Ok(part),
        _ => Err(AppError::new(StatusCode::BAD_REQUEST, "Invalid sync part")),
    }
}

fn content_type_for_part(part: &str) -> &'static str {
    match part {
        "backup" | "manifest" | "remoteRelay" => "application/json; charset=utf-8",
        _ => "application/zip",
    }
}

fn content_type_for_download(content_type: &str, part: &str) -> String {
    if content_type.trim().is_empty() {
        content_type_for_part(part).to_string()
    } else {
        content_type.to_string()
    }
}

fn clean_id(value: &str) -> String {
    value
        .chars()
        .filter(|ch| ch.is_ascii_alphanumeric() || matches!(ch, '_' | '.' | ':' | '-'))
        .take(80)
        .collect()
}

fn device_group_ids(device: Option<&Device>) -> Vec<String> {
    let mut ids = BTreeSet::new();
    if let Some(device) = device {
        for id in &device.group_ids {
            if !id.is_empty() {
                ids.insert(id.clone());
            }
        }
        if let Some(id) = &device.group_id {
            if !id.is_empty() {
                ids.insert(id.clone());
            }
        }
    }
    ids.into_iter().collect()
}

fn add_group_to_device(device: &mut Device, group_id: &str) {
    let mut ids = BTreeSet::new();
    for id in device_group_ids(Some(device)) {
        ids.insert(id);
    }
    if !group_id.is_empty() {
        ids.insert(group_id.to_string());
    }
    device.group_ids = ids.into_iter().collect();
    device.group_id = device.group_ids.first().cloned();
}

fn device_in_group(device: &Device, group_id: &str) -> bool {
    device_group_ids(Some(device))
        .iter()
        .any(|id| id == group_id)
}

fn link_device_locked(relay: &mut RelayState, group_id: &str, device_id: &str) {
    if group_id.is_empty() || device_id.is_empty() {
        return;
    }
    relay
        .group_devices
        .entry(group_id.to_string())
        .or_default()
        .insert(device_id.to_string());
}

fn public_device_locked(relay: &RelayState, device: &Device) -> PublicDevice {
    let alias = device.alias.as_deref().unwrap_or("").trim();
    let name = if alias.is_empty() {
        device.name.clone()
    } else {
        alias.to_string()
    };
    PublicDevice {
        device_id: device.device_id.clone(),
        name,
        raw_name: Some(device.name.clone()).filter(|value| !value.is_empty()),
        role: device.role.clone(),
        kind: device.kind.clone(),
        app_version: device.app_version.clone(),
        last_seen: device.last_seen,
        online: now_ms() - device.last_seen < 45_000
            || relay
                .sockets
                .get(&device.device_id)
                .map(|list| !list.is_empty())
                .unwrap_or(false),
        capabilities: device.capabilities.clone(),
    }
}

fn sync_part_keys(sync: &SyncTask) -> Vec<String> {
    sync.parts
        .values()
        .filter_map(|value| value.as_object())
        .map(|object| string_value(object.get("key")))
        .filter(|key| !key.is_empty())
        .collect()
}

fn string_value(value: Option<&Value>) -> String {
    match value {
        Some(Value::String(text)) => text.trim().to_string(),
        Some(Value::Number(number)) => number.to_string(),
        Some(Value::Bool(value)) => value.to_string(),
        Some(Value::Null) | None => String::new(),
        Some(value) => value.to_string(),
    }
}

fn object_value(value: Option<&Value>) -> JsonMap {
    value
        .and_then(|value| value.as_object().cloned())
        .unwrap_or_default()
}

fn bool_value(value: Option<&Value>, fallback: bool) -> bool {
    match value {
        Some(Value::Bool(value)) => *value,
        Some(Value::String(text)) if text.trim().is_empty() => fallback,
        Some(Value::String(text)) => text.eq_ignore_ascii_case("true") || text == "1",
        Some(Value::Number(number)) => number.as_i64().map(|value| value != 0).unwrap_or(fallback),
        Some(Value::Null) | None => fallback,
        _ => fallback,
    }
}

fn optional_string(value: String) -> Option<String> {
    let value = value.trim().to_string();
    if value.is_empty() {
        None
    } else {
        Some(value)
    }
}

fn first_non_empty(values: &[String]) -> String {
    values
        .iter()
        .map(|value| value.trim())
        .find(|value| !value.is_empty())
        .unwrap_or("")
        .to_string()
}

fn header_string(headers: &HeaderMap, key: &str) -> String {
    headers
        .get(key)
        .and_then(|value| value.to_str().ok())
        .unwrap_or("")
        .trim()
        .to_string()
}

fn now_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}

fn is_expired(time: i64, ttl_ms: i64) -> bool {
    now_ms() - time > ttl_ms
}

fn sha256_hex(text: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(text.as_bytes());
    hex::encode(hasher.finalize())
}

fn random_id(bytes: usize) -> String {
    let mut data = vec![0_u8; bytes];
    OsRng.fill_bytes(&mut data);
    hex::encode(data)
}

fn random_capability(prefix: &str) -> String {
    format!("{}_{}", prefix, random_id(32))
}
