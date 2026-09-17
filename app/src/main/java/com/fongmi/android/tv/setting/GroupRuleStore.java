package com.fongmi.android.tv.setting;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.GroupRule;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GroupRuleStore {

    public static final String USER_KEY = "user_group_rules";
    public static final String DISABLED_KEY = "disabled_group_rule_ids";

    private static final Type LIST_TYPE = new TypeToken<List<GroupRule>>() {}.getType();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Boolean>>() {}.getType();
    private static final Object LOCK = new Object();

    private GroupRuleStore() {
    }

    public static List<GroupRule> loadUser() {
        synchronized (LOCK) {
            return loadUserLocked();
        }
    }

    public static void saveUser(List<GroupRule> rules) {
        synchronized (LOCK) {
            saveUserLocked(rules);
        }
        GroupRuleConfig.invalidate();
    }

    public static void addUser(GroupRule rule) {
        if (rule == null) return;
        synchronized (LOCK) {
            rule.setSource(GroupRule.SOURCE_USER);
            List<GroupRule> rules = loadUserLocked();
            rules.add(rule);
            saveUserLocked(rules);
        }
        GroupRuleConfig.invalidate();
    }

    public static void updateUser(GroupRule rule) {
        if (rule == null || TextUtils.isEmpty(rule.getId())) return;
        boolean changed = false;
        synchronized (LOCK) {
            List<GroupRule> rules = loadUserLocked();
            for (int i = 0; i < rules.size(); i++) {
                if (!rule.getId().equals(rules.get(i).getId())) continue;
                rule.setSource(GroupRule.SOURCE_USER);
                rules.set(i, rule);
                saveUserLocked(rules);
                changed = true;
                break;
            }
        }
        if (changed) GroupRuleConfig.invalidate();
    }

    public static void deleteUser(String id) {
        if (TextUtils.isEmpty(id)) return;
        boolean changed = false;
        synchronized (LOCK) {
            List<GroupRule> rules = loadUserLocked();
            if (rules.removeIf(item -> item != null && id.equals(item.getId()))) {
                saveUserLocked(rules);
                changed = true;
            }
        }
        if (changed) GroupRuleConfig.invalidate();
    }

    public static Map<String, Boolean> loadDisabled() {
        synchronized (LOCK) {
            return loadDisabledLocked();
        }
    }

    public static void setEnabled(String id, boolean enabled) {
        if (TextUtils.isEmpty(id)) return;
        boolean changed;
        synchronized (LOCK) {
            Map<String, Boolean> disabled = loadDisabledLocked();
            changed = enabled ? disabled.remove(id) != null : !Boolean.TRUE.equals(disabled.put(id, true));
            if (changed) Prefers.put(DISABLED_KEY, App.gson().toJson(disabled));
        }
        if (changed) GroupRuleConfig.invalidate();
    }

    public static boolean isDisabled(String id) {
        return Boolean.TRUE.equals(loadDisabled().get(id));
    }

    private static List<GroupRule> loadUserLocked() {
        String json = Prefers.getString(USER_KEY, "[]");
        try {
            List<GroupRule> rules = App.gson().fromJson(json, LIST_TYPE);
            if (rules == null) return new ArrayList<>();
            for (GroupRule rule : rules) {
                if (rule == null) continue;
                rule.setSource(GroupRule.SOURCE_USER);
            }
            return new ArrayList<>(rules);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static void saveUserLocked(List<GroupRule> rules) {
        Prefers.put(USER_KEY, App.gson().toJson(rules == null ? new ArrayList<>() : rules));
    }

    private static Map<String, Boolean> loadDisabledLocked() {
        String json = Prefers.getString(DISABLED_KEY, "{}");
        try {
            Map<String, Boolean> value = App.gson().fromJson(json, MAP_TYPE);
            return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
