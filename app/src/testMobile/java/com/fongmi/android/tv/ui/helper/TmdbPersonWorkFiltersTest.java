package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbItem;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TmdbPersonWorkFiltersTest {

    @Test
    public void from_keepsDepartmentAndMediaFiltersSeparate() {
        TmdbItem actingTv = item(1, "tv", "演员作品", "");
        TmdbItem directingMovie = item(2, "movie", "导演电影", "Directing");
        TmdbItem productionTv = item(3, "tv", "制作剧集", "Production");

        TmdbPersonWorkFilters filters = TmdbPersonWorkFilters.from(
                List.of(actingTv),
                List.of(directingMovie, productionTv)
        );

        assertEquals(3, filters.allCount());
        assertEquals(List.of("全部部门", "出演", "导演", "制作"), labels(filters.departmentOptions()));
        assertEquals(List.of("全部类型", "电影", "剧集"), labels(filters.mediaOptions()));
        assertEquals(List.of("制作剧集"), titles(filters.filter("department:Production", "tv")));
        assertEquals(List.of("演员作品", "制作剧集"), titles(filters.filter("all", "tv")));
    }

    private static TmdbItem item(int id, String mediaType, String title, String department) {
        return new TmdbItem(id, mediaType, title, "", "", "", "", "", 0.0, "", "", List.<Integer>of(), department);
    }

    private static List<String> labels(List<TmdbPersonWorkFilters.Option> options) {
        return options.stream().map(TmdbPersonWorkFilters.Option::label).toList();
    }

    private static List<String> titles(List<TmdbItem> items) {
        return items.stream().map(TmdbItem::getTitle).toList();
    }
}
