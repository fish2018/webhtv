package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SearchResultFilterTest {

    @Test
    public void matchesExactAndContainedTitles() {
        assertTrue(SearchResultFilter.matches("庆余年", "庆余年", 80));
        assertTrue(SearchResultFilter.matches("庆余年", "庆余年2", 75));
        assertFalse(SearchResultFilter.matches("庆余年", "庆余年2", 76));
    }

    @Test
    public void matchesEquivalentSeasonNotation() {
        assertTrue(SearchResultFilter.matches("庆余年2", "庆余年 第二季", 80));
        assertTrue(SearchResultFilter.matches("The Last of Us Season 2", "The Last of Us S02", 80));
    }

    @Test
    public void rejectsShortTitleFuzzyFalsePositives() {
        assertFalse(SearchResultFilter.matches("长相思", "长相守", 80));
        assertFalse(SearchResultFilter.matches("无间道2", "无间道3", 80));
        assertFalse(SearchResultFilter.matches("三体2023", "三体2024", 80));
    }

    @Test
    public void rejectsNumericPrefixButAllowsSeparateMetadata() {
        assertFalse(SearchResultFilter.matches("无间道2", "无间道20", 80));
        assertFalse(SearchResultFilter.matches("三体2", "三体2024", 80));
        assertTrue(SearchResultFilter.matches("无间道2", "无间道2 2024 4K", 35));
        assertTrue(SearchResultFilter.matches("The Last of Us Season 2", "The Last of Us S02E03", 75));
    }

    @Test
    public void acceptsHighSimilarityWhenNumbersAgree() {
        assertTrue(SearchResultFilter.matches("流浪地球2", "流浪地求2", 80));
        assertTrue(SearchResultFilter.matches("The Last of Us", "The Last of Us", 80));
    }

    @Test
    public void shortEnglishWordsUseWholeTokenMatching() {
        assertTrue(SearchResultFilter.matches("IT", "IT Chapter Two", 15));
        assertFalse(SearchResultFilter.matches("IT", "Little Women", 15));
    }

    @Test
    public void oneCharacterKeywordSupportsSimilarityAndNoFiltering() {
        assertTrue(SearchResultFilter.matches("囧", "囧", 100));
        assertFalse(SearchResultFilter.matches("囧", "泰囧", 30));
        assertTrue(SearchResultFilter.matches("囧", "泰囧", 0));
    }

    @Test
    public void normalizesTraditionalCaseAndFullWidthText() {
        assertTrue(SearchResultFilter.matches("慶餘年", "庆余年", 80));
        assertTrue(SearchResultFilter.matches("ＡＢＣ", "abc", 80));
    }

    @Test
    public void similarityThresholdControlsFuzzyMatches() {
        assertTrue(SearchResultFilter.matches("流浪地球2", "流浪地求2", 80));
        assertFalse(SearchResultFilter.matches("流浪地球2", "流浪地求2", 81));
    }

    @Test
    public void containedTitlesUseRealCoverageWithoutMinimumScore() {
        assertFalse(SearchResultFilter.matches("莫离", "阿YueYue《莫离(33秒片段)(片段)》[MP3_LRC]", 80));
        assertTrue(SearchResultFilter.matches("莫离", "莫离片段", 50));
        assertFalse(SearchResultFilter.matches("莫离", "莫离片段", 51));
    }

    @Test
    public void hundredPercentRequiresNormalizedEquality() {
        assertTrue(SearchResultFilter.matches("慶餘年", "庆余年", 100));
        assertFalse(SearchResultFilter.matches("庆余年", "[腾讯] 庆余年 第二季 4K", 100));
    }

    @Test
    public void filterPreservesSourceOrder() {
        Vod first = vod("庆余年 第二季");
        Vod ignored = vod("长相守");
        Vod third = vod("庆余年");

        List<Vod> result = SearchResultFilter.filter(List.of(first, ignored, third), "庆余年", 75);

        assertSame(first, result.get(0));
        assertSame(third, result.get(1));
    }

    @Test
    public void zeroSimilarityLeavesResultsUnfiltered() {
        Vod first = vod("庆余年");
        Vod second = vod("长相守");

        List<Vod> result = SearchResultFilter.filter(List.of(first, second), "庆余年", 0);

        assertEquals(2, result.size());
    }

    @Test
    public void emptyFilterResultRemainsMutableForIncrementalTvUpdates() {
        List<Vod> result = SearchResultFilter.filter(List.of(), "庆余年", 80);

        result.add(vod("庆余年"));

        assertEquals(1, result.size());
    }

    private Vod vod(String name) {
        return new Vod() {
            @Override
            public String getName() {
                return name;
            }
        };
    }
}
