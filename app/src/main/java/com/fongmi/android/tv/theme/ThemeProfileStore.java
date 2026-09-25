package com.fongmi.android.tv.theme;

import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Prefers;

/** SharedPreferences-backed profile store with legacy migration and last-good fallback. */
public final class ThemeProfileStore {

    public static final String KEY_PROFILE = "theme_profile_json";
    public static final String KEY_LAST_GOOD = "theme_profile_last_good";
    public static final String KEY_SCHEMA = "theme_profile_schema";

    private ThemeProfileStore() {
    }

    public static ThemeProfile load() {
        ThemeProfile current = parse(Prefers.getString(KEY_PROFILE));
        if (current != null) return current;
        ThemeProfile lastGood = parse(Prefers.getString(KEY_LAST_GOOD));
        return lastGood != null ? lastGood : migrateLegacy(Setting.getThemeColor());
    }

    public static ThemeProfile ensureMigrated() {
        String raw = Prefers.getString(KEY_PROFILE);
        ThemeProfile profile = parse(raw);
        if (profile != null) return profile;
        profile = load();
        if (raw == null || raw.isBlank()) write(profile, true);
        return profile;
    }

    public static ApplyResult apply(ThemeProfile draft) {
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(draft);
        if (!result.valid()) return ApplyResult.failure(result.message());
        String json;
        try {
            json = ThemeProfileCodec.encode(result.profile());
        } catch (RuntimeException e) {
            return ApplyResult.failure(e.getMessage() == null ? "theme serialization failed" : e.getMessage());
        }
        if (!writeJson(result.profile(), json)) return ApplyResult.failure("theme preferences could not be saved");
        return ApplyResult.success(result.profile());
    }

    public static ApplyResult reset() {
        return apply(ThemeProfile.defaultProfile());
    }

    public static ThemeProfile migrateLegacy(int legacyThemeColor) {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        if (legacyThemeColor == 0) {
            profile.seedSource = ThemeProfile.SEED_WALLPAPER;
        } else if (legacyThemeColor != -1) {
            profile.seedSource = ThemeProfile.SEED_CUSTOM;
            profile.seedColor = ThemeColorUtil.format(legacyThemeColor);
            profile.id = "webhtv.legacy-custom";
            profile.name = "Custom";
        }
        return profile;
    }

    public static int legacyThemeColor(ThemeProfile profile) {
        if (profile == null || ThemeProfile.SEED_NONE.equals(profile.seedSource)) return -1;
        if (ThemeProfile.SEED_WALLPAPER.equals(profile.seedSource)) return 0;
        return ThemeColorUtil.parse(profile.seedColor, -1);
    }

    private static ThemeProfile parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ThemeProfileCodec.parse(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean write(ThemeProfile profile, boolean updateLastGood) {
        try {
            String json = ThemeProfileCodec.encode(profile);
            return writeJson(profile, json, updateLastGood);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean writeJson(ThemeProfile profile, String json) {
        return writeJson(profile, json, true);
    }

    private static boolean writeJson(ThemeProfile profile, String json, boolean updateLastGood) {
        try {
            android.content.SharedPreferences.Editor editor = Prefers.getPrefers().edit()
                    .putString(KEY_PROFILE, json)
                    .putInt(KEY_SCHEMA, ThemeProfile.SCHEMA_VERSION)
                    .putInt("theme_color", legacyThemeColor(profile));
            if (updateLastGood) editor.putString(KEY_LAST_GOOD, json);
            return editor.commit();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public record ApplyResult(boolean success, ThemeProfile profile, String error) {

        public static ApplyResult success(ThemeProfile profile) {
            return new ApplyResult(true, profile, "");
        }

        public static ApplyResult failure(String error) {
            return new ApplyResult(false, null, error == null ? "unknown error" : error);
        }
    }
}
