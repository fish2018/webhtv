package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class UpdateDialogChannelVisibilityTest {

    @Test
    public void dualChannelDialogKeepsBothChannelRowsVisibleInitially() throws Exception {
        assertDualChannelSelectionStartsCollapsed("mobile");
        assertDualChannelSelectionStartsCollapsed("leanback");
    }

    private static void assertDualChannelSelectionStartsCollapsed(String flavor) throws Exception {
        String source = read(findFlavorJavaPath(flavor).resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "UpdateDialog.java")));
        String method = between(source, "public UpdateDialog selected", "public UpdateDialog listener");

        assertTrue(flavor + " should detect whether the beta channel is available before choosing the initial expansion state",
                method.contains("boolean betaAvailable = hasBeta();"));
        assertTrue(flavor + " should collapse stable notes when beta is available so both channel rows remain visible",
                method.contains("this.stableExpanded = !betaAvailable && !Update.CHANNEL_BETA.equals(selected);"));
        assertTrue(flavor + " should only expand beta when it is available and explicitly selected",
                method.contains("this.betaExpanded = betaAvailable && Update.CHANNEL_BETA.equals(selected);"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        return source.substring(from, to);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findFlavorJavaPath(String flavor) {
        Path moduleRelative = Path.of("src", flavor, "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", flavor, "java");
    }
}
