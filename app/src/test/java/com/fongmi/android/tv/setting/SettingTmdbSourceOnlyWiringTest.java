package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingTmdbSourceOnlyWiringTest {

    @Test
    public void configuredModeDoesNotRequireOnlineTmdbKey() throws Exception {
        String source = readMain(Path.of("com", "fongmi", "android", "tv", "setting", "Setting.java"));
        int methodStart = source.indexOf("public static int getDetailOpenMode()");
        int methodEnd = source.indexOf("public static void putDetailOpenMode", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("return mode;"));
        assertFalse(method.contains("isTmdbReady()"));
        assertTrue(source.contains("public static boolean isTmdbDetailModeConfigured()")
                && source.contains("return isTmdbMode(getDetailOpenMode()) && getTmdbModel() == TMDB_MODEL_NATIVE;"));
        assertTrue(source.contains("public static boolean isTmdbDetailPage()")
                && source.contains("TmdbConfig.objectFrom(getTmdbConfig()).isReady()"));
    }

    @Test
    public void mobileSettingsSavesTmdbModeWithoutKeyPrompt() throws Exception {
        String source = readFlavor("mobile", Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SettingTmdbFragment.java"));
        String method = method(source, "private void setDetailOpenMode(View view)", "private int getDetailOpenModeIndex()");

        assertTrue(method.contains("Setting.putDetailOpenMode(mode);"));
        assertFalse(method.contains("isTmdbReady()"));
        assertFalse(method.contains("detail_tmdb_need_key"));
        assertFalse(method.contains("TmdbSourceDialog"));
    }

    @Test
    public void leanbackSettingsSavesTmdbModeWithoutKeyPrompt() throws Exception {
        String source = readFlavor("leanback", Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingTmdbActivity.java"));
        String method = method(source, "private void setDetailOpenMode(View view)", "private int getDetailOpenModeIndex()");

        assertTrue(method.contains("Setting.putDetailOpenMode(mode);"));
        assertFalse(method.contains("isTmdbReady()"));
        assertFalse(method.contains("detail_tmdb_need_key"));
        assertFalse(method.contains("TmdbSourceDialog"));
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String readMain(Path relative) throws Exception {
        return Files.readString(root().resolve("app").resolve("src").resolve("main").resolve("java").resolve(relative), StandardCharsets.UTF_8);
    }

    private static String readFlavor(String flavor, Path relative) throws Exception {
        return Files.readString(root().resolve("app").resolve("src").resolve(flavor).resolve("java").resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path root() {
        return Files.exists(Path.of("app", "src", "main")) ? Path.of(".") : Path.of("..");
    }
}
