package com.fongmi.android.tv.setting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InterfaceOrderStoreTest {

    @Test
    public void normalizeRemovesBlankAndDuplicateUrlsInOrder() {
        assertEquals(
                Arrays.asList("a", "b", "c"),
                InterfaceOrderStore.normalize(Arrays.asList("a", "b", "a", "", "c", "b")));
    }

    @Test
    public void normalizePreservesEmptyListContract() {
        assertTrue(InterfaceOrderStore.normalize(new ArrayList<>()).isEmpty());
    }

    @Test
    public void sortUsesSavedOrderThenAppendsNewUrls() {
        assertEquals(
                Arrays.asList("c", "a", "b"),
                InterfaceOrderStore.sortUrls(
                        Arrays.asList("a", "b", "c"),
                        Arrays.asList("c", "deleted", "a")));
    }

    @Test
    public void sortDeduplicatesAvailableAndSavedUrls() {
        assertEquals(
                Arrays.asList("b", "a"),
                InterfaceOrderStore.sortUrls(
                        Arrays.asList("a", "b", "a"),
                        Arrays.asList("b", "b", "missing")));
    }
}
