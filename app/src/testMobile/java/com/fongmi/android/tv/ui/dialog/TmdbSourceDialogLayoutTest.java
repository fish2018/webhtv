package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TmdbSourceDialogLayoutTest {

    @Test
    public void tmdbSourceDialogMatchesAiConfigTvChromeAndFocus() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "TmdbSourceDialog.java")));
        String layout = read(findMainResPath().resolve(Path.of("layout", "dialog_tmdb_source.xml")));

        assertTrue("TMDB source dialog should use the same light panel background as AI config",
                layout.contains("android:background=\"#F6F8FC\""));
        assertTrue("TMDB source dialog should group fields into light dialog cards",
                layout.contains("com.google.android.material.card.MaterialCardView")
                        && layout.contains("app:cardCornerRadius=\"18dp\"")
                        && layout.contains("app:strokeColor=\"#D8E0EA\""));
        assertTrue("TMDB source dialog controls should use AI config's stable TV heights",
                layout.contains("android:layout_height=\"44dp\"")
                        && layout.contains("android:layout_height=\"48dp\"")
                        && layout.contains("android:layout_height=\"52dp\""));

        assertTrue("TMDB source dialog should wire an explicit DPAD focus chain after AlertDialog buttons exist",
                source.contains("wireConfigDialogFocus(dialog, ruleInput, addBtn, disabledRuleInput, addDisabledBtn, manageBtn, resetBtn);"));
        assertTrue("TMDB source dialog should keep text editing usable by only leaving horizontal inputs at cursor edges",
                source.contains("private static void wireTextDpadFocus(EditText view, View up, View down, View left, View right)")
                        && source.contains("isCursorAtStart(view)")
                        && source.contains("isCursorAtEnd(view)"));
    }

    @Test
    public void tmdbSeasonChoiceIsSharedAcrossDetailAndPlaybackSurfaces() throws Exception {
        String choiceDialog = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "ChoiceDialog.java")));
        String detailActivity = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));
        String mobileActivity = read(findFlavorJavaPath("mobile").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String leanbackActivity = read(findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String sharedHeader = read(findMainResPath().resolve(Path.of("layout", "view_tmdb_header.xml")));
        String detailLayout = read(findMainResPath().resolve(Path.of("layout", "activity_tmdb_detail.xml")));
        String mobileLayout = read(findFlavorResPath("mobile").resolve(Path.of("layout", "activity_video.xml")));
        String leanbackLayout = read(findFlavorResPath("leanback").resolve(Path.of("layout", "activity_video.xml")));

        assertTrue("season selection must be implemented once in the shared choice dialog",
                choiceDialog.contains("showTmdbSeason("));
        assertTrue("season selection must expose deterministic TMDB count slicing before AI",
                choiceDialog.contains("R.string.tmdb_season_auto_by_counts")
                        && choiceDialog.contains("listener.onTmdbCounts()"));
        assertTrue("mobile playback should merge season matching into the episode heading",
                !sharedHeader.contains("@+id/tmdbSeasonMatch")
                        && mobileActivity.contains("mBinding.episodeTitle.setOnClickListener"));
        assertTrue("leanback playback should expose a focusable episode-season heading",
                !leanbackLayout.contains("@+id/tmdbSeasonMatch")
                        && leanbackLayout.contains("android:id=\"@+id/episodeTitle\"")
                        && leanbackLayout.contains("android:focusable=\"true\""));
        assertTrue("TMDB detail should merge season matching into the episode heading",
                !detailLayout.contains("@+id/seasonMatch")
                        && !detailLayout.contains("@+id/seasonMatchTop")
                        && !detailLayout.contains("@+id/seasonMatchFusion")
                        && detailLayout.contains("android:id=\"@+id/episodeTitle\"")
                        && detailActivity.contains("binding.episodeTitle.setOnClickListener"));
        assertTrue("touch playback should open season matching on the first tap",
                !xmlElement(detailLayout, "episodeTitle").contains("android:focusableInTouchMode=\"true\"")
                        && !xmlElement(mobileLayout, "episodeTitle").contains("android:focusableInTouchMode=\"true\"")
                        && detailActivity.contains("binding.episodeTitle.setFocusableInTouchMode(false)")
                        && mobileActivity.contains("mBinding.episodeTitle.setFocusableInTouchMode(false)"));
        assertTrue("TMDB detail must expose manual season selection",
                detailActivity.contains("ChoiceDialog.showTmdbSeason("));
        assertTrue("mobile playback must expose manual season selection",
                mobileActivity.contains("ChoiceDialog.showTmdbSeason("));
        assertTrue("leanback playback must expose manual season selection",
                leanbackActivity.contains("ChoiceDialog.showTmdbSeason("));
        assertTrue("manual TV rematch must continue into season selection on all three surfaces",
                detailActivity.contains("maybeShowPendingTmdbSeasonDialog();")
                        && mobileActivity.contains("maybeShowPendingTmdbSeasonDialog();")
                        && leanbackActivity.contains("maybeShowPendingTmdbSeasonDialog();"));
        assertTrue("detail rematch must invalidate a stale season binding before loading it",
                detailActivity.contains("cache.removeIfMediaChanged(getKeyText(), getIdText(), sourceTitle,"));
    }

    @Test
    public void aiSeasonAnalysisUsesCancellableLoadingOnAllSurfaces() throws Exception {
        String loadingDialog = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AiAnalysisDialog.java")));
        String service = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "service", "AiEpisodeSeasonService.java")));
        String detailActivity = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));
        String mobileActivity = read(findFlavorJavaPath("mobile").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String leanbackActivity = read(findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertTrue("AI analysis must show indeterminate progress and allow back-button cancellation",
                loadingDialog.contains("progress.setIndeterminate(true)")
                        && loadingDialog.contains("dialog.setOnCancelListener")
                        && loadingDialog.contains("dialog.setCanceledOnTouchOutside(false)"));
        assertTrue("canceling AI analysis must cancel the active HTTP call",
                service.contains("public void cancel()") && service.contains("call.cancel()"));
        assertTrue("all detail surfaces must use the cancellable AI loading dialog",
                detailActivity.contains("AiAnalysisDialog.show(")
                        && mobileActivity.contains("AiAnalysisDialog.show(")
                        && leanbackActivity.contains("AiAnalysisDialog.show("));
    }


    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String xmlElement(String layout, String id) {
        int idIndex = layout.indexOf("android:id=\"@+id/" + id + "\"");
        if (idIndex < 0) return "";
        int start = layout.lastIndexOf('<', idIndex);
        int end = layout.indexOf("/>", idIndex);
        return start >= 0 && end >= 0 ? layout.substring(start, end + 2) : "";
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findFlavorJavaPath(String flavor) {
        Path moduleRelative = Path.of("src", flavor, "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", flavor, "java");
    }

    private static Path findFlavorResPath(String flavor) {
        Path moduleRelative = Path.of("src", flavor, "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", flavor, "res");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }
}
