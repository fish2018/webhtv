package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbSourceHybridWiringTest {

    @Test
    public void sourceLoadUsesPlannerAndFillsOnlyInitialGaps() throws Exception {
        String adapter = read("src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java");
        String load = method(adapter, "public void loadSource(TmdbBundle bundle", "private void applySourceBundle");
        String fill = method(adapter, "private void fillInitialSourceGaps", "private static String seasonEpisodeKey");

        assertTrue(load.contains("sourceOnly = !tmdbConfig.isReady();"));
        assertTrue(load.contains("plan.hasInitialNetworkGaps()"));
        assertTrue(load.contains("if (fillInitial) backgroundTasks.submit"));
        assertTrue(fill.contains("tmdbService.detailForSource("));
        assertTrue(fill.contains("TmdbSourceMerger.fillOnly("));
        assertTrue(fill.contains("SubscriptionTmdbCredentialStore") || fill.contains("isCurrentGeneration(generation)"));
    }

    @Test
    public void keylessSourceNeverFallsThroughToSeasonOrVideoNetwork() throws Exception {
        String adapter = read("src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java");
        String season = method(adapter, "private List<TmdbEpisode> seasonEpisodes", "private Integer currentEpisodeMetadataSeason");
        String videos = method(adapter, "public void loadRelatedVideosAsync", "private boolean applySourceVideosIfAvailable");

        int sourceOnly = season.indexOf("if (sourceOnly) return List.of();");
        int seasonNetwork = season.indexOf("tmdbService.season(");
        assertTrue(sourceOnly >= 0 && sourceOnly < seasonNetwork);
        assertTrue(videos.contains("applySourceVideosIfAvailable(item, seasonNumber, episodeNumber, contextKey)"));
        assertTrue(videos.contains("if (sourceOnly) {"));
    }

    @Test
    public void bothFlavorsPreferRenderableSourceBeforeNormalMatching() throws Exception {
        String mobile = read("src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String leanback = read("src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

        for (String source : new String[]{mobile, leanback}) {
            int sourceFirst = source.indexOf("sourceState == TmdbSourceState.RENDERABLE && sourceBundle != null");
            int loadSource = source.indexOf("mTmdbUIAdapter.loadSource(sourceBundle, item, sourcePayload);", sourceFirst);
            assertTrue(sourceFirst > 0 && loadSource > sourceFirst);
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("missing method " + startMarker, start >= 0 && end > start);
        return source.substring(start, end);
    }
}
