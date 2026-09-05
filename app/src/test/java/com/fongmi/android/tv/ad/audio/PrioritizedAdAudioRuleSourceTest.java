package com.fongmi.android.tv.ad.audio;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrioritizedAdAudioRuleSourceTest {

    @Test
    public void validLocalRulesWinWithoutLoadingSignedSource() {
        AdAudioRuleSnapshot local = snapshot("local", "l1", rules("local-rule"), List.of(), "");
        AtomicInteger signedLoads = new AtomicInteger();
        AdAudioRuleSource signed = () -> {
            signedLoads.incrementAndGet();
            return snapshot("signed:official", "s1", rules("signed-rule"), List.of(), "");
        };

        AdAudioRuleSnapshot selected =
                new PrioritizedAdAudioRuleSource(() -> local, signed).load();

        assertEquals(local, selected);
        assertEquals(0, signedLoads.get());
    }

    @Test
    public void emptyLocalRulesFallBackToSignedSnapshot() {
        AdAudioRuleSnapshot signed =
                snapshot("signed:official", "s1", rules("signed-rule"), List.of(), "");
        PrioritizedAdAudioRuleSource source = new PrioritizedAdAudioRuleSource(
                () -> snapshot("local", "", AudioFingerprintRuleSet.empty(), List.of(), ""),
                () -> signed);

        AdAudioRuleSnapshot selected = source.load();

        assertEquals(signed, selected);
    }

    @Test
    public void invalidLocalRulesFallBackWithFixedWarning() {
        AdAudioRuleSnapshot signed = snapshot(
                "signed:official", "s2", rules("signed-rule"), List.of("CACHE_RECOVERED"), "");
        PrioritizedAdAudioRuleSource source = new PrioritizedAdAudioRuleSource(
                () -> snapshot("local", "", AudioFingerprintRuleSet.empty(),
                        List.of(), "RULE_LOAD_FAILED"),
                () -> signed);

        AdAudioRuleSnapshot selected = source.load();

        assertEquals("signed:official", selected.sourceId());
        assertEquals("signed-rule", selected.ruleSet().rules().get(0).id());
        assertTrue(selected.warnings().contains("CACHE_RECOVERED"));
        assertTrue(selected.warnings().contains("LOCAL_SOURCE_INVALID"));
        assertFalse(selected.hasError());
    }

    @Test
    public void localExceptionStillFallsBackToSignedRules() {
        AdAudioRuleSnapshot signed =
                snapshot("signed:official", "s3", rules("signed-rule"), List.of(), "");
        PrioritizedAdAudioRuleSource source = new PrioritizedAdAudioRuleSource(
                () -> {
                    throw new IllegalStateException("broken local source");
                },
                () -> signed);

        AdAudioRuleSnapshot selected = source.load();

        assertEquals(signed.sourceId(), selected.sourceId());
        assertTrue(selected.warnings().contains("LOCAL_SOURCE_INVALID"));
    }

    @Test
    public void nullLocalSnapshotStillFallsBackToSignedRules() {
        AdAudioRuleSnapshot signed =
                snapshot("signed:official", "s4", rules("signed-rule"), List.of(), "");
        PrioritizedAdAudioRuleSource source =
                new PrioritizedAdAudioRuleSource(() -> null, () -> signed);

        AdAudioRuleSnapshot selected = source.load();

        assertEquals(signed.sourceId(), selected.sourceId());
        assertTrue(selected.warnings().contains("LOCAL_SOURCE_INVALID"));
    }

    @Test
    public void bothUnavailableReturnEmptySnapshotWithLocalSpecificError() {
        PrioritizedAdAudioRuleSource source = new PrioritizedAdAudioRuleSource(
                () -> snapshot("local", "", AudioFingerprintRuleSet.empty(),
                        List.of(), "RULE_LOAD_FAILED"),
                () -> snapshot("signed:official", "", AudioFingerprintRuleSet.empty(),
                        List.of(), "NO_VALID_SIGNED_PACKAGE"));

        AdAudioRuleSnapshot selected = source.load();

        assertFalse(selected.hasRules());
        assertEquals("RULE_LOAD_FAILED", selected.lastError());
        assertTrue(selected.warnings().contains("LOCAL_SOURCE_INVALID"));
    }

    private static AdAudioRuleSnapshot snapshot(String sourceId, String version,
                                                AudioFingerprintRuleSet ruleSet,
                                                List<String> warnings, String error) {
        return new AdAudioRuleSnapshot(sourceId, version, ruleSet, warnings, error);
    }

    private static AudioFingerprintRuleSet rules(String id) {
        AudioFingerprintRule rule = new AudioFingerprintRule(
                id, 15_000L, 0L, 3_000L,
                new int[]{0, 1, 2, 3}, List.of());
        return new AudioFingerprintRuleSet(AudioFingerprintConfig.standard(), List.of(rule));
    }
}
