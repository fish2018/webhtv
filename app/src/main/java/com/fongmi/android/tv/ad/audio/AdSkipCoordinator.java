package com.fongmi.android.tv.ad.audio;

import com.fongmi.android.tv.player.audio.PlaybackMediaClock;
import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class AdSkipCoordinator implements AutoCloseable {

    public enum State { IDLE, PROMPT_PENDING, SEEKING, UNDO_WINDOW, SUPPRESSED }

    public interface PlaybackPort {
        PlaybackSnapshot snapshot(long sessionId, long generation);

        SeekResult seekTo(long sessionId, long generation, long positionMs);
    }

    public interface UiPort {
        void showCandidate(Prompt prompt, Actions actions);

        void showUndo(UndoPrompt prompt, Actions actions);

        void dismiss(long sessionId);
    }

    public interface Actions {
        void confirm();

        void ignore();

        void undo();
    }

    public record PlaybackSnapshot(long sessionId, long generation, long positionMs,
                                   long durationMs, boolean seekable, boolean live,
                                   PlaybackMediaClock.Snapshot clock) {
        public PlaybackSnapshot {
            Objects.requireNonNull(clock, "clock");
        }
    }

    public record SeekResult(boolean applied, long sessionId, long generation) {
        public static SeekResult rejected(long sessionId, long generation) {
            return new SeekResult(false, sessionId, generation);
        }
    }

    public record Prompt(long sessionId, long generation, String ruleId,
                         long targetMediaMs, long skipDurationSeconds, boolean fullMatch) {
        public Prompt {
            Objects.requireNonNull(ruleId, "ruleId");
        }
    }

    public record UndoPrompt(long sessionId, long generation, String ruleId,
                             long originalPositionMs, long targetMediaMs,
                             long expiresAfterMs) {
        public UndoPrompt {
            Objects.requireNonNull(ruleId, "ruleId");
        }
    }

    private final PlaybackPort playback;
    private final UiPort ui;
    private final long undoWindowMs;
    private final LongSupplier elapsedRealtime;
    private final AdAudioDiagnostics diagnostics;

    private State state = State.IDLE;
    private CandidateKey activeKey;
    private CandidateKey suppressedKey;
    private AdAudioConsumer.Candidate activeCandidate;
    private UndoContext undoContext;
    private long actionToken;
    private boolean closed;

    public AdSkipCoordinator(PlaybackPort playback, UiPort ui, long undoWindowMs) {
        this(playback, ui, undoWindowMs,
                () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), new AdAudioDiagnostics());
    }

    AdSkipCoordinator(PlaybackPort playback, UiPort ui, long undoWindowMs,
                      LongSupplier elapsedRealtime) {
        this(playback, ui, undoWindowMs, elapsedRealtime, new AdAudioDiagnostics());
    }

    AdSkipCoordinator(PlaybackPort playback, UiPort ui, long undoWindowMs,
                      AdAudioDiagnostics diagnostics) {
        this(playback, ui, undoWindowMs,
                () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), diagnostics);
    }

    private AdSkipCoordinator(PlaybackPort playback, UiPort ui, long undoWindowMs,
                              LongSupplier elapsedRealtime, AdAudioDiagnostics diagnostics) {
        this.playback = Objects.requireNonNull(playback, "playback");
        this.ui = Objects.requireNonNull(ui, "ui");
        if (undoWindowMs <= 0L) throw new IllegalArgumentException("undoWindowMs must be positive");
        this.undoWindowMs = undoWindowMs;
        this.elapsedRealtime = Objects.requireNonNull(elapsedRealtime, "elapsedRealtime");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public synchronized State state() {
        return state;
    }

    public synchronized void onCandidate(AdAudioConsumer.Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (closed || state == State.SEEKING || state == State.UNDO_WINDOW) return;
        CandidateKey key = CandidateKey.of(candidate);
        if (key.equals(suppressedKey)) return;
        if (state == State.SUPPRESSED) {
            state = State.IDLE;
            suppressedKey = null;
        }
        if (state == State.PROMPT_PENDING && key.equals(activeKey)) {
            if (!isFull(activeCandidate) && isFull(candidate)) showCandidate(candidate, key);
            return;
        }
        showCandidate(candidate, key);
    }

    public void onCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        onCandidate(toLegacyCandidate(candidate));
    }

    public synchronized boolean onAutoCandidate(
            AdAudioSignalProvider.AdAudioCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (closed || state == State.PROMPT_PENDING
                || state == State.SEEKING || state == State.UNDO_WINDOW) {
            return false;
        }
        AdAudioConsumer.Candidate legacy = toLegacyCandidate(candidate);
        CandidateKey key = CandidateKey.of(legacy);
        if (key.equals(suppressedKey)) return false;
        if (state == State.SUPPRESSED) {
            state = State.IDLE;
            suppressedKey = null;
        }
        Target target = targetFor(legacy);
        if (target == null) return false;
        return applyAutomaticSeek(legacy, target);
    }

    public synchronized void reset(long sessionId) {
        if (closed) return;
        actionToken++;
        clearState();
        ui.dismiss(sessionId);
    }

    public synchronized void onTimelineReset(PlaybackMediaSignalHub.Lifecycle event) {
        Objects.requireNonNull(event, "event");
        if (closed) return;
        if (event.reason() == PlaybackMediaSignalHub.ResetReason.SEEK
                && state == State.UNDO_WINDOW
                && undoContext != null
                && undoContext.sessionId == event.sessionId()
                && undoContext.generation == event.generation()) return;
        reset(event.sessionId());
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        actionToken++;
        long sessionId = activeSessionId();
        clearState();
        if (sessionId != Long.MIN_VALUE) ui.dismiss(sessionId);
    }

    private void showCandidate(AdAudioConsumer.Candidate candidate, CandidateKey key) {
        Target target = targetFor(candidate);
        if (target == null) {
            AdAudioDiagnostics.log("prompt dropped rule=%s endMs=%d",
                    candidate.event().ruleId(), candidate.event().endTimeMs());
            return;
        }
        activeCandidate = candidate;
        activeKey = key;
        undoContext = null;
        state = State.PROMPT_PENDING;
        long token = ++actionToken;
        long remainingMs = Math.max(0L, target.positionMs - target.snapshot.positionMs());
        Prompt prompt = new Prompt(candidate.sessionId(), candidate.generation(),
                candidate.event().ruleId(), target.positionMs,
                Math.max(1L, (remainingMs + 999L) / 1_000L), isFull(candidate));
        AdAudioDiagnostics.log("prompt shown rule=%s targetMs=%d fromMs=%d",
                prompt.ruleId(), prompt.targetMediaMs(), target.snapshot.positionMs());
        ui.showCandidate(prompt, new TokenActions(token));
    }

    private synchronized void confirm(long token) {
        if (!isActive(token, State.PROMPT_PENDING) || activeCandidate == null) return;
        Target target = targetFor(activeCandidate);
        if (target == null) {
            rejectActive();
            return;
        }
        long originalPositionMs = target.snapshot.positionMs();
        AdAudioConsumer.Candidate candidate = activeCandidate;
        state = State.SEEKING;
        SeekResult seek = playback.seekTo(candidate.sessionId(), candidate.generation(), target.positionMs);
        if (seek == null || !seek.applied() || seek.sessionId() != candidate.sessionId()) {
            diagnostics.record(AdAudioDiagnostics.Code.SEEK_REJECTED);
            rejectActive();
            return;
        }
        long now = elapsedRealtime.getAsLong();
        long deadline = saturatedAdd(now, undoWindowMs);
        undoContext = new UndoContext(candidate.sessionId(), seek.generation(),
                candidate.event().ruleId(), originalPositionMs, target.positionMs, deadline);
        activeCandidate = null;
        activeKey = null;
        state = State.UNDO_WINDOW;
        long undoToken = ++actionToken;
        ui.showUndo(new UndoPrompt(undoContext.sessionId, undoContext.generation,
                undoContext.ruleId, undoContext.originalPositionMs,
                undoContext.targetPositionMs, undoWindowMs), new TokenActions(undoToken));
    }

    private boolean applyAutomaticSeek(AdAudioConsumer.Candidate candidate, Target target) {
        long originalPositionMs = target.snapshot.positionMs();
        state = State.SEEKING;
        SeekResult seek = playback.seekTo(
                candidate.sessionId(), candidate.generation(), target.positionMs);
        if (seek == null || !seek.applied() || seek.sessionId() != candidate.sessionId()) {
            diagnostics.record(AdAudioDiagnostics.Code.SEEK_REJECTED);
            clearState();
            return false;
        }
        long now = elapsedRealtime.getAsLong();
        long deadline = saturatedAdd(now, undoWindowMs);
        undoContext = new UndoContext(candidate.sessionId(), seek.generation(),
                candidate.event().ruleId(), originalPositionMs, target.positionMs, deadline);
        activeCandidate = null;
        activeKey = null;
        state = State.UNDO_WINDOW;
        long undoToken = ++actionToken;
        ui.showUndo(new UndoPrompt(undoContext.sessionId, undoContext.generation,
                undoContext.ruleId, undoContext.originalPositionMs,
                undoContext.targetPositionMs, undoWindowMs), new TokenActions(undoToken));
        return true;
    }

    private synchronized void ignore(long token) {
        if (closed || token != actionToken) return;
        long sessionId = activeSessionId();
        if (state == State.PROMPT_PENDING) {
            suppressedKey = activeKey;
            activeKey = null;
            activeCandidate = null;
            state = State.SUPPRESSED;
        } else if (state == State.UNDO_WINDOW) {
            undoContext = null;
            state = State.IDLE;
        } else {
            return;
        }
        actionToken++;
        if (sessionId != Long.MIN_VALUE) ui.dismiss(sessionId);
    }

    private synchronized void undo(long token) {
        if (!isActive(token, State.UNDO_WINDOW) || undoContext == null) return;
        UndoContext undo = undoContext;
        if (elapsedRealtime.getAsLong() > undo.deadlineElapsedMs) {
            finishUndo(undo.sessionId);
            return;
        }
        PlaybackSnapshot snapshot = playback.snapshot(undo.sessionId, undo.generation);
        if (isCurrentSeekable(snapshot, undo.sessionId, undo.generation)) {
            SeekResult result = playback.seekTo(undo.sessionId, undo.generation,
                    clamp(undo.originalPositionMs, 0L, snapshot.durationMs));
            if (result == null || !result.applied()) diagnostics.record(AdAudioDiagnostics.Code.SEEK_REJECTED);
        } else {
            diagnostics.record(AdAudioDiagnostics.Code.SEEK_REJECTED);
        }
        finishUndo(undo.sessionId);
    }

    private Target targetFor(AdAudioConsumer.Candidate candidate) {
        PlaybackSnapshot snapshot = playback.snapshot(candidate.sessionId(), candidate.generation());
        if (!isCurrentSeekable(snapshot, candidate.sessionId(), candidate.generation())) {
            diagnostics.record(AdAudioDiagnostics.Code.SEEK_REJECTED);
            return null;
        }
        if (!snapshot.clock().fresh() || snapshot.clock().generation() != candidate.generation()) {
            diagnostics.record(AdAudioDiagnostics.Code.CLOCK_UNAVAILABLE);
            return null;
        }
        OptionalLong mapped = snapshot.clock().mapCaptureToMediaMs(candidate.event().endTimeMs());
        if (mapped.isEmpty()) {
            diagnostics.record(AdAudioDiagnostics.Code.CLOCK_UNAVAILABLE);
            return null;
        }
        long target = clamp(mapped.getAsLong(), 0L, snapshot.durationMs());
        if (target <= snapshot.positionMs()) {
            diagnostics.record(AdAudioDiagnostics.Code.SEEK_REJECTED);
            return null;
        }
        return new Target(snapshot, target);
    }

    private static boolean isCurrentSeekable(PlaybackSnapshot snapshot, long sessionId, long generation) {
        return snapshot != null
                && snapshot.sessionId() == sessionId
                && snapshot.generation() == generation
                && snapshot.seekable()
                && !snapshot.live()
                && snapshot.durationMs() > 0L;
    }

    private boolean isActive(long token, State expected) {
        return !closed && token == actionToken && state == expected;
    }

    private void rejectActive() {
        long sessionId = activeSessionId();
        actionToken++;
        activeKey = null;
        activeCandidate = null;
        undoContext = null;
        state = State.IDLE;
        if (sessionId != Long.MIN_VALUE) ui.dismiss(sessionId);
    }

    private void finishUndo(long sessionId) {
        actionToken++;
        undoContext = null;
        state = State.IDLE;
        ui.dismiss(sessionId);
    }

    private void clearState() {
        state = State.IDLE;
        activeKey = null;
        suppressedKey = null;
        activeCandidate = null;
        undoContext = null;
    }

    private long activeSessionId() {
        if (activeCandidate != null) return activeCandidate.sessionId();
        if (undoContext != null) return undoContext.sessionId;
        if (suppressedKey != null) return suppressedKey.sessionId;
        return Long.MIN_VALUE;
    }

    private static boolean isFull(AdAudioConsumer.Candidate candidate) {
        return candidate != null && candidate.event().type() == AudioFingerprintMatcher.Type.FULL_MATCHED;
    }

    private static AdAudioConsumer.Candidate toLegacyCandidate(
            AdAudioSignalProvider.AdAudioCandidate candidate) {
        AudioFingerprintMatcher.MatchEvent event = new AudioFingerprintMatcher.MatchEvent(
                candidate.fullMatch() ? AudioFingerprintMatcher.Type.FULL_MATCHED
                        : AudioFingerprintMatcher.Type.START_MATCHED,
                candidate.ruleId(), candidate.startMs(), candidate.endMs(),
                (float) candidate.similarity(), 0);
        return new AdAudioConsumer.Candidate(candidate.sessionId(), candidate.generation(), event);
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private record CandidateKey(long sessionId, long generation, String ruleId,
                                long startMs, long endMs) {
        static CandidateKey of(AdAudioConsumer.Candidate candidate) {
            // The matched interval is part of the identity, otherwise ignoring one
            // occurrence silences every later match of the same rule. START_MATCHED and
            // FULL_MATCHED share an interval, so prompt upgrades still collapse onto one key.
            return new CandidateKey(candidate.sessionId(), candidate.generation(),
                    candidate.event().ruleId(), candidate.event().startTimeMs(),
                    candidate.event().endTimeMs());
        }
    }

    private record Target(PlaybackSnapshot snapshot, long positionMs) {
    }

    private record UndoContext(long sessionId, long generation, String ruleId,
                               long originalPositionMs, long targetPositionMs,
                               long deadlineElapsedMs) {
    }

    private final class TokenActions implements Actions {
        private final long token;

        private TokenActions(long token) {
            this.token = token;
        }

        @Override
        public void confirm() {
            AdSkipCoordinator.this.confirm(token);
        }

        @Override
        public void ignore() {
            AdSkipCoordinator.this.ignore(token);
        }

        @Override
        public void undo() {
            AdSkipCoordinator.this.undo(token);
        }
    }
}
