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

test('mobile WebHome reserves the native top safe area only for edge chrome', () => {
  const mobileCss = section(html, '@media (max-width: 640px)', '@media (min-width: 1500px)');
  const safeAreaLogic = section(html, 'function shouldUseTopSafeArea', 'function status(');

  assert.match(html, /viewport-fit=cover/);
  assert.match(html, /--eclipse-safe-top:\s*max\(var\(--fm-safe-top, 0px\), env\(safe-area-inset-top, 0px\)\)/);
  assert.match(html, /html\.safe-top-active\s*\{\s*--eclipse-active-safe-top:\s*var\(--eclipse-safe-top\)/);
  assert.match(mobileCss, /padding-top:\s*calc\(14px \+ var\(--eclipse-active-safe-top, 0px\)\)/);
  assert.match(safeAreaLogic, /mode === 'edge' \|\| mode === 'immersive'/);
  assert.doesNotMatch(safeAreaLogic, /mode === 'normal'/);
  assert.match(html, /listen\(w, 'fmviewport', function \(event\)/);
  assert.match(html, /refreshSafeAreaMode\(\)/);
});
