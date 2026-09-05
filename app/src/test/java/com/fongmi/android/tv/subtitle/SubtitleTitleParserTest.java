package com.fongmi.android.tv.subtitle;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubtitleTitleParserTest {

    @Test
    public void cleanTitle_removesCommonNoise() {
        SubtitleTitleParser parser = new SubtitleTitleParser();
        assertEquals("想见你", parser.cleanTitle("想见你 2019 1080P 国语版"));
    }

    @Test
    public void firstYear_extractsFirstYear() {
        SubtitleTitleParser parser = new SubtitleTitleParser();
        assertEquals(2019, parser.firstYear("想见你 (2019)"));
    }

    @Test
    public void seasonAndEpisodeNumber_parseStructuredEpisodeTokens() {
        SubtitleTitleParser parser = new SubtitleTitleParser();
        assertEquals(2, parser.seasonNumber("最后生还者 S02E03"));
        assertEquals(3, parser.episodeNumber("最后生还者 S02E03"));
        assertEquals(12, parser.episodeNumber("第12集"));
    }

    @Test
    public void aliases_includeCleanAndYearTrimmedVariants() {
        SubtitleTitleParser parser = new SubtitleTitleParser();
        List<String> aliases = parser.aliases("想见你 (2019)", "想见你 国语版");
        assertTrue(aliases.contains("想见你"));
    }
}
