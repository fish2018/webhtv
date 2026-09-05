package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TrackDialogTest {

    @Test
    public void subtitleAssetFrom_acceptsLocalSrtSubtitle() throws Exception {
        File source = Files.createTempFile("selected-subtitle", ".srt").toFile();
        Sub sub = Sub.create("English.srt", source.getAbsolutePath(), "en", MimeTypes.APPLICATION_SUBRIP);

        SubtitleAsset asset = TrackDialog.subtitleAssetFrom(sub);

        assertNotNull(asset);
        assertEquals(source.getAbsolutePath(), asset.getLocalPath());
        assertEquals("English.srt", asset.getDisplayName());
        assertTrue(TrackDialog.isLocalSrt(asset));
    }

    @Test
    public void subtitleAssetFrom_rejectsRemoteSubtitleUrl() {
        Sub sub = Sub.create("English.srt", "https://example.com/English.srt", "en", MimeTypes.APPLICATION_SUBRIP);

        assertNull(TrackDialog.subtitleAssetFrom(sub));
    }

    @Test
    public void selectedSubtitleFallbackTrack_marksAppliedSubtitleSelected() {
        Sub sub = Sub.create("Manual", "/tmp/manual.srt", "zh-Hans", MimeTypes.APPLICATION_SUBRIP);

        Track track = TrackDialog.selectedSubtitleFallbackTrack(sub, C.TRACK_TYPE_TEXT);

        assertNotNull(track);
        assertEquals("Manual", track.getName());
        assertEquals("Manual,zh-Hans,application/x-subrip", track.getFormat());
        assertTrue(track.isSelected());
    }

    @Test
    public void aiTaskState_keepsProgressForReopenedDialog() {
        TrackDialog.AiTaskState.clearForTest();

        TrackDialog.AiTaskState.start("playback-1", "preparing", 100L);
        TrackDialog.AiTaskState.update("playback-1", "AI progress 1/2");
        TrackDialog.AiTaskState progress = TrackDialog.AiTaskState.get("playback-1");

        assertNotNull(progress);
        assertTrue(progress.isTranslating());
        assertEquals(100L, progress.getStartedAt());
        assertEquals("AI progress 1/2", progress.getMessage());

        TrackDialog.AiTaskState.finish("playback-1", "done");
        TrackDialog.AiTaskState done = TrackDialog.AiTaskState.get("playback-1");

        assertNotNull(done);
        assertFalse(done.isTranslating());
        assertEquals(100L, done.getStartedAt());
        assertEquals("done", done.getMessage());
    }

    @Test
    public void aiTranslatedSubtitleDetection_matchesGeneratedSubtitlePath() {
        Sub original = Sub.create("English.srt", "/tmp/English.srt", "en", MimeTypes.APPLICATION_SUBRIP);
        Sub translated = Sub.create("English.zh-Hans.srt", "/tmp/subtitle_translation/abc.zh-Hans.srt", "zh-Hans", MimeTypes.APPLICATION_SUBRIP);

        assertFalse(TrackDialog.isAiTranslatedSubtitle(original));
        assertTrue(TrackDialog.isAiTranslatedSubtitle(translated));
    }

    @Test
    public void originalSubtitleForRegenerate_usesMatchingNonAiSource() throws Exception {
        File dir = Files.createTempDirectory("ai-regenerate-source").toFile();
        File originalFile = new File(dir, "English.srt");
        File otherFile = new File(dir, "Other.srt");
        Files.writeString(originalFile.toPath(), "1\n00:00:01,000 --> 00:00:02,000\nHello\n");
        Files.writeString(otherFile.toPath(), "1\n00:00:01,000 --> 00:00:02,000\nOther\n");
        Sub original = Sub.create("English.srt", originalFile.getAbsolutePath(), "en", MimeTypes.APPLICATION_SUBRIP);
        Sub other = Sub.create("Other.srt", otherFile.getAbsolutePath(), "en", MimeTypes.APPLICATION_SUBRIP);
        Sub translated = Sub.create("English.zh-Hans.srt", "/tmp/subtitle_translation/abc.zh-Hans.srt", "zh-Hans", MimeTypes.APPLICATION_SUBRIP);

        assertEquals(original, TrackDialog.originalSubtitleForRegenerate(translated, List.of(translated, other, original)));
    }

    @Test
    public void aiRegenerateButton_bypassesCacheAndSharesBusyState() throws Exception {
        Path root = moduleRoot();
        String source = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "TrackDialog.java")));

        assertTrue("track dialog must wire an AI regenerate action that is only visible for selected AI subtitles",
                source.contains("binding.aiRegenerate.setVisibility(hasAiRegenerate() ? View.VISIBLE : View.GONE)")
                        && source.contains("binding.aiRegenerate.setOnClickListener(this::onAiRegenerate)"));
        assertTrue("AI regenerate must bypass the translation cache",
                source.contains("startAiTranslation(false)") && source.contains(".useCache(useCache)"));
        assertTrue("AI translate and regenerate actions must share the same busy state",
                source.contains("binding.aiRegenerate.setEnabled(!translating)")
                        && source.contains("binding.aiRegenerate.setAlpha(translating ? 0.5f : 1f)"));
        assertTrue("leanback track dialog must expose the regenerate button",
                read(root.resolve(Path.of("src", "leanback", "res", "layout", "dialog_track.xml"))).contains("@+id/aiRegenerate"));
        assertTrue("mobile track dialog must expose the regenerate button",
                read(root.resolve(Path.of("src", "mobile", "res", "layout", "dialog_track.xml"))).contains("@+id/aiRegenerate"));
    }

    @Test
    public void realtimeLanguageCanBeChangedFromTrackDialogWithoutRebuildingPlayback() throws Exception {
        Path root = moduleRoot();
        String dialog = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "TrackDialog.java")));
        String controller = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "subtitle", "RealtimeSubtitleController.java")));
        String mobileLayout = read(root.resolve(Path.of("src", "mobile", "res", "layout", "dialog_track.xml")));
        String leanbackLayout = read(root.resolve(Path.of("src", "leanback", "res", "layout", "dialog_track.xml")));

        assertTrue("track dialog must expose the realtime recognition language selector",
                dialog.contains("binding.realtimeLanguage.setOnClickListener(this::onRealtimeLanguage")
                        && dialog.contains("binding.realtimeLanguage.setVisibility(hasRealtimeAi() ? View.VISIBLE : View.GONE)"));
        assertTrue("both playback layouts must expose the realtime recognition language selector",
                mobileLayout.contains("@+id/realtimeLanguage")
                        && mobileLayout.contains("@+id/realtimeLanguageText")
                        && leanbackLayout.contains("@+id/realtimeLanguage")
                        && leanbackLayout.contains("@+id/realtimeLanguageText"));
        assertTrue("an active realtime session must switch the selected model in place",
                dialog.contains("controller.switchModel(player)")
                        && controller.contains("public synchronized void switchModel(PlayerManager player)"));
        assertTrue("model switching must invalidate old audio and subtitle timeline work",
                controller.contains("timelineGeneration.incrementAndGet()")
                        && controller.contains("audioQueue.clear()"));
        assertTrue("stale recognizer errors must not terminate a newer model switch",
                controller.contains("if (request != generation) return;")
                        && controller.contains("worker.execute(() -> {")
                        && controller.contains("failRecognizer(error, request);"));
        assertTrue("stale audio drain failures must not terminate a newer realtime session",
                controller.contains("int drainGeneration = generation;")
                        && controller.contains("failRecognizer(e, drainGeneration);"));
        assertTrue("recognizer failures must recheck the expected generation before changing session state",
                controller.contains("private synchronized void failRecognizer(Throwable error, int expectedGeneration)")
                        && controller.contains("if (expectedGeneration != generation) return;"));
        int switchStart = controller.indexOf("public synchronized void switchModel(PlayerManager player)");
        int prepareStart = controller.indexOf("private void prepareModel", switchStart);
        String switchBody = controller.substring(switchStart, prepareStart);
        assertFalse("switching the recognition model must retain the existing audio pipeline and playback session",
                switchBody.contains("releaseMediaSignals()")
                        || switchBody.contains("restorePlayback("));
        int resetOld = controller.indexOf("if (replacing) resetRecognizer(previousRecognizer, previousTranslator);", prepareStart);
        int createNew = controller.indexOf("RealtimeSubtitleRecognizer.create", prepareStart);
        int releaseOld = controller.indexOf("releaseRecognizer(previousRecognizer, previousTranslator);", createNew);
        assertTrue("model switching must invalidate the old recognizer before creating the next one",
                resetOld >= prepareStart && createNew > resetOld);
        assertTrue("the old native recognizer must be released after the new one is installed",
                releaseOld > createNew);
        assertTrue("stale model preparation state must not be published after a newer selection",
                controller.contains("notifyState(request, State.PREPARING, \"\")")
                        && controller.contains("notifyState(request, State.ON, \"\")")
                        && controller.contains("private void notifyState(int expectedGeneration, State state, String message)"));
        assertTrue("a stale model-ready callback must not disable native subtitles after realtime subtitles were turned off",
                controller.contains("if (request != generation || !enabled) return;")
                        && controller.contains("disableNativeSubtitle();")
                        && controller.contains("startCueTicker();"));
        int replacementFailureStart = controller.indexOf("if (replacing && (previousRecognizer != null || previousTranslator != null))");
        int replacementFailureEnd = controller.indexOf("enabled = false;", replacementFailureStart);
        String replacementFailureBody = replacementFailureStart >= 0 && replacementFailureEnd > replacementFailureStart
                ? controller.substring(replacementFailureStart, replacementFailureEnd) : "";
        assertTrue("a failed model switch must recheck its generation before restoring the previous session",
                replacementFailureBody.contains("synchronized (this)")
                        && replacementFailureBody.contains("if (request != generation) return;")
                        && replacementFailureBody.indexOf("if (request != generation) return;") > replacementFailureBody.indexOf("synchronized (this)"));
        assertTrue("a stale model-switch recovery callback must not restart the subtitle ticker",
                replacementFailureBody.contains("main.post(() -> {")
                        && replacementFailureBody.contains("if (request != generation || !enabled) return;")
                        && replacementFailureBody.contains("startCueTicker();"));
    }

    @Test
    public void appliedAiSubtitleIsPrependedWithoutRemovingExistingTrackOptions() throws Exception {
        Path root = moduleRoot();
        String dialog = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "TrackDialog.java")));
        String adapter = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "adapter", "TrackAdapter.java")));

        assertTrue("applying an AI subtitle must update the open track dialog with the new selected item",
                dialog.contains("showAppliedSubtitleTrack(sub)") && dialog.contains("adapter.prependSelected(item)"));
        assertTrue("track adapter must prepend the selected item instead of clearing existing options",
                adapter.contains("void prependSelected(Track item)")
                        && adapter.contains("existing.setSelected(false)")
                        && adapter.contains("mItems.add(0, item)"));
    }

    @Test
    public void videoTrackListHighlightsOnlyTheDecodedAdaptiveTrack() throws Exception {
        Path root = moduleRoot();
        String dialog = read(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "TrackDialog.java")));

        assertTrue("track list must resolve the selected flag through the adaptive-aware helper",
                dialog.contains("item.setSelected(isSelected(trackGroup, j, format, active))"));
        assertTrue("the decoded video format must narrow a multi-track adaptive selection",
                dialog.contains("TrackUtil.uniqueActiveFormat(tracks, type, player.getVideoFormat())"));
        assertTrue("字段完全相同的两条轨道用 equals 会同时高亮，必须按引用比对",
                dialog.contains("if (active != null) return format == active;")
                        && !dialog.contains("return format.equals(active);"));
        assertTrue("without a resolved decoded format the player flags must still win",
                dialog.contains("return trackGroup.isTrackSelected(trackIndex);"));
        assertTrue("轨道快照只能读一次，MPV/IJK 的快照由原生线程写入，两次读可能不一致",
                dialog.contains("Tracks tracks = player.getCurrentTracks();")
                        && dialog.contains("Format active = activeVideoFormat(tracks);"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path moduleRoot() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return Path.of(".");
        return Path.of("app");
    }
}
