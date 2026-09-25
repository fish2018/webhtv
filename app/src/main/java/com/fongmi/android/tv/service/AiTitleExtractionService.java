package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.title.MediaTitleCandidate;
import com.fongmi.android.tv.title.MediaTitleLearningExample;
import com.fongmi.android.tv.title.MediaTitleParser;
import com.fongmi.android.tv.title.MediaTitleRequest;
import com.fongmi.android.tv.title.MediaTitleResolution;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class AiTitleExtractionService {

    public static final int PROMPT_VERSION = AiConfig.DEFAULT_TITLE_EXTRACTION_PROMPT_VERSION;
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 25;
    private static final int CALL_TIMEOUT_SECONDS = 35;
    private static final int MAX_LEARNING_EXAMPLES = 5;
    private static final int MAX_REASONABLE_SEASON = 100;
    private static final int MAX_REASONABLE_EPISODE = 10000;

    private final AiConfig config;

    public AiTitleExtractionService(AiConfig config) {
        this.config = config == null ? new AiConfig().sanitize() : config.sanitize();
    }

    public MediaTitleResolution extract(MediaTitleRequest request, MediaTitleResolution rule) {
        if (request == null || rule == null || !config.isReady()) return null;
        String prompt = buildPrompt(config, request, rule);
        try {
            AiCompletionClient.RequestSpec spec = AiCompletionClient.requestSpec(config, prompt);
            AiDebugLog.request("ai-title", "title-extraction", config, spec, "rawTitle=" + request.getRawTitle());
            Request httpRequest = AiCompletionClient.buildRequest(spec);
            long start = System.currentTimeMillis();
            try (Response response = client().newCall(httpRequest).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                long cost = System.currentTimeMillis() - start;
                if (!response.isSuccessful()) {
                    AiDebugLog.response("ai-title", "title-extraction", response.code(), cost, body, "success=false");
                    SpiderDebug.log("ai-title", "request failed code=%d cost=%dms", response.code(), cost);
                    return null;
                }
                MediaTitleResolution parsed = parseResponse(AiCompletionClient.extractCompletionText(body, config), rule);
                if (parsed == null) parsed = parseResponse(body, rule);
                AiDebugLog.response("ai-title", "title-extraction", response.code(), cost, body, "hit=" + (parsed != null));
                SpiderDebug.log("ai-title", "request cost=%dms hit=%s", cost, parsed != null);
                return parsed;
            }
        } catch (Throwable e) {
            AiDebugLog.error("ai-title", "title-extraction", 0, e, "rawTitle=" + request.getRawTitle());
            SpiderDebug.log("ai-title", "request failed error=%s", e.getMessage());
            return null;
        }
    }

    static String buildPrompt(MediaTitleRequest request, MediaTitleResolution rule) {
        return buildPrompt(new AiConfig().sanitize(), request, rule);
    }

    static String buildPrompt(AiConfig config, MediaTitleRequest request, MediaTitleResolution rule) {
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        JsonObject input = new JsonObject();
        input.addProperty("rawTitle", request.getRawTitle());
        input.addProperty("searchKeyword", request.getSearchKeyword());
        input.addProperty("ruleTitle", rule.getRuleTitle());
        input.addProperty("rawRemarks", request.getRawRemarks());
        input.addProperty("episodeName", request.getEpisodeName());
        input.addProperty("folderName", request.getFolderName());
        JsonArray contextTitles = new JsonArray();
        for (String title : request.getContextTitles()) contextTitles.add(title);
        input.add("contextTitles", contextTitles);
        input.addProperty("year", rule.getYear());
        input.addProperty("seasonNumber", rule.getSeasonNumber());
        input.addProperty("episodeNumber", rule.getEpisodeNumber());
        input.addProperty("ruleSource", rule.getRuleSource());
        input.addProperty("contextConfidence", rule.getContextConfidence());
        input.addProperty("contextGroupSize", rule.getContextGroupSize());

        JsonArray examples = new JsonArray();
        List<MediaTitleLearningExample> learning = request.getLearningExamples();
        for (int i = 0; i < learning.size() && examples.size() < MAX_LEARNING_EXAMPLES; i++) {
            MediaTitleLearningExample example = learning.get(i);
            if (example == null || !example.isUsable()) continue;
            JsonObject item = new JsonObject();
            item.addProperty("rawTitlePattern", example.getRawTitle());
            item.addProperty("ruleTitle", example.getRuleTitle());
            item.addProperty("expectedTitle", example.getExpectedTitle());
            item.addProperty("mediaType", example.getMediaType());
            item.addProperty("year", example.getYear());
            item.addProperty("seasonNumber", example.getSeasonNumber());
            item.addProperty("source", example.getSource());
            examples.add(item);
        }
        if (examples.size() > 0) input.add("learningExamples", examples);

        return safe.getTitleExtractionPrompt().trim()
                + "\n输入 JSON:\n"
                + input;
    }

    static MediaTitleResolution parseResponse(String text, MediaTitleResolution fallback) {
        String json = extractJson(text);
        if (json.isEmpty()) return null;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) return null;
            JsonObject object = element.getAsJsonObject();
            MediaTitleParser parser = new MediaTitleParser();
            String rawTitle = firstString(object, "canonicalTitle", "title", "name");
            if (!isUsableAiTitle(rawTitle) || containsNoise(rawTitle)) return null;
            String title = parser.cleanTitle(rawTitle);
            if (!isUsableAiTitle(title) || containsNoise(title)) return null;
            MediaTitleResolution result = copyRule(fallback);
            result.setCanonicalTitle(title);
            result.setOriginalTitle(firstString(object, "originalTitle", "originalName"));
            String mediaType = firstString(object, "mediaType", "type").toLowerCase(Locale.ROOT);
            if ("tv".equals(mediaType) || "movie".equals(mediaType)) result.setMediaType(mediaType);
            result.setYear(firstValidInt(object, fallback.getYear(), 1900, 2099, "year", "releaseYear"));
            result.setSeasonNumber(firstValidInt(object, fallback.getSeasonNumber(), 1, MAX_REASONABLE_SEASON, "seasonNumber", "season"));
            result.setEpisodeNumber(firstValidInt(object, fallback.getEpisodeNumber(), 1, MAX_REASONABLE_EPISODE, "episodeNumber", "episode"));
            result.setEpisodeTitle(parser.cleanTitle(firstString(object, "episodeTitle")));
            result.setConfidence(confidence(object, fallback));
            result.setSource(MediaTitleResolution.SOURCE_AI);
            result.setAiReasonCode(firstString(object, "reasonCode", "reason"));
            result.setNeedsVerification(!normalize(title).equals(normalize(fallback.getRuleTitle())) && result.getConfidence() < 0.9f);
            result.addCandidate(MediaTitleCandidate.of(title, MediaTitleCandidate.SOURCE_AI, result.getConfidence()));
            for (JsonElement alias : array(object, "aliases")) {
                if (!alias.isJsonPrimitive()) continue;
                String rawAlias = alias.getAsString();
                String value = cleanAlias(rawAlias);
                if (!value.isEmpty() && !containsNoise(rawAlias) && !containsNoise(value)) {
                    result.addAlias(value);
                    result.addCandidate(MediaTitleCandidate.of(value, MediaTitleCandidate.SOURCE_ALIAS, Math.min(result.getConfidence(), 0.72f)));
                }
            }
            result.addCandidate(MediaTitleCandidate.of(fallback.getRuleTitle(), MediaTitleCandidate.SOURCE_RULE, Math.min(0.75f, fallback.getConfidence())));
            return result;
        } catch (Throwable e) {
            return null;
        }
    }

    private static MediaTitleResolution copyRule(MediaTitleResolution source) {
        MediaTitleResolution result = new MediaTitleResolution();
        result.setRawTitle(source.getRawTitle());
        result.setRuleTitle(source.getRuleTitle());
        result.setCanonicalTitle(source.getCanonicalTitle());
        result.setOriginalTitle(source.getOriginalTitle());
        result.setMediaType(source.getMediaType());
        result.setYear(source.getYear());
        result.setSeasonNumber(source.getSeasonNumber());
        result.setEpisodeNumber(source.getEpisodeNumber());
        result.setEpisodeTitle(source.getEpisodeTitle());
        result.setConfidence(source.getConfidence());
        result.setSource(source.getSource());
        result.setAiReasonCode(source.getAiReasonCode());
        result.setContextConfidence(source.getContextConfidence());
        result.setContextGroupSize(source.getContextGroupSize());
        result.setRuleSource(source.getRuleSource());
        result.setConfidenceBreakdown(source.getConfidenceBreakdown());
        result.setDegradationReason(source.getDegradationReason());
        result.setNeedsVerification(source.isNeedsVerification());
        for (String alias : source.getAliases()) result.addAlias(alias);
        for (MediaTitleCandidate candidate : source.getCandidates()) result.addCandidate(candidate);
        return result;
    }

    private OkHttpClient client() {
        return com.github.catvod.net.OkHttp.client().newBuilder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = string(object, key);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) return "";
        return Objects.toString(object.get(key).getAsString(), "").trim();
    }

    private static int firstValidInt(JsonObject object, int fallback, int min, int max, String... keys) {
        for (String key : keys) {
            try {
                if (object == null || !object.has(key) || object.get(key).isJsonNull()) continue;
                int value = object.get(key).getAsInt();
                if (value >= min && value <= max) return value;
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    private static float confidence(JsonObject object, MediaTitleResolution fallback) {
        for (String key : new String[]{"confidence", "score"}) {
            try {
                if (object == null || !object.has(key) || object.get(key).isJsonNull()) continue;
                double value = object.get(key).getAsDouble();
                if (Double.isNaN(value) || Double.isInfinite(value) || value < 0d || value > 1d) return 0f;
                return (float) value;
            } catch (Throwable ignored) {
                return 0f;
            }
        }
        return Math.max(0.65f, fallback == null ? 0f : fallback.getConfidence());
    }

    private static boolean isUsableAiTitle(String text) {
        String value = Objects.toString(text, "").trim();
        if (value.isEmpty() || value.length() > 80 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) return false;
        if (value.matches(".*https?://.*") || value.matches(".*[\\\\/].*")) return false;
        if (!value.matches(".*[\\u4e00-\\u9fffA-Za-z].*")) return false;
        return !value.matches("(?i)^(更新至|更至|连载至|全集|合集|完结|电影|电视剧|剧集|动漫|动画|综艺|tv)$");
    }

    private static String extractJson(String text) {
        String value = Objects.toString(text, "").trim();
        if (value.startsWith("```")) value = value.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        if (value.startsWith("{")) return value;
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        return end > start ? value.substring(start, end + 1) : "";
    }

    private static boolean containsNoise(String text) {
        String value = Objects.toString(text, "");
        return value.matches("(?i).*(1080p|2160p|720p|4k|8k|web[- ]?dl|h\\.?265|h\\.?264|x265|x264|更新至|全集|第\\s*\\d+\\s*集).*");
    }

    private static String cleanAlias(String text) {
        String value = Objects.toString(text, "").trim();
        if (value.isEmpty()) return "";
        value = value.replaceAll("(?i)\\.(mkv|mp4|avi|mov|wmv|flv|rmvb|ts|m2ts)$", " ");
        value = value.replaceAll("(?i)\\b(HD|4K|8K|1080P|2160P|720P|HDR|HDR10|DV|BluRay|WEB[- ]?DL|HDTV|BDRip|Remux|HEVC|H\\.?265|H\\.?264|x265|x264|AAC|DTS|DDP|Atmos)\\b", " ");
        value = value.replaceAll("(更新至|更至|连载至|全|共)\\s*[0-9零〇一二三四五六七八九十百]+\\s*[集话話回期章节節]", " ");
        value = value.replaceAll("[._\\-+]+", " ");
        value = value.replaceAll("\\s+", " ").trim();
        return value.replaceAll("^[\\s:：,，.。·|/\\\\]+|[\\s:：,，.。·|/\\\\]+$", "");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }

}
