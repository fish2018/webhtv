package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 锁住"源站不分季的长番（如航海王 1000+ 集）在播放页也能跨季匹配 TMDB"这条链路。
 * 曾经的症状：第 62 集之后全部匹配不到 TMDB，选集里显示成无剧照的降级卡片。
 */
public class CrossSeasonEpisodeMappingTest {

    private static final List<Integer> SEASONS = List.of(1, 2);
    private static final Map<Integer, Integer> COUNTS = Map.of(1, 61, 2, 16);

    private static Episode sourceEpisode(int number) {
        // 集号由 Episode 构造时从名字解析，测试里用"第N集"让它得到期望的扁平集号
        return Episode.create("第" + number + "集", "https://example.test/" + number);
    }

    private static TmdbEpisode tmdbEpisode(int season, int number) {
        return new TmdbEpisode(number, "题目" + number, "", "", "", 0, 0, 0, season);
    }

    @Test
    public void flatEpisodeBeyondFirstSeasonMapsIntoLaterSeason() {
        EpisodeSeasonPolicy.SeasonEpisode mapped =
                EpisodeSeasonPolicy.mapFlatEpisodeNumber(62, SEASONS, COUNTS);

        assertNotNull("扁平第 62 集必须能映射到第二季，否则播放页无法匹配 TMDB", mapped);
        assertEquals(2, mapped.seasonNumber());
        assertEquals(1, mapped.episodeNumber());
    }

    /**
     * 挂载跨季数据必须用 setMappedTmdbEpisode。setTmdbEpisode 会把 tmdbEpisodeMapped
     * 置 false，渲染时被 matcher 否决 —— 数据挂上了却显示不出来，且难以定位。
     */
    @Test
    public void mappedSetterKeepsCrossSeasonDataVisibleToMatcher() {
        TmdbEpisode tmdb = tmdbEpisode(2, 1);

        Episode viaMapped = sourceEpisode(62);
        viaMapped.setMappedTmdbEpisode(tmdb);
        assertTrue(viaMapped.isTmdbEpisodeMapped());
        assertTrue("跨季映射的集必须能通过两参 matcher",
                TmdbEpisodeMatcher.shouldApply(viaMapped, tmdb));

        Episode viaPlain = sourceEpisode(62);
        viaPlain.setTmdbEpisode(tmdb);
        assertFalse(viaPlain.isTmdbEpisodeMapped());
        assertFalse("setTmdbEpisode 存跨季数据会被 matcher 否决，这正是要避免的写法",
                TmdbEpisodeMatcher.shouldApply(viaPlain, tmdb));
    }

    /**
     * 渲染侧的分岔：holder 手里只有扁平集号（62），而 TMDB 是本季集号（1）。
     * 三参 matcher 要求两者相等，对跨季映射的集必然否决；两参版只校验身份。
     */
    @Test
    public void threeArgMatcherRejectsCrossSeasonWhileTwoArgAccepts() {
        TmdbEpisode tmdb = tmdbEpisode(2, 1);
        Episode episode = sourceEpisode(62);
        episode.setMappedTmdbEpisode(tmdb);

        assertFalse("传扁平集号给三参版会否决跨季映射，holder 必须改走两参版",
                TmdbEpisodeMatcher.shouldApply(episode, tmdb, 62));
        assertTrue(TmdbEpisodeMatcher.shouldApply(episode, tmdb));
        assertTrue(TmdbEpisodeMatcher.shouldApply(episode, tmdb, 1));
    }

    @Test
    public void sameSeasonEpisodeStillMatchesWithoutMappedFlag() {
        TmdbEpisode tmdb = tmdbEpisode(1, 5);
        Episode episode = sourceEpisode(5);
        episode.setTmdbEpisode(tmdb);

        assertFalse(episode.isTmdbEpisodeMapped());
        assertTrue("同季集号一致的常规情况不受跨季改动影响",
                TmdbEpisodeMatcher.shouldApply(episode, tmdb, 5));
    }

    /**
     * intent 带来的 season 只是进场选中季，不能锁死季度解析。resolveSeason 里必须在
     * Source.REQUEST 时重算一次不带 requestSeason 的解析，MULTI_SLICE 就改用切片结果，
     * 否则分集富化走单季路径，扁平集号超出该季集数后全部匹配不到。
     */
    @Test
    public void requestSeasonMustNotSuppressMultiSliceResolution() throws IOException {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv",
                "ui", "helper", "TmdbUIAdapter.java")));

        assertTrue("Source.REQUEST 时必须重算不带 requestSeason 的解析",
                source.contains("TmdbSeasonResolver.Resolution unrequested = TmdbSeasonResolver.resolve(")
                        && source.contains("if (unrequested.getStatus() == TmdbSeasonResolver.Status.MULTI_SLICE)")
                        && source.contains("seasonResolution = unrequested;"));
        assertTrue("分段富化必须保住 mapped 标记",
                source.contains("episode.setMappedTmdbEpisode(tmdbEpisode);"));
    }

    /**
     * 按位置贴元数据的前提是该季集号无跳号。indexEpisodesByNumber 会滤掉集号 <= 0 并对重号去重，
     * 一旦 TMDB 侧中间缺集，排序后的列表会让缺口之后每一集都挂上下一集的标题和剧照。
     * 这条 fallback 路径没有 TmdbEpisodeMatcher 校验兜底，只能靠连续性判据整季放弃。
     */
    @Test
    public void positionalFallbackRejectsSeasonsWithEpisodeNumberGaps() {
        // 缺了 198：按位置贴会让第 3 个源集拿到 199 的数据，之后全部前移一位
        assertFalse(TmdbUIAdapter.isContiguousFrom(List.of(196, 197, 199, 200), 4));
        assertFalse(TmdbUIAdapter.isContiguousFrom(List.of(1, 2, 4), 3));
    }

    /**
     * 末季的分段会被 EpisodeSeasonSegments.build 裁到源集数（航海王 S22 分段宽 26，
     * 而该季 TMDB 有 67 集）。这种前缀对齐是安全的，不能因"集数不等于分段宽度"就整季跳过，
     * 否则末季的集永远拿不到刮削数据。
     */
    @Test
    public void positionalFallbackAcceptsClampedLastSeasonPrefix() {
        List<Integer> season22 = new java.util.ArrayList<>();
        for (int number = 1006; number <= 1072; number++) season22.add(number);

        assertEquals(67, season22.size());
        assertTrue("分段被裁短时，取连续前缀是安全的", TmdbUIAdapter.isContiguousFrom(season22, 26));
        assertTrue(TmdbUIAdapter.isContiguousFrom(season22, 67));
        assertFalse("宽度超过实际集数时不能贴", TmdbUIAdapter.isContiguousFrom(season22, 68));
    }

    @Test
    public void positionalFallbackRejectsEmptyOrZeroWidth() {
        assertFalse(TmdbUIAdapter.isContiguousFrom(List.of(), 1));
        assertFalse(TmdbUIAdapter.isContiguousFrom(List.of(1, 2, 3), 0));
    }

    /** 集号大于 Integer 缓存上限（127）时必须按数值比较，不能比对象引用。 */
    @Test
    public void contiguityCheckComparesNumbersNotReferences() {
        assertTrue(TmdbUIAdapter.isContiguousFrom(List.of(500, 501, 502, 503), 4));
        assertFalse(TmdbUIAdapter.isContiguousFrom(List.of(500, 501, 503), 3));
    }

    /**
     * 拉取范围必须按源集数收窄。改动初期拉全部可切分季，航海王 23 季串行阻塞 HTTP
     * 首次冷缓存要 7-18 秒，期间选集界面无标题无图。
     */
    @Test
    public void seasonFetchIsNarrowedToWhatSourceEpisodesCover() throws IOException {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv",
                "ui", "helper", "TmdbUIAdapter.java")));

        assertTrue("必须按源集数累加收窄拉取范围，而不是无条件拉全部可切分季",
                source.contains("private List<Integer> seasonsCoveringSource(")
                        && source.contains("if (covered >= sourceEpisodeCount) break;")
                        && source.contains("List<Integer> seasons = seasonsCoveringSource("));
        assertTrue("已持久化分段涉及的季必须补进拉取集合，否则分段富化拿不到数据",
                source.contains("for (TmdbSeasonSegment segment : persistedSegments) needed.add(segment.getSeasonNumber());"));
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        return Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", "main", "java");
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
