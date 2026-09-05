package com.fongmi.android.tv.web;

import android.content.Context;

import com.fongmi.android.tv.BuildConfig;

import java.io.IOException;

/** Public, validation-preserving facade for controlled remote Manifest recovery. */
public final class WebThemeManifestRollback {

    public enum Action {
        NONE,
        ROLLBACK,
        RETRY
    }

    private WebThemeManifestRollback() {
    }

    public static boolean supports(String manifestUrl) {
        return manifestUrl != null
                && manifestUrl.regionMatches(true, 0, "https://", 0, 8)
                && WebHomeTarget.isSafeThemeUrl(manifestUrl)
                && WebHomeTarget.isManifestUrl(manifestUrl);
    }

    public static Action action(Context context, String manifestUrl) {
        if (context == null || !supports(manifestUrl)) return Action.NONE;
        return fromLoader(WebThemeManifestLoader.rollbackAction(
                context, manifestUrl, BuildConfig.FLAVOR_mode));
    }

    public static boolean apply(Context context, String manifestUrl, Action action) throws IOException {
        if (context == null || !supports(manifestUrl)
                || action == null || action == Action.NONE) return false;
        WebThemeManifestLoader.RollbackAction loaderAction = toLoader(action);
        WebThemeManifestLoader.LoadResult result = WebThemeManifestLoader.applyRollbackAction(
                context, manifestUrl, BuildConfig.FLAVOR_mode, loaderAction);
        return action == Action.ROLLBACK ? result.usedRollback() : result.activationPending();
    }

    private static Action fromLoader(WebThemeManifestLoader.RollbackAction action) {
        if (action == WebThemeManifestLoader.RollbackAction.ROLLBACK) return Action.ROLLBACK;
        if (action == WebThemeManifestLoader.RollbackAction.RETRY) return Action.RETRY;
        return Action.NONE;
    }

    private static WebThemeManifestLoader.RollbackAction toLoader(Action action) {
        return action == Action.ROLLBACK
                ? WebThemeManifestLoader.RollbackAction.ROLLBACK
                : WebThemeManifestLoader.RollbackAction.RETRY;
    }
}
