package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Episode;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EpisodeTitleCompactTest {

    @Test
    public void garoCollectionUsesSemanticLabelsAndRealEpisodeTitles() {
        List<String> names = List.of(
                "[01.2005 牙狼 第一季] [光の影字幕組][牙狼GARO][01][绘本]2880x2160.mp4 [2.65GB]",
                "[01.2005 牙狼 第一季] [光の影字幕組][牙狼GARO][02][阴我]2880x2160.mp4 [7.36GB]",
                "[01.2005 牙狼 第一季] [光の影字幕組][牙狼GARO][04][晚餐]2880x2160.mp4 [2.52GB]",
                "[01.2005 牙狼 第一季] [光の影字幕組][牙狼GARO][17][水槽]2880x2160.mp4 [2.52GB]",
                "[02.2006 牙狼 特别篇 白夜的魔兽] [光の影字幕組][牙狼 白夜的魔兽][BDRip][720p][x264·AAC][Hi10p].mkv [4.34GB]",
                "[03.2010 牙狼 剧场版 红色镇魂曲] [DBD-Raws][牙狼：红色镇魂曲][1080P][BDRip][HEVC-10bit][FLAC].mkv [4.14GB]",
                "[03.2010 牙狼 剧场版 红色镇魂曲] 牙狼：红色镇魂曲.Garo.The.Movie.Red.Requiem.2010.BD1080P.X264.AAC.Japanese.CHS-JPN.CHD.mp4 [3.21GB]",
                "[04.2011 牙狼 OV 呀（KIBA）暗黑骑士铠传] [光の影字幕组][GARO][Kiba~暗黑骑士铠传][BDrip][1920x1080][X264xAC3][MKV].mkv [3.29GB]",
                "[05.2011 牙狼 第二季 魔戒闪骑] 01[光の影字幕组][牙狼GARO][魔戒闪骑][01][火花][繁日双语][X264][1920x1080][BDrip][MP4][2ACDEA60].mp4 [1.72GB]",
                "[05.2011 牙狼 第二季 魔戒闪骑] 02[光の影字幕组][牙狼GARO][魔戒闪骑][02][街灯][繁日双语][X264][1920x1080][BDrip][MP4][55BFC4B9].mp4 [1.70GB]",
                "[05.2011 牙狼 第二季 魔戒闪骑] 04[光の影字幕组][牙狼GARO][魔戒闪骑][04][王牌][繁日双语][X264][1920x1080][BDrip][MP4][4CE9F2E6].mp4 [1.72GB]"
        );

        assertEquals(List.of(
                "第一季 01 绘本 [2.65GB]",
                "第一季 02 阴我 [7.36GB]",
                "第一季 04 晚餐 [2.52GB]",
                "第一季 17 水槽 [2.52GB]",
                "特别篇 白夜的魔兽 [4.34GB]",
                "剧场版 红色镇魂曲 HEVC [4.14GB]",
                "剧场版 红色镇魂曲 AVC [3.21GB]",
                "OV 呀（KIBA）暗黑骑士铠传 [3.29GB]",
                "第二季 01 火花 [1.72GB]",
                "第二季 02 街灯 [1.70GB]",
                "第二季 04 王牌 [1.72GB]"
        ), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void resolutionsDoNotOverrideStandaloneEpisodeNumbers() {
        List<String> names = List.of(
                "Long Show [01] Alpha 2880x2160 WEB-DL.mp4 [1.00GB]",
                "Long Show [02] Beta 1920x1080 BluRay.mkv [2.00GB]",
                "Long Show [03] Gamma 3840x2160 HEVC.mkv [3.00GB]",
                "Long Show [04] Delta 1280x720 AVC.mp4 [4.00GB]"
        );

        assertEquals(List.of(
                "01 [1.00GB]",
                "02 [2.00GB]",
                "03 [3.00GB]",
                "04 [4.00GB]"
        ), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void realNxMEpisodeTokensRemainSupported() {
        List<String> names = List.of(
                "Long Show 1x02 Alpha 1920x1080 WEB-DL.mkv",
                "Long Show 01x003 Beta 3840x2160 BluRay.mkv"
        );

        assertEquals(List.of("1X02", "01X003"), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void existingStrongEpisodeFormatsKeepTheirBehavior() {
        List<String> names = List.of(
                "Long Show S01E02 Alpha 2160p WEB-DL.mkv",
                "Long Show EP03 Beta 1080p BluRay.mkv",
                "Long Show 第04集 Gamma 720p AVC.mp4",
                "Long Show 2026-07-23 Daily Edition.mp4"
        );

        assertEquals(List.of("S01E02", "EP03", "第04集", "2026-07-23"), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void leadingEpisodeNumbersWinOverLaterMetadataDates() {
        List<String> names = List.of(
                "001 - 4K- 2023-05-03 - HEVC-H.265.mp4",
                "002 - 4K- 2023-05-03 - HEVC-H.265.mp4",
                "010 - 4K- 2023-06-21 - HEVC-H.265.mp4",
                "100 纯享[4K][HEVC][2025-03-11].mp4",
                "120 纯享[4K][HEVC][2025-07-29].mp4",
                "148 纯享[4K][HEVC.AAC][2026.02-10].mp4",
                "172 纯享 [4K][HEVC.AAC][2026.07.21].mp4"
        );

        assertEquals(List.of("001", "002", "010", "100 纯享", "120 纯享", "148 纯享", "172 纯享"), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void driveFolderPrefixesKeepRealEpisodeAndEditionTitlesAheadOfDates() {
        List<String> names = List.of(
                "[001-030]001 - 4K- 2023-05-03 - HEVC-H.265.mp4[628.8 MB]",
                "[100-120]100 纯享[4K][HEVC][2025-03-11].mp4[881.6 MB]",
                "[100-120]113 纯享[4K][HEVC][2025-06-10].mp4[1 GB]",
                "[100-120]113 纯享 [重置版][4K][HEVC][2025-06-13].mp4[1 GB]",
                "[【Z】遮丨天]148 4K.mp4[919.1 MB]",
                "[【Z】遮丨天]148 纯享[4K][HEVC.AAC][2026.02-10].mp4[630 MB]",
                "[【Z】遮丨天]170.mp4[760.6 MB]",
                "[【Z】遮丨天]170 纯享 [4K][HEVC.AAC][2026.07.07].mp4[496.1 MB]",
                "[剧场版]剧场版 背.棺.战.王.腾 .4K.HEVC.10bit.AAC-2026.04.04.mp4[2.8 GB]"
        );

        assertEquals(List.of(
                "001 [628.8MB]",
                "100 纯享 [881.6MB]",
                "113 纯享 [1GB]",
                "113 纯享 重置版 [1GB]",
                "148 [919.1MB]",
                "148 纯享 [630MB]",
                "170 [760.6MB]",
                "170 纯享 [496.1MB]",
                "剧场版 背.棺.战.王.腾 [2.8GB]"
        ), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void summaryDirectionsIgnoreFolderMetadataAndRepeatedSeriesLabelsStayReadable() {
        List<String> names = List.of(
                "[总结篇上 下集]总集篇上 4K .mp4[878.51MB]",
                "[总结篇上 下集]总集篇下 4K .mp4[1.11GB]",
                "[缘起]缘起01 4K.mp4[923.75MB]",
                "[缘起]缘起02 4K.mp4[397.02MB]",
                "[缘起]缘起03 4K.mp4[781.86MB]",
                "斗破苍穹·S05E001·2160p·WEB-DL·H265·10bit·DDP2·0.mp4[395.33MB]"
        );

        assertEquals(List.of(
                "上 [878.51MB]",
                "下 [1.11GB]",
                "缘起 01 [923.75MB]",
                "缘起 02 [397.02MB]",
                "缘起 03 [781.86MB]",
                "S05E001 [395.33MB]"
        ), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void leadingBracketGroupsUseTheEpisodeAfterTheGroupAndNeverBecomeUnbalanced() {
        List<String> names = List.of(
                "[001-100集]01·4K.mkv[1.12GB]",
                "[001-100集]02·4K.mkv[1.14GB]",
                "[仙逆]121 4K.mp4[1.04GB]",
                "[仙逆]122 4K.mp4[989.83MB]",
                "[资料]幕后访谈.mkv[312.00MB]",
                "[资料]制作特辑.mkv[428.00MB]"
        );

        assertEquals(List.of(
                "01 [1.12GB]",
                "02 [1.14GB]",
                "121 [1.04GB]",
                "122 [989.83MB]",
                "[资料]幕后访谈 [312.00MB]",
                "[资料]制作特辑 [428.00MB]"
        ), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void unrecognizedCollectionHeadersFallBackToExistingRules() {
        List<String> names = List.of(
                "[01.2020 Space Documentary] Part Alpha 1080p WEB-DL.mkv",
                "[02.2021 A Movie Archive] Part Beta 720p BluRay.mkv"
        );

        assertEquals(List.of("01", "02"), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void animationCollectionsUsePartAndActualEpisodeInsteadOfArchiveNumber() {
        List<String> names = List.of(
                "[11.2014 牙狼 动画版 第一部 炎之刻印] 牙狼GARO 炎之刻印 01业火 HELL FIRE.mp4 [284.67MB]",
                "[11.2014 牙狼 动画版 第一部 炎之刻印] 牙狼GARO 炎之刻印 12.5SP 飨応（相应） DAYBREAK.mp4 [284.43MB]",
                "[11.2014 牙狼 动画版 第一部 炎之刻印] 牙狼GARO 炎之刻印 19黑翼TEMPEST.mkv [284.29MB]",
                "[14.2015 牙狼 动画版 第二部 红莲之月] [光の影字幕组][牙狼-紅蓮之月][01][陰陽][x264·AAC][720p][TVrip][MKV].mkv [297.34MB]",
                "[20.2017 牙狼 动画版 第三部 死亡线] [光の影字幕組][牙狼-VANISHING LINE-][01][SWORD][TVRip][720p][x264·AAC][Hi10p].mkv [288.99MB]",
                "[20.2017 牙狼 动画版 第三部 死亡线] [JYFanSub][GARO-VANISHING·LINE-][13][GB·CN][X264·AAC][720p](779A85A4).mp4 [183.83MB]",
                "[28.2024 牙狼 第十季 钢之继承者] [XK SPIRITS][GARO-Hagane wo Tsugu Mono-][01][CHS·JAP][1080P][TVrip][MP4].mp4 [697.72MB]"
        );

        assertEquals(List.of(
                "动画第一部 01 业火 [284.67MB]",
                "动画第一部 12.5SP 飨応（相应） [284.43MB]",
                "动画第一部 19 黑翼 [284.29MB]",
                "动画第二部 01 陰陽 [297.34MB]",
                "动画第三部 01 SWORD [288.99MB]",
                "动画第三部 13 [183.83MB]",
                "第十季 01 [697.72MB]"
        ), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void nestedFolderMetadataDoesNotOverrideActualEpisodeOrTitle() {
        List<String> names = List.of(
                "[[0-12][光の影字幕組][720P]] [光の影字幕組][神之牙-JINGA-][00][TVRip][720p][x264·AAC][Hi10p].mp4 [340.49MB]",
                "[[5-12][银梦字幕组][1080P60帧]] 【银梦字幕组】神之牙-JINGA-EP 05[1080P60P].mp4 [415.77MB]",
                "[[DBD-Raws][牙狼：神之牙][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]] [DBD-Raws][牙狼：神之牙][1080P][BDRip][HEVC-10bit][FLAC].mkv [8.05GB]",
                "[[魔星字幕团][牙狼_GARO][中日双语字幕][HDRip][720P][MKV][重制校译版]] [魔星字幕团][牙狼·GARO][01][绘本][中日双语字幕][HDRip][720P][MKV][重制校译版].mkv [600.78MB]"
        );

        assertEquals(List.of(
                "00 [340.49MB]",
                "EP05 [415.77MB]",
                "牙狼：神之牙 [8.05GB]",
                "01 绘本 [600.78MB]"
        ), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void promosExtrasAndSideStoriesKeepMeaningfulLabels() {
        List<String> names = List.of(
                "[PV] [DBD-Raws][牙狼：神之牙][PV1][1080P][BDRip][HEVC-10bit][FLAC].mkv [43.93MB]",
                "[其他] CM.mkv [90.85MB]",
                "[其他] CR集.mkv [724.72MB]",
                "[其他] [RCRAW][牙狼 -月虹ノ旅人-][予告編集][BDrip][1080p][x265·PCM][HEVC-Main10]..mkv [430.56MB]",
                "[其他] [RCRAW][牙狼 -月虹ノ旅人-][Event映像集][DVDrip][480p][x265·AC3][HEVC-Main10]..mkv [79.17MB]",
                "[其他] [RCRAW][牙狼 -月虹ノ旅人-][Visual Commentary][DVDrip][480p][x265·AC3][HEVC-Main10]..mkv [1.32GB]",
                "[其他] [RCRAW][牙狼 -月虹ノ旅人-][ファンタジア国際映画祭][DVDrip][480p][x265·AC3][HEVC-Main10]..mkv [44.99MB]",
                "[番外] [RC個人字幕組][牙狼][番外篇][笑顔][繁中字幕][DVDrip][640x480][DivX AAC][MP4].mp4 [376.15MB]"
        );

        assertEquals(List.of(
                "PV1 [43.93MB]",
                "CM [90.85MB]",
                "CR集 [724.72MB]",
                "予告編集 [430.56MB]",
                "Event映像集 [79.17MB]",
                "Visual Commentary [1.32GB]",
                "ファンタジア国際映画祭 [44.99MB]",
                "番外篇 笑顔 [376.15MB]"
        ), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void collectionMoviesAndStageProductionsKeepSemanticVariants() {
        List<String> names = List.of(
                "[09.2014 牙狼 剧场版 绝狼 漆黑的血液] [光の影字幕组][絕狼ZERO - Black Blood -白之章-][x265·FLAC][1080p][BDrip][HEVC].mkv [2.17GB]",
                "[09.2014 牙狼 剧场版 绝狼 漆黑的血液] [光の影字幕组][絕狼ZERO - Black Blood -黑之章-][x265·FLAC][1080p][BDrip][HEVC].mkv [2.23GB]",
                "[16.2016 牙狼 炎之刻印 剧场版 牙狼-DIVINE FLAME-] [光の影字幕组][剧场版 牙狼 神圣之焰].mkv [1.06GB]",
                "[21.2017 牙狼 舞台剧 觉醒] 【银梦字幕组】舞台剧『牙狼GARO 神之牙』 觉醒中日(1080p).mp4 [6.76GB]",
                "[24.2018 牙狼 红莲之月 剧场版 薄墨樱  牙狼] 【Black·Sun·Sub】GAROUsuzumizakura BD1080P.mp4 [2.43GB]"
        );

        assertEquals(List.of(
                "剧场版 绝狼 白之章 [2.17GB]",
                "剧场版 绝狼 黑之章 [2.23GB]",
                "剧场版 牙狼-DIVINE FLAME [1.06GB]",
                "舞台剧 觉醒 [6.76GB]",
                "剧场版 薄墨樱 牙狼 [2.43GB]"
        ), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void capturedGaroSamplesNeverFallBackToArchiveOrTechnicalNoise() throws Exception {
        List<String> names = loadFixture("/garo-short-display-regression-081-210.txt");
        List<String> displays = EpisodeTitleCompact.compact(names);
        assertEquals(130, names.size());
        assertEquals(names.size(), displays.size());
        for (int i = 0; i < names.size(); i++) {
            String raw = names.get(i);
            String display = displays.get(i);
            boolean recognized = true;
            if (raw.startsWith("[09.")) {
                assertTrue(raw + " -> " + display, display.startsWith("剧场版 绝狼 "));
            } else if (raw.startsWith("[11.")) {
                assertTrue(raw + " -> " + display, display.startsWith("动画第一部 "));
            } else if (raw.startsWith("[14.")) {
                assertTrue(raw + " -> " + display, display.startsWith("动画第二部 "));
            } else if (raw.startsWith("[16.")) {
                assertTrue(raw + " -> " + display, display.startsWith("剧场版 "));
            } else if (raw.startsWith("[20.")) {
                assertTrue(raw + " -> " + display, display.startsWith("动画第三部 "));
            } else if (raw.startsWith("[21.")) {
                assertTrue(raw + " -> " + display, display.startsWith("舞台剧 "));
            } else if (raw.startsWith("[24.")) {
                assertTrue(raw + " -> " + display, display.startsWith("剧场版 "));
            } else if (raw.startsWith("[28.")) {
                assertTrue(raw + " -> " + display, display.startsWith("第十季 "));
            } else if (raw.startsWith("[PV]")) {
                assertTrue(raw + " -> " + display, display.startsWith("PV"));
            } else if (raw.startsWith("[[0-12]")) {
                assertTrue(raw + " -> " + display, display.matches("^[0-9]{2}\\s.*"));
            } else if (raw.startsWith("[[5-12]")) {
                assertTrue(raw + " -> " + display, display.matches("^EP[0-9]{2}\\s.*"));
            } else if (raw.startsWith("[[DBD-Raws]")) {
                assertTrue(raw + " -> " + display, display.startsWith("牙狼：神之牙 "));
            } else if (raw.startsWith("[[魔星字幕团]")) {
                assertTrue(raw + " -> " + display, display.matches("^[0-9]{2}\\s.*"));
            } else if (raw.startsWith("[其他]")) {
                assertFalse(raw + " -> " + display, display.startsWith("其他"));
                assertFalse(raw + " -> " + display, display.matches("^(?:1080|720|480|265|HEVC|BDRip).*"));
            } else if (raw.startsWith("[番外]")) {
                assertTrue(raw + " -> " + display, display.startsWith("番外篇 笑顔 "));
            } else {
                recognized = false;
            }
            assertTrue("Unhandled captured filename: " + raw, recognized);
        }
    }

    @Test
    public void spinOffAndAnniversaryCollectionsUseTheirSemanticSeriesLabels() {
        List<String> names = List.of(
                "[19.2017 牙狼 外传 绝狼 龙之血] [絕狼-DRAGON BLOOD][01][銀狼][x264_AAC][720p][TVrip][Hi10p][MKV].mkv[524.75MB]",
                "[19.2017 牙狼 外传 绝狼 龙之血] [絕狼-DRAGON BLOOD][13][世界][x264_AAC][720p][TVrip][Hi10p][MKV].mkv[515.72MB]",
                "[27.2020 牙狼 十五周年纪念作 对阵之路][XKsub][GARO-VERSUS ROAD-][01][CHS_JAP][720P][TVrip][MP4].mp4[485.85MB]",
                "[27.2020 牙狼 十五周年纪念作 对阵之路][XKsub][GARO-VERSUS ROAD-][6·5][CHS_JAP][720P][TVrip][MP4].mp4[393.39MB]"
        );

        assertEquals(List.of(
                "绝狼外传 01 銀狼 [524.75MB]",
                "绝狼外传 13 世界 [515.72MB]",
                "对阵之路 01 [485.85MB]",
                "对阵之路 6.5 [393.39MB]"
        ), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void bareReleaseFoldersAndStageCategoriesKeepUsefulTitles() {
        List<String> names = List.of(
                "[DBD-Raws][牙狼：神之牙][1080P][BDRip][HEVC-10bit][FLAC].mkv[8.05GB]",
                "[PV][DBD-Raws][牙狼：神之牙][PV1][1080P][BDRip][HEVC-10bit][FLAC].mkv[43.93MB]",
                "[光の影字幕組][神之牙-JINGA-][00][TVRip][720p][x264_AAC][Hi10p].mp4[340.49MB]",
                "[舞台剧][FatCatRAW][神ノ牙-JINGA- 転生][1080P][BDRIP][HEVC-YUV420P10 FLAC] [19B1598D] .mkv[2.81GB]",
                "[舞台剧][FatCatRAW][神ノ牙-JINGA- 転生][SP01][1080P][BDRIP][HEVC-YUV420P10 FLAC] [9A2CE8AA] .mkv[164.87MB]",
                "[舞台剧]舞台剧-『牙狼GARO 神之牙』 觉醒1080p（中文字幕） - 1·『牙狼GARO 神之牙』 觉醒中日1080p(Av59576952,P1).mp4[1.39GB]"
        );

        assertEquals(List.of(
                "牙狼：神之牙 [8.05GB]",
                "PV1 [43.93MB]",
                "00 [340.49MB]",
                "舞台剧 転生 [2.81GB]",
                "舞台剧 SP01 [164.87MB]",
                "舞台剧 觉醒 [1.39GB]"
        ), EpisodeTitleCompact.compact(names));
    }

    @Test
    public void capturedQuarkRangeNeverUsesCollectionSequenceAsEpisode() throws Exception {
        List<String> names = loadFixture("/garo-quark-short-display-regression-161-264.txt");
        List<String> displays = EpisodeTitleCompact.compact(names);
        assertEquals(84, names.size());
        assertEquals(names.size(), displays.size());
        for (int i = 0; i < names.size(); i++) {
            String raw = names.get(i);
            String display = displays.get(i);
            boolean recognized = true;
            if (raw.startsWith("[19.")) {
                assertTrue(raw + " -> " + display, display.startsWith("绝狼外传 "));
            } else if (raw.startsWith("[20.")) {
                assertTrue(raw + " -> " + display, display.startsWith("动画第三部 "));
            } else if (raw.startsWith("[21.")) {
                assertTrue(raw + " -> " + display, display.startsWith("舞台剧 "));
            } else if (raw.startsWith("[22.") || raw.startsWith("[24.") || raw.startsWith("[26.")) {
                assertTrue(raw + " -> " + display, display.startsWith("剧场版 "));
            } else if (raw.startsWith("[27.")) {
                assertTrue(raw + " -> " + display, display.startsWith("对阵之路 "));
            } else if (raw.startsWith("[DBD-Raws]")) {
                assertTrue(raw + " -> " + display, display.startsWith("牙狼：神之牙 "));
            } else if (raw.startsWith("[PV]")) {
                assertTrue(raw + " -> " + display, display.startsWith("PV"));
            } else if (raw.startsWith("[光の影字幕組]")) {
                assertTrue(raw + " -> " + display, display.matches("^[0-9]{2}\\s.*"));
            } else if (raw.startsWith("[其他]")) {
                assertFalse(raw + " -> " + display, display.startsWith("其他"));
                assertFalse(raw + " -> " + display, display.matches("^(?:1080|720|480|265|HEVC|BDRip).*"));
            } else if (raw.startsWith("[舞台剧]")) {
                assertTrue(raw + " -> " + display, display.startsWith("舞台剧 "));
            } else {
                recognized = false;
            }
            assertTrue("Unhandled captured filename: " + raw, recognized);
        }
    }

    @Test
    public void shinkengerQuarkUsesMakuAndSemanticSpecialLabels() throws Exception {
        List<String> names = loadFixture("/shinkenger-quark-short-display-regression.txt");
        List<String> displays = EpisodeTitleCompact.compact(names);
        assertEquals(57, names.size());
        assertEquals(names.size(), displays.size());

        for (String display : displays) {
            assertFalse(display, display.contains("DAY]"));
            assertFalse(display, display.matches("(?i).*(?:BDRIP|WEBRIP|1080P|480P|X264|FLAC).*"));
        }
        for (int i = 0; i < 51; i++) {
            if (names.get(i).contains("仮面ライダーディケイド")) continue;
            assertTrue(names.get(i) + " -> " + displays.get(i), displays.get(i).matches("^(?:第[0-9一二三四五六七八九十百千零〇两兩]+幕|最終幕)(?: DC版)? \\[[0-9.]+GB\\]$"));
        }

        assertEquals("第四十七幕 [1.67GB]", displays.get(0));
        assertEquals("第一幕 DC版 [1.86GB]", displays.get(1));
        assertEquals("第24話 [1.37GB]", displays.get(16));
        assertEquals("第25話 [1.29GB]", displays.get(32));
        assertEquals("最終幕 [1.71GB]", displays.get(50));
        assertEquals(List.of(
                "スペシャルDVD 光侍驚変身 [988.77MB]",
                "VS エピック ON 銀幕 [4.71GB]",
                "VS 銀幕BANG [5.11GB]",
                "特別幕 [5.65GB]",
                "ファイナルライブ2010 [1.86GB]",
                "銀幕版 天下分け目の戦 [1.52GB]"
        ), displays.subList(51, 57));
    }

    @Test
    public void computesDisplayNamesWithoutMutatingEpisodesUntilApplied() {
        Episode first = Episode.create("Show S01E01 Alpha 1080p.mkv", "https://example.test/1");
        Episode second = Episode.create("Show S01E02 Beta 1080p.mkv", "https://example.test/2");
        List<Episode> episodes = List.of(first, second);

        List<String> displayNames = EpisodeTitleCompact.compute(episodes, true);

        assertEquals(List.of("S01E01 Alpha", "S01E02 Beta"), displayNames);
        assertEquals(first.getRawDisplayName(), first.getDisplayName());
        assertEquals(second.getRawDisplayName(), second.getDisplayName());

        EpisodeTitleCompact.apply(episodes, displayNames);

        assertEquals("S01E01 Alpha", first.getDisplayName());
        assertEquals("S01E02 Beta", second.getDisplayName());
    }

    private List<String> loadFixture(String resource) throws Exception {
        InputStream stream = getClass().getResourceAsStream(resource);
        assertNotNull(stream);
        List<String> names = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) if (!line.isEmpty()) names.add(line);
        }
        return names;
    }
}
