package androidx.media3.mpvplayer;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class MpvDtsHdFallbackPolicy {

    private static final String DTS = "dts";
    private static final String DTS_HD = "dts-hd";
    private static final String DTS_HD_FORMAT = "spdif-dtshd";

    private MpvDtsHdFallbackPolicy() {
    }

    /** Check the log/configuration before asking native code for any audio state. */
    static boolean shouldInspectAudioState(String configuredCodecs, String logLine,
                                           boolean attempted) {
        return !attempted && isAudioTrackInitFailure(logLine)
                && containsCodec(configuredCodecs, DTS_HD);
    }

    static Decision evaluate(String configuredCodecs, String audioFormat,
                             String codecProfile, String logLine,
                             boolean attempted) {
        if (attempted) return Decision.skip("already-attempted");
        if (!containsCodec(configuredCodecs, DTS_HD)) {
            return Decision.skip("dts-hd-not-configured");
        }
        if (!isDtsHd(audioFormat, codecProfile)) {
            return Decision.skip("not-dts-hd-passthrough");
        }
        if (!isAudioTrackInitFailure(logLine)) {
            return Decision.skip("not-audiotrack-init-failure");
        }
        return new Decision(true, downgradeToDtsCore(configuredCodecs),
                "dts-hd-audiotrack-init-failed");
    }

    static String downgradeToDtsCore(String configuredCodecs) {
        return DTS;
    }

    private static boolean isDtsHd(String audioFormat, String codecProfile) {
        if (DTS_HD_FORMAT.equals(normalize(audioFormat))) return true;
        String profile = normalize(codecProfile).replace('_', '-');
        return profile.contains("dts-hd") || profile.contains("dts hd")
                || profile.contains("dts:x") || profile.contains("dtsx");
    }

    private static boolean containsCodec(String configuredCodecs, String target) {
        return splitCodecs(configuredCodecs).contains(target);
    }

    private static Set<String> splitCodecs(String value) {
        Set<String> codecs = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return codecs;
        for (String codec : value.split(",")) {
            String normalized = normalize(codec);
            if (!normalized.isEmpty()) codecs.add(normalized);
        }
        return codecs;
    }

    private static boolean isAudioTrackInitFailure(String line) {
        String normalized = normalize(line);
        return normalized.contains("audiotrack init failed")
                || normalized.contains("audiotrack.getstate failed");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    record Decision(boolean retry, String codecs, String reason) {

        static Decision skip(String reason) {
            return new Decision(false, "", reason);
        }
    }
}
