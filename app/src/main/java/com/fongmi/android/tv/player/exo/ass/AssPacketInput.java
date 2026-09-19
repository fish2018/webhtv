package com.fongmi.android.tv.player.exo.ass;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, selected-stream bridge for MatroskaExtractor's existing SSA sample format. */
final class AssPacketInput {
    static final byte[] FORMAT = ("Format: Start, End, ReadOrder, Layer, Style, Name, "
            + "MarginL, MarginR, MarginV, Effect, Text").getBytes(StandardCharsets.UTF_8);
    private static final byte[] PREFIX = "Dialogue: 0:00:00:00,".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern TIME = Pattern.compile("(\\d{1,10}):(\\d{2}):(\\d{2})[:.](\\d{2})");
    private final List<Packet> packets = new ArrayList<>();
    private final Set<Integer> readOrders = new HashSet<>();
    private int bytes;

    record Packet(byte[] data, long startMs, long durationMs) { }

    static boolean matches(List<byte[]> initializationData) {
        return initializationData.size() == 2 && Arrays.equals(FORMAT, initializationData.get(0))
                && initializationData.get(1).length > 0;
    }

    // Called under the session lock. A seek retains packets already received; libass ReadOrder
    // semantics remove duplicate preroll. A new stream gets a new input, never the previous cache.
    boolean add(ByteBuffer input, long sampleTimeUs, long streamOffsetUs) {
        byte[] sample = AssInput.copy(input);
        if (sample.length <= PREFIX.length) throw new IllegalArgumentException("ssa-packet-prefix");
        for (int i = 0; i < PREFIX.length; i++)
            if (sample[i] != PREFIX[i]) throw new IllegalArgumentException("ssa-packet-prefix");
        int end = tokenEnd(sample, PREFIX.length, PREFIX.length + 24);
        long durationMs = durationMs(new String(sample, PREFIX.length, end - PREFIX.length, StandardCharsets.US_ASCII));
        int start = end + 1;
        int orderEnd = tokenEnd(sample, start, start + 11);
        if (orderEnd == start) throw new IllegalArgumentException("ssa-read-order");
        for (int i = start; i < orderEnd; i++)
            if (sample[i] < '0' || sample[i] > '9') throw new IllegalArgumentException("ssa-read-order");
        int readOrder = Integer.parseInt(new String(sample, start, orderEnd - start, StandardCharsets.US_ASCII));
        if (readOrders.contains(readOrder)) return false;
        int separators = 0;
        for (int i = start; i < sample.length && separators < 8; i++) if (sample[i] == ',') separators++;
        if (separators < 8) throw new IllegalArgumentException("ssa-packet-fields");
        int length = sample.length - start;
        if (packets.size() >= AssInput.MAX_EVENTS || length > AssInput.MAX_UTF8_BYTES - bytes)
            throw new IllegalArgumentException("ssa-packet-budget");
        long timeMs = AssInput.timeMs(sampleTimeUs, streamOffsetUs, 0);
        Math.addExact(timeMs, durationMs);
        packets.add(new Packet(Arrays.copyOfRange(sample, start, sample.length), timeMs, durationMs));
        readOrders.add(readOrder);
        bytes += length;
        return true;
    }

    private static int tokenEnd(byte[] data, int start, int limit) {
        for (int i = start; i < Math.min(data.length, limit); i++) if (data[i] == ',') return i;
        throw new IllegalArgumentException("ssa-packet-fields");
    }

    private static long durationMs(String value) {
        Matcher time = TIME.matcher(value);
        if (!time.matches()) throw new IllegalArgumentException("ssa-packet-duration");
        long hours = Long.parseLong(time.group(1));
        int minutes = Integer.parseInt(time.group(2)), seconds = Integer.parseInt(time.group(3));
        if (minutes >= 60 || seconds >= 60) throw new IllegalArgumentException("ssa-packet-duration");
        // This is the current Media3 duration, already quantized to centiseconds. Do not claim
        // that stripping its prefix recovers the original Matroska blockDurationUs precision.
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1000L + Integer.parseInt(time.group(4)) * 10L;
    }

    int size() { return packets.size(); }
    int bytes() { return bytes; }

    List<Packet> after(int cursor) {
        return cursor == packets.size() ? List.of() : List.copyOf(packets.subList(cursor, packets.size()));
    }
}
