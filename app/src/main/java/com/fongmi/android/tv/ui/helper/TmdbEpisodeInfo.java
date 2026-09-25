package com.fongmi.android.tv.ui.helper;

import android.content.Context;

import com.fongmi.android.tv.R;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * Normalized episode progress extracted from a TMDB TV detail response.
 */
public final class TmdbEpisodeInfo {

    public enum State {
        UNKNOWN,
        COMPLETE,
        ONGOING,
        PLANNED,
        CANCELED
    }

    private static final TmdbEpisodeInfo EMPTY = new TmdbEpisodeInfo(
            true, State.UNKNOWN, 0, 0, 0, 0, 0, 0, 0);

    private final boolean empty;
    private final State state;
    private final int seasonCount;
    private final int totalEpisodes;
    private final int lastSeason;
    private final int lastEpisode;
    private final int scopedSeason;
    private final int scopedTotalEpisodes;
    private final int scopedAiredEpisodes;

    private TmdbEpisodeInfo(boolean empty, State state, int seasonCount, int totalEpisodes,
                            int lastSeason, int lastEpisode, int scopedSeason,
                            int scopedTotalEpisodes, int scopedAiredEpisodes) {
        this.empty = empty;
        this.state = state;
        this.seasonCount = seasonCount;
        this.totalEpisodes = totalEpisodes;
        this.lastSeason = lastSeason;
        this.lastEpisode = lastEpisode;
        this.scopedSeason = scopedSeason;
        this.scopedTotalEpisodes = scopedTotalEpisodes;
        this.scopedAiredEpisodes = scopedAiredEpisodes;
    }

    public static TmdbEpisodeInfo from(String mediaType, JsonObject detail, int sourceSeason) {
        if (!isTv(mediaType, detail)) return EMPTY;

        int seasonCount = positiveInt(detail, "number_of_seasons");
        int totalEpisodes = positiveInt(detail, "number_of_episodes");
        JsonObject last = object(detail, "last_episode_to_air");
        JsonObject next = object(detail, "next_episode_to_air");
        int lastSeason = positiveInt(last, "season_number");
        int lastEpisode = positiveInt(last, "episode_number");
        int nextSeason = positiveInt(next, "season_number");
        State state = seriesState(detail, next, lastEpisode);
        Scope scope = seasonScope(detail, sourceSeason, state, lastSeason, lastEpisode, nextSeason);

        boolean empty = seasonCount <= 0
                && totalEpisodes <= 0
                && lastEpisode <= 0
                && scope.totalEpisodes <= 0
                && scope.state == State.UNKNOWN;
        if (empty) return EMPTY;

        return new TmdbEpisodeInfo(false, scope.state, seasonCount, totalEpisodes,
                lastSeason, lastEpisode, scope.season, scope.totalEpisodes, scope.airedEpisodes);
    }


    private static State seriesState(JsonObject detail, JsonObject next, int lastEpisode) {
        String status = string(detail, "status").toLowerCase(Locale.ROOT);
        if (status.contains("cancel")) return State.CANCELED;
        boolean active = next != null
                || bool(detail, "in_production")
                || status.contains("returning")
                || status.contains("production")
                || status.contains("planned")
                || status.contains("pilot");
        if (active) return lastEpisode > 0 ? State.ONGOING : State.PLANNED;
        return status.contains("ended") ? State.COMPLETE : State.UNKNOWN;
    }

    private static Scope seasonScope(JsonObject detail, int sourceSeason, State seriesState,
                                     int lastSeason, int lastEpisode, int nextSeason) {
        int total = sourceSeason > 0 ? seasonEpisodeCount(detail, sourceSeason) : 0;
        if (total <= 0) return new Scope(seriesState, 0, 0, 0);
        if (lastSeason > sourceSeason) return new Scope(State.COMPLETE, sourceSeason, total, total);
        if (lastSeason < sourceSeason) {
            State state = seriesState == State.ONGOING || seriesState == State.PLANNED
                    ? State.PLANNED : seriesState;
            return new Scope(state, sourceSeason, total, 0);
        }

        State state = seriesState;
        int aired = lastEpisode;
        if (nextSeason > sourceSeason) state = State.COMPLETE;
        else if (nextSeason == sourceSeason) state = State.ONGOING;
        else if ((state == State.COMPLETE || state == State.CANCELED) && total > 0) aired = total;
        return new Scope(state, sourceSeason, total, aired);
    }

    private static final class Scope {

        private final State state;
        private final int season;
        private final int totalEpisodes;
        private final int airedEpisodes;

        private Scope(State state, int season, int totalEpisodes, int airedEpisodes) {
            this.state = state;
            this.season = season;
            this.totalEpisodes = totalEpisodes;
            this.airedEpisodes = airedEpisodes;
        }
    }

    private static boolean isTv(String mediaType, JsonObject detail) {
        if ("tv".equalsIgnoreCase(mediaType)) return true;
        if (mediaType != null && !mediaType.trim().isEmpty()) return false;
        return detail != null && (detail.has("first_air_date")
                || detail.has("number_of_seasons")
                || detail.has("number_of_episodes"));
    }

    private static int seasonEpisodeCount(JsonObject detail, int seasonNumber) {
        JsonArray seasons = array(detail, "seasons");
        for (JsonElement element : seasons) {
            if (!element.isJsonObject()) continue;
            JsonObject season = element.getAsJsonObject();
            if (positiveInt(season, "season_number") != seasonNumber) continue;
            return positiveInt(season, "episode_count");
        }
        return 0;
    }

    private static JsonArray array(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && object.get(key).isJsonArray()
                    ? object.getAsJsonArray(key) : new JsonArray();
        } catch (Throwable ignored) {
            return new JsonArray();
        }
    }

    private static JsonObject object(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && object.get(key).isJsonObject()
                    ? object.getAsJsonObject(key) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int positiveInt(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0;
            return Math.max(0, object.get(key).getAsInt());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String string(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsString() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    public String detailText(Context context) {
        if (context == null || isEmpty()) return "";
        if (isSeasonScoped()) return scopedDetailText(context);
        if (state == State.PLANNED) {
            return totalEpisodes > 0
                    ? context.getString(R.string.tmdb_episode_planned_total, totalEpisodes)
                    : context.getString(R.string.tmdb_episode_planned);
        }
        if (state == State.ONGOING && lastEpisode > 0) {
            if (seasonCount > 1 && lastSeason > 0) {
                return totalEpisodes > 0
                        ? context.getString(R.string.tmdb_episode_series_ongoing_total, lastSeason, lastEpisode, totalEpisodes)
                        : context.getString(R.string.tmdb_episode_series_ongoing, lastSeason, lastEpisode);
            }
            return totalEpisodes > lastEpisode
                    ? context.getString(R.string.tmdb_episode_ongoing_total, lastEpisode, totalEpisodes)
                    : context.getString(R.string.tmdb_episode_ongoing, lastEpisode);
        }
        int total = knownTotalEpisodes();
        if (state == State.CANCELED && total > 0) return context.getString(R.string.tmdb_episode_canceled, total);
        if (state == State.COMPLETE && total > 0) return context.getString(R.string.tmdb_episode_complete, total);
        return total > 0 ? context.getString(R.string.tmdb_episode_total, total) : "";
    }

    public String compactText(Context context) {
        if (context == null || isEmpty()) return "";
        if (state == State.PLANNED) return context.getString(R.string.tmdb_episode_compact_planned);
        if (isSeasonScoped()) {
            if (state == State.ONGOING && scopedAiredEpisodes > 0 && scopedTotalEpisodes > scopedAiredEpisodes) {
                return context.getString(R.string.tmdb_episode_compact_season_progress,
                        scopedSeason, scopedAiredEpisodes, scopedTotalEpisodes);
            }
            if (scopedTotalEpisodes > 0) {
                return context.getString(R.string.tmdb_episode_compact_season, scopedSeason, scopedTotalEpisodes);
            }
        }
        if (state == State.ONGOING && lastEpisode > 0) {
            if (seasonCount > 1 && lastSeason > 0) {
                return totalEpisodes > 0
                        ? context.getString(R.string.tmdb_episode_compact_series_total, lastSeason, lastEpisode, totalEpisodes)
                        : context.getString(R.string.tmdb_episode_compact_series, lastSeason, lastEpisode);
            }
            return totalEpisodes > lastEpisode
                    ? context.getString(R.string.tmdb_episode_compact_progress, lastEpisode, totalEpisodes)
                    : context.getString(R.string.tmdb_episode_compact_episode, lastEpisode);
        }
        int total = knownTotalEpisodes();
        return total > 0 ? context.getString(R.string.tmdb_episode_compact_total, total) : "";
    }

    private String scopedDetailText(Context context) {
        if (state == State.PLANNED) {
            return scopedTotalEpisodes > 0
                    ? context.getString(R.string.tmdb_episode_planned_total, scopedTotalEpisodes)
                    : context.getString(R.string.tmdb_episode_planned);
        }
        if (state == State.ONGOING && scopedAiredEpisodes > 0) {
            return scopedTotalEpisodes > scopedAiredEpisodes
                    ? context.getString(R.string.tmdb_episode_season_ongoing_total,
                    scopedSeason, scopedAiredEpisodes, scopedTotalEpisodes)
                    : context.getString(R.string.tmdb_episode_season_ongoing, scopedSeason, scopedAiredEpisodes);
        }
        if (state == State.COMPLETE && scopedTotalEpisodes > 0) {
            return context.getString(R.string.tmdb_episode_season_complete, scopedSeason, scopedTotalEpisodes);
        }
        return scopedTotalEpisodes > 0
                ? context.getString(R.string.tmdb_episode_season_total, scopedSeason, scopedTotalEpisodes)
                : "";
    }

    private int knownTotalEpisodes() {
        if (totalEpisodes > 0) return totalEpisodes;
        return seasonCount <= 1 ? lastEpisode : 0;
    }

    public boolean isEmpty() {
        return empty;
    }

    public State getState() {
        return state;
    }

    public int getSeasonCount() {
        return seasonCount;
    }

    public int getTotalEpisodes() {
        return totalEpisodes;
    }

    public int getLastSeason() {
        return lastSeason;
    }

    public int getLastEpisode() {
        return lastEpisode;
    }

    public boolean isSeasonScoped() {
        return scopedSeason > 0;
    }

    public int getScopedSeason() {
        return scopedSeason;
    }

    public int getScopedTotalEpisodes() {
        return scopedTotalEpisodes;
    }

    public int getScopedAiredEpisodes() {
        return scopedAiredEpisodes;
    }
}
