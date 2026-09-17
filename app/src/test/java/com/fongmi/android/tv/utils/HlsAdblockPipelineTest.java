package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HlsAdblockPipelineTest {

    @Test
    public void recognizesOnlyLoopbackCoreM3u8Proxy() {
        assertTrue(HlsAdblockPipeline.isCoreM3u8Proxy("http://127.0.0.1:9978/m3u8?url=x"));
        assertTrue(HlsAdblockPipeline.isCoreM3u8Proxy("http://localhost:9978/m3u8?url=x"));
        assertFalse(HlsAdblockPipeline.isCoreM3u8Proxy("https://example.com/m3u8?url=x"));
        assertFalse(HlsAdblockPipeline.isCoreM3u8Proxy("http://127.0.0.1:9978/mpv/playlist?id=1"));
    }

    @Test
    public void structuredMatchFinishesBeforeLegacyFallback() {
        String manifest = "#EXTM3U\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad.ts\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                "https://video.example.com/index.m3u8", manifest, List.of(rule), true);

        assertTrue(outcome.structured());
        assertFalse(outcome.manifest().contains("ad.ts"));
    }

    @Test
    public void legacyFallbackReportsRemovedSegmentDomainAndTimeline() {
        String original = "#EXTM3U\n"
                + "#EXTINF:6.0,\nhttps://ads.example.com/ad-1.ts\n"
                + "#EXTINF:6.0,\nhttps://ads.example.com/ad-2.ts\n"
                + "#EXTINF:8.0,\nhttps://video.example.com/main-1.ts\n"
                + "#EXTINF:8.0,\nhttps://video.example.com/main-2.ts\n"
                + "#EXTINF:8.0,\nhttps://video.example.com/main-3.ts\n"
                + "#EXT-X-ENDLIST\n";
        String filtered = "#EXTM3U\n"
                + "#EXTINF:8.0,\nhttps://video.example.com/main-1.ts\n"
                + "#EXTINF:8.0,\nhttps://video.example.com/main-2.ts\n"
                + "#EXTINF:8.0,\nhttps://video.example.com/main-3.ts\n"
                + "#EXT-X-ENDLIST\n";

        List<HlsManifestCleaner.RemovedSegment> removed = HlsAdblockPipeline.legacyRemovedSegments(
                "https://video.example.com/index.m3u8", original, filtered);

        assertEquals(2, removed.size());
        assertEquals("ads.example.com", removed.get(0).adDomain());
        assertEquals("hls.legacy-fallback", removed.get(0).ruleId());
        assertEquals(0.0, removed.get(0).startSeconds(), 0.001);
        assertEquals(6.0, removed.get(1).startSeconds(), 0.001);
        assertEquals(12.0, removed.stream().mapToDouble(HlsManifestCleaner.RemovedSegment::durationSec).sum(), 0.001);
    }

    @Test
    public void exposesStructuredRuleCounts() {
        String manifest = "#EXTM3U\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad.ts\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXTINF:8.0,\nmain-3.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .id("rule-one")
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                "https://video.example.com/index.m3u8", manifest, List.of(rule), true);

        assertTrue(outcome.structured());
        assertEquals(Map.of("rule-one", 1L), outcome.ruleCounts());
    }

    @Test
    public void keepsStructuredSegmentDetailsWhenLegacyFallbackIsDisabled() {
        String manifest = "#EXTM3U\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad.ts\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXTINF:8.0,\nmain-3.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .id("rule-one")
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                "https://video.example.com/index.m3u8", manifest, List.of(rule), false);

        assertTrue(outcome.structured());
        assertEquals(1, outcome.removedSegmentDetails().size());
        HlsManifestCleaner.RemovedSegment removed = outcome.removedSegmentDetails().get(0);
        assertEquals("ads.example.com", removed.adDomain());
        assertEquals("rule-one", removed.ruleId());
        assertEquals(0.0, removed.startSeconds(), 0.001);
        assertEquals(7.0, removed.durationSec(), 0.001);
    }

    @Test
    public void disablesLegacyHeuristicsWithoutRules() {
        String manifest = "#EXTM3U\n"
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXTINF:4.0,\nmain-1.ts\n"
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXTINF:4.0,\nmain-2.ts\n"
                + "#EXT-X-ENDLIST\n";

        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                "https://cdn.example.com/index.m3u8", manifest, List.of(), false);

        assertFalse(outcome.structured());
        assertFalse(outcome.legacy());
        assertEquals(manifest, outcome.manifest());
    }
}
