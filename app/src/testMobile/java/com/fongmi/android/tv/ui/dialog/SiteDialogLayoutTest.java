package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class SiteDialogLayoutTest {

    @Test
    public void siteDialogUsesFullScreenLayout() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "SiteDialog.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("Site dialog window should use MATCH_PARENT width for full screen",
                source.contains("params.width = WindowManager.LayoutParams.MATCH_PARENT"));
        assertTrue("Site dialog window should use MATCH_PARENT height for full screen",
                source.contains("params.height = WindowManager.LayoutParams.MATCH_PARENT"));
        assertTrue("Site dialog window size must be applied through setLayout on TV devices",
                source.contains("window.setLayout(params.width, params.height);"));
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }
}
