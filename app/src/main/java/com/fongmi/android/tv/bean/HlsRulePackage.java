package com.fongmi.android.tv.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class HlsRulePackage {

    @SerializedName("schemaVersion")
    private int schemaVersion;
    @SerializedName("packageId")
    private String packageId;
    @SerializedName("version")
    private int version;
    @SerializedName("rules")
    private List<HlsAdRule> rules;

    public static HlsRulePackage parse(String json) {
        try {
            HlsRulePackage value = new Gson().fromJson(json, HlsRulePackage.class);
            return value == null || value.schemaVersion != 2 ? new HlsRulePackage() : value;
        } catch (Throwable e) {
            return new HlsRulePackage();
        }
    }

    public int getSchemaVersion() { return schemaVersion; }
    public String getPackageId() { return packageId == null ? "" : packageId; }
    public int getVersion() { return version; }
    public List<HlsAdRule> getRules() { return rules == null ? Collections.emptyList() : rules; }
}
