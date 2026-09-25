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

test('mobile topbar keeps primary actions in two compact rows', () => {
  const mobileCss = section(html, '@media (max-width: 640px)', '@media (min-width: 1500px)');

  assert.match(mobileCss, /\.topbar\s*\{[^}]*display:\s*grid[^}]*grid-template-columns:\s*auto minmax\(0,\s*1fr\) auto[^}]*gap:\s*10px/s);
  assert.match(mobileCss, /\.brand\s*\{[^}]*grid-column:\s*1\s*\/\s*3[^}]*grid-row:\s*1/s);
  assert.match(mobileCss, /\.source-pill\s*\{[^}]*grid-column:\s*3[^}]*grid-row:\s*1[^}]*width:\s*34vw[^}]*max-width:\s*120px/s);
  assert.match(mobileCss, /#libraryButton\s*\{[^}]*grid-column:\s*1[^}]*grid-row:\s*2/s);
  assert.match(mobileCss, /\.search\s*\{[^}]*grid-column:\s*2\s*\/\s*4[^}]*grid-row:\s*2[^}]*margin:\s*0/s);
  assert.match(mobileCss, /\.top-action\s*\{[^}]*height:\s*42px/s);
  assert.match(mobileCss, /\.top-action\.settings-action\s*\{\s*display:\s*none/);
});