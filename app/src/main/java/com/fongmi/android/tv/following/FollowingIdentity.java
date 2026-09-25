package com.fongmi.android.tv.following;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.TmdbItem;

import java.util.Locale;

public final class FollowingIdentity {

    private FollowingIdentity() {
    }

    public static String normalizeMediaType(String value) {
        String mediaType = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return mediaType.isEmpty() ? "tv" : mediaType;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String seriesKey(int cid, String siteKey, String vodId) {
        return "source:" + cid + ":" + normalize(siteKey) + ":" + normalize(vodId);
    }

    public static String seriesKey(TmdbItem item) {
        return item == null || item.getTmdbId() <= 0 ? "" : "tmdb:" + normalizeMediaType(item.getMediaType()) + ":" + item.getTmdbId();
    }

    public static String identityKey(String seriesKey, int season) {
        return normalizeSeriesKey(seriesKey) + ":s" + Math.max(0, season);
    }

    public static String identityKey(TmdbItem item, int season) {
        String seriesKey = seriesKey(item);
        return TextUtils.isEmpty(seriesKey) ? "" : identityKey(seriesKey, season);
    }

    public static String identityKey(int cid, String siteKey, String vodId, int season) {
        return identityKey(seriesKey(cid, siteKey, vodId), season);
    }

    private static String normalizeSeriesKey(String seriesKey) {
        String value = seriesKey == null ? "" : seriesKey.trim();
        if (value.startsWith("tmdb:")) {
            String[] parts = value.split(":", 3);
            if (parts.length == 3) return "tmdb:" + normalizeMediaType(parts[1]) + ":" + parts[2].trim();
        }
        if (value.startsWith("source:")) {
            String[] parts = value.split(":", 4);
            if (parts.length == 4) return seriesKey(Integer.parseInt(parts[1]), parts[2], parts[3]);
        }
        return value;
    }
}
