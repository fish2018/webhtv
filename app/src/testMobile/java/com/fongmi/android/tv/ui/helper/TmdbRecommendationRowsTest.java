package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbItem;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbRecommendationRowsTest {

    @Test
    public void rankedRelated_mergesRecommendationsAndSimilarWithContextRanking() {
        JsonObject detail = JsonParser.parseString("{"
                + "\"genres\":[{\"id\":9648,\"name\":\"悬疑\"}],"
                + "\"original_language\":\"ko\","
                + "\"origin_country\":[\"KR\"]"
                + "}").getAsJsonObject();
        TmdbItem highRated = item(1, "High Rated", 9.6, "en", "US", List.of(35));
        TmdbItem contextual = item(2, "Contextual Similar", 7.0, "ko", "KR", List.of(9648));

        List<TmdbItem> ranked = TmdbRecommendationRows.rankedRelated(detail, List.of(highRated), List.of(contextual));

        assertEquals(List.of("Contextual Similar", "High Rated"), titles(ranked));
    }

    @Test
    public void personalRows_doNotRemoveDuplicatesAgainstOtherRows() {
        TmdbItem related = item(1, "重叠作品");
        TmdbItem tmdbOnly = item(2, "TMDB 个性");
        TmdbItem doubanOnly = item(-3, "豆瓣个性");

        List<TmdbItem> personalTmdb = TmdbRecommendationRows.personalTmdb(List.of(related, tmdbOnly), List.of(related));
        List<TmdbItem> personalDouban = TmdbRecommendationRows.personalDouban(List.of(tmdbOnly, doubanOnly), List.of(related), personalTmdb);

        assertEquals(List.of("重叠作品", "TMDB 个性"), titles(personalTmdb));
        assertEquals(List.of("TMDB 个性", "豆瓣个性"), titles(personalDouban));
    }

    @Test
    public void personalTmdb_keepsRowWhenEveryItemAlsoExistsInRelated() {
        TmdbItem related = item(1, "重叠作品");

        List<TmdbItem> personalTmdb = TmdbRecommendationRows.personalTmdb(List.of(related), List.of(related));

        assertEquals(List.of("重叠作品"), titles(personalTmdb));
    }

    @Test
    public void personalAi_keepsRowWhenEveryItemAlsoExistsInOtherRows() {
        TmdbItem duplicate = item(1, "智能重叠");

        List<TmdbItem> personalAi = TmdbRecommendationRows.personalAi(List.of(duplicate), List.of(duplicate), List.of(duplicate), List.of());

        assertEquals(List.of("智能重叠"), titles(personalAi));
    }

    @Test
    public void personalRows_removeDuplicatesInsideSameRowOnly() {
        TmdbItem first = item(1, "同区重复");
        TmdbItem duplicate = item(1, "同区重复");

        List<TmdbItem> personalTmdb = TmdbRecommendationRows.personalTmdb(List.of(first, duplicate), new ArrayList<>());

        assertEquals(List.of("同区重复"), titles(personalTmdb));
    }

    @Test
    public void sameIdentity_distinguishesKnownMovieAndTvWhenTmdbIdMissing() {
        TmdbItem movie = new TmdbItem(0, "movie", "同名作品", "电影 · 2024", "", "", "");
        TmdbItem tv = new TmdbItem(0, "tv", "同名作品", "剧集 · 2024", "", "", "");
        TmdbItem unknown = new TmdbItem(0, "", "同名作品", "2024", "", "", "");

        assertFalse(TmdbRecommendationRows.sameIdentity(movie, tv));
        assertTrue(TmdbRecommendationRows.sameIdentity(movie, unknown));
    }

    @Test
    public void sameDisplayList_detectsDoubanRatingUpgradeForSameItem() {
        TmdbItem unrated = ratedItem(123, "movie", "指环王2：双塔奇兵", 8.4, 8.4, 0.0);
        TmdbItem enriched = ratedItem(123, "movie", "指环王2：双塔奇兵", 8.4, 8.4, 9.2);

        assertFalse(TmdbRecommendationRows.sameDisplayList(List.of(unrated), List.of(enriched)));
    }

    @Test
    public void sameDisplayList_detectsTmdbRatingUpgradeForSameItem() {
        TmdbItem unrated = ratedItem(123, "movie", "指环王2：双塔奇兵", 9.2, 0.0, 9.2);
        TmdbItem enriched = ratedItem(123, "movie", "指环王2：双塔奇兵", 9.2, 8.4, 9.2);

        assertFalse(TmdbRecommendationRows.sameDisplayList(List.of(unrated), List.of(enriched)));
    }

    @Test
    public void sameDisplayList_detectsPosterUpgradeForSameTitle() {
        TmdbItem placeholder = new TmdbItem(-1, "tv", "长安十二时辰", "剧集 · 2019", "推荐理由", "", "");
        TmdbItem resolved = new TmdbItem(123, "tv", "长安十二时辰", "剧集 · 2019", "推荐理由", "poster.jpg", "backdrop.jpg");

        assertFalse(TmdbRecommendationRows.sameDisplayList(List.of(placeholder), List.of(resolved)));
        assertTrue(TmdbRecommendationRows.sameDisplayList(List.of(resolved), List.of(resolved)));
    }

    private static TmdbItem item(int id, String title) {
        return item(id, title, 0.0, "", "", new ArrayList<>());
    }

    private static TmdbItem item(int id, String title, double rating, String language, String country, List<Integer> genreIds) {
        return new TmdbItem(id, "movie", title, "", "", "", "", "", rating, language, country, genreIds);
    }

    private static TmdbItem ratedItem(int id, String mediaType, String title, double rating, double tmdbRating, double doubanRating) {
        return new TmdbItem(id, mediaType, title, "电影 · 2002", "", "", "", "", rating,
                "en", "US", List.of(12), "", tmdbRating, doubanRating);
    }

    private static List<String> titles(List<TmdbItem> items) {
        return items.stream().map(TmdbItem::getTitle).toList();
    }
}
