package com.fongmi.android.tv.player.cache;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackDiskBufferStoreTest {

    private final PlaybackDiskBufferStore store = PlaybackDiskBufferStore.process();

    @Before
    public void setUp() {
        store.clear();
    }

    @After
    public void tearDown() {
        store.clear();
    }

    @Test
    public void completedOverlappingRangesMerge() {
        store.recordCompleted("movie", 10_000, 20_000);
        store.recordCompleted("movie", 18_000, 30_000);

        assertEquals(30_000, store.contiguousEnd("movie", 10_000, 0));
    }

    @Test
    public void onlySmallKnownBoundaryGapIsBridged() {
        store.recordCompleted("movie", 10_000, 20_000);
        store.recordCompleted("movie", 22_000, 30_000);
        store.recordCompleted("movie", 40_000, 50_000);

        assertEquals(20_000, store.contiguousEnd("movie", 10_000, 1_999));
        assertEquals(30_000, store.contiguousEnd("movie", 10_000, 2_000));
        assertEquals(30_000, store.contiguousEnd("movie", 10_000, 9_999));
        assertEquals(50_000, store.contiguousEnd("movie", 10_000, 10_000));
    }

    @Test
    public void rangeContainingPlayerBufferExtendsFromThatPoint() {
        store.recordCompleted("movie", 10_000, 30_000);

        assertEquals(30_000, store.contiguousEnd("movie", 15_000, 0));
    }

    @Test
    public void effectiveEndUsesDiskRangeAndHonorsDuration() {
        store.recordCompleted("movie", 10_000, 30_000);

        assertEquals(30_000, store.effectiveEnd("movie", 15_000, 60_000, 0));
        assertEquals(20_000, store.effectiveEnd("movie", 15_000, 20_000, 0));
    }

    @Test
    public void resetDropsStaleSessionRanges() {
        store.recordCompleted("movie", 10_000, 30_000);
        store.reset("movie");

        assertEquals(10_000, store.contiguousEnd("movie", 10_000, 2_000));
    }
}
