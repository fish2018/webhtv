import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveIdentity, parseIdentityRequest, canonicalizeAddress, addressMaterial, sha256 } from './identity.js';

function createMemoryPlaybackStore() {
  const entries = new Map();
  return {
    async load(key) { const entry = entries.get(key); return entry ? { version: entry.version, state: JSON.parse(JSON.stringify(entry.state)) } : { version: null, state: null }; },
    async compareAndSet(key, version, state) { const current = entries.get(key); if ((current?.version ?? null) !== version) return false; entries.set(key, { version: (current?.version || 0) + 1, state: JSON.parse(JSON.stringify(state)) }); return true; }
  };
}

test('canonicalizes protocol vectors and keeps query ordering significant', async () => {
  const same = (a, b, kind) => Promise.all([sha256(addressMaterial('vod', canonicalizeAddress(a), kind)), sha256(addressMaterial('vod', canonicalizeAddress(b), kind))]).then(([x, y]) => x === y);
  assert.equal(await same('HTTPS://API.Example.com:443/config.json#x', 'https://api.example.com/config.json', 'strict'), true);
  assert.equal(await same('http://api.example.com:80/config.json', 'https://api.example.com/config.json', 'strict'), false);
  assert.equal(await same('http://api.example.com:80/config.json', 'https://api.example.com/config.json', 'endpoint'), true);
  assert.equal(await same('https://api.example.com/a.json', 'https://api.example.com/b.json', 'endpoint'), false);
  assert.equal(await same('https://api.example.com/a.json?x=1&y=2', 'https://api.example.com/a.json?y=2&x=1', 'endpoint'), false);
});

test('resolves exact aliases, refuses host-only silent merges, and isolates config types', async () => {
  const store = createMemoryPlaybackStore();
  const input = (key, strict, endpoint, host, type = 'vod', state = 'empty') => parseIdentityRequest({ schema: 'webhtv.playback.identity.v1', operation: 'resolve', interfaceKey: key, configType: type, strictAddressKeys: strict, endpointMatchKeys: endpoint, hostMatchKeys: host, legacyConfigKeys: [], sourceDataState: state }, new Headers());
  let result = await resolveIdentity(store, 'token', input('a', ['strict'], ['endpoint'], ['host']));
  assert.equal(result.body.action, 'create');
  result = await resolveIdentity(store, 'token', input('b', ['strict'], ['endpoint'], ['host']));
  assert.equal(result.body.action, 'adopt');
  assert.equal(result.body.canonicalInterfaceKey, 'a');
  result = await resolveIdentity(store, 'token', input('c', [], [], ['host']));
  assert.equal(result.body.action, 'confirm_required');
  result = await resolveIdentity(store, 'token', input('live', ['strict'], ['endpoint'], ['host'], 'live'));
  assert.equal(result.body.action, 'create');
});


test('replays the same resolve result for an idempotent request id', async () => {
  const store = createMemoryPlaybackStore();
  const headers = new Headers({ 'x-webhtv-request-id': 'request-1' });
  const input = parseIdentityRequest({ schema: 'webhtv.playback.identity.v1', operation: 'resolve', interfaceKey: 'idempotent-a', strictAddressKeys: ['strict-idempotent'], endpointMatchKeys: [], hostMatchKeys: [], legacyConfigKeys: [], sourceDataState: 'empty' }, headers);
  const first = await resolveIdentity(store, 'token-idempotent', input);
  const second = await resolveIdentity(store, 'token-idempotent', input);
  assert.deepEqual(second, first);
});
