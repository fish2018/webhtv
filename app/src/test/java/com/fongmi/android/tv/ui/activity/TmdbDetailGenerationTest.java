package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TmdbDetailGenerationTest {

    @Test
    public void staleSourcePayloadNetworkResultCannotUpdateNewPage() throws Exception {
        String body = loadContentBody();
        int request = body.indexOf("tmdbService.detailForSource(initialBundle.item(), sourcePayload.getSeasonNumber(), tmdbConfig, plan.missing())");
        int staleCheck = body.indexOf("if (generation != loadGeneration || Thread.currentThread().isInterrupted()) return;", request);
        int apply = body.indexOf("runOnAliveUi(() -> {", staleCheck);
        int identityCheck = body.indexOf("!isSameTmdbItem(mergedBundle.item(), matchedTmdbItem)", apply);

        assertTrue("network fill must be discarded when the page generation changed", staleCheck > request);
        assertTrue("UI apply must recheck generation, loaded source and TMDB identity", apply > staleCheck && identityCheck > apply);
    }

    private static String loadContentBody() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        int start = source.indexOf("private void loadContent(@Nullable TmdbBundle reusableBundle)");
        int end = source.indexOf("private boolean shouldLoadInitialStandaloneTmdbDetailInSinglePass", start);
        assertTrue(sourcePath + " is missing the loadContent method", start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static Path findMainJavaPath() {
        Path root = Files.exists(Path.of("src", "main")) ? Path.of(".") : Path.of("app");
        return root.resolve(Path.of("src", "main", "java"));
    }
}
