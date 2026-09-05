package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PersonalRecommendationServiceTest {

    @Test
    public void enrichTmdbRatings_addsDoubanRatingWithoutDroppingTmdbMetadata() {
        PersonalRecommendationService service = new PersonalRecommendationService(null, null) {
            @Override
            public DoubanRating loadDoubanRating(String title, String mediaType, int year) {
                return parseDoubanSubjectAbstract("{\"r\":0,\"subject\":{\"id\":\"100\",\"title\":\"测试影片 (2024)\",\"is_tv\":false,\"release_year\":\"2024\",\"rate\":\"8.8\"}}");
            }
        };
        TmdbItem original = new TmdbItem(
                1, "movie", "测试影片", "2024", "已有简介", "poster.jpg", "backdrop.jpg",
                "主演", 8.1, "zh", "CN", List.of(18), "Acting");

        List<TmdbItem> enriched = service.enrichTmdbRatings(List.of(original));

        assertEquals(1, enriched.size());
        assertEquals(8.1, enriched.get(0).getTmdbRating(), 0.01);
        assertEquals(8.8, enriched.get(0).getDoubanRating(), 0.01);
        assertEquals("已有简介", enriched.get(0).getOverview());
        assertEquals("poster.jpg", enriched.get(0).getPosterUrl());
        assertEquals("backdrop.jpg", enriched.get(0).getBackdropUrl());
        assertEquals(List.of(18), enriched.get(0).getGenreIds());
    }

    @Test
    public void enrichTmdbRatingsAsync_limitsColdLookupsToVisiblePageAndReturnsFullList() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        PersonalRecommendationService service = new PersonalRecommendationService(null, null) {
            @Override
            public DoubanRating loadDoubanRating(String title, String mediaType, int year) {
                lookups.incrementAndGet();
                return parseDoubanSubjectAbstract("{\"subject\":{\"id\":\"100\",\"title\":\"" + title + "\",\"is_tv\":false,\"release_year\":\"2024\",\"rate\":\"8.8\"}}");
            }
        };
        List<TmdbItem> items = new ArrayList<>();
        for (int index = 0; index < PersonalRecommendationService.DEFAULT_PAGE_SIZE + 3; index++) {
            items.add(new TmdbItem(index + 1, "movie", "影片" + index, "2024", "", "", ""));
        }
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<List<TmdbItem>> result = new AtomicReference<>();

        service.enrichTmdbRatingsAsync(items, enriched -> {
            result.set(enriched);
            completed.countDown();
        });

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals(PersonalRecommendationService.DEFAULT_PAGE_SIZE, lookups.get());
        assertEquals(items.size(), result.get().size());
        assertEquals(8.8, result.get().get(PersonalRecommendationService.DEFAULT_PAGE_SIZE - 1).getDoubanRating(), 0.01);
        assertEquals(0.0, result.get().get(PersonalRecommendationService.DEFAULT_PAGE_SIZE).getDoubanRating(), 0.01);
    }

    @Test
    public void parseDoubanSubjects_readsStructuredSuggestItems() {
        String body = "[{\"id\":\"1291843\",\"title\":\"The Matrix\",\"type\":\"movie\",\"year\":\"1999\",\"img\":\"https://img1.doubanio.com/view/photo/s_ratio_poster/public/p451926968.jpg\"}]";

        List<PersonalRecommendationService.DoubanSubject> subjects = PersonalRecommendationService.parseDoubanSubjects(body);

        assertEquals(1, subjects.size());
        PersonalRecommendationService.DoubanSubject subject = subjects.get(0);
        assertEquals("1291843", subject.id);
        assertEquals("The Matrix", subject.title);
        assertEquals("movie", subject.mediaType);
        assertEquals(1999, subject.year);
        assertTrue(subject.posterUrl.contains("m_ratio_poster"));
    }

    @Test
    public void parseDoubanSubjects_readsRexxarSearchSubjects() {
        String body = "{"
                + "\"smart_box\":[{\"layout\":\"subject\",\"target_type\":\"tv\",\"target\":{"
                + "\"id\":\"3016187\",\"title\":\"权力的游戏 第一季\",\"year\":\"2011\","
                + "\"cover_url\":\"https://img.example/poster.jpg\",\"rating\":{\"value\":9.5}}}],"
                + "\"subjects\":{\"items\":[{\"layout\":\"subject\",\"target_type\":\"movie\",\"target\":{"
                + "\"id\":\"1291843\",\"title\":\"黑客帝国\",\"year\":\"1999\",\"rating\":{\"value\":9.1}}},"
                + "{\"layout\":\"chart\",\"target_type\":\"chart\",\"target\":{\"id\":\"list\",\"title\":\"片单\"}}]}}";

        List<PersonalRecommendationService.DoubanSubject> subjects = PersonalRecommendationService.parseDoubanSubjects(body);

        assertEquals(2, subjects.size());
        assertEquals("3016187", subjects.get(0).id);
        assertEquals("tv", subjects.get(0).mediaType);
        assertEquals(2011, subjects.get(0).year);
        assertEquals(9.5, subjects.get(0).rating, 0.01);
        assertEquals("1291843", subjects.get(1).id);
        assertEquals("movie", subjects.get(1).mediaType);
        assertEquals(9.1, subjects.get(1).rating, 0.01);
    }

    @Test
    public void parseDoubanSearchPage_readsWindowDataRatingsAndTvMetadata() {
        String body = "<html><script>window.__DATA__ = {\"items\":[{"
                + "\"id\":\"36156235\","
                + "\"title\":\"重启人生 ブラッシュアップライフ‎ (2023)\","
                + "\"cover_url\":\"https://img.example/reboot.jpg\","
                + "\"labels\":[{\"text\":\"剧集\"}],"
                + "\"rating\":{\"value\":9.4}"
                + "}]}; window.__USER__ = {\"name\":\"tester\"};</script></html>";

        List<PersonalRecommendationService.DoubanSubject> subjects =
                PersonalRecommendationService.parseDoubanSearchPage(body);

        assertEquals(1, subjects.size());
        PersonalRecommendationService.DoubanSubject subject = subjects.get(0);
        assertEquals("36156235", subject.id);
        assertEquals("tv", subject.mediaType);
        assertEquals(2023, subject.year);
        assertEquals(9.4, subject.rating, 0.01);
        assertEquals("https://img.example/reboot.jpg", subject.posterUrl);
    }

    @Test
    public void isCacheableDoubanResponse_rejectsSearchRateLimitPages() {
        String blocked = "<script>window.__DATA__ = {\"error_info\":\"搜索访问太频繁。\",\"items\":[]};</script>";
        String valid = "<script>window.__DATA__ = {\"items\":[{\"id\":\"1\",\"title\":\"测试\"}]};</script>";

        assertFalse(PersonalRecommendationService.isCacheableDoubanResponse(blocked));
        assertTrue(PersonalRecommendationService.isCacheableDoubanResponse(valid));
    }

    @Test
    public void isCacheableDoubanResponse_rejectsApiErrorPayloads() {
        String blocked = "{\"request\":\"GET /v2/search\",\"msg\":\"need_login\",\"code\":103}";
        String valid = "[{\"id\":\"1\",\"title\":\"测试\"}]";

        assertFalse(PersonalRecommendationService.isCacheableDoubanResponse(blocked));
        assertTrue(PersonalRecommendationService.isCacheableDoubanResponse(valid));
    }

    @Test
    public void shouldCoolDownDoubanRequests_onlyForPrimaryRateLimits() {
        assertTrue(PersonalRecommendationService.shouldCoolDownDoubanRequests("suggest", 403));
        assertTrue(PersonalRecommendationService.shouldCoolDownDoubanRequests("search_page", 429));
        assertFalse(PersonalRecommendationService.shouldCoolDownDoubanRequests("search_api", 403));
        assertFalse(PersonalRecommendationService.shouldCoolDownDoubanRequests("suggest", 500));
    }

    @Test
    public void doubanRequestDelay_spacesNetworkRequestStarts() {
        assertEquals(0L, PersonalRecommendationService.doubanRequestDelay(2_000L, 0L, 750L));
        assertEquals(250L, PersonalRecommendationService.doubanRequestDelay(1_500L, 1_000L, 750L));
        assertEquals(0L, PersonalRecommendationService.doubanRequestDelay(1_750L, 1_000L, 750L));
    }

    @Test
    public void serializeDoubanRequest_allowsOnlyOneColdRequestAtATime() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 4; index++) {
                final int value = index;
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(1, TimeUnit.SECONDS));
                    return PersonalRecommendationService.serializeDoubanRequest(() -> {
                        int current = active.incrementAndGet();
                        maxActive.updateAndGet(previous -> Math.max(previous, current));
                        try {
                            Thread.sleep(30L);
                            return value;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return -1;
                        } finally {
                            active.decrementAndGet();
                        }
                    });
                }));
            }

            start.countDown();
            for (int index = 0; index < futures.size(); index++) {
                assertEquals(index, futures.get(index).get(2, TimeUnit.SECONDS).intValue());
            }
            assertEquals(1, maxActive.get());
        } finally {
            executor.shutdownNow();
        }
    }
    @Test
    public void parseDoubanRelatedSubjects_readsRexxarRelatedItems() {
        String body = "{"
                + "\"subjects\":[{"
                + "\"id\":\"1292720\","
                + "\"title\":\"阿甘正传\","
                + "\"type\":\"movie\","
                + "\"card_subtitle\":\"1994 / 美国 / 剧情\","
                + "\"pic\":{\"large\":\"https://img1.doubanio.com/view/photo/m_ratio_poster/public/p2372307693.jpg\"},"
                + "\"rating\":{\"value\":9.5}"
                + "},{"
                + "\"id\":\"30391241\","
                + "\"title\":\"想见你\","
                + "\"type\":\"tv\","
                + "\"card_subtitle\":\"2019 / 中国台湾 / 爱情\","
                + "\"cover_url\":\"https://img9.doubanio.com/view/photo/s_ratio_poster/public/p2576977981.jpg\","
                + "\"rating\":{\"value\":\"9.2\"}"
                + "}]}";

        List<PersonalRecommendationService.DoubanSubject> subjects = PersonalRecommendationService.parseDoubanRelatedSubjects(body);

        assertEquals(2, subjects.size());
        assertEquals("1292720", subjects.get(0).id);
        assertEquals("movie", subjects.get(0).mediaType);
        assertEquals(1994, subjects.get(0).year);
        assertEquals(9.5, subjects.get(0).rating, 0.01);
        assertEquals("30391241", subjects.get(1).id);
        assertEquals("tv", subjects.get(1).mediaType);
        assertEquals(2019, subjects.get(1).year);
        assertTrue(subjects.get(1).posterUrl.contains("m_ratio_poster"));
    }

    @Test
    public void parseDoubanSubjects_marksEpisodeSuggestItemAsTv() {
        String body = "[{\"id\":\"30468961\",\"title\":\"想见你\",\"type\":\"movie\",\"year\":\"2019\",\"episode\":\"13\"}]";

        List<PersonalRecommendationService.DoubanSubject> subjects = PersonalRecommendationService.parseDoubanSubjects(body);

        assertEquals(1, subjects.size());
        assertEquals("tv", subjects.get(0).mediaType);
    }

    @Test
    public void parseDoubanSubjectAbstract_readsCurrentRating() {
        String body = "{"
                + "\"r\":0,"
                + "\"subject\":{"
                + "\"id\":\"30468961\","
                + "\"title\":\"想见你 想見你‎ (2019)\","
                + "\"is_tv\":true,"
                + "\"release_year\":\"2019\","
                + "\"rate\":\"9.3\""
                + "}}";

        PersonalRecommendationService.DoubanRating rating = PersonalRecommendationService.parseDoubanSubjectAbstract(body);

        assertFalse(rating.isEmpty());
        assertEquals("30468961", rating.getId());
        assertEquals("tv", rating.getMediaType());
        assertEquals(2019, rating.getYear());
        assertEquals(9.3, rating.getRating(), 0.01);
    }

    @Test
    public void doubanRatingMemoryCache_reusesFreshRatingAndExpiresIt() {
        PersonalRecommendationService.DoubanRatingMemoryCache cache =
                new PersonalRecommendationService.DoubanRatingMemoryCache(2, 1_000L);
        PersonalRecommendationService.DoubanRating rating = PersonalRecommendationService.parseDoubanSubjectAbstract(
                "{\"subject\":{\"id\":\"1295644\",\"title\":\"这个杀手不太冷\",\"type\":\"movie\",\"release_year\":\"1994\",\"rate\":\"9.4\"}}");

        cache.put("movie|这个杀手不太冷|1994", rating, 100L);

        assertSame(rating, cache.get("movie|这个杀手不太冷|1994", 1_099L));
        assertNull(cache.get("movie|这个杀手不太冷|1994", 1_101L));
    }

    @Test
    public void bestDoubanRating_prefersCurrentTitleYearAndType() {
        String body = "[{"
                + "\"id\":\"1292720\","
                + "\"title\":\"想见你电影版\","
                + "\"type\":\"movie\","
                + "\"year\":\"2022\","
                + "\"rating\":{\"value\":9.9}"
                + "},{"
                + "\"id\":\"30391241\","
                + "\"title\":\"想见你\","
                + "\"type\":\"tv\","
                + "\"year\":\"2019\","
                + "\"rating\":{\"value\":\"9.2\"}"
                + "}]";

        PersonalRecommendationService.DoubanRating rating = PersonalRecommendationService.bestDoubanRating(
                "想见你", "tv", 2019, PersonalRecommendationService.parseDoubanSubjects(body));

        assertFalse(rating.isEmpty());
        assertEquals("30391241", rating.getId());
        assertEquals("想见你", rating.getTitle());
        assertEquals("tv", rating.getMediaType());
        assertEquals(2019, rating.getYear());
        assertEquals(9.2, rating.getRating(), 0.01);
    }

    @Test
    public void withRating_addsFetchedDoubanRatingToRecommendationItem() {
        TmdbItem item = new TmdbItem(-35316582, "tv", "折腰", "剧集 · 2025", "", "poster", "", "", 0.0);

        TmdbItem rated = PersonalRecommendationService.withRating(item, 6.7);

        assertEquals("折腰", rated.getTitle());
        assertEquals("剧集 · 2025", rated.getSubtitle());
        assertEquals("poster", rated.getPosterUrl());
        assertEquals(6.7, rated.getRating(), 0.01);
    }

    @Test
    public void withRating_preservesTmdbRatingAndAddsDoubanRating() {
        TmdbItem item = new TmdbItem(123, "movie", "测试电影", "电影 · 2025", "", "poster", "", "", 8.1);

        TmdbItem rated = PersonalRecommendationService.withRating(item, 8.7);

        assertEquals(8.1, rated.getRating(), 0.01);
        assertEquals(8.1, rated.getTmdbRating(), 0.01);
        assertEquals(8.7, rated.getDoubanRating(), 0.01);
    }

    @Test
    public void withRating_preservesLegacyCachedTmdbRatingWhenAddingDouban() {
        TmdbItem legacy = new Gson().fromJson(
                "{\"tmdbId\":123,\"mediaType\":\"movie\",\"title\":\"旧缓存\",\"rating\":8.1,\"genreIds\":[]}",
                TmdbItem.class);

        TmdbItem rated = PersonalRecommendationService.withRating(legacy, 8.7);

        assertEquals(8.1, rated.getRating(), 0.01);
        assertEquals(8.1, rated.getTmdbRating(), 0.01);
        assertEquals(8.7, rated.getDoubanRating(), 0.01);
    }

    @Test
    public void rankCandidates_sortsByScoreAndBoostsDuplicateSignals() {
        List<PersonalRecommendationService.RecommendationCandidate> candidates = List.of(
                new PersonalRecommendationService.RecommendationCandidate(null, "movie:1", "low", 70.0, 0),
                new PersonalRecommendationService.RecommendationCandidate(null, "movie:2", "high", 92.0, 1),
                new PersonalRecommendationService.RecommendationCandidate(null, "movie:1", "low", 90.0, 2)
        );

        List<PersonalRecommendationService.RecommendationCandidate> ranked = PersonalRecommendationService.rankCandidates(candidates, 2);

        assertEquals(2, ranked.size());
        assertEquals("movie:1", ranked.get(0).key);
        assertEquals("movie:2", ranked.get(1).key);
    }

    @Test
    public void mergeTmdbPersonalCandidates_keepsHistoryWhenCurrentCandidatesOverflow() {
        List<PersonalRecommendationService.RecommendationCandidate> current = List.of(
                new PersonalRecommendationService.RecommendationCandidate(item(1, "Current 1"), "movie:1", "current1", 130.0, 0),
                new PersonalRecommendationService.RecommendationCandidate(item(2, "Current 2"), "movie:2", "current2", 129.0, 1),
                new PersonalRecommendationService.RecommendationCandidate(item(3, "Current 3"), "movie:3", "current3", 128.0, 2)
        );
        List<PersonalRecommendationService.RecommendationCandidate> history = List.of(
                new PersonalRecommendationService.RecommendationCandidate(item(9, "History"), "movie:9", "history", 80.0, 3)
        );

        List<PersonalRecommendationService.RecommendationCandidate> ranked = PersonalRecommendationService.mergeTmdbPersonalCandidates(current, history, 3, 1);

        assertEquals(3, ranked.size());
        assertTrue(ranked.stream().anyMatch(candidate -> "movie:9".equals(candidate.key)));
    }

    @Test
    public void mergeTmdbPersonalCandidates_prioritizesHistoryOverCurrentContext() {
        List<PersonalRecommendationService.RecommendationCandidate> current = List.of(
                new PersonalRecommendationService.RecommendationCandidate(item(1, "Current"), "movie:1", "current", 140.0, 0)
        );
        List<PersonalRecommendationService.RecommendationCandidate> history = List.of(
                new PersonalRecommendationService.RecommendationCandidate(item(9, "History"), "movie:9", "history", 80.0, 1)
        );

        List<PersonalRecommendationService.RecommendationCandidate> ranked = PersonalRecommendationService.mergeTmdbPersonalCandidates(current, history, 2, 1);

        assertEquals("movie:9", ranked.get(0).key);
        assertEquals("movie:1", ranked.get(1).key);
    }

    @Test
    public void rankTmdbItemsForContext_prefersGenreLanguageAndCountryMatches() {
        JsonObject detail = JsonParser.parseString("{"
                + "\"genres\":[{\"id\":9648,\"name\":\"悬疑\"}],"
                + "\"original_language\":\"ko\","
                + "\"origin_country\":[\"KR\"]"
                + "}").getAsJsonObject();
        TmdbItem highRated = item(1, "High Rated", 9.6, "en", "US", List.of(35));
        TmdbItem contextual = item(2, "Contextual", 7.0, "ko", "KR", List.of(9648));

        List<TmdbItem> ranked = PersonalRecommendationService.rankTmdbItemsForContext(detail, List.of(highRated, contextual), new ArrayList<>(), 2);

        assertEquals("Contextual", ranked.get(0).getTitle());
    }

    @Test
    public void recommendationPage_slicesRankedItemsWithoutLegacyCap() {
        List<PersonalRecommendationService.RecommendationCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            candidates.add(new PersonalRecommendationService.RecommendationCandidate(item(i + 1, "Item " + i), "movie:" + i, "item" + i, 100 - i, i));
        }

        PersonalRecommendationService.RecommendationPage page = PersonalRecommendationService.pageItems(
                PersonalRecommendationService.rankCandidates(candidates, 20), 12, 6, "history", false);

        assertEquals(6, page.getItems().size());
        assertEquals("Item 12", page.getItems().get(0).getTitle());
        assertEquals("Item 17", page.getItems().get(5).getTitle());
        assertEquals(18, page.getNextOffset());
        assertTrue(page.hasMore());
    }

    @Test
    public void recommendationPage_reportsMoreWhenSeedScanCanContinue() {
        List<PersonalRecommendationService.RecommendationCandidate> candidates = List.of(
                new PersonalRecommendationService.RecommendationCandidate(item(1, "Only"), "movie:1", "only", 100, 0)
        );

        PersonalRecommendationService.RecommendationPage page = PersonalRecommendationService.pageItems(
                PersonalRecommendationService.rankCandidates(candidates, 20), 12, 6, "history", true);

        assertTrue(page.getItems().isEmpty());
        assertEquals(12, page.getNextOffset());
        assertTrue(page.hasMore());
    }

    @Test
    public void tmdbHistoryCandidates_stopsAfterAuthenticationFailure() {
        AtomicInteger searches = new AtomicInteger();
        TmdbService tmdbService = new TmdbService() {
            @Override
            public List<TmdbItem> search(String keyword, TmdbConfig config) {
                searches.incrementAndGet();
                throw new TmdbService.AuthException(401, "TMDB search failed: HTTP 401");
            }
        };
        PersonalRecommendationService service = new PersonalRecommendationService(
                tmdbService, TmdbConfig.objectFrom("{\"apiKey\":\"invalid\"}"));

        assertThrows(TmdbService.AuthException.class, () -> service.tmdbHistoryCandidates(
                List.of("auth-seed-1", "auth-seed-2", "auth-seed-3"),
                3,
                new HashSet<>(),
                new ArrayList<>(),
                null,
                null));
        assertEquals(1, searches.get());
    }

    @Test
    public void historySeedFingerprint_changesForSeedSetChangesOnly() {
        String base = PersonalRecommendationService.historySeedFingerprint(List.of("A", "B"));
        String added = PersonalRecommendationService.historySeedFingerprint(List.of("A", "B", "C"));
        String renamed = PersonalRecommendationService.historySeedFingerprint(List.of("A", "B2"));
        String sameAfterDuplicate = PersonalRecommendationService.historySeedFingerprint(List.of("A", "B", "A"));

        assertFalse(base.equals(added));
        assertFalse(base.equals(renamed));
        assertEquals(base, sameAfterDuplicate);
    }

    @Test
    public void normalizeTitle_removesCommonSeparators() {
        assertEquals("thematrix1999", PersonalRecommendationService.normalizeTitle("The Matrix (1999)"));
    }

    @Test
    public void shouldUseHistorySeed_filtersAudioSourcesForTmdbAndDouban() {
        PersonalRecommendationService.SourceClassifier classifier = new PersonalRecommendationService.SourceClassifier() {
            @Override
            public boolean isAudio(String siteKey, String siteName) {
                return true;
            }

            @Override
            public boolean isShortDrama(String siteKey, String siteName) {
                return false;
            }
        };

        assertFalse(PersonalRecommendationService.shouldUseHistorySeed("fm", "凤凰FM[听]", false, classifier));
        assertFalse(PersonalRecommendationService.shouldUseHistorySeed("fm", "凤凰FM[听]", true, classifier));
    }

    @Test
    public void shouldUseHistorySeed_filtersShortDramaSourcesOnlyForTmdb() {
        PersonalRecommendationService.SourceClassifier classifier = new PersonalRecommendationService.SourceClassifier() {
            @Override
            public boolean isAudio(String siteKey, String siteName) {
                return false;
            }

            @Override
            public boolean isShortDrama(String siteKey, String siteName) {
                return true;
            }
        };

        assertTrue(PersonalRecommendationService.shouldUseHistorySeed("mini", "荐片[APP]", false, classifier));
        assertFalse(PersonalRecommendationService.shouldUseHistorySeed("mini", "荐片[APP]", true, classifier));
    }

    private static TmdbItem item(int id, String title) {
        return item(id, title, 0.0, "", "", new ArrayList<>());
    }

    private static TmdbItem item(int id, String title, double rating, String language, String country, List<Integer> genres) {
        return new TmdbItem(id, "movie", title, "", "", "", "", "", rating, language, country, genres);
    }
}
