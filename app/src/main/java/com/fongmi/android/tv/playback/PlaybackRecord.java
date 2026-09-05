package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Player;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.player.PlayerManager;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

public class PlaybackRecord {

    public static final String SCHEMA = "webhtv.playback.v1";

    public String schema;
    public String event;
    public String eventId;
    public long timestamp;
    public String scope;
    public long deletedAt;
    public String sessionId;
    public String dedupeKey;
    public int cid;
    public String configKey;
    public String configName;
    public String historyKey;
    public String siteKey;
    public String siteName;
    public String vodId;
    public String vodName;
    public String vodPic;
    public String flag;
    public String sourceBindingKey;
    public String episodeName;
    public String episodeUrl;
    public Integer episodeIndex;
    public String mediaType;
    public int tmdbId;
    public int seasonNumber;
    public int tmdbEpisodeNumber;
    public String state;
    public long positionMs;
    public long durationMs;
    public double progress;
    public float speed;
    public boolean speedOverride;
    public boolean completed;
    public String appVersion;
    public String client;
    public String clientKey;

    public PlaybackRecord() {
        this.schema = SCHEMA;
        this.timestamp = System.currentTimeMillis();
        this.event = "";
        this.eventId = "";
        this.scope = "";
        this.sessionId = "";
        this.dedupeKey = "";
        this.configKey = "";
        this.configName = "";
        this.historyKey = "";
        this.siteKey = "";
        this.siteName = "";
        this.vodId = "";
        this.vodName = "";
        this.vodPic = "";
        this.flag = "";
        this.sourceBindingKey = "";
        this.episodeName = "";
        this.episodeUrl = "";
        this.mediaType = "";
        this.seasonNumber = -1;
        this.tmdbEpisodeNumber = -1;
        this.state = "idle";
        this.speed = 1f;
        this.appVersion = BuildConfig.VERSION_NAME;
        this.client = BuildConfig.FLAVOR_mode;
        this.clientKey = "";
    }

    public static boolean canCreate(@Nullable History history) {
        return history != null && !TextUtils.isEmpty(history.getKey()) && history.canSave() && validTime(history.getPosition()) && validTime(history.getDuration());
    }

    public static PlaybackRecord from(@Nullable History history, @Nullable PlayerManager player, @Nullable String event, String sessionId) {
        PlaybackRecord record = new PlaybackRecord();
        record.event = event == null ? "" : event;
        record.eventId = TextUtils.isEmpty(record.event) ? "" : UUID.randomUUID().toString();
        record.sessionId = sessionId == null ? "" : sessionId;
        if (history == null) return record;
        record.cid = history.getCid();
        record.configKey = PlaybackConfigIdentity.keyForCid(record.cid);
        record.configName = PlaybackConfigIdentity.nameForCid(record.cid);
        record.historyKey = safe(history.getKey());
        record.siteKey = part(record.historyKey, 0);
        record.vodId = part(record.historyKey, 1);
        record.siteName = siteName(record.siteKey);
        record.vodName = safe(history.getVodName());
        record.vodPic = safe(history.getVodPic());
        record.flag = safe(history.getVodFlag());
        record.sourceBindingKey = safe(history.getSourceBindingKey());
        record.episodeName = safe(history.getVodRemarks());
        record.episodeUrl = safe(history.getEpisodeUrl());
        applyTmdbIdentity(record, history);
        record.positionMs = position(history, player);
        record.durationMs = duration(history, player);
        record.progress = progress(record.positionMs, record.durationMs);
        record.speed = speed(history, player);
        record.speedOverride = history.hasUserSpeed();
        record.state = state(player);
        record.completed = completed(record.state, record.positionMs, record.durationMs);
        record.dedupeKey = dedupeKey(record);
        record.clientKey = clientKey();
        return record;
    }

    public static PlaybackRecord deleted(PlaybackProgressDeleteInput input, int cid) {
        PlaybackRecord record = new PlaybackRecord();
        if (input == null) return record;
        input.normalize();
        if (input.deletedAt <= 0) input.deletedAt = System.currentTimeMillis();
        record.event = "playback.deleted";
        record.eventId = UUID.randomUUID().toString();
        record.timestamp = input.deletedAt;
        record.deletedAt = input.deletedAt;
        record.scope = input.isAllScope() ? "all" : input.isSiteScope() ? "site"
                : input.isSeasonScope() ? "season" : "item";
        record.cid = cid;
        record.configKey = TextUtils.isEmpty(input.configKey) ? PlaybackConfigIdentity.keyForCid(cid) : input.configKey;
        record.configName = PlaybackConfigIdentity.nameForCid(cid);
        record.historyKey = safe(input.historyKey);
        record.siteKey = safe(input.siteKey);
        record.siteName = siteName(record.siteKey);
        record.vodId = safe(input.vodId);
        record.episodeName = safe(input.episodeName);
        record.mediaType = safe(input.mediaType);
        record.tmdbId = input.tmdbId;
        record.seasonNumber = input.seasonNumber;
        record.state = "deleted";
        record.dedupeKey = sha256(join(record.configKey, record.scope, record.historyKey,
                record.siteKey, record.vodId, record.mediaType,
                String.valueOf(record.tmdbId), String.valueOf(record.seasonNumber)));
        record.clientKey = clientKey();
        return record;
    }

    static void applyTmdbIdentity(PlaybackRecord record, History history) {
        if (record == null || history == null) return;
        record.mediaType = safe(history.getMediaType()).trim().toLowerCase(Locale.ROOT);
        record.tmdbId = history.getTmdbId();
        record.seasonNumber = history.getTmdbSeasonNumber();
        record.tmdbEpisodeNumber = history.getTmdbEpisodeNumber();
    }

    public PlaybackRecord withEvent(String event) {
        PlaybackRecord record = copy();
        record.event = event == null ? "" : event;
        record.eventId = TextUtils.isEmpty(record.event) ? "" : UUID.randomUUID().toString();
        record.timestamp = System.currentTimeMillis();
        return record;
    }

    public PlaybackRecord copyFor(PlaybackFieldPolicy policy) {
        PlaybackRecord record = new PlaybackRecord();
        record.clear();
        if (policy.includes("schema")) record.schema = schema;
        if (policy.includes("event")) record.event = event;
        if (policy.includes("eventId")) record.eventId = eventId;
        if (policy.includes("timestamp")) record.timestamp = timestamp;
        if (policy.includes("scope")) record.scope = scope;
        if (policy.includes("deletedAt")) record.deletedAt = deletedAt;
        if (policy.includes("sessionId")) record.sessionId = sessionId;
        if (policy.includes("dedupeKey")) record.dedupeKey = dedupeKey;
        if (policy.includes("cid")) record.cid = cid;
        if (policy.includes("configKey")) record.configKey = configKey;
        if (policy.includes("configName")) record.configName = configName;
        if (policy.includes("historyKey")) record.historyKey = policy.hashHistoryKey() ? sha256(historyKey) : historyKey;
        if (policy.includes("siteKey")) record.siteKey = siteKey;
        if (policy.includes("siteName")) record.siteName = siteName;
        if (policy.includes("vodId")) record.vodId = vodId;
        if (policy.includes("vodName")) record.vodName = vodName;
        if (policy.includes("vodPic")) record.vodPic = vodPic;
        if (policy.includes("flag")) record.flag = flag;
        if (policy.includes("sourceBindingKey")) record.sourceBindingKey = sourceBindingKey;
        if (policy.includes("episodeName")) record.episodeName = episodeName;
        if (policy.includes("episodeUrl")) record.episodeUrl = episodeUrl;
        if (policy.includes("episodeIndex")) record.episodeIndex = episodeIndex;
        if (policy.includes("mediaType")) record.mediaType = mediaType;
        if (policy.includes("tmdbId")) record.tmdbId = tmdbId;
        if (policy.includes("seasonNumber")) record.seasonNumber = seasonNumber;
        if (policy.includes("tmdbEpisodeNumber")) record.tmdbEpisodeNumber = tmdbEpisodeNumber;
        if (policy.includes("state")) record.state = state;
        if (policy.includes("positionMs")) record.positionMs = positionMs;
        if (policy.includes("durationMs")) record.durationMs = durationMs;
        if (policy.includes("progress")) record.progress = progress;
        if (policy.includes("speed")) record.speed = speed;
        if (policy.includes("speedOverride")) record.speedOverride = speedOverride;
        if (policy.includes("completed")) record.completed = completed;
        if (policy.includes("appVersion")) record.appVersion = appVersion;
        if (policy.includes("client")) record.client = client;
        if (policy.includes("clientKey")) record.clientKey = clientKey;
        return record;
    }

    public JsonObject toJson(PlaybackFieldPolicy policy) {
        JsonObject object = new JsonObject();
        boolean deletion = "playback.deleted".equals(event);
        if (policy.includes("schema")) object.addProperty("schema", schema);
        if (policy.includes("event") && !TextUtils.isEmpty(event)) object.addProperty("event", event);
        if (policy.includes("eventId") && !TextUtils.isEmpty(eventId)) object.addProperty("eventId", eventId);
        if (policy.includes("timestamp")) object.addProperty("timestamp", timestamp);
        if (policy.includes("scope") && !TextUtils.isEmpty(scope)) object.addProperty("scope", scope);
        if (policy.includes("deletedAt") && deletedAt > 0) object.addProperty("deletedAt", deletedAt);
        if (policy.includes("sessionId") && !TextUtils.isEmpty(sessionId)) object.addProperty("sessionId", sessionId);
        if (policy.includes("dedupeKey") && !TextUtils.isEmpty(dedupeKey)) object.addProperty("dedupeKey", dedupeKey);
        if (policy.includes("cid")) object.addProperty("cid", cid);
        if (policy.includes("configKey") && !TextUtils.isEmpty(configKey)) object.addProperty("configKey", configKey);
        if (policy.includes("configName") && !TextUtils.isEmpty(configName)) object.addProperty("configName", configName);
        if (policy.includes("historyKey")) object.addProperty("historyKey", policy.hashHistoryKey() ? sha256(historyKey) : historyKey);
        if (policy.includes("siteKey")) object.addProperty("siteKey", siteKey);
        if (policy.includes("siteName")) object.addProperty("siteName", siteName);
        if (policy.includes("vodId")) object.addProperty("vodId", vodId);
        if (policy.includes("vodName")) object.addProperty("vodName", vodName);
        if (policy.includes("vodPic")) object.addProperty("vodPic", vodPic);
        if (policy.includes("flag")) object.addProperty("flag", flag);
        if (policy.includes("sourceBindingKey")) object.addProperty("sourceBindingKey", sourceBindingKey);
        if (policy.includes("episodeName")) object.addProperty("episodeName", episodeName);
        if (policy.includes("episodeUrl")) object.addProperty("episodeUrl", episodeUrl);
        if (policy.includes("episodeIndex") && episodeIndex != null) object.addProperty("episodeIndex", episodeIndex);
        if (policy.includes("mediaType") && !TextUtils.isEmpty(mediaType)) object.addProperty("mediaType", mediaType);
        if (policy.includes("tmdbId") && tmdbId > 0) object.addProperty("tmdbId", tmdbId);
        if (policy.includes("seasonNumber") && seasonNumber >= 0) object.addProperty("seasonNumber", seasonNumber);
        if (!deletion && policy.includes("tmdbEpisodeNumber") && tmdbEpisodeNumber > 0) object.addProperty("tmdbEpisodeNumber", tmdbEpisodeNumber);
        if (!deletion && policy.includes("state")) object.addProperty("state", state);
        if (!deletion && policy.includes("positionMs")) object.addProperty("positionMs", positionMs);
        if (!deletion && policy.includes("durationMs")) object.addProperty("durationMs", durationMs);
        if (!deletion && policy.includes("progress")) object.addProperty("progress", progress);
        if (!deletion && policy.includes("speed")) object.addProperty("speed", speed);
        if (!deletion && policy.includes("speedOverride")) object.addProperty("speedOverride", speedOverride);
        if (!deletion && policy.includes("completed")) object.addProperty("completed", completed);
        if (policy.includes("appVersion")) object.addProperty("appVersion", appVersion);
        if (policy.includes("client")) object.addProperty("client", client);
        if (policy.includes("clientKey") && !TextUtils.isEmpty(clientKey)) object.addProperty("clientKey", clientKey);
        return object;
    }

    private PlaybackRecord copy() {
        PlaybackRecord record = new PlaybackRecord();
        record.schema = schema;
        record.event = event;
        record.eventId = eventId;
        record.timestamp = timestamp;
        record.scope = scope;
        record.deletedAt = deletedAt;
        record.sessionId = sessionId;
        record.dedupeKey = dedupeKey;
        record.cid = cid;
        record.configKey = configKey;
        record.configName = configName;
        record.historyKey = historyKey;
        record.siteKey = siteKey;
        record.siteName = siteName;
        record.vodId = vodId;
        record.vodName = vodName;
        record.vodPic = vodPic;
        record.flag = flag;
        record.sourceBindingKey = sourceBindingKey;
        record.episodeName = episodeName;
        record.episodeUrl = episodeUrl;
        record.episodeIndex = episodeIndex;
        record.mediaType = mediaType;
        record.tmdbId = tmdbId;
        record.seasonNumber = seasonNumber;
        record.tmdbEpisodeNumber = tmdbEpisodeNumber;
        record.state = state;
        record.positionMs = positionMs;
        record.durationMs = durationMs;
        record.progress = progress;
        record.speed = speed;
        record.speedOverride = speedOverride;
        record.completed = completed;
        record.appVersion = appVersion;
        record.client = client;
        record.clientKey = clientKey;
        return record;
    }

    private void clear() {
        schema = "";
        event = "";
        eventId = "";
        timestamp = 0;
        scope = "";
        deletedAt = 0;
        sessionId = "";
        dedupeKey = "";
        cid = 0;
        configKey = "";
        configName = "";
        historyKey = "";
        siteKey = "";
        siteName = "";
        vodId = "";
        vodName = "";
        vodPic = "";
        flag = "";
        sourceBindingKey = "";
        episodeName = "";
        episodeUrl = "";
        episodeIndex = null;
        mediaType = "";
        tmdbId = 0;
        seasonNumber = -1;
        tmdbEpisodeNumber = -1;
        state = "";
        positionMs = 0;
        durationMs = 0;
        progress = 0;
        speed = 0;
        speedOverride = false;
        completed = false;
        appVersion = "";
        client = "";
        clientKey = "";
    }

    private static long position(History history, PlayerManager player) {
        long value = safePosition(player);
        if (value > 0) return value;
        return validTime(history.getPosition()) ? Math.max(0, history.getPosition()) : 0;
    }

    private static long duration(History history, PlayerManager player) {
        long value = safeDuration(player);
        if (value > 0) return value;
        return validTime(history.getDuration()) ? Math.max(0, history.getDuration()) : 0;
    }

    private static float speed(History history, PlayerManager player) {
        try {
            return player == null || player.isReleased() ? Math.max(history.getSpeed(), 0.25f) : player.getSpeed();
        } catch (Exception e) {
            return Math.max(history.getSpeed(), 0.25f);
        }
    }

    private static String state(PlayerManager player) {
        try {
            if (player == null || player.isReleased()) return "idle";
            int state = player.getPlaybackState();
            if (state == Player.STATE_ENDED) return "ended";
            if (state == Player.STATE_BUFFERING) return "buffering";
            if (state == Player.STATE_IDLE) return "idle";
            return player.isPlaying() ? "playing" : "paused";
        } catch (Exception e) {
            return "idle";
        }
    }

    private static long safePosition(PlayerManager player) {
        try {
            if (player == null || player.isReleased()) return 0;
            long value = player.getPosition();
            return validTime(value) ? Math.max(0, value) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long safeDuration(PlayerManager player) {
        try {
            if (player == null || player.isReleased()) return 0;
            long value = player.getDuration();
            return validTime(value) ? Math.max(0, value) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean validTime(long value) {
        return value != C.TIME_UNSET && value >= 0;
    }

    private static double progress(long position, long duration) {
        if (duration <= 0) return 0;
        double value = Math.max(0d, Math.min(1d, (double) position / (double) duration));
        return Math.round(value * 10000d) / 10000d;
    }

    private static boolean completed(String state, long position, long duration) {
        if ("ended".equals(state)) return true;
        if (duration <= 0) return false;
        return position >= duration || progress(position, duration) >= 0.95d;
    }

    private static String siteName(String siteKey) {
        try {
            Site site = VodConfig.get().getSite(siteKey);
            return site == null || TextUtils.isEmpty(site.getName()) ? siteKey : site.getName();
        } catch (Exception e) {
            return siteKey;
        }
    }

    private static String part(String key, int index) {
        try {
            String[] parts = key.split(AppDatabase.SYMBOL);
            return parts.length > index ? parts[index] : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String dedupeKey(PlaybackRecord record) {
        return sha256(join(record.configKey, record.historyKey, record.siteKey, record.vodId,
                record.vodName, record.flag, record.sourceBindingKey, record.episodeName, record.episodeUrl,
                record.mediaType, String.valueOf(record.tmdbId),
                String.valueOf(record.seasonNumber), String.valueOf(record.tmdbEpisodeNumber)));
    }

    private static String clientKey() {
        try {
            String uuid = Device.get().getUuid();
            return TextUtils.isEmpty(uuid) ? "" : sha256(uuid);
        } catch (Exception e) {
            return "";
        }
    }

    private static String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) builder.append(safe(value).trim().toLowerCase(Locale.ROOT)).append('\n');
        return builder.toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) builder.append(String.format(Locale.ROOT, "%02x", b));
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
