package com.fongmi.android.tv.ui.helper;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.fongmi.android.tv.utils.TmdbLanguagePolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Pure capability planner for source-embedded TMDB data. */
public final class TmdbSourceCapabilityPlanner {

    public static final String CORE = "core";
    public static final String CREDITS = "credits";
    public static final String IMAGES = "images";
    public static final String EXTERNAL_IDS = "external_ids";
    public static final String VIDEOS = "videos";
    public static final String RECOMMENDATIONS = "recommendations";
    public static final String SIMILAR = "similar";

    public record UiState(boolean core, boolean credits, boolean images, boolean externalIds, boolean videos,
                          boolean recommendations, boolean similar, int seasonNumber, int episodeNumber,
                          boolean seasonVideos, boolean episodeVideos) {

        public static UiState initialScreen() {
            return new UiState(true, true, true, false, false, false, false, -1, -1, false, false);
        }

        public UiState withSeason(int seasonNumber) {
            return new UiState(core, credits, images, externalIds, videos, recommendations, similar, seasonNumber, episodeNumber, seasonVideos, episodeVideos);
        }

        public UiState withEpisode(int seasonNumber, int episodeNumber) {
            return new UiState(core, credits, images, externalIds, videos, recommendations, similar, seasonNumber, episodeNumber, seasonVideos, episodeVideos);
        }

        public UiState withVideos(boolean detailVideos, boolean seasonVideos, boolean episodeVideos) {
            return new UiState(core, credits, images, externalIds, detailVideos, recommendations, similar, seasonNumber, episodeNumber, seasonVideos, episodeVideos);
        }

        public UiState withRelated(boolean recommendations, boolean similar) {
            return new UiState(core, credits, images, externalIds, videos, recommendations, similar, seasonNumber, episodeNumber, seasonVideos, episodeVideos);
        }
    }

    public record Plan(Set<String> required, Set<String> available, Set<String> missing) {

        public Plan {
            required = Set.copyOf(required);
            available = Set.copyOf(available);
            missing = Set.copyOf(missing);
        }

        public boolean hasInitialNetworkGaps() {
            return missing.contains(CORE) || missing.contains(CREDITS) || missing.contains(IMAGES) || missing.contains(EXTERNAL_IDS);
        }

        public boolean needsNetwork() {
            return !missing.isEmpty();
        }
    }

    public static Plan plan(@Nullable TmdbBundle bundle, @Nullable TmdbSourcePayload payload, @Nullable UiState state) {
        return plan(bundle, payload, state, "");
    }

    public static Plan plan(@Nullable TmdbBundle bundle, @Nullable TmdbSourcePayload payload, @Nullable UiState state,
                            @Nullable String targetLanguage) {
        UiState ui = state == null ? UiState.initialScreen() : state;
        Set<String> required = new TreeSet<>();
        if (ui.core()) required.add(CORE);
        if (ui.credits()) required.add(CREDITS);
        if (ui.images()) required.add(IMAGES);
        if (ui.externalIds()) required.add(EXTERNAL_IDS);
        if (ui.videos()) required.add(VIDEOS);
        if (ui.recommendations()) required.add(RECOMMENDATIONS);
        if (ui.similar()) required.add(SIMILAR);
        if (ui.seasonNumber() >= 0) required.add(season(ui.seasonNumber()));
        if (ui.episodeNumber() > 0 && ui.seasonNumber() >= 0) required.add(episode(ui.seasonNumber(), ui.episodeNumber()));
        if (ui.seasonVideos() && ui.seasonNumber() >= 0) required.add(seasonVideos(ui.seasonNumber()));
        if (ui.episodeVideos() && ui.seasonNumber() >= 0 && ui.episodeNumber() > 0) required.add(episodeVideos(ui.seasonNumber(), ui.episodeNumber()));

        Set<String> available = new LinkedHashSet<>();
        for (String group : required) if (isAvailable(bundle, payload, group, ui.seasonNumber(), ui.episodeNumber(), targetLanguage)) available.add(group);
        Set<String> missing = new TreeSet<>(required);
        missing.removeAll(available);
        return new Plan(required, available, missing);
    }

    public static boolean isAvailable(@Nullable TmdbBundle bundle, @Nullable TmdbSourcePayload payload, String group, int seasonNumber, int episodeNumber) {
        return isAvailable(bundle, payload, group, seasonNumber, episodeNumber, "");
    }

    public static boolean isAvailable(@Nullable TmdbBundle bundle, @Nullable TmdbSourcePayload payload, String group,
                                      int seasonNumber, int episodeNumber, @Nullable String targetLanguage) {
        if (group == null || group.isEmpty()) return false;
        if (group.equals(CORE) && payload != null && bundle != null && bundle.item() != null
                && !coreLanguageAvailable(bundle.item(), bundle.detail(), payload, targetLanguage)) return false;
        if (payload != null && payload.hasCapability(group)) return true;
        if (bundle == null || bundle.item() == null) return false;
        JsonObject detail = bundle.detail() == null ? new JsonObject() : bundle.detail();
        return switch (group) {
            case CORE -> coreAvailable(bundle.item(), detail, payload, targetLanguage);
            case CREDITS -> !bundle.cast().isEmpty() || !bundle.creators().isEmpty();
            case IMAGES -> !bundle.item().getPosterUrl().isEmpty() && !bundle.item().getBackdropUrl().isEmpty();
            case EXTERNAL_IDS -> externalIdsAvailable(detail);
            case VIDEOS -> array(object(detail, "videos"), "results").size() > 0;
            case RECOMMENDATIONS -> pageOneAvailable(detail, "recommendations");
            case SIMILAR -> pageOneAvailable(detail, "similar");
            default -> seasonalAvailable(bundle, detail, group, seasonNumber, episodeNumber);
        };
    }

    public static String season(int seasonNumber) {
        return "season:" + Math.max(0, seasonNumber);
    }

    public static String seasonVideos(int seasonNumber) {
        return "season_videos:" + Math.max(0, seasonNumber);
    }

    public static String episode(int seasonNumber, int episodeNumber) {
        return "episode:" + Math.max(0, seasonNumber) + ':' + Math.max(1, episodeNumber);
    }

    public static String episodeVideos(int seasonNumber, int episodeNumber) {
        return "episode_videos:" + Math.max(0, seasonNumber) + ':' + Math.max(1, episodeNumber);
    }

    private static boolean seasonalAvailable(TmdbBundle bundle, JsonObject detail, String group, int seasonNumber, int episodeNumber) {
        if (group.startsWith("season_videos:") || group.startsWith("episode_videos:")) return false;
        if (group.startsWith("episode:")) {
            int[] parsed = parseEpisode(group);
            if (parsed == null) return false;
            for (TmdbEpisode item : bundle.seasonEpisodes().getOrDefault(parsed[0], List.of())) if (item.getNumber() == parsed[1]) return true;
            return false;
        }
        if (group.startsWith("season:")) {
            int[] parsed = parseSeason(group);
            if (parsed == null) return false;
            List<TmdbEpisode> episodes = bundle.seasonEpisodes().get(parsed[0]);
            return episodes != null && !episodes.isEmpty();
        }
        return false;
    }

    private static boolean coreAvailable(TmdbItem item, JsonObject detail, @Nullable TmdbSourcePayload payload, @Nullable String targetLanguage) {
        if (item.getTmdbId() <= 0 || item.getMediaType().isEmpty() || item.getTitle().isEmpty()) return false;
        if (!coreLanguageAvailable(item, detail, payload, targetLanguage)) return false;
        if (string(detail, "overview").isEmpty() || date(detail).isEmpty() || !detail.has("vote_average") || !detail.has("genres")) return false;
        return !item.isTv() || (detail.has("number_of_seasons") && detail.has("number_of_episodes"));
    }

    private static boolean coreLanguageAvailable(TmdbItem item, JsonObject detail, @Nullable TmdbSourcePayload payload, @Nullable String targetLanguage) {
        if (TextUtils.isEmpty(targetLanguage)) return true;
        String normalized = TmdbLanguagePolicy.normalize(targetLanguage);
        String declared = payload == null ? "" : payload.getLanguage();
        if (!TextUtils.isEmpty(declared)) return TmdbLanguagePolicy.isDisplayLanguageCompatible(normalized, declared);
        return TmdbLanguagePolicy.looksTargetLanguage(normalized, item.getTitle()) || TmdbLanguagePolicy.looksTargetLanguage(normalized, item.getOverview());
    }

    private static boolean pageOneAvailable(JsonObject detail, String field) {
        JsonObject value = object(detail, field);
        if (value == null) return false;
        JsonElement page = value.get("page");
        return page != null && page.isJsonPrimitive() && page.getAsJsonPrimitive().isNumber() && page.getAsInt() == 1 && value.has("results") && value.get("results").isJsonArray() && value.getAsJsonArray("results").size() > 0;
    }

    private static boolean externalIdsAvailable(JsonObject detail) {
        JsonObject externalIds = object(detail, "external_ids");
        if (externalIds == null) return false;
        for (Map.Entry<String, JsonElement> entry : externalIds.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) continue;
            if (value.getAsJsonPrimitive().isString() && !value.getAsString().trim().isEmpty()) return true;
            if (value.getAsJsonPrimitive().isNumber()) {
                try {
                    if (value.getAsLong() > 0) return true;
                } catch (RuntimeException ignored) {
                    // Keep checking the remaining IDs.
                }
            }
        }
        return false;
    }

    private static String date(JsonObject detail) {
        String date = string(detail, "release_date");
        return date.isEmpty() ? string(detail, "first_air_date") : date;
    }

    private static String string(JsonObject detail, String key) {
        if (detail == null || !detail.has(key) || !detail.get(key).isJsonPrimitive() || !detail.getAsJsonPrimitive(key).isString()) return "";
        String value = detail.get(key).getAsString();
        return value == null ? "" : value.trim();
    }

    private static JsonObject object(JsonObject detail, String key) {
        if (detail == null || !detail.has(key) || !detail.get(key).isJsonObject()) return null;
        return detail.getAsJsonObject(key);
    }

    private static JsonArray array(JsonObject detail, String key) {
        if (detail == null || !detail.has(key) || !detail.get(key).isJsonArray()) return new JsonArray();
        return detail.getAsJsonArray(key);
    }

    private static int[] parseSeason(String group) {
        try {
            return new int[]{Integer.parseInt(group.substring("season:".length()))};
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int[] parseEpisode(String group) {
        try {
            String[] parts = group.substring("episode:".length()).split(":", -1);
            return parts.length == 2 ? new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])} : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private TmdbSourceCapabilityPlanner() {
    }
}
