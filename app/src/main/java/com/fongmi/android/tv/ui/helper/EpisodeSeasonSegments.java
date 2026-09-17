package com.fongmi.android.tv.ui.helper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把源站扁平集号列表按 TMDB 各季集数切成"季度分段"，供选集界面的季度按钮使用。
 *
 * <p>与 {@link EpisodeSeasonPolicy#mapFlatEpisodeNumber} 的区别：那个方法要求各季集数
 * 精确覆盖源集数，任何偏差就整体放弃（返回 null）。而实际数据经常对不上——例如航海王
 * 源站 1114 集，TMDB 24 季合计约 1120 集——严格策略下后段集会全部落空。
 *
 * <p>这里改为"尽力切分"：能落进某季的归该季，剩下的（超出所有季总数、或所在季集数缺失）
 * 归入一个显式的"其他"分段，让用户仍能点到这些集，而不是悄悄丢掉。
 */
public final class EpisodeSeasonSegments {

    /** "其他"分段的季号；负数不会与真实季号（含特别篇的 0）冲突。 */
    public static final int OTHER_SEASON = -100;

    private EpisodeSeasonSegments() {
    }

    /**
     * @param episodeCount 源站集数
     * @param seasons      TMDB 季号列表（season 0 特别篇会被排除，不参与切分）
     * @param seasonCounts 季号 -> 该季集数
     * @return 有序分段列表；分不出多于一段时返回空列表（调用方据此隐藏季度按钮）
     */
    public static List<Segment> build(int episodeCount, List<Integer> seasons, Map<Integer, Integer> seasonCounts) {
        if (episodeCount <= 0 || seasons == null || seasonCounts == null) return List.of();
        List<Segment> segments = new ArrayList<>();
        int start = 0;
        for (Integer season : EpisodeSeasonPolicy.sliceableSeasons(seasons)) {
            if (start >= episodeCount) break;
            int count = Math.max(0, seasonCounts.getOrDefault(season, 0));
            if (count <= 0) continue;
            int end = Math.min(episodeCount, start + count);
            segments.add(new Segment(season, start, end));
            start = end;
        }
        // 源集数多于各季集数之和：剩余的集不属于任何季，单独成段而不是丢弃
        if (start < episodeCount) segments.add(new Segment(OTHER_SEASON, start, episodeCount));
        return segments.size() > 1 ? segments : List.of();
    }

    /** 返回 position 落在哪一段；找不到返回 -1。 */
    public static int indexOf(List<Segment> segments, int position) {
        if (segments == null) return -1;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (position >= segment.start() && position < segment.end()) return i;
        }
        return -1;
    }

    /** 按季号取出该段的源集子列表；季号不存在时返回空列表。 */
    public static <T> List<T> slice(List<T> items, List<Segment> segments, int seasonNumber) {
        if (items == null || segments == null) return List.of();
        for (Segment segment : segments) {
            if (segment.season() != seasonNumber) continue;
            int end = Math.min(items.size(), segment.end());
            if (segment.start() >= end) return List.of();
            return new ArrayList<>(items.subList(segment.start(), end));
        }
        return List.of();
    }

    /** 供 UI 直接渲染的季号 -> 集数映射，保持分段顺序。 */
    public static Map<Integer, Integer> sizes(List<Segment> segments) {
        Map<Integer, Integer> sizes = new LinkedHashMap<>();
        if (segments == null) return sizes;
        for (Segment segment : segments) sizes.put(segment.season(), segment.end() - segment.start());
        return sizes;
    }

    public static boolean isOther(int seasonNumber) {
        return seasonNumber == OTHER_SEASON;
    }

    /** start 闭、end 开，均为源集列表下标。 */
    public record Segment(int season, int start, int end) {
    }
}
