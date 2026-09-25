package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubtitleContextBuilderTest {

    @Test
    public void buildWithoutTmdb_usesSourceMetadataFallbackForMovie() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("movie-1")
                .vodName("想见你 (2019) 1080P")
                .vodYear("2019")
                .allowTmdbLookup(false)
                .build();

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertEquals("movie", context.getMediaType());
        assertEquals("想见你", context.getCanonicalTitle());
        assertEquals(2019, context.getYear());
        assertFalse(context.hasTmdbIdentity());
    }

    @Test
    public void buildWithoutTmdb_usesEpisodeMetadataFallbackForTv() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("tv-1")
                .vodName("最后生还者 第二季")
                .episodeName("S02E03 漫长漫长时光")
                .allowTmdbLookup(false)
                .build();

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertEquals("tv", context.getMediaType());
        assertEquals("最后生还者", context.getCanonicalTitle());
        assertEquals(2, context.getSeasonNumber());
        assertEquals(3, context.getEpisodeNumber());
    }

    @Test
    public void build_carriesPreferredLanguageThroughToContext() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("movie-1")
                .vodName("想见你")
                .preferredLanguage("en")
                .allowTmdbLookup(false)
                .build();

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertEquals("en", context.getPreferredLanguage());
    }

    @Test
    public void build_extractsLocalMediaPathFromFileUrl() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("movie-1")
                .vodName("想见你")
                .playUrl("file:///sdcard/Movies/movie.mkv")
                .allowTmdbLookup(false)
                .build();

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertEquals("/sdcard/Movies/movie.mkv", context.getMediaPath());
        assertFalse(context.isNetworkStream());
    }

    @Test
    public void build_leavesNetworkUrlOutOfMediaPath() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("movie-1")
                .vodName("想见你")
                .playUrl("https://example.com/movie.mkv")
                .allowTmdbLookup(false)
                .build();

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertTrue(context.getMediaPath().isEmpty());
        assertTrue(context.isNetworkStream());
    }
}
