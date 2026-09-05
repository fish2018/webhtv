package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ControlDialogOsdSettingsTest {

    private static final List<String> DISPLAY_IDS = List.of(
            "displayTime",
            "displayTraffic",
            "displaySize",
            "displayProgress",
            "displayMini",
            "displayTitle",
            "displayParams"
    );

    private static final List<String> LIVE_DISPLAY_IDS = List.of(
            "displayTime",
            "displayTraffic",
            "displaySize",
            "displayTitle",
            "displayParams"
    );

    @Test
    public void everyPlayerControlDialogEmbedsOsdSettings() throws Exception {
        assertControlDialogEmbedsOsdSettings("mobile");
        assertControlDialogEmbedsOsdSettings("leanback");
    }

    @Test
    public void mobileNativePlayerRemovesTheSeparateDisplayEntry() throws Exception {
        Path root = moduleRoot();
        String controls = read(root.resolve(Path.of("src", "mobile", "res", "layout", "view_control_vod.xml")));
        String activity = read(root.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertFalse("the player toolbar must not keep a duplicate screen-display button", controls.contains("@+id/display"));
        assertFalse("the player toolbar must no longer open the display-only dialog", activity.contains("DisplayDialog.showPlayerOsd"));
    }

    @Test
    public void tmdbLeanbackPlayerKeepsACompleteOsdSettingsEntry() throws Exception {
        Path root = moduleRoot();
        String fusionLayout = read(root.resolve(Path.of("src", "main", "res", "layout", "activity_tmdb_detail.xml")));
        String activity = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));
        String dialogLayout = read(root.resolve(Path.of("src", "main", "res", "layout", "dialog_display.xml")));
        String dialog = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "DisplayDialog.java")));

        int fusionDisplay = fusionLayout.indexOf("android:id=\"@+id/playerDisplay\"");
        int fusionDisplayEnd = fusionLayout.indexOf("/>", fusionDisplay);
        assertTrue("leanback fusion playback must keep its standalone screen-display action",
                fusionDisplay >= 0 && fusionDisplayEnd > fusionDisplay
                        && !fusionLayout.substring(fusionDisplay, fusionDisplayEnd).contains("android:visibility=\"gone\""));
        assertMethodContains(activity, "public void showDisplay()", "showInlineDisplay();");
        assertMethodContains(activity, "private void showInlineDisplay()",
                "DisplayDialog.showPlayerOsd", "inlineControlDialogDisplayChanged();");
        assertTrue("the shared player OSD dialog must expose playback parameters", dialogLayout.contains("@+id/display_params"));
        assertMethodContains(dialog, "private void initPlayerOsdView()", "PlayerSetting.isOsdDiagnostics()");
        assertMethodContains(dialog, "private void initPlayerOsdEvent()", "PlayerSetting.putOsdDiagnostics");
    }

    private static void assertControlDialogEmbedsOsdSettings(String flavor) throws Exception {
        Path root = moduleRoot();
        String layout = read(root.resolve(Path.of("src", flavor, "res", "layout", "dialog_control.xml")));
        String source = read(root.resolve(Path.of("src", flavor, "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java")));

        assertTrue(flavor + " control dialog must label the embedded section as player OSD settings",
                layout.contains("android:text=\"@string/player_osd\""));
        for (String id : DISPLAY_IDS) {
            assertTrue(flavor + " control dialog is missing " + id, layout.contains("@+id/" + id));
        }
        assertTrue(flavor + " control dialog must initialize the display toggles",
                source.contains("PlayerSetting.getDisplayChecked()"));
        assertTrue(flavor + " control dialog must persist display changes",
                source.contains("PlayerSetting.putDisplayChecked(checked)"));
    }

    @Test
    public void nativeAndFusionPlayersRefreshOsdAfterASettingChanges() throws Exception {
        Path root = moduleRoot();
        String mobileActivity = read(root.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String leanbackActivity = read(root.resolve(Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String mobileDialog = read(root.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java")));
        String tmdbActivity = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));

        assertTrue("mobile native playback must restart the OSD renderer", mobileActivity.contains("public void onDisplayChanged()") && mobileActivity.contains("mOsd.start();"));
        assertTrue("leanback native playback must restart the OSD renderer", leanbackActivity.contains("public void onDisplayChanged()") && leanbackActivity.contains("mOsd.start();"));
        assertTrue("fusion playback must receive the display-change callback", mobileDialog.contains("activity.inlineControlDialogDisplayChanged();"));
        assertTrue("fusion playback must restart its inline OSD renderer", tmdbActivity.contains("public void inlineControlDialogDisplayChanged()") && tmdbActivity.contains("inlineOsd.start();"));
    }

    @Test
    public void displayChangeCallbacksApplyThePlayParamsPreference() throws Exception {
        Path root = moduleRoot();
        String mobileActivity = read(root.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String leanbackActivity = read(root.resolve(Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String tmdbActivity = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));

        assertMethodContains(mobileActivity, "public void onDisplayChanged()", "mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());");
        assertMethodContains(mobileActivity, "public void onDisplayChanged()", "setPlayParamsState();");
        assertMethodContains(leanbackActivity, "public void onDisplayChanged()",
                "mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());", "setPlayParamsState();");
        assertMethodContains(tmdbActivity, "public void inlineControlDialogDisplayChanged()", "inlineOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());");
    }

    @Test
    public void mobileLivePlayerSettingsEmbedOsdSettingsAndRefreshTheRenderer() throws Exception {
        Path root = moduleRoot();
        String layout = read(root.resolve(Path.of("src", "mobile", "res", "layout", "dialog_live_control.xml")));
        String dialog = read(root.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "LiveControlDialog.java")));
        String activity = read(root.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java")));

        assertTrue("live player settings must label the embedded section as player OSD settings",
                layout.contains("android:text=\"@string/player_osd\""));
        for (String id : LIVE_DISPLAY_IDS) {
            assertTrue("live player settings are missing " + id, layout.contains("@+id/" + id));
        }
        assertFalse("live player settings must not expose unsupported progress", layout.contains("@+id/displayProgress"));
        assertFalse("live player settings must not expose unsupported mini progress", layout.contains("@+id/displayMini"));
        assertTrue("live player settings must initialize only supported display toggles", dialog.contains("PlayerSetting.getLiveDisplayChecked()"));
        assertTrue("live player settings must persist only supported display changes", dialog.contains("PlayerSetting.putLiveDisplayChecked(checked)"));
        assertTrue("live player settings must notify playback after a display change", dialog.contains("listener().onLiveDisplayChanged();"));
        assertMethodContains(activity, "public void onLiveDisplayChanged()",
                "mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());", "mOsd.start();");
        assertMethodContains(activity, "protected void onStart()",
                "mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());", "mOsd.start();");
    }

    @Test
    public void leanbackLiveControlsExposeOsdSettingsAndRefreshTheRenderer() throws Exception {
        Path root = moduleRoot();
        String layout = read(root.resolve(Path.of("src", "leanback", "res", "layout", "view_control_live_action.xml")));
        String activity = read(root.resolve(Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java")));

        assertTrue("leanback live controls must expose an OSD settings action", layout.contains("android:id=\"@+id/osdSettings\""));
        assertTrue("leanback live controls must label the action as player OSD settings", layout.contains("android:text=\"@string/player_osd\""));
        assertMethodContains(activity, "protected void initEvent()", "mBinding.control.action.osdSettings.setOnClickListener(view -> onOsd());");
        assertMethodContains(activity, "private void onOsd()",
                "R.array.select_live_player_osd", "PlayerOsdDialog.show", "PlayerSetting.getLiveDisplayChecked()", "PlayerSetting.putLiveDisplayChecked(checked)",
                "mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());", "mOsd.start();");
    }

    @Test
    public void leanbackLiveControlsDoNotShadowPlayerOsdContainer() throws Exception {
        Path root = moduleRoot();
        String activityLayout = read(root.resolve(Path.of("src", "leanback", "res", "layout", "activity_live.xml")));
        String actionLayout = read(root.resolve(Path.of("src", "leanback", "res", "layout", "view_control_live_action.xml")));

        assertTrue("leanback live activity must keep the player OSD container ID", activityLayout.contains("android:id=\"@+id/osd\""));
        assertFalse("leanback live controls must not shadow the player OSD container ID", actionLayout.contains("android:id=\"@+id/osd\""));
        assertTrue("leanback live controls must use a distinct OSD settings ID", actionLayout.contains("android:id=\"@+id/osdSettings\""));
    }

    @Test
    public void liveOsdSettingsMapOnlySupportedGlobalOptions() throws Exception {
        Path root = moduleRoot();
        String settings = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "setting", "PlayerSetting.java")));

        assertTrue("live OSD reads must omit progress-only options",
                settings.contains("return new boolean[]{isDisplayTime(), isDisplayTraffic(), isDisplaySize(), isDisplayTitle(), isOsdDiagnostics()};"));
        assertMethodContains(settings, "public static void putLiveDisplayChecked(boolean[] checked)",
                "putDisplayTime(valueAt(checked, 0, isDisplayTime()));",
                "putDisplayTraffic(valueAt(checked, 1, isDisplayTraffic()));",
                "putDisplaySize(valueAt(checked, 2, isDisplaySize()));",
                "putDisplayTitle(valueAt(checked, 3, isDisplayTitle()));",
                "putOsdDiagnostics(valueAt(checked, 4, isOsdDiagnostics()));");
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void assertMethodContains(String source, String signature, String... snippets) {
        int start = source.indexOf(signature);
        assertTrue("missing method " + signature, start >= 0);
        int open = source.indexOf('{', start);
        assertTrue("missing method body for " + signature, open >= 0);
        int depth = 0;
        int end = -1;
        for (int i = open; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            else if (value == '}' && --depth == 0) {
                end = i + 1;
                break;
            }
        }
        assertTrue("unterminated method " + signature, end > open);
        String method = source.substring(start, end);
        for (String snippet : snippets) {
            assertTrue(signature + " must contain " + snippet, method.contains(snippet));
        }
    }

    private static Path moduleRoot() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return Path.of(".");
        return Path.of("app");
    }
}
