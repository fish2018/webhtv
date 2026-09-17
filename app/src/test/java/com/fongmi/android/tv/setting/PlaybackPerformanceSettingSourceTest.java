package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackPerformanceSettingSourceTest {

    @Test
    public void consolidatedProfileIdsKeepCustomValueStable() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));
        String method = methodBody(source, "public static void applyAuto()", "public static void applyRecommended()");

        assertTrue(source.contains("PROFILE_CUSTOM = 2"));
        assertTrue(source.contains("PROFILE_LIGHTWEIGHT = 3"));
        assertTrue(source.contains("PROFILE_AUTO = 4"));
        assertFalse(source.contains("PROFILE_ORIGINAL"));
        assertTrue(method.contains("putCurrentProfile(PROFILE_AUTO)"));
    }

    @Test
    public void consolidatedPresetsApplyAutoAndLightweightDefaults() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));
        String auto = methodBody(source, "public static void applyAuto()", "public static void applyRecommended()");
        String lightweight = methodBody(source, "public static void applyLightweight()", "private static void applyLightweightProfile");
        String kernelSource = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "KernelPerformanceSetting.java"));
        String kernelPreset = methodBody(kernelSource, "public static void applyPreset(int kernel, int profile)", "static void applyPreloadPreset");

        assertContainsAll(auto,
                "clearOverrides(kernel)",
                "applyAutoProfile(kernel)",
                "putCurrentProfile(PROFILE_AUTO)");
        assertContainsAll(lightweight,
                "clearOverrides(kernel)",
                "applyLightweightProfile(kernel)",
                "putCurrentProfile(PROFILE_LIGHTWEIGHT)");
        assertContainsAll(kernelPreset,
                "profile == PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT",
                "profile == PlaybackPerformanceSetting.PROFILE_COMPATIBLE");
        assertFalse(kernelPreset.contains("PROFILE_ORIGINAL"));
    }

    @Test
    public void performanceDialogExposesConsolidatedPresets() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PlaybackPerformanceDialog.java"));

        assertTrue(source.contains("PlaybackPerformanceSetting.applyAuto()"));
        assertTrue(source.contains("PlaybackPerformanceSetting.applyLightweight()"));
        assertTrue(source.contains("PlaybackProfileMergePolicy.selectableProfiles"));
        assertFalse(source.contains("PlaybackPerformanceSetting.applyOriginal()"));

        String catalog = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceCatalog.java"));
        assertTrue(catalog.contains("profileDescription(kernel, recommendedMerged)"));
        assertTrue(catalog.contains("自动档"));
    }

    @Test
    public void consolidatedProfilesUseShortChineseLabels() throws Exception {
        String setting = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));
        String strings = read(sourcePath("main", "res", "values-zh-rCN", "strings.xml"));

        assertTrue(setting.contains("yield count == 0 ? \"自动\""));
        assertTrue(setting.contains("PROFILE_LIGHTWEIGHT -> \"轻量\""));
        assertTrue(strings.contains("<string name=\"player_performance_auto\">自动</string>"));
        assertTrue(strings.contains("<string name=\"player_performance_lightweight\">轻量</string>"));
    }

    @Test
    public void performanceDialogHighlightsSelectedProfile() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PlaybackPerformanceDialog.java"));
        String createView = methodBody(source, "private View createView", "private void showHelpDialog");
        String refresh = methodBody(source, "private void refresh()", "private void reset()");
        String sync = methodBody(source, "private void syncProfileTabs(TabLayout tabs)", "private boolean profileTabsMatch");
        String profileAt = methodBody(source, "private int profileAt", "private int profilePosition");

        assertContainsAll(source,
                "private TabLayout createProfileTabs()",
                "tabs.selectTab(position < 0 ? null : tabs.getTabAt(position))",
                "PlaybackProfileMergePolicy.selectableProfiles");
        assertTrue(createView.contains("profileTabs = createProfileTabs();"));
        assertTrue(refresh.contains("syncProfileTabs();"));
        assertTrue(sync.contains("profilePosition(PlaybackPerformanceSetting.getProfile())"));
        assertTrue(profileAt.contains("PlaybackPerformanceSetting.PROFILE_AUTO"));
    }

    @Test
    public void performanceDialogButtonsKeepLabelsVisible() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PlaybackPerformanceDialog.java"));
        String actionButton = methodBody(source, "private MaterialButton actionButton", "private MaterialButton closeButton");

        assertContainsAll(actionButton,
                "button.setMaxLines(1)",
                "button.setIncludeFontPadding(false)",
                "button.setPadding(dp(6), 0, dp(6), 0)",
                "TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(",
                "TypedValue.COMPLEX_UNIT_SP");
    }

    @Test
    public void exoNonEnhancedProfilesKeepMedia3DefaultLoadControl() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "exo", "ExoUtil.java"));
        String buildPlayer = methodBody(source, "public static ExoPlayer buildPlayer", "public static MediaItem getMediaItem");

        assertTrue(buildPlayer.contains("if (PlaybackPerformanceSetting.isHighBufferEnabled()) builder.setLoadControl(buildEnhancedLoadControl());"));
        assertFalse(buildPlayer.contains("buildLoadControl()"));
    }

    private static void assertContainsAll(String source, String... values) {
        for (String value : values) assertTrue("Missing: " + value, source.contains(value));
    }

    private static void assertDoesNotContainAny(String source, String... values) {
        for (String value : values) assertFalse("Unexpected: " + value, source.contains(value));
    }

    private static String methodBody(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing method: " + start, from >= 0);
        assertTrue("Missing method boundary: " + end, to > from);
        return source.substring(from, to);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath(String... parts) {
        Path path = Path.of("src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        if (Files.exists(path)) return path;
        path = Path.of("app", "src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        return path;
    }
}
