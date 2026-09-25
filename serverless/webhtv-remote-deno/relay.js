const SERVER_MODE = 'deno';
const BIND_TTL_MS = 10 * 60 * 1000;
const COMMAND_TTL_MS = 60 * 60 * 1000;
const SYNC_TTL_MS = 2 * 60 * 60 * 1000;
const MAX_SYNC_PART_BYTES = 25 * 1024 * 1024;
const PARTS = new Set(['backup', 'syncFiles', 'loginStateFiles', 'manifest']);

const CAPABILITIES = {
  configManage: true,
  remoteSync: true,
  pushAction: true,
  recentLog: true,
  deviceBackup: false,
  fileManage: false,
  webHomeManage: false,
  shellProxyManage: false,
  siteInjectManage: false,
  webHomeExtensionManage: false,
  playbackSync: false,
  playbackPersistentStorage: false,
  multiDeviceBatch: false,
  webSocket: false,
  persistentStorage: false,
  externalObjectStorage: false,
  deviceRevoke: false
};

const state = globalThis.__WEBHTV_REMOTE_RELAY_STATE || (globalThis.__WEBHTV_REMOTE_RELAY_STATE = {
  devices: new Map(),
  bindCodes: new Map(),
  groupDevices: new Map(),
  commands: new Map(),
  queues: new Map(),
  syncs: new Map(),
  parts: new Map(),
  lastCleanup: 0
});

export async function handleRelayRequest(request, options = {}) {
  if (request.method === 'OPTIONS') return cors(new Response(null, { status: 204 }));
  try {
    cleanup();
    return cors(await route(request, options));
  } catch (e) {
    return cors(json({ ok: false, error: e && e.message ? e.message : String(e) }, e && e.status ? e.status : 500));
  }
}

async function route(request, options) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, '') || '/';
  const method = request.method.toUpperCase();

  if (method === 'GET' && path === '/api/health') return json({ ok: true, time: Date.now() });
  if (method === 'GET' && path === '/api/server/capabilities') return json(capabilities(options));

  if (method === 'POST' && path === '/api/device/register') return registerDevice(request, options);
  if (method === 'POST' && path === '/api/device/bind-code') return createBindCode(request, options);
  if (method === 'POST' && path === '/api/device/poll') return pollDevice(request, options);

  if (method === 'POST' && (path === '/api/groups/claim' || path === '/api/family/claim')) return claimDevice(request, options);
  if (method === 'GET' && path === '/api/devices') return listDevices(request, options);

  if (method === 'POST' && path === '/api/commands') return createCommand(request, options);
  {
    const m = path.match(/^\/api\/commands\/([^/]+)$/);
    if (m && method === 'GET') return getCommand(request, options, m[1]);
  }
  {
    const m = path.match(/^\/api\/commands\/([^/]+)\/result$/);
    if (m && method === 'POST') return commandResult(request, m[1]);
  }

  if (method === 'POST' && path === '/api/sync/create') return createSync(request, options);
  {
    const m = path.match(/^\/api\/sync\/([^/]+)$/);
    if (m && method === 'GET') return getSync(request, m[1]);
  }
  {
    const m = path.match(/^\/api\/sync\/([^/]+)\/part\/([^/]+)$/);
    if (m && method === 'POST') return uploadSyncPart(request, m[1], m[2]);
    if (m && method === 'GET') return downloadSyncPart(request, m[1], m[2]);
  }
  {
    const m = path.match(/^\/api\/sync\/([^/]+)\/export-complete$/);
    if (m && method === 'POST') return exportComplete(request, options, m[1]);
  }
  {
    const m = path.match(/^\/api\/sync\/([^/]+)\/restore-complete$/);
    if (m && method === 'POST') return restoreComplete(request, m[1]);
  }

  return json({ ok: false, error: 'Not found' }, 404);
}

function capabilities(options) {
  return {
    ok: true,
    serverMode: SERVER_MODE,
    serverName: options.serverName || 'WebHTV Remote Relay',
    relayMode: 'origin-token-memory',
    time: Date.now(),
    maxSyncPartBytes: MAX_SYNC_PART_BYTES,
    capabilities: {
      ...CAPABILITIES,
      playbackSync: options.playbackSync === true,
      playbackPersistentStorage: options.playbackPersistentStorage === true
    }
  };
}

async function registerDevice(request, options) {
  const body = await readJson(request);
  const origin = serverOrigin(request);
  let deviceToken = readDeviceToken(request, body);
  if (!deviceToken) deviceToken = randomCapability('dtk');
  const deviceId = await requireDerivedId('dev', origin, deviceToken, body.deviceId, 'Invalid device token');
  const existing = state.devices.get(deviceId);
  const now = Date.now();
  const groupIds = new Set(deviceGroupIds(existing));
  const groupTokens = readGroupTokens(request, body);
  for (const token of groupTokens) {
    const group = await groupFromToken(request, token);
    groupIds.add(group.groupId);
    linkDevice(group.groupId, deviceId);
  }
  const device = {
    deviceId,
    groupId: [...groupIds][0] || null,
    groupIds: [...groupIds],
    name: String(body.name || existing?.name || 'WebHTV'),
    alias: String(body.alias || existing?.alias || ''),
    role: String(body.role || existing?.role || 'app'),
    type: body.type ?? existing?.type ?? 0,
    appVersion: String(body.appVersion || existing?.appVersion || ''),
    capabilities: body.capabilities || existing?.capabilities || {},
    lastSeen: now,
    createdAt: existing?.createdAt || now,
    updatedAt: now
  };
  state.devices.set(deviceId, device);
  for (const id of device.groupIds) linkDevice(id, deviceId);
  return json({ ok: true, deviceId, deviceToken, deviceSecret: deviceToken, groupIds: device.groupIds, server: capabilities(options) });
}

async function createBindCode(request, options) {
  const body = await readJson(request);
  const { device } = await requireDevice(request, body);
  const origin = serverOrigin(request);
  const bindGrantToken = String(body.bindGrantToken || '').trim();
  if (!bindGrantToken) throw httpError(400, 'Missing bindGrantToken');
  const grantId = await requireDerivedId('bnd', origin, bindGrantToken, body.grantId, 'Invalid bind grant token');
  let code = '';
  for (let i = 0; i < 8; i++) {
    code = String(Math.floor(100000 + Math.random() * 900000));
    if (!state.bindCodes.has(code)) break;
  }
  state.bindCodes.set(code, { code, deviceId: device.deviceId, grantId, bindGrantToken, expiresAt: Date.now() + BIND_TTL_MS });
  return json({ ok: true, code, grantId, expiresIn: Math.floor(BIND_TTL_MS / 1000), server: capabilities(options) });
}

async function claimDevice(request, options) {
  const body = await readJson(request);
  const { device: requester } = await requireDevice(request, body);
  const code = String(body.code || '').trim();
  const bind = state.bindCodes.get(code);
  if (!bind || bind.expiresAt < Date.now()) throw httpError(404, 'Bind code expired');
  if (requester.deviceId === bind.deviceId) throw httpError(400, 'Cannot bind local device');

  const device = state.devices.get(bind.deviceId);
  if (!device) throw httpError(404, 'Device not found');
  const group = await groupFromToken(request, readGroupToken(request, body) || randomCapability('gtk'));
  addGroupToDevice(device, group.groupId);
  device.alias = String(body.alias || device.alias || device.name || '');
  device.updatedAt = Date.now();
  state.devices.set(device.deviceId, device);
  linkDevice(group.groupId, device.deviceId);
  state.bindCodes.delete(code);
  const command = enqueueCommand(group.groupId, device.deviceId, 'remote.profile.addGroup', {
    groupId: group.groupId,
    groupToken: group.groupToken,
    groupTokenHash: group.groupTokenHash,
    grantId: bind.grantId,
    bindGrantToken: bind.bindGrantToken,
    alias: String(body.alias || '')
  }, group.groupTokenHash);
  return json({
    ok: true,
    deviceId: device.deviceId,
    groupId: group.groupId,
    groupToken: group.groupToken,
    familyToken: group.groupToken,
    groupTokenHash: group.groupTokenHash,
    grantId: bind.grantId,
    bindGrantToken: bind.bindGrantToken,
    commandId: command.id,
    device: publicDevice(device),
    server: capabilities(options)
  });
}

async function listDevices(request, options) {
  const { groupId } = await requireGroup(request);
  const devices = [...(state.groupDevices.get(groupId) || [])]
    .map((deviceId) => state.devices.get(deviceId))
    .filter(Boolean)
    .map(publicDevice);
  devices.sort((a, b) => String(a.name).localeCompare(String(b.name)));
  return json({ ok: true, devices, server: capabilities(options) });
}

async function pollDevice(request, options) {
  const body = await readJson(request);
  const { device } = await requireDevice(request, body);
  const groupTokens = readGroupTokens(request, body);
  const groupIds = new Set(deviceGroupIds(device));
  for (const token of groupTokens) {
    const group = await groupFromToken(request, token);
    groupIds.add(group.groupId);
    linkDevice(group.groupId, device.deviceId);
  }
  device.groupId = [...groupIds][0] || null;
  device.groupIds = [...groupIds];
  device.lastSeen = Date.now();
  device.updatedAt = Date.now();
  state.devices.set(device.deviceId, device);

  const queue = state.queues.get(device.deviceId) || [];
  while (queue.length) {
    const commandId = queue.shift();
    const command = state.commands.get(commandId);
    if (!command || isExpired(command.createdAt, COMMAND_TTL_MS)) continue;
    command.status = 'delivered';
    command.deliveredAt = Date.now();
    state.commands.set(command.id, command);
    return json({ ok: true, command, server: capabilities(options) });
  }
  state.queues.set(device.deviceId, queue);
  return json({ ok: true, command: null, server: capabilities(options) });
}

async function createCommand(request) {
  const body = await readJson(request);
  const group = await requireGroup(request, body);
  const targetDeviceId = cleanId(body.targetDeviceId);
  const type = String(body.type || '');
  const payload = body.payload || {};
  if (type === 'remote.profile.addGroup') {
    if (!payload.bindGrantToken || !payload.groupToken) throw httpError(400, 'Missing bootstrap payload');
    const bootstrapGroup = await groupFromToken(request, payload.groupToken);
    if (bootstrapGroup.groupId !== group.groupId) throw httpError(400, 'Bootstrap group token mismatch');
    payload.groupId = bootstrapGroup.groupId;
    payload.groupTokenHash = bootstrapGroup.groupTokenHash;
  } else {
    requireTargetIfKnown(group.groupId, targetDeviceId);
    payload.groupId = group.groupId;
    payload.groupTokenHash = group.groupTokenHash;
  }
  const command = enqueueCommand(group.groupId, targetDeviceId, type, payload, group.groupTokenHash);
  return json({ ok: true, commandId: command.id, command });
}

async function getCommand(request, options, commandId) {
  const { groupId } = await requireGroup(request);
  const command = state.commands.get(cleanId(commandId));
  if (!command || command.groupId !== groupId) throw httpError(404, 'Command not found');
  return json({ ok: true, command, server: capabilities(options) });
}

async function commandResult(request, commandId) {
  const body = await readJson(request);
  const { device } = await requireDevice(request, body);
  const command = state.commands.get(cleanId(commandId));
  if (!command) throw httpError(404, 'Command not found');
  if (command.targetDeviceId !== device.deviceId) throw httpError(403, 'Command target mismatch');
  command.status = body.ok === false ? 'failed' : 'done';
  command.result = body;
  command.finishedAt = Date.now();
  state.commands.set(command.id, command);
  return json({ ok: true });
}

async function createSync(request, options) {
  const body = await readJson(request);
  const { groupId, groupTokenHash } = await requireGroup(request, body);
  const sourceDeviceId = cleanId(body.sourceDeviceId);
  const targetDeviceId = cleanId(body.targetDeviceId);
  requireTargetIfKnown(groupId, sourceDeviceId);
  requireTargetIfKnown(groupId, targetDeviceId);
  if (sourceDeviceId === targetDeviceId) throw httpError(400, 'Source and target device must be different');

  const syncId = `sync_${randomId(20)}`;
  const origin = new URL(request.url).origin;
  const sync = {
    id: syncId,
    groupId,
    groupTokenHash,
    sourceDeviceId,
    targetDeviceId,
    options: normalizeSyncOptions(body.options || {}),
    status: 'created',
    parts: {},
    createdAt: Date.now(),
    updatedAt: Date.now()
  };
  state.syncs.set(syncId, sync);

  const command = enqueueCommand(groupId, sourceDeviceId, 'remoteSync.export', {
    syncId,
    targetDeviceId,
    options: sync.options,
    uploadBase: `${origin}/api/sync/${syncId}/part`,
    completeUrl: `${origin}/api/sync/${syncId}/export-complete`,
    groupId,
    groupTokenHash
  }, groupTokenHash);
  sync.exportCommandId = command.id;
  return json({ ok: true, sync, exportCommandId: command.id, server: capabilities(options) });
}

async function getSync(request, syncId) {
  const { groupId } = await requireGroup(request);
  const sync = state.syncs.get(cleanId(syncId));
  if (!sync || sync.groupId !== groupId) throw httpError(404, 'Sync not found');
  return json({ ok: true, sync });
}

async function uploadSyncPart(request, syncId, part) {
  part = normalizePart(part);
  const { device } = await requireDevice(request);
  const sync = getSyncForDevice(cleanId(syncId), device.deviceId);
  if (sync.sourceDeviceId !== device.deviceId) throw httpError(403, 'Only source device can upload sync parts');
  const contentLength = Number(request.headers.get('content-length') || 0);
  if (contentLength > MAX_SYNC_PART_BYTES) throw httpError(413, 'Sync part is too large for online relay');

  const bytes = await request.arrayBuffer();
  if (bytes.byteLength > MAX_SYNC_PART_BYTES) throw httpError(413, 'Sync part is too large for online relay');

  const key = partKey(sync.id, part);
  const contentType = request.headers.get('content-type') || contentTypeForPart(part);
  state.parts.set(key, { bytes, contentType, size: bytes.byteLength, uploadedAt: Date.now() });
  sync.parts[part] = { key, size: bytes.byteLength, contentType, uploadedAt: Date.now() };
  sync.status = 'exporting';
  sync.updatedAt = Date.now();
  state.syncs.set(sync.id, sync);
  return json({ ok: true, part, size: bytes.byteLength });
}

async function downloadSyncPart(request, syncId, part) {
  part = normalizePart(part);
  const { device } = await requireDevice(request);
  const sync = getSyncForDevice(cleanId(syncId), device.deviceId);
  const info = sync.parts && sync.parts[part];
  if (!info) throw httpError(404, 'Sync part not found');
  const stored = state.parts.get(info.key);
  if (!stored) throw httpError(404, 'Sync part expired');
  return new Response(stored.bytes.slice(0), {
    headers: {
      'content-type': stored.contentType || contentTypeForPart(part),
      'content-length': String(stored.size || 0),
      'cache-control': 'no-store'
    }
  });
}

async function exportComplete(request, options, syncId) {
  const body = await readJson(request);
  const { device } = await requireDevice(request, body);
  const sync = getSyncForDevice(cleanId(syncId), device.deviceId);
  if (sync.sourceDeviceId !== device.deviceId) throw httpError(403, 'Only source device can finish export');
  sync.status = 'exported';
  sync.exportResult = body;
  sync.updatedAt = Date.now();

  const origin = new URL(request.url).origin;
  const downloads = {};
  for (const part of Object.keys(sync.parts || {})) downloads[part] = `${origin}/api/sync/${sync.id}/part/${part}`;
  const command = enqueueCommand(sync.groupId, sync.targetDeviceId, 'remoteSync.restore', {
    syncId: sync.id,
    sourceDeviceId: sync.sourceDeviceId,
    options: sync.options,
    parts: sync.parts,
    downloads,
    completeUrl: `${origin}/api/sync/${sync.id}/restore-complete`,
    groupId: sync.groupId,
    groupTokenHash: sync.groupTokenHash
  }, sync.groupTokenHash);
  sync.restoreCommandId = command.id;
  state.syncs.set(sync.id, sync);
  return json({ ok: true, restoreCommandId: command.id, server: capabilities(options) });
}

async function restoreComplete(request, syncId) {
  const body = await readJson(request);
  const { device } = await requireDevice(request, body);
  const sync = getSyncForDevice(cleanId(syncId), device.deviceId);
  if (sync.targetDeviceId !== device.deviceId) throw httpError(403, 'Only target device can finish restore');
  sync.status = body.ok === false ? 'restore_failed' : 'done';
  sync.restoreResult = body;
  sync.updatedAt = Date.now();
  state.syncs.set(sync.id, sync);
  if (body.ok !== false) deleteSyncParts(sync);
  return json({ ok: true });
}

function enqueueCommand(groupId, targetDeviceId, type, payload, groupTokenHash) {
  if (!type) throw httpError(400, 'Missing command type');
  const id = `cmd_${randomId(20)}`;
  const command = { id, groupId, groupTokenHash, targetDeviceId, type, payload, status: 'queued', createdAt: Date.now() };
  state.commands.set(id, command);
  const queue = state.queues.get(targetDeviceId) || [];
  queue.push(id);
  state.queues.set(targetDeviceId, queue);
  return command;
}

function requireTargetIfKnown(groupId, deviceId) {
  if (!deviceId) throw httpError(400, 'Missing deviceId');
  const device = state.devices.get(deviceId);
  if (device && !deviceInGroup(device, groupId)) throw httpError(404, 'Device is not bound to this group');
  return device;
}

function getSyncForDevice(syncId, deviceId) {
  const sync = state.syncs.get(syncId);
  if (!sync) throw httpError(404, 'Sync not found');
  if (sync.sourceDeviceId !== deviceId && sync.targetDeviceId !== deviceId) throw httpError(403, 'Device is not part of this sync');
  return sync;
}

async function requireDevice(request, body = {}) {
  const origin = serverOrigin(request);
  const deviceToken = readDeviceToken(request, body);
  if (!deviceToken) throw httpError(401, 'Missing device credentials');
  const deviceId = await requireDerivedId('dev', origin, deviceToken, body.deviceId || request.headers.get('x-device-id'), 'Invalid device token');
  let device = state.devices.get(deviceId);
  if (!device) {
    const now = Date.now();
    device = {
      deviceId,
      groupId: null,
      groupIds: [],
      name: String(body.name || 'WebHTV'),
      alias: String(body.alias || ''),
      role: String(body.role || 'app'),
      type: body.type ?? 0,
      appVersion: String(body.appVersion || ''),
      capabilities: body.capabilities || {},
      lastSeen: now,
      createdAt: now,
      updatedAt: now
    };
    state.devices.set(deviceId, device);
  }
  return { device };
}

async function requireGroup(request, body = {}) {
  const groupToken = readGroupToken(request, body);
  if (!groupToken) throw httpError(401, 'Missing group token');
  return groupFromToken(request, groupToken);
}

function readGroupToken(request, body = {}) {
  return String(body.groupToken || body.familyToken || request?.headers.get('x-group-token') || request?.headers.get('x-family-token') || bearer(request) || '').trim();
}

function readDeviceToken(request, body = {}) {
  return String(body.deviceToken || body.deviceSecret || request?.headers.get('x-device-token') || bearer(request) || '').trim();
}

function readGroupTokens(request, body = {}) {
  const result = [];
  const direct = readGroupToken(null, body);
  if (direct) result.push(direct);
  if (Array.isArray(body.groups)) {
    for (const item of body.groups) {
      const token = typeof item === 'string' ? item : item && (item.groupToken || item.familyToken);
      if (token) result.push(String(token).trim());
    }
  }
  const seen = new Set();
  return result.filter((token) => token && !seen.has(token) && seen.add(token));
}

async function groupFromToken(request, groupToken) {
  groupToken = String(groupToken || '').trim();
  if (!groupToken) throw httpError(401, 'Missing group token');
  const origin = serverOrigin(request);
  const groupId = await deriveId('grp', origin, groupToken);
  const groupTokenHash = await hashText(`${origin}:${groupToken}`);
  return { groupId, groupToken, familyToken: groupToken, groupTokenHash };
}

async function requireDerivedId(prefix, origin, token, id, message) {
  const expected = await deriveId(prefix, origin, token);
  const actual = cleanId(id);
  if (actual && actual !== expected) throw httpError(401, message);
  return expected;
}

function linkDevice(groupId, deviceId) {
  const devices = state.groupDevices.get(groupId) || new Set();
  devices.add(deviceId);
  state.groupDevices.set(groupId, devices);
}

function addGroupToDevice(device, groupId) {
  const groupIds = new Set(deviceGroupIds(device));
  if (groupId) groupIds.add(groupId);
  device.groupIds = [...groupIds];
  device.groupId = device.groupIds[0] || null;
  return device;
}

function deviceInGroup(device, groupId) {
  return deviceGroupIds(device).includes(groupId);
}

function deviceGroupIds(device) {
  if (!device) return [];
  const groupIds = new Set();
  if (Array.isArray(device.groupIds)) for (const id of device.groupIds) if (id) groupIds.add(id);
  if (device.groupId) groupIds.add(device.groupId);
  if (device.ownerId) groupIds.add(device.ownerId);
  return [...groupIds];
}

function normalizeSyncOptions(options) {
  return {
    config: options.config !== false,
    loginState: options.loginState !== false,
    spider: options.spider !== false,
    webHome: options.webHome !== false,
    search: options.search !== false,
    keep: options.keep !== false,
    history: options.history !== false,
    follow: options.follow === true,
    settings: options.settings === true,
    paths: typeof options.paths === 'string' ? options.paths : undefined
  };
}

function normalizePart(part) {
  part = String(part || '').replace(/\.zip$|\.json$/g, '');
  if (!PARTS.has(part)) throw httpError(400, 'Invalid sync part');
  return part;
}

function deleteSyncParts(sync) {
  for (const info of Object.values(sync.parts || {})) if (info.key) state.parts.delete(info.key);
}

function publicDevice(device) {
  return {
    deviceId: device.deviceId,
    name: device.alias || device.name,
    rawName: device.name,
    role: device.role,
    type: device.type,
    appVersion: device.appVersion,
    lastSeen: device.lastSeen,
    online: Date.now() - Number(device.lastSeen || 0) < 45_000,
    capabilities: device.capabilities || {}
  };
}

async function readJson(request) {
  const text = await request.text();
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch (e) {
    throw httpError(400, 'Invalid JSON body');
  }
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store'
    }
  });
}

function cors(response) {
  const headers = new Headers(response.headers);
  headers.set('access-control-allow-origin', '*');
  headers.set('access-control-allow-methods', 'GET,POST,OPTIONS');
  headers.set('access-control-allow-headers', 'authorization,content-type,x-webhtv-origin,x-device-id,x-device-token,x-group-token,x-family-token');
  headers.set('access-control-max-age', '86400');
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}

function cleanup() {
  const now = Date.now();
  if (now - state.lastCleanup < 30_000) return;
  state.lastCleanup = now;

  for (const [code, item] of state.bindCodes) if (item.expiresAt < now) state.bindCodes.delete(code);
  for (const [id, command] of state.commands) if (isExpired(command.createdAt, COMMAND_TTL_MS)) state.commands.delete(id);
  for (const [id, sync] of state.syncs) {
    if (!isExpired(sync.createdAt, SYNC_TTL_MS)) continue;
    deleteSyncParts(sync);
    state.syncs.delete(id);
  }
  for (const [deviceId, queue] of state.queues) {
    state.queues.set(deviceId, queue.filter((id) => state.commands.has(id)));
  }
}

function isExpired(time, ttl) {
  return Date.now() - Number(time || 0) > ttl;
}

function httpError(status, message) {
  const error = new Error(message);
  error.status = status;
  throw error;
}

function bearer(request) {
  const value = request?.headers.get('authorization') || '';
  const match = value.match(/^Bearer\s+(.+)$/i);
  return match ? match[1].trim() : '';
}

function cleanId(value) {
  return String(value || '').replace(/[^A-Za-z0-9_.:-]/g, '').slice(0, 80);
}

function partKey(syncId, part) {
  return `sync:${syncId}:${part}`;
}

function contentTypeForPart(part) {
  return part === 'backup' || part === 'manifest' ? 'application/json; charset=utf-8' : 'application/zip';
}

function randomId(bytes = 16) {
  const array = new Uint8Array(bytes);
  crypto.getRandomValues(array);
  return [...array].map((b) => b.toString(16).padStart(2, '0')).join('');
}

function randomToken(bytes = 32) {
  return randomId(bytes);
}

function randomCapability(prefix) {
  return `${prefix}_${randomToken(32)}`;
}

function serverOrigin(request) {
  const declared = normalizeOrigin(request.headers.get('x-webhtv-origin'));
  if (declared) return declared;
  return new URL(request.url).origin;
}

function normalizeOrigin(value) {
  value = String(value || '').trim();
  if (!value) return '';
  try {
    const url = new URL(value);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return '';
    return url.origin.toLowerCase();
  } catch {
    return '';
  }
}

async function deriveId(prefix, origin, token) {
  return `${prefix}_${(await hashText(`${origin}:${token}`)).slice(0, 24)}`;
}

async function hashText(text) {
  const data = new TextEncoder().encode(text);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return [...new Uint8Array(hash)].map((b) => b.toString(16).padStart(2, '0')).join('');
}
