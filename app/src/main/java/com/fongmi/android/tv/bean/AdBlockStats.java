package com.fongmi.android.tv.bean;

import java.util.HashMap;
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
    private long lastResetAt;               // 上次重置时间

    public AdBlockStats() {
        this.siteBlocked = new HashMap<>();
        this.ruleCounts = new HashMap<>();
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
        if (ruleId == null || ruleId.isEmpty()) return;
        Map<String, Long> map = getRuleCounts();
        map.put(ruleId, toLong(map.get(ruleId)) + 1);
        this.ruleCounts = map;
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
                Objects.equals(ruleCounts, that.ruleCounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalBlocked, aiRuleFeedbackCount, aiAnalysisSuccess, aiAnalysisFailed, siteBlocked, ruleCounts, lastResetAt);
    }
}
