package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WebHomeThemeDialogTest {

    @Test
    public void remoteConfirmationIsRequiredUntilTheExactUrlWasTrustedLocally() {
        assertTrue(WebHomeThemeDialog.requiresRemoteConfirmation("",
                "https://theme.example/one"));
        assertTrue(WebHomeThemeDialog.requiresRemoteConfirmation("https://theme.example/one",
                "https://theme.example/two"));
        assertFalse(WebHomeThemeDialog.requiresRemoteConfirmation("https://theme.example/one",
                " https://theme.example/one "));
    }

    @Test
    public void customRemoteManifestOffersControlledRecoveryActions() throws Exception {
        String source = source();

        assertTrue(source.contains("setNeutralButton(R.string.setting_web_home_theme_recovery"));
        assertTrue(source.contains("WebThemeManifestRollback.action(activity, url)"));
        assertTrue(source.contains("WebThemeManifestRollback.apply(activity, url, action)"));
        assertTrue(source.contains("RefreshEvent.home();"));
    }

    private static String source() throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(
                "src/main/java/com/fongmi/android/tv/ui/dialog/WebHomeThemeDialog.java"),
                StandardCharsets.UTF_8);
    }
}
