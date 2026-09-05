package com.fongmi.android.tv.viewing;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.History;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 观影报告本地统计生成器。
 * <p>
 * 纯本地计算,不依赖网络与 AI。即使未配置 AI,也能产出完整的基础报告。
 * AI 深度分析由 {@link ViewingReportAiAnalyzer} 在此结果之上叠加。
 */
public class ViewingReportGenerator {

    // 完播阈值:观看进度 >= 90% 视为完播
    private static final double COMPLETION_THRESHOLD = 0.9;
    // 深夜时段起点(含):23:00-06:00
    private static final int LATE_NIGHT_START = 23;
    private static final int LATE_NIGHT_END = 6;
    // 榜单最多保留条数
    private static final int TOP_GENRE = 5;
    private static final int TOP_ACTOR = 8;
    private static final int TOP_DIRECTOR = 5;
    private static final int TOP_AREA = 5;
    // 元数据分隔符(包括空格,因为源站 actor/director 常见"张三 李四 王五"格式)
    private static final String SPLIT = "[,，/、|｜;；\\s]+";

    public ViewingReport generate(ViewingReportRange range) {
        ViewingReportRange safe = range == null ? ViewingReportRange.ALL : range;
        long[] bounds = safe.getTimeBounds();
        long start = bounds[0];
        long end = bounds[1];
        List<History> records = filterByRange(History.get(), start, end);

        ViewingReport report = new ViewingReport();
        report.setRange(safe);
        report.setStartTimestamp(start);
        report.setEndTimestamp(end);
        report.setGeneratedAt(System.currentTimeMillis());
        report.setRecordCount(records.size());

        computeBasicStats(report, records);
        computeTimePattern(report, records);
        computeContentPreference(report, records);
        computePeoplePreference(report, records);
        computeBadges(report, records);
        return report;
    }

    private List<History> filterByRange(List<History> all, long start, long end) {
        List<History> result = new ArrayList<>();
        if (all == null) return result;
        for (History h : all) {
            if (h == null) continue;
            long t = h.getCreateTime();
            if (t <= 0) continue;
            if (start > 0 && t < start) continue;
            if (end > 0 && t > end) continue;
            result.add(h);
        }
        return result;
    }

    private void computeBasicStats(ViewingReport report, List<History> records) {
        long totalMs = 0;
        int completed = 0;
        int valid = 0;
        Map<String, Boolean> uniqueVods = new HashMap<>();
        for (History h : records) {
            long pos = h.getPosition();
            long dur = h.getDuration();
            if (pos > 0) totalMs += pos;
            if (pos > 0 && dur > 0) {
                valid++;
                if ((double) pos / dur >= COMPLETION_THRESHOLD) completed++;
            }
            String name = normalize(h.getVodName());
            if (!TextUtils.isEmpty(name)) uniqueVods.put(name, true);
        }
        report.setTotalWatchMinutes(totalMs / 60000L);
        report.setTotalVodCount(uniqueVods.size());
        report.setTotalEpisodeCount(records.size());
        report.setCompletionRate(valid == 0 ? 0 : (double) completed / valid);
        report.setAverageWatchMinutes(records.isEmpty() ? 0 : (double) (totalMs / 60000L) / records.size());
    }

    private void computeTimePattern(ViewingReport report, List<History> records) {
        Map<ViewingReport.TimeSlot, Integer> slots = new LinkedHashMap<>();
        for (ViewingReport.TimeSlot slot : ViewingReport.TimeSlot.values()) slots.put(slot, 0);
        int weekend = 0;
        int lateNight = 0;
        Calendar calendar = Calendar.getInstance();
        for (History h : records) {
            if (h.getCreateTime() <= 0) continue;
            calendar.setTimeInMillis(h.getCreateTime());
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int dow = calendar.get(Calendar.DAY_OF_WEEK);
            ViewingReport.TimeSlot slot = slotOf(hour);
            slots.put(slot, slots.get(slot) + 1);
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) weekend++;
            if (hour >= LATE_NIGHT_START || hour < LATE_NIGHT_END) lateNight++;
        }
        report.setTimeSlotDistribution(slots);
        report.setWeekendRatio(records.isEmpty() ? 0 : (double) weekend / records.size());
        report.setLateNightCount(lateNight);
        report.setTopTimeSlot(topSlot(slots));
    }

    private ViewingReport.TimeSlot slotOf(int hour) {
        if (hour >= 0 && hour < 6) return ViewingReport.TimeSlot.DAWN;
        if (hour < 12) return ViewingReport.TimeSlot.MORNING;
        if (hour < 18) return ViewingReport.TimeSlot.AFTERNOON;
        if (hour < 23) return ViewingReport.TimeSlot.EVENING;
        return ViewingReport.TimeSlot.LATE_NIGHT;
    }

    private ViewingReport.TimeSlot topSlot(Map<ViewingReport.TimeSlot, Integer> slots) {
        ViewingReport.TimeSlot top = null;
        int max = -1;
        for (Map.Entry<ViewingReport.TimeSlot, Integer> e : slots.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                top = e.getKey();
            }
        }
        return top;
    }

    private void computeContentPreference(ViewingReport report, List<History> records) {
        Map<String, Integer> genres = new HashMap<>();
        Map<String, Integer> areas = new HashMap<>();
        int tv = 0;
        int movie = 0;
        for (History h : records) {
            for (String g : split(h.getTypeName())) inc(genres, g);
            for (String a : split(h.getArea())) inc(areas, a);
            String type = Objects.toString(h.getTypeName(), "");
            if (isMovie(type)) movie++;
            else tv++;
        }
        report.setTopGenres(topEntries(genres, TOP_GENRE));
        report.setTopAreas(topEntries(areas, TOP_AREA));
        int total = tv + movie;
        report.setTvRatio(total == 0 ? 0 : (double) tv / total);
        report.setMovieRatio(total == 0 ? 0 : (double) movie / total);
    }

    private void computePeoplePreference(ViewingReport report, List<History> records) {
        Map<String, Integer> actors = new HashMap<>();
        Map<String, Integer> directors = new HashMap<>();
        for (History h : records) {
            for (String a : split(h.getActor())) inc(actors, a);
            for (String d : split(h.getDirector())) inc(directors, d);
        }
        report.setTopActors(topEntries(actors, TOP_ACTOR));
        report.setTopDirectors(topEntries(directors, TOP_DIRECTOR));
    }

    private void computeBadges(ViewingReport report, List<History> records) {
        List<ViewingReport.Badge> badges = new ArrayList<>();
        if (report.getLateNightCount() >= 10) {
            badges.add(new ViewingReport.Badge("night_owl", "深夜观影者", "深夜观影 " + report.getLateNightCount() + " 次", "🌙"));
        }
        if (report.getTotalVodCount() >= 30) {
            badges.add(new ViewingReport.Badge("drama_king", "追剧达人", "观看了 " + report.getTotalVodCount() + " 部作品", "📺"));
        }
        if (report.getTopAreas() != null && report.getTopAreas().size() >= 4) {
            badges.add(new ViewingReport.Badge("explorer", "跨文化探索者", "观看了 " + report.getTopAreas().size() + " 个地区的作品", "🌏"));
        }
        if (report.getCompletionRate() >= 0.7 && report.getTotalEpisodeCount() >= 20) {
            badges.add(new ViewingReport.Badge("finisher", "完播王者", "完播率 " + Math.round(report.getCompletionRate() * 100) + "%", "🏆"));
        }
        report.setBadges(badges);
    }

    // ---- helpers ----

    private List<ViewingReport.CountStat> topEntries(Map<String, Integer> map, int limit) {
        List<ViewingReport.CountStat> list = new ArrayList<>();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (TextUtils.isEmpty(e.getKey())) continue;
            list.add(new ViewingReport.CountStat(e.getKey(), e.getValue()));
        }
        list.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    private void inc(Map<String, Integer> map, String key) {
        String value = key == null ? "" : key.trim();
        if (TextUtils.isEmpty(value)) return;
        map.put(value, map.getOrDefault(value, 0) + 1);
    }

    private List<String> split(String text) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return result;
        for (String part : text.split(SPLIT)) {
            String value = part == null ? "" : part.trim();
            if (!TextUtils.isEmpty(value)) result.add(value);
        }
        return result;
    }

    private boolean isMovie(String type) {
        String value = type.toLowerCase(Locale.ROOT);
        return value.contains("电影") || value.contains("movie") || value.contains("剧场版");
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }
}
