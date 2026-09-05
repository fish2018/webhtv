package com.fongmi.android.tv.setting;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.GroupRule;
import com.fongmi.android.tv.playback.PlaybackConfigIdentity;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiGroupRuleStore {

    public static final String KEY = "ai_group_rules";
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<String, List<GroupRule>>>() {}.getType();
    private static final Object LOCK = new Object();

    private AiGroupRuleStore() {
    }

    public static List<GroupRule> loadCurrent() {
        return load(currentKey());
    }

    public static List<GroupRule> load(String configKey) {
        if (TextUtils.isEmpty(configKey)) return new ArrayList<>();
        synchronized (LOCK) {
            return normalize(loadAllLocked().get(configKey));
        }
    }

    public static void replace(String configKey, List<GroupRule> rules) {
        if (TextUtils.isEmpty(configKey)) return;
        synchronized (LOCK) {
            Map<String, List<GroupRule>> all = loadAllLocked();
            writeLocked(all, configKey, rules);
        }
        GroupRuleConfig.invalidate();
    }

    public static void update(String configKey, GroupRule rule) {
        if (TextUtils.isEmpty(configKey) || rule == null || TextUtils.isEmpty(rule.getId())) return;
        boolean changed = false;
        synchronized (LOCK) {
            Map<String, List<GroupRule>> all = loadAllLocked();
            List<GroupRule> rules = normalize(all.get(configKey));
            for (int i = 0; i < rules.size(); i++) {
                if (!rule.getId().equals(rules.get(i).getId())) continue;
                rule.setSource(GroupRule.SOURCE_AI);
                rules.set(i, rule);
                writeLocked(all, configKey, rules);
                changed = true;
                break;
            }
        }
        if (changed) GroupRuleConfig.invalidate();
    }

    public static void delete(String configKey, String id) {
        if (TextUtils.isEmpty(configKey) || TextUtils.isEmpty(id)) return;
        boolean changed = false;
        synchronized (LOCK) {
            Map<String, List<GroupRule>> all = loadAllLocked();
            List<GroupRule> rules = normalize(all.get(configKey));
            if (rules.removeIf(item -> id.equals(item.getId()))) {
                writeLocked(all, configKey, rules);
                changed = true;
            }
        }
        if (changed) GroupRuleConfig.invalidate();
    }

    public static boolean hasCurrent() {
        return has(currentKey());
    }

    public static boolean has(String configKey) {
        return !load(configKey).isEmpty();
    }

    static Map<String, List<GroupRule>> parse(String json) {
        try {
            Map<String, List<GroupRule>> value = GSON.fromJson(json, TYPE);
            if (value == null) return new LinkedHashMap<>();
            Map<String, List<GroupRule>> result = new LinkedHashMap<>();
            for (Map.Entry<String, List<GroupRule>> entry : value.entrySet()) {
                if (TextUtils.isEmpty(entry.getKey())) continue;
                List<GroupRule> rules = normalize(entry.getValue());
                if (!rules.isEmpty()) result.put(entry.getKey(), rules);
            }
            return result;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    static String encode(Map<String, List<GroupRule>> value) {
        return GSON.toJson(value == null ? new LinkedHashMap<>() : value);
    }

    private static Map<String, List<GroupRule>> loadAllLocked() {
        return parse(Prefers.getString(KEY, "{}"));
    }

    private static void writeLocked(Map<String, List<GroupRule>> all, String configKey, List<GroupRule> rules) {
        List<GroupRule> normalized = normalize(rules);
        if (normalized.isEmpty()) all.remove(configKey);
        else all.put(configKey, normalized);
        Prefers.put(KEY, encode(all));
    }

    private static List<GroupRule> normalize(List<GroupRule> source) {
        List<GroupRule> rules = new ArrayList<>();
        if (source == null) return rules;
        for (GroupRule rule : source) {
            if (rule == null || TextUtils.isEmpty(rule.getId()) || TextUtils.isEmpty(rule.getRegex())) continue;
            rule.setSource(GroupRule.SOURCE_AI);
            if (rule.isValid()) rules.add(rule);
        }
        return rules;
    }

    private static String currentKey() {
        try {
            String key = PlaybackConfigIdentity.currentKey();
            return TextUtils.isEmpty(key) ? "cid:" + VodConfig.getCid() : key;
        } catch (Exception ignored) {
            return "";
        }
    }
}
