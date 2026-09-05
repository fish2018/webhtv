package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.setting.Setting;

public final class TmdbCinemaTheme {

    private TmdbCinemaTheme() {
    }

    public static boolean resolveLight(int detailThemeMode, boolean systemNight) {
        return Setting.resolveTmdbDetailLightTheme(detailThemeMode, systemNight);
    }

    public static Palette palette(boolean light) {
        return light ? light() : dark();
    }

    private static Palette dark() {
        return new Palette(
                0xFF090B0F,
                0xC914171C,
                0xFF252A32,
                0x40252A32,
                0x664B8F72,
                0x24FFFFFF,
                0x42FFFFFF,
                0xFFFFFFFF,
                0xD9FFFFFF,
                0x99FFFFFF,
                0xE6FFFFFF,
                0xFF8FE7B6,
                0xFF2DBA76,
                0xB3090B0F,
                0xB314202A,
                0x33FFFFFF,
                0x26FFFFFF,
                0x40000000
        );
    }

    private static Palette light() {
        return new Palette(
                0xFFF4F7FA,
                0xBFFFFFFF,
                0xFFE7EDF3,
                0xD9FFFFFF,
                0xFFE5F7EC,
                0x33424B57,
                0x66424B57,
                0xFF12202D,
                0xCC12202D,
                0x9912202D,
                0xE612202D,
                0xFF1D8F5A,
                0xFF20B866,
                0xBFF4F7FA,
                0xD9FFFFFF,
                0x33424B57,
                0x1F12202D,
                0xD9FFFFFF
        );
    }

    public record Palette(int background, int panel, int control, int chip, int chipActive, int line, int lineStrong,
                          int primary, int secondary, int muted, int body, int accent, int play, int backdropShade,
                          int card, int cardStroke, int imagePlaceholder, int ratingChip) {
    }
}
