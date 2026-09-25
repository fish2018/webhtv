package com.fongmi.android.tv.theme;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemeResolverTest {

    @Test
    public void resolvesExplicitLightRolesAndDerivesReadableText() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.seedSource = ThemeProfile.SEED_CUSTOM;
        profile.seedColor = "#155DFC";
        profile.colors.light.primary = "#155DFC";
        profile.colors.light.appBackground = "#F8FAFC";
        profile.colors.light.surface = "#FFFFFF";
        ThemeTokens tokens = ThemeResolver.resolve(profile, false, 0);
        assertEquals(0xFF155DFC, tokens.primary());
        assertTrue(ThemeColorUtil.contrast(tokens.onSurface(), tokens.surface()) >= 4.5);
        assertTrue(ThemeColorUtil.contrast(tokens.onPrimary(), tokens.primary()) >= 4.5);
    }

    @Test
    public void explicitHighlightDoesNotChangeUnrelatedSurfaceRoles() {
        ThemeProfile reset = ThemeProfile.defaultProfile();
        ThemeTokens resetTokens = ThemeResolver.resolve(reset, false, 0);

        ThemeProfile selected = ThemeProfile.defaultProfile();
        selected.colors.light.primary = ThemeColorUtil.format(resetTokens.primary());
        ThemeTokens selectedTokens = ThemeResolver.resolve(selected, false, 0);

        assertEquals(resetTokens.primary(), selectedTokens.primary());
        assertEquals(resetTokens.appBackground(), selectedTokens.appBackground());
        assertEquals(resetTokens.surface(), selectedTokens.surface());
        assertEquals(resetTokens.surfaceElevated(), selectedTokens.surfaceElevated());
        assertEquals(resetTokens.onSurface(), selectedTokens.onSurface());
        assertEquals(resetTokens.onSurfaceVariant(), selectedTokens.onSurfaceVariant());
        assertEquals(resetTokens.outline(), selectedTokens.outline());
    }

    @Test
    public void mobileHighlightSelectionDoesNotRewriteThemeSeed() throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        String editor = new String(Files.readAllBytes(root.resolve(
                "app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ThemeEditorDialog.java")), StandardCharsets.UTF_8);

        assertTrue(editor.contains("private void setHighlightColor(String color)"));
        assertTrue(editor.contains("draft.colorsFor(systemDark()).primary = color;"));
        assertTrue(editor.contains("setHighlightColor(ThemeColorUtil.format(color));"));
        assertTrue(editor.contains("setHighlightColor(color);"));
        assertFalse(editor.contains("draft.seedSource = ThemeProfile.SEED_CUSTOM;"));
        assertFalse(editor.contains("draft.seedColor = colors.primary;"));
    }

    @Test
    public void mobileDefaultPresetClearsExplicitPrimary() throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        String editor = new String(Files.readAllBytes(root.resolve(
                "app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ThemeEditorDialog.java")), StandardCharsets.UTF_8);

        assertTrue(editor.contains("private static final int DEFAULT_PRESET = PRESETS[0];"));
        assertTrue(editor.contains("if (color == DEFAULT_PRESET) draft.colorsFor(systemDark()).primary = null;"));
        assertTrue(editor.contains("else setHighlightColor(ThemeColorUtil.format(color));"));
    }

    @Test
    public void systemAndWallpaperSeedAffectResolvedModeAndAccent() {
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(0);
        ThemeTokens dark = ThemeResolver.resolve(profile, true, 0xFF00897B);
        assertEquals(ThemeProfile.MODE_DARK, dark.mode());
        assertTrue(dark.primary() != 0xFF6750A4);
    }
}
