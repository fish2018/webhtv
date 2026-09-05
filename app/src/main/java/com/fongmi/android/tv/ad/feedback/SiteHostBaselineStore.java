package com.fongmi.android.tv.ad.feedback;

import com.github.catvod.utils.Prefers;

/**
 * {@link SitePlaylistHostBaseline} 的应用级单例，持久化到 SharedPreferences。
 */
public final class SiteHostBaselineStore {

    private static final String PREF_KEY = "ad_feedback_site_hosts";

    private static volatile SitePlaylistHostBaseline instance;

    private SiteHostBaselineStore() {
    }

    public static SitePlaylistHostBaseline get() {
        if (instance == null) {
            synchronized (SiteHostBaselineStore.class) {
                if (instance == null) instance = new SitePlaylistHostBaseline(new PrefersStorage());
            }
        }
        return instance;
    }

    private static final class PrefersStorage implements SitePlaylistHostBaseline.Storage {
        @Override
        public String read() {
            try {
                return Prefers.getString(PREF_KEY, "");
            } catch (RuntimeException e) {
                return "";
            }
        }

        @Override
        public void write(String value) {
            try {
                Prefers.put(PREF_KEY, value == null ? "" : value);
            } catch (RuntimeException ignored) {
                // 基线只是归因的辅助输入，写失败不应影响播放
            }
        }
    }
}
