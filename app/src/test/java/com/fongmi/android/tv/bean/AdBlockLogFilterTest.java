package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdBlockLogFilterTest {

    @Test
    public void filtersEachColumnIndependentlyAndCombinesConditions() {
        AdBlockLog first = new AdBlockLog(1L, "site-a", "站点 A", "a.example",
                "HLS", "ad-a.example", "rule-a", 10, 2)
                .setPlaybackContext("墨雨云间", "LZ源", "第 2 集");
        AdBlockLog second = new AdBlockLog(2L, "site-b", "站点 B", "b.example",
                "MPV", "ad-b.example", "rule-b", 20, 3)
                .setPlaybackContext("庆余年", "备用线路", "第 1 集");
        List<AdBlockLog> logs = List.of(first, second);

        EnumMap<AdBlockLogFilter.Column, String> filters = new EnumMap<>(AdBlockLogFilter.Column.class);
        filters.put(AdBlockLogFilter.Column.VOD_NAME, "墨雨");
        assertEquals(List.of(first), AdBlockLogFilter.filter(logs, filters));

        filters.put(AdBlockLogFilter.Column.LINE_NAME, "lz");
        filters.put(AdBlockLogFilter.Column.EPISODE_NAME, "第 2");
        assertEquals(List.of(first), AdBlockLogFilter.filter(logs, filters));

        filters.put(AdBlockLogFilter.Column.SITE_DOMAIN, "not-found");
        assertTrue(AdBlockLogFilter.filter(logs, filters).isEmpty());
    }

    @Test
    public void emptyFiltersAndBlankFieldsAreHandledSafely() {
        AdBlockLog log = new AdBlockLog();
        EnumMap<AdBlockLogFilter.Column, String> filters = new EnumMap<>(AdBlockLogFilter.Column.class);
        filters.put(AdBlockLogFilter.Column.VOD_NAME, "  ");
        assertEquals(1, AdBlockLogFilter.filter(List.of(log), filters).size());

        filters.put(AdBlockLogFilter.Column.VOD_NAME, "missing");
        assertTrue(AdBlockLogFilter.filter(List.of(log), filters).isEmpty());
    }

    @Test
    public void deduplicatedSelectionsUseOrWithinAColumnAndAndAcrossColumns() {
        AdBlockLog first = new AdBlockLog().setPlaybackContext("剧一", "线路 A", "第一集");
        AdBlockLog second = new AdBlockLog().setPlaybackContext("剧二", "线路 A", "第二集");
        AdBlockLog third = new AdBlockLog().setPlaybackContext("剧三", "线路 B", "第三集");
        EnumMap<AdBlockLogFilter.Column, Set<String>> selections =
                new EnumMap<>(AdBlockLogFilter.Column.class);
        selections.put(AdBlockLogFilter.Column.VOD_NAME,
                new LinkedHashSet<>(List.of("剧一", "剧二")));
        selections.put(AdBlockLogFilter.Column.LINE_NAME,
                new LinkedHashSet<>(List.of("线路 A")));

        assertEquals(List.of(first, second), AdBlockLogFilter.filter(
                List.of(first, second, third), Map.of(), selections, AdBlockLogFilter::values));
    }
}
