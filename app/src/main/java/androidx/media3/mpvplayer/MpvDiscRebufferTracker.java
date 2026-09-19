package androidx.media3.mpvplayer;

/** Observes output starvation without pausing the interactive disc VM. */
final class MpvDiscRebufferTracker {
    static final long MIN_STALL_MS = 250;
    private long position = Long.MIN_VALUE;
    private long lastProgressMs;
    private long activeSinceMs = -1;
    private long totalMs;
    private int count;

    void update(long nowMs, boolean eligible, long positionMs, long bufferedMs,
                long demandWaitMs) {
        if (!eligible) {
            interrupt(nowMs);
            return;
        }
        if (position != positionMs) {
            finish(nowMs);
            position = positionMs;
            lastProgressMs = nowMs;
            return;
        }
        boolean starved = demandWaitMs >= MIN_STALL_MS && bufferedMs <= 100
                && nowMs - lastProgressMs >= MIN_STALL_MS;
        if (starved && activeSinceMs < 0) {
            activeSinceMs = Math.max(lastProgressMs, nowMs - demandWaitMs);
            if (count < Integer.MAX_VALUE) count++;
        } else if (!starved) {
            finish(nowMs);
        }
    }

    void interrupt(long nowMs) {
        finish(nowMs);
        position = Long.MIN_VALUE;
    }

    private void finish(long nowMs) {
        if (activeSinceMs >= 0) totalMs += Math.max(0, nowMs - activeSinceMs);
        activeSinceMs = -1;
    }

    int count() {
        return count;
    }

    long totalMs(long nowMs) {
        return totalMs + (activeSinceMs < 0 ? 0 : Math.max(0, nowMs - activeSinceMs));
    }

    boolean active() {
        return activeSinceMs >= 0;
    }

    void reset() {
        position = Long.MIN_VALUE;
        activeSinceMs = -1;
        lastProgressMs = totalMs = count = 0;
    }
}
