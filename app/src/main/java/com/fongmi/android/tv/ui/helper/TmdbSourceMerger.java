package com.fongmi.android.tv.ui.helper;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.fongmi.android.tv.utils.TmdbLanguagePolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Fill-only merge for source, local-cache and remote TMDB bundles. */
public final class TmdbSourceMerger {

    public enum Origin {
        SOURCE,
        LOCAL_CACHE,
        REMOTE_TMDB,
        VOD_FALLBACK,
        REMOTE_TMDB_LANGUAGE
    }

    public record MergedBundle(TmdbBundle bundle, Map<String, Origin> origins) {

        public MergedBundle {
            origins = Collections.unmodifiableMap(new LinkedHashMap<>(origins));
        }

        public Origin origin(String field) {
            return origins.getOrDefault(field, Origin.SOURCE);
        }
    }

    public static TmdbBundle fillOnly(@Nullable TmdbBundle source, @Nullable TmdbSourcePayload payload, @Nullable TmdbBundle network) {
        return fillOnly(source, payload, network, "");
    }

    public static TmdbBundle fillOnly(@Nullable TmdbBundle source, @Nullable TmdbSourcePayload payload,
                                      @Nullable TmdbBundle network, @Nullable String targetLanguage) {
        return merge(source, payload, null, network, targetLanguage).bundle();
    }

    public static MergedBundle merge(@Nullable TmdbBundle source, @Nullable TmdbSourcePayload payload,
                                     @Nullable TmdbBundle cache, @Nullable TmdbBundle network) {
        return merge(source, payload, cache, network, "");
    }

    public static MergedBundle merge(@Nullable TmdbBundle source, @Nullable TmdbSourcePayload payload,
                                     @Nullable TmdbBundle cache, @Nullable TmdbBundle network,
                                     @Nullable String targetLanguage) {
        TmdbBundle base = source == null ? empty() : source;
        Map<String, Origin> origins = new LinkedHashMap<>();
        base = mergePair(base, payload, cache, Origin.LOCAL_CACHE, origins, targetLanguage);
        base = mergePair(base, payload, network, Origin.REMOTE_TMDB, origins, targetLanguage);
        return new MergedBundle(base, origins);
    }

    private static TmdbBundle mergePair(TmdbBundle base, @Nullable TmdbSourcePayload payload, @Nullable TmdbBundle lower,
                                        Origin lowerOrigin, Map<String, Origin> origins, @Nullable String targetLanguage) {
        if (lower == null || lower.item() == null) return base;
        if (base.item() != null && !identityMatches(base.item(), lower.item())) return base;

        TmdbItem item = mergeItem(base.item(), lower.item(), payload, origins, lowerOrigin, targetLanguage);
        JsonObject detail = mergeObject(base.detail(), lower.detail(), payload, "detail", origins, lowerOrigin, targetLanguage);
        List<TmdbPerson> cast = mergeList(base.cast(), lower.cast(), "cast", cachedComplete(payload, "credits"), origins, lowerOrigin);
        List<TmdbPerson> creators = mergeList(base.creators(), lower.creators(), "creators", cachedComplete(payload, "credits"), origins, lowerOrigin);
        List<String> photos = mergeList(base.photos(), lower.photos(), "photos", cachedComplete(payload, "images"), origins, lowerOrigin);
        List<TmdbItem> related = mergeList(base.related(), lower.related(), "related", cachedComplete(payload, "recommendations") && cachedComplete(payload, "similar"), origins, lowerOrigin);
        List<Integer> seasons = mergeSeasons(base.seasons(), lower.seasons());
        Map<Integer, Integer> seasonCounts = mergeMap(base.seasonCounts(), lower.seasonCounts(), payload, "seasonCounts", origins, lowerOrigin);
        Map<Integer, List<TmdbEpisode>> seasonEpisodes = mergeSeasonEpisodes(base.seasonEpisodes(), lower.seasonEpisodes(), payload, origins, lowerOrigin);
        Map<Integer, List<TmdbPerson>> seasonCast = mergeMap(base.seasonCast(), lower.seasonCast(), payload, "seasonCast", origins, lowerOrigin);
        Map<Integer, List<String>> seasonPhotos = mergeMap(base.seasonPhotos(), lower.seasonPhotos(), payload, "seasonPhotos", origins, lowerOrigin);
        return new TmdbBundle(item, detail, cast, creators, photos, related, seasons, seasonCounts, seasonEpisodes, seasonCast, seasonPhotos);
    }

    private static TmdbItem mergeItem(TmdbItem source, TmdbItem lower, @Nullable TmdbSourcePayload payload,
                                       Map<String, Origin> origins, Origin lowerOrigin, @Nullable String targetLanguage) {
        if (source == null) return lower;
        String title = chooseDisplay(source.getTitle(), lower.getTitle(), payload, targetLanguage, "item.title", origins, lowerOrigin);
        String subtitle = choose(source.getSubtitle(), lower.getSubtitle(), "item.subtitle", origins, lowerOrigin);
        String overview = chooseDisplay(source.getOverview(), lower.getOverview(), payload, targetLanguage, "item.overview", origins, lowerOrigin);
        String poster = choose(source.getPosterUrl(), lower.getPosterUrl(), "item.poster", origins, lowerOrigin);
        String backdrop = choose(source.getBackdropUrl(), lower.getBackdropUrl(), "item.backdrop", origins, lowerOrigin);
        String credit = choose(source.getCredit(), lower.getCredit(), "item.credit", origins, lowerOrigin);
        String language = choose(source.getOriginalLanguage(), lower.getOriginalLanguage(), "item.language", origins, lowerOrigin);
        String country = choose(source.getOriginCountry(), lower.getOriginCountry(), "item.country", origins, lowerOrigin);
        String department = choose(source.getDepartment(), lower.getDepartment(), "item.department", origins, lowerOrigin);
        List<Integer> genres = source.getGenreIds();
        if (genres.isEmpty() && !lower.getGenreIds().isEmpty()) {
            genres = lower.getGenreIds();
            mark(origins, "item.genres", lowerOrigin);
        }
        boolean coreComplete = cachedComplete(payload, TmdbSourceCapabilityPlanner.CORE);
        double rating = source.getRating() > 0 || coreComplete ? source.getRating() : lower.getRating();
        double tmdbRating = source.getTmdbRating() > 0 || coreComplete ? source.getTmdbRating() : lower.getTmdbRating();
        double doubanRating = source.getDoubanRating() > 0 ? source.getDoubanRating() : lower.getDoubanRating();
        return new TmdbItem(source.getTmdbId(), source.getMediaType(), title, subtitle, overview, poster, backdrop, credit, rating, language, country, genres, department, tmdbRating, doubanRating, source.getRecommendationReason());
    }

    private static JsonObject mergeObject(@Nullable JsonObject source, @Nullable JsonObject lower, @Nullable TmdbSourcePayload payload, String path,
                                          Map<String, Origin> origins, Origin lowerOrigin, @Nullable String targetLanguage) {
        JsonObject result = source == null ? new JsonObject() : source.deepCopy();
        if (lower == null) return result;
        for (Map.Entry<String, JsonElement> entry : lower.entrySet()) {
            String key = entry.getKey();
            String childPath = path.isEmpty() ? key : path + '.' + key;
            JsonElement current = result.get(key);
            JsonElement replacement = entry.getValue();
            boolean languageOverride = isCoreDisplayField(childPath) && !isEmptyValue(replacement, payload, childPath)
                    && shouldOverrideCoreDisplay(stringValue(current), stringValue(replacement), payload, targetLanguage);
            if (current == null || languageOverride || isEmptyValue(current, payload, childPath)) {
                result.add(key, replacement.deepCopy());
                mark(origins, childPath, languageOverride ? Origin.REMOTE_TMDB_LANGUAGE : lowerOrigin);
            } else if (current.isJsonObject() && replacement.isJsonObject()) {
                result.add(key, mergeObject(current.getAsJsonObject(), replacement.getAsJsonObject(), payload, childPath, origins, lowerOrigin, targetLanguage));
            } else if (current.isJsonArray() && replacement.isJsonArray()) {
                JsonArray merged = mergeArray(current.getAsJsonArray(), replacement.getAsJsonArray(), payload, childPath, origins, lowerOrigin);
                result.add(key, merged);
            }
        }
        return result;
    }

    private static JsonArray mergeArray(JsonArray source, JsonArray lower, @Nullable TmdbSourcePayload payload, String path,
                                        Map<String, Origin> origins, Origin lowerOrigin) {
        JsonArray result = source.deepCopy();
        if (!source.isEmpty() || cachedComplete(payload, capabilityForField(path))) return result;
        for (JsonElement element : lower) result.add(element.deepCopy());
        if (!lower.isEmpty()) mark(origins, path, lowerOrigin);
        return result;
    }

    private static boolean isEmptyValue(JsonElement value, @Nullable TmdbSourcePayload payload, String key) {
        if (value == null || value.isJsonNull()) return true;
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isString()) return value.getAsString().trim().isEmpty();
            if (value.getAsJsonPrimitive().isNumber()) {
                try {
                    return value.getAsDouble() == 0d && !cachedComplete(payload, capabilityForField(key));
                } catch (RuntimeException ignored) {
                    return true;
                }
            }
        }
        if (value.isJsonObject()) return value.getAsJsonObject().isEmpty() && !cachedComplete(payload, capabilityForField(key));
        if (value.isJsonArray()) return value.getAsJsonArray().isEmpty() && !cachedComplete(payload, capabilityForField(key));
        return false;
    }

    private static <T> List<T> mergeList(@Nullable List<T> source, @Nullable List<T> lower, String field, boolean sourceComplete,
                                         Map<String, Origin> origins, Origin lowerOrigin) {
        List<T> base = source == null ? List.of() : source;
        if (!base.isEmpty() || sourceComplete) return base;
        List<T> replacement = lower == null ? List.of() : lower;
        if (!replacement.isEmpty()) mark(origins, field, lowerOrigin);
        return replacement;
    }

    private static List<Integer> mergeSeasons(@Nullable List<Integer> source, @Nullable List<Integer> lower) {
        Set<Integer> merged = new TreeSet<>();
        if (source != null) merged.addAll(source);
        if (lower != null) merged.addAll(lower);
        return new ArrayList<>(merged);
    }

    private static <T> Map<Integer, T> mergeMap(@Nullable Map<Integer, T> source, @Nullable Map<Integer, T> lower,
                                                @Nullable TmdbSourcePayload payload, String field, Map<String, Origin> origins, Origin lowerOrigin) {
        Map<Integer, T> result = source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
        if (lower == null) return result;
        for (Map.Entry<Integer, T> entry : lower.entrySet()) {
            T current = result.get(entry.getKey());
            if (current != null && !isEmpty(current)) continue;
            result.put(entry.getKey(), entry.getValue());
            mark(origins, field + '.' + entry.getKey(), lowerOrigin);
        }
        return result;
    }

    private static Map<Integer, List<TmdbEpisode>> mergeSeasonEpisodes(@Nullable Map<Integer, List<TmdbEpisode>> source,
                                                                         @Nullable Map<Integer, List<TmdbEpisode>> lower,
                                                                         @Nullable TmdbSourcePayload payload,
                                                                         Map<String, Origin> origins, Origin lowerOrigin) {
        Map<Integer, List<TmdbEpisode>> result = source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
        if (lower == null) return result;
        for (Map.Entry<Integer, List<TmdbEpisode>> entry : lower.entrySet()) {
            List<TmdbEpisode> current = result.get(entry.getKey());
            boolean complete = payload != null && payload.hasCapability(TmdbSourceCapabilityPlanner.season(entry.getKey()));
            if (current != null && (!current.isEmpty() || complete)) continue;
            result.put(entry.getKey(), entry.getValue());
            mark(origins, "seasonEpisodes." + entry.getKey(), lowerOrigin);
        }
        return result;
    }

    private static boolean isEmpty(Object value) {
        if (value instanceof List<?> list) return list.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return value == null;
    }

    private static boolean cachedComplete(@Nullable TmdbSourcePayload payload, String group) {
        return payload != null && payload.hasCapability(group);
    }

    private static String capabilityForField(String path) {
        String normalized = path == null ? "" : path;
        if (normalized.contains("credits")) return TmdbSourceCapabilityPlanner.CREDITS;
        if (normalized.contains("recommendations")) return TmdbSourceCapabilityPlanner.RECOMMENDATIONS;
        if (normalized.contains("similar")) return TmdbSourceCapabilityPlanner.SIMILAR;
        if (normalized.contains("videos")) return TmdbSourceCapabilityPlanner.VIDEOS;
        if (normalized.contains("images")) return TmdbSourceCapabilityPlanner.IMAGES;
        if (normalized.contains("external_ids")) return TmdbSourceCapabilityPlanner.EXTERNAL_IDS;
        if (isCoreField(normalized)) return TmdbSourceCapabilityPlanner.CORE;
        String field = path == null ? "" : path.substring(path.lastIndexOf('.') + 1);
        return switch (field) {
            case "credits", "aggregate_credits", "cast", "crew" -> TmdbSourceCapabilityPlanner.CREDITS;
            case "images" -> TmdbSourceCapabilityPlanner.IMAGES;
            case "external_ids" -> TmdbSourceCapabilityPlanner.EXTERNAL_IDS;
            case "videos" -> TmdbSourceCapabilityPlanner.VIDEOS;
            case "recommendations" -> TmdbSourceCapabilityPlanner.RECOMMENDATIONS;
            case "similar" -> TmdbSourceCapabilityPlanner.SIMILAR;
            default -> "";
        };
    }

    private static boolean isCoreField(String path) {
        String field = path.substring(path.lastIndexOf('.') + 1);
        return switch (field) {
            case "id", "title", "name", "original_title", "original_name", "overview", "tagline", "status",
                    "release_date", "first_air_date", "vote_average", "vote_count", "genres", "origin_country",
                    "original_language", "runtime", "episode_run_time", "number_of_seasons", "number_of_episodes",
                    "poster_path", "backdrop_path" -> true;
            default -> false;
        };
    }

    private static String chooseDisplay(String source, String lower, @Nullable TmdbSourcePayload payload,
                                       @Nullable String targetLanguage, String field, Map<String, Origin> origins, Origin lowerOrigin) {
        if (!TextUtils.isEmpty(source) && shouldOverrideCoreDisplay(source, lower, payload, targetLanguage)) {
            mark(origins, field, Origin.REMOTE_TMDB_LANGUAGE);
            return lower == null ? "" : lower;
        }
        return choose(source, lower, field, origins, lowerOrigin);
    }

    private static boolean shouldOverrideCoreDisplay(String source, String lower, @Nullable TmdbSourcePayload payload,
                                                     @Nullable String targetLanguage) {
        if (TextUtils.isEmpty(lower) || TextUtils.isEmpty(source)) return false;
        if (TextUtils.isEmpty(targetLanguage)) return false;
        String target = TmdbLanguagePolicy.normalize(targetLanguage);
        String declared = payload == null ? "" : payload.getLanguage();
        if (!TmdbLanguagePolicy.isDisplayLanguageCompatible(target, declared)) {
            return TmdbLanguagePolicy.looksTargetLanguage(target, lower);
        }
        return false;
    }

    private static String stringValue(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return "";
        String text = value.getAsString();
        return text == null ? "" : text.trim();
    }

    private static boolean isCoreDisplayField(String path) {
        String field = path.substring(path.lastIndexOf('.') + 1);
        return "title".equals(field) || "name".equals(field) || "overview".equals(field);
    }

    private static String choose(String source, String lower, String field, Map<String, Origin> origins, Origin lowerOrigin) {
        if (source != null && !source.isEmpty()) return source;
        if (lower != null && !lower.isEmpty()) mark(origins, field, lowerOrigin);
        return lower == null ? "" : lower;
    }

    private static void mark(Map<String, Origin> origins, String field, Origin origin) {
        origins.putIfAbsent(field, origin);
    }

    private static boolean identityMatches(TmdbItem left, TmdbItem right) {
        return left.getTmdbId() > 0 && left.getTmdbId() == right.getTmdbId() && left.getMediaType().equalsIgnoreCase(right.getMediaType());
    }

    private static TmdbBundle empty() {
        return new TmdbBundle(null, new JsonObject(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private TmdbSourceMerger() {
    }
}
