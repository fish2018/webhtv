package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 被用户临时禁用的 APP 默认去广规则 ID 集合的持久化管理。
 * 存储于 SharedPreferences（key: disabled_default_rule_ids），JSON 数组。
 * 规则 ID 由 {@link com.fongmi.android.tv.utils.RuleIdUtil#computeRuleId} 计算。
 */
public class DisabledDefaultRuleStore {

    private static final String PREF_KEY = "disabled_default_rule_ids";
    private static final Type LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    public static Set<String> load() {
        String json = Prefers.getString(PREF_KEY, "[]");
        try {
            List<String> ids = App.gson().fromJson(json, LIST_TYPE);
            return ids == null ? new HashSet<>() : new HashSet<>(ids);
        } catch (Throwable e) {
            return new HashSet<>();
        }
    }

    public static void save(Set<String> ids) {
        Prefers.put(PREF_KEY, App.gson().toJson(new ArrayList<>(ids == null ? new HashSet<>() : ids)));
        RuleConfig.get().invalidate();
    }

    public static boolean isDisabled(String ruleId) {
        if (ruleId == null || ruleId.isEmpty()) return false;
        return load().contains(ruleId);
    }

    public static void disable(String ruleId) {
        if (ruleId == null || ruleId.isEmpty()) return;
        Set<String> ids = load();
        if (ids.add(ruleId)) save(ids);
    }

    public static void enable(String ruleId) {
        if (ruleId == null || ruleId.isEmpty()) return;
        Set<String> ids = load();
        if (ids.remove(ruleId)) save(ids);
    }

    public static void setDisabled(String ruleId, boolean disabled) {
        if (disabled) disable(ruleId);
        else enable(ruleId);
    }
}
