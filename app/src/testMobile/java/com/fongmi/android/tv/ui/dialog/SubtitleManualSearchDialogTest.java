package com.fongmi.android.tv.ui.dialog;

import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;
import com.fongmi.android.tv.subtitle.translate.SubtitleTranslationResult;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SubtitleManualSearchDialogTest {

    @Test
    public void withTranslatedCandidatePrependsGeneratedSubtitleAndSelectsIt() {
        SubtitleCandidate original = new SubtitleCandidate("assrt", "sub-1", "English.srt", "en", "srt", "", 80, 2025, 1, 1, SubtitleMatchType.MANUAL, "query", true, "payload");
        SubtitleAsset translated = new SubtitleAsset("/tmp/English.zh-Hans.srt", "/tmp/English.zh-Hans.srt", "English.zh-Hans.srt", "zh-Hans", "application/x-subrip", 0, true, 0L);
        SubtitleTranslationResult result = SubtitleTranslationResult.of(SubtitleTranslationResult.Status.TRANSLATED, null, translated, "", 2, 1, 100L);

        List<SubtitleCandidate> candidates = SubtitleManualSearchDialog.withTranslatedCandidate(List.of(original), result);
        SubtitleCandidate selected = candidates.get(0);
        SubtitleAsset asset = SubtitleManualSearchDialog.assetFromGeneratedCandidate(selected);

        assertEquals(2, candidates.size());
        assertTrue(SubtitleManualSearchDialog.isGeneratedAiCandidate(selected));
        assertEquals(0, SubtitleManualSearchDialog.indexOfCandidate(candidates, selected));
        assertEquals(original, candidates.get(1));
        assertNotNull(asset);
        assertEquals("/tmp/English.zh-Hans.srt", asset.getLocalPath());
        assertEquals("zh-Hans", asset.getLanguage());
    }

    @Test
    public void resolveManual_doesNotOfferAiTranslateAutomatically() throws Exception {
        String source = readManualSearchDialogSource();
        int resolve = source.indexOf("private static void resolve");
        int nextMethod = source.indexOf("private static void applyGeneratedCandidate", resolve);

        assertTrue(resolve >= 0);
        assertTrue(nextMethod > resolve);
        assertFalse(source.substring(resolve, nextMethod).contains("maybeOfferAiTranslate("));
    }

    @Test
    public void keywordDialog_resizesAboveSoftKeyboard() throws Exception {
        String source = readManualSearchDialogSource();
        int methodStart = source.indexOf("private static void showKeywordDialog");
        assertTrue(methodStart >= 0);
        int methodEnd = source.indexOf("private static void search", methodStart);
        assertTrue(methodEnd > methodStart);
        String method = source.substring(methodStart, methodEnd);
        int dialogShown = method.indexOf(".show();");
        int keyboardShown = method.indexOf("Util.showKeyboard(input);", dialogShown);

        assertTrue(dialogShown >= 0);
        assertTrue(keyboardShown > dialogShown);
        String windowSetup = method.substring(dialogShown, keyboardShown);
        assertTrue(windowSetup.contains("window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);"));
        assertTrue(windowSetup.contains("WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE"));
        assertTrue(windowSetup.contains("WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE"));
    }

    private static String readManualSearchDialogSource() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "SubtitleManualSearchDialog.java"));
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
