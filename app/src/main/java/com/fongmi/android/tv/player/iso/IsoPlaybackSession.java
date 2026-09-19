package com.fongmi.android.tv.player.iso;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.fongmi.android.tv.setting.PlayerSetting;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

final class IsoPlaybackSession {

    private static final int DISC_SIGNATURE_OFFSET = 16 * 2048;
    private static final int DISC_SIGNATURE_LENGTH = 64 * 1024;

    private final AtomicBoolean closed = new AtomicBoolean();
    private final CopyOnWriteArrayList<Runnable> metadataListeners = new CopyOnWriteArrayList<>();
    private final RemoteIsoSource source;
    private final long id;
    private volatile IsoTrackMetadataResolver.Snapshot trackMetadata = IsoTrackMetadataResolver.Snapshot.EMPTY;
    private volatile boolean trackMetadataReady;
    private boolean metadataPreparing;
    private int demandReaders;
    private long demandStartedNs;

    IsoPlaybackSession(long id, String url, Map<String, String> headers) {
        this.id = id;
        HttpRangeIsoSource remote = new HttpRangeIsoSource(url, headers);
        IsoDiskPageStore disk = createDiskStore();
        this.source = disk == null ? new IsoPageCache(remote) : new ProgressiveIsoPageCache(remote, disk);
    }

    private static IsoDiskPageStore createDiskStore() {
        if (!PlayerSetting.isBlurayMenu()) return null;
        try {
            return new IsoDiskPageStore(Path.cache("mpv_hls"), PlayerSetting.getPlayCacheSize(PlayerSetting.MPV));
        } catch (RuntimeException error) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("iso-cache", "disk-init fallback=%s", error.getClass().getSimpleName());
            return null;
        }
    }

    long id() {
        return id;
    }

    long length() throws IOException {
        ensureOpen();
        return source.length();
    }

    int readAt(long offset, ByteBuffer target, int length) throws IOException {
        ensureOpen();
        int wanted = Math.min(length, target.remaining());
        byte[] data = new byte[wanted];
        synchronized (this) {
            if (demandReaders++ == 0) demandStartedNs = System.nanoTime();
        }
        try {
            int read = source.readAt(offset, data, 0, wanted);
            if (read > 0) target.put(data, 0, read);
            return read;
        } finally {
            synchronized (this) {
                if (--demandReaders == 0) demandStartedNs = 0;
            }
        }
    }

    synchronized long demandWaitMs() {
        return demandReaders == 0 ? 0 : Math.max(0, (System.nanoTime() - demandStartedNs) / 1_000_000);
    }

    boolean hasDiscImageSignature() throws IOException {
        ensureOpen();
        byte[] data = new byte[DISC_SIGNATURE_LENGTH];
        int read = source.readAt(DISC_SIGNATURE_OFFSET, data, 0, data.length);
        return contains(data, read, "CD001") || contains(data, read, "NSR02") || contains(data, read, "NSR03");
    }

    synchronized void prepareTrackMetadata(int playlist, Executor executor) {
        if (closed.get() || playlist < 0 || trackMetadataReady || metadataPreparing) return;
        metadataPreparing = true;
        executor.execute(() -> {
            long startMs = System.currentTimeMillis();
            IsoTrackMetadataResolver.Snapshot resolved = IsoTrackMetadataResolver.Snapshot.EMPTY;
            try {
                resolved = IsoTrackMetadataResolver.resolve(source, playlist);
                SpiderDebug.log("iso-track", "metadata ready id=%d playlist=%05d audio=%d subtitle=%d elapsedMs=%d", id, playlist, resolved.audioCount(), resolved.subtitleCount(), System.currentTimeMillis() - startMs);
            } catch (Throwable e) {
                SpiderDebug.log("iso-track", "metadata failed id=%d playlist=%05d error=%s elapsedMs=%d", id, playlist, e.getMessage(), System.currentTimeMillis() - startMs);
            }
            trackMetadata = resolved;
            trackMetadataReady = true;
            synchronized (IsoPlaybackSession.this) {
                metadataPreparing = false;
            }
            for (Runnable listener : metadataListeners) {
                try {
                    listener.run();
                } catch (Throwable ignored) {
                }
            }
            metadataListeners.clear();
        });
    }

    String trackLanguage(int type, int demuxId, int ordinal) {
        return trackMetadata.language(type, demuxId, ordinal);
    }

    void addTrackMetadataListener(Runnable listener) {
        if (listener == null) return;
        if (trackMetadataReady) {
            listener.run();
            return;
        }
        metadataListeners.add(listener);
        if (trackMetadataReady && metadataListeners.remove(listener)) listener.run();
    }

    void removeTrackMetadataListener(Runnable listener) {
        metadataListeners.remove(listener);
    }

    private static boolean contains(byte[] data, int length, String value) {
        if (length <= 0) return false;
        byte[] needle = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 0; i <= length - needle.length; i++) {
            int j = 0;
            while (j < needle.length && data[i + j] == needle[j]) j++;
            if (j == needle.length) return true;
        }
        return false;
    }

    void close() {
        if (closed.compareAndSet(false, true)) {
            metadataListeners.clear();
            source.close();
        }
    }

    private void ensureOpen() throws IsoSourceException {
        if (closed.get()) throw new IsoSourceException(IsoSourceException.Reason.CLOSED, "ISO session closed");
    }
}
