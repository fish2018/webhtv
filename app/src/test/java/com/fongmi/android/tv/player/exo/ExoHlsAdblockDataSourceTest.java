package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ExoHlsAdblockDataSourceTest {

    @Test
    public void identifiesOnlyHlsManifestRequests() {
        assertTrue(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/index.m3u8"));
        assertTrue(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/playlist.m3u8?token=1"));
        assertFalse(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/segment.ts"));
        assertFalse(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/key.bin"));
    }

    @Test
    public void identifiesExtensionlessHlsManifestFromResponseContentType() {
        assertTrue(ExoHlsAdblockDataSource.isManifestRequest(
                "https://example.test/playback/token",
                Map.of("Content-Type", List.of("application/vnd.apple.mpegurl; charset=utf-8"))));
        assertTrue(ExoHlsAdblockDataSource.isManifestRequest(
                "https://example.test/playback/token",
                Map.of("content-type", List.of("application/x-mpegURL"))));
        assertFalse(ExoHlsAdblockDataSource.isManifestRequest(
                "https://example.test/live/segment.ts",
                Map.of("Content-Type", List.of("video/mp2t"))));
    }

    @Test
    public void changedOutcomeProducesPlaybackNoticeText() {
        String manifest = "#EXTM3U\n#EXTINF:5.0,\nad/001.ts\n#EXTINF:8.0,\nvideo/002.ts\n#EXTINF:8.0,\nvideo/003.ts\n#EXTINF:8.0,\nvideo/004.ts\n#EXT-X-ENDLIST\n";
        ExoHlsAdblockDataSource.Result result = ExoHlsAdblockDataSource.cleanForTest(
                "https://example.test/live/index.m3u8", manifest);

        assertTrue(result.changed());
        assertTrue("Unexpected notice: [" + result.notice() + "]", result.notice().startsWith("已跳过 "));
    }
}
