package com.fongmi.android.tv.service;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.github.catvod.utils.Path;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class TmdbService {

    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long DETAIL_CACHE_TTL = DAY * 7;
    private static final long PERSON_CACHE_TTL = DAY * 7;
    private static final long SEASON_CACHE_TTL = DAY * 3;
    private static final long CN_ON_AIR_SEASON_CACHE_TTL = DAY;

    public JsonObject configuration(@NonNull TmdbConfig config) throws Exception {
        ensureReady(config);
        HttpUrl url = apiBuilder(config.getApiBase() + "/configuration", config).build();
        try (Response response = execute(url.toString(), config)) {
            if (response.body() == null) throw new IllegalStateException("TMDB configuration returned empty");
            if (!response.isSuccessful()) throw new IllegalStateException("TMDB configuration failed: HTTP " + response.code());
            return App.gson().fromJson(response.body().string(), JsonObject.class);
        }
    }

    public JsonObject searchRaw(@NonNull String keyword, @NonNull TmdbConfig config) throws Exception {
        ensureReady(config);
        try (Response response = execute(searchUrl(keyword, config), config)) {
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
        String append = "tv".equalsIgnoreCase(item.getMediaType())
                ? "images,credits,aggregate_credits,recommendations,similar,translations,external_ids,content_ratings"
                : "images,credits,recommendations,similar,translations,external_ids,release_dates";
        HttpUrl url = apiBuilder(config.getApiBase() + "/" + item.getMediaType() + "/" + item.getTmdbId(), config)
                .addQueryParameter("language", config.getLanguage())
                .addQueryParameter("append_to_response", append)
                .addQueryParameter("include_image_language", config.getLanguage() + ",null")
                .build();
        if (System.currentTimeMillis() >= 0) return requestJson(url.toString(), config, "detail", DETAIL_CACHE_TTL, "TMDB 详情返回为空", "TMDB 详情失败: HTTP ");
        try (Response response = execute(url.toString(), config)) {
            if (response.body() == null) throw new IllegalStateException("TMDB 详情返回为空");
            if (!response.isSuccessful()) throw new IllegalStateException("TMDB 详情失败: HTTP " + response.code());
            return App.gson().fromJson(response.body().string(), JsonObject.class);
        }
    }

    public JsonObject season(@NonNull TmdbItem item, int seasonNumber, @NonNull TmdbConfig config) throws Exception {
        ensureReady(config);
        HttpUrl url = apiBuilder(config.getApiBase() + "/tv/" + item.getTmdbId() + "/season/" + seasonNumber, config)
                .addQueryParameter("language", config.getLanguage())
                .addQueryParameter("append_to_response", "images,credits,aggregate_credits,translations")
                .addQueryParameter("include_image_language", config.getLanguage() + ",null")
                .build();
        if (System.currentTimeMillis() >= 0) return requestJson(url.toString(), config, "season", SEASON_CACHE_TTL, "TMDB 分季返回为空", "TMDB 分季失败: HTTP ");
        try (Response response = execute(url.toString(), config)) {
            if (response.body() == null) throw new IllegalStateException("TMDB 分季返回为空");
            if (!response.isSuccessful()) throw new IllegalStateException("TMDB 分季失败: HTTP " + response.code());
            return App.gson().fromJson(response.body().string(), JsonObject.class);
        }
    }

    public JsonObject season(@NonNull TmdbItem item, int seasonNumber, @NonNull TmdbConfig config, JsonObject detail) throws Exception {
        ensureReady(config);
        HttpUrl url = apiBuilder(config.getApiBase() + "/tv/" + item.getTmdbId() + "/season/" + seasonNumber, config)
                .addQueryParameter("language", config.getLanguage())
                .addQueryParameter("append_to_response", "images,credits,aggregate_credits,translations")
                .addQueryParameter("include_image_language", config.getLanguage() + ",null")
                .build();
        return requestJson(url.toString(), config, "season", seasonCacheTtl(detail), "TMDB 分季返回为空", "TMDB 分季失败: HTTP ");
    }

    public JsonObject episode(@NonNull TmdbItem item, int seasonNumber, int episodeNumber, @NonNull TmdbConfig config, JsonObject detail) throws Exception {
        ensureReady(config);
        HttpUrl url = apiBuilder(config.getApiBase() + "/tv/" + item.getTmdbId() + "/season/" + seasonNumber + "/episode/" + episodeNumber, config)
                .addQueryParameter("language", config.getLanguage())
                .addQueryParameter("append_to_response", "images,credits,translations")
                .addQueryParameter("include_image_language", config.getLanguage() + ",null")
                .build();
        return requestJson(url.toString(), config, "episode", seasonCacheTtl(detail), "TMDB 单集返回为空", "TMDB 单集失败: HTTP ");
    }

    public JsonObject person(int personId, @NonNull TmdbConfig config) throws Exception {
        ensureReady(config);
        HttpUrl url = apiBuilder(config.getApiBase() + "/person/" + personId, config)
                .addQueryParameter("language", config.getLanguage())
                .addQueryParameter("append_to_response", "combined_credits,images,translations,external_ids")
                .build();
        if (System.currentTimeMillis() >= 0) return requestJson(url.toString(), config, "person", PERSON_CACHE_TTL, "TMDB 演员作品返回为空", "TMDB 演员作品失败: HTTP ");
        try (Response response = execute(url.toString(), config)) {
            if (response.body() == null) throw new IllegalStateException("TMDB 演员作品返回为空");
            if (!response.isSuccessful()) throw new IllegalStateException("TMDB 演员作品失败: HTTP " + response.code());
            return App.gson().fromJson(response.body().string(), JsonObject.class);
        }
    }

    public List<TmdbPerson> cast(JsonObject detail, @NonNull TmdbConfig config) {
        JsonArray aggregate = array(detail, "aggregate_credits", "cast");
        if (aggregate.size() > 0) return aggregateCast(aggregate, config);
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

    public List<TmdbPerson> creators(JsonObject detail, @NonNull TmdbConfig config) {
        Map<Integer, CrewEntry> entries = new LinkedHashMap<>();
        addCreditCrew(entries, array(detail, "credits", "crew"), config);
        addAggregateCrew(entries, array(detail, "aggregate_credits", "crew"), config);
        List<CrewEntry> creators = new ArrayList<>(entries.values());
        creators.sort(Comparator.comparingInt(this::creatorEntryOrder));
        List<TmdbPerson> items = new ArrayList<>();
        for (CrewEntry entry : creators) {
            if (entry.jobs.isEmpty()) continue;
            items.add(new TmdbPerson(entry.id, entry.name, TextUtils.join(" / ", entry.jobs), entry.profile, entry.department, ""));
            if (items.size() >= 12) break;
        }
        return items;
    }

    private void addAggregateCrew(Map<Integer, CrewEntry> entries, JsonArray results, TmdbConfig config) {
        for (JsonElement element : results) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!object.has("id") || object.get("id").isJsonNull()) continue;
            CrewEntry entry = crewEntry(entries, object, config);
            for (JsonElement jobElement : array(object, "jobs")) {
                if (!jobElement.isJsonObject()) continue;
                addCreatorJob(entry, string(jobElement.getAsJsonObject(), "job"), string(object, "department", "known_for_department"));
            }
        }
    }

    private void addCreditCrew(Map<Integer, CrewEntry> entries, JsonArray results, TmdbConfig config) {
        for (JsonElement element : results) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!object.has("id") || object.get("id").isJsonNull()) continue;
            CrewEntry entry = crewEntry(entries, object, config);
            addCreatorJob(entry, string(object, "job"), string(object, "department", "known_for_department"));
        }
    }

    private CrewEntry crewEntry(Map<Integer, CrewEntry> entries, JsonObject object, TmdbConfig config) {
        int id = object.get("id").getAsInt();
        CrewEntry entry = entries.get(id);
        if (entry != null) return entry;
        entry = new CrewEntry(id, string(object, "name"), image(config.getImageBase(), string(object, "profile_path")), string(object, "department", "known_for_department"));
        entries.put(id, entry);
        return entry;
    }

    private void addCreatorJob(CrewEntry entry, String job, String department) {
        String normalized = creatorJob(job, department);
        if (TextUtils.isEmpty(normalized) || entry.jobs.contains(normalized)) return;
        int index = 0;
        while (index < entry.jobs.size() && creatorJobOrder(entry.jobs.get(index)) <= creatorJobOrder(normalized)) index++;
        entry.jobs.add(index, normalized);
    }

    private String creatorJob(String job, String department) {
        if (TextUtils.isEmpty(job) && TextUtils.isEmpty(department)) return "";
        String value = TextUtils.isEmpty(job) ? department : job;
        String lower = value.toLowerCase(Locale.ROOT);
        String group = (department + " " + job).toLowerCase(Locale.ROOT);
        if (lower.contains("director") || group.contains("directing")) return "导演";
        if (lower.contains("writer") || lower.contains("screenplay") || lower.contains("story") || lower.contains("teleplay") || group.contains("writing")) return "编剧";
        if (lower.contains("producer") || group.contains("production")) return "制片";
        return "";
    }

    private int creatorJobOrder(String job) {
        if ("导演".equals(job)) return 0;
        if ("编剧".equals(job)) return 1;
        if ("制片".equals(job)) return 2;
        return 3;
    }

    private int creatorEntryOrder(CrewEntry entry) {
        return entry.jobs.isEmpty() ? 3 : creatorJobOrder(entry.jobs.get(0));
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
                    image(config.getBackdropBase(), string(object, "still_path")),
                    object.has("vote_average") && !object.get("vote_average").isJsonNull() ? object.get("vote_average").getAsDouble() : 0,
                    object.has("runtime") && !object.get("runtime").isJsonNull() ? object.get("runtime").getAsInt() : 0
            ));
        }
        return items;
    }

    public List<TmdbPerson> seasonCast(JsonObject season, @NonNull TmdbConfig config) {
        return cast(season, config);
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

    public List<String> seasonPhotos(JsonObject season, @NonNull TmdbConfig config) {
        return photos(season, config);
    }

    public List<String> episodePhotos(JsonObject episode, @NonNull TmdbConfig config) {
        List<String> items = new ArrayList<>();
        for (JsonElement element : array(episode, "images", "stills")) {
            if (!element.isJsonObject()) continue;
            String url = image(config.getBackdropBase(), string(element.getAsJsonObject(), "file_path"));
            if (TextUtils.isEmpty(url) || items.contains(url)) continue;
            items.add(url);
            if (items.size() >= 24) break;
        }
        String fallback = image(config.getBackdropBase(), string(episode, "still_path"));
        if (!TextUtils.isEmpty(fallback) && !items.contains(fallback)) items.add(fallback);
        return items;
    }

    public List<TmdbPerson> episodeGuests(JsonObject episode, @NonNull TmdbConfig config) {
        List<TmdbPerson> items = new ArrayList<>();
        for (JsonElement element : array(episode, "guest_stars")) {
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
                personBiography(detail, config)
        );
    }

    public List<String> personPhotos(JsonObject person, @NonNull TmdbConfig config) {
        List<String> items = new ArrayList<>();
        for (JsonElement element : array(person, "images", "profiles")) {
            if (!element.isJsonObject()) continue;
            String url = image(config.getImageBase(), string(element.getAsJsonObject(), "file_path"));
            if (TextUtils.isEmpty(url) || items.contains(url)) continue;
            items.add(url);
            if (items.size() >= 24) break;
        }
        return items;
    }

    public List<TmdbItem> recommendations(JsonObject detail, @NonNull TmdbConfig config) {
        return items(array(detail, "recommendations", "results"), config, inferMediaType(detail));
    }

    public List<TmdbItem> similar(JsonObject detail, @NonNull TmdbConfig config) {
        return items(array(detail, "similar", "results"), config, inferMediaType(detail));
    }

    public String translatedOverview(JsonObject detail, @NonNull TmdbConfig config) {
        String current = string(detail, "overview");
        if (!TextUtils.isEmpty(current)) return current;
        JsonArray translations = array(detail, "translations", "translations");
        String preferred = overviewForLanguage(translations, config.getLanguage());
        if (!TextUtils.isEmpty(preferred)) return preferred;
        preferred = overviewForLanguage(translations, languageRoot(config.getLanguage()));
        if (!TextUtils.isEmpty(preferred)) return preferred;
        preferred = overviewForLanguage(translations, "zh-CN");
        if (!TextUtils.isEmpty(preferred)) return preferred;
        preferred = overviewForLanguage(translations, "zh");
        if (!TextUtils.isEmpty(preferred)) return preferred;
        return overviewForLanguage(translations, "en");
    }

    public List<TmdbItem> personWorks(JsonObject person, @NonNull TmdbConfig config) {
        Map<String, TmdbItem> items = new LinkedHashMap<>();
        addWorks(items, array(person, "combined_credits", "cast"), config);
        addWorks(items, array(person, "combined_credits", "crew"), config);
        return items.values().stream().sorted(Comparator.comparing(this::sortDate).reversed()).limit(30).toList();
    }

    public List<TmdbItem> personCastWorks(JsonObject person, @NonNull TmdbConfig config) {
        return items(array(person, "combined_credits", "cast"), config).stream().sorted(Comparator.comparing(this::sortDate).reversed()).limit(60).toList();
    }

    public List<TmdbItem> personCrewWorks(JsonObject person, @NonNull TmdbConfig config) {
        return items(array(person, "combined_credits", "crew"), config).stream().sorted(Comparator.comparing(this::sortDate).reversed()).limit(60).toList();
    }

    public String image(String base, String path) {
        if (TextUtils.isEmpty(path)) return "";
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private String searchUrl(String keyword, TmdbConfig config) {
        return apiBuilder(config.getApiBase() + "/search/multi", config)
                .addQueryParameter("language", config.getLanguage())
                .addQueryParameter("query", keyword)
                .build()
                .toString();
    }

    private void ensureReady(TmdbConfig config) {
        if (!config.sanitize().isReady()) throw new IllegalStateException("请先配置 TMDB API Key");
    }

    private HttpUrl.Builder apiBuilder(String url, TmdbConfig config) {
        HttpUrl.Builder builder = HttpUrl.parse(url).newBuilder();
        if (TextUtils.isEmpty(config.getAccessToken())) builder.addQueryParameter("api_key", config.getApiKey());
        return builder;
    }

    private Response execute(String url, TmdbConfig config) throws Exception {
        Request.Builder builder = new Request.Builder().url(url);
        if (!TextUtils.isEmpty(config.getAccessToken())) builder.header("Authorization", "Bearer " + config.getAccessToken());
        return com.github.catvod.net.OkHttp.client().newCall(builder.build()).execute();
    }

    private JsonObject requestJson(String url, TmdbConfig config, String type, long ttl, String emptyMessage, String failurePrefix) throws Exception {
        File file = cacheFile(type, url);
        JsonObject cached = readCache(file, ttl);
        if (cached != null) return cached;
        try (Response response = execute(url, config)) {
            if (response.body() == null) throw new IllegalStateException(emptyMessage);
            if (!response.isSuccessful()) throw new IllegalStateException(failurePrefix + response.code());
            String body = response.body().string();
            JsonObject object = App.gson().fromJson(body, JsonObject.class);
            writeCache(file, body);
            return object;
        } catch (Throwable e) {
            cached = readCache(file, Long.MAX_VALUE);
            if (cached != null) return cached;
            throw e;
        }
    }

    private File cacheFile(String type, String key) {
        File dir = new File(Path.cache(), "tmdb");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, type + "_" + md5(key) + ".json");
    }

    private JsonObject readCache(File file, long ttl) {
        try {
            if (file == null || !file.exists() || file.length() <= 0) return null;
            if (ttl != Long.MAX_VALUE && System.currentTimeMillis() - file.lastModified() > ttl) return null;
            String body = Path.read(file);
            return TextUtils.isEmpty(body) ? null : App.gson().fromJson(body, JsonObject.class);
        } catch (Throwable e) {
            return null;
        }
    }

    private void writeCache(File file, String body) {
        if (file == null || TextUtils.isEmpty(body)) return;
        Path.write(file, body.getBytes(StandardCharsets.UTF_8));
    }

    private long seasonCacheTtl(JsonObject detail) {
        return isMainlandChina(detail) && isOnAir(detail) ? CN_ON_AIR_SEASON_CACHE_TTL : SEASON_CACHE_TTL;
    }

    private boolean isOnAir(JsonObject detail) {
        if (detail == null) return false;
        if (object(detail, "next_episode_to_air") != null) return true;
        String status = string(detail, "status").toLowerCase(Locale.ROOT);
        return status.contains("returning") || status.contains("production") || status.contains("planned");
    }

    private boolean isMainlandChina(JsonObject detail) {
        for (JsonElement element : array(detail, "origin_country")) {
            if (element.isJsonPrimitive() && "CN".equalsIgnoreCase(element.getAsString())) return true;
        }
        for (JsonElement element : array(detail, "production_countries")) {
            if (!element.isJsonObject()) continue;
            JsonObject country = element.getAsJsonObject();
            String code = string(country, "iso_3166_1");
            String name = string(country, "name");
            if ("CN".equalsIgnoreCase(code)) return true;
            if (name.contains("China") || name.contains("中国") || name.contains("大陆") || name.contains("內地") || name.contains("内地")) return true;
        }
        return false;
    }

    private String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) builder.append(String.format(Locale.US, "%02x", value));
            return builder.toString();
        } catch (Throwable e) {
            return Integer.toHexString(text.hashCode());
        }
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

    private List<TmdbPerson> aggregateCast(JsonArray results, TmdbConfig config) {
        List<TmdbPerson> items = new ArrayList<>();
        for (JsonElement element : results) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!object.has("id") || object.get("id").isJsonNull()) continue;
            items.add(new TmdbPerson(
                    object.get("id").getAsInt(),
                    string(object, "name"),
                    aggregateRole(object),
                    image(config.getImageBase(), string(object, "profile_path")),
                    string(object, "known_for_department"),
                    ""
            ));
            if (items.size() >= 18) break;
        }
        return items;
    }

    private String aggregateRole(JsonObject object) {
        JsonArray roles = array(object, "roles");
        for (JsonElement element : roles) {
            if (element.isJsonObject()) {
                String character = string(element.getAsJsonObject(), "character");
                if (!TextUtils.isEmpty(character)) return character;
            }
        }
        return string(object, "character", "known_for_department");
    }

    private String overviewForLanguage(JsonArray translations, String language) {
        if (TextUtils.isEmpty(language)) return "";
        String target = language.toLowerCase(Locale.ROOT);
        for (JsonElement element : translations) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String iso = string(object, "iso_639_1");
            String name = string(object, "name", "english_name");
            String code = string(object, "iso_3166_1");
            if (!matchesLanguage(target, iso, code, name)) continue;
            String overview = string(object.has("data") && object.get("data").isJsonObject() ? object.getAsJsonObject("data") : null, "overview");
            if (!TextUtils.isEmpty(overview)) return overview;
        }
        return "";
    }

    private boolean matchesLanguage(String target, String iso, String code, String name) {
        String root = languageRoot(target);
        if (target.equalsIgnoreCase(iso) || target.equalsIgnoreCase(iso + "-" + code)) return true;
        if (!TextUtils.isEmpty(root) && root.equalsIgnoreCase(iso)) return true;
        return target.equalsIgnoreCase(name);
    }

    private String languageRoot(String language) {
        if (TextUtils.isEmpty(language)) return "";
        int separator = language.indexOf('-');
        return separator > 0 ? language.substring(0, separator) : language;
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

    private String personBiography(JsonObject detail, TmdbConfig config) {
        String current = string(detail, "biography");
        if (!TextUtils.isEmpty(current)) return current;
        JsonArray translations = array(detail, "translations", "translations");
        String preferred = biographyForLanguage(translations, config.getLanguage());
        if (!TextUtils.isEmpty(preferred)) return preferred;
        preferred = biographyForLanguage(translations, languageRoot(config.getLanguage()));
        if (!TextUtils.isEmpty(preferred)) return preferred;
        preferred = biographyForLanguage(translations, "zh-CN");
        if (!TextUtils.isEmpty(preferred)) return preferred;
        preferred = biographyForLanguage(translations, "zh");
        if (!TextUtils.isEmpty(preferred)) return preferred;
        return biographyForLanguage(translations, "en");
    }

    private String biographyForLanguage(JsonArray translations, String language) {
        if (TextUtils.isEmpty(language)) return "";
        String target = language.toLowerCase(Locale.ROOT);
        for (JsonElement element : translations) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String iso = string(object, "iso_639_1");
            String name = string(object, "name", "english_name");
            String code = string(object, "iso_3166_1");
            if (!matchesLanguage(target, iso, code, name)) continue;
            String biography = string(object.has("data") && object.get("data").isJsonObject() ? object.getAsJsonObject("data") : null, "biography");
            if (!TextUtils.isEmpty(biography)) return biography;
        }
        return "";
    }

    private List<TmdbItem> items(JsonArray array, TmdbConfig config) {
        return items(array, config, "");
    }

    private List<TmdbItem> items(JsonArray array, TmdbConfig config, String defaultMediaType) {
        List<TmdbItem> items = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String mediaType = normalizeMediaType(string(object, "media_type"));
            if (TextUtils.isEmpty(mediaType)) mediaType = normalizeMediaType(defaultMediaType);
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

    private String inferMediaType(JsonObject detail) {
        String mediaType = normalizeMediaType(string(detail, "media_type"));
        if (!TextUtils.isEmpty(mediaType)) return mediaType;
        if (detail == null) return "";
        if (detail.has("first_air_date") || detail.has("number_of_seasons") || detail.has("episode_run_time") || detail.has("last_air_date")) return "tv";
        if (detail.has("release_date") || detail.has("runtime")) return "movie";
        return "";
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

    private static class CrewEntry {

        private final int id;
        private final String name;
        private final String profile;
        private final String department;
        private final List<String> jobs = new ArrayList<>();

        private CrewEntry(int id, String name, String profile, String department) {
            this.id = id;
            this.name = name;
            this.profile = profile;
            this.department = department;
        }
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

    private JsonObject object(JsonObject object, String... keys) {
        JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return null;
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return null;
            current = currentObject.get(key);
        }
        return current != null && current.isJsonObject() ? current.getAsJsonObject() : null;
    }

    private String normalizeMediaType(String mediaType) {
        if ("movie".equals(mediaType) || "tv".equals(mediaType)) return mediaType;
        return "";
    }
}
