package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.TmdbItem;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecommendationFeedbackStoreTest {

    @Test
    public void feedbackRoundTrip_preservesEntryMetadata() {
        TmdbItem item = new TmdbItem(123, "tv", "漫长的季节", "2023 · 剧情", "", "", "", "", 9.4);
        RecommendationFeedbackStore.Entry entry = RecommendationFeedbackStore.Entry.from(item, "ai", 1000L);

        List<RecommendationFeedbackStore.Entry> parsed = RecommendationFeedbackStore.parse(
                RecommendationFeedbackStore.serialize(List.of(entry)));

        assertEquals(1, parsed.size());
        assertEquals("漫长的季节", parsed.get(0).getTitle());
        assertEquals("tv", parsed.get(0).getMediaType());
        assertEquals(2023, parsed.get(0).getYear());
        assertEquals("ai", parsed.get(0).getSource());
    }

    @Test
    public void feedbackMatch_normalizesTitleAndAllowsUnknownMediaType() {
        TmdbItem item = new TmdbItem(1, "tv", "The Matrix", "", "", "", "");
        RecommendationFeedbackStore.Entry entry = RecommendationFeedbackStore.Entry.from(item, "tmdb", 1L);
        TmdbItem sameTitle = new TmdbItem(2, "", "The Matrix ( )", "", "", "", "");

        assertTrue(entry.matches(RecommendationFeedbackStore.Entry.from(sameTitle, "ai", 2L)));
    }

    @Test
    public void contains_distinguishesKnownMediaTypesButKeepsUnknownAsWildcard() {
        RecommendationFeedbackStore.Entry blockedMovie = RecommendationFeedbackStore.Entry.from(
                new TmdbItem(1, "movie", "同名作品", "2023", "", "", ""), "tmdb", 1L);

        assertTrue(RecommendationFeedbackStore.contains(List.of(blockedMovie),
                new TmdbItem(2, "movie", "同名作品", "2024", "", "", "")));
        assertFalse(RecommendationFeedbackStore.contains(List.of(blockedMovie),
                new TmdbItem(3, "tv", "同名作品", "2024", "", "", "")));
        assertTrue(RecommendationFeedbackStore.contains(List.of(blockedMovie),
                new TmdbItem(4, "", "同名作品", "2024", "", "", "")));
    }

    @Test
    public void parse_ignoresNullEntries() {
        assertEquals(0, RecommendationFeedbackStore.parse("[null]").size());
    }

    @Test
    public void active_expiresEntriesAtRetentionBoundary() {
        long now = RecommendationFeedbackStore.RETENTION_MILLIS * 2;
        RecommendationFeedbackStore.Entry expired = RecommendationFeedbackStore.Entry.from(
                new TmdbItem(1, "tv", "已过期", "2020", "", "", ""), "ai", now - RecommendationFeedbackStore.RETENTION_MILLIS);
        RecommendationFeedbackStore.Entry recent = RecommendationFeedbackStore.Entry.from(
                new TmdbItem(2, "movie", "仍有效", "2024", "", "", ""), "tmdb", now - RecommendationFeedbackStore.RETENTION_MILLIS + 1);

        List<RecommendationFeedbackStore.Entry> active = RecommendationFeedbackStore.active(List.of(expired, recent), now);

        assertEquals(1, active.size());
        assertEquals("仍有效", active.get(0).getTitle());
    }

    @Test
    public void without_removesOnlySelectedEntry() {
        RecommendationFeedbackStore.Entry tv = RecommendationFeedbackStore.Entry.from(
                new TmdbItem(1, "tv", "同名作品", "2023", "", "", ""), "ai", 100L);
        RecommendationFeedbackStore.Entry movie = RecommendationFeedbackStore.Entry.from(
                new TmdbItem(2, "movie", "同名作品", "2023", "", "", ""), "tmdb", 200L);
        RecommendationFeedbackStore.Entry other = RecommendationFeedbackStore.Entry.from(
                new TmdbItem(3, "tv", "其他作品", "2022", "", "", ""), "ai", 300L);

        List<RecommendationFeedbackStore.Entry> remaining = RecommendationFeedbackStore.without(List.of(tv, movie, other), tv);

        assertEquals(2, remaining.size());
        assertEquals("movie", remaining.get(0).getMediaType());
        assertEquals("其他作品", remaining.get(1).getTitle());
    }

    @Test
    public void remainingDays_roundsPartialDayUp() {
        long now = 5_000_000_000L;
        RecommendationFeedbackStore.Entry entry = RecommendationFeedbackStore.Entry.from(
                new TmdbItem(1, "tv", "测试", "", "", "", ""), "ai", now - RecommendationFeedbackStore.DAY_MILLIS - 1);

        assertEquals(89, RecommendationFeedbackStore.remainingDays(entry, now));
    }
}
