package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.utils.TmdbProxy;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.TmdbSourceDetail;
import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.fongmi.android.tv.bean.TmdbVideo;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.TmdbLanguagePolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts a validated C16 payload into the existing UI bundle without network access. */
public final class TmdbSourceAdapter {

    private static final int MAX_CAST = 18;
    private static final int MAX_CREATORS = 12;
    private static final int MAX_PHOTOS = 24;
    private static final int MAX_RELATED = 40;

    @Nullable
    public static TmdbBundle toBundle(@Nullable TmdbSourcePayload payload, @Nullable Vod vod, @Nullable TmdbConfig config) {
        TmdbSourcePayload valid = TmdbSourcePayloadParser.parse(payload);
        if (valid == null) return null;
        TmdbConfig effectiveConfig = config == null ? new TmdbConfig().sanitize() : config.sanitize();
        JsonObject detail = new TmdbSourceDetail(valid.getDetailJson()).object();
        if (!detail.has("media_type") && !valid.getMediaType().isEmpty()) detail.addProperty("media_type", valid.getMediaType());
        TmdbItem item = item(valid.getTmdbId(), valid.getMediaType(), detail, vod, effectiveConfig, valid.hasCapability(TmdbSourceCapabilityPlanner.CORE));
        List<TmdbPerson> cast = cast(detail, effectiveConfig);
        List<TmdbPerson> creators = creators(detail, effectiveConfig);
        List<String> photos = photos(detail, effectiveConfig);
        List<TmdbItem> related = related(valid.getMediaType(), detail, effectiveConfig);
        SeasonData seasons = seasons(valid.getTmdbId(), valid.getSeasonNumber(), detail, effectiveConfig);
        return new TmdbBundle(item, detail, cast, creators, photos, related, seasons.numbers, seasons.counts, seasons.episodes, seasons.cast, seasons.photos);
    }

    public static TmdbBundle fromNetwork(@Nullable TmdbItem sourceItem, @Nullable JsonObject sourceDetail, @Nullable TmdbConfig config) {
        if (sourceItem == null) return null;
        TmdbConfig effectiveConfig = config == null ? new TmdbConfig().sanitize() : config.sanitize();
        JsonObject detail = sourceDetail == null ? new JsonObject() : sourceDetail.deepCopy();
        String mediaType = normalizeMediaType(sourceItem.getMediaType());
        if (mediaType.isEmpty()) mediaType = sourceItem.getMediaType();
        TmdbItem item = item(sourceItem.getTmdbId(), mediaType, detail, null, effectiveConfig, true);
        item = fallbackItem(item, sourceItem);
        List<TmdbPerson> cast = cast(detail, effectiveConfig);
        List<TmdbPerson> creators = creators(detail, effectiveConfig);
        List<String> photos = photos(detail, effectiveConfig);
        List<TmdbItem> related = related(mediaType, detail, effectiveConfig);
        SeasonData seasons = seasons(sourceItem.getTmdbId(), 0, detail, effectiveConfig);
        return new TmdbBundle(item, detail, cast, creators, photos, related, seasons.numbers, seasons.counts, seasons.episodes, seasons.cast, seasons.photos);
    }

    public static List<TmdbVideo> videos(@Nullable JsonObject detail, @Nullable String mediaType, int seasonNumber, int episodeNumber, @Nullable String language) {
        List<TmdbVideo> result = new ArrayList<>();
        if (detail == null) return result;
        String normalized = normalizeMediaType(mediaType);
        JsonObject season = findSeason(detail, seasonNumber);
        JsonObject episode = findEpisode(season, episodeNumber);
        if ("movie".equals(normalized) || seasonNumber < 0) {
            addVideos(result, object(detail, "videos"), TmdbVideo.Scope.TITLE, -1, -1);
        } else {
            addVideos(result, object(season, "videos"), TmdbVideo.Scope.SEASON, seasonNumber, -1);
            addVideos(result, object(episode, "videos"), TmdbVideo.Scope.EPISODE, seasonNumber, episodeNumber);
            addVideos(result, object(detail, "videos"), TmdbVideo.Scope.TITLE, -1, -1);
        }
        return TmdbVideo.mergeAndRank(result, language == null ? "" : language, 12);
    }

    private static TmdbItem item(int tmdbId, String mediaType, JsonObject detail, @Nullable Vod vod, TmdbConfig config, boolean coreComplete) {
        boolean tv = "tv".equals(mediaType);
        String titleKey = tv ? "name" : "title";
        String title = TmdbLanguagePolicy.bestDisplayValue(firstString(detail, tv ? new String[]{"name", "title"} : new String[]{"title", "name"}),
                array(object(detail, "translations"), "translations"), titleKey, TmdbLanguagePolicy.requestLanguage(config));
        if (title.isEmpty() && vod != null && coreComplete) title = vod.getName();
        String date = firstString(detail, tv ? new String[]{"first_air_date", "release_date"} : new String[]{"release_date", "first_air_date"});
        double vote = number(detail, "vote_average", 0d);
        String subtitle = subtitle(date, vote);
        String poster = imageUrl(config.getImageBase(), string(detail, "poster_path"));
        String backdrop = imageUrl(config.getBackdropBase(), string(detail, "backdrop_path"));
        return new TmdbItem(
                tmdbId,
                mediaType,
                title,
                subtitle,
                TmdbLanguagePolicy.bestDisplayValue(string(detail, "overview"), array(object(detail, "translations"), "translations"), "overview", TmdbLanguagePolicy.requestLanguage(config)),
                poster,
                backdrop,
                "",
                vote,
                string(detail, "original_language"),
                firstString(detail, new String[]{"origin_country"}),
                genreIds(detail),
                "",
                vote,
                0d
        );
    }

    private static TmdbItem fallbackItem(TmdbItem primary, TmdbItem fallback) {
        return new TmdbItem(
                primary.getTmdbId(),
                primary.getMediaType(),
                firstNonEmpty(primary.getTitle(), fallback.getTitle()),
                firstNonEmpty(primary.getSubtitle(), fallback.getSubtitle()),
                firstNonEmpty(primary.getOverview(), fallback.getOverview()),
                firstNonEmpty(primary.getPosterUrl(), fallback.getPosterUrl()),
                firstNonEmpty(primary.getBackdropUrl(), fallback.getBackdropUrl()),
                firstNonEmpty(primary.getCredit(), fallback.getCredit()),
                primary.getRating() > 0 ? primary.getRating() : fallback.getRating(),
                firstNonEmpty(primary.getOriginalLanguage(), fallback.getOriginalLanguage()),
                firstNonEmpty(primary.getOriginCountry(), fallback.getOriginCountry()),
                primary.getGenreIds().isEmpty() ? fallback.getGenreIds() : primary.getGenreIds(),
                firstNonEmpty(primary.getDepartment(), fallback.getDepartment()),
                primary.getTmdbRating() > 0 ? primary.getTmdbRating() : fallback.getTmdbRating(),
                primary.getDoubanRating() > 0 ? primary.getDoubanRating() : fallback.getDoubanRating(),
                firstNonEmpty(primary.getRecommendationReason(), fallback.getRecommendationReason())
        );
    }

    private static List<TmdbPerson> cast(JsonObject detail, TmdbConfig config) {
        JsonArray aggregate = array(object(detail, "aggregate_credits"), "cast");
        if (!aggregate.isEmpty()) return castItems(aggregate, config, true);
        return castItems(array(object(detail, "credits"), "cast"), config, false);
    }

    private static List<TmdbPerson> castItems(JsonArray array, TmdbConfig config, boolean aggregate) {
        List<TmdbPerson> result = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            int id = integer(object, "id", 0);
            if (id <= 0 || !seen.add(id)) continue;
            String role = aggregate ? firstRole(object) : firstString(object, new String[]{"character", "known_for_department"});
            result.add(new TmdbPerson(
                    id,
                    string(object, "name"),
                    role,
                    imageUrl(config.getImageBase(), string(object, "profile_path")),
                    string(object, "known_for_department"),
                    ""
            ));
            if (result.size() >= MAX_CAST) break;
        }
        return result;
    }

    private static List<TmdbPerson> creators(JsonObject detail, TmdbConfig config) {
        Map<Integer, Crew> crews = new LinkedHashMap<>();
        addCrew(crews, array(object(detail, "credits"), "crew"), config, false);
        addCrew(crews, array(object(detail, "aggregate_credits"), "crew"), config, true);
        List<Crew> values = new ArrayList<>(crews.values());
        values.sort(Comparator.comparingInt(crew -> jobOrder(crew.jobs.isEmpty() ? "" : crew.jobs.get(0))));
        List<TmdbPerson> result = new ArrayList<>();
        for (Crew crew : values) {
            if (crew.jobs.isEmpty()) continue;
            result.add(new TmdbPerson(crew.id, crew.name, String.join(" / ", crew.jobs), crew.profileUrl, crew.department, ""));
            if (result.size() >= MAX_CREATORS) break;
        }
        return result;
    }

    private static void addCrew(Map<Integer, Crew> crews, JsonArray array, TmdbConfig config, boolean aggregate) {
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            int id = integer(object, "id", 0);
            if (id <= 0) continue;
            Crew crew = crews.get(id);
            if (crew == null) {
                crew = new Crew(id, string(object, "name"), imageUrl(config.getImageBase(), string(object, "profile_path")), string(object, "known_for_department"));
                crews.put(id, crew);
            }
            if (aggregate) {
                boolean added = false;
                for (JsonElement job : array(object, "jobs")) {
                    if (job.isJsonObject()) {
                        addJob(crew.jobs, string(job.getAsJsonObject(), "job"));
                        added = true;
                    }
                }
                if (!added) addJob(crew.jobs, string(object, "job"));
            } else {
                addJob(crew.jobs, string(object, "job"));
            }
        }
    }

    private static void addJob(List<String> jobs, String job) {
        if (!job.isEmpty() && !jobs.contains(job)) jobs.add(job);
    }

    private static int jobOrder(String job) {
        if ("Director".equalsIgnoreCase(job) || "导演".equals(job)) return 0;
        if ("Writer".equalsIgnoreCase(job) || "Screenplay".equalsIgnoreCase(job) || "编剧".equals(job)) return 1;
        if ("Producer".equalsIgnoreCase(job) || "制片".equals(job)) return 2;
        return 3;
    }

    private static List<String> photos(JsonObject detail, TmdbConfig config) {
        JsonObject images = object(detail, "images");
        List<String> result = imageArray(array(images, "backdrops"), config.getBackdropBase());
        for (String url : imageArray(array(images, "posters"), config.getImageBase())) if (!result.contains(url)) result.add(url);
        addImage(result, config.getBackdropBase(), string(detail, "backdrop_path"));
        addImage(result, config.getImageBase(), string(detail, "poster_path"));
        return limit(result, MAX_PHOTOS);
    }

    private static List<TmdbItem> related(String mediaType, JsonObject detail, TmdbConfig config) {
        Map<String, TmdbItem> result = new LinkedHashMap<>();
        addRelated(result, array(object(detail, "recommendations"), "results"), mediaType, config);
        addRelated(result, array(object(detail, "similar"), "results"), mediaType, config);
        return result.values().stream().limit(MAX_RELATED).toList();
    }

    private static void addRelated(Map<String, TmdbItem> result, JsonArray array, String defaultMediaType, TmdbConfig config) {
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            int id = integer(object, "id", 0);
            String mediaType = normalizeMediaType(string(object, "media_type"));
            if (mediaType.isEmpty()) mediaType = defaultMediaType;
            if (id <= 0 || mediaType.isEmpty()) continue;
            String title = firstString(object, mediaType.equals("movie") ? new String[]{"title", "name"} : new String[]{"name", "title"});
            String date = firstString(object, mediaType.equals("movie") ? new String[]{"release_date"} : new String[]{"first_air_date"});
            double vote = number(object, "vote_average", 0d);
            result.putIfAbsent(mediaType + ':' + id, new TmdbItem(
                    id,
                    mediaType,
                    title,
                    subtitle(date, vote),
                    string(object, "overview"),
                    imageUrl(config.getImageBase(), string(object, "poster_path")),
                    imageUrl(config.getBackdropBase(), string(object, "backdrop_path")),
                    "",
                    vote,
                    string(object, "original_language"),
                    firstString(object, new String[]{"origin_country"}),
                    integers(array(object, "genre_ids")),
                    string(object, "department"),
                    vote,
                    0d
            ));
        }
    }

    private static SeasonData seasons(int tmdbId, int selectedSeasonNumber, JsonObject detail, TmdbConfig config) {
        LinkedHashSet<Integer> numbers = new LinkedHashSet<>();
        Map<Integer, Integer> counts = new HashMap<>();
        Map<Integer, List<TmdbEpisode>> episodes = new HashMap<>();
        Map<Integer, List<TmdbPerson>> casts = new HashMap<>();
        Map<Integer, List<String>> photos = new HashMap<>();
        for (JsonElement element : array(detail, "seasons")) {
            if (!element.isJsonObject()) continue;
            JsonObject season = element.getAsJsonObject();
            int number = integer(season, "season_number", -1);
            if (number < 0) continue;
            numbers.add(number);
            List<TmdbEpisode> seasonEpisodes = episodes(season, tmdbId, number, config);
            if (!seasonEpisodes.isEmpty() || season.has("episodes")) episodes.put(number, seasonEpisodes);
            counts.put(number, integer(season, "episode_count", seasonEpisodes.size()));
            List<TmdbPerson> seasonCast = cast(season, config);
            if (!seasonCast.isEmpty() || hasCreditObject(season)) casts.put(number, seasonCast);
            List<String> seasonPhotos = photos(season, config);
            if (!seasonPhotos.isEmpty() || season.has("images")) photos.put(number, seasonPhotos);
        }
        if (selectedSeasonNumber >= 0 && detail.has("episodes")) {
            int seasonNumber = selectedSeasonNumber;
            List<TmdbEpisode> selected = episodes(detail, tmdbId, seasonNumber, config);
            if (selectedSeasonNumber > 0 || !selected.isEmpty()) {
                numbers.add(seasonNumber);
                episodes.put(seasonNumber, selected);
                counts.putIfAbsent(seasonNumber, selected.size());
            }
        }
        List<Integer> sorted = numbers.stream().sorted().toList();
        return new SeasonData(sorted, counts, episodes, casts, photos);
    }

    private static List<TmdbEpisode> episodes(JsonObject season, int tmdbId, int seasonNumber, TmdbConfig config) {
        List<TmdbEpisode> result = new ArrayList<>();
        for (JsonElement element : array(season, "episodes")) {
            if (!element.isJsonObject()) continue;
            JsonObject episode = element.getAsJsonObject();
            int number = integer(episode, "episode_number", result.size() + 1);
            String target = TmdbLanguagePolicy.requestLanguage(config);
            result.add(new TmdbEpisode(
                    number,
                    TmdbLanguagePolicy.bestDisplayValue(string(episode, "name"), array(object(episode, "translations"), "translations"), "name", target),
                    string(episode, "air_date"),
                    TmdbLanguagePolicy.bestDisplayValue(string(episode, "overview"), array(object(episode, "translations"), "translations"), "overview", target),
                    imageUrl(config.getBackdropBase(), string(episode, "still_path")),
                    number(episode, "vote_average", 0d),
                    integer(episode, "runtime", 0),
                    tmdbId,
                    seasonNumber
            ));
        }
        return result;
    }

    private static boolean hasCreditObject(JsonObject object) {
        return object.has("credits") || object.has("aggregate_credits");
    }

    private static List<Integer> genreIds(JsonObject detail) {
        List<Integer> result = new ArrayList<>();
        for (JsonElement element : array(detail, "genres")) {
            if (!element.isJsonObject()) continue;
            int id = integer(element.getAsJsonObject(), "id", 0);
            if (id > 0) result.add(id);
        }
        return result;
    }

    private static List<Integer> integers(JsonArray array) {
        List<Integer> result = new ArrayList<>();
        for (JsonElement element : array) {
            int value = integerElement(element, 0);
            if (value > 0) result.add(value);
        }
        return result;
    }

    private static List<String> imageArray(JsonArray array, String base) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) addImage(result, base, string(element.getAsJsonObject(), "file_path"));
        }
        return result;
    }

    private static void addImage(List<String> result, String base, String path) {
        String url = imageUrl(base, path);
        if (!url.isEmpty() && !result.contains(url)) result.add(url);
    }

    private static List<String> limit(List<String> values, int max) {
        return values.size() <= max ? values : new ArrayList<>(values.subList(0, max));
    }

    private static void addVideos(List<TmdbVideo> target, JsonObject videos, TmdbVideo.Scope scope, int seasonNumber, int episodeNumber) {
        for (JsonElement element : array(videos, "results")) {
            if (!element.isJsonObject()) continue;
            TmdbVideo video = TmdbVideo.from(element.getAsJsonObject(), scope, seasonNumber, episodeNumber);
            if (video != null) target.add(video);
        }
    }

    private static JsonObject findSeason(JsonObject detail, int seasonNumber) {
        if (seasonNumber < 0) return new JsonObject();
        for (JsonElement element : array(detail, "seasons")) {
            if (!element.isJsonObject()) continue;
            JsonObject season = element.getAsJsonObject();
            if (integer(season, "season_number", -1) == seasonNumber) return season;
        }
        return new JsonObject();
    }

    private static JsonObject findEpisode(JsonObject season, int episodeNumber) {
        if (season == null || episodeNumber <= 0) return new JsonObject();
        for (JsonElement element : array(season, "episodes")) {
            if (!element.isJsonObject()) continue;
            JsonObject episode = element.getAsJsonObject();
            if (integer(episode, "episode_number", -1) == episodeNumber) return episode;
        }
        return new JsonObject();
    }

    private static String imageUrl(String base, String path) {
        String normalized = TmdbSourcePayloadParser.normalizeImageUrl(path);
        if (normalized.isEmpty()) return "";
        if (!normalized.startsWith("/")) return normalized;
        String normalizedBase = base == null ? "" : base.trim();
        return normalizedBase.isEmpty() ? normalized : TmdbProxy.imageUrl(normalizedBase, normalized);
    }

    private static String subtitle(String date, double vote) {
        String rating = vote > 0 ? String.format(Locale.US, "%.1f", vote) : "";
        if (!date.isEmpty() && !rating.isEmpty()) return date + " · " + rating;
        return date.isEmpty() ? rating : date;
    }

    private static String firstNonEmpty(String first, String second) {
        return first != null && !first.isEmpty() ? first : second == null ? "" : second;
    }

    private static String firstRole(JsonObject object) {
        for (JsonElement element : array(object, "roles")) {
            if (!element.isJsonObject()) continue;
            String role = string(element.getAsJsonObject(), "character");
            if (!role.isEmpty()) return role;
        }
        return firstString(object, new String[]{"character", "known_for_department"});
    }

    private static String normalizeMediaType(String value) {
        String mediaType = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "movie".equals(mediaType) || "tv".equals(mediaType) ? mediaType : "";
    }

    private static JsonObject object(@Nullable JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonObject()) return new JsonObject();
        return object.getAsJsonObject(key);
    }

    private static JsonArray array(@Nullable JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) return new JsonArray();
        return object.getAsJsonArray(key);
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()) return "";
        return object.get(key).getAsString();
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber()) return fallback;
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        long value = longValue(object, key, fallback);
        return value > Integer.MAX_VALUE || value < Integer.MIN_VALUE ? fallback : (int) value;
    }

    private static int integerElement(JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber()) return fallback;
        try {
            double value = object.get(key).getAsDouble();
            return Double.isFinite(value) ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String firstString(JsonObject object, String[] keys) {
        for (String key : keys) {
            JsonElement value = object == null ? null : object.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String text = value.getAsString();
                if (text != null && !text.trim().isEmpty()) return text.trim();
            }
            JsonArray array = array(object, key);
            for (JsonElement item : array) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                    String text = item.getAsString();
                    if (text != null && !text.trim().isEmpty()) return text.trim();
                }
            }
        }
        return "";
    }

    private record Crew(int id, String name, String profileUrl, String department, List<String> jobs) {
        private Crew(int id, String name, String profileUrl, String department) {
            this(id, name, profileUrl, department, new ArrayList<>());
        }
    }

    private record SeasonData(List<Integer> numbers, Map<Integer, Integer> counts, Map<Integer, List<TmdbEpisode>> episodes,
                              Map<Integer, List<TmdbPerson>> cast, Map<Integer, List<String>> photos) {
    }

    private TmdbSourceAdapter() {
    }
}
