package com.fongmi.android.tv.ad.audio;

import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AdAudioSignalProviderTest {

    @Test
    public void sessionContextDefensivelyCopiesHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", "https://media.example/");

        AdAudioSignalProvider.SessionContext context =
                new AdAudioSignalProvider.SessionContext(
                        7L, 2L, "media-1", "https://media.example/video.m3u8", headers);
        headers.put("Authorization", "late mutation");

        assertEquals(Map.of("Referer", "https://media.example/"), context.headers());
        assertThrows(UnsupportedOperationException.class,
                () -> context.headers().put("X-Test", "value"));
    }

    @Test
    public void boundaryValuesRejectInvalidTimelineAndCandidateData() {
        assertThrows(IllegalArgumentException.class,
                () -> new AdAudioSignalProvider.SessionContext(
                        -1L, 0L, "media", "https://media.example/video", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AdAudioSignalProvider.HostPosition(
                        1L, 0L, -1L, 10_000L, true, false));
        assertThrows(IllegalArgumentException.class,
                () -> new AdAudioSignalProvider.AdAudioCandidate(
                        1L, 0L, "rule", "v1", 2_000L, 1_000L,
                        true, 1.0d, "pcm"));
        assertThrows(IllegalArgumentException.class,
                () -> new AdAudioSignalProvider.AdAudioCandidate(
                        1L, 0L, "rule", "v1", 1_000L, 2_000L,
                        true, Double.NaN, "pcm"));
    }

    @Test
    public void noopProviderHasNoResourcesOrCallbacksAndClosesIdempotently() {
        NoopAdAudioSignalProvider provider = new NoopAdAudioSignalProvider("probe");
        AtomicInteger callbacks = new AtomicInteger();
        AdAudioSignalProvider.Listener listener = new AdAudioSignalProvider.Listener() {
            @Override
            public void onCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
                callbacks.incrementAndGet();
            }

            @Override
            public void onProviderError(AdAudioSignalProvider.ProviderError error) {
                callbacks.incrementAndGet();
            }
        };

        assertEquals("probe", provider.id());
        assertEquals(AdAudioSignalProvider.ProviderState.DISABLED, provider.state());
        provider.setEnabled(true);
        assertEquals(AdAudioSignalProvider.ProviderState.IDLE, provider.state());
        provider.start(context(7L, 2L), snapshot(), listener);
        provider.onHostPosition(new AdAudioSignalProvider.HostPosition(
                6L, 1L, 1_000L, 10_000L, true, false));
        provider.onTimelineReset(new AdAudioSignalProvider.TimelineReset(
                6L, 1L, AdAudioSignalProvider.ResetReason.SEEK, 2_000L));

        assertEquals(0, callbacks.get());
        assertEquals(AdAudioSignalProvider.ProviderState.IDLE, provider.state());

        provider.close();
        provider.close();
        provider.setEnabled(true);
        provider.start(context(8L, 0L), snapshot(), listener);

        assertEquals(0, callbacks.get());
        assertEquals(AdAudioSignalProvider.ProviderState.CLOSED, provider.state());
    }

    @Test
    public void disablingNoopReturnsItToDisabledState() {
        NoopAdAudioSignalProvider provider = new NoopAdAudioSignalProvider("probe");

        provider.setEnabled(true);
        provider.setEnabled(false);

        assertEquals(AdAudioSignalProvider.ProviderState.DISABLED, provider.state());
    }

    @Test
    public void pcmProviderMapsMatcherEventsAndTracksTimelineGeneration() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        RecordingListener listener = new RecordingListener();
        PcmAdAudioSignalProvider provider = pcmProvider(hub, ruleSet -> matcherReturning(
                new AudioFingerprintMatcher.MatchEvent(
                        AudioFingerprintMatcher.Type.FULL_MATCHED,
                        "ad-1", 1_000L, 3_000L, 0.95f, 8)));

        provider.setEnabled(true);
        provider.start(context(session.id(), session.generation()), rulesSnapshot(), listener);
        hub.publishPcm(session.frame(new float[] {0.1f, -0.1f}, 16_000, 900L));

        assertEquals(AdAudioSignalProvider.ProviderState.RUNNING, provider.state());
        assertTrue(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        assertEquals(1, listener.candidates.size());
        AdAudioSignalProvider.AdAudioCandidate first = listener.candidates.get(0);
        assertEquals(session.id(), first.sessionId());
        assertEquals(0L, first.generation());
        assertEquals("rules-v1", first.ruleVersion());
        assertEquals("pcm", first.providerId());
        assertTrue(first.fullMatch());

        PlaybackMediaSignalHub.Session reset = hub.resetTimeline(
                4_000L, PlaybackMediaSignalHub.ResetReason.SEEK);
        hub.publishPcm(reset.frame(new float[] {0.2f, -0.2f}, 16_000, 4_000L));

        assertEquals(2, listener.candidates.size());
        assertEquals(reset.generation(), listener.candidates.get(1).generation());
        assertTrue(listener.errors.isEmpty());
    }

    @Test
    public void disablingPcmProviderReleasesCaptureAndStopsCallbacks() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        RecordingListener listener = new RecordingListener();
        PcmAdAudioSignalProvider provider = pcmProvider(hub, ruleSet -> matcherReturning(
                new AudioFingerprintMatcher.MatchEvent(
                        AudioFingerprintMatcher.Type.START_MATCHED,
                        "ad-1", 1_000L, 3_000L, 0.9f, 4)));
        provider.setEnabled(true);
        provider.start(context(session.id(), session.generation()), rulesSnapshot(), listener);

        provider.setEnabled(false);
        hub.publishPcm(session.frame(new float[] {0.1f}, 16_000, 1_000L));

        assertEquals(AdAudioSignalProvider.ProviderState.DISABLED, provider.state());
        assertFalse(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        assertTrue(listener.candidates.isEmpty());
        provider.close();
        provider.close();
        assertEquals(AdAudioSignalProvider.ProviderState.CLOSED, provider.state());
    }

    @Test
    public void pcmMatcherFailureIsReportedWithoutEscapingTheHub() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        RecordingListener listener = new RecordingListener();
        PcmAdAudioSignalProvider provider = pcmProvider(hub, ruleSet -> new AdAudioConsumer.Matcher() {
            @Override
            public List<AudioFingerprintMatcher.MatchEvent> feed(
                    short[] samples, int sampleRate, long captureStartMs) {
                throw new IllegalStateException("matcher failed");
            }

            @Override
            public List<AudioFingerprintMatcher.MatchEvent> finish() {
                return List.of();
            }
        });
        provider.setEnabled(true);
        provider.start(context(session.id(), session.generation()), rulesSnapshot(), listener);

        hub.publishPcm(session.frame(new float[] {0.1f}, 16_000, 1_000L));
        provider.onHostPosition(new AdAudioSignalProvider.HostPosition(
                session.id(), session.generation(), 1_000L, 10_000L, true, false));

        assertEquals(1, listener.errors.size());
        assertEquals(AdAudioSignalProvider.ErrorCode.ANALYSIS_FAILED,
                listener.errors.get(0).code());
        assertEquals(AdAudioSignalProvider.ProviderState.DEGRADED, provider.state());
    }

    @Test
    public void pcmMatcherRestartFailureAfterResetIsReported() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        RecordingListener listener = new RecordingListener();
        AtomicInteger creates = new AtomicInteger();
        PcmAdAudioSignalProvider provider = pcmProvider(hub, ruleSet -> {
            if (creates.incrementAndGet() > 1) {
                throw new IllegalStateException("restart failed");
            }
            return quietMatcher();
        });
        provider.setEnabled(true);
        provider.start(context(session.id(), session.generation()), rulesSnapshot(), listener);

        hub.resetTimeline(2_000L, PlaybackMediaSignalHub.ResetReason.SEEK);

        assertEquals(1, listener.errors.size());
        assertEquals(AdAudioSignalProvider.ErrorCode.ANALYSIS_FAILED,
                listener.errors.get(0).code());
        assertEquals(AdAudioSignalProvider.ProviderState.DEGRADED, provider.state());
    }

    private static AdAudioSignalProvider.SessionContext context(long sessionId, long generation) {
        return new AdAudioSignalProvider.SessionContext(
                sessionId, generation, "media", "https://media.example/video.m3u8", Map.of());
    }

    private static AdAudioRuleSnapshot snapshot() {
        return new AdAudioRuleSnapshot(
                "test", "v1", AudioFingerprintRuleSet.empty(), List.of(), "");
    }

    private static AdAudioRuleSnapshot rulesSnapshot() {
        AudioFingerprintRule rule = new AudioFingerprintRule(
                "ad-1", 3_000L, 0L, 1_000L,
                new int[] {1, 2, 3, 4}, List.of());
        AudioFingerprintRuleSet rules = new AudioFingerprintRuleSet(
                AudioFingerprintConfig.standard(), List.of(rule));
        return new AdAudioRuleSnapshot("signed:test", "rules-v1", rules, List.of(), "");
    }

    private static PcmAdAudioSignalProvider pcmProvider(
            PlaybackMediaSignalHub hub, AdAudioConsumer.MatcherFactory matcherFactory) {
        return new PcmAdAudioSignalProvider(
                hub, matcherFactory, 4, Runnable::run, new AdAudioDiagnostics());
    }

    private static AdAudioConsumer.Matcher matcherReturning(
            AudioFingerprintMatcher.MatchEvent event) {
        return new AdAudioConsumer.Matcher() {
            @Override
            public List<AudioFingerprintMatcher.MatchEvent> feed(
                    short[] samples, int sampleRate, long captureStartMs) {
                return List.of(event);
            }

            @Override
            public List<AudioFingerprintMatcher.MatchEvent> finish() {
                return List.of();
            }
        };
    }

    private static AdAudioConsumer.Matcher quietMatcher() {
        return new AdAudioConsumer.Matcher() {
            @Override
            public List<AudioFingerprintMatcher.MatchEvent> feed(
                    short[] samples, int sampleRate, long captureStartMs) {
                return List.of();
            }

            @Override
            public List<AudioFingerprintMatcher.MatchEvent> finish() {
                return List.of();
            }
        };
    }

    private static final class RecordingListener implements AdAudioSignalProvider.Listener {
        private final List<AdAudioSignalProvider.AdAudioCandidate> candidates = new ArrayList<>();
        private final List<AdAudioSignalProvider.ProviderError> errors = new ArrayList<>();

        @Override
        public void onCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
            candidates.add(candidate);
        }

        @Override
        public void onProviderError(AdAudioSignalProvider.ProviderError error) {
            errors.add(error);
        }
    }
}
