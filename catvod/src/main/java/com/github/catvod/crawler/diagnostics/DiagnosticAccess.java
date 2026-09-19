package com.github.catvod.crawler.diagnostics;

import java.net.URI;
import java.util.Set;
import java.util.function.LongSupplier;

/** Same-origin local debug controls with a bounded action rate; no pairing state. */
public final class DiagnosticAccess {
    private final LongSupplier clock;
    private long actionWindow;
    private int actions;
    public DiagnosticAccess() { this(() -> System.nanoTime() / 1_000_000); }
    public DiagnosticAccess(LongSupplier clock) { this.clock = clock; }

    public synchronized boolean allowAction() {
        long now = clock.getAsLong();
        if (actions == 0 || now < actionWindow || now - actionWindow >= 1000) {
            actionWindow = now;
            actions = 0;
        }
        if (actions >= 5) return false;
        actions++;
        return true;
    }

    public static boolean sameOrigin(String origin, String host, Set<String> allowedHosts) {
        if (host == null || !allowedHosts.contains(host.toLowerCase(java.util.Locale.ROOT))) return false;
        if (origin == null || origin.isEmpty()) return false; // Browser POST must identify its source.
        try {
            URI uri = URI.create(origin);
            return "http".equals(uri.getScheme()) && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                    && host.equalsIgnoreCase(uri.getRawAuthority());
        } catch (IllegalArgumentException ignored) { return false; }
    }
}
