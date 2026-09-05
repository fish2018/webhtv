'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const diagnostics = require('../../main/assets/webhome/eclipse-category-diagnostics.js');

function items() {
  return [
    { kind: 'vod', siteKey: 'site', vodId: '100', name: '第一部', pic: 'https://img/100.jpg' },
    { kind: 'vod', siteKey: 'site', vodId: '200', name: '第二部', pic: 'https://img/200.jpg' }
  ];
}

test('reports an exact duplicate returned for a different category', () => {
  const snapshots = {};

  assert.equal(diagnostics.record(snapshots, '1', '电影', items()), null);
  assert.deepEqual(diagnostics.record(snapshots, '2', '剧集', items()), {
    typeId: '1',
    typeName: '电影'
  });
});

test('does not report categories whose item identities differ', () => {
  const snapshots = {};
  const changed = items();
  changed[1] = Object.assign({}, changed[1], { vodId: '201' });

  assert.equal(diagnostics.record(snapshots, '1', '电影', items()), null);
  assert.equal(diagnostics.record(snapshots, '2', '剧集', changed), null);
});

test('ignores empty results and keeps snapshots source-local', () => {
  const snapshots = {};

  assert.equal(diagnostics.record(snapshots, '1', '电影', []), null);
  assert.deepEqual(snapshots, {});
  assert.notEqual(diagnostics.fingerprint(items()), '');
  assert.equal(diagnostics.fingerprint([]), '');
});
