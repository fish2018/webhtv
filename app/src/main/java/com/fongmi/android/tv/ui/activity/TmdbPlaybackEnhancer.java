package com.fongmi.android.tv.ui.activity;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbMatchCache;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class TmdbPlaybackEnhancer {

    public interface Host {
        String getKey();

        String getId();

        String getName();

        void runOnUiThread(Runnable action);

        void applyTmdbVod(Vod vod);

        default void applyTmdbArtwork(String title, String subtitle, String overview, String poster, String backdrop) {
        }
    }

    private final TmdbService tmdbService = new TmdbService();
    private final TmdbConfig tmdbConfig;
    private final Host host;
    private boolean applied;

    public TmdbPlaybackEnhancer(Host host) {
        this.host = host;
        this.tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
    }

    public void onDetailReady(Vod item) {
        if (applied || item == null || !tmdbConfig.isReady()) return;
        applied = true;
        Task.execute(() -> {
            TmdbUpdate update = loadTmdbUpdate(item);
            if (update != null) host.runOnUiThread(() -> {
                host.applyTmdbVod(update.apply(item));
                host.applyTmdbArtwork(update.title, update.subtitle, update.overview, update.poster, update.backdrop);
            });
        });
    }

    @Nullable
    private TmdbUpdate loadTmdbUpdate(Vod vod) {
        try {
            TmdbItem item = getCachedTmdbMatch();
            if (item == null) {
                List<TmdbItem> items = tmdbService.search(searchTitle(vod), tmdbConfig);
                item = chooseTmdbMatch(items, searchTitle(vod));
                if (item != null) saveTmdbMatch(item);
            }
            if (item == null) return null;
            JsonObject detail = tmdbService.detail(item, tmdbConfig);
            return TmdbUpdate.from(tmdbService, tmdbConfig, item, detail);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private TmdbItem getCachedTmdbMatch() {
        return Setting.getTmdbMatchCache().find(host.getKey(), host.getId());
    }

    private void saveTmdbMatch(TmdbItem item) {
        if (item == null || item.getTmdbId() <= 0) return;
        TmdbMatchCache cache = Setting.getTmdbMatchCache();
        cache.put(host.getKey(), host.getId(), item);
        Setting.putTmdbMatchCache(cache);
    }

    @Nullable
    private TmdbItem chooseTmdbMatch(List<TmdbItem> items, String title) {
        if (items == null || items.isEmpty()) return null;
        String key = normalize(title);
        for (TmdbItem item : items) if (normalize(item.getTitle()).equals(key)) return item;
        for (TmdbItem item : items) if (normalize(item.getTitle()).contains(key) || key.contains(normalize(item.getTitle()))) return item;
        return items.get(0);
    }

    private String searchTitle(Vod vod) {
        if (vod != null && !TextUtils.isEmpty(vod.getName())) return vod.getName();
        return host.getName();
    }

    private String normalize(String text) {
        return Objects.toString(text, "").replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }

    private static class TmdbUpdate {

        private final String title;
        private final String overview;
        private final String poster;
        private final String backdrop;
        private final String subtitle;
        private final String director;
        private final String year;
        private final String area;
        private final String type;

        private TmdbUpdate(String title, String overview, String poster, String backdrop, String subtitle, String director, String year, String area, String type) {
            this.title = title;
            this.overview = overview;
            this.poster = poster;
            this.backdrop = backdrop;
            this.subtitle = subtitle;
            this.director = director;
            this.year = year;
            this.area = area;
            this.type = type;
        }

        static TmdbUpdate from(TmdbService service, TmdbConfig config, TmdbItem item, JsonObject detail) {
            String poster = coalesce(item.getPosterUrl(), service.image(config.getImageBase(), string(detail, "poster_path")));
            String backdrop = coalesce(item.getBackdropUrl(), service.image(config.getBackdropBase(), string(detail, "backdrop_path")));
            String year = dateYear(string(detail, "release_date", "first_air_date"));
            String area = firstCountry(detail);
            String type = firstGenre(detail);
            String subtitle = subtitle(item, detail, year, area, type);
            return new TmdbUpdate(
                    coalesce(string(detail, "title", "name"), item.getTitle()),
                    coalesce(string(detail, "overview"), item.getOverview()),
                    poster,
                    backdrop,
                    subtitle,
                    firstCrew(detail, "Director"),
                    year,
                    area,
                    type);
        }

        Vod apply(Vod vod) {
            Vod update = new Vod();
            update.setId(vod.getId());
            update.setPlayFrom(vod.getPlayFrom());
            update.setPlayUrl(vod.getPlayUrl());
            update.setFlags(vod.getFlags());
            update.setSite(vod.getSite());
            update.setName(vod.getName());
            update.setPic(vod.getPic());
            update.setContent(vod.getContent());
            update.setDirector(vod.getDirector());
            update.setYear(vod.getYear());
            update.setArea(vod.getArea());
            update.setTypeName(vod.getTypeName());
            update.setRemarks(vod.getRemarks());
            if (!TextUtils.isEmpty(title)) update.setName(title);
            if (!TextUtils.isEmpty(overview)) update.setContent(overview);
            if (!TextUtils.isEmpty(coalesce(backdrop, poster))) update.setPic(coalesce(backdrop, poster));
            if (!TextUtils.isEmpty(director) && TextUtils.isEmpty(vod.getDirector())) update.setDirector(director);
            if (!TextUtils.isEmpty(year) && TextUtils.isEmpty(vod.getYear())) update.setYear(year);
            if (!TextUtils.isEmpty(area) && TextUtils.isEmpty(vod.getArea())) update.setArea(area);
            if (!TextUtils.isEmpty(type) && TextUtils.isEmpty(vod.getTypeName())) update.setTypeName(type);
            return update;
        }

        private static String firstCrew(JsonObject detail, String job) {
            JsonArray crew = array(detail, "credits", "crew");
            for (JsonElement element : crew) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                if (job.equalsIgnoreCase(string(object, "job"))) return string(object, "name");
            }
            return "";
        }

        private static String firstGenre(JsonObject detail) {
            JsonArray genres = array(detail, "genres");
            for (JsonElement element : genres) if (element.isJsonObject()) return string(element.getAsJsonObject(), "name");
            return "";
        }

        private static String firstCountry(JsonObject detail) {
            JsonArray countries = array(detail, "production_countries");
            for (JsonElement element : countries) if (element.isJsonObject()) return string(element.getAsJsonObject(), "name", "iso_3166_1");
            return "";
        }

        private static String dateYear(String date) {
            if (TextUtils.isEmpty(date) || date.length() < 4) return "";
            return date.substring(0, 4);
        }

        private static String subtitle(TmdbItem item, JsonObject detail, String year, String area, String type) {
            String media = "tv".equalsIgnoreCase(item.getMediaType()) ? "TV" : "Movie";
            String rating = "";
            if (detail != null && detail.has("vote_average") && !detail.get("vote_average").isJsonNull()) {
                rating = String.format(Locale.US, "%.1f", detail.get("vote_average").getAsDouble());
            }
            StringBuilder builder = new StringBuilder(media);
            if (!TextUtils.isEmpty(year)) builder.append(" · ").append(year);
            if (!TextUtils.isEmpty(rating)) builder.append(" · Rating ").append(rating);
            if (!TextUtils.isEmpty(area)) builder.append(" · ").append(area);
            if (!TextUtils.isEmpty(type)) builder.append(" · ").append(type);
            return builder.toString();
        }

        private static JsonArray array(JsonObject object, String... keys) {
            JsonElement current = object;
            for (String key : keys) {
                if (current == null || !current.isJsonObject()) return new JsonArray();
                JsonObject currentObject = current.getAsJsonObject();
                if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return new JsonArray();
                current = currentObject.get(key);
            }
            return current != null && current.isJsonArray() ? current.getAsJsonArray() : new JsonArray();
        }

        private static String string(JsonObject object, String... keys) {
            for (String key : keys) {
                if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
                    String value = object.get(key).getAsString();
                    if (!TextUtils.isEmpty(value)) return value.trim();
                }
            }
            return "";
        }

        private static String coalesce(String... values) {
            for (String value : values) if (!TextUtils.isEmpty(value)) return value;
            return "";
        }
    }
}
