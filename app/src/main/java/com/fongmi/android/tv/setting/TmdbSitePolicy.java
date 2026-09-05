package com.fongmi.android.tv.setting;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.web.WebHomeInlineVodStore;

/** Resolves the real site identity behind synthetic playback routes before applying TMDB rules. */
public final class TmdbSitePolicy {

    private TmdbSitePolicy() {
    }

    public static boolean isEnabled(String key, String id) {
        return isEnabled(TmdbConfig.objectFrom(Setting.getTmdbConfig()), key, id);
    }

    public static boolean isEnabled(TmdbConfig config, String key, String id) {
        if (config == null) return false;
        Site site = resolve(key, id);
        String effectiveKey = site.isEmpty() ? key : site.getKey();
        String effectiveName = site.isEmpty() ? "" : site.getName();
        return config.isSiteEnabled(effectiveKey, effectiveName);
    }

    private static Site resolve(String key, String id) {
        if (WebHomeInlineVodStore.KEY.equals(key)) {
            Site inlineSite = WebHomeInlineVodStore.getOriginSite(id);
            if (!inlineSite.isEmpty()) return inlineSite;
        }
        return VodConfig.get().getSite(key);
    }
}
