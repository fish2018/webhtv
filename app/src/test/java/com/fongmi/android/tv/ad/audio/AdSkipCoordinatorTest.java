package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.audio.PlaybackMediaClock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

public class AdSkipCoordinatorTest {

    @Test
    public void confirmSeeksAndUndoReturnsToOriginalPosition() {
        ManualTime time = new ManualTime();
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 45_000L, 120_000L, true, false,
                freshClock(2L, 35_000L, 35_000L, 25_000L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L, time);

        coordinator.onCandidate(candidate(7L, 2L, "ad-1", 10_000L, 25_000L,
                AudioFingerprintMatcher.Type.START_MATCHED));
        ui.actions.confirm();
        ui.actions.undo();

        assertEquals(List.of(60_000L, 45_000L), playback.seekTargets);
        assertEquals(AdSkipCoordinator.State.IDLE, coordinator.state());
    }

    @Test
    public void coordinatorSeekLifecyclePreservesUndoAcrossNewGeneration() {
        ManualTime time = new ManualTime();
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 45_000L, 120_000L, true, false,
                freshClock(2L, 35_000L, 35_000L, 25_000L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L, time);

        coordinator.onCandidate(candidate(7L, 2L, "ad-1", 10_000L, 25_000L,
                AudioFingerprintMatcher.Type.START_MATCHED));
        ui.actions.confirm();
        coordinator.onTimelineReset(new com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub.Lifecycle(
                7L, 3L, com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub.ResetReason.SEEK, 60_000L));
        ui.actions.undo();

        assertEquals(List.of(60_000L, 45_000L), playback.seekTargets);
        assertEquals(AdSkipCoordinator.State.IDLE, coordinator.state());
    }

    @Test
    public void staleGenerationAndLivePlaybackAreRejected() {
        FakePlaybackPort stalePlayback = new FakePlaybackPort(snapshot(
                7L, 3L, 1_000L, 100_000L, true, false,
                freshClock(3L, 0L, 30_000L, 1_000L)));
        FakeUiPort staleUi = new FakeUiPort();
        AdSkipCoordinator stale = new AdSkipCoordinator(stalePlayback, staleUi, 5_000L);
        stale.onCandidate(candidate(7L, 2L, "old", 0L, 10_000L,
                AudioFingerprintMatcher.Type.START_MATCHED));

        FakePlaybackPort livePlayback = new FakePlaybackPort(snapshot(
                8L, 1L, 1_000L, 100_000L, true, true,
                freshClock(1L, 0L, 30_000L, 1_000L)));
        FakeUiPort liveUi = new FakeUiPort();
        AdSkipCoordinator live = new AdSkipCoordinator(livePlayback, liveUi, 5_000L);
        live.onCandidate(candidate(8L, 1L, "live", 0L, 10_000L,
                AudioFingerprintMatcher.Type.START_MATCHED));

        assertEquals(0, staleUi.candidateShows);
        assertEquals(0, liveUi.candidateShows);
    }

    @Test
    public void fullMatchUpgradesPromptButDuplicateStartIsIgnored() {
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                1L, 1L, 5_000L, 100_000L, true, false,
                freshClock(1L, 0L, 30_000L, 5_000L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);

        coordinator.onCandidate(candidate(1L, 1L, "ad", 0L, 10_000L,
                AudioFingerprintMatcher.Type.START_MATCHED));
        coordinator.onCandidate(candidate(1L, 1L, "ad", 0L, 10_000L,
                AudioFingerprintMatcher.Type.START_MATCHED));
        coordinator.onCandidate(candidate(1L, 1L, "ad", 0L, 10_000L,
                AudioFingerprintMatcher.Type.FULL_MATCHED));

        assertEquals(2, ui.candidateShows);
        assertTrue(ui.lastPrompt.fullMatch());
    }

    @Test
    public void ignoreSuppressesOnlyTheCurrentCandidate() {
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                1L, 1L, 5_000L, 100_000L, true, false,
                freshClock(1L, 0L, 30_000L, 5_000L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);
        AdAudioConsumer.Candidate first = candidate(1L, 1L, "ad-1", 0L, 10_000L,
                AudioFingerprintMatcher.Type.START_MATCHED);

        coordinator.onCandidate(first);
        ui.actions.ignore();
        coordinator.onCandidate(first);
        coordinator.onCandidate(candidate(1L, 1L, "ad-2", 0L, 12_000L,
                AudioFingerprintMatcher.Type.START_MATCHED));

        assertEquals(2, ui.candidateShows);
        assertEquals("ad-2", ui.lastPrompt.ruleId());
    }

    @Test
    public void undoAfterDeadlineDoesNotSeekAgain() {
        ManualTime time = new ManualTime();
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                1L, 1L, 5_000L, 100_000L, true, false,
                freshClock(1L, 0L, 30_000L, 5_000L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L, time);
        coordinator.onCandidate(candidate(1L, 1L, "ad", 0L, 10_000L,
                AudioFingerprintMatcher.Type.FULL_MATCHED));
        ui.actions.confirm();
        time.now = 5_001L;

        ui.actions.undo();

        assertEquals(List.of(10_000L), playback.seekTargets);
        assertEquals(AdSkipCoordinator.State.IDLE, coordinator.state());
    }

    @Test
    public void staleDialogActionCannotConfirmReplacementPrompt() {
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                1L, 1L, 5_000L, 100_000L, true, false,
                freshClock(1L, 0L, 30_000L, 5_000L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);
        coordinator.onCandidate(candidate(1L, 1L, "ad-1", 0L, 10_000L,
                AudioFingerprintMatcher.Type.START_MATCHED));
        AdSkipCoordinator.Actions old = ui.actions;
        coordinator.onCandidate(candidate(1L, 1L, "ad-2", 0L, 20_000L,
                AudioFingerprintMatcher.Type.START_MATCHED));

        old.confirm();

        assertTrue(playback.seekTargets.isEmpty());
        assertEquals("ad-2", ui.lastPrompt.ruleId());
    }

    @Test
    public void automaticCandidateSeeksImmediatelyAndKeepsUndo() {
        ManualTime time = new ManualTime();
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                1L, 1L, 5_000L, 100_000L, true, false,
                freshClock(1L, 0L, 30_000L, 5_000L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L, time);

        boolean applied = coordinator.onAutoCandidate(providerCandidate(
                1L, 1L, "ad", 0L, 10_000L, true));

        assertTrue(applied);
        assertEquals(0, ui.candidateShows);
        assertEquals(List.of(10_000L), playback.seekTargets);
        assertEquals(AdSkipCoordinator.State.UNDO_WINDOW, coordinator.state());

        ui.actions.undo();

        assertEquals(List.of(10_000L, 5_000L), playback.seekTargets);
        assertEquals(AdSkipCoordinator.State.IDLE, coordinator.state());
    }

    @Test
    public void ignoreSuppressesOnlyTheMatchedIntervalNotEveryLaterMatchOfTheRule() {
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                1L, 1L, 5_000L, 1_500_000L, true, false,
                freshClock(1L, 0L, 1_000_000L, 5_000L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);

        coordinator.onCandidate(candidate(1L, 1L, "speech-keyword", 60_000L, 75_000L,
                AudioFingerprintMatcher.Type.FULL_MATCHED));
        ui.actions.ignore();
        coordinator.onCandidate(candidate(1L, 1L, "speech-keyword", 60_000L, 75_000L,
                AudioFingerprintMatcher.Type.FULL_MATCHED));
        coordinator.onCandidate(candidate(1L, 1L, "speech-keyword", 900_000L, 915_000L,
                AudioFingerprintMatcher.Type.FULL_MATCHED));

        assertEquals(2, ui.candidateShows);
        assertEquals(915_000L, ui.lastPrompt.targetMediaMs());
    }

    private static AdSkipCoordinator.PlaybackSnapshot snapshot(
            long session, long generation, long position, long duration,
            boolean seekable, boolean live, PlaybackMediaClock.Snapshot clock) {
        return new AdSkipCoordinator.PlaybackSnapshot(
                session, generation, position, duration, seekable, live, clock);
    }

    private static PlaybackMediaClock.Snapshot freshClock(
            long generation, long anchor, long captured, long presented) {
        return new PlaybackMediaClock.Snapshot(generation, anchor, captured, presented, true, true);
    }

    private static AdAudioConsumer.Candidate candidate(
            long session, long generation, String ruleId, long start, long end,
            AudioFingerprintMatcher.Type type) {
        return new AdAudioConsumer.Candidate(session, generation,
                new AudioFingerprintMatcher.MatchEvent(type, ruleId, start, end, 0.9f, 6));
    }

    private static AdAudioSignalProvider.AdAudioCandidate providerCandidate(
            long session, long generation, String ruleId, long start, long end,
            boolean fullMatch) {
        return new AdAudioSignalProvider.AdAudioCandidate(
                session, generation, ruleId, "rules-v1", start, end,
                fullMatch, 0.9d, "pcm");
    }

    private static final class ManualTime implements LongSupplier {
        long now;

        @Override
        public long getAsLong() {
            return now;
        }
    }

    private static final class FakePlaybackPort implements AdSkipCoordinator.PlaybackPort {
        private AdSkipCoordinator.PlaybackSnapshot snapshot;
        private final List<Long> seekTargets = new ArrayList<>();

        FakePlaybackPort(AdSkipCoordinator.PlaybackSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public AdSkipCoordinator.PlaybackSnapshot snapshot(long sessionId, long generation) {
            return snapshot;
        }

        @Override
        public AdSkipCoordinator.SeekResult seekTo(long sessionId, long generation, long positionMs) {
            if (snapshot.sessionId() != sessionId || snapshot.generation() != generation) {
                return AdSkipCoordinator.SeekResult.rejected(sessionId, generation);
            }
            seekTargets.add(positionMs);
            long nextGeneration = generation == Long.MAX_VALUE ? 0L : generation + 1L;
            PlaybackMediaClock.Snapshot nextClock = new PlaybackMediaClock.Snapshot(
                    nextGeneration, snapshot.clock().mediaAnchorMs(), snapshot.clock().capturedUntilMs(),
                    snapshot.clock().presentedCaptureMs(), snapshot.clock().playing(), snapshot.clock().fresh());
            snapshot = new AdSkipCoordinator.PlaybackSnapshot(
                    snapshot.sessionId(), nextGeneration, positionMs, snapshot.durationMs(),
                    snapshot.seekable(), snapshot.live(), nextClock);
            return new AdSkipCoordinator.SeekResult(true, sessionId, nextGeneration);
        }
    }

    private static final class FakeUiPort implements AdSkipCoordinator.UiPort {
        private int candidateShows;
        private AdSkipCoordinator.Prompt lastPrompt;
        private AdSkipCoordinator.Actions actions;

        @Override
        public void showCandidate(AdSkipCoordinator.Prompt prompt, AdSkipCoordinator.Actions actions) {
            candidateShows++;
            lastPrompt = prompt;
            this.actions = actions;
        }

        @Override
        public void showUndo(AdSkipCoordinator.UndoPrompt prompt, AdSkipCoordinator.Actions actions) {
            this.actions = actions;
        }

        @Override
        public void dismiss(long sessionId) {
        }
    }
}
