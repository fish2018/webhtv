package com.fongmi.android.tv.web;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeWebMediaLifecycleTest {

    @Test
    public void controllerKeepsBackgroundAudioAndSilencesExplicitHtmlHandoffs() throws Exception {
        String source = readMainSource("HomeWebController.java");
        String onPause = methodBody(source, "public void onPause()", "public boolean beginInlineEvaluation()");
        String endInlineEvaluation = methodBody(source, "public void endInlineEvaluation(boolean active)", "public void destroy()");
        String prepareNativePlayback = methodBody(source, "public void prepareNativePlayback(Runnable launch)", "public void destroy()");

        assertFalse("Background pause must not silence HTML media",
                onPause.contains("pausePageMedia();"));
        assertTrue("Inline resolver cleanup must pause HTML media before pausing WebView",
                ordered(endInlineEvaluation, "pausePageMedia();", "webView.onPause();"));
        assertTrue("Native playback handoff must pause HTML media before launch",
                prepareNativePlayback.contains("PAUSE_PAGE_MEDIA_SCRIPT"));
        assertTrue("Page media cleanup must cover video and audio elements",
                source.contains("video,audio") && source.contains("media.pause()"));
    }

    @Test
    public void nativePlaybackRoutesPauseWebHomeBeforeLaunch() throws Exception {
        String source = readMainSource("HomeWebBridge.java");
        assertNativeRoutePausesMedia(source, "private String playUrl(JsonObject payload)", "private String playVod(JsonObject payload)");
        assertNativeRoutePausesMedia(source, "private String playVod(JsonObject payload)", "private String playVodInline(JsonObject payload)");
        assertNativeRoutePausesMedia(source, "private String playVodInline(JsonObject payload)", "private String preloadArtwork(JsonObject payload)");
        assertNativeRoutePausesMedia(source, "private String playPan(JsonObject payload)", "private String stripPush(String url)");
    }

    @Test
    public void nativePlaybackLaunchStateRejectsDestroyedAndDuplicateCallbacks() {
        HomeWebController.NativePlaybackLaunchState state = new HomeWebController.NativePlaybackLaunchState();
        HomeWebController.NativePlaybackLaunchState cancelled = new HomeWebController.NativePlaybackLaunchState();

        assertTrue(state.tryLaunch(false, false, false));
        assertFalse(state.tryLaunch(false, false, false));
        cancelled.cancel();
        assertFalse(cancelled.tryLaunch(false, false, false));
        assertFalse(new HomeWebController.NativePlaybackLaunchState().tryLaunch(true, false, false));
        assertFalse(new HomeWebController.NativePlaybackLaunchState().tryLaunch(false, true, false));
        assertFalse(new HomeWebController.NativePlaybackLaunchState().tryLaunch(false, false, true));
    }

    @Test
    public void controllerDestroyCancelsPendingNativePlaybackBeforeWebViewDestroy() throws Exception {
        String source = readMainSource("HomeWebController.java");
        String destroy = methodBody(source, "public void destroy()", "private void consumeExtensionReload()");

        assertTrue(ordered(destroy, "destroyed = true;", "cancelPendingNativePlaybacks();"));
        assertTrue(ordered(destroy, "cancelPendingNativePlaybacks();", "webView.destroy();"));
    }

    @Test
    public void remoteRequestGateKeepsUntrustedWorkOutOfTheSharedQueue() {
        HomeWebController.RemoteRequestGate gate = new HomeWebController.RemoteRequestGate(2);

        assertTrue(gate.tryAcquire());
        assertTrue(gate.tryAcquire());
        assertFalse(gate.tryAcquire());
        gate.release();
        assertTrue(gate.tryAcquire());
        gate.release();
        gate.release();
        gate.release();
        assertEquals(0, gate.inFlight());
    }

    @Test
    public void remoteActionGateThrottlesSideEffectsWithoutBlockingReadsAndResetsPerSession() {
        HomeWebController.RemoteActionGate gate = new HomeWebController.RemoteActionGate(400);

        assertTrue(gate.tryAcquire("vod.home", 1_000));
        assertTrue(gate.tryAcquire("theme.info", 1_001));
        assertTrue(gate.tryAcquire("favorite.set", 1_000));
        assertFalse(gate.tryAcquire("navigation.reload", 1_399));
        assertTrue(gate.tryAcquire("player.playVod", 1_400));
        gate.reset();
        assertTrue(gate.tryAcquire("navigation.openDetail", 1_400));
    }

    @Test
    public void diagnosticUrlsRemoveCredentialsQueriesAndFragments() {
        assertEquals("https://example.com:8443/path/file",
                HomeWebController.safeLogUrl("https://user:password@example.com:8443/path/file?token=secret#part"));
        assertEquals("https://[2001:db8::1]/page",
                HomeWebController.safeLogUrl("https://[2001:db8::1]/page?token=secret"));
        assertEquals("custom:<redacted>", HomeWebController.safeLogUrl("custom:secret-payload?token=value"));
    }

    @Test
    public void bridgeDiagnosticsDoNotPrintRawPayloadsOrPlaybackUrls() throws Exception {
        String bridge = readMainSource("HomeWebBridge.java");

        assertFalse(bridge.contains("invoke method=%s payload=%s"));
        assertFalse(bridge.contains("player.playUrl title=%s url=%s\", playTitle, playUrl"));
        assertTrue(bridge.contains("HomeWebController.safeLogUrl(playUrl)"));
    }

    private static void assertNativeRoutePausesMedia(String source, String start, String end) {
        String body = methodBody(source, start, end);
        assertTrue(start + " must pause WebHome media before VideoActivity launch",
                ordered(body, "controller.prepareNativePlayback", "VideoActivity.start"));
    }

    private static boolean ordered(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second, Math.max(0, firstIndex + first.length()));
        return firstIndex >= 0 && secondIndex > firstIndex;
    }

    private static String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue("Missing source token: " + startToken, start >= 0);
        assertTrue("Missing source token after " + startToken + ": " + endToken, end > start);
        return source.substring(start, end);
    }

    private static String readMainSource(String fileName) throws Exception {
        Path root = Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "web");
        if (!Files.exists(root)) root = Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "web");
        return Files.readString(root.resolve(fileName), StandardCharsets.UTF_8);
    }
}
