package androidx.media3.mpvplayer;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvOptionPriorityPolicyTest {

    @Test
    public void performancePriorityOverlaysOnlyExplicitlyManagedOptions() {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("cache-pause-wait", "2.000");
        candidates.put("video-sync", "audio");
        candidates.put("android-vulkan-aimagereader-backend", "legacy");
        candidates.put("sub-font", "User Font");
        candidates.put("glsl-shaders", "/storage/user-shader.glsl");

        Map<String, String> overlay = MpvOptionPriorityPolicy.selectPerformanceOverlay(true, candidates);

        assertEquals(3, overlay.size());
        assertEquals("2.000", overlay.get("cache-pause-wait"));
        assertEquals("audio", overlay.get("video-sync"));
        assertEquals("legacy", overlay.get(
                "android-vulkan-aimagereader-backend"));
        assertFalse(overlay.containsKey("sub-font"));
        assertFalse(overlay.containsKey("glsl-shaders"));
    }

    @Test
    public void configPriorityLeavesAllSameNameOptionsToMpvConf() {
        Map<String, String> candidates = Map.of(
                "hwdec", "mediacodec-copy",
                "demuxer-max-bytes", "67108864",
                "framedrop", "vo");

        assertTrue(MpvOptionPriorityPolicy.selectPerformanceOverlay(false, candidates).isEmpty());
        assertEquals("mpv.conf", MpvOptionPriorityPolicy.priorityName(false));
    }

    @Test
    public void felRequestPreservesOrdinaryVideoAndConfigPriority() {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("android-dovi-fel", "yes");
        candidates.put("android-dovi-fel-vulkan", "yes");
        candidates.put("vo", "gpu-next");
        candidates.put("demuxer-dovi-profile7", "preserve");
        candidates.put("android-dolby-vision-output", "configured");
        candidates.put("vd-lavc-skipframe", "default");
        candidates.put("vd-lavc-skipidct", "default");
        candidates.put("audio-spdif", "ac3,eac3");
        candidates.put("cache-secs", "30");
        candidates.put("glsl-shaders", "user.glsl");
        candidates.put("android-vulkan-aimagereader-backend", "stable");

        Map<String, String> overlay = MpvOptionPriorityPolicy.selectPerformanceOverlay(false, candidates);
        assertEquals(3, overlay.size());
        assertEquals("yes", overlay.get("android-dovi-fel"));
        assertEquals("yes", overlay.get("android-dovi-fel-vulkan"));
        assertEquals("preserve", overlay.get("demuxer-dovi-profile7"));
        assertFalse(overlay.containsKey("vo"));
        assertFalse(overlay.containsKey("android-dolby-vision-output"));
        assertFalse(overlay.containsKey("vd-lavc-skipframe"));
        assertFalse(overlay.containsKey("vd-lavc-skipidct"));
        assertFalse(overlay.containsKey("audio-spdif"));
        assertFalse(overlay.containsKey("cache-secs"));
        assertFalse(overlay.containsKey("glsl-shaders"));
        assertFalse(overlay.containsKey("android-vulkan-aimagereader-backend"));
    }

    @Test
    public void disablingFelRestoresNormalConfigPriority() {
        assertTrue(MpvOptionPriorityPolicy.selectPerformanceOverlay(false, Map.of(
                "android-dovi-fel", "no", "vo", "gpu", "demuxer-dovi-profile7", "p81")).isEmpty());
    }

    @Test
    public void currentPerformanceCatalogIsExplicitlyManaged() {
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("vo"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged(
                "android-vulkan-aimagereader-backend"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("hwdec"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("cache-pause-initial"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("cache-pause-wait"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("demuxer-max-bytes"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("demuxer-hysteresis-secs"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("framedrop"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("video-sync"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("interpolation"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("hls-bitrate"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("vd-lavc-fast"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("vd-lavc-threads"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("vd-lavc-skiploopfilter"));
        assertFalse(MpvOptionPriorityPolicy.isPerformanceManaged("sub-font"));
        assertFalse(MpvOptionPriorityPolicy.isPerformanceManaged("glsl-shaders"));
        assertEquals("performance", MpvOptionPriorityPolicy.priorityName(true));
    }

    @Test
    public void nullCandidateValuesAreNeverApplied() {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("hwdec", null);
        candidates.put("cache", "yes");

        Map<String, String> overlay = MpvOptionPriorityPolicy.selectPerformanceOverlay(true, candidates);

        assertEquals(Map.of("cache", "yes"), overlay);
    }

    @Test
    public void videoOutputFollowsSelectedPriority() {
        assertEquals("gpu", MpvOptionPriorityPolicy.resolveVideoOutput(
                true, "gpu", "gpu-next"));
        assertEquals("gpu-next", MpvOptionPriorityPolicy.resolveVideoOutput(
                false, "gpu", "gpu-next"));
        assertEquals("gpu", MpvOptionPriorityPolicy.resolveVideoOutput(
                false, "gpu", ""));
    }
}
