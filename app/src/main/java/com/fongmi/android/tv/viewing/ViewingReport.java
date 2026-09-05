package com.fongmi.android.tv.viewing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 观影报告数据模型。本地统计层填充基础字段，AI 分析层补充画像与洞察。
 */
public class ViewingReport {

    private ViewingReportRange range;
    private long startTimestamp;
    private long endTimestamp;

    // 基础统计
    private long totalWatchMinutes;
    private int totalVodCount;
    private int totalEpisodeCount;
    private int recordCount;
    private double averageWatchMinutes;
    private double completionRate;

    // 时间分布
    private Map<TimeSlot, Integer> timeSlotDistribution = new LinkedHashMap<>();
    private TimeSlot topTimeSlot;
    private double weekendRatio;
    private int lateNightCount;

    // 内容偏好
    private List<CountStat> topGenres = new ArrayList<>();
    private List<CountStat> topAreas = new ArrayList<>();
    private double tvRatio;
    private double movieRatio;

    // 人物偏好
    private List<CountStat> topActors = new ArrayList<>();
    private List<CountStat> topDirectors = new ArrayList<>();

    // 成就徽章
    private List<Badge> badges = new ArrayList<>();

    // AI 分析结果
    private String aiSummary;
    private String aiPersona;
    private final List<String> styleTags = new ArrayList<>();
    private final List<String> insights = new ArrayList<>();

    // 元数据
    private long generatedAt;
    private boolean aiAnalyzed;

    public ViewingReportRange getRange() {
        return range;
    }

    public void setRange(ViewingReportRange range) {
        this.range = range;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public long getTotalWatchMinutes() {
        return totalWatchMinutes;
    }

    public void setTotalWatchMinutes(long totalWatchMinutes) {
        this.totalWatchMinutes = totalWatchMinutes;
    }

    public int getTotalVodCount() {
        return totalVodCount;
    }

    public void setTotalVodCount(int totalVodCount) {
        this.totalVodCount = totalVodCount;
    }

    public int getTotalEpisodeCount() {
        return totalEpisodeCount;
    }

    public void setTotalEpisodeCount(int totalEpisodeCount) {
        this.totalEpisodeCount = totalEpisodeCount;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }

    public double getAverageWatchMinutes() {
        return averageWatchMinutes;
    }

    public void setAverageWatchMinutes(double averageWatchMinutes) {
        this.averageWatchMinutes = averageWatchMinutes;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(double completionRate) {
        this.completionRate = completionRate;
    }

    public Map<TimeSlot, Integer> getTimeSlotDistribution() {
        return timeSlotDistribution;
    }

    public void setTimeSlotDistribution(Map<TimeSlot, Integer> timeSlotDistribution) {
        this.timeSlotDistribution = timeSlotDistribution;
    }

    public TimeSlot getTopTimeSlot() {
        return topTimeSlot;
    }

    public void setTopTimeSlot(TimeSlot topTimeSlot) {
        this.topTimeSlot = topTimeSlot;
    }

    public double getWeekendRatio() {
        return weekendRatio;
    }

    public void setWeekendRatio(double weekendRatio) {
        this.weekendRatio = weekendRatio;
    }

    public int getLateNightCount() {
        return lateNightCount;
    }

    public void setLateNightCount(int lateNightCount) {
        this.lateNightCount = lateNightCount;
    }

    public List<CountStat> getTopGenres() {
        return topGenres;
    }

    public void setTopGenres(List<CountStat> topGenres) {
        this.topGenres = topGenres;
    }

    public List<CountStat> getTopAreas() {
        return topAreas;
    }

    public void setTopAreas(List<CountStat> topAreas) {
        this.topAreas = topAreas;
    }

    public double getTvRatio() {
        return tvRatio;
    }

    public void setTvRatio(double tvRatio) {
        this.tvRatio = tvRatio;
    }

    public double getMovieRatio() {
        return movieRatio;
    }

    public void setMovieRatio(double movieRatio) {
        this.movieRatio = movieRatio;
    }

    public List<CountStat> getTopActors() {
        return topActors;
    }

    public void setTopActors(List<CountStat> topActors) {
        this.topActors = topActors;
    }

    public List<CountStat> getTopDirectors() {
        return topDirectors;
    }

    public void setTopDirectors(List<CountStat> topDirectors) {
        this.topDirectors = topDirectors;
    }

    public List<Badge> getBadges() {
        return badges;
    }

    public void setBadges(List<Badge> badges) {
        this.badges = badges;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public String getAiPersona() {
        return aiPersona;
    }

    public void setAiPersona(String aiPersona) {
        this.aiPersona = aiPersona;
    }

    public List<String> getStyleTags() {
        return styleTags;
    }

    public List<String> getInsights() {
        return insights;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(long generatedAt) {
        this.generatedAt = generatedAt;
    }

    public boolean isAiAnalyzed() {
        return aiAnalyzed;
    }

    public void setAiAnalyzed(boolean aiAnalyzed) {
        this.aiAnalyzed = aiAnalyzed;
    }

    public boolean isEmpty() {
        return recordCount == 0;
    }

    /** 观影时段枚举 */
    public enum TimeSlot {
        DAWN("凌晨"),
        MORNING("上午"),
        AFTERNOON("下午"),
        EVENING("晚上"),
        LATE_NIGHT("深夜");

        private final String label;

        TimeSlot(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 通用名称计数（题材、地区、演员、导演） */
    public static class CountStat {
        private final String name;
        private final int count;

        public CountStat(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }

    /** 成就徽章 */
    public static class Badge {
        private final String id;
        private final String name;
        private final String description;
        private final String icon;

        public Badge(String id, String name, String description, String icon) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.icon = icon;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }
    }
}
