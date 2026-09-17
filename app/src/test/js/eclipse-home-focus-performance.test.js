'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const html = fs.readFileSync(path.join(__dirname, '../../main/assets/webhome/eclipse.html'), 'utf8');

function section(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.notEqual(from, -1, 'missing section start: ' + start);
  assert.notEqual(to, -1, 'missing section end: ' + end);
  return source.slice(from, to);
}

test('home D-pad navigation reuses one layout snapshot per key press', () => {
  const snapshot = section(html, 'function getFocusableSnapshot()', 'function getFocusable()');
  const rowMove = section(html, 'function moveFocusInRow', 'function moveFocus(direction)');
  const move = section(html, 'function moveFocus(direction)', 'function keydown');

  assert.match(snapshot, /rects\.push\(rect\)/);
  assert.match(move, /var snapshot = getFocusableSnapshot\(\)/);
  assert.match(move, /moveFocusInRow\(list, snapshot\.rects, current, direction\)/);
  assert.doesNotMatch(rowMove, /getBoundingClientRect/);
  assert.doesNotMatch(move, /getBoundingClientRect/);
});

test('card focus frame follows the poster radius without multi-frame effects', () => {
  const posterRule = html.match(/\.poster \{([\s\S]*?)\n    \}/);
  const focusRule = html.match(/\.card-button:focus \.poster \{([\s\S]*?)\n    \}/);
  assert.ok(posterRule, 'missing poster rule');
  assert.ok(focusRule, 'missing card focus rule');

  assert.doesNotMatch(posterRule[1], /transition:/);
  assert.doesNotMatch(html, /\.card-button:focus \.poster,\s*\.tab:focus/);
  assert.match(posterRule[1], /border-radius:\s*14px/);
  assert.match(focusRule[1], /box-shadow:\s*0 0 0 3px #785fff/);
  assert.doesNotMatch(focusRule[1], /outline|transform|transition/);
});
