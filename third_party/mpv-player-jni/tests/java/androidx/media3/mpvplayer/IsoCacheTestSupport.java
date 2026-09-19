package androidx.media3.mpvplayer;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

public final class IsoCacheTestSupport {
    public static MpvHlsCacheCoordinator coordinator(File directory, boolean available) {
        AtomicLong clock = new AtomicLong(System.currentTimeMillis());
        return new MpvHlsCacheCoordinator(directory,
                ignored -> new MpvHlsCacheCoordinator.StorageFacts(available, 8L << 30, 10L << 30),
                clock::incrementAndGet, 0, 300_000);
    }
}
