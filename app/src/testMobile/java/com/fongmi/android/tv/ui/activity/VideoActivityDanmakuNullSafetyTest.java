package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoActivityDanmakuNullSafetyTest {

    @Test
    public void danmakuEntryHandlesMissingEpisodeInEveryFlavor() throws Exception {
        assertDanmakuEntryIsNullSafe("mobile");
        assertDanmakuEntryIsNullSafe("leanback");
    }

    private static void assertDanmakuEntryIsNullSafe(String flavor) throws Exception {
        String source = readVideoActivity(flavor);
        String helper = methodBody(source, "private String getDanmakuEpisodeName()");
        String directEntry = methodBody(source, "private void onDanmaku()");
        String panelEntry = methodBody(source, "public void onDanmakuPanel()");

        assertTrue(flavor + " player must read the current episode once", helper.contains("Episode episode = getEpisode();"));
        assertTrue(flavor + " player must tolerate a missing episode", helper.contains("episode == null ? \"\" : episode.getName()"));
        assertTrue(flavor + " danmaku entry must use the null-safe episode name", directEntry.contains("getDanmakuEpisodeName()"));
        assertFalse(flavor + " control-panel danmaku entry must not dereference getEpisode() directly",
                panelEntry.contains("getEpisode().getName()"));
        assertTrue(flavor + " control-panel danmaku entry must use a null-safe path",
                panelEntry.contains("getDanmakuEpisodeName()") || panelEntry.contains("onDanmaku();"));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing method: " + signature, start >= 0);
        int openBrace = source.indexOf('{', start + signature.length());
        assertTrue("missing opening brace: " + signature, openBrace >= 0);
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return source.substring(start, index + 1);
        }
        throw new AssertionError("missing closing brace: " + signature);
    }

    private static String readVideoActivity(String flavor) throws Exception {
        Path relative = Path.of("src", flavor, "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        Path path = Files.exists(relative) ? relative : Path.of("app").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
