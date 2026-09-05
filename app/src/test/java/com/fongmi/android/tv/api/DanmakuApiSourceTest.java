package com.fongmi.android.tv.api;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class DanmakuApiSourceTest {

    @Test
    public void autoSearchTriesCleanedTitlesBeforeAiFallback() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "api", "DanmakuApi.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("public static void search(MediaTitleRequest request");
        int original = source.indexOf("resolver.queryTitles(request, 3)", method);
        int cleaned = source.indexOf("resolver.queryCleanedTitles(request, 3)", original);
        int aiFallback = source.indexOf("resolver.queryAiFallbackTitles(request, 3)", original);

        assertTrue(sourcePath + " is missing MediaTitleRequest search overload", method >= 0);
        assertTrue("danmaku auto search must try code-cleaned titles after original candidates",
                cleaned > original);
        assertTrue("danmaku auto search must try code-cleaned titles before AI fallback",
                aiFallback > cleaned);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
