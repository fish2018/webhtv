package androidx.media3.mpvplayer;

final class MpvPauseIntentPolicy {

    enum Action {
        NONE,
        WAIT_FOR_ACTIVE_MEDIA,
        REASSERT_REQUESTED_STATE
    }

    private MpvPauseIntentPolicy() {
    }

    static Action resolve(boolean playWhenReady, boolean observedPaused, boolean activeMedia) {
        boolean requestedPaused = !playWhenReady;
        if (observedPaused == requestedPaused) return Action.NONE;
        return activeMedia ? Action.REASSERT_REQUESTED_STATE : Action.WAIT_FOR_ACTIVE_MEDIA;
    }
}
