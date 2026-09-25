package com.fongmi.android.tv.title;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class MediaTitleResolverSourceTest {

    @Test
    public void resolverSkipsTmdbCacheWhenItConflictsWithRuleTitle() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "title", "MediaTitleResolver.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int apply = source.indexOf("private void applyTmdbCache");
        int compatible = source.indexOf("private boolean isTmdbCacheCompatible");

        assertTrue(sourcePath + " is missing applyTmdbCache", apply >= 0);
        assertTrue("resolver must not let stale TMDB cache override a different parsed title",
                compatible > apply && source.indexOf("if (!isTmdbCacheCompatible(item, resolution)) return;", apply) > apply);
        assertTrue("cache compatibility must compare normalized cached and parsed titles",
                source.indexOf("parser.normalizeSearchText(item.getTitle())", compatible) > compatible
                        && source.indexOf("parser.normalizeSearchText(resolution.getCanonicalTitle())", compatible) > compatible);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
