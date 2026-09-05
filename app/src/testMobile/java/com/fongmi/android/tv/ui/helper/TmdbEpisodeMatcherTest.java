package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbEpisodeMatcherTest {

    @Test
    public void allowsMatchedSourceEpisodeTitle() {
        Episode episode = Episode.create("106. 六圣紫霄议封神", "http://example.test/106");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(106, "六圣紫霄议封神", "", "", "", 0, 0);

        assertTrue(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, 106));
    }

    @Test
    public void allowsDifferentSourceEpisodeTitleForSameNumber() {
        Episode episode = Episode.create("106. 通天宫中通天抗压", "http://example.test/106");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(106, "六圣紫霄议封神", "", "", "", 0, 0);

        assertTrue(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, 106));
    }

    @Test
    public void allowsNumberOnlySourceEpisodeName() {
        Episode episode = Episode.create("第106集", "http://example.test/106");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(106, "六圣紫霄议封神", "", "", "", 0, 0);

        assertTrue(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, 106));
    }

    @Test
    public void allowsNumberOnlyReleaseNameWithSourceTags() {
        Episode episode = Episode.create("[1.08G] S01E03.2026.2160p.WEB-DL.H265.10bit", "http://example.test/3");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(3, "雷修远看似在帮助小棒槌", "", "", "", 0, 0);

        assertTrue(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, 3));
    }

    @Test
    public void ignoresFileSizeWhenItMatchesEpisodeNumber() {
        Episode episode = Episode.create("[1.41G] S01E01.2026.2160p.WEB-DL.H265.10bit", "http://example.test/1");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(1, "青丘脚下", "", "", "", 0, 0);

        assertEquals(1, episode.getNumber());
        assertTrue(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, 1));
    }

    @Test
    public void appliesStandardSeasonEpisodeReleaseNamesByNumber() {
        Episode episode21 = Episode.create("[1.14G] S01E21.2026.2160p.WEB-DL.H265.10bit", "http://example.test/21");
        Episode episode22 = Episode.create("[1.22G] S01E22.2026.2160p.WEB-DL.H265.10bit", "http://example.test/22");

        assertEquals(21, episode21.getNumber());
        assertEquals(22, episode22.getNumber());
        assertTrue(TmdbEpisodeMatcher.shouldApply(episode21, new TmdbEpisode(21, "妙青重伤众人为其招灵", "", "", "https://image.test/21.jpg", 0, 0), 21));
        assertTrue(TmdbEpisodeMatcher.shouldApply(episode22, new TmdbEpisode(22, "修远黎非入翠玄小屋", "", "", "https://image.test/22.jpg", 0, 0), 22));
    }

    @Test
    public void allowsUnderscoreSeparatedQualitySuffix() {
        Episode episode = Episode.create("85_4K.mp4", "http://example.test/85");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(85, "Episode 85", "", "", "", 0, 0);

        assertEquals(85, episode.getNumber());
        assertTrue(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, 85));
    }

    @Test
    public void rejectsWhenSourceEpisodeNumberDoesNotMatchTmdbEpisodeNumber() {
        Episode episode = Episode.create("11. 啪啪鹿鸣上分！太子认出她", "http://example.test/11");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(12, "诀爱！郦主烧信烧", "", "", "", 0, 0);

        assertEquals(11, episode.getNumber());
        assertFalse(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, 11));
    }

    @Test
    public void rejectsWhenTmdbEpisodeIsNull() {
        Episode episode = Episode.create("12. 诀爱！郦主烧信烧", "http://example.test/12");

        assertEquals(12, episode.getNumber());
        assertFalse(TmdbEpisodeMatcher.shouldApply(episode, null, 12));
    }

    @Test
    public void rejectsWhenSourceEpisodeHasNoNumber() {
        // 无法提取集号的文件名（如 "正片"、"00.mp4"、"trailer"）不应该匹配 TMDB 数据
        // 避免按位置错配（如 00.mp4 被匹配到 E18）
        Episode episode = Episode.create("正片", "http://example.test/1");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(1, "青丘脚下", "", "", "", 0, 0);

        assertEquals(-1, episode.getNumber());
        assertFalse(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, 1));
    }

    @Test
    public void allowsOnlyExplicitlyMarkedCrossSeasonMapping() {
        Episode episode = Episode.create("11", "http://example.test/11");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(1, "Season 2 Episode 1", "", "", "", 0, 0, 0, 2);

        assertFalse(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, 1));
        assertTrue(TmdbEpisodeMatcher.shouldApplyMapped(episode, tmdbEpisode, 2, 1));
        episode.setMappedTmdbEpisode(tmdbEpisode);
        assertTrue(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, 1));
        assertFalse(TmdbEpisodeMatcher.shouldApply(episode, new TmdbEpisode(1, "Other", "", "", "", 0, 0, 0, 2), 1));
    }

    @Test
    public void mappedEpisodeMustMatchExpectedTmdbSeason() {
        Episode episode = Episode.create("11", "http://example.test/11");
        TmdbEpisode seasonTwoEpisode = new TmdbEpisode(1, "Season 2 Episode 1", "", "", "", 0, 0, 0, 2);
        TmdbEpisode wrongSeasonEpisode = new TmdbEpisode(1, "Season 1 Episode 1", "", "", "", 0, 0, 0, 1);

        assertTrue(TmdbEpisodeMatcher.shouldApplyMapped(episode, seasonTwoEpisode, 2, 1));
        assertFalse(TmdbEpisodeMatcher.shouldApplyMapped(episode, wrongSeasonEpisode, 2, 1));
    }

    @Test
    public void rejectsWhenSourceNumberMismatchesTmdb() {
        // 新逻辑：文件名有明确集号时，必须与 TMDB 集号一致才匹配
        // 即使 mappedNumber 说它是第1集，但文件名是"第14集"，就不应该匹配到 TMDB 第1集
        Episode episode14 = Episode.create("第14集", "http://example.test/14");
        TmdbEpisode tmdbS2E1 = new TmdbEpisode(1, "Season 2 Episode 1", "", "", "", 0, 0);

        assertEquals(14, episode14.getNumber());
        // episode.getNumber() (14) != tmdbEpisode.getNumber() (1) → 拒绝匹配
        assertFalse(TmdbEpisodeMatcher.shouldApply(episode14, tmdbS2E1, 1));
    }
}
