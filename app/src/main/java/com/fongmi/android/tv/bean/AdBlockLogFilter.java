package com.fongmi.android.tv.bean;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 广告拦截日志的列筛选工具。
 *
 * <p>筛选值按列分别保存，多个非空条件采用 AND 关系，并以不区分大小写的
 * 包含匹配执行。UI 可以通过 {@link ValueProvider} 提供格式化后的显示值，
 * 从而保证筛选内容与表格中看到的文本一致。</p>
 */
public final class AdBlockLogFilter {

    public enum Column {
        SITE_NAME,
        SITE_DOMAIN,
        VOD_NAME,
        LINE_NAME,
        EPISODE_NAME,
        RULE,
        AD_DOMAIN,
        BLOCKED_AT,
        SEGMENT_START,
        SEGMENT_END,
        SEGMENT_DURATION,
        PIPELINE
    }

    /** 将一条日志转换为各列可搜索文本的回调。 */
    public interface ValueProvider {
        Map<Column, String> values(AdBlockLog log);
    }

    private AdBlockLogFilter() {
    }

    /** 返回日志字段的默认文本映射，供界面在需要时覆盖格式化列。 */
    public static EnumMap<Column, String> values(AdBlockLog log) {
        EnumMap<Column, String> values = new EnumMap<>(Column.class);
        if (log == null) return values;
        values.put(Column.SITE_NAME, safe(log.getSiteName()));
        values.put(Column.SITE_DOMAIN, safe(log.getSiteDomain()));
        values.put(Column.VOD_NAME, safe(log.getVodName()));
        values.put(Column.LINE_NAME, safe(log.getLineName()));
        values.put(Column.EPISODE_NAME, safe(log.getEpisodeName()));
        values.put(Column.RULE, safe(log.getRuleId()));
        values.put(Column.AD_DOMAIN, safe(log.getAdDomain()));
        values.put(Column.BLOCKED_AT, log.getBlockedAt() > 0 ? String.valueOf(log.getBlockedAt()) : "");
        values.put(Column.SEGMENT_START, log.hasSegmentTiming() ? String.valueOf(log.getSegmentStartSeconds()) : "");
        values.put(Column.SEGMENT_END, log.hasSegmentTiming() ? String.valueOf(log.getSegmentEndSeconds()) : "");
        values.put(Column.SEGMENT_DURATION, log.hasSegmentTiming() ? String.valueOf(log.getSegmentDurationSeconds()) : "");
        values.put(Column.PIPELINE, safe(log.getPipelineName()));
        return values;
    }

    public static List<AdBlockLog> filter(List<AdBlockLog> logs, Map<Column, String> filters) {
        return filter(logs, filters, AdBlockLogFilter::values);
    }

    public static List<AdBlockLog> filter(List<AdBlockLog> logs,
                                          Map<Column, String> filters,
                                          ValueProvider provider) {
        return filter(logs, filters, Map.of(), provider);
    }

    /**
     * 使用文本条件和去重多选条件筛选日志。
     * 同一列的多选值采用 OR，不同列之间与文本条件采用 AND。
     */
    public static List<AdBlockLog> filter(List<AdBlockLog> logs,
                                          Map<Column, String> filters,
                                          Map<Column, ? extends Set<String>> selections,
                                          ValueProvider provider) {
        List<AdBlockLog> result = new ArrayList<>();
        if (logs == null || logs.isEmpty()) return result;
        ValueProvider valueProvider = provider == null ? AdBlockLogFilter::values : provider;
        for (AdBlockLog log : logs) {
            if (matches(valueProvider.values(log), filters, selections)) result.add(log);
        }
        return result;
    }

    public static boolean matches(Map<Column, String> values, Map<Column, String> filters) {
        return matches(values, filters, Map.of());
    }

    public static boolean matches(Map<Column, String> values,
                                  Map<Column, String> filters,
                                  Map<Column, ? extends Set<String>> selections) {
        if (selections != null) {
            for (Map.Entry<Column, ? extends Set<String>> entry : selections.entrySet()) {
                Set<String> selected = entry.getValue();
                if (selected == null || selected.isEmpty()) continue;
                String value = values == null ? "" : normalize(values.get(entry.getKey()));
                boolean selectedValue = false;
                for (String candidate : selected) {
                    if (value.equals(normalize(candidate))) {
                        selectedValue = true;
                        break;
                    }
                }
                if (!selectedValue) return false;
            }
        }
        if (filters == null || filters.isEmpty()) return true;
        for (Map.Entry<Column, String> entry : filters.entrySet()) {
            String query = normalize(entry.getValue());
            if (query.isEmpty()) continue;
            String value = values == null ? "" : normalize(values.get(entry.getKey()));
            if (!value.contains(query)) return false;
        }
        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
