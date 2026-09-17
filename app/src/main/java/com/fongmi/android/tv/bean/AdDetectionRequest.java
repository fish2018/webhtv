package com.fongmi.android.tv.bean;

/**
 * AI 广告检测请求上下文
 * 只包含必要的元数据和 URL 特征，不包含完整 URL、query 参数、token 等敏感信息
 */
public class AdDetectionRequest {

    private String siteKey;      // 站点标识
    private String siteName;     // 站点名称
    private String vodName;      // 剧名
    private String flagName;     // 线路名
    private String episodeName;  // 集名
    private String urlHost;      // URL 主机名（如 "cdn.example.com"）
    private String urlPath;      // URL 路径特征（去参数，如 "/ad/video.m3u8"）
    private String urlPattern;   // 简化正则建议（如 ".*\\/ad\\/.*"）
    private M3u8Evidence evidence; // m3u8 切片证据（切片列表、DISCONTINUITY、跨域等）

    public AdDetectionRequest() {
    }

    public M3u8Evidence getEvidence() {
        return evidence;
    }

    public void setEvidence(M3u8Evidence evidence) {
        this.evidence = evidence;
    }

    public String getSiteKey() {
        return siteKey;
    }

    public void setSiteKey(String siteKey) {
        this.siteKey = siteKey;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getFlagName() {
        return flagName;
    }

    public void setFlagName(String flagName) {
        this.flagName = flagName;
    }

    public String getEpisodeName() {
        return episodeName;
    }

    public void setEpisodeName(String episodeName) {
        this.episodeName = episodeName;
    }

    public String getUrlHost() {
        return urlHost;
    }

    public void setUrlHost(String urlHost) {
        this.urlHost = urlHost;
    }

    public String getUrlPath() {
        return urlPath;
    }

    public void setUrlPath(String urlPath) {
        this.urlPath = urlPath;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public void setUrlPattern(String urlPattern) {
        this.urlPattern = urlPattern;
    }
}
