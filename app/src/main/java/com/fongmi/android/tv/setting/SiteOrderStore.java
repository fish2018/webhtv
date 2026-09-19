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
        // 分离已记忆和未记忆的站点
        List<Site> remembered = new ArrayList<>();
        List<Site> unrememberedFile = new ArrayList<>();
        List<Site> unrememberedApi = new ArrayList<>();
        for (Site site : sites) {
            if (indexes.containsKey(site.getKey())) {
                remembered.add(site);
            } else if (site.isFile()) {
                unrememberedFile.add(site);
            } else {
                unrememberedApi.add(site);
            }
        }
        // 已记忆的按记忆顺序排序
        remembered.sort((a, b) -> Integer.compare(indexes.get(a.getKey()), indexes.get(b.getKey())));
        // 未记忆的文件源按类型插入到记忆列表中相应类型的最后
        if (!unrememberedFile.isEmpty()) {
            // 按类型分组
            Map<String, List<Site>> byType = new HashMap<>();
            for (Site site : unrememberedFile) {
                String type = site.getFileType();
                byType.computeIfAbsent(type, k -> new ArrayList<>()).add(site);
            }
            // 固定类型顺序
            String[] typeOrder = {"XBPQ", "JS", "PY", "RAW"};
            // 从后往前插入，避免索引偏移
            for (int t = typeOrder.length - 1; t >= 0; t--) {
                String type = typeOrder[t];
                List<Site> group = byType.get(type);
                if (group == null || group.isEmpty()) continue;
                // 找到该类型在记忆列表中的最后位置
                int insertPos = remembered.size();
                for (int i = remembered.size() - 1; i >= 0; i--) {
                    Site s = remembered.get(i);
                    if (s.isFile() && s.getFileType().equals(type)) {
                        insertPos = i + 1;
                        break;
                    }
                }
                remembered.addAll(insertPos, group);
            }
        }
        // 未记忆的接口源放最后
        remembered.addAll(unrememberedApi);
        sites.clear();
        sites.addAll(remembered);
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
