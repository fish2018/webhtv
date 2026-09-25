package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.google.gson.Gson;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TmdbSourceMergerTest {

    private static final Gson GSON = new Gson();

    @Test
    public void sourceValuesWinAndMissingFieldsFillFromNetwork() {
        TmdbBundle source = bundle(item("Source", "", ""), "{\"id\":1,\"overview\":\"\",\"credits\":{\"cast\":[]}}", List.of(), List.of());
        TmdbBundle network = bundle(item("Network", "Network overview", "poster.jpg"), "{\"id\":1,\"overview\":\"Network overview\",\"genres\":[{\"id\":18}],\"credits\":{\"cast\":[{\"id\":9}]}}", List.of(new TmdbPerson(9, "Actor", "Hero", "", "", "")), List.of());
        TmdbSourcePayload payload = GSON.fromJson("{\"schema\":1,\"id\":1,\"media_type\":\"movie\",\"complete\":[\"core\",\"credits\",\"images\"],\"detail\":{\"id\":1}}", TmdbSourcePayload.class);

        TmdbSourceMerger.MergedBundle merged = TmdbSourceMerger.merge(source, payload, null, network);

        assertEquals("Source", merged.bundle().item().getTitle());
        assertEquals("Network overview", merged.bundle().item().getOverview());
        assertEquals("poster.jpg", merged.bundle().item().getPosterUrl());
        assertTrue(merged.bundle().detail().getAsJsonArray("genres").size() == 1);
        assertTrue(merged.bundle().cast().isEmpty());
        assertEquals(TmdbSourceMerger.Origin.REMOTE_TMDB, merged.origin("detail.overview"));
        assertEquals(TmdbSourceMerger.Origin.SOURCE, merged.origin("item.title"));
    }

    @Test
    public void localCacheFillsBeforeNetworkWithoutOverwritingSource() {
        TmdbItem sourceItem = new TmdbItem(1, "movie", "Source", "", "Source overview", "", "");
        TmdbBundle source = bundle(sourceItem, "{\"id\":1,\"overview\":\"Source overview\"}", List.of(), List.of());
        TmdbBundle cache = bundle(new TmdbItem(1, "movie", "Cache", "", "Cache overview", "cache.jpg", ""), "{\"id\":1,\"overview\":\"Cache overview\",\"poster_path\":\"cache.jpg\"}", List.of(), List.of());
        TmdbBundle network = bundle(new TmdbItem(1, "movie", "Network", "", "Network overview", "network.jpg", ""), "{\"id\":1,\"overview\":\"Network overview\",\"poster_path\":\"network.jpg\"}", List.of(), List.of());
        TmdbSourcePayload payload = GSON.fromJson("{\"schema\":1,\"id\":1,\"media_type\":\"movie\",\"complete\":[\"core\"],\"detail\":{\"id\":1}}", TmdbSourcePayload.class);

        TmdbSourceMerger.MergedBundle merged = TmdbSourceMerger.merge(source, payload, cache, network);

        assertEquals("Source overview", merged.bundle().item().getOverview());
        assertEquals("cache.jpg", merged.bundle().item().getPosterUrl());
        assertEquals(TmdbSourceMerger.Origin.LOCAL_CACHE, merged.origin("item.poster"));
    }

    @Test
    public void completeEmptySeasonDoesNotGetReplacedByNetwork() {
        TmdbBundle source = bundle(item("Show", "", ""), "{\"id\":1,\"seasons\":[{\"season_number\":2,\"episodes\":[]}]}", List.of(), List.of(), Map.of(2, List.of()));
        TmdbBundle network = bundle(item("Show", "", ""), "{\"id\":1,\"seasons\":[{\"season_number\":2,\"episodes\":[{\"episode_number\":1}]}]}", List.of(), List.of(), Map.of(2, List.of(new com.fongmi.android.tv.bean.TmdbEpisode(1, "Episode", "", "", "", 0, 0, 1, 2))));
        TmdbSourcePayload payload = GSON.fromJson("{\"schema\":1,\"id\":1,\"media_type\":\"tv\",\"season_number\":2,\"complete\":[\"season:2\"],\"detail\":{\"id\":1,\"season_number\":2}}", TmdbSourcePayload.class);

        TmdbSourceMerger.MergedBundle merged = TmdbSourceMerger.merge(source, payload, null, network);

        assertTrue(merged.bundle().seasonEpisodes().get(2).isEmpty());
    }

    @Test
    public void incompatibleEnglishSourceCoreAllowsChineseNetworkOverride() {
        TmdbItem sourceItem = new TmdbItem(1, "movie", "Source English", "", "Source English overview", "", "");
        TmdbBundle source = bundle(sourceItem, "{\"id\":1,\"title\":\"Source English\",\"overview\":\"Source English overview\"}", List.of(), List.of());
        TmdbBundle network = bundle(new TmdbItem(1, "movie", "中文标题", "", "中文简介", "", ""), "{\"id\":1,\"title\":\"中文标题\",\"overview\":\"中文简介\"}", List.of(), List.of());
        TmdbSourcePayload payload = GSON.fromJson("{\"schema\":1,\"id\":1,\"media_type\":\"movie\",\"language\":\"en-US\",\"complete\":[\"core\"],\"detail\":{\"id\":1}}", TmdbSourcePayload.class);

        TmdbSourceMerger.MergedBundle merged = TmdbSourceMerger.merge(source, payload, null, network, "zh-CN");

        assertEquals("中文标题", merged.bundle().item().getTitle());
        assertEquals("中文简介", merged.bundle().item().getOverview());
        assertEquals(TmdbSourceMerger.Origin.REMOTE_TMDB_LANGUAGE, merged.origin("item.title"));
        assertEquals(TmdbSourceMerger.Origin.REMOTE_TMDB_LANGUAGE, merged.origin("item.overview"));
    }

    @Test
    public void identityMismatchLeavesSourceBundleUntouched() {
        TmdbBundle source = bundle(item("Source", "Overview", ""), "{\"id\":1,\"overview\":\"Overview\"}", List.of(), List.of());
        TmdbBundle network = bundle(item("Wrong", "Wrong", "wrong.jpg"), "{\"id\":1,\"overview\":\"Wrong\"}", List.of(new TmdbPerson(9, "Actor", "", "", "", "")), List.of());
        network = new TmdbBundle(new TmdbItem(2, "movie", "Wrong", "", "Wrong", "wrong.jpg", ""), network.detail(), network.cast(), network.creators(), network.photos(), network.related(), network.seasons(), network.seasonCounts(), network.seasonEpisodes(), network.seasonCast(), network.seasonPhotos());

        TmdbSourceMerger.MergedBundle merged = TmdbSourceMerger.merge(source, null, null, network);

        assertEquals("Source", merged.bundle().item().getTitle());
        assertTrue(merged.bundle().detail().get("overview").getAsString().equals("Overview"));
    }

    private static TmdbItem item(String title, String overview, String poster) {
        return new TmdbItem(1, "movie", title, "", overview, poster, "");
    }

    private static TmdbBundle bundle(TmdbItem item, String detail, List<TmdbPerson> cast, List<TmdbItem> related) {
        return bundle(item, detail, cast, related, Map.of());
    }

    private static TmdbBundle bundle(TmdbItem item, String detail, List<TmdbPerson> cast, List<TmdbItem> related, Map<Integer, List<com.fongmi.android.tv.bean.TmdbEpisode>> episodes) {
        return new TmdbBundle(item, JsonParser.parseString(detail).getAsJsonObject(), cast, List.of(), List.of(), related, List.of(), Map.of(), episodes, Map.of(), Map.of());
    }
}
