package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.github.catvod.utils.Prefers;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 小说源配置（对齐 AudioConfig / ShortDramaConfig）。
 * 站点 key 或名称命中启用规则（默认 [书] / [小说]）时，点击卡片直接进小说阅读器。
 */
public class NovelSourceConfig {

    private static final String KEY = "novel_source_config";
    private static final List<String> DEFAULT_ENABLED_RULES = List.of("[书]", "[小说]");

    @SerializedName(value = "enabledSites", alternate = {"siteKeys", "sites", "matchSites"})
    private List<String> enabledSites;
    @SerializedName("configured")
    private Boolean configured;

    public static NovelSourceConfig objectFrom(String json) {
        try {
            NovelSourceConfig config = App.gson().fromJson(json, NovelSourceConfig.class);
            return config == null ? new NovelSourceConfig().sanitize() : config.sanitize();
        } catch (Throwable e) {
            return new NovelSourceConfig().sanitize();
        }
    }

    public static NovelSourceConfig get() {
        return objectFrom(Prefers.getString(KEY));
    }

    public NovelSourceConfig sanitize() {
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

    public static boolean isSiteEnabled(String key, String name) {
        NovelSourceConfig config = get();
        List<String> rules = config.isConfigured() ? config.getEnabledSites() : DEFAULT_ENABLED_RULES;
        return matches(rules, key) || matches(rules, name);
    }

    /** 按站点 key 解析站点名后判定（标签/站点名命中即视为小说源）。 */
    public static boolean isEnabledByKey(String key) {
        if (TextUtils.isEmpty(key)) return false;
        Site site = VodConfig.get().getSite(key);
        String name = site == null ? "" : site.getName();
        return isSiteEnabled(key, name);
    }

    public String getDisplayRules() {
        List<String> rules = isConfigured() ? getEnabledSites() : DEFAULT_ENABLED_RULES;
        return rules.isEmpty() ? "" : String.join(";", rules);
    }

    public String toJson() {
        configured = true;
        return App.gson().toJson(sanitize());
    }

    public void save() {
        Prefers.put(KEY, toJson());
    }

    public static String defaultRulesText() {
        return String.join(";", DEFAULT_ENABLED_RULES);
    }

    public static List<String> defaultRules() {
        return new ArrayList<>(DEFAULT_ENABLED_RULES);
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

    public static Site findSite(String value) {
        if (TextUtils.isEmpty(value)) return null;
        String target = value.trim();
        for (Site site : VodConfig.get().getSites()) {
            if (site == null || site.isEmpty()) continue;
            if (target.equalsIgnoreCase(site.getKey())) return site;
            if (!TextUtils.isEmpty(site.getName()) && target.equalsIgnoreCase(site.getName())) return site;
        }
        return null;
    }
}
