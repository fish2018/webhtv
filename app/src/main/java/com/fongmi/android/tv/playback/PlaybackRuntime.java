package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.player.PlayerManager;

import java.lang.ref.WeakReference;
import java.util.UUID;

public final class PlaybackRuntime {

    private static volatile History currentHistory;
    private static volatile WeakReference<PlayerManager> currentPlayer = new WeakReference<>(null);
    private static String sessionId;
    private static String sessionSignature;

    private PlaybackRuntime() {
    }

    static void setPlayer(@Nullable PlayerManager player) {
        currentPlayer = new WeakReference<>(player);
    }

    static PlayerManager playerFor(String historyKey) {
        PlayerManager player = currentPlayer.get();
        if (player == null || player.isReleased()) return null;
        String key = player.getKey();
        return TextUtils.isEmpty(historyKey) || TextUtils.equals(key, historyKey) ? player : null;
    }

    static void updateHistory(@Nullable History history) {
        currentHistory = history == null ? null : history.copy();
    }

    static History currentHistory() {
        History history = currentHistory;
        return history == null ? null : history.copy();
    }

    static History currentHistoryFor(@Nullable String historyKey) {
        History history = currentHistory();
        if (history == null) return null;
        if (!TextUtils.isEmpty(historyKey) && !TextUtils.equals(history.getKey(), historyKey)) return null;
        return history;
    }

    public static SiteIdentity currentSiteIdentity(String fallbackDomain) {
        History history = currentHistory();
        if (history != null) {
            String siteKey = safe(history.getSiteKey()).trim();
            if (!siteKey.isEmpty()) {
                try {
                    Site site = VodConfig.get().getSite(siteKey);
                    String siteName = site == null || site.isEmpty() ? siteKey : site.getDisplayName();
                    return new SiteIdentity(siteKey, siteName, safe(fallbackDomain));
                } catch (Throwable ignored) {
                    return new SiteIdentity(siteKey, siteKey, safe(fallbackDomain));
                }
            }
        }
        return new SiteIdentity("", "未知站点", safe(fallbackDomain));
    }

    public static PlaybackIdentity currentPlaybackIdentity() {
        History history = currentHistory();
        if (history == null) return new PlaybackIdentity("", "", "");
        return new PlaybackIdentity(safe(history.getVodName()), safe(history.getVodFlag()),
                safe(history.getVodRemarks()));
    }

    public record SiteIdentity(String siteKey, String siteName, String siteDomain) {}

    public record PlaybackIdentity(String vodName, String lineName, String episodeName) {}

    static synchronized String ensureSession(History history) {
        String signature = signature(history);
        if (TextUtils.isEmpty(sessionId) || !TextUtils.equals(sessionSignature, signature)) {
            sessionId = UUID.randomUUID().toString();
            sessionSignature = signature;
        }
        return sessionId;
    }

    static synchronized String sessionId() {
        return sessionId == null ? "" : sessionId;
    }

    static synchronized void clearSession() {
        sessionId = null;
        sessionSignature = null;
    }

    private static String signature(History history) {
        if (history == null) return "";
        return safe(history.getKey()) + '\n' + safe(history.getVodFlag()) + '\n' + safe(history.getVodRemarks());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
