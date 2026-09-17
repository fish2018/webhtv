package com.fongmi.android.tv.player;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The automatic MPV output shutter hides the direct probe frame before a GPU
 * rebuild. That frame only exists when automatic output can actually reach
 * surface direct, so while the stability guard pins automatic mode to GPU the
 * shutter would only delay the first frame while audio already plays.
 */
public class MpvOutputShutterSourceTest {

    @Test
    public void shutterOnlyClosesWhenAutomaticOutputCanReachSurfaceDirect() throws Exception {
        String method = methodBody(readPlayerManager(),
                "public boolean shouldKeepVideoShutterClosed()", "public boolean isExo()");

        assertTrue("a shutter that can never hide a probe frame must not close",
                method.contains("MpvPerformanceSetting.isAutoSurfaceDirectEnabled()"));
        assertTrue(method.contains("MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO"));
        assertTrue(method.contains("!mpvAutoOutputEvaluated"));
        // PlaybackActivity.syncShutter ORs this predicate with nativeOutputPending,
        // so a released shutter has to be visible on this side too.
        assertTrue("a probe that gave up must also open this side of the shutter",
                method.contains("!mpvAutoOutputProbeGaveUp"));
    }

    @Test
    public void newItemClosesShutterOnlyWhenTheShutterPolicyAgrees() throws Exception {
        String method = methodBody(readPlayerManager(),
                "private void prepareMpvOutputForNewItem()", "private void resetMpvOutputRuntime()");

        assertFalse("the output mode alone closed the shutter even when nothing could hide behind it",
                method.contains("if (automaticOutput) callback.onPlayerOutputPending();"));
        assertTrue("shutter must follow the single shutter policy, not the output mode alone",
                method.contains("shouldKeepVideoShutterClosed()"));
        assertTrue(method.contains("callback.onPlayerOutputPending();"));
    }

    @Test
    public void exhaustedProbeReleasesBothSidesOfTheShutter() throws Exception {
        String source = readPlayerManager();
        String method = methodBody(source,
                "private void scheduleMpvAutoOutputEvaluation()", "private boolean evaluateMpvAutoOutput()");

        int giveUp = method.indexOf("} else if (!evaluated) {");
        assertTrue("probe exhaustion branch is missing", giveUp >= 0);
        // Scope the assertions to the branch body, so moving the release out of
        // the branch fails instead of silently passing.
        int branchEnd = method.indexOf("}, MPV_AUTO_OUTPUT_PROBE_INTERVAL_MS", giveUp);
        assertTrue("probe exhaustion branch is not followed by the post delay", branchEnd > giveUp);
        String branch = method.substring(giveUp, branchEnd);

        assertTrue("an exhausted probe must not withhold the picture indefinitely",
                branch.contains("callback.onPlayerOutputReady();"));
        assertTrue("clearing nativeOutputPending alone leaves the predicate holding the shutter",
                branch.contains("mpvAutoOutputProbeGaveUp = true;"));
        assertFalse("ending the evaluation would kill automatic output for the whole item",
                branch.contains("mpvAutoOutputEvaluated = true;"));
        // onPlayerOutputReady re-enters syncShutter synchronously, which re-reads
        // shouldKeepVideoShutterClosed() — so the latch has to be set first.
        assertTrue("the latch must be set before the re-entrant callback re-reads the predicate",
                branch.indexOf("mpvAutoOutputProbeGaveUp = true;")
                        < branch.indexOf("callback.onPlayerOutputReady();"));
    }

    @Test
    public void newItemClearsTheGaveUpLatch() throws Exception {
        String method = methodBody(readPlayerManager(),
                "private void resetMpvOutputEvaluationState()", "private void scheduleMpvAutoOutputEvaluation()");

        assertTrue("a stale give-up latch would suppress the shutter on later items",
                method.contains("mpvAutoOutputProbeGaveUp = false;"));
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
