'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const pagination = require('../../main/assets/webhome/eclipse-auto-pagination.js');

test('loads the next page when focus enters the last visual card row', () => {
  assert.equal(pagination.shouldLoadForFocus({
    hasMore: true,
    loading: false,
    itemIndex: 12,
    itemCount: 18,
    preloadCount: 6
  }), true);
  assert.equal(pagination.shouldLoadForFocus({
    hasMore: true,
    loading: false,
    itemIndex: 10,
    itemCount: 18,
    preloadCount: 6
  }), false);
});

test('does not request another page while loading or after the last page', () => {
  assert.equal(pagination.shouldLoadForFocus({
    hasMore: true,
    loading: true,
    itemIndex: 17,
    itemCount: 18,
    preloadCount: 6
  }), false);
  assert.equal(pagination.shouldLoadForFocus({
    hasMore: false,
    loading: false,
    itemIndex: 17,
    itemCount: 18,
    preloadCount: 6
  }), false);
});

test('loads when the pagination sentinel approaches the viewport', () => {
  assert.equal(pagination.shouldLoadForViewport({
    hasMore: true,
    loading: false,
    sentinelTop: 1200,
    viewportHeight: 1080,
    preloadDistance: 240
  }), true);
  assert.equal(pagination.shouldLoadForViewport({
    hasMore: true,
    loading: false,
    sentinelTop: 1500,
    viewportHeight: 1080,
    preloadDistance: 240
  }), false);
});
