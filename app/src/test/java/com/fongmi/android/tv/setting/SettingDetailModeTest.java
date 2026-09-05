package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SettingDetailModeTest {

    @Test
    public void tmdbModes_includeOriginalEnhancedAndMigratedLegacyModes() {
        assertTrue(Setting.isTmdbMode(Setting.DETAIL_OPEN_ORIGINAL_ENHANCED));
        assertTrue(Setting.isTmdbMode(Setting.DETAIL_OPEN_FUSION));
        assertTrue(Setting.isTmdbMode(Setting.DETAIL_OPEN_ENHANCED));
        assertTrue(Setting.isTmdbMode(Setting.DETAIL_OPEN_PLAYER));
    }

    @Test
    public void tmdbModes_excludeNativePlaybackAndLegacyCinemaAlias() {
        assertFalse(Setting.isTmdbMode(Setting.DETAIL_OPEN_DIRECT));
        assertFalse(Setting.isTmdbMode(Setting.DETAIL_OPEN_CINEMA));
    }

    @Test
    public void standaloneTmdbDetailModes_includeFusionButExcludeOriginalEnhanced() {
        assertTrue(Setting.isStandaloneTmdbDetailMode(Setting.DETAIL_OPEN_FUSION));
        assertTrue(Setting.isStandaloneTmdbDetailMode(Setting.DETAIL_OPEN_ENHANCED));
        assertTrue(Setting.isStandaloneTmdbDetailMode(Setting.DETAIL_OPEN_PLAYER));
        assertFalse(Setting.isStandaloneTmdbDetailMode(Setting.DETAIL_OPEN_ORIGINAL_ENHANCED));
        assertFalse(Setting.isStandaloneTmdbDetailMode(Setting.DETAIL_OPEN_DIRECT));
        assertFalse(Setting.isStandaloneTmdbDetailMode(Setting.DETAIL_OPEN_CINEMA));
    }

    @Test
    public void nativeTheme_isSeparateFromLegacyThemes() {
        assertNotEquals(Setting.DETAIL_STYLE_PROFILE, Setting.DETAIL_STYLE_NATIVE);
        assertNotEquals(Setting.DETAIL_STYLE_CINEMA, Setting.DETAIL_STYLE_NATIVE);
    }

    @Test
    public void detailTheme_migratesUnknownAndLegacyAutoToLight() {
        assertEquals(2, Setting.clampTmdbDetailTheme(-1));
        assertEquals(2, Setting.clampTmdbDetailTheme(0));
        assertEquals(1, Setting.clampTmdbDetailTheme(1));
        assertEquals(2, Setting.clampTmdbDetailTheme(2));
        assertEquals(2, Setting.clampTmdbDetailTheme(3));
    }

    @Test
    public void detailTheme_cyclesOnlyBetweenLightAndDark() {
        assertEquals(1, Setting.nextTmdbDetailTheme(0));
        assertEquals(2, Setting.nextTmdbDetailTheme(1));
        assertEquals(1, Setting.nextTmdbDetailTheme(2));
        assertEquals(1, Setting.nextTmdbDetailTheme(9));
    }

    @Test
    public void detailTheme_resolvesLegacyAutoAsLightRegardlessOfSystemMode() {
        assertTrue(Setting.resolveTmdbDetailLightTheme(0, false));
        assertTrue(Setting.resolveTmdbDetailLightTheme(0, true));
        assertFalse(Setting.resolveTmdbDetailLightTheme(1, false));
        assertFalse(Setting.resolveTmdbDetailLightTheme(1, true));
        assertTrue(Setting.resolveTmdbDetailLightTheme(2, false));
        assertTrue(Setting.resolveTmdbDetailLightTheme(2, true));
    }
}
