package androidx.media3.mpvplayer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MpvOptionPriorityPolicy {

    private static final Set<String> PERFORMANCE_MANAGED_OPTIONS = Set.of(
            "vo",
            "gpu-context",
            "gpu-api",
            "opengl-es",
            "android-vulkan-aimagereader-backend",
            "hwdec",
            "hwdec-codecs",
            "ao",
            "audio-spdif",
            "cache",
            "cache-secs",
            "cache-pause",
            "cache-pause-initial",
            "cache-pause-wait",
            "demuxer-thread",
            "demuxer-seekable-cache",
            "demuxer-max-bytes",
            "demuxer-max-back-bytes",
            "demuxer-readahead-secs",
            "demuxer-hysteresis-secs",
            "demuxer-dovi-profile7",
            "framedrop",
            "video-sync",
            "interpolation",
            "hls-bitrate",
            "vd-lavc-fast",
            "vd-lavc-threads",
            "vd-lavc-skiploopfilter");

    private MpvOptionPriorityPolicy() {
    }

    static Map<String, String> resolvePerformanceOverlay(MpvPlayerConfig config) {
        if (!MpvStartupBufferPolicy.shouldApplyPerformanceOverlay(config.performanceOptionsPriority())) return Collections.emptyMap();
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("vo", config.vo());
        candidates.put("gpu-context", config.gpuContext());
        if (config.gpuApi() != null && !config.gpuApi().isEmpty()) candidates.put("gpu-api", config.gpuApi());
        if (config.openglEs()) candidates.put("opengl-es", "yes");
        candidates.put("hwdec", config.hwdec());
        candidates.put("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1");
        candidates.put("ao", config.ao());
        candidates.put("audio-spdif", config.audioSpdif());
        candidates.put("cache", config.cache() ? "yes" : "no");
        candidates.put("cache-secs", String.valueOf(config.cacheSeconds()));
        candidates.put("cache-pause", MpvStartupBufferPolicy.CACHE_PAUSE);
        candidates.put("cache-pause-initial", MpvStartupBufferPolicy.CACHE_PAUSE_INITIAL);
        candidates.put("cache-pause-wait", String.format(Locale.US, "%.3f", config.rebufferMs() / 1000.0));
        candidates.put("demuxer-thread", "yes");
        candidates.put("demuxer-seekable-cache", "auto");
        candidates.put("demuxer-max-bytes", String.valueOf(config.demuxerMaxBytes()));
        candidates.put("demuxer-max-back-bytes", String.valueOf(config.demuxerMaxBackBytes()));
        candidates.put("demuxer-readahead-secs", String.valueOf(config.demuxerReadaheadSeconds()));
        candidates.put("demuxer-hysteresis-secs", String.valueOf(config.demuxerHysteresisSeconds()));
        candidates.putAll(config.extraOptions());
        return selectPerformanceOverlay(true, candidates);
    }

    static Map<String, String> selectPerformanceOverlay(boolean performanceOptionsPriority, Map<String, String> candidates) {
        if (!performanceOptionsPriority || candidates == null || candidates.isEmpty()) return Collections.emptyMap();
        Map<String, String> overlay = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : candidates.entrySet()) {
            if (isPerformanceManaged(entry.getKey()) && entry.getValue() != null) overlay.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(overlay);
    }

    static boolean isPerformanceManaged(String name) {
        return name != null && PERFORMANCE_MANAGED_OPTIONS.contains(name);
    }

    static String priorityName(boolean performanceOptionsPriority) {
        return performanceOptionsPriority ? "performance" : "mpv.conf";
    }

    static String resolveVideoOutput(boolean performanceOptionsPriority,
                                     String appVideoOutput,
                                     String configuredVideoOutput) {
        if (!performanceOptionsPriority
                && configuredVideoOutput != null
                && !configuredVideoOutput.isEmpty()) {
            return configuredVideoOutput;
        }
        return appVideoOutput;
    }
}
