package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/** UI-facing TMDB data assembled from source payload or the existing network path. */
public record TmdbBundle(
        TmdbItem item,
        JsonObject detail,
        List<TmdbPerson> cast,
        List<TmdbPerson> creators,
        List<String> photos,
        List<TmdbItem> related,
        List<Integer> seasons,
        Map<Integer, Integer> seasonCounts,
        Map<Integer, List<TmdbEpisode>> seasonEpisodes,
        Map<Integer, List<TmdbPerson>> seasonCast,
        Map<Integer, List<String>> seasonPhotos
) {

    public boolean identityMatches(TmdbItem other) {
        return item != null && other != null && item.getTmdbId() > 0
                && item.getTmdbId() == other.getTmdbId()
                && item.getMediaType().equalsIgnoreCase(other.getMediaType());
    }
}
