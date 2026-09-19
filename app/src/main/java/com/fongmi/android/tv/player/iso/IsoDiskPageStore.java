package com.fongmi.android.tv.player.iso;

import androidx.media3.mpvplayer.MpvHlsCacheCoordinator;

import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

/** Session-private raw ISO pages, sharing the existing MPV disk-cache budget. */
final class IsoDiskPageStore implements AutoCloseable {

    private final String prefix = "iso-" + UUID.randomUUID() + "-";
    private final File directory;
    private final long capacity;
    private final MpvHlsCacheCoordinator coordinator;
    private final MpvHlsCacheCoordinator.ClientLease lease;
    private boolean closed;

    IsoDiskPageStore(File directory, long capacity) {
        this(directory, capacity, MpvHlsCacheCoordinator.shared(directory));
    }

    IsoDiskPageStore(File directory, long capacity, MpvHlsCacheCoordinator coordinator) {
        this.directory = directory;
        this.capacity = capacity;
        this.coordinator = coordinator;
        this.lease = coordinator.registerClient(capacity, null);
    }

    synchronized byte[] read(long index, int expected) {
        if (closed) return null;
        File file = file(index);
        try {
            if (file.length() != expected) {
                discard(file);
                return null;
            }
            try (MpvHlsCacheCoordinator.ReadLease input = coordinator.openRead(file)) {
                if (input == null) return null;
                byte[] data = new byte[expected];
                int read = 0;
                while (read < expected) {
                    int count = input.read(data, read, expected - read);
                    if (count <= 0) throw new IOException("Incomplete cached ISO page");
                    read += count;
                }
                return data;
            }
        } catch (IOException | RuntimeException error) {
            discard(file);
            logFailure("read", error);
            return null;
        }
    }

    synchronized void write(long index, byte[] data, boolean speculative) {
        if (closed || data.length == 0) return;
        File file = file(index);
        MpvHlsCacheCoordinator.WriteReservation reservation = null;
        try {
            MpvHlsCacheCoordinator.ReservationDecision decision = coordinator.tryReserve(
                    file.getName(), file, data.length, capacity,
                    speculative ? MpvHlsCacheCoordinator.WriterType.PREFETCH : MpvHlsCacheCoordinator.WriterType.FOREGROUND);
            reservation = decision.reservation();
            if (reservation == null) return;
            try (FileOutputStream output = new FileOutputStream(reservation.tempFile())) {
                for (int offset = 0; offset < data.length; ) {
                    int count = Math.min(64 * 1024, data.length - offset);
                    if (!reservation.canWrite(count)) return;
                    output.write(data, offset, count);
                    if (!reservation.recordWritten(count)) return;
                    offset += count;
                }
            }
            reservation.commit("application/octet-stream");
        } catch (IOException | RuntimeException error) {
            if (reservation != null) reservation.fail(error);
            logFailure("write", error);
        } finally {
            if (reservation != null) reservation.close();
        }
    }

    private File file(long index) {
        return new File(directory, prefix + index + ".bin");
    }

    private static void discard(File file) {
        try {
            // Only a session-owned cache object and its MIME sidecar, never source data.
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            //noinspection ResultOfMethodCallIgnored
            new File(file.getParentFile(), file.getName() + ".meta").delete();
        } catch (SecurityException ignored) {
        }
    }

    private static void logFailure(String operation, Exception error) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("iso-cache", "disk-%s fallback=%s", operation, error.getClass().getSimpleName());
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        lease.close();
        try {
            File[] files = directory.listFiles(file -> file.isFile() && file.getName().startsWith(prefix));
            if (files != null) for (File file : files) discard(file);
        } catch (SecurityException ignored) {
        }
    }
}
