package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EpisodeTitleFormatterTest {

    @Test
    public void extractFileSizeSupportsCloudDriveNames() {
        assertEquals("[5.37G]", EpisodeTitleFormatter.extractFileSize("[5.37G] 01.mkv"));
        assertEquals("【850MB】", EpisodeTitleFormatter.extractFileSize("【850MB】第02集"));
        assertEquals("(1.2TB)", EpisodeTitleFormatter.extractFileSize("(1.2 TB) Episode 03"));
        assertEquals("696.68M", EpisodeTitleFormatter.extractFileSize("第04集 696.68M.mp4"));
        assertEquals("[44.01G]", EpisodeTitleFormatter.extractFileSize("[我不是药神 2018 Dying to survive][原盘国语简体字幕花絮][44.01G].iso"));
    }

    @Test
    public void removeFileSizesCleansProviderPrefixAndFilenameSuffix() {
        assertEquals(
                "[我不是药神 2018 Dying to survive][原盘国语简体字幕花絮].iso",
                EpisodeTitleFormatter.removeFileSizes("[44.01GB] [我不是药神 2018 Dying to survive][原盘国语简体字幕花絮][44.01G].iso"));
        assertEquals(
                "Movie Name - Part.mkv",
                EpisodeTitleFormatter.removeFileSizes("Movie Name - [5G] Part.mkv"));
    }

    @Test
    public void extractFileSizeIgnoresCommonResolutionTokens() {
        assertEquals("", EpisodeTitleFormatter.extractFileSize("4K 1080P 第01集"));
        assertEquals("", EpisodeTitleFormatter.extractFileSize("2160p HDR 第02集"));
    }

    @Test
    public void withSourceFileSizePrefixesScrapedTitleWhenEnabled() {
        String title = EpisodeTitleFormatter.formatTmdbTitle(1, "相遇");
        assertEquals("[5.37G] 1. 相遇", EpisodeTitleFormatter.withSourceFileSize("[5.37G] 01.mkv", title, true));
    }

    @Test
    public void formatTmdbTitleKeepsFileSizeOutOfCleanTitle() {
        String title = EpisodeTitleFormatter.formatTmdbTitle("274", "[905.83MB] 274.mkv", "夸克原画");
        assertEquals("274. 夸克原画", title);
        assertEquals("[905.83MB] 274. 夸克原画", EpisodeTitleFormatter.withSourceFileSize("[905.83MB] 274.mkv", title, true));
    }

    @Test
    public void formatTmdbTitleUsesTmdbTitleWhenLabelIsMissing() {
        assertEquals("相遇", EpisodeTitleFormatter.formatTmdbTitle(null, "[5.37G] 01.mkv", "相遇"));
    }

    @Test
    public void withSourceFileSizeRespectsSwitchAndAvoidsDuplicates() {
        assertEquals("1. 相遇", EpisodeTitleFormatter.withSourceFileSize("[5.37G] 01.mkv", "1. 相遇", false));
        assertEquals("[5.37G] 1. 相遇", EpisodeTitleFormatter.withSourceFileSize("[5.37G] 01.mkv", "[5.37G] 1. 相遇", true));
    }

    @Test
    public void buildPlaybackTitleAppendsCurrentEpisode() {
        assertEquals("三体 第2集", EpisodeTitleFormatter.buildPlaybackTitle("三体", "第2集"));
        assertEquals("第2集", EpisodeTitleFormatter.buildPlaybackTitle("", "第2集"));
        assertEquals("三体", EpisodeTitleFormatter.buildPlaybackTitle("三体", ""));
        assertEquals("三体", EpisodeTitleFormatter.buildPlaybackTitle("三体", "三体"));
    }
}
