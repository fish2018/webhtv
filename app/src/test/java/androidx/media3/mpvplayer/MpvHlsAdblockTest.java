package androidx.media3.mpvplayer;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class MpvHlsAdblockTest {

    private static final HlsPlaylistRewriter.Variant LOW =
            new HlsPlaylistRewriter.Variant(1_000_000, 900_000, 640, 360,
                    HlsPlaylistRewriter.VariantKind.STREAM);
    private static final HlsPlaylistRewriter.Variant HIGH =
            new HlsPlaylistRewriter.Variant(4_000_000, 3_500_000, 1920, 1080,
                    HlsPlaylistRewriter.VariantKind.STREAM);

    @Test
    public void directMediaPlaylistDoesNotNeedNativeVariantMetadata() {
        HlsAdTimeline direct = HlsAdTimelineTest.middleAd("2");
        assertSame(direct, MpvHlsProxy.resolveAdTimeline(direct, Map.of(), 0, 0));
    }

    @Test
    public void selectedPeakOrAverageBitrateChoosesItsOwnPlan() {
        HlsAdTimeline low = HlsAdTimelineTest.middleAd("2");
        HlsAdTimeline high = HlsAdTimelineTest.middleAd("4");
        Map<HlsPlaylistRewriter.Variant, HlsAdTimeline> plans = Map.of(LOW, low, HIGH, high);
        assertSame(low, MpvHlsProxy.resolveAdTimeline(null, plans, 1_000_000, 2));
        assertSame(high, MpvHlsProxy.resolveAdTimeline(null, plans, 3_500_000, 2));
        assertTrue(MpvHlsProxy.resolveAdTimeline(null, plans, 2_000_000, 2).ranges().isEmpty());
    }

    @Test
    public void unknownSelectionRequiresEveryDeclaredVariantToAgree() {
        HlsAdTimeline plan = HlsAdTimelineTest.middleAd("2");
        assertTrue(MpvHlsProxy.resolveAdTimeline(null, Map.of(LOW, plan), 0, 2)
                .ranges().isEmpty());
        assertSame(plan, MpvHlsProxy.resolveAdTimeline(null,
                Map.of(LOW, plan, HIGH, plan), 0, 2));
        assertTrue(MpvHlsProxy.resolveAdTimeline(null,
                Map.of(LOW, plan, HIGH, HlsAdTimeline.NONE), 0, 2).ranges().isEmpty());
    }

    @Test
    public void matchingBitratesWithConflictingPlansNeverGuess() {
        HlsPlaylistRewriter.Variant alternate =
                new HlsPlaylistRewriter.Variant(1_000_000, 900_000, 960, 540,
                        HlsPlaylistRewriter.VariantKind.STREAM);
        assertTrue(MpvHlsProxy.resolveAdTimeline(null,
                Map.of(LOW, HlsAdTimelineTest.middleAd("2"),
                        alternate, HlsAdTimelineTest.middleAd("4")),
                1_000_000, 2).ranges().isEmpty());
    }

    @Test
    public void imageOrIframePlaylistCannotSupplyTheVideoAdPlan() {
        HlsPlaylistRewriter.Variant iframe =
                new HlsPlaylistRewriter.Variant(1_000_000, 900_000, 640, 360,
                        HlsPlaylistRewriter.VariantKind.I_FRAME);
        assertTrue(MpvHlsProxy.resolveAdTimeline(null,
                Map.of(iframe, HlsAdTimelineTest.middleAd("2")), 1_000_000, 1)
                .ranges().isEmpty());
    }

    @Test
    public void unequalProgrammeBlocksDoNotBecomeUnseekable() {
        // Same discontinuity structure and exact block durations as the reported
        // mixed.m3u8; opaque local names keep this regression fully offline.
        StringBuilder source = new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:8\n");
        int next = appendBlock(source, 0, 124, "4", "4.32");
        next = appendAd(source, next, "6.633333", "3.333333", "4.8", "1.7");
        int retainedStart = source.length();
        next = appendBlock(source, next, 375, "4", "4.72");
        int retainedEnd = source.length();
        next = appendAd(source, next, "5.933333", "3.333333", "2.8", "5.3", "0.3");
        next = appendBlock(source, next, 200, "4", "4.24");
        int finalBlockStart = source.length();
        appendAd(source, next, "6.633333", "3.333333", "4.8", "1.7");
        source.append("#EXT-X-ENDLIST\n");
        String original = source.toString();
        // Freeze the detector's observed omissions: it keeps only the largest
        // programme block and the final block. The mapping must defend against
        // that output, without requiring Android TextUtils/Log in this JVM test.
        String filtered = "#EXTM3U\n" + source.substring(retainedStart, retainedEnd)
                + source.substring(finalBlockStart);
        HlsAdTimeline timeline = HlsAdTimeline.from(original, filtered);
        assertEquals(List.of(new HlsAdTimeline.Range(496320, 512786),
                new HlsAdTimeline.Range(2013507, 2031173)), timeline.ranges());
        for (long position : new long[]{0, 9000, 490000, 600000, 2300000}) {
            assertEquals(position, timeline.skipTargetMs(position));
        }
        assertEquals(512786, timeline.skipTargetMs(500000));
        assertEquals(2031173, timeline.skipTargetMs(2020000));
    }

    private static int appendBlock(StringBuilder out, int start, int count,
            String duration, String firstDuration) {
        out.append("#EXT-X-DISCONTINUITY\n");
        for (int i = 0; i < count; i++) {
            out.append(HlsAdTimelineTest.segment("segment" + (start + i) + ".ts",
                    i == 0 ? firstDuration : duration));
        }
        return start + count;
    }

    private static int appendAd(StringBuilder out, int start, String... durations) {
        out.append("#EXT-X-DISCONTINUITY\n");
        for (String duration : durations) {
            out.append(HlsAdTimelineTest.segment("segment" + start++ + ".ts", duration));
        }
        return start;
    }
}
