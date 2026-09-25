package com.fongmi.android.tv.following;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class FollowingMetadataClient {

    private final TmdbService service;

    public FollowingMetadataClient() {
        this(new TmdbService());
    }

    FollowingMetadataClient(TmdbService service) {
        this.service = service;
    }

    public FollowingMetadataSnapshot fetch(Following item, boolean refresh) throws Exception {
        if (item == null) throw new IllegalArgumentException("following == null");
        if (item.tmdbId <= 0 || !Setting.isTmdbReady()) return null;
        TmdbItem tmdbItem = tmdbItem(item);
        TmdbConfig config = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        JsonObject detail = service.detailForFollowing(tmdbItem, config, refresh);
        FollowingMetadataSnapshot snapshot = parseDetail(detail, item, System.currentTimeMillis());
        if (item.trackedSeason >= 0) {
            try {
                JsonObject season = service.season(tmdbItem, item.trackedSeason, config, detail, refresh);
                applySeason(snapshot, season, item.trackedSeason, System.currentTimeMillis());
            } catch (Throwable ignored) {
                // TV detail already carries enough state for a safe fallback.
            }
        }
        return snapshot;
    }

    public static TmdbItem tmdbItem(Following item) {
        return new TmdbItem(item.tmdbId, FollowingIdentity.normalizeMediaType(item.mediaType), item.vodName,
                "", "", item.vodPic, "", "", 0.0);
    }

    static FollowingMetadataSnapshot parseDetail(JsonObject detail, Following item, long now) {
        FollowingMetadataSnapshot snapshot = new FollowingMetadataSnapshot();
        snapshot.source = "tmdb";
        snapshot.status = FollowingMetadataSnapshot.normalizeStatus(string(detail, "status"));
        snapshot.seriesTotalEpisodes = integer(detail, "number_of_episodes");
        snapshot.fetchedAt = now;

        JsonObject last = object(detail, "last_episode_to_air");
        if (last != null && !last.isJsonNull()) {
            snapshot.latestReleasedSeason = integer(last, "season_number");
            snapshot.latestReleasedEpisode = integer(last, "episode_number");
        }
        JsonObject next = object(detail, "next_episode_to_air");
        if (next != null && !next.isJsonNull()) {
            snapshot.nextAirSeason = integer(next, "season_number");
            snapshot.nextAirEpisode = integer(next, "episode_number");
            snapshot.nextAirAt = airTime(string(next, "air_date"));
        }
        JsonArray seasons = array(detail, "seasons");
        if (seasons != null) {
            for (JsonElement element : seasons) {
                if (!element.isJsonObject()) continue;
                JsonObject season = element.getAsJsonObject();
                if (integer(season, "season_number") != item.trackedSeason) continue;
                snapshot.seasonTotalEpisodes = integer(season, "episode_count");
                break;
            }
        }
        if (snapshot.seasonTotalEpisodes <= 0 && snapshot.latestReleasedSeason == item.trackedSeason) {
            snapshot.seasonTotalEpisodes = snapshot.latestReleasedEpisode;
        }
        snapshot.seasonReleasedEpisodes = snapshot.latestReleasedSeason == item.trackedSeason
                ? snapshot.latestReleasedEpisode : 0;
        return snapshot;
    }

    static void applySeason(FollowingMetadataSnapshot snapshot, JsonObject season, int trackedSeason, long now) {
        if (snapshot == null || season == null) return;
        snapshot.seasonTotalEpisodes = Math.max(snapshot.seasonTotalEpisodes, integer(season, "episode_count"));
        int released = 0;
        int latestReleased = 0;
        JsonArray episodes = array(season, "episodes");
        if (episodes != null) {
            for (JsonElement element : episodes) {
                if (!element.isJsonObject()) continue;
                JsonObject episode = element.getAsJsonObject();
                int number = integer(episode, "episode_number");
                if (number <= 0) continue;
                long airAt = airTime(string(episode, "air_date"));
                if (airAt > 0 && airAt <= now) {
                    released++;
                    latestReleased = Math.max(latestReleased, number);
                }
            }
        }
        if (released > 0) snapshot.seasonReleasedEpisodes = Math.max(snapshot.seasonReleasedEpisodes, released);
        if (latestReleased > 0 && snapshot.latestReleasedSeason == trackedSeason) {
            snapshot.latestReleasedEpisode = Math.max(snapshot.latestReleasedEpisode, latestReleased);
        }
    }

    static long airTime(String date) {
        if (date == null || date.length() < 10) return 0;
        try {
            SimpleDateFormat source = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            source.setLenient(false);
            source.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = source.parse(date.substring(0, 10));
            if (parsed == null) return 0;
            Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.US);
            calendar.setTime(parsed);
            calendar.set(Calendar.HOUR_OF_DAY, 20);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTimeInMillis();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static int integer(JsonObject object, String key) {
        try {
            JsonElement value = object == null ? null : object.get(key);
            return value == null || value.isJsonNull() ? 0 : value.getAsInt();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String string(JsonObject object, String key) {
        try {
            JsonElement value = object == null ? null : object.get(key);
            return value == null || value.isJsonNull() ? "" : value.getAsString();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
