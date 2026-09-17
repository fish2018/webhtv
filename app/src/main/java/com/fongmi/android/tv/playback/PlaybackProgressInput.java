package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.db.AppDatabase;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PlaybackProgressInput {

    private static final Gson GSON = new Gson();

    @SerializedName("historyKey")
    public String historyKey;
    @SerializedName("siteKey")
    public String siteKey;
    @SerializedName("vodId")
    public String vodId;
    @SerializedName("vodName")
    public String vodName;
    @SerializedName("vodPic")
    public String vodPic;
    @SerializedName("flag")
    public String flag;
    @SerializedName("sourceBindingKey")
    public String sourceBindingKey;
    @SerializedName("episodeName")
    public String episodeName;
    @SerializedName("episodeUrl")
    public String episodeUrl;
    @SerializedName("mediaType")
    public String mediaType;
    @SerializedName("tmdbId")
    public int tmdbId;
    @SerializedName("seasonNumber")
    public int seasonNumber = -1;
    @SerializedName("tmdbEpisodeNumber")
    public int episodeNumber = -1;
    @SerializedName("positionMs")
    public long positionMs;
    @SerializedName("durationMs")
    public long durationMs;
    @SerializedName("progress")
    public double progress;
    @SerializedName("speed")
    public float speed;
    @SerializedName("speedOverride")
    public Boolean speedOverride;
    @SerializedName("completed")
    public boolean completed;
    @SerializedName("updatedAt")
    public long updatedAt;
    @SerializedName("cid")
    public int cid;
    @SerializedName("configKey")
    public String configKey;
    @SerializedName("configName")
    public String configName;
    @SerializedName("configUrl")
    public String configUrl;
    @SerializedName("clientKey")
    public String clientKey;

    public PlaybackProgressInput normalize() {
        historyKey = safe(historyKey);
        siteKey = fallback(siteKey, part(historyKey, 0));
        vodId = fallback(vodId, part(historyKey, 1));
        vodName = safe(vodName);
        vodPic = safe(vodPic);
        flag = safe(flag);
        sourceBindingKey = safe(sourceBindingKey);
        episodeName = safe(episodeName);
        episodeUrl = safe(episodeUrl);
        mediaType = safe(mediaType).toLowerCase(Locale.ROOT);
        if (!"tv".equals(mediaType) && !"movie".equals(mediaType)) mediaType = "";
        if (seasonNumber < 0) seasonNumber = -1;
        if (episodeNumber <= 0) episodeNumber = -1;
        configKey = PlaybackConfigIdentity.normalizeKey(configKey);
        configName = safe(configName);
        configUrl = safe(configUrl);
        if (empty(configKey) && !empty(configUrl)) configKey = PlaybackConfigIdentity.keyForUrl(configUrl);
        clientKey = safe(clientKey);
        if (speed <= 0) speed = 1f;
        if (durationMs > 0 && positionMs <= 0 && progress > 0) positionMs = Math.round(durationMs * progress);
        if (completed && durationMs > 0 && positionMs < durationMs) positionMs = durationMs;
        return this;
    }

    public String validate() {
        return validate(true);
    }

    String validate(boolean defaultUpdatedAt) {
        normalize();
        if (updatedAt <= 0 && defaultUpdatedAt) updatedAt = System.currentTimeMillis();
        if (empty(siteKey)) return "siteKey不能为空";
        if (empty(vodId)) return "vodId不能为空";
        if (empty(vodName)) return "vodName不能为空";
        if (empty(episodeName)) return "episodeName不能为空";
        if (positionMs <= 0) return "positionMs必须大于0";
        if (durationMs <= 0) return "durationMs必须大于0";
        if (positionMs > durationMs && durationMs > 0) positionMs = durationMs;
        return "";
    }

    public static PlaybackProgressInput fromJson(JsonObject object) {
        PlaybackProgressInput input = GSON.fromJson(object, PlaybackProgressInput.class);
        if (input == null) input = new PlaybackProgressInput();
        applyAliases(input, object);
        return input.normalize();
    }

    public static PlaybackProgressInput fromJson(String text) {
        JsonElement element = JsonParser.parseString(text);
        if (element == null || !element.isJsonObject()) return new PlaybackProgressInput();
        return fromJson(unwrapSingle(element.getAsJsonObject()));
    }

    public static List<PlaybackProgressInput> listFromJson(String text) {
        if (empty(text)) return Collections.emptyList();
        JsonElement element = JsonParser.parseString(text);
        if (element == null || element.isJsonNull()) return Collections.emptyList();
        JsonArray array = asArray(element);
        if (array == null) return element.isJsonObject() ? Collections.singletonList(fromJson(unwrapSingle(element.getAsJsonObject()))) : Collections.emptyList();
        List<PlaybackProgressInput> inputs = new ArrayList<>();
        for (JsonElement item : array) if (item != null && item.isJsonObject()) inputs.add(fromJson(item.getAsJsonObject()));
        return inputs;
    }

    public String targetHistoryKey(int cid) {
        normalize();
        if (!empty(configKey) && !empty(siteKey) && !empty(vodId)) return siteKey + AppDatabase.SYMBOL + vodId + AppDatabase.SYMBOL + cid;
        if (validHistoryKey(historyKey)) return historyKey;
        return siteKey + AppDatabase.SYMBOL + vodId + AppDatabase.SYMBOL + cid;
    }

    private static void applyAliases(PlaybackProgressInput input, JsonObject object) {
        input.siteKey = firstString(input.siteKey, object, "site", "site_key");
        input.configKey = firstString(input.configKey, object, "config_key", "interfaceKey", "sourceConfigKey");
        input.configName = firstString(input.configName, object, "config_name", "interfaceName", "sourceConfigName");
        input.configUrl = firstString(input.configUrl, object, "config_url", "interfaceUrl", "sourceConfigUrl");
        input.vodId = firstString(input.vodId, object, "vod_id", "videoId", "itemId");
        input.vodName = firstString(input.vodName, object, "name", "title", "vod_name");
        input.vodPic = firstString(input.vodPic, object, "pic", "poster", "vodPic", "vod_pic");
        input.flag = firstString(input.flag, object, "vodFlag", "line", "source");
        input.sourceBindingKey = firstString(input.sourceBindingKey, object, "flagKey", "source_binding_key");
        input.episodeName = firstString(input.episodeName, object, "episode", "episodeTitle", "vodRemarks", "remarks");
        input.episodeUrl = firstString(input.episodeUrl, object, "url", "playUrl", "episode_url");
        input.mediaType = firstString(input.mediaType, object, "media_type", "type");
        input.tmdbId = firstInt(input.tmdbId, object, "tmdb_id", "tmdb");
        input.seasonNumber = firstInt(input.seasonNumber, object, "season", "season_number", "tmdbSeasonNumber");
        input.episodeNumber = firstInt(input.episodeNumber, object, "episodeNumber", "episode_number", "tmdb_episode_number");
        input.positionMs = firstLong(input.positionMs, object, "position", "position_ms", "pos");
        input.durationMs = firstLong(input.durationMs, object, "duration", "duration_ms");
        input.updatedAt = firstLong(input.updatedAt, object, "timestamp", "updateTime", "updated_at");
    }

    private static JsonObject unwrapSingle(JsonObject object) {
        for (String key : new String[]{"data", "record", "item"}) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonObject()) return value.getAsJsonObject();
        }
        return object;
    }

    private static JsonArray asArray(JsonElement element) {
        if (element.isJsonArray()) return element.getAsJsonArray();
        if (!element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        for (String key : new String[]{"items", "records", "data", "list"}) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonArray()) return value.getAsJsonArray();
        }
        return null;
    }

    private boolean validHistoryKey(String key) {
        return !empty(key) && key.split(AppDatabase.SYMBOL).length >= 2;
    }

    private static String firstString(String current, JsonObject object, String... keys) {
        if (!empty(current)) return current;
        for (String key : keys) {
            try {
                JsonElement value = object.get(key);
                if (value != null && !value.isJsonNull()) return value.getAsString();
            } catch (Exception ignored) {
            }
        }
        return current;
    }

    private static long firstLong(long current, JsonObject object, String... keys) {
        if (current > 0) return current;
        for (String key : keys) {
            try {
                JsonElement value = object.get(key);
                if (value != null && !value.isJsonNull()) return value.getAsLong();
            } catch (Exception ignored) {
            }
        }
        return current;
    }

    private static int firstInt(int current, JsonObject object, String... keys) {
        if (current > 0) return current;
        for (String key : keys) {
            try {
                JsonElement value = object.get(key);
                if (value != null && !value.isJsonNull()) return value.getAsInt();
            } catch (Exception ignored) {
            }
        }
        return current;
    }

    public boolean hasTmdbEpisodeIdentity() {
        normalize();
        return "tv".equals(mediaType) && tmdbId > 0 && seasonNumber >= 0 && episodeNumber > 0;
    }

    private static String part(String key, int index) {
        try {
            String[] parts = safe(key).split(AppDatabase.SYMBOL);
            return parts.length > index ? parts[index] : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String fallback(String value, String fallback) {
        return empty(value) ? safe(fallback).trim() : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
