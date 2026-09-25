package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteDialogThemeSourceTest {
    @Test
    public void siteDialogSharesOnePaletteAcrossSearchSitesAndGroups() throws Exception {
        String dialog = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/SiteDialog.java");
        assertFalse("site selection must not force the static light dialog theme", dialog.contains("ThemeOverlay_WebHTV_LightDialog"));
        assertTrue("site selection should use the theme-aware dialog base", dialog.contains("return builder().setView(getBinding().getRoot());"));
        assertTrue(dialog.contains("SiteDialogTheme.resolve(binding.getRoot().getContext(), ThemeController.resolve(binding.getRoot().getContext()))"));
        assertTrue(dialog.contains("binding.getRoot().setBackgroundColor(theme.surface())"));
        assertTrue(dialog.contains("binding.keyword.setTextColor(theme.onSurface())"));
        assertTrue(dialog.contains("binding.keyword.setHintTextColor(theme.onSurfaceVariant())"));
        assertTrue(dialog.contains("TextViewCompat.setCompoundDrawableTintList(binding.keyword, theme.accent())"));
        assertTrue(dialog.contains("theme.tint(binding.block)"));
        assertTrue(dialog.contains("theme.tint(binding.search)"));
        assertTrue(dialog.contains("new SiteAdapter(this, theme)"));
        assertTrue(dialog.contains("new SiteGroupAdapter(this::onGroupClick, theme)"));
        assertFalse("a seed is not a readable surface", dialog.contains("binding.getRoot().setBackgroundColor(color)"));
    }

    @Test
    public void manualAndWallpaperColorsDoNotDependOnPlatformDynamicColorSupport() throws Exception {
        String theme = read("app/src/mobile/java/com/fongmi/android/tv/ui/helper/SiteDialogTheme.java");
        assertTrue(theme.contains("if (seedColor != 0)"));
        assertTrue(theme.contains("MaterialColors.getColorRoles(context, seedColor)"));
        assertTrue(theme.contains("MaterialColors.getSurfaceContainerFromSeed(context, seedColor)"));
        assertFalse(theme.contains("DynamicColors.apply"));
        assertFalse(theme.contains("SDK_INT"));
        assertTrue("selected foreground/background must be paired", theme.contains("states(onPrimary, onContainer, onSurface)"));
        assertTrue(theme.contains("states(primary, container, surface)"));
        assertTrue("no seed keeps the current theme", theme.contains("color(context, androidx.appcompat.R.attr.colorPrimary)"));
    }

    @Test
    public void zeroTokensFallBackToCurrentThemeInsteadOfTransparentSurface() throws Exception {
        String theme = read("app/src/mobile/java/com/fongmi/android/tv/ui/helper/SiteDialogTheme.java");
        assertTrue(theme.contains("public static SiteDialogTheme resolve(Context context, ThemeTokens tokens) {\n"
                + "        if (tokens.primary() == 0) return resolve(context, 0);"));
    }

    @Test
    public void controlsDoNotUseTheLegacyFixedLightPalette() throws Exception {
        String site = read("app/src/mobile/java/com/fongmi/android/tv/ui/adapter/SiteAdapter.java");
        String group = read("app/src/mobile/java/com/fongmi/android/tv/ui/adapter/SiteGroupAdapter.java");
        assertTrue(site.contains("theme.apply(binding.text)"));
        assertTrue(site.contains("theme.tint(binding.search)"));
        assertTrue(site.contains("theme.tint(binding.change)"));
        assertTrue(group.contains("theme.apply(button)"));
        assertTrue(group.contains("holder.button.setSelected(selected)"));
        assertFalse(group.contains("R.color.dialog_outlined_button"));
        for (String file : new String[]{"dialog_site.xml", "adapter_site.xml"}) {
            String layout = read("app/src/mobile/res/layout/" + file);
            assertFalse(layout.contains("@color/dialog_outlined_button"));
            assertFalse(layout.contains("@color/site_button"));
            assertFalse(layout.contains("android:textColor=\"#"));
            assertTrue(layout.contains("?attr/colorOnSurface"));
        }
    }

    @Test
    public void leanbackThemeChoicesAreDpadFocusable() throws Exception {
        String layout = read("app/src/leanback/res/layout/adapter_theme.xml");
        assertTrue(layout.contains("android:background=\"@drawable/selector_item\""));
        assertTrue(layout.contains("android:focusable=\"true\""));
        assertTrue(layout.contains("android:focusableInTouchMode=\"true\""));
    }

    @Test
    public void everyActivityAppliesThemeChangesImmediately() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        assertTrue(mobile.contains("event.getType() == RefreshEvent.Type.LANGUAGE"));
        assertTrue(mobile.contains("event.getType() == RefreshEvent.Type.THEME"));
        assertTrue(mobile.contains("recreate()"));

        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        assertTrue(leanback.contains("event.getType() == RefreshEvent.Type.LANGUAGE"));
        assertTrue(leanback.contains("event.getType() == RefreshEvent.Type.UI_SCALE"));
        assertTrue(leanback.contains("event.getType() == RefreshEvent.Type.THEME"));
        assertTrue(leanback.contains("recreate()"));
    }

    private String read(String path) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
