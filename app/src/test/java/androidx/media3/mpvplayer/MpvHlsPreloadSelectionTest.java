package androidx.media3.mpvplayer;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MpvHlsPreloadSelectionTest {

    @Test
    public void automaticPreloadUsesOnlyTheActualSelectedVariant() {
        List<HlsPlaylistRewriter.Segment> low = List.of(segment("low"));
        List<HlsPlaylistRewriter.Segment> high = List.of(segment("high"));

        List<HlsPlaylistRewriter.Segment> selected =
                MpvHlsProxy.resolveAutomaticPreloadSegments(
                        Map.of(5_000_000L, low, 15_000_000L, high),
                        List.of(),
                        15_000_000L);

        assertEquals(1, selected.size());
        assertEquals("high", selected.get(0).uri());
    }

    @Test
    public void unknownMultiVariantDoesNotFallThroughToAnotherParsedLadderEntry() {
        List<HlsPlaylistRewriter.Segment> selected =
                MpvHlsProxy.resolveAutomaticPreloadSegments(
                        Map.of(5_000_000L, List.of(segment("low"))),
                        List.of(segment("direct")),
                        15_000_000L);
        List<HlsPlaylistRewriter.Segment> unknown =
                MpvHlsProxy.resolveAutomaticPreloadSegments(
                        Map.of(5_000_000L, List.of(segment("low"))),
                        List.of(segment("direct")),
                        0);

        assertTrue(selected.isEmpty());
        assertEquals(1, unknown.size());
        assertEquals("low", unknown.get(0).uri());
    }

    @Test
    public void singleParsedVariantCanBootstrapBeforeNativeBitrateIsKnown() {
        List<HlsPlaylistRewriter.Segment> only = List.of(segment("only"));
        List<HlsPlaylistRewriter.Segment> selected =
                MpvHlsProxy.resolveAutomaticPreloadSegments(
                        Map.of(8_000_000L, only), List.of(), 0);

        assertEquals(1, selected.size());
        assertEquals("only", selected.get(0).uri());
    }

    @Test
    public void directSingleVariantPlaylistKeepsItsSegmentList() {
        HlsPlaylistRewriter.Segment direct = segment("direct");
        List<HlsPlaylistRewriter.Segment> selected =
                MpvHlsProxy.resolveAutomaticPreloadSegments(
                        Map.of(), List.of(direct), 10_000_000L);

        assertEquals(1, selected.size());
        assertSame(direct, selected.get(0));
    }

    @Test
    public void preloadQueueBudgetCapsPendingSegments() {
        assertEquals(256, MpvHlsProxy.resolvePreloadSubmissionBudget(-1));
        assertEquals(256, MpvHlsProxy.resolvePreloadSubmissionBudget(0));
        assertEquals(1, MpvHlsProxy.resolvePreloadSubmissionBudget(255));
        assertEquals(0, MpvHlsProxy.resolvePreloadSubmissionBudget(256));
        assertEquals(0, MpvHlsProxy.resolvePreloadSubmissionBudget(1_000));
    }

    private static HlsPlaylistRewriter.Segment segment(String uri) {
        return new HlsPlaylistRewriter.Segment(uri, 6, 0, false);
    }
}
