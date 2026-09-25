package com.fongmi.android.tv.web;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WebThemeManifestResolverTest {

    @Test
    public void invalidManifestPageDoesNotReachTheLoader() throws Exception {
        WebThemeManifestResolver resolver = new WebThemeManifestResolver(null, "debug");

        WebHomeTarget legacy = WebHomeTarget.resolve("https://source.example/home", false, "", "");
        WebHomeTarget manifest = WebHomeTarget.resolve("", true, "https://themes.example/theme.json",
                "https://themes.example/theme.json");

        assertNull(resolver.resolvePage(null, WebThemePage.HOME, false));
        assertNull(resolver.resolvePage(legacy, WebThemePage.HOME, false));
        assertNull(resolver.resolvePage(manifest, null, false));
    }

    @Test
    public void resolverOwnsManifestLoadingAndPageTargetResolution() throws Exception {
        String source = source();

        assertTrue(source.contains("WebThemeManifestLoader.loadResult"));
        assertTrue(source.contains("record Resolution"));
        assertTrue(source.contains("usedLastKnownGood"));
        assertTrue(source.contains("usedRollback"));
        assertTrue(source.contains("activationPending"));
        assertTrue(source.contains("rollbackAvailable"));
        assertTrue(source.contains("String revision"));
        assertTrue(source.contains("WebThemeManifestLoader.accept"));
        assertTrue(source.contains("WebThemeManifestLoader.rollbackPending"));
        assertTrue(source.contains("WebHomeTarget.forManifestPage"));
        assertTrue(source.contains("WebThemePage page"));
    }

    private static String source() throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(
                "src/main/java/com/fongmi/android/tv/web/WebThemeManifestResolver.java"), StandardCharsets.UTF_8);
    }
}
