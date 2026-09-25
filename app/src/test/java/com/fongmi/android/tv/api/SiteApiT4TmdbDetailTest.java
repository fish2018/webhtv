package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.fongmi.android.tv.utils.VodDetailCache;
import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SiteApiT4TmdbDetailTest {

    private static final Gson GSON = new Gson();
    private static final String JSON = """
            {"code":0,"msg":"ok","list":[{"vod_id":"t4-tv-1","vod_name":"T4 Example",
              "vod_play_from":"line-a","vod_play_url":"S01E01$https://media.test/episode1.m3u8",
              "tmdb":{"schema":1,"id":1399,"media_type":"tv","season_number":1,"language":"zh-CN",
                "complete":["core","images","season:1"],"detail":{"id":1399,"season_number":1,
                  "name":"Example Show","overview":"Overview","poster_path":"/poster.jpg",
                  "seasons":[{"season_number":1,"episode_count":1,"episodes":[{"episode_number":1,"name":"Pilot"}]}]}}}]}
            """;

    @Test
    public void type4DetailJsonKeepsTmdbContract() {
        Result result = GSON.fromJson(JSON, Result.class);
        TmdbSourcePayload payload = result.getVod().getTmdb();

        assertNotNull(payload);
        assertEquals(1399, payload.getTmdbId());
        assertEquals("tv", payload.getMediaType());
        assertEquals(1, payload.getSeasonNumber());
        assertEquals("Example Show", payload.getDetail().name());
        assertTrue(payload.hasCapability("season:1"));
        assertEquals(1, payload.getDetail().seasons().size());
    }

    @Test
    public void type4DetailJsonKeepsTmdbAcrossDetailCacheRoundTrip() {
        Result parsed = GSON.fromJson(JSON, Result.class);
        String source = "t4-cache-" + System.nanoTime();
        String id = "t4-tv-1";

        VodDetailCache.putContent(source, id, GSON.toJson(parsed));
        Result restored = GSON.fromJson(VodDetailCache.getContent(source, id), Result.class);

        assertNotNull(restored.getVod().getTmdb());
        assertEquals(1399, restored.getVod().getTmdb().getTmdbId());
        assertEquals(1, restored.getVod().getTmdb().getDetail().seasons().size());
    }
}
