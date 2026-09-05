package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class MultiThreadProxyAppLifecycleSourceTest {

    @Test
    public void appStartsAndStopsProcessProxyWithBackgroundServices() throws Exception {
        String app = read("main", "java", "com", "fongmi", "android", "tv", "App.java");

        assertTrue(app.contains("MultiThreadProxy.applyStored()"));
        assertTrue(app.contains("MultiThreadProxy.stop()"));
        assertTrue(app.contains("multi-thread proxy start failed"));
    }

    private static String read(String... parts) throws Exception {
        Path path = Path.of("src");
        for (String part : parts) path = path.resolve(part);
        if (!Files.exists(path)) {
            path = Path.of("app", "src");
            for (String part : parts) path = path.resolve(part);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
