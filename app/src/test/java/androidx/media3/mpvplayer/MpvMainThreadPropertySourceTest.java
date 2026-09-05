package androidx.media3.mpvplayer;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvMainThreadPropertySourceTest {

    @Test
    public void nativeCacheFallbackIsFreshnessGated() throws Exception {
        String source = readMpvPlayer();
        String refresh = methodBody(source, "private void refreshCacheState()", "private void validateEarlyEndFile()");

        assertTrue("native cache fallback must run only after observer freshness checks",
                refresh.contains("cacheObserverState.shouldQueryFallback")
                        && refresh.contains("cacheObserverState.needsFallback")
                        && refresh.contains("cacheObserverState.onFallbackQuery(nowMs)"));
        assertTrue("upstream cache fallback must remain available when nested MPV observers do not report",
                source.contains("MPVLib.getProperty"));
    }

    @Test
    public void publicCacheSnapshotNeverQueriesNativeProperties() throws Exception {
        String source = readMpvPlayer();
        String snapshot = methodBody(source, "public PlayerCacheState getCacheState()", "/** Observer-only cache snapshot");

        assertTrue("public cache diagnostics must use the observer-backed snapshot",
                snapshot.contains("return buildCacheState()"));
        assertFalse("UI diagnostics must never synchronously refresh native MPV properties",
                snapshot.contains("refreshCacheState()"));
    }
    @Test
    public void inactiveHlsPreloadSnapshotSkipsCacheDirectoryScan() throws Exception {
        String source = readMpvHlsProxy();
        String snapshot = methodBody(
                source,
                "PreloadRuntimeSnapshot preloadRuntimeSnapshot(long nowElapsedMs)",
                "@Override");
        int stats = snapshot.indexOf("SessionStats stats");
        int inactiveGuard = snapshot.indexOf("stats == null || !stats.vod");
        int lightweightReturn = snapshot.indexOf("return new PreloadRuntimeSnapshot");
        int capacityScan = snapshot.indexOf("cacheCoordinator.preloadSnapshot");

        assertTrue("preload snapshot must inspect the active proxy session first", stats >= 0);
        assertTrue("non-HLS and non-VOD sessions must be guarded before cache probing",
                inactiveGuard > stats);
        assertTrue("the inactive snapshot must return before scanning the cache directory",
                lightweightReturn > inactiveGuard && lightweightReturn < capacityScan);
    }

    @Test
    public void dynamicTrackAndChapterPropertiesAreObserved() throws Exception {
        String source = readMpvPlayer();

        assertTrue(source.contains("observeTrackProperties("));
        assertTrue(source.contains("observeChapterProperties("));
        assertTrue(source.contains("propertyCache.put(property, value)"));
    }

    @Test
    public void diagnosticsUseObservedCacheAndReportPendingRuntime() throws Exception {
        String source = readMpvPlayer();
        String render = methodBody(source, "public String getRenderDiagnostics()", "public String getRuntimeDiagnostics()");
        String runtime = methodBody(source, "public String getRuntimeDiagnostics()", "public long getDroppedFrames()");

        assertTrue(render.contains("等待上报"));
        assertFalse(render.contains("config.vo()"));
        assertTrue(runtime.contains("等待上报"));
        assertFalse(runtime.contains("config.hwdec()"));
    }

    @Test
    public void periodicStateRefreshUsesObservedCache() throws Exception {
        String source = readMpvPlayer();
        String refresh = methodBody(source, "private void refreshPlaybackState()", "private void applyPausedForCache");
        String position = methodBody(source, "private long positionMs()", "private long durationMs()");
        String duration = methodBody(source, "private long durationMs()", "private long bufferedPositionMs");
        String validation = methodBody(source, "private void validateEarlyEndFile()", "private boolean isFailedLoadedMedia()");

        assertFalse(refresh.contains("Property("));
        assertFalse(refresh.contains("refreshCacheState("));
        assertFalse(position.contains("doubleProperty("));
        assertFalse(duration.contains("doubleProperty("));
        assertFalse(validation.contains("booleanProperty("));
    }

    private static String readMpvPlayer() throws IOException {
        return readSource("MpvPlayer.java");
    }

    private static String readMpvHlsProxy() throws IOException {
        return readSource("MpvHlsProxy.java");
    }

    private static String readSource(String name) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(Path.of("app", "src", "main", "java", "androidx", "media3", "mpvplayer", name));
        if (!Files.exists(source)) source = root.resolve(Path.of("src", "main", "java", "androidx", "media3", "mpvplayer", name));
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
