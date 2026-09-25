package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbLanguagePolicyTest {

    @Test
    public void normalizesChineseAliasesAndCase() {
        assertEquals("zh-CN", TmdbLanguagePolicy.normalize("ZH-Hans"));
        assertEquals("zh-CN", TmdbLanguagePolicy.normalize(" zh-sg "));
        assertEquals("zh-TW", TmdbLanguagePolicy.normalize("zh-Hant"));
        assertEquals("en-US", TmdbLanguagePolicy.normalize("en_us"));
        assertEquals("zh-CN", TmdbLanguagePolicy.requestLanguage(config("zh-CN")));
    }

    @Test
    public void chineseTranslationWinsWhenPrimaryIsEnglish() {
        String json = """
                [
                  {"iso_639_1":"en","data":{"overview":"English overview"}},
                  {"iso_639_1":"zh","iso_3166_1":"CN","data":{"overview":"中文简介"}}
                ]
                """;
        TmdbLanguagePolicy.TranslatedValue value = TmdbLanguagePolicy.bestValue(
                "English overview", JsonParser.parseString(json).getAsJsonArray(), "overview", "zh-CN");

        assertEquals("中文简介", value.value());
        assertEquals("zh-CN", value.language());
        assertTrue(value.exact());
    }

    @Test
    public void englishIsAnExplicitFallbackWhenChineseIsMissing() {
        String json = """
                [{"iso_639_1":"en","data":{"overview":"English overview"}}]
                """;
        assertEquals("English overview", TmdbLanguagePolicy.bestDisplayValue(
                "English overview", JsonParser.parseString(json).getAsJsonArray(), "overview", "zh-CN"));
    }

    @Test
    public void displayCompatibilityRequiresExactLanguageOrRootOnlyCandidate() {
        assertTrue(TmdbLanguagePolicy.isDisplayLanguageCompatible("zh-CN", "zh"));
        assertTrue(TmdbLanguagePolicy.isDisplayLanguageCompatible("zh-CN", "zh-Hans"));
        assertTrue(TmdbLanguagePolicy.isDisplayLanguageCompatible("zh-TW", "zh"));
        assertFalse(TmdbLanguagePolicy.isDisplayLanguageCompatible("zh-CN", "zh-TW"));
        assertFalse(TmdbLanguagePolicy.isDisplayLanguageCompatible("zh-CN", "zh-Hant"));
        assertFalse(TmdbLanguagePolicy.isDisplayLanguageCompatible("en-US", "en-GB"));
        assertFalse(TmdbLanguagePolicy.isDisplayLanguageCompatible("zh-CN", "en-US"));
    }

    private static TmdbConfig config(String language) {
        try {
            TmdbConfig config = new TmdbConfig();
            var field = TmdbConfig.class.getDeclaredField("language");
            field.setAccessible(true);
            field.set(config, language);
            return config;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
