package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.DefaultRenderersFactory;

import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.PlayerSetting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExoUtilTest {

    @Test
    public void getRenderMode_keepsPlatformRendererFirstForHardDecode() {
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF, ExoUtil.getRenderMode(PlayerEngine.HARD));
    }

    @Test
    public void getRenderMode_prefersExtensionRendererForSoftDecode() {
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, ExoUtil.getRenderMode(PlayerEngine.SOFT));
    }

    @Test
    public void getFfmpegVideoRenderMode_keepsFfmpegAsFallbackForHardDecode() {
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON, ExoUtil.getFfmpegVideoRenderMode(ExoUtil.getRenderMode(PlayerEngine.HARD)));
    }

    @Test
    public void getFfmpegVideoRenderMode_prefersFfmpegForSoftDecode() {
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, ExoUtil.getFfmpegVideoRenderMode(ExoUtil.getRenderMode(PlayerEngine.SOFT)));
    }

    @Test
    public void hardDecodeFactory_keepsFfmpegVideoFallbackWired() throws Exception {
        String source = readMainSource("player/exo/ExoUtil.java");
        int start = source.indexOf("private static class FfmpegRenderersFactory");
        int end = source.indexOf("private static class FfmpegFallbackRenderersFactory", start);
        String factory = source.substring(start, end);

        assertFalse(factory.contains("if (videoRenderMode == EXTENSION_RENDERER_MODE_OFF) return;"));
        assertTrue(factory.contains("out.add(index, buildFfmpegVideoRenderer("));
    }

    @Test
    public void isFfmpegVideoFallbackOnly_yieldsToPlatformForHardDecode() {
        assertTrue(ExoUtil.isFfmpegVideoFallbackOnly(ExoUtil.getRenderMode(PlayerEngine.HARD), false));
    }

    @Test
    public void isFfmpegVideoFallbackOnly_honoursVideoSoftPrefer() {
        assertFalse(ExoUtil.isFfmpegVideoFallbackOnly(ExoUtil.getRenderMode(PlayerEngine.HARD), true));
    }

    @Test
    public void isFfmpegVideoFallbackOnly_letsFfmpegCompeteForSoftDecode() {
        assertFalse(ExoUtil.isFfmpegVideoFallbackOnly(ExoUtil.getRenderMode(PlayerEngine.SOFT), false));
    }

    @Test
    public void ffmpegVideoRenderers_useCompatRendererSoPlatformKeepsHighSpecTracks() throws Exception {
        String source = readMainSource("player/exo/ExoUtil.java");

        assertFalse(source.contains("new FfmpegVideoRenderer("));
        assertTrue(source.contains("new CompatFfmpegVideoRenderer("));
        assertTrue(source.contains("isFfmpegVideoFallbackOnly(videoRenderMode, videoPrefer)"));
    }

    @Test
    public void ffmpegVideoRenderers_shareTheSelectorTheirMediaCodecRenderersUse() throws Exception {
        String source = readMainSource("player/exo/ExoUtil.java");
        int start = source.indexOf("private CompatFfmpegVideoRenderer buildFfmpegVideoRenderer(");
        int end = source.indexOf("private MediaCodecSelector getVideoCodecSelector(", start);
        assertTrue(start >= 0 && end > start);
        String builder = source.substring(start, end);

        assertTrue(source.contains("buildFfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, videoCodecSelector)"));
        assertTrue(source.contains("isFfmpegVideoFallbackOnly(videoRenderMode, videoPrefer), mediaCodecSelector)"));
        assertTrue(builder.contains("fallbackOnly, platformDecoderSelector)"));
        assertFalse(builder.contains("MediaCodecSelector.DEFAULT"));
    }

    @Test
    public void automaticConstraintReasonLabel_avoidsUnsupportedAndroidStringBuilderApi() throws Exception {
        String source = readMainSource("player/exo/ExoAutomaticVideoConstraintPolicy.java");

        assertFalse(source.contains("builder.isEmpty()"));
    }

    @Test
    public void videoLimits_keepAdaptiveSelectionInsideTheConstraints() throws Exception {
        String source = readMainSource("player/exo/ExoUtil.java");
        int start = source.indexOf("private static void applyVideoLimit(");
        int end = source.indexOf("public static EnhancedVideoProfile getEnhancedVideoProfile()", start);
        assertTrue(start >= 0 && end > start);
        String limit = source.substring(start, end);

        assertTrue("受约束轨道必须保留 Media3 自适应选轨，否则低码率 H.264 可能固定到过高分辨率",
                limit.contains("builder.setForceHighestSupportedBitrate(false)"));
        assertFalse(limit.contains("builder.setForceHighestSupportedBitrate(true)"));
    }

    @Test
    public void automaticConstraintController_downgradesOnThroughputShortfall() throws Exception {
        String source = readMainSource("player/exo/ExoUtil.java");
        int start = source.indexOf("private static class AutomaticVideoConstraintController");
        int end = source.indexOf("private static class LegacyAdaptiveVideoProfileController", start);
        assertTrue(start >= 0 && end > start);
        String controller = source.substring(start, end);

        // 固定选最高画质后原生 ABR 不再兜底吞吐，这个自动档控制器必须自己按带宽和重缓冲降档，
        // 否则弱网会锁在最高档一直重缓冲且无法恢复。
        assertTrue("自动档必须监听带宽估算",
                controller.contains("public void onBandwidthEstimate("));
        assertTrue("带宽不足要走吞吐降档",
                controller.contains("ExoAdaptiveVideoBitratePolicy.shouldDowngrade(selectedBitrate, bitrateEstimate)")
                        && controller.contains("ExoAutomaticVideoConstraintPolicy.Fault.THROUGHPUT"));
        // appliedLimit 是设备能力上限（4K 档可达 20Mbps），拿它当轨道码率比对会让正常片源持续误降档。
        assertTrue("轨道码率未知时必须放弃带宽判断，而不是退回设备上限",
                controller.contains("if (selectedBitrate <= 0) return;")
                        && !controller.contains("selectedBitrate = appliedLimit.maxVideoBitrate()"));
        assertTrue("起播后再次进入缓冲同样是吞吐不足的证据",
                controller.contains("state == Player.STATE_BUFFERING && everReady")
                        && controller.contains("\"rebuffer\""));
        assertTrue("换片要重置起播标记，避免上一片的状态误触发降档",
                controller.contains("everReady = false;"));
        // bindSession 才会重置 everReady，若先读 everReady 再对齐会话，换片后的首次缓冲会被
        // 当成重缓冲而白降一档。
        int stateChanged = controller.indexOf("public void onPlaybackStateChanged(");
        int bindFirst = controller.indexOf("bindEventSession();", stateChanged);
        int readsEverReady = controller.indexOf(
                "boolean rebuffered = state == Player.STATE_BUFFERING && everReady;", stateChanged);
        assertTrue("重缓冲判断前必须先对齐会话",
                stateChanged >= 0 && bindFirst > stateChanged && readsEverReady > bindFirst);
    }

    @Test
    public void ffmpegRendererPolicy_usesFullNextLibRenderersInNextLibMode() {
        assertTrue(ExoUtil.useFfmpegAudioFallback(PlayerSetting.FFMPEG_MODE_NEXTLIB));
        assertTrue(ExoUtil.useFfmpegVideoRenderer(PlayerSetting.FFMPEG_MODE_NEXTLIB));
    }

    @Test
    public void ffmpegRendererPolicy_usesAudioAndVideoFallbackInSimpleMode() {
        assertTrue(ExoUtil.useFfmpegAudioFallback(PlayerSetting.FFMPEG_MODE_SIMPLE));
        assertTrue(ExoUtil.useFfmpegVideoRenderer(PlayerSetting.FFMPEG_MODE_SIMPLE));
    }

    @Test
    public void ffmpegRendererPolicy_disablesNextLibInOfficialMode() {
        assertFalse(ExoUtil.useFfmpegAudioFallback(PlayerSetting.FFMPEG_MODE_OFFICIAL));
        assertFalse(ExoUtil.useFfmpegVideoRenderer(PlayerSetting.FFMPEG_MODE_OFFICIAL));
    }

    private static String readMainSource(String relative) throws Exception {
        Path path = Path.of("app/src/main/java/com/fongmi/android/tv", relative);
        if (!Files.exists(path)) path = Path.of("src/main/java/com/fongmi/android/tv", relative);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
