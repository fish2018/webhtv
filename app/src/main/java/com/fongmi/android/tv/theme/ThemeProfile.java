package com.fongmi.android.tv.theme;

import java.util.Locale;

/** Persisted, UI-independent WebHTV theme profile. */
public class ThemeProfile {

    public static final int SCHEMA_VERSION = 1;
    public static final String FORMAT = "webhtv-theme";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String BACKGROUND_WALLPAPER = "wallpaper";
    public static final String BACKGROUND_TINTED_WALLPAPER = "tinted-wallpaper";
    public static final String BACKGROUND_SOLID = "solid";
    public static final String SEED_NONE = "none";
    public static final String SEED_WALLPAPER = "wallpaper";
    public static final String SEED_CUSTOM = "custom";
    public static final String TWEAK_PRIMARY = "--primary";
    public static final String TWEAK_PRIMARY_FOREGROUND = "--primary-foreground";
    public static final String TWEAK_BACKGROUND = "--background";
    public static final String TWEAK_CARD = "--card";
    public static final String TWEAK_POPOVER = "--popover";
    public static final String TWEAK_FOREGROUND = "--foreground";
    public static final String TWEAK_MUTED_FOREGROUND = "--muted-foreground";
    public static final String TWEAK_BORDER = "--border";
    public static final String TWEAK_INPUT = "--input";
    public static final String TWEAK_RING = "--ring";
    public static final String TWEAK_DESTRUCTIVE = "--destructive";

    public int schemaVersion = SCHEMA_VERSION;
    public String format = FORMAT;
    public String id = "webhtv.default";
    public String name = "Default";
    public String author;
    public Source source = new Source();
    public String mode = MODE_SYSTEM;
    public Background background = new Background();
    public String seedSource = SEED_NONE;
    public String seedColor;
    public Colors colors = new Colors();
    public Metadata metadata = new Metadata();

    public ThemeProfile() {
    }

    public static ThemeProfile defaultProfile() {
        ThemeProfile profile = new ThemeProfile();
        profile.id = "webhtv.default";
        profile.name = "Default";
        profile.seedSource = SEED_NONE;
        profile.seedColor = null;
        profile.background.type = BACKGROUND_WALLPAPER;
        profile.background.color = null;
        profile.background.scrimAlpha = 0f;
        return profile;
    }

    public ThemeProfile copy() {
        ThemeProfile copy = new ThemeProfile();
        copy.schemaVersion = schemaVersion;
        copy.format = format;
        copy.id = id;
        copy.name = name;
        copy.author = author;
        copy.source = source == null ? null : source.copy();
        copy.mode = mode;
        copy.background = background == null ? null : background.copy();
        copy.seedSource = seedSource;
        copy.seedColor = seedColor;
        copy.colors = colors == null ? null : colors.copy();
        copy.metadata = metadata == null ? null : metadata.copy();
        return copy;
    }

    public ColorSet colorsFor(boolean dark) {
        if (colors == null) colors = new Colors();
        if (dark) {
            if (colors.dark == null) colors.dark = new ColorSet();
            return colors.dark;
        }
        if (colors.light == null) colors.light = new ColorSet();
        return colors.light;
    }

    public String displayName() {
        return name == null || name.isBlank() ? "Default" : name;
    }

    public static final class Source {
        public String type = "local";
        public String url;

        public Source copy() {
            Source copy = new Source();
            copy.type = type;
            copy.url = url;
            return copy;
        }
    }

    public static final class Background {
        public String type = BACKGROUND_WALLPAPER;
        public String color;
        public float scrimAlpha;

        public Background copy() {
            Background copy = new Background();
            copy.type = type;
            copy.color = color;
            copy.scrimAlpha = scrimAlpha;
            return copy;
        }
    }

    public static final class Colors {
        public ColorSet light = new ColorSet();
        public ColorSet dark = new ColorSet();

        public Colors copy() {
            Colors copy = new Colors();
            copy.light = light == null ? null : light.copy();
            copy.dark = dark == null ? null : dark.copy();
            return copy;
        }
    }

    public static final class ColorSet {
        public String primary;
        public String onPrimary;
        public String primaryContainer;
        public String onPrimaryContainer;
        public String appBackground;
        public String surface;
        public String surfaceElevated;
        public String onSurface;
        public String onSurfaceVariant;
        public String outline;
        public String focus;
        public String error;

        public ColorSet copy() {
            ColorSet copy = new ColorSet();
            copy.primary = primary;
            copy.onPrimary = onPrimary;
            copy.primaryContainer = primaryContainer;
            copy.onPrimaryContainer = onPrimaryContainer;
            copy.appBackground = appBackground;
            copy.surface = surface;
            copy.surfaceElevated = surfaceElevated;
            copy.onSurface = onSurface;
            copy.onSurfaceVariant = onSurfaceVariant;
            copy.outline = outline;
            copy.focus = focus;
            copy.error = error;
            return copy;
        }
    }

    public static final class Metadata {
        public String[] tags;
        public int previewVersion = 1;

        public Metadata copy() {
            Metadata copy = new Metadata();
            copy.tags = tags == null ? null : tags.clone();
            copy.previewVersion = previewVersion;
            return copy;
        }
    }

    public static String normalizeMode(String value) {
        if (MODE_LIGHT.equals(value)) return MODE_LIGHT;
        if (MODE_DARK.equals(value)) return MODE_DARK;
        return MODE_SYSTEM;
    }

    public static String normalizeSeedSource(String value) {
        if (SEED_CUSTOM.equals(value)) return SEED_CUSTOM;
        if (SEED_WALLPAPER.equals(value)) return SEED_WALLPAPER;
        return SEED_NONE;
    }

    public static String normalizeBackgroundType(String value) {
        if (BACKGROUND_SOLID.equals(value)) return BACKGROUND_SOLID;
        if (BACKGROUND_TINTED_WALLPAPER.equals(value)) return BACKGROUND_TINTED_WALLPAPER;
        return BACKGROUND_WALLPAPER;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s (%s)", displayName(), mode);
    }
}
