package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbDetailInlineOsdTitleTest {

    @Test
    public void inlineOsdUsesCurrentEpisodeTitleInsteadOfSeasonProgress() throws IOException {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("private String getInlineOsdTitle()");
        int end = source.indexOf("\n    private ", start + 1);

        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);
        assertTrue(method.contains("historyEpisodeTitle(selectedEpisode)"));
        assertTrue(method.contains("getString(R.string.detail_title, name, episodeTitle)"));
        assertFalse(method.contains("selectedEpisode.getName()"));
        assertFalse(method.contains("tmdbEpisodeInfo().compactText"));
    }
}
