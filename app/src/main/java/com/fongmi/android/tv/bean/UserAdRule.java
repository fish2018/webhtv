package com.fongmi.android.tv.bean;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 用户自定义去广规则
 * 支持 AI 生成和手动添加
 */
public class UserAdRule {

    public static final String SOURCE_AI = "ai";
    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_INTERFACE_ADS = "interface_ads";
    public static final String SOURCE_INTERFACE_RULE = "interface_rule";

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("source")
    private String source; // "ai" / "manual"

    @SerializedName("hosts")
    private List<String> hosts;  // 广告域名片段（WebView 层拦截 + Sniffer 规则选择器）

    @SerializedName("regex")
    private List<String> regex;  // 广告 URL 正则（命中即广告，映射到 Rule.exclude）

    @SerializedName("exclude")
    private List<String> exclude;  // 正片保护正则（命中即正片，映射到 Rule.regex）

    @SerializedName("createdAt")
    private long createdAt;

    @SerializedName("siteKey")
    private String siteKey; // 可选，标记规则适用站点

    @SerializedName("sourceName")
    private String sourceName;

    @SerializedName("importedCandidateId")
    private String importedCandidateId;

    @SerializedName("enabled")
    private boolean enabled = true; // 默认 true

    public UserAdRule() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
    }

    public static UserAdRule fromAiResult(AdDetectionResult result, String siteKey) {
        UserAdRule rule = new UserAdRule();
        rule.name = "AI生成_" + System.currentTimeMillis();
        rule.source = SOURCE_AI;
        rule.siteKey = siteKey;
        rule.hosts = new ArrayList<>(result.getHostsBlacklist());
        rule.regex = new ArrayList<>(result.getRegexPatterns());
        rule.exclude = new ArrayList<>(result.getExcludePatterns());
        return rule;
    }

    public static UserAdRule createManual(String name) {
        UserAdRule rule = new UserAdRule();
        rule.name = name;
        rule.source = SOURCE_MANUAL;
        rule.hosts = new ArrayList<>();
        rule.regex = new ArrayList<>();
        rule.exclude = new ArrayList<>();
        return rule;
    }

    public static UserAdRule fromImportedCandidate(ImportedAdRuleCandidate candidate) {
        UserAdRule rule = new UserAdRule();
        rule.name = candidate.getName();
        rule.source = "ads".equals(candidate.getSourceType()) ? SOURCE_INTERFACE_ADS : SOURCE_INTERFACE_RULE;
        rule.sourceName = candidate.getSourceConfigName();
        rule.hosts = new ArrayList<>(candidate.getHosts());
        rule.regex = new ArrayList<>(candidate.getRegex());
        rule.exclude = new ArrayList<>(candidate.getExclude());
        rule.importedCandidateId = candidate.getId();
        rule.enabled = false;
        return rule;
    }

    /**
     * 转为 Rule bean，用于合并到 RuleConfig
     * 映射：hosts→Rule.hosts（规则选择器）
     *       regex（广告正则）→Rule.exclude（Sniffer 跳过）
     *       exclude（正片正则）→Rule.regex（Sniffer 采纳）
     */
    public Rule toRule() {
        return Rule.create(
                name,
                new ArrayList<>(getHosts()),      // hosts: 规则适用域
                new ArrayList<>(getExclude()),    // exclude(正片) → Rule.regex
                new ArrayList<>(getRegex())       // regex(广告) → Rule.exclude
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<String> getHosts() {
        return hosts == null ? Collections.emptyList() : hosts;
    }

    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
    }

    public List<String> getRegex() {
        return regex == null ? Collections.emptyList() : regex;
    }

    public void setRegex(List<String> regex) {
        this.regex = regex;
    }

    public List<String> getExclude() {
        return exclude == null ? Collections.emptyList() : exclude;
    }

    public void setExclude(List<String> exclude) {
        this.exclude = exclude;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getSiteKey() {
        return siteKey;
    }

    public void setSiteKey(String siteKey) {
        this.siteKey = siteKey;
    }

    public String getSourceName() {
        return sourceName == null ? "" : sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getImportedCandidateId() {
        return importedCandidateId;
    }

    public void setImportedCandidateId(String importedCandidateId) {
        this.importedCandidateId = importedCandidateId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 列表摘要：来源 · 域名 N · 广告规则 N · 白名单 N
     */
    public String getSummary() {
        String label = SOURCE_AI.equals(source) ? "AI 识别" : source != null && source.startsWith("interface_") ? "接口导入" : "手动添加";
        if (!getSourceName().isEmpty()) label += " · " + getSourceName();
        return label + " · 域名 " + getHosts().size() + " · 广告规则 " + getRegex().size() + " · 白名单 " + getExclude().size();
    }
}
