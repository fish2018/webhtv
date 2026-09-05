package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;
import com.google.gson.Gson;

public final class HistoryResumePayload {

    private static final String PREFIX = "tmdb-season:";
    private static final Gson GSON = new Gson();

    private HistoryResumePayload() {
    }

    public static String encode(History history) {
        if (!TmdbSeasonProgressStore.isEligible(history)) {
            return history == null ? "" : history.getKey();
        }
        Reference reference = new Reference();
        reference.key = history.getKey();
        reference.tmdbId = history.getTmdbId();
        reference.mediaType = TmdbSeasonProgress.normalizeMediaType(history.getMediaType());
        reference.seasonNumber = history.getTmdbSeasonNumber();
        return PREFIX + GSON.toJson(reference);
    }

    public static History restore(int cid, String payload) {
        Reference reference = reference(payload);
        if (reference == null || TextUtils.isEmpty(reference.key)) return null;
        History current = History.find(cid, reference.key);
        if (!isSeasonal(payload)) return current;
        TmdbSeasonProgress progress = TmdbSeasonProgressStore.find(
                cid, reference.mediaType, reference.tmdbId, reference.seasonNumber);
        return restore(current, progress, payload);
    }

    static History restore(History current, TmdbSeasonProgress progress, String payload) {
        Reference reference = reference(payload);
        if (reference == null || current == null || !TextUtils.equals(current.getKey(), reference.key)) {
            return null;
        }
        if (!isSeasonal(payload)) return current;
        // Older History rows may predate the season snapshot table. The route row is
        // still safe when it represents the exact requested TMDB season.
        if (progress == null) {
            return current.getTmdbId() == reference.tmdbId
                    && current.getTmdbSeasonNumber() == reference.seasonNumber
                    && TextUtils.equals(TmdbSeasonProgress.normalizeMediaType(current.getMediaType()), reference.mediaType)
                    ? current : null;
        }

        if (progress.cid != current.getCid()
                || progress.tmdbId != reference.tmdbId
                || progress.seasonNumber != reference.seasonNumber
                || !TextUtils.equals(TmdbSeasonProgress.normalizeMediaType(progress.mediaType), reference.mediaType)
                || !TextUtils.equals(progress.sourceHistoryKey, current.getKey())
                || current.getTmdbId() != reference.tmdbId
                || !TextUtils.equals(TmdbSeasonProgress.normalizeMediaType(current.getMediaType()), reference.mediaType)) {
            return null;
        }
        History restored = current.copy();
        TmdbSeasonProgressStore.apply(restored, progress);
        return restored;
    }

    private static boolean isSeasonal(String payload) {
        return payload != null && payload.startsWith(PREFIX);
    }

    private static Reference reference(String payload) {
        if (TextUtils.isEmpty(payload)) return null;
        if (!isSeasonal(payload)) {
            Reference reference = new Reference();
            reference.key = payload;
            return reference;
        }
        try {
            return GSON.fromJson(payload.substring(PREFIX.length()), Reference.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class Reference {
        String key;
        int tmdbId;
        String mediaType;
        int seasonNumber;
    }
}
