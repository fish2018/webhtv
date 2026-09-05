package com.fongmi.android.tv.ui.audio;

import java.util.Objects;

public final class AudioPlaybackReconciliation {

    private static final String VOD_EPISODE_PREFIX = "VE:";

    private AudioPlaybackReconciliation() {
    }

    public static Match reconcile(String currentKey, String playbackKey, String flag, int currentIndex, int episodeCount) {
        String current = Objects.toString(currentKey, "");
        String playback = Objects.toString(playbackKey, "");
        int fallbackIndex = clampIndex(currentIndex, episodeCount);
        if (!playback.isEmpty() && playback.equals(current)) return new Match(true, fallbackIndex);
        if (playback.isEmpty() || flag == null || flag.isEmpty() || episodeCount <= 0) return new Match(false, fallbackIndex);
        String prefix = VOD_EPISODE_PREFIX + playback + '|' + flag + '|';
        if (!current.startsWith(prefix)) return new Match(false, fallbackIndex);
        try {
            int index = Integer.parseInt(current.substring(prefix.length()));
            return index >= 0 && index < episodeCount ? new Match(true, index) : new Match(false, fallbackIndex);
        } catch (NumberFormatException e) {
            return new Match(false, fallbackIndex);
        }
    }

    private static int clampIndex(int index, int episodeCount) {
        if (episodeCount <= 0) return 0;
        return Math.max(0, Math.min(index, episodeCount - 1));
    }

    public record Match(boolean owned, int index) {
    }
}
