package androidx.media3.mpvplayer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MpvOptionPriorityPolicy {

    static final String HARDWARE_CODECS = "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1,avs3";

    // Native applies the decoder/renderer contract to the selected DV7 chain.
    // Protect only the request and original bitstream; a FEL preference must
    // not override ordinary videos' VO, decoder shortcuts, or mpv.conf priority.
    private static final Set<String> FEL_REQUIRED_OPTIONS = Set.of(
            "android-dovi-fel", "android-dovi-fel-vulkan", "demuxer-dovi-profile7");

    private static final Set<String> PERFORMANCE_MANAGED_OPTIONS = Set.of(
            "vo",
            "gpu-context",
            "gpu-api",
            "opengl-es",
            "android-vulkan-aimagereader-backend",
            "hwdec",
            "hwdec-codecs",
            "ao",
            "ad",
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
            "demuxer-dovi-profile8",
            "android-dovi-fel",
            "android-dovi-fel-vulkan",
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
        boolean fel = "yes".equals(config.extraOptions().get("android-dovi-fel"));
        if (!MpvStartupBufferPolicy.shouldApplyPerformanceOverlay(config.performanceOptionsPriority())
                && !fel) return Collections.emptyMap();
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("vo", config.vo());
        candidates.put("gpu-context", config.gpuContext());
        if (config.gpuApi() != null && !config.gpuApi().isEmpty()) candidates.put("gpu-api", config.gpuApi());
        if (config.openglEs() || fel) candidates.put("opengl-es", config.openglEs() ? "yes" : "no");
        candidates.put("hwdec", config.hwdec());
        candidates.put("hwdec-codecs", HARDWARE_CODECS);
        candidates.put("ao", config.ao());
        candidates.put("ad", MpvAudioDecoderPolicy.hardwareFirstDecoderList());
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
        return selectPerformanceOverlay(config.performanceOptionsPriority(), candidates);
    }

    static Map<String, String> selectPerformanceOverlay(boolean performanceOptionsPriority, Map<String, String> candidates) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyMap();
        boolean fel = "yes".equals(candidates.get("android-dovi-fel"));
        if (!performanceOptionsPriority && !fel) return Collections.emptyMap();
        Map<String, String> overlay = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : candidates.entrySet()) {
            if (entry.getValue() != null && (performanceOptionsPriority && isPerformanceManaged(entry.getKey())
                    || fel && FEL_REQUIRED_OPTIONS.contains(entry.getKey()))) {
                overlay.put(entry.getKey(), entry.getValue());
            }
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
