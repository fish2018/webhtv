package com.fongmi.android.tv.player;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class IntroSkipActivityWiringTest {

    @Test
    public void everyPlaybackActivityInvalidatesIntroSkipOnDestroy() throws Exception {
        for (String path : new String[] {
                "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java"
        }) {
            String source = read(path);
            int destroy = source.lastIndexOf("protected void onDestroy()");
            int reset = source.indexOf(path.contains("TmdbDetailActivity")
                    ? "introSkipPlayback.reset();" : "mIntroSkipPlayback.reset();", destroy);
            int parent = source.indexOf("super.onDestroy();", destroy);
            assertTrue(path, destroy >= 0 && reset > destroy && reset < parent);
        }
    }

    @Test
    public void confirmationDialogsReportDismissalAndMobileHonorsReverseOrder() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String detail = read("app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");

        assertTrue(mobile.contains("setOnDismissListener"));
        assertTrue(leanback.contains("setOnDismissListener"));
        assertTrue(detail.contains("setOnDismissListener"));
        assertTrue(mobile.contains("mHistory.isRevPlay() ? -1 : 1"));
    }

    @Test
    public void tmdbIntroSkipCallbacksRequireLiveOwnedPlayback() throws Exception {
        String detail = read("app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");
        int loaded = detail.indexOf("private void onIntroSkipPlanLoaded()");
        int loadedEnd = detail.indexOf("private void preloadAdjacentIntroSkipPlans()", loaded);
        int apply = detail.indexOf("private boolean applyAutoIntroSkip()");
        int applyEnd = detail.indexOf("private IntroSkipService.Query buildIntroSkipQuery()", apply);

        assertTrue("TMDB plan callback must reject a dead or borrowed player",
                loaded >= 0 && loadedEnd > loaded
                        && detail.substring(loaded, loadedEnd).contains("isFinishing()")
                        && detail.substring(loaded, loadedEnd).contains("isOwner()"));
        assertTrue("TMDB auto skip must reject a dead or borrowed player",
                apply >= 0 && applyEnd > apply
                        && detail.substring(apply, applyEnd).contains("isFinishing()")
                        && detail.substring(apply, applyEnd).contains("isOwner()"));
    }

    @Test
    public void tmdbIntroSkipCallbacksAlsoRequireInlinePlaybackState() throws Exception {
        String detail = read("app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");
        int loaded = detail.indexOf("private void onIntroSkipPlanLoaded()");
        int loadedEnd = detail.indexOf("private void preloadAdjacentIntroSkipPlans()", loaded);
        int apply = detail.indexOf("private boolean applyAutoIntroSkip()");
        int applyEnd = detail.indexOf("private IntroSkipService.Query buildIntroSkipQuery()", apply);

        assertTrue("a late TMDB plan must be ignored outside inline playback",
                loaded >= 0 && loadedEnd > loaded
                        && detail.substring(loaded, loadedEnd).contains("inlineStarted")
                        && detail.substring(loaded, loadedEnd).contains("player().isReleased()"));
        assertTrue("auto skip must be gated by the active inline playback state",
                apply >= 0 && applyEnd > apply
                        && detail.substring(apply, applyEnd).contains("inlineStarted")
                        && detail.substring(apply, applyEnd).contains("player().isReleased()"));
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of(path.substring("app/".length())), StandardCharsets.UTF_8);
    }
}
