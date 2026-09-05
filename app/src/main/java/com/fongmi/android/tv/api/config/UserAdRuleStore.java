package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.UserAdRule;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户自定义去广规则的持久化管理
 * 存储于 SharedPreferences（key: user_ad_rules），JSON 数组
 */
public class UserAdRuleStore {

    private static final String PREF_KEY = "user_ad_rules";
    private static final Type LIST_TYPE = new TypeToken<List<UserAdRule>>() {}.getType();

    public static synchronized List<UserAdRule> load() {
        String json = Prefers.getString(PREF_KEY, "[]");
        try {
            List<UserAdRule> rules = App.gson().fromJson(json, LIST_TYPE);
            return rules == null ? new ArrayList<>() : rules;
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void save(List<UserAdRule> rules) {
        Prefers.put(PREF_KEY, App.gson().toJson(rules == null ? new ArrayList<>() : rules));
        RuleConfig.get().invalidate();
    }

    public static synchronized void add(UserAdRule rule) {
        if (rule == null) return;
        List<UserAdRule> rules = load();
        rules.add(rule);
        save(rules);
    }

    public static void delete(String id) {
        if (id == null) return;
        UserAdRule removed = null;
        synchronized (UserAdRuleStore.class) {
            List<UserAdRule> rules = load();
            for (int i = 0; i < rules.size(); i++) {
                if (id.equals(rules.get(i).getId())) {
                    removed = rules.remove(i);
                    break;
                }
            }
            if (removed != null) save(rules);
        }
        if (removed != null) ImportedAdRuleCandidateStore.reopen(removed.getImportedCandidateId());
    }

    public static synchronized void update(UserAdRule rule) {
        if (rule == null || rule.getId() == null) return;
        List<UserAdRule> rules = load();
        for (int i = 0; i < rules.size(); i++) {
            if (rule.getId().equals(rules.get(i).getId())) {
                rules.set(i, rule);
                break;
            }
        }
        save(rules);
    }

    /**
     * 转为可合并进 RuleConfig 的 Rule 列表，仅包含已启用且有内容的规则
     */
    public static List<Rule> toRules() {
        return load().stream()
                .filter(UserAdRule::isEnabled)
                .filter(r -> !r.getHosts().isEmpty() || !r.getRegex().isEmpty() || !r.getExclude().isEmpty())
                .map(UserAdRule::toRule)
                .collect(Collectors.toList());
    }

    /**
     * 收集所有已启用规则的 hosts（广告域名），供 RuleConfig.ads 使用（WebView 层拦截主力）
     */
    public static List<String> toAds() {
        return load().stream()
                .filter(UserAdRule::isEnabled)
                .filter(r -> !UserAdRule.SOURCE_INTERFACE_RULE.equals(r.getSource()))
                .flatMap(r -> r.getHosts().stream())
                .filter(h -> h != null && !h.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
}
