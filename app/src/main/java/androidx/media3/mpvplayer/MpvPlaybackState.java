package androidx.media3.mpvplayer;

import androidx.media3.common.Player;

final class MpvPlaybackState {

    private MpvPlaybackState() {
    }

    static int resolveAfterCachePoll(int currentState, boolean fileLoaded, boolean playbackRestarted, boolean stopping, boolean pausedForCache) {
        if (!fileLoaded || !playbackRestarted || stopping || currentState == Player.STATE_IDLE || currentState == Player.STATE_ENDED) return currentState;
        return pausedForCache ? Player.STATE_BUFFERING : Player.STATE_READY;
    }

    /**
     * Resolves the state a seek request should publish.
     *
     * <p>mpv stops playback the moment a seek starts and only resumes once it emits
     * MPV_EVENT_PLAYBACK_RESTART, so a seek is a real buffering window. Staying READY
     * across it makes the UI hide its progress indicator over a frozen frame: the seek
     * hook shows the indicator up front, then a 500 ms fallback hides it again because
     * the player still claims READY.
     *
     * <p>IDLE keeps its state because nothing is loaded to seek within. ENDED becomes
     * BUFFERING because seeking away from the end resumes playback. {@code stopping}
     * sessions are left alone so a teardown is not reanimated.
     */
    static int resolveAfterSeekRequest(int currentState, boolean fileLoaded, boolean stopping) {
        if (currentState == Player.STATE_IDLE || stopping) return currentState;
        if (currentState == Player.STATE_ENDED) return Player.STATE_BUFFERING;
        return fileLoaded ? Player.STATE_BUFFERING : currentState;
    }
}
