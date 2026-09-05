package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbSeasonMatchCache;
import com.fongmi.android.tv.bean.TmdbSeasonScope;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.Task;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TmdbUIAdapterTest {

    @Test
    public void applyTmdbTitle_updatesVodNameToScrapedTitle() {
        Vod vod = new FakeVod();
        vod.setName("源站标题");
        TmdbItem item = new TmdbItem(123, "tv", "刮削后的标题", "", "", "", "");

        assertTrue(TmdbUIAdapter.applyTmdbTitle(vod, item));

        assertEquals("刮削后的标题", vod.getName());
    }

    @Test
    public void applyTmdbTitle_keepsExplicitSourceSeason() {
        Vod vod = new FakeVod();
        vod.setName("乐高幻影忍者：神龙崛起第三季");
        TmdbItem item = new TmdbItem(123, "tv", "乐高幻影忍者：神龙崛起", "", "", "", "");

        assertTrue(TmdbUIAdapter.applyTmdbTitle(vod, item));

        assertEquals("乐高幻影忍者：神龙崛起第三季", vod.getName());
    }

    @Test
    public void stableFlagKeyIncludesIndexForDuplicateDisplayNames() {
        Flag first = new Flag("全集");
        Flag second = new Flag("全集");

        assertEquals("全集#0", TmdbUIAdapter.flagKey(first, 0));
        assertEquals("全集#1", TmdbUIAdapter.flagKey(second, 1));
        assertEquals(1, TmdbUIAdapter.flagIndex(List.of(first, second), second));
    }

    @Test
    public void stableFlagKeyDisambiguatesDuplicateNameAndEpisodeUrl() {
        Flag first = Flag.create("same", "E1$same-url");
        Flag second = Flag.create("same", "E1$same-url");

        assertSame(second, TmdbUIAdapter.selectPlaybackFlag(
                List.of(first, second), "same#1", "same-url", "same"));
    }

    @Test
    public void staleStableFlagKeyDoesNotFallBackToAmbiguousEpisodeUrl() {
        Flag first = Flag.create("same", "E1$same-url");
        Flag second = Flag.create("same", "E1$same-url");

        assertNull(TmdbUIAdapter.selectPlaybackFlag(
                List.of(first, second), "same#9", "same-url", "same"));
    }

    @Test
    public void reorderedStableFlagKeyFallsBackToUniqueEpisodeUrl() {
        Flag movedTarget = Flag.create("same", "E1$target-url");
        Flag oldIndexNowPointsElsewhere = Flag.create("same", "E1$other-url");

        assertSame(movedTarget, TmdbUIAdapter.selectPlaybackFlag(
                List.of(movedTarget, oldIndexNowPointsElsewhere),
                "same#1", "target-url", "same"));
    }

    @Test
    public void explicitMultiSeasonSegmentsApplySecondSeasonMetadata() {
        List<Episode> source = List.of(
                Episode.create("S01E01", "s1e1"),
                Episode.create("S01E02", "s1e2"),
                Episode.create("S01E03", "s1e3"),
                Episode.create("S02E01", "s2e1"),
                Episode.create("S02E02", "s2e2"));
        TmdbSeasonResolver.Resolution resolution = TmdbSeasonResolver.resolve(
                -1, null, List.of(1, 2), -1,
                List.of(1, 2), Map.of(1, 3, 2, 2), 5,
                List.of(1, 2, 3, 1, 2),
                List.of(1, 1, 1, 2, 2));
        Map<Integer, Map<Integer, TmdbEpisode>> metadata = Map.of(
                1, Map.of(
                        1, new TmdbEpisode(1, "", "", "", "", 0, 0, 101, 1),
                        2, new TmdbEpisode(2, "", "", "", "", 0, 0, 102, 1),
                        3, new TmdbEpisode(3, "", "", "", "", 0, 0, 103, 1)),
                2, Map.of(
                        1, new TmdbEpisode(1, "", "", "", "", 0, 0, 201, 2),
                        2, new TmdbEpisode(2, "", "", "", "", 0, 0, 202, 2)));

        assertTrue(TmdbUIAdapter.applySegmentedEpisodeMetadata(source, metadata, resolution.getSegments()));
        assertEquals(1, source.get(0).getTmdbEpisode().getSeasonNumber());
        assertEquals(2, source.get(3).getTmdbEpisode().getSeasonNumber());
        assertEquals(1, source.get(3).getTmdbEpisode().getNumber());
        // 分段已通过 shouldApplyMapped 校验，必须标记 mapped，
        // 否则第二季分段的源集号与本季集号不同，会被严格匹配当成无效匹配而丢掉刮削结果
        assertTrue(source.get(3).isTmdbEpisodeMapped());
        assertTrue(EpisodeDisplayPolicy.hasTmdbEpisodeData(List.of(source.get(3))));
        assertTrue(TmdbEpisodeMatcher.shouldApply(source.get(3), source.get(3).getTmdbEpisode()));
    }

    @Test
    public void staleDuplicateFlagReferenceResolvesByStableIdentityKey() {
        Flag staleSecond = new Flag("same");
        Flag first = new Flag("same");
        Flag second = new Flag("same");

        assertSame(second, TmdbUIAdapter.resolveActiveFlag(
                List.of(first, second), staleSecond, TmdbUIAdapter.flagKey(staleSecond, 1)));
    }

    @Test
    public void clearingActiveMetadataLeavesInactiveFlagUntouched() {
        Flag active = Flag.create("same", "E1$a1");
        Flag inactive = Flag.create("same", "E1$b1");
        TmdbEpisode activeMetadata = new TmdbEpisode(1, "Active", "", "", "", 0, 0);
        TmdbEpisode inactiveMetadata = new TmdbEpisode(1, "Inactive", "", "", "", 0, 0);
        active.getEpisodes().get(0).setTmdbEpisode(activeMetadata);
        inactive.getEpisodes().get(0).setTmdbEpisode(inactiveMetadata);
        inactive.getEpisodes().get(0).setDisplayName("keep");

        TmdbUIAdapter.clearEpisodeMetadata(active);

        assertNull(active.getEpisodes().get(0).getTmdbEpisode());
        assertSame(inactiveMetadata, inactive.getEpisodes().get(0).getTmdbEpisode());
        assertEquals("keep", inactive.getEpisodes().get(0).getDisplayName());
    }

    @Test
    public void seasonEvidenceAndEpisodeShapeAreScopedToActiveFlag() {
        Flag seasonOne = Flag.create("Season 1", "Episode 1$s1e1#Episode 2$s1e2");
        Flag seasonTwo = Flag.create("Season 2", "Episode 1$s2e1");

        assertEquals(List.of(1), TmdbUIAdapter.explicitSourceSeasons(seasonOne));
        assertEquals(List.of(2), TmdbUIAdapter.explicitSourceSeasons(seasonTwo));
        assertEquals(2, TmdbUIAdapter.sourceEpisodeCount(seasonOne));
        assertEquals(1, TmdbUIAdapter.sourceEpisodeCount(seasonTwo));
        assertEquals(List.of(1), TmdbUIAdapter.sourceEpisodeNumbers(seasonTwo));
    }

    @Test
    public void sourceFingerprintChangesWhenSameSizeEpisodeLineChanges() {
        Flag before = Flag.create("line", "E1$a1#E2$a2");
        Flag after = Flag.create("line", "E2$a2#E1$a1");

        assertNotSame(before, after);
        assertFalse(TmdbUIAdapter.sourceFingerprint(before, "line#0", Map.of(1, 2))
                .equals(TmdbUIAdapter.sourceFingerprint(after, "line#0", Map.of(1, 2))));
    }

    @Test
    public void sourceFingerprintIgnoresVolatilePlaybackUrlTokens() {
        Flag before = Flag.create("line", "E1$https://source.test/e1?token=old");
        Flag after = Flag.create("line", "E1$https://source.test/e1?token=new");

        assertEquals(TmdbUIAdapter.sourceFingerprint(before, "line#0", Map.of(1, 1)),
                TmdbUIAdapter.sourceFingerprint(after, "line#0", Map.of(1, 1)));
    }

    @Test
    public void manualBindingFingerprintIgnoresUnrelatedTmdbSeasonCountChanges() {
        Flag flag = Flag.create("line", "E1$a1#E2$a2");
        String manual = TmdbUIAdapter.manualBindingFingerprint("show", flag, "line#0");
        TmdbSeasonMatchCache.Entry binding = TmdbSeasonMatchCache.Entry.create(
                88, "tv", null, TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE,
                manual, 2, 0);

        assertTrue(binding.isFresh(
                TmdbUIAdapter.manualBindingFingerprint("show", flag, "line#0"), 2, 0));
        assertFalse(TmdbUIAdapter.sourceFingerprint(flag, "line#0", Map.of(1, 2, 2, 10))
                .equals(TmdbUIAdapter.sourceFingerprint(flag, "line#0", Map.of(1, 2, 2, 11))));
    }

    @Test
    public void projectSourceFlagsSeparatesKnownAndMultiSeasonLines() {
        Map<Integer, List<String>> result = TmdbUIAdapter.projectSourceFlags(List.of(
                new TmdbUIAdapter.FlagSeasonBinding("全集#0", TmdbSeasonScope.multi(List.of(1, 2))),
                new TmdbUIAdapter.FlagSeasonBinding("第三季#1", TmdbSeasonScope.known(3))));

        assertEquals(List.of("全集#0"), result.get(1));
        assertEquals(List.of("全集#0"), result.get(2));
        assertEquals(List.of("第三季#1"), result.get(3));
    }

    @Test
    public void tmdbDetailPlaybackHistoryUsesSourceAwareTitle() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        int method = source.indexOf("private String playbackHistoryName()");
        int end = source.indexOf("private String matchedTmdbTitle()", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";

        assertTrue("TMDB detail playback history must preserve an explicit source season",
                body.contains("TmdbUIAdapter.sourceAwareTitle(sourceVodName, matchedTmdbItem, matchedTmdbTitle())"));
    }

    @Test
    public void seasonCacheTitlePrefersStableSourceSignalsOverEnrichedVodTitle() {
        assertEquals("explicit source", TmdbUIAdapter.selectSourceCacheTitle("explicit source", "intent source", "TMDB title"));
        assertEquals("intent source", TmdbUIAdapter.selectSourceCacheTitle("", "intent source", "TMDB title"));
        assertEquals("TMDB title", TmdbUIAdapter.selectSourceCacheTitle("", "", "TMDB title"));
    }

    @Test
    public void staleSeasonBindingIsRemovedWhenMatchedMediaChanges() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        cache.put("site", "vod", "source title", 100, "tv", 2,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fingerprint", 8, 8);

        TmdbItem rematched = new TmdbItem(200, "tv", "new match", "", "", "", "");

        assertTrue(TmdbUIAdapter.removeStaleSeasonBinding(cache, "site", "vod", "source title", rematched));
        assertFalse(TmdbUIAdapter.removeStaleSeasonBinding(cache, "site", "vod", "source title", rematched));
    }

    @Test
    public void episodeLineMappingFallsBackToPositionWhenSourceNumbersRepeat() {
        List<TmdbEpisode> tmdbEpisodes = new ArrayList<>();
        for (int number = 1; number <= 8; number++) {
            tmdbEpisodes.add(new TmdbEpisode(number, "Episode " + number, "", "", "https://image.test/" + number + ".jpg", 0, 0));
        }
        List<Episode> repeatedNumberLine = List.of(
                Episode.create("S01E01", "https://source.test/1"),
                Episode.create("S01E02", "https://source.test/2"),
                Episode.create("S01E03", "https://source.test/3"),
                Episode.create("S01E04", "https://source.test/4"),
                Episode.create("S01E04", "https://source.test/5"),
                Episode.create("S01E05", "https://source.test/6"),
                Episode.create("S01E06", "https://source.test/7"),
                Episode.create("S01E06", "https://source.test/8"));
        List<Episode> reliableLine = List.of(
                Episode.create("E01", "https://other.test/1"),
                Episode.create("E02", "https://other.test/2"),
                Episode.create("E03", "https://other.test/3"),
                Episode.create("E04", "https://other.test/4"),
                Episode.create("E05", "https://other.test/5"),
                Episode.create("E06", "https://other.test/6"),
                Episode.create("E07", "https://other.test/7"),
                Episode.create("E08", "https://other.test/8"));

        assertTrue(TmdbUIAdapter.shouldUseEpisodePosition(repeatedNumberLine, tmdbEpisodes));
        assertEquals(5, TmdbUIAdapter.resolveEpisodeNumber(repeatedNumberLine.get(4), 4, true));
        assertFalse(TmdbUIAdapter.shouldUseEpisodePosition(reliableLine, tmdbEpisodes));
        assertEquals(5, TmdbUIAdapter.resolveEpisodeNumber(reliableLine.get(4), 4, false));

        TmdbEpisode cachedWithoutStill = new TmdbEpisode(5, "Episode 5", "", "", "", 0, 0);
        TmdbEpisode refreshedWithStill = new TmdbEpisode(5, "Episode 5", "", "", "https://image.test/5.jpg", 0, 0);
        assertTrue(TmdbUIAdapter.hasEpisodeMetadataChanged(cachedWithoutStill, refreshedWithStill));
        assertFalse(TmdbUIAdapter.hasEpisodeMetadataChanged(refreshedWithStill, refreshedWithStill));
    }

    @Test
    public void episodeMetadataBindingIndexesTmdbEpisodesOnce() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        int method = source.indexOf("private boolean applyEpisodeTitles(Vod vod, TmdbItem item, int selectedSeason, int generation, int metadataGeneration)");
        int end = source.indexOf("static boolean shouldUseEpisodePosition", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";

        assertTrue(sourcePath + " is missing applyEpisodeTitles", method >= 0);
        assertTrue("TMDB episode metadata must be indexed once before source-line traversal",
                body.contains("Map<Integer, TmdbEpisode> episodesByNumber = indexEpisodesByNumber(episodes);"));
        assertTrue("each source episode must use O(1) number lookup",
                body.contains("TmdbEpisode tmdbEp = episodesByNumber.get(resolvedNumber);"));
        assertFalse("the source episode loop must not linearly rescan TMDB episodes",
                body.contains("findEpisodeByNumber("));
    }

    @Test
    public void removeRecommendationFrom_removesThePersistedModelItem() {
        TmdbItem selected = new TmdbItem(1, "movie", "隐藏作品", "2024", "", "", "");
        List<TmdbItem> recommendations = new ArrayList<>(List.of(
                selected,
                new TmdbItem(2, "movie", "保留作品", "2024", "", "", "")));

        assertTrue(TmdbUIAdapter.removeRecommendationFrom(recommendations,
                new TmdbItem(1, "movie", "隐藏作品", "2024", "", "", "")));
        assertEquals(1, recommendations.size());
        assertEquals("保留作品", recommendations.get(0).getTitle());
    }

    @Test
    public void removeRecommendationFrom_withoutTmdbIdKeepsSameTitleOtherMediaType() {
        TmdbItem movie = new TmdbItem(0, "movie", "同名作品", "电影 · 2024", "", "", "");
        TmdbItem tv = new TmdbItem(0, "tv", "同名作品", "剧集 · 2024", "", "", "");
        List<TmdbItem> recommendations = new ArrayList<>(List.of(movie, tv));

        assertTrue(TmdbUIAdapter.removeRecommendationFrom(recommendations, movie));
        assertEquals(1, recommendations.size());
        assertEquals("tv", recommendations.get(0).getMediaType());
    }

    @Test
    public void mergeRecommendationRatings_updatesExistingItemsWithoutRestoringRemovedItems() {
        TmdbItem kept = new TmdbItem(2, "movie", "保留作品", "2024", "", "", "");
        List<TmdbItem> current = new ArrayList<>(List.of(kept));
        TmdbItem removedWithRating = new TmdbItem(1, "movie", "隐藏作品", "2024", "", "", "", "", 0.0, "", "", List.of(), "", 0.0, 8.1);
        TmdbItem keptWithRating = new TmdbItem(2, "movie", "保留作品", "2024", "", "", "", "", 0.0, "", "", List.of(), "", 0.0, 8.8);

        assertTrue(TmdbUIAdapter.mergeRecommendationRatings(current, List.of(removedWithRating, keptWithRating)));
        assertEquals(1, current.size());
        assertEquals("保留作品", current.get(0).getTitle());
        assertEquals(8.8, current.get(0).getDoubanRating(), 0.01);
    }

    @Test
    public void autoMatchSkipsCachedSplitSeasonVariantBeforeSearching() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int cacheHit = source.indexOf("auto match cache hit");
        int skipCheck = source.indexOf("isCachedSplitSeasonMismatch(videoName, vod, matched)", cacheHit);
        int search = source.indexOf("tmdbMatcher.searchAndMatch(title, vod)", cacheHit);

        assertTrue("TMDB UI adapter must check cached matches for split-season duplicates", skipCheck > cacheHit);
        assertTrue("split-season cache check must run before falling back to TMDB search", search > skipCheck);
        assertTrue("split-season cache check must use TMDB detail original_name/name fields",
                source.contains("TmdbMatchPolicy.isUnwantedSplitSeasonVariant(matchSourceText(videoName, vod), detail)"));
    }

    @Test
    public void autoMatchTriesCleanedTitleCandidatesBeforeAiFallback() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int helper = source.indexOf("private TmdbItem searchResolvedMatch(String videoName, Vod vod, String searchKeyword)");
        int originalSearch = source.indexOf("tmdbMatcher.searchAndMatch(title, vod)", helper);
        int cleaned = source.indexOf("resolver.queryCleanedTitles(request, 4)", originalSearch);
        int aiFallback = source.indexOf("resolver.resolveWithAiFallback(aiRequest)", originalSearch);

        assertTrue(sourcePath + " is missing searchResolvedMatch", helper >= 0);
        assertTrue("TMDB auto match must try code-cleaned title candidates after original candidates",
                cleaned > originalSearch);
        assertTrue("TMDB auto match must try code-cleaned title candidates before AI fallback",
                aiFallback > cleaned);
    }

    @Test
    public void autoMatchUsesSearchKeywordAfterCardNameBeforeCleaningAndAi() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int helper = source.indexOf("private TmdbItem searchResolvedMatch(String videoName, Vod vod, String searchKeyword)");
        int cardName = source.indexOf("for (String title : resolution.queryTitles())", helper);
        int keyword = source.indexOf("searchKeywordMatch(searchKeyword, vod, attempted)", cardName);
        int cleaned = source.indexOf("resolver.queryCleanedTitles(request, 4)", keyword);
        int aiFallback = source.indexOf("resolver.resolveWithAiFallback(aiRequest)", cleaned);

        assertTrue("TMDB auto match must expose a search-keyword-aware card matching path", helper >= 0);
        assertTrue("TMDB auto match must try the card name candidates first", cardName > helper);
        assertTrue("TMDB auto match must try the original search keyword after card name failure",
                keyword > cardName);
        assertTrue("TMDB auto match must clean titles only after the search keyword fails",
                cleaned > keyword);
        assertTrue("TMDB AI fallback must remain the last matching stage",
                aiFallback > cleaned);
        assertTrue("the title request must carry the user search keyword",
                source.indexOf(".searchKeyword(searchKeyword)", helper) > helper);
    }

    @Test
    public void autoMatchDoesNotInvokeAiBeforeSearchKeywordAndCleaningStages() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int helper = source.indexOf("private TmdbItem searchResolvedMatch(String videoName, Vod vod, String searchKeyword)");
        int initialRequest = source.indexOf("buildTitleRequest(videoName, vod, searchKeyword, false)", helper);
        int initialResolve = source.indexOf("MediaTitleResolution resolution = resolver.resolve(request);", initialRequest);
        int cleaned = source.indexOf("resolver.queryCleanedTitles(request, 4)", initialResolve);
        int aiRequest = source.indexOf("buildTitleRequest(videoName, vod, searchKeyword, true)", cleaned);
        int aiResolve = source.indexOf("resolver.resolveWithAiFallback(aiRequest)", initialResolve);
        int requestBuilder = source.indexOf("private MediaTitleRequest buildTitleRequest", helper);
        int allowAi = source.indexOf(".allowAi(allowAi)", requestBuilder);

        assertTrue("the initial title request must disable AI while card/search/cleaned matching runs",
                initialRequest > helper && initialResolve > initialRequest
                        && requestBuilder > initialRequest && allowAi > requestBuilder);
        assertTrue("AI may only be enabled for the final fallback request",
                cleaned > initialResolve && aiRequest > cleaned && aiResolve > aiRequest);
    }

    @Test
    public void autoMatchExceptionsAlwaysReleaseEpisodePlaceholder() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("public void autoMatch(String videoName, Vod vod)");
        int task = source.indexOf("backgroundTasks.submit(() -> {", method);
        int nextMethod = source.indexOf("private TmdbItem searchResolvedMatch", task);
        String body = source.substring(task, nextMethod);
        int failureHandler = body.indexOf("} catch (Exception e) {");
        int failureLog = body.indexOf("auto match failed", failureHandler);
        int completion = body.indexOf("notifyLoadComplete(vod, generation);", failureHandler);

        assertTrue(sourcePath + " is missing autoMatch", method >= 0 && task > method && nextMethod > task);
        assertTrue("TMDB auto-match background failures must be caught", failureHandler >= 0);
        assertTrue("TMDB auto-match failures must be logged and release the episode placeholder",
                failureLog > failureHandler && completion > failureLog);
    }
    @Test
    public void loadDetailNormalizesCachedOrPassedTitleFromTmdbDetail() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void loadDetailSync");
        int detail = source.indexOf("tmdbService.detail(item, tmdbConfig, false)", method);
        int normalize = source.indexOf("item = normalizeLoadedItem(item, detail);", detail);
        int assign = source.indexOf("tmdbItem = item;", normalize);
        int save = source.indexOf("saveMatch(vod, item);", assign);

        assertTrue(sourcePath + " is missing loadDetailSync", method >= 0);
        assertTrue("TMDB detail load must normalize noisy cached/passed titles from detail response",
                detail > method && normalize > detail && assign > normalize);
        assertTrue("normalized TMDB title must be written back to the match cache",
                save > assign);
    }

    @Test
    public void directTmdbLoadConsumesMemoryDetailCacheBeforeServiceRequest() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int load = source.indexOf("public void load(TmdbItem item, Vod vod)");
        int call = source.indexOf("TmdbDetailCache.Entry cached = takeTmdbDetailCache(item);", load);
        int loadCached = source.indexOf("backgroundTasks.submit(() -> loadDetailSync(vod, cached.getItem(), cached.getDetail(), cached.getCast(), generation))", call);
        int helper = source.indexOf("private TmdbDetailCache.Entry takeTmdbDetailCache", loadCached);
        int take = source.indexOf("TmdbDetailCache.take", helper);
        int sync = source.indexOf("private void loadDetailSync(Vod vod, TmdbItem item, JsonObject cachedDetail", helper);
        int service = source.indexOf("tmdbService.detail(item, tmdbConfig, false)", sync);

        assertTrue(sourcePath + " is missing TMDB direct load method", load >= 0);
        assertTrue("direct TMDB playback should consume memory detail cache asynchronously before disk/network detail load",
                call > load && loadCached > call && take > helper && service > sync);
    }


    @Test
    public void tmdbRefreshEventsAlignIdentityLessCachedVodToCurrentPlayback() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int helper = source.indexOf("private Vod alignCachedVodIdentity(Vod vod)");
        int identity = source.indexOf("VodEventGuard.alignCachedIdentity", helper);
        int notify = source.indexOf("private void notifyVodChanged", identity);
        int assign = source.indexOf("pendingVodRefreshVod = alignCachedVodIdentity(vod);", notify);

        assertTrue(sourcePath + " must normalize cache-only Vod identity before TMDB refresh dispatch", helper >= 0 && identity > helper);
        assertTrue("TMDB refresh dispatch must use the normalized Vod", notify > identity && assign > notify);
    }

    @Test
    public void tmdbVodRefreshesAreCoalescedAndStartupWorkIsDeferred() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int constants = source.indexOf("VOD_REFRESH_COALESCE_MS");
        int backgroundDelay = source.indexOf("TMDB_STARTUP_BACKGROUND_DELAY_MS", constants);
        int episodeDelay = source.indexOf("TMDB_STARTUP_EPISODE_DELAY_MS", backgroundDelay);
        int firstRefresh = source.indexOf("notifyVodChanged(vod, generation, RefreshEvent.Type.VOD_CORE);", episodeDelay);
        int deferredLoads = source.indexOf("scheduleStartupBackgroundLoads(vod, item, detail, generation);", firstRefresh);
        int scheduler = source.indexOf("private void scheduleStartupBackgroundLoads", deferredLoads);
        // 选集不排在 1200ms 之后：它是首屏内容，单独立刻排程。
        int episodeScheduleCall = source.indexOf("scheduleStartupEpisodeLoad(vod, item, generation);", scheduler);
        int related = source.indexOf("loadRelatedRecommendationsAsync(vod, item, detail, generation);", episodeScheduleCall);
        int personal = source.indexOf("loadPersonalRecommendationsAsync(vod, item, detail, generation);", related);
        int backgroundPost = source.indexOf("App.post(pendingStartupBackgroundLoads, TMDB_STARTUP_BACKGROUND_DELAY_MS);", personal);
        int episodeScheduler = source.indexOf("private void scheduleStartupEpisodeLoad", backgroundPost);
        int episode = source.indexOf("loadEpisodeTitlesAsync(vod, item, generation, metadataGeneration, selectedSeason);", episodeScheduler);
        int episodePost = source.indexOf("App.post(pendingStartupEpisodeLoad, TMDB_STARTUP_EPISODE_DELAY_MS);", episode);
        int notify = source.indexOf("private void notifyVodChanged");
        int pending = source.indexOf("pendingVodRefresh", notify);
        int post = source.indexOf("App.post(pendingVodRefresh, VOD_REFRESH_COALESCE_MS);", pending);
        int relatedMethod = source.indexOf("private void loadRelatedRecommendationsAsync");
        int relatedNotify = source.indexOf("notifyVodChanged(vod, generation, RefreshEvent.Type.VOD_RECOMMENDATIONS);", relatedMethod);
        int personalMethod = source.indexOf("private void loadPersonalRecommendationsAsync");
        int personalNotify = source.indexOf("notifyVodChanged(vod, generation, RefreshEvent.Type.VOD_PERSONAL);", personalMethod);

        assertTrue(sourcePath + " is missing TMDB playback refresh throttle constants", constants >= 0 && backgroundDelay > constants && episodeDelay > backgroundDelay);
        assertTrue("TMDB detail should queue one lightweight VOD refresh before deferred background work",
                firstRefresh > episodeDelay && deferredLoads > firstRefresh);
        assertTrue("recommendation loads should stay behind the first-frame delay",
                scheduler > deferredLoads && related > episodeScheduleCall && personal > related && backgroundPost > personal);
        assertTrue("episode titles must be scheduled without the first-frame delay so the episode list is not gated on it",
                episodeScheduleCall > scheduler && episodeScheduler > backgroundPost && episode > episodeScheduler && episodePost > episode);
        assertTrue("VOD refreshes should be coalesced on the main thread instead of posting every async result",
                notify >= 0 && pending > notify && post > pending);
        assertTrue("related and personal recommendation completion should reuse the coalesced VOD refresh path",
                relatedNotify > relatedMethod && personalNotify > personalMethod);
    }

    @Test
    public void seasonWarmUpOnlyGuessesWhenEvidenceIsUnambiguous() {
        JsonObject multiSeason = detailWithSeasons("[{\"season_number\":0},{\"season_number\":1},{\"season_number\":2}]");
        JsonObject singleSeason = detailWithSeasons("[{\"season_number\":0},{\"season_number\":1}]");

        // 多季且没有 Intent 季号：证据不足，不猜，避免白拉一整季。
        assertEquals(-1, TmdbUIAdapter.likelySeasonNumber(multiSeason, -1));
        // 单一正片季：可以放心预热。
        assertEquals(1, TmdbUIAdapter.likelySeasonNumber(singleSeason, -1));
        // Intent 指定的季号优先，但必须真的存在于 TMDB。
        assertEquals(2, TmdbUIAdapter.likelySeasonNumber(multiSeason, 2));
        assertEquals(-1, TmdbUIAdapter.likelySeasonNumber(multiSeason, 9));
        assertEquals(-1, TmdbUIAdapter.likelySeasonNumber(detailWithSeasons("[]"), 1));
    }

    private static JsonObject detailWithSeasons(String seasonsJson) {
        return JsonParser.parseString("{\"seasons\":" + seasonsJson + "}").getAsJsonObject();
    }

    @Test
    public void episodeLoadsUseTheirOwnWidePoolAndCacheIsScopedToOneDetailRequest() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        // recommendationExecutor 只有 3 条线程，推荐 / 个性化 / AI 推荐都在里面。选集不再延迟后
        // 若继续共用会排在推荐后面，反而更慢，所以必须走 largeExecutor。
        int episodeScope = source.indexOf("this.episodeTasks = new Task.Scope(Task.largeExecutor());");
        int episodeSubmit = source.indexOf("episodeTasks.submit(", episodeScope);
        int beginRequest = source.indexOf("public void beginDetailRequest()");
        int cancelEpisodes = source.indexOf("episodeTasks.cancelAll();", beginRequest);
        // 季缓存必须在 beginDetailRequest 清空：它在 prefetch 预热之前调用，所以既不会挡住
        // 强制刷新，也不会冲掉预热结果。放到 resetLoadState 会被 load() 冲掉预热。
        int clearCache = source.indexOf("seasonEpisodeCache.clear();", cancelEpisodes);
        int releaseMethod = source.indexOf("public void release()");
        int closeEpisodes = source.indexOf("episodeTasks.close();", releaseMethod);
        int resetMethod = source.indexOf("private int resetLoadState()");
        int resetEnd = source.indexOf("\n    private void captureSourceSeason", resetMethod);
        String resetBody = resetMethod >= 0 && resetEnd > resetMethod ? source.substring(resetMethod, resetEnd) : "";
        int prefetchMethod = source.indexOf("public void prefetch(TmdbItem item)");
        int prefetchEnd = source.indexOf("\n    /**", prefetchMethod);
        String prefetchBody = prefetchMethod >= 0 && prefetchEnd > prefetchMethod ? source.substring(prefetchMethod, prefetchEnd) : "";

        assertTrue("episode metadata must not share the 3-thread recommendation pool", episodeScope >= 0 && episodeSubmit > episodeScope);
        assertTrue("a new detail request must cancel in-flight episode work", cancelEpisodes > beginRequest);
        assertTrue("the season memory cache must be cleared per detail request so refresh is not shadowed", clearCache > cancelEpisodes);
        assertTrue("destroying the adapter must close the episode scope", closeEpisodes > releaseMethod);
        assertFalse("clearing the season cache in resetLoadState would discard the prefetch warm-up", resetBody.contains("seasonEpisodeCache.clear();"));
        // 季预热若同步跑在 prefetch 的 Callable 里，会阻塞 future，进而延后 loadDetailSync
        // 和整个详情页首屏——那正好和优化目标相反。
        assertTrue("the season warm-up must be dispatched asynchronously", prefetchBody.contains("warmLikelySeasonAsync(loadedItem, detail, intentSeason);"));
        assertFalse("the season warm-up must not block the prefetch future", prefetchBody.contains("seasonEpisodes(loadedItem"));
        // Intent 内部是非线程安全的 Bundle，主线程会并发改它，季号必须在主线程算好再传进去。
        assertTrue("the intent season must be read on the calling thread, not inside the background callable",
                prefetchBody.indexOf("int intentSeason = intentSeasonNumber();") < prefetchBody.indexOf("detailPrefetch.start("));
    }

    @Test
    public void episodeMetadataCompletionIsTrackedSeparatelyFromCoreDetail() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int field = source.indexOf("private volatile boolean episodeMetadataLoaded;");
        int getter = source.indexOf("public boolean isEpisodeMetadataLoaded()");
        int resetMethod = source.indexOf("private int resetLoadState()");
        int reset = source.indexOf("episodeMetadataLoaded = false;", resetMethod);
        int coreMethod = source.indexOf("private void loadDetailSync(Vod vod, TmdbItem item, JsonObject cachedDetail");
        int coreLoaded = source.indexOf("loaded = true;", coreMethod);
        int coreEpisodeState = source.indexOf("episodeMetadataLoaded = vod == null || item == null || !item.isTv();", coreLoaded);
        int firstRefresh = source.indexOf("notifyVodChanged(vod, generation, RefreshEvent.Type.VOD_CORE);", coreEpisodeState);
        int episodeMethod = source.indexOf("private void loadEpisodeTitlesAsync(Vod vod, TmdbItem item, int generation, int metadataGeneration, int selectedSeason)");
        int episodeComplete = source.indexOf("finishEpisodeMetadataLoad(vod, generation, metadataGeneration, selectedSeason);", episodeMethod);
        int completionMethod = source.indexOf("private void finishEpisodeMetadataLoad(Vod vod, int generation, int metadataGeneration, Integer selectedSeason)", episodeComplete);
        int markComplete = source.indexOf("episodeMetadataLoaded = true;", completionMethod);
        int completionRefresh = source.indexOf("notifyVodChanged(vod, generation, RefreshEvent.Type.VOD_EPISODE_TITLES);", markComplete);
        int failureMethod = source.indexOf("private void notifyLoadComplete(Vod vod, int generation)");
        int failureComplete = source.indexOf("episodeMetadataLoaded = true;", failureMethod);
        int failureRefresh = source.indexOf("notifyVodChanged(vod, generation, RefreshEvent.Type.VOD_CORE);", failureComplete);

        assertTrue(sourcePath + " should track episode metadata independently from core detail", field >= 0 && getter > field);
        assertTrue("starting a new TMDB load should clear episode metadata completion", reset > resetMethod);
        assertTrue("the first core-detail refresh must keep TV episode metadata pending", coreLoaded > coreMethod && coreEpisodeState > coreLoaded && firstRefresh > coreEpisodeState);
        assertTrue("episode loading should always publish a terminal completion refresh", episodeComplete > episodeMethod && completionMethod > episodeComplete && markComplete > completionMethod && completionRefresh > markComplete);
        assertTrue("TMDB skip/failure should also release the episode placeholder", failureComplete > failureMethod && failureRefresh > failureComplete);
    }

    @Test
    public void videoActivityAppliesCachedVodDetailAfterFirstFrameOpportunity() throws Exception {
        assertVideoActivityDefersCachedDetail("mobile");
        assertVideoActivityDefersCachedDetail("leanback");
    }

    @Test
    public void leanbackDirectTmdbPlaybackStartsBeforeFullCachedDetailBind() throws Exception {
        Path sourcePath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private boolean setCachedTmdbDetail()");
        int fastBranch = source.indexOf("if (tryStartFastTmdbPlayback(cached))", method);
        int fastReturn = source.indexOf("return true;", fastBranch);
        int waitService = source.indexOf("shouldWaitForPlaybackService()", fastReturn);
        int queueService = source.indexOf("queueFastTmdbPlaybackUntilServiceReady(cached);", waitService);
        int service = source.indexOf("protected void onServiceConnected()");
        int flush = source.indexOf("flushPendingFastTmdbPlayback();", service);
        int flushMethod = source.indexOf("private void flushPendingFastTmdbPlayback()", method);
        int flushFast = source.indexOf("if (tryStartFastTmdbPlayback(item)) return;", flushMethod);
        int fallbackPost = source.indexOf("mBinding.getRoot().postDelayed(() ->", waitService);
        int fallbackBind = source.indexOf("setDetail(Result.vod(item));", fallbackPost);
        int fastMethod = source.indexOf("private boolean tryStartFastTmdbPlayback(Vod item)", method);
        int reveal = source.indexOf("showFastTmdbPlaybackContent();", fastMethod);
        int serviceWaitInFastPath = source.indexOf("if (shouldWaitForPlaybackService())", reveal);
        int queueInFastPath = source.indexOf("queueFastTmdbPlaybackUntilServiceReady(item);", serviceWaitInFastPath);
        int postStart = source.indexOf("App.post(mPendingFastTmdbPlaybackStart, TMDB_FAST_PLAYBACK_START_DELAY_MS);", reveal);
        int startPending = source.indexOf("private void startPendingFastTmdbPlayback()", postStart);
        int sort = source.indexOf("TmdbEpisodeSorter.sort(item)", startPending);
        int flag = source.indexOf("findFastTmdbPlaybackFlag(item)", startPending);
        int episode = source.indexOf("findFastTmdbPlaybackEpisode(flag)", flag);
        int firstPrepare = source.indexOf("prepareFastTmdbPlaybackHistory(item, flag, episode);", fastMethod);
        int prepare = source.indexOf("prepareFastTmdbPlaybackHistory(item, flag, episode);", startPending);
        int firstHistoryLookup = source.indexOf("History.findPlayback", fastMethod);
        int player = source.indexOf("mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl(), applyHistoryPlayerKernel());", prepare);
        int fullBind = source.indexOf("applyFastTmdbPlaybackFullDetailNextFrame(item);", player);
        int canApply = source.indexOf("private boolean canApplyPlayerResult()");
        int fastCanApply = source.indexOf("mFastPlaybackFlag != null && mFastPlaybackEpisode != null && mHistory != null", canApply);
        int actionVisibility = source.indexOf("setOriginalEnhancedActionVisibility(loadTmdbDetail && (Setting.isOriginalEnhancedDetailPage() || isIntentTmdbPlayback()));");
        int checkFlag = source.indexOf("private void checkFlag(Vod item)");
        int fastCheckFlag = source.indexOf("mFastTmdbPlaybackStarted && mFastPlaybackFlag != null && mFastPlaybackEpisode != null", checkFlag);
        int bindEpisodes = source.indexOf("setEpisodeAdapter(mFastPlaybackFlag.getEpisodes(), false);", fastCheckFlag);
        int normalClick = source.indexOf("onItemClick(resolveHistoryPlaybackFlag(item.getFlags()));", bindEpisodes);

        assertTrue(sourcePath + " is missing direct TMDB fast playback cache branch", method >= 0 && fastBranch > method && fastReturn > fastBranch);
        assertTrue("cached direct TMDB playback should wait for the playback service instead of falling back to full detail binding",
                waitService > fastReturn && queueService > waitService && service >= 0 && flush > service && flushMethod > method && flushFast > flushMethod);
        assertTrue("cached source detail fallback bind should stay behind the fast playback branch", fallbackPost > waitService && fallbackBind > fallbackPost);
        assertTrue("direct TMDB playback should reveal the playback page before episode/history/player startup work",
                fastMethod > method && reveal > fastMethod && postStart > reveal && startPending > postStart && flag > startPending && episode > flag && firstPrepare == prepare && prepare > startPending && player > prepare);
        assertTrue("direct TMDB playback content reveal should not wait for playback service connection",
                serviceWaitInFastPath > reveal && queueInFastPath > serviceWaitInFastPath);
        assertTrue("direct TMDB playback should not sort or format the episode list before requesting playback",
                sort > player && source.indexOf("applyTmdbEpisodeTitles(item);", startPending) > player);
        assertTrue("direct TMDB playback should not query playback history before revealing content",
                firstHistoryLookup > startPending);
        assertTrue("direct TMDB playback should bind the full native-enhanced layout only after requesting playback",
                fullBind > player && fastCheckFlag > checkFlag && bindEpisodes > fastCheckFlag && normalClick > bindEpisodes);
        assertTrue("direct TMDB playback should use the native-enhanced action button set",
                actionVisibility > method);
        assertTrue("player results from the fast path must be accepted before adapters are fully bound",
                canApply > player && fastCanApply > canApply);
    }

    @Test
    public void leanbackDirectTmdbPlaybackReusesCachedDetailForMetadataBind() throws Exception {
        Path videoPath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String video = new String(Files.readAllBytes(videoPath), StandardCharsets.UTF_8);
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        int setDetail = video.indexOf("private void setDetail(Vod item)");
        int load = video.indexOf("mTmdbUIAdapter.load(tmdbItem, item, mFastTmdbDetailCache);", setDetail);
        int prepare = video.indexOf("private void prepareFastTmdbPlaybackItem(Vod item)");
        int metadata = video.indexOf("setIfEmpty(item.getYear(), getTmdbVodYear(), item::setYear)", prepare);
        int fastStart = video.indexOf("private boolean tryStartFastTmdbPlayback(Vod item)");
        int initialText = video.indexOf("setText(item);", fastStart);
        int reveal = video.indexOf("showFastTmdbPlaybackContent();", initialText);
        int equality = video.indexOf("if (TextUtils.equals(view.getText(), value)) return;", video.indexOf("private void setText(TextView view"));
        int cachedLoad = adapter.indexOf("public void load(TmdbItem item, Vod vod, TmdbDetailCache.Entry cached)");
        int syncCache = adapter.indexOf("loadDetailSync(vod, cached.getItem(), cached.getDetail(), cached.getCast(), generation)", cachedLoad);

        assertTrue("leanback full detail binding should reuse the TMDB cache already consumed by fast playback",
                setDetail >= 0 && load > setDetail);
        assertTrue("fast playback should seed all right-side metadata from the detail-page payload before the full bind",
                prepare >= 0 && metadata > prepare);
        assertTrue("the complete right-side text should be bound before the playback page becomes visible",
                fastStart >= 0 && initialText > fastStart && reveal > initialText);
        assertTrue("later cache enrichment should not assign identical text again",
                equality >= 0);
        assertTrue("TMDB adapter should accept the already consumed detail cache instead of fetching again",
                cachedLoad >= 0 && syncCache > cachedLoad);
    }

    @Test
    public void leanbackDirectTmdbPlaybackKeepsDetailPageTextStableDuringTmdbBind() throws Exception {
        Path sourcePath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int apply = source.indexOf("private void applyTmdbDetailFields()");
        int applyEnd = source.indexOf("private void updateTmdbOverviewButton()", apply);
        String applyBody = source.substring(apply, applyEnd);
        int directGuard = applyBody.indexOf("if (isIntentTmdbPlayback())");
        int directReturn = applyBody.indexOf("return;", directGuard);
        int suppress = applyBody.indexOf("suppressTmdbNativeTextFields();");
        int hideSynopsisButton = applyBody.indexOf("mBinding.content.setVisibility(View.GONE);");
        int replaceWithOverview = applyBody.indexOf("mBinding.tmdbOverview.setVisibility(View.VISIBLE);");
        int suppressMethod = source.indexOf("private void suppressTmdbNativeTextFields()");
        int suppressEnd = source.indexOf("\n    }", suppressMethod);
        String suppressBody = source.substring(suppressMethod, suppressEnd);

        assertTrue("direct colorful-detail playback must exit before TMDB replaces the native right panel",
                apply >= 0 && directGuard > 0 && directReturn > directGuard
                        && suppress > directReturn && hideSynopsisButton > directReturn && replaceWithOverview > directReturn);
        assertTrue("all asynchronous TMDB completion paths must preserve the native right panel for direct playback",
                suppressBody.contains("if (isIntentTmdbPlayback()) return;"));
    }

    @Test
    public void leanbackDirectTmdbPlaybackHydratesSynopsisWithoutFullDetailBind() throws Exception {
        Path sourcePath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int startPending = source.indexOf("private void startPendingFastTmdbPlayback()");
        int prepareItem = source.indexOf("prepareFastTmdbPlaybackItem(item);", startPending);
        int player = source.indexOf("mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl(), applyHistoryPlayerKernel());", prepareItem);
        int postHydrate = source.indexOf("mBinding.getRoot().post(() -> hydrateFastTmdbPlaybackDetail(item));", player);
        int initialHydrate = source.indexOf("hydrateFastTmdbPlaybackDetail(item);", source.indexOf("private boolean tryStartFastTmdbPlayback(Vod item)"));
        int hydrate = source.indexOf("private void hydrateFastTmdbPlaybackDetail(Vod item)");
        int hydrateEnd = source.indexOf("private String firstNonEmpty", hydrate);
        String hydrateBody = source.substring(hydrate, hydrateEnd);

        assertTrue(sourcePath + " is missing direct TMDB playback summary hydration", startPending >= 0 && prepareItem > startPending && player > prepareItem && (initialHydrate >= 0 || postHydrate > player));
        assertTrue("direct TMDB playback should hydrate the synopsis from source detail or TMDB intent cache",
                hydrateBody.contains("applyFastTmdbDetailCache(item)")
                        && hydrateBody.contains("getTmdbVodContent()")
                        && hydrateBody.contains("mBinding.content.setTag(content);"));
        assertTrue("direct TMDB playback should consume colorful detail's cached TMDB detail for synopsis",
                source.contains("TmdbDetailCache.take(getIntent().getStringExtra(TmdbDetailCache.EXTRA_KEY), getTmdbItem())")
                        && source.contains("cachedTmdbOverview(detail)")
                        && source.contains("cachedTmdbOverviewForLanguage(translations, \"zh-CN\")"));
        assertTrue("fast hydration must keep direct colorful-detail playback on the native right-panel layout",
                hydrateBody.contains("if (isTmdbMode() && !isIntentTmdbPlayback())")
                        && hydrateBody.contains("mBinding.tmdbOverview.setSingleLine(false);")
                        && hydrateBody.contains("mBinding.tmdbOverview.setHorizontallyScrolling(false);")
                        && hydrateBody.contains("CharSequence overview = getString(R.string.detail_content, content);")
                        && hydrateBody.contains("mBinding.tmdbOverview.setText(overview)"));
        assertTrue("direct TMDB playback should restore the cached backdrop as the playback page background",
                hydrateBody.contains("String wall = firstNonEmpty(cachedFastTmdbBackdrop(), getWallPic(), mHistory == null ? \"\" : mHistory.getWallPic());")
                        && hydrateBody.contains("if (!TextUtils.isEmpty(wall)) setContextWall(wall);")
                        && hydrateBody.contains("if (!TextUtils.isEmpty(wall)) mHistory.setWallPic(wall);")
                        && source.contains("cachedTmdbImage(cached.getDetail(), \"backdrop_path\", true)")
                        && source.contains("base = backdrop ? config.getBackdropBase() : config.getImageBase();"));
        assertTrue("direct TMDB launch should carry the matched TMDB backdrop into the playback page",
                source.contains("String wallPic = item == null ? \"\" : item.getBackdropUrl();")
                        && source.contains("intent.putExtra(\"wallPic\", wallPic);"));
        assertTrue("fast summary hydration must stay lightweight",
                hydrateBody.indexOf("setDetail(Result.vod(item))") < 0
                        && hydrateBody.indexOf("updateVod(item)") < 0
                        && hydrateBody.indexOf("updateFlag(") < 0
                        && hydrateBody.indexOf("setPartAdapter(") < 0
                        && hydrateBody.indexOf("applyTmdbEpisodeTitles(item)") < 0
                        && source.indexOf("tmdbService.detail(item", hydrate) < 0);
    }

    @Test
    public void leanbackVideoActivityReusesBackdropSlideshowForSamePhotos() throws Exception {
        Path sourcePath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int field = source.indexOf("private String mBackdropSignature");
        int method = source.indexOf("private void setupBackdropSlideshow");
        int signature = source.indexOf("String signature = backdropSignature(photos);", method);
        int same = source.indexOf("if (TextUtils.equals(mBackdropSignature, signature))", signature);
        int setItems = source.indexOf("mBackdropAdapter.setItems(photos);", same);
        int save = source.indexOf("mBackdropSignature = signature;", setItems);
        int helper = source.indexOf("private String backdropSignature", method);

        assertTrue(sourcePath + " is missing cached backdrop signature state", field >= 0);
        assertTrue("same TMDB photo list should not rebuild backdrop adapter or restart auto-scroll",
                method >= 0 && signature > method && same > signature && setItems > same && save > setItems && helper > method);
    }

    @Test
    public void leanbackVideoActivityDefersInitialTmdbBindUntilContentReveal() throws Exception {
        Path sourcePath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int revealDelay = source.indexOf("TMDB_BIND_AFTER_REVEAL_DELAY_MS");
        int pending = source.indexOf("private final Runnable mPendingTmdbBind");
        int deferred = source.indexOf("private final Runnable mDeferredTmdbDataBind");
        int refresh = source.indexOf("public void onRefreshEvent(RefreshEvent event)");
        int immediateUpdate = source.indexOf("updateVod(event.getVod());", refresh);
        int queue = source.indexOf("queueTmdbBind(event.getVod());", refresh);
        int player = source.indexOf("private void setPlayer(Result result)");
        int quality = source.indexOf("mQualityAdapter.addAll(result);", player);
        int queueMethod = source.indexOf("private void queueTmdbBind(Vod item)");
        int schedule = source.indexOf("schedulePendingTmdbBindAfterContentReveal();", queueMethod);
        int scheduleMethod = source.indexOf("private void schedulePendingTmdbBindAfterContentReveal()");
        int postAfterReveal = source.indexOf("App.post(mPendingTmdbBind, TMDB_BIND_AFTER_REVEAL_DELAY_MS);", scheduleMethod);
        int flush = source.indexOf("private void flushPendingTmdbBind()");
        int flushUpdate = source.indexOf("updateVod(item);", flush);
        int finish = source.indexOf("finishTmdbDetail();", flush);
        int finishEpisode = source.indexOf("finishEpisodeLoading();", finish);
        int dataPending = source.indexOf("mTmdbDataBindPending = true;", finishEpisode);
        int postDeferred = source.indexOf("App.post(mDeferredTmdbDataBind, TMDB_BIND_AFTER_REVEAL_DELAY_MS);", dataPending);
        int deferredMethod = source.indexOf("private void applyDeferredTmdbDataBind()", postDeferred);
        int bind = source.indexOf("bindTmdbData();", deferredMethod);
        int reset = source.indexOf("private void resetPendingTmdbBind()");
        int removeCallbacks = source.indexOf("App.removeCallbacks(mPendingTmdbBind, mDeferredTmdbDataBind);", reset);

        assertTrue(sourcePath + " is missing TMDB bind defer constant", revealDelay >= 0);
        assertTrue(sourcePath + " is missing pending TMDB bind runnables", pending >= 0 && deferred > pending);
        assertTrue("VOD refresh should queue TMDB binding instead of updating the detail UI immediately",
                refresh >= 0 && queue > refresh && (immediateUpdate < 0 || immediateUpdate > queueMethod) && flushUpdate > flush);
        assertTrue("player result should not control queued TMDB binding release",
                player >= 0 && quality > player && source.indexOf("schedulePendingTmdbBindAfterPlayerReady") < 0 && source.indexOf("TMDB_BIND_AFTER_PLAYER_READY_DELAY_MS") < 0);
        assertTrue("TMDB binding should be released after content reveal, independent of player ready",
                queueMethod > refresh && schedule > queueMethod && scheduleMethod > queueMethod && postAfterReveal > scheduleMethod);
        assertTrue("queued TMDB binding should reveal detail before posting heavier TMDB grids",
                flush > queueMethod && finish > flush && finishEpisode > finish && dataPending > finishEpisode && postDeferred > dataPending);
        assertTrue("deferred TMDB data binding must still run the original bind path after reveal",
                deferredMethod > postDeferred && bind > deferredMethod);
        assertTrue("pending TMDB bind must be reset when changing or destroying playback detail",
                reset > flush && removeCallbacks > reset);
    }

    @Test
    public void tmdbDetailActivityPassesMemoryDetailCacheKeyToDirectPlayback() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void playDefaultPlayback()");
        int put = source.indexOf("TmdbDetailCache.put(playbackTmdbItem(), matchedTmdbDetail, detailCastItems)", method);
        if (put < 0) put = source.indexOf("TmdbDetailCache.put(item, matchedTmdbDetail, detailCastItems)", method);
        int start = source.indexOf("VideoActivity.startDirectTmdb", put);
        int keyArg = source.indexOf("tmdbDetailCacheKey", start);
        int fastTitles = source.indexOf("fastPlaybackEpisodeTitles()", start);
        int allTitles = source.indexOf("selectedTmdbEpisodeTitles()", method);

        assertTrue(sourcePath + " is missing playDefaultPlayback", method >= 0);
        assertTrue("TMDB detail page should hand in-memory detail to direct playback",
                put > method && start > put && keyArg > start);
        assertTrue("TMDB detail play click should pass only the selected episode title instead of rebuilding every TMDB episode title before launch",
                fastTitles > start && (allTitles < 0 || allTitles > source.indexOf("private ArrayList<String> selectedTmdbEpisodeTitles()")));
    }

    @Test
    public void tmdbDetailActivityProfilesStandaloneSinglePassLoading() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int loadContent = source.indexOf("private void loadContent(@Nullable TmdbBundle reusableBundle)");
        int loadStart = source.indexOf("load start mode=%d", loadContent);
        int singlePass = source.indexOf("shouldLoadInitialStandaloneTmdbDetailInSinglePass", loadStart);
        int taskLog = source.indexOf("load tasks mode=%d", singlePass);
        int sourceStart = source.indexOf("long sourceStart = System.currentTimeMillis();", taskLog);
        int sourceLog = source.indexOf("source detail cost=%dms", sourceStart);
        int waitStart = source.indexOf("long tmdbWaitStart = System.currentTimeMillis();", sourceLog);
        int waitLog = source.indexOf("tmdb wait cost=%dms", waitStart);
        int singlePassApply = source.indexOf("if (singlePassStandaloneTmdb)", waitLog);
        int applyLoaded = source.indexOf("applyLoaded(finalVod", singlePassApply);
        int applyMethod = source.indexOf("private void applyLoaded(Vod loadedVod", applyLoaded);
        int applyLog = source.indexOf("apply loaded cost=%dms", applyMethod);
        int applyTmdb = source.indexOf("private void applyTmdbResultNow", applyLog);
        int applyTmdbLog = source.indexOf("apply tmdb result cost=%dms", applyTmdb);
        int bundleMethod = source.indexOf("private TmdbBundle loadTmdbBundle", applyTmdbLog);
        int bundleLog = source.indexOf("tmdb bundle cost=%dms", bundleMethod);

        assertTrue(sourcePath + " is missing standalone detail load profiling", loadContent >= 0 && loadStart > loadContent && taskLog > singlePass);
        assertTrue("standalone detail load should measure source detail and TMDB wait separately",
                sourceStart > taskLog && sourceLog > sourceStart && waitStart > sourceLog && waitLog > waitStart);
        assertTrue("standalone TMDB modes should still apply source detail and TMDB bundle together in the single-pass branch",
                singlePassApply > waitLog && applyLoaded > singlePassApply);
        assertTrue("detail page UI binding and TMDB bundle loading must stay observable for emulator verification",
                applyLog > applyMethod && applyTmdbLog > applyTmdb && bundleLog > bundleMethod);
    }

    @Test
    public void tmdbDetailActivityIgnoresPlaybackCallbacksOutsideInlineMode() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int playing = source.indexOf("protected void onPlayingChanged(boolean isPlaying)");
        int playingGuard = source.indexOf("if (!isInlinePlayerMode() || !inlineStarted || !isOwner()) return;", playing);
        int pip = source.indexOf("updateInlinePiPActions(isPlaying);", playingGuard);
        int size = source.indexOf("protected void onSizeChanged(VideoSize size)");
        int sizeGuard = source.indexOf("if (!isInlinePlayerMode() || !inlineStarted || !isOwner()) return;", size);
        int sizeUpdate = source.indexOf("updateInlineButtons(service() != null", sizeGuard);

        assertTrue(sourcePath + " is missing TMDB detail playback callback guards", playing >= 0 && size >= 0);
        assertTrue("standalone playback navigation should not let the detail page update PiP actions",
                playingGuard > playing && pip > playingGuard);
        assertTrue("standalone playback navigation should not let the detail page update inline sizing controls",
                sizeGuard > size && sizeUpdate > sizeGuard);
    }

    @Test
    public void tmdbDetailActivityRefreshesCurrentEpisodeForSelectedPlayerKernel() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int switchMethod = source.indexOf("private void switchInlinePlayer(int playerType)");
        int refreshCall = source.indexOf("refreshAndSwitchInlinePlayer(playerType)", switchMethod);
        int refreshMethod = source.indexOf("private boolean refreshAndSwitchInlinePlayer(int playerType)", refreshCall);
        int samePlayerGuard = source.indexOf("if (playerType == player().getPlayerType()) {", refreshMethod);
        int cancelSwitch = source.indexOf("cancelPendingInlinePlayerSwitch();", samePlayerGuard);
        int contextGuard = source.indexOf("if (selectedFlag == null || selectedEpisode == null) return false;", cancelSwitch);
        int emptyContextGuard = source.indexOf("if (TextUtils.isEmpty(flag) || TextUtils.isEmpty(episodeUrl)) return false;", contextGuard);
        int generation = source.indexOf("int generation = ++inlinePlaybackGeneration;", emptyContextGuard);
        int switchLoading = source.indexOf("inlinePlayerSwitchLoading = true;", generation);
        int showLoading = source.indexOf("showInlineLoading();", switchLoading);
        int position = source.indexOf("long position = player().getPosition();", showLoading);
        int speed = source.indexOf("float speed = player().getSpeed();", position);
        int repeat = source.indexOf("boolean repeat = player().isRepeatOne();", speed);
        int request = source.indexOf("SiteApi.playerContent(key, flag, episodeUrl, playerType)", repeat);
        int staleGuard = source.indexOf("isInlinePlayerSwitchRequestCurrent(generation, key, flag, episodeUrl)", request);
        int lifecycleGuard = source.indexOf("private boolean isInlinePlayerSwitchRequestCurrent(int generation, String key, String flag, String episodeUrl)", staleGuard);
        int pendingGuard = source.indexOf("return inlinePlayerSwitchLoading", lifecycleGuard);
        int activeMode = source.indexOf("&& isInlinePlayerMode()", pendingGuard);
        int activeOwner = source.indexOf("&& isOwner()", activeMode);
        int activePlayer = source.indexOf("&& !player().isEmpty()", activeOwner);
        int updateResult = source.indexOf("currentInlineResult = result;", staleGuard);
        int updateParse = source.indexOf("useParse = result.shouldUseParse();", updateResult);
        int switchResult = source.indexOf("player().switchPlayer(playerType, result, activePlaybackKey(), metadata, useParse, position, speed, repeat);", updateParse);
        int oldFallback = source.indexOf("player().switchPlayerManually(playerType);", switchMethod);

        assertTrue(sourcePath + " is missing refreshed inline player-kernel switching", switchMethod >= 0 && refreshCall > switchMethod && refreshMethod > refreshCall);
        assertTrue("choosing the active kernel must cancel only a pending kernel switch, while missing playback context must not invalidate the current playback request",
                samePlayerGuard > refreshMethod && cancelSwitch > samePlayerGuard && contextGuard > cancelSwitch && emptyContextGuard > contextGuard && generation > emptyContextGuard);
        assertTrue("inline kernel switching should expose a cancellable loading state and preserve playback state before refreshing the selected episode",
                switchLoading > generation && showLoading > switchLoading && position > showLoading && speed > position && repeat > speed);
        assertTrue("inline kernel switching should request a result resolved for the selected target kernel and ignore stale callbacks",
                request > repeat && staleGuard > request);
        assertTrue("inline kernel switch callbacks should require a pending switch and be ignored after leaving inline playback or losing player ownership",
                lifecycleGuard > staleGuard && pendingGuard > lifecycleGuard && activeMode > pendingGuard && activeOwner > activeMode && activePlayer > activeOwner);
        assertTrue("inline kernel switching should install the refreshed result before rebuilding the player",
                updateResult > staleGuard && updateParse > updateResult && switchResult > updateParse);
        assertTrue("inline kernel switching must not fall back to rebuilding from the stale PlaySpec", oldFallback < 0);
    }

    @Test
    public void tmdbDetailActivityGatesInlineSystemPipUpdatesOnMobileCapability() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int canUse = source.indexOf("private boolean canUseInlineSystemPiP()");
        int mobileGate = source.indexOf("return Util.isMobile() && !PiP.noPiP();", canUse);
        int sourceHelper = source.indexOf("private void updateInlinePiPSource(View view)");
        int sourceGuard = source.indexOf("if (!canUseInlineSystemPiP() || inlinePiP == null || view == null) return;", sourceHelper);
        int sourceUpdate = source.indexOf("inlinePiP.update(this, view);", sourceGuard);
        int actionHelper = source.indexOf("private void updateInlinePiPActions(boolean playing)");
        int actionGuard = source.indexOf("if (!canUseInlineSystemPiP() || inlinePiP == null) return;", actionHelper);
        int actionUpdate = source.indexOf("inlinePiP.update(this, playing);", actionGuard);
        int canEnter = source.indexOf("private boolean canEnterInlinePiP()");
        int enterGate = source.indexOf("return canUseInlineSystemPiP() &&", canEnter);

        assertTrue(sourcePath + " is missing inline system PiP capability gate", canUse >= 0 && mobileGate > canUse);
        assertTrue("inline PiP source-rect updates should be skipped on leanback where TmdbDetailActivity does not support system PiP",
                sourceHelper > canUse && sourceGuard > sourceHelper && sourceUpdate > sourceGuard);
        assertTrue("inline PiP action updates should be skipped on leanback where TmdbDetailActivity does not support system PiP",
                actionHelper > sourceUpdate && actionGuard > actionHelper && actionUpdate > actionGuard);
        assertTrue("inline system PiP entry should share the same capability gate",
                canEnter >= 0 && enterGate > canEnter);
    }

    @Test
    public void directTmdbDetailPrefetchStartsBeforeCrawlerForBothFlavors() throws Exception {
        assertDirectTmdbPrefetchOrder("leanback");
        assertDirectTmdbPrefetchOrder("mobile");
    }

    @Test
    public void tmdbAdapterConsumesPrefetchWithoutTemporaryVod() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int prefetchMethod = source.indexOf("public void prefetch(TmdbItem item)");
        int prefetchStart = source.indexOf("detailPrefetch.start(item", prefetchMethod);
        int loadMethod = source.indexOf("public void load(TmdbItem item, Vod vod)");
        int take = source.indexOf("detailPrefetch.take(item)", loadMethod);

        String prefetchBody = source.substring(prefetchMethod, loadMethod);
        assertTrue("TMDB adapter must expose direct-item prefetch", prefetchMethod >= 0 && prefetchStart > prefetchMethod);
        assertTrue("normal load must consume the matching prefetch after the crawler Vod arrives", loadMethod >= 0 && take > loadMethod);
        assertFalse("prefetch must not create a temporary Vod", prefetchBody.contains("new Vod("));
        assertFalse("prefetch must not enrich a Vod before crawler detail returns", prefetchBody.contains("enrichVod("));
        assertFalse("prefetch must not publish a Vod event before crawler detail returns", prefetchBody.contains("notifyVodChanged("));
    }

    @Test
    public void detailSwitchInvalidatesTmdbBeforeCachedDetailCanApply() throws Exception {
        Path leanbackPath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        int leanbackNewIntent = leanback.indexOf("protected void onNewIntent(Intent intent)");
        int leanbackResetCall = leanback.indexOf("resetDetailForNewIntent();", leanbackNewIntent);
        int leanbackCheck = leanback.indexOf("checkId();", leanbackResetCall);
        int leanbackReset = leanback.indexOf("private void resetDetailForNewIntent()");
        int leanbackInvalidate = leanback.indexOf("mTmdbUIAdapter.beginDetailRequest();", leanbackReset);
        int leanbackResetEnd = leanback.indexOf("private void clearDetailAdapters()", leanbackReset);

        Path mobilePath = findFlavorJavaPath("mobile").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        int mobileNewIntent = mobile.indexOf("protected void onNewIntent(Intent intent)");
        int mobileOldKey = mobile.indexOf("String oldKey = getKey();", mobileNewIntent);
        int mobileNewKey = mobile.indexOf("String key = Objects.toString(intent.getStringExtra(\"key\"), \"\");", mobileOldKey);
        int mobileIdentityCheck = mobile.indexOf("id.equals(oldId) && key.equals(oldKey)", mobileNewKey);
        int mobileExtras = mobile.indexOf("getIntent().putExtras(intent);", mobileIdentityCheck);
        int mobileInvalidate = mobile.indexOf("mTmdbUIAdapter.beginDetailRequest();", mobileExtras);
        int mobileCheck = mobile.indexOf("checkId();", mobileInvalidate);

        assertTrue("leanback new-intent flow must reset detail state before checking the next cached detail",
                leanbackNewIntent >= 0 && leanbackResetCall > leanbackNewIntent && leanbackCheck > leanbackResetCall);
        assertTrue("leanback detail reset must invalidate the old TMDB generation",
                leanbackReset >= 0 && leanbackInvalidate > leanbackReset && leanbackResetEnd > leanbackInvalidate);
        assertTrue("mobile must treat site key plus id as the detail identity",
                mobileNewIntent >= 0 && mobileOldKey > mobileNewIntent && mobileNewKey > mobileOldKey && mobileIdentityCheck > mobileNewKey);
        assertTrue("mobile must invalidate the old TMDB generation before checking the next cached detail",
                mobileExtras > mobileIdentityCheck && mobileInvalidate > mobileExtras && mobileCheck > mobileInvalidate);
    }

    @Test
    public void mobileDropsStaleVodEventsBeforeUpdatingCurrentPage() throws Exception {
        Path sourcePath = findFlavorJavaPath("mobile").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int observer = source.indexOf("public void onRefreshEvent(RefreshEvent event)");
        int vodBranch = source.indexOf("event.getType() == RefreshEvent.Type.VOD", observer);
        int currentGuard = source.indexOf("if (!isCurrentVodEvent(event.getVod()))", vodBranch);
        int update = source.indexOf("updateVod(event.getVod());", currentGuard);
        int helper = source.indexOf("private boolean isCurrentVodEvent(Vod item)", update);
        int sharedGuard = source.indexOf("VodEventGuard.matches(item, getKey(), getId(), mVod == null ? \"\" : mVod.getId())", helper);

        assertTrue("mobile VOD events must be identity-checked before mutating mVod and Intent state",
                observer >= 0 && vodBranch > observer && currentGuard > vodBranch && update > currentGuard);
        assertTrue("mobile stale-event guard must delegate to the shared site/id policy",
                helper > update && sharedGuard > helper);
    }

    @Test
    public void leanbackGuardsTmdbEventsWithNavigationAndLoadedVodIdentities() throws Exception {
        Path sourcePath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        int helper = source.indexOf("private boolean isCurrentVodEvent(Vod item)");
        int sharedGuard = source.indexOf("VodEventGuard.matches(item, getKey(), getId(), mVod == null ? \"\" : mVod.getId())", helper);

        assertTrue("leanback stale-event guard must accept only the current navigation or loaded Vod identity",
                helper >= 0 && sharedGuard > helper);
    }

    @Test
    public void mobileVideoActivityUsesFineGrainedTmdbRefreshHandlers() throws Exception {
        Path activityPath = findFlavorJavaPath("mobile").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String activity = Files.readString(activityPath, StandardCharsets.UTF_8);
        int observer = activity.indexOf("public void onRefreshEvent(RefreshEvent event)");
        int coreBranch = activity.indexOf("event.getType() == RefreshEvent.Type.VOD_CORE", observer);
        int recommendationBranch = activity.indexOf("event.getType() == RefreshEvent.Type.VOD_RECOMMENDATIONS", coreBranch);
        int personalBranch = activity.indexOf("event.getType() == RefreshEvent.Type.VOD_PERSONAL", recommendationBranch);
        int episodeBranch = activity.indexOf("event.getType() == RefreshEvent.Type.VOD_EPISODE_TITLES", personalBranch);
        int coreUpdate = activity.indexOf("updateVod(event.getVod());", coreBranch);
        int recommendationCall = activity.indexOf("refreshTmdbRecommendations();", recommendationBranch);
        int personalCall = activity.indexOf("refreshTmdbPersonalRecommendations();", personalBranch);
        int episodeCall = activity.indexOf("refreshTmdbEpisodeTitles();", episodeBranch);

        assertTrue("mobile must route each TMDB completion to a dedicated handler",
                observer >= 0 && coreBranch > observer && recommendationBranch > coreBranch
                        && personalBranch > recommendationBranch && episodeBranch > personalBranch);
        assertTrue("only core detail completion may run the full VOD update",
                coreUpdate > coreBranch && coreUpdate < recommendationBranch);
        assertTrue("recommendation, personal and episode metadata events need fine-grained handlers",
                recommendationCall > recommendationBranch && personalCall > personalBranch && episodeCall > episodeBranch);

        int recommendationHelper = activity.indexOf("private void refreshTmdbRecommendations()");
        int personalHelper = activity.indexOf("private void refreshTmdbPersonalRecommendations()", recommendationHelper);
        int episodeHelper = activity.indexOf("private void refreshTmdbEpisodeTitles()", personalHelper);
        int nextHelper = activity.indexOf("private ", episodeHelper + 1);
        String recommendationBody = recommendationHelper >= 0 && personalHelper > recommendationHelper
                ? activity.substring(recommendationHelper, personalHelper) : "";
        String personalBody = personalHelper >= 0 && episodeHelper > personalHelper
                ? activity.substring(personalHelper, episodeHelper) : "";
        String episodeBody = episodeHelper >= 0 && nextHelper > episodeHelper
                ? activity.substring(episodeHelper, nextHelper) : "";

        assertTrue("related recommendations should only rebind their header row",
                recommendationBody.contains("mTmdbHeaderView.refreshRecommendations();"));
        assertTrue("personal recommendations should only rebind their header rows",
                personalBody.contains("mTmdbHeaderView.refreshPersonalRecommendationRows();"));
        assertFalse("recommendation refreshes must not rebuild source or episode state",
                recommendationBody.contains("updateVod(") || recommendationBody.contains("updateFlag(")
                        || recommendationBody.contains("setEpisodeAdapter(") || recommendationBody.contains("mSourceEpisodeSeasonCache.clear()")
                        || personalBody.contains("updateVod(") || personalBody.contains("updateFlag(")
                        || personalBody.contains("setEpisodeAdapter(") || personalBody.contains("mSourceEpisodeSeasonCache.clear()"));
        assertTrue("episode metadata completion should refresh only the visible episode page",
                episodeBody.contains("int maxGroupSize = shouldUseTmdbDetailLayout() ? EpisodeRangePolicy.CARD_PAGE_MAX_SIZE : 0;")
                        && episodeBody.contains("mEpisodeAdapter.refreshMetadata(displayItems);")
                        && episodeBody.contains("mTmdbHeaderView.refreshEpisodeMetadata();"));
        assertFalse("shared TMDB episode metadata must not re-enter the full VOD/flag rebuild path",
                episodeBody.contains("updateVod(") || episodeBody.contains("updateFlag(") || episodeBody.contains("setEpisodeAdapter("));

        int updateVod = activity.indexOf("private void updateVod(Vod item)");
        int updateVodEnd = activity.indexOf("private String tmdbEpisodeCompactText()", updateVod);
        String updateVodBody = updateVod >= 0 && updateVodEnd > updateVod ? activity.substring(updateVod, updateVodEnd) : "";
        assertTrue("core enrichment of the current Vod must retain the existing source-season cache",
                updateVodBody.contains("if (mVod != item) mSourceEpisodeSeasonCache.clear();"));

        Path headerPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String header = Files.readString(headerPath, StandardCharsets.UTF_8);
        assertTrue("mobile header must expose lightweight row and episode metadata refresh methods",
                header.contains("public void refreshRecommendations()")
                        && header.contains("public void refreshPersonalRecommendationRows()")
                        && header.contains("public void refreshEpisodeMetadata()"));
    }


    @Test
    public void videoActivitiesMergeEpisodeMetadataBeforeRebindingCards() throws Exception {
        assertEpisodeMetadataEventMerge("mobile");
        assertEpisodeMetadataEventMerge("leanback");
    }

    @Test
    public void tmdbAdapterTracksAndCancelsAttachedPrefetchAndLogsReuseState() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int activeField = source.indexOf("ListenableFuture<TmdbDetailPrefetch.Result> activePrefetch");
        int attach = source.indexOf("setActivePrefetch(prefetched)");
        int clear = source.indexOf("clearActivePrefetch(prefetched)", attach);
        int begin = source.indexOf("public void beginDetailRequest()");
        int cancel = source.indexOf("cancelActivePrefetch();", begin);
        int release = source.indexOf("public void release()", cancel);
        int reuseLog = source.indexOf("return \"reuse\"", release);
        int failureLog = source.indexOf("logPrefetchFailure(", reuseLog);

        assertTrue("adapter must retain the consumed future until it completes", activeField >= 0 && attach > activeField && clear > attach);
        assertTrue("new detail requests and release must cancel an attached prefetch", begin >= 0 && cancel > begin && release > cancel);
        assertTrue("prefetch logs must distinguish reuse and register failure logging", reuseLog > release && failureLog > reuseLog);
    }

    private static void assertDirectTmdbPrefetchOrder(String flavor) throws Exception {
        Path sourcePath = findFlavorJavaPath(flavor).resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int getDetail = source.indexOf("private void getDetail(boolean refresh)");
        String crawlerCall = "mViewModel.detailContent(getKey(), getId(), refresh);";
        int refreshEvent = source.indexOf("if (event.getType() == RefreshEvent.Type.DETAIL) getDetail(true);");
        int prefetch = source.indexOf("prefetchDirectTmdbDetail();", getDetail);
        int crawler = source.indexOf(crawlerCall, getDetail);
        int helper = source.indexOf("private void prefetchDirectTmdbDetail()", crawler);
        int invalidate = source.indexOf("mTmdbUIAdapter.beginDetailRequest();", helper);
        int explicitItem = source.indexOf("getTmdbItem()", invalidate);
        int adapterPrefetch = source.indexOf("mTmdbUIAdapter.prefetch(item);", explicitItem);

        assertTrue(sourcePath + " must start direct TMDB prefetch before crawler detail", getDetail >= 0 && prefetch > getDetail && crawler > prefetch);
        assertTrue(sourcePath + " manual detail refresh must bypass the reusable detail cache", refreshEvent >= 0);
        assertTrue(sourcePath + " must invalidate the previous detail before reading the next explicit item", helper > crawler && invalidate > helper && explicitItem > invalidate);
        assertTrue(sourcePath + " must only prefetch an explicit TmdbItem", adapterPrefetch > explicitItem);
    }

    @Test
    public void recommendationExecutorIsIsolatedFromDetailExecutor() {
        assertNotSame(Task.executor(), Task.recommendationExecutor());
    }

    @Test
    public void taskScopeCancelAllInterruptsWorkAndRejectsStaleNestedSubmissions() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService alternateExecutor = Executors.newSingleThreadExecutor();
        Task.Scope scope = new Task.Scope(executor);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicInteger nestedRuns = new AtomicInteger();
        try {
            assertEquals(Integer.valueOf(42), scope.submitCallable(alternateExecutor, () -> 42).get(1, TimeUnit.SECONDS));
            Future<?> running = scope.submit(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
                scope.submit(alternateExecutor, () -> nestedRuns.incrementAndGet());
            });

            assertTrue(started.await(1, TimeUnit.SECONDS));
            scope.cancelAll();
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            assertTrue(running.isCancelled());
            assertEquals(0, nestedRuns.get());
        } finally {
            scope.close();
            executor.shutdownNow();
            alternateExecutor.shutdownNow();
        }
    }

    @Test
    public void tmdbRatingRequestsAreSharedAndLifecycleScoped() throws Exception {
        Path headerPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String header = Files.readString(headerPath, StandardCharsets.UTF_8);
        assertTrue("TMDB header must own cancellable rating work",
                header.contains("private final Task.Scope backgroundTasks = new Task.Scope(Task.recommendationExecutor());"));
        assertTrue("rebinding or removing the header must cancel stale rating work",
                header.contains("backgroundTasks.cancelAll();"));
        assertTrue("destroying the header must close rating work",
                header.contains("backgroundTasks.close();"));
        assertTrue("all header OMDb calls must use the shared service",
                header.contains("OmdbService.fetch("));
        assertFalse("header ratings must not use the shared detail executor",
                header.contains("com.fongmi.android.tv.utils.Task.execute(() -> {"));
        assertFalse("header ratings must not create one OkHttp client per request",
                header.contains("new okhttp3.OkHttpClient.Builder()"));

        Path leanbackPath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = Files.readString(leanbackPath, StandardCharsets.UTF_8);
        int fetch = leanback.indexOf("private void fetchTmdbOmdbRatings");
        int next = leanback.indexOf("private void hideTmdbRatingChips", fetch);
        String fetchBody = leanback.substring(fetch, next);
        assertTrue("leanback OMDb rating work must be lifecycle scoped",
                leanback.contains("Task.Scope mTmdbRatingTasks") && fetchBody.contains("mTmdbRatingTasks.submit(() -> {"));
        assertTrue("leanback OMDb ratings must use the shared service", fetchBody.contains("OmdbService.fetch("));
        assertFalse("leanback OMDb ratings must not create one OkHttp client per request",
                fetchBody.contains("new okhttp3.OkHttpClient.Builder()"));

        for (String flavor : List.of("mobile", "leanback")) {
            Path sourcePath = findFlavorJavaPath(flavor).resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
            assertTrue(sourcePath + " must destroy TMDB header background work on exit",
                    source.contains("mTmdbHeaderView.onDestroy();"));
        }
    }
    @Test
    public void videoActivitiesCancelNativeRecommendationsOnSwitchAndExit() throws Exception {
        for (String flavor : List.of("mobile", "leanback")) {
            Path sourcePath = findFlavorJavaPath(flavor).resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
            assertTrue(sourcePath + " must own a cancellable recommendation scope", source.contains("Task.Scope mPersonalRecommendationTasks"));
            assertTrue(sourcePath + " must cancel old recommendation work before a new detail", source.contains("mPersonalRecommendationTasks.cancelAll();"));
            assertTrue(sourcePath + " must stop history refresh while leaving", source.contains("if (isPlaybackExiting() || isFinishing() || isDestroyed()) return;"));
            assertTrue(sourcePath + " must close recommendation work on destroy", source.contains("mPersonalRecommendationTasks.close();"));
        }
    }

    @Test
    public void episodeMetadataUsesStructuredSeasonResolutionWithoutBlindFallback() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        int resolve = source.indexOf("TmdbSeasonResolver.resolve(");
        int snapshot = source.indexOf("private Integer currentEpisodeMetadataSeason()");
        int apply = source.indexOf("private boolean applyEpisodeTitles(Vod vod, TmdbItem item, int selectedSeason, int generation, int metadataGeneration)");
        int selectedSeason = source.indexOf("Integer selectedSeason = seasonResolution.getSelectedSeason()", snapshot);

        assertTrue("TMDB adapter must resolve the season after TV detail is available", resolve >= 0);
        assertTrue("episode metadata must capture the structured selected season", snapshot > resolve && selectedSeason > snapshot && apply > snapshot);
        assertFalse("unknown seasons must not try season 1 / specials candidates",
                source.substring(apply).contains("episodeMetadataSeasonCandidates("));
    }


    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findFlavorJavaPath(String flavor) {
        Path moduleRelative = Path.of("src", flavor, "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", flavor, "java");
    }

    private static void assertEpisodeMetadataEventMerge(String flavor) throws Exception {
        Path sourcePath = findFlavorJavaPath(flavor).resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        int observer = source.indexOf("public void onRefreshEvent(RefreshEvent event)");
        int branch = source.indexOf("event.getType() == RefreshEvent.Type.VOD_EPISODE_TITLES", observer);
        int merge = source.indexOf("mergeTmdbEpisodeMetadata(event.getVod());", branch);
        int refresh = source.indexOf("refreshTmdbEpisodeTitles();", branch);
        int helper = source.indexOf("private void mergeTmdbEpisodeMetadata(Vod item)");
        int mergeEpisodes = source.indexOf("current.mergeEpisodes(source.getEpisodes()", helper);

        assertTrue(sourcePath + " must merge completed TMDB episode objects before rebinding cards",
                observer >= 0 && branch > observer && merge > branch && refresh > merge && helper >= 0 && mergeEpisodes > helper);
    }
    private static void assertVideoActivityDefersCachedDetail(String flavor) throws Exception {
        Path sourcePath = findFlavorJavaPath(flavor).resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private boolean setCachedTmdbDetail()");
        int take = source.indexOf("VodDetailCache.take(getTmdbVodCacheKey())", method);
        int align = source.indexOf("VodEventGuard.alignCachedIdentity(cached, getKey(), getId());", take);
        int loading = source.indexOf("mBinding.progressLayout.showProgress();", align);
        int queued = source.indexOf("detail cache hit queued", loading);
        int post = source.indexOf("mBinding.getRoot().postDelayed(() ->", queued);
        int apply = source.indexOf("setDetail(Result.vod(cached));", post);
        if (apply < 0) apply = source.indexOf("setDetail(Result.vod(item));", post);
        int delay = source.indexOf("TMDB_CACHED_DETAIL_APPLY_DELAY_MS", apply);

        assertTrue(sourcePath + " is missing cached TMDB source detail path", method >= 0);
        assertTrue("cached source detail should show playback page loading before applying detail on the next frame",
                take > method && align > take && loading > align && queued > loading && post > queued && apply > post && delay > apply);
    }

    private static final class FakeVod extends Vod {

        private String name;

        @Override
        public String getName() {
            return name == null ? "" : name;
        }

        @Override
        public void setName(String vodName) {
            name = vodName;
        }
    }
}
