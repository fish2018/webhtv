package com.fongmi.android.tv.title;

import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.service.AiTitleExtractionService;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.List;

public final class MediaTitleResolver {

    private static final float AI_THRESHOLD = 0.75f;

    private final MediaTitleParser parser;
    private final MediaTitleCache cache;

    public MediaTitleResolver() {
        this(new MediaTitleParser(), new MediaTitleCache());
    }

    MediaTitleResolver(MediaTitleParser parser, MediaTitleCache cache) {
        this.parser = parser;
        this.cache = cache;
    }

    public MediaTitleResolution resolve(MediaTitleRequest request) {
        return resolve(request, false);
    }

    public MediaTitleResolution resolveWithAiFallback(MediaTitleRequest request) {
        return resolve(request, true);
    }

    private MediaTitleResolution resolve(MediaTitleRequest request, boolean forceAi) {
        MediaTitleRequest safe = enrichLearning(request);
        MediaTitleResolution resolution = parser.parse(safe);
        if (!forceAi) applyTmdbCache(safe, resolution);
        String skipReason = aiSkipReason(safe, resolution, forceAi);
        SpiderDebug.log("ai-title", "rule source=%s raw=%s canonical=%s confidence=%.2f force=%s candidates=%s",
                resolution.getSource(), safe.getRawTitle(), resolution.getCanonicalTitle(), resolution.getConfidence(), forceAi, resolution.queryTitles());
        if (!skipReason.isEmpty()) {
            SpiderDebug.log("ai-title", "skip title-extraction reason=%s raw=%s canonical=%s allowAi=%s enabled=%s ready=%s",
                    skipReason, safe.getRawTitle(), resolution.getCanonicalTitle(), safe.isAllowAi(), Setting.isAiTitleExtraction(), Setting.isAiConfigReady());
            return resolution;
        }

        AiConfig config = AiConfig.objectFrom(Setting.getAiConfig());
        MediaTitleResolution ai = cache.read(safe, config);
        if (ai != null) SpiderDebug.log("ai-title", "cache hit raw=%s canonical=%s confidence=%.2f candidates=%s", safe.getRawTitle(), ai.getCanonicalTitle(), ai.getConfidence(), ai.queryTitles());
        if (ai == null) {
            ai = new AiTitleExtractionService(config).extract(safe, resolution);
            if (ai != null) cache.write(safe, config, ai);
        }
        return ai == null || (!forceAi && ai.getConfidence() < 0.55f) ? resolution : merge(resolution, ai);
    }

    public List<String> queryTitles(MediaTitleRequest request, int limit) {
        return queryTitles(resolve(request), limit);
    }

    public List<String> queryAiFallbackTitles(MediaTitleRequest request, int limit) {
        return queryTitles(resolveWithAiFallback(request), limit);
    }

    public List<String> queryCleanedTitles(MediaTitleRequest request, int limit) {
        List<String> result = new ArrayList<>();
        for (String title : parser.cleanSearchTitles(request)) {
            if (title.isEmpty()) continue;
            boolean exists = false;
            for (String item : result) if (item.equalsIgnoreCase(title)) { exists = true; break; }
            if (!exists) result.add(title);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private List<String> queryTitles(MediaTitleResolution resolution, int limit) {
        List<String> result = new ArrayList<>();
        for (String title : resolution.queryTitles()) {
            if (title.isEmpty()) continue;
            boolean exists = false;
            for (String item : result) if (item.equalsIgnoreCase(title)) { exists = true; break; }
            if (!exists) result.add(title);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private MediaTitleRequest enrichLearning(MediaTitleRequest request) {
        MediaTitleRequest safe = request == null ? MediaTitleRequest.builder().build() : request;
        List<MediaTitleLearningExample> examples = new ArrayList<>(safe.getLearningExamples());
        examples.addAll(MediaTitleLearningStore.load().find(safe, 5));
        examples.addAll(Setting.getDanmakuMatchCache().learningExamples(parser.cleanTitle(safe.getRawTitle())));
        return MediaTitleRequest.builder()
                .siteKey(safe.getSiteKey())
                .vodId(safe.getVodId())
                .rawTitle(safe.getRawTitle())
                .rawRemarks(safe.getRawRemarks())
                .searchKeyword(safe.getSearchKeyword())
                .vodYear(safe.getVodYear())
                .episodeName(safe.getEpisodeName())
                .flag(safe.getFlag())
                .source(safe.getSource())
                .allowAi(safe.isAllowAi())
                .learningExamples(examples)
                .build();
    }

    private void applyTmdbCache(MediaTitleRequest request, MediaTitleResolution resolution) {
        TmdbItem item = Setting.getTmdbMatchCache().find(request.getSiteKey(), request.getVodId(), request.getRawTitle());
        if (item == null || item.getTitle().isEmpty()) return;
        if (!isTmdbCacheCompatible(item, resolution)) return;
        resolution.setCanonicalTitle(item.getTitle());
        resolution.setMediaType(item.getMediaType());
        resolution.setConfidence(0.95f);
        resolution.setSource(MediaTitleResolution.SOURCE_TMDB_CACHE);
        resolution.addCandidate(MediaTitleCandidate.of(item.getTitle(), MediaTitleCandidate.SOURCE_TMDB, 0.96f));
    }

    private boolean isTmdbCacheCompatible(TmdbItem item, MediaTitleResolution resolution) {
        String cached = parser.normalizeSearchText(item.getTitle());
        String parsed = parser.normalizeSearchText(resolution.getCanonicalTitle());
        boolean compatible = parsed.isEmpty() || cached.equals(parsed);
        if (!compatible) SpiderDebug.log("ai-title", "skip tmdb-cache raw=%s cached=%s parsed=%s", resolution.getRawTitle(), item.getTitle(), resolution.getCanonicalTitle());
        return compatible;
    }

    private boolean shouldUseAi(MediaTitleRequest request, MediaTitleResolution resolution, boolean forceAi) {
        return aiSkipReason(request, resolution, forceAi).isEmpty();
    }

    private String aiSkipReason(MediaTitleRequest request, MediaTitleResolution resolution, boolean forceAi) {
        if (!request.isAllowAi()) return "request-disabled";
        if (!Setting.isAiTitleExtraction()) return "setting-disabled";
        if (!Setting.isAiConfigReady()) return "config-not-ready";
        if (resolution.getCanonicalTitle().isEmpty()) return "empty-title";
        if (!forceAi && resolution.getConfidence() >= AI_THRESHOLD) return "rule-confidence";
        return "";
    }

    private MediaTitleResolution merge(MediaTitleResolution rule, MediaTitleResolution ai) {
        MediaTitleResolution merged = copy(ai);
        merged.setSource(MediaTitleResolution.SOURCE_RULE_AI_MERGED);
        merged.addCandidate(MediaTitleCandidate.of(rule.getCanonicalTitle(), MediaTitleCandidate.SOURCE_RULE, Math.min(0.75f, rule.getConfidence())));
        merged.addCandidate(MediaTitleCandidate.of(rule.getRuleTitle(), MediaTitleCandidate.SOURCE_RULE, Math.min(0.7f, rule.getConfidence())));
        if (!rule.getRawTitle().equals(rule.getRuleTitle())) merged.addCandidate(MediaTitleCandidate.of(rule.getRawTitle(), MediaTitleCandidate.SOURCE_RAW, 0.25f));
        return merged;
    }

    private MediaTitleResolution copy(MediaTitleResolution source) {
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
        result.setNeedsVerification(source.isNeedsVerification());
        for (String alias : source.getAliases()) result.addAlias(alias);
        for (MediaTitleCandidate candidate : source.getCandidates()) result.addCandidate(candidate);
        return result;
    }
}
