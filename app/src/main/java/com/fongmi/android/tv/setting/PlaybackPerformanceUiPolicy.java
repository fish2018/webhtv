package com.fongmi.android.tv.setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure presentation policy for playback settings. */
public final class PlaybackPerformanceUiPolicy {

    private static final Set<String> EXO_COMMON = Set.of(
            PlaybackPerformanceCatalog.RENDER,
            PlaybackPerformanceCatalog.TRACK_LIMIT,
            PlaybackPerformanceCatalog.ADAPTIVE_DOWNGRADE,
            PlaybackPerformanceCatalog.TUNNEL,
            PlaybackPerformanceCatalog.EXO_FRAME_RATE,
            PlaybackPerformanceCatalog.AUDIO_PASSTHROUGH);
    private static final Set<String> MPV_COMMON = Set.of(
            PlaybackPerformanceCatalog.MPV_OUTPUT,
            PlaybackPerformanceCatalog.MPV_RENDER,
            PlaybackPerformanceCatalog.MPV_VULKAN_BACKEND,
            PlaybackPerformanceCatalog.MPV_HWDEC,
            PlaybackPerformanceCatalog.MPV_FRAME_RATE,
            PlaybackPerformanceCatalog.MPV_OPTION_PRIORITY,
            PlaybackPerformanceCatalog.AUDIO_PASSTHROUGH);
    private static final Set<String> IJK_COMMON = Set.of(
            PlaybackPerformanceCatalog.IJK_SCENE,
            PlaybackPerformanceCatalog.IJK_BUFFER,
            PlaybackPerformanceCatalog.IJK_FRAME_DROP,
            PlaybackPerformanceCatalog.IJK_RTSP_TRANSPORT,
            PlaybackPerformanceCatalog.IJK_RECONNECT);

    private PlaybackPerformanceUiPolicy() {
    }

    public static Split splitForKernel(int kernel) {
        return splitForKernel(
                kernel,
                PlaybackPerformanceSetting.isRecommendedMerged(),
                PlayerSetting.getMpvRender()
                        == PlayerSetting.MPV_RENDER_VULKAN);
    }

    static Split splitForKernel(
            int kernel,
            boolean recommendedMerged) {
        return splitForKernel(kernel, recommendedMerged, true);
    }

    static Split splitForKernel(
            int kernel,
            boolean recommendedMerged,
            boolean mpvVulkanSelected) {
        Set<String> commonIds = commonIds(kernel);
        List<PlaybackPerformanceOption> common = new ArrayList<>();
        List<PlaybackPerformanceOption> advanced = new ArrayList<>();
        PlaybackPerformanceOption profile = null;
        for (PlaybackPerformanceOption option
                : PlaybackPerformanceCatalog.forKernel(
                        kernel, recommendedMerged)) {
            if (kernel == PlayerSetting.MPV
                    && PlaybackPerformanceCatalog.MPV_VULKAN_BACKEND
                    .equals(option.id())
                    && !mpvVulkanSelected) {
                continue;
            }
            if (PlaybackPerformanceCatalog.PROFILE.equals(option.id())) {
                profile = option;
            } else if (commonIds.contains(option.id())) {
                common.add(option);
            } else {
                advanced.add(option);
            }
        }
        return new Split(profile, common, advanced);
    }

    private static Set<String> commonIds(int kernel) {
        return switch (kernel) {
            case PlayerSetting.MPV -> MPV_COMMON;
            case PlayerSetting.IJK -> IJK_COMMON;
            default -> EXO_COMMON;
        };
    }

    public record Split(
            PlaybackPerformanceOption profile,
            List<PlaybackPerformanceOption> common,
            List<PlaybackPerformanceOption> advanced) {

        public Split {
            common = common == null ? List.of() : List.copyOf(common);
            advanced = advanced == null ? List.of() : List.copyOf(advanced);
        }
    }
}
