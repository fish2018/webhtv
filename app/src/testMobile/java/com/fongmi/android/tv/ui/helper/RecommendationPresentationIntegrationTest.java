package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class RecommendationPresentationIntegrationTest {

    @Test
    public void guessYouLikeRowsRenderBeforeAsynchronousDoubanRatingEnrichment() throws Exception {
        String adapter = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java")));
        String detail = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));
        String service = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "service", "PersonalRecommendationService.java")));

        assertTrue("Initial and paged shared recommendations should enrich ratings without delaying first display",
                occurrences(adapter, "enrichTmdbRatingsAsync(") >= 2);
        assertTrue("Standalone TMDB detail recommendations should enrich ratings without delaying media blocks",
                detail.contains("enrichTmdbRatingsAsync("));
        int loadFromTmdb = service.indexOf("private RecommendationPage loadFromTmdb(");
        int loadFromDouban = service.indexOf("private RecommendationPage loadFromDouban(", loadFromTmdb);
        assertTrue("TMDB recommendation loading must not synchronously wait for rating enrichment",
                loadFromTmdb >= 0 && loadFromDouban > loadFromTmdb
                        && !service.substring(loadFromTmdb, loadFromDouban).contains("enrichTmdbPageRatings("));
    }

    @Test
    public void cachedAiRowsReceiveTheSameAsynchronousDoubanEnrichmentAfterFirstDisplay() throws Exception {
        String adapter = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java")));
        String detail = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));

        assertTrue("Shared AI recommendation pages should update cached source ratings asynchronously",
                adapter.contains("service.enrichTmdbPageRatingsAsync(")
                        && adapter.contains("applyPersonalAiRatingEnrichment(enriched, generation)"));
        assertTrue("Standalone AI recommendation pages should update cached source ratings asynchronously",
                detail.contains("service.enrichTmdbPageRatingsAsync(")
                        && detail.contains("applyTmdbRatingEnrichment(bundle, personalAiItems, enrichedAi, generation)"));
    }

    @Test
    public void everyRecommendationCardKeepsTmdbAndDoubanSlotsAligned() throws Exception {
        String recommendation = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbRecommendationAdapter.java")));
        String rail = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbRailAdapter.java")));
        String presenter = read(findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "presenter", "TmdbRecommendationPresenter.java")));

        assertTrue(recommendation.contains("TmdbRatingFormatter.completeRatings(item)"));
        assertTrue(rail.contains("TmdbRatingFormatter.completeRatings(item"));
        assertTrue(presenter.contains("TmdbRatingFormatter.completeRatings(tmdbItem)"));
    }

    @Test
    public void guessYouLikeRowsExposeLongPressDetailsAndImmediateRemovalEverywhere() throws Exception {
        String leanback = read(findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String sharedHeader = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java")));
        String standaloneDetail = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));

        assertTrue("Leanback 猜你喜欢 cards should open the shared detail dialog on long press",
                leanback.contains("row == RecommendationRow.RECOMMENDATIONS")
                        && leanback.contains("item -> onRecommendationLongClick(item, \"related\")"));
        assertTrue("Shared/mobile 猜你喜欢 cards should expose the same long-press dialog",
                sharedHeader.contains("recommendationAdapter.setOnItemLongClickListener(item -> onRecommendationLongClick(item, \"related\"))"));
        assertTrue("Standalone TMDB detail 猜你喜欢 cards should expose the same long-press dialog",
                standaloneDetail.contains("relatedAdapter.setOnItemLongClickListener(item -> onRecommendationLongClick(item, \"related\"))"));
        assertTrue("A disliked 猜你喜欢 item should disappear immediately from every presentation",
                leanback.contains("mTmdbRecommendationsObjectAdapter.remove(item)")
                        && sharedHeader.contains("recommendationAdapter.removeItem(item)")
                        && sharedHeader.contains("boundAdapter.removeRecommendation(item)")
                        && standaloneDetail.contains("relatedItems.removeIf(candidate -> sameRecommendationItem(candidate, item))")
                        && standaloneDetail.contains("relatedAdapter.removeItem(item)"));
    }

    @Test
    public void everyRecommendationCardTriesTheOppositeImageOrientationBeforeTextPlaceholder() throws Exception {
        String recommendation = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbRecommendationAdapter.java")));
        String rail = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbRailAdapter.java")));
        String presenter = read(findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "presenter", "TmdbRecommendationPresenter.java")));

        assertTrue(recommendation.contains("TmdbImageSelector.cardImage(item, cinema)")
                && recommendation.contains("TmdbImageSelector.cardImage(item, !cinema)"));
        assertTrue(rail.contains("TmdbImageSelector.cardImage(item, cinema)")
                && rail.contains("TmdbImageSelector.cardImage(item, !cinema)"));
        assertTrue(presenter.contains("TmdbImageSelector.cardImage(tmdbItem, false)")
                && presenter.contains("TmdbImageSelector.cardImage(tmdbItem, true)"));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
}
