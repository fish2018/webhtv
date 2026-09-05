package com.fongmi.android.tv.subtitle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RealtimeSubtitleTimelineTest {

    @Test
    public void futureCueKeepsItsSpeechWindow() {
        RealtimeSubtitleTimeline.CueWindow window = RealtimeSubtitleTimeline.planCueWindow(
                10_000_000L, 12_000_000L, 13_000_000L, 7_000_000L);

        assertEquals(10_000_000L, window.startUs());
        assertEquals(12_500_000L, window.endUs());
    }

    @Test
    public void lateCueIsDroppedInsteadOfFollowingSpeech() {
        assertNull(RealtimeSubtitleTimeline.planCueWindow(
                10_000_000L, 12_000_000L, 20_000_000L, 7_000_000L));
    }

    @Test
    public void shortTailIsDroppedInsteadOfFlashing() {
        assertNull(RealtimeSubtitleTimeline.planCueWindow(
                10_000_000L, 14_000_000L, 18_250_000L, 4_750_000L));
    }

    @Test
    public void audioPresentationPositionStopsWhilePaused() {
        assertEquals(6_000_000L, RealtimeSubtitleTimeline.presentedPositionUs(
                13_000_000L, 7_000_000L));
        assertEquals(-6_000_000L, RealtimeSubtitleTimeline.presentedPositionUs(
                4_000_000L, 10_000_000L));
    }
}
