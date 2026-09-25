package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteGroupOrderStoreTest {

    @Test
    public void orderKeepsDefaultWhenNoSavedOrderExists() {
        assertEquals(
                List.of("注入", "首页", "直播", "影视", "4K"),
                SiteGroupOrderStore.order(
                        List.of("注入", "首页", "直播", "影视", "4K"),
                        List.of()));
    }

    @Test
    public void orderAppliesSavedGroupsAndAppendsUnknownGroupsInDefaultOrder() {
        assertEquals(
                List.of("4K", "首页", "影视", "注入", "直播", "新组"),
                SiteGroupOrderStore.order(
                        List.of("注入", "首页", "直播", "影视", "4K", "新组"),
                        List.of("4K", "首页", "影视")));
    }

    @Test
    public void orderIgnoresStaleBlankAndDuplicateSavedGroups() {
        assertEquals(
                List.of("影视", "首页", "4K"),
                SiteGroupOrderStore.order(
                        List.of("首页", "影视", "4K", "影视", " "),
                        List.of("失效分组", "影视", "", "影视", "首页")));
    }

    @Test
    public void mergeVisibleOrderPreservesHiddenGroupSlots() {
        assertEquals(
                List.of("4K", "注入", "直播", "首页", "影视"),
                SiteGroupOrderStore.mergeVisibleOrder(
                        List.of("注入", "首页", "直播", "影视", "4K"),
                        List.of("4K", "注入", "首页", "影视")));
    }

    @Test
    public void mergeVisibleOrderKeepsFullOrderWhenVisibleListIsEmpty() {
        assertEquals(
                List.of("注入", "首页", "直播"),
                SiteGroupOrderStore.mergeVisibleOrder(
                        List.of("注入", "首页", "直播"),
                        List.of()));
    }

    @Test
    public void moveChangesOnePositionAtATime() {
        List<String> groups = new ArrayList<>(List.of("A", "B", "C"));

        assertTrue(SiteGroupOrderStore.move(groups, "B", -1));
        assertEquals(List.of("B", "A", "C"), groups);
        assertTrue(SiteGroupOrderStore.move(groups, "B", 1));
        assertEquals(List.of("A", "B", "C"), groups);
    }

    @Test
    public void moveRejectsBoundariesAndMissingGroups() {
        List<String> groups = new ArrayList<>(List.of("A", "B", "C"));

        assertFalse(SiteGroupOrderStore.move(groups, "A", -1));
        assertFalse(SiteGroupOrderStore.move(groups, "C", 1));
        assertFalse(SiteGroupOrderStore.move(groups, "missing", 1));
        assertFalse(SiteGroupOrderStore.move(groups, "B", 0));
        assertFalse(SiteGroupOrderStore.move(groups, "B", 2));
        assertEquals(List.of("A", "B", "C"), groups);
    }
}
