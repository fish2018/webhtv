package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdAudioDetectionMultiplexerTest {

    @Test
    public void duplicateCandidatesFromTwoProvidersAreEmittedOnce() {
        RecordingListener output = new RecordingListener();
        AdAudioDetectionMultiplexer mux = multiplexer(8, output);

        mux.onCandidate(candidate("pcm", "rule-1", 1_000L, 3_000L, true, 0.91d));
        mux.onCandidate(candidate("probe", "rule-1", 1_000L, 3_000L, true, 0.98d));

        assertEquals(1, output.candidates.size());
        assertEquals(1L, mux.diagnostics().emitted());
        assertEquals(1L, mux.diagnostics().duplicates());
    }

    @Test
    public void fullMatchUpgradesAnAlreadyEmittedStartExactlyOnce() {
        RecordingListener output = new RecordingListener();
        AdAudioDetectionMultiplexer mux = multiplexer(8, output);

        mux.onCandidate(candidate("pcm", "rule-1", 1_000L, 3_000L, false, 0.85d));
        mux.onCandidate(candidate("probe", "rule-1", 1_000L, 3_000L, true, 0.96d));
        mux.onCandidate(candidate("pcm", "rule-1", 1_000L, 3_000L, true, 0.97d));

        assertEquals(2, output.candidates.size());
        assertTrue(output.candidates.get(1).fullMatch());
        assertEquals(1L, mux.diagnostics().upgrades());
        assertEquals(1L, mux.diagnostics().duplicates());
    }

    @Test
    public void staleGenerationAndWrongRuleVersionAreDropped() {
        RecordingListener output = new RecordingListener();
        AdAudioDetectionMultiplexer mux = multiplexer(8, output);

        mux.onCandidate(new AdAudioSignalProvider.AdAudioCandidate(
                7L, 1L, "rule-1", "rules-v1", 1_000L, 3_000L,
                true, 0.9d, "pcm"));
        mux.onCandidate(new AdAudioSignalProvider.AdAudioCandidate(
                7L, 2L, "rule-1", "rules-v0", 1_000L, 3_000L,
                true, 0.9d, "probe"));

        assertTrue(output.candidates.isEmpty());
        assertEquals(2L, mux.diagnostics().stale());
    }

    @Test
    public void timelineResetClearsDedupAndRejectsOldCallbacks() {
        RecordingListener output = new RecordingListener();
        AdAudioDetectionMultiplexer mux = multiplexer(8, output);
        mux.onCandidate(candidate("pcm", "rule-1", 1_000L, 3_000L, true, 0.9d));

        mux.onTimelineReset(new AdAudioSignalProvider.TimelineReset(
                7L, 3L, AdAudioSignalProvider.ResetReason.SEEK, 5_000L));
        mux.onCandidate(candidate("pcm", "rule-1", 1_000L, 3_000L, true, 0.9d));
        mux.onCandidate(new AdAudioSignalProvider.AdAudioCandidate(
                7L, 3L, "rule-1", "rules-v1", 1_000L, 3_000L,
                true, 0.9d, "pcm"));

        assertEquals(2, output.candidates.size());
        assertEquals(1, output.resets.size());
        assertEquals(1L, mux.diagnostics().resets());
        assertEquals(1L, mux.diagnostics().stale());
    }

    @Test
    public void oversizedIntervalsAreRejectedBeforeStateAllocation() {
        RecordingListener output = new RecordingListener();
        AdAudioDetectionMultiplexer mux = multiplexer(8, output);

        mux.onCandidate(candidate(
                "probe", "rule-1", 1_000L, 1_000L + 600_001L, true, 0.9d));

        assertTrue(output.candidates.isEmpty());
        assertEquals(0, mux.trackedCandidateCount());
        assertEquals(1L, mux.diagnostics().invalid());
    }

    @Test
    public void unknownRuleIdsAreRejectedBeforeStateAllocation() {
        RecordingListener output = new RecordingListener();
        AdAudioDetectionMultiplexer mux = multiplexer(8, output);

        mux.onCandidate(candidate(
                "probe", "untrusted-rule", 1_000L, 3_000L, true, 0.9d));

        assertTrue(output.candidates.isEmpty());
        assertEquals(0, mux.trackedCandidateCount());
        assertEquals(1L, mux.diagnostics().invalid());
    }

    @Test
    public void speechRulePassesOnlyWhenExplicitlyAllowed() {
        RecordingListener output = new RecordingListener();
        AdAudioDetectionMultiplexer mux = new AdAudioDetectionMultiplexer(
                new AdAudioSignalProvider.SessionContext(
                        7L, 2L, "media", "https://media.example/video.m3u8", Map.of()),
                "rules-v1", Set.of(SpeechAdSignalProvider.RULE_ID), 8, output);

        mux.onCandidate(candidate(
                SpeechAdSignalProvider.ID, SpeechAdSignalProvider.RULE_ID,
                1_000L, 3_000L, true, 1.0d));
        mux.onCandidate(candidate(
                SpeechAdSignalProvider.ID, "untrusted-speech-rule",
                4_000L, 6_000L, true, 1.0d));

        assertEquals(1, output.candidates.size());
        assertEquals(SpeechAdSignalProvider.RULE_ID,
                output.candidates.get(0).ruleId());
        assertEquals(1L, mux.diagnostics().invalid());
    }
    @Test
    public void boundedStateEvictsOldestCandidates() {
        RecordingListener output = new RecordingListener();
        AdAudioDetectionMultiplexer mux = multiplexer(2, output);

        mux.onCandidate(candidate("pcm", "rule-1", 1_000L, 2_000L, true, 0.9d));
        mux.onCandidate(candidate("pcm", "rule-2", 2_000L, 3_000L, true, 0.9d));
        mux.onCandidate(candidate("pcm", "rule-3", 3_000L, 4_000L, true, 0.9d));
        mux.onCandidate(candidate("probe", "rule-1", 1_000L, 2_000L, true, 0.9d));

        assertEquals(4, output.candidates.size());
        assertEquals(2, mux.trackedCandidateCount());
        assertEquals(2L, mux.diagnostics().evicted());
    }

    @Test
    public void downstreamFailuresAndProviderErrorsAreIsolatedAndCounted() {
        AdAudioSignalProvider.Listener throwing = new AdAudioSignalProvider.Listener() {
            @Override
            public void onCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
                throw new IllegalStateException("candidate sink failed");
            }

            @Override
            public void onProviderError(AdAudioSignalProvider.ProviderError error) {
                throw new IllegalStateException("error sink failed");
            }
        };
        AdAudioDetectionMultiplexer mux = multiplexer(8, throwing);

        mux.onCandidate(candidate("pcm", "rule-1", 1_000L, 2_000L, true, 0.9d));
        mux.onProviderError(new AdAudioSignalProvider.ProviderError(
                "probe", AdAudioSignalProvider.ErrorCode.ANALYSIS_FAILED, "decode failed"));

        assertEquals(1L, mux.diagnostics().emitted());
        assertEquals(1L, mux.diagnostics().providerErrors());
        assertEquals(2L, mux.diagnostics().listenerErrors());
    }

    private static AdAudioDetectionMultiplexer multiplexer(
            int capacity, AdAudioSignalProvider.Listener listener) {
        return new AdAudioDetectionMultiplexer(
                new AdAudioSignalProvider.SessionContext(
                        7L, 2L, "media", "https://media.example/video.m3u8", Map.of()),
                "rules-v1", Set.of("rule-1", "rule-2", "rule-3"),
                capacity, listener);
    }

    private static AdAudioSignalProvider.AdAudioCandidate candidate(
            String providerId, String ruleId, long startMs, long endMs,
            boolean fullMatch, double similarity) {
        return new AdAudioSignalProvider.AdAudioCandidate(
                7L, 2L, ruleId, "rules-v1", startMs, endMs,
                fullMatch, similarity, providerId);
    }

    private static final class RecordingListener implements AdAudioSignalProvider.Listener {
        private final List<AdAudioSignalProvider.AdAudioCandidate> candidates = new ArrayList<>();
        private final List<AdAudioSignalProvider.ProviderError> errors = new ArrayList<>();
        private final List<AdAudioSignalProvider.TimelineReset> resets = new ArrayList<>();

        @Override
        public void onCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
            candidates.add(candidate);
        }

        @Override
        public void onProviderError(AdAudioSignalProvider.ProviderError error) {
            errors.add(error);
        }

        @Override
        public void onTimelineReset(AdAudioSignalProvider.TimelineReset reset) {
            resets.add(reset);
        }
    }
}
