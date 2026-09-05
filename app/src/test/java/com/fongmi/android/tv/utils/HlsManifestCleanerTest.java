package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HlsManifestCleanerTest {

    private static final String BASE_URL = "https://video.example.com/path/index.m3u8";

    @Test
    public void removesCompleteSegmentEntryWhenTwoSignalsMatch() {
        String manifest = "#EXTM3U\n"
                + "#EXT-X-TARGETDURATION:8\n"
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXTINF:7.166667,\n"
                + "https://ads.example.com/preroll/ad-1.ts\n"
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXTINF:8.0,\n"
                + "main-1.ts\n"
                + "#EXTINF:8.0,\n"
                + "main-2.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .segmentUrlPatterns(List.of("/preroll/"))
                .minimumSignals(2)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertTrue(result.changed());
        assertEquals(1, result.removedSegments());
        assertFalse(result.manifest().contains("ad-1.ts"));
        assertFalse(result.manifest().contains("#EXTINF:7.166667"));
        assertTrue(result.manifest().contains("main-1.ts"));
        assertTrue(result.manifest().startsWith("#EXTM3U"));
    }

    @Test
    public void leavesManifestUnchangedWhenRuleDoesNotMatch() {
        String manifest = "#EXTM3U\n#EXTINF:8.0,\nmain-1.ts\n#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertFalse(result.changed());
        assertEquals(manifest, result.manifest());
    }

    @Test
    public void fallsBackWhenRuleWouldRemoveTooMuchContent() {
        String manifest = "#EXTM3U\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad-1.ts\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad-2.ts\n"
                + "#EXTINF:8.0,\nmain.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertTrue(result.fallback());
        assertFalse(result.changed());
        assertEquals(manifest, result.manifest());
    }

    @Test
    public void doesNotFilterMasterPlaylist() {
        String manifest = "#EXTM3U\r\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=800000\r\n"
                + "low/index.m3u8\r\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .segmentUrlPatterns(List.of("index\\.m3u8"))
                .minimumSignals(1)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertFalse(result.changed());
        assertEquals(manifest, result.manifest());
    }

    @Test
    public void leavesIncompleteSegmentEntryUnchanged() {
        String manifest = "#EXTM3U\n#EXTINF:7.0,\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .segmentUrlPatterns(List.of(".*"))
                .minimumSignals(1)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertFalse(result.changed());
        assertEquals(manifest, result.manifest());
    }

    @Test
    public void requiresConfiguredNumberOfIndependentSignals() {
        String manifest = "#EXTM3U\n"
                + "#EXTINF:7.166667,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXTINF:8.0,\nmain-3.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .durationRange(7.0, 7.3)
                .minimumSignals(2)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertFalse(result.changed());
        assertEquals(manifest, result.manifest());
    }

    @Test
    public void removesCrossDomainDiscontinuityBlockAndReportsDuration() {
        String manifest = "#EXTM3U\n"
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXTINF:7.0,\nhttps://cdn.other.example/ad.ts\n"
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .requireDiscontinuity(true)
                .requireCrossDomain(true)
                .minimumSignals(2)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertTrue(result.changed());
        assertEquals(1, result.removedSegments());
        assertEquals(7.0, result.removedDurationSec(), 0.001);
        assertFalse(result.manifest().contains("ad.ts"));
        assertFalse(result.manifest().contains("#EXT-X-DISCONTINUITY\n#EXT-X-DISCONTINUITY"));
    }

    @Test
    public void removesLeadingLiveSegmentAndAdvancesSequences() {
        String manifest = "#EXTM3U\n"
                + "#EXT-X-MEDIA-SEQUENCE:100\n"
                + "#EXT-X-DISCONTINUITY-SEQUENCE:5\n"
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad.ts\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXTINF:8.0,\nmain-3.ts\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertTrue(result.changed());
        assertFalse(result.fallback());
        assertTrue(result.manifest().contains("#EXT-X-MEDIA-SEQUENCE:101"));
        assertTrue(result.manifest().contains("#EXT-X-DISCONTINUITY-SEQUENCE:6"));
        assertFalse(result.manifest().contains("ad.ts"));
    }

    @Test
    public void fallsBackInsteadOfDeletingMiddleLiveSegment() {
        String manifest = "#EXTM3U\n"
                + "#EXT-X-MEDIA-SEQUENCE:100\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXTINF:8.0,\nmain-3.ts\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertTrue(result.fallback());
        assertFalse(result.changed());
        assertEquals(manifest, result.manifest());
    }

    @Test
    public void doesNotTreatDiscontinuitySequenceAsBoundary() {
        String manifest = "#EXTM3U\n"
                + "#EXT-X-MEDIA-SEQUENCE:100\n"
                + "#EXT-X-DISCONTINUITY-SEQUENCE:5\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad.ts\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXTINF:8.0,\nmain-3.ts\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertTrue(result.changed());
        assertTrue(result.manifest().contains("#EXT-X-DISCONTINUITY-SEQUENCE:5"));
    }

    @Test
    public void removesBoundaryAcrossKeyTagWithoutDoubleCounting() {
        String manifest = "#EXTM3U\n"
                + "#EXT-X-MEDIA-SEQUENCE:100\n"
                + "#EXT-X-DISCONTINUITY-SEQUENCE:5\n"
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad.ts\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXTINF:8.0,\nmain-3.ts\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertTrue(result.changed());
        assertTrue(result.manifest().contains("#EXT-X-DISCONTINUITY-SEQUENCE:6"));
        assertFalse(result.manifest().contains("#EXT-X-DISCONTINUITY\n"));
        assertTrue(result.manifest().contains("#EXT-X-KEY:METHOD=AES-128"));
    }

    @Test
    public void fallsBackForByteRangePlaylist() {
        String manifest = "#EXTM3U\n"
                + "#EXT-X-BYTERANGE:1000@0\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/media.ts\n"
                + "#EXT-X-BYTERANGE:1000\n"
                + "#EXTINF:8.0,\nmedia.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertTrue(result.fallback());
        assertEquals(manifest, result.manifest());
    }

    @Test
    public void fallsBackWhenRemovedDurationExceedsSafetyLimit() {
        String manifest = "#EXTM3U\n"
                + "#EXTINF:91.0,\nhttps://ads.example.com/long.ts\n"
                + "#EXTINF:120.0,\nmain-1.ts\n"
                + "#EXTINF:120.0,\nmain-2.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(BASE_URL, manifest, List.of(rule));

        assertTrue(result.fallback());
        assertEquals(manifest, result.manifest());
    }
}
