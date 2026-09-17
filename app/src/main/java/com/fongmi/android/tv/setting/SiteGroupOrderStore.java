package com.fongmi.android.tv.setting;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SiteGroupOrderStore {

    private static final String KEY_PREFIX = "site_group_order_";
    private static final Type TYPE = new TypeToken<List<String>>() {}.getType();

    private SiteGroupOrderStore() {
    }

    public static List<String> sort(List<String> groups) {
        return order(groups, load());
    }

    public static void save(List<String> allGroups, List<String> visibleGroups) {
        List<String> fullOrder = sort(allGroups);
        List<String> merged = mergeVisibleOrder(fullOrder, visibleGroups);
        Prefers.put(key(), App.gson().toJson(merged));
    }

    public static boolean move(List<String> groups, String group, int direction) {
        if (groups == null || group == null || (direction != -1 && direction != 1)) return false;
        int from = groups.indexOf(group);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= groups.size()) return false;
        Collections.swap(groups, from, to);
        return true;
    }

    static List<String> order(List<String> groups, List<String> savedOrder) {
        List<String> available = normalize(groups);
        if (available.isEmpty()) return available;
        Set<String> remaining = new LinkedHashSet<>(available);
        List<String> result = new ArrayList<>();
        for (String group : normalize(savedOrder)) {
            if (remaining.remove(group)) result.add(group);
        }
        for (String group : available) {
            if (remaining.remove(group)) result.add(group);
        }
        return result;
    }

    static List<String> mergeVisibleOrder(List<String> fullOrder, List<String> visibleOrder) {
        List<String> full = normalize(fullOrder);
        List<String> visible = normalize(visibleOrder);
        if (visible.isEmpty()) return full;

        Set<String> fullSet = new HashSet<>(full);
        List<String> knownVisible = new ArrayList<>();
        for (String group : visible) {
            if (fullSet.contains(group)) knownVisible.add(group);
        }

        Set<String> visibleSet = new HashSet<>(knownVisible);
        List<String> result = new ArrayList<>();
        int visibleIndex = 0;
        for (String group : full) {
            if (visibleSet.contains(group) && visibleIndex < knownVisible.size()) result.add(knownVisible.get(visibleIndex++));
            else result.add(group);
        }
        for (String group : visible) {
            if (!result.contains(group)) result.add(group);
        }
        return normalize(result);
    }

    private static List<String> normalize(List<String> groups) {
        Set<String> result = new LinkedHashSet<>();
        if (groups == null) return new ArrayList<>();
        for (String group : groups) {
            if (group == null) continue;
            String value = group.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return new ArrayList<>(result);
    }

    private static List<String> load() {
        try {
            List<String> groups = App.gson().fromJson(Prefers.getString(key(), "[]"), TYPE);
            return groups == null ? new ArrayList<>() : groups;
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    private static String key() {
        return KEY_PREFIX + VodConfig.getCid();
    }
}
