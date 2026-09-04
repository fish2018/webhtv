package androidx.media3.mpvplayer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Restricts MPV automatic cache updates to the two controller-owned byte limits. */
final class MpvAutoCacheOptionPolicy {

    private static final long MIN_FORWARD_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_CONFIGURED_BYTES = 256L * 1024L * 1024L;

    private MpvAutoCacheOptionPolicy() {
    }

    static Map<String, String> resolve(
            boolean performanceOptionsPriority,
            long forwardBytes,
            long backBytes) {
        if (!performanceOptionsPriority
                || forwardBytes < MIN_FORWARD_BYTES
                || forwardBytes > MAX_CONFIGURED_BYTES
                || backBytes < 0
                || backBytes > MAX_CONFIGURED_BYTES
                || backBytes > forwardBytes) {
            return Collections.emptyMap();
        }
        Map<String, String> options = new LinkedHashMap<>();
        options.put("demuxer-max-back-bytes", String.valueOf(backBytes));
        options.put("demuxer-max-bytes", String.valueOf(forwardBytes));
        return Collections.unmodifiableMap(options);
    }
}
