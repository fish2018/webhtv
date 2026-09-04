package androidx.media3.mpvplayer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MpvPauseIntentPolicyTest {

    @Test
    public void matchingPlayingStateNeedsNoAction() {
        assertEquals(MpvPauseIntentPolicy.Action.NONE,
                MpvPauseIntentPolicy.resolve(true, false, true));
    }

    @Test
    public void matchingPausedStateNeedsNoAction() {
        assertEquals(MpvPauseIntentPolicy.Action.NONE,
                MpvPauseIntentPolicy.resolve(false, true, true));
    }

    @Test
    public void delayedStartupPauseReassertsAutoplayIntent() {
        assertEquals(MpvPauseIntentPolicy.Action.REASSERT_REQUESTED_STATE,
                MpvPauseIntentPolicy.resolve(true, true, true));
    }

    @Test
    public void delayedUnpauseReassertsUserPauseIntent() {
        assertEquals(MpvPauseIntentPolicy.Action.REASSERT_REQUESTED_STATE,
                MpvPauseIntentPolicy.resolve(false, false, true));
    }

    @Test
    public void mismatchBeforeFileIsActiveWaitsForLoadBoundary() {
        assertEquals(MpvPauseIntentPolicy.Action.WAIT_FOR_ACTIVE_MEDIA,
                MpvPauseIntentPolicy.resolve(true, true, false));
    }
}
