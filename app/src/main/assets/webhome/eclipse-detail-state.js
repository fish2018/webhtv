(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.EclipseDetailState = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  function playableSources(dto) {
    return Array.isArray(dto && dto.sources) ? dto.sources.filter(Boolean) : [];
  }

  function episodes(source) {
    return Array.isArray(source && source.episodes) ? source.episodes.filter(Boolean) : [];
  }

  function create(dto, pageSize) {
    const sources = playableSources(dto);
    const selectedEpisodeSource = sources.find(source => episodes(source).some(episode => episode.selected));
    const selectedPlayableSource = sources.find(source => source.selected && episodes(source).length);
    const firstPlayableSource = sources.find(source => episodes(source).length);
    const source = selectedPlayableSource || selectedEpisodeSource || firstPlayableSource || sources[0] || null;
    const sourceEpisodes = episodes(source);
    const episode = sourceEpisodes.find(item => item.selected) || sourceEpisodes[0] || null;
    const size = Math.max(1, Math.min(240, Number(pageSize) || 120));
    const selectedIndex = episode ? Math.max(0, sourceEpisodes.indexOf(episode)) : 0;
    return {
      dto: dto || {},
      sources,
      sourceId: source ? String(source.sourceId || '') : '',
      episodeId: episode ? String(episode.episodeId || '') : '',
      page: Math.floor(selectedIndex / size) + 1,
      pageSize: size,
      favorite: Boolean(dto && dto.state && dto.state.favorite),
      truncated: Boolean(dto && dto.truncated)
    };
  }

  function currentSource(state) {
    return state.sources.find(source => String(source.sourceId || '') === state.sourceId)
      || state.sources[0]
      || null;
  }

  function selectedEpisode(state) {
    const source = currentSource(state || { sources: [] });
    const items = episodes(source);
    return items.find(item => String(item.episodeId || '') === String(state && state.episodeId || ''))
      || items[0]
      || null;
  }

  function refresh(previous, dto, pageSize) {
    const next = create(dto, pageSize || previous && previous.pageSize);
    if (!previous) return next;
    const source = next.sources.find(item => String(item.sourceId || '') === String(previous.sourceId || ''));
    if (!source) return next;
    next.sourceId = String(source.sourceId || '');
    const items = episodes(source);
    const pageCount = Math.max(1, Math.ceil(items.length / next.pageSize));
    next.page = Math.max(1, Math.min(pageCount, Number(previous.page) || 1));
    const episodeIndex = items.findIndex(item => String(item.episodeId || '') === String(previous.episodeId || ''));
    if (episodeIndex < 0) return next;
    next.episodeId = String(items[episodeIndex].episodeId || '');
    return next;
  }

  function visibleEpisodes(state) {
    const items = episodes(currentSource(state));
    const pageCount = Math.max(1, Math.ceil(items.length / state.pageSize));
    const page = Math.max(1, Math.min(pageCount, Number(state.page) || 1));
    state.page = page;
    const start = (page - 1) * state.pageSize;
    return { items: items.slice(start, start + state.pageSize), page, pageCount, total: items.length };
  }

  function episodeRanges(state) {
    const items = episodes(currentSource(state));
    const size = Math.max(1, Number(state && state.pageSize) || 20);
    const pageCount = Math.max(1, Math.ceil(items.length / size));
    const selectedPage = Math.max(1, Math.min(pageCount, Number(state && state.page) || 1));
    const ranges = [];
    for (let page = 1; page <= pageCount; page += 1) {
      const start = (page - 1) * size;
      const end = Math.min(items.length, start + size);
      if (end <= start) continue;
      ranges.push({
        label: String(start + 1) + (end > start + 1 ? '-' + String(end) : ''),
        start,
        end,
        selected: page === selectedPage
      });
    }
    return ranges;
  }

  function selectSource(state, sourceId) {
    const source = state.sources.find(item => String(item.sourceId || '') === String(sourceId || ''));
    if (!source) return false;
    state.sourceId = String(source.sourceId || '');
    const selected = episodes(source).find(item => item.selected) || episodes(source)[0] || null;
    state.episodeId = selected ? String(selected.episodeId || '') : '';
    state.page = 1;
    return true;
  }

  function safeImageUrl(value) {
    try {
      const url = new URL(String(value || ''));
      if ((url.protocol !== 'https:' && url.protocol !== 'http:') || url.username || url.password) return '';
      return url.href;
    } catch (ignore) {
      return '';
    }
  }

  function nextFocusIndex(items, currentIndex, direction) {
    const nodes = Array.isArray(items) ? items : [];
    const current = nodes[currentIndex];
    if (!current) return nodes.length ? 0 : -1;

    const horizontal = direction === 'left' || direction === 'right';
    const fromX = Number(current.left || 0) + Number(current.width || 0) / 2;
    const fromY = Number(current.top || 0) + Number(current.height || 0) / 2;
    let candidates = [];
    nodes.forEach(function (node, index) {
      if (index === currentIndex) return;
      const dx = Number(node.left || 0) + Number(node.width || 0) / 2 - fromX;
      const dy = Number(node.top || 0) + Number(node.height || 0) / 2 - fromY;
      const primary = direction === 'left' ? -dx : direction === 'right' ? dx : direction === 'up' ? -dy : dy;
      if (primary <= 5) return;
      const secondary = horizontal ? Math.abs(dy) : Math.abs(dx);
      candidates.push({
        index,
        row: String(node.row || ''),
        score: primary * 10 + secondary * 2 + Math.sqrt(dx * dx + dy * dy)
      });
    });

    const row = String(current.row || '');
    if (horizontal && row) {
      const sameRow = candidates.filter(function (candidate) { return candidate.row === row; });
      if (sameRow.length) candidates = sameRow;
    }
    candidates.sort(function (left, right) { return left.score - right.score || left.index - right.index; });
    return candidates.length ? candidates[0].index : -1;
  }

  return { create, refresh, currentSource, selectedEpisode, visibleEpisodes, episodeRanges, selectSource, safeImageUrl, nextFocusIndex };
});
