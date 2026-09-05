'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const detailState = require('../../main/assets/webhome/eclipse-detail-state.js');

function dto(count) {
  return {
    item: { vodId: 'vod-1', name: 'Eclipse' },
    sources: [
      {
        sourceId: 'line-0',
        name: '线路一',
        selected: false,
        episodes: Array.from({ length: count }, (_, index) => ({
          episodeId: 'episode-' + index,
          name: '第 ' + (index + 1) + ' 集',
          playRef: 'play-' + index,
          selected: index === 130
        }))
      },
      { sourceId: 'line-1', name: '线路二', selected: true, episodes: [] }
    ],
    state: { favorite: true },
    truncated: true
  };
}

test('detail state prefers a selected playable episode over an empty selected source', () => {
  const state = detailState.create(dto(240), 120);

  assert.equal(state.sourceId, 'line-0');
  assert.equal(state.episodeId, 'episode-130');
  assert.equal(state.page, 2);
  assert.equal(state.favorite, true);
  assert.equal(state.truncated, true);
});

test('episode paging bounds DOM work and preserves opaque playRef', () => {
  const state = detailState.create(dto(241), 120);
  state.page = 1;

  const first = detailState.visibleEpisodes(state);
  assert.equal(first.items.length, 120);
  assert.equal(first.pageCount, 3);
  assert.equal(first.items[0].playRef, 'play-0');

  state.page = 99;
  const last = detailState.visibleEpisodes(state);
  assert.equal(last.page, 3);
  assert.equal(last.items.length, 1);
});

test('TMDB refresh preserves the viewer source, episode and page', () => {
  const initial = detailState.create(dto(241), 120);
  initial.sourceId = 'line-0';
  initial.episodeId = 'episode-239';
  initial.page = 2;
  const updatedDto = dto(241);
  updatedDto.people = [{ personId: 1, name: '演员甲', role: '角色甲' }];
  updatedDto.sources[0].episodes[239].title = '终章';
  updatedDto.sources[0].episodes[239].still = 'https://img.example/still.jpg';

  const refreshed = detailState.refresh(initial, updatedDto, 120);

  assert.equal(refreshed.sourceId, 'line-0');
  assert.equal(refreshed.episodeId, 'episode-239');
  assert.equal(refreshed.page, 2);
  assert.equal(detailState.selectedEpisode(refreshed).title, '终章');
});

test('TMDB refresh keeps a manually paged episode viewport', () => {
  const initial = detailState.create(dto(241), 120);
  initial.episodeId = 'episode-0';
  initial.page = 2;

  const refreshed = detailState.refresh(initial, dto(241), 120);

  assert.equal(refreshed.episodeId, 'episode-0');
  assert.equal(refreshed.page, 2);
});

test('image URLs allow only ordinary http and https resources', () => {
  assert.equal(detailState.safeImageUrl('https://img.example/poster.jpg'), 'https://img.example/poster.jpg');
  assert.equal(detailState.safeImageUrl('http://img.example/poster.jpg'), 'http://img.example/poster.jpg');
  assert.equal(detailState.safeImageUrl('javascript:alert(1)'), '');
  assert.equal(detailState.safeImageUrl('data:image/svg+xml,evil'), '');
  assert.equal(detailState.safeImageUrl('file:///sdcard/secret'), '');
});

test('horizontal D-pad navigation stays within the current focus row', () => {
  const nodes = [
    { left: 609, top: 983, width: 164, height: 84, row: 'actions' },
    { left: 789, top: 983, width: 163, height: 84, row: 'actions' },
    { left: 969, top: 983, width: 186, height: 84, row: 'actions' },
    { left: 650, top: 1210, width: 176, height: 70, row: 'lines' }
  ];

  assert.equal(detailState.nextFocusIndex(nodes, 0, 'right'), 1);
  assert.equal(detailState.nextFocusIndex(nodes, 1, 'right'), 2);
  assert.equal(detailState.nextFocusIndex(nodes, 1, 'left'), 0);
  assert.equal(detailState.nextFocusIndex(nodes, 0, 'down'), 3);
});

test('detail page wires action buttons through row-aware D-pad navigation', () => {
  const html = fs.readFileSync(path.join(__dirname, '../../main/assets/webhome/eclipse-detail.html'), 'utf8');

  assert.match(html, /id="playButton"[^>]*data-focus-row="actions"/);
  assert.match(html, /id="favoriteButton"[^>]*data-focus-row="actions"/);
  assert.match(html, /id="nativeButton"[^>]*data-focus-row="actions"/);
  assert.match(html, /<script src="eclipse-focus-navigation\.js"><\/script>/);
  assert.match(html, /EclipseFocusNavigation\.nextFocusIndex\(geometry, index, direction\)/);
});

test('detail sample renders optional cast, gallery, episode metadata and recommendations', () => {
  const html = fs.readFileSync(path.join(__dirname, '../../main/assets/webhome/eclipse-detail.html'), 'utf8');

  assert.match(html, /id="peopleSection"/);
  assert.match(html, /id="gallerySection"/);
  assert.match(html, /id="episodeFeature"/);
  assert.match(html, /id="recommendationSection"/);
  assert.match(html, /data-focus-row', rowId/);
  assert.match(html, /addEventListener\('fmdetailchange'/);
  assert.match(html, /vodDetail\(\{[\s\S]*cached:\s*true/);
  assert.match(html, /recommendationGroups/);
  assert.match(html, /api\.person\.open/);
  assert.match(html, /api\.image\.preview/);
  assert.match(html, /\(canPreview \|\| canSave\) && value\.imageRef/);
  assert.match(html, /api\.recommendation\.open/);
  assert.match(html, /api\.external\.open/);
  assert.match(html, /--fm-safe-top/);
  assert.match(html, /scroll-padding-inline/);
  assert.match(html, /function ensureRailItemVisible\(/);
  assert.match(html, /function bindLongPress\(/);
  assert.match(html, /event\.repeat[\s\S]*markFired\(event\)/);
  assert.match(html, /api\.episode\.info/);
  assert.match(html, /EclipseDetailState\.episodeRanges/);
  assert.match(html, /episode-marquee-track/);
  assert.match(html, /item\.pic \|\| item\.backdrop/);
  assert.doesNotMatch(html, /-webkit-line-clamp:\s*3/);
});

test('recommendation rails stay viewport-bounded and feedback restores focus', () => {
  const html = fs.readFileSync(path.join(__dirname, '../../main/assets/webhome/eclipse-detail.html'), 'utf8');

  assert.match(html, /\.recommendation-group\s*\{[^}]*min-width:\s*0/);
  assert.match(html, /function focusAfterRecommendationRemoval\(/);
  assert.match(html, /function focusByRow\(/);
  assert.match(html, /rowIndex/);
  assert.match(html, /focusByRow\(marker\.row, marker\.rowIndex\)/);
  assert.match(html, /bindLongPress\(card,[\s\S]*card\.focus\(\)[\s\S]*openRecommendationInfo/);
  assert.match(html, /function updateRecommendationSummary\(/);
  const summary = html.slice(html.indexOf('function updateRecommendationSummary'), html.indexOf('function focusAfterRecommendationRemoval'));
  assert.match(summary, /setText\('recommendationStatus'/);
  assert.doesNotMatch(summary, /updateRecommendationSummary\(\);/);
  assert.match(html, /api && typeof api\.search === 'function'/);
});
test('favorite updates restore D-pad focus after the pending state', () => {
  const html = fs.readFileSync(path.join(__dirname, '../../main/assets/webhome/eclipse-detail.html'), 'utf8');
  const restores = html.match(/finishPendingFocus\('favoriteButton'\)/g) || [];

  assert.match(html, /function finishPendingFocus\(id\)/);
  assert.equal(restores.length, 2);
});

test('retry cancels the pending automatic native fallback', () => {
  const html = fs.readFileSync(path.join(__dirname, '../../main/assets/webhome/eclipse-detail.html'), 'utf8');

  assert.match(html, /function clearNativeFallback\(\)/);
  assert.match(html, /function loadDetail\(\) \{[\s\S]*?clearNativeFallback\(\)/);
  assert.match(html, /function scheduleNativeFallback\(\)/);
});


test('episode ranges keep large selections quick to browse', () => {
  const state = detailState.create(dto(100), 20);
  assert.deepEqual(detailState.episodeRanges(state), [
    { label: '1-20', start: 0, end: 20, selected: true },
    { label: '21-40', start: 20, end: 40, selected: false },
    { label: '41-60', start: 40, end: 60, selected: false },
    { label: '61-80', start: 60, end: 80, selected: false },
    { label: '81-100', start: 80, end: 100, selected: false }
  ]);
});

test('episode state exposes a compact selected group after refresh', () => {
  const state = detailState.create(dto(36), 20);
  state.episodeId = 'episode-35';
  state.page = 2;
  const refreshed = detailState.refresh(state, dto(36), 20);
  const page = detailState.visibleEpisodes(refreshed);
  assert.equal(page.page, 2);
  assert.equal(page.items.length, 16);
  assert.equal(detailState.selectedEpisode(refreshed).episodeId, 'episode-35');
});
