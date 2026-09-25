package com.fongmi.android.tv.bean;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * AI 广告检测结果
 */
public class AdDetectionResult {

    @SerializedName("confidence")
    private float confidence; // 0.0 ~ 1.0

    @SerializedName("hostsBlacklist")
    private List<String> hostsBlacklist; // 建议加入 hosts 黑名单的域名片段

    @SerializedName("regexPatterns")
    private List<String> regexPatterns; // 建议的 URL 正则匹配（命中即广告）

    @SerializedName("excludePatterns")
    private List<String> excludePatterns; // 排除正则（命中则不是广告）

    @SerializedName("reasoning")
    private String reasoning; // AI 分析理由（展示给用户）

    // 非序列化字段，用于承载错误信息（不发给 AI 也不从 AI 解析）
    private transient String errorMessage;

    public static AdDetectionResult error(String message) {
        AdDetectionResult result = new AdDetectionResult();
        result.errorMessage = message;
        return result;
    }

    public boolean isError() {
        return errorMessage != null;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isEmpty() {
        return getHostsBlacklist().isEmpty() && getRegexPatterns().isEmpty() && getExcludePatterns().isEmpty();
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public List<String> getHostsBlacklist() {
        return hostsBlacklist == null ? Collections.emptyList() : hostsBlacklist;
    }

    public void setHostsBlacklist(List<String> hostsBlacklist) {
        this.hostsBlacklist = hostsBlacklist;
    }

    public List<String> getRegexPatterns() {
        return regexPatterns == null ? Collections.emptyList() : regexPatterns;
    }

    public void setRegexPatterns(List<String> regexPatterns) {
        this.regexPatterns = regexPatterns;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns == null ? Collections.emptyList() : excludePatterns;
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public String getReasoning() {
        return reasoning == null ? "" : reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
}
