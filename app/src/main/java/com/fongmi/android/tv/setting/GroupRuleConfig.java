package com.fongmi.android.tv.setting;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.GroupRule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GroupRuleConfig {

    public static final String BUILTIN_BRACKET = "builtin_bracket_tag";
    public static final String BUILTIN_PIPE = "builtin_pipe_quality";
    public static final String BUILTIN_BOX = "builtin_box_separator";
    public static final String BUILTIN_BULLET = "builtin_bullet_suffix";

    private static volatile List<GroupRule> interfaceRules = List.of();
    private static volatile List<GroupRule> cachedActive;
    private static volatile List<Entry> cachedEntries;

    private GroupRuleConfig() {
    }

    public static List<GroupRule> builtins() {
        List<GroupRule> items = new ArrayList<>();
        items.add(GroupRule.builtin(BUILTIN_BRACKET, "方括号标签", "\\[([^\\]]+)\\]", false));
        items.add(GroupRule.builtin(BUILTIN_PIPE, "竖线后缀", "(?i)(?:[|｜])\\s*([^|｜]+?)\\s*$", false));
        items.add(GroupRule.builtin(BUILTIN_BOX, "框线分隔", "(?i)┆\\s*([^┆]+)\\s*$", false));
        items.add(GroupRule.builtin(BUILTIN_BULLET, "圆点后缀", "(?i)(?:[•·])\\s*([^•·]+?)\\s*$", false));
        return items;
    }

    public static synchronized void setInterfaceRules(List<GroupRule> rules) {
        interfaceRules = rules == null ? List.of() : List.copyOf(rules);
        invalidate();
    }

    public static List<GroupRule> getInterfaceRules() {
        return interfaceRules;
    }

    public static synchronized void invalidate() {
        cachedActive = null;
        cachedEntries = null;
    }

    public static List<String> extract(String text) {
        if (TextUtils.isEmpty(text)) return List.of();
        Set<String> groups = new LinkedHashSet<>();
        for (GroupRule rule : activeRules()) {
            groups.addAll(rule.extract(text));
        }
        return new ArrayList<>(groups);
    }

    public static List<GroupRule> activeRules() {
        List<GroupRule> cached = cachedActive;
        if (cached != null) return cached;
        synchronized (GroupRuleConfig.class) {
            if (cachedActive != null) return cachedActive;
            List<GroupRule> active = new ArrayList<>();
            for (Entry entry : entries()) {
                if (entry.enabled() && entry.rule().isValid()) active.add(entry.rule());
            }
            cachedActive = active;
            return active;
        }
    }

    public static List<Entry> entries() {
        List<Entry> cached = cachedEntries;
        if (cached != null) return cached;
        synchronized (GroupRuleConfig.class) {
            if (cachedEntries != null) return cachedEntries;
            Map<String, Boolean> disabled = GroupRuleStore.loadDisabled();
            List<Entry> items = new ArrayList<>();
            for (GroupRule rule : builtins()) {
                boolean enabled = !Boolean.TRUE.equals(disabled.get(rule.getId()));
                items.add(new Entry(rule, enabled));
            }
            for (GroupRule rule : interfaceRules) {
                if (rule == null || TextUtils.isEmpty(rule.getId())) continue;
                boolean enabled = rule.isEnabled() && !Boolean.TRUE.equals(disabled.get(rule.getId()));
                items.add(new Entry(copy(rule, GroupRule.SOURCE_INTERFACE), enabled));
            }
            for (GroupRule rule : AiGroupRuleStore.loadCurrent()) {
                if (rule == null || TextUtils.isEmpty(rule.getId())) continue;
                items.add(new Entry(copy(rule, GroupRule.SOURCE_AI), rule.isEnabled()));
            }
            for (GroupRule rule : GroupRuleStore.loadUser()) {
                if (rule == null || TextUtils.isEmpty(rule.getId())) continue;
                boolean enabled = rule.isEnabled() && !Boolean.TRUE.equals(disabled.get(rule.getId()));
                items.add(new Entry(copy(rule, GroupRule.SOURCE_USER), enabled));
            }
            cachedEntries = items;
            return items;
        }
    }

    public static int enabledCount() {
        int count = 0;
        for (Entry entry : entries()) if (entry.enabled()) count++;
        return count;
    }

    public static int totalCount() {
        return entries().size();
    }

    private static GroupRule copy(GroupRule source, String sourceType) {
        GroupRule rule = new GroupRule();
        rule.setId(source.getId());
        rule.setName(source.getName());
        rule.setRegex(source.getRegex());
        rule.setEnabled(source.isEnabled());
        rule.setSource(sourceType);
        rule.setWrapBracket(source.isWrapBracket());
        return rule;
    }

    public record Entry(GroupRule rule, boolean enabled) {
    }
}
