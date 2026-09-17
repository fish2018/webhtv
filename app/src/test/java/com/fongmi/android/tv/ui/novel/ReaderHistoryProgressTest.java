package com.fongmi.android.tv.ui.novel;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.db.AppDatabase;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * 阅读进度的落库编码：历史列表按 position/duration 画进度条。
 *
 * 只把「读完」编码为 duration，其余锚点原样存序号 —— 这样读完是 100%，
 * 而升级前写入的存量 0 基记录读回来也不会平移。
 */
public class ReaderHistoryProgressTest {

    @Test
    public void readerKeysAndTypeMarkIsolateRowsFromPlaybackHistory() {
        History playback = new History();
        playback.setKey("site" + AppDatabase.SYMBOL + "vod" + AppDatabase.SYMBOL + 7);
        playback.setMediaType("movie");

        assertFalse(ReaderHistory.isReaderRecord(playback));

        String readerKey = ReaderHistory.buildKey("site", "vod", 7);
        History reader = new History();
        reader.setKey(readerKey);
        reader.setMediaType(ReaderHistory.MEDIA_TYPE);

        assertTrue(ReaderHistory.isReaderRecord(reader));
        assertTrue(readerKey.endsWith(AppDatabase.SYMBOL + ReaderHistory.MEDIA_TYPE));
        assertNotEquals(playback.getKey(), readerKey);
    }

    @Test
    public void finishedChapterFillsTheBar() {
        assertEquals(33, ReaderHistory.toPosition(32, 33));
        assertEquals(2, ReaderHistory.toPosition(1, 2));
        assertEquals(1, ReaderHistory.toPosition(0, 1));
    }

    @Test
    public void midChapterAnchorIsStoredAsIs() {
        assertEquals(0, ReaderHistory.toPosition(0, 33));
        assertEquals(11, ReaderHistory.toPosition(11, 33));
        assertEquals(31, ReaderHistory.toPosition(31, 33));
    }

    @Test
    public void anchorSurvivesTheRoundTrip() {
        for (int total : new int[] {1, 2, 3, 33, 95}) {
            for (int anchor = 0; anchor < total; anchor++) {
                long position = ReaderHistory.toPosition(anchor, total);
                assertEquals("total=" + total + " anchor=" + anchor,
                        anchor, ReaderHistory.toAnchor(position, total));
            }
        }
    }

    /** 打开后不滚动就退出会把恢复值原样回写，必须不漂移。 */
    @Test
    public void repeatedOpenAndExitDoesNotDrift() {
        int total = 33;
        for (long start : new long[] {0, 12, 31, 33}) {
            long position = start;
            for (int cycle = 0; cycle < 5; cycle++) {
                int anchor = ReaderHistory.toAnchor(position, total);
                position = ReaderHistory.toPosition(anchor, total);
            }
            assertEquals("start=" + start, start, position);
        }
    }

    /**
     * 升级前的存量记录不会被误判成「读完」。
     * 旧代码上限是 min(duration, anchor)，anchor 最大 total-1，故旧 position 恒小于 duration。
     */
    @Test
    public void legacyRowsAreNotMistakenForFinished() {
        int total = 33;
        for (int legacy = 0; legacy <= total - 1; legacy++) {
            assertTrue("legacy=" + legacy, legacy < total);
            assertEquals("legacy=" + legacy, legacy, ReaderHistory.toAnchor(legacy, total));
        }
    }

    /**
     * duration 恰好等于 SCALE 时不能把「读完」编码成 duration。
     * 那会让记录长得和旧版百分比记录一模一样，下次恢复走百分比分支，
     * 把锚点当成 0~1 的比例用，位置彻底错乱。
     */
    @Test
    public void scaleSizedChapterDoesNotCollideWithLegacyPercentRecords() {
        long scale = ReaderHistory.SCALE;
        assertEquals(scale - 1, ReaderHistory.toPosition((int) scale - 1, scale));
        // 少记一个锚点是可接受代价；换回来仍落在合法范围内
        assertEquals(scale - 1, ReaderHistory.toAnchor(scale - 1, scale));
        // 相邻规模不受影响，读完仍编码为 duration
        assertEquals(scale + 1, ReaderHistory.toPosition((int) scale, scale + 1));
        assertEquals(scale - 1, ReaderHistory.toPosition((int) scale - 2, scale - 1));
    }

    @Test
    public void degenerateTotalsDoNotCrash() {
        assertEquals(0, ReaderHistory.toAnchor(5, 0));
        assertEquals(0, ReaderHistory.toAnchor(-1, 10));
        assertEquals(0, ReaderHistory.toPosition(-5, 10));
    }

    @Test
    public void readerPayloadUsesTheFieldThatTriggeredReaderRouting() {
        Result result = new Result();
        result.setPlayUrl("novel://{\"title\":\"chapter\",\"content\":\"text\"}");
        result.setUrl("https://example.invalid/video.m3u8");

        assertEquals(result.getPlayUrl(), NovelRouter.readerPayload(result));
    }

    @Test
    public void resolvedChapterChangeCopiesSourceProgressInsteadOfKeepingAnotherChapter() {
        History source = history("source", 21, 50);
        History target = history("old-target", 8, 10);

        NovelRouter.alignResolvedHistoryProgress(target, source, "new-target");

        assertEquals(21, target.getPosition());
        assertEquals(50, target.getDuration());
    }

    @Test
    public void sameResolvedChapterKeepsExistingTargetProgress() {
        History source = history("chapter", 21, 50);
        History target = history("chapter", 8, 10);

        NovelRouter.alignResolvedHistoryProgress(target, source, "chapter");

        assertEquals(8, target.getPosition());
        assertEquals(10, target.getDuration());
    }

    @Test
    public void videoProgressDoesNotBecomeAReaderAnchor() {
        History source = history("source", 120_000, 2_700_000);
        source.setMediaType("tv");
        History target = history("old-target", 8, 10);

        NovelRouter.alignResolvedHistoryProgress(target, source, "new-target");

        assertFalse(target.hasPlaybackTime());
    }

    private static History history(String episodeUrl, long position, long duration) {
        History history = new History();
        history.setKey("site" + AppDatabase.SYMBOL + episodeUrl);
        history.setEpisodeUrl(episodeUrl);
        history.setMediaType(ReaderHistory.MEDIA_TYPE);
        history.setPosition(position);
        history.setDuration(duration);
        return history;
    }
}
