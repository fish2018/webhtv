(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module && module.exports) module.exports = api;
  else root.EclipseFocusNavigation = api;
}(this, function () {
  'use strict';

  function text(value) {
    return value === null || typeof value === 'undefined' ? '' : String(value);
  }

  function asArray(value) {
    return Object.prototype.toString.call(value) === '[object Array]' ? value : [];
  }

  function findHorizontalTarget(items, currentIndex, direction) {
    var list = asArray(items);
    var index = Number(currentIndex);
    var current;
    var row;
    var currentX;
    var best = -1;
    var bestDistance = Number.MAX_VALUE;
    var i;
    if ((direction !== 'left' && direction !== 'right') || index < 0 || index >= list.length) return -1;
    current = list[index] && typeof list[index] === 'object' ? list[index] : {};
    row = text(current.row);
    currentX = Number(current.x);
    if (!row || !isFinite(currentX)) return -1;
    for (i = 0; i < list.length; i += 1) {
      var candidate = list[i] && typeof list[i] === 'object' ? list[i] : {};
      var candidateX;
      var delta;
      var distance;
      if (i === index || text(candidate.row) !== row) continue;
      candidateX = Number(candidate.x);
      if (!isFinite(candidateX)) continue;
      delta = candidateX - currentX;
      if (direction === 'left' && delta >= -3) continue;
      if (direction === 'right' && delta <= 3) continue;
      distance = Math.abs(delta);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = i;
      }
    }
    return best;
  }

  function nextFocusIndex(items, currentIndex, direction) {
    var nodes = asArray(items);
    var current = nodes[currentIndex];
    var horizontal;
    var fromX;
    var fromY;
    var candidates;
    var row;
    var sameRow;
    var i;
    if (!current) return nodes.length ? 0 : -1;

    horizontal = direction === 'left' || direction === 'right';
    fromX = Number(current.left || 0) + Number(current.width || 0) / 2;
    fromY = Number(current.top || 0) + Number(current.height || 0) / 2;
    candidates = [];
    for (i = 0; i < nodes.length; i += 1) {
      var node;
      var dx;
      var dy;
      var primary;
      var secondary;
      if (i === currentIndex) continue;
      node = nodes[i] && typeof nodes[i] === 'object' ? nodes[i] : {};
      dx = Number(node.left || 0) + Number(node.width || 0) / 2 - fromX;
      dy = Number(node.top || 0) + Number(node.height || 0) / 2 - fromY;
      primary = direction === 'left' ? -dx : direction === 'right' ? dx : direction === 'up' ? -dy : dy;
      if (primary <= 5) continue;
      secondary = horizontal ? Math.abs(dy) : Math.abs(dx);
      candidates.push({
        index: i,
        row: text(node.row),
        score: primary * 10 + secondary * 2 + Math.sqrt(dx * dx + dy * dy)
      });
    }

    row = text(current.row);
    if (horizontal && row) {
      sameRow = candidates.filter(function (candidate) { return candidate.row === row; });
      if (sameRow.length) candidates = sameRow;
    }
    candidates.sort(function (left, right) { return left.score - right.score || left.index - right.index; });
    return candidates.length ? candidates[0].index : -1;
  }

  return {
    findHorizontalTarget: findHorizontalTarget,
    nextFocusIndex: nextFocusIndex
  };
}));
