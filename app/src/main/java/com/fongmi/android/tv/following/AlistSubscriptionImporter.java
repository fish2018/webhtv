package com.fongmi.android.tv.following;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class AlistSubscriptionImporter {

    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Gson GSON = new Gson();

    private AlistSubscriptionImporter() {
    }

    public static List<Candidate> fetch(String baseUrl, String token) throws IOException {
        if (!FollowingSettings.isServerImportEnabled()) throw new IOException("服务端订阅导入未启用");
        String url = normalizeBase(baseUrl);
        if (TextUtils.isEmpty(url)) throw new IOException("服务端地址为空");
        Request.Builder request = new Request.Builder().url(url + "/api/media-subscriptions").get();
        if (!TextUtils.isEmpty(token)) request.header("Authorization", "Bearer " + token.trim());
        try (Response response = CLIENT.newCall(request.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code());
            return parse(response.body().string());
        }
    }

    public static List<Candidate> parse(String json) {
        List<Candidate> result = new ArrayList<>();
        JsonElement root = GSON.fromJson(json, JsonElement.class);
        JsonArray array = null;
        if (root != null && root.isJsonArray()) array = root.getAsJsonArray();
        else if (root != null && root.isJsonObject()) {
            JsonElement data = root.getAsJsonObject().get("data");
            if (data != null && data.isJsonArray()) array = data.getAsJsonArray();
            else if (data != null && data.isJsonObject()) {
                JsonElement list = data.getAsJsonObject().get("list");
                if (list != null && list.isJsonArray()) array = list.getAsJsonArray();
            }
        }
        if (array == null) return result;
        for (JsonElement element : array) {
            Candidate candidate = parseCandidate(element);
            if (candidate != null) result.add(candidate);
        }
        return result;
    }

    public static ImportResult importCandidates(List<Candidate> candidates) {
        ImportResult result = new ImportResult();
        if (candidates == null) return result;
        long now = System.currentTimeMillis();
        for (Candidate candidate : candidates) {
            if (candidate == null || TextUtils.isEmpty(candidate.title)) continue;
            Following existing = findExisting(candidate);
            if (existing != null) {
                updateExisting(existing, candidate, now);
                result.updated++;
                continue;
            }
            Following item = toFollowing(candidate, now);
            FollowingSource source = toSource(item, candidate, now);
            FollowingStore.saveNew(item, source);
            result.created++;
        }
        return result;
    }

    static Candidate parseCandidate(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        String title = string(object, "name", "title");
        if (TextUtils.isEmpty(title)) return null;
        Candidate candidate = new Candidate();
        candidate.title = title;
        candidate.season = integer(object, "season", "seasonNumber");
        candidate.tmdbId = parseProviderId(object, "tmdb");
        candidate.tmdbMediaType = normalizeProvider(object, "tv");
        candidate.doubanId = TextUtils.isEmpty(string(object, "doubanId")) ? integer(object, "douban_id") : integer(object, "doubanId");
        candidate.currentEpisodes = integer(object, "currentEpisodes", "current_episodes");
        candidate.status = string(object, "status");
        return candidate;
    }

    static Following findExisting(Candidate candidate) {
        if (candidate.tmdbId > 0) {
            Following item = FollowingStore.findByTmdb(
                    new com.fongmi.android.tv.bean.TmdbItem(candidate.tmdbId, candidate.mediaType(), candidate.title,
                            "", "", "", ""), candidate.season);
            if (item != null) return item;
        }
        for (Following item : FollowingStore.list()) {
            if (item.trackedSeason != candidate.season) continue;
            if (candidate.tmdbId > 0 && item.tmdbId == candidate.tmdbId) return item;
            if (titleMatches(item.vodName, candidate.title)) return item;
        }
        return null;
    }

    static void updateExisting(Following item, Candidate candidate, long now) {
        if (candidate.currentEpisodes > 0) {
            FollowingSource source = FollowingStore.preferredSource(item.identityKey);
            if (source == null) source = toSource(item, candidate, now);
            source.playableSeason = Math.max(source.playableSeason, candidate.season);
            source.playableEpisode = Math.max(source.playableEpisode, candidate.currentEpisodes);
            source.playableCount = Math.max(source.playableCount, candidate.currentEpisodes);
            source.lastProbeAt = now;
            source.lastError = "";
            FollowingStore.updateSource(source);
        }
        FollowingUpdatePolicy.refreshDerived(item, now);
        FollowingStore.update(item);
    }

    static Following toFollowing(Candidate candidate, long now) {
        String seriesKey = candidate.tmdbId > 0
                ? "tmdb:" + FollowingIdentity.normalizeMediaType(candidate.mediaType()) + ":" + candidate.tmdbId
                : "alist:" + FollowingIdentity.normalize(candidate.title) + ":s" + candidate.season;
        Following item = new Following();
        item.identityKey = FollowingIdentity.identityKey(seriesKey, candidate.season);
        item.seriesKey = seriesKey;
        item.cid = 0;
        item.siteKey = "";
        item.vodId = "";
        item.vodName = candidate.title;
        item.mediaType = candidate.mediaType();
        item.tmdbId = candidate.tmdbId;
        item.trackedSeason = candidate.season;
        item.trackedEpisode = candidate.currentEpisodes;
        item.seasonReleasedEpisodes = candidate.currentEpisodes;
        item.latestReleasedSeason = candidate.season;
        item.latestReleasedEpisode = candidate.currentEpisodes;
        item.officialStatus = FollowingMetadataSnapshot.normalizeStatus(candidate.status);
        item.notifyEnabled = FollowingSettings.isNotificationsEnabled();
        item.enabled = true;
        item.createdAt = now;
        item.updatedAt = now;
        FollowingUpdatePolicy.initializeNew(item, candidate.currentEpisodes, now);
        item.nextCheckAt = FollowingSchedulePolicy.nextCheckAt(now, item.officialStatus, 0);
        return item;
    }

    static FollowingSource toSource(Following item, Candidate candidate, long now) {
        FollowingSource source = new FollowingSource();
        source.followingKey = item.identityKey;
        source.cid = 0;
        source.siteKey = "";
        source.vodId = "";
        source.vodName = candidate.title;
        source.playableSeason = candidate.season;
        source.playableEpisode = candidate.currentEpisodes;
        source.playableCount = candidate.currentEpisodes;
        source.preferred = true;
        source.lastProbeAt = now;
        return source;
    }

    private static String normalizeBase(String value) {
        String base = value == null ? "" : value.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!base.startsWith("http://") && !base.startsWith("https://")) return "";
        HttpUrl parsed = HttpUrl.parse(base);
        return parsed == null ? "" : parsed.toString().replaceAll("/$", "");
    }

    private static int parseProviderId(JsonObject object, String provider) {
        String metaProvider = string(object, "metaProvider", "meta_provider").toLowerCase(Locale.ROOT);
        if (provider.equals(metaProvider)) return integer(object, "metaId", "meta_id");
        int direct = integer(object, provider + "Id");
        return direct > 0 ? direct : integer(object, provider + "_id");
    }

    private static String normalizeProvider(JsonObject object, String fallback) {
        String mediaType = string(object, "mediaType", "media_type");
        if (!TextUtils.isEmpty(mediaType)) return FollowingIdentity.normalizeMediaType(mediaType);
        String provider = string(object, "metaProvider", "meta_provider");
        return "movie".equalsIgnoreCase(provider) ? "movie" : fallback;
    }

    private static boolean titleMatches(String left, String right) {
        String a = left == null ? "" : left.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        String b = right == null ? "" : right.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return !a.isEmpty() && (a.equals(b) || a.contains(b) || b.contains(a));
    }

    private static int integer(JsonObject object, String... keys) {
        for (String key : keys) {
            try {
                JsonElement value = object.get(key);
                if (value != null && !value.isJsonNull()) return Math.max(0, value.getAsInt());
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static String string(JsonObject object, String... keys) {
        for (String key : keys) {
            try {
                JsonElement value = object.get(key);
                if (value != null && !value.isJsonNull()) return value.getAsString().trim();
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    public static final class Candidate {
        public String title = "";
        public int season;
        public int tmdbId;
        public String tmdbMediaType = "tv";
        public int doubanId;
        public int currentEpisodes;
        public String status = "";

        public String mediaType() {
            return FollowingIdentity.normalizeMediaType(tmdbMediaType);
        }
    }

    public static final class ImportResult {
        public int created;
        public int updated;
    }
}
