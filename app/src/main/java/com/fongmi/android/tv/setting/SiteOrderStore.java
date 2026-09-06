package com.fongmi.android.tv.setting;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SiteOrderStore {

    private static final String KEY_PREFIX = "site_dialog_order_";
    private static final Type TYPE = new TypeToken<List<String>>() {}.getType();

    public static void sortSites(List<Site> sites) {
        if (sites == null || sites.size() < 2) return;
        List<String> keys = load();
        if (keys.isEmpty()) return;
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) indexes.put(keys.get(i), i);
        sites.sort((a, b) -> {
            int ai = indexes.getOrDefault(a.getKey(), a.isFile() ? -1 : Integer.MAX_VALUE);
            int bi = indexes.getOrDefault(b.getKey(), b.isFile() ? -1 : Integer.MAX_VALUE);
            return Integer.compare(ai, bi);
        });
    }

    public static void save(List<Site> sites) {
        if (sites == null) return;
        List<String> keys = new ArrayList<>();
        for (Site site : sites) {
            if (site == null || TextUtils.isEmpty(site.getKey())) continue;
            keys.add(site.getKey());
        }
        Prefers.put(key(), App.gson().toJson(keys));
    }

    private static List<String> load() {
        try {
            List<String> keys = App.gson().fromJson(Prefers.getString(key(), "[]"), TYPE);
            return keys == null ? new ArrayList<>() : keys;
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    private static String key() {
        return KEY_PREFIX + VodConfig.getCid();
    }
}