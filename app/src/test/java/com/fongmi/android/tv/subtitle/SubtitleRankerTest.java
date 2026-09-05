package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SubtitleRankerTest {

    @Test
    public void rank_prefersConfiguredLanguageOverDefaultChineseBias() {
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("The Last of Us")
                .preferredLanguage("en")
                .build();

        SubtitleCandidate chinese = new SubtitleCandidate("assrt", "1", "The Last of Us 中文字幕", "中文字幕", "srt", "The Last of Us", 60, 0, 0, 0, SubtitleMatchType.METADATA_FUZZY, "query", false, "{}");
        SubtitleCandidate english = new SubtitleCandidate("assrt", "2", "The Last of Us English", "English", "srt", "The Last of Us", 60, 0, 0, 0, SubtitleMatchType.METADATA_FUZZY, "query", false, "{}");

        List<SubtitleCandidate> ranked = new SubtitleRanker().rank(List.of(chinese, english), context);
        assertEquals("2", ranked.get(0).getCandidateId());
    }
}
