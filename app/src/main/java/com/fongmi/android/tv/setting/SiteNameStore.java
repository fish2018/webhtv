package com.fongmi.android.tv.setting;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.playback.PlaybackConfigIdentity;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SiteNameStore {

    public static final String KEY = "site_names";
    private static final Type TYPE = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
    private static String cachedJson;
    private static Map<String, Map<String, String>> cachedValue;

    private SiteNameStore() {
    }

    public static String get(Site site) {
        if (site == null || TextUtils.isEmpty(site.getKey())) return "";
        Map<String, String> names = load().get(configKey());
        if (names == null) return "";
        String name = names.get(site.getKey());
        return name == null ? "" : name.trim();
    }

    public static String getDisplayName(int cid, String siteKey, String rawName) {
        Config item = cid == VodConfig.getCid() ? VodConfig.get().getConfig() : Config.find(cid);
        String config = item == null ? "" : PlaybackConfigIdentity.keyForUrl(item.getUrl());
        String name = SiteNameRules.effectiveName(rawName, get(config, siteKey));
        return TextUtils.isEmpty(name) ? siteKey : name;
    }

    private static String get(String config, String siteKey) {
        if (TextUtils.isEmpty(config) || TextUtils.isEmpty(siteKey)) return "";
        Map<String, String> names = load().get(config);
        if (names == null) return "";
        String name = names.get(siteKey);
        return name == null ? "" : name.trim();
    }

    public static synchronized void put(Site site, String name) {
        if (site == null || TextUtils.isEmpty(site.getKey())) return;
        String config = configKey();
        if (TextUtils.isEmpty(config)) return;
        Map<String, Map<String, String>> all = load();
        Map<String, String> names = all.computeIfAbsent(config, ignored -> new LinkedHashMap<>());
        String value = SiteNameRules.customNameForStorage(site.getName(), name);
        if (value.isEmpty()) names.remove(site.getKey());
        else names.put(site.getKey(), value);
        if (names.isEmpty()) all.remove(config);
        save(all);
    }

    public static String getEditableName(Site site) {
        return site == null ? "" : SiteNameRules.effectiveName(site.getName(), get(site));
    }

    public static String getDisplayName(Site site) {
        if (site == null) return "";
        String name = SiteNameRules.effectiveName(site.getName(), get(site));
        return TextUtils.isEmpty(name) ? site.getKey() : name;
    }

    public static List<String> getGroups(Site site) {
        if (site == null) return List.of();
        return SiteNameRules.groups(site.getName(), get(site));
    }

    public static boolean matchesSearch(Site site, String keyword) {
        return site != null && SiteNameRules.matchesSearch(site.getName(), get(site), site.getKey(), keyword);
    }

    public static int count() {
        Map<String, String> names = load().get(configKey());
        return names == null ? 0 : names.size();
    }

    public static synchronized void clear() {
        Map<String, Map<String, String>> all = load();
        all.remove(configKey());
        save(all);
    }

    private static synchronized Map<String, Map<String, String>> load() {
        String json = Prefers.getString(KEY, "{}");
        if (cachedValue != null && TextUtils.equals(cachedJson, json)) return cachedValue;
        try {
            Map<String, Map<String, String>> value = App.gson().fromJson(json, TYPE);
            cachedValue = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
        } catch (Throwable e) {
            cachedValue = new LinkedHashMap<>();
        }
        cachedJson = json;
        return cachedValue;
    }

    private static synchronized void save(Map<String, Map<String, String>> value) {
        cachedValue = value;
        cachedJson = App.gson().toJson(value);
        Prefers.put(KEY, cachedJson);
    }

    private static String configKey() {
        String key = PlaybackConfigIdentity.currentKey();
        return TextUtils.isEmpty(key) ? "cid:" + VodConfig.getCid() : key;
    }
}
