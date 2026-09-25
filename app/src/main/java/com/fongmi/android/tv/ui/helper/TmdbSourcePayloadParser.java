package com.fongmi.android.tv.ui.helper;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates and normalizes source-provided C16 TMDB payloads without throwing into the detail page. */
public final class TmdbSourcePayloadParser {

    public static final int MAX_PROTOCOL_BYTES = 2 * 1024 * 1024;
    public static final int MAX_ARRAY_ITEMS = 500;
    public static final int MAX_STRING_BYTES = 64 * 1024;

    private static final Pattern FETCHED_AT = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})$");
    private static final Pattern SEASON = Pattern.compile("^season:(\\d+)$");
    private static final Pattern SEASON_VIDEOS = Pattern.compile("^season_videos:(\\d+)$");
    private static final Pattern EPISODE = Pattern.compile("^episode:(\\d+):(\\d+)$");
    private static final Pattern EPISODE_VIDEOS = Pattern.compile("^episode_videos:(\\d+):(\\d+)$");

    private static final Set<String> STATIC_CAPABILITIES = Set.of(
            "core", "credits", "images", "external_ids", "videos", "recommendations", "similar"
    );
    private static final Set<String> IMAGE_FIELDS = Set.of(
            "poster_path", "backdrop_path", "still_path", "profile_path", "logo_path", "file_path"
    );
    private static final Set<String> STRING_FIELDS = Set.of(
            "title", "name", "original_title", "original_name", "overview", "tagline", "status",
            "release_date", "first_air_date", "original_language", "poster_path", "backdrop_path", "still_path",
            "profile_path", "logo_path", "file_path", "imdb_id", "iso_639_1", "iso_3166_1"
    );
    private static final Set<String> NUMBER_FIELDS = Set.of(
            "id", "vote_average", "vote_count", "runtime", "number_of_seasons", "number_of_episodes",
            "season_number", "episode_number", "order", "popularity"
    );
    private static final Set<String> OBJECT_FIELDS = Set.of(
            "images", "credits", "aggregate_credits", "external_ids", "videos", "recommendations", "similar",
            "translations", "content_ratings", "release_dates"
    );
    private static final Set<String> ARRAY_FIELDS = Set.of(
            "genres", "origin_country", "episode_run_time", "seasons", "episodes", "results", "cast", "crew",
            "guest_stars", "translations", "content_ratings", "release_dates", "backdrops", "posters", "logos", "profiles"
    );
    private static final String[] TRIM_ORDER = {
            "release_dates", "content_ratings", "translations", "similar", "recommendations", "videos",
            "images", "credits", "aggregate_credits", "seasons", "episodes"
    };

    private static final Gson GSON = new Gson();

    @Nullable
    public static TmdbSourcePayload parseJson(@Nullable String json) {
        if (json == null || json.isBlank()) return null;
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_PROTOCOL_BYTES) return null;
        try {
            return parse(GSON.fromJson(json, TmdbSourcePayload.class));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    public static TmdbSourcePayload parse(@Nullable TmdbSourcePayload source) {
        if (source == null || source.getSchema() != TmdbSourcePayload.SCHEMA_VERSION || source.getTmdbId() <= 0) return null;
        String mediaType = normalizeMediaType(source.getMediaType());
        if (mediaType.isEmpty()) return null;
        int seasonNumber = source.getSeasonNumber();
        if (seasonNumber < 0 || ("movie".equals(mediaType) && seasonNumber != 0)) return null;

        TmdbSourcePayload result = source.copy();
        result.setMediaType(mediaType);
        result.setSeasonNumber(seasonNumber);
        result.setLanguage(normalizeLanguage(result.getLanguage()));
        result.setFetchedAt(normalizeFetchedAt(result.getFetchedAt()));
        result.setSourceKind(normalizeSourceKind(result.getSourceKind()));

        JsonObject detail = parseDetail(result.getDetailJson());
        JsonElement sanitized = sanitize(detail, "");
        JsonObject normalized = sanitized != null && sanitized.isJsonObject() ? sanitized.getAsJsonObject() : new JsonObject();
        normalized = normalizeIdentity(normalized, result);
        if (normalized == null) return null;

        LinkedHashSet<String> complete = canonicalCapabilities(result.getComplete());
        removeUnsupportedClaims(normalized, complete);
        normalized = trimToLimit(normalized, complete);
        if (normalized == null) return null;

        result.setComplete(complete);
        result.setDetailJson(normalized.toString());
        if (result.estimatedParcelBytes() > MAX_PROTOCOL_BYTES) return null;
        return result;
    }

    /** Returns an empty string for invalid image references and otherwise the protocol-safe value. */
    public static String normalizeImageUrl(@Nullable String value) {
        if (value == null) return "";
        String result = value.trim();
        if (result.isEmpty() || result.startsWith("//")) return "";
        if (result.startsWith("/")) return result.indexOf('\\') >= 0 || result.indexOf(' ') >= 0 ? "" : result;
        try {
            URI uri = URI.create(result);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isEmpty()) return "";
            return result;
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static JsonObject parseDetail(String json) {
        if (json == null || json.isBlank()) return new JsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    @Nullable
    private static JsonObject normalizeIdentity(JsonObject detail, TmdbSourcePayload payload) {
        JsonElement id = detail.get("id");
        if (id != null) {
            if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isNumber()) {
                detail.remove("id");
            } else {
                try {
                    if (id.getAsInt() != payload.getTmdbId()) return null;
                } catch (RuntimeException ignored) {
                    detail.remove("id");
                }
            }
        }
        if ("tv".equals(payload.getMediaType())) {
            JsonElement season = detail.get("season_number");
            if (season != null) {
                if (!season.isJsonPrimitive() || !season.getAsJsonPrimitive().isNumber()) {
                    detail.remove("season_number");
                } else {
                    try {
                        if (season.getAsInt() != payload.getSeasonNumber()) return null;
                    } catch (RuntimeException ignored) {
                        detail.remove("season_number");
                    }
                }
            }
        } else {
            detail.remove("season_number");
        }
        return detail;
    }

    @Nullable
    private static JsonElement sanitize(@Nullable JsonElement element, String field) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                String value = primitive.getAsString();
                if (value == null || value.getBytes(StandardCharsets.UTF_8).length > MAX_STRING_BYTES) return null;
                if (IMAGE_FIELDS.contains(field)) {
                    String image = normalizeImageUrl(value);
                    return image.isEmpty() ? null : new JsonPrimitive(image);
                }
                return new JsonPrimitive(value);
            }
            if (NUMBER_FIELDS.contains(field) && !primitive.isNumber()) return null;
            if (STRING_FIELDS.contains(field)) return null;
            return element.deepCopy();
        }
        if (ARRAY_FIELDS.contains(field) && !element.isJsonArray()) return null;
        if (OBJECT_FIELDS.contains(field) && !element.isJsonObject()) return null;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() > MAX_ARRAY_ITEMS) return null;
            JsonArray result = new JsonArray();
            for (JsonElement item : array) {
                JsonElement sanitized = sanitize(item, "");
                if (sanitized != null) result.add(sanitized);
            }
            return result;
        }
        if (element.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (String key : element.getAsJsonObject().keySet()) {
                JsonElement sanitized = sanitize(element.getAsJsonObject().get(key), key);
                if (sanitized != null) result.add(key, sanitized);
            }
            if (result.isEmpty() && !element.getAsJsonObject().isEmpty() && OBJECT_FIELDS.contains(field) && !"external_ids".equals(field)) return null;
            return result;
        }
        return null;
    }

    private static LinkedHashSet<String> canonicalCapabilities(Set<String> values) {
        TreeSet<String> sorted = new TreeSet<>();
        if (values != null) for (String value : values) {
            String canonical = canonicalCapability(value);
            if (!canonical.isEmpty()) sorted.add(canonical);
        }
        return new LinkedHashSet<>(sorted);
    }

    private static String canonicalCapability(String value) {
        if (value == null) return "";
        String group = value.trim().toLowerCase(Locale.ROOT);
        if (STATIC_CAPABILITIES.contains(group)) return group;
        Matcher season = SEASON.matcher(group);
        if (season.matches()) return canonicalSeason("season", season.group(1));
        Matcher seasonVideos = SEASON_VIDEOS.matcher(group);
        if (seasonVideos.matches()) return canonicalSeason("season_videos", seasonVideos.group(1));
        Matcher episode = EPISODE.matcher(group);
        if (episode.matches()) return canonicalEpisode("episode", episode.group(1), episode.group(2));
        Matcher episodeVideos = EPISODE_VIDEOS.matcher(group);
        if (episodeVideos.matches()) return canonicalEpisode("episode_videos", episodeVideos.group(1), episodeVideos.group(2));
        return "";
    }

    private static String canonicalSeason(String prefix, String number) {
        try {
            int value = Integer.parseInt(number);
            return value < 0 ? "" : prefix + ':' + value;
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static String canonicalEpisode(String prefix, String season, String episode) {
        try {
            int seasonNumber = Integer.parseInt(season);
            int episodeNumber = Integer.parseInt(episode);
            return seasonNumber < 0 || episodeNumber <= 0 ? "" : prefix + ':' + seasonNumber + ':' + episodeNumber;
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static void removeUnsupportedClaims(JsonObject detail, Set<String> complete) {
        if (!hasAny(detail, "credits", "aggregate_credits")) complete.remove("credits");
        if (!hasAny(detail, "images", "poster_path", "backdrop_path")) complete.remove("images");
        if (!hasObject(detail, "external_ids")) complete.remove("external_ids");
        if (!hasObject(detail, "videos")) complete.remove("videos");
        if (!hasObject(detail, "recommendations")) complete.remove("recommendations");
        if (!hasObject(detail, "similar")) complete.remove("similar");
        if (!hasArray(detail, "seasons")) {
            complete.removeIf(group -> group.startsWith("season:"));
            complete.removeIf(group -> group.startsWith("season_videos:"));
        }
    }

    @Nullable
    private static JsonObject trimToLimit(JsonObject detail, Set<String> complete) {
        for (String field : TRIM_ORDER) {
            if (detail.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_PROTOCOL_BYTES) return detail;
            detail.remove(field);
            removeCapabilityForField(complete, field);
        }
        return detail.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_PROTOCOL_BYTES ? detail : null;
    }

    private static void removeCapabilityForField(Set<String> complete, String field) {
        switch (field) {
            case "credits", "aggregate_credits" -> complete.remove("credits");
            case "images" -> complete.remove("images");
            case "videos" -> complete.remove("videos");
            case "recommendations" -> complete.remove("recommendations");
            case "similar" -> complete.remove("similar");
            case "seasons" -> complete.removeIf(group -> group.startsWith("season:") || group.startsWith("season_videos:"));
            case "episodes" -> complete.removeIf(group -> group.startsWith("episode:") || group.startsWith("episode_videos:"));
            default -> {
            }
        }
    }

    private static boolean hasAny(JsonObject object, String... fields) {
        for (String field : fields) if (object.has(field) && !object.get(field).isJsonNull()) return true;
        return false;
    }

    private static boolean hasObject(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonObject();
    }

    private static boolean hasArray(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonArray();
    }

    private static String normalizeMediaType(String value) {
        String result = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "movie".equals(result) || "tv".equals(result) ? result : "";
    }

    private static String normalizeLanguage(String value) {
        String result = value == null ? "" : value.trim();
        return result.length() > 64 ? "" : result;
    }

    private static String normalizeFetchedAt(String value) {
        String result = value == null ? "" : value.trim();
        return FETCHED_AT.matcher(result).matches() ? result : "";
    }

    private static String normalizeSourceKind(String value) {
        return TmdbSourcePayload.CACHE_TMDB.equals(value) || TmdbSourcePayload.REMOTE_TMDB.equals(value)
                ? value : TmdbSourcePayload.SOURCE_TMDB;
    }

    private TmdbSourcePayloadParser() {
    }
}
