package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdSkipPolicyControllerTest {

    @Test
    public void defaultsToPromptAndRoutesNewCandidatesToPromptSink() {
        RecordingSinks sinks = new RecordingSinks();
        AdSkipPolicyController policy = policy(sinks);

        policy.onCandidate(candidate("rule-1", 1_000L, 3_000L, false));

        assertEquals(AdSkipPolicyController.Mode.PROMPT, policy.mode());
        assertEquals(1, sinks.prompted.size());
        assertTrue(sinks.automated.isEmpty());
    }

    @Test
    public void switchingToAutoAffectsOnlyNewCandidates() {
        RecordingSinks sinks = new RecordingSinks();
        AdSkipPolicyController policy = policy(sinks);
        policy.onCandidate(candidate("rule-1", 1_000L, 3_000L, false));

        policy.setMode(AdSkipPolicyController.Mode.AUTO);
        policy.onCandidate(candidate("rule-1", 1_000L, 3_000L, true));
        policy.onCandidate(candidate("rule-2", 4_000L, 6_000L, true));

        assertEquals(2, sinks.prompted.size());
        assertTrue(sinks.prompted.get(1).fullMatch());
        assertEquals(1, sinks.automated.size());
        assertEquals("rule-2", sinks.automated.get(0).ruleId());
    }

    @Test
    public void switchingBackToPromptAffectsOnlyLaterCandidates() {
        RecordingSinks sinks = new RecordingSinks();
        AdSkipPolicyController policy = policy(sinks);
        policy.setMode(AdSkipPolicyController.Mode.AUTO);
        policy.onCandidate(candidate("rule-1", 1_000L, 3_000L, true));

        policy.setMode(AdSkipPolicyController.Mode.PROMPT);
        policy.onCandidate(candidate("rule-2", 4_000L, 6_000L, true));

        assertEquals(1, sinks.automated.size());
        assertEquals(1, sinks.prompted.size());
        assertEquals("rule-2", sinks.prompted.get(0).ruleId());
    }

    @Test
    public void generationResetClearsDecisionsAndRejectsOldCallbacks() {
        RecordingSinks sinks = new RecordingSinks();
        AdSkipPolicyController policy = policy(sinks);
        policy.onCandidate(candidate("rule-1", 1_000L, 3_000L, true));

        policy.onTimelineReset(new AdAudioSignalProvider.TimelineReset(
                7L, 3L, AdAudioSignalProvider.ResetReason.SEEK, 5_000L));
        policy.onCandidate(candidate("rule-2", 4_000L, 6_000L, true));
        policy.onCandidate(new AdAudioSignalProvider.AdAudioCandidate(
                7L, 3L, "rule-1", "rules-v1", 1_000L, 3_000L,
                true, 0.95d, "pcm"));

        assertEquals(2, sinks.prompted.size());
        assertEquals(1L, policy.diagnostics().stale());
        assertEquals(1L, policy.diagnostics().resets());
    }

    @Test
    public void repeatedModeAndDuplicateCandidatesAreIdempotent() {
        RecordingSinks sinks = new RecordingSinks();
        AdSkipPolicyController policy = policy(sinks);

        policy.setMode(AdSkipPolicyController.Mode.PROMPT);
        policy.setMode(AdSkipPolicyController.Mode.PROMPT);
        policy.onCandidate(candidate("rule-1", 1_000L, 3_000L, true));
        policy.onCandidate(candidate("rule-1", 1_000L, 3_000L, true));

        assertEquals(1, sinks.prompted.size());
        assertEquals(0L, policy.diagnostics().modeSwitches());
        assertEquals(1L, policy.diagnostics().duplicates());
    }

    @Test
    public void speechAutoDoesNotChangePcmOrProbePolicy() {
        RecordingSinks sinks = new RecordingSinks();
        AdSkipPolicyController policy = policy(sinks);
        policy.setModeResolver(provider -> "speech".equals(provider)
                ? AdSkipPolicyController.Mode.AUTO
                : AdSkipPolicyController.Mode.PROMPT);

        policy.onCandidate(candidate("speech-rule", 1_000L, 3_000L, true, "speech"));
        policy.onCandidate(candidate("pcm-rule", 4_000L, 6_000L, true, "pcm"));
        policy.onCandidate(candidate("probe-rule", 7_000L, 9_000L, true, "probe"));

        assertEquals(1, sinks.automated.size());
        assertEquals("speech", sinks.automated.get(0).providerId());
        assertEquals(2, sinks.prompted.size());
        assertEquals("pcm", sinks.prompted.get(0).providerId());
        assertEquals("probe", sinks.prompted.get(1).providerId());
    }

    @Test
    public void unknownProviderDefaultsToPromptWhenResolverReturnsNull() {
        RecordingSinks sinks = new RecordingSinks();
        AdSkipPolicyController policy = policy(sinks);
        policy.setModeResolver(provider -> null);

        policy.onCandidate(candidate("future-rule", 1_000L, 3_000L, true, "future"));

        assertEquals(1, sinks.prompted.size());
        assertTrue(sinks.automated.isEmpty());
    }

    @Test
    public void resolverFailureDefaultsToPrompt() {
        RecordingSinks sinks = new RecordingSinks();
        AdSkipPolicyController policy = policy(sinks);
        policy.setModeResolver(provider -> { throw new IllegalStateException("boom"); });

        policy.onCandidate(candidate("future-rule", 1_000L, 3_000L, true, "future"));

        assertEquals(1, sinks.prompted.size());
        assertTrue(sinks.automated.isEmpty());
    }
    @Test
    public void closeStopsAllFutureDispatch() {
        RecordingSinks sinks = new RecordingSinks();
        AdSkipPolicyController policy = policy(sinks);

        policy.close();
        policy.setMode(AdSkipPolicyController.Mode.AUTO);
        policy.onCandidate(candidate("rule-1", 1_000L, 3_000L, true));

        assertTrue(sinks.prompted.isEmpty());
        assertTrue(sinks.automated.isEmpty());
    }

    private static AdSkipPolicyController policy(RecordingSinks sinks) {
        return new AdSkipPolicyController(
                new AdAudioSignalProvider.SessionContext(
                        7L, 2L, "media", "https://media.example/video.m3u8", Map.of()),
                "rules-v1", 16, sinks::prompt, sinks::auto);
    }

    private static AdAudioSignalProvider.AdAudioCandidate candidate(
            String ruleId, long startMs, long endMs, boolean fullMatch) {
        return new AdAudioSignalProvider.AdAudioCandidate(
                7L, 2L, ruleId, "rules-v1", startMs, endMs,
                fullMatch, 0.95d, "pcm");
    }

    private static AdAudioSignalProvider.AdAudioCandidate candidate(
            String ruleId, long startMs, long endMs, boolean fullMatch, String providerId) {
        return new AdAudioSignalProvider.AdAudioCandidate(
                7L, 2L, ruleId, "rules-v1", startMs, endMs,
                fullMatch, 0.95d, providerId);
    }
    private static final class RecordingSinks {
        private final List<AdAudioSignalProvider.AdAudioCandidate> prompted = new ArrayList<>();
        private final List<AdAudioSignalProvider.AdAudioCandidate> automated = new ArrayList<>();

        private void prompt(AdAudioSignalProvider.AdAudioCandidate candidate) {
            prompted.add(candidate);
        }

        private void auto(AdAudioSignalProvider.AdAudioCandidate candidate) {
            automated.add(candidate);
        }
    }
}
