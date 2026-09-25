import test from 'node:test';
import assert from 'node:assert/strict';

import { createMemoryPlaybackStore, handlePlaybackSyncRequest } from '../api/playback-sync.js';

const URL = 'https://sync.example/api/playback/sync';
const TOKEN = 'test-token-a';
const CONFIG = 'config-a';

function request(method, body, token = TOKEN, configKey = CONFIG, extra = {}) {
  const headers = new Headers({
    'X-WebHTV-Token': token,
    'X-WebHTV-Config-Key': configKey,
    ...extra
  });
  if (body != null) headers.set('Content-Type', 'application/json');
  return new Request(URL, { method, headers, body: body == null ? undefined : JSON.stringify(body) });
}

async function json(response) {
  return { status: response.status, body: await response.json() };
}

test('syncs progress, deletion tombstones, and a newer restore', async () => {
  const store = createMemoryPlaybackStore();
  const progress = {
    event: 'playback.progress',
    eventId: 'progress-1',
    timestamp: Date.now() + 1000,
    historyKey: 'site-a@@@vod-1@@@1',
    siteKey: 'site-a',
    vodId: 'vod-1',
    vodName: '影片 A',
    episodeName: '第 1 集',
    positionMs: 120000,
    durationMs: 600000
  };

  let result = await json(await handlePlaybackSyncRequest(request('POST', progress), store));
  assert.equal(result.status, 200);
  assert.equal(result.body.results[0].action, 'created');

  result = await json(await handlePlaybackSyncRequest(request('POST', progress), store));
  assert.equal(result.body.results[0].action, 'duplicate');

  result = await json(await handlePlaybackSyncRequest(request('GET', null, TOKEN, CONFIG, { 'X-WebHTV-Since': '0' }), store));
  assert.equal(result.body.changes.length, 1);
  assert.equal(result.body.changes[0].action, 'upsert');
  assert.equal(result.body.nextSince, '1');

  result = await json(await handlePlaybackSyncRequest(request('POST', {
    event: 'playback.deleted',
    eventId: 'delete-1',
    scope: 'item',
    historyKey: 'site-a@@@vod-1@@@1',
    siteKey: 'site-a',
    vodId: 'vod-1',
    deletedAt: Date.now() + 6000
  }), store));
  assert.equal(result.body.results[0].action, 'deleted');
  assert.equal(result.body.results[0].affected, 1);

  result = await json(await handlePlaybackSyncRequest(request('POST', {
    ...progress,
    eventId: 'progress-stale',
    timestamp: Date.now() + 5000,
    positionMs: 180000
  }), store));
  assert.equal(result.body.results[0].action, 'skipped');

  result = await json(await handlePlaybackSyncRequest(request('POST', {
    ...progress,
    eventId: 'progress-fresh',
    timestamp: Date.now() + 7000,
    positionMs: 240000
  }), store));
  assert.equal(result.body.results[0].action, 'created');

  result = await json(await handlePlaybackSyncRequest(request('GET', null, TOKEN, CONFIG, { 'X-WebHTV-Since': '0' }), store));
  assert.deepEqual(result.body.changes.map((item) => item.action), ['delete', 'upsert']);
  assert.equal(result.body.nextSince, '3');
});

test('requires explicit all deletion and keeps token/config spaces isolated', async () => {
  const store = createMemoryPlaybackStore();
  let result = await json(await handlePlaybackSyncRequest(request('POST', {
    event: 'playback.deleted',
    eventId: 'unsafe',
    deletedAt: Date.now() + 1000
  }), store));
  assert.equal(result.status, 400);
  assert.match(result.body.error, /scope=all must be explicit/);

  result = await json(await handlePlaybackSyncRequest(request('POST', {
    event: 'playback.deleted',
    eventId: 'all-1',
    scope: 'all',
    deletedAt: Date.now() + 1000
  }), store));
  assert.equal(result.status, 200);
  assert.equal(result.body.results[0].action, 'deleted');

  const otherToken = await json(await handlePlaybackSyncRequest(request('GET', null, 'other-token', CONFIG), store));
  assert.deepEqual(otherToken.body.changes, []);
  const otherConfig = await json(await handlePlaybackSyncRequest(request('GET', null, TOKEN, 'other-config'), store));
  assert.deepEqual(otherConfig.body.changes, []);
});

test('validates an entire batch before applying any record', async () => {
  const store = createMemoryPlaybackStore();
  const result = await json(await handlePlaybackSyncRequest(request('POST', {
    changes: [
      {
        event: 'playback.progress',
        eventId: 'valid-first',
        timestamp: Date.now() + 1000,
        siteKey: 'site-a',
        vodId: 'vod-1',
        vodName: '影片 A',
        episodeName: '第 1 集',
        positionMs: 1000,
        durationMs: 10000
      },
      { event: 'playback.deleted', eventId: 'invalid-second', deletedAt: Date.now() + 2000 }
    ]
  }), store));
  assert.equal(result.status, 400);

  const pull = await json(await handlePlaybackSyncRequest(request('GET'), store));
  assert.deepEqual(pull.body.changes, []);
});


test('resolves stable identity, exact aliases, host candidates, and config-type isolation', async () => {
  const store = createMemoryPlaybackStore();
  const resolveUrl = 'https://sync.example/api/playback/identity/resolve';
  const resolve = async (interfaceKey, values, configType = 'vod') => {
    const response = await handlePlaybackSyncRequest(new Request(resolveUrl, {
      method: 'POST',
      headers: { 'X-WebHTV-Token': TOKEN, 'Content-Type': 'application/json' },
      body: JSON.stringify({ schema: 'webhtv.playback.identity.v1', operation: 'resolve', interfaceKey, configType, sourceDataState: 'empty', ...values })
    }), store);
    return { response, body: await response.json() };
  };
  let result = await resolve('identity-a', { strictAddressKeys: ['strict-a'], endpointMatchKeys: ['endpoint-a'], hostMatchKeys: ['host-a'] });
  assert.equal(result.body.action, 'create');
  result = await resolve('identity-b', { strictAddressKeys: ['strict-a'], endpointMatchKeys: ['endpoint-a'], hostMatchKeys: ['host-a'] });
  assert.equal(result.body.action, 'adopt');
  assert.equal(result.body.canonicalInterfaceKey, 'identity-a');
  result = await resolve('identity-c', { strictAddressKeys: [], endpointMatchKeys: [], hostMatchKeys: ['host-a'] });
  assert.equal(result.body.action, 'confirm_required');
  result = await resolve('identity-live', { strictAddressKeys: ['strict-a'], endpointMatchKeys: ['endpoint-a'], hostMatchKeys: ['host-a'] }, 'live');
  assert.equal(result.body.action, 'create');
});
