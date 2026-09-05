package com.fongmi.android.tv.api.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BaseConfigLifecycleTest {

    @Test
    public void ensureLoadedWaitsForCleanupBeforeTakingConfigMonitor() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "api", "config", "BaseConfig.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int ensure = source.indexOf("public void ensureLoaded()");
        int postEvent = source.indexOf("protected void postEvent()", ensure);

        assertTrue(sourcePath + " is missing ensureLoaded", ensure >= 0 && postEvent > ensure);

        String body = source.substring(ensure, postEvent);
        int await = body.indexOf("BaseLoader.get().awaitClear();");
        int monitor = body.indexOf("synchronized (this)");
        assertTrue("ensureLoaded must wait for loader cleanup before taking the config monitor",
                await >= 0 && monitor > await);
        assertFalse("ensureLoaded must not hold the config monitor while waiting for plugin cleanup",
                body.contains("public synchronized void ensureLoaded()"));
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
