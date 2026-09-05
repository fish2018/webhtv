package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TmdbPersonWorkFilters {

    public static final String ALL = "all";
    public static final String CAST = "cast";
    public static final String MOVIE = "movie";
    public static final String TV = "tv";

    private final List<TmdbItem> allWorks;
    private final Map<String, List<TmdbItem>> departmentWorks;
    private final Map<String, List<TmdbItem>> mediaWorks;

    private TmdbPersonWorkFilters(List<TmdbItem> allWorks, Map<String, List<TmdbItem>> departmentWorks, Map<String, List<TmdbItem>> mediaWorks) {
        this.allWorks = allWorks;
        this.departmentWorks = departmentWorks;
        this.mediaWorks = mediaWorks;
    }

    public static TmdbPersonWorkFilters from(List<TmdbItem> castWorks, List<TmdbItem> crewWorks) {
        LinkedHashMap<String, TmdbItem> all = new LinkedHashMap<>();
        LinkedHashMap<String, List<TmdbItem>> departments = new LinkedHashMap<>();
        LinkedHashMap<String, List<TmdbItem>> media = new LinkedHashMap<>();
        departments.put(ALL, new ArrayList<>());
        media.put(ALL, new ArrayList<>());

        addWorks(all, departments, media, CAST, castWorks);
        if (crewWorks != null) {
            for (TmdbItem item : crewWorks) {
                addWork(all, departments, media, departmentKey(item), item);
            }
        }

        List<TmdbItem> allItems = new ArrayList<>(all.values());
        departments.put(ALL, allItems);
        media.put(ALL, allItems);
        return new TmdbPersonWorkFilters(allItems, departments, media);
    }

    public int allCount() {
        return allWorks.size();
    }

    public List<Option> departmentOptions() {
        List<Option> options = new ArrayList<>();
        addOption(options, ALL, "全部部门", allWorks);
        for (Map.Entry<String, List<TmdbItem>> entry : departmentWorks.entrySet()) {
            String key = entry.getKey();
            if (ALL.equals(key)) continue;
            addOption(options, key, departmentLabel(key), entry.getValue());
        }
        return options;
    }

    public List<Option> mediaOptions() {
        List<Option> options = new ArrayList<>();
        addOption(options, ALL, "全部类型", allWorks);
        addOption(options, MOVIE, "电影", mediaWorks.get(MOVIE));
        addOption(options, TV, "剧集", mediaWorks.get(TV));
        return options;
    }

    public List<TmdbItem> filter(String departmentFilter, String mediaFilter) {
        List<TmdbItem> departments = departmentWorks.getOrDefault(emptyToAll(departmentFilter), allWorks);
        List<TmdbItem> media = mediaWorks.getOrDefault(emptyToAll(mediaFilter), allWorks);
        if (ALL.equals(emptyToAll(departmentFilter)) && ALL.equals(emptyToAll(mediaFilter))) return new ArrayList<>(allWorks);
        LinkedHashMap<String, TmdbItem> mediaKeys = new LinkedHashMap<>();
        for (TmdbItem item : media) mediaKeys.put(key(item), item);
        List<TmdbItem> result = new ArrayList<>();
        for (TmdbItem item : departments) {
            if (mediaKeys.containsKey(key(item))) result.add(item);
        }
        return result;
    }

    private static void addWorks(LinkedHashMap<String, TmdbItem> all, Map<String, List<TmdbItem>> departments, Map<String, List<TmdbItem>> media, String department, List<TmdbItem> works) {
        if (works == null) return;
        for (TmdbItem item : works) addWork(all, departments, media, department, item);
    }

    private static void addWork(LinkedHashMap<String, TmdbItem> all, Map<String, List<TmdbItem>> departments, Map<String, List<TmdbItem>> media, String department, TmdbItem item) {
        if (item == null || item.getTmdbId() <= 0) return;
        all.putIfAbsent(key(item), item);
        putUnique(departments, department, item);
        if (item.isMovie()) putUnique(media, MOVIE, item);
        else if (item.isTv()) putUnique(media, TV, item);
    }

    private static void putUnique(Map<String, List<TmdbItem>> map, String key, TmdbItem item) {
        List<TmdbItem> items = map.computeIfAbsent(emptyToAll(key), value -> new ArrayList<>());
        String itemKey = key(item);
        for (TmdbItem existing : items) {
            if (itemKey.equals(key(existing))) return;
        }
        items.add(item);
    }

    private static void addOption(List<Option> options, String key, String label, List<TmdbItem> items) {
        if (items == null || items.isEmpty()) return;
        options.add(new Option(key, label, items.size()));
    }

    private static String departmentKey(TmdbItem item) {
        String department = item == null ? "" : item.getDepartment();
        return department.isEmpty() ? "department:Other" : "department:" + department;
    }

    private static String departmentLabel(String key) {
        if (CAST.equals(key)) return "出演";
        String department = key != null && key.startsWith("department:") ? key.substring("department:".length()) : key;
        if (department == null) return "";
        switch (department.toLowerCase(Locale.ROOT)) {
            case "acting": return "出演";
            case "directing": return "导演";
            case "writing": return "编剧";
            case "production": return "制作";
            case "camera": return "摄影";
            case "editing": return "剪辑";
            case "sound": return "声音";
            case "art": return "美术";
            case "visual effects": return "视效";
            case "costume & make-up": return "服化";
            case "lighting": return "灯光";
            case "crew": return "剧组";
            case "creator": return "创作";
            case "other": return "其他";
            default: return department;
        }
    }

    private static String emptyToAll(String value) {
        return value == null || value.isEmpty() ? ALL : value;
    }

    private static String key(TmdbItem item) {
        return item.getMediaType() + ":" + item.getTmdbId();
    }

    public static final class Option {
        private final String key;
        private final String label;
        private final int count;

        public Option(String key, String label, int count) {
            this.key = key;
            this.label = label;
            this.count = count;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public int count() {
            return count;
        }
    }
}
