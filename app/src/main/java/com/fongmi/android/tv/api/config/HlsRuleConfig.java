package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.bean.HlsRulePackage;
import com.fongmi.android.tv.bean.HlsRuleState;
import com.fongmi.android.tv.utils.HlsManifestCleaner;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HlsRuleConfig {

    private static final String TAG = "hls-rule";
    private static final Gson DETAIL_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static List<HlsManifestCleaner.Rule> rules = List.of();
    private static List<Entry> entries = List.of();
    private static boolean dirty = true;

    private HlsRuleConfig() {}

    public static synchronized List<HlsManifestCleaner.Rule> getRules() {
        if (dirty) reload();
        return rules;
    }

    public static synchronized List<Entry> getEntries() {
        if (dirty) reload();
        return entries;
    }

    public static synchronized void invalidate() {
        dirty = true;
    }

    private static void reload() {
        if (!dirty) return;
        List<HlsManifestCleaner.Rule> compiled = new ArrayList<>();
        List<Entry> summaries = new ArrayList<>();
        HlsRulePackage builtin = BuiltinHlsRuleLoader.getPackage();
        Map<String, Boolean> overrides = HlsRuleStateStore.load();
        compileBuiltin(builtin, overrides, compiled, summaries);
        compileExternal("vod", VodConfig.get().getConfig().getUrl(), VodConfig.get().getHlsRules(), overrides, compiled, summaries);
        compileExternal("live", LiveConfig.get().getConfig().getUrl(), LiveConfig.get().getHlsRules(), overrides, compiled, summaries);
        compileExternal("user", "local", UserHlsRuleStore.load(), overrides, compiled, summaries);
        rules = List.copyOf(compiled);
        entries = List.copyOf(summaries);
        dirty = false;
    }

    private static void compileBuiltin(HlsRulePackage rulePackage, Map<String, Boolean> overrides,
                                       List<HlsManifestCleaner.Rule> output, List<Entry> summaries) {
        for (HlsAdRule rule : rulePackage.getRules()) {
            if (rule == null) continue;
            String key = HlsRuleState.key("builtin", rulePackage.getPackageId(), rule.getId());
            boolean enabled = HlsRuleState.resolveEnabled(rule, key, overrides);
            compileOne(key, "builtin", rule, enabled, output, summaries);
        }
    }

    private static void compileExternal(String origin, String sourceId, List<HlsAdRule> rules, Map<String, Boolean> overrides,
                                        List<HlsManifestCleaner.Rule> output, List<Entry> summaries) {
        for (HlsAdRule rule : rules) {
            if (rule == null) continue;
            String key = HlsRuleState.key(origin, sourceId, rule.getId());
            Boolean override = overrides.get(key);
            compileOne(key, origin, rule, override == null ? rule.isEnabled() : override, output, summaries);
        }
    }

    private static void compileOne(String key, String source, HlsAdRule rule, boolean enabled,
                                   List<HlsManifestCleaner.Rule> output, List<Entry> summaries) {
        try {
            HlsManifestCleaner.Rule compiled = rule.compile();
            if (enabled) output.add(compiled);
            summaries.add(new Entry(key, rule.getId(), rule.getName(), rule.getVersion(), source, enabled, true, "", DETAIL_GSON.toJson(rule)));
        } catch (RuntimeException e) {
            SpiderDebug.log(TAG, "Skip invalid rule %s: %s", rule.getId(), e.getMessage());
            summaries.add(new Entry(key, rule.getId(), rule.getName(), rule.getVersion(), source, true, false,
                    e.getMessage() == null ? "Invalid rule" : e.getMessage(), DETAIL_GSON.toJson(rule)));
        }
    }

    public record Entry(String key, String id, String name, int version, String source,
                        boolean enabled, boolean valid, String error, String detail) {}
}
