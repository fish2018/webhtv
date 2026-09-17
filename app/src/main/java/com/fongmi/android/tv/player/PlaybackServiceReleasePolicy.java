package com.fongmi.android.tv.player;

public final class PlaybackServiceReleasePolicy {

    public enum Action {
        DETACH,
        RESET_SESSION,
        SUSPEND_AND_RESET,
        SHUTDOWN
    }

    private PlaybackServiceReleasePolicy() {
    }

    public static Action decide(boolean owner, boolean keepAlive, boolean hasConsumer) {
        if (owner && keepAlive) return Action.RESET_SESSION;
        if (hasConsumer) return owner ? Action.SUSPEND_AND_RESET : Action.RESET_SESSION;
        return owner ? Action.SHUTDOWN : Action.DETACH;
    }
}
