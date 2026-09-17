package com.fongmi.android.tv.ui.adapter;

import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;

import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FlagAdapterTest {

    @Test
    public void getActivated_emptyItems_returnsEmptyFlag() {
        FlagAdapter adapter = new FlagAdapter(item -> {
        });

        Flag activated = adapter.getActivated();

        assertNotNull(activated);
        assertTrue(activated.getEpisodes().isEmpty());
    }

    @Test
    public void setSelected_emptyItems_doesNothing() {
        FlagAdapter adapter = new FlagAdapter(item -> {
        });

        adapter.setSelected(new Flag("missing"));

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void setSelected_duplicateNames_selectsExactFlagOnly() {
        Flag first = new Flag("duplicate");
        Flag second = new Flag("duplicate");
        FlagAdapter adapter = new FlagAdapter(item -> {
        });
        adapter.getItems().addAll(Arrays.asList(first, second));

        adapter.setSelected(second);

        assertFalse(first.isSelected());
        assertTrue(second.isSelected());
        assertEquals(1, adapter.getPosition());
        assertEquals(1, adapter.indexOf(second));
        assertSame(second, adapter.getActivated());
    }

    @Test
    public void toggle_updatesEpisodeSelectionAcrossFlags() {
        Flag selectedFlag = new Flag("drive-a");
        Episode firstEpisode = episode("1", "a-1");
        Episode secondEpisode = episode("2", "a-2");
        selectedFlag.getEpisodes().addAll(Arrays.asList(firstEpisode, secondEpisode));

        Flag otherFlag = new Flag("drive-b");
        Episode otherEpisode = episode("1", "b-1");
        otherEpisode.setSelected(true);
        otherFlag.getEpisodes().add(otherEpisode);

        FlagAdapter adapter = new FlagAdapter(item -> {
        });
        adapter.getItems().addAll(Arrays.asList(selectedFlag, otherFlag));
        adapter.setSelected(selectedFlag);

        adapter.toggle(secondEpisode);

        assertFalse(firstEpisode.isSelected());
        assertTrue(secondEpisode.isSelected());
        assertFalse(otherEpisode.isSelected());
    }

    @Test
    public void toggle_doesNotNotifyFlagListDuringSelection() {
        Flag selectedFlag = new Flag("drive-a");
        Episode episode = episode("1", "a-1");
        selectedFlag.getEpisodes().add(episode);

        FlagAdapter adapter = new FlagAdapter(item -> {
        });
        adapter.getItems().add(selectedFlag);
        adapter.setSelected(selectedFlag);

        AtomicInteger notifications = new AtomicInteger();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                notifications.incrementAndGet();
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                notifications.incrementAndGet();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                notifications.incrementAndGet();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                notifications.incrementAndGet();
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                notifications.incrementAndGet();
            }
        });

        adapter.toggle(episode);

        assertEquals("FlagAdapter.toggle(Episode) must not notify while the flag RecyclerView may be laying out", 0, notifications.get());
    }

    private Episode episode(String name, String url) {
        return Episode.create(name, url);
    }

}
