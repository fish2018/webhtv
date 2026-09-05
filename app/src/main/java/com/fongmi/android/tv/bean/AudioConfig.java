package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AudioConfig {

    private static final List<String> DEFAULT_ENABLED_RULES = List.of("[音]", "[听]");

    @SerializedName(value = "enabledSites", alternate = {"siteKeys", "sites", "matchSites"})
    private List<String> enabledSites;
    @SerializedName("configured")
    private Boolean configured;

    public static AudioConfig objectFrom(String json) {
        try {
            AudioConfig config = App.gson().fromJson(json, AudioConfig.class);
            return config == null ? new AudioConfig().sanitize() : config.sanitize();
        } catch (Throwable e) {
            return new AudioConfig().sanitize();
        }
    }

    public AudioConfig sanitize() {
        enabledSites = cleanList(enabledSites);
        if (configured == null) configured = !enabledSites.isEmpty();
        return this;
    }

    public List<String> getEnabledSites() {
        return enabledSites == null ? new ArrayList<>() : enabledSites;
    }

    public boolean isConfigured() {
        return Boolean.TRUE.equals(configured);
    }

    public boolean isSiteEnabled(String key, String name) {
        sanitize();
        List<String> rules = isConfigured() ? getEnabledSites() : DEFAULT_ENABLED_RULES;
        return matches(rules, key) || matches(rules, name);
    }

    public String getDisplayRules() {
        List<String> rules = isConfigured() ? getEnabledSites() : DEFAULT_ENABLED_RULES;
        return rules.isEmpty() ? "" : String.join(";", rules);
    }

    public String getDisplayRulesWithNames() {
        List<String> rules = isConfigured() ? getEnabledSites() : DEFAULT_ENABLED_RULES;
        if (rules.isEmpty()) return "";
        List<String> display = new ArrayList<>();
        for (String rule : rules) {
            Site site = findSite(rule);
            display.add(site != null ? getSiteName(site) : rule);
        }
        return String.join(";", display);
    }

    public String toJson() {
        configured = true;
        return App.gson().toJson(sanitize());
    }

    public static String defaultRulesText() {
        return String.join(";", DEFAULT_ENABLED_RULES);
    }

    private static List<String> cleanList(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            String item = value.trim();
            if (!item.isEmpty() && !result.contains(item)) result.add(item);
        }
        return result;
    }

    private static boolean matches(List<String> rules, String value) {
        if (rules == null || TextUtils.isEmpty(value)) return false;
        String target = value.toLowerCase(Locale.ROOT);
        for (String rule : rules) {
            if (TextUtils.isEmpty(rule)) continue;
            if (target.contains(rule.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static Site findSite(String value) {
        if (TextUtils.isEmpty(value)) return null;
        String target = value.trim();
        for (Site site : VodConfig.get().getSites()) {
            if (site == null || site.isEmpty()) continue;
            if (target.equalsIgnoreCase(site.getKey())) return site;
        }
        return null;
    }

    private static String getSiteName(Site site) {
        return site.getDisplayName();
    }
}
