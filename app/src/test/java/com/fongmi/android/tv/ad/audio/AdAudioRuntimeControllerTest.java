package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.audio.PlaybackMediaClock;
import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;
import com.fongmi.android.tv.subtitle.SpeechRecognitionFactory;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AdAudioRuntimeControllerTest {

    @Test
    public void enabledRulesRequestCaptureOnlyWhileEligibleUiIsBound() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        AdAudioRuntimeController runtime = runtime(hub, playback, goodSnapshot());

        runtime.start(true);
        assertFalse(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        runtime.bindUi(new FakeUiPort());
        assertTrue(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        assertTrue(runtime.needsPipelineRebuild());

        runtime.unbindUi();
        assertFalse(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        runtime.close();
    }

    @Test
    public void disabledEmptyAndIneligibleConfigurationsNeverCapture() {
        PlaybackMediaSignalHub disabledHub = new PlaybackMediaSignalHub(8);
        disabledHub.beginSession(0L);
        AdAudioRuntimeController disabled = runtime(
                disabledHub, new FakePlaybackPort(disabledHub, true), goodSnapshot());
        disabled.start(false);
        disabled.bindUi(new FakeUiPort());

        PlaybackMediaSignalHub emptyHub = new PlaybackMediaSignalHub(8);
        emptyHub.beginSession(0L);
        AdAudioRuntimeController empty = runtime(
                emptyHub, new FakePlaybackPort(emptyHub, true), emptySnapshot());
        empty.start(true);
        empty.bindUi(new FakeUiPort());

        PlaybackMediaSignalHub ineligibleHub = new PlaybackMediaSignalHub(8);
        ineligibleHub.beginSession(0L);
        AdAudioRuntimeController ineligible = runtime(
                ineligibleHub, new FakePlaybackPort(ineligibleHub, false), goodSnapshot());
        ineligible.start(true);
        ineligible.bindUi(new FakeUiPort());

        assertFalse(disabledHub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        assertFalse(emptyHub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        assertFalse(ineligibleHub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        disabled.close();
        empty.close();
        ineligible.close();
    }

    @Test
    public void refreshReleasesCaptureWhenPlaybackBecomesLive() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        AdAudioRuntimeController runtime = runtime(hub, playback, goodSnapshot());
        runtime.start(true);
        runtime.bindUi(new FakeUiPort());
        assertTrue(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));

        playback.eligible = false;
        runtime.refresh();

        assertFalse(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        runtime.close();
    }

    @Test
    public void ruleSnapshotErrorIsReportedAndCaptureStaysDisabled() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        AdAudioRuntimeController runtime = runtime(
                hub, new FakePlaybackPort(hub, true), errorSnapshot());

        runtime.start(true);
        runtime.bindUi(new FakeUiPort());

        assertEquals(1L, runtime.diagnostics().count(
                AdAudioDiagnostics.Code.RULE_LOAD_FAILED));
        assertFalse(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        runtime.close();
    }

    @Test
    public void nullRuleSnapshotIsReportedAndCaptureStaysDisabled() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        AdAudioRuntimeController runtime = new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L), () -> null, playback,
                Runnable::run, () -> { });

        runtime.start(true);
        runtime.bindUi(new FakeUiPort());

        assertEquals(1L, runtime.diagnostics().count(
                AdAudioDiagnostics.Code.RULE_LOAD_FAILED));
        assertFalse(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        runtime.close();
    }

    @Test
    public void reloadRulesReplacesTheActiveMatcher() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        PlaybackMediaSignalHub.Session firstSession = hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        MutableRuleSource rules = new MutableRuleSource(snapshotForRule("old-ad"));
        AdAudioRuntimeController runtime = new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L), rules, playback,
                Runnable::run, () -> { });
        FakeUiPort ui = new FakeUiPort();
        runtime.start(true);
        runtime.bindUi(ui);

        rules.snapshot = snapshotForRule("new-ad");
        runtime.reloadRules();
        PlaybackMediaSignalHub.Session nextSession = hub.beginSession(0L);
        hub.publishPcm(nextSession.frame(chirp16k(), 16_000, 0L));

        assertEquals("new-ad", ui.lastPrompt.ruleId());
        assertTrue(firstSession.id() != nextSession.id());
        runtime.close();
    }

    @Test
    public void repeatedStartReplacesTheActiveMatcher() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        MutableRuleSource rules = new MutableRuleSource(snapshotForRule("old-ad"));
        AdAudioRuntimeController runtime = new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L), rules, playback,
                Runnable::run, () -> { });
        FakeUiPort ui = new FakeUiPort();
        runtime.start(true);
        runtime.bindUi(ui);

        rules.snapshot = snapshotForRule("new-ad");
        runtime.start(true);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        hub.publishPcm(session.frame(chirp16k(), 16_000, 0L));

        assertEquals("new-ad", ui.lastPrompt.ruleId());
        runtime.close();
    }

    @Test
    public void probeProviderStartsOnlyWhenSnapshotHasVerifiedSidecar() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        FakeSignalProvider probe = new FakeSignalProvider("probe");
        AtomicInteger factoryCalls = new AtomicInteger();
        AdAudioRuntimeController runtime = runtimeWithProbe(
                hub, playback, snapshotForRuleWithSidecar("ad"), sidecar -> {
                    factoryCalls.incrementAndGet();
                    return probe;
                });

        runtime.start(true);
        runtime.bindUi(new FakeUiPort());

        assertEquals(1, factoryCalls.get());
        assertEquals(1, probe.starts);
        assertEquals(AdAudioSignalProvider.ProviderState.RUNNING, probe.state());
        runtime.close();

        PlaybackMediaSignalHub noSidecarHub = new PlaybackMediaSignalHub(8);
        noSidecarHub.beginSession(0L);
        AdAudioRuntimeController noSidecar = runtimeWithProbe(
                noSidecarHub, new FakePlaybackPort(noSidecarHub, true),
                snapshotForRule("ad"), sidecar -> {
                    factoryCalls.incrementAndGet();
                    return new FakeSignalProvider("unexpected");
                });
        noSidecar.start(true);
        noSidecar.bindUi(new FakeUiPort());

        assertEquals(1, factoryCalls.get());
        noSidecar.close();
    }

    @Test
    public void duplicateProbeCandidatesReachThePromptOnlyOnce() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        FakeSignalProvider probe = new FakeSignalProvider("probe");
        AdAudioRuntimeController runtime = runtimeWithProbe(
                hub, playback, snapshotForRuleWithSidecar("ad"), sidecar -> probe);
        FakeUiPort ui = new FakeUiPort();
        runtime.start(true);
        runtime.bindUi(ui);
        AdAudioSignalProvider.AdAudioCandidate candidate = providerCandidate(
                session, "ad", 0L, 10_000L);

        probe.emit(candidate);
        probe.emit(candidate);

        assertEquals(1, ui.candidateShows);
        assertEquals(AdSkipPolicyController.Mode.PROMPT, runtime.skipMode());
        runtime.close();
    }

    @Test
    public void switchingToAutoDuringPlaybackAppliesOnlyLaterProbeCandidates() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        FakeSignalProvider probe = new FakeSignalProvider("probe");
        AdAudioRuntimeController runtime = runtimeWithProbe(
                hub, playback, snapshotForRulesWithSidecar(
                        "prompt-ad", "auto-ad"), sidecar -> probe);
        FakeUiPort ui = new FakeUiPort();
        runtime.start(true);
        runtime.bindUi(ui);

        probe.emit(providerCandidate(session, "prompt-ad", 0L, 8_000L));
        ui.actions.ignore();
        runtime.setSkipMode(AdSkipPolicyController.Mode.AUTO);
        probe.emit(providerCandidate(session, "auto-ad", 0L, 10_000L));

        assertEquals(1, ui.candidateShows);
        assertEquals(AdSkipPolicyController.Mode.AUTO, runtime.skipMode());
        assertEquals(1, playback.seekTargets.size());
        runtime.close();
    }

    @Test
    public void probeFailureAndSuspendDoNotLeakOrDisablePcmFallback() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        FakeSignalProvider probe = new FakeSignalProvider("probe");
        AdAudioRuntimeController runtime = runtimeWithProbe(
                hub, playback, snapshotForRuleWithSidecar("ad"), sidecar -> probe);
        runtime.start(true);
        runtime.bindUi(new FakeUiPort());

        probe.fail(AdAudioSignalProvider.ErrorCode.ANALYSIS_FAILED);

        assertTrue(runtime.isActive());
        assertTrue(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));

        runtime.suspend();

        assertEquals(1, probe.closes);
        assertFalse(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        runtime.close();
    }

    @Test
    public void speechRunsWithoutFingerprintRulesAndUsesItsOwnAutoMode() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        FakeSignalProvider speech = new FakeSignalProvider(SpeechAdSignalProvider.ID);
        AdAudioRuntimeController runtime = runtimeWithProviders(
                hub, playback, emptySnapshot(),
                ignored -> new NoopAdAudioSignalProvider("probe"), () -> speech);
        FakeUiPort ui = new FakeUiPort();

        runtime.setSpeechConfig(SpeechAdConfig.create(
                true, "\u8d4c\u573a", 15, "AUTO"));
        runtime.start(false);
        runtime.bindUi(ui);

        assertEquals(AdAudioSignalProvider.ProviderState.RUNNING, speech.state());
        speech.emit(SpeechAdSignalProvider.RULE_ID, 10_000L, 25_000L);
        assertEquals(List.of(25_000L), playback.seekTargets);
        assertEquals(0, ui.candidateShows);
        runtime.close();
    }

    @Test
    public void speechStartFailureDoesNotStopPcmOrProbe() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        FakeSignalProvider probe = new FakeSignalProvider("probe");
        FakeSignalProvider speech = new FakeSignalProvider(SpeechAdSignalProvider.ID);
        speech.failOnStart = true;
        AdAudioRuntimeController runtime = runtimeWithProviders(
                hub, playback, snapshotForRuleWithSidecar("ad"),
                ignored -> probe, () -> speech);

        runtime.setSpeechConfig(SpeechAdConfig.create(
                true, "\u8d4c\u573a", 15, "PROMPT"));
        runtime.start(true);
        runtime.bindUi(new FakeUiPort());

        assertEquals(AdAudioSignalProvider.ProviderState.RUNNING, probe.state());
        assertTrue(runtime.isActive());
        assertTrue(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        runtime.close();
    }

    @Test
    public void speechModeSwitchAffectsOnlyFutureSpeechCandidates() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        List<FakeSignalProvider> speeches = new ArrayList<>();
        AdAudioRuntimeController runtime = runtimeWithProviders(
                hub, playback, emptySnapshot(),
                ignored -> new NoopAdAudioSignalProvider("probe"), () -> {
                    FakeSignalProvider provider = new FakeSignalProvider(
                            SpeechAdSignalProvider.ID);
                    speeches.add(provider);
                    return provider;
                });
        FakeUiPort ui = new FakeUiPort();

        runtime.setSpeechConfig(SpeechAdConfig.create(
                true, "\u8d4c\u573a", 15, "PROMPT"));
        runtime.start(false);
        runtime.bindUi(ui);
        speeches.get(0).emit(SpeechAdSignalProvider.RULE_ID, 1_000L, 16_000L);
        ui.actions.ignore();

        runtime.setSpeechConfig(SpeechAdConfig.create(
                true, "\u8d4c\u573a", 15, "AUTO"));
        speeches.get(1).emit(SpeechAdSignalProvider.RULE_ID, 30_000L, 45_000L);

        assertEquals(1, ui.candidateShows);
        assertEquals(List.of(45_000L), playback.seekTargets);
        assertEquals(AdAudioSignalProvider.ProviderState.CLOSED,
                speeches.get(0).state());
        runtime.close();
    }
    @Test
    public void replacingSpeechConfigClosesProductionSessionAndDropsOldCallbacks() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        FakeSpeechRecognitionFactory recognition = new FakeSpeechRecognitionFactory();
        AdAudioRuntimeController runtime = new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L),
                AdAudioRuntimeControllerTest::emptySnapshot, playback, recognition);
        FakeUiPort ui = new FakeUiPort();

        runtime.setSpeechConfig(SpeechAdConfig.create(
                true, "\u8d4c\u573a", 15, "PROMPT"));
        runtime.start(false);
        runtime.bindUi(ui);
        FakeSpeechSession first = recognition.sessions.get(0);

        runtime.setSpeechConfig(SpeechAdConfig.create(
                true, "\u9996\u5145", 30, "AUTO"));

        assertEquals(1, first.closeCalls);
        assertEquals(2, recognition.sessions.size());
        first.listener.onResult("\u8d4c\u573a", 1L, 2L, 1);
        assertTrue(playback.seekTargets.isEmpty());
        assertEquals(0, ui.candidateShows);
        runtime.close();
    }
    @Test
    public void rebindingTheSameUiKeepsTheRunningSpeechSession() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        FakeSpeechRecognitionFactory recognition = new FakeSpeechRecognitionFactory();
        AdAudioRuntimeController runtime = new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L),
                AdAudioRuntimeControllerTest::emptySnapshot, playback, recognition);
        FakeUiPort ui = new FakeUiPort();

        runtime.setSpeechConfig(SpeechAdConfig.create(
                true, "赌场", 15, "PROMPT"));
        runtime.start(false);
        runtime.bindUi(ui);
        assertEquals(1, recognition.sessions.size());

        // PlaybackActivity rebinds on every playback state change; that must not tear the
        // recognizer down and lose the in-flight utterance.
        runtime.bindUi(ui);
        runtime.bindUi(ui);

        assertEquals(1, recognition.sessions.size());
        assertEquals(0, recognition.sessions.get(0).closeCalls);

        // The rebind installed a new coordinator. Candidates from the still-running
        // provider must reach it, not the discarded one.
        hub.publishPcm(hub.session().frame(new float[] {0.1f}, 16_000, 30_000L));
        recognition.sessions.get(0).listener.onResult(
                "赌场", 30_000_000L, 31_000_000L, 1);
        assertEquals(1, ui.candidateShows);
        runtime.close();
    }

    @Test
    public void refreshKeepsAnIdleSpeechProviderInsteadOfRebuildingIt() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        // Duration still unknown: mirrors STATE_BUFFERING right after the pipeline rebuild.
        playback.positionEligible = false;
        FakeSpeechRecognitionFactory recognition = new FakeSpeechRecognitionFactory();
        AdAudioRuntimeController runtime = new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L),
                AdAudioRuntimeControllerTest::emptySnapshot, playback, recognition);

        runtime.setSpeechConfig(SpeechAdConfig.create(true, "赌场", 15, "PROMPT"));
        runtime.start(false);
        runtime.bindUi(new FakeUiPort());
        // Resources are only acquired once the position is eligible, so nothing exists yet.
        assertTrue(recognition.sessions.isEmpty());

        // Playback flaps between BUFFERING and READY; each flap calls refresh(). An IDLE
        // provider must count as live, otherwise every refresh destroys and re-creates the
        // recognizer and it never reaches RUNNING.
        runtime.refresh();
        runtime.refresh();
        runtime.refresh();

        assertTrue(recognition.sessions.isEmpty());

        // Once the duration is known the provider must acquire its recognizer exactly once
        // and then survive further refreshes without being rebuilt.
        playback.positionEligible = true;
        runtime.refresh();
        assertEquals(1, recognition.sessions.size());
        assertTrue(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));

        runtime.refresh();
        runtime.refresh();
        assertEquals(1, recognition.sessions.size());
        assertEquals(0, recognition.sessions.get(0).closeCalls);

        // Falling back to buffering must not tear the recognizer down again.
        playback.positionEligible = false;
        runtime.refresh();
        assertEquals(1, recognition.sessions.size());
        assertEquals(0, recognition.sessions.get(0).closeCalls);
        runtime.close();
    }

    @Test
    public void newHubSessionRebuildsAParkedSpeechProviderInsteadOfKeepingItsStaleLease() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        FakeSpeechRecognitionFactory recognition = new FakeSpeechRecognitionFactory();
        AdAudioRuntimeController runtime = new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L),
                AdAudioRuntimeControllerTest::emptySnapshot, playback, recognition);

        runtime.setSpeechConfig(SpeechAdConfig.create(true, "赌场", 15, "PROMPT"));
        runtime.start(false);
        runtime.bindUi(new FakeUiPort());
        assertEquals(1, recognition.sessions.size());
        FakeSpeechSession first = recognition.sessions.get(0);

        // A new media session (换源/换集) without a suspend() in between. The parked provider
        // holds a capture lease bound to the old session, so it must be rebuilt.
        hub.beginSession(0L);
        runtime.refresh();

        assertEquals(1, first.closeCalls);
        assertEquals(2, recognition.sessions.size());
        runtime.close();
    }

    private static AdAudioRuntimeController runtime(
            PlaybackMediaSignalHub hub, FakePlaybackPort playback, AdAudioRuleSnapshot snapshot) {
        return new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L), () -> snapshot, playback,
                Runnable::run, () -> { });
    }

    private static AdAudioRuntimeController runtimeWithProbe(
            PlaybackMediaSignalHub hub, FakePlaybackPort playback,
            AdAudioRuleSnapshot snapshot,
            AdAudioRuntimeController.ProbeProviderFactory factory) {
        return new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L), () -> snapshot, playback,
                Runnable::run, () -> { }, factory);
    }

    private static AdAudioRuntimeController runtimeWithProviders(
            PlaybackMediaSignalHub hub, FakePlaybackPort playback,
            AdAudioRuleSnapshot snapshot,
            AdAudioRuntimeController.ProbeProviderFactory probeFactory,
            AdAudioRuntimeController.SpeechProviderFactory speechFactory) {
        return new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L), () -> snapshot, playback,
                Runnable::run, () -> { }, probeFactory, speechFactory);
    }
    private static AdAudioRuleSnapshot goodSnapshot() {
        return snapshotForRule("ad");
    }

    private static AdAudioRuleSnapshot snapshotForRule(String ruleId) {
        AudioFingerprintRuleSet rules = AudioFingerprintRuleCodec.fromJson("{"
                + "\"schemaVersion\":2,\"algorithm\":{"
                + "\"id\":\"spectral-sequence-v2\",\"sampleRate\":16000,"
                + "\"windowMs\":512,\"hopMs\":256,\"bandCount\":16},"
                + "\"rules\":[{\"id\":\"" + ruleId + "\",\"durationMs\":10000,"
                + "\"anchorOffsetMs\":0,\"anchorDurationMs\":3000,"
                + "\"fingerprint\":[\"32f0007c\",\"35c100e0\",\"3b8b01c0\",\"d30a0380\"]}]}" );
        return new AdAudioRuleSnapshot("test", "v1", rules, List.of(), "");
    }

    private static AdAudioRuleSnapshot snapshotForRuleWithSidecar(String ruleId) {
        return snapshotForRulesWithSidecar(ruleId);
    }

    private static AdAudioRuleSnapshot snapshotForRulesWithSidecar(String... ruleIds) {
        AdAudioRuleSnapshot base = snapshotForRule(ruleIds[0]);
        AudioFingerprintRule prototype = base.ruleSet().rules().get(0);
        List<AudioFingerprintRule> rules = new ArrayList<>(ruleIds.length);
        for (String ruleId : ruleIds) {
            rules.add(new AudioFingerprintRule(
                    ruleId, prototype.durationMs(), prototype.anchorOffsetMs(),
                    prototype.anchorDurationMs(), prototype.fingerprint(),
                    prototype.variants()));
        }
        AudioFingerprintRuleSet ruleSet = new AudioFingerprintRuleSet(
                base.ruleSet().config(), rules);
        byte[] canonical = "{\"format\":\"ad-audio-probe-rules\"}"
                .getBytes(StandardCharsets.UTF_8);
        ProbeRuleSidecar sidecar = ProbeRuleSidecar.verified(
                "test", 1L, new byte[32], ProbeRuleSidecar.ALGORITHM_ID,
                "converter-1", canonical, sha256(canonical));
        return new AdAudioRuleSnapshot(
                base.sourceId(), base.version(), ruleSet,
                base.warnings(), base.lastError(), sidecar);
    }

    private static AdAudioSignalProvider.AdAudioCandidate providerCandidate(
            PlaybackMediaSignalHub.Session session, String ruleId,
            long startMs, long endMs) {
        return new AdAudioSignalProvider.AdAudioCandidate(
                session.id(), session.generation(), ruleId, "v1",
                startMs, endMs, true, 0.95d, "probe");
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static AdAudioRuleSnapshot emptySnapshot() {
        return new AdAudioRuleSnapshot(
                "test", "", AudioFingerprintRuleSet.empty(), List.of(), "");
    }

    private static AdAudioRuleSnapshot errorSnapshot() {
        return new AdAudioRuleSnapshot(
                "test", "", AudioFingerprintRuleSet.empty(), List.of(), "INVALID_JSON");
    }

    private static float[] chirp16k() {
        int sampleRate = 16_000;
        int seconds = 3;
        float[] output = new float[sampleRate * seconds];
        double startFrequency = 300;
        double endFrequency = 3_000;
        double rate = (endFrequency - startFrequency) / seconds;
        for (int index = 0; index < output.length; index++) {
            double time = index / (double) sampleRate;
            double phase = 2.0 * Math.PI * (startFrequency * time + 0.5 * rate * time * time);
            short pcm = (short) Math.round(Math.sin(phase) * Short.MAX_VALUE * 0.8);
            output[index] = pcm / (float) Short.MAX_VALUE;
        }
        return output;
    }

    private static final class MutableRuleSource implements AdAudioRuleSource {
        private AdAudioRuleSnapshot snapshot;

        MutableRuleSource(AdAudioRuleSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public AdAudioRuleSnapshot load() {
            return snapshot;
        }
    }

    private static final class FakePlaybackPort implements AdAudioRuntimeController.PlaybackPort {
        private final PlaybackMediaSignalHub hub;
        private final List<Long> seekTargets = new java.util.ArrayList<>();
        private boolean eligible;
        /** Mirrors STATE_BUFFERING: the controller gate passes but duration is unknown. */
        private boolean positionEligible = true;

        FakePlaybackPort(PlaybackMediaSignalHub hub, boolean eligible) {
            this.hub = hub;
            this.eligible = eligible;
        }

        @Override
        public boolean isEligible(long sessionId, long generation) {
            return eligible;
        }

        @Override
        public AdSkipCoordinator.PlaybackSnapshot snapshot(long sessionId, long generation) {
            boolean seekable = eligible && positionEligible;
            return new AdSkipCoordinator.PlaybackSnapshot(
                    sessionId, generation, 1_000L,
                    positionEligible ? 100_000L : -1L, seekable, !eligible,
                    new PlaybackMediaClock.Snapshot(generation, 0L, 50_000L, 1_000L, true, true));
        }

        @Override
        public AdSkipCoordinator.SeekResult seekTo(long sessionId, long generation, long positionMs) {
            PlaybackMediaSignalHub.Session session = hub.session();
            boolean applied = eligible && session.id() == sessionId && session.generation() == generation;
            if (applied) seekTargets.add(positionMs);
            return new AdSkipCoordinator.SeekResult(applied, session.id(), session.generation());
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

    private static final class FakeSpeechRecognitionFactory
            implements SpeechRecognitionFactory {
        private final List<FakeSpeechSession> sessions = new ArrayList<>();

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public Session create(Listener listener) {
            FakeSpeechSession session = new FakeSpeechSession(listener);
            sessions.add(session);
            return session;
        }
    }

    private static final class FakeSpeechSession
            implements SpeechRecognitionFactory.Session {
        private final SpeechRecognitionFactory.Listener listener;
        private int closeCalls;

        private FakeSpeechSession(SpeechRecognitionFactory.Listener listener) {
            this.listener = listener;
        }

        @Override
        public void accept(float[] samples, long startUs, long endUs,
                           int timelineToken) {
        }

        @Override
        public void reset() {
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }
    private static final class FakeSignalProvider implements AdAudioSignalProvider {
        private final String id;
        private Listener listener;
        private SessionContext context;
        private String ruleVersion;
        private ProviderState state = ProviderState.DISABLED;
        private int starts;
        private int closes;
        private boolean failOnStart;

        private FakeSignalProvider(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void start(SessionContext context, AdAudioRuleSnapshot rules, Listener listener) {
            if (failOnStart) throw new IllegalStateException("start failed");
            this.context = context;
            this.ruleVersion = rules.version();
            this.listener = listener;
            starts++;
            state = ProviderState.RUNNING;
        }

        @Override
        public void onHostPosition(HostPosition position) {
        }

        @Override
        public void onTimelineReset(TimelineReset reset) {
        }

        @Override
        public void setEnabled(boolean enabled) {
            state = enabled ? ProviderState.IDLE : ProviderState.DISABLED;
        }

        @Override
        public ProviderState state() {
            return state;
        }

        @Override
        public void close() {
            if (state == ProviderState.CLOSED) return;
            state = ProviderState.CLOSED;
            closes++;
        }

        private void emit(AdAudioCandidate candidate) {
            listener.onCandidate(candidate);
        }

        private void emit(String ruleId, long startMs, long endMs) {
            listener.onCandidate(new AdAudioCandidate(
                    context.sessionId(), context.generation(), ruleId, ruleVersion,
                    startMs, endMs, true, 1.0d, id));
        }

        private void fail(ErrorCode code) {
            listener.onProviderError(new ProviderError(id, code, "test failure"));
        }
    }
}
