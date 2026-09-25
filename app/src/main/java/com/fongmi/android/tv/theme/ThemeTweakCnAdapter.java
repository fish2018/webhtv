package com.fongmi.android.tv.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline, allowlisted adapter for common shadcn/TweakCN color tokens. */
public final class ThemeTweakCnAdapter {

    private static final int MAX_WARNINGS = 32;

    private ThemeTweakCnAdapter() {
    }

    public static Result parse(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("theme JSON is empty");
        ThemeProfileCodec.validateJsonBounds(json);
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("theme JSON is invalid", e);
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("theme JSON must be an object");
        JsonObject object = root.getAsJsonObject();
        if (object.has("format") || object.has("schemaVersion")) {
            return new Result(ThemeProfileCodec.parse(json), List.of());
        }

        ThemeProfile profile = ThemeProfile.defaultProfile();
        ThemeProfile.ColorSet light = profile.colors.light;
        ThemeProfile.ColorSet dark = profile.colors.dark;
        List<String> warnings = new ArrayList<>();
        int recognized;
        JsonObject cssVars = object.has("cssVars") && object.get("cssVars").isJsonObject()
                ? object.getAsJsonObject("cssVars") : object;
        if (cssVars.has("light") || cssVars.has("dark")) {
            recognized = 0;
            if (cssVars.has("light") && cssVars.get("light").isJsonObject()) {
                recognized += applyObject(cssVars.getAsJsonObject("light"), light, warnings, "light");
            }
            if (cssVars.has("dark") && cssVars.get("dark").isJsonObject()) {
                recognized += applyObject(cssVars.getAsJsonObject("dark"), dark, warnings, "dark");
            }
            warnUnsupported(cssVars, warnings, "cssVars");
            if (cssVars.has("theme") && cssVars.get("theme").isJsonObject()) {
                warnUnsupported(cssVars.getAsJsonObject("theme"), warnings, "cssVars.theme");
            }
            if (cssVars.has("light") && cssVars.get("light").isJsonObject()) {
                warnUnsupportedColors(cssVars.getAsJsonObject("light"), warnings, "light");
            }
            if (cssVars.has("dark") && cssVars.get("dark").isJsonObject()) {
                warnUnsupportedColors(cssVars.getAsJsonObject("dark"), warnings, "dark");
            }
        } else {
            recognized = applyObject(cssVars, light, warnings, "theme");
            copyMissing(light, dark);
        }
        if (recognized == 0) throw new IllegalArgumentException("no supported TweakCN color tokens");

        String importedName = object.has("name") && object.get("name").isJsonPrimitive()
                ? object.get("name").getAsString() : "Imported TweakCN theme";
        profile.id = "webhtv.tweakcn.imported";
        profile.name = importedName == null || importedName.isBlank() ? "Imported TweakCN theme"
                : importedName.trim().substring(0, Math.min(80, importedName.trim().length()));
        profile.source.type = "tweakcn";
        String seed = light.primary != null ? light.primary : dark.primary;
        profile.seedSource = seed == null ? ThemeProfile.SEED_NONE : ThemeProfile.SEED_CUSTOM;
        profile.seedColor = seed;
        ThemeProfileValidator.Result checked = ThemeProfileValidator.validate(profile);
        if (!checked.valid()) throw new IllegalArgumentException(checked.message());
        return new Result(checked.profile(), warnings);
    }

    private static int applyObject(JsonObject object, ThemeProfile.ColorSet target, List<String> warnings, String path) {
        int recognized = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = normalizeKey(entry.getKey());
            String value = color(entry.getValue());
            if (value == null) {
                if (isColorToken(key)) addWarning(warnings, path + "." + entry.getKey() + " ignored: unsupported color format");
                continue;
            }
            if (apply(key, value, target)) recognized++;
            else if (isColorToken(key)) addWarning(warnings, path + "." + entry.getKey() + " ignored: unsupported token");
        }
        return recognized;
    }

    private static void warnUnsupportedColors(JsonObject object, List<String> warnings, String path) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (!applyKey(key) && !"--theme".equals(key)) {
                addWarning(warnings, path + "." + entry.getKey() + " ignored: unsupported token");
            }
        }
    }

    private static boolean applyKey(String key) {
        return switch (key) {
            case ThemeProfile.TWEAK_PRIMARY, ThemeProfile.TWEAK_PRIMARY_FOREGROUND,
                    "--primary-container", "--primary-container-foreground", ThemeProfile.TWEAK_BACKGROUND,
                    ThemeProfile.TWEAK_CARD, "--card-foreground", ThemeProfile.TWEAK_POPOVER,
                    ThemeProfile.TWEAK_POPOVER + "-foreground", ThemeProfile.TWEAK_FOREGROUND,
                    ThemeProfile.TWEAK_MUTED_FOREGROUND, ThemeProfile.TWEAK_BORDER, ThemeProfile.TWEAK_INPUT,
                    ThemeProfile.TWEAK_RING, ThemeProfile.TWEAK_DESTRUCTIVE, "--secondary", "--secondary-foreground",
                    "--muted", "--accent", "--accent-foreground" -> true;
            default -> false;
        };
    }

    private static void warnUnsupported(JsonObject object, List<String> warnings, String path) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (!key.equals("--light") && !key.equals("--dark") && !isColorToken(key) && !"--theme".equals(key)) {
                addWarning(warnings, path + "." + entry.getKey() + " ignored: unsupported field");
            }
        }
    }

    private static void copyMissing(ThemeProfile.ColorSet from, ThemeProfile.ColorSet to) {
        to.primary = from.primary;
        to.onPrimary = from.onPrimary;
        to.primaryContainer = from.primaryContainer;
        to.onPrimaryContainer = from.onPrimaryContainer;
        to.appBackground = from.appBackground;
        to.surface = from.surface;
        to.surfaceElevated = from.surfaceElevated;
        to.onSurface = from.onSurface;
        to.onSurfaceVariant = from.onSurfaceVariant;
        to.outline = from.outline;
        to.focus = from.focus;
        to.error = from.error;
    }

    private static boolean apply(String key, String value, ThemeProfile.ColorSet target) {
        switch (key) {
            case ThemeProfile.TWEAK_PRIMARY -> target.primary = value;
            case ThemeProfile.TWEAK_PRIMARY_FOREGROUND -> target.onPrimary = value;
            case "--primary-container" -> target.primaryContainer = value;
            case "--primary-container-foreground" -> target.onPrimaryContainer = value;
            case ThemeProfile.TWEAK_BACKGROUND -> target.appBackground = value;
            case ThemeProfile.TWEAK_CARD -> target.surface = value;
            case "--card-foreground", ThemeProfile.TWEAK_POPOVER + "-foreground",
                    "--secondary-foreground", "--accent-foreground" -> target.onSurface = value;
            case ThemeProfile.TWEAK_POPOVER, "--secondary", "--muted" -> target.surfaceElevated = value;
            case ThemeProfile.TWEAK_FOREGROUND -> target.onSurface = value;
            case ThemeProfile.TWEAK_MUTED_FOREGROUND -> target.onSurfaceVariant = value;
            case "--accent" -> target.primaryContainer = value;
            case ThemeProfile.TWEAK_BORDER, ThemeProfile.TWEAK_INPUT -> target.outline = value;
            case ThemeProfile.TWEAK_RING -> target.focus = value;
            case ThemeProfile.TWEAK_DESTRUCTIVE -> target.error = value;
            default -> { return false; }
        }
        return true;
    }

    private static boolean isColorToken(String key) {
        return key.contains("primary") || key.contains("background") || key.contains("foreground")
                || key.contains("card") || key.contains("popover") || key.contains("muted")
                || key.contains("border") || key.contains("input") || key.contains("ring")
                || key.contains("destructive");
    }

    private static void addWarning(List<String> warnings, String warning) {
        if (warnings.size() < MAX_WARNINGS) warnings.add(warning);
    }

    private static String normalizeKey(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return key.startsWith("--") ? key : "--" + key;
    }

    private static String color(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
        return ThemeColorUtil.normalizeCss(value.getAsString());
    }

    public record Result(ThemeProfile profile, List<String> warnings) {
        public Result {
            warnings = warnings == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(warnings));
        }
    }
}
