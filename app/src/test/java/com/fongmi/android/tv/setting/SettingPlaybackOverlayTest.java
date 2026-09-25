package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingPlaybackOverlayTest {

    @Test
    public void playbackOverlayDefaultsOnAndPersistsChanges() throws Exception {
        Path root = moduleRoot();
        String settingSource = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "setting", "Setting.java")));

        assertTrue(settingSource.contains("Prefers.getBoolean(\"playback_overlay_enabled\", true)"));
        assertTrue(settingSource.contains("Prefers.put(\"playback_overlay_enabled\", enabled)"));
    }

    @Test
    public void mobileSettingsAndPlayersSharePlaybackOverlayPreference() throws Exception {
        Path root = moduleRoot();
        String personalLayout = read(root.resolve(Path.of("src", "mobile", "res", "layout", "fragment_setting_personal.xml")));
        String personalSource = read(root.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "SettingPersonalFragment.java")));
        String videoSource = read(root.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String liveSource = read(root.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java")));

        assertTrue(personalLayout.contains("@+id/playbackOverlay"));
        assertTrue(personalLayout.contains("@string/setting_playback_overlay"));
        assertTrue(personalSource.contains("mBinding.playbackOverlay.setOnClickListener(this::setPlaybackOverlay)"));
        assertTrue(personalSource.contains("Setting.putPlaybackOverlayEnabled(!Setting.isPlaybackOverlayEnabled())"));
        assertPlayerAppliesOverlay(videoSource);
        assertPlayerAppliesOverlay(liveSource);

        for (String values : new String[]{"values", "values-zh-rCN", "values-zh-rTW"}) {
            String strings = read(root.resolve(Path.of("src", "main", "res", values, "strings.xml")));
            assertTrue(strings.contains("<string name=\"setting_playback_overlay\">"));
        }
    }

    @Test
    public void leanbackSettingsAndPlayersSharePlaybackOverlayPreference() throws Exception {
        Path root = moduleRoot();
        String personalLayout = read(root.resolve(Path.of("src", "leanback", "res", "layout", "activity_setting_personal.xml")));
        String personalSource = read(root.resolve(Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "SettingPersonalActivity.java")));
        String videoSource = read(root.resolve(Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String liveSource = read(root.resolve(Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java")));

        assertTrue(personalLayout.contains("@+id/playbackOverlay"));
        assertTrue(personalLayout.contains("@string/setting_playback_overlay"));
        assertTrue(personalSource.contains("mBinding.playbackOverlay.setOnClickListener(this::setPlaybackOverlay)"));
        assertTrue(personalSource.contains("Setting.putPlaybackOverlayEnabled(!Setting.isPlaybackOverlayEnabled())"));
        assertLeanbackPlayerAppliesOverlay(videoSource);
        assertLeanbackPlayerAppliesOverlay(liveSource);

        for (String name : new String[]{"view_control_vod.xml", "view_control_live.xml", "view_control_cast.xml"}) {
            String layout = read(root.resolve(Path.of("src", "leanback", "res", "layout", name)));
            assertTrue(layout.contains("android:background=\"@color/transparent\""));
        }
    }

    private static void assertLeanbackPlayerAppliesOverlay(String source) {
        assertTrue(source.contains("applyPlaybackOverlay();"));
        assertTrue(source.contains("mBinding.control.getRoot().setBackgroundResource(Setting.isPlaybackOverlayEnabled() ? R.drawable.shape_controller_scrim : R.color.transparent)"));
    }

    private static void assertPlayerAppliesOverlay(String source) {
        assertTrue(source.contains("mBinding.control.getRoot().setBackgroundResource(R.color.transparent)"));
        assertTrue(source.contains("mBinding.control.bottom.setBackgroundResource(Setting.isPlaybackOverlayEnabled() ? R.drawable.shape_controller_scrim : R.color.transparent)"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path moduleRoot() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return Path.of(".");
        return Path.of("app");
    }
}
