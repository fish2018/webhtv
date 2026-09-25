package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.utils.VodDetailCache;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VodTmdbParcelableTest {

    @Test
    public void vodParcelSourceTracksTmdbAndContentComparison() throws Exception {
        String source = read(findAppPath().resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "bean", "Vod.java")));

        assertTrue(source.contains("this.tmdb = in.readParcelable(TmdbSourcePayload.class.getClassLoader());"));
        assertTrue(source.contains("dest.writeParcelable(this.tmdb, flags);"));
        assertTrue(source.contains("Objects.equals(getTmdb(), other.getTmdb())"));
    }

    @Test
    public void oversizedPayloadUsesCacheKeyInsteadOfBinder() {
        TmdbSourcePayload small = payload("{\"id\":550,\"title\":\"Small\"}");
        TmdbSourcePayload large = payload("{\"id\":550,\"overview\":\"" + "x".repeat(TmdbSourcePayload.MAX_PARCEL_BYTES) + "\"}");

        assertFalse(small.requiresParcelCache());
        assertTrue(large.requiresParcelCache());

        String key = large.putParcelCacheCopy();
        Vod cached = VodDetailCache.take(key);

        assertNotNull(cached);
        assertNotNull(cached.getTmdb());
        assertEquals(550, cached.getTmdb().getTmdbId());
        assertTrue(cached.getTmdb().requiresParcelCache());
    }

    private static TmdbSourcePayload payload(String detailJson) {
        TmdbSourcePayload payload = new TmdbSourcePayload();
        payload.setSchema(TmdbSourcePayload.SCHEMA_VERSION);
        payload.setTmdbId(550);
        payload.setMediaType("movie");
        payload.setDetailJson(detailJson);
        return payload;
    }

    private static Path findAppPath() {
        return Files.exists(Path.of("src", "main")) ? Path.of(".") : Path.of("app");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
