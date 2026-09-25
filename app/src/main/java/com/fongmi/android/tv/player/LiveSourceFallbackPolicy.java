package com.fongmi.android.tv.player;

public final class LiveSourceFallbackPolicy {

    public enum Action {
        NONE,
        NEXT_LINE,
        NEXT_SOURCE
    }

    private LiveSourceFallbackPolicy() {
    }

    public static Action decide(boolean changeLineEnabled, boolean sourceFallbackEnabled, boolean channelPresent, boolean lastLine, boolean onlyLine, boolean nextSourceAvailable) {
        if (channelPresent && changeLineEnabled && !lastLine) return Action.NEXT_LINE;
        if (!sourceFallbackEnabled) return Action.NONE;
        if (nextSourceAvailable) return Action.NEXT_SOURCE;
        if (channelPresent && !onlyLine) return Action.NEXT_LINE;
        return Action.NONE;
    }
}
