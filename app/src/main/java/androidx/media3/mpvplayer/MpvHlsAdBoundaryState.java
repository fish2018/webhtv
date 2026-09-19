package androidx.media3.mpvplayer;

/** Associates a native clipping EOF with one ad boundary, never the real file end. */
final class MpvHlsAdBoundaryState {
    private static final long LAST_FRAME_TOLERANCE_MS = 250;

    private HlsAdTimeline.Range range;
    private long pendingTargetMs = -1;

    HlsAdTimeline.Range range() {
        return range;
    }

    void arm(HlsAdTimeline.Range range) {
        this.range = range;
    }

    long consumeEof(long positionMs, boolean seeking) {
        if (range == null || seeking || pendingTargetMs >= 0
                || positionMs < Math.max(0, range.startMs() - LAST_FRAME_TOLERANCE_MS)
                || positionMs > range.startMs() + LAST_FRAME_TOLERANCE_MS) return -1;
        pendingTargetMs = range.endMs();
        range = null;
        return pendingTargetMs;
    }

    boolean pending() {
        return pendingTargetMs >= 0;
    }

    void playbackRestarted(long positionMs) {
        if (pendingTargetMs >= 0 && Math.abs(positionMs - pendingTargetMs)
                <= MpvSeekPositionState.TARGET_TOLERANCE_MS) pendingTargetMs = -1;
    }

    void clear() {
        range = null;
        pendingTargetMs = -1;
    }
}
