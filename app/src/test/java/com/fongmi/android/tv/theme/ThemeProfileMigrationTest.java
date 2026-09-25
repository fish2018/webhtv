package com.fongmi.android.tv.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ThemeProfileMigrationTest {

    @Test
    public void legacyOffMigratesToDisabledSeed() {
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(-1);
        assertEquals(ThemeProfile.SEED_NONE, profile.seedSource);
        assertEquals(-1, ThemeProfileStore.legacyThemeColor(profile));
    }

    @Test
    public void legacyWallpaperKeepsWallpaperSeedMode() {
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(0);
        assertEquals(ThemeProfile.SEED_WALLPAPER, profile.seedSource);
        assertEquals(0, ThemeProfileStore.legacyThemeColor(profile));
    }

    @Test
    public void legacyCustomSeedRoundTripsAsArgbInt() {
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(0xFF155DFC);
        assertEquals(ThemeProfile.SEED_CUSTOM, profile.seedSource);
        assertEquals("#155DFC", profile.seedColor);
        assertEquals(0xFF155DFC, ThemeProfileStore.legacyThemeColor(profile));
    }
}
