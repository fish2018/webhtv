package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TmdbSeasonMatchCacheTest {

    @Test
    public void manualSeasonZeroRoundTripsForSameSourceAndTmdb() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();

        cache.put("site", "vod", "示例剧 特别篇", 100, "tv", 0,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fingerprint", 4, 4);

        TmdbSeasonMatchCache.Entry entry = cache.find("site", "vod", "示例剧 特别篇", 100);
        assertEquals(Integer.valueOf(0), entry.getSeasonNumber());
        assertEquals(TmdbSeasonMatchCache.Mode.MANUAL_SEASON, entry.getMode());
    }

    @Test
    public void bindingIsScopedBySourceTitle() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();

        cache.put("site", "shared", "示例剧 第一季", 100, "tv", 1,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "first", 10, 10);
        cache.put("site", "shared", "示例剧 第二季", 100, "tv", 2,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "second", 8, 8);

        assertEquals(Integer.valueOf(1), cache.find("site", "shared", "示例剧 第一季", 100).getSeasonNumber());
        assertEquals(Integer.valueOf(2), cache.find("site", "shared", "示例剧 第二季", 100).getSeasonNumber());
    }

    @Test
    public void bindingIsScopedByPlaybackFlag() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();

        cache.put("site", "vod", "Series", "flag-s1", 100, "tv", 1,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "first", 10, 10);
        cache.put("site", "vod", "Series", "flag-s2", 100, "tv", 2,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "second", 8, 8);

        assertEquals(Integer.valueOf(1), cache.find("site", "vod", "Series", "flag-s1", 100).getSeasonNumber());
        assertEquals(Integer.valueOf(2), cache.find("site", "vod", "Series", "flag-s2", 100).getSeasonNumber());
    }

    @Test
    public void multiFlagLookupDoesNotReuseLegacyVodBinding() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        cache.put("site", "vod", "Series", 100, "tv", 1,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "legacy", 8, 8);

        assertNull(cache.find("site", "vod", "Series", "new-flag", 100));
    }

    @Test
    public void singleFlagLookupMayReuseLegacyVodBindingExplicitly() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        cache.put("site", "vod", "Series", 100, "tv", 1,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "legacy", 8, 8);

        assertEquals(Integer.valueOf(1), cache.find(
                "site", "vod", "Series", "only-flag", 100, true).getSeasonNumber());
    }

    @Test
    public void bindingIsRejectedWhenTmdbIdentityChanges() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();

        cache.put("site", "vod", "示例剧 第二季", 100, "tv", 2,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fingerprint", 8, 8);

        assertNull(cache.find("site", "vod", "示例剧 第二季", 200));
    }

    @Test
    public void identityChangeRemovesOldBinding() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        cache.put("site", "vod", "示例剧集", 100, "tv", 2,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fingerprint", 8, 8);

        assertTrue(cache.removeIfMediaChanged("site", "vod", "示例剧集", 200, "tv"));
        assertNull(cache.find("site", "vod", "示例剧集", 200));
        assertFalse(cache.removeIfMediaChanged("site", "vod", "示例剧集", 200, "tv"));
    }

    @Test
    public void movieRematchRemovesBindingEvenWhenNumericIdMatches() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        cache.put("site", "vod", "示例剧集", 100, "tv", 2,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fingerprint", 8, 8);

        assertTrue(cache.removeIfMediaChanged("site", "vod", "示例剧集", 100, "movie"));
        assertNull(cache.find("site", "vod", "示例剧集", 100));
    }

    @Test
    public void unchangedTvIdentityKeepsBinding() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        cache.put("site", "vod", "示例剧集", 100, "tv", 2,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fingerprint", 8, 8);

        assertFalse(cache.removeIfMediaChanged("site", "vod", "示例剧集", 100, "tv"));
        assertEquals(Integer.valueOf(2), cache.find("site", "vod", "示例剧集", 100).getSeasonNumber());
    }

    @Test
    public void staleEpisodeStructureInvalidatesManualBindingFreshness() {
        TmdbSeasonMatchCache.Entry entry = TmdbSeasonMatchCache.Entry.create(
                100, "tv", 1, TmdbSeasonMatchCache.Mode.MANUAL_SEASON,
                "shape-a", 2, 2);

        assertTrue(entry.isFresh("shape-a", 2, 2));
        assertFalse(entry.isFresh("shape-b", 2, 2));
        assertFalse(entry.isFresh("shape-a", 3, 2));
        assertFalse(entry.isFresh("shape-a", 2, 3));
    }

    @Test
    public void routeBindingsRetainEveryRouteThatServesRequestedSeason() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        cache.recordRouteBinding("site-a", "vod-a", "line#0", "line", 100, "tv",
                TmdbSeasonScope.multi(List.of(1, 2)));
        cache.recordRouteBinding("site-b", "vod-b", "line#0", "line", 100, "tv",
                TmdbSeasonScope.multi(List.of(1, 2)));

        assertEquals(1, cache.findRouteBindings("site-a", "vod-a", 100, "tv", 1).size());
        assertEquals(1, cache.findRouteBindings("site-b", "vod-b", 100, "tv", 1).size());
        assertTrue(cache.findRouteBindings("site-a", "vod-a", 100, "tv", 3).isEmpty());
    }

    @Test
    public void routeBindingIndexGroupsAllMatchingRoutesInOneScan() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        cache.recordRouteBinding("site-a", "vod-a", "line#0", "line", 100, "tv",
                TmdbSeasonScope.multi(List.of(1, 2)));
        cache.recordRouteBinding("site-b", "vod-b", "line#0", "line", 100, "tv",
                TmdbSeasonScope.known(1));
        cache.recordRouteBinding("site-c", "vod-c", "line#0", "line", 200, "tv",
                TmdbSeasonScope.known(1));

        Map<String, List<TmdbSeasonMatchCache.RouteBinding>> index =
                cache.indexRouteBindings(100, "tv", 1);

        assertEquals(2, index.size());
        assertEquals(1, index.get(TmdbSeasonMatchCache.routeIdentity("site-a", "vod-a")).size());
    }

    @Test
    public void routeBindingCacheHasABoundedPersistentSize() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        for (int i = 0; i < 520; i++) {
            cache.recordRouteBinding("site-" + i, "vod-" + i, "line#0", "line", 100, "tv",
                    TmdbSeasonScope.known(1));
        }

        int retained = cache.indexRouteBindings(100, "tv", 1).values().stream()
                .mapToInt(List::size).sum();

        assertEquals(512, retained);
    }

    @Test
    public void manualFlatRoundTripsWithoutSeasonNumber() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();

        cache.put("site", "vod", "示例剧 全集", 100, "tv", null,
                TmdbSeasonMatchCache.Mode.MANUAL_FLAT, "fingerprint", 24, 0);

        TmdbSeasonMatchCache.Entry entry = cache.find("site", "vod", "示例剧 全集", 100);
        assertNull(entry.getSeasonNumber());
        assertEquals(TmdbSeasonMatchCache.Mode.MANUAL_FLAT, entry.getMode());
    }

    @Test
    public void manualMultiSliceRoundTripsWithoutSeasonNumber() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();

        cache.put("site", "vod", "full series", 100, "tv", null,
                TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE, "fingerprint", 24, 0);

        TmdbSeasonMatchCache.Entry entry = cache.find("site", "vod", "full series", 100);
        assertNull(entry.getSeasonNumber());
        assertEquals(TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE, entry.getMode());
    }
    @Test
    public void removeOnlyClearsMatchingSourceBinding() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        cache.put("site", "shared", "示例剧 第一季", 100, "tv", 1,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "first", 10, 10);
        cache.put("site", "shared", "示例剧 第二季", 100, "tv", 2,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "second", 8, 8);

        cache.remove("site", "shared", "示例剧 第一季");

        assertNull(cache.find("site", "shared", "示例剧 第一季", 100));
        assertEquals(Integer.valueOf(2), cache.find("site", "shared", "示例剧 第二季", 100).getSeasonNumber());
    }
}
