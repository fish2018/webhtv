package com.fongmi.android.tv.player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PlaybackRouteRegistry {

    private static final ConcurrentHashMap<Integer, Entry> APP_PORTS = new ConcurrentHashMap<>();
    private static final AtomicLong TOKENS = new AtomicLong();

    private PlaybackRouteRegistry() {
    }

    public static Registration registerAppService(int port, AppOwner owner) {
        if (port <= 0 || port > 65535 || owner == null) return Registration.EMPTY;
        Entry entry = new Entry(TOKENS.incrementAndGet(), owner);
        APP_PORTS.put(port, entry);
        return new Registration(port, entry);
    }

    static AppOwner findAppOwner(int port) {
        Entry entry = APP_PORTS.get(port);
        return entry == null ? null : entry.owner();
    }

    public enum AppOwner {
        MAIN_SERVER("app-main-server"),
        HLS_PROXY("app-hls-proxy"),
        MULTI_THREAD_PROXY("app-multi-thread-proxy");

        private final String label;

        AppOwner(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final class Registration implements AutoCloseable {

        private static final Registration EMPTY = new Registration(-1, null);
        private final int port;
        private final Entry entry;

        private Registration(int port, Entry entry) {
            this.port = port;
            this.entry = entry;
        }

        @Override
        public void close() {
            if (entry != null) APP_PORTS.remove(port, entry);
        }
    }

    private record Entry(long token, AppOwner owner) {
    }
}
