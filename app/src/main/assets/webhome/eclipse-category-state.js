(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module && module.exports) module.exports = api;
  else root.EclipseCategoryState = api;
}(this, function () {
  'use strict';

  function asArray(value) {
    return Object.prototype.toString.call(value) === '[object Array]' ? value : [];
  }

  function copy(value) {
    var result = {};
    var key;
    if (!value || typeof value !== 'object') return result;
    for (key in value) {
      if (Object.prototype.hasOwnProperty.call(value, key)) result[key] = value[key];
    }
    return result;
  }

  function page(value, fallback) {
    var number = Number(value);
    return isFinite(number) && number >= 1 ? Math.floor(number) : fallback;
  }

  function pageCount(value) {
    var number = Number(value);
    return isFinite(number) && number >= 0 ? Math.floor(number) : 0;
  }

  function begin(state, typeId, requestedPage, append, extend) {
    var request = {
      generation: (Number(state.generation) || 0) + 1,
      typeId: String(typeId || ''),
      page: page(requestedPage, 1),
      append: !!append,
      extend: copy(extend)
    };
    state.generation = request.generation;
    state.loading = true;
    if (!request.append) {
      state.items = [];
      state.hasMore = false;
      state.truncated = false;
    }
    return request;
  }

  function current(state, request) {
    return !!request && Number(state.generation) === request.generation;
  }

  function commit(state, request, data) {
    var items;
    if (!current(state, request)) return false;
    items = asArray(data && data.items);
    state.items = request.append ? asArray(state.items).concat(items) : items;
    state.page = page(data && data.page, request.page);
    state.pageCount = pageCount(data && data.pageCount);
    state.hasMore = items.length > 0 && !!(data && data.hasMore);
    state.truncated = !!state.truncated || !!(data && data.truncated);
    state.loading = false;
    return true;
  }

  function fail(state, request) {
    if (!current(state, request)) return false;
    state.loading = false;
    return true;
  }

  function invalidate(state) {
    state.generation = (Number(state.generation) || 0) + 1;
    state.loading = false;
  }

  return {
    begin: begin,
    commit: commit,
    fail: fail,
    invalidate: invalidate
  };
}));
