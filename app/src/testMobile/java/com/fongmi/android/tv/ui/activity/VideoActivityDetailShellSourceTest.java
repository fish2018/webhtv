package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-contract checks for the TV detail shell; these do not replace device rendering tests. */
public class VideoActivityDetailShellSourceTest {

    @Test
    public void initialDetailModeIsPreparedBeforePreviewCanBeRevealed() throws Exception {
        String init = method(source(), "protected void initView(Bundle savedInstanceState)");
        int prepare = init.indexOf("prepareInitialDetailShell();");
        int preview = init.indexOf("checkCast();");
        int request = init.indexOf("checkId();");

        assertTrue("prepare detail actions before checkCast reveals the preview",
                prepare >= 0 && preview > prepare);
        assertTrue("prepare detail actions before starting asynchronous detail loading", request > prepare);

        String reset = method(source(), "private void resetDetailForNewIntent()");
        assertTrue("singleTop title changes must prepare the new detail shell before loading",
                reset.contains("prepareInitialDetailShell();")
                        && reset.indexOf("prepareInitialDetailShell();") < reset.indexOf("mBinding.progressLayout.showProgress();"));
    }

    @Test
    public void initialEnhancedShellKeepsTheTmdbConfigurationAndLayoutGuards() throws Exception {
        String prepare = method(source(), "private void prepareInitialDetailShell()");

        assertTrue("unconfigured TMDB must retain ordinary detail actions",
                prepare.contains("boolean tmdbDetail = shouldLoadTmdbDetail();"));
        assertTrue("enhanced actions require both available TMDB and an enhanced/direct entry",
                prepare.contains("tmdbDetail && (Setting.isOriginalEnhancedDetailPage() || isIntentTmdbPlayback())"));
        assertTrue("initial actions must use the same visibility policy as later detail binding",
                prepare.contains("setOriginalEnhancedActionVisibility(enhancedDetail);"));
        assertTrue("source text must only be suppressed for an enhanced TMDB layout",
                prepare.contains("if (enhancedDetail && shouldUseTmdbLayout()) suppressTmdbNativeTextFields();"));
    }

    @Test
    public void orderAndTitleRebindsPreserveEpisodeToolbarVisibility() throws Exception {
        String source = source();
        String reverse = method(source, "private void reverseEpisode(boolean scroll)");
        String title = method(source, "private void toggleEpisodeFileName()");
        String bind = method(source,
                "private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent, boolean updateEpisodeChrome)");
        int start = bind.indexOf("if (updateEpisodeChrome) {");
        int end = start < 0 ? -1 : bind.indexOf("\n        }", start);

        assertTrue("reverse must not recompute visibility during a presentation-only rebind",
                reverse.contains("setEpisodeAdapter(getFlag().getEpisodes(), scroll, false);"));
        assertTrue("title toggle must not recompute visibility during a presentation-only rebind",
                title.contains("setEpisodeAdapter(getFlag().getEpisodes(), true, false);"));
        assertTrue("episode toolbar updates need an explicit guard", start >= 0 && end > start);
        String guarded = bind.substring(start, end);
        String outsideGuard = bind.substring(0, start) + bind.substring(end);
        for (String view : new String[]{"episodeHeader", "episodeReverse", "episodeViewMode", "episodeFileName"}) {
            String setter = "mBinding." + view + ".setVisibility(";
            assertTrue(view + " must be inside the visibility guard", guarded.contains(setter));
            assertFalse(view + " must not be updated outside the visibility guard", outsideGuard.contains(setter));
        }
    }

    @Test
    public void ordinaryEpisodeBindingStillRefreshesToolbarVisibility() throws Exception {
        String source = source();
        assertTrue(method(source, "private void setEpisodeAdapter(List<Episode> items)")
                .contains("setEpisodeAdapter(items, true, true);"));
        assertTrue(method(source, "private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent)")
                .contains("setEpisodeAdapter(items, scrollToCurrent, true);"));
    }

    private static String source() throws Exception {
        Path path = Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        if (!Files.isRegularFile(path)) path = Path.of("app").resolve(path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature + " {");
        int end = start < 0 ? -1 : source.indexOf("\n    }", start);
        assertTrue("missing method: " + signature, start >= 0 && end > start);
        return source.substring(start, end);
    }
}
