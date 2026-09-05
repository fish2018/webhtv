package com.fongmi.android.tv.player.exo;

/**
 * Detects a player stuck in BUFFERING that is no longer making any progress.
 *
 * <p>Position alone is not evidence: it legitimately stands still throughout a
 * normal rebuffer. Only when neither the playback position nor the buffered end
 * advances is the session actually stalled.
 *
 * <p>The timeout must stay above {@code MAX_STREAMING_REBUFFER_MS} (15 s) so a
 * LoadControl that is still filling its rebuffer threshold is never killed.
 */
public final class ExoBufferingStallWatchdog {

    public static final long STALL_TIMEOUT_MS = 20_000L;

    /**
     * While the source still reports loading, neither the position nor the buffered
     * end has to move: a remote Matroska seek fetches the file-tail Cues before it
     * can produce a single sample (see E-SP2). Killing that would trade a working
     * fetch for a needless fallback, so a loading source gets this longer ceiling
     * instead. It still has to be bounded, or a hung socket read would never trip.
     */
    public static final long LOADING_STALL_TIMEOUT_MS = 60_000L;

    /**
     * A regression smaller than this counts as jitter and is ignored; anything larger is
     * treated as a seek or flush and re-arms the baseline. Well below the smallest useful
     * seek step so a real jump is never mistaken for jitter.
     */
    public static final long DISCONTINUITY_TOLERANCE_MS = 1_000L;

    /**
     * Absolute ceiling for one buffering episode. A discontinuity re-anchors the progress clock,
     * so a source that regresses by more than the tolerance on a repeating cycle could otherwise
     * defer the timeout forever and resurrect the stall this class exists to catch.
     *
     * <p>Only the episode's <em>time</em> anchor survives a re-anchor; the progress watermarks do
     * not (see {@link #segmentStartPositionMs}). That split is what makes termination provable:
     * each re-anchor raises the progress bar to the new low point, and a bounded signal's low
     * points are bounded, so the bar eventually cannot be cleared. Keeping the watermarks pinned
     * to {@code arm()} instead would let any oscillation whose trough sits above
     * {@code arm + margin} defer the ceiling forever.
     */
    public static final long EPISODE_CEILING_MS = 90_000L;

    /**
     * Net progress within the current continuity segment that spares a session from the ceiling.
     * Sized at the same order as {@code MAX_STREAMING_REBUFFER_MS} (15 s) so a session that is
     * slowly but genuinely filling its rebuffer threshold survives, while a session whose only
     * movement is jitter or a regression cycle does not.
     *
     * <p>Its ratio to {@link #EPISODE_CEILING_MS} implies a floor on throughput: 15 s of media
     * within 90 s means roughly one sixth of realtime. A stream that cannot sustain that will
     * never catch up to playback, so falling back beats waiting. Adjusting either constant alone
     * moves that floor, which is the property to reason about rather than the raw numbers.
     */
    public static final long SEGMENT_PROGRESS_MARGIN_MS = 15_000L;

    private boolean armed;
    private long episodeStartedAtMs;
    /**
     * Progress watermarks for the current <em>continuity segment</em>, not for the episode. A
     * discontinuity re-anchors them because progress can only be measured within one continuous
     * stretch: after a backward seek the pre-seek watermarks are unreachable, so keeping them
     * would read every later sample as zero progress and let the ceiling kill a session that is
     * in fact advancing. The episode's <em>time</em> anchor deliberately survives, since that is
     * what bounds a repeating regression cycle.
     */
    private long segmentStartPositionMs;
    private long segmentStartBufferedMs;
    private long lastProgressAtMs;
    private long lastPositionMs;
    private long lastBufferedPositionMs;

    /**
     * Starts a fresh episode. Use this at the real arming points (entering BUFFERING, a seek,
     * a first frame before READY) and for every tick while paused, so paused time never
     * accumulates toward {@link #EPISODE_CEILING_MS}.
     */
    public void arm(long nowMs, long positionMs, long bufferedPositionMs) {
        armed = true;
        episodeStartedAtMs = nowMs;
        rebaseline(nowMs, positionMs, bufferedPositionMs);
    }

    /** Starts a new continuity segment: both the progress clock and its watermarks re-anchor. */
    private void rebaseline(long nowMs, long positionMs, long bufferedPositionMs) {
        lastProgressAtMs = nowMs;
        lastPositionMs = positionMs;
        lastBufferedPositionMs = bufferedPositionMs;
        segmentStartPositionMs = positionMs;
        segmentStartBufferedMs = bufferedPositionMs;
    }

    public void observe(long nowMs, long positionMs, long bufferedPositionMs) {
        if (!armed) {
            arm(nowMs, positionMs, bufferedPositionMs);
            return;
        }
        // A large regression is a discontinuity, not a stall: a backward seek or a flush moves
        // the position and the buffered end below the recorded baseline, and keeping the old
        // baseline would make every later sample compare as "no progress" and time out a
        // session that merely jumped. Re-arm on the new, lower baseline instead.
        //
        // Small regressions must NOT re-arm. The buffered end can jitter down a little while
        // buffering, and re-arming on jitter would reset the clock on every dip, so an
        // oscillating-but-stalled session would never time out at all.
        // This re-anchors the progress clock AND its watermarks (a new continuity segment), while
        // the episode's time anchor deliberately survives so a repeating regression cycle still
        // cannot defer the ceiling indefinitely.
        if (positionMs < lastPositionMs - DISCONTINUITY_TOLERANCE_MS
                || bufferedPositionMs < lastBufferedPositionMs - DISCONTINUITY_TOLERANCE_MS) {
            rebaseline(nowMs, positionMs, bufferedPositionMs);
            return;
        }
        if (positionMs > lastPositionMs || bufferedPositionMs > lastBufferedPositionMs) {
            lastPositionMs = Math.max(lastPositionMs, positionMs);
            lastBufferedPositionMs = Math.max(lastBufferedPositionMs, bufferedPositionMs);
            lastProgressAtMs = nowMs;
        }
    }

    public void reset() {
        armed = false;
        episodeStartedAtMs = 0;
        segmentStartPositionMs = 0;
        segmentStartBufferedMs = 0;
        lastProgressAtMs = 0;
        lastPositionMs = 0;
        lastBufferedPositionMs = 0;
    }

    public boolean isArmed() {
        return armed;
    }

    public boolean shouldTimeout(
            long nowMs, long positionMs, long bufferedPositionMs, boolean loading) {
        if (!armed) return false;
        // The ceiling bounds a regression cycle that keeps re-anchoring the progress clock, but it
        // must not kill a session that is genuinely advancing: a large file on a slow link can
        // spend well over the ceiling steadily filling its rebuffer threshold. Require both the
        // elapsed ceiling AND insufficient net progress within the current continuity segment.
        if (nowMs - episodeStartedAtMs >= EPISODE_CEILING_MS
                && netSegmentProgressMs(positionMs, bufferedPositionMs)
                < SEGMENT_PROGRESS_MARGIN_MS) {
            return true;
        }
        return positionMs <= lastPositionMs
                && bufferedPositionMs <= lastBufferedPositionMs
                && nowMs - lastProgressAtMs >= (loading ? LOADING_STALL_TIMEOUT_MS : STALL_TIMEOUT_MS);
    }

    /**
     * Progress within the current continuity segment. The larger of the two axes is used: either
     * the position advancing or the buffered end growing is real forward motion on its own, so
     * requiring both would kill a session that is only filling its buffer while paused-by-buffer.
     */
    private long netSegmentProgressMs(long positionMs, long bufferedPositionMs) {
        long position = Math.max(0, positionMs - segmentStartPositionMs);
        long buffered = Math.max(0, bufferedPositionMs - segmentStartBufferedMs);
        return Math.max(position, buffered);
    }
}
