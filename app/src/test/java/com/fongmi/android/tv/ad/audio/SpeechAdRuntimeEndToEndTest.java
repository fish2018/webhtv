package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.audio.PlaybackMediaClock;
import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;
import com.fongmi.android.tv.subtitle.SpeechRecognitionFactory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SpeechAdRuntimeEndToEndTest {

    @Test
    public void realHubAndRuntimeHonorPromptAutoUndoAndTimelineIsolation() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub);
        FakeUiPort ui = new FakeUiPort();
        FakeRecognitionFactory recognition = new FakeRecognitionFactory();
        AtomicReference<SpeechAdConfig> config = new AtomicReference<>(
                SpeechAdConfig.create(true, "赌场", 15, "PROMPT"));

        AdAudioRuntimeController runtime = new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L),
                SpeechAdRuntimeEndToEndTest::emptySnapshot, playback,
                Runnable::run, () -> { },
                ignored -> new NoopAdAudioSignalProvider("probe"),
                () -> new SpeechAdSignalProvider(
                        hub, recognition, config::get, Runnable::run,
                        new AdAudioDiagnostics()));

        runtime.setSpeechConfig(config.get());
        runtime.start(false);
        runtime.bindUi(ui);
        assertTrue(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        assertEquals(AdSkipPolicyController.Mode.PROMPT, runtime.skipMode());

        FakeRecognitionSession promptSession = recognition.current();
        hub.publishPcm(session.frame(new float[] {0.1f}, 16_000, 1_000L));
        promptSession.emit("欢迎来到赌场", promptSession.lastTimelineToken);

        assertEquals(1, ui.candidateShows);
        assertTrue(playback.seekTargets.isEmpty());
        ui.confirm();
        assertEquals(List.of(16_000L), playback.seekTargets);
        ui.undo();
        assertEquals(List.of(16_000L, 1_000L), playback.seekTargets);

        config.set(SpeechAdConfig.create(true, "赌场", 15, "AUTO"));
        runtime.setSpeechConfig(config.get());
        assertEquals(1, promptSession.closeCalls);
        assertEquals(AdSkipPolicyController.Mode.PROMPT, runtime.skipMode());

        promptSession.emit("赌场", promptSession.lastTimelineToken);
        assertEquals(List.of(16_000L, 1_000L), playback.seekTargets);
        assertEquals(1, ui.candidateShows);

        FakeRecognitionSession autoSession = recognition.current();
        hub.publishPcm(session.frame(new float[] {0.2f}, 16_000, 1_100L));
        autoSession.emit("赌场", autoSession.lastTimelineToken);

        assertEquals(1, ui.candidateShows);
        assertEquals(List.of(16_000L, 1_000L, 16_100L), playback.seekTargets);
        ui.undo();
        assertEquals(List.of(16_000L, 1_000L, 16_100L, 1_000L), playback.seekTargets);

        int staleTimelineToken = autoSession.lastTimelineToken;
        playback.positionMs = 30_000L;
        PlaybackMediaSignalHub.Session reset = hub.resetTimeline(
                30_000L, PlaybackMediaSignalHub.ResetReason.SEEK);
        assertEquals(1, autoSession.resetCalls);

        autoSession.emit("赌场", staleTimelineToken);
        assertEquals(4, playback.seekTargets.size());
        assertEquals(1, ui.candidateShows);

        runtime.refresh();
        // The recognizer is reused across the reset instead of being rebuilt; isolation
        // comes from the bumped timeline token plus session.reset(), not from a new session.
        FakeRecognitionSession resumedSession = recognition.current();
        assertTrue(autoSession == resumedSession);
        assertEquals(0, autoSession.closeCalls);
        assertEquals(1, autoSession.resetCalls);

        autoSession.emit("赌场", staleTimelineToken);
        assertEquals(4, playback.seekTargets.size());
        assertEquals(1, ui.candidateShows);

        hub.publishPcm(reset.frame(new float[] {0.3f}, 16_000, 30_000L));
        resumedSession.emit("赌场", resumedSession.lastTimelineToken);

        assertEquals(List.of(16_000L, 1_000L, 16_100L, 1_000L, 45_000L),
                playback.seekTargets);
        ui.undo();
        assertEquals(List.of(16_000L, 1_000L, 16_100L, 1_000L, 45_000L, 30_000L),
                playback.seekTargets);

        runtime.close();
        hub.close();
    }

    private static AdAudioRuleSnapshot emptySnapshot() {
        return new AdAudioRuleSnapshot(
                "test", "", AudioFingerprintRuleSet.empty(), List.of(), "");
    }

    private static final class FakePlaybackPort
            implements AdAudioRuntimeController.PlaybackPort {
        private final PlaybackMediaSignalHub hub;
        private final List<Long> seekTargets = new ArrayList<>();
        private long positionMs = 1_000L;

        private FakePlaybackPort(PlaybackMediaSignalHub hub) {
            this.hub = hub;
        }

        @Override
        public boolean isEligible(long sessionId, long generation) {
            PlaybackMediaSignalHub.Session current = hub.session();
            return current.id() == sessionId && current.generation() == generation;
        }

        @Override
        public AdSkipCoordinator.PlaybackSnapshot snapshot(
                long sessionId, long generation) {
            return new AdSkipCoordinator.PlaybackSnapshot(
                    sessionId, generation, positionMs, 120_000L, true, false,
                    new PlaybackMediaClock.Snapshot(
                            generation, 0L, positionMs, positionMs, true, true));
        }

        @Override
        public AdSkipCoordinator.SeekResult seekTo(
                long sessionId, long generation, long positionMs) {
            PlaybackMediaSignalHub.Session current = hub.session();
            boolean applied = current.id() == sessionId
                    && current.generation() == generation;
            if (applied) seekTargets.add(positionMs);
            return new AdSkipCoordinator.SeekResult(
                    applied, current.id(), current.generation());
        }
    }

    private static final class FakeUiPort implements AdSkipCoordinator.UiPort {
        private int candidateShows;
        private AdSkipCoordinator.Actions candidateActions;
        private AdSkipCoordinator.Actions undoActions;

        @Override
        public void showCandidate(
                AdSkipCoordinator.Prompt prompt,
                AdSkipCoordinator.Actions actions) {
            candidateShows++;
            candidateActions = actions;
        }

        @Override
        public void showUndo(
                AdSkipCoordinator.UndoPrompt prompt,
                AdSkipCoordinator.Actions actions) {
            undoActions = actions;
        }

        @Override
        public void dismiss(long sessionId) {
        }

        private void confirm() {
            candidateActions.confirm();
        }

        private void undo() {
            undoActions.undo();
        }
    }

    private static final class FakeRecognitionFactory
            implements SpeechRecognitionFactory {
        private final List<FakeRecognitionSession> sessions = new ArrayList<>();

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public Session create(Listener listener) {
            FakeRecognitionSession session = new FakeRecognitionSession(listener);
            sessions.add(session);
            return session;
        }

        private FakeRecognitionSession current() {
            return sessions.get(sessions.size() - 1);
        }
    }

    private static final class FakeRecognitionSession
            implements SpeechRecognitionFactory.Session {
        private final SpeechRecognitionFactory.Listener listener;
        private int lastTimelineToken;
        private long lastStartUs;
        private long lastEndUs;
        private int resetCalls;
        private int closeCalls;

        private FakeRecognitionSession(SpeechRecognitionFactory.Listener listener) {
            this.listener = listener;
        }

        @Override
        public void accept(float[] samples, long startUs, long endUs,
                           int timelineToken) {
            lastTimelineToken = timelineToken;
            lastStartUs = startUs;
            lastEndUs = endUs;
        }

        @Override
        public void reset() {
            resetCalls++;
        }

        @Override
        public void close() {
            closeCalls++;
        }

        /** Echoes the capture window of the last accepted frame, like the real recognizer. */
        private void emit(String text, int timelineToken) {
            listener.onResult(text, lastStartUs, lastEndUs, timelineToken);
        }
    }
}
