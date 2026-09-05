package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;
import com.fongmi.android.tv.subtitle.SpeechRecognitionFactory;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executor;

public class SpeechAdSignalProviderTest {

    @Test
    public void keywordMatchEmitsBoundedSpeechCandidate() {
        Fixture fixture = new Fixture(true, config(true, "\u8d4c\u573a", 15), Runnable::run, 8);
        fixture.host(10_000L, 20_000L, true, false);
        assertEquals(AdAudioSignalProvider.ProviderState.RUNNING, fixture.provider.state());
        assertTrue(fixture.hub.isCaptureRequested(
                PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));

        fixture.publish(new float[] {0.1f, 0.2f}, 8_000, 10_000L);
        FakeSession recognizer = fixture.factory.current();
        assertEquals(4, recognizer.acceptedSamples.get(0).length);
        recognizer.emit("\u6b22\u8fce\u6765\u5230\u8d4c\u573a", recognizer.lastTimelineToken());

        assertEquals(1, fixture.emitted.size());
        AdAudioSignalProvider.AdAudioCandidate candidate = fixture.emitted.get(0);
        assertEquals(SpeechAdSignalProvider.ID, candidate.providerId());
        assertEquals(SpeechAdSignalProvider.RULE_ID, candidate.ruleId());
        assertEquals("v1", candidate.ruleVersion());
        assertEquals(10_000L, candidate.startMs());
        assertEquals(25_000L, candidate.endMs());
        assertTrue(candidate.fullMatch());
        assertEquals(1.0d, candidate.similarity(), 0.0d);
        assertEquals(1L, fixture.diagnostics.count(AdAudioDiagnostics.Code.SPEECH_MATCHED));
        fixture.close();
    }

    @Test
    public void providerDoesNotRunWhenDisabledModelKeywordsOrVodClockAreInvalid() {
        Fixture missingModel = new Fixture(false, config(true, "\u8d4c\u573a", 15), Runnable::run, 8);
        missingModel.host(1_000L, 20_000L, true, false);
        assertEquals(AdAudioSignalProvider.ProviderState.DEGRADED, missingModel.provider.state());
        assertEquals(1L, missingModel.diagnostics.count(
                AdAudioDiagnostics.Code.SPEECH_MODEL_UNAVAILABLE));
        assertFalse(missingModel.hub.isCaptureRequested(
                PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        missingModel.close();

        Fixture disabled = new Fixture(true, config(false, "\u8d4c\u573a", 15), Runnable::run, 8);
        disabled.host(1_000L, 20_000L, true, false);
        assertEquals(AdAudioSignalProvider.ProviderState.DISABLED, disabled.provider.state());
        assertFalse(disabled.hub.isCaptureRequested(
                PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        disabled.close();

        Fixture emptyKeywords = new Fixture(true, config(true, "", 15), Runnable::run, 8);
        emptyKeywords.host(1_000L, 20_000L, true, false);
        assertNotEquals(AdAudioSignalProvider.ProviderState.RUNNING,
                emptyKeywords.provider.state());
        emptyKeywords.close();

        Fixture live = new Fixture(true, config(true, "\u8d4c\u573a", 15), Runnable::run, 8);
        live.host(1_000L, 20_000L, true, true);
        assertNotEquals(AdAudioSignalProvider.ProviderState.RUNNING, live.provider.state());
        live.close();

        Fixture unseekable = new Fixture(true, config(true, "\u8d4c\u573a", 15), Runnable::run, 8);
        unseekable.host(1_000L, 20_000L, false, false);
        assertNotEquals(AdAudioSignalProvider.ProviderState.RUNNING, unseekable.provider.state());
        unseekable.close();

        Fixture unknownDuration = new Fixture(true, config(true, "\u8d4c\u573a", 15), Runnable::run, 8);
        unknownDuration.host(1_000L, -1L, true, false);
        assertNotEquals(AdAudioSignalProvider.ProviderState.RUNNING,
                unknownDuration.provider.state());
        unknownDuration.close();
    }

    @Test
    public void resetDropsLateWorkAndContinuesWithTheSameRecognizerSession() {
        ManualExecutor worker = new ManualExecutor();
        Fixture fixture = new Fixture(true, config(true, "\u8d4c\u573a", 15), worker, 4);
        fixture.host(10_000L, 60_000L, true, false);
        FakeSession recognizer = fixture.factory.current();
        fixture.publish(new float[] {0.1f}, 16_000, 10_000L);
        worker.runAll();
        int oldTimelineToken = recognizer.lastTimelineToken();
        fixture.publish(new float[] {0.2f}, 16_000, 10_100L);

        PlaybackMediaSignalHub.Session resetSession = fixture.hub.resetTimeline(
                30_000L, PlaybackMediaSignalHub.ResetReason.SEEK);
        worker.runAll();
        recognizer.emit("\u8d4c\u573a", oldTimelineToken);

        assertTrue(fixture.emitted.isEmpty());
        assertEquals(1, recognizer.resetCalls);
        assertEquals(0, recognizer.closeCalls);
        assertEquals(1, recognizer.acceptedSamples.size());
        assertTrue(fixture.diagnostics.count(AdAudioDiagnostics.Code.STALE_GENERATION) >= 1L);
        assertTrue(fixture.diagnostics.count(
                AdAudioDiagnostics.Code.SPEECH_STALE_CALLBACK) >= 1L);
        assertTrue(fixture.hub.isCaptureRequested(
                PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));

        fixture.provider.onHostPosition(host(
                resetSession, 30_000L, 60_000L, true, false));
        fixture.hub.publishPcm(resetSession.frame(
                new float[] {0.3f}, 16_000, 30_000L));
        worker.runAll();
        assertEquals(2, recognizer.acceptedSamples.size());
        recognizer.emit("\u8d4c\u573a", recognizer.lastTimelineToken());

        assertEquals(1, fixture.emitted.size());
        assertEquals(resetSession.generation(), fixture.emitted.get(0).generation());
        assertEquals(30_000L, fixture.emitted.get(0).startMs());
        fixture.close();
    }
    @Test
    public void cooldownSuppressesPartialAndFinalDuplicates() {
        Fixture fixture = new Fixture(true, config(true, "\u8d4c\u573a", 15), Runnable::run, 8);
        fixture.host(10_000L, 60_000L, true, false);
        fixture.publish(new float[] {0.1f}, 16_000, 10_000L);
        FakeSession recognizer = fixture.factory.current();

        recognizer.emit("\u8d4c\u573a", recognizer.lastTimelineToken());
        fixture.host(20_000L, 60_000L, true, false);
        recognizer.emit("\u6b22\u8fce\u6765\u5230\u8d4c\u573a", recognizer.lastTimelineToken());

        assertEquals(1, fixture.emitted.size());
        assertEquals(1L, fixture.diagnostics.count(AdAudioDiagnostics.Code.SPEECH_COOLDOWN));
        fixture.close();
    }

    @Test
    public void candidateTracksSpokenMomentNotTheLastPublishedHostPosition() {
        Fixture fixture = new Fixture(true, config(true, "赌场", 15), Runnable::run, 8);
        // Host position is published once when playback becomes READY and is not pumped
        // again during steady playback.
        fixture.host(0L, 2_400_000L, true, false);

        // 15 minutes later the keyword is spoken; the capture clock knows this, the cached
        // host position does not.
        fixture.publish(new float[] {0.1f}, 16_000, 900_000L);
        FakeSession recognizer = fixture.factory.current();
        recognizer.emitAt("赌场", 900_000_000L, 901_000_000L, recognizer.lastTimelineToken());

        assertEquals(1, fixture.emitted.size());
        assertEquals(900_000L, fixture.emitted.get(0).startMs());
        assertEquals(915_000L, fixture.emitted.get(0).endMs());
        fixture.close();
    }

    @Test
    public void ineligiblePositionParksTheProviderWithoutTearingDownTheRecognizer() {
        Fixture fixture = new Fixture(true, config(true, "赌场", 15), Runnable::run, 8);
        fixture.host(10_000L, 20_000L, true, false);
        assertEquals(AdAudioSignalProvider.ProviderState.RUNNING, fixture.provider.state());
        FakeSession running = fixture.factory.current();

        // Buffering: duration momentarily unknown. This happens repeatedly during normal
        // playback, so it must not destroy the recognizer or drop the capture lease --
        // rebuilding sherpa on every buffering blip loses the in-flight utterance and
        // forces the audio pipeline to be re-attached.
        fixture.host(10_000L, -1L, false, false);

        assertEquals(AdAudioSignalProvider.ProviderState.IDLE, fixture.provider.state());
        assertEquals(0, running.closeCalls);
        assertEquals(1, fixture.factory.sessions.size());
        assertTrue(fixture.hub.isCaptureRequested(
                PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));

        // Recovering must resume the same session rather than start a new one.
        fixture.host(11_000L, 20_000L, true, false);
        assertEquals(AdAudioSignalProvider.ProviderState.RUNNING, fixture.provider.state());
        assertEquals(1, fixture.factory.sessions.size());
        fixture.close();
    }

    @Test
    public void mismatchedHostGenerationIsIgnoredBeforeActivation() {
        Fixture fixture = new Fixture(true, config(true, "\u8d4c\u573a", 15), Runnable::run, 8);

        fixture.provider.onHostPosition(new AdAudioSignalProvider.HostPosition(
                fixture.session.id(), fixture.session.generation() + 1L,
                1_000L, 20_000L, true, false));

        assertNotEquals(AdAudioSignalProvider.ProviderState.RUNNING, fixture.provider.state());
        assertEquals(1L, fixture.diagnostics.count(AdAudioDiagnostics.Code.STALE_GENERATION));
        fixture.host(1_000L, 20_000L, true, false);
        assertEquals(AdAudioSignalProvider.ProviderState.RUNNING, fixture.provider.state());
        fixture.close();
    }

    @Test
    public void candidateUsesCaptureTimeAndLeavesDurationClampingToTheCoordinator() {
        Fixture fixture = new Fixture(true, config(true, "\u8d4c\u573a", 120), Runnable::run, 8);
        fixture.host(59_500L, 60_000L, true, false);
        fixture.publish(new float[] {0.1f}, 16_000, 59_500L);
        FakeSession recognizer = fixture.factory.current();

        recognizer.emit("\u8d4c\u573a", recognizer.lastTimelineToken());

        // The provider reports the raw capture interval, exactly like the fingerprint
        // provider. AdSkipCoordinator.targetFor is the single place that clamps to
        // duration, so clamping here would double-apply it.
        assertEquals(1, fixture.emitted.size());
        assertEquals(59_500L, fixture.emitted.get(0).startMs());
        assertEquals(179_500L, fixture.emitted.get(0).endMs());
        fixture.close();
    }

    @Test
    public void factoryAndRecognizerFailuresAreIsolated() {
        Fixture createFailure = new Fixture(true, config(true, "\u8d4c\u573a", 15), Runnable::run, 8);
        createFailure.factory.throwOnCreate = true;
        createFailure.host(1_000L, 20_000L, true, false);
        assertEquals(AdAudioSignalProvider.ProviderState.DEGRADED,
                createFailure.provider.state());
        assertEquals(1L, createFailure.diagnostics.count(
                AdAudioDiagnostics.Code.SPEECH_START_FAILED));
        createFailure.close();

        Fixture acceptFailure = new Fixture(true, config(true, "\u8d4c\u573a", 15), Runnable::run, 8);
        acceptFailure.host(1_000L, 20_000L, true, false);
        acceptFailure.factory.current().throwOnAccept = true;
        acceptFailure.publish(new float[] {0.1f}, 16_000, 1_000L);
        acceptFailure.factory.current().fail();
        assertTrue(acceptFailure.diagnostics.count(AdAudioDiagnostics.Code.MATCHER_ERROR) >= 2L);
        acceptFailure.close();
    }

    @Test
    public void listenerExceptionsAreIsolatedWithoutLeakingRecognizedText() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        FakeRecognizerFactory factory = new FakeRecognizerFactory(true);
        AdAudioDiagnostics diagnostics = new AdAudioDiagnostics();
        SpeechAdSignalProvider provider = new SpeechAdSignalProvider(
                hub, factory, () -> config(true, "\u8d4c\u573a", 15), Runnable::run, diagnostics);
        AdAudioSignalProvider.Listener throwing = new AdAudioSignalProvider.Listener() {
            @Override
            public void onCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
                throw new IllegalStateException("listener failed");
            }

            @Override
            public void onProviderError(AdAudioSignalProvider.ProviderError error) {
                throw new IllegalStateException("listener failed");
            }

            @Override
            public void onTimelineReset(AdAudioSignalProvider.TimelineReset reset) {
                throw new IllegalStateException("listener failed");
            }
        };

        provider.setEnabled(true);
        provider.start(context(session), rules(), throwing);
        provider.onHostPosition(host(session, 1_000L, 20_000L, true, false));
        hub.publishPcm(session.frame(new float[] {0.1f}, 16_000, 1_000L));
        factory.current().emit("\u8d4c\u573a", factory.current().lastTimelineToken());
        provider.onTimelineReset(new AdAudioSignalProvider.TimelineReset(
                session.id(), session.generation() + 1L,
                AdAudioSignalProvider.ResetReason.SEEK, 2_000L));

        assertTrue(diagnostics.count(AdAudioDiagnostics.Code.MATCHER_ERROR) >= 2L);
        provider.close();
        hub.close();
    }

    @Test
    public void mailboxEvictsOldestPcmAndKeepsNewestFrames() {
        ManualExecutor worker = new ManualExecutor();
        Fixture fixture = new Fixture(true, config(true, "\u8d4c\u573a", 15), worker, 2);
        fixture.host(1_000L, 20_000L, true, false);

        fixture.publish(new float[] {1.0f}, 16_000, 1_000L);
        fixture.publish(new float[] {2.0f}, 16_000, 1_100L);
        fixture.publish(new float[] {3.0f}, 16_000, 1_200L);
        assertEquals(1L, fixture.diagnostics.count(AdAudioDiagnostics.Code.QUEUE_OVERFLOW));
        worker.runAll();

        FakeSession recognizer = fixture.factory.current();
        assertEquals(2, recognizer.acceptedSamples.size());
        assertEquals(2.0f, recognizer.acceptedSamples.get(0)[0], 0.0f);
        assertEquals(3.0f, recognizer.acceptedSamples.get(1)[0], 0.0f);
        fixture.close();
    }

    @Test
    public void emptyAndNonMatchingTextDoNotEmitCandidates() {
        Fixture fixture = new Fixture(true, config(true, "\u8d4c\u573a", 15), Runnable::run, 8);
        fixture.host(1_000L, 20_000L, true, false);
        fixture.publish(new float[] {0.1f}, 16_000, 1_000L);
        FakeSession recognizer = fixture.factory.current();

        recognizer.emit("   ", recognizer.lastTimelineToken());
        recognizer.emit("\u6b22\u8fce\u6536\u770b\u8282\u76ee", recognizer.lastTimelineToken());

        assertTrue(fixture.emitted.isEmpty());
        assertEquals(1L, fixture.diagnostics.count(AdAudioDiagnostics.Code.SPEECH_TEXT_EMPTY));
        fixture.close();
    }

    @Test
    public void closeIsIdempotentAndReleasesRegistrationLeaseAndSession() {
        Fixture fixture = new Fixture(true, config(true, "\u8d4c\u573a", 15), Runnable::run, 8);
        fixture.host(1_000L, 20_000L, true, false);
        FakeSession recognizer = fixture.factory.current();
        recognizer.throwOnClose = true;

        fixture.provider.close();
        fixture.provider.close();

        assertEquals(AdAudioSignalProvider.ProviderState.CLOSED, fixture.provider.state());
        assertEquals(1, recognizer.closeCalls);
        assertFalse(fixture.hub.isCaptureRequested(
                PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        PlaybackMediaSignalHub.Registration replacement = fixture.hub.register(
                "speech-ad", Runnable::run, 1, new PlaybackMediaSignalHub.Consumer() {});
        assertNotNull(replacement);
        replacement.close();
        fixture.hub.close();
    }

    private static SpeechAdConfig config(boolean enabled, String keywords, int skipSeconds) {
        return SpeechAdConfig.create(enabled, keywords, skipSeconds, "PROMPT");
    }

    private static AdAudioSignalProvider.SessionContext context(
            PlaybackMediaSignalHub.Session session) {
        return new AdAudioSignalProvider.SessionContext(
                session.id(), session.generation(), "media", "https://example.test/video",
                Map.of());
    }

    private static AdAudioSignalProvider.HostPosition host(
            PlaybackMediaSignalHub.Session session, long positionMs, long durationMs,
            boolean seekable, boolean live) {
        return new AdAudioSignalProvider.HostPosition(
                session.id(), session.generation(), positionMs, durationMs, seekable, live);
    }

    private static AdAudioRuleSnapshot rules() {
        return new AdAudioRuleSnapshot(
                "test", "v1", AudioFingerprintRuleSet.empty(), List.of(), "");
    }

    private static AdAudioSignalProvider.Listener listener(
            List<AdAudioSignalProvider.AdAudioCandidate> emitted,
            List<AdAudioSignalProvider.ProviderError> errors,
            List<AdAudioSignalProvider.TimelineReset> resets) {
        return new AdAudioSignalProvider.Listener() {
            @Override
            public void onCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
                emitted.add(candidate);
            }

            @Override
            public void onProviderError(AdAudioSignalProvider.ProviderError error) {
                errors.add(error);
            }

            @Override
            public void onTimelineReset(AdAudioSignalProvider.TimelineReset reset) {
                resets.add(reset);
            }
        };
    }

    private static final class Fixture implements AutoCloseable {
        private final PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        private final PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        private final FakeRecognizerFactory factory;
        private final AdAudioDiagnostics diagnostics = new AdAudioDiagnostics();
        private final List<AdAudioSignalProvider.AdAudioCandidate> emitted = new ArrayList<>();
        private final List<AdAudioSignalProvider.ProviderError> errors = new ArrayList<>();
        private final List<AdAudioSignalProvider.TimelineReset> resets = new ArrayList<>();
        private final SpeechAdSignalProvider provider;

        private Fixture(boolean modelReady, SpeechAdConfig config,
                        Executor worker, int mailboxCapacity) {
            factory = new FakeRecognizerFactory(modelReady);
            provider = new SpeechAdSignalProvider(
                    hub, factory, () -> config, mailboxCapacity, worker, diagnostics);
            provider.setEnabled(true);
            provider.start(context(session), rules(), listener(emitted, errors, resets));
        }

        private void host(long positionMs, long durationMs,
                          boolean seekable, boolean live) {
            provider.onHostPosition(SpeechAdSignalProviderTest.host(
                    session, positionMs, durationMs, seekable, live));
        }

        private void publish(float[] samples, int sampleRate, long startMs) {
            hub.publishPcm(session.frame(samples, sampleRate, startMs));
        }

        @Override
        public void close() {
            provider.close();
            hub.close();
        }
    }

    private static final class FakeRecognizerFactory implements SpeechRecognitionFactory {
        private final boolean ready;
        private final List<FakeSession> sessions = new ArrayList<>();
        private boolean throwOnCreate;

        private FakeRecognizerFactory(boolean ready) {
            this.ready = ready;
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public Session create(Listener listener) {
            if (throwOnCreate) throw new IllegalStateException("create failed");
            FakeSession session = new FakeSession(listener);
            sessions.add(session);
            return session;
        }

        private FakeSession current() {
            assertFalse(sessions.isEmpty());
            return sessions.get(sessions.size() - 1);
        }
    }

    private static final class FakeSession implements SpeechRecognitionFactory.Session {
        private final SpeechRecognitionFactory.Listener listener;
        private final List<float[]> acceptedSamples = new ArrayList<>();
        private int lastTimelineToken;
        private long lastStartUs;
        private long lastEndUs;
        private int resetCalls;
        private int closeCalls;
        private boolean throwOnAccept;
        private boolean throwOnClose;

        private FakeSession(SpeechRecognitionFactory.Listener listener) {
            this.listener = listener;
        }

        @Override
        public void accept(float[] samples, long startUs, long endUs, int timelineToken) {
            if (throwOnAccept) throw new IllegalStateException("accept failed");
            acceptedSamples.add(samples.clone());
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
            if (throwOnClose) throw new IllegalStateException("close failed");
        }

        private int lastTimelineToken() {
            return lastTimelineToken;
        }

        /** Echoes the capture window of the last accepted frame, like the real recognizer. */
        private void emit(String text, int timelineToken) {
            listener.onResult(text, lastStartUs, lastEndUs, timelineToken);
        }

        private void emitAt(String text, long startUs, long endUs, int timelineToken) {
            listener.onResult(text, startUs, endUs, timelineToken);
        }

        private void fail() {
            listener.onError(new IllegalStateException("recognizer failed"));
        }
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) task.run();
        }
    }
}
