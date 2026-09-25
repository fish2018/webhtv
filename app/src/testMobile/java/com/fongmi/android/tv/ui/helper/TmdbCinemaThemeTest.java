package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbCinemaThemeTest {

    @Test
    public void resolveLight_usesOnlyLightAndDarkThemeModes() {
        assertTrue(TmdbCinemaTheme.resolveLight(0, false));
        assertTrue(TmdbCinemaTheme.resolveLight(0, true));
        assertFalse(TmdbCinemaTheme.resolveLight(1, false));
        assertTrue(TmdbCinemaTheme.resolveLight(2, true));
    }

    @Test
    public void palette_usesDarkCanvasForDarkTheme() {
        TmdbCinemaTheme.Palette palette = TmdbCinemaTheme.palette(false);

        assertEquals(0xFF090B0F, palette.background());
        assertEquals(0xFFFFFFFF, palette.primary());
        assertEquals(0xB314202A, palette.card());
    }

    @Test
    public void palette_usesLightCanvasForLightTheme() {
        TmdbCinemaTheme.Palette palette = TmdbCinemaTheme.palette(true);

        assertEquals(0xFFF4F7FA, palette.background());
        assertEquals(0xFF12202D, palette.primary());
        assertEquals(0xD9FFFFFF, palette.card());
    }
}
