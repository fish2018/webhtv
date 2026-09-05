package com.fongmi.android.tv.web;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Immutable TMDB enrichment snapshot consumed by the public detail DTO mapper. */
public final class WebThemeDetailMetadata {

    public static final WebThemeDetailMetadata EMPTY = new WebThemeDetailMetadata(
            null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    private final TmdbItem item;
    private final JsonObject detail;
    private final List<TmdbPerson> cast;
    private final List<TmdbPerson> crew;
    private final List<String> gallery;
    private final List<TmdbItem> recommendations;
    private final List<TmdbItem> personalTmdbRecommendations;
    private final List<TmdbItem> personalDoubanRecommendations;
    private final List<TmdbItem> personalAiRecommendations;

    private WebThemeDetailMetadata(TmdbItem item, JsonObject detail, List<TmdbPerson> cast,
            List<TmdbPerson> crew, List<String> gallery, List<TmdbItem> recommendations,
            List<TmdbItem> personalTmdbRecommendations, List<TmdbItem> personalDoubanRecommendations,
            List<TmdbItem> personalAiRecommendations) {
        this.item = item;
        this.detail = detail == null ? null : detail.deepCopy();
        this.cast = copy(cast);
        this.crew = copy(crew);
        this.gallery = copy(gallery);
        this.recommendations = copy(recommendations);
        this.personalTmdbRecommendations = copy(personalTmdbRecommendations);
        this.personalDoubanRecommendations = copy(personalDoubanRecommendations);
        this.personalAiRecommendations = copy(personalAiRecommendations);
    }

    public static WebThemeDetailMetadata fromTmdb(TmdbItem item, JsonObject detail, List<TmdbPerson> cast,
            List<TmdbPerson> crew, List<String> gallery, List<TmdbItem> recommendations) {
        return fromTmdb(item, detail, cast, crew, gallery, recommendations, List.of(), List.of(), List.of());
    }

    public static WebThemeDetailMetadata fromTmdb(TmdbItem item, JsonObject detail, List<TmdbPerson> cast,
            List<TmdbPerson> crew, List<String> gallery, List<TmdbItem> recommendations,
            List<TmdbItem> personalTmdbRecommendations, List<TmdbItem> personalDoubanRecommendations,
            List<TmdbItem> personalAiRecommendations) {
        if (item == null && detail == null && empty(cast) && empty(crew) && empty(gallery)
                && empty(recommendations) && empty(personalTmdbRecommendations)
                && empty(personalDoubanRecommendations) && empty(personalAiRecommendations)) {
            return EMPTY;
        }
        return new WebThemeDetailMetadata(item, detail, cast, crew, gallery, recommendations,
                personalTmdbRecommendations, personalDoubanRecommendations, personalAiRecommendations);
    }

    TmdbItem getItem() {
        return item;
    }

    JsonObject getDetail() {
        return detail;
    }

    List<TmdbPerson> getCast() {
        return cast;
    }

    List<TmdbPerson> getCrew() {
        return crew;
    }

    List<String> getGallery() {
        return gallery;
    }

    List<TmdbItem> getRecommendations() {
        return recommendations;
    }

    List<TmdbItem> getPersonalTmdbRecommendations() {
        return personalTmdbRecommendations;
    }

    List<TmdbItem> getPersonalDoubanRecommendations() {
        return personalDoubanRecommendations;
    }

    List<TmdbItem> getPersonalAiRecommendations() {
        return personalAiRecommendations;
    }

    boolean isEmpty() {
        return this == EMPTY || (item == null && detail == null && cast.isEmpty() && crew.isEmpty()
                && gallery.isEmpty() && recommendations.isEmpty() && personalTmdbRecommendations.isEmpty()
                && personalDoubanRecommendations.isEmpty() && personalAiRecommendations.isEmpty());
    }

    private static boolean empty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
