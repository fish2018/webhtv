package com.fongmi.android.tv.utils;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

public final class AppCache {

    public static final String KEY_TMDB_MATCH = "tmdb_match_cache";
    public static final String KEY_TMDB_SEASON_MATCH = "tmdb_season_match_cache";
    public static final String KEY_MEDIA_TITLE_LEARNING = "media_title_learning";

    private static final String DIR = "app_cache";
    private static final String WEB_CACHE_PREFIX = "cache_";

    private AppCache() {
    }

    public static String key(String rule, String key) {
        return WEB_CACHE_PREFIX + (TextUtils.isEmpty(rule) ? "" : rule + "_") + safe(key);
    }

    public static String get(String key) {
        if (TextUtils.isEmpty(key)) return "";
        try {
            File file = file(key);
            if (file.exists()) return Path.read(file);
            return migrateLegacy(key);
        } catch (Throwable e) {
            return "";
        }
    }

    public static String get(String rule, String key) {
        return get(key(rule, key));
    }

    public static void put(String key, String value) {
        if (TextUtils.isEmpty(key) || value == null) return;
        try {
            Path.write(file(key), value.getBytes(StandardCharsets.UTF_8));
            Prefers.remove(key);
        } catch (Throwable ignored) {
        }
    }

    public static void put(String rule, String key, String value) {
        put(key(rule, key), value);
    }

    public static void remove(String key) {
        if (TextUtils.isEmpty(key)) return;
        try {
            Path.clear(file(key));
            Prefers.remove(key);
        } catch (Throwable ignored) {
        }
    }

    public static void remove(String rule, String key) {
        remove(key(rule, key));
    }

    public static void clearLegacyPreferences() {
        try {
            SharedPreferences prefs = Prefers.getPrefers();
            SharedPreferences.Editor editor = prefs.edit()
                    .remove(KEY_TMDB_MATCH)
                    .remove(KEY_TMDB_SEASON_MATCH)
                    .remove(KEY_MEDIA_TITLE_LEARNING);
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (entry.getKey().startsWith(WEB_CACHE_PREFIX)) editor.remove(entry.getKey());
            }
            editor.apply();
        } catch (Throwable ignored) {
        }
    }

    private static String migrateLegacy(String key) {
        try {
            SharedPreferences prefs = Prefers.getPrefers();
            if (!prefs.contains(key)) return "";
            String value = Prefers.getString(key);
            Path.write(file(key), value.getBytes(StandardCharsets.UTF_8));
            Prefers.remove(key);
            return value;
        } catch (Throwable e) {
            return "";
        }
    }

    private static File file(String key) {
        File dir = Path.cache(DIR);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, md5(key) + ".txt");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) builder.append(String.format(Locale.US, "%02x", value));
            return builder.toString();
        } catch (Throwable e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
