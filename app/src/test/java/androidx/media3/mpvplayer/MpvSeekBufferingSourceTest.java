package androidx.media3.mpvplayer;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A seek stops mpv playback until MPV_EVENT_PLAYBACK_RESTART. Reporting READY across that
 * gap makes the UI hide its progress indicator over a frozen frame, so the window has to be
 * opened on every seek entrance and closed on every exit. These are wiring invariants that a
 * unit test over {@link MpvPlaybackState} cannot reach.
 */
public class MpvSeekBufferingSourceTest {

    @Test
    public void requestedSeekOpensTheBufferingWindow() throws Exception {
        String seek = methodBody(
                readMpvPlayer(),
                "protected ListenableFuture<?> handleSeek(",
                "protected ListenableFuture<?> handleSetPlaybackParameters(");

        assertTrue("handleSeek must resolve its state through the shared policy",
                seek.contains("MpvPlaybackState.resolveAfterSeekRequest("));
        assertTrue("a requested seek must open the buffering window",
                seek.contains("beginSeekBuffering("));
        assertFalse("the ENDED-only special case must not survive alongside the policy",
                seek.contains("if (playbackState == Player.STATE_ENDED) playbackState = Player.STATE_BUFFERING;"));
    }

    @Test
    public void nativeSeekEventIsHandledAsTheSecondEntrance() throws Exception {
        String source = readMpvPlayer();

        assertTrue("MPV_EVENT_SEEK must be handled, not just declared in MPVLib",
                source.contains("case MPVLib.MpvEvent.MPV_EVENT_SEEK ->"));

        String handler = methodBody(source,
                "case MPVLib.MpvEvent.MPV_EVENT_SEEK ->",
                "case MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART ->");

        assertTrue("a native seek must open the same window", handler.contains("beginSeekBuffering("));
        assertTrue("a teardown must not be reanimated by a late seek event", handler.contains("!stopping"));
        assertTrue("a cache stall must keep owning its own BUFFERING", handler.contains("seekBufferingActive"));
    }

    @Test
    public void everyExitClosesTheWindow() throws Exception {
        String source = readMpvPlayer();

        assertTrue("PLAYBACK_RESTART is the primary exit",
                source.contains("endSeekBuffering(\"playback-restart\")"));
        assertTrue("the paused-for-cache observer is the secondary exit",
                source.contains("endSeekBuffering(\"paused-for-cache\")"));
        assertTrue("a new load supersedes an open window",
                source.contains("endSeekBuffering(\"start-file\")"));
        assertTrue("terminal states must release the window",
                source.contains("endSeekBuffering(\"ended\")")
                        && source.contains("endSeekBuffering(\"stop\")")
                        && source.contains("endSeekBuffering(\"fail\")"));
    }

    @Test
    public void openWindowCannotLatchForever() throws Exception {
        String source = readMpvPlayer();
        String timeout = methodBody(source,
                "private void timeOutSeekBuffering()",
                "private void loadCurrentUri()");

        assertTrue("the window must carry its own deadline",
                source.contains("mainHandler.postDelayed(seekBufferingTimeoutRunnable"));
        assertTrue("a swallowed seek must fall back to READY rather than spin forever",
                timeout.contains("playbackState = Player.STATE_READY"));
        assertTrue("the fallback must not resurrect a released player", timeout.contains("released"));
        assertTrue("the fallback must not fight a teardown", timeout.contains("stopping"));
        // The deadline alone must not decide. If mpv is still waiting on its cache the BUFFERING
        // is honest, and publishing READY would both hide the indicator over a frozen frame and
        // disarm the stall watchdog, which checkBufferingStall() cancels on READY.
        int probe = timeout.indexOf("nativeBooleanProperty(\"paused-for-cache\"");
        int publish = timeout.indexOf("playbackState = Player.STATE_READY");
        assertTrue("the fallback must consult mpv before overriding the state", probe >= 0);
        assertTrue("the cache probe must gate the READY publish", probe < publish);
        assertTrue("the timeout must be cancelled on release",
                source.contains("mainHandler.removeCallbacks(seekBufferingTimeoutRunnable)"));
    }

    @Test
    public void consecutiveSeeksRestartTheDeadline() throws Exception {
        String begin = methodBody(readMpvPlayer(),
                "private void beginSeekBuffering(",
                "private void endSeekBuffering(");

        assertTrue("a re-arm must drop the previous deadline first",
                begin.indexOf("mainHandler.removeCallbacks(seekBufferingTimeoutRunnable)")
                        < begin.indexOf("mainHandler.postDelayed(seekBufferingTimeoutRunnable"));
    }

    private static String readMpvPlayer() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(Path.of("app", "src", "main", "java", "androidx", "media3", "mpvplayer", "MpvPlayer.java"));
        if (!Files.exists(source)) source = root.resolve(Path.of("src", "main", "java", "androidx", "media3", "mpvplayer", "MpvPlayer.java"));
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
