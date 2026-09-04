package androidx.media3.mpvplayer;

final class MpvInitialTrackSelectionPolicy {

    private MpvInitialTrackSelectionPolicy() {
    }

    static boolean shouldPauseNativePlayback(
            boolean playWhenReady, boolean trackSelectionPending) {
        return !playWhenReady || trackSelectionPending;
    }

    static boolean isSameTrackSelection(String current, String requested) {
        if (current == null || requested == null) return false;
        return normalizeTrackId(current).equals(normalizeTrackId(requested));
    }

    private static String normalizeTrackId(String value) {
        String normalized = value.trim();
        while (normalized.startsWith("0") && normalized.length() > 1) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
