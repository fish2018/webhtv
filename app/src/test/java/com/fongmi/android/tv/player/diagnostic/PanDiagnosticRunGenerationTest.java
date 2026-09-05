package com.fongmi.android.tv.player.diagnostic;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PanDiagnosticRunGenerationTest {

    @Test
    public void invalidatedRunCannotDeliverQueuedCallback() {
        PanDiagnosticRunGeneration generation = new PanDiagnosticRunGeneration();
        long first = generation.next();
        AtomicInteger callbacks = new AtomicInteger();

        generation.invalidate();

        assertFalse(generation.runIfCurrent(first, callbacks::incrementAndGet));
        assertTrue(callbacks.get() == 0);
    }

    @Test
    public void onlyLatestRunCanDeliverCallbacks() {
        PanDiagnosticRunGeneration generation = new PanDiagnosticRunGeneration();
        long first = generation.next();
        long second = generation.next();
        AtomicInteger callbacks = new AtomicInteger();

        assertFalse(generation.runIfCurrent(first, callbacks::incrementAndGet));
        assertTrue(generation.runIfCurrent(second, callbacks::incrementAndGet));
        assertTrue(callbacks.get() == 1);
    }
}
