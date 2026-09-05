package com.fongmi.android.tv.player;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvStabilityGuardSourceTest {

    @Test
    public void runtimeAutoEvaluationAppliesStabilityGuardBeforeTransition() throws Exception {
        String method = methodBody(readPlayerManager(), "private boolean evaluateMpvAutoOutput()", "private boolean hasRequestedSubtitle");

        assertTrue(method.contains("boolean effectiveEligible = MpvPerformanceSetting.isAutoSurfaceDirectEnabled() && decision.eligible();"));
        assertTrue(method.contains("MpvAutoOutputPolicy.transition(effectiveEligible, currentlyDirect)"));
    }

    @Test
    public void stickyAutoDirectRequiresStabilityGuardPermission() throws Exception {
        String method = methodBody(readPlayerManager(), "private void prepareMpvOutputForNewItem()", "private void resetMpvOutputRuntime()");

        assertTrue(method.contains("automaticOutput && MpvPerformanceSetting.isAutoSurfaceDirectEnabled()"));
    }

    @Test
    public void engineOverrideCannotBypassZeroCopyDeviceGuard() throws Exception {
        String method = methodBody(readMpvPlayerEngine(), "private MpvPlayerConfig buildConfig()", "private void applySoftDecodeOptions");

        assertTrue(method.contains("surfaceDirectOverride && decode == HARD && !zeroCopyBlocked"));
        assertTrue(method.contains("String hwdec = surfaceDirect ? \"mediacodec\""));
        assertTrue(method.contains(".hwdec(hwdec)"));
    }

    @Test
    public void hardDecodePrimesSoftwareFallbackTuning() throws Exception {
        String method = methodBody(
                readMpvPlayerEngine(),
                "private void applySoftDecodeOptions",
                "private String resolveAudioSpdifCodecs");

        assertFalse("mpv can silently fall back from hardware to software decoding",
                method.contains("decode != SOFT"));
        assertTrue(method.contains("vd-lavc-fast"));
        assertTrue(method.contains("vd-lavc-threads"));
        assertTrue(method.contains("vd-lavc-skiploopfilter"));
    }

    @Test
    public void postInitSafetyOverrideCannotBeReplacedByMpvConfig() throws Exception {
        String source = readMpvPlayer();
        String postInit = methodBody(source, "private void applyPostInitOptions()", "private void applyHardwareSafetyOptions()");
        String safety = methodBody(source, "private void applyHardwareSafetyOptions()", "private int applyPerformanceOptionOverlay()");

        assertTrue(postInit.contains("applyHardwareSafetyOptions()"));
        assertTrue(safety.contains("MpvPerformanceSetting.isZeroCopyBlocked()"));
        assertTrue(safety.contains("if (!\"no\".equals(config.hwdec())) setRuntimeString(\"hwdec\", \"mediacodec-copy\")"));
        assertTrue(safety.contains("setRuntimeString(\"vo\", config.vo())"));
    }

    private static String readMpvPlayer() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(Path.of("app", "src", "main", "java", "androidx", "media3", "mpvplayer", "MpvPlayer.java"));
        if (!Files.exists(source)) source = root.resolve(Path.of("src", "main", "java", "androidx", "media3", "mpvplayer", "MpvPlayer.java"));
        return Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
    private static String readMpvPlayerEngine() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "player", "engine", "MpvPlayerEngine.java"));
        if (!Files.exists(source)) source = root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "player", "engine", "MpvPlayerEngine.java"));
        return Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
    private static String readPlayerManager() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "player", "PlayerManager.java"));
        if (!Files.exists(source)) source = root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "player", "PlayerManager.java"));
        return Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        assertTrue("Missing source token: " + startToken, start >= 0);
        assertTrue("Missing source token after " + startToken + ": " + endToken, end > start);
        return source.substring(start, end);
    }
}
