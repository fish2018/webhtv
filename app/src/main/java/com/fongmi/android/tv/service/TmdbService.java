package com.fongmi.android.tv.service;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.Response;

public class TmdbService {

    public JsonObject searchRaw(@NonNull String keyword, @NonNull TmdbConfig config) throws Exception {
        ensureReady(config);
        try (Response response = com.github.catvod.net.OkHttp.newCall(searchUrl(keyword, config)).execute()) {
            if (response.body() == null) throw new IllegalStateException("TMDB 搜索返回为空");
            if (!response.isSuccessful()) throw new IllegalStateException("TMDB 搜索失败: HTTP " + response.code());
            return App.gson().fromJson(response.body().string(), JsonObject.class);
        }
    }

    public List<TmdbItem> search(@NonNull String keyword, @NonNull TmdbConfig config) throws Exception {
        JsonObject body = searchRaw(keyword, config);
        List<TmdbItem> items = new ArrayList<>();
        JsonArray results = body != null && body.has("results") ? body.getAsJsonArray("results") : new JsonArray();
        for (JsonElement element : results) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String mediaType = string(object, "media_type");
            if (!"movie".equals(mediaType) && !"tv".equals(mediaType)) continue;
            String posterPath = string(object, "poster_path");
            String backdropPath = string(object, "backdrop_path");
            String title = "movie".equals(mediaType) ? string(object, "title", "name") : string(object, "name", "title");
            String date = "movie".equals(mediaType) ? string(object, "release_date") : string(object, "first_air_date");
            String vote = object.has("vote_average") ? String.format(Locale.US, "%.1f", object.get("vote_average").getAsDouble()) : "";
            String subtitle = buildSubtitle(mediaType, date, vote);
            items.add(new TmdbItem(object.get("id").getAsInt(), mediaType, title, subtitle, string(object, "overview"), image(config.getImageBase(), posterPath), image(config.getBackdropBase(), backdropPath)));
        }
        return items;
    }

    public JsonObject detail(@NonNull TmdbItem item, @NonNull TmdbConfig config) throws Exception {
        ensureReady(config);
        HttpUrl url = HttpUrl.parse(config.getApiBase() + "/" + item.getMediaType() + "/" + item.getTmdbId()).newBuilder()
                .addQueryParameter("api_key", config.getApiKey())
                .addQueryParameter("language", config.getLanguage())
                .addQueryParameter("append_to_response", "images,credits,recommendations")
                .addQueryParameter("include_image_language", config.getLanguage() + ",null")
                .build();
        try (Response response = com.github.catvod.net.OkHttp.newCall(url.toString()).execute()) {
            if (response.body() == null) throw new IllegalStateException("TMDB 详情返回为空");
            if (!response.isSuccessful()) throw new IllegalStateException("TMDB 详情失败: HTTP " + response.code());
            return App.gson().fromJson(response.body().string(), JsonObject.class);
        }
    }

    public JsonObject season(@NonNull TmdbItem item, int seasonNumber, @NonNull TmdbConfig config) throws Exception {
        ensureReady(config);
        HttpUrl url = HttpUrl.parse(config.getApiBase() + "/tv/" + item.getTmdbId() + "/season/" + seasonNumber).newBuilder()
                .addQueryParameter("api_key", config.getApiKey())
                .addQueryParameter("language", config.getLanguage())
                .build();
        try (Response response = com.github.catvod.net.OkHttp.newCall(url.toString()).execute()) {
            if (response.body() == null) throw new IllegalStateException("TMDB 分季返回为空");
            if (!response.isSuccessful()) throw new IllegalStateException("TMDB 分季失败: HTTP " + response.code());
            return App.gson().fromJson(response.body().string(), JsonObject.class);
        }
    }

    public JsonObject person(int personId, @NonNull TmdbConfig config) throws Exception {
        ensureReady(config);
        HttpUrl url = HttpUrl.parse(config.getApiBase() + "/person/" + personId).newBuilder()
                .addQueryParameter("api_key", config.getApiKey())
                .addQueryParameter("language", config.getLanguage())
                .addQueryParameter("append_to_response", "combined_credits")
                .build();
        try (Response response = com.github.catvod.net.OkHttp.newCall(url.toString()).execute()) {
            if (response.body() == null) throw new IllegalStateException("TMDB 演员作品返回为空");
            if (!response.isSuccessful()) throw new IllegalStateException("TMDB 演员作品失败: HTTP " + response.code());
            return App.gson().fromJson(response.body().string(), JsonObject.class);
        }
    }

    public List<TmdbPerson> cast(JsonObject detail, @NonNull TmdbConfig config) {
        List<TmdbPerson> items = new ArrayList<>();
        JsonArray results = array(detail, "credits", "cast");
        for (JsonElement element : results) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!object.has("id") || object.get("id").isJsonNull()) continue;
            items.add(new TmdbPerson(
                    object.get("id").getAsInt(),
                    string(object, "name"),
                    string(object, "character", "known_for_department"),
                    image(config.getImageBase(), string(object, "profile_path")),
                    string(object, "known_for_department"),
                    ""
            ));
            if (items.size() >= 18) break;
        }
        return items;
    }

    public List<TmdbEpisode> episodes(JsonObject season, @NonNull TmdbConfig config) {
        List<TmdbEpisode> items = new ArrayList<>();
        JsonArray results = array(season, "episodes");
        for (JsonElement element : results) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            int number = object.has("episode_number") && !object.get("episode_number").isJsonNull() ? object.get("episode_number").getAsInt() : items.size() + 1;
            items.add(new TmdbEpisode(
                    number,
                    string(object, "name"),
                    string(object, "air_date"),
                    string(object, "overview"),
                    image(config.getBackdropBase(), string(object, "still_path"))
            ));
        }
        return items;
    }

    public List<String> photos(JsonObject detail, @NonNull TmdbConfig config) {
        List<String> items = new ArrayList<>();
        for (JsonElement element : array(detail, "images", "backdrops")) {
            if (!element.isJsonObject()) continue;
            String url = image(config.getBackdropBase(), string(element.getAsJsonObject(), "file_path"));
            if (TextUtils.isEmpty(url) || items.contains(url)) continue;
            items.add(url);
            if (items.size() >= 24) break;
        }
        return items;
    }

    public TmdbPerson personProfile(JsonObject detail, @NonNull TmdbConfig config) {
        if (detail == null) return new TmdbPerson(0, "", "", "", "", "");
        int personId = detail.has("id") && !detail.get("id").isJsonNull() ? detail.get("id").getAsInt() : 0;
        List<String> parts = new ArrayList<>();
        String department = string(detail, "known_for_department");
        String birthday = string(detail, "birthday");
        String deathday = string(detail, "deathday");
        String birthplace = string(detail, "place_of_birth");
        String aliases = aliases(detail);
        if (!TextUtils.isEmpty(department)) parts.add(department);
        if (!TextUtils.isEmpty(birthday)) parts.add(TextUtils.isEmpty(deathday) ? birthday : birthday + " - " + deathday);
        if (!TextUtils.isEmpty(birthplace)) parts.add(birthplace);
        if (!TextUtils.isEmpty(aliases)) parts.add(aliases);
        return new TmdbPerson(
                personId,
                string(detail, "name"),
                TextUtils.join(" · ", parts),
                image(config.getImageBase(), string(detail, "profile_path")),
                string(detail, "known_for_department"),
                string(detail, "biography")
        );
    }

    public List<TmdbItem> recommendations(JsonObject detail, @NonNull TmdbConfig config) {
        return items(array(detail, "recommendations", "results"), config);
    }

    public List<TmdbItem> personWorks(JsonObject person, @NonNull TmdbConfig config) {
        Map<String, TmdbItem> items = new LinkedHashMap<>();
        addWorks(items, array(person, "combined_credits", "cast"), config);
        addWorks(items, array(person, "combined_credits", "crew"), config);
        return items.values().stream().sorted(Comparator.comparing(this::sortDate).reversed()).limit(30).toList();
    }

    public String image(String base, String path) {
        if (TextUtils.isEmpty(path)) return "";
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private String searchUrl(String keyword, TmdbConfig config) {
        return HttpUrl.parse(config.getApiBase() + "/search/multi").newBuilder()
                .addQueryParameter("api_key", config.getApiKey())
                .addQueryParameter("language", config.getLanguage())
                .addQueryParameter("query", keyword)
                .build()
                .toString();
    }

    private void ensureReady(TmdbConfig config) {
        if (!config.sanitize().isReady()) throw new IllegalStateException("请先配置 TMDB API Key");
    }

    private String buildSubtitle(String mediaType, String date, String vote) {
        List<String> parts = new ArrayList<>();
        parts.add("tv".equals(mediaType) ? "剧集" : "电影");
        if (!TextUtils.isEmpty(date)) parts.add(date);
        if (!TextUtils.isEmpty(vote)) parts.add("评分 " + vote);
        return TextUtils.join(" · ", parts);
    }

    private String string(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
                String value = object.get(key).getAsString();
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }

    private void addWorks(Map<String, TmdbItem> items, JsonArray array, TmdbConfig config) {
        for (TmdbItem item : items(array, config)) {
            items.putIfAbsent(item.getMediaType() + ":" + item.getTmdbId(), item);
        }
    }

    private String aliases(JsonObject detail) {
        JsonArray aliases = array(detail, "also_known_as");
        List<String> values = new ArrayList<>();
        for (JsonElement element : aliases) {
            if (!element.isJsonPrimitive()) continue;
            String value = element.getAsString();
            if (!TextUtils.isEmpty(value) && values.size() < 2) values.add(value);
        }
        return values.isEmpty() ? "" : "又名 " + TextUtils.join(" / ", values);
    }

    private List<TmdbItem> items(JsonArray array, TmdbConfig config) {
        List<TmdbItem> items = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String mediaType = normalizeMediaType(string(object, "media_type"));
            if (TextUtils.isEmpty(mediaType)) continue;
            if (!object.has("id") || object.get("id").isJsonNull()) continue;
            String title = "movie".equals(mediaType) ? string(object, "title", "name") : string(object, "name", "title");
            String date = "movie".equals(mediaType) ? string(object, "release_date") : string(object, "first_air_date");
            String vote = object.has("vote_average") && !object.get("vote_average").isJsonNull() ? String.format(Locale.US, "%.1f", object.get("vote_average").getAsDouble()) : "";
            String subtitle = buildSubtitle(mediaType, date, vote);
            String credit = credit(object);
            String posterPath = string(object, "poster_path");
            String backdropPath = string(object, "backdrop_path");
            items.add(new TmdbItem(object.get("id").getAsInt(), mediaType, title, subtitle, string(object, "overview"), image(config.getImageBase(), posterPath), image(config.getBackdropBase(), backdropPath), credit));
        }
        return items;
    }

    private String credit(JsonObject object) {
        String character = string(object, "character");
        if (!TextUtils.isEmpty(character)) return "饰 " + character;
        String job = string(object, "job");
        if (!TextUtils.isEmpty(job)) return job;
        return string(object, "department");
    }

    private String sortDate(TmdbItem item) {
        String subtitle = item.getSubtitle();
        return subtitle == null ? "" : subtitle.replaceAll("^.*?(\\d{4}.*)$", "$1");
    }

    private JsonArray array(JsonObject object, String... keys) {
        JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return new JsonArray();
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return new JsonArray();
            current = currentObject.get(key);
        }
        return current != null && current.isJsonArray() ? current.getAsJsonArray() : new JsonArray();
    }

    private String normalizeMediaType(String mediaType) {
        if ("movie".equals(mediaType) || "tv".equals(mediaType)) return mediaType;
        return "";
    }
}
