package com.fongmi.android.tv.ad.audio;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AudioFingerprintRuleCodecTest {

    @Test
    public void sdkV2JsonRoundTrips() {
        AudioFingerprintRuleSet parsed = AudioFingerprintRuleCodec.fromJson(validSdkJson());
        AudioFingerprintRuleSet roundTripped = AudioFingerprintRuleCodec.fromJson(
                AudioFingerprintRuleCodec.toJson(parsed));

        assertEquals(2, parsed.rules().size());
        assertEquals("spectral-sequence-v2", AudioFingerprintRuleSet.ALGORITHM_ID);
        assertArrayEquals(parsed.rules().get(0).fingerprint(), roundTripped.rules().get(0).fingerprint());
        assertEquals(parsed.rules().get(0).variants().size(), roundTripped.rules().get(0).variants().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedHexIsRejectedInsteadOfSilentlyDroppingRule() {
        AudioFingerprintRuleCodec.fromJson(jsonWithHash("not-hex"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void oversizedRuleSetIsRejectedBeforeAllocation() {
        AudioFingerprintRuleCodec.fromJson(jsonWithRuleCount(AudioFingerprintRuleSet.MAX_RULES + 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateRuleIdsRejectTheWholePackage() {
        AudioFingerprintRuleCodec.fromJson(jsonWithRuleCount(2)
                .replace("ad-1", "same")
                .replace("ad-2", "same"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedAlgorithmIsRejected() {
        AudioFingerprintRuleCodec.fromJson(validSdkJson().replace("spectral-sequence-v2", "other-v1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownRootFieldIsRejected() {
        AudioFingerprintRuleCodec.fromJson(validSdkJson().replace("\"rules\":[", "\"unknown\":{},\"rules\":["));
    }

    @Test(expected = IllegalArgumentException.class)
    public void deeplyNestedJsonIsRejectedBeforeTreeConstruction() {
        StringBuilder nested = new StringBuilder(validSdkJson().replace("\"rules\":[", "\"junk\":"));
        for (int i = 0; i < 80; i++) nested.append('[');
        nested.append('0');
        for (int i = 0; i < 80; i++) nested.append(']');
        nested.append(",\"rules\":[]}");
        AudioFingerprintRuleCodec.fromJson(nested.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void oversizedSerializedRuleSetIsRejected() {
        int[] hashes = new int[AudioFingerprintRule.MAX_SEQUENCE_FRAMES];
        List<AudioFingerprintRule> rules = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            rules.add(new AudioFingerprintRule("large-" + i, 15_000, 0, 3_000,
                    hashes, List.of(hashes, hashes, hashes, hashes)));
        }
        AudioFingerprintRuleCodec.toJson(new AudioFingerprintRuleSet(
                AudioFingerprintConfig.standard(), rules));
    }

    @Test
    public void largeAcceptedRuleSetStillRoundTrips() {
        int[] hashes = new int[46];
        List<AudioFingerprintRule> rules = new ArrayList<>();
        for (int i = 0; i < AudioFingerprintRuleSet.MAX_RULES; i++) {
            String prefix = "rule-" + i + '-';
            String id = prefix + "x".repeat(128 - prefix.length());
            rules.add(new AudioFingerprintRule(id, 15_000, 0, 3_000, hashes, List.of()));
        }
        AudioFingerprintRuleSet ruleSet = new AudioFingerprintRuleSet(
                AudioFingerprintConfig.standard(), rules);

        String json = AudioFingerprintRuleCodec.toJson(ruleSet);

        assertEquals(AudioFingerprintRuleSet.MAX_RULES,
                AudioFingerprintRuleCodec.fromJson(json).rules().size());
    }

    private static String validSdkJson() {
        return "{"
                + "\"schemaVersion\":2,"
                + "\"algorithm\":{"
                + "\"id\":\"spectral-sequence-v2\",\"sampleRate\":16000,"
                + "\"windowMs\":512,\"hopMs\":256,\"bandCount\":16},"
                + "\"rules\":["
                + "{\"id\":\"ad-1\",\"durationMs\":15000,\"anchorOffsetMs\":0,"
                + "\"anchorDurationMs\":3000,\"fingerprint\":[\"32f0007c\",\"35c100e0\",\"3b8b01c0\",\"d30a0380\"],"
                + "\"variants\":[[\"32e00078\",\"2dc500e0\",\"33890180\",\"f70a0300\"]]},"
                + "{\"id\":\"ad-2\",\"durationMs\":12000,\"anchorOffsetMs\":1000,"
                + "\"anchorDurationMs\":3000,\"fingerprint\":[\"32f0007c\",\"35c100e0\",\"3b8b01c0\",\"d30a0380\"]}"
                + "]}";
    }

    private static String jsonWithHash(String hash) {
        return validSdkJson().replace("32f0007c", hash);
    }

    private static String jsonWithRuleCount(int count) {
        String rule = "{\"id\":\"ad-1\",\"durationMs\":15000,\"anchorOffsetMs\":0,"
                + "\"anchorDurationMs\":3000,\"fingerprint\":[\"32f0007c\",\"35c100e0\",\"3b8b01c0\",\"d30a0380\"]}";
        StringBuilder rules = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) rules.append(',');
            rules.append(rule.replace("ad-1", "ad-" + (i + 1)));
        }
        return "{\"schemaVersion\":2,\"algorithm\":{"
                + "\"id\":\"spectral-sequence-v2\",\"sampleRate\":16000,"
                + "\"windowMs\":512,\"hopMs\":256,\"bandCount\":16},\"rules\":["
                + rules + "]}";
    }
}
