package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.ui.helper.TmdbSourcePayloadParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TmdbSourcePayloadTest {

    private static final Gson GSON = new Gson();

    @Test
    public void gsonRoundTripKeepsDetailAsProtocolObject() {
        Vod vod = GSON.fromJson("""
                {"vod_id":"movie-1","vod_name":"Example","tmdb":{
                  "schema":1,"id":550,"media_type":"movie","season_number":0,"language":"zh-CN",
                  "complete":["core","images"],"detail":{"id":550,"title":"Fight Club","poster_path":"/poster.jpg"}
                }}
                """, Vod.class);

        assertNotNull(vod.getTmdb());
        assertEquals(550, vod.getTmdb().getTmdbId());
        assertEquals("movie", vod.getTmdb().getMediaType());
        assertEquals(550, vod.getTmdb().getDetail().id());
        assertEquals("Fight Club", vod.getTmdb().getDetail().title());

        String encoded = GSON.toJson(vod);
        assertTrue(encoded.contains("\"detail\":{"));
        assertFalse(encoded.contains("detailJson"));
        assertFalse(encoded.contains("sourceKind"));
    }

    @Test
    public void parserNormalizesIdentityCapabilitiesAndImages() {
        TmdbSourcePayload raw = GSON.fromJson("""
                {"schema":1,"id":1399,"media_type":"TV","season_number":2,"language":" zh-CN ",
                 "fetched_at":"2026-09-19T00:00:00Z","complete":["images","season:02","unknown","images"],
                 "detail":{"id":1399,"season_number":2,"name":"Example","poster_path":"/p.jpg",
                   "backdrop_path":"http://example.test/b.jpg","images":{"posters":[]},
                   "seasons":[{"season_number":2,"episodes":[]}]}}
                """, TmdbSourcePayload.class);

        TmdbSourcePayload parsed = TmdbSourcePayloadParser.parse(raw);

        assertNotNull(parsed);
        assertEquals("tv", parsed.getMediaType());
        assertEquals("zh-CN", parsed.getLanguage());
        assertEquals(Set.of("images", "season:2"), parsed.getComplete());
        assertEquals("/p.jpg", parsed.getDetail().posterPath());
        assertEquals("", parsed.getDetail().backdropPath());
        assertTrue(parsed.hasCapability("season:2"));
        assertFalse(parsed.hasCapability("unknown"));
    }

    @Test
    public void parserRejectsInvalidIdentityAndConflictingDetail() {
        assertNull(TmdbSourcePayloadParser.parse(payload(0, "movie", 0, "{\"id\":550}")));
        assertNull(TmdbSourcePayloadParser.parse(payload(550, "music", 0, "{\"id\":550}")));
        assertNull(TmdbSourcePayloadParser.parse(payload(550, "movie", 1, "{\"id\":550}")));
        assertNull(TmdbSourcePayloadParser.parse(payload(550, "movie", 0, "{\"id\":551}")));
        assertNull(TmdbSourcePayloadParser.parse(payload(1399, "tv", 2, "{\"id\":1399,\"season_number\":1}")));
    }

    @Test
    public void parserDropsBadTypesInvalidImagesAndOversizedCollections() {
        JsonObject detail = new JsonObject();
        detail.addProperty("id", 550);
        detail.addProperty("overview", 42);
        detail.addProperty("poster_path", "poster.jpg");
        detail.addProperty("backdrop_path", "https://image.test/b.jpg");
        detail.addProperty("images", "not-an-object");
        JsonArray cast = new JsonArray();
        for (int i = 0; i <= TmdbSourcePayloadParser.MAX_ARRAY_ITEMS; i++) cast.add("actor-" + i);
        JsonObject credits = new JsonObject();
        credits.add("cast", cast);
        detail.add("credits", credits);
        detail.addProperty("tagline", "x".repeat(TmdbSourcePayloadParser.MAX_STRING_BYTES + 1));

        TmdbSourcePayload parsed = TmdbSourcePayloadParser.parse(payload(550, "movie", 0, detail.toString()));

        assertNotNull(parsed);
        TmdbSourceDetail result = parsed.getDetail();
        assertEquals("", result.overview());
        assertEquals("", result.posterPath());
        assertEquals("https://image.test/b.jpg", result.backdropPath());
        assertNull(result.images());
        assertNull(result.credits());
        assertEquals("", result.tagline());
    }

    @Test
    public void copyAndEqualityIgnoreInternalSourceKind() {
        TmdbSourcePayload payload = payload(550, "movie", 0, "{\"id\":550}");
        payload.setSourceKind(TmdbSourcePayload.SOURCE_TMDB);
        TmdbSourcePayload copy = payload.copy();
        copy.setSourceKind(TmdbSourcePayload.CACHE_TMDB);

        assertEquals(payload, copy);
        assertEquals(payload.hashCode(), copy.hashCode());
        assertFalse(payload.getComplete() == copy.getComplete());
    }

    private static TmdbSourcePayload payload(int id, String mediaType, int seasonNumber, String detailJson) {
        TmdbSourcePayload payload = new TmdbSourcePayload();
        payload.setSchema(TmdbSourcePayload.SCHEMA_VERSION);
        payload.setTmdbId(id);
        payload.setMediaType(mediaType);
        payload.setSeasonNumber(seasonNumber);
        payload.setDetailJson(detailJson);
        return payload;
    }
}
