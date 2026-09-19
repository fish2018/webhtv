package androidx.media3.mpvplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class HlsAdTimelineTest {

    static String playlist(String... entries) {
        return "#EXTM3U\n#EXT-X-TARGETDURATION:10\n"
                + String.join("", entries) + "#EXT-X-ENDLIST\n";
    }

    static String segment(String uri, String duration) {
        return "#EXTINF:" + duration + ",\n" + uri + "\n";
    }

    static HlsAdTimeline middleAd(String duration) {
        return HlsAdTimeline.from(
                playlist(segment("main/0.ts", "4"), segment("ad/0.ts", duration),
                        segment("main/1.ts", "6")),
                playlist(segment("main/0.ts", "4"), segment("main/1.ts", "6")));
    }

    @Test
    public void removedSegmentsUseSourceTimeAndAdjacentAdsMerge() {
        HlsAdTimeline timeline = HlsAdTimeline.from(
                playlist(segment("main/0.ts", "4"), segment("ads/0.ts", "1.2"),
                        segment("ads/1.ts", "2.3"), segment("main/1.ts", "6")),
                playlist(segment("main/0.ts", "4"), segment("main/1.ts", "6")));
        assertEquals(List.of(new HlsAdTimeline.Range(4000, 7500)), timeline.ranges());
        assertEquals(3999, timeline.skipTargetMs(3999));
        assertEquals(7500, timeline.skipTargetMs(4000));
        assertEquals(7500, timeline.skipTargetMs(7499));
        assertEquals(7500, timeline.skipTargetMs(7500));
        assertEquals(12000, timeline.skipTargetMs(12000));
    }

    @Test
    public void leadingAndTrailingAdsKeepDistinctSourceRanges() {
        HlsAdTimeline timeline = HlsAdTimeline.from(
                playlist(segment("ad/start.ts", "2"), segment("main.ts", "9"),
                        segment("ad/end.ts", "3")),
                playlist(segment("main.ts", "9")));
        assertEquals(List.of(new HlsAdTimeline.Range(0, 2000),
                new HlsAdTimeline.Range(11000, 14000)), timeline.ranges());
        assertEquals(2000, timeline.skipTargetMs(0));
        assertEquals(14000, timeline.skipTargetMs(13000));
    }

    @Test
    public void roundingDoesNotSkipProgrammeAtSubMillisecondBoundaries() {
        HlsAdTimeline timeline = HlsAdTimeline.from(
                playlist(segment("main/0", "1.000001"), segment("ad", "0.003998"),
                        segment("main/1", "1")),
                playlist(segment("main/0", "1.000001"), segment("main/1", "1")));
        assertEquals(List.of(new HlsAdTimeline.Range(1001, 1003)), timeline.ranges());
        assertEquals(1000, timeline.skipTargetMs(1000));
        assertEquals(1003, timeline.skipTargetMs(1001));
    }

    @Test
    public void rejectsAmbiguousDuplicateOccurrences() {
        HlsAdTimeline timeline = HlsAdTimeline.from(
                playlist(segment("same.ts", "2"), segment("same.ts", "2"),
                        segment("tail.ts", "5")),
                playlist(segment("same.ts", "2"), segment("tail.ts", "5")));
        assertTrue(timeline.ranges().isEmpty());
        assertEquals("ambiguous-segments", timeline.reason());
    }

    @Test
    public void repeatedRetainedSegmentsCanStillHaveAnUnambiguousMapping() {
        HlsAdTimeline timeline = HlsAdTimeline.from(
                playlist(segment("same.ts", "2"), segment("same.ts", "2"),
                        segment("ads.ts", "1"), segment("tail.ts", "5")),
                playlist(segment("same.ts", "2"), segment("same.ts", "2"),
                        segment("tail.ts", "5")));
        assertEquals(List.of(new HlsAdTimeline.Range(4000, 5000)), timeline.ranges());
    }

    @Test
    public void byteRangeIdentitySeparatesSegmentsUsingTheSameUrl() {
        String first = "#EXTINF:2,\n#EXT-X-BYTERANGE:100@0\nvideo.mp4\n";
        String ad = "#EXTINF:2,\n#EXT-X-BYTERANGE:100@100\nvideo.mp4\n";
        String last = "#EXTINF:2,\n#EXT-X-BYTERANGE:100@200\nvideo.mp4\n";
        HlsAdTimeline timeline = HlsAdTimeline.from(playlist(first, ad, last),
                playlist(first, last));
        assertEquals(List.of(new HlsAdTimeline.Range(2000, 4000)), timeline.ranges());
    }

    @Test
    public void keyRotationAndDiscontinuityMetadataDoNotChangeSourcePositions() {
        String first = segment("body/0.ts", "4");
        String last = segment("body/1.ts", "6");
        String key = "#EXT-X-MEDIA-SEQUENCE:37\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n";
        HlsAdTimeline timeline = HlsAdTimeline.from(
                playlist(key, first, "#EXT-X-DISCONTINUITY\n",
                        segment("ad/0.ts", "2"), "#EXT-X-KEY:METHOD=NONE\n", last),
                playlist(key, first, "#EXT-X-KEY:METHOD=NONE\n", last));
        assertEquals(List.of(new HlsAdTimeline.Range(4000, 6000)), timeline.ranges());
    }

    @Test
    public void longFalsePositiveBeforeAndAfterAnAdRemainsSeekable() {
        String tail = segment("retained.ts", "900");
        HlsAdTimeline timeline = HlsAdTimeline.from(
                playlist(segment("programme-before.ts", "496.32"),
                        "#EXT-X-DISCONTINUITY\n", segment("ad.ts", "16.466666"),
                        "#EXT-X-DISCONTINUITY\n", segment("programme-after.ts", "800"),
                        tail), playlist(tail));
        assertEquals(List.of(new HlsAdTimeline.Range(496320, 512786)), timeline.ranges());
        assertEquals(0, timeline.skipTargetMs(0));
        assertEquals(490000, timeline.skipTargetMs(490000));
        assertEquals(512786, timeline.skipTargetMs(500000));
        assertEquals(700000, timeline.skipTargetMs(700000));
    }

    @Test
    public void uninterruptedLongCandidateIsPreservedAndLimitIsInclusive() {
        String kept = segment("body.ts", "1000");
        assertTrue(HlsAdTimeline.from(
                playlist(segment("uncertain.ts", "120.000001"), kept),
                playlist(kept)).ranges().isEmpty());
        assertEquals(List.of(new HlsAdTimeline.Range(0, 120000)), HlsAdTimeline.from(
                playlist(segment("ad.ts", "120"), kept), playlist(kept)).ranges());
    }

    @Test
    public void acceptedAdjacentDiscontinuityBlocksStillMerge() {
        String kept = segment("body.ts", "1000");
        HlsAdTimeline timeline = HlsAdTimeline.from(
                playlist(segment("ad-1.ts", "10"), "#EXT-X-DISCONTINUITY\n",
                        segment("ad-2.ts", "15"), kept), playlist(kept));
        assertEquals(List.of(new HlsAdTimeline.Range(0, 25000)), timeline.ranges());
    }

    @Test
    public void rejectsLiveMalformedNonFiniteAndOverflowingDurations() {
        String filtered = playlist(segment("body.ts", "5"));
        for (String duration : List.of("NaN", "Infinity", "-1", "0", "1e100",
                "1e1000000000", "1e-1000000000")) {
            assertTrue(HlsAdTimeline.from(
                    playlist(segment("bad.ts", duration), segment("body.ts", "5")),
                    filtered).ranges().isEmpty());
        }
        String original = playlist(segment("ad.ts", "2"), segment("body.ts", "5"));
        assertTrue(HlsAdTimeline.from(original.replace("#EXT-X-ENDLIST", ""), filtered)
                .ranges().isEmpty());
        assertTrue(HlsAdTimeline.from(original, "#EXTM3U\n#EXT-X-ENDLIST\n")
                .ranges().isEmpty());
        assertTrue(HlsAdTimeline.from(original, null).ranges().isEmpty());
        assertTrue(HlsAdTimeline.from(null, filtered).ranges().isEmpty());
    }

    @Test
    public void rejectsReorderingOrChangingRetainedMedia() {
        String a = segment("a.ts", "3");
        String b = segment("b.ts", "4");
        String original = playlist(a, segment("ad.ts", "1"), b);
        assertTrue(HlsAdTimeline.from(original, playlist(b, a)).ranges().isEmpty());
        assertTrue(HlsAdTimeline.from(original, playlist(segment("a.ts", "2"), b))
                .ranges().isEmpty());
        assertTrue(HlsAdTimeline.from(original, original).ranges().isEmpty());
    }

    @Test
    public void stalePositionsCannotRepeatSeekAndManualOrMediaResetAllowsReplay() {
        HlsAdTimeline timeline = middleAd("2");
        HlsAdTimeline.SkipState state = new HlsAdTimeline.SkipState();
        assertEquals(-1, state.nextTargetMs(timeline, 3999));
        assertEquals(6000, state.nextTargetMs(timeline, 4000));
        assertEquals(-1, state.nextTargetMs(timeline, 4200));
        assertEquals(-1, state.nextTargetMs(middleAd("2"), 4500));
        assertEquals(-1, state.nextTargetMs(timeline, 6000));
        state.clear();
        assertEquals(6000, state.nextTargetMs(timeline, 5000));
        assertEquals(-1, state.nextTargetMs(HlsAdTimeline.NONE, 5000));
    }
}
