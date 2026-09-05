package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
    public void settingUsesExactlyFourStablePreferenceKeysAndNormalizesWrites() throws Exception {
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
    }

    private static String readSource(String fileName) throws Exception {
        Path moduleRoot = Files.exists(Path.of("src/main/java")) ? Path.of("src/main/java") : Path.of("app/src/main/java");
        Path path = moduleRoot.resolve(Path.of("com", "fongmi", "android", "tv", "ad", "audio", fileName));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}