package com.fongmi.android.tv.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.bean.AudioConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.ShortDramaConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.db.AppDatabase;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class PersonalRecommendationService {

    public static final int DEFAULT_PAGE_SIZE = 12;
    private static final int TMDB_HISTORY_SEED_BATCH = 4;
    private static final int MIN_TMDB_HISTORY_RESULTS = 4;
    private static final int DOUBAN_SEED_BATCH = 8;
    private static final int MAX_DOUBAN_LOOKUPS_PER_SEED = 1;
    private static final int DOUBAN_RELATED_COUNT = 10;
    private static final int MAX_TMDB_SEED_CACHE = 128;
    private static final int MAX_DOUBAN_RATING_CACHE = 256;
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long DOUBAN_CACHE_TTL = DAY * 7;
    private static final long DOUBAN_REQUEST_COOLDOWN = TimeUnit.MINUTES.toMillis(30);
    private static final long DOUBAN_REQUEST_MIN_INTERVAL = 750L;
    private static final long TMDB_SEED_CACHE_TTL = DAY * 7;
    private static final String DOUBAN_SUGGEST_URL = "https://movie.douban.com/j/subject_suggest";
    private static final String DOUBAN_SEARCH_PAGE_URL = "https://search.douban.com/movie/subject_search";
    private static final String DOUBAN_ABSTRACT_URL = "https://movie.douban.com/j/subject_abstract";
    private static final String DOUBAN_REFERER = "https://movie.douban.com/";
    private static final String DOUBAN_SEARCH_PAGE_REFERER = "https://search.douban.com/movie/";
    private static final String DOUBAN_MOBILE_REFERER = "https://m.douban.com/movie/subject/%s/";
    private static final String DOUBAN_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final double SCORE_TMDB_CURRENT_RECOMMENDATION = 120.0;
    private static final double SCORE_TMDB_CURRENT_SIMILAR = 105.0;
    private static final double SCORE_TMDB_HISTORY_RECOMMENDATION = 92.0;
    private static final double SCORE_TMDB_HISTORY_SIMILAR_DELTA = -10.0;
    private static final double SCORE_TMDB_HISTORY_DECAY = 8.0;
    private static final double SCORE_TMDB_GENRE_MATCH = 18.0;
    private static final double SCORE_TMDB_LANGUAGE_MATCH = 10.0;
    private static final double SCORE_TMDB_COUNTRY_MATCH = 8.0;
    private static final double SCORE_DOUBAN_CURRENT_RELATED = 110.0;
    private static final double SCORE_DOUBAN_HISTORY_RELATED = 88.0;
    private static final double SCORE_DOUBAN_HISTORY_DECAY = 7.0;
    private static final double SCORE_DOUBAN_SUGGEST_FALLBACK = 45.0;
    private static final double SCORE_DUPLICATE_SIGNAL_BONUS = 12.0;
    private static final double SCORE_RATING_WEIGHT = 0.8;
    private static final Pattern YEAR = Pattern.compile("(19\\d{2}|20\\d{2})");

    private final TmdbService tmdbService;
    private final TmdbConfig tmdbConfig;
    private final TmdbMatcher tmdbMatcher;

    private static final LinkedHashMap<String, TmdbSeedData> TMDB_SEED_CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, TmdbSeedData> eldest) {
            return size() > MAX_TMDB_SEED_CACHE;
        }
    };

    private static final DoubanRatingMemoryCache DOUBAN_RATING_CACHE =
            new DoubanRatingMemoryCache(MAX_DOUBAN_RATING_CACHE, DOUBAN_CACHE_TTL);
    private static final Object DOUBAN_REQUEST_LOCK = new Object();
    private static volatile long doubanRequestBlockedUntil;
    private static volatile long doubanLastRequestAt;
    private static volatile boolean doubanCooldownLoaded;

    public PersonalRecommendationService() {
        this(new TmdbService(), TmdbConfig.objectFrom(Setting.getTmdbConfig()));
    }

    public PersonalRecommendationService(TmdbService tmdbService, TmdbConfig tmdbConfig) {
        this.tmdbService = tmdbService == null ? new TmdbService() : tmdbService;
        this.tmdbConfig = tmdbConfig == null ? new TmdbConfig() : tmdbConfig;
        this.tmdbMatcher = new TmdbMatcher(this.tmdbService, this.tmdbConfig);
    }

    public Recommendations load(@Nullable Vod currentVod, @Nullable TmdbItem currentItem, @Nullable JsonObject currentDetail) {
        if (!Setting.isPersonalRecommendation()) return Recommendations.empty();
        RecommendationPages pages = loadPage(currentVod, currentItem, currentDetail, 0, DEFAULT_PAGE_SIZE);
        RecommendationPage ai = loadAiPage(currentVod, currentItem, DEFAULT_PAGE_SIZE);
        return new Recommendations(pages.getTmdb().getItems(), pages.getDouban().getItems(), ai.getItems());
    }

    public RecommendationPages loadPage(@Nullable Vod currentVod, @Nullable TmdbItem currentItem, @Nullable JsonObject currentDetail, int offset, int pageSize) {
        if (!Setting.isPersonalRecommendation() || Thread.currentThread().isInterrupted()) return RecommendationPages.empty();
        RecommendationPage tmdb = loadTmdbPage(currentVod, currentItem, currentDetail, offset, pageSize);
        if (Thread.currentThread().isInterrupted()) return new RecommendationPages(tmdb, RecommendationPage.empty(""), RecommendationPage.empty(""));
        RecommendationPage douban = loadDoubanPage(currentVod, offset, pageSize);
        return new RecommendationPages(tmdb, douban, RecommendationPage.empty(aiFingerprint(currentVod, currentItem)));
    }

    public List<TmdbItem> loadTmdb(@Nullable Vod currentVod, @Nullable TmdbItem currentItem, @Nullable JsonObject currentDetail) {
        if (!Setting.isPersonalRecommendation() || !tmdbConfig.isReady()) return new ArrayList<>();
        return loadFromTmdb(currentVod, currentItem, currentDetail, 0, DEFAULT_PAGE_SIZE).getItems();
    }

    public RecommendationPage loadTmdbPage(@Nullable Vod currentVod, @Nullable TmdbItem currentItem, @Nullable JsonObject currentDetail, int offset, int pageSize) {
        if (!Setting.isPersonalRecommendation() || !tmdbConfig.isReady()) return RecommendationPage.empty(historyFingerprint(currentVod, true));
        return loadFromTmdb(currentVod, currentItem, currentDetail, offset, pageSize);
    }

    public List<TmdbItem> loadDouban(@Nullable Vod currentVod) {
        if (!Setting.isPersonalRecommendation()) return new ArrayList<>();
        return loadFromDouban(currentVod, 0, DEFAULT_PAGE_SIZE).getItems();
    }

    public DoubanRating loadDoubanRating(@Nullable String title, @Nullable String mediaType, int year) {
        if (isBlank(title)) return DoubanRating.empty();
        String cacheKey = doubanRatingCacheKey(title, mediaType, year);
        DoubanRating cached = DOUBAN_RATING_CACHE.get(cacheKey, System.currentTimeMillis());
        if (cached != null) return cached;

        List<DoubanSubject> subjects = fetchDoubanSuggest(title, false);
        DoubanRating rating = bestDoubanRating(title, mediaType, year, subjects);
        if (!rating.isEmpty()) return cacheDoubanRating(cacheKey, rating);
        for (DoubanSubject subject : rankDoubanSubjects(title, mediaType, year, subjects, false)) {
            rating = fetchDoubanAbstractRating(subject);
            if (!rating.isEmpty()) return cacheDoubanRating(cacheKey, rating);
        }
        return DoubanRating.empty();
    }

    private static DoubanRating cacheDoubanRating(String cacheKey, DoubanRating rating) {
        DOUBAN_RATING_CACHE.put(cacheKey, rating, System.currentTimeMillis());
        return rating;
    }

    private static String doubanRatingCacheKey(String title, @Nullable String mediaType, int year) {
        String type = isBlank(mediaType) ? "" : DoubanSubject.mediaType(mediaType);
        return type + "|" + normalizeTitle(title) + "|" + year;
    }

    TmdbItem matchDoubanItem(@Nullable String title, @Nullable String mediaType, int year) {
        return bestDoubanItem(title, mediaType, year, fetchDoubanSuggest(title));
    }

    static DoubanRating bestDoubanRating(@Nullable String title, @Nullable String mediaType, int year, @Nullable List<DoubanSubject> subjects) {
        List<DoubanSubject> ranked = rankDoubanSubjects(title, mediaType, year, subjects, true);
        return ranked.isEmpty() ? DoubanRating.empty() : DoubanRating.from(ranked.get(0));
    }

    static TmdbItem bestDoubanItem(@Nullable String title, @Nullable String mediaType, int year, @Nullable List<DoubanSubject> subjects) {
        List<DoubanSubject> ranked = rankDoubanSubjects(title, mediaType, year, subjects, false);
        return ranked.isEmpty() ? null : ranked.get(0).toTmdbItem();
    }

    static List<DoubanSubject> rankDoubanSubjects(@Nullable String title, @Nullable String mediaType, int year, @Nullable List<DoubanSubject> subjects, boolean requireRating) {
        List<DoubanSubject> ranked = new ArrayList<>();
        if (isBlank(title) || subjects == null || subjects.isEmpty()) return ranked;
        String normalizedTitle = normalizeTitle(title);
        String targetType = isBlank(mediaType) ? "" : DoubanSubject.mediaType(mediaType);
        List<DoubanSubjectScore> scored = new ArrayList<>();
        int order = 0;
        for (DoubanSubject subject : subjects) {
            if (subject == null || isBlank(subject.title) || (requireRating && subject.rating <= 0)) {
                order++;
                continue;
            }
            String normalizedSubject = normalizeTitle(subject.title);
            int score = -order;
            if (normalizedSubject.equals(normalizedTitle)) score += 100;
            else if (normalizedSubject.contains(normalizedTitle) || normalizedTitle.contains(normalizedSubject)) score += 40;
            if (!isBlank(targetType) && targetType.equals(subject.mediaType)) score += 20;
            if (year > 0 && subject.year == year) score += 20;
            scored.add(new DoubanSubjectScore(subject, score, order));
            order++;
        }
        scored.sort(Comparator
                .comparingInt((DoubanSubjectScore item) -> item.score).reversed()
                .thenComparingInt(item -> item.order));
        for (DoubanSubjectScore item : scored) ranked.add(item.subject);
        return ranked;
    }

    public RecommendationPage loadDoubanPage(@Nullable Vod currentVod, int offset, int pageSize) {
        if (!Setting.isPersonalRecommendation()) return RecommendationPage.empty(historyFingerprint(currentVod, false));
        return loadFromDouban(currentVod, offset, pageSize);
    }

    public RecommendationPage loadAiPage(@Nullable Vod currentVod, @Nullable TmdbItem currentItem, int pageSize) {
        String currentTitle = currentTitle(currentVod, currentItem);
        return new AiRecommendationService(tmdbService, tmdbConfig).load(currentVod, currentTitle, aiHistoryFingerprint(currentTitle), pageSize);
    }

    public AiRecommendationService.CachedPage loadCachedAiPage(@Nullable Vod currentVod, @Nullable TmdbItem currentItem, int pageSize) {
        String currentTitle = currentTitle(currentVod, currentItem);
        return new AiRecommendationService(tmdbService, tmdbConfig).loadCached(currentTitle, aiHistoryFingerprint(currentTitle), pageSize);
    }

    public RecommendationPage resolveCachedAiPage(@Nullable Vod currentVod, @Nullable TmdbItem currentItem, int pageSize) {
        String currentTitle = currentTitle(currentVod, currentItem);
        return new AiRecommendationService(tmdbService, tmdbConfig).resolveCached(currentTitle, aiHistoryFingerprint(currentTitle), pageSize);
    }

    public RecommendationPage refreshAiPage(@Nullable Vod currentVod, @Nullable TmdbItem currentItem, int pageSize) {
        String currentTitle = currentTitle(currentVod, currentItem);
        return new AiRecommendationService(tmdbService, tmdbConfig).refresh(currentVod, currentTitle, aiHistoryFingerprint(currentTitle), pageSize);
    }

    public String aiFingerprint(@Nullable Vod currentVod, @Nullable TmdbItem currentItem) {
        String currentTitle = currentTitle(currentVod, currentItem);
        return AiRecommendationService.fingerprint(currentTitle, aiHistoryFingerprint(currentTitle), Setting.getKeyword(), AiConfig.objectFrom(Setting.getAiConfig()));
    }

    private String aiHistoryFingerprint(String currentTitle) {
        return AiRecommendationService.historyMetadataFingerprint(safeHistory(), currentTitle)
                + "|feedback:" + RecommendationFeedbackStore.fingerprint();
    }

    public String historyFingerprint(@Nullable Vod currentVod, boolean tmdbTarget) {
        String currentTitle = currentTitle(currentVod, null);
        List<String> seeds = tmdbTarget
                ? historySeeds(currentTitle, Integer.MAX_VALUE, true)
                : doubanSeeds(currentTitle, Integer.MAX_VALUE);
        return recommendationFingerprint(seeds);
    }

    private RecommendationPage loadFromTmdb(@Nullable Vod currentVod, @Nullable TmdbItem currentItem, @Nullable JsonObject currentDetail, int offset, int pageSize) {
        int pageOffset = Math.max(0, offset);
        int limit = safePageSize(pageSize);
        int requested = pageOffset + limit + 1;
        List<RecommendationCandidate> currentCandidates = new ArrayList<>();
        Set<String> blockedTitles = blockedTitles(currentVod);
        List<RecommendationFeedbackStore.Entry> feedback = feedbackEntries();
        String currentTitle = currentTitle(currentVod, currentItem);
        if (!isBlank(currentTitle)) blockedTitles.add(normalizeTitle(currentTitle));
        List<String> historySeeds = historySeeds(currentTitle, Integer.MAX_VALUE, true);
        String fingerprint = recommendationFingerprint(historySeeds);
        if (Thread.currentThread().isInterrupted()) return RecommendationPage.empty(fingerprint);

        TmdbItem anchorItem = currentItem;
        JsonObject anchorDetail = currentDetail;
        if (anchorDetail == null && anchorItem == null && !isBlank(currentTitle)) {
            try {
                anchorItem = tmdbMatcher.searchAndMatch(currentTitle, currentVod);
                throwIfInterrupted();
                if (anchorItem != null) anchorDetail = tmdbService.detail(anchorItem, tmdbConfig);
            } catch (TmdbService.AuthException | CancellationException e) {
                SpiderDebug.log("personal-rec", "TMDB current match aborted title=%s error=%s", currentTitle, e.getMessage());
                return RecommendationPage.empty(fingerprint);
            } catch (Throwable e) {
                SpiderDebug.log("personal-rec", "TMDB current match failed title=%s error=%s", currentTitle, e.getMessage());
            }
        }
        if (anchorItem != null && !isBlank(anchorItem.getTitle())) blockedTitles.add(normalizeTitle(anchorItem.getTitle()));
        TmdbContext context = TmdbContext.from(anchorDetail);

        if (anchorDetail != null) {
            addTmdbCandidates(currentCandidates, blockedTitles, feedback, tmdbService.recommendations(anchorDetail, tmdbConfig), anchorItem, SCORE_TMDB_CURRENT_RECOMMENDATION, context);
            addTmdbCandidates(currentCandidates, blockedTitles, feedback, tmdbService.similar(anchorDetail, tmdbConfig), anchorItem, SCORE_TMDB_CURRENT_SIMILAR, context);
        }

        int seedLimit = seedLimitForOffset(historySeeds.size(), pageOffset, limit, TMDB_HISTORY_SEED_BATCH);
        List<RecommendationCandidate> ranked = new ArrayList<>(currentCandidates);
        List<RecommendationCandidate> historyCandidates = new ArrayList<>();
        try {
            do {
                throwIfInterrupted();
                historyCandidates = tmdbHistoryCandidates(historySeeds, seedLimit, blockedTitles, feedback, anchorItem, context);
                ranked = mergeTmdbPersonalCandidates(currentCandidates, historyCandidates, requested, pageOffset == 0 ? MIN_TMDB_HISTORY_RESULTS : 0);
                if (ranked.size() > pageOffset || seedLimit >= historySeeds.size()) break;
                seedLimit = Math.min(historySeeds.size(), seedLimit + TMDB_HISTORY_SEED_BATCH);
            } while (true);
        } catch (TmdbService.AuthException | CancellationException e) {
            SpiderDebug.log("personal-rec", "TMDB history scan aborted seeds=%d error=%s", seedLimit, e.getMessage());
            ranked = mergeTmdbPersonalCandidates(currentCandidates, historyCandidates, requested, 0);
            seedLimit = historySeeds.size();
        }

        RecommendationPage page = pageItems(ranked, pageOffset, limit, fingerprint, seedLimit < historySeeds.size());
        SpiderDebug.log("personal-rec", "TMDB candidates current=%d history=%d offset=%d result=%d more=%s title=%s", currentCandidates.size(), historyCandidates.size(), pageOffset, page.getItems().size(), page.hasMore(), currentTitle);
        return page;
    }

    private RecommendationPage loadFromDouban(@Nullable Vod currentVod, int offset, int pageSize) {
        int pageOffset = Math.max(0, offset);
        int limit = safePageSize(pageSize);
        int requested = pageOffset + limit + 1;
        String currentTitle = currentTitle(currentVod, null);
        Set<String> sourceTitles = blockedTitles(currentVod);
        List<RecommendationFeedbackStore.Entry> feedback = feedbackEntries();
        if (!isBlank(currentTitle)) sourceTitles.add(normalizeTitle(currentTitle));
        List<String> seeds = doubanSeeds(currentTitle, Integer.MAX_VALUE);
        String fingerprint = recommendationFingerprint(seeds);

        int seedLimit = seedLimitForOffset(seeds.size(), pageOffset, limit, DOUBAN_SEED_BATCH);
        List<RecommendationCandidate> ranked = new ArrayList<>();
        do {
            if (Thread.currentThread().isInterrupted()) break;
            ranked = doubanRankedCandidates(seeds, seedLimit, sourceTitles, feedback, currentTitle, requested);
            if (Thread.currentThread().isInterrupted() || ranked.size() > pageOffset || seedLimit >= seeds.size()) break;
            seedLimit = Math.min(seeds.size(), seedLimit + DOUBAN_SEED_BATCH);
        } while (true);

        return enrichDoubanPage(pageItems(ranked, pageOffset, limit, fingerprint, !Thread.currentThread().isInterrupted() && seedLimit < seeds.size()));
    }

    private RecommendationPage enrichDoubanPage(RecommendationPage page) {
        if (page == null || page.getItems().isEmpty() || !tmdbConfig.isReady()) return page;
        List<TmdbItem> items = RecommendationEnrichmentExecutor.map(page.getItems(), this::enrichDoubanItem, item -> item);
        return new RecommendationPage(items, page.getOffset(), page.getNextOffset(), page.hasMore(), page.getHistoryFingerprint());
    }

    private TmdbItem enrichDoubanItem(TmdbItem doubanItem) {
        if (doubanItem == null) return null;
        try {
            TmdbItem tmdbItem = tmdbMatcher.searchAndMatch(
                    doubanItem.getTitle(), doubanItem.getMediaType(), yearFromText(doubanItem.getSubtitle()));
            return tmdbItem == null ? doubanItem : mergePreferredMetadata(tmdbItem, doubanItem);
        } catch (Throwable e) {
            SpiderDebug.log("personal-rec", "TMDB enrich failed title=%s error=%s", doubanItem.getTitle(), e.getMessage());
            return doubanItem;
        }
    }

    public List<TmdbItem> enrichTmdbRatings(@Nullable List<TmdbItem> items) {
        return RecommendationEnrichmentExecutor.map(items, this::enrichTmdbItemRating, item -> item);
    }

    public void enrichTmdbRatingsAsync(@Nullable List<TmdbItem> items, Consumer<List<TmdbItem>> callback) {
        if (callback == null) return;
        List<TmdbItem> result = items == null ? new ArrayList<>() : new ArrayList<>(items);
        int enrichmentCount = Math.min(DEFAULT_PAGE_SIZE, result.size());
        List<TmdbItem> visibleItems = new ArrayList<>(result.subList(0, enrichmentCount));
        RecommendationEnrichmentExecutor.mapAsync(visibleItems, this::enrichTmdbItemRating, item -> item, enriched -> {
            for (int index = 0; index < enriched.size(); index++) result.set(index, enriched.get(index));
            callback.accept(result);
        });
    }

    public void enrichTmdbPageRatingsAsync(@Nullable RecommendationPage page, Consumer<RecommendationPage> callback) {
        if (callback == null) return;
        if (page == null || page.getItems().isEmpty()) {
            callback.accept(page);
            return;
        }
        enrichTmdbRatingsAsync(page.getItems(), items -> callback.accept(page.withItems(items)));
    }

    public RecommendationPage enrichTmdbPageRatings(@Nullable RecommendationPage page) {
        if (page == null || page.getItems().isEmpty()) return page;
        List<TmdbItem> items = enrichTmdbRatings(page.getItems());
        return new RecommendationPage(items, page.getOffset(), page.getNextOffset(), page.hasMore(), page.getHistoryFingerprint());
    }

    private TmdbItem enrichTmdbItemRating(TmdbItem tmdbItem) {
        if (tmdbItem == null || tmdbItem.getDoubanRating() > 0) return tmdbItem;
        try {
            DoubanRating rating = loadDoubanRating(tmdbItem.getTitle(), tmdbItem.getMediaType(), yearFromText(tmdbItem.getSubtitle()));
            return withRating(tmdbItem, rating.getRating());
        } catch (Throwable e) {
            SpiderDebug.log("personal-rec", "Douban rating enrich failed title=%s error=%s", tmdbItem.getTitle(), e.getMessage());
            return tmdbItem;
        }
    }

    static TmdbItem mergePreferredMetadata(TmdbItem tmdbItem, TmdbItem doubanItem) {
        if (tmdbItem == null) return doubanItem;
        if (doubanItem == null) return tmdbItem;
        double tmdbRating = tmdbItem.getTmdbRating() > 0 ? tmdbItem.getTmdbRating() : tmdbItem.getRating();
        double doubanRating = doubanItem.getDoubanRating() > 0 ? doubanItem.getDoubanRating() : doubanItem.getRating();
        return new TmdbItem(
                tmdbItem.getTmdbId(),
                valueOr(tmdbItem.getMediaType(), doubanItem.getMediaType()),
                valueOr(tmdbItem.getTitle(), doubanItem.getTitle()),
                valueOr(tmdbItem.getSubtitle(), doubanItem.getSubtitle()),
                valueOr(tmdbItem.getOverview(), doubanItem.getOverview()),
                valueOr(tmdbItem.getPosterUrl(), doubanItem.getPosterUrl()),
                valueOr(tmdbItem.getBackdropUrl(), doubanItem.getBackdropUrl()),
                valueOr(tmdbItem.getCredit(), doubanItem.getCredit()),
                tmdbRating > 0 ? tmdbRating : doubanRating,
                tmdbItem.getOriginalLanguage(),
                tmdbItem.getOriginCountry(),
                tmdbItem.getGenreIds(),
                tmdbItem.getDepartment(),
                tmdbRating,
                doubanRating,
                valueOr(tmdbItem.getRecommendationReason(), doubanItem.getRecommendationReason())
        );
    }

    private static String valueOr(String preferred, String fallback) {
        return isBlank(preferred) ? Objects.toString(fallback, "") : preferred;
    }

    private static int yearFromText(String text) {
        Matcher matcher = YEAR.matcher(Objects.toString(text, ""));
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void addTmdbCandidates(List<RecommendationCandidate> candidates, Set<String> blockedTitles, List<RecommendationFeedbackStore.Entry> feedback, List<TmdbItem> items, TmdbItem currentItem, double baseScore, TmdbContext context) {
        if (items == null) return;
        for (TmdbItem item : items) {
            if (item == null || isBlank(item.getTitle())) continue;
            if (sameTmdbItem(item, currentItem)) continue;
            String normalizedTitle = normalizeTitle(item.getTitle());
            if (blockedTitles.contains(normalizedTitle) || isFeedbackBlocked(feedback, item)) continue;
            candidates.add(new RecommendationCandidate(item, tmdbKey(item), normalizedTitle, baseScore + ratingBonus(item.getRating()) + contextBonus(item, context), candidates.size()));
        }
    }

    private void addDoubanCandidates(List<RecommendationCandidate> candidates, Set<String> blockedTitles, List<RecommendationFeedbackStore.Entry> feedback, List<DoubanSubject> subjects, double baseScore) {
        if (subjects == null) return;
        for (DoubanSubject subject : subjects) {
            if (subject == null || isBlank(subject.title)) continue;
            String normalizedTitle = normalizeTitle(subject.title);
            TmdbItem item = subject.toTmdbItem();
            if (isBlank(normalizedTitle) || blockedTitles.contains(normalizedTitle) || isFeedbackBlocked(feedback, item)) continue;
            candidates.add(new RecommendationCandidate(item, doubanKey(subject), normalizedTitle, baseScore + ratingBonus(subject.rating), candidates.size()));
        }
    }

    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("recommendation task cancelled");
    }

    List<RecommendationCandidate> tmdbHistoryCandidates(List<String> seeds, int seedLimit, Set<String> blockedTitles, List<RecommendationFeedbackStore.Entry> feedback, TmdbItem anchorItem, TmdbContext context) {
        List<RecommendationCandidate> candidates = new ArrayList<>();
        int seedIndex = 0;
        for (int i = 0; i < Math.min(seedLimit, seeds.size()); i++) {
            throwIfInterrupted();
            String seed = seeds.get(i);
            try {
                TmdbSeedData data = tmdbSeedData(seed);
                if (data == null || data.seedItem == null) continue;
                double seedScore = SCORE_TMDB_HISTORY_RECOMMENDATION - seedIndex * SCORE_TMDB_HISTORY_DECAY;
                addTmdbCandidates(candidates, blockedTitles, feedback, data.recommendations, anchorItem, seedScore, context);
                addTmdbCandidates(candidates, blockedTitles, feedback, data.similar, anchorItem, seedScore + SCORE_TMDB_HISTORY_SIMILAR_DELTA, context);
                seedIndex++;
            } catch (TmdbService.AuthException | CancellationException e) {
                throw e;
            } catch (Throwable e) {
                SpiderDebug.log("personal-rec", "TMDB seed failed title=%s error=%s", seed, e.getMessage());
            }
        }
        return candidates;
    }

    private TmdbSeedData tmdbSeedData(String seed) throws Exception {
        throwIfInterrupted();
        String key = tmdbSeedCacheKey(seed);
        TmdbSeedData cached = readTmdbSeedCache(key);
        if (cached != null) return cached;
        TmdbItem seedItem = tmdbMatcher.searchAndMatch(seed);
        throwIfInterrupted();
        if (seedItem == null) return null;
        JsonObject detail = tmdbService.detail(seedItem, tmdbConfig);
        throwIfInterrupted();
        List<TmdbItem> recommendations = tmdbService.recommendations(detail, tmdbConfig);
        throwIfInterrupted();
        List<TmdbItem> similar = tmdbService.similar(detail, tmdbConfig);
        throwIfInterrupted();
        TmdbSeedData data = new TmdbSeedData(seedItem, recommendations, similar, System.currentTimeMillis());
        writeTmdbSeedCache(key, data);
        return data;
    }

    private String tmdbSeedCacheKey(String seed) {
        return Objects.toString(tmdbConfig.getApiBase(), "") + "|" + Objects.toString(tmdbConfig.getLanguage(), "") + "|" + normalizeTitle(seed);
    }

    private TmdbSeedData readTmdbSeedCache(String key) {
        synchronized (TMDB_SEED_CACHE) {
            TmdbSeedData data = TMDB_SEED_CACHE.get(key);
            if (data == null) return null;
            if (System.currentTimeMillis() - data.time > TMDB_SEED_CACHE_TTL) {
                TMDB_SEED_CACHE.remove(key);
                return null;
            }
            return data;
        }
    }

    private void writeTmdbSeedCache(String key, TmdbSeedData data) {
        if (isBlank(key) || data == null) return;
        synchronized (TMDB_SEED_CACHE) {
            TMDB_SEED_CACHE.put(key, data);
        }
    }

    private List<RecommendationCandidate> doubanRankedCandidates(List<String> seeds, int seedLimit, Set<String> sourceTitles, List<RecommendationFeedbackStore.Entry> feedback, String currentTitle, int maxResults) {
        List<RecommendationCandidate> relatedCandidates = new ArrayList<>();
        List<RecommendationCandidate> fallbackCandidates = new ArrayList<>();
        int seedIndex = 0;
        int historySeedIndex = 0;
        Set<String> lookedUpSubjects = new HashSet<>();
        for (int i = 0; i < Math.min(seedLimit, seeds.size()); i++) {
            if (Thread.currentThread().isInterrupted()) break;
            String seed = seeds.get(i);
            List<DoubanSubject> suggestions = fetchDoubanSuggest(seed);
            if (Thread.currentThread().isInterrupted()) break;
            boolean currentSeed = !isBlank(currentTitle) && normalizeTitle(seed).equals(normalizeTitle(currentTitle));
            double relatedScore = currentSeed ? SCORE_DOUBAN_CURRENT_RELATED : SCORE_DOUBAN_HISTORY_RELATED - historySeedIndex * SCORE_DOUBAN_HISTORY_DECAY;
            addDoubanCandidates(fallbackCandidates, sourceTitles, feedback, suggestions, SCORE_DOUBAN_SUGGEST_FALLBACK - seedIndex);

            int lookupCount = 0;
            for (DoubanSubject subject : suggestions) {
                if (Thread.currentThread().isInterrupted()) break;
                if (isBlank(subject.id)) continue;
                if (!lookedUpSubjects.add(doubanKey(subject))) continue;
                addDoubanCandidates(relatedCandidates, sourceTitles, feedback, fetchDoubanRelated(subject), relatedScore);
                lookupCount++;
                if (lookupCount >= MAX_DOUBAN_LOOKUPS_PER_SEED) break;
            }
            if (!currentSeed) historySeedIndex++;
            seedIndex++;
        }
        List<RecommendationCandidate> ranked = rankCandidates(relatedCandidates.isEmpty() ? fallbackCandidates : relatedCandidates, maxResults);
        if (!Thread.currentThread().isInterrupted()) enrichDoubanRatings(ranked);
        return ranked;
    }

    private void enrichDoubanRatings(List<RecommendationCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return;
        for (RecommendationCandidate candidate : candidates) {
            if (Thread.currentThread().isInterrupted()) return;
            if (candidate == null || candidate.item == null || candidate.item.getRating() > 0) continue;
            String doubanId = doubanIdFromKey(candidate.key);
            if (isBlank(doubanId)) continue;
            DoubanRating rating = fetchDoubanAbstractRating(doubanId);
            if (rating.isEmpty()) continue;
            candidate.item = withRating(candidate.item, rating.getRating());
        }
    }

    private static String doubanIdFromKey(String key) {
        String value = Objects.toString(key, "");
        return value.startsWith("douban:") ? value.substring("douban:".length()) : "";
    }

    static TmdbItem withRating(TmdbItem item, double rating) {
        if (item == null || rating <= 0 || item.getDoubanRating() > 0) return item;
        double tmdbRating = item.getTmdbRating();
        if (tmdbRating <= 0 && item.getTmdbId() > 0 && item.getRating() > 0) {
            tmdbRating = item.getRating();
        }
        double primaryRating = item.getRating() > 0 ? item.getRating() : rating;
        return new TmdbItem(item.getTmdbId(), item.getMediaType(), item.getTitle(), item.getSubtitle(), item.getOverview(), item.getPosterUrl(), item.getBackdropUrl(), item.getCredit(), primaryRating, item.getOriginalLanguage(), item.getOriginCountry(), item.getGenreIds(), item.getDepartment(), tmdbRating, rating, item.getRecommendationReason());
    }

    static RecommendationPage pageItems(List<RecommendationCandidate> ranked, int offset, int pageSize, String historyFingerprint, boolean hasMoreSeeds) {
        int start = Math.max(0, offset);
        int limit = safePageSize(pageSize);
        int end = Math.min(ranked == null ? 0 : ranked.size(), start + limit);
        List<TmdbItem> items = new ArrayList<>();
        if (ranked != null) {
            for (int i = start; i < end; i++) {
                RecommendationCandidate candidate = ranked.get(i);
                if (candidate != null && candidate.item != null) items.add(candidate.item);
            }
        }
        int nextOffset = start + items.size();
        boolean hasMore = (ranked != null && ranked.size() > end) || hasMoreSeeds;
        return new RecommendationPage(items, start, nextOffset, hasMore, historyFingerprint);
    }

    private static int seedLimitForOffset(int totalSeeds, int offset, int pageSize, int batchSize) {
        if (totalSeeds <= 0) return 0;
        int batch = Math.max(1, batchSize);
        int page = Math.max(0, offset) / safePageSize(pageSize);
        return Math.min(totalSeeds, Math.max(batch, (page + 1) * batch));
    }

    private static int safePageSize(int pageSize) {
        return pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize;
    }

    private List<String> doubanSeeds(String currentTitle, int maxSeeds) {
        List<String> seeds = new ArrayList<>();
        addSeed(seeds, currentTitle, maxSeeds);
        for (String seed : historySeeds(currentTitle, maxSeeds, false)) addSeed(seeds, seed, maxSeeds);
        return seeds;
    }

    static String historySeedFingerprint(List<String> seeds) {
        if (seeds == null || seeds.isEmpty()) return "";
        List<String> normalized = new ArrayList<>();
        for (String seed : seeds) {
            String value = normalizeTitle(seed);
            if (isBlank(value) || normalized.contains(value)) continue;
            normalized.add(value);
        }
        return String.join("|", normalized);
    }

    static String recommendationFingerprint(List<String> seeds, String feedbackFingerprint) {
        return historySeedFingerprint(seeds) + "|feedback:" + Objects.toString(feedbackFingerprint, "");
    }

    private static String recommendationFingerprint(List<String> seeds) {
        return recommendationFingerprint(seeds, RecommendationFeedbackStore.fingerprint());
    }

    private List<String> historySeeds(String currentTitle, int maxSeeds, boolean tmdbTarget) {
        List<String> seeds = new ArrayList<>();
        SourceClassifier classifier = sourceClassifier();
        Set<String> dislikedTitles = RecommendationFeedbackStore.blockedTitles();
        for (History history : safeHistory()) {
            if (!shouldUseHistorySeed(history, tmdbTarget, classifier)) continue;
            if (history != null && dislikedTitles.contains(normalizeTitle(history.getVodName()))) continue;
            addSeed(seeds, history == null ? "" : history.getVodName(), maxSeeds);
            if (seeds.size() >= maxSeeds) break;
        }
        String normalizedCurrent = normalizeTitle(currentTitle);
        seeds.removeIf(seed -> normalizeTitle(seed).equals(normalizedCurrent));
        return seeds;
    }

    private SourceClassifier sourceClassifier() {
        AudioConfig audioConfig = AudioConfig.objectFrom(Setting.getAudioConfig());
        ShortDramaConfig shortDramaConfig = ShortDramaConfig.objectFrom(Setting.getShortDramaConfig());
        return new SourceClassifier() {
            @Override
            public boolean isAudio(String siteKey, String siteName) {
                return audioConfig.isSiteEnabled(siteKey, siteName);
            }

            @Override
            public boolean isShortDrama(String siteKey, String siteName) {
                return shortDramaConfig.isSiteEnabled(siteKey, siteName);
            }
        };
    }

    private boolean shouldUseHistorySeed(History history, boolean tmdbTarget, SourceClassifier classifier) {
        if (history == null) return false;
        SourceInfo source = sourceInfo(history);
        return shouldUseHistorySeed(source.key, source.name, tmdbTarget, classifier);
    }

    static boolean shouldUseHistorySeed(String siteKey, String siteName, boolean tmdbTarget, SourceClassifier classifier) {
        if (classifier == null) return true;
        if (classifier.isAudio(siteKey, siteName)) return false;
        return !tmdbTarget || !classifier.isShortDrama(siteKey, siteName);
    }

    private SourceInfo sourceInfo(History history) {
        String siteKey = historySiteKey(history);
        return new SourceInfo(siteKey, siteName(siteKey));
    }

    private static String historySiteKey(History history) {
        String key = history == null ? "" : Objects.toString(history.getKey(), "");
        int index = key.indexOf(AppDatabase.SYMBOL);
        return index <= 0 ? key : key.substring(0, index);
    }

    private String siteName(String siteKey) {
        if (isBlank(siteKey)) return "";
        try {
            Site site = VodConfig.get().getSite(siteKey);
            return site == null ? "" : site.getName();
        } catch (Throwable e) {
            return "";
        }
    }

    private void addSeed(List<String> seeds, String title, int maxSeeds) {
        if (seeds.size() >= maxSeeds || isBlank(title)) return;
        String normalized = normalizeTitle(title);
        if (isBlank(normalized)) return;
        for (String seed : seeds) if (normalizeTitle(seed).equals(normalized)) return;
        seeds.add(title.trim());
    }

    private Set<String> blockedTitles(Vod currentVod) {
        Set<String> titles = new HashSet<>();
        if (currentVod != null) titles.add(normalizeTitle(currentVod.getName()));
        for (History history : safeHistory()) {
            if (history == null) continue;
            String title = normalizeTitle(history.getVodName());
            if (!isBlank(title)) titles.add(title);
        }
        return titles;
    }

    private List<History> safeHistory() {
        try {
            return historyByWatchTime(History.getAll());
        } catch (Throwable e) {
            SpiderDebug.log("personal-rec", "history read failed: %s", e.getMessage());
            return new ArrayList<>();
        }
    }

    static List<History> historyByWatchTime(List<History> histories) {
        List<History> ordered = histories == null ? new ArrayList<>() : new ArrayList<>(histories);
        ordered.sort(Comparator
                .comparingLong((History history) -> history == null ? 0 : history.getCreateTime()).reversed()
                .thenComparing(Comparator.comparingLong((History history) -> history == null ? 0 : history.getPosition()).reversed()));
        return ordered;
    }

    private List<DoubanSubject> fetchDoubanSuggest(String keyword) {
        return fetchDoubanSuggest(keyword, true);
    }

    private List<DoubanSubject> fetchDoubanSuggest(String keyword, boolean allowSearchPageFallback) {
        if (isBlank(keyword)) return new ArrayList<>();

        HttpUrl suggestBase = HttpUrl.parse(DOUBAN_SUGGEST_URL);
        if (suggestBase != null) {
            HttpUrl suggestUrl = suggestBase.newBuilder().addQueryParameter("q", keyword).build();
            List<DoubanSubject> subjects = parseDoubanSubjects(
                    requestDouban(suggestUrl, DOUBAN_REFERER, "suggest"));
            if (!subjects.isEmpty()) return subjects;
        }
        if (!allowSearchPageFallback) return new ArrayList<>();

        HttpUrl pageBase = HttpUrl.parse(DOUBAN_SEARCH_PAGE_URL);
        if (pageBase == null) return new ArrayList<>();
        HttpUrl pageUrl = pageBase.newBuilder()
                .addQueryParameter("search_text", keyword)
                .addQueryParameter("cat", "1002")
                .build();
        return parseDoubanSearchPage(
                requestDouban(pageUrl, DOUBAN_SEARCH_PAGE_REFERER, "search_page"));
    }

    private DoubanRating fetchDoubanAbstractRating(DoubanSubject subject) {
        if (subject == null || isBlank(subject.id)) return DoubanRating.empty();
        DoubanRating rating = fetchDoubanAbstractRating(subject.id);
        if (!rating.isEmpty()) return rating;
        return DoubanRating.from(subject);
    }

    private DoubanRating fetchDoubanAbstractRating(String subjectId) {
        if (isBlank(subjectId)) return DoubanRating.empty();
        HttpUrl base = HttpUrl.parse(DOUBAN_ABSTRACT_URL);
        if (base == null) return DoubanRating.empty();
        HttpUrl url = base.newBuilder().addQueryParameter("subject_id", subjectId).build();
        String body = requestDouban(url, DOUBAN_REFERER, "abstract");
        return parseDoubanSubjectAbstract(body);
    }

    private List<DoubanSubject> fetchDoubanRelated(DoubanSubject subject) {
        if (subject == null || isBlank(subject.id)) return new ArrayList<>();
        List<DoubanSubject> subjects = fetchDoubanRelated("movie", subject.id);
        if (!subjects.isEmpty()) return subjects;
        return fetchDoubanRelated("tv", subject.id);
    }

    private List<DoubanSubject> fetchDoubanRelated(String mediaType, String doubanId) {
        if (isBlank(doubanId)) return new ArrayList<>();
        String type = "tv".equals(mediaType) ? "tv" : "movie";
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("m.douban.com")
                .addPathSegment("rexxar")
                .addPathSegment("api")
                .addPathSegment("v2")
                .addPathSegment(type)
                .addPathSegment(doubanId)
                .addPathSegment("related_items")
                .addQueryParameter("start", "0")
                .addQueryParameter("count", String.valueOf(DOUBAN_RELATED_COUNT))
                .build();
        String body = requestDouban(url, String.format(Locale.ROOT, DOUBAN_MOBILE_REFERER, doubanId), "related");
        return parseDoubanRelatedSubjects(body);
    }

    static <T> T serializeDoubanRequest(Supplier<T> request) {
        synchronized (DOUBAN_REQUEST_LOCK) {
            return request.get();
        }
    }

    private String requestDouban(HttpUrl url, String referer, String cacheType) {
        if (url == null) return "";
        File file = doubanCacheFile(cacheType, url.toString());
        String cached = readUsableDoubanCache(file, DOUBAN_CACHE_TTL, cacheType);
        if (!isBlank(cached)) return cached;
        if (isDoubanRequestBlocked()) return staleDoubanCache(file, cacheType);

        return serializeDoubanRequest(() -> {
            String refreshedCache = readUsableDoubanCache(file, DOUBAN_CACHE_TTL, cacheType);
            if (!isBlank(refreshedCache)) return refreshedCache;
            if (isDoubanRequestBlocked()) return staleDoubanCache(file, cacheType);
            if (!awaitDoubanRequestSlot()) return staleDoubanCache(file, cacheType);

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", DOUBAN_UA)
                    .header("Referer", isBlank(referer) ? DOUBAN_REFERER : referer)
                    .header("Accept", "text/html,application/json,text/plain;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .build();
            try (Response response = com.github.catvod.net.OkHttp.client().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    if (shouldCoolDownDoubanRequests(cacheType, response.code())) blockDoubanRequests();
                    SpiderDebug.log("personal-rec", "Douban request rejected url=%s status=%d", url, response.code());
                    return staleDoubanCache(file, cacheType);
                }
                String body = response.body().string();
                if ("search_page".equals(cacheType) && hasDoubanSearchPageError(body)) {
                    blockDoubanRequests();
                    SpiderDebug.log("personal-rec", "Douban requests cooling down after search rejection url=%s", url);
                    return staleDoubanCache(file, cacheType);
                }
                if (!isCacheableDoubanResponse(body)) {
                    SpiderDebug.log("personal-rec", "Douban response ignored url=%s", url);
                    return staleDoubanCache(file, cacheType);
                }
                writeDoubanCache(file, body);
                return body;
            } catch (Throwable e) {
                SpiderDebug.log("personal-rec", "Douban request failed url=%s error=%s", url, e.getMessage());
                return staleDoubanCache(file, cacheType);
            }
        });
    }

    static boolean shouldCoolDownDoubanRequests(String cacheType, int status) {
        if (status == 418 || status == 429) return true;
        if (status != 403) return false;
        return "suggest".equals(cacheType)
                || "search_page".equals(cacheType)
                || "abstract".equals(cacheType);
    }

    static long doubanRequestDelay(long now, long lastRequestAt, long minInterval) {
        if (lastRequestAt <= 0 || minInterval <= 0) return 0L;
        return Math.max(0L, lastRequestAt + minInterval - now);
    }

    private static boolean awaitDoubanRequestSlot() {
        long delay = doubanRequestDelay(System.currentTimeMillis(), doubanLastRequestAt, DOUBAN_REQUEST_MIN_INTERVAL);
        try {
            if (delay > 0) Thread.sleep(delay);
            doubanLastRequestAt = System.currentTimeMillis();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String staleDoubanCache(File file, String cacheType) {
        String stale = readUsableDoubanCache(file, Long.MAX_VALUE, cacheType);
        return stale == null ? "" : stale;
    }

    private String readUsableDoubanCache(File file, long ttl, String cacheType) {
        String body = readDoubanCache(file, ttl);
        if (isBlank(body)) return null;
        return isCacheableDoubanResponse(body) ? body : null;
    }

    private boolean isDoubanRequestBlocked() {
        if (!doubanCooldownLoaded) {
            synchronized (DOUBAN_REQUEST_LOCK) {
                if (!doubanCooldownLoaded) {
                    doubanRequestBlockedUntil = readDoubanCooldown();
                    doubanCooldownLoaded = true;
                }
            }
        }
        return System.currentTimeMillis() < doubanRequestBlockedUntil;
    }

    private void blockDoubanRequests() {
        long blockedUntil = System.currentTimeMillis() + DOUBAN_REQUEST_COOLDOWN;
        doubanRequestBlockedUntil = Math.max(doubanRequestBlockedUntil, blockedUntil);
        doubanCooldownLoaded = true;
        persistDoubanCooldown(doubanRequestBlockedUntil);
    }

    static List<DoubanSubject> parseDoubanSearchPage(String body) {
        return parseDoubanSubjects(extractAssignedJsonObject(body, "window.__DATA__"));
    }

    private static String extractAssignedJsonObject(String body, String marker) {
        if (isBlank(body) || isBlank(marker)) return "";
        int markerIndex = body.indexOf(marker);
        if (markerIndex < 0) return "";
        int start = body.indexOf('{', markerIndex + marker.length());
        if (start < 0) return "";

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < body.length(); index++) {
            char value = body.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    inString = false;
                }
                continue;
            }
            if (value == '"') {
                inString = true;
            } else if (value == '{') {
                depth++;
            } else if (value == '}' && --depth == 0) {
                return body.substring(start, index + 1);
            }
        }
        return "";
    }
    static List<DoubanSubject> parseDoubanSubjects(String body) {
        List<DoubanSubject> subjects = new ArrayList<>();
        if (isBlank(body)) return subjects;
        try {
            JsonElement element = JsonParser.parseString(body);
            Set<String> seen = new HashSet<>();
            if (element == null) return subjects;
            if (element.isJsonArray()) {
                appendDoubanSearchSubjects(element.getAsJsonArray(), subjects, seen);
                return subjects;
            }
            if (!element.isJsonObject()) return subjects;

            JsonObject root = element.getAsJsonObject();
            appendDoubanSearchSubjects(jsonArray(root, "smart_box"), subjects, seen);
            JsonElement subjectElement = root.get("subjects");
            if (subjectElement != null && subjectElement.isJsonArray()) {
                appendDoubanSearchSubjects(subjectElement.getAsJsonArray(), subjects, seen);
            } else if (subjectElement != null && subjectElement.isJsonObject()) {
                appendDoubanSearchSubjects(jsonArray(subjectElement.getAsJsonObject(), "items"), subjects, seen);
            }
            appendDoubanSearchSubjects(jsonArray(root, "items"), subjects, seen);
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
        return subjects;
    }

    private static void appendDoubanSearchSubjects(@Nullable JsonArray items, List<DoubanSubject> subjects, Set<String> seen) {
        if (items == null) return;
        for (JsonElement item : items) {
            if (item == null || !item.isJsonObject()) continue;
            DoubanSubject subject = DoubanSubject.fromSearchItem(item.getAsJsonObject());
            if (subject == null) continue;
            String key = !isBlank(subject.id)
                    ? "id:" + subject.id
                    : subject.mediaType + "|" + normalizeTitle(subject.title) + "|" + subject.year;
            if (seen.add(key)) subjects.add(subject);
        }
    }

    @Nullable
    private static JsonArray jsonArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonArray()) return null;
        return object.getAsJsonArray(key);
    }

    static DoubanRating parseDoubanSubjectAbstract(String body) {
        if (isBlank(body)) return DoubanRating.empty();
        try {
            JsonElement element = JsonParser.parseString(body);
            if (element == null || !element.isJsonObject()) return DoubanRating.empty();
            JsonObject root = element.getAsJsonObject();
            JsonObject subject = root.has("subject") && root.get("subject").isJsonObject()
                    ? root.getAsJsonObject("subject")
                    : root;
            DoubanSubject parsed = DoubanSubject.from(subject);
            return DoubanRating.from(parsed);
        } catch (Throwable ignored) {
            return DoubanRating.empty();
        }
    }

    static List<DoubanSubject> parseDoubanRelatedSubjects(String body) {
        List<DoubanSubject> subjects = new ArrayList<>();
        if (isBlank(body)) return subjects;
        try {
            JsonElement element = JsonParser.parseString(body);
            if (element == null || !element.isJsonObject()) return subjects;
            JsonObject object = element.getAsJsonObject();
            JsonArray array = object.has("subjects") && object.get("subjects").isJsonArray()
                    ? object.getAsJsonArray("subjects")
                    : object.has("items") && object.get("items").isJsonArray() ? object.getAsJsonArray("items") : new JsonArray();
            for (JsonElement item : array) {
                if (!item.isJsonObject()) continue;
                DoubanSubject subject = DoubanSubject.from(item.getAsJsonObject());
                if (subject != null) subjects.add(subject);
            }
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
        return subjects;
    }

    private File doubanCacheFile(String type, String key) {
        File dir = doubanCacheDirectory();
        return dir == null ? null : new File(dir, type + "_" + md5(key) + ".json");
    }

    private File doubanCooldownFile() {
        File dir = doubanCacheDirectory();
        return dir == null ? null : new File(dir, "request_cooldown.txt");
    }

    @Nullable
    private File doubanCacheDirectory() {
        try {
            File dir = new File(Path.cache(), "douban_rec");
            if (!dir.exists() && !dir.mkdirs()) return null;
            return dir;
        } catch (Throwable e) {
            return null;
        }
    }

    private long readDoubanCooldown() {
        String value = readDoubanCache(doubanCooldownFile(), Long.MAX_VALUE);
        if (isBlank(value)) return 0L;
        try {
            return Long.parseLong(value.trim());
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private void persistDoubanCooldown(long blockedUntil) {
        writeDoubanCache(doubanCooldownFile(), String.valueOf(blockedUntil));
    }

    private String readDoubanCache(File file, long ttl) {
        try {
            if (file == null || !file.exists() || file.length() <= 0) return null;
            if (ttl != Long.MAX_VALUE && System.currentTimeMillis() - file.lastModified() > ttl) return null;
            return Path.read(file);
        } catch (Throwable e) {
            return null;
        }
    }

    private void writeDoubanCache(File file, String body) {
        try {
            if (file == null || isBlank(body)) return;
            Path.write(file, body.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    static boolean isCacheableDoubanResponse(String body) {
        if (isBlank(body)) return false;
        try {
            JsonElement element = JsonParser.parseString(body);
            if (element != null && element.isJsonArray()) return true;
            if (element != null && element.isJsonObject()) {
                return !hasDoubanApiError(element.getAsJsonObject());
            }
        } catch (Throwable ignored) {
        }
        JsonObject data = doubanSearchPageData(body);
        return data != null && !hasDoubanSearchPageError(data);
    }

    private static boolean hasDoubanApiError(JsonObject object) {
        if (object == null || hasDoubanSearchPageError(object)) return true;
        if (object.has("code") && !object.get("code").isJsonNull()) {
            try {
                if (object.get("code").getAsInt() != 0) return true;
            } catch (Throwable ignored) {
                try {
                    String code = object.get("code").getAsString();
                    if (!isBlank(code) && !"0".equals(code.trim())) return true;
                } catch (Throwable ignoredAgain) {
                    return true;
                }
            }
        }
        if (!object.has("msg") || object.get("msg").isJsonNull()) return false;
        try {
            String message = object.get("msg").getAsString();
            String normalized = Objects.toString(message, "").trim().toLowerCase(Locale.ROOT);
            return normalized.contains("login")
                    || normalized.contains("forbidden")
                    || normalized.contains("访问太频繁")
                    || normalized.contains("请求太频繁");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasDoubanSearchPageError(String body) {
        JsonObject data = doubanSearchPageData(body);
        return data != null && hasDoubanSearchPageError(data);
    }

    private static boolean hasDoubanSearchPageError(JsonObject data) {
        if (data == null || !data.has("error_info") || data.get("error_info").isJsonNull() || !data.get("error_info").isJsonPrimitive()) return false;
        try {
            return !isBlank(data.get("error_info").getAsString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    private static JsonObject doubanSearchPageData(String body) {
        String json = extractAssignedJsonObject(body, "window.__DATA__");
        if (isBlank(json)) return null;
        try {
            JsonElement element = JsonParser.parseString(json);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static List<TmdbItem> rankTmdbItemsForContext(@Nullable JsonObject detail, List<TmdbItem> recommendations, List<TmdbItem> similar, int maxResults) {
        if (maxResults <= 0) return new ArrayList<>();
        TmdbContext context = TmdbContext.from(detail);
        List<RecommendationFeedbackStore.Entry> feedback = feedbackEntries();
        List<RecommendationCandidate> candidates = new ArrayList<>();
        addTmdbRankingCandidates(candidates, feedback, recommendations, SCORE_TMDB_CURRENT_RECOMMENDATION, context);
        addTmdbRankingCandidates(candidates, feedback, similar, SCORE_TMDB_CURRENT_SIMILAR, context);
        List<TmdbItem> items = new ArrayList<>();
        for (RecommendationCandidate candidate : rankCandidates(candidates, maxResults)) {
            if (candidate.item != null) items.add(candidate.item);
        }
        return items;
    }

    private static void addTmdbRankingCandidates(List<RecommendationCandidate> candidates, List<RecommendationFeedbackStore.Entry> feedback, List<TmdbItem> items, double baseScore, TmdbContext context) {
        if (items == null) return;
        for (TmdbItem item : items) {
            if (item == null || isBlank(item.getTitle())) continue;
            String normalizedTitle = normalizeTitle(item.getTitle());
            if (isFeedbackBlocked(feedback, item)) continue;
            candidates.add(new RecommendationCandidate(item, tmdbKey(item), normalizedTitle, baseScore + ratingBonus(item.getRating()) + contextBonus(item, context), candidates.size()));
        }
    }

    static List<RecommendationFeedbackStore.Entry> feedbackEntries() {
        try {
            return RecommendationFeedbackStore.get();
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    static boolean isFeedbackBlocked(@Nullable List<RecommendationFeedbackStore.Entry> feedback, @Nullable TmdbItem item) {
        return RecommendationFeedbackStore.contains(feedback, item);
    }

    static List<RecommendationCandidate> mergeTmdbPersonalCandidates(List<RecommendationCandidate> currentCandidates, List<RecommendationCandidate> historyCandidates, int maxResults, int minHistoryResults) {
        if (maxResults <= 0) return new ArrayList<>();
        List<RecommendationCandidate> history = rankCandidates(historyCandidates, maxResults);
        if (!history.isEmpty()) {
            List<RecommendationCandidate> selected = new ArrayList<>(history.subList(0, Math.min(maxResults, history.size())));
            if (selected.size() < maxResults) {
                for (RecommendationCandidate candidate : rankCandidates(currentCandidates, maxResults)) {
                    if (containsCandidateKey(selected, candidate.key)) continue;
                    selected.add(candidate);
                    if (selected.size() >= maxResults) break;
                }
            }
            return selected;
        }
        List<RecommendationCandidate> current = rankCandidates(currentCandidates, maxResults);
        List<RecommendationCandidate> mergedInput = new ArrayList<>();
        mergedInput.addAll(current);
        List<RecommendationCandidate> selected = rankCandidates(mergedInput, maxResults);
        return selected;
    }

    private static boolean containsCandidateKey(List<RecommendationCandidate> candidates, String key) {
        if (isBlank(key)) return false;
        for (RecommendationCandidate candidate : candidates) {
            if (candidate != null && key.equals(candidate.key)) return true;
        }
        return false;
    }

    private String md5(String text) {
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

    static List<RecommendationCandidate> rankCandidates(List<RecommendationCandidate> candidates, int maxResults) {
        if (candidates == null || maxResults <= 0) return new ArrayList<>();
        LinkedHashMap<String, RecommendationCandidate> merged = new LinkedHashMap<>();
        for (RecommendationCandidate candidate : candidates) {
            if (candidate == null || isBlank(candidate.key) || isBlank(candidate.normalizedTitle)) continue;
            RecommendationCandidate existing = merged.get(candidate.key);
            if (existing == null) {
                merged.put(candidate.key, new RecommendationCandidate(candidate.item, candidate.key, candidate.normalizedTitle, candidate.score, candidate.order));
            } else {
                if (candidate.item != null && candidate.score > existing.score) existing.item = candidate.item;
                existing.score = Math.max(existing.score, candidate.score) + SCORE_DUPLICATE_SIGNAL_BONUS;
            }
        }
        List<RecommendationCandidate> ranked = new ArrayList<>(merged.values());
        ranked.sort(Comparator
                .comparingDouble((RecommendationCandidate candidate) -> candidate.score).reversed()
                .thenComparingInt(candidate -> candidate.order));
        return ranked.size() > maxResults ? new ArrayList<>(ranked.subList(0, maxResults)) : ranked;
    }

    private static double ratingBonus(double rating) {
        if (rating <= 0) return 0.0;
        return Math.min(rating, 10.0) * SCORE_RATING_WEIGHT;
    }

    private static double contextBonus(TmdbItem item, TmdbContext context) {
        if (item == null || context == null || context.isEmpty()) return 0.0;
        double score = 0.0;
        for (Integer genreId : item.getGenreIds()) {
            if (genreId != null && context.genreIds.contains(genreId)) score += SCORE_TMDB_GENRE_MATCH;
        }
        if (!isBlank(item.getOriginalLanguage()) && item.getOriginalLanguage().equalsIgnoreCase(context.originalLanguage)) {
            score += SCORE_TMDB_LANGUAGE_MATCH;
        }
        String country = item.getOriginCountry();
        if (!isBlank(country) && context.originCountries.contains(country.toUpperCase(Locale.ROOT))) {
            score += SCORE_TMDB_COUNTRY_MATCH;
        }
        return score;
    }

    private static String currentTitle(Vod currentVod, TmdbItem currentItem) {
        if (currentItem != null && !isBlank(currentItem.getTitle())) return currentItem.getTitle();
        return currentVod == null ? "" : currentVod.getName();
    }

    private static boolean sameTmdbItem(TmdbItem first, TmdbItem second) {
        return first != null && second != null && first.getTmdbId() > 0 && first.getTmdbId() == second.getTmdbId() && Objects.equals(first.getMediaType(), second.getMediaType());
    }

    private static String tmdbKey(TmdbItem item) {
        if (item.getTmdbId() > 0) return item.getMediaType() + ":" + item.getTmdbId();
        return "title:" + normalizeTitle(item.getTitle());
    }

    private static String doubanKey(DoubanSubject subject) {
        return !isBlank(subject.id) ? "douban:" + subject.id : "douban-title:" + normalizeTitle(subject.title);
    }

    static String normalizeTitle(String text) {
        return Objects.toString(text, "").replaceAll("[\\s·•・._\\-/\\\\|()（）\\[\\]【】《》<>:：,，.。]+", "").trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    interface SourceClassifier {
        boolean isAudio(String siteKey, String siteName);

        boolean isShortDrama(String siteKey, String siteName);
    }

    private static final class SourceInfo {

        private final String key;
        private final String name;

        private SourceInfo(String key, String name) {
            this.key = Objects.toString(key, "");
            this.name = Objects.toString(name, "");
        }
    }

    private static final class DoubanSubjectScore {

        private final DoubanSubject subject;
        private final int score;
        private final int order;

        private DoubanSubjectScore(DoubanSubject subject, int score, int order) {
            this.subject = subject;
            this.score = score;
            this.order = order;
        }
    }

    static final class TmdbContext {

        final Set<Integer> genreIds;
        final String originalLanguage;
        final Set<String> originCountries;

        private TmdbContext(Set<Integer> genreIds, String originalLanguage, Set<String> originCountries) {
            this.genreIds = genreIds == null ? new HashSet<>() : genreIds;
            this.originalLanguage = originalLanguage == null ? "" : originalLanguage.trim();
            this.originCountries = originCountries == null ? new HashSet<>() : originCountries;
        }

        static TmdbContext from(JsonObject detail) {
            Set<Integer> genreIds = new HashSet<>();
            Set<String> countries = new HashSet<>();
            if (detail != null) {
                JsonArray genres = array(detail, "genres");
                for (JsonElement element : genres) {
                    if (!element.isJsonObject()) continue;
                    JsonObject object = element.getAsJsonObject();
                    if (object.has("id") && !object.get("id").isJsonNull()) {
                        try {
                            genreIds.add(object.get("id").getAsInt());
                        } catch (Throwable ignored) {
                        }
                    }
                }
                for (JsonElement element : array(detail, "origin_country")) addCountry(countries, element);
                for (JsonElement element : array(detail, "production_countries")) {
                    if (!element.isJsonObject()) continue;
                    addCountry(countries, element.getAsJsonObject().get("iso_3166_1"));
                }
            }
            return new TmdbContext(genreIds, string(detail, "original_language"), countries);
        }

        boolean isEmpty() {
            return genreIds.isEmpty() && isBlank(originalLanguage) && originCountries.isEmpty();
        }

        private static JsonArray array(JsonObject object, String key) {
            if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonArray()) return new JsonArray();
            return object.getAsJsonArray(key);
        }

        private static String string(JsonObject object, String key) {
            if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) return "";
            String value = object.get(key).getAsString();
            return value == null ? "" : value.trim();
        }

        private static void addCountry(Set<String> countries, JsonElement element) {
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return;
            String value = element.getAsString();
            if (!isBlank(value)) countries.add(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    static final class RecommendationCandidate {

        TmdbItem item;
        final String key;
        final String normalizedTitle;
        double score;
        final int order;

        RecommendationCandidate(TmdbItem item, String key, String normalizedTitle, double score, int order) {
            this.item = item;
            this.key = nullToEmpty(key);
            this.normalizedTitle = nullToEmpty(normalizedTitle);
            this.score = score;
            this.order = order;
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private static final class TmdbSeedData {

        private final TmdbItem seedItem;
        private final List<TmdbItem> recommendations;
        private final List<TmdbItem> similar;
        private final long time;

        private TmdbSeedData(TmdbItem seedItem, List<TmdbItem> recommendations, List<TmdbItem> similar, long time) {
            this.seedItem = seedItem;
            this.recommendations = recommendations == null ? new ArrayList<>() : new ArrayList<>(recommendations);
            this.similar = similar == null ? new ArrayList<>() : new ArrayList<>(similar);
            this.time = time;
        }
    }

    public static final class RecommendationPage {

        private final List<TmdbItem> items;
        private final int offset;
        private final int nextOffset;
        private final boolean hasMore;
        private final String historyFingerprint;

        RecommendationPage(List<TmdbItem> items, int offset, int nextOffset, boolean hasMore, String historyFingerprint) {
            this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
            this.offset = Math.max(0, offset);
            this.nextOffset = Math.max(this.offset, nextOffset);
            this.hasMore = hasMore;
            this.historyFingerprint = historyFingerprint == null ? "" : historyFingerprint;
        }

        public static RecommendationPage empty(String historyFingerprint) {
            return new RecommendationPage(new ArrayList<>(), 0, 0, false, historyFingerprint);
        }

        public List<TmdbItem> getItems() {
            return new ArrayList<>(items);
        }

        public RecommendationPage withItems(List<TmdbItem> items) {
            return new RecommendationPage(items, offset, nextOffset, hasMore, historyFingerprint);
        }

        public int getOffset() {
            return offset;
        }

        public int getNextOffset() {
            return nextOffset;
        }

        public boolean hasMore() {
            return hasMore;
        }

        public String getHistoryFingerprint() {
            return historyFingerprint;
        }
    }

    public static final class RecommendationPages {

        private final RecommendationPage tmdb;
        private final RecommendationPage douban;
        private final RecommendationPage ai;

        RecommendationPages(RecommendationPage tmdb, RecommendationPage douban, RecommendationPage ai) {
            this.tmdb = tmdb == null ? RecommendationPage.empty("") : tmdb;
            this.douban = douban == null ? RecommendationPage.empty("") : douban;
            this.ai = ai == null ? RecommendationPage.empty("") : ai;
        }

        public static RecommendationPages empty() {
            return new RecommendationPages(RecommendationPage.empty(""), RecommendationPage.empty(""), RecommendationPage.empty(""));
        }

        public RecommendationPage getTmdb() {
            return tmdb;
        }

        public RecommendationPage getDouban() {
            return douban;
        }

        public RecommendationPage getAi() {
            return ai;
        }
    }

    public static final class Recommendations {

        private final List<TmdbItem> tmdb;
        private final List<TmdbItem> douban;
        private final List<TmdbItem> ai;

        Recommendations(List<TmdbItem> tmdb, List<TmdbItem> douban, List<TmdbItem> ai) {
            this.tmdb = tmdb == null ? new ArrayList<>() : tmdb;
            this.douban = douban == null ? new ArrayList<>() : douban;
            this.ai = ai == null ? new ArrayList<>() : ai;
        }

        static Recommendations empty() {
            return new Recommendations(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        public List<TmdbItem> getTmdb() {
            return tmdb;
        }

        public List<TmdbItem> getDouban() {
            return douban;
        }

        public List<TmdbItem> getAi() {
            return ai;
        }

        public boolean isEmpty() {
            return tmdb.isEmpty() && douban.isEmpty() && ai.isEmpty();
        }
    }

    static final class DoubanRatingMemoryCache {

        private final int maxSize;
        private final long ttlMillis;
        private final LinkedHashMap<String, CacheEntry> values;

        DoubanRatingMemoryCache(int maxSize, long ttlMillis) {
            this.maxSize = Math.max(1, maxSize);
            this.ttlMillis = Math.max(1L, ttlMillis);
            this.values = new LinkedHashMap<>(16, 0.75f, true);
        }

        synchronized DoubanRating get(String key, long now) {
            CacheEntry entry = values.get(key);
            if (entry == null) return null;
            if (now - entry.cachedAt <= ttlMillis) return entry.rating;
            values.remove(key);
            return null;
        }

        synchronized void put(String key, DoubanRating rating, long now) {
            if (isBlank(key) || rating == null || rating.isEmpty()) return;
            values.put(key, new CacheEntry(rating, now));
            while (values.size() > maxSize) values.remove(values.keySet().iterator().next());
        }

        private static final class CacheEntry {

            private final DoubanRating rating;
            private final long cachedAt;

            private CacheEntry(DoubanRating rating, long cachedAt) {
                this.rating = rating;
                this.cachedAt = cachedAt;
            }
        }
    }


    public static final class DoubanRating {

        private static final DoubanRating EMPTY = new DoubanRating("", "", "", 0, 0.0);

        private final String id;
        private final String title;
        private final String mediaType;
        private final int year;
        private final double rating;

        private DoubanRating(String id, String title, String mediaType, int year, double rating) {
            this.id = id == null ? "" : id.trim();
            this.title = title == null ? "" : title.trim();
            this.mediaType = mediaType == null ? "" : mediaType.trim();
            this.year = year;
            this.rating = rating;
        }

        static DoubanRating from(DoubanSubject subject) {
            if (subject == null || subject.rating <= 0) return empty();
            return new DoubanRating(subject.id, subject.title, subject.mediaType, subject.year, subject.rating);
        }

        public static DoubanRating empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return rating <= 0 || isBlank(title);
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getMediaType() {
            return mediaType;
        }

        public int getYear() {
            return year;
        }

        public double getRating() {
            return rating;
        }
    }

    static final class DoubanSubject {

        final String id;
        final String title;
        final String mediaType;
        final int year;
        final String posterUrl;
        final double rating;

        private DoubanSubject(String id, String title, String mediaType, int year, String posterUrl, double rating) {
            this.id = nullToEmpty(id);
            this.title = nullToEmpty(title);
            this.mediaType = nullToEmpty(mediaType);
            this.year = year;
            this.posterUrl = nullToEmpty(posterUrl);
            this.rating = rating;
        }

        static DoubanSubject from(@NonNull JsonObject object) {
            String title = string(object, "title", "name");
            if (isBlank(title)) return null;
            String type = mediaType(string(object, "type", "subtype"));
            if ("movie".equals(type) && isTvLike(object)) type = "tv";
            int year = firstYear(string(object, "year", "release_year", "sub_title", "card_subtitle"));
            if (year == 0) year = firstYear(title);
            String poster = highResPoster(poster(object));
            return new DoubanSubject(string(object, "id"), title, type, year, poster, rating(object));
        }

        static DoubanSubject fromSearchItem(@NonNull JsonObject item) {
            String layout = string(item, "layout");
            if (!isBlank(layout) && !"subject".equals(layout)) return null;
            JsonObject target = jsonObject(item, "target");
            if (target == null) return from(item);
            JsonObject subject = target.deepCopy();
            String targetType = string(item, "target_type");
            if (!isBlank(targetType) && isBlank(string(subject, "type", "subtype"))) subject.addProperty("type", targetType);
            return from(subject);
        }

        TmdbItem toTmdbItem() {
            return new TmdbItem(doubanIntId(), mediaType, title, subtitle(), "", posterUrl, "", "", rating, "", "", new ArrayList<>(), "", 0.0, rating);
        }

        private int doubanIntId() {
            if (!isBlank(id)) {
                try {
                    long value = Long.parseLong(id);
                    if (value > 0 && value <= Integer.MAX_VALUE) return (int) -value;
                } catch (NumberFormatException ignored) {
                }
            }
            return -Math.abs((title + year).hashCode());
        }

        private String subtitle() {
            List<String> parts = new ArrayList<>();
            parts.add("tv".equals(mediaType) ? "剧集" : "电影");
            if (year > 0) parts.add(String.valueOf(year));
            if (rating > 0) parts.add(String.format(Locale.US, "评分 %.1f", rating));
            return String.join(" · ", parts);
        }

        private static String mediaType(String value) {
            String type = nullToEmpty(value).toLowerCase(Locale.ROOT);
            if (type.contains("tv") || type.contains("series") || type.contains("电视剧") || type.contains("剧集") || type.contains("劇集") || type.contains("电视")) return "tv";
            return "movie";
        }

        private static boolean isTvLike(JsonObject object) {
            if (bool(object, "is_tv") || number(object, "episode", "episodes_count") > 0) return true;
            JsonArray labels = jsonArray(object, "labels");
            if (labels != null) {
                for (JsonElement element : labels) {
                    String label = element != null && element.isJsonPrimitive()
                            ? element.getAsString()
                            : element != null && element.isJsonObject()
                            ? string(element.getAsJsonObject(), "text", "name", "title")
                            : "";
                    if ("tv".equals(mediaType(label))) return true;
                }
            }
            String moreUrl = string(object, "more_url").toLowerCase(Locale.ROOT);
            return moreUrl.contains("is_tv:'1'")
                    || moreUrl.contains("is_tv%3a%271%27")
                    || moreUrl.contains("is_tv=1");
        }

        private static int firstYear(String text) {
            Matcher matcher = YEAR.matcher(Objects.toString(text, ""));
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
        }

        private static String highResPoster(String url) {
            String value = nullToEmpty(url);
            if (value.contains("s_ratio_poster")) return value.replace("s_ratio_poster", "m_ratio_poster");
            return value;
        }

        private static String poster(JsonObject object) {
            String poster = string(object, "img", "cover_url", "cover");
            if (!isBlank(poster)) return poster;
            JsonObject pic = jsonObject(object, "pic");
            if (pic != null) return string(pic, "large", "normal", "url");
            return "";
        }

        private static double rating(JsonObject object) {
            double value = number(object, "rating", "rate");
            if (value > 0) return value;
            JsonObject rating = jsonObject(object, "rating");
            return rating == null ? 0.0 : number(rating, "value", "average", "rating");
        }

        private static String string(JsonObject object, String... keys) {
            for (String key : keys) {
                if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) continue;
                String value = object.get(key).getAsString();
                if (!isBlank(value)) return value.trim();
            }
            return "";
        }

        private static double number(JsonObject object, String... keys) {
            for (String key : keys) {
                if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) continue;
                try {
                    String value = object.get(key).getAsString();
                    if (!isBlank(value)) return Double.parseDouble(value);
                } catch (Throwable ignored) {
                }
            }
            return 0.0;
        }

        private static boolean bool(JsonObject object, String key) {
            if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) return false;
            try {
                return object.get(key).getAsBoolean();
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static JsonObject jsonObject(JsonObject object, String key) {
            if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonObject()) return null;
            return object.getAsJsonObject(key);
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
