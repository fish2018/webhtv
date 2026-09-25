package com.fongmi.android.tv.bean;

/** 单条广告切片拦截日志。 */
public class AdBlockLog {

    private long blockedAt;
    private String sourceName; // legacy JSON field; new records use siteKey/siteName
    private String siteKey;
    private String siteName;
    private String siteDomain;
    private String pipelineName;
    private String adDomain;
    private String ruleId;
    private String vodName;
    private String lineName;
    private String episodeName;
    private double segmentStartSeconds;
    private double segmentDurationSeconds;

    public AdBlockLog() {
    }

    public AdBlockLog(long blockedAt, String sourceName, String pipelineName, String adDomain,
                      String ruleId, double segmentStartSeconds, double segmentDurationSeconds) {
        this(blockedAt, sourceName, sourceName, adDomain, pipelineName, adDomain, ruleId,
                segmentStartSeconds, segmentDurationSeconds);
    }

    public AdBlockLog(long blockedAt, String siteKey, String siteName, String siteDomain,
                      String pipelineName, String adDomain, String ruleId,
                      double segmentStartSeconds, double segmentDurationSeconds) {
        this.blockedAt = blockedAt;
        this.sourceName = siteName;
        this.siteKey = siteKey;
        this.siteName = siteName;
        this.siteDomain = siteDomain;
        this.pipelineName = pipelineName;
        this.adDomain = adDomain;
        this.ruleId = ruleId;
        this.segmentStartSeconds = segmentStartSeconds;
        this.segmentDurationSeconds = segmentDurationSeconds;
    }

    public long getBlockedAt() {
        return blockedAt;
    }

    public String getSiteKey() {
        return empty(siteKey) ? "" : siteKey;
    }

    public String getSiteName() {
        if (!empty(siteName)) return siteName;
        if (!empty(sourceName) && !sourceName.equals(getSiteDomain())) return sourceName;
        return "";
    }

    public String getSiteDomain() {
        return empty(siteDomain) ? adDomain : siteDomain;
    }

    /** Backward-compatible grouping accessor for statistics saved before site identity was split. */
    public String getSourceName() {
        if (!empty(getSiteName())) return getSiteName();
        if (!empty(sourceName)) return sourceName;
        return getSiteDomain();
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public String getAdDomain() {
        return adDomain;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getVodName() {
        return empty(vodName) ? "" : vodName;
    }

    public String getLineName() {
        return empty(lineName) ? "" : lineName;
    }

    public String getEpisodeName() {
        return empty(episodeName) ? "" : episodeName;
    }

    public AdBlockLog setPlaybackContext(String vodName, String lineName, String episodeName) {
        this.vodName = vodName;
        this.lineName = lineName;
        this.episodeName = episodeName;
        return this;
    }

    public double getSegmentStartSeconds() {
        return segmentStartSeconds;
    }

    public double getSegmentEndSeconds() {
        return segmentStartSeconds + segmentDurationSeconds;
    }

    public boolean hasSegmentTiming() {
        return segmentDurationSeconds > 0;
    }

    public double getSegmentDurationSeconds() {
        return segmentDurationSeconds;
    }

    private static boolean empty(String value) {
        return value == null || value.isBlank();
    }
}
