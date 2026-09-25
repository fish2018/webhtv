package com.fongmi.android.tv.theme;

/** Resolves explicit profile values and deterministic fallback roles for every mode. */
public final class ThemeResolver {

    private static final int DEFAULT_SEED = 0xFF6750A4;
    private static final int LIGHT_BACKGROUND = 0xFFF8FAFC;
    private static final int DARK_BACKGROUND = 0xFF111827;
    private static final int LIGHT_SURFACE = 0xFFFFFFFF;
    private static final int DARK_SURFACE = 0xFF1F2937;
    private static final int LIGHT_ERROR = 0xFFBA1A1A;
    private static final int DARK_ERROR = 0xFFFFB4AB;

    private ThemeResolver() {
    }

    public static ThemeTokens resolve(ThemeProfile input, boolean systemDark, int wallpaperSeed) {
        ThemeProfile profile = input == null ? ThemeProfile.defaultProfile() : input;
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(profile);
        if (!result.valid()) profile = ThemeProfile.defaultProfile();
        else profile = result.profile();

        boolean dark = ThemeProfile.MODE_DARK.equals(profile.mode)
                || (ThemeProfile.MODE_SYSTEM.equals(profile.mode) && systemDark);
        ThemeProfile.ColorSet explicit = profile.colorsFor(dark);
        int seed = resolveSeed(profile, wallpaperSeed);
        int appBackground = color(explicit.appBackground, dark ? DARK_BACKGROUND : LIGHT_BACKGROUND);
        if (ThemeProfile.BACKGROUND_SOLID.equals(profile.background.type) && profile.background.color != null) {
            appBackground = ThemeColorUtil.parse(profile.background.color, appBackground);
        }
        int surface = color(explicit.surface, dark ? DARK_SURFACE : LIGHT_SURFACE);
        int surfaceElevated = color(explicit.surfaceElevated,
                dark ? ThemeColorUtil.mix(surface, ThemeColorUtil.WHITE, 0.10f)
                        : ThemeColorUtil.mix(surface, ThemeColorUtil.BLACK, 0.04f));
        int primary = color(explicit.primary, derivedPrimary(seed, dark));
        int onPrimary = color(explicit.onPrimary, ThemeColorUtil.readableOn(primary));
        int primaryContainer = color(explicit.primaryContainer,
                dark ? ThemeColorUtil.mix(primary, appBackground, 0.60f)
                        : ThemeColorUtil.mix(primary, appBackground, 0.88f));
        int onPrimaryContainer = color(explicit.onPrimaryContainer, ThemeColorUtil.readableOn(primaryContainer));
        int onSurface = color(explicit.onSurface, ThemeColorUtil.readableOn(surface));
        int onSurfaceVariant = color(explicit.onSurfaceVariant,
                ThemeColorUtil.ensureContrast(ThemeColorUtil.mix(onSurface, surface, dark ? 0.38f : 0.48f), surface, 3.0));
        int outline = color(explicit.outline,
                ThemeColorUtil.ensureContrast(ThemeColorUtil.mix(onSurface, surface, dark ? 0.52f : 0.68f), surface, 3.0));
        int focus = color(explicit.focus, primary);
        int error = color(explicit.error, dark ? DARK_ERROR : LIGHT_ERROR);
        float scrim = Math.max(0f, Math.min(0.85f, profile.background.scrimAlpha));
        return new ThemeTokens(primary, onPrimary, primaryContainer, onPrimaryContainer,
                appBackground, surface, surfaceElevated, onSurface, onSurfaceVariant,
                outline, focus, error, dark ? ThemeProfile.MODE_DARK : ThemeProfile.MODE_LIGHT,
                profile.background.type, scrim);
    }

    private static int resolveSeed(ThemeProfile profile, int wallpaperSeed) {
        if (ThemeProfile.SEED_CUSTOM.equals(profile.seedSource)) {
            return ThemeColorUtil.parse(profile.seedColor, DEFAULT_SEED);
        }
        if (ThemeProfile.SEED_WALLPAPER.equals(profile.seedSource) && wallpaperSeed != 0) {
            return wallpaperSeed | 0xFF000000;
        }
        return DEFAULT_SEED;
    }

    private static int derivedPrimary(int seed, boolean dark) {
        int base = seed | 0xFF000000;
        return dark ? ThemeColorUtil.mix(base, ThemeColorUtil.WHITE, 0.30f) : base;
    }

    private static int color(String value, int fallback) {
        return value == null ? fallback : ThemeColorUtil.parse(value, fallback);
    }
}
