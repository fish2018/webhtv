package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.bean.HlsRulePackage;
import com.fongmi.android.tv.bean.HlsRuleState;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.utils.RuleIdUtil;
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
    public static final String LEGACY_FALLBACK_KEY = "hls.legacy-fallback";
    private static final String LEGACY_RULE_PREFIX = "legacy:";
    public static final String LEGACY_FALLBACK_SUMMARY = "仅完整 HLS 点播清单 · 路径/文件名前缀少数组 · 不连续标签明显短块";
    public static final String LEGACY_FALLBACK_DETAIL = "策略一：按分片路径或文件名前缀分组，仅移除明显少数组。\n"
            + "策略二：按 #EXT-X-DISCONTINUITY 分块，仅移除相对主块明显偏短的少数块。\n"
            + "适用范围：仅处理包含 #EXT-X-ENDLIST 的完整 HLS 点播清单。";
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

    public static synchronized boolean isLegacyFallbackEnabled() {
        if (dirty) reload();
        return entries.stream().filter(item -> LEGACY_FALLBACK_KEY.equals(item.key()))
                .findFirst().map(Entry::enabled).orElse(true);
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
        compileLegacyRules(RuleConfig.get().getRules(), compiled);
        boolean fallbackEnabled = !Boolean.FALSE.equals(overrides.get(LEGACY_FALLBACK_KEY));
        summaries.add(new Entry(LEGACY_FALLBACK_KEY, LEGACY_FALLBACK_KEY,
                "内置 HLS 兜底规则（路径/文件名前缀少数组、不连续标签明显短块）", 1,
                "builtin", fallbackEnabled, true, "", LEGACY_FALLBACK_DETAIL));
        rules = List.copyOf(compiled);
        entries = List.copyOf(summaries);
        dirty = false;
    }

    static void compileLegacyRules(List<Rule> legacyRules, List<HlsManifestCleaner.Rule> output) {
        if (legacyRules == null) return;
        for (Rule rule : legacyRules) {
            if (rule == null || rule.getExclude().isEmpty()) continue;
            try {
                output.add(HlsManifestCleaner.Rule.builder()
                        .id(legacyRuleId(rule))
                        .playlistHostPatterns(rule.getHosts())
                        .segmentUrlPatterns(rule.getExclude())
                        .minimumSignals(1)
                        .build());
            } catch (RuntimeException e) {
                SpiderDebug.log(TAG, "Skip invalid legacy rule %s: %s", rule.getName(), e.getMessage());
            }
        }
    }

    static String legacyRuleId(Rule rule) {
        return LEGACY_RULE_PREFIX + RuleIdUtil.computeRuleId(rule);
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
