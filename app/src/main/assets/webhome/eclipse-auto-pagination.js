(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module && module.exports) module.exports = api;
  else root.EclipseAutoPagination = api;
}(this, function () {
  'use strict';

  function number(value, fallback) {
    var parsed = Number(value);
    return isFinite(parsed) ? parsed : fallback;
  }

  function ready(options) {
    return !!(options && options.hasMore && !options.loading);
  }

  function shouldLoadForFocus(options) {
    var count;
    var index;
    var preload;
    if (!ready(options)) return false;
    count = Math.max(0, Math.floor(number(options.itemCount, 0)));
    index = Math.floor(number(options.itemIndex, -1));
    preload = Math.max(1, Math.floor(number(options.preloadCount, 1)));
    if (!count || index < 0 || index >= count) return false;
    return index >= Math.max(0, count - preload);
  }

  function shouldLoadForViewport(options) {
    var top;
    var height;
    var preload;
    if (!ready(options)) return false;
    top = number(options.sentinelTop, Number.MAX_VALUE);
    height = Math.max(0, number(options.viewportHeight, 0));
    preload = Math.max(0, number(options.preloadDistance, 0));
    return height > 0 && top <= height + preload;
  }

  return {
    shouldLoadForFocus: shouldLoadForFocus,
    shouldLoadForViewport: shouldLoadForViewport
  };
}));
