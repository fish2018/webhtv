package com.fongmi.android.tv.bean;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Set;

/** Lightweight, type-safe reader for the TMDB-shaped detail object in a C16 payload. */
public final class TmdbSourceDetail {

    private static final Set<String> FIELDS = Set.of(
            "id", "title", "name", "original_title", "original_name", "overview", "tagline", "status",
            "poster_path", "backdrop_path", "images", "release_date", "first_air_date", "vote_average",
            "vote_count", "genres", "origin_country", "original_language", "runtime", "episode_run_time",
            "number_of_seasons", "number_of_episodes", "seasons", "episodes", "external_ids", "credits",
            "aggregate_credits", "videos", "recommendations", "similar", "translations", "content_ratings",
            "release_dates"
    );

    private final JsonObject detail;

    public TmdbSourceDetail(@Nullable String detailJson) {
        this.detail = objectOrEmpty(detailJson);
    }

    public String toJson() {
        return detail.toString();
    }

    public JsonObject object() {
        return detail.deepCopy();
    }

    public boolean has(String field) {
        return FIELDS.contains(field) && detail.has(field) && !detail.get(field).isJsonNull();
    }

    public int id() {
        return intValue("id", 0);
    }

    public String title() {
        String title = string("title");
        return title.isEmpty() ? string("name") : title;
    }

    public String name() {
        return string("name");
    }

    public String originalTitle() {
        String title = string("original_title");
        return title.isEmpty() ? string("original_name") : title;
    }

    public String overview() {
        return string("overview");
    }

    public String tagline() {
        return string("tagline");
    }

    public String status() {
        return string("status");
    }

    public String posterPath() {
        return string("poster_path");
    }

    public String backdropPath() {
        return string("backdrop_path");
    }

    public String releaseDate() {
        String date = string("release_date");
        return date.isEmpty() ? string("first_air_date") : date;
    }

    public double voteAverage() {
        return doubleValue("vote_average", 0d);
    }

    public long voteCount() {
        return longValue("vote_count", 0L);
    }

    public JsonArray genres() {
        return array("genres");
    }

    public JsonArray originCountry() {
        return array("origin_country");
    }

    public String originalLanguage() {
        return string("original_language");
    }

    public int runtime() {
        return intValue("runtime", 0);
    }

    public JsonArray episodeRunTime() {
        return array("episode_run_time");
    }

    public int numberOfSeasons() {
        return intValue("number_of_seasons", 0);
    }

    public int numberOfEpisodes() {
        return intValue("number_of_episodes", 0);
    }

    @Nullable
    public JsonObject images() {
        return object("images");
    }

    @Nullable
    public JsonObject credits() {
        JsonObject value = object("credits");
        return value == null ? object("aggregate_credits") : value;
    }

    @Nullable
    public JsonObject externalIds() {
        return object("external_ids");
    }

    @Nullable
    public JsonObject videos() {
        return object("videos");
    }

    @Nullable
    public JsonObject recommendations() {
        return object("recommendations");
    }

    @Nullable
    public JsonObject similar() {
        return object("similar");
    }

    public JsonArray seasons() {
        return array("seasons");
    }

    public JsonArray episodes() {
        return array("episodes");
    }

    @Nullable
    public JsonObject translations() {
        return object("translations");
    }

    @Nullable
    public JsonObject contentRatings() {
        return object("content_ratings");
    }

    @Nullable
    public JsonObject releaseDates() {
        return object("release_dates");
    }

    public String string(String field) {
        if (!FIELDS.contains(field)) return "";
        JsonElement value = detail.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return "";
        String result = value.getAsString();
        return result == null ? "" : result.trim();
    }

    public int intValue(String field, int fallback) {
        long value = longValue(field, fallback);
        return value > Integer.MAX_VALUE || value < Integer.MIN_VALUE ? fallback : (int) value;
    }

    public long longValue(String field, long fallback) {
        if (!FIELDS.contains(field)) return fallback;
        JsonElement value = detail.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            return value.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public double doubleValue(String field, double fallback) {
        if (!FIELDS.contains(field)) return fallback;
        JsonElement value = detail.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            double result = value.getAsDouble();
            return Double.isFinite(result) ? result : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    @Nullable
    public JsonObject object(String field) {
        if (!FIELDS.contains(field)) return null;
        JsonElement value = detail.get(field);
        return value != null && value.isJsonObject() ? value.getAsJsonObject().deepCopy() : null;
    }

    public JsonArray array(String field) {
        if (!FIELDS.contains(field)) return new JsonArray();
        JsonElement value = detail.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray().deepCopy() : new JsonArray();
    }

    private static JsonObject objectOrEmpty(@Nullable String json) {
        if (json == null || json.isBlank()) return new JsonObject();
        try {
            JsonElement element = JsonParser.parseString(json);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }
}
