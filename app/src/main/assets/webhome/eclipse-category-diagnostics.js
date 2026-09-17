(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module && module.exports) module.exports = api;
  else root.EclipseCategoryDiagnostics = api;
}(this, function () {
  'use strict';

  function text(value) {
    return value === null || typeof value === 'undefined' ? '' : String(value);
  }

  function asArray(value) {
    return Object.prototype.toString.call(value) === '[object Array]' ? value : [];
  }

  function itemIdentity(item) {
    item = item && typeof item === 'object' ? item : {};
    return [
      text(item.kind),
      text(item.siteKey),
      text(item.vodId || item.id),
      text(item.name),
      text(item.pic)
    ].join('\u001f');
  }

  function fingerprint(items) {
    var list = asArray(items);
    var result;
    var i;
    if (!list.length) return '';
    result = [text(list.length)];
    for (i = 0; i < list.length; i += 1) result.push(itemIdentity(list[i]));
    return result.join('\u001e');
  }

  function record(snapshots, typeId, typeName, items) {
    var id = text(typeId);
    var key = '$' + id;
    var value = fingerprint(items);
    var own = Object.prototype.hasOwnProperty;
    var candidate;
    var match = null;
    if (!snapshots || !id || !value) return null;
    for (candidate in snapshots) {
      if (!own.call(snapshots, candidate) || candidate === key) continue;
      if (snapshots[candidate].fingerprint === value) {
        match = snapshots[candidate];
        break;
      }
    }
    snapshots[key] = {
      typeId: id,
      typeName: text(typeName) || id,
      fingerprint: value
    };
    return match ? { typeId: match.typeId, typeName: match.typeName } : null;
  }

  return {
    fingerprint: fingerprint,
    record: record
  };
}));
