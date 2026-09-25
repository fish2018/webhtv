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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
        assertEquals(10_000_000L, recognizer.lastStartUs);
        assertEquals(10_000_250L, recognizer.lastEndUs);
        assertEquals(SpeechRecognitionFactory.ExecutionProfile.AD_AUDIO,
                fixture.factory.lastProfile);
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
    public void compoundRuleUsesRecognitionEndAndRemainsAnApproximateCandidate() {
        SpeechAdConfig config = SpeechAdConfig.createWithRules(
                true, "", "广告之后*>马上回来，[2,30]", 15, "AUTO");
        Fixture fixture = new Fixture(config, Runnable::run, 8);
        fixture.host(1_000L, 60_000L, true, false);
        fixture.publish(new float[] {0.1f}, 16_000, 10_000L);
        FakeSession recognizer = fixture.factory.current();

        recognizer.emitAt("广告", 10_000_000L, 11_000_000L,
                recognizer.lastTimelineToken);
        recognizer.emitAt("之后", 12_000_000L, 13_000_000L,
                recognizer.lastTimelineToken);
        recognizer.emitAt("马上回来", 14_000_000L, 15_000_000L,
                recognizer.lastTimelineToken);

        assertEquals(1, fixture.emitted.size());
        AdAudioSignalProvider.AdAudioCandidate candidate = fixture.emitted.get(0);
        assertEquals(SpeechAdRuleCodec.parse("广告之后*>马上回来，[2,30]").rules().get(0).id(),
                candidate.ruleId());
        assertEquals(8_000L, candidate.startMs());
        assertEquals(45_000L, candidate.endMs());
        assertFalse(candidate.fullMatch());
        assertEquals(1L, fixture.diagnostics.count(AdAudioDiagnostics.Code.SPEECH_MATCHED));
        fixture.close();
    }

    @Test
    public void compoundRuleResetsOnTimelineAndDoesNotJoinStaleText() {
        SpeechAdConfig config = SpeechAdConfig.createWithRules(
                true, "", "广告>回来,30", 15, "PROMPT");
        Fixture fixture = new Fixture(config, Runnable::run, 8);
        fixture.host(1_000L, 60_000L, true, false);
        fixture.publish(new float[] {0.1f}, 16_000, 10_000L);
        FakeSession recognizer = fixture.factory.current();
        int staleTimeline = recognizer.lastTimelineToken;
        recognizer.emitAt("广告", 10_000_000L, 11_000_000L, staleTimeline);

        PlaybackMediaSignalHub.Session reset = fixture.hub.resetTimeline(
                20_000L, PlaybackMediaSignalHub.ResetReason.SEEK);
        fixture.provider.onHostPosition(host(reset, 20_000L, 60_000L, true, false));
        recognizer.emitAt("回来", 12_000_000L, 13_000_000L, staleTimeline);
        assertTrue(fixture.emitted.isEmpty());

        fixture.hub.publishPcm(reset.frame(new float[] {0.1f}, 16_000, 20_000L));
        int currentTimeline = recognizer.lastTimelineToken;
        recognizer.emitAt("广告", 20_000_000L, 21_000_000L, currentTimeline);
        recognizer.emitAt("回来", 22_000_000L, 23_000_000L, currentTimeline);
        assertEquals(1, fixture.emitted.size());
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
    public void modelReadinessRunsOnOwnerInsteadOfHostPositionCallback() {
        ManualExecutor worker = new ManualExecutor();
        Fixture fixture = new Fixture(true, config(true, "赌场", 15), worker, 8);

        fixture.host(1_000L, 20_000L, true, false);

        assertEquals(0, fixture.factory.readyCalls);
        assertTrue(fixture.factory.sessions.isEmpty());
        worker.runAll();
        assertEquals(1, fixture.factory.readyCalls);
        assertEquals(1, fixture.factory.sessions.size());
        fixture.close();
    }

    @Test
    public void resetDropsLateWorkAndContinuesWithTheSameRecognizerSession() {
        ManualExecutor worker = new ManualExecutor();
        Fixture fixture = new Fixture(true, config(true, "\u8d4c\u573a", 15), worker, 4);
        fixture.host(10_000L, 60_000L, true, false);
        // Session creation is an owner-worker command in Phase 2A.
        worker.runAll();
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
        worker.runAll();

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
        worker.runAll();

        fixture.publish(new float[] {1.0f}, 16_000, 1_000L);
        fixture.publish(new float[] {2.0f}, 16_000, 1_100L);
        fixture.publish(new float[] {3.0f}, 16_000, 1_200L);
        assertEquals(1L, fixture.diagnostics.count(AdAudioDiagnostics.Code.QUEUE_OVERFLOW));
        worker.runAll();

        FakeSession recognizer = fixture.factory.current();
        assertEquals(2, recognizer.acceptedSamples.size());
        assertEquals(2.0f, recognizer.acceptedSamples.get(0)[0], 0.0f);
        assertEquals(3.0f, recognizer.acceptedSamples.get(1)[0], 0.0f);
        assertEquals(1, recognizer.resetCalls);
        fixture.close();
    }

    @Test(timeout = 10_000)
    public void slowAcceptDoesNotBlockResetOrCloseAndNativeCallsRemainSerialized()
            throws Exception {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        FakeRecognizerFactory factory = new FakeRecognizerFactory(true);
        ExecutorService owner = Executors.newSingleThreadExecutor();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        AdAudioDiagnostics diagnostics = new AdAudioDiagnostics();
        List<AdAudioSignalProvider.AdAudioCandidate> emitted = new ArrayList<>();
        SpeechAdSignalProvider provider = new SpeechAdSignalProvider(
                hub, factory, () -> config(true, "赌场", 15), owner, diagnostics);
        FakeSession recognizer = null;
        try {
            provider.setEnabled(true);
            provider.start(context(session), rules(), listener(
                    emitted, new ArrayList<>(), new ArrayList<>()));
            provider.onHostPosition(host(session, 1_000L, 20_000L, true, false));
            owner.submit(() -> { }).get(2, TimeUnit.SECONDS);
            recognizer = factory.current();
            recognizer.blockAccept();

            caller.submit(() -> hub.publishPcm(session.frame(new float[] {0.1f}, 16_000, 1_000L)))
                    .get(2, TimeUnit.SECONDS);
            assertTrue(recognizer.acceptStarted.await(2, TimeUnit.SECONDS));
            caller.submit(() -> {
                provider.onHostPosition(host(session, 1_200L, 20_000L, true, false));
                hub.publishPcm(session.frame(new float[] {0.2f}, 16_000, 1_200L));
                hub.resetTimeline(2_000L, PlaybackMediaSignalHub.ResetReason.SEEK);
            }).get(2, TimeUnit.SECONDS);
            int staleToken = recognizer.lastTimelineToken;
            recognizer.emit("赌场", staleToken);
            assertEquals(0, recognizer.resetCalls);
            recognizer.allowAccept.countDown();
            owner.submit(() -> { }).get(2, TimeUnit.SECONDS);
            assertEquals(1, recognizer.resetCalls);
            assertFalse(recognizer.concurrentNativeCall.get());
            assertTrue(emitted.isEmpty());

            PlaybackMediaSignalHub.Session resetSession = hub.session();
            provider.onHostPosition(host(resetSession, 2_000L, 20_000L, true, false));
            recognizer.blockAccept();
            caller.submit(() -> hub.publishPcm(resetSession.frame(new float[] {0.3f}, 16_000, 2_000L)))
                    .get(2, TimeUnit.SECONDS);
            assertTrue(recognizer.acceptStarted.await(2, TimeUnit.SECONDS));
            caller.submit(provider::close).get(2, TimeUnit.SECONDS);
            owner.shutdown(); // Exact production ordering: cleanup is still inside the owner.
            assertEquals(0, recognizer.closeCalls);
            recognizer.emit("赌场", recognizer.lastTimelineToken);
            recognizer.allowAccept.countDown();
            assertTrue(owner.awaitTermination(2, TimeUnit.SECONDS));
            assertFalse(recognizer.concurrentNativeCall.get());
            assertEquals(1, recognizer.closeCalls);
            assertTrue(emitted.isEmpty());
            assertFalse(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
            assertEquals(1L, diagnostics.count(AdAudioDiagnostics.Code.SPEECH_CLOSE_PENDING));
            assertEquals(1L, diagnostics.count(AdAudioDiagnostics.Code.SPEECH_CLOSED));
            assertEquals(0L, diagnostics.count(AdAudioDiagnostics.Code.SPEECH_OWNER_REJECTED));
        } finally {
            if (recognizer != null && recognizer.allowAccept != null) recognizer.allowAccept.countDown();
            provider.close();
            owner.shutdown();
            caller.shutdownNow();
            owner.awaitTermination(2, TimeUnit.SECONDS);
            hub.close();
        }
    }

    @Test(timeout = 10_000)
    public void closeFinishesPendingSubmissionBeforeTheRuntimeCanShutdownTheOwner()
            throws Exception {
        ExecutorService owner = Executors.newSingleThreadExecutor();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        AtomicBoolean pauseSubmission = new AtomicBoolean();
        CountDownLatch submitting = new CountDownLatch(1);
        CountDownLatch allowSubmission = new CountDownLatch(1);
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch closeReturned = new CountDownLatch(1);
        Executor handoff = command -> {
            if (pauseSubmission.getAndSet(false)) {
                submitting.countDown();
                try {
                    if (!allowSubmission.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("submission timed out");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
            }
            owner.execute(command);
        };
        Fixture fixture = new Fixture(true, config(true, "赌场", 15), handoff, 4);
        try {
            fixture.host(1_000L, 20_000L, true, false);
            owner.submit(() -> { }).get(2, TimeUnit.SECONDS);
            FakeSession session = fixture.factory.current();
            pauseSubmission.set(true);
            var publish = callers.submit(() -> fixture.publish(new float[] {0.1f}, 16_000, 1_000L));
            assertTrue(submitting.await(2, TimeUnit.SECONDS));
            var close = callers.submit(() -> {
                closeEntered.countDown();
                fixture.provider.close();
                owner.shutdown();
                closeReturned.countDown();
            });
            assertTrue(closeEntered.await(2, TimeUnit.SECONDS));
            // Native isn't running here: the only pending work is the executor handoff.
            assertFalse(closeReturned.await(200, TimeUnit.MILLISECONDS));
            assertFalse(owner.isShutdown());
            allowSubmission.countDown();
            publish.get(2, TimeUnit.SECONDS);
            close.get(2, TimeUnit.SECONDS);
            assertTrue(owner.awaitTermination(2, TimeUnit.SECONDS));
            assertEquals(1, session.closeCalls);
            assertFalse(fixture.hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
            assertEquals(0L, fixture.diagnostics.count(AdAudioDiagnostics.Code.SPEECH_OWNER_REJECTED));
        } finally {
            allowSubmission.countDown();
            fixture.close();
            owner.shutdown();
            callers.shutdownNow();
            owner.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    public void pcmGapRejectsInFlightTextAndResetsBeforeJoiningNewAudio() {
        ManualExecutor owner = new ManualExecutor();
        Fixture fixture = new Fixture(true, config(true, "赌场", 15), owner, 2);
        fixture.host(1_000L, 20_000L, true, false);
        owner.runAll();
        FakeSession recognizer = fixture.factory.current();
        recognizer.joinSpeech = true;
        fixture.publish(new float[] {1f}, 16_000, 1_000L); // 赌
        owner.runAll();
        int previousTimeline = recognizer.lastTimelineToken;
        fixture.publish(new float[] {0f}, 16_000, 1_100L);
        fixture.publish(new float[] {0f}, 16_000, 1_200L);
        fixture.publish(new float[] {3f}, 16_000, 1_300L); // 场, after a dropped frame
        recognizer.emitAt("赌场", 1_000_000L, 1_300_000L, previousTimeline);
        owner.runAll();
        assertTrue(fixture.emitted.isEmpty());
        assertEquals(1, recognizer.resetCalls);
        assertNotEquals(previousTimeline, recognizer.lastTimelineToken);
        assertEquals(2L, fixture.diagnostics.count(AdAudioDiagnostics.Code.SPEECH_PCM_QUEUE_PEAK));
        fixture.close();
    }

    @Test
    public void resetStormIsCoalescedAndRejectedInputDoesNotEnterNative() {
        ManualExecutor owner = new ManualExecutor();
        Fixture fixture = new Fixture(true, config(true, "赌场", 15), owner, 2);
        fixture.host(1_000L, 20_000L, true, false);
        owner.runAll();
        FakeSession recognizer = fixture.factory.current();
        for (int i = 0; i < 1_000; i++) fixture.hub.resetTimeline(
                2_000L, PlaybackMediaSignalHub.ResetReason.SEEK);
        assertEquals(1, owner.tasks.size());
        owner.runAll();
        assertEquals(1, recognizer.resetCalls);
        fixture.provider.onHostPosition(host(fixture.hub.session(), 2_000L, 20_000L, true, false));
        fixture.hub.publishPcm(fixture.hub.session().frame(new float[192_001], 16_000, 2_000L));
        owner.runAll();
        assertTrue(recognizer.acceptedSamples.isEmpty());
        assertEquals(1L, fixture.diagnostics.count(AdAudioDiagnostics.Code.SPEECH_PCM_DROPPED));
        fixture.close();
    }

    @Test
    public void queuedResultsAreBoundedAndInvalidatedOnClose() {
        ManualExecutor owner = new ManualExecutor();
        Fixture fixture = new Fixture(true, config(true, "赌场", 15), owner, 2);
        fixture.host(1_000L, 20_000L, true, false);
        owner.runAll();
        fixture.publish(new float[] {0.1f}, 16_000, 1_000L);
        owner.runAll();
        FakeSession recognizer = fixture.factory.current();
        for (int i = 0; i < 20; i++) recognizer.emit("赌场", recognizer.lastTimelineToken);
        assertEquals(12L, fixture.diagnostics.count(AdAudioDiagnostics.Code.QUEUE_OVERFLOW));
        fixture.provider.close();
        owner.runAll();
        assertTrue(fixture.emitted.isEmpty());
        assertEquals(1, recognizer.closeCalls);
        fixture.close();
    }

    @Test
    public void executorRejectionDegradesWithoutRunningNativeOnTheCaller() {
        Executor rejecting = command -> { throw new java.util.concurrent.RejectedExecutionException(); };
        Fixture fixture = new Fixture(true, config(true, "赌场", 15), rejecting, 2);
        fixture.host(1_000L, 20_000L, true, false);
        assertEquals(AdAudioSignalProvider.ProviderState.DEGRADED, fixture.provider.state());
        assertEquals(0, fixture.factory.readyCalls);
        assertTrue(fixture.factory.sessions.isEmpty());
        assertFalse(fixture.hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        assertEquals(1L, fixture.diagnostics.count(AdAudioDiagnostics.Code.SPEECH_OWNER_REJECTED));
        fixture.close();
    }

    @Test
    public void failedResetStopsThisProviderInsteadOfJoiningAcrossTheGap() {
        Fixture fixture = new Fixture(true, config(true, "赌场", 15), Runnable::run, 2);
        fixture.host(1_000L, 20_000L, true, false);
        FakeSession recognizer = fixture.factory.current();
        recognizer.throwOnReset = true;
        PlaybackMediaSignalHub.Session reset = fixture.hub.resetTimeline(
                2_000L, PlaybackMediaSignalHub.ResetReason.SEEK);
        fixture.provider.onHostPosition(host(reset, 2_000L, 20_000L, true, false));
        fixture.hub.publishPcm(reset.frame(new float[] {1f}, 16_000, 2_000L));
        assertEquals(AdAudioSignalProvider.ProviderState.DEGRADED, fixture.provider.state());
        assertEquals(1, recognizer.closeCalls);
        assertTrue(recognizer.acceptedSamples.isEmpty());
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
        private final Executor worker;

        private Fixture(boolean modelReady, SpeechAdConfig config,
                        Executor worker, int mailboxCapacity) {
            this.worker = worker;
            factory = new FakeRecognizerFactory(modelReady);
            provider = new SpeechAdSignalProvider(
                    hub, factory, () -> config, mailboxCapacity, worker, diagnostics);
            provider.setEnabled(true);
            provider.start(context(session), rules(), listener(emitted, errors, resets));
        }

        private Fixture(SpeechAdConfig config, Executor worker, int mailboxCapacity) {
            this.worker = worker;
            factory = new FakeRecognizerFactory(true);
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
            if (worker instanceof ManualExecutor manual) manual.runAll();
            hub.close();
        }
    }

    private static final class FakeRecognizerFactory implements SpeechRecognitionFactory {
        private final boolean ready;
        private final List<FakeSession> sessions = new ArrayList<>();
        private final CountDownLatch sessionCreated = new CountDownLatch(1);
        private int readyCalls;
        private SpeechRecognitionFactory.ExecutionProfile lastProfile;
        private boolean throwOnCreate;

        private FakeRecognizerFactory(boolean ready) {
            this.ready = ready;
        }

        @Override
        public boolean isReady() {
            readyCalls++;
            return ready;
        }

        @Override
        public Session create(Listener listener) {
            return create(listener, SpeechRecognitionFactory.ExecutionProfile.SUBTITLE);
        }

        @Override
        public Session create(Listener listener, ExecutionProfile profile) {
            if (throwOnCreate) throw new IllegalStateException("create failed");
            lastProfile = profile;
            FakeSession session = new FakeSession(listener);
            sessions.add(session);
            sessionCreated.countDown();
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
        private boolean throwOnReset;
        private boolean joinSpeech;
        private final StringBuilder utterance = new StringBuilder();
        private CountDownLatch acceptStarted;
        private CountDownLatch allowAccept;
        private final CountDownLatch closeFinished = new CountDownLatch(1);
        private final AtomicBoolean concurrentNativeCall = new AtomicBoolean();
        private volatile boolean nativeAccepting;

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
            if (joinSpeech) {
                if (samples[0] == 1f) utterance.append("赌");
                if (samples[0] == 3f) {
                    utterance.append("场");
                    listener.onResult(utterance.toString(), startUs, endUs, timelineToken);
                }
            }
            if (acceptStarted != null) {
                nativeAccepting = true;
                acceptStarted.countDown();
                try {
                    allowAccept.await();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                } finally {
                    nativeAccepting = false;
                }
            }
        }

        @Override
        public void reset() {
            if (nativeAccepting) concurrentNativeCall.set(true);
            resetCalls++;
            if (throwOnReset) throw new IllegalStateException("reset failed");
            utterance.setLength(0);
        }

        @Override
        public void close() {
            if (nativeAccepting) concurrentNativeCall.set(true);
            closeCalls++;
            if (throwOnClose) throw new IllegalStateException("close failed");
            closeFinished.countDown();
        }

        private void blockAccept() {
            acceptStarted = new CountDownLatch(1);
            allowAccept = new CountDownLatch(1);
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
