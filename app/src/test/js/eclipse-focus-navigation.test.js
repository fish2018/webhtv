'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const navigation = require('../../main/assets/webhome/eclipse-focus-navigation.js');

test('horizontal navigation prefers the next item in the same focus row', () => {
  const items = [
    { row: 'classes', x: 970 },
    { row: 'classes', x: 1147 },
    { row: 'filter-0', x: 1116 }
  ];

  assert.equal(navigation.findHorizontalTarget(items, 0, 'right'), 1);
});

test('horizontal navigation stays inside each filter row', () => {
  const items = [
    { row: 'classes', x: 1147 },
    { row: 'filter-0', x: 1007 },
    { row: 'filter-0', x: 1116 },
    { row: 'filter-1', x: 1080 }
  ];

  assert.equal(navigation.findHorizontalTarget(items, 1, 'right'), 2);
  assert.equal(navigation.findHorizontalTarget(items, 2, 'left'), 1);
});

test('horizontal navigation stops at the focus row boundary', () => {
  const items = [
    { row: 'classes', x: 970 },
    { row: 'classes', x: 1147 },
    { row: 'filter-0', x: 1300 }
  ];

  assert.equal(navigation.findHorizontalTarget(items, 1, 'right'), -1);
  assert.equal(navigation.findHorizontalTarget(items, 0, 'left'), -1);
});

test('geometric navigation prefers the nearest candidate in the intended direction', () => {
  const items = [
    { left: 609, top: 983, width: 164, height: 84, row: 'actions' },
    { left: 789, top: 983, width: 163, height: 84, row: 'actions' },
    { left: 969, top: 983, width: 186, height: 84, row: 'actions' },
    { left: 650, top: 1210, width: 176, height: 70, row: 'lines' }
  ];

  assert.equal(navigation.nextFocusIndex(items, 0, 'right'), 1);
  assert.equal(navigation.nextFocusIndex(items, 1, 'right'), 2);
  assert.equal(navigation.nextFocusIndex(items, 1, 'left'), 0);
  assert.equal(navigation.nextFocusIndex(items, 0, 'down'), 3);
});

test('geometric navigation returns the first item when focus is not present', () => {
  assert.equal(navigation.nextFocusIndex([{ left: 0, top: 0, width: 10, height: 10 }], 9, 'right'), 0);
  assert.equal(navigation.nextFocusIndex([], 0, 'right'), -1);
});
