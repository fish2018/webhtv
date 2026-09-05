package com.fongmi.android.tv.model;

import com.fongmi.android.tv.utils.Task;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchExecutorRecoveryTest {

    @Test
    public void aNewSearchExecutorRunsAfterThePreviousGenerationIsStopped() throws Exception {
        ListeningExecutorService previous = newSearchExecutor(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        previous.submit(() -> {
            started.countDown();
            while (release.getCount() > 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // Simulate a third-party site spider that ignores cancellation.
                }
            }
            return null;
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));
        ListeningExecutorService current = null;
        try {
            previous.shutdownNow();
            current = newSearchExecutor(1);
            assertEquals("current", current.submit(() -> "current").get(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            previous.shutdownNow();
            if (current != null) current.shutdownNow();
        }
    }

    @Test
    public void siteSearchUsesAReplaceableExecutorInsteadOfTheGlobalPool() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/fongmi/android/tv/model/SiteViewModel.java"));

        assertTrue(source.contains("searchExecutor = Task.newSearchExecutor(Setting.getSearchThread())"));
        assertTrue(source.contains("FluentFuture.from(executor.submit(SearchTask.create(site, keyword, quick)))"));
        assertTrue(source.contains("searchExecutor.shutdownNow()"));
        assertTrue(source.contains("synchronized (searchLock)"));
        assertFalse(source.contains("Task.searchPoolExecutor().submit(SearchTask.create(site, keyword, quick))"));
    }

    private static ListeningExecutorService newSearchExecutor(int threads) {
        return Task.newSearchExecutor(threads);
    }
}
