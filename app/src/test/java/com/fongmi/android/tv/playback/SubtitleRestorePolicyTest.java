package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.Sub;

import org.junit.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubtitleRestorePolicyTest {

    private static final String EPISODE = "https://site/ep12.m3u8";

    @Test
    public void restoresLocalSubtitleWhenFileStillExists() {
        SubtitleRestorePolicy.Decision decision = decide(
                source("/storage/emulated/0/a.srt", EPISODE), EPISODE, false, "/storage/emulated/0/a.srt");

        assertTrue(decision.restore());
        assertFalse(decision.clear());
    }

    @Test
    public void dropsLocalSubtitleWhenFileIsGone() {
        SubtitleRestorePolicy.Decision decision = decide(
                source("/data/cache/subtitle_asset/a.srt", EPISODE), EPISODE, false);

        assertFalse(decision.restore());
        assertTrue("失效记录必须清掉，否则每次起播都白跑一次文件检查", decision.clear());
        assertEquals("file-missing", decision.reason());
    }

    @Test
    public void restoresRemoteSubtitleWithoutTouchingTheFilesystem() {
        SubtitleRestorePolicy.Decision decision = decide(
                source("https://sub.example/a.srt", EPISODE), EPISODE, false);

        assertTrue(decision.restore());
        assertFalse(decision.clear());
    }

    @Test
    public void keepsPreferenceWhenPlayingAnotherEpisode() {
        SubtitleRestorePolicy.Decision decision = decide(
                source("/storage/emulated/0/a.srt", "https://site/ep11.m3u8"),
                EPISODE, false, "/storage/emulated/0/a.srt");

        assertFalse("上一集的字幕挂到下一集会让时间轴完全错位", decision.restore());
        assertFalse("换集只是这一次不适用，清掉的话切回 ep11 就没字幕了", decision.clear());
        assertEquals("episode-changed", decision.reason());
    }

    @Test
    public void dropsLegacyRecordWithoutEpisodeUrl() {
        SubtitleRestorePolicy.Decision decision = decide(
                source("/storage/emulated/0/a.srt", ""), EPISODE, false, "/storage/emulated/0/a.srt");

        assertFalse(decision.restore());
        assertTrue(decision.clear());
        assertEquals("episode-unknown", decision.reason());
    }

    @Test
    public void keepsOriginalPreferenceOnCrossSourcePlayback() {
        SubtitleRestorePolicy.Decision decision = decide(
                source("/storage/emulated/0/a.srt", EPISODE), EPISODE, true, "/storage/emulated/0/a.srt");

        assertFalse(decision.restore());
        assertFalse("跨源不注入，但原源的偏好要留着", decision.clear());
        assertEquals("cross-source", decision.reason());
    }

    @Test
    public void absentSourceNeedsNoWriteBack() {
        SubtitleRestorePolicy.Decision decision = decide(null, EPISODE, false);

        assertFalse(decision.restore());
        assertFalse(decision.clear());
        assertEquals("absent", decision.reason());
    }

    private static SubtitleRestorePolicy.Decision decide(
            SubtitleSource source, String episodeUrl, boolean crossSource, String... existing) {
        Set<String> files = Set.of(existing);
        Predicate<String> exists = files::contains;
        return SubtitleRestorePolicy.decide(source, episodeUrl, crossSource, exists);
    }

    private static SubtitleSource source(String url, String episodeUrl) {
        return SubtitleSource.of(Sub.create("字幕", url, "zh", "application/x-subrip"), episodeUrl);
    }
}
