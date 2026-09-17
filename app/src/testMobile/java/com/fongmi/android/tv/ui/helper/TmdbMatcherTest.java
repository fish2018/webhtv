package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.service.TmdbService;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class TmdbMatcherTest {

    @Test
    public void cleanVideoName_removesPushObfuscationTokens() {
        TmdbMatcher matcher = new TmdbMatcher(null, new TmdbConfig());

        assertEquals("凡人修仙传", matcher.cleanVideoName("F 凡人#修仙传 动漫 A"));
        assertEquals("凡人修仙传", matcher.cleanVideoName("F 凡人#修仙传 动漫 B"));
    }

    @Test
    public void cleanVideoName_removesTrueColorSourceTag() {
        TmdbMatcher matcher = new TmdbMatcher(null, new TmdbConfig());

        assertEquals("云秀行", matcher.cleanVideoName("云秀行（真彩） 4K HDR"));
    }

    @Test
    public void searchAndMatch_acceptsContainedFranchiseTitleWhenYearMatches() {
        List<TmdbItem> results = List.of(
                item(1, "movie", "纳尼亚传奇：狮子、女巫和魔衣橱", "2005")
        );
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiKey\":\"test\"}");
        TmdbMatcher matcher = new TmdbMatcher(new FakeTmdbService(results), config);

        TmdbItem match = matcher.searchAndMatch("纳尼亚传奇", "movie", 2005);

        assertNotNull(match);
        assertEquals(1, match.getTmdbId());
    }

    @Test
    public void searchAndMatch_rejectsContainedFranchiseTitleWhenYearDiffers() {
        List<TmdbItem> results = List.of(
                item(1, "movie", "纳尼亚传奇：凯斯宾王子", "2008")
        );
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiKey\":\"test\"}");
        TmdbMatcher matcher = new TmdbMatcher(new FakeTmdbService(results), config);

        assertNull(matcher.searchAndMatch("纳尼亚传奇", "movie", 2005));
    }

    @Test
    public void searchAndMatch_respectsExplicitMediaType() {
        List<TmdbItem> results = List.of(
                item(1, "movie", "同名作品", "2024"),
                item(2, "tv", "同名作品", "2024")
        );
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiKey\":\"test\"}");
        TmdbMatcher matcher = new TmdbMatcher(new FakeTmdbService(results), config);

        TmdbItem tv = matcher.searchAndMatch("同名作品", "tv", 2024);
        TmdbItem movie = matcher.searchAndMatch("同名作品", "movie", 2024);

        assertNotNull(tv);
        assertNotNull(movie);
        assertEquals("tv", tv.getMediaType());
        assertEquals("movie", movie.getMediaType());
    }

    @Test
    public void searchAndMatchRethrowsAuthenticationFailures() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiKey\":\"invalid\"}");
        TmdbMatcher matcher = new TmdbMatcher(new AuthFailureTmdbService(), config);

        assertThrows(TmdbService.AuthException.class, () -> matcher.searchAndMatch("test title"));
    }

    @Test
    public void sortSearchResults_ordersExactTitleMatchFirst() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiKey\":\"test\"}");
        TmdbMatcher matcher = new TmdbMatcher(null, config);
        List<TmdbItem> results = new ArrayList<>(List.of(
                item(1, "tv", "后宫露营!", "2022"),
                item(2, "tv", "甄嬛传", "2011"),
                item(3, "tv", "宫", "2011")
        ));

        matcher.sortSearchResults(results, "宫");

        assertEquals(3, results.get(0).getTmdbId());
        assertEquals(1, results.get(1).getTmdbId());
        assertEquals(2, results.get(2).getTmdbId());
    }

    private static TmdbItem item(int id, String mediaType, String title, String subtitle) {
        return new TmdbItem(id, mediaType, title, subtitle, "", "", "", "", 8.0, "", "", new ArrayList<>());
    }

    private static final class AuthFailureTmdbService extends TmdbService {

        @Override
        public List<TmdbItem> search(String keyword, TmdbConfig config) {
            throw new TmdbService.AuthException(401, "TMDB search failed: HTTP 401");
        }
    }

    private static final class FakeTmdbService extends TmdbService {

        private final List<TmdbItem> results;

        private FakeTmdbService(List<TmdbItem> results) {
            this.results = results;
        }

        @Override
        public List<TmdbItem> search(String keyword, TmdbConfig config) {
            return new ArrayList<>(results);
        }
    }
}
