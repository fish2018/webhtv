package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UtilTest {

    @Test
    public void getNumber_extractsStandardFormats() {
        // S01E03 格式
        assertEquals(3, Util.getEpisodeNumber("S01E03"));
        assertEquals(17, Util.getEpisodeNumber("S01E17"));
        assertEquals(23, Util.getEpisodeNumber("s02e23.1080p.mp4"));

        // EP/E 格式
        assertEquals(5, Util.getEpisodeNumber("EP05"));
        assertEquals(12, Util.getEpisodeNumber("E12"));
        assertEquals(8, Util.getEpisodeNumber("Ep08.mkv"));

        // 独立数字
        assertEquals(17, Util.getEpisodeNumber("17"));
        assertEquals(3, Util.getEpisodeNumber("03"));
        assertEquals(1, Util.getEpisodeNumber("1"));
    }

    @Test
    public void getNumber_extractsChineseNumbers() {
        // 简单中文数字
        assertEquals(1, Util.getEpisodeNumber("第一集"));
        assertEquals(2, Util.getEpisodeNumber("第二集"));
        assertEquals(5, Util.getEpisodeNumber("第五话"));
        assertEquals(9, Util.getEpisodeNumber("第九章"));

        // 十位数
        assertEquals(10, Util.getEpisodeNumber("第十集"));
        assertEquals(15, Util.getEpisodeNumber("第十五集"));
        assertEquals(23, Util.getEpisodeNumber("第二十三集"));
        assertEquals(99, Util.getEpisodeNumber("第九十九集"));

        // 百位数
        assertEquals(100, Util.getEpisodeNumber("第一百集"));
        assertEquals(105, Util.getEpisodeNumber("第一百零五集"));
        assertEquals(123, Util.getEpisodeNumber("第一百二十三集"));

        // 混合中文和阿拉伯数字
        assertEquals(1, Util.getEpisodeNumber("第1集"));
        assertEquals(17, Util.getEpisodeNumber("第17集"));
        assertEquals(23, Util.getEpisodeNumber("第23话"));
    }

    @Test
    public void getNumber_filtersOutInvalidNumbers() {
        // 00.mp4 应该返回 -1（无效集号）
        assertEquals(-1, Util.getEpisodeNumber("00.mp4"));
        assertEquals(-1, Util.getEpisodeNumber("000"));
        assertEquals(-1, Util.getEpisodeNumber("0"));

        // 年份应该被过滤
        assertEquals(-1, Util.getEpisodeNumber("2026"));
        assertEquals(-1, Util.getEpisodeNumber("1999"));
        assertEquals(-1, Util.getEpisodeNumber("2024.mp4"));

        // 版本号应该被过滤
        assertEquals(5, Util.getEpisodeNumber("EP05.v2"));
        assertEquals(3, Util.getEpisodeNumber("第三集 v3.0"));
    }

    @Test
    public void getNumber_filtersOutFileSize() {
        // 文件大小应该被移除
        assertEquals(17, Util.getEpisodeNumber("[1.87GB]第17集"));
        assertEquals(23, Util.getEpisodeNumber("第23集[2.03GB]"));
        assertEquals(5, Util.getEpisodeNumber("[210.03G]EP05"));

        // 括号内的数字应该被忽略
        assertEquals(8, Util.getEpisodeNumber("剧名(2024)第8集"));
        assertEquals(15, Util.getEpisodeNumber("Series[1080p]EP15"));
    }

    @Test
    public void getNumber_filtersOutVideoQuality() {
        // 画质信息应该被移除
        assertEquals(17, Util.getEpisodeNumber("第17集.1080p.mkv"));
        assertEquals(23, Util.getEpisodeNumber("EP23.4K.BluRay.x265"));
        assertEquals(5, Util.getEpisodeNumber("E05.2160p.WEB-DL.AAC"));
        assertEquals(12, Util.getEpisodeNumber("12.720p.HEVC.mp4"));
        assertEquals(85, Util.getEpisodeNumber("85_4K.mp4"));
    }

    @Test
    public void getNumber_ignoresUnderscoreSeparatedDates() {
        assertEquals(-1, Util.getEpisodeNumber("show_2026_07_18.mp4"));
        assertEquals(-1, Util.getEpisodeNumber("release_2024_12_31.mkv"));
    }

    @Test
    public void getNumber_handlesComplexRealWorldCases() {
        // 真实世界的复杂文件名
        assertEquals(17, Util.getEpisodeNumber("The.Eternal.Fragrance.2026.S01E17.1080p.WEB-DL.H264.AAC-GPTHD[1.87GB]"));
        assertEquals(18, Util.getEpisodeNumber("权力的游戏.S08E18.4K.BluRay.x265[2.5GB]"));
        assertEquals(23, Util.getEpisodeNumber("[1.89GB]第23集.皇子斗法，为爱人燃放烟花！[2026-07-17]"));
        assertEquals(5, Util.getEpisodeNumber("剧名 第五集 高清1080p v2"));

        // 只有数字的文件名
        assertEquals(17, Util.getEpisodeNumber("17"));
        assertEquals(3, Util.getEpisodeNumber("03"));

        // 无效的情况
        assertEquals(-1, Util.getEpisodeNumber("00.mp4"));
        assertEquals(-1, Util.getEpisodeNumber("trailer"));
        assertEquals(-1, Util.getEpisodeNumber("preview.2024.mp4"));
    }

    @Test
    public void getNumber_handlesEdgeCases() {
        // 空字符串
        assertEquals(-1, Util.getEpisodeNumber(""));
        assertEquals(-1, Util.getEpisodeNumber(null));

        // 没有集数信息的文本
        assertEquals(-1, Util.getEpisodeNumber("剧名"));
        assertEquals(-1, Util.getEpisodeNumber("trailer.mp4"));

        // 超大集数（超过999应该被拒绝）
        assertEquals(-1, Util.getEpisodeNumber("1000"));
        assertEquals(-1, Util.getEpisodeNumber("9999"));

        // 边界值
        assertEquals(1, Util.getEpisodeNumber("1"));
        assertEquals(999, Util.getEpisodeNumber("999"));
        assertEquals(99, Util.getEpisodeNumber("第九十九集"));
    }

    @Test
    public void getNumber_parsesSeasonEpisodeSuffixWithoutConcatenating() {
        assertEquals(1, Util.getEpisodeNumber("一人之下6_01"));
        assertEquals(20, Util.getEpisodeNumber("一人之下6_20.mp4"));
        assertEquals(-1, Util.getEpisodeNumber("601-620"));
        assertEquals(-1, Util.getEpisodeNumber("621-27"));
    }

    @Test
    public void getNumber_prioritizesExplicitFormats() {
        // 当同时存在多种格式时，应该优先使用显式格式
        assertEquals(5, Util.getEpisodeNumber("剧名2024第5集"));  // 第5集 优先于 2024
        assertEquals(17, Util.getEpisodeNumber("S01E17.1080p")); // S01E17 优先于 1080
        assertEquals(23, Util.getEpisodeNumber("EP23.v2"));      // EP23 优先于 v2
    }
}
