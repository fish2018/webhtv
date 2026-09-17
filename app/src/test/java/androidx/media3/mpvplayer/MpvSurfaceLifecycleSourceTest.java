package androidx.media3.mpvplayer;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvSurfaceLifecycleSourceTest {

    @Test
    public void sameSurfaceResizeUpdatesSizeWithoutDetachingNativeSurface() throws Exception {
        String method = methodBody(readMpvPlayer(), "private void bindVideoOutput()", "private void clearVideoOutput()");
        int fastPathStart = method.indexOf("if (sameVideoSurface)");
        int replacementPathStart = method.indexOf(
                "if (surfaceAttached || osdSurfaceAttached) detachMpvSurface();",
                fastPathStart);

        assertTrue("Missing same-video-surface fast path", fastPathStart >= 0);
        assertTrue("Missing replacement surface detach path", replacementPathStart > fastPathStart);

        String fastPath = method.substring(fastPathStart, replacementPathStart);
        assertTrue("Same-surface resize must refresh the holder dimensions", fastPath.contains("updateSurfaceSize(surfaceHolder)"));
        assertTrue("Same video surface must reconcile the independently managed OSD surface", fastPath.contains("reconcileOsdSurface()"));
        assertFalse("Same-surface resize must not race native VO teardown", fastPath.contains("detachMpvSurface()"));
    }

    @Test
    public void contextShutdownTimeoutKeepsOldContextMarkedAsDestroying() throws Exception {
        String source = readMpvLib();
        String playerSource = readMpvPlayer();
        String initializeContext = methodBody(
                source,
                "public static synchronized void initializeCreatedContext()",
                "public static synchronized boolean tryCreate(Context appctx)");
        String tryCreate = methodBody(
                source,
                "public static synchronized boolean tryCreate(Context appctx)",
                "private static boolean awaitContextShutdown()");
        String awaitShutdown = methodBody(
                source,
                "private static boolean awaitContextShutdown()",
                "public static synchronized void destroyCreatedContext()");
        String ensureInitialized = methodBody(
                playerSource,
                "private void ensureInitialized()",
                "private void applyPreInitOptions()");
        String initWrapper = methodBody(
                playerSource,
                "private void mpvInit()",
                "private void mpvDestroyCreatedContext()");

        assertTrue("Creation must stop while the previous native context is still shutting down",
                tryCreate.contains("if (!awaitContextShutdown()) return false;"));
        assertTrue("A completed shutdown wait must allow creation",
                awaitShutdown.contains("if (!contextDestroying) return true;"));
        assertTrue("A timed-out shutdown wait must reject creation",
                awaitShutdown.contains("return false;"));
        assertFalse("A timeout must not pretend the native context finished shutting down",
                awaitShutdown.contains("contextDestroying = false;"));
        assertTrue("A failed native create call must allow a later retry",
                tryCreate.contains("catch (RuntimeException | Error error)"));
        assertTrue("A failed native create call must clear the attempt guard",
                tryCreate.contains("contextCreationAttempted = false;"));
        assertTrue("A failed native init call must reset Java lifecycle state",
                initializeContext.contains("catch (RuntimeException error)"));
        assertTrue("A failed native init call must clear the created flag",
                initializeContext.contains("contextCreated = false;"));
        assertTrue("A failed native init call must clear the attempt guard",
                initializeContext.contains("contextCreationAttempted = false;"));
        assertTrue("MpvPlayer initialization must use the instrumented init wrapper",
                ensureInitialized.contains("mpvInit();"));
        assertTrue("The init wrapper must call the lifecycle-safe MPVLib initializer",
                initWrapper.contains("MPVLib.initializeCreatedContext();"));
    }

    private static String readMpvPlayer() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(Path.of("app", "src", "main", "java", "androidx", "media3", "mpvplayer", "MpvPlayer.java"));
        if (!Files.exists(source)) source = root.resolve(Path.of("src", "main", "java", "androidx", "media3", "mpvplayer", "MpvPlayer.java"));
        return Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String readMpvLib() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(Path.of("app", "src", "main", "java", "is", "xyz", "mpv", "MPVLib.java"));
        if (!Files.exists(source)) source = root.resolve(Path.of("src", "main", "java", "is", "xyz", "mpv", "MPVLib.java"));
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
