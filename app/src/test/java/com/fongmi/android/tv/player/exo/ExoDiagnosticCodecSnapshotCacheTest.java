package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.PlaybackDiagnosticCollector.Context;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ExoDiagnosticCodecSnapshotCacheTest {
    private static final Context MEDIA = new Context("test", 1, 1, "media-a", "foreground");
    private static final ExoDiagnosticCodecSnapshotCache.Query QUERY = query("video/dolby-vision", false, false, "selector");
    private static final ExoDiagnosticCodecSnapshotCache.Candidate DV = candidate("dv5", 32, 256);

    @Test public void repeatedQueriesReferenceTheOriginalSnapshotAndKeepCounts() {
        ExoDiagnosticCodecSnapshotCache cache = new ExoDiagnosticCodecSnapshotCache();
        var first = cache.observe(MEDIA, 1, QUERY, List.of(DV));
        assertTrue(first.fullSnapshot());
        for (int i = 2; i <= 100; i++) {
            var next = cache.observe(MEDIA, 1, QUERY, List.of(candidate("dv5", 32, 256)));
            assertFalse(next.fullSnapshot());
            assertEquals(first.snapshotId(), next.snapshotId());
            assertEquals(i, next.queryCount());
        }
    }

    @Test public void orderCapabilitiesAndAttributesArePartOfTheSnapshot() {
        ExoDiagnosticCodecSnapshotCache cache = new ExoDiagnosticCodecSnapshotCache();
        var hevc = candidate("hevc", 2, 256);
        cache.observe(MEDIA, 1, QUERY, List.of(DV, hevc));
        assertTrue(cache.observe(MEDIA, 1, QUERY, List.of(hevc, DV)).fullSnapshot());
        assertTrue(cache.observe(MEDIA, 1, QUERY, List.of(hevc, candidate("dv5", 32, 512))).fullSnapshot());
        assertTrue(cache.observe(MEDIA, 1, QUERY, List.of(hevc,
                new ExoDiagnosticCodecSnapshotCache.Candidate("dv5", false, true, false,
                        1, List.of(new ExoDiagnosticCodecSnapshotCache.Profile(32, 512)), true))).fullSnapshot());
    }

    @Test public void queriesAreSeparatedByMimeSecurityTunnelingAndPolicy() {
        ExoDiagnosticCodecSnapshotCache cache = new ExoDiagnosticCodecSnapshotCache();
        cache.observe(MEDIA, 1, QUERY, List.of(DV));
        for (var key : List.of(query("video/hevc", false, false, "selector"),
                query("video/dolby-vision", true, false, "selector"),
                query("video/dolby-vision", false, true, "selector"),
                query("video/dolby-vision", false, false, "renderer-final-order"))) {
            assertTrue(cache.observe(MEDIA, 1, key, List.of(DV)).fullSnapshot());
        }
        assertFalse(cache.observe(MEDIA, 1, QUERY, List.of(DV)).fullSnapshot());
    }

    @Test public void clearCaptureRestartAndReprepareRequireCompleteSnapshots() {
        ExoDiagnosticCodecSnapshotCache cache = new ExoDiagnosticCodecSnapshotCache();
        long original = cache.observe(MEDIA, 1, QUERY, List.of(DV)).snapshotId();
        assertTrue(cache.observe(MEDIA, 2, QUERY, List.of(DV)).fullSnapshot());
        Context nextMedia = new Context("test", 1, 2, "media-b", "foreground");
        assertTrue(cache.observe(nextMedia, 2, QUERY, List.of(DV)).fullSnapshot());
        cache.clear();
        var restarted = cache.observe(nextMedia, 2, QUERY, List.of(DV));
        assertTrue(restarted.fullSnapshot());
        assertNotEquals(original, restarted.snapshotId());
    }

    @Test public void failedReadsAreNeverReusedAndEmptyResultsCanChange() {
        ExoDiagnosticCodecSnapshotCache cache = new ExoDiagnosticCodecSnapshotCache();
        var failed = new ExoDiagnosticCodecSnapshotCache.Candidate("dv5", true, false, true, 0, List.of(), false);
        cache.observe(MEDIA, 1, QUERY, List.of(failed));
        assertTrue(cache.observe(MEDIA, 1, QUERY, List.of(failed)).fullSnapshot());
        assertTrue(cache.observe(MEDIA, 1, QUERY, List.of()).fullSnapshot());
        assertFalse(cache.observe(MEDIA, 1, QUERY, List.of()).fullSnapshot());
        assertTrue(cache.observe(MEDIA, 1, QUERY, List.of(DV)).fullSnapshot());
    }

    @Test public void cacheIsBoundedAndEvictionProducesACompleteSnapshot() {
        ExoDiagnosticCodecSnapshotCache cache = new ExoDiagnosticCodecSnapshotCache();
        cache.observe(MEDIA, 1, QUERY, List.of(DV));
        for (int i = 0; i < ExoDiagnosticCodecSnapshotCache.MAX_QUERIES; i++) {
            cache.observe(MEDIA, 1, query("video/test-" + i, false, false, "selector"), List.of(DV));
        }
        assertTrue(cache.observe(MEDIA, 1, QUERY, List.of(DV)).fullSnapshot());
    }

    @Test public void mutableInputCannotChangeAnAlreadyRecordedSnapshot() {
        ExoDiagnosticCodecSnapshotCache cache = new ExoDiagnosticCodecSnapshotCache();
        List<ExoDiagnosticCodecSnapshotCache.Candidate> candidates = new ArrayList<>(List.of(DV));
        cache.observe(MEDIA, 1, QUERY, candidates);
        candidates.clear();
        assertFalse(cache.observe(MEDIA, 1, QUERY, List.of(DV)).fullSnapshot());
    }

    @Test public void concurrentQueriesKeepOneSnapshotAndAnExactTotal() throws Exception {
        ExoDiagnosticCodecSnapshotCache cache = new ExoDiagnosticCodecSnapshotCache();
        long id = cache.observe(MEDIA, 1, QUERY, List.of(DV)).snapshotId();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) calls.add(() -> {
                for (int i = 0; i < 100; i++) {
                    var result = cache.observe(MEDIA, 1, QUERY, List.of(DV));
                    if (result.fullSnapshot() || result.snapshotId() != id) return false;
                }
                return true;
            });
            for (Future<Boolean> result : executor.invokeAll(calls)) assertTrue(result.get());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(402, cache.observe(MEDIA, 1, QUERY, List.of(DV)).queryCount());
    }

    private static ExoDiagnosticCodecSnapshotCache.Candidate candidate(String name, int profile, int level) {
        return new ExoDiagnosticCodecSnapshotCache.Candidate(name, true, false, true, 1,
                List.of(new ExoDiagnosticCodecSnapshotCache.Profile(profile, level)), true);
    }

    private static ExoDiagnosticCodecSnapshotCache.Query query(String mime, boolean secure, boolean tunneling, String policy) {
        return new ExoDiagnosticCodecSnapshotCache.Query(mime, secure, tunneling, policy);
    }
}
