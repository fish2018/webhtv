package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.fongmi.android.tv.utils.VodDetailCache;
import com.google.gson.Gson;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class TmdbSourceCredentialContractTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final Gson GSON = new Gson();
    private static final String TMDB = """
            "tmdb":{"schema":1,"id":1399,"media_type":"tv","season_number":1,"complete":["core","credits","images"],
              "detail":{"id":1399,"name":"Example","overview":"Overview","first_air_date":"2024-01-01","vote_average":8,
                "number_of_seasons":1,"number_of_episodes":1,"genres":[],"credits":{"cast":[{"id":1,"name":"Actor"}]},
                "images":{"posters":[{"file_path":"/p.jpg"}],"backdrops":[{"file_path":"/b.jpg"}]}}}
            """;

    @Test
    public void legacyDetailRootCredentialIsStrippedForType3AndType4() {
        Result t3 = parse("{\"tmdb_api_key\":\"" + KEY + "\",\"list\":[{\"vod_id\":\"t3\",\"vod_name\":\"T3\"," + TMDB + "}]}");
        Result t4 = parse("{\"code\":0,\"msg\":\"ok\",\"tmdb_api_key\":\"" + KEY + "\",\"list\":[{\"vod_id\":\"t4\",\"vod_name\":\"T4\"," + TMDB + "}]}");

        assertEquals(t3.getVod().getTmdb().getTmdbId(), t4.getVod().getTmdb().getTmdbId());
        assertEquals(t3.getVod().getTmdb().getMediaType(), t4.getVod().getTmdb().getMediaType());
        assertEquals(t3.getVod().getTmdb().getDetailJson(), t4.getVod().getTmdb().getDetailJson());
        assertFalse(GSON.toJson(t3).contains(KEY));
        assertFalse(GSON.toJson(t4).contains(KEY));
    }

    @Test
    public void sanitizedT3AndT4PayloadsAreSafeForDetailCache() {
        String t3Raw = "{\"tmdb_api_key\":\"" + KEY + "\",\"list\":[{\"vod_id\":\"t3-cache\",\"vod_name\":\"T3\"," + TMDB + "}]}";
        String t4Raw = "{\"code\":0,\"tmdb_api_key\":\"" + KEY + "\",\"list\":[{\"vod_id\":\"t4-cache\",\"vod_name\":\"T4\"," + TMDB + "}]}";

        cacheRoundTrip("c16-t3-" + System.nanoTime(), "t3-cache", t3Raw);
        cacheRoundTrip("c16-t4-" + System.nanoTime(), "t4-cache", t4Raw);
    }

    @Test
    public void credentialFieldIsAbsentFromPersistedDetailModels() throws Exception {
        for (String path : new String[]{
                "src/main/java/com/fongmi/android/tv/bean/Vod.java",
                "src/main/java/com/fongmi/android/tv/bean/Result.java",
                "src/main/java/com/fongmi/android/tv/bean/TmdbSourcePayload.java",
                "src/main/java/com/fongmi/android/tv/utils/VodDetailCache.java"
        }) {
            String source = Files.readString(Path.of(path));
            assertFalse(path, source.contains(TmdbSourceCredentialIngress.ROOT_FIELD));
        }
    }

    private static Result parse(String raw) {
        TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(raw);
        // The ingress API still reports a candidate so callers can sanitize it. SiteApi must not accept this candidate.
        assertEquals(KEY, ingress.getCandidateKey());
        assertFalse(ingress.getSanitizedJson().contains(KEY));
        Result result = GSON.fromJson(ingress.getSanitizedJson(), Result.class);
        assertNotNull(result.getVod().getTmdb());
        return result;
    }

    private static void cacheRoundTrip(String sourceKey, String id, String raw) {
        TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(raw);
        Result result = GSON.fromJson(ingress.getSanitizedJson(), Result.class);
        VodDetailCache.putContent(sourceKey, id, GSON.toJson(result));

        String cached = VodDetailCache.getContent(sourceKey, id);
        assertNotNull(cached);
        assertFalse(cached.contains(KEY));
        TmdbSourcePayload payload = GSON.fromJson(cached, Result.class).getVod().getTmdb();
        assertNotNull(payload);
        assertEquals(1399, payload.getTmdbId());
    }
}
