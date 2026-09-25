/**
 * Protocol v1 identity/address helpers shared by the built-in JS deployments.
 * The module deliberately never accepts or stores a complete URL: clients send
 * only the opaque SHA-256 address clues defined by the protocol.
 */

export const IDENTITY_SCHEMA = 'webhtv.playback.identity.v1';
export const IDENTITY_VERSION = '2';
export const ADDRESS_MATCH_VERSION = '1';
export const IDENTITY_CAPABILITIES = Object.freeze({
  playbackSync: true,
  identityResolve: true,
  identityAliases: true,
  legacyUrlHashMigration: true,
  identityMerge: true
});
const MAX_CAS_ATTEMPTS = 5;
const MAX_KEYS = 32;
const MAX_KEY_LENGTH = 128;
const KEY_PATTERN = /^[a-z0-9._:-]{1,128}$/;
const NETWORK_SCHEMES = new Set(['http', 'https']);

export function identityCapabilities() {
  return { ...IDENTITY_CAPABILITIES };
}

export function normalizeConfigType(value) {
  const text = String(value == null ? '' : value).trim().toLowerCase();
  if (text === '0') return 'vod';
  if (text === '1') return 'live';
  if (text === '2') return 'wall';
  return text === 'live' || text === 'wall' ? text : 'vod';
}

export function normalizeIdentityKey(value, label = 'identity key') {
  const text = String(value == null ? '' : value).trim().toLowerCase();
  if (!text || text.length > MAX_KEY_LENGTH || !KEY_PATTERN.test(text)) {
    const error = new Error(`${label} is invalid`);
    error.status = 400;
    throw error;
  }
  return text;
}

export function normalizeOptionalKey(value, label = 'identity key') {
  const text = String(value == null ? '' : value).trim().toLowerCase();
  if (!text) return '';
  return normalizeIdentityKey(text, label);
}

export function normalizeKeyList(value, label, max = MAX_KEYS) {
  if (value == null) return [];
  if (!Array.isArray(value)) {
    const error = new Error(`${label} must be an array`);
    error.status = 400;
    throw error;
  }
  if (value.length > max) {
    const error = new Error(`${label} has too many entries`);
    error.status = 400;
    throw error;
  }
  const result = [];
  for (const item of value) {
    const key = normalizeOptionalKey(item, label);
    if (key && !result.includes(key)) result.push(key);
  }
  return result;
}

export function canonicalizeAddress(value) {
  const source = String(value == null ? '' : value).trim();
  if (!source) return null;
  let url;
  try {
    url = new URL(source);
  } catch {
    return null;
  }
  const scheme = url.protocol.slice(0, -1).toLowerCase();
  if (!NETWORK_SCHEMES.has(scheme) || url.username || url.password || !url.hostname) return null;
  const host = url.hostname.toLowerCase();
  let port = url.port || '';
  if ((scheme === 'http' && port === '80') || (scheme === 'https' && port === '443')) port = '';
  const path = normalizePath(url.pathname || '/');
  const query = normalizePercentEncoding(url.search.startsWith('?') ? url.search.slice(1) : url.search);
  return { scheme, host, port, path, query };
}

export function normalizePath(path) {
  let value = String(path || '/');
  if (!value.startsWith('/')) value = `/${value}`;
  const trailing = value.length > 1 && value.endsWith('/');
  const parts = value.split('/');
  const output = [];
  for (const part of parts) {
    if (part === '' || part === '.') continue;
    if (part === '..') {
      if (output.length) output.pop();
      continue;
    }
    output.push(part);
  }
  value = `/${output.join('/')}`;
  if (!value) value = '/';
  if (trailing && value !== '/') value += '/';
  return normalizePercentEncoding(value);
}

export function normalizePercentEncoding(value) {
  return String(value == null ? '' : value).replace(/%([0-9a-fA-F]{2})/g, (_, hex) => {
    const byte = Number.parseInt(hex, 16);
    const char = String.fromCharCode(byte);
    return isUnreserved(byte) ? char : `%${hex.toUpperCase()}`;
  });
}

export function addressMaterial(configType, address, kind) {
  const type = normalizeConfigType(configType);
  if (kind === 'strict') return ['webhtv.address.strict.v1', type, address.scheme, address.host, address.port, address.path, address.query].join('\n');
  if (kind === 'endpoint') return ['webhtv.address.endpoint.v1', type, address.host, address.port, address.path, address.query].join('\n');
  return ['webhtv.address.host.v1', type, address.host, address.port].join('\n');
}

export async function sha256(value) {
  const bytes = new TextEncoder().encode(String(value == null ? '' : value));
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)].map((item) => item.toString(16).padStart(2, '0')).join('');
}

export async function legacyConfigKey(url) {
  const value = String(url == null ? '' : url).trim();
  return value ? sha256(value) : '';
}

export async function addressKeys(configType, urls = []) {
  const strictAddressKeys = [];
  const endpointMatchKeys = [];
  const hostMatchKeys = [];
  const legacyConfigKeys = [];
  const seenUrls = new Set();
  for (const raw of Array.isArray(urls) ? urls : []) {
    const value = String(raw == null ? '' : raw).trim();
    if (!value || seenUrls.has(value)) continue;
    seenUrls.add(value);
    const legacy = await legacyConfigKey(value);
    if (legacy) legacyConfigKeys.push(legacy);
    const address = canonicalizeAddress(value);
    if (!address) continue;
    const strict = await sha256(addressMaterial(configType, address, 'strict'));
    const endpoint = await sha256(addressMaterial(configType, address, 'endpoint'));
    const host = await sha256(addressMaterial(configType, address, 'host'));
    if (!strictAddressKeys.includes(strict)) strictAddressKeys.push(strict);
    if (!endpointMatchKeys.includes(endpoint)) endpointMatchKeys.push(endpoint);
    if (!hostMatchKeys.includes(host)) hostMatchKeys.push(host);
  }
  return { strictAddressKeys, endpointMatchKeys, hostMatchKeys, legacyConfigKeys };
}

export function parseIdentityRequest(body, headers = new Headers()) {
  const source = body && typeof body === 'object' && !Array.isArray(body) ? body : {};
  const configType = normalizeConfigType(source.configType || headers.get('x-webhtv-config-type') || 'vod');
  const interfaceKey = normalizeIdentityKey(
    source.interfaceKey || headers.get('x-webhtv-config-key') || '',
    'interfaceKey'
  );
  const sourceDataState = normalizeSourceDataState(source.sourceDataState || source.source_data_state);
  return {
    schema: String(source.schema || '').trim() || IDENTITY_SCHEMA,
    operation: String(source.operation || '').trim().toLowerCase() || 'resolve',
    configType,
    interfaceKey,
    strictAddressKeys: normalizeKeyList(source.strictAddressKeys, 'strictAddressKeys'),
    endpointMatchKeys: normalizeKeyList(source.endpointMatchKeys, 'endpointMatchKeys'),
    hostMatchKeys: normalizeKeyList(source.hostMatchKeys, 'hostMatchKeys'),
    legacyConfigKeys: normalizeKeyList(source.legacyConfigKeys, 'legacyConfigKeys'),
    sourceDataState,
    confirm: Boolean(source.confirm),
    client: cleanText(source.client, 64),
    appVersion: cleanText(source.appVersion, 64),
    requestId: cleanText(source.requestId || headers.get('x-webhtv-request-id'), 160)
  };
}

export function normalizeSourceDataState(value) {
  const text = String(value == null ? '' : value).trim().toLowerCase();
  if (text === 'empty' || text === 'has_data' || text === 'unknown') return text;
  return 'unknown';
}

export async function identityRegistryKey(token, configType) {
  return `webhtv:playback:identity:v1:${await sha256(token)}:${normalizeConfigType(configType)}`;
}

export function newIdentityRegistry(input = {}) {
  input = input && typeof input === 'object' && !Array.isArray(input) ? input : {};
  return {
    schema: 1,
    epoch: Number.isSafeInteger(input.epoch) && input.epoch >= 0 ? input.epoch : 0,
    identities: safeObject(input.identities),
    aliases: safeObject(input.aliases),
    requests: safeObject(input.requests),
    updatedAt: Number.isSafeInteger(input.updatedAt) ? input.updatedAt : 0
  };
}

export function normalizeIdentityRegistry(value) {
  const registry = newIdentityRegistry(value);
  for (const [key, identity] of Object.entries(registry.identities)) {
    if (!KEY_PATTERN.test(key)) {
      delete registry.identities[key];
      continue;
    }
    registry.identities[key] = normalizeIdentity(identity, key);
  }
  for (const [key, alias] of Object.entries(registry.aliases)) {
    const canonical = normalizeOptionalKey(alias && alias.canonicalInterfaceKey);
    if (!canonical || !registry.identities[canonical]) delete registry.aliases[key];
    else registry.aliases[key] = { canonicalInterfaceKey: canonical, kind: cleanText(alias.kind, 24) || 'alias' };
  }
  for (const [key, entry] of Object.entries(registry.requests)) {
    if (!KEY_PATTERN.test(key) && !/^[a-zA-Z0-9._:-]{1,160}$/.test(key)) delete registry.requests[key];
    else if (!entry || typeof entry !== 'object' || !entry.body) delete registry.requests[key];
  }
  return registry;
}

export async function loadIdentityRegistry(store, token, configType) {
  const key = await identityRegistryKey(token, configType);
  const snapshot = await store.load(key);
  return { key, version: snapshot.version, registry: normalizeIdentityRegistry(snapshot.state) };
}

export async function resolveIdentity(store, token, input) {
  if (input.schema !== IDENTITY_SCHEMA) return identityResponse('invalid', input, { error: 'Unsupported identity schema' }, 400);
  if (input.operation !== 'resolve') return identityResponse('invalid', input, { error: 'Unsupported identity operation' }, 400);
  const registryKey = await identityRegistryKey(token, input.configType);
  for (let attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
    const snapshot = await store.load(registryKey);
    const registry = normalizeIdentityRegistry(snapshot.state);
    if (input.requestId && registry.requests[input.requestId]) {
      const cached = registry.requests[input.requestId];
      return { status: Number(cached.status || 200), body: cloneJson(cached.body) };
    }
    const resolution = chooseIdentity(registry, input);
    if (resolution.action === 'conflict') return cacheIdentityResponse(store, registryKey, snapshot, registry, input, identityResponse('conflict', input, resolution, 409));
    if (resolution.action === 'confirm_required') return cacheIdentityResponse(store, registryKey, snapshot, registry, input, identityResponse('confirm_required', input, resolution, 200));
    const next = cloneJson(registry);
    const now = Date.now();
    let identity = next.identities[resolution.canonicalInterfaceKey];
    if (!identity) {
      identity = normalizeIdentity({}, resolution.canonicalInterfaceKey);
      identity.createdAt = now;
      next.identities[resolution.canonicalInterfaceKey] = identity;
    }
    addIdentityKeys(next, identity, input);
    next.epoch += 1;
    next.updatedAt = now;
    if ((resolution.action === 'adopt' || resolution.action === 'merge') && input.interfaceKey !== resolution.canonicalInterfaceKey) {
      addAlias(next, input.interfaceKey, resolution.canonicalInterfaceKey, 'interface');
    }
    for (const key of [...identity.strictAddressKeys, ...identity.endpointMatchKeys, ...identity.hostMatchKeys, ...identity.legacyConfigKeys]) {
      addAlias(next, key, resolution.canonicalInterfaceKey, keyKind(identity, key));
    }
    if (await store.compareAndSet(registryKey, snapshot.version, next)) {
      let migration = { migrated: false, pending: false, resetSince: false };
      try {
        migration = typeof store.migrateIdentitySpaces === 'function'
          ? await store.migrateIdentitySpaces(token, input.configType, resolution.canonicalInterfaceKey, [...input.legacyConfigKeys, input.interfaceKey])
          : await migrateIdentitySpaces(
            store,
            token,
            input.configType,
            resolution.canonicalInterfaceKey,
            [...input.legacyConfigKeys, input.interfaceKey]
          );
      } catch {
        migration.pending = true;
      }
      const action = migration.pending ? 'migration_pending' : resolution.action;
      const response = identityResponse(action, input, {
        ...resolution,
        migrationRequired: migration.pending,
        migrationDone: migration.migrated,
        resetSince: migration.resetSince,
        nextSince: migration.resetSince ? '0' : '',
        identityEpoch: String(next.epoch),
        sourceInterfaceKey: input.interfaceKey,
        canonicalInterfaceKey: resolution.canonicalInterfaceKey
      }, 200);
      return cacheIdentityResponse(store, registryKey, snapshot, next, input, response);
    }
  }
  return identityResponse('unavailable', input, { error: 'Identity registry changed concurrently; retry the request' }, 503);
}

export async function resolveConfigKey(store, token, configType, configKey, aliases = []) {
  const raw = normalizeIdentityKey(configKey, 'configKey');
  const keys = [raw, ...(Array.isArray(aliases) ? aliases : [])]
    .map((item) => normalizeOptionalKey(item, 'config alias'))
    .filter(Boolean);
  const { registry } = await loadIdentityRegistry(store, token, configType);
  const candidates = new Set();
  for (const key of keys) {
    if (registry.identities[key]) candidates.add(key);
    const alias = registry.aliases[key];
    if (alias) candidates.add(alias.canonicalInterfaceKey);
  }
  if (candidates.size > 1) {
    const error = new Error('Identity alias maps to multiple canonical interfaces');
    error.status = 409;
    throw error;
  }
  return candidates.size === 1 ? [...candidates][0] : raw;
}


/**
 * Move old URL-hash/provisional spaces into the canonical space without
 * deleting the source. The source remains routable through its alias for the
 * compatibility window, while new reads/writes use the canonical key.
 */
async function cacheIdentityResponse(store, registryKey, snapshot, registry, input, response) {
  if (!input.requestId) return response;
  for (let attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
    const current = attempt === 0 && snapshot && snapshot.state
      ? { version: snapshot.version, state: snapshot.state }
      : await store.load(registryKey);
    const next = normalizeIdentityRegistry(current.state);
    next.requests[input.requestId] = { status: response.status, body: response.body, createdAt: Date.now() };
    if (await store.compareAndSet(registryKey, current.version, next)) return response;
  }
  // A concurrent resolver may have committed the same logical identity. Do not
  // turn a successful resolve into a failure merely because cache persistence
  // raced; the canonical identity is already durable.
  return response;
}

export async function migrateIdentitySpaces(store, token, configType, canonicalInterfaceKey, sourceKeys = []) {
  const canonicalKey = await identityPlaybackSpaceKey(token, canonicalInterfaceKey, configType, false);
  const candidates = [...new Set((Array.isArray(sourceKeys) ? sourceKeys : [])
    .map((item) => String(item || '').trim().toLowerCase())
    .filter((item) => item && item !== canonicalInterfaceKey))];
  if (!candidates.length) return { migrated: false, pending: false, resetSince: false };
  const sourceSnapshots = [];
  for (const sourceKey of candidates) {
    const keys = [
      await identityPlaybackSpaceKey(token, sourceKey, configType, true),
      await identityPlaybackSpaceKey(token, sourceKey, configType, false)
    ];
    for (const key of [...new Set(keys)]) {
      if (key === canonicalKey || sourceSnapshots.some((item) => item.key === key)) continue;
      const snapshot = await store.load(key);
      if (snapshot && snapshot.state) sourceSnapshots.push({ key, snapshot });
    }
  }
  if (!sourceSnapshots.length) return { migrated: false, pending: false, resetSince: false };
  for (let attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
    const target = await store.load(canonicalKey);
    const merged = mergePlaybackStates(target.state, sourceSnapshots.map((item) => item.snapshot.state));
    if (!merged.changed) return { migrated: false, pending: false, resetSince: false };
    if (await store.compareAndSet(canonicalKey, target.version, merged.state)) {
      return { migrated: true, pending: false, resetSince: true };
    }
  }
  const error = new Error('Playback identity migration changed concurrently');
  error.status = 503;
  throw error;
}

async function identityPlaybackSpaceKey(token, configKey, configType, legacy) {
  const [tokenHash, configHash] = await Promise.all([sha256(token), sha256(configKey)]);
  if (legacy || normalizeConfigType(configType) === 'vod') return `${tokenHash}:${configHash}`;
  return `${tokenHash}:${normalizeConfigType(configType)}:${configHash}`;
}

function mergePlaybackStates(targetInput, sources) {
  const target = normalizePlaybackStateForMigration(targetInput);
  const all = [target, ...sources.map(normalizePlaybackStateForMigration)];
  const items = new Map();
  const tombstones = new Map();
  const events = Object.create(null);
  for (const state of all) {
    for (const [storedKey, item] of Object.entries(state.items)) {
      const key = storedKey || `${item.siteKey || ''}\n${item.vodId || ''}\n${item.historyKey || ''}`;
      const current = items.get(key);
      if (!current || compareProgress(item, current) > 0) items.set(key, { ...item });
    }
    for (const [storedKey, tombstone] of Object.entries(state.tombstones)) {
      const key = storedKey || JSON.stringify([tombstone.scope || 'item', tombstone.historyKey || '', tombstone.siteKey || '', tombstone.vodId || '']);
      const current = tombstones.get(key);
      if (!current || Number(tombstone.deletedAt || 0) > Number(current.deletedAt || 0)) tombstones.set(key, { ...tombstone });
    }
    for (const [key, value] of Object.entries(state.events)) events[key] = Math.max(Number(events[key] || 0), Number(value || 0));
  }
  for (const [itemKey, item] of items) {
    const deletion = [...tombstones.values()]
      .filter((tombstone) => migrationTombstoneMatches(tombstone, item, itemKey))
      .sort((left, right) => Number(right.deletedAt || 0) - Number(left.deletedAt || 0))[0];
    if (deletion && Number(item.updatedAt || 0) <= Number(deletion.deletedAt || 0)) items.delete(itemKey);
  }
  let sequence = 0;
  const ordered = [
    ...[...tombstones.values()].map((item) => ({ item, kind: 'delete', at: Number(item.deletedAt || 0) })),
    ...[...items.values()].map((item) => ({ item, kind: 'upsert', at: Number(item.updatedAt || 0) }))
  ].sort((a, b) => a.at - b.at || JSON.stringify(a.item).localeCompare(JSON.stringify(b.item)));
  const outputItems = Object.create(null);
  const outputTombstones = Object.create(null);
  for (const entry of ordered) {
    const item = { ...entry.item, seq: ++sequence };
    const key = entry.kind === 'delete'
      ? [...tombstones.entries()].find(([, value]) => value === entry.item)?.[0]
      : [...items.entries()].find(([, value]) => value === entry.item)?.[0];
    if (entry.kind === 'delete') outputTombstones[key || JSON.stringify(item)] = item;
    else outputItems[key || `${item.siteKey || ''}\n${item.vodId || ''}`] = item;
  }
  const state = { schema: 1, sequence, lastCleanup: Math.max(...all.map((item) => item.lastCleanup), 0), items: outputItems, tombstones: outputTombstones, events };
  return { changed: JSON.stringify(state) !== JSON.stringify(target), state };
}

function compareProgress(left, right) {
  const time = Number(left.updatedAt || left.timestamp || 0) - Number(right.updatedAt || right.timestamp || 0);
  if (time) return time;
  if (Boolean(left.payload?.completed) !== Boolean(right.payload?.completed)) return left.payload?.completed ? 1 : -1;
  const position = Number(left.payload?.positionMs || 0) - Number(right.payload?.positionMs || 0);
  if (position) return position;
  return String(left.payload?.eventId || '').localeCompare(String(right.payload?.eventId || ''));
}

function migrationTombstoneMatches(tombstone, item, itemKey) {
  if (tombstone.scope === 'all') return true;
  if (tombstone.scope === 'site') return tombstone.siteKey === item.siteKey;
  return itemKey === tombstone.itemKey || (tombstone.historyKey && tombstone.historyKey === item.historyKey)
    || (tombstone.siteKey && tombstone.vodId && tombstone.siteKey === item.siteKey && tombstone.vodId === item.vodId);
}

function normalizePlaybackStateForMigration(value) {
  const source = value && typeof value === 'object' && !Array.isArray(value) ? value : {};
  return {
    sequence: Number.isSafeInteger(source.sequence) && source.sequence >= 0 ? source.sequence : 0,
    lastCleanup: Number.isSafeInteger(source.lastCleanup) && source.lastCleanup >= 0 ? source.lastCleanup : 0,
    items: safeObject(source.items),
    tombstones: safeObject(source.tombstones),
    events: safeObject(source.events)
  };
}

export function identityResponse(action, input, extra = {}, status = 200) {
  return {
    status,
    body: {
      ok: status < 400,
      schema: IDENTITY_SCHEMA,
      identityProtocol: IDENTITY_SCHEMA,
      addressMatchVersion: Number(ADDRESS_MATCH_VERSION),
      action,
      canonicalInterfaceKey: extra.canonicalInterfaceKey || (action === 'create' || action === 'keep' ? input.interfaceKey : ''),
      sourceInterfaceKey: extra.sourceInterfaceKey || input.interfaceKey,
      matchedBy: extra.matchedBy || 'none',
      matchedKeys: extra.matchedKeys || [],
      migrationRequired: Boolean(extra.migrationRequired),
      migrationDone: Boolean(extra.migrationDone),
      resetSince: Boolean(extra.resetSince),
      nextSince: extra.nextSince == null ? '' : String(extra.nextSince),
      identityEpoch: extra.identityEpoch || '',
      capabilities: identityCapabilities(),
      ...(extra.error ? { error: extra.error } : {}),
      ...(extra.candidates ? { candidates: extra.candidates } : {})
    }
  };
}

function chooseIdentity(registry, input) {
  const bound = boundCanonical(registry, input.interfaceKey);
  const strict = collectCandidates(registry, input.strictAddressKeys);
  const endpoint = collectCandidates(registry, input.endpointMatchKeys);
  const legacy = collectCandidates(registry, input.legacyConfigKeys);
  const strong = strict.size ? { set: strict, matchedBy: 'strictAddressKey', keys: input.strictAddressKeys } : endpoint.size
    ? { set: endpoint, matchedBy: 'endpointMatchKey', keys: input.endpointMatchKeys } : legacy.size
      ? { set: legacy, matchedBy: 'legacyConfigKey', keys: input.legacyConfigKeys } : null;
  if (bound) {
    if (strong && (strong.set.size > 1 || !strong.set.has(bound))) {
      return { action: 'conflict', matchedBy: strong.matchedBy, matchedKeys: [...strong.set], candidates: [...strong.set], error: 'Submitted interface is bound to a different identity' };
    }
    return { action: 'keep', canonicalInterfaceKey: bound, matchedBy: bound === input.interfaceKey ? 'interfaceKey' : 'alias', matchedKeys: [] };
  }
  if (strong && strong.set.size > 1) return { action: 'conflict', matchedBy: strong.matchedBy, matchedKeys: strong.keys, candidates: [...strong.set], error: 'Address clues belong to multiple identities' };
  if (strong && strong.set.size === 1) {
    const canonicalInterfaceKey = [...strong.set][0];
    if (canonicalInterfaceKey === input.interfaceKey) return { action: 'keep', canonicalInterfaceKey, matchedBy: strong.matchedBy, matchedKeys: strong.keys };
    if (input.sourceDataState !== 'empty') {
      if (input.confirm) return { action: 'merge', canonicalInterfaceKey, matchedBy: strong.matchedBy, matchedKeys: strong.keys };
      return { action: 'confirm_required', canonicalInterfaceKey, matchedBy: strong.matchedBy, matchedKeys: strong.keys, candidates: [canonicalInterfaceKey], error: 'Existing source data requires explicit merge confirmation' };
    }
    return { action: 'adopt', canonicalInterfaceKey, matchedBy: strong.matchedBy, matchedKeys: strong.keys };
  }
  const weak = collectCandidates(registry, input.hostMatchKeys);
  if (weak.size === 1 && input.confirm) return { action: 'merge', canonicalInterfaceKey: [...weak][0], matchedBy: 'hostMatchKey', matchedKeys: input.hostMatchKeys };
  if (weak.size) return { action: 'confirm_required', matchedBy: 'hostMatchKey', matchedKeys: input.hostMatchKeys, candidates: [...weak], error: 'Host-only match requires confirmation' };
  return { action: 'create', canonicalInterfaceKey: input.interfaceKey, matchedBy: 'none', matchedKeys: [] };
}

function boundCanonical(registry, key) {
  if (registry.identities[key]) return key;
  return registry.aliases[key]?.canonicalInterfaceKey || '';
}

function collectCandidates(registry, keys) {
  const result = new Set();
  for (const key of keys) {
    const alias = registry.aliases[key];
    if (alias) result.add(alias.canonicalInterfaceKey);
    if (registry.identities[key]) result.add(key);
  }
  return result;
}

function addIdentityKeys(registry, identity, input) {
  identity.updatedAt = Date.now();
  for (const key of input.strictAddressKeys) pushUnique(identity.strictAddressKeys, key);
  for (const key of input.endpointMatchKeys) pushUnique(identity.endpointMatchKeys, key);
  for (const key of input.hostMatchKeys) pushUnique(identity.hostMatchKeys, key);
  for (const key of input.legacyConfigKeys) pushUnique(identity.legacyConfigKeys, key);
}

function addAlias(registry, key, canonicalInterfaceKey, kind) {
  if (!key || !registry.identities[canonicalInterfaceKey]) return;
  const current = registry.aliases[key];
  if (current && current.canonicalInterfaceKey !== canonicalInterfaceKey) return;
  registry.aliases[key] = { canonicalInterfaceKey, kind: kind || 'alias' };
}

function keyKind(identity, key) {
  if (identity.strictAddressKeys.includes(key)) return 'strict';
  if (identity.endpointMatchKeys.includes(key)) return 'endpoint';
  if (identity.hostMatchKeys.includes(key)) return 'host';
  if (identity.legacyConfigKeys.includes(key)) return 'legacy';
  return 'alias';
}

function normalizeIdentity(value, canonicalInterfaceKey) {
  const source = value && typeof value === 'object' ? value : {};
  return {
    canonicalInterfaceKey,
    strictAddressKeys: uniqueKeys(source.strictAddressKeys),
    endpointMatchKeys: uniqueKeys(source.endpointMatchKeys),
    hostMatchKeys: uniqueKeys(source.hostMatchKeys),
    legacyConfigKeys: uniqueKeys(source.legacyConfigKeys),
    createdAt: Number.isSafeInteger(source.createdAt) ? source.createdAt : 0,
    updatedAt: Number.isSafeInteger(source.updatedAt) ? source.updatedAt : 0
  };
}

function uniqueKeys(value) {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.map((item) => String(item || '').trim().toLowerCase()).filter((item) => KEY_PATTERN.test(item)))].slice(0, MAX_KEYS);
}

function safeObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

function cloneJson(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value));
}

function pushUnique(list, value) {
  if (!list.includes(value) && list.length < MAX_KEYS) list.push(value);
}

function cleanText(value, max) {
  return String(value == null ? '' : value).trim().slice(0, max);
}

function isUnreserved(value) {
  return value >= 0x41 && value <= 0x5a || value >= 0x61 && value <= 0x7a || value >= 0x30 && value <= 0x39 || value === 0x2d || value === 0x2e || value === 0x5f || value === 0x7e;
}
