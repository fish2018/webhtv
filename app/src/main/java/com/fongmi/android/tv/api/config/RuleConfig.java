package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.utils.RuleIdUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RuleConfig {

    public static class DefaultRuleEntry {
        private final Rule rule;
        private final String source;

        DefaultRuleEntry(Rule rule, String source) {
            this.rule = rule;
            this.source = source;
        }

        public Rule getRule() { return rule; }
        public String getSource() { return source; }
    }

    private List<String> ads = List.of();
    private List<Rule> rules = List.of();
    private boolean dirty;

    public static RuleConfig get() {
        return Loader.INSTANCE;
    }

    public List<String> getAds() {
        if (dirty) merge();
        return ads;
    }

    public List<Rule> getRules() {
        if (dirty) merge();
        return rules;
    }

    void invalidate() {
        dirty = true;
    }

    /**
     * 获取所有 APP 默认规则（未过滤禁用状态），供规则管理界面展示。
     * 来自 VodConfig + LiveConfig，不含用户自定义规则。
     */
    public List<Rule> getDefaultRules() {
        List<Rule> defaultRules = new ArrayList<>(VodConfig.get().getRules());
        defaultRules.addAll(LiveConfig.get().getRules());
        return defaultRules;
    }

    public List<DefaultRuleEntry> getDefaultRuleEntries() {
        List<DefaultRuleEntry> entries = new ArrayList<>();
        String vodSource = formatSource("点播接口", VodConfig.get().getConfig().getName(), VodConfig.get().getConfig().getUrl());
        String liveSource = formatSource("直播接口", LiveConfig.get().getConfig().getName(), LiveConfig.get().getConfig().getUrl());
        for (Rule rule : VodConfig.get().getRules()) entries.add(new DefaultRuleEntry(rule, vodSource));
        for (Rule rule : LiveConfig.get().getRules()) entries.add(new DefaultRuleEntry(rule, liveSource));
        return entries;
    }

    static String formatSource(String type, String name, String url) {
        String detail = name != null && !name.trim().isEmpty() ? name.trim() : url == null ? "" : url.trim();
        return detail.isEmpty() ? type : type + " · " + detail;
    }

    private void merge() {
        Set<String> disabledIds = DisabledDefaultRuleStore.load();

        // 收集默认规则，过滤被禁用的
        List<Rule> defaultRules = new ArrayList<>(VodConfig.get().getRules());
        defaultRules.addAll(LiveConfig.get().getRules());
        List<Rule> enabledDefaults = defaultRules.stream()
                .filter(r -> !disabledIds.contains(RuleIdUtil.computeRuleId(r)))
                .collect(Collectors.toList());

        // ads: 默认 ads + 用户规则的 hosts
        List<String> ads = new ArrayList<>(VodConfig.get().getAds());
        ads.addAll(LiveConfig.get().getAds());
        ads.addAll(UserAdRuleStore.toAds());
        this.ads = ads;

        // rules: 启用的默认规则 + 用户规则
        List<Rule> rules = new ArrayList<>(enabledDefaults);
        rules.addAll(UserAdRuleStore.toRules());
        this.rules = rules;

        dirty = false;
    }

    private static class Loader {
        static volatile RuleConfig INSTANCE = new RuleConfig();
    }
}
