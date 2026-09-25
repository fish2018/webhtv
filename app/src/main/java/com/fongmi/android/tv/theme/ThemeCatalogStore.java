package com.fongmi.android.tv.theme;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists validated local catalog snapshots. The APK asset is authoritative; the current
 * snapshot is a startup fallback, and the prior verified current snapshot is retained as
 * last-good for automatic or explicit rollback.
 */
public final class ThemeCatalogStore {

    public static final String PREFS_NAME = "webhtv_theme_catalog";
    public static final String KEY_CURRENT = "current";
    public static final String KEY_LAST_GOOD = "last_good";
    public static final String KEY_VERSION = "version";

    private static final Object LOCK = new Object();
    private static volatile LoadResult memory;

    private ThemeCatalogStore() {
    }

    public static LoadResult load(Context context) {
        if (context == null) return LoadResult.failure("theme catalog context is unavailable");
        LoadResult cached = memory;
        if (cached != null) return cached;
        synchronized (LOCK) {
            if (memory != null) return memory;
            SharedPreferences preferences = preferences(context);
            ThemeCatalog.Result bundled = ThemeCatalog.load(context);
            memory = bundled.success() ? activateBundled(preferences, bundled.catalog())
                    : recover(preferences, bundled.error());
            return memory;
        }
    }

    /** Promotes the verified last-good snapshot to current for a future UI rollback action. */
    public static LoadResult rollback(Context context) {
        if (context == null) return LoadResult.failure("theme catalog context is unavailable");
        synchronized (LOCK) {
            SharedPreferences preferences = preferences(context);
            ThemeCatalog.Catalog lastGood = ThemeCatalog.fromCache(
                    preferences.getString(KEY_LAST_GOOD, null));
            if (lastGood == null) return LoadResult.failure("no verified last-good theme catalog");
            String snapshot = ThemeCatalog.toCacheJson(lastGood);
            if (!preferences.edit().putString(KEY_CURRENT, snapshot)
                    .putInt(KEY_VERSION, lastGood.version()).commit()) {
                return LoadResult.failure("theme catalog rollback could not be saved");
            }
            return memory = new LoadResult(lastGood, State.ROLLED_BACK, "");
        }
    }

    private static LoadResult activateBundled(SharedPreferences preferences,
            ThemeCatalog.Catalog bundled) {
        ThemeCatalog.Catalog current = ThemeCatalog.fromCache(preferences.getString(KEY_CURRENT, null));
        ThemeCatalog.Catalog lastGood = ThemeCatalog.fromCache(preferences.getString(KEY_LAST_GOOD, null));
        String snapshot = ThemeCatalog.toCacheJson(bundled);
        boolean changed = !sameRevision(current, bundled);
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_CURRENT, snapshot)
                .putInt(KEY_VERSION, bundled.version());
        if (changed && current != null) {
            editor.putString(KEY_LAST_GOOD, ThemeCatalog.toCacheJson(current));
        } else if (lastGood == null) {
            editor.putString(KEY_LAST_GOOD, snapshot);
        }
        if (!editor.commit()) return new LoadResult(bundled, State.BUNDLED,
                "theme catalog cache could not be saved");
        return new LoadResult(bundled, State.BUNDLED, "");
    }

    private static LoadResult recover(SharedPreferences preferences, String assetError) {
        ThemeCatalog.Catalog current = ThemeCatalog.fromCache(preferences.getString(KEY_CURRENT, null));
        if (current != null) return new LoadResult(current, State.CACHE, assetError);
        ThemeCatalog.Catalog lastGood = ThemeCatalog.fromCache(preferences.getString(KEY_LAST_GOOD, null));
        if (lastGood == null) return LoadResult.failure(assetError);
        String snapshot = ThemeCatalog.toCacheJson(lastGood);
        if (!preferences.edit().putString(KEY_CURRENT, snapshot)
                .putInt(KEY_VERSION, lastGood.version()).commit()) {
            return new LoadResult(lastGood, State.ROLLED_BACK,
                    "theme catalog recovered in memory but could not update current cache");
        }
        return new LoadResult(lastGood, State.ROLLED_BACK, assetError);
    }

    private static boolean sameRevision(ThemeCatalog.Catalog first, ThemeCatalog.Catalog second) {
        return first != null && second != null && first.version() == second.version()
                && first.indexSha256().equals(second.indexSha256());
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public enum State {
        BUNDLED,
        CACHE,
        ROLLED_BACK,
        FAILED
    }

    public record LoadResult(ThemeCatalog.Catalog catalog, State state, String error) {

        static LoadResult failure(String error) {
            return new LoadResult(null, State.FAILED,
                    error == null || error.isBlank() ? "theme catalog is unavailable" : error);
        }

        public boolean success() {
            return catalog != null && state != State.FAILED;
        }
    }
}
