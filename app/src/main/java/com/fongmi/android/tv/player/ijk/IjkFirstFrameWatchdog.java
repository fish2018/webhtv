package com.fongmi.android.tv.player.ijk;

import com.fongmi.android.tv.player.PlaybackAutoContext;

/**
 * Session-scoped safety watchdog for IJK attempts that become active without
 * ever producing a first frame. It deliberately knows nothing about profile
 * learning so fixed performance profiles can use the normal fallback policy.
 */
public final class IjkFirstFrameWatchdog {

    static final long TIMEOUT_ACTIVE_MS = 10_000L;

    private PlaybackAutoContext.SessionToken session =
            PlaybackAutoContext.SessionToken.none();
    private boolean enabled;
    private boolean prepared;
    private boolean firstFrame;
    private boolean timedOut;
    private long activeDurationMs;
    private long lastSampleAtElapsedMs = -1;

    public synchronized void beginSession(
            PlaybackAutoContext.SessionToken token) {
        session = token == null
                ? PlaybackAutoContext.SessionToken.none() : token;
        resetAttempt(false);
    }

    public synchronized void beginAttempt(
            PlaybackAutoContext.SessionToken token,
            boolean enabled) {
        if (!isCurrent(token)) return;
        resetAttempt(enabled);
    }

    public synchronized void onPrepared(
            PlaybackAutoContext.SessionToken token,
            long nowElapsedMs) {
        if (!isCurrent(token) || !enabled || prepared) return;
        prepared = true;
        activeDurationMs = 0;
        lastSampleAtElapsedMs = Math.max(0, nowElapsedMs);
    }

    public synchronized void onFirstFrame(
            PlaybackAutoContext.SessionToken token) {
        if (!isCurrent(token) || !enabled) return;
        firstFrame = true;
        resetWait(-1);
    }

    public synchronized Decision evaluate(
            PlaybackAutoContext.SessionToken token,
            RuntimeSample sample,
            long nowElapsedMs) {
        if (!isCurrent(token)) return Decision.hold(Reason.STALE_SESSION, 0);
        if (!enabled) return Decision.hold(Reason.DISABLED, 0);
        if (!prepared) {
            return Decision.hold(Reason.NOT_PREPARED, activeDurationMs);
        }
        if (firstFrame) {
            return Decision.hold(Reason.FIRST_FRAME_RENDERED, activeDurationMs);
        }
        if (timedOut) {
            return Decision.hold(Reason.ALREADY_TIMED_OUT, activeDurationMs);
        }
        RuntimeSample current = sample == null
                ? RuntimeSample.unknown() : sample;
        long now = Math.max(0, nowElapsedMs);
        if (!current.videoTrackPresent()) {
            resetWait(now);
            return Decision.hold(Reason.NO_VIDEO_EVIDENCE, activeDurationMs);
        }
        if (current.outputFramesPresent()) {
            resetWait(now);
            return Decision.hold(Reason.OUTPUT_FRAMES_PRESENT, activeDurationMs);
        }
        advance(current.active(), now);
        if (!current.active()) {
            return Decision.hold(Reason.INACTIVE, activeDurationMs);
        }
        if (activeDurationMs < TIMEOUT_ACTIVE_MS) {
            return Decision.hold(Reason.WAITING, activeDurationMs);
        }
        timedOut = true;
        return new Decision(Action.TIMEOUT, Reason.ACTIVE_DEADLINE,
                activeDurationMs);
    }

    public synchronized void endSession(
            PlaybackAutoContext.SessionToken token) {
        if (!isCurrent(token)) return;
        session = PlaybackAutoContext.SessionToken.none();
        resetAttempt(false);
    }

    private void advance(boolean active, long nowElapsedMs) {
        // 以「当前采样活跃」为累加条件：采样由外部周期性驱动，若改用上一次
        // 采样的状态，prepared 后的第一个间隔永远不计时，实际超时会比
        // TIMEOUT_ACTIVE_MS 多出一个采样周期。
        if (lastSampleAtElapsedMs >= 0
                && nowElapsedMs >= lastSampleAtElapsedMs
                && active) {
            activeDurationMs = saturatingAdd(
                    activeDurationMs,
                    nowElapsedMs - lastSampleAtElapsedMs);
        }
        lastSampleAtElapsedMs = nowElapsedMs;
    }

    private void resetWait(long nowElapsedMs) {
        activeDurationMs = 0;
        lastSampleAtElapsedMs = nowElapsedMs < 0
                ? -1 : Math.max(0, nowElapsedMs);
    }

    private void resetAttempt(boolean enabled) {
        this.enabled = enabled;
        prepared = false;
        firstFrame = false;
        timedOut = false;
        resetWait(-1);
    }

    private boolean isCurrent(PlaybackAutoContext.SessionToken token) {
        return token != null && token.active() && token.equals(session);
    }

    private static long saturatingAdd(long first, long second) {
        if (second <= 0) return Math.max(0, first);
        return first > Long.MAX_VALUE - second
                ? Long.MAX_VALUE : Math.max(0, first) + second;
    }

    public enum Action {
        HOLD,
        TIMEOUT
    }

    public enum Reason {
        DISABLED,
        STALE_SESSION,
        NOT_PREPARED,
        FIRST_FRAME_RENDERED,
        ALREADY_TIMED_OUT,
        NO_VIDEO_EVIDENCE,
        OUTPUT_FRAMES_PRESENT,
        INACTIVE,
        WAITING,
        ACTIVE_DEADLINE
    }

    public record RuntimeSample(
            boolean active,
            boolean videoTrackPresent,
            boolean outputFramesPresent) {

        static RuntimeSample unknown() {
            return new RuntimeSample(false, false, false);
        }
    }

    public record Decision(
            Action action,
            Reason reason,
            long activeDurationMs) {

        public Decision {
            action = action == null ? Action.HOLD : action;
            reason = reason == null ? Reason.DISABLED : reason;
            activeDurationMs = Math.max(0, activeDurationMs);
        }

        static Decision hold(Reason reason, long activeDurationMs) {
            return new Decision(Action.HOLD, reason, activeDurationMs);
        }

        public boolean timedOut() {
            return action == Action.TIMEOUT;
        }
    }
}