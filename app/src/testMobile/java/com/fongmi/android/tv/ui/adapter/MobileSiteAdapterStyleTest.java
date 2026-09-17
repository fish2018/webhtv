package com.fongmi.android.tv.ui.adapter;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MobileSiteAdapterStyleTest {

    @Test
    public void siteLabelsKeepSelectionDialogContrastAcrossModes() throws Exception {
        Path moduleRoot = findModuleRoot();
        String adapter = read(moduleRoot.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "adapter", "SiteAdapter.java")));
        String theme = read(moduleRoot.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "helper", "SiteDialogTheme.java")));

        assertTrue("Site labels should stay fully opaque in block mode",
                adapter.contains("holder.binding.text.setAlpha(1.0f);"));
        assertFalse("Disabled search-mode labels should use the same text color as selectable site labels",
                theme.contains("state_enabled"));
        assertTrue("The adapter must use the palette covered by this test", adapter.contains("theme.apply(binding.text)"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findModuleRoot() {
        Path currentModule = Path.of(".");
        if (Files.exists(currentModule.resolve(Path.of("src", "mobile")))) return currentModule;
        return Path.of("app");
    }
}
