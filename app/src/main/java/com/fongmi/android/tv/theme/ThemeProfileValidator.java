package com.fongmi.android.tv.theme;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validates and normalizes the persisted/imported theme schema. */
public final class ThemeProfileValidator {

    public static final int MAX_JSON_BYTES = 256 * 1024;
    public static final int MAX_NESTING_DEPTH = 8;
    private static final Set<String> DANGEROUS_KEYS = Set.of(
            "script", "javascript", "resource", "resourcepath", "intent", "classname", "class", "css", "plugin");

    private ThemeProfileValidator() {
    }

    public static Result validate(ThemeProfile original) {
        if (original == null) return Result.invalid("profile is null");
        ThemeProfile profile = original.copy();
        List<String> errors = new ArrayList<>();
        if (profile.schemaVersion != ThemeProfile.SCHEMA_VERSION) errors.add("unsupported schemaVersion");
        if (!ThemeProfile.FORMAT.equals(profile.format)) errors.add("unsupported format");
        profile.id = text(profile.id, "webhtv.imported");
        profile.name = text(profile.name, "Imported theme");
        profile.author = optionalText(profile.author, 120, errors, "author");
        if (profile.id.length() > 96) errors.add("id is too long");
        if (profile.name.length() > 80) errors.add("name is too long");
        if (!isMode(profile.mode)) errors.add("unsupported mode");
        profile.mode = ThemeProfile.normalizeMode(profile.mode);
        if (!isSeedSource(profile.seedSource)) errors.add("unsupported seedSource");
        profile.seedSource = ThemeProfile.normalizeSeedSource(profile.seedSource);
        profile.background = profile.background == null ? new ThemeProfile.Background() : profile.background;
        if (!isBackgroundType(profile.background.type)) errors.add("unsupported background.type");
        profile.background.type = ThemeProfile.normalizeBackgroundType(profile.background.type);
        if (Float.isNaN(profile.background.scrimAlpha) || Float.isInfinite(profile.background.scrimAlpha)
                || profile.background.scrimAlpha < 0f || profile.background.scrimAlpha > 0.85f) {
            errors.add("scrimAlpha must be between 0 and 0.85");
        }
        profile.background.color = color(profile.background.color, errors, "background.color");
        if (ThemeProfile.BACKGROUND_SOLID.equals(profile.background.type) && profile.background.color == null) {
            errors.add("solid background requires color");
        }
        profile.seedColor = color(profile.seedColor, errors, "seedColor");
        if (ThemeProfile.SEED_NONE.equals(profile.seedSource) && profile.seedColor != null) {
            profile.seedSource = ThemeProfile.SEED_CUSTOM;
        }
        if (ThemeProfile.SEED_CUSTOM.equals(profile.seedSource) && profile.seedColor == null) {
            errors.add("custom seedSource requires seedColor");
        }
        if (profile.source == null) profile.source = new ThemeProfile.Source();
        profile.source.type = profile.source.type == null ? "local" : profile.source.type.toLowerCase(Locale.ROOT);
        if (!Set.of("local", "tweakcn", "url").contains(profile.source.type)) errors.add("unsupported source.type");
        profile.source.url = optionalText(profile.source.url, 2048, errors, "source.url");
        if (profile.source.url != null && !isSafeHttps(profile.source.url)) errors.add("source.url must be HTTPS");
        if (profile.colors == null) profile.colors = new ThemeProfile.Colors();
        profile.colors.light = normalizeColors(profile.colors.light, errors, "colors.light");
        profile.colors.dark = normalizeColors(profile.colors.dark, errors, "colors.dark");
        if (profile.metadata == null) profile.metadata = new ThemeProfile.Metadata();
        if (profile.metadata.tags != null && profile.metadata.tags.length > 16) errors.add("too many metadata tags");
        return new Result(profile, errors);
    }

    private static ThemeProfile.ColorSet normalizeColors(ThemeProfile.ColorSet colors, List<String> errors, String path) {
        if (colors == null) return new ThemeProfile.ColorSet();
        ThemeProfile.ColorSet normalized = colors.copy();
        normalized.primary = color(normalized.primary, errors, path + ".primary");
        normalized.onPrimary = color(normalized.onPrimary, errors, path + ".onPrimary");
        normalized.primaryContainer = color(normalized.primaryContainer, errors, path + ".primaryContainer");
        normalized.onPrimaryContainer = color(normalized.onPrimaryContainer, errors, path + ".onPrimaryContainer");
        normalized.appBackground = color(normalized.appBackground, errors, path + ".appBackground");
        normalized.surface = color(normalized.surface, errors, path + ".surface");
        normalized.surfaceElevated = color(normalized.surfaceElevated, errors, path + ".surfaceElevated");
        normalized.onSurface = color(normalized.onSurface, errors, path + ".onSurface");
        normalized.onSurfaceVariant = color(normalized.onSurfaceVariant, errors, path + ".onSurfaceVariant");
        normalized.outline = color(normalized.outline, errors, path + ".outline");
        normalized.focus = color(normalized.focus, errors, path + ".focus");
        normalized.error = color(normalized.error, errors, path + ".error");
        if (normalized.primary != null && normalized.onPrimary != null
                && ThemeColorUtil.contrast(ThemeColorUtil.parse(normalized.primary, ThemeColorUtil.BLACK),
                ThemeColorUtil.parse(normalized.onPrimary, ThemeColorUtil.BLACK)) < 4.5) {
            errors.add(path + ".primary/onPrimary contrast is below 4.5:1");
        }
        if (normalized.surface != null && normalized.onSurface != null
                && ThemeColorUtil.contrast(ThemeColorUtil.parse(normalized.surface, ThemeColorUtil.WHITE),
                ThemeColorUtil.parse(normalized.onSurface, ThemeColorUtil.BLACK)) < 4.5) {
            errors.add(path + ".surface/onSurface contrast is below 4.5:1");
        }
        return normalized;
    }

    private static boolean isMode(String value) {
        return value == null || ThemeProfile.MODE_SYSTEM.equals(value) || ThemeProfile.MODE_LIGHT.equals(value)
                || ThemeProfile.MODE_DARK.equals(value);
    }

    private static boolean isSeedSource(String value) {
        return value == null || ThemeProfile.SEED_NONE.equals(value) || ThemeProfile.SEED_WALLPAPER.equals(value)
                || ThemeProfile.SEED_CUSTOM.equals(value);
    }

    private static boolean isBackgroundType(String value) {
        return value == null || ThemeProfile.BACKGROUND_WALLPAPER.equals(value)
                || ThemeProfile.BACKGROUND_TINTED_WALLPAPER.equals(value) || ThemeProfile.BACKGROUND_SOLID.equals(value);
    }

    private static String color(String value, List<String> errors, String path) {
        if (value == null || value.isBlank()) return null;
        String normalized = ThemeColorUtil.normalize(value);
        if (normalized == null) errors.add(path + " must be an opaque #RGB/#RRGGBB color");
        return normalized;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String optionalText(String value, int maxLength, List<String> errors, String path) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > maxLength) errors.add(path + " is too long");
        return result;
    }

    public static boolean isSafeJsonKey(String key) {
        if (key == null) return false;
        String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return !DANGEROUS_KEYS.contains(normalized);
    }

    public static boolean isSafeHttps(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return !host.equals("localhost") && !host.equals("127.0.0.1") && !host.equals("::1")
                    && !host.startsWith("10.") && !host.startsWith("192.168.") && !host.startsWith("169.254.")
                    && !host.startsWith("172.16.") && !host.startsWith("172.17.") && !host.startsWith("172.18.")
                    && !host.startsWith("172.19.") && !host.startsWith("172.2") && !host.startsWith("172.3");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public record Result(ThemeProfile profile, List<String> errors) {

        public Result {
            errors = errors == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(errors));
        }

        public boolean valid() {
            return errors.isEmpty();
        }

        public String message() {
            return valid() ? "" : String.join("; ", errors);
        }

        public static Result invalid(String error) {
            return new Result(null, List.of(error));
        }
    }
}
