package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DanmakuSearchIntentTest {

    @Test
    public void completedRequestPublishesSubmittedKeyword() {
        Object request = new Object();
        DanmakuSearchIntent intent = new DanmakuSearchIntent();

        intent.begin(request, "  searched title  ");

        assertTrue(intent.complete(request));
        assertEquals("searched title", intent.getResultKeyword());
    }

    @Test
    public void staleRequestCannotReplaceLatestResultKeyword() {
        Object stale = new Object();
        Object current = new Object();
        DanmakuSearchIntent intent = new DanmakuSearchIntent();

        intent.begin(stale, "stale title");
        intent.begin(current, "current title");

        assertFalse(intent.complete(stale));
        assertEquals("", intent.getResultKeyword());
        assertTrue(intent.complete(current));
        assertEquals("current title", intent.getResultKeyword());
    }

    @Test
    public void cancelInvalidatesPendingRequest() {
        Object request = new Object();
        DanmakuSearchIntent intent = new DanmakuSearchIntent();

        intent.begin(request, "title");
        intent.cancel();

        assertFalse(intent.isCurrent(request));
        assertFalse(intent.complete(request));
    }

    @Test
    public void selectedResultsUseKeywordSubmittedWithTheCurrentRequest() throws Exception {
        Path root = Files.isDirectory(Path.of("src", "main", "java")) ? Path.of(".") : Path.of("app");
        for (String name : new String[]{"DanmakuSearchDialog", "DanmakuSearchInputDialog"}) {
            Path source = root.resolve("src/main/java/com/fongmi/android/tv/ui/dialog/" + name + ".java");
            String code = Files.readString(source, StandardCharsets.UTF_8);
            int method = code.indexOf("private void rememberManualDanmaku(");
            assertTrue(name + " must remember manual selections", method >= 0);
            int end = code.indexOf("\n    }", method);
            String body = code.substring(method, end);
            assertTrue(name + " must remember the submitted keyword", body.contains("searchIntent.getResultKeyword()"));
        }
    }
}
