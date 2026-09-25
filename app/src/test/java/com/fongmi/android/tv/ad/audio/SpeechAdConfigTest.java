package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.Test;

public class SpeechAdConfigTest {

    @Test
    public void defaultsAreDisabledPromptAndFifteenSeconds() {
        SpeechAdConfig config = SpeechAdConfig.defaults();
        assertFalse(config.enabled());
        assertEquals(AdSkipPolicyController.Mode.PROMPT, config.mode());
        assertEquals(15, config.skipSeconds());
        assertEquals(SpeechAdConfig.DEFAULT_KEYWORDS, String.join(",", config.keywords().values()));
        assertFalse(config.keywords().isEmpty());
    }

    @Test
    public void unsafeDurationIsClamped() {
        assertEquals(1, SpeechAdConfig.create(true, "赌场", 0, "PROMPT").skipSeconds());
        assertEquals(120, SpeechAdConfig.create(true, "赌场", 999, "AUTO").skipSeconds());
    }

    @Test
    public void unknownOrNullModeFallsBackToPrompt() {
        assertEquals(AdSkipPolicyController.Mode.PROMPT,
                SpeechAdConfig.create(true, "赌场", 15, "UNKNOWN").mode());
        assertEquals(AdSkipPolicyController.Mode.PROMPT,
                SpeechAdConfig.create(true, "赌场", 15, null).mode());
    }

    @Test
    public void keywordsAreNormalizedAndDeduplicated() {
        SpeechAdConfig config = SpeechAdConfig.create(true, "  赌场,赌场，\uFF27\uFF21\uFF2D\uFF25  ", 15, "AUTO");
        assertEquals(java.util.List.of("赌场", "game"), config.keywords().values());
        assertEquals(AdSkipPolicyController.Mode.AUTO, config.mode());
    }

    @Test
    public void compoundRulesAreOptionalAndHaveStableBoundedRoutingVersion() {
        SpeechAdConfig first = SpeechAdConfig.createWithRules(
                true, "", "广告之后*>马上回来，[2,30]", 15, "AUTO");
        SpeechAdConfig second = SpeechAdConfig.createWithRules(
                true, "", "广告之后*>马上回来，[2,30]", 15, "AUTO");

        assertTrue(first.hasSpeechRules());
        assertEquals(1, first.rules().rules().size());
        assertEquals(first.rulesVersion(), second.rulesVersion());
        assertTrue(first.rulesVersion().length() <= 16);
        assertEquals(AdSkipPolicyController.Mode.AUTO, first.mode());
    }

    @Test
    public void legacyConfigKeepsAnEmptyCompoundRuleSnapshot() {
        SpeechAdConfig config = SpeechAdConfig.create(true, "赌场", 15, "PROMPT");

        assertTrue(config.hasSpeechRules());
        assertTrue(config.rules().isEmpty());
        assertEquals("", config.rulesVersion());
    }

    @Test
    public void snapshotKeywordsAreImmutable() {
        SpeechAdConfig config = SpeechAdConfig.create(true, "赌场,棋牌", 15, "PROMPT");
        try {
            config.keywords().values().add("下注");
            fail("keyword snapshot must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void settingSnapshotHasSafeDefaultsWithoutAndroidContext() {
        SpeechAdConfig config = SpeechAdSetting.snapshot();
        assertFalse(config.enabled());
        assertEquals(AdSkipPolicyController.Mode.PROMPT, config.mode());
        assertEquals(15, config.skipSeconds());
    }

    @Test(expected = NullPointerException.class)
    public void settingModeRejectsNull() {
        SpeechAdSetting.setMode(null);
    }

    @Test
    public void settingKeepsLegacyKeysAndAddsBoundedRulePreferences() throws Exception {
        String source = readSource("SpeechAdSetting.java");
        assertTrue(source.contains("speech_ad_enabled"));
        assertTrue(source.contains("speech_ad_keywords"));
        assertTrue(source.contains("speech_ad_skip_seconds"));
        assertTrue(source.contains("speech_ad_skip_mode"));
        assertTrue(source.contains("Prefers.getBoolean(KEY_ENABLED, false)"));
        assertTrue(source.contains("Prefers.getString(KEY_KEYWORDS, SpeechAdConfig.DEFAULT_KEYWORDS)"));
        assertTrue(source.contains("Prefers.getInt(KEY_SKIP_SECONDS, 15)"));
        assertTrue(source.contains("Prefers.getString(KEY_SKIP_MODE, AdSkipPolicyController.Mode.PROMPT.name())"));
        assertTrue(source.contains("SpeechAdKeywordSet.parse(value)"));
        assertTrue(source.contains("Math.max(1, Math.min(120, value))"));
        assertTrue(source.contains("Objects.requireNonNull(value, \"mode\")"));
        assertEquals("speech_ad_rules_v1", SpeechAdSetting.KEY_RULES);
        assertEquals("speech_ad_rules_source", SpeechAdSetting.KEY_RULE_SOURCE);
        assertEquals("speech_ad_builtin_enabled", SpeechAdSetting.KEY_BUILTIN_ENABLED);
        assertFalse(SpeechAdSetting.isBuiltinEnabled());
        assertTrue(source.contains("SpeechAdRuleCodec.MAX_INPUT_BYTES"));
        assertTrue(source.contains("newDecoder().onMalformedInput(CodingErrorAction.REPORT)"));
    }

    @Test
    public void ruleDocumentIsCanonicalizedAndMalformedRestoreKeepsPreviousValue() {
        assertEquals("广告之后*>马上回来,[2,30]",
                SpeechAdSetting.canonicalize(" 广告之后** > 马上回来，[2，30] "));

        Map<String, ?> previous = Map.of(
                SpeechAdSetting.KEY_RULES, "旧规则,30",
                SpeechAdSetting.KEY_RULE_SOURCE, "user",
                SpeechAdSetting.KEY_BUILTIN_ENABLED, false);
        Map<String, ?> malformed = Map.of(
                SpeechAdSetting.KEY_RULES, "新规则,-1",
                SpeechAdSetting.KEY_RULE_SOURCE, "imported",
                SpeechAdSetting.KEY_BUILTIN_ENABLED, false);

        Map<String, ?> restored = SpeechAdSetting.sanitizePreferences(malformed, previous, false);
        assertEquals("旧规则,30", restored.get(SpeechAdSetting.KEY_RULES));
        assertEquals("user", restored.get(SpeechAdSetting.KEY_RULE_SOURCE));
        assertEquals(false, restored.get(SpeechAdSetting.KEY_BUILTIN_ENABLED));
    }

    @Test
    public void ruleMergeDeduplicatesExactRulesAndRejectsWindowConflicts() {
        SpeechAdRuleSet same = SpeechAdRuleCodec.parse("广告之后,30");
        assertEquals(1, SpeechAdSetting.merge(same, same).rules().size());

        SpeechAdRuleSet builtin = SpeechAdRuleCodec.parse("广告之后,30");
        SpeechAdRuleSet custom = SpeechAdRuleCodec.parse("广告之后,[2,30]");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SpeechAdSetting.merge(builtin, custom));
        assertTrue(error.getMessage().contains("conflicting windows"));
    }

    @Test
    public void invalidPreferenceTypesAreRejectedWithoutLeakingValues() {
        Map<String, ?> previous = Map.of(
                SpeechAdSetting.KEY_RULES, "旧规则,30",
                SpeechAdSetting.KEY_RULE_SOURCE, "user",
                SpeechAdSetting.KEY_BUILTIN_ENABLED, false);
        Map<String, ?> malformed = Map.of(
                SpeechAdSetting.KEY_RULES, "新规则,30",
                SpeechAdSetting.KEY_RULE_SOURCE, 42,
                SpeechAdSetting.KEY_BUILTIN_ENABLED, true);

        Map<String, ?> restored = SpeechAdSetting.sanitizePreferences(malformed, previous, false);
        assertEquals(previous, Map.of(
                SpeechAdSetting.KEY_RULES, restored.get(SpeechAdSetting.KEY_RULES),
                SpeechAdSetting.KEY_RULE_SOURCE, restored.get(SpeechAdSetting.KEY_RULE_SOURCE),
                SpeechAdSetting.KEY_BUILTIN_ENABLED, restored.get(SpeechAdSetting.KEY_BUILTIN_ENABLED)));
    }

    private static String readSource(String fileName) throws Exception {
        Path moduleRoot = Files.exists(Path.of("src/main/java")) ? Path.of("src/main/java") : Path.of("app/src/main/java");
        Path path = moduleRoot.resolve(Path.of("com", "fongmi", "android", "tv", "ad", "audio", fileName));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
