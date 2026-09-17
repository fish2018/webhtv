package com.fongmi.android.tv.ui.adapter;

import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Site;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class CollectAdapterTest {

    @Test
    public void deferredSelectionResolvesBySiteKeyAfterEarlierSourceInsertion() {
        Collect selected = collect("selected");
        selected.setSelected(true);
        List<Collect> items = new ArrayList<>(List.of(collect("all"), selected, collect("later")));
        int scheduledPosition = items.indexOf(selected);
        String scheduledSiteKey = selected.getSite().getKey();

        items.add(1, collect("earlier"));

        assertNotEquals("Dynamic insertion must demonstrate why a captured position is stale", scheduledPosition, items.indexOf(selected));
        assertEquals(2, items.indexOf(selected));
        assertSame("Deferred work must still resolve the originally selected source", selected, CollectAdapter.findActivated(items, scheduledSiteKey));
    }

    @Test
    public void deferredSelectionIsDiscardedAfterActiveSourceChanges() {
        Collect selected = collect("selected");
        Collect replacement = collect("replacement");
        selected.setSelected(true);
        List<Collect> items = new ArrayList<>(List.of(collect("all"), selected, replacement));
        String scheduledSiteKey = selected.getSite().getKey();

        selected.setSelected(false);
        replacement.setSelected(true);

        assertNull("Deferred work for a stale source must not update the new selection", CollectAdapter.findActivated(items, scheduledSiteKey));
    }

    private static Collect collect(String siteKey) {
        Site site = new Site() {
            @Override
            public String getKey() {
                return siteKey;
            }
        };
        return new Collect(site, new ArrayList<>());
    }
}