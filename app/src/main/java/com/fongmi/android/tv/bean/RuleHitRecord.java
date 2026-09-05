package com.fongmi.android.tv.bean;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 规则命中记录（用于详细追踪某条规则的命中情况）
 */
public class RuleHitRecord {

    private String ruleId;
    private String ruleName;
    private String ruleSource;  // "default"/"ai"/"manual"
    private long hitCount;
    private long lastHitAt;
    private List<String> recentBlockedHosts;  // 最近拦截的域名（最多保留 10 条）

    public RuleHitRecord() {
        this.recentBlockedHosts = new ArrayList<>();
    }

    public RuleHitRecord(String ruleId, String ruleName, String ruleSource) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.ruleSource = ruleSource;
        this.recentBlockedHosts = new ArrayList<>();
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleSource() {
        return ruleSource;
    }

    public void setRuleSource(String ruleSource) {
        this.ruleSource = ruleSource;
    }

    public long getHitCount() {
        return hitCount;
    }

    public void setHitCount(long hitCount) {
        this.hitCount = hitCount;
    }

    public long getLastHitAt() {
        return lastHitAt;
    }

    public void setLastHitAt(long lastHitAt) {
        this.lastHitAt = lastHitAt;
    }

    public List<String> getRecentBlockedHosts() {
        return recentBlockedHosts == null ? new ArrayList<>() : recentBlockedHosts;
    }

    public void setRecentBlockedHosts(List<String> recentBlockedHosts) {
        this.recentBlockedHosts = recentBlockedHosts;
    }

    // 增量操作方法

    public void recordHit(String host) {
        this.hitCount++;
        this.lastHitAt = System.currentTimeMillis();
        addRecentHost(host);
    }

    private void addRecentHost(String host) {
        if (host == null || host.isEmpty()) return;
        List<String> list = getRecentBlockedHosts();
        // 避免重复
        list.remove(host);
        // 添加到最前面
        list.add(0, host);
        // 最多保留 10 条
        if (list.size() > 10) {
            list = list.subList(0, 10);
        }
        this.recentBlockedHosts = list;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuleHitRecord that = (RuleHitRecord) o;
        return Objects.equals(ruleId, that.ruleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleId);
    }
}
