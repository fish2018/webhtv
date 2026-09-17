'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const stateApi = require('../../main/assets/webhome/eclipse-category-state.js');

function state(items) {
  return {
    generation: 0,
    items: items || [],
    page: 1,
    pageCount: 0,
    hasMore: true,
    truncated: false,
    loading: false
  };
}

test('unknown page count continues after a non-empty page and stops on an empty page', () => {
  const first = state();
  const firstRequest = stateApi.begin(first, 'movie', 1, false, {});

  assert.equal(stateApi.commit(first, firstRequest, {
    page: 1,
    pageCount: 0,
    hasMore: true,
    items: [{ vodId: 'one' }]
  }), true);
  assert.equal(first.hasMore, true);

  const secondRequest = stateApi.begin(first, 'movie', 2, true, {});
  assert.equal(stateApi.commit(first, secondRequest, {
    page: 2,
    pageCount: 0,
    hasMore: true,
    items: []
  }), true);
  assert.equal(first.hasMore, false);
});

test('a failed append does not advance the page and can be retried', () => {
  const current = state([{ vodId: 'one' }]);
  current.page = 1;
  const request = stateApi.begin(current, 'movie', 2, true, {});

  assert.equal(stateApi.fail(current, request), true);
  assert.equal(current.page, 1);
  assert.equal(current.loading, false);

  const retry = stateApi.begin(current, 'movie', current.page + 1, true, {});
  assert.equal(retry.page, 2);
});

test('only the newest category or filter request may commit a response', () => {
  const current = state([{ vodId: 'old' }]);
  const first = stateApi.begin(current, 'movie', 1, false, { year: '2025' });
  const latest = stateApi.begin(current, 'movie', 1, false, { year: '2026' });

  assert.equal(stateApi.commit(current, first, {
    page: 1,
    pageCount: 1,
    hasMore: false,
    items: [{ vodId: 'stale' }]
  }), false);
  assert.deepEqual(current.items, []);
  assert.equal(stateApi.commit(current, latest, {
    page: 1,
    pageCount: 1,
    hasMore: false,
    items: [{ vodId: 'latest' }]
  }), true);
  assert.deepEqual(current.items, [{ vodId: 'latest' }]);
});

test('starting a replacement request clears the previous category result', () => {
  const current = state([{ vodId: 'previous-category' }]);
  stateApi.begin(current, 'series', 1, false, {});

  assert.deepEqual(current.items, []);
  assert.equal(current.hasMore, false);
});

test('a truncated native response remains visible to the renderer', () => {
  const current = state();
  const request = stateApi.begin(current, 'movie', 1, false, {});

  assert.equal(stateApi.commit(current, request, {
    page: 1,
    pageCount: 1,
    hasMore: false,
    truncated: true,
    items: [{ vodId: 'one' }]
  }), true);
  assert.equal(current.truncated, true);

  stateApi.begin(current, 'series', 1, false, {});
  assert.equal(current.truncated, false);
});
