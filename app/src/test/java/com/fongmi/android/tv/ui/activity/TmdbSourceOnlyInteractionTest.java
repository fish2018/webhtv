package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbSourceOnlyInteractionTest {

    @Test
    public void standaloneSourceOnlyHidesRematchAndRemoteRatings() throws Exception {
        String source = Files.readString(root().resolve(Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("private void applySourceOnlyActionVisibility()"));
        assertTrue(source.contains("binding.rematch.setVisibility(View.GONE);"));
        assertTrue(source.contains("if (!isTmdbSourceOnly()) {"));
        assertTrue(method(source, "private void fetchDoubanRating", "private void fetchOmdbRating").contains("if (isTmdbSourceOnly()) return;"));
        assertTrue(method(source, "private void fetchOmdbRating", "private void addOmdbRatingChips").contains("if (isTmdbSourceOnly()) return;"));
        assertTrue(source.contains("boolean tmdbSectionData = hasPhotos"));
        assertTrue(source.contains("!isTmdbSourceOnly() && matchedTmdbDetail != null"));
    }

    @Test
    public void nativeVideoSourceOnlyHidesNetworkActionsInBothFlavors() throws Exception {
        String mobile = read("mobile");
        String leanback = read("leanback");

        assertTrue(mobile.contains("private void applySourceOnlyActionVisibility()"));
        assertTrue(mobile.contains("rematch.setVisibility(View.GONE);"));
        assertTrue(mobile.contains("&& !runtimeSourceOnly"));
        assertTrue(method(mobile, "private void showManualTmdbMatchDialog", "private void showTmdbMatchDialog").contains("if (runtimeSourceOnly) return;"));
        assertTrue(method(mobile, "private void showManualTmdbSeasonDialog", "private void analyzeTmdbSeasonWithAi").contains("if (runtimeSourceOnly) return;"));

        assertTrue(leanback.contains("setTmdbRematchVisible(loadTmdbDetail && !runtimeSourceOnly);"));
        assertTrue(leanback.contains("&& !runtimeSourceOnly"));
        assertTrue(method(leanback, "private void showManualTmdbMatchDialog", "private void searchTmdb").contains("if (runtimeSourceOnly) return;"));
        assertTrue(method(leanback, "private void showManualTmdbSeasonDialog", "private void analyzeTmdbSeasonWithAi").contains("if (runtimeSourceOnly) return;"));
        assertTrue(leanback.contains("if (runtimeSourceOnly) {\n            renderTmdbRatingChips(label, container, buildTmdbRatingChips());"));
    }

    @Test
    public void sourceOnlyNeverShowsNullablePlaceholdersInProductionCode() throws Exception {
        String standalone = Files.readString(root().resolve(Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")), StandardCharsets.UTF_8);
        String mobile = read("mobile");
        String leanback = read("leanback");

        for (String source : new String[]{standalone, mobile, leanback}) {
            assertFalse(source.contains("sourceOnlyMissingPlaceholder"));
        }
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("missing method " + startMarker, start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String read(String flavor) throws Exception {
        return Files.readString(root().resolve(Path.of("app", "src", flavor, "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")), StandardCharsets.UTF_8);
    }

    private static Path root() {
        return Files.exists(Path.of("app", "src", "main")) ? Path.of(".") : Path.of("..");
    }
}
