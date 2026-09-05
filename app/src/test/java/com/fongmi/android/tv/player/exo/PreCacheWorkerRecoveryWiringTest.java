package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreCacheWorkerRecoveryWiringTest {

    @Test
    public void workerFailureReleasesResourcesBeforePostingStateCleanup() throws Exception {
        String method = methodBody(readPreCache(), "private void onWorkerRuntimeFailure", "private void disablePreCacheAfterWorkerFailure");
        int release = method.indexOf("releaseFailedWorkerResources(failedThread, failedHelper, failedExecutor, error)");
        int stateCleanup = method.indexOf("target.post(() -> disablePreCacheAfterWorkerFailure");

        assertTrue("Worker resources must be released on the failed looper", release >= 0);
        assertTrue("Resource release must finish before application-thread state cleanup", stateCleanup > release);
    }

    @Test
    public void androidAdapterQueuesHelperReleaseAndDrainsFailedLooper() throws Exception {
        String method = methodBody(readPreCache(), "private PreCacheWorkerRecovery.Result releaseFailedWorkerResources", "private void disablePreCacheAfterWorkerFailure");

        assertTrue(method.contains("failedHelper.release(false);"));
        assertTrue(method.contains("target.quitSafely();"));
        assertTrue(method.contains("Looper.loop();"));
        assertTrue(method.contains("recoverWorkerResources(queue, failedExecutor"));
    }

    @Test
    public void androidAdapterDrainsReleaseQueuedByConcurrentStopAfterHelperWasCleared() throws Exception {
        String method = methodBody(readPreCache(), "private PreCacheWorkerRecovery.Result releaseFailedWorkerResources", "private void disablePreCacheAfterWorkerFailure");
        String preconditions = method.substring(0, method.indexOf("PreCacheWorkerRecovery.Queue queue"));

        assertFalse("A cleared helper may already have queued its release", preconditions.contains("failedHelper == null"));
        assertTrue("Only enqueue a second release when the helper is still available", method.contains("if (failedHelper != null) failedHelper.release(false);"));
    }

    @Test
    public void workerFailureUsesResourcesOwnedByTheFailedWorker() throws Exception {
        String source = readPreCache();
        String getWorker = methodBody(source, "private HandlerThread getWorker", "private void onWorkerRuntimeFailure");
        String onFailure = methodBody(source, "private void onWorkerRuntimeFailure", "private PreCacheWorkerRecovery.Result releaseFailedWorkerResources");

        assertTrue("Each worker needs an isolated resource context", getWorker.contains("new WorkerResources(created)"));
        assertTrue("The exception handler must capture its worker context", getWorker.contains("onWorkerRuntimeFailure(resources, thread, error)"));
        assertTrue("Recovery must use the failed worker's helper", onFailure.contains("failedResources.helper"));
        assertTrue("Recovery must use the failed worker's executor", onFailure.contains("failedResources.executor"));
        assertFalse("Recovery must not snapshot a replacement session's helper", onFailure.contains("PreCacheHelper failedHelper = helper"));
        assertFalse("Recovery must not snapshot a replacement session's executor", onFailure.contains("ThreadPoolExecutor failedExecutor = executor"));
    }

    @Test
    public void workerFailureIsLoggedEvenAfterApplicationHandlerWasCleared() throws Exception {
        String method = methodBody(readPreCache(), "private void onWorkerRuntimeFailure", "private PreCacheWorkerRecovery.Result releaseFailedWorkerResources");
        int log = method.indexOf("logWorkerFailure(\"worker-failure\", error, recovery)");
        int handler = method.indexOf("Handler target = handler");

        assertTrue("Worker failure must be logged before checking application state", log >= 0 && log < handler);
    }

    @Test
    public void workerContextRejectsResourcesBoundAfterFailureBegins() throws Exception {
        String source = readPreCache();
        String createHelper = methodBody(source, "private PreCacheHelper createHelper", "private String errorDetails");
        String onFailure = methodBody(source, "private void onWorkerRuntimeFailure", "private PreCacheWorkerRecovery.Result releaseFailedWorkerResources");
        String resources = source.substring(source.indexOf("private static final class WorkerResources"), source.indexOf("private enum BufferGate"));

        assertTrue("Failure must freeze the worker resource snapshot", onFailure.contains("failedResources.markFailed();"));
        assertTrue("Late helper binding must abort the new pre-cache session", createHelper.contains("if (!resources.bindHelper(created))"));
        assertTrue("Worker resource binding must reject failed contexts", resources.contains("if (failed) return false;"));
    }

    private static String readPreCache() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "player", "exo", "PreCache.java"));
        if (!Files.exists(source)) source = root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "player", "exo", "PreCache.java"));
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
