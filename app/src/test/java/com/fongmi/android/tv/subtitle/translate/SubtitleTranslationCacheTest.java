package com.fongmi.android.tv.subtitle.translate;

import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SubtitleTranslationCacheTest {

    @Test
    public void key_changesWhenSourceContentOrModelChanges() throws Exception {
        File source = File.createTempFile("subtitle-source", ".srt");
        Files.writeString(source.toPath(), "1\n00:00:01,000 --> 00:00:02,000\nHello\n", StandardCharsets.UTF_8);
        SubtitleAsset asset = new SubtitleAsset("file://" + source.getAbsolutePath(), source.getAbsolutePath(), "source.srt", "en", "application/x-subrip", 0, false, 0L);
        SubtitleTranslationRequest request = SubtitleTranslationRequest.builder()
                .sourceAsset(asset)
                .targetLanguage("zh-Hans")
                .mode(SubtitleTranslationRequest.MODE_TRANSLATED)
                .build();
        AiConfig config = config("gpt-4.1-mini");
        SubtitleTranslationCache cache = new SubtitleTranslationCache();

        String initial = cache.key(request, config);
        String otherModel = cache.key(request, config("gpt-4.1"));
        Files.writeString(source.toPath(), "1\n00:00:01,000 --> 00:00:02,000\nHello again\n", StandardCharsets.UTF_8);
        String changedSource = cache.key(request, config);

        assertNotEquals(initial, otherModel);
        assertNotEquals(initial, changedSource);
    }

    @Test
    public void key_reusesCacheForSameSourceContentInDifferentFiles() throws Exception {
        String content = "1\n00:00:01,000 --> 00:00:02,000\nHello\n";
        File first = File.createTempFile("subtitle-source-first", ".srt");
        File second = File.createTempFile("subtitle-source-second", ".srt");
        Files.writeString(first.toPath(), content, StandardCharsets.UTF_8);
        Files.writeString(second.toPath(), content, StandardCharsets.UTF_8);
        first.setLastModified(1_700_000_000_000L);
        second.setLastModified(1_800_000_000_000L);
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        AiConfig config = config("gpt-4.1-mini");

        String firstKey = cache.key(request(first), config);
        String secondKey = cache.key(request(second), config);

        assertEquals(firstKey, secondKey);
    }

    @Test
    public void key_reusesCacheWhenSourceLanguageMetadataChanges() throws Exception {
        File source = File.createTempFile("subtitle-source", ".srt");
        Files.writeString(source.toPath(), "1\n00:00:01,000 --> 00:00:02,000\nHello\n", StandardCharsets.UTF_8);
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        AiConfig config = config("gpt-4.1-mini");

        String englishKey = cache.key(request(source, "en"), config);
        String threeLetterKey = cache.key(request(source, "eng"), config);

        assertEquals(englishKey, threeLetterKey);
    }

    private SubtitleTranslationRequest request(File source) {
        return request(source, "en");
    }

    private SubtitleTranslationRequest request(File source, String language) {
        SubtitleAsset asset = new SubtitleAsset("file://" + source.getAbsolutePath(), source.getAbsolutePath(), "source.srt", "en", "application/x-subrip", 0, false, 0L);
        return SubtitleTranslationRequest.builder()
                .sourceAsset(asset)
                .sourceLanguage(language)
                .targetLanguage("zh-Hans")
                .mode(SubtitleTranslationRequest.MODE_TRANSLATED)
                .build();
    }

    private AiConfig config(String model) {
        AiConfig config = new AiConfig();
        config.setEnabled(true);
        config.setProtocol(AiConfig.PROTOCOL_OPENAI_RESPONSES);
        config.setEndpoint("https://api.example.test/v1/responses");
        config.setApiKey("test-key");
        config.setModel(model);
        return config.sanitize();
    }
}
