package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AdAudioConsumerTest {

    @Test
    public void resetFinishesOldMatcherAndDropsLateResult() {
        FakeMatcherFactory factory = new FakeMatcherFactory();
        List<AdAudioConsumer.Candidate> candidates = new ArrayList<>();
        AdAudioConsumer consumer = new AdAudioConsumer(factory, candidates::add, 4);
        consumer.start(11L, 3L, AudioFingerprintRuleSet.empty());
        consumer.onPcm(new PlaybackMediaSignalHub.PcmFrame(11L, 3L, new float[]{0.1f}, 16_000, 0L));
        consumer.onLifecycle(new PlaybackMediaSignalHub.Lifecycle(
                11L, 4L, PlaybackMediaSignalHub.ResetReason.SEEK, 20_000L));
        factory.old().finishEvents = List.of(event(AudioFingerprintMatcher.Type.FULL_MATCHED, "old"));

        consumer.drainForTest();

        assertTrue(candidates.isEmpty());
        assertEquals(1, factory.old().finishedCount);
        assertEquals(4L, consumer.generation());
    }

    @Test
    public void pcmIsConvertedToSaturatedPcm16AndEventsBecomeCandidates() {
        FakeMatcherFactory factory = new FakeMatcherFactory();
        List<AdAudioConsumer.Candidate> candidates = new ArrayList<>();
        AdAudioConsumer consumer = new AdAudioConsumer(factory, candidates::add, 4);
        consumer.start(1L, 2L, AudioFingerprintRuleSet.empty());

        consumer.onPcm(new PlaybackMediaSignalHub.PcmFrame(
                1L, 2L, new float[]{-1.0f, 0.5f, 1.2f}, 16_000, 123L));
        consumer.drainForTest();

        assertEquals(List.of((short) -32768, (short) 16384, (short) 32767), factory.current().lastSamples);
        assertEquals(1, candidates.size());
        assertEquals(123L, candidates.get(0).event().startTimeMs());
    }

    @Test
    public void fullQueueDropsOldestFrameAndReportsOverflow() {
        FakeMatcherFactory factory = new FakeMatcherFactory();
        AdAudioConsumer consumer = new AdAudioConsumer(factory, candidate -> { }, 2);
        consumer.start(1L, 1L, AudioFingerprintRuleSet.empty());

        consumer.onPcm(frame(1L));
        consumer.onPcm(frame(2L));
        consumer.onPcm(frame(3L));
        consumer.drainForTest();

        assertEquals(1, consumer.queueOverflowCount());
        assertEquals(3L, factory.current().lastCaptureStartMs);
    }

    @Test
    public void matcherFailureIsIsolatedAndConsumerContinues() {
        FakeMatcherFactory factory = new FakeMatcherFactory();
        AdAudioConsumer consumer = new AdAudioConsumer(factory, candidate -> { }, 2);
        consumer.start(1L, 1L, AudioFingerprintRuleSet.empty());
        factory.current().throwOnFeed = true;

        consumer.onPcm(frame(1L));
        consumer.drainForTest();
        factory.current().throwOnFeed = false;
        consumer.onPcm(frame(2L));
        consumer.drainForTest();

        assertEquals(1, consumer.matcherErrorCount());
        assertEquals(2L, factory.current().lastCaptureStartMs);
    }

    @Test
    public void sourceChangeStartsMatcherForTheNewSession() {
        FakeMatcherFactory factory = new FakeMatcherFactory();
        List<AdAudioConsumer.Candidate> candidates = new ArrayList<>();
        AdAudioConsumer consumer = new AdAudioConsumer(factory, candidates::add, 2);
        consumer.start(1L, 3L, AudioFingerprintRuleSet.empty());

        consumer.onLifecycle(new PlaybackMediaSignalHub.Lifecycle(
                2L, 0L, PlaybackMediaSignalHub.ResetReason.SOURCE_CHANGED, 0L));
        consumer.onPcm(new PlaybackMediaSignalHub.PcmFrame(
                2L, 0L, new float[]{0.1f}, 16_000, 0L));
        consumer.drainForTest();

        assertEquals(1, candidates.size());
        assertEquals(2L, candidates.get(0).sessionId());
        assertEquals(0L, candidates.get(0).generation());
        assertEquals(2, factory.created.size());
    }

    @Test
    public void rejectedWorkerIsReportedWithoutEscapingAudioCallback() {
        AdAudioDiagnostics diagnostics = new AdAudioDiagnostics();
        AdAudioConsumer consumer = new AdAudioConsumer(
                ruleSet -> new FakeMatcher(), candidate -> { }, 2,
                command -> { throw new IllegalStateException("worker rejected"); }, diagnostics);
        consumer.start(1L, 1L, AudioFingerprintRuleSet.empty());

        consumer.onPcm(frame(1L));

        assertEquals(1L, diagnostics.count(AdAudioDiagnostics.Code.MATCHER_ERROR));
    }

    private static PlaybackMediaSignalHub.PcmFrame frame(long startMs) {
        return new PlaybackMediaSignalHub.PcmFrame(1L, 1L, new float[]{0.1f}, 16_000, startMs);
    }

    private static AudioFingerprintMatcher.MatchEvent event(AudioFingerprintMatcher.Type type, String id) {
        return new AudioFingerprintMatcher.MatchEvent(type, id, 123L, 456L, 0.9f, 6);
    }

    private static final class FakeMatcherFactory implements AdAudioConsumer.MatcherFactory {
        private final List<FakeMatcher> created = new ArrayList<>();

        @Override
        public AdAudioConsumer.Matcher create(AudioFingerprintRuleSet ruleSet) {
            FakeMatcher matcher = new FakeMatcher();
            created.add(matcher);
            return matcher;
        }

        FakeMatcher current() {
            return created.get(created.size() - 1);
        }

        FakeMatcher old() {
            return created.get(0);
        }
    }

    private static final class FakeMatcher implements AdAudioConsumer.Matcher {
        private List<AudioFingerprintMatcher.MatchEvent> feedEvents = List.of(event(AudioFingerprintMatcher.Type.START_MATCHED, "ad"));
        private List<AudioFingerprintMatcher.MatchEvent> finishEvents = List.of();
        private boolean throwOnFeed;
        private int finishedCount;
        private long lastCaptureStartMs = -1L;
        private List<Short> lastSamples = List.of();

        @Override
        public List<AudioFingerprintMatcher.MatchEvent> feed(short[] samples, int sampleRate, long captureStartMs) {
            if (throwOnFeed) throw new IllegalStateException("test matcher failure");
            lastCaptureStartMs = captureStartMs;
            List<Short> values = new ArrayList<>(samples.length);
            for (short sample : samples) values.add(sample);
            lastSamples = values;
            return feedEvents;
        }

        @Override
        public List<AudioFingerprintMatcher.MatchEvent> finish() {
            finishedCount++;
            return finishEvents;
        }
    }
}
