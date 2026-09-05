package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.ResolvedMediaIdentity;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubtitlePlannerTest {

    @Test
    public void buildMovieQueries_prefersTitleWithYearThenFallbackTitle() {
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("想见你")
                .year(2019)
                .identity(ResolvedMediaIdentity.builder().canonicalTitle("想见你").year(2019).build())
                .build();

        List<SubtitleQuery> queries = new SubtitleQueryPlanner().build(context);
        assertEquals("想见你 2019", queries.get(0).getText());
        assertTrue(queries.stream().anyMatch(query -> "想见你".equals(query.getText())));
    }

    @Test
    public void buildEpisodeQueries_prefersSxeAndEpisodeTitle() {
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("tv")
                .canonicalTitle("最后生还者")
                .seasonNumber(2)
                .episodeNumber(3)
                .episodeTitle("漫长漫长时光")
                .identity(ResolvedMediaIdentity.builder().canonicalTitle("最后生还者").seasonNumber(2).episodeNumber(3).episodeTitle("漫长漫长时光").build())
                .build();

        List<SubtitleQuery> queries = new SubtitleQueryPlanner().build(context);
        assertEquals("最后生还者 S02E03", queries.get(0).getText());
        assertTrue(queries.stream().anyMatch(query -> query.getText().contains("第3集")));
        assertTrue(queries.stream().anyMatch(query -> query.getText().contains("漫长漫长时光")));
    }

    @Test
    public void buildManual_usesEditableKeywordAsSingleQuery() {
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("镖人：风起大漠")
                .year(2026)
                .preferredLanguage("zh-Hans")
                .build();

        List<SubtitleQuery> queries = new SubtitleQueryPlanner().buildManual(context, " 镖人：风起大漠 2026 ");
        assertEquals(1, queries.size());
        assertEquals("镖人：风起大漠 2026", queries.get(0).getText());
        assertEquals("zh-Hans", queries.get(0).getLanguage());
        assertEquals(2026, queries.get(0).getYear());
    }
}
