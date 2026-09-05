package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerDisplaySettingSyncTest {

    @Test
    public void settingPagesUsePlaybackDisplayPreferences() throws Exception {
        assertSettingPageUsesDisplayPreferences(read(sourcePath("mobile", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SettingPlayerFragment.java"))));
        assertSettingPageUsesDisplayPreferences(read(sourcePath("leanback", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingPlayerActivity.java"))));
    }

    @Test
    public void playerOsdArrayMatchesPlaybackDisplayOptions() throws Exception {
        assertPlayerOsdArrayIncludesAllDisplayOptions(read(mainResPath().resolve(Path.of("values", "strings.xml"))));
        assertPlayerOsdArrayIncludesAllDisplayOptions(read(mainResPath().resolve(Path.of("values-zh-rCN", "strings.xml"))));
        assertPlayerOsdArrayIncludesAllDisplayOptions(read(mainResPath().resolve(Path.of("values-zh-rTW", "strings.xml"))));
    }

    @Test
    public void playerOsdControllerHonorsResolutionDisplayToggle() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("private void setTopLeft(PlayerManager player)");
        assertTrue("PlayerOsdController is missing setTopLeft", method >= 0);
        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);
        assertTrue("Player OSD should honor the shared resolution display switch", body.contains("PlayerSetting.isOsdResolution()"));
        assertTrue("Player OSD should allow title and resolution to be toggled independently", body.contains("showTitle") && body.contains("showResolution"));
    }

    @Test
    public void playerOsdControllerForcesPersistentTextWhite() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("private void setTextSize(float sp)");
        assertTrue("PlayerOsdController is missing setTextSize", method >= 0);
        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);

        assertTrue("top-left OSD text must stay white over themed TMDB surfaces", body.contains("topLeft.setTextColor(0xFFFFFFFF)"));
        assertTrue("top-right OSD text must stay white over themed TMDB surfaces", body.contains("topRight.setTextColor(0xFFFFFFFF)"));
        assertTrue("bottom-left OSD text must stay white over themed TMDB surfaces", body.contains("bottomLeft.setTextColor(0xFFFFFFFF)"));
        assertTrue("bottom-right OSD text must stay white over themed TMDB surfaces", body.contains("bottomRight.setTextColor(0xFFFFFFFF)"));
    }

    @Test
    public void playerOsdControllerCanSuppressPersistentCornerLabels() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("public void setPersistentSuppressed(boolean persistentSuppressed)");
        int render = source.indexOf("private boolean render()");

        assertTrue("PlayerOsdController must expose persistent display suppression for embedded mobile players", method >= 0);
        assertTrue("persistent suppression should hide title/time/progress/traffic/mini labels while keeping diagnostics available",
                render >= 0
                        && source.indexOf("if (persistentSuppressed)", render) > render
                        && source.indexOf("hidePersistent();", render) > render
                        && source.indexOf("setDiagnosticsPanel(player);", render) > render);
    }

    @Test
    public void ffmpegModeIsVisibleOnlyForExoKernel() throws Exception {
        assertFfmpegModeVisibility(read(sourcePath("mobile", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SettingPlayerFragment.java"))));
        assertFfmpegModeVisibility(read(sourcePath("leanback", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingPlayerActivity.java"))));
    }

    @Test
    public void backupIncludesPlaybackDisplayPreferences() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "bean", "Backup.java")));
        assertTrue(source.contains("\"display_time\""));
        assertTrue(source.contains("\"display_traffic\""));
        assertTrue(source.contains("\"display_size\""));
        assertTrue(source.contains("\"display_progress\""));
        assertTrue(source.contains("\"display_mini\""));
        assertTrue(source.contains("\"display_title\""));
    }

    @Test
    public void backupIncludesFfmpegModePreference() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "bean", "Backup.java")));
        assertTrue(source.contains("\"ffmpeg_mode\""));
    }

    private static void assertFfmpegModeVisibility(String source) {
        String visibility = "private void setFfmpegModeVisibility()";
        assertTrue(source.contains(visibility));
        assertTrue(source.contains("PlayerSetting.getPlayer() == PlayerSetting.EXO"));
        assertTrue(source.contains("mBinding.ffmpegMode.setVisibility(visible ? View.VISIBLE : View.GONE)"));
        assertTrue(count(source, "setFfmpegModeVisibility();") >= 2);
    }

    private static int count(String source, String token) {
        int count = 0;
        for (int from = 0; (from = source.indexOf(token, from)) >= 0; from += token.length()) count++;
        return count;
    }

    private static void assertSettingPageUsesDisplayPreferences(String source) {
        assertTrue(source.contains("PlayerSetting.getDisplayChecked()"));
        assertTrue(source.contains("PlayerSetting.putDisplayChecked(checked)"));
        assertFalse(source.contains("PlayerSetting.isOsdTitle()"));
        assertFalse(source.contains("PlayerSetting.putOsdTitle("));
    }

    private static void assertPlayerOsdArrayIncludesAllDisplayOptions(String source) {
        String array = array(source, "select_player_osd");
        assertOrdered(array, "@string/display_time", "@string/display_traffic", "@string/display_size", "@string/display_progress", "@string/display_mini", "@string/display_title");
    }

    private static void assertOrdered(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int index = source.indexOf(token);
            assertTrue(token + " is missing", index >= 0);
            assertTrue(token + " is out of order", index > previous);
            previous = index;
        }
    }

    private static String array(String source, String name) {
        String startTag = "<string-array name=\"" + name + "\">";
        int start = source.indexOf(startTag);
        assertTrue(name + " array is missing", start >= 0);
        int end = source.indexOf("</string-array>", start);
        assertTrue(name + " array is not closed", end > start);
        return source.substring(start, end);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path mainJavaPath() {
        return sourcePath("main", "java");
    }

    private static Path mainResPath() {
        return sourcePath("main", "res");
    }

    private static Path sourcePath(String sourceSet, String kind) {
        Path moduleRelative = Path.of("src", sourceSet, kind);
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", sourceSet, kind);
    }
}
