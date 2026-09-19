package androidx.media3.mpvplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MpvHlsAdBoundaryStateTest {
    private static final HlsAdTimeline.Range AD = new HlsAdTimeline.Range(496320, 512786);

    @Test
    public void nativeLastProgrammeFrameCanEndBeforeTheBoundary() {
        MpvHlsAdBoundaryState state = new MpvHlsAdBoundaryState();
        state.arm(AD);
        assertEquals(512786, state.consumeEof(496280, false));
        assertTrue(state.pending());
        assertNull(state.range());
    }

    @Test
    public void earlyNetworkEofAndUnrelatedPositionsAreNotAds() {
        MpvHlsAdBoundaryState state = new MpvHlsAdBoundaryState();
        state.arm(AD);
        assertEquals(-1, state.consumeEof(420000, false));
        assertEquals(-1, state.consumeEof(700000, false));
        assertFalse(state.pending());
    }

    @Test
    public void pendingManualSeekCannotBeMistakenForTheClipEnd() {
        MpvHlsAdBoundaryState state = new MpvHlsAdBoundaryState();
        state.arm(AD);
        assertEquals(-1, state.consumeEof(496320, true));
        assertFalse(state.pending());
    }

    @Test
    public void duplicateEofCannotConsumeTheNextBoundaryBeforeRestart() {
        MpvHlsAdBoundaryState state = new MpvHlsAdBoundaryState();
        state.arm(AD);
        assertEquals(512786, state.consumeEof(496280, false));
        state.arm(new HlsAdTimeline.Range(1000000, 1010000));
        assertEquals(-1, state.consumeEof(1000000, false));
        state.playbackRestarted(496280);
        assertTrue(state.pending());
        state.playbackRestarted(512800);
        assertFalse(state.pending());
        assertEquals(1010000, state.consumeEof(999960, false));
    }

    @Test
    public void mediaAndManualResetForgetOldBoundaryAndEof() {
        MpvHlsAdBoundaryState state = new MpvHlsAdBoundaryState();
        state.arm(AD);
        state.consumeEof(496280, false);
        state.clear();
        assertNull(state.range());
        assertFalse(state.pending());
        assertEquals(-1, state.consumeEof(496320, false));
    }

    @Test
    public void nextBoundaryFollowsSourceTimeInBothSeekDirections() {
        HlsAdTimeline timeline = HlsAdTimelineTest.middleAd("2");
        assertEquals(new HlsAdTimeline.Range(4000, 6000), timeline.nextRange(0));
        assertEquals(new HlsAdTimeline.Range(4000, 6000), timeline.nextRange(4000));
        assertNull(timeline.nextRange(6000));
        assertEquals(new HlsAdTimeline.Range(4000, 6000), timeline.nextRange(1000));
        assertNull(HlsAdTimeline.NONE.nextRange(1000));
    }
}
