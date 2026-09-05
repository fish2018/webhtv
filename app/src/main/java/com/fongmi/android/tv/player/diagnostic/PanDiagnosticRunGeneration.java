package com.fongmi.android.tv.player.diagnostic;

import java.util.concurrent.atomic.AtomicLong;

final class PanDiagnosticRunGeneration {

    private final AtomicLong value = new AtomicLong();

    long next() {
        return value.incrementAndGet();
    }

    void invalidate() {
        value.incrementAndGet();
    }

    boolean runIfCurrent(long expected, Runnable action) {
        if (value.get() != expected) return false;
        action.run();
        return true;
    }
}
