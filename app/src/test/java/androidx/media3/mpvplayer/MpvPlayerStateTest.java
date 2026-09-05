package androidx.media3.mpvplayer;

import static org.junit.Assert.assertEquals;

import androidx.media3.common.Player;

import org.junit.Test;

public class MpvPlayerStateTest {

    @Test
    public void cachePollRecoversReadyWhenPausedForCacheFalseEventWasMissed() {
        assertEquals(Player.STATE_READY,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_BUFFERING, true, true, false, false));
    }

    @Test
    public void cachePollKeepsBufferingWhileMpvIsActuallyPausedForCache() {
        assertEquals(Player.STATE_BUFFERING,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_READY, true, true, false, true));
    }

    @Test
    public void cachePollDoesNotClaimReadyBeforeFirstPlaybackRestart() {
        assertEquals(Player.STATE_BUFFERING,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_BUFFERING, true, false, false, false));
    }

    @Test
    public void cachePollDoesNotChangeStateWhileStopping() {
        assertEquals(Player.STATE_BUFFERING,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_BUFFERING, true, true, true, false));
    }

    @Test
    public void cachePollKeepsTerminalStates() {
        assertEquals(Player.STATE_IDLE,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_IDLE, true, true, false, false));
        assertEquals(Player.STATE_ENDED,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_ENDED, true, true, false, true));
    }

    @Test
    public void seekFromReadyReportsBufferingInsteadOfHoldingAFrozenFrame() {
        assertEquals(Player.STATE_BUFFERING,
                MpvPlaybackState.resolveAfterSeekRequest(
                        Player.STATE_READY, true, false));
    }

    @Test
    public void seekAwayFromTheEndResumesThroughBuffering() {
        assertEquals(Player.STATE_BUFFERING,
                MpvPlaybackState.resolveAfterSeekRequest(
                        Player.STATE_ENDED, true, false));
    }

    @Test
    public void seekKeepsIdleBecauseNothingIsLoadedToSeekWithin() {
        assertEquals(Player.STATE_IDLE,
                MpvPlaybackState.resolveAfterSeekRequest(
                        Player.STATE_IDLE, false, false));
        assertEquals(Player.STATE_IDLE,
                MpvPlaybackState.resolveAfterSeekRequest(
                        Player.STATE_IDLE, true, false));
    }

    @Test
    public void seekDoesNotReanimateATeardown() {
        assertEquals(Player.STATE_READY,
                MpvPlaybackState.resolveAfterSeekRequest(
                        Player.STATE_READY, true, true));
        assertEquals(Player.STATE_ENDED,
                MpvPlaybackState.resolveAfterSeekRequest(
                        Player.STATE_ENDED, true, true));
    }

    @Test
    public void seekBeforeFileLoadedKeepsTheStartupBufferingState() {
        assertEquals(Player.STATE_BUFFERING,
                MpvPlaybackState.resolveAfterSeekRequest(
                        Player.STATE_BUFFERING, false, false));
        assertEquals(Player.STATE_READY,
                MpvPlaybackState.resolveAfterSeekRequest(
                        Player.STATE_READY, false, false));
    }
}
