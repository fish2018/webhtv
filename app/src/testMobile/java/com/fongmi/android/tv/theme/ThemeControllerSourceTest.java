package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contract checks for the low-API stage-C binding path. */
public class ThemeControllerSourceTest {

    @Test
    public void controllerKeepsSemanticAndPlayerBindingsSeparate() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(source.contains("applyPlayerControls(view, tokens)"));
        assertTrue(source.contains("DefaultTimeBar"));
        assertTrue(source.contains("setPlayedColor(tokens.primary())"));
        assertTrue(source.contains("playerIconColors(tokens)"));
        assertTrue(source.contains("isPlayerRoot"));
        assertTrue(source.contains("tokens.surface()"));
        assertTrue(source.contains("tokens.onSurfaceVariant()"));
    }

    @Test
    public void bottomNavigationKeepsDefaultUncheckedStyleAndOnlyHighlightsSelection() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java");

        assertTrue(source.contains("navigation.setBackgroundTintList(null)"));
        assertTrue(source.contains("ColorStateList navigationColors = new ColorStateList("));
        assertTrue(source.contains("new int[][]{{android.R.attr.state_checked}, {}}"));
        assertTrue(source.contains("new int[]{tokens.primary(), Color.WHITE}"));
        assertTrue(source.contains("navigation.setItemIconTintList(navigationColors)"));
        assertTrue(source.contains("navigation.setItemTextColor(navigationColors)"));
        assertTrue(source.contains("navigation.setItemTextColor(navigationColors);\n            return;"));
    }

    @Test
    public void bothActivityTargetsBindAfterDynamicChildrenAreInflated() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        assertTrue(mobile.contains("ThemeController.applyNightMode(this)"));
        assertTrue(mobile.contains("ThemeController.dynamicColor(this)"));
        assertTrue(mobile.contains("initEvent();\n        // Some detail/player controls"));
        assertTrue(leanback.contains("ThemeController.applyNightMode(this)"));
        assertTrue(leanback.contains("initEvent();\n        // Some detail/player controls"));
    }

    @Test
    public void wallpaperScrimIsASeparateNonInteractiveLayer() throws Exception {
        String wall = read("app/src/main/res/layout/view_wall.xml");
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/custom/CustomWallView.java");
        assertTrue(wall.contains("@+id/themeScrim"));
        assertTrue(wall.contains("android:clickable=\"false\""));
        assertTrue(wall.contains("android:focusable=\"false\""));
        assertTrue(source.contains("applyThemeScrim()"));
        assertTrue(source.contains("ThemeController.wallpaperScrim"));
    }

    @Test
    public void themeColorMasterSwitchDefaultsOffAndShortCircuitsRuntime() throws Exception {
        String setting = read("app/src/main/java/com/fongmi/android/tv/setting/Setting.java");
        String controller = read("app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        String wall = read("app/src/main/java/com/fongmi/android/tv/ui/custom/CustomWallView.java");
        String mobileAppearance = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AppearanceDialog.java");
        String leanbackAppearance = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AppearanceDialog.java");

        assertTrue(setting.contains("public static boolean isThemeColorEnabled()"));
        assertTrue(setting.contains("Prefers.getBoolean(\"theme_color_enabled\")"));
        assertTrue(setting.contains("public static void putThemeColorEnabled(boolean enabled)"));
        assertTrue(setting.contains("Prefers.put(\"theme_color_enabled\", enabled)"));

        assertTrue(controller.contains("public static void applyNightMode(Context context) {\n"
                + "        if (!isCustomThemeEnabled()) {"));
        assertTrue(controller.contains("AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;"));
        assertTrue(controller.contains("private static boolean isCustomThemeEnabled()"));
        assertTrue(controller.contains("if (!Setting.isThemeColorEnabled()) return false;"));
        assertTrue(controller.contains("ThemeProfile.SEED_NONE.equals(profile.seedSource)"));
        assertTrue(controller.contains("return !isEmpty(profile.colors == null ? null : profile.colors.light)"));
        assertTrue(controller.contains("public static ThemeTokens resolve(Context context) {\n"
                + "        if (!isCustomThemeEnabled()) return disabledTokens();"));
        assertTrue(controller.contains("public static int dynamicColor(Context context) {\n"
                + "        if (!isCustomThemeEnabled()) return 0;"));
        assertTrue(controller.contains("public static void apply(Activity activity) {\n"
                + "        if (!isCustomThemeEnabled()) return;"));
        assertTrue(controller.contains("public static void applyLeanback(Activity activity) {\n"
                + "        if (!isCustomThemeEnabled()) return;"));
        assertTrue(controller.contains("public static void apply(View root, ThemeTokens tokens) {\n"
                + "        if (!isCustomThemeEnabled() || root == null || tokens == null) return;"));
        assertTrue(controller.contains("public static void applyLeanback(View root, ThemeTokens tokens) {\n"
                + "        if (!isCustomThemeEnabled() || root == null || tokens == null) return;"));
        assertTrue(controller.contains("public static int wallpaperScrim(ThemeTokens tokens) {\n"
                + "        if (!isCustomThemeEnabled()) return 0;"));

        assertTrue(wall.contains("private void applyThemeScrim() {\n"
                + "        if (!Setting.isThemeColorEnabled()) return;"));
        assertTrue(mobileAppearance.contains("private String getThemeText() {\n"
                + "        if (!Setting.isThemeColorEnabled()) return getString(R.string.setting_off);"));
        assertTrue(leanbackAppearance.contains("private String getThemeText() {\n"
                + "        if (!Setting.isThemeColorEnabled()) return getString(R.string.setting_off);"));
    }

    @Test
    public void videoActivityPlayersUseBlackShutterAcrossMobileLayouts() throws Exception {
        String[] layouts = {
                "app/src/mobile/res/layout/activity_video.xml",
                "app/src/mobile/res/layout-land/activity_video.xml",
                "app/src/mobile/res/layout-sw600dp/activity_video.xml",
                "app/src/mobile/res/layout-sw600dp-land/activity_video.xml"
        };

        for (String path : layouts) {
            String layout = read(path);
            assertTrue(path, layout.contains("app:shutter_background_color=\"@android:color/black\""));
            assertTrue(path, layout.contains("app:surface_type=\"none\""));
        }
    }

    @Test
    public void tmdbInlinePlayerUsesBlackShutterWhileVideoIsUnavailable() throws Exception {
        String layout = read("app/src/main/res/layout/activity_tmdb_detail.xml");

        assertTrue(layout.contains("app:shutter_background_color=\"@android:color/black\""));
        assertTrue(layout.contains("app:surface_type=\"none\""));
    }

    @Test
    public void colorfulDetailOwnsItsBackgroundInsteadOfGlobalThemeTraversal() throws Exception {
        String base = read("app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        String detail = read("app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");

        assertTrue(base.contains("if (applyGlobalTheme()) ThemeController.apply(this);"));
        assertTrue(base.contains("protected boolean applyGlobalTheme()"));
        assertTrue(detail.contains("protected boolean applyGlobalTheme()"));
        assertTrue(detail.contains("return !isCinemaStyle();"));
        assertTrue(detail.contains("return isCinemaStyle() ? ThemeColors.cinema(lightTheme) : colors;"));
    }

    @Test
    public void highlightColorRefreshDoesNotRecreateColorfulDetailActivity() throws Exception {
        String editor = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ThemeEditorDialog.java");
        String base = read("app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        String detail = read("app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");

        assertTrue(editor.contains("setHighlightColor(color)"));
        assertTrue(editor.contains("RefreshEvent.theme()"));
        assertTrue(detail.contains("return isCinemaStyle() ? ThemeColors.cinema(lightTheme) : colors;"));
        assertTrue(base.contains("if (event.getType() == RefreshEvent.Type.THEME && preserveDetailThemeState()) return;"));
        assertTrue(base.contains("protected boolean preserveDetailThemeState()"));
        assertTrue(detail.contains("protected boolean preserveDetailThemeState()"));
    }

    @Test
    public void mobileThemeEditorShowsAndPersistsMasterSwitch() throws Exception {
        String layout = read("app/src/mobile/res/layout/dialog_theme_editor.xml");
        String editor = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ThemeEditorDialog.java");

        assertTrue(layout.contains("@+id/themeEnabled"));
        assertTrue(layout.contains("@string/theme_enabled"));
        assertTrue(editor.contains("binding.themeEnabled.setChecked(Setting.isThemeColorEnabled())"));
        assertFalse(editor.contains("binding.themeEnabled.setChecked(false)"));
        assertTrue(editor.contains("Setting.putThemeColorEnabled(binding.themeEnabled.isChecked())"));
        assertTrue(editor.contains("binding.primaryRow.setOnClickListener"));
        assertTrue(editor.contains("binding.backgroundRow.setOnClickListener"));
        assertTrue(editor.contains("binding.surfaceRow.setOnClickListener"));

        String picker = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ThemeColorPickerDialog.java");
        assertTrue(picker.contains("createPresetPalette()"));
        assertTrue(picker.contains("binding.getRoot().addView(createPresetPalette(), 1)"));
        assertTrue(picker.contains("binding.input.setText(ThemeColorUtil.format(color))"));
    }

    @Test
    public void highlightOnlyProfilePreservesExistingSurfacesAndText() throws Exception {
        String controller = read("app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java");

        assertTrue(controller.contains("if (isHighlightOnly(profile))"));
        assertTrue(controller.contains("applyHighlightView(root, tokens)"));
        assertTrue(controller.contains("private static boolean hasOnlyPrimary"));
        assertTrue(controller.contains("current.getDefaultColor()"));
        assertFalse(controller.contains("applyHighlightView(root, tokens);\n        root.setBackgroundColor"));
    }

    @Test
    public void customAccentOnlyFillsExplicitPrimaryButtons() throws Exception {
        String controller = read("app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java");

        assertTrue(controller.contains("boolean primary = name.contains(\"apply\")"));
        assertTrue(controller.contains("if (!primary && !secondary) return;"));
        assertTrue(controller.contains("int background = primary ? tokens.primary() : tokens.surfaceElevated();"));
        assertTrue(controller.contains("int foreground = primary ? tokens.onPrimary() : tokens.primary();"));
    }

    @Test
    public void videoActivityPreservesPlayerOwnedColors() throws Exception {
        String activity = read("app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

        assertTrue(activity.contains("protected boolean applyGlobalTheme()"));
        assertTrue(activity.contains("protected boolean applyGlobalTheme() {\n        return false;"));
    }

    @Test
    public void playerControlRootsAreExplicitForMobileLayouts() throws Exception {
        for (String file : new String[]{
                "app/src/mobile/res/layout/view_control_vod.xml",
                "app/src/mobile/res/layout/view_control_live.xml"}) {
            assertTrue(read(file).contains("@+id/playerControlRoot"));
        }
    }

    private String read(String path) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
