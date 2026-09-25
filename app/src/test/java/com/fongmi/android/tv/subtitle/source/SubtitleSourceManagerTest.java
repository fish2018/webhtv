package com.fongmi.android.tv.subtitle.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.subtitle.provider.SubtitleProviderRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SubtitleSourceManagerTest {

    @Test
    public void replacesAndRemovesPreviouslyRegisteredProviders() {
        RecordingRuntime runtime = new RecordingRuntime();
        SubtitleProviderRegistry registry = new SubtitleProviderRegistry();
        SubtitleSourceManager manager = new SubtitleSourceManager(registry, new SubtitleScriptRuntimeFactory(runtime));

        manager.install("demo", subscription("first", "first.py"), "https://example.com/subtitles/manifest.json");
        assertTrue(providerNames(registry).contains("first"));
        assertEquals("https://example.com/subtitles/first.py", runtime.paths.get(0));

        manager.install("demo", subscription("second", "second.py"), "https://example.com/subtitles/manifest.json");
        assertFalse(providerNames(registry).contains("first"));
        assertTrue(providerNames(registry).contains("second"));
        assertTrue(runtime.destroyed.contains("demo:first"));

        manager.destroy();
        assertFalse(providerNames(registry).contains("second"));
        assertTrue(runtime.destroyed.contains("demo:second"));
    }

    private static String subscription(String key, String ext) {
        return "{\"version\":1,\"subtitles\":[{\"key\":\"" + key + "\",\"name\":\"" + key
                + "\",\"type\":3,\"api\":\"py\",\"ext\":\"" + ext + "\"}]}";
    }

    private static List<String> providerNames(SubtitleProviderRegistry registry) {
        return registry.providerNames();
    }

    private static final class RecordingRuntime implements SubtitleScriptRuntime {
        private final List<String> paths = new ArrayList<>();
        private final List<String> destroyed = new ArrayList<>();

        @Override
        public String init(String sourceKey, String scriptPath, String config) {
            paths.add(scriptPath);
            return "{\"code\":0,\"data\":{}}";
        }

        @Override
        public String search(String sourceKey, String request) {
            return "{\"code\":0,\"data\":{\"items\":[]}}";
        }

        @Override
        public String resolve(String sourceKey, String request) {
            return "{\"code\":0,\"data\":{\"url\":\"https://example.com/subtitle.srt\"}}";
        }

        @Override
        public void destroy(String sourceKey) {
            destroyed.add(sourceKey);
        }
    }
}
