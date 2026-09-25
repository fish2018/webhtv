package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShortDramaConfig {

    private static final List<String> DEFAULT_ENABLED_RULES = List.of("[短]", "短剧");

    @SerializedName(value = "enabledSites", alternate = {"siteKeys", "sites", "matchSites"})
    private List<String> enabledSites;
    @SerializedName("disabledSites")
    private List<String> disabledSites;
    @SerializedName("configured")
    private Boolean configured;

    public static ShortDramaConfig objectFrom(String json) {
        try {
            ShortDramaConfig config = App.gson().fromJson(json, ShortDramaConfig.class);
            return config == null ? new ShortDramaConfig().sanitize() : config.sanitize();
        } catch (Throwable e) {
            return new ShortDramaConfig().sanitize();
        }
    }

    public ShortDramaConfig sanitize() {
        enabledSites = cleanList(enabledSites);
        disabledSites = cleanList(disabledSites);
        if (configured == null) configured = !enabledSites.isEmpty();
        return this;
    }

    public List<String> getEnabledSites() {
        return enabledSites == null ? new ArrayList<>() : enabledSites;
    }

    public boolean isConfigured() {
        return Boolean.TRUE.equals(configured);
    }

    public List<String> getDisabledSites() {
        return disabledSites == null ? new ArrayList<>() : disabledSites;
    }

    public boolean isSiteEnabled(String key, String name) {
        sanitize();
        if (isBlacklisted(key)) return false;
        List<String> rules = !enabledSites.isEmpty() ? enabledSites : DEFAULT_ENABLED_RULES;
        return matches(rules, key) || matches(rules, name);
    }

    private boolean isBlacklisted(String key) {
        if (TextUtils.isEmpty(key)) return false;
        for (String item : getDisabledSites()) {
            if (key.equalsIgnoreCase(item.trim())) return true;
        }
        return false;
    }

    public String getDisplayRules() {
        List<String> rules = !getEnabledSites().isEmpty() ? getEnabledSites() : DEFAULT_ENABLED_RULES;
        return rules.isEmpty() ? "" : String.join(";", rules);
    }

    public String getDisplayRulesWithNames() {
        List<String> rules = !getEnabledSites().isEmpty() ? getEnabledSites() : DEFAULT_ENABLED_RULES;
        if (rules.isEmpty()) return "";
        List<String> display = new ArrayList<>();
        for (String rule : rules) {
            Site site = findSite(rule);
            display.add(site != null ? getSiteName(site) : rule);
        }
        return String.join(";", display);
    }

    public String getDisplayDisabledSites() {
        if (disabledSites == null || disabledSites.isEmpty()) return "";
        List<String> display = new ArrayList<>();
        for (String key : disabledSites) {
            Site site = findSite(key);
            display.add(site != null ? getSiteName(site) : key);
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
