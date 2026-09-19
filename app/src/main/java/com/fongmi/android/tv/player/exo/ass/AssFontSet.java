package com.fongmi.android.tv.player.exo.ass;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Fonts belong to one foreground media source, never a process-wide font directory. */
public final class AssFontSet {
    static final int MAX_FONT_BYTES = 16 * 1024 * 1024;
    static final int MAX_TOTAL_BYTES = 32 * 1024 * 1024;
    static final int MAX_FONTS = 64;
    record Snapshot(String[] names, byte[][] data, int bytes) { }
    static final Snapshot EMPTY = new Snapshot(new String[0], new byte[0][], 0);
    private record Font(String name, byte[] data) { }
    private final Map<String, Font> fonts = new LinkedHashMap<>();
    private final Consumer<AssFontSet> changed;
    private Snapshot snapshot = EMPTY;
    private String failure = "";
    private boolean closed;

    AssFontSet(Consumer<AssFontSet> changed) { this.changed = changed; }

    synchronized boolean canRead(int size) {
        return !closed && failure.isEmpty() && size > 0 && size <= MAX_FONT_BYTES;
    }

    void add(String name, byte[] data) {
        String digest;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder key = new StringBuilder(64);
            for (byte b : hash) key.append(Character.forDigit((b >>> 4) & 15, 16))
                    .append(Character.forDigit(b & 15, 16));
            digest = key.toString();
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        synchronized (this) {
            if (closed || !failure.isEmpty() || fonts.containsKey(digest)) return;
            if (data.length == 0 || data.length > MAX_FONT_BYTES || fonts.size() >= MAX_FONTS
                    || data.length > MAX_TOTAL_BYTES - snapshot.bytes()) {
                failure = "font-budget";
            } else {
                // Ownership transfers from the extractor. Neither side mutates font bytes.
                fonts.put(digest, new Font(name == null ? "attachment" : name, data));
                String[] names = new String[fonts.size()];
                byte[][] bytes = new byte[fonts.size()][];
                int index = 0, total = 0;
                for (Font font : fonts.values()) {
                    names[index] = font.name(); bytes[index++] = font.data(); total += font.data().length;
                }
                snapshot = new Snapshot(names, bytes, total);
            }
        }
        changed.accept(this);
    }

    void reject() {
        synchronized (this) {
            if (closed || !failure.isEmpty()) return;
            failure = "font-budget";
        }
        changed.accept(this);
    }

    synchronized Snapshot snapshot() { return snapshot; }
    synchronized String failure() { return failure; }
    synchronized void close() { closed = true; fonts.clear(); snapshot = EMPTY; }
}
