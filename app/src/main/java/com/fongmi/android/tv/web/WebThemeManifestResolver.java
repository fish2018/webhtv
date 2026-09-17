package com.fongmi.android.tv.web;

import android.content.Context;

import java.io.IOException;

/** Resolves a validated Manifest page into the concrete target consumed by the page host. */
final class WebThemeManifestResolver {

    record Resolution(WebHomeTarget target, WebThemeManifestLoader.CacheState cacheState,
            IOException refreshFailure, boolean activationPending,
            boolean rollbackAvailable, String revision) {
        boolean usedLastKnownGood() {
            return cacheState == WebThemeManifestLoader.CacheState.LAST_KNOWN_GOOD;
        }

        boolean usedRollback() {
            return cacheState == WebThemeManifestLoader.CacheState.ROLLED_BACK;
        }
    }

    private final Context context;
    private final String platformTarget;

    WebThemeManifestResolver(Context context, String platformTarget) {
        this.context = context;
        this.platformTarget = platformTarget;
    }

    Resolution resolvePageResult(WebHomeTarget configured, WebThemePage page, boolean force) throws IOException {
        if (!isManifest(configured) || page == null) return null;
        WebThemeManifestLoader.LoadResult loaded = WebThemeManifestLoader.loadResult(
                context, configured.getUrl(), platformTarget, force);
        try {
            return resolve(configured, page, loaded);
        } catch (IllegalArgumentException failure) {
            if (!loaded.activationPending() || !loaded.rollbackAvailable()) throw failure;
            return rollbackPageResult(configured, page, loaded.revision());
        }
    }

    Resolution rollbackPageResult(WebHomeTarget configured, WebThemePage page,
            String expectedRevision) throws IOException {
        if (!isManifest(configured) || page == null) return null;
        WebThemeManifestLoader.LoadResult loaded = WebThemeManifestLoader.rollbackPending(
                context, configured.getUrl(), platformTarget, expectedRevision);
        return resolve(configured, page, loaded);
    }

    boolean accept(String manifestUrl, String expectedRevision) {
        if (!WebHomeTarget.isSafeThemeUrl(manifestUrl)
                || !WebHomeTarget.isManifestUrl(manifestUrl)) return false;
        return WebThemeManifestLoader.accept(
                context, manifestUrl, platformTarget, expectedRevision);
    }

    WebHomeTarget resolvePage(WebHomeTarget configured, WebThemePage page, boolean force) throws IOException {
        Resolution resolved = resolvePageResult(configured, page, force);
        return resolved == null ? null : resolved.target();
    }

    private static Resolution resolve(WebHomeTarget configured, WebThemePage page,
            WebThemeManifestLoader.LoadResult loaded) {
        WebHomeTarget target = WebHomeTarget.forManifestPage(configured, loaded.manifest(), page);
        return new Resolution(target, loaded.state(), loaded.refreshFailure(),
                loaded.activationPending(), loaded.rollbackAvailable(), loaded.revision());
    }

    private static boolean isManifest(WebHomeTarget configured) {
        return configured != null && configured.isManifest();
    }
}
