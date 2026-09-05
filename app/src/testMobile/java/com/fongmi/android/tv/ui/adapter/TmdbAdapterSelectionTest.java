package com.fongmi.android.tv.ui.adapter;

import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.TmdbItem;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TmdbAdapterSelectionTest {

    @Test
    public void selectedPositionMatchesTmdbIdAndMediaType() {
        TmdbItem current = item(123, "tv", "同名作品");
        List<TmdbItem> results = List.of(
                item(123, "movie", "同名作品"),
                item(123, "tv", "同名作品"),
                item(456, "tv", "其他作品")
        );

        assertEquals(1, TmdbAdapter.findSelectedPosition(results, current));
    }

    @Test
    public void noCurrentMatchHasNoSelectedPosition() {
        assertEquals(RecyclerView.NO_POSITION, TmdbAdapter.findSelectedPosition(
                List.of(item(123, "tv", "候选作品")), null));
    }

    @Test
    public void selectedPositionTracksReplacementSearchResults() {
        TmdbItem current = item(123, "tv", "当前作品");

        assertEquals(RecyclerView.NO_POSITION, TmdbAdapter.findSelectedPosition(
                List.of(item(456, "tv", "其他作品")), current));
        assertEquals(1, TmdbAdapter.findSelectedPosition(List.of(
                item(456, "tv", "其他作品"),
                item(123, "tv", "当前作品")
        ), current));
    }

    private static TmdbItem item(int id, String mediaType, String title) {
        return new TmdbItem(id, mediaType, title, "", "", "", "");
    }
}
