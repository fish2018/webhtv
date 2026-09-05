package com.fongmi.android.tv.bean;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class ImportedAdRuleCandidate {

    public static final String CLASS_AD_BLOCK = "AD_BLOCK";
    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IMPORTED = "IMPORTED";
    public static final String STATUS_IGNORED = "IGNORED";

    @SerializedName("id") private String id;
    @SerializedName("fingerprint") private String fingerprint;
    @SerializedName("name") private String name;
    @SerializedName("hosts") private List<String> hosts;
    @SerializedName("regex") private List<String> regex;
    @SerializedName("exclude") private List<String> exclude;
    @SerializedName("sourceConfigUrlHash") private String sourceConfigUrlHash;
    @SerializedName("sourceConfigName") private String sourceConfigName;
    @SerializedName("sourceRuleName") private String sourceRuleName;
    @SerializedName("sourceType") private String sourceType;
    @SerializedName("classification") private String classification;
    @SerializedName("confidence") private float confidence;
    @SerializedName("riskLevel") private String riskLevel;
    @SerializedName("reasons") private List<String> reasons;
    @SerializedName("status") private String status;
    @SerializedName("firstSeenAt") private long firstSeenAt;
    @SerializedName("lastSeenAt") private long lastSeenAt;
    @SerializedName("seenCount") private int seenCount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name; }
    public List<String> getHosts() { return hosts == null ? Collections.emptyList() : hosts; }
    public void setHosts(List<String> hosts) { this.hosts = hosts; }
    public List<String> getRegex() { return regex == null ? Collections.emptyList() : regex; }
    public void setRegex(List<String> regex) { this.regex = regex; }
    public List<String> getExclude() { return exclude == null ? Collections.emptyList() : exclude; }
    public void setExclude(List<String> exclude) { this.exclude = exclude; }
    public String getSourceConfigUrlHash() { return sourceConfigUrlHash == null ? "" : sourceConfigUrlHash; }
    public void setSourceConfigUrlHash(String value) { sourceConfigUrlHash = value; }
    public String getSourceConfigName() { return sourceConfigName == null ? "" : sourceConfigName; }
    public void setSourceConfigName(String value) { sourceConfigName = value; }
    public String getSourceRuleName() { return sourceRuleName == null ? "" : sourceRuleName; }
    public void setSourceRuleName(String value) { sourceRuleName = value; }
    public String getSourceType() { return sourceType == null ? "" : sourceType; }
    public void setSourceType(String value) { sourceType = value; }
    public String getClassification() { return classification == null ? "" : classification; }
    public void setClassification(String value) { classification = value; }
    public float getConfidence() { return confidence; }
    public void setConfidence(float value) { confidence = value; }
    public String getRiskLevel() { return riskLevel == null ? "" : riskLevel; }
    public void setRiskLevel(String value) { riskLevel = value; }
    public List<String> getReasons() { return reasons == null ? Collections.emptyList() : reasons; }
    public void setReasons(List<String> value) { reasons = value; }
    public String getStatus() { return status == null ? STATUS_PENDING : status; }
    public void setStatus(String value) { status = value; }
    public long getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(long value) { firstSeenAt = value; }
    public long getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(long value) { lastSeenAt = value; }
    public int getSeenCount() { return seenCount; }
    public void setSeenCount(int value) { seenCount = value; }
}
