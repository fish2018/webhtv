import { parseIdentityRequest, resolveIdentity, resolveConfigKey, normalizeConfigType, identityCapabilities } from '../../playback-identity-fixtures/identity.js';
const PLAYBACK_SYNC_PATHS = new Set(['/api/playback/sync', '/playback/sync']);
const IDENTITY_RESOLVE_PATHS = new Set(['/api/playback/identity/resolve', '/playback/identity/resolve']);
const PLAYBACK_SCHEMA = 'webhtv.playback.v1';
const TOMBSTONE_RETENTION_MS = 90 * 24 * 60 * 60 * 1000;
const CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000;
const MAX_BODY_BYTES = 128 * 1024;
const MAX_BATCH_ITEMS = 100;
const DEFAULT_LIMIT = 100;
const MAX_LIMIT = 1000;
const MAX_CAS_ATTEMPTS = 5;

export function isPlaybackSyncPath(pathname) {
  const path = normalizePath(pathname);
  if (PLAYBACK_SYNC_PATHS.has(path)) return true;
  for (const base of PLAYBACK_SYNC_PATHS) if (path === `${base}/status`) return true;
  return IDENTITY_RESOLVE_PATHS.has(path);
}

export async function handlePlaybackSyncRequest(request, store) {
  if (request.method === 'OPTIONS') return playbackCors(new Response(null, { status: 204 }));
  try {
    if (!store || !store.isConfigured()) throw playbackHttpError(503, 'Playback persistent storage is not configured');
    const token = playbackToken(request);
    if (!token) throw playbackHttpError(401, 'Missing X-WebHTV-Token');
    if (token.length > 512) throw playbackHttpError(400, 'X-WebHTV-Token is too long');

    const url = new URL(request.url);
    const path = normalizePath(url.pathname);
    if (IDENTITY_RESOLVE_PATHS.has(path)) {
      if (request.method !== 'POST') throw playbackHttpError(405, 'Method not allowed');
      const input = parseIdentityRequest(await readPlaybackJson(request), request.headers);
      const result = await resolveIdentity(store, token, input);
      return playbackCors(playbackJson(result.body, result.status));
    }
    const statusPath = [...PLAYBACK_SYNC_PATHS].some((base) => path === `${base}/status`);
    if (statusPath && request.method !== 'GET') throw playbackHttpError(405, 'Method not allowed');
    if (!statusPath && !PLAYBACK_SYNC_PATHS.has(path)) throw playbackHttpError(404, 'Not found');

    if (request.method === 'POST' && !statusPath) return playbackCors(await ingestPlayback(request, store, token));
    if (request.method === 'GET') {
      const configKey = requireConfigKey(request);
      const configType = normalizeConfigType(request.headers.get('x-webhtv-config-type') || url.searchParams.get('configType'));
      const canonicalKey = await resolveConfigKey(store, token, configType, configKey, requestAliases(request));
      const spaceKey = await playbackSpaceKey(token, canonicalKey, configType);
      const snapshot = await loadPlaybackStore(store, spaceKey);
      const state = normalizePlaybackState(snapshot.state);
      return playbackCors(statusPath
        ? playbackStatus(state, canonicalKey, url)
        : pullPlayback(state, request, url));
    }
    throw playbackHttpError(405, 'Method not allowed');
  } catch (error) {
    const status = Number(error && error.status) || 500;
    if ((status >= 400 && status < 500) || status === 503) return playbackError(status, error && error.message ? error.message : 'Invalid request');
    console.error('Playback sync request failed', error && error.stack ? error.stack : error);
    return playbackError(500, 'Internal server error');
  }
}

export function createMemoryPlaybackStore() {
  const entries = new Map();
  return {
    persistent: false,
    isConfigured: () => true,
    async load(key) {
      const entry = entries.get(key);
      return entry
        ? { version: entry.version, state: cloneJson(entry.state) }
        : { version: null, state: null };
    },
    async compareAndSet(key, version, state) {
      const entry = entries.get(key);
      const current = entry ? entry.version : null;
      if (current !== version) return false;
      entries.set(key, { version: (current || 0) + 1, state: cloneJson(state) });
      return true;
    }
  };
}

async function ingestPlayback(request, store, token) {
  const body = await readPlaybackJson(request);
  const submittedConfigKey = requireConfigKey(request, body);
  const configType = normalizeConfigType(request.headers.get('x-webhtv-config-type') || body.configType || 'vod');
  const configKey = await resolveConfigKey(store, token, configType, submittedConfigKey, requestAliases(request, body));
  const rawEvents = extractPlaybackEvents(body);
  if (!rawEvents.length) throw playbackHttpError(400, 'Playback event is empty');
  if (rawEvents.length > MAX_BATCH_ITEMS) throw playbackHttpError(413, `Too many playback events; maximum is ${MAX_BATCH_ITEMS}`);

  const sharedEventId = rawEvents.length === 1
    ? cleanString(request.headers.get('x-webhtv-webhook-id') || request.headers.get('idempotency-key'), 160)
    : '';
  const now = Date.now();
  const events = rawEvents.map((raw) => normalizePlaybackEvent(raw, configKey, now, sharedEventId));
  const spaceKey = await playbackSpaceKey(token, configKey, configType);

  for (let attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
    const snapshot = await loadPlaybackStore(store, spaceKey);
    const state = normalizePlaybackState(snapshot.state);
    cleanupPlaybackState(state, now);
    const results = events.map((event) => event.kind === 'delete'
      ? applyDelete(state, event, now)
      : applyUpsert(state, event, now));
    if (!await savePlaybackStore(store, spaceKey, snapshot.version, state)) continue;
    return playbackJson({
      ok: true,
      received: results.length,
      applied: results.filter((item) => item.action === 'created' || item.action === 'updated' || item.action === 'deleted').length,
      skipped: results.filter((item) => item.action === 'skipped' || item.action === 'duplicate').length,
      results
    });
  }
  throw playbackHttpError(409, 'Playback data changed concurrently; retry the request');
}

function applyUpsert(state, event, receivedAt) {
  if (event.eventId && hasEvent(state, event.eventId)) return resultFor(event, 'duplicate', 0, 'Event already processed');
  const current = state.items[event.itemKey];
  let newestDeletion = null;
  for (const tombstone of Object.values(state.tombstones)) {
    if (!matchesTombstone(tombstone, event)) continue;
    if (!newestDeletion || tombstone.deletedAt > newestDeletion.deletedAt) newestDeletion = tombstone;
  }
  if (newestDeletion && event.updatedAt <= newestDeletion.deletedAt) {
    recordEvent(state, event.eventId, receivedAt);
    return resultFor(event, 'skipped', newestDeletion.seq, 'A newer deletion exists');
  }
  if (current && event.updatedAt <= current.updatedAt) {
    recordEvent(state, event.eventId, receivedAt);
    return resultFor(event, 'skipped', current.seq, 'A newer progress record exists');
  }

  const seq = nextSequence(state);
  state.items[event.itemKey] = {
    historyKey: event.historyKey,
    siteKey: event.siteKey,
    vodId: event.vodId,
    updatedAt: event.updatedAt,
    seq,
    payload: event.payload
  };
  recordEvent(state, event.eventId, receivedAt);
  return resultFor(event, current ? 'updated' : 'created', seq, '');
}

function applyDelete(state, event, receivedAt) {
  if (event.eventId && hasEvent(state, event.eventId)) return resultFor(event, 'duplicate', 0, 'Event already processed');
  const current = state.tombstones[event.markerKey];
  if (current && event.deletedAt <= current.deletedAt) {
    recordEvent(state, event.eventId, receivedAt);
    return resultFor(event, 'skipped', current.seq, 'A newer deletion exists');
  }

  const seq = nextSequence(state);
  state.tombstones[event.markerKey] = {
    scope: event.scope,
    historyKey: event.historyKey,
    siteKey: event.siteKey,
    vodId: event.vodId,
    deletedAt: event.deletedAt,
    seq,
    payload: event.payload
  };
  let affected = 0;
  for (const [key, item] of Object.entries(state.items)) {
    if (item.updatedAt > event.deletedAt || !matchesDelete(event, item, key)) continue;
    delete state.items[key];
    affected++;
  }
  recordEvent(state, event.eventId, receivedAt);
  return { ...resultFor(event, 'deleted', seq, ''), affected };
}

function pullPlayback(state, request, url) {
  const since = parseCursor(request.headers.get('x-webhtv-since') || url.searchParams.get('since'));
  const limit = parseLimit(request.headers.get('x-webhtv-limit') || url.searchParams.get('limit'));
  const cutoff = Date.now() - TOMBSTONE_RETENTION_MS;
  const rows = [];
  for (const item of Object.values(state.items)) if (item.seq > since) rows.push(item);
  for (const item of Object.values(state.tombstones)) if (item.deletedAt >= cutoff && item.seq > since) rows.push(item);
  rows.sort((a, b) => a.seq - b.seq);
  const hasMore = rows.length > limit;
  const selected = rows.slice(0, limit);
  const nextSince = selected.length ? String(selected[selected.length - 1].seq) : String(since);
  return playbackJson({ changes: selected.map((item) => cloneJson(item.payload)), nextSince, hasMore });
}

function playbackStatus(state, configKey, url) {
  const cutoff = Date.now() - TOMBSTONE_RETENTION_MS;
  const tombstones = Object.values(state.tombstones).filter((item) => item.deletedAt >= cutoff);
  let latest = 0;
  for (const item of Object.values(state.items)) latest = Math.max(latest, item.seq);
  for (const item of tombstones) latest = Math.max(latest, item.seq);
  return playbackJson({
    ok: true,
    configKey,
    identityProtocol: 'webhtv.playback.identity.v1',
    addressMatchVersion: 1,
    capabilities: identityCapabilities(),
    items: Object.keys(state.items).length,
    tombstones: tombstones.length,
    nextSince: String(latest),
    retentionDays: 90,
    endpoint: `${url.origin}${basePlaybackPath(url.pathname)}`
  });
}

export function normalizePlaybackEvent(input, configKey, now = Date.now(), fallbackEventId = '') {
  const raw = unwrapPlaybackEvent(input);
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw playbackHttpError(400, 'Invalid playback event');
  configKey = validatedConfigKey(configKey, 'Missing X-WebHTV-Config-Key');
  const bodyConfigKey = normalizeConfigKey(raw.configKey || raw.config_key);
  if (bodyConfigKey.length > 256) throw playbackHttpError(400, 'configKey is too long');
  if (bodyConfigKey && bodyConfigKey !== configKey) throw playbackHttpError(400, 'configKey does not match X-WebHTV-Config-Key');

  const eventName = cleanString(raw.event, 80).toLowerCase();
  const action = cleanString(raw.action || raw.op || raw.operation, 32).toLowerCase();
  const deletion = booleanValue(raw.deleted) || eventName === 'playback.deleted'
    || action === 'delete' || action === 'deleted' || action === 'remove' || action === 'removed';
  const eventId = cleanString(raw.eventId || raw.event_id || fallbackEventId, 160);
  let historyKey = cleanString(raw.historyKey || raw.key, 4096);
  const parts = historyParts(historyKey);
  let siteKey = cleanString(raw.siteKey || raw.site || raw.site_key || parts.siteKey, 1024);
  let vodId = cleanString(raw.vodId || raw.vod_id || raw.videoId || raw.itemId || parts.vodId, 8192);

  if (deletion) {
    const requestedScope = cleanString(raw.scope, 16).toLowerCase();
    if (requestedScope && !['all', 'site', 'item'].includes(requestedScope)) throw playbackHttpError(400, 'scope must be item, site, or all');
    const scope = normalizeScope(raw.scope, historyKey, siteKey, vodId);
    if (!scope) throw playbackHttpError(400, 'scope=all must be explicit when no item or site identity is provided');
    if (scope === 'site' && !siteKey) throw playbackHttpError(400, 'siteKey is required for a site deletion');
    if (scope === 'item' && !historyKey && (!siteKey || !vodId)) throw playbackHttpError(400, 'historyKey or siteKey + vodId is required for an item deletion');
    if (scope === 'all') {
      historyKey = '';
      siteKey = '';
      vodId = '';
    } else if (scope === 'site') {
      historyKey = '';
      vodId = '';
    }
    const deletedAt = positiveTimestamp(raw.deletedAt || raw.deleted_at || raw.timestamp || raw.updatedAt, 0);
    if (!deletedAt) throw playbackHttpError(400, 'deletedAt or timestamp is required for a deletion');
    const itemKey = portableItemKey(historyKey, siteKey, vodId);
    const markerKey = JSON.stringify(scope === 'all' ? ['all'] : scope === 'site' ? ['site', siteKey] : ['item', itemKey]);
    const payload = compactObject({
      schema: PLAYBACK_SCHEMA,
      action: 'delete',
      event: 'playback.deleted',
      eventId,
      configKey,
      historyKey,
      siteKey,
      vodId,
      scope,
      deletedAt
    });
    return { kind: 'delete', configKey, eventId, historyKey, siteKey, vodId, scope, deletedAt, itemKey, markerKey, payload };
  }

  if (!siteKey) throw playbackHttpError(400, 'siteKey is required');
  if (!vodId) throw playbackHttpError(400, 'vodId is required');
  const vodName = cleanString(raw.vodName || raw.vod_name || raw.name || raw.title, 2048);
  const episodeName = cleanString(raw.episodeName || raw.episode || raw.episodeTitle || raw.vodRemarks || raw.remarks, 2048);
  const positionMs = positiveNumber(raw.positionMs || raw.position || raw.position_ms || raw.pos);
  const durationMs = positiveNumber(raw.durationMs || raw.duration || raw.duration_ms);
  if (!vodName) throw playbackHttpError(400, 'vodName is required');
  if (!episodeName) throw playbackHttpError(400, 'episodeName is required');
  if (positionMs <= 0) throw playbackHttpError(400, 'positionMs must be greater than 0');
  if (durationMs <= 0) throw playbackHttpError(400, 'durationMs must be greater than 0');
  const updatedAt = positiveTimestamp(raw.updatedAt || raw.updated_at || raw.timestamp || raw.updateTime, now);
  const completed = eventName === 'playback.ended' || booleanValue(raw.completed);
  const suppliedProgress = boundedNumber(raw.progress, 0, 1);
  const payload = compactObject({
    schema: PLAYBACK_SCHEMA,
    action: 'upsert',
    event: eventName || undefined,
    eventId,
    configKey,
    configName: cleanString(raw.configName || raw.config_name, 2048),
    historyKey,
    siteKey,
    siteName: cleanString(raw.siteName || raw.site_name, 2048),
    vodId,
    vodName,
    vodPic: cleanString(raw.vodPic || raw.vod_pic || raw.pic || raw.poster, 8192),
    flag: cleanString(raw.flag || raw.vodFlag || raw.line || raw.source, 2048),
    episodeName,
    episodeUrl: cleanString(raw.episodeUrl || raw.episode_url || raw.url || raw.playUrl, 8192),
    positionMs: Math.min(positionMs, durationMs),
    durationMs,
    progress: suppliedProgress > 0 ? suppliedProgress : Math.min(positionMs, durationMs) / durationMs,
    speed: positiveNumber(raw.speed) || 1,
    completed,
    updatedAt,
    clientKey: cleanString(raw.clientKey || raw.client_key, 256)
  });
  return {
    kind: 'upsert',
    configKey,
    eventId,
    historyKey,
    siteKey,
    vodId,
    itemKey: portableItemKey(historyKey, siteKey, vodId),
    updatedAt,
    payload
  };
}

export function parseCursor(value) {
  if (value == null || String(value).trim() === '') return 0;
  const parsed = Number(String(value).trim());
  if (!Number.isSafeInteger(parsed) || parsed < 0) throw playbackHttpError(400, 'Invalid X-WebHTV-Since cursor');
  return parsed;
}

export function parseLimit(value) {
  if (value == null || String(value).trim() === '') return DEFAULT_LIMIT;
  const text = String(value).trim();
  if (!/^\d+$/.test(text)) return DEFAULT_LIMIT;
  const parsed = Number(text);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) return DEFAULT_LIMIT;
  return Math.min(MAX_LIMIT, parsed);
}

function normalizePlaybackState(input) {
  const state = input && typeof input === 'object' && !Array.isArray(input) ? input : {};
  return {
    schema: 1,
    sequence: safeNonNegativeInteger(state.sequence),
    lastCleanup: safeNonNegativeInteger(state.lastCleanup),
    items: safeRecord(state.items),
    tombstones: safeRecord(state.tombstones),
    events: safeRecord(state.events)
  };
}

function cleanupPlaybackState(state, now) {
  if (now - state.lastCleanup < CLEANUP_INTERVAL_MS) return;
  const cutoff = now - TOMBSTONE_RETENTION_MS;
  for (const [key, item] of Object.entries(state.tombstones)) if (Number(item?.deletedAt || 0) < cutoff) delete state.tombstones[key];
  for (const [key, receivedAt] of Object.entries(state.events)) if (Number(receivedAt || 0) < cutoff) delete state.events[key];
  state.lastCleanup = now;
}

function safeRecord(value) {
  const output = Object.create(null);
  if (!value || typeof value !== 'object' || Array.isArray(value)) return output;
  for (const [key, item] of Object.entries(value)) output[key] = item;
  return output;
}

function nextSequence(state) {
  state.sequence = safeNonNegativeInteger(state.sequence) + 1;
  return state.sequence;
}

function hasEvent(state, eventId) {
  return eventId ? Object.prototype.hasOwnProperty.call(state.events, eventStorageKey(eventId)) : false;
}

function recordEvent(state, eventId, receivedAt) {
  if (eventId) state.events[eventStorageKey(eventId)] = receivedAt;
}

function eventStorageKey(eventId) {
  return `event:${eventId}`;
}

function matchesTombstone(tombstone, event) {
  if (!tombstone) return false;
  if (tombstone.scope === 'all') return true;
  if (tombstone.scope === 'site') return tombstone.siteKey === event.siteKey;
  return (tombstone.siteKey && tombstone.vodId && tombstone.siteKey === event.siteKey && tombstone.vodId === event.vodId)
    || (tombstone.historyKey && tombstone.historyKey === event.historyKey);
}

function matchesDelete(event, item, itemKey) {
  if (event.scope === 'all') return true;
  if (event.scope === 'site') return item.siteKey === event.siteKey;
  return itemKey === event.itemKey || (event.historyKey && item.historyKey === event.historyKey);
}

function extractPlaybackEvents(body) {
  if (Array.isArray(body)) return body;
  if (!body || typeof body !== 'object') return [];
  const changes = firstArray(body, 'changes', 'operations');
  if (changes) return changes.map((item) => inheritPlaybackFields(item, body));

  const result = [];
  const deletions = firstArray(body, 'deleted', 'deletions', 'tombstones', 'removed', 'deletedItems');
  if (deletions) {
    for (const item of deletions) {
      const value = typeof item === 'string' ? { historyKey: item } : item;
      result.push(inheritPlaybackFields({ ...(value || {}), action: 'delete' }, body));
    }
  }
  const items = firstArray(body, 'items', 'records', 'upserts', 'list');
  if (items) for (const item of items) result.push(inheritPlaybackFields(item, body));
  if (result.length) return result;
  if (Array.isArray(body.data)) return body.data.map((item) => inheritPlaybackFields(item, body));
  return [body];
}

function unwrapPlaybackEvent(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return input;
  if (!input.data || typeof input.data !== 'object' || Array.isArray(input.data)) return input;
  return inheritPlaybackFields(input.data, input);
}

function inheritPlaybackFields(input, parent) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return input;
  const output = { ...input };
  for (const key of ['action', 'op', 'operation', 'event', 'eventId', 'deleted', 'scope', 'deletedAt', 'timestamp', 'updatedAt', 'configKey', 'configName']) {
    if (output[key] == null && parent && parent[key] != null && typeof parent[key] !== 'object') output[key] = parent[key];
  }
  return output;
}

async function readPlaybackJson(request) {
  const declared = Number(request.headers.get('content-length') || 0);
  if (declared > MAX_BODY_BYTES) throw playbackHttpError(413, 'Playback payload is too large');
  const text = await request.text();
  if (new TextEncoder().encode(text).byteLength > MAX_BODY_BYTES) throw playbackHttpError(413, 'Playback payload is too large');
  if (!text.trim()) throw playbackHttpError(400, 'Playback payload is empty');
  try {
    return JSON.parse(text);
  } catch {
    throw playbackHttpError(400, 'Invalid JSON body');
  }
}

function requestAliases(request, body = null) {
  const header = String(request.headers.get('x-webhtv-config-aliases') || '').split(',').map((item) => item.trim()).filter(Boolean);
  const bodyAliases = body && !Array.isArray(body) && Array.isArray(body.configAliases) ? body.configAliases.map((item) => String(item || '').trim()).filter(Boolean) : [];
  if (header.length && bodyAliases.length && JSON.stringify(header) !== JSON.stringify(bodyAliases)) throw playbackHttpError(400, 'configAliases does not match X-WebHTV-Config-Aliases');
  return [...new Set([...header, ...bodyAliases])].slice(0, 16);
}

function requireConfigKey(request, body = null) {
  const header = validatedConfigKey(request.headers.get('x-webhtv-config-key'));
  const bodyKey = body && !Array.isArray(body) ? validatedConfigKey(body.configKey || body.config_key) : '';
  if (header && bodyKey && header !== bodyKey) throw playbackHttpError(400, 'configKey does not match X-WebHTV-Config-Key');
  const configKey = header || bodyKey;
  if (!configKey) throw playbackHttpError(400, 'Missing X-WebHTV-Config-Key');
  return configKey;
}

function normalizeConfigKey(value) {
  return String(value == null ? '' : value).trim().toLowerCase();
}

function validatedConfigKey(value, missingMessage = '') {
  const configKey = normalizeConfigKey(value);
  if (configKey.length > 256) throw playbackHttpError(400, 'configKey is too long');
  if (!configKey && missingMessage) throw playbackHttpError(400, missingMessage);
  return configKey;
}

function normalizeScope(value, historyKey, siteKey, vodId) {
  const scope = cleanString(value, 16).toLowerCase();
  if (scope === 'all' || scope === 'site' || scope === 'item') return scope;
  if (historyKey || (siteKey && vodId)) return 'item';
  if (siteKey) return 'site';
  return '';
}

function portableItemKey(historyKey, siteKey, vodId) {
  return JSON.stringify(siteKey && vodId ? ['site-vod', siteKey, vodId] : ['history', historyKey]);
}

function historyParts(historyKey) {
  const parts = String(historyKey || '').split('@@@');
  return { siteKey: parts[0] || '', vodId: parts[1] || '' };
}

function positiveTimestamp(value, fallback) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : fallback;
}

function positiveNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : 0;
}

function boundedNumber(value, min, max) {
  const number = Number(value);
  if (!Number.isFinite(number)) return 0;
  return Math.max(min, Math.min(max, number));
}

function booleanValue(value) {
  if (value === true || value === 1) return true;
  const text = String(value == null ? '' : value).trim().toLowerCase();
  return text === 'true' || text === '1' || text === 'yes';
}

function cleanString(value, maxLength) {
  return String(value == null ? '' : value).trim().slice(0, maxLength);
}

function compactObject(value) {
  return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== undefined && item !== ''));
}

function firstArray(object, ...keys) {
  for (const key of keys) if (Array.isArray(object[key])) return object[key];
  return null;
}

function resultFor(event, action, sequence, message) {
  return compactObject({
    action,
    sequence,
    message,
    eventId: event.eventId,
    configKey: event.configKey,
    historyKey: event.historyKey,
    siteKey: event.siteKey,
    vodId: event.vodId,
    updatedAt: event.updatedAt,
    deletedAt: event.deletedAt
  });
}

function playbackToken(request) {
  const direct = String(request.headers.get('x-webhtv-token') || '').trim();
  if (direct) return direct;
  const authorization = request.headers.get('authorization') || '';
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  return match ? String(match[1] || '').trim() : '';
}

async function playbackSpaceKey(token, configKey, configType = 'vod') {
  const [tokenHash, configHash] = await Promise.all([sha256(token), sha256(configKey)]);
  const type = normalizeConfigType(configType);
  return type === 'vod' ? `${tokenHash}:${configHash}` : `${tokenHash}:${type}:${configHash}`;
}

function basePlaybackPath(pathname) {
  const path = normalizePath(pathname);
  for (const base of PLAYBACK_SYNC_PATHS) if (path === base || path === `${base}/status`) return base;
  return '/api/playback/sync';
}

function normalizePath(pathname) {
  const path = String(pathname || '').replace(/\/+$/, '');
  return path || '/';
}

async function sha256(value) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)].map((item) => item.toString(16).padStart(2, '0')).join('');
}

function safeNonNegativeInteger(value) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 ? number : 0;
}

function cloneJson(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value));
}

async function loadPlaybackStore(store, spaceKey) {
  try {
    return await store.load(spaceKey);
  } catch (error) {
    console.error('Playback storage read failed', error && error.message ? error.message : error);
    throw playbackHttpError(503, 'Playback persistent storage is unavailable');
  }
}

async function savePlaybackStore(store, spaceKey, version, state) {
  try {
    return await store.compareAndSet(spaceKey, version, state);
  } catch (error) {
    console.error('Playback storage write failed', error && error.message ? error.message : error);
    throw playbackHttpError(503, 'Playback persistent storage is unavailable');
  }
}

function playbackJson(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store'
    }
  });
}

function playbackError(status, message) {
  return playbackCors(playbackJson({ ok: false, error: message }, status));
}

function playbackCors(response) {
  const headers = new Headers(response.headers);
  headers.set('access-control-allow-origin', '*');
  headers.set('access-control-allow-methods', 'GET,POST,OPTIONS');
  headers.set('access-control-allow-headers', [
    'authorization',
    'content-type',
    'idempotency-key',
    'x-webhtv-token',
    'x-webhtv-config-key',
    'x-webhtv-config-name',
    'x-webhtv-config-aliases',
    'x-webhtv-config-type',
    'x-webhtv-identity-version',
    'x-webhtv-address-match-version',
    'x-webhtv-request-id',
    'x-webhtv-timestamp',
    'x-webhtv-since',
    'x-webhtv-limit',
    'x-webhtv-webhook-id',
    'x-webhtv-dedupe-key'
  ].join(','));
  headers.set('access-control-expose-headers', '*');
  headers.set('access-control-max-age', '86400');
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}

function playbackHttpError(status, message) {
  const error = new Error(message);
  error.status = status;
  return error;
}
