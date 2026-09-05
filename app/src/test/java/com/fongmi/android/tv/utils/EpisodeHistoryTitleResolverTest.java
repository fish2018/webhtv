package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EpisodeHistoryTitleResolverTest {

    @Test
    public void resolvesIntentScrapedTitleAfterDisplayNameIsCleared() {
        Episode episode = Episode.create("S01E01.mkv", "https://example.com/1");
        episode.setDisplayName(null);

        assertEquals("1. 初次相遇", EpisodeHistoryTitleResolver.resolve(
                episode, Map.of(1, "初次相遇"), true, false));
    }

    @Test
    public void doesNotReuseLaunchTitleForAnotherEpisode() {
        Episode episode = Episode.create("S01E02.mkv", "https://example.com/2");

        assertEquals("S01E02.mkv", EpisodeHistoryTitleResolver.resolve(
                episode, Map.of(1, "第一集标题"), true, false));
    }

    @Test
    public void resolvesAsyncTmdbMetadataWithoutDependingOnDisplayName() {
        Episode episode = Episode.create("S01E02.mkv", "https://example.com/2");
        episode.setDisplayName("被紧凑标题改写的名称");
        episode.setTmdbEpisode(new TmdbEpisode(2, "新的旅程", "", "", "", 0, 0));

        assertEquals("2. 新的旅程", EpisodeHistoryTitleResolver.resolve(
                episode, Map.of(1, "启动集标题"), true, false));
    }

    @Test
    public void ignoresPresentationOnlyDisplayNameWithoutTmdbData() {
        Episode episode = Episode.create("原始第3集.mkv", "https://example.com/3");
        episode.setDisplayName("3");

        assertEquals("原始第3集.mkv", EpisodeHistoryTitleResolver.resolve(
                episode, Map.of(), true, false));
    }

    @Test
    public void respectsOriginalEpisodeNamePreference() {
        Episode episode = Episode.create("S01E04.mkv", "https://example.com/4");

        assertEquals("S01E04.mkv", EpisodeHistoryTitleResolver.resolve(
                episode, Map.of(4, "刮削标题"), false, false));
    }

    @Test
    public void optionallyKeepsSourceFileSizeWithScrapedTitle() {
        Episode episode = Episode.create("[5.37G] S01E05.mkv", "https://example.com/5");

        assertEquals("[5.37G] 5. 最终章", EpisodeHistoryTitleResolver.resolve(
                episode, Map.of(5, "最终章"), true, true));
    }
}
