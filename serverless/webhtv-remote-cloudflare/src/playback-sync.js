import { parseIdentityRequest, resolveIdentity, resolveConfigKey, normalizeConfigType, identityCapabilities } from '../../playback-identity-fixtures/identity.js';
const PLAYBACK_SYNC_PATHS = new Set(['/api/playback/sync', '/playback/sync']);
const IDENTITY_RESOLVE_PATHS = new Set(['/api/playback/identity/resolve', '/playback/identity/resolve']);
const TOMBSTONE_RETENTION_MS = 90 * 24 * 60 * 60 * 1000;
const CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000;
const MAX_BODY_BYTES = 128 * 1024;
const MAX_BATCH_ITEMS = 100;
const DEFAULT_LIMIT = 100;
const MAX_LIMIT = 1000;
const PLAYBACK_SCHEMA = 'webhtv.playback.v1';

export function isPlaybackSyncPath(pathname) {
  const path = normalizePath(pathname);
  if (PLAYBACK_SYNC_PATHS.has(path)) return true;
  for (const base of PLAYBACK_SYNC_PATHS) if (path === `${base}/status`) return true;
  return IDENTITY_RESOLVE_PATHS.has(path);
}

export async function handlePlaybackSyncGateway(request, env) {
  if (request.method === 'OPTIONS') return playbackCors(new Response(null, { status: 204 }));
  if (!env || !env.PLAYBACK_DO) return playbackError(503, 'PLAYBACK_DO is not configured');

  const token = playbackToken(request);
  if (!token) return playbackError(401, 'Missing X-WebHTV-Token');
  if (token.length > 512) return playbackError(400, 'X-WebHTV-Token is too long');

  const namespace = `user-${await sha256(token)}`;
  return env.PLAYBACK_DO.getByName(namespace).fetch(request);
}

export class WebHTVPlaybackSyncDO {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.sql = state.storage.sql;
    this.ready = state.blockConcurrencyWhile(async () => this.migrate());
  }

  async fetch(request) {
    await this.ready;
    if (request.method === 'OPTIONS') return playbackCors(new Response(null, { status: 204 }));
    try {
      this.cleanup();
      const url = new URL(request.url);
      const path = normalizePath(url.pathname);
      if (IDENTITY_RESOLVE_PATHS.has(path)) {
        if (request.method !== 'POST') return playbackError(405, 'Method not allowed');
        const input = parseIdentityRequest(await readPlaybackJson(request), request.headers);
        const result = await resolveIdentity(this.identityStore(request), playbackToken(request), input);
        return playbackCors(playbackJson(result.body, result.status));
      }
      const status = [...PLAYBACK_SYNC_PATHS].some((base) => path === `${base}/status`);
      if (status) {
        if (request.method === 'GET') return playbackCors(await this.status(request, url));
        return playbackError(405, 'Method not allowed');
      }
      if (!PLAYBACK_SYNC_PATHS.has(path)) return playbackError(404, 'Not found');
      if (request.method === 'GET') return playbackCors(await this.pull(request, url));
      if (request.method === 'POST') return playbackCors(await this.ingest(request));
      return playbackError(405, 'Method not allowed');
    } catch (error) {
      const status = Number(error && error.status) || 500;
      if (status >= 400 && status < 500) return playbackError(status, error && error.message ? error.message : 'Invalid request');
      console.error('Playback sync request failed', error && error.stack ? error.stack : error);
      return playbackError(500, 'Internal server error');
    }
  }

  migrate() {
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS playback_meta (
        key TEXT PRIMARY KEY,
        value INTEGER NOT NULL
      );
      INSERT OR IGNORE INTO playback_meta (key, value) VALUES ('sequence', 0);
      INSERT OR IGNORE INTO playback_meta (key, value) VALUES ('last_cleanup', 0);

      CREATE TABLE IF NOT EXISTS playback_items (
        config_key TEXT NOT NULL,
        item_key TEXT NOT NULL,
        history_key TEXT NOT NULL,
        site_key TEXT NOT NULL,
        vod_id TEXT NOT NULL,
        updated_at INTEGER NOT NULL,
        seq INTEGER NOT NULL,
        payload TEXT NOT NULL,
        PRIMARY KEY (config_key, item_key)
      );
      CREATE INDEX IF NOT EXISTS idx_playback_items_config_seq
        ON playback_items (config_key, seq);

      CREATE TABLE IF NOT EXISTS playback_tombstones (
        config_key TEXT NOT NULL,
        marker_key TEXT NOT NULL,
        scope TEXT NOT NULL,
        history_key TEXT NOT NULL,
        site_key TEXT NOT NULL,
        vod_id TEXT NOT NULL,
        deleted_at INTEGER NOT NULL,
        seq INTEGER NOT NULL,
        payload TEXT NOT NULL,
        PRIMARY KEY (config_key, marker_key)
      );
      CREATE INDEX IF NOT EXISTS idx_playback_tombstones_config_seq
        ON playback_tombstones (config_key, seq);
      CREATE INDEX IF NOT EXISTS idx_playback_tombstones_deleted_at
        ON playback_tombstones (deleted_at);

      CREATE TABLE IF NOT EXISTS playback_events (
        config_key TEXT NOT NULL,
        event_id TEXT NOT NULL,
        received_at INTEGER NOT NULL,
        PRIMARY KEY (config_key, event_id)
      );
      CREATE INDEX IF NOT EXISTS idx_playback_events_received_at
        ON playback_events (received_at);
      CREATE TABLE IF NOT EXISTS playback_identity_registry (
        registry_key TEXT PRIMARY KEY,
        version INTEGER NOT NULL DEFAULT 0,
        state TEXT NOT NULL
      );
    `);
  }

  async ingest(request) {
    const body = await readPlaybackJson(request);
    const submittedConfigKey = requireConfigKey(request, body);
    const configType = normalizeConfigType(request.headers.get('x-webhtv-config-type') || body.configType || 'vod');
    const configKey = await this.resolvePlaybackConfigKey(request, submittedConfigKey, configType, requestAliases(request, body));
    const rawEvents = extractPlaybackEvents(body);
    if (!rawEvents.length) throw playbackHttpError(400, 'Playback event is empty');
    if (rawEvents.length > MAX_BATCH_ITEMS) throw playbackHttpError(413, `Too many playback events; maximum is ${MAX_BATCH_ITEMS}`);

    const sharedEventId = rawEvents.length === 1
      ? cleanString(request.headers.get('x-webhtv-webhook-id') || request.headers.get('idempotency-key'), 160)
      : '';
    const now = Date.now();
    // Validate the entire batch before applying any item so a malformed item cannot
    // leave earlier records committed while the request itself returns an error.
    const storageConfigKey = scopedConfigKey(configType, configKey);
    const events = rawEvents.map((raw) => normalizePlaybackEvent(raw, configKey, now, sharedEventId));
    for (const event of events) event.storageConfigKey = storageConfigKey;
    const results = events.map((event) => event.kind === 'delete' ? this.applyDelete(event, now) : this.applyUpsert(event, now));
    return playbackJson({
      ok: true,
      received: results.length,
      applied: results.filter((item) => item.action === 'created' || item.action === 'updated' || item.action === 'deleted').length,
      skipped: results.filter((item) => item.action === 'skipped' || item.action === 'duplicate').length,
      results
    });
  }

  async pull(request, url) {
    const submittedConfigKey = requireConfigKey(request);
    const configType = normalizeConfigType(request.headers.get('x-webhtv-config-type') || url.searchParams.get('configType'));
    const configKey = await this.resolvePlaybackConfigKey(request, submittedConfigKey, configType, requestAliases(request));
    const storageConfigKey = scopedConfigKey(configType, configKey);
    const since = parseCursor(request.headers.get('x-webhtv-since') || url.searchParams.get('since'));
    const limit = parseLimit(request.headers.get('x-webhtv-limit') || url.searchParams.get('limit'));
    const cutoff = Date.now() - TOMBSTONE_RETENTION_MS;
    const rows = this.sql.exec(`
      SELECT seq, kind, payload FROM (
        SELECT seq, 'upsert' AS kind, payload
          FROM playback_items
         WHERE config_key = ? AND seq > ?
        UNION ALL
        SELECT seq, 'delete' AS kind, payload
          FROM playback_tombstones
         WHERE config_key = ? AND deleted_at >= ? AND seq > ?
      )
      ORDER BY seq ASC
      LIMIT ?
    `, storageConfigKey, since, storageConfigKey, cutoff, since, limit + 1).toArray();

    const hasMore = rows.length > limit;
    const selected = hasMore ? rows.slice(0, limit) : rows;
    const changes = [];
    for (const row of selected) {
      try {
        changes.push(JSON.parse(row.payload));
      } catch {
        // Ignore an individually corrupted row without breaking all other records.
      }
    }
    const nextSince = selected.length ? String(selected[selected.length - 1].seq) : String(since);
    return playbackJson({ changes, nextSince, hasMore });
  }

  async status(request, url) {
    const submittedConfigKey = requireConfigKey(request);
    const configType = normalizeConfigType(request.headers.get('x-webhtv-config-type') || url.searchParams.get('configType'));
    const configKey = await this.resolvePlaybackConfigKey(request, submittedConfigKey, configType, requestAliases(request));
    const storageConfigKey = scopedConfigKey(configType, configKey);
    const cutoff = Date.now() - TOMBSTONE_RETENTION_MS;
    const items = this.sql.exec('SELECT COUNT(*) AS count FROM playback_items WHERE config_key = ?', storageConfigKey).one();
    const tombstones = this.sql.exec('SELECT COUNT(*) AS count FROM playback_tombstones WHERE config_key = ? AND deleted_at >= ?', storageConfigKey, cutoff).one();
    const latest = this.sql.exec(`
      SELECT COALESCE(MAX(seq), 0) AS seq FROM (
        SELECT seq FROM playback_items WHERE config_key = ?
        UNION ALL
        SELECT seq FROM playback_tombstones WHERE config_key = ? AND deleted_at >= ?
      )
    `, storageConfigKey, storageConfigKey, cutoff).one();
    return playbackJson({
      ok: true,
      configKey,
      identityProtocol: 'webhtv.playback.identity.v1',
      addressMatchVersion: 1,
      capabilities: identityCapabilities(),
      items: Number(items.count || 0),
      tombstones: Number(tombstones.count || 0),
      nextSince: String(latest.seq || 0),
      retentionDays: 90,
      endpoint: `${url.origin}${basePlaybackPath(url.pathname)}`
    });
  }

  identityStore(request) {
    const token = playbackToken(request);
    const sql = this.sql;
    return {
      persistent: true,
      isConfigured: () => true,
      async load(key) {
        const row = firstRow(sql.exec('SELECT version, state FROM playback_identity_registry WHERE registry_key = ?', key));
        if (!row) return { version: null, state: null };
        let state = null;
        try { state = JSON.parse(row.state); } catch { throw new Error('Invalid identity registry state'); }
        return { version: Number(row.version || 0), state };
      },
      async compareAndSet(key, version, state) {
        const current = firstRow(sql.exec('SELECT version FROM playback_identity_registry WHERE registry_key = ?', key));
        const currentVersion = current ? Number(current.version || 0) : null;
        if (currentVersion !== version && !(version == null && currentVersion == null)) return false;
        const nextVersion = currentVersion == null ? 1 : currentVersion + 1;
        sql.exec('INSERT INTO playback_identity_registry (registry_key, version, state) VALUES (?, ?, ?) ON CONFLICT(registry_key) DO UPDATE SET version = excluded.version, state = excluded.state', key, nextVersion, JSON.stringify(state));
        return true;
      },
      migrateIdentitySpaces: async (identityToken, configType, canonicalInterfaceKey, sourceKeys) => this.migrateIdentitySpaces(identityToken, configType, canonicalInterfaceKey, sourceKeys)
    };
  }

  async resolvePlaybackConfigKey(request, submittedConfigKey, configType, aliases = []) {
    return resolveConfigKey(this.identityStore(request), playbackToken(request), configType, submittedConfigKey, aliases);
  }

  migrateIdentitySpaces(token, configType, canonicalInterfaceKey, sourceKeys) {
    const sources = [...new Set((sourceKeys || [])
      .filter((item) => item && item !== canonicalInterfaceKey)
      .map((item) => scopedConfigKey(configType, item)))];
    const canonicalStorageKey = scopedConfigKey(configType, canonicalInterfaceKey);
    if (!sources.length) return { migrated: false, pending: false, resetSince: false };
    let migrated = false;
    this.state.storage.transactionSync(() => {
      for (const source of sources) {
        const items = this.sql.exec('SELECT item_key, history_key, site_key, vod_id, updated_at, payload FROM playback_items WHERE config_key = ?', source).toArray();
        for (const item of items) {
          const current = firstRow(this.sql.exec('SELECT updated_at FROM playback_items WHERE config_key = ? AND item_key = ?', canonicalStorageKey, item.item_key));
          if (current && Number(current.updated_at || 0) >= Number(item.updated_at || 0)) continue;
          const seq = this.nextSequence();
          this.sql.exec('INSERT INTO playback_items (config_key, item_key, history_key, site_key, vod_id, updated_at, seq, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(config_key, item_key) DO UPDATE SET history_key = excluded.history_key, site_key = excluded.site_key, vod_id = excluded.vod_id, updated_at = excluded.updated_at, seq = excluded.seq, payload = excluded.payload', canonicalStorageKey, item.item_key, item.history_key, item.site_key, item.vod_id, item.updated_at, seq, item.payload);
          migrated = true;
        }
        const tombstones = this.sql.exec('SELECT marker_key, scope, history_key, site_key, vod_id, deleted_at, payload FROM playback_tombstones WHERE config_key = ?', source).toArray();
        for (const item of tombstones) {
          const current = firstRow(this.sql.exec('SELECT deleted_at FROM playback_tombstones WHERE config_key = ? AND marker_key = ?', canonicalStorageKey, item.marker_key));
          if (current && Number(current.deleted_at || 0) >= Number(item.deleted_at || 0)) continue;
          const seq = this.nextSequence();
          this.sql.exec('INSERT INTO playback_tombstones (config_key, marker_key, scope, history_key, site_key, vod_id, deleted_at, seq, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(config_key, marker_key) DO UPDATE SET scope = excluded.scope, history_key = excluded.history_key, site_key = excluded.site_key, vod_id = excluded.vod_id, deleted_at = excluded.deleted_at, seq = excluded.seq, payload = excluded.payload', canonicalStorageKey, item.marker_key, item.scope, item.history_key, item.site_key, item.vod_id, item.deleted_at, seq, item.payload);
          this.sql.exec('DELETE FROM playback_items WHERE config_key = ? AND updated_at <= ?', canonicalStorageKey, item.deleted_at);
          migrated = true;
        }
        const events = this.sql.exec('SELECT event_id, received_at FROM playback_events WHERE config_key = ?', source).toArray();
        for (const event of events) this.sql.exec('INSERT OR IGNORE INTO playback_events (config_key, event_id, received_at) VALUES (?, ?, ?)', canonicalStorageKey, event.event_id, event.received_at);
      }
    });
    return { migrated, pending: false, resetSince: migrated };
  }

  nextSequence() {
    const row = firstRow(this.sql.exec("SELECT value FROM playback_meta WHERE key = 'sequence'"));
    const next = Number(row?.value || 0) + 1;
    this.sql.exec("UPDATE playback_meta SET value = ? WHERE key = 'sequence'", next);
    return next;
  }

  applyUpsert(event, receivedAt) {
    const storageConfigKey = event.storageConfigKey || event.configKey;
    return this.state.storage.transactionSync(() => {
      if (event.eventId && this.hasEvent(storageConfigKey, event.eventId)) {
        return resultFor(event, 'duplicate', 0, 'Event already processed');
      }

      const current = firstRow(this.sql.exec(
        'SELECT updated_at, seq FROM playback_items WHERE config_key = ? AND item_key = ?',
        storageConfigKey,
        event.itemKey
      ));
      const tombstone = firstRow(this.sql.exec(`
        SELECT MAX(deleted_at) AS deleted_at, MAX(seq) AS seq
          FROM playback_tombstones
         WHERE config_key = ? AND (
           scope = 'all'
           OR (scope = 'site' AND site_key = ?)
           OR (scope = 'item' AND ((site_key = ? AND vod_id = ?) OR (history_key <> '' AND history_key = ?)))
         )
      `, storageConfigKey, event.siteKey, event.siteKey, event.vodId, event.historyKey));
      const deletedAt = Number(tombstone?.deleted_at || 0);
      if (deletedAt > 0 && event.updatedAt <= deletedAt) {
        this.recordEvent(storageConfigKey, event.eventId, receivedAt);
        return resultFor(event, 'skipped', Number(tombstone?.seq || 0), 'A newer deletion exists');
      }
      if (current && event.updatedAt <= Number(current.updated_at || 0)) {
        this.recordEvent(storageConfigKey, event.eventId, receivedAt);
        return resultFor(event, 'skipped', Number(current.seq || 0), 'A newer progress record exists');
      }

      const seq = this.nextSequence();
      const payload = JSON.stringify(event.payload);
      this.sql.exec(`
        INSERT INTO playback_items
          (config_key, item_key, history_key, site_key, vod_id, updated_at, seq, payload)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(config_key, item_key) DO UPDATE SET
          history_key = excluded.history_key,
          site_key = excluded.site_key,
          vod_id = excluded.vod_id,
          updated_at = excluded.updated_at,
          seq = excluded.seq,
          payload = excluded.payload
      `, storageConfigKey, event.itemKey, event.historyKey, event.siteKey, event.vodId, event.updatedAt, seq, payload);
      this.recordEvent(storageConfigKey, event.eventId, receivedAt);
      return resultFor(event, current ? 'updated' : 'created', seq, '');
    });
  }

  applyDelete(event, receivedAt) {
    const storageConfigKey = event.storageConfigKey || event.configKey;
    return this.state.storage.transactionSync(() => {
      if (event.eventId && this.hasEvent(storageConfigKey, event.eventId)) {
        return resultFor(event, 'duplicate', 0, 'Event already processed');
      }

      const current = firstRow(this.sql.exec(
        'SELECT deleted_at, seq FROM playback_tombstones WHERE config_key = ? AND marker_key = ?',
        storageConfigKey,
        event.markerKey
      ));
      if (current && event.deletedAt <= Number(current.deleted_at || 0)) {
        this.recordEvent(storageConfigKey, event.eventId, receivedAt);
        return resultFor(event, 'skipped', Number(current.seq || 0), 'A newer deletion exists');
      }

      const seq = this.nextSequence();
      const payload = JSON.stringify(event.payload);
      this.sql.exec(`
        INSERT INTO playback_tombstones
          (config_key, marker_key, scope, history_key, site_key, vod_id, deleted_at, seq, payload)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(config_key, marker_key) DO UPDATE SET
          scope = excluded.scope,
          history_key = excluded.history_key,
          site_key = excluded.site_key,
          vod_id = excluded.vod_id,
          deleted_at = excluded.deleted_at,
          seq = excluded.seq,
          payload = excluded.payload
      `, storageConfigKey, event.markerKey, event.scope, event.historyKey, event.siteKey, event.vodId, event.deletedAt, seq, payload);

      let deletedRows = 0;
      if (event.scope === 'all') {
        deletedRows = this.sql.exec(
          'DELETE FROM playback_items WHERE config_key = ? AND updated_at <= ?',
          storageConfigKey,
          event.deletedAt
        ).rowsWritten;
      } else if (event.scope === 'site') {
        deletedRows = this.sql.exec(
          'DELETE FROM playback_items WHERE config_key = ? AND site_key = ? AND updated_at <= ?',
          storageConfigKey,
          event.siteKey,
          event.deletedAt
        ).rowsWritten;
      } else {
        deletedRows = this.sql.exec(`
          DELETE FROM playback_items
           WHERE config_key = ? AND updated_at <= ?
             AND (item_key = ? OR (history_key <> '' AND history_key = ?))
        `, storageConfigKey, event.deletedAt, event.itemKey, event.historyKey).rowsWritten;
      }
      this.recordEvent(storageConfigKey, event.eventId, receivedAt);
      return { ...resultFor(event, 'deleted', seq, ''), affected: Number(deletedRows || 0) };
    });
  }

  nextSequence() {
    this.sql.exec("UPDATE playback_meta SET value = value + 1 WHERE key = 'sequence'");
    return Number(this.sql.exec("SELECT value FROM playback_meta WHERE key = 'sequence'").one().value || 0);
  }

  hasEvent(configKey, eventId) {
    if (!eventId) return false;
    return Boolean(firstRow(this.sql.exec(
      'SELECT 1 AS found FROM playback_events WHERE config_key = ? AND event_id = ? LIMIT 1',
      configKey,
      eventId
    )));
  }

  recordEvent(configKey, eventId, receivedAt) {
    if (!eventId) return;
    this.sql.exec(
      'INSERT OR IGNORE INTO playback_events (config_key, event_id, received_at) VALUES (?, ?, ?)',
      configKey,
      eventId,
      receivedAt
    );
  }

  cleanup() {
    const now = Date.now();
    const last = Number(this.sql.exec("SELECT value FROM playback_meta WHERE key = 'last_cleanup'").one().value || 0);
    if (now - last < CLEANUP_INTERVAL_MS) return;
    const cutoff = now - TOMBSTONE_RETENTION_MS;
    this.state.storage.transactionSync(() => {
      this.sql.exec('DELETE FROM playback_tombstones WHERE deleted_at < ?', cutoff);
      this.sql.exec('DELETE FROM playback_events WHERE received_at < ?', cutoff);
      this.sql.exec("UPDATE playback_meta SET value = ? WHERE key = 'last_cleanup'", now);
    });
  }
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
    if (requestedScope && !['all', 'site', 'item'].includes(requestedScope)) {
      throw playbackHttpError(400, 'scope must be item, site, or all');
    }
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
    const markerKey = scope === 'all' ? 'all' : scope === 'site' ? `site\n${siteKey}` : `item\n${itemKey}`;
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
    siteName: cleanString(raw.siteName, 2048),
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
    clientKey: cleanString(raw.clientKey, 256)
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
      result.push(inheritPlaybackFields({ ...value, action: 'delete' }, body));
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

function scopedConfigKey(configType, configKey) {
  const type = normalizeConfigType(configType);
  return type === 'vod' ? configKey : `${type}:${configKey}`;
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
  if (siteKey && vodId) return `${siteKey}\n${vodId}`;
  return `history\n${historyKey}`;
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

function firstRow(cursor) {
  const result = cursor.next();
  return result.done ? null : result.value;
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
