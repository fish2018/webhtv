package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PlaybackPerformanceUiPolicyTest {

    @Test
    public void everyKernelSeparatesProfileCommonAndAdvancedWithoutLoss() {
        for (int kernel : new int[]{
                PlayerSetting.EXO, PlayerSetting.MPV, PlayerSetting.IJK}) {
            List<PlaybackPerformanceOption> all =
                    PlaybackPerformanceCatalog.forKernel(kernel, false);
            PlaybackPerformanceUiPolicy.Split split =
                    PlaybackPerformanceUiPolicy.splitForKernel(
                            kernel, false);

            assertNotNull(split.profile());
            assertEquals(PlaybackPerformanceCatalog.PROFILE,
                    split.profile().id());
            Set<String> ids = new HashSet<>();
            ids.add(split.profile().id());
            split.common().forEach(option -> assertTrue(ids.add(option.id())));
            split.advanced().forEach(option -> assertTrue(ids.add(option.id())));
            assertEquals(all.size(), ids.size());
            assertFalse(split.common().isEmpty());
            assertFalse(split.advanced().isEmpty());
        }
    }

    @Test
    public void commonRowsContainOnlyHighFrequencyControls() {
        assertEquals(Set.of(
                        PlaybackPerformanceCatalog.RENDER,
                        PlaybackPerformanceCatalog.TRACK_LIMIT,
                        PlaybackPerformanceCatalog.ADAPTIVE_DOWNGRADE,
                        PlaybackPerformanceCatalog.TUNNEL,
                        PlaybackPerformanceCatalog.EXO_FRAME_RATE,
                        PlaybackPerformanceCatalog.AUDIO_PASSTHROUGH),
                ids(PlaybackPerformanceUiPolicy
                        .splitForKernel(PlayerSetting.EXO, false).common()));
        assertEquals(Set.of(
                        PlaybackPerformanceCatalog.MPV_OUTPUT,
                        PlaybackPerformanceCatalog.MPV_RENDER,
                        PlaybackPerformanceCatalog.MPV_VULKAN_BACKEND,
                        PlaybackPerformanceCatalog.MPV_HWDEC,
                        PlaybackPerformanceCatalog.MPV_FRAME_RATE,
                        PlaybackPerformanceCatalog.MPV_OPTION_PRIORITY,
                        PlaybackPerformanceCatalog.AUDIO_PASSTHROUGH),
                ids(PlaybackPerformanceUiPolicy
                        .splitForKernel(PlayerSetting.MPV, false).common()));
        assertEquals(Set.of(
                        PlaybackPerformanceCatalog.IJK_SCENE,
                        PlaybackPerformanceCatalog.IJK_BUFFER,
                        PlaybackPerformanceCatalog.IJK_FRAME_DROP,
                        PlaybackPerformanceCatalog.IJK_RTSP_TRANSPORT,
                        PlaybackPerformanceCatalog.IJK_RECONNECT),
                ids(PlaybackPerformanceUiPolicy
                        .splitForKernel(PlayerSetting.IJK, false).common()));
    }

    @Test
    public void vulkanBackendRowOnlyAppearsAfterAppSelectsVulkan() {
        assertFalse(ids(PlaybackPerformanceUiPolicy.splitForKernel(
                PlayerSetting.MPV, false, false).common()).contains(
                PlaybackPerformanceCatalog.MPV_VULKAN_BACKEND));
        assertTrue(ids(PlaybackPerformanceUiPolicy.splitForKernel(
                PlayerSetting.MPV, false, true).common()).contains(
                PlaybackPerformanceCatalog.MPV_VULKAN_BACKEND));
    }

    @Test
    public void helpDescriptionsCoverEveryParameter() {
        for (int kernel : new int[]{
                PlayerSetting.EXO, PlayerSetting.MPV, PlayerSetting.IJK}) {
            for (PlaybackPerformanceOption option
                    : PlaybackPerformanceCatalog.forKernel(kernel, false)) {
                assertFalse(option.title().isBlank());
                assertFalse(option.section().isBlank());
                assertTrue(option.description().length() >= 40);
            }
        }
    }

    @Test
    public void dv7FallbackControlIsAvailableForExoAndMpv() {
        assertTrue(ids(PlaybackPerformanceCatalog.forKernel(
                PlayerSetting.EXO, false)).contains(
                PlaybackPerformanceCatalog.DV7_HDR10_FALLBACK));
        assertTrue(ids(PlaybackPerformanceCatalog.forKernel(
                PlayerSetting.MPV, false)).contains(
                PlaybackPerformanceCatalog.DV7_HDR10_FALLBACK));
        assertFalse(ids(PlaybackPerformanceCatalog.forKernel(
                PlayerSetting.IJK, false)).contains(
                PlaybackPerformanceCatalog.DV7_HDR10_FALLBACK));
    }

    private static Set<String> ids(
            List<PlaybackPerformanceOption> options) {
        Set<String> ids = new HashSet<>();
        options.forEach(option -> ids.add(option.id()));
        return ids;
    }
}
