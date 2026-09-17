package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.playback.PlaybackRuntime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 广告拦截统计数据
 */
public class AdBlockStats {

    private long totalBlocked;              // 总拦截次数
    private long aiRuleFeedbackCount;       // AI 反馈次数
    private long aiAnalysisSuccess;         // AI 分析成功次数
    private long aiAnalysisFailed;          // AI 分析失败次数
    private Map<String, Long> siteBlocked;  // 按站点统计拦截次数
    private Map<String, Long> ruleCounts;   // 按规则 ID 统计匹配次数
    private Map<String, Long> pipelineCounts; // 按播放链路统计拦截次数
    private List<AdBlockLog> blockLogs;       // 每条广告切片的拦截明细
    private long lastResetAt;               // 上次重置时间

    public AdBlockStats() {
        this.siteBlocked = new HashMap<>();
        this.ruleCounts = new HashMap<>();
        this.pipelineCounts = new HashMap<>();
        this.blockLogs = new ArrayList<>();
        this.lastResetAt = System.currentTimeMillis();
    }

    public long getTotalBlocked() {
        return totalBlocked;
    }

    public void setTotalBlocked(long totalBlocked) {
        this.totalBlocked = totalBlocked;
    }

    public long getAiRuleFeedbackCount() {
        return aiRuleFeedbackCount;
    }

    public void setAiRuleFeedbackCount(long aiRuleFeedbackCount) {
        this.aiRuleFeedbackCount = aiRuleFeedbackCount;
    }

    public long getAiAnalysisSuccess() {
        return aiAnalysisSuccess;
    }

    public void setAiAnalysisSuccess(long aiAnalysisSuccess) {
        this.aiAnalysisSuccess = aiAnalysisSuccess;
    }

    public long getAiAnalysisFailed() {
        return aiAnalysisFailed;
    }

    public void setAiAnalysisFailed(long aiAnalysisFailed) {
        this.aiAnalysisFailed = aiAnalysisFailed;
    }

    public Map<String, Long> getSiteBlocked() {
        return siteBlocked == null ? new HashMap<>() : siteBlocked;
    }

    public void setSiteBlocked(Map<String, Long> siteBlocked) {
        this.siteBlocked = siteBlocked;
    }

    public Map<String, Long> getRuleCounts() {
        return ruleCounts == null ? new HashMap<>() : ruleCounts;
    }

    public void setRuleCounts(Map<String, Long> ruleCounts) {
        this.ruleCounts = ruleCounts;
    }

    public Map<String, Long> getPipelineCounts() {
        return pipelineCounts == null ? new HashMap<>() : pipelineCounts;
    }

    public void setPipelineCounts(Map<String, Long> pipelineCounts) {
        this.pipelineCounts = pipelineCounts;
    }

    public List<AdBlockLog> getBlockLogs() {
        if (blockLogs == null) blockLogs = new ArrayList<>();
        return blockLogs;
    }

    public void setBlockLogs(List<AdBlockLog> blockLogs) {
        this.blockLogs = blockLogs;
    }

    public void recordBlockLog(long blockedAt, String sourceName, String pipelineName,
                               String adDomain, String ruleId, double segmentDurationSeconds) {
        recordBlockLog(blockedAt, sourceName, pipelineName, adDomain, ruleId, 0, segmentDurationSeconds);
    }

    public void recordBlockLog(long blockedAt, String sourceName, String pipelineName,
                               String adDomain, String ruleId, double segmentStartSeconds, double segmentDurationSeconds) {
        recordBlockLog(blockedAt, sourceName, sourceName, adDomain, pipelineName, adDomain,
                ruleId, segmentStartSeconds, segmentDurationSeconds);
    }

    public void recordBlockLog(long blockedAt, String siteKey, String siteName, String siteDomain,
                               String pipelineName, String adDomain, String ruleId,
                               double segmentStartSeconds, double segmentDurationSeconds) {
        List<AdBlockLog> logs = getBlockLogs();
        AdBlockLog log = new AdBlockLog(blockedAt, siteKey, siteName, siteDomain, pipelineName,
                adDomain, ruleId, segmentStartSeconds, segmentDurationSeconds);
        PlaybackRuntime.PlaybackIdentity playback = PlaybackRuntime.currentPlaybackIdentity();
        log.setPlaybackContext(playback.vodName(), playback.lineName(), playback.episodeName());
        logs.add(0, log);
        while (logs.size() > 1000) logs.remove(logs.size() - 1);
    }

    public void recordBlockLogs(long blockedAt, String sourceName, String pipelineName,
                                String adDomain, Map<String, Long> ruleCounts,
                                double totalDurationSeconds) {
        recordBlockLogs(blockedAt, sourceName, sourceName, adDomain, pipelineName, adDomain,
                ruleCounts, totalDurationSeconds);
    }

    public void recordBlockLogs(long blockedAt, String siteKey, String siteName, String siteDomain,
                                String pipelineName, String adDomain, Map<String, Long> ruleCounts,
                                double totalDurationSeconds) {
        if (ruleCounts == null || ruleCounts.isEmpty()) return;
        long totalCount = 0;
        for (Long count : ruleCounts.values()) {
            if (count != null && count > 0) totalCount += count;
        }
        if (totalCount <= 0) return;
        double durationPerSegment = Math.max(0, totalDurationSeconds) / totalCount;
        for (Map.Entry<String, Long> entry : ruleCounts.entrySet()) {
            long count = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            for (long index = 0; index < count; index++) {
                recordBlockLog(blockedAt, siteKey, siteName, siteDomain, pipelineName, adDomain,
                        entry.getKey(), 0, durationPerSegment);
            }
        }
    }

    public List<AdBlockLog> getBlockLogsBySource(String sourceName) {
        return filterLogs(sourceName, 0);
    }

    /** Supports stable site-key aggregates while remaining compatible with older display-name keys. */
    public List<AdBlockLog> getBlockLogsBySite(String siteKeyOrName) {
        List<AdBlockLog> result = new ArrayList<>();
        for (AdBlockLog log : getBlockLogs()) {
            if (Objects.equals(siteKeyOrName, log.getSiteKey())
                    || Objects.equals(siteKeyOrName, log.getSourceName())) result.add(log);
        }
        return result;
    }

    public List<AdBlockLog> getBlockLogsByRule(String ruleId) {
        return filterLogs(ruleId, 1);
    }

    public List<AdBlockLog> getBlockLogsByPipeline(String pipelineName) {
        return filterLogs(pipelineName, 2);
    }

    public List<String> getSourceNamesWithBlocks() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (AdBlockLog log : getBlockLogs()) {
            if (log.getSourceName() != null && !log.getSourceName().isEmpty()) names.add(log.getSourceName());
        }
        return new ArrayList<>(names);
    }

    private List<AdBlockLog> filterLogs(String value, int dimension) {
        List<AdBlockLog> result = new ArrayList<>();
        for (AdBlockLog log : getBlockLogs()) {
            String candidate = dimension == 0 ? log.getSourceName()
                    : dimension == 1 ? log.getRuleId() : log.getPipelineName();
            if (Objects.equals(value, candidate)) result.add(log);
        }
        return result;
    }

    public long getLastResetAt() {
        return lastResetAt;
    }

    public void setLastResetAt(long lastResetAt) {
        this.lastResetAt = lastResetAt;
    }

    // 增量操作方法

    public void incrementTotalBlocked() {
        this.totalBlocked++;
    }

    public void incrementSiteBlocked(String siteKey) {
        if (siteKey == null || siteKey.isEmpty()) return;
        Map<String, Long> map = getSiteBlocked();
        map.put(siteKey, toLong(map.get(siteKey)) + 1);
        this.siteBlocked = map;
    }

    public void incrementRuleCount(String ruleId) {
        incrementBlocks(null, null, ruleId, 1, false);
    }

    public void incrementBlocks(String siteKey, String pipeline, String ruleId, long count) {
        incrementBlocks(siteKey, pipeline, ruleId, count, true);
    }

    private void incrementBlocks(String siteKey, String pipeline, String ruleId, long count, boolean includeTotal) {
        if (count <= 0) return;
        if (includeTotal) {
            totalBlocked += count;
            merge(siteBlocked = getSiteBlocked(), siteKey, count);
            merge(pipelineCounts = getPipelineCounts(), pipeline, count);
        }
        merge(ruleCounts = getRuleCounts(), ruleId, count);
    }

    private static void merge(Map<String, Long> target, String key, long count) {
        if (key == null || key.isEmpty() || count <= 0) return;
        target.put(key, toLong(target.get(key)) + count);
    }

    /**
     * 安全地将 Map 中的值转为 long。
     * Gson 在泛型签名缺失时可能把数字反序列化为 Double，直接拆箱会 ClassCastException，此处兼容处理。
     */
    private static long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        return 0L;
    }

    public void incrementAiFeedback() {
        this.aiRuleFeedbackCount++;
    }

    public void incrementAiSuccess() {
        this.aiAnalysisSuccess++;
    }

    public void incrementAiFailed() {
        this.aiAnalysisFailed++;
    }

    public void reset() {
        this.totalBlocked = 0;
        this.aiRuleFeedbackCount = 0;
        this.aiAnalysisSuccess = 0;
        this.aiAnalysisFailed = 0;
        this.siteBlocked = new HashMap<>();
        this.ruleCounts = new HashMap<>();
        this.pipelineCounts = new HashMap<>();
        this.blockLogs = new ArrayList<>();
        this.lastResetAt = System.currentTimeMillis();
    }

    // 统计查询方法

    /**
     * 获取指定站点的拦截次数
     */
    public long getSiteBlockedCount(String siteKey) {
        if (siteKey == null || siteKey.isEmpty()) return 0;
        return toLong(getSiteBlocked().get(siteKey));
    }

    public long getRuleBlockedCount(String ruleId) {
        if (ruleId == null || ruleId.isEmpty()) return 0;
        return toLong(getRuleCounts().get(ruleId));
    }

    public long getPipelineBlockedCount(String pipeline) {
        if (pipeline == null || pipeline.isEmpty()) return 0;
        return toLong(getPipelineCounts().get(pipeline));
    }

    public int getAiAnalysisTotal() {
        return (int) (aiAnalysisSuccess + aiAnalysisFailed);
    }

    public float getAiSuccessRate() {
        int total = getAiAnalysisTotal();
        return total == 0 ? 0 : (float) aiAnalysisSuccess / total * 100;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdBlockStats that = (AdBlockStats) o;
        return totalBlocked == that.totalBlocked &&
                aiRuleFeedbackCount == that.aiRuleFeedbackCount &&
                aiAnalysisSuccess == that.aiAnalysisSuccess &&
                aiAnalysisFailed == that.aiAnalysisFailed &&
                lastResetAt == that.lastResetAt &&
                Objects.equals(siteBlocked, that.siteBlocked) &&
                Objects.equals(ruleCounts, that.ruleCounts) &&
                Objects.equals(pipelineCounts, that.pipelineCounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalBlocked, aiRuleFeedbackCount, aiAnalysisSuccess, aiAnalysisFailed, siteBlocked, ruleCounts, pipelineCounts, lastResetAt);
    }
}
