package com.fongmi.android.tv.service;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.helper.TmdbMatcher;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AiRecommendationService {

    private static final int MAX_CONTEXT_ITEMS = 24;
    private static final int MIN_RECOMMENDATION_COUNT = 12;
    private static final int DEFAULT_RECOMMENDATION_COUNT = 16;
    private static final int MAX_RECOMMENDATION_COUNT = 24;
    private static final int CONNECT_TIMEOUT_SECONDS = 15;
    private static final int READ_TIMEOUT_SECONDS = 60;
    private static final int CALL_TIMEOUT_SECONDS = 75;
    private static final int MAX_RECOMMENDATION_ATTEMPTS = 2;
    private static final String RESOLVED_CACHE_SUFFIX = ".items.v2.json";
    private static final String LATEST_CACHE_PREFIX = "latest_";
    private static final String DISPLAY_CACHE_PREFIX = "display_";
    private static final Pattern HISTORY_EPISODE_LABEL = Pattern.compile("(?:第\\s*\\d+\\s*[集期话]|\\b(?:ep(?:isode)?|e)\\s*\\.?\\s*\\d+\\b|\\bs\\d+e\\d+\\b)", Pattern.CASE_INSENSITIVE);

    private final TmdbService tmdbService;
    private final TmdbConfig tmdbConfig;
    private final TmdbMatcher tmdbMatcher;
    private final AiConfig config;

    public AiRecommendationService(TmdbService tmdbService, TmdbConfig tmdbConfig) {
        this.tmdbService = tmdbService == null ? new TmdbService() : tmdbService;
        this.tmdbConfig = tmdbConfig == null ? new TmdbConfig() : tmdbConfig;
        this.tmdbMatcher = new TmdbMatcher(this.tmdbService, this.tmdbConfig);
        this.config = AiConfig.objectFrom(Setting.getAiConfig());
    }

    private AiRecommendationService(TmdbService tmdbService, TmdbConfig tmdbConfig, AiConfig config) {
        this.tmdbService = tmdbService == null ? new TmdbService() : tmdbService;
        this.tmdbConfig = tmdbConfig == null ? new TmdbConfig() : tmdbConfig;
        this.tmdbMatcher = new TmdbMatcher(this.tmdbService, this.tmdbConfig);
        this.config = config == null ? new AiConfig().sanitize() : config.sanitize();
    }

    public PersonalRecommendationService.RecommendationPage load(@Nullable Vod currentVod, @Nullable String currentTitle, @Nullable String historyFingerprint, int pageSize) {
        String fingerprint = fingerprint(currentTitle, historyFingerprint, Setting.getKeyword(), config);
        if (!config.isReady()) return PersonalRecommendationService.RecommendationPage.empty(fingerprint);
        CachedPage cached = loadCached(currentTitle, historyFingerprint, pageSize);
        if (cached.isExact() && cached.isResolved()) return cached.getPage();
        List<AiRecommendation> recommendations = readCache(fingerprint);
        if (recommendations.isEmpty()) {
            recommendations = requestRecommendations(currentVod, currentTitle);
            if (!recommendations.isEmpty()) writeCache(fingerprint, currentTitle, recommendations);
        }
        if (recommendations.isEmpty()) return cached.hasItems() ? cached.getPage() : PersonalRecommendationService.RecommendationPage.empty(fingerprint);
        return resolveAndCache(recommendations, fingerprint, currentTitle);
    }

    public CachedPage loadCached(@Nullable String currentTitle, @Nullable String historyFingerprint, int pageSize) {
        String fingerprint = fingerprint(currentTitle, historyFingerprint, Setting.getKeyword(), config);
        if (!config.isReady()) return CachedPage.empty(fingerprint);
        List<TmdbItem> resolved = readResolvedCache(resolvedCacheFile(fingerprint));
        if (!resolved.isEmpty()) return new CachedPage(pageItems(resolved, fingerprint), true, true);
        List<AiRecommendation> recommendations = readCache(fingerprint);
        if (!recommendations.isEmpty()) return new CachedPage(pageFallbackRecommendations(recommendations, fingerprint), true, false);

        for (String latestKey : latestCacheKeysForRead(currentTitle, config)) {
            resolved = readResolvedCache(latestResolvedCacheFile(latestKey));
            if (!resolved.isEmpty()) return new CachedPage(pageItems(resolved, fingerprint), false, true);
            recommendations = readCache(latestCacheFile(latestKey));
            if (!recommendations.isEmpty()) return new CachedPage(pageFallbackRecommendations(recommendations, fingerprint), false, false);
        }
        String displayKey = latestDisplayCacheKey(currentTitle, config);
        resolved = readResolvedCache(displayResolvedCacheFile(displayKey));
        if (!resolved.isEmpty()) return new CachedPage(pageItems(resolved, fingerprint), false, true);
        recommendations = readCache(displayCacheFile(displayKey));
        if (!recommendations.isEmpty()) return new CachedPage(pageFallbackRecommendations(recommendations, fingerprint), false, false);
        resolved = readResolvedCache(latestAnyResolvedCacheFile());
        if (!resolved.isEmpty()) return new CachedPage(pageItems(resolved, fingerprint), false, true);
        recommendations = readCache(latestAnyRecommendationCacheFile());
        if (!recommendations.isEmpty()) return new CachedPage(pageFallbackRecommendations(recommendations, fingerprint), false, false);
        return CachedPage.empty(fingerprint);
    }

    public PersonalRecommendationService.RecommendationPage resolveCached(@Nullable String currentTitle, @Nullable String historyFingerprint, int pageSize) {
        String fingerprint = fingerprint(currentTitle, historyFingerprint, Setting.getKeyword(), config);
        if (!config.isReady()) return PersonalRecommendationService.RecommendationPage.empty(fingerprint);
        List<AiRecommendation> recommendations = readCache(fingerprint);
        if (recommendations.isEmpty()) return PersonalRecommendationService.RecommendationPage.empty(fingerprint);
        return resolveAndCache(recommendations, fingerprint, currentTitle);
    }

    public PersonalRecommendationService.RecommendationPage refresh(@Nullable Vod currentVod, @Nullable String currentTitle, @Nullable String historyFingerprint, int pageSize) {
        String fingerprint = fingerprint(currentTitle, historyFingerprint, Setting.getKeyword(), config);
        if (!config.isReady()) return PersonalRecommendationService.RecommendationPage.empty(fingerprint);
        List<AiRecommendation> recommendations = requestRecommendations(currentVod, currentTitle);
        if (recommendations.isEmpty()) return PersonalRecommendationService.RecommendationPage.empty(fingerprint);
        writeCache(fingerprint, currentTitle, recommendations);
        return resolveAndCache(recommendations, fingerprint, currentTitle);
    }

    static String fingerprint(String currentTitle, String historyFingerprint, String searchRecords, AiConfig config) {
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        String value = "v4|"
                + Objects.toString(safe.getProtocol(), "") + "|"
                + Objects.toString(safe.getEndpoint(), "") + "|"
                + Objects.toString(safe.getModel(), "") + "|"
                + Objects.toString(safe.getCustomUserAgent(), "") + "|"
                + Objects.toString(safe.getRecommendPrompt(), "") + "|"
                + Objects.toString(historyFingerprint, "") + "|"
                + normalizeSearchRecords(searchRecords);
        return md5(value);
    }

    static String latestCacheKey(String currentTitle, AiConfig config) {
        return latestCacheKey(currentTitle, config, null);
    }

    private static String latestCacheKey(String currentTitle, AiConfig config, @Nullable String promptOverride) {
        if (isBlank(currentTitle)) return "";
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        String value = "latest-v1|"
                + Objects.toString(safe.getProtocol(), "") + "|"
                + Objects.toString(safe.getEndpoint(), "") + "|"
                + Objects.toString(safe.getModel(), "") + "|"
                + Objects.toString(safe.getCustomUserAgent(), "") + "|"
                + Objects.toString(promptOverride == null ? safe.getRecommendPrompt() : promptOverride, "") + "|"
                + PersonalRecommendationService.normalizeTitle(currentTitle);
        return md5(value);
    }

    static String latestDisplayCacheKey(String currentTitle, AiConfig config) {
        if (isBlank(currentTitle)) return "";
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        String value = "display-v1|"
                + Objects.toString(safe.getProtocol(), "") + "|"
                + Objects.toString(safe.getEndpoint(), "") + "|"
                + Objects.toString(safe.getModel(), "") + "|"
                + Objects.toString(safe.getCustomUserAgent(), "") + "|"
                + PersonalRecommendationService.normalizeTitle(currentTitle);
        return md5(value);
    }

    static List<String> latestCacheKeysForRead(String currentTitle, AiConfig config) {
        List<String> keys = new ArrayList<>();
        String current = latestCacheKey(currentTitle, config);
        if (!isBlank(current)) keys.add(current);
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        if (!safe.isRecommendPromptCustom()) {
            for (String prompt : AiConfig.systemRecommendPromptsForCache()) {
                String key = latestCacheKey(currentTitle, safe, prompt);
                if (!isBlank(key) && !keys.contains(key)) keys.add(key);
            }
        }
        return keys;
    }

    static List<AiRecommendation> parseRecommendations(String text) {
        List<AiRecommendation> items = new ArrayList<>();
        String json = extractJson(text);
        if (isBlank(json)) return items;
        try {
            JsonElement element = JsonParser.parseString(json);
            JsonArray array;
            if (element.isJsonArray()) {
                array = element.getAsJsonArray();
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                array = firstArray(object, "items", "recommendations", "results", "data", "list", "movies", "shows", "titles");
            } else {
                array = new JsonArray();
            }
            for (JsonElement item : array) {
                if (!item.isJsonObject()) continue;
                AiRecommendation recommendation = AiRecommendation.from(item.getAsJsonObject());
                if (recommendation != null) items.add(recommendation);
            }
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
        return items;
    }

    static List<TmdbItem> parseResolvedItems(String text) {
        List<TmdbItem> items = new ArrayList<>();
        String json = extractJson(text);
        if (isBlank(json)) return items;
        try {
            JsonElement element = JsonParser.parseString(json);
            JsonArray array;
            if (element.isJsonArray()) {
                array = element.getAsJsonArray();
            } else if (element.isJsonObject()) {
                array = firstArray(element.getAsJsonObject(), "items", "recommendations", "results", "data", "list", "movies", "shows", "titles");
            } else {
                array = new JsonArray();
            }
            for (JsonElement item : array) {
                if (!item.isJsonObject()) continue;
                TmdbItem resolved = tmdbItemFromJson(item.getAsJsonObject());
                if (resolved != null && !isBlank(resolved.getTitle())) items.add(resolved);
            }
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
        return items;
    }

    static JsonObject tmdbItemToJson(TmdbItem item) {
        JsonObject object = new JsonObject();
        if (item == null) return object;
        object.addProperty("tmdbId", item.getTmdbId());
        object.addProperty("mediaType", item.getMediaType());
        object.addProperty("title", item.getTitle());
        object.addProperty("subtitle", item.getSubtitle());
        object.addProperty("overview", item.getOverview());
        object.addProperty("recommendationReason", item.getRecommendationReason());
        object.addProperty("posterUrl", item.getPosterUrl());
        object.addProperty("backdropUrl", item.getBackdropUrl());
        object.addProperty("credit", item.getCredit());
        object.addProperty("rating", item.getRating());
        object.addProperty("tmdbRating", item.getTmdbRating());
        object.addProperty("doubanRating", item.getDoubanRating());
        object.addProperty("originalLanguage", item.getOriginalLanguage());
        object.addProperty("originCountry", item.getOriginCountry());
        object.addProperty("department", item.getDepartment());
        JsonArray genres = new JsonArray();
        for (Integer genreId : item.getGenreIds()) if (genreId != null) genres.add(genreId);
        object.add("genreIds", genres);
        return object;
    }

    private static TmdbItem tmdbItemFromJson(JsonObject object) {
        if (object == null) return null;
        String title = firstString(object, "title", "name", "vodName");
        if (isBlank(title)) return null;
        String mediaType = firstString(object, "mediaType", "type", "category");
        if (!"tv".equals(mediaType)) mediaType = "movie";
        List<Integer> genreIds = new ArrayList<>();
        for (JsonElement element : array(object, "genreIds")) {
            try {
                if (element != null && element.isJsonPrimitive()) genreIds.add(element.getAsInt());
            } catch (Throwable ignored) {
            }
        }
        double rating = doubleValue(object, "rating", "voteAverage", "vote_average");
        double tmdbRating = doubleValue(object, "tmdbRating", "tmdb_rating");
        double doubanRating = doubleValue(object, "doubanRating", "douban_rating");
        int tmdbId = intValue(object, "tmdbId", "id");
        if (rating > 0) {
            if (tmdbRating <= 0 && tmdbId > 0) tmdbRating = rating;
            if (doubanRating <= 0 && tmdbId <= 0) doubanRating = rating;
        }
        String overview = firstString(object, "overview");
        String recommendationReason = firstString(object, "recommendationReason", "recommendation_reason", "reason", "desc");
        boolean hasSeparatedReason = object.has("recommendationReason") || object.has("recommendation_reason") || object.has("reason") || object.has("desc");
        if (!hasSeparatedReason && !isBlank(overview)) {
            // Older resolved-AI caches stored the recommendation reason in overview and lost the synopsis.
            recommendationReason = overview;
            overview = "";
        }
        return new TmdbItem(
                tmdbId,
                mediaType,
                title,
                firstString(object, "subtitle", "subTitle"),
                overview,
                firstString(object, "posterUrl", "poster", "pic", "img"),
                firstString(object, "backdropUrl", "backdrop", "background"),
                firstString(object, "credit"),
                rating,
                firstString(object, "originalLanguage", "original_language"),
                firstString(object, "originCountry", "origin_country"),
                genreIds,
                firstString(object, "department"),
                tmdbRating,
                doubanRating,
                recommendationReason
        );
    }

    static List<AiRecommendation> parseResponseRecommendations(String body, AiConfig config) {
        List<AiRecommendation> items = parseRecommendations(AiCompletionClient.extractCompletionText(body, config));
        if (!items.isEmpty()) return items;
        items = parseRecommendations(body);
        return items.isEmpty() ? parseSseRecommendations(body, config) : items;
    }

    static boolean shouldRetryRecommendationRequest(int code, boolean parseFailed, Throwable error) {
        if (error != null) return !(error instanceof InterruptedException);
        if (parseFailed) return true;
        return code == 408 || code == 409 || code == 425 || code == 429 || code >= 500;
    }

    private List<AiRecommendation> requestRecommendations(Vod currentVod, String currentTitle) {
        String basePrompt = buildPrompt(currentVod, currentTitle);
        String prompt = basePrompt;
        for (int attempt = 1; attempt <= MAX_RECOMMENDATION_ATTEMPTS; attempt++) {
            try {
                AiCompletionClient.RequestSpec spec = AiCompletionClient.requestSpec(config, prompt);
                AiDebugLog.request("ai-rec", "recommendation", config, spec, "attempt=" + attempt + "/" + MAX_RECOMMENDATION_ATTEMPTS);
                Request request = AiCompletionClient.buildRequest(spec);
                long start = System.currentTimeMillis();
                try (Response response = client().newCall(request).execute()) {
                    String body = response.body() == null ? "" : response.body().string();
                    long cost = System.currentTimeMillis() - start;
                    if (!response.isSuccessful()) {
                        AiDebugLog.response("ai-rec", "recommendation", response.code(), cost, body, "attempt=" + attempt + "/" + MAX_RECOMMENDATION_ATTEMPTS + " success=false");
                        SpiderDebug.log("ai-rec", "request failed attempt=%d/%d code=%d cost=%dms body=%s", attempt, MAX_RECOMMENDATION_ATTEMPTS, response.code(), cost, excerpt(body));
                        if (!shouldRetryRecommendationRequest(response.code(), false, null)) break;
                    } else {
                        List<AiRecommendation> recommendations = parseResponseRecommendations(body, config);
                        AiDebugLog.response("ai-rec", "recommendation", response.code(), cost, body, "attempt=" + attempt + "/" + MAX_RECOMMENDATION_ATTEMPTS + " parsed=" + recommendations.size());
                        if (!recommendations.isEmpty()) {
                            SpiderDebug.log("ai-rec", "request success attempt=%d/%d cost=%dms count=%d", attempt, MAX_RECOMMENDATION_ATTEMPTS, cost, recommendations.size());
                            return recommendations;
                        }
                        SpiderDebug.log("ai-rec", "request parse empty attempt=%d/%d cost=%dms body=%s", attempt, MAX_RECOMMENDATION_ATTEMPTS, cost, excerpt(body));
                        if (!shouldRetryRecommendationRequest(response.code(), true, null)) break;
                        prompt = retryPrompt(basePrompt);
                    }
                }
            } catch (Throwable e) {
                AiDebugLog.error("ai-rec", "recommendation", 0, e, "attempt=" + attempt + "/" + MAX_RECOMMENDATION_ATTEMPTS);
                SpiderDebug.log("ai-rec", "request failed attempt=%d/%d error=%s", attempt, MAX_RECOMMENDATION_ATTEMPTS, e.getMessage());
                if (!shouldRetryRecommendationRequest(0, false, e)) break;
            }
            sleepBeforeRetry(attempt);
        }
        return new ArrayList<>();
    }

    private OkHttpClient client() {
        return com.github.catvod.net.OkHttp.client().newBuilder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    private String buildPrompt(Vod currentVod, String currentTitle) {
        return buildPrompt(config, currentContextItem(currentVod, currentTitle), historyItems(currentVod, currentTitle), searchKeywords(), notInterestedItems());
    }

    static String buildPrompt(AiConfig config, @Nullable JsonObject currentItem, List<JsonObject> playHistory, List<String> searchKeywords) {
        return buildPrompt(config, currentItem, playHistory, searchKeywords, new ArrayList<>());
    }

    static String buildPrompt(AiConfig config, @Nullable JsonObject currentItem, List<JsonObject> playHistory, List<String> searchKeywords, List<JsonObject> notInterested) {
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        JsonObject context = new JsonObject();
        JsonObject policy = new JsonObject();
        policy.addProperty("minItems", MIN_RECOMMENDATION_COUNT);
        policy.addProperty("defaultItems", DEFAULT_RECOMMENDATION_COUNT);
        policy.addProperty("maxItems", MAX_RECOMMENDATION_COUNT);
        policy.addProperty("avoidCurrentAndHistory", true);
        policy.addProperty("avoidNotInterested", true);
        policy.addProperty("outputMediaType", "movie 或 tv");
        context.add("recommendationPolicy", policy);
        context.add("currentItem", currentItem == null ? new JsonObject() : currentItem);
        context.add("playHistory", objectArray(playHistory));
        context.add("searchHistory", searchArray(searchKeywords));
        context.add("notInterested", objectArray(notInterested));

        StringBuilder builder = new StringBuilder();
        builder.append(safe.getRecommendPrompt()).append("\n\n");
        builder.append("推荐硬性要求:\n");
        builder.append("- items 数量必须在 ").append(MIN_RECOMMENDATION_COUNT).append('-').append(MAX_RECOMMENDATION_COUNT).append(" 部之间，默认 ").append(DEFAULT_RECOMMENDATION_COUNT).append(" 部。\n");
        builder.append("- 不要推荐 currentItem 或 playHistory 中已经出现的作品、别名或明显同名混淆项。\n");
        builder.append("- 不要推荐 notInterested 中用户明确标记为不感兴趣的作品、别名、翻拍版或明显同名混淆项。\n");
        builder.append("- searchHistory 只代表兴趣意向，不等同于已观看；重复或靠前搜索词权重更高。\n");
        builder.append("- notInterested 是明确负反馈，优先级高于相似题材、演员、导演和搜索兴趣。\n");
        builder.append("- 如果 playHistory 中包含异常标题、合集或非影视内容，请降低权重。\n");
        builder.append("- currentItem 代表即时兴趣，playHistory 代表长期偏好；不要只围绕当前作品推荐。\n");
        builder.append("- 同一题材、演员、导演、地区或年代重复出现在多条高完成率历史时，视为更强偏好。\n");
        builder.append("- 高完成率、较长观看时长和较新记录权重更高，低完成率或短暂播放权重更低。\n");
        builder.append("- 单条历史中的人员或导演只作为弱信号，空字段表示未知而不是负面偏好。\n");
        builder.append("- 推荐理由必须结合用户偏好，不能只复述剧情。\n\n");
        appendFieldGuide(builder);
        builder.append("请基于下面结构化 JSON 分析，字段为空表示本地暂无该信息:\n");
        builder.append(context).append("\n\n");
        builder.append("最终只返回严格 JSON: {\"items\":[{\"title\":\"片名\",\"year\":2024,\"mediaType\":\"movie 或 tv\",\"reason\":\"一句推荐理由\"}]}");
        return builder.toString();
    }

    private static void appendFieldGuide(StringBuilder builder) {
        builder.append("字段说明:\n");
        builder.append("- recommendationPolicy: 推荐数量、去重和输出类型约束。\n");
        builder.append("- currentItem: 用户当前正在查看或播放的作品，代表即时兴趣。\n");
        builder.append("- playHistory: 用户最近播放过的作品，越靠前越近，观看深度越高权重越高。\n");
        builder.append("- searchHistory: 用户搜索词，代表潜在兴趣，不等同于已观看。\n");
        builder.append("- notInterested: 用户明确标记为不感兴趣的作品，必须作为负向约束排除。\n");
        builder.append("- title/year/mediaType/country/language: 作品基础信息，用于判断题材、地区、语言和年代偏好。\n");
        builder.append("- genres/tags/statusOrRemarks/description: 类型、标签、更新状态或简介，用于判断内容气质。\n");
        builder.append("- actors/director: 演员与导演/主创；在多条高兴趣历史中重复出现时代表稳定人员偏好。\n");
        builder.append("- episodeCount/episodeName/episodeNumber: 总集数、历史观看集名和集号，用于判断剧集观看偏好。\n");
        builder.append("- watchedMinutes/durationMinutes: 用户在单集或单条历史上的已看时长和总时长。\n");
        builder.append("- completionRate: 单集观看完成比例，接近 1 表示兴趣更强。\n");
        builder.append("- lastWatchedAt: 最近观看时间，越新权重越高。\n\n");
    }

    private static String retryPrompt(String prompt) {
        return Objects.toString(prompt, "")
                + "\n\n上一次响应没有解析到可用推荐。请重新返回严格 JSON，禁止解释、Markdown 和额外文本："
                + "{\"items\":[{\"title\":\"片名\",\"year\":2024,\"mediaType\":\"movie 或 tv\",\"reason\":\"一句推荐理由\"}]}";
    }

    private static void sleepBeforeRetry(int attempt) {
        if (attempt >= MAX_RECOMMENDATION_ATTEMPTS) return;
        try {
            Thread.sleep(Math.min(1200, 400L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonObject currentContextItem(Vod currentVod, String currentTitle) {
        JsonObject object = new JsonObject();
        String title = !isBlank(currentTitle) ? currentTitle : currentVod == null ? "" : currentVod.getName();
        addString(object, "title", title);
        if (currentVod == null) return object;
        int episodeCount = episodeCount(currentVod);
        addString(object, "year", currentVod.getYear());
        addString(object, "mediaType", inferMediaType(currentVod.getTypeName(), episodeCount));
        addString(object, "country", currentVod.getArea());
        addString(object, "language", inferLanguage(currentVod.getArea()));
        addStringArray(object, "genres", currentVod.getTypeName());
        addStringArray(object, "tags", currentVod.getTag());
        addString(object, "statusOrRemarks", currentVod.getRemarks());
        addString(object, "director", currentVod.getDirector());
        addPeopleArray(object, "actors", currentVod.getActor());
        addPositiveInt(object, "episodeCount", episodeCount);
        addString(object, "description", limit(currentVod.getContent(), 220));
        return object;
    }

    private List<JsonObject> historyItems(Vod currentVod, String currentTitle) {
        List<JsonObject> items = new ArrayList<>();
        String current = !isBlank(currentTitle) ? currentTitle : currentVod == null ? "" : currentVod.getName();
        try {
            for (History history : selectHistoryContext(History.getAll(), current)) items.add(historyContextItem(history));
        } catch (Throwable e) {
            SpiderDebug.log("ai-rec", "history read failed: %s", e.getMessage());
        }
        return items;
    }

    private List<JsonObject> notInterestedItems() {
        List<JsonObject> items = new ArrayList<>();
        for (RecommendationFeedbackStore.Entry entry : RecommendationFeedbackStore.get()) {
            JsonObject item = new JsonObject();
            addString(item, "title", entry.getTitle());
            addString(item, "mediaType", entry.getMediaType());
            addPositiveInt(item, "year", entry.getYear());
            addString(item, "source", entry.getSource());
            items.add(item);
            if (items.size() >= MAX_CONTEXT_ITEMS) break;
        }
        return items;
    }

    static JsonObject historyContextItem(History history) {
        JsonObject object = new JsonObject();
        int episodeNumber = extractNumber(history.getVodRemarks());
        addString(object, "title", history.getVodName());
        addString(object, "year", history.getYear());
        addString(object, "mediaType", inferHistoryMediaType(history.getTypeName(), history.getVodRemarks()));
        addString(object, "country", history.getArea());
        addString(object, "language", inferLanguage(history.getArea()));
        addStringArray(object, "genres", history.getTypeName());
        addString(object, "director", history.getDirector());
        addPeopleArray(object, "actors", history.getActor());
        addString(object, "episodeName", history.getVodRemarks());
        addPositiveInt(object, "episodeNumber", episodeNumber);
        long position = history.getPosition();
        long duration = history.getDuration();
        if (position > 0) object.addProperty("watchedMinutes", TimeUnit.MILLISECONDS.toMinutes(position));
        if (duration > 0) object.addProperty("durationMinutes", TimeUnit.MILLISECONDS.toMinutes(duration));
        if (position > 0 && duration > 0) object.addProperty("completionRate", Math.min(1.0, Math.round((position * 100.0 / duration)) / 100.0));
        if (history.getCreateTime() > 0) addString(object, "lastWatchedAt", formatTime(history.getCreateTime()));
        return object;
    }

    static String historyMetadataFingerprint(List<History> histories) {
        return historyMetadataFingerprint(histories, "");
    }

    static String historyMetadataFingerprint(List<History> histories, String currentTitle) {
        JsonArray items = new JsonArray();
        for (History history : selectHistoryContext(histories, currentTitle)) {
            JsonObject item = new JsonObject();
            addString(item, "title", history.getVodName());
            addString(item, "typeName", history.getTypeName());
            addString(item, "mediaType", inferHistoryMediaType(history.getTypeName(), history.getVodRemarks()));
            addString(item, "area", history.getArea());
            addString(item, "actor", history.getActor());
            addString(item, "director", history.getDirector());
            addString(item, "year", history.getYear());
            items.add(item);
        }
        return md5("history-metadata-v1|" + items);
    }

    static List<History> selectHistoryContext(List<History> histories, String currentTitle) {
        List<History> selected = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        String normalizedCurrent = PersonalRecommendationService.normalizeTitle(currentTitle);
        if (histories == null) return selected;
        for (History history : PersonalRecommendationService.historyByWatchTime(histories)) {
            if (history == null || isBlank(history.getVodName())) continue;
            String normalizedTitle = PersonalRecommendationService.normalizeTitle(history.getVodName());
            if (normalizedTitle.equals(normalizedCurrent)) continue;
            if (containsNormalized(titles, history.getVodName())) continue;
            titles.add(history.getVodName());
            selected.add(history);
            if (selected.size() >= MAX_CONTEXT_ITEMS) break;
        }
        return selected;
    }

    private List<String> searchKeywords() {
        List<String> keywords = new ArrayList<>();
        try {
            JsonElement element = JsonParser.parseString(Setting.getKeyword());
            if (element != null && element.isJsonArray()) {
                for (JsonElement item : element.getAsJsonArray()) {
                    if (!item.isJsonPrimitive()) continue;
                    addUnique(keywords, item.getAsString());
                    if (keywords.size() >= MAX_CONTEXT_ITEMS) break;
                }
            }
        } catch (Throwable ignored) {
        }
        return keywords;
    }

    private static JsonArray objectArray(List<JsonObject> values) {
        JsonArray array = new JsonArray();
        if (values == null) return array;
        for (JsonObject value : values) if (value != null) array.add(value);
        return array;
    }

    private static JsonArray searchArray(List<String> keywords) {
        JsonArray array = new JsonArray();
        if (keywords == null) return array;
        int rank = 1;
        for (String keyword : keywords) {
            if (isBlank(keyword)) continue;
            JsonObject object = new JsonObject();
            object.addProperty("query", keyword.trim());
            object.addProperty("rank", rank++);
            array.add(object);
        }
        return array;
    }

    private static void addString(JsonObject object, String key, String value) {
        if (object == null || isBlank(key) || isBlank(value)) return;
        object.addProperty(key, value.trim());
    }

    private static void addPositiveInt(JsonObject object, String key, int value) {
        if (object == null || isBlank(key) || value <= 0) return;
        object.addProperty(key, value);
    }

    private static void addStringArray(JsonObject object, String key, String value) {
        if (object == null || isBlank(key) || isBlank(value)) return;
        JsonArray array = new JsonArray();
        for (String part : value.split("[,，/、|｜;；\\s]+")) {
            addArrayItem(array, part);
            if (array.size() >= 8) break;
        }
        if (array.size() > 0) object.add(key, array);
    }

    private static void addPeopleArray(JsonObject object, String key, String value) {
        if (object == null || isBlank(key) || isBlank(value)) return;
        JsonArray array = new JsonArray();
        for (String group : value.split("[,，/、|｜;；]+")) {
            String item = normalizeListItem(group);
            if (hasLatinLetter(item)) {
                addArrayItem(array, item);
            } else {
                for (String part : item.split("\\s+")) addArrayItem(array, part);
            }
            if (array.size() >= 8) break;
        }
        if (array.size() > 0) object.add(key, array);
    }

    private static void addArrayItem(JsonArray array, String value) {
        String item = normalizeListItem(value);
        if (isBlank(item) || contains(array, item) || array.size() >= 8) return;
        array.add(item);
    }

    private static String normalizeListItem(String value) {
        return Objects.toString(value, "").trim().replaceAll("\\s+", " ");
    }

    private static boolean hasLatinLetter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 128 && Character.isLetter(character)) return true;
        }
        return false;
    }

    private static boolean contains(JsonArray array, String value) {
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && element.getAsString().equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static boolean containsNormalized(List<String> values, String value) {
        String normalized = PersonalRecommendationService.normalizeTitle(value);
        for (String item : values) if (PersonalRecommendationService.normalizeTitle(item).equals(normalized)) return true;
        return false;
    }

    private static int episodeCount(Vod vod) {
        if (vod == null) return 0;
        int count = 0;
        for (Flag flag : vod.getFlags()) if (flag != null && flag.getEpisodes() != null) count = Math.max(count, flag.getEpisodes().size());
        if (count > 0) return count;
        String playUrl = vod.getPlayUrl();
        if (isBlank(playUrl)) return 0;
        for (String group : playUrl.split("\\$\\$\\$")) {
            if (isBlank(group)) continue;
            count = Math.max(count, group.split("#").length);
        }
        return count;
    }

    private static String inferMediaType(String typeName, int episodeCount) {
        String value = Objects.toString(typeName, "").toLowerCase(Locale.ROOT);
        if (value.contains("电影") || value.contains("movie")) return "movie";
        if (value.contains("电视剧") || value.contains("连续") || value.contains("剧集") || value.contains("短剧") || value.contains("动漫") || value.contains("动画") || value.contains("综艺") || value.contains("纪录")) return "tv";
        return episodeCount > 1 ? "tv" : "";
    }

    private static String inferHistoryMediaType(String typeName, String episodeName) {
        String mediaType = inferMediaType(typeName, 0);
        if (!isBlank(mediaType)) return mediaType;
        return HISTORY_EPISODE_LABEL.matcher(Objects.toString(episodeName, "")).find() ? "tv" : "";
    }

    private static String inferLanguage(String area) {
        String value = Objects.toString(area, "").trim();
        if (isBlank(value)) return "";
        if (value.contains("中国") || value.contains("大陆") || value.contains("香港") || value.contains("台湾") || value.contains("华语")) return "中文";
        if (value.contains("日本")) return "日语";
        if (value.contains("韩国")) return "韩语";
        if (value.contains("美国") || value.contains("英国") || value.contains("加拿大") || value.contains("澳大利亚")) return "英语";
        if (value.contains("法国")) return "法语";
        if (value.contains("德国")) return "德语";
        if (value.contains("印度")) return "印地语";
        return "";
    }

    private static int extractNumber(String text) {
        String value = Objects.toString(text, "");
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else if (digits.length() > 0) break;
        }
        if (digits.length() == 0) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (Throwable e) {
            return 0;
        }
    }

    private static String formatTime(long time) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(time));
        } catch (Throwable e) {
            return "";
        }
    }

    private static String limit(String text, int maxLength) {
        String value = Objects.toString(text, "").trim();
        if (value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength)).trim();
    }

    private TmdbItem resolveItem(AiRecommendation recommendation) {
        if (recommendation == null || isBlank(recommendation.title)) return null;
        String mediaType = "tv".equals(recommendation.mediaType) ? "tv" : "movie";
        PersonalRecommendationService personalService = new PersonalRecommendationService(tmdbService, tmdbConfig);
        if (tmdbConfig.isReady()) {
            try {
                TmdbItem item = tmdbMatcher.searchAndMatch(recommendation.title, mediaType, recommendation.year);
                if (item != null) {
                    try {
                        PersonalRecommendationService.DoubanRating rating = personalService.loadDoubanRating(recommendation.title, mediaType, recommendation.year);
                        item = PersonalRecommendationService.withRating(item, rating.getRating());
                    } catch (Throwable e) {
                        SpiderDebug.log("ai-rec", "douban rating resolve failed title=%s error=%s", recommendation.title, e.getMessage());
                    }
                    return withReason(item, recommendation.reason);
                }
            } catch (Throwable e) {
                SpiderDebug.log("ai-rec", "tmdb resolve failed title=%s error=%s", recommendation.title, e.getMessage());
            }
        }
        try {
            TmdbItem item = personalService.matchDoubanItem(recommendation.title, mediaType, recommendation.year);
            if (item != null && item.getDoubanRating() <= 0) {
                PersonalRecommendationService.DoubanRating rating = personalService.loadDoubanRating(recommendation.title, mediaType, recommendation.year);
                item = PersonalRecommendationService.withRating(item, rating.getRating());
            }
            if (item != null) return withReason(item, recommendation.reason);
        } catch (Throwable e) {
            SpiderDebug.log("ai-rec", "douban resolve failed title=%s error=%s", recommendation.title, e.getMessage());
        }
        return fallbackItem(recommendation);
    }

    static PersonalRecommendationService.RecommendationPage pageAllCandidates(List<PersonalRecommendationService.RecommendationCandidate> candidates, String fingerprint) {
        List<PersonalRecommendationService.RecommendationCandidate> allowed = new ArrayList<>();
        List<RecommendationFeedbackStore.Entry> feedback = PersonalRecommendationService.feedbackEntries();
        for (PersonalRecommendationService.RecommendationCandidate candidate : candidates == null ? new ArrayList<PersonalRecommendationService.RecommendationCandidate>() : candidates) {
            if (candidate == null || PersonalRecommendationService.isFeedbackBlocked(feedback, candidate.item)) continue;
            allowed.add(candidate);
        }
        List<PersonalRecommendationService.RecommendationCandidate> ranked = PersonalRecommendationService.rankCandidates(allowed, Integer.MAX_VALUE);
        return PersonalRecommendationService.pageItems(ranked, 0, Math.max(1, ranked.size()), fingerprint, false);
    }

    private PersonalRecommendationService.RecommendationPage resolveAndCache(List<AiRecommendation> recommendations, String fingerprint, @Nullable String currentTitle) {
        long start = System.currentTimeMillis();
        List<TmdbItem> resolvedItems = RecommendationEnrichmentExecutor.map(recommendations, this::resolveItem, AiRecommendationService::fallbackItem);
        List<PersonalRecommendationService.RecommendationCandidate> candidates = new ArrayList<>();
        int fallbackCount = 0;
        int order = 0;
        for (TmdbItem item : resolvedItems) {
            if (item == null || isBlank(item.getTitle())) continue;
            if (item.getTmdbId() <= 0 && isBlank(item.getPosterUrl()) && isBlank(item.getBackdropUrl())) fallbackCount++;
            String normalized = PersonalRecommendationService.normalizeTitle(item.getTitle());
            if (isBlank(normalized)) continue;
            candidates.add(new PersonalRecommendationService.RecommendationCandidate(item, aiKey(item, normalized), normalized, 100.0 - order, order));
            order++;
        }
        PersonalRecommendationService.RecommendationPage page = pageAllCandidates(candidates, fingerprint);
        writeResolvedCache(fingerprint, currentTitle, page.getItems());
        SpiderDebug.log("ai-rec", "resolve recommendations cost=%dms input=%d output=%d fallback=%d", System.currentTimeMillis() - start, recommendations == null ? 0 : recommendations.size(), page.getItems().size(), fallbackCount);
        return page;
    }

    private static PersonalRecommendationService.RecommendationPage pageFallbackRecommendations(List<AiRecommendation> recommendations, String fingerprint) {
        List<PersonalRecommendationService.RecommendationCandidate> candidates = new ArrayList<>();
        int order = 0;
        for (AiRecommendation recommendation : recommendations) {
            TmdbItem item = fallbackItem(recommendation);
            if (item == null || isBlank(item.getTitle())) continue;
            String normalized = PersonalRecommendationService.normalizeTitle(item.getTitle());
            if (isBlank(normalized)) continue;
            candidates.add(new PersonalRecommendationService.RecommendationCandidate(item, aiKey(item, normalized), normalized, 100.0 - order, order));
            order++;
        }
        return pageAllCandidates(candidates, fingerprint);
    }

    private static PersonalRecommendationService.RecommendationPage pageItems(List<TmdbItem> items, String fingerprint) {
        List<PersonalRecommendationService.RecommendationCandidate> candidates = new ArrayList<>();
        int order = 0;
        for (TmdbItem item : items == null ? new ArrayList<TmdbItem>() : items) {
            if (item == null || isBlank(item.getTitle())) continue;
            String normalized = PersonalRecommendationService.normalizeTitle(item.getTitle());
            if (isBlank(normalized)) continue;
            candidates.add(new PersonalRecommendationService.RecommendationCandidate(item, aiKey(item, normalized), normalized, 100.0 - order, order));
            order++;
        }
        return pageAllCandidates(candidates, fingerprint);
    }

    private static TmdbItem fallbackItem(AiRecommendation recommendation) {
        if (recommendation == null || isBlank(recommendation.title)) return null;
        String mediaType = "tv".equals(recommendation.mediaType) ? "tv" : "movie";
        String subtitle = recommendation.subtitle();
        return new TmdbItem(
                -Math.abs((recommendation.title + recommendation.year + mediaType).hashCode()),
                mediaType,
                recommendation.title,
                subtitle,
                "",
                "",
                "",
                "",
                0.0,
                "",
                "",
                new ArrayList<>(),
                "",
                0.0,
                0.0,
                recommendation.reason);
    }

    static TmdbItem withReason(TmdbItem item, String reason) {
        if (item == null || isBlank(reason)) return item;
        return new TmdbItem(
                item.getTmdbId(),
                item.getMediaType(),
                item.getTitle(),
                item.getSubtitle(),
                item.getOverview(),
                item.getPosterUrl(),
                item.getBackdropUrl(),
                item.getCredit(),
                item.getRating(),
                item.getOriginalLanguage(),
                item.getOriginCountry(),
                item.getGenreIds(),
                item.getDepartment(),
                item.getTmdbRating(),
                item.getDoubanRating(),
                reason
        );
    }

    private List<AiRecommendation> readCache(String fingerprint) {
        return readCache(cacheFile(fingerprint));
    }

    private List<AiRecommendation> readCache(File file) {
        try {
            if (file == null || !file.exists() || file.length() <= 0) return new ArrayList<>();
            return parseRecommendations(Path.read(file));
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    private void writeCache(String fingerprint, @Nullable String currentTitle, List<AiRecommendation> recommendations) {
        try {
            writeRecommendationFile(cacheFile(fingerprint), recommendations);
            writeRecommendationFile(latestCacheFile(latestCacheKey(currentTitle, config)), recommendations);
            writeRecommendationFile(displayCacheFile(latestDisplayCacheKey(currentTitle, config)), recommendations);
        } catch (Throwable ignored) {
        }
    }

    private static void writeRecommendationFile(File file, List<AiRecommendation> recommendations) {
        try {
            if (file == null || recommendations == null || recommendations.isEmpty()) return;
            JsonArray array = new JsonArray();
            for (AiRecommendation recommendation : recommendations) array.add(recommendation.toJson());
            JsonObject root = new JsonObject();
            root.add("items", array);
            Path.write(file, root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    private List<TmdbItem> readResolvedCache(File file) {
        try {
            if (file == null || !file.exists() || file.length() <= 0) return new ArrayList<>();
            return parseResolvedItems(Path.read(file));
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    private void writeResolvedCache(String fingerprint, @Nullable String currentTitle, List<TmdbItem> items) {
        try {
            writeResolvedFile(resolvedCacheFile(fingerprint), items);
            writeResolvedFile(latestResolvedCacheFile(latestCacheKey(currentTitle, config)), items);
            writeResolvedFile(displayResolvedCacheFile(latestDisplayCacheKey(currentTitle, config)), items);
        } catch (Throwable ignored) {
        }
    }

    private static void writeResolvedFile(File file, List<TmdbItem> items) {
        try {
            if (file == null || items == null || items.isEmpty()) return;
            JsonArray array = new JsonArray();
            for (TmdbItem item : items) array.add(tmdbItemToJson(item));
            JsonObject root = new JsonObject();
            root.add("items", array);
            Path.write(file, root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    private File cacheFile(String fingerprint) {
        try {
            File dir = new File(Path.cache(), "ai_rec");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, fingerprint + ".json");
        } catch (Throwable e) {
            return null;
        }
    }

    private File resolvedCacheFile(String fingerprint) {
        try {
            File dir = new File(Path.cache(), "ai_rec");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, fingerprint + RESOLVED_CACHE_SUFFIX);
        } catch (Throwable e) {
            return null;
        }
    }

    private File latestCacheFile(String latestKey) {
        try {
            if (isBlank(latestKey)) return null;
            File dir = new File(Path.cache(), "ai_rec");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, LATEST_CACHE_PREFIX + latestKey + ".json");
        } catch (Throwable e) {
            return null;
        }
    }

    private File latestResolvedCacheFile(String latestKey) {
        try {
            if (isBlank(latestKey)) return null;
            File dir = new File(Path.cache(), "ai_rec");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, LATEST_CACHE_PREFIX + latestKey + RESOLVED_CACHE_SUFFIX);
        } catch (Throwable e) {
            return null;
        }
    }

    private File displayCacheFile(String displayKey) {
        try {
            if (isBlank(displayKey)) return null;
            File dir = new File(Path.cache(), "ai_rec");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, DISPLAY_CACHE_PREFIX + displayKey + ".json");
        } catch (Throwable e) {
            return null;
        }
    }

    private File displayResolvedCacheFile(String displayKey) {
        try {
            if (isBlank(displayKey)) return null;
            File dir = new File(Path.cache(), "ai_rec");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, DISPLAY_CACHE_PREFIX + displayKey + RESOLVED_CACHE_SUFFIX);
        } catch (Throwable e) {
            return null;
        }
    }

    private File latestAnyRecommendationCacheFile() {
        return newestCacheFile(aiCacheDir(), false);
    }

    private File latestAnyResolvedCacheFile() {
        return newestCacheFile(aiCacheDir(), true);
    }

    private File aiCacheDir() {
        try {
            File dir = new File(Path.cache(), "ai_rec");
            if (!dir.exists()) dir.mkdirs();
            return dir;
        } catch (Throwable e) {
            return null;
        }
    }

    static File newestCacheFile(File dir, boolean resolved) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return null;
        File[] files = dir.listFiles(file -> file != null && file.isFile() && file.length() > 0 && isCacheFileName(file.getName(), resolved));
        if (files == null || files.length == 0) return null;
        File newest = null;
        for (File file : files) {
            if (newest == null || file.lastModified() > newest.lastModified()) newest = file;
        }
        return newest;
    }

    private static boolean isCacheFileName(String name, boolean resolved) {
        if (isBlank(name)) return false;
        if (resolved) return name.endsWith(RESOLVED_CACHE_SUFFIX);
        return name.endsWith(".json") && !name.contains(".items");
    }

    private static String normalizeSearchRecords(String searchRecords) {
        if (isBlank(searchRecords)) return "";
        try {
            JsonElement element = JsonParser.parseString(searchRecords);
            if (!element.isJsonArray()) return PersonalRecommendationService.normalizeTitle(searchRecords);
            List<String> values = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                if (!item.isJsonPrimitive()) continue;
                String normalized = PersonalRecommendationService.normalizeTitle(item.getAsString());
                if (!isBlank(normalized) && !values.contains(normalized)) values.add(normalized);
            }
            return String.join("|", values);
        } catch (Throwable e) {
            return PersonalRecommendationService.normalizeTitle(searchRecords);
        }
    }

    private static String extractJson(String text) {
        String value = Objects.toString(text, "").trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        if (value.startsWith("{") || value.startsWith("[")) return value;
        int objectStart = value.indexOf('{');
        int arrayStart = value.indexOf('[');
        if (objectStart < 0 && arrayStart < 0) return "";
        boolean useArray = arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart);
        int start = useArray ? arrayStart : objectStart;
        int end = useArray ? value.lastIndexOf(']') : value.lastIndexOf('}');
        return end > start ? value.substring(start, end + 1) : "";
    }

    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonArray firstArray(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonArray values = array(object, key);
            if (values.size() > 0) return values;
        }
        return new JsonArray();
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) return "";
        return Objects.toString(object.get(key).getAsString(), "").trim();
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = string(object, key);
            if (!isBlank(value)) return value;
        }
        return "";
    }

    private static int intValue(JsonObject object, String... keys) {
        for (String key : keys) {
            try {
                if (object != null && object.has(key) && !object.get(key).isJsonNull()) return object.get(key).getAsInt();
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static double doubleValue(JsonObject object, String... keys) {
        for (String key : keys) {
            try {
                if (object != null && object.has(key) && !object.get(key).isJsonNull()) return object.get(key).getAsDouble();
            } catch (Throwable ignored) {
            }
        }
        return 0.0;
    }

    private static void addUnique(List<String> values, String value) {
        if (isBlank(value)) return;
        String normalized = PersonalRecommendationService.normalizeTitle(value);
        for (String item : values) if (PersonalRecommendationService.normalizeTitle(item).equals(normalized)) return;
        values.add(value.trim());
    }

    private static String aiKey(TmdbItem item, String normalized) {
        return item.getTmdbId() > 0 ? item.getMediaType() + ":" + item.getTmdbId() : "ai:" + normalized;
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(Objects.toString(text, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) builder.append(String.format(Locale.US, "%02x", value));
            return builder.toString();
        } catch (Throwable e) {
            return Integer.toHexString(Objects.toString(text, "").hashCode());
        }
    }

    private static String excerpt(String text) {
        String value = Objects.toString(text, "").replace('\n', ' ').trim();
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    private static List<AiRecommendation> parseSseRecommendations(String body, AiConfig config) {
        List<AiRecommendation> result = new ArrayList<>();
        for (String line : Objects.toString(body, "").split("\\r?\\n")) {
            String value = line == null ? "" : line.trim();
            if (!value.startsWith("data:")) continue;
            value = value.substring("data:".length()).trim();
            if (value.isEmpty() || "[DONE]".equalsIgnoreCase(value)) continue;
            List<AiRecommendation> items = parseResponseRecommendations(value, config);
            if (!items.isEmpty()) result.addAll(items);
        }
        return result;
    }

    public static final class CachedPage {

        private final PersonalRecommendationService.RecommendationPage page;
        private final boolean exact;
        private final boolean resolved;

        private CachedPage(PersonalRecommendationService.RecommendationPage page, boolean exact, boolean resolved) {
            this.page = page == null ? PersonalRecommendationService.RecommendationPage.empty("") : page;
            this.exact = exact;
            this.resolved = resolved;
        }

        static CachedPage empty(String fingerprint) {
            return new CachedPage(PersonalRecommendationService.RecommendationPage.empty(fingerprint), false, false);
        }

        public PersonalRecommendationService.RecommendationPage getPage() {
            return page;
        }

        public boolean hasItems() {
            return !page.getItems().isEmpty();
        }

        public boolean isExact() {
            return exact;
        }

        public boolean isResolved() {
            return resolved;
        }
    }

    static final class AiRecommendation {

        final String title;
        final int year;
        final String mediaType;
        final String reason;

        private AiRecommendation(String title, int year, String mediaType, String reason) {
            this.title = title == null ? "" : title.trim();
            this.year = year;
            this.mediaType = "tv".equals(mediaType) ? "tv" : "movie";
            this.reason = reason == null ? "" : reason.trim();
        }

        static AiRecommendation from(JsonObject object) {
            String title = firstString(object, "title", "name", "vodName");
            if (isBlank(title)) return null;
            String mediaType = firstString(object, "mediaType", "type", "category");
            int year = firstInt(object, "year", "releaseYear");
            String reason = firstString(object, "reason", "desc", "overview");
            return new AiRecommendation(title, year, mediaType, reason);
        }

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("title", title);
            object.addProperty("year", year);
            object.addProperty("mediaType", mediaType);
            object.addProperty("reason", reason);
            return object;
        }

        String subtitle() {
            List<String> parts = new ArrayList<>();
            parts.add("tv".equals(mediaType) ? "剧集" : "电影");
            if (year > 0) parts.add(String.valueOf(year));
            return String.join(" · ", parts);
        }

        private static String firstString(JsonObject object, String... keys) {
            for (String key : keys) {
                String value = string(object, key);
                if (!isBlank(value)) return value;
            }
            return "";
        }

        private static int firstInt(JsonObject object, String... keys) {
            for (String key : keys) {
                try {
                    String value = string(object, key);
                    if (!isBlank(value)) return Integer.parseInt(value.replaceAll("[^0-9]", ""));
                } catch (Throwable ignored) {
                }
            }
            return 0;
        }
    }
}
