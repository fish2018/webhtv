package com.fongmi.android.tv.node;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class NodeServiceForegroundTest {
    @Test
    public void startCommandAlwaysCallsStartForegroundBeforeHandlingIntent() throws Exception {
        Path path = Path.of("src", "main", "java", "com", "fongmi", "android", "tv",
                "node", "NodeService.java");
        String source = Files.readString(path);
        int onStart = source.indexOf("public int onStartCommand(Intent intent, int flags, int startId)");
        int foreground = source.indexOf("startForegroundCompat(", onStart);
        int launch = source.indexOf("nodeLaunched.compareAndSet", onStart);

        assertTrue(onStart >= 0);
        assertTrue(foreground >= 0 && launch > foreground);
    }
}
