package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.fongmi.android.tv.utils.VodDetailCache;
import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SiteApiT3TmdbDetailTest {

    private static final Gson GSON = new Gson();
    private static final String JSON = """
            {"list":[{"vod_id":"t3-movie-1","vod_name":"T3 Example","vod_play_from":"line-a",
              "vod_play_url":"正片$https://media.test/movie.m3u8",
              "tmdb":{"schema":1,"id":550,"media_type":"movie","season_number":0,"complete":["core","credits"],
                "detail":{"id":550,"title":"Fight Club","overview":"Overview","poster_path":"/poster.jpg"}}}]}
            """;

    @Test
    public void type3DetailJsonKeepsTmdbContract() {
        Result result = GSON.fromJson(JSON, Result.class);
        TmdbSourcePayload payload = result.getVod().getTmdb();

        assertNotNull(payload);
        assertEquals(550, payload.getTmdbId());
        assertEquals("movie", payload.getMediaType());
        assertEquals("Fight Club", payload.getDetail().title());
        assertTrue(payload.hasCapability("core"));
        assertTrue(payload.hasCapability("credits"));
    }

    @Test
    public void type3DetailJsonKeepsTmdbAcrossDetailCacheRoundTrip() {
        Result parsed = GSON.fromJson(JSON, Result.class);
        String source = "t3-cache-" + System.nanoTime();
        String id = "t3-movie-1";

        VodDetailCache.putContent(source, id, GSON.toJson(parsed));
        Result restored = GSON.fromJson(VodDetailCache.getContent(source, id), Result.class);

        assertNotNull(restored.getVod().getTmdb());
        assertEquals(550, restored.getVod().getTmdb().getTmdbId());
        assertEquals("Fight Club", restored.getVod().getTmdb().getDetail().title());
    }
}
