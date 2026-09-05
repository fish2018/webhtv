package com.fongmi.android.tv.ad.audio;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AudioFingerprintContractTest {

    @Test
    public void standardConfigMatchesSdkV2() {
        AudioFingerprintConfig config = AudioFingerprintConfig.standard();

        assertEquals(16_000, config.sampleRate());
        assertEquals(512, config.windowMs());
        assertEquals(256, config.hopMs());
        assertEquals(16, config.bandCount());
        assertEquals(8_192, config.windowSamples());
        assertEquals(4_096, config.hopSamples());
    }

    @Test
    public void sampleCountsUseSdkRoundingForFractionalMilliseconds() {
        AudioFingerprintConfig config = new AudioFingerprintConfig(11_025, 513, 257, 16);

        assertEquals(5_656, config.windowSamples());
        assertEquals(2_833, config.hopSamples());
    }

    @Test(expected = IllegalArgumentException.class)
    public void configRejectsExcessiveFftWorkload() {
        new AudioFingerprintConfig(192_000, 4_000, 64, 16);
    }

    @Test(expected = IllegalArgumentException.class)
    public void configRejectsHopLongerThanWindow() {
        new AudioFingerprintConfig(16_000, 256, 512, 16);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ruleRejectsTooShortFingerprint() {
        new AudioFingerprintRule("ad-1", 15_000, 0, 5_000, new int[]{1, 2, 3}, List.of());
    }

    @Test(expected = IllegalArgumentException.class)
    public void ruleRejectsAnchorOutsideDuration() {
        new AudioFingerprintRule("ad-1", 5_000, 2_000, 4_000, sequence(1), List.of());
    }

    @Test
    public void ruleDefensivelyCopiesSequences() {
        int[] primary = sequence(1);
        int[] variant = sequence(11);
        AudioFingerprintRule rule = new AudioFingerprintRule(
                "ad-1", 15_000, 0, 5_000, primary, List.of(variant));

        primary[0] = 99;
        variant[0] = 99;
        int[] returned = rule.fingerprint();
        returned[0] = 77;

        assertArrayEquals(sequence(1), rule.fingerprint());
        assertArrayEquals(sequence(11), rule.variants().get(0));
        assertEquals(2, rule.allSequences().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void ruleSetRejectsDuplicateIds() {
        AudioFingerprintRule first = rule("same", 1);
        AudioFingerprintRule second = rule("same", 11);

        new AudioFingerprintRuleSet(AudioFingerprintConfig.standard(), List.of(first, second));
    }

    @Test
    public void ruleSetExposesSdkSchemaAndAlgorithm() {
        AudioFingerprintRuleSet rules = new AudioFingerprintRuleSet(
                AudioFingerprintConfig.standard(), List.of(rule("ad-1", 1)));

        assertEquals(2, AudioFingerprintRuleSet.SCHEMA_VERSION);
        assertEquals("spectral-sequence-v2", AudioFingerprintRuleSet.ALGORITHM_ID);
        assertEquals(1, rules.rules().size());
    }

    private static AudioFingerprintRule rule(String id, int seed) {
        return new AudioFingerprintRule(id, 15_000, 0, 5_000, sequence(seed), List.of());
    }

    private static int[] sequence(int seed) {
        return new int[]{seed, seed + 1, seed + 2, seed + 3};
    }
}
