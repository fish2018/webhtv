package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.playback.SubtitleSource;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class HistorySubtitleSourceTest {

    @Test
    public void copyCarriesSubtitleSource() {
        History history = new History();
        history.setKey("site@@@vod@@@1");
        history.setSubtitleSourceObject(source());

        History copy = history.copy();

        assertEquals(history.getSubtitleSource(), copy.getSubtitleSource());
        assertNotNull(copy.getSubtitleSourceObject());
        assertEquals("/storage/emulated/0/a.srt", copy.getSubtitleSourceObject().getUrl());
    }

    @Test
    public void unusableSourceIsStoredAsEmptyString() {
        History history = new History();
        history.setSubtitleSourceObject(source());

        history.setSubtitleSourceObject(null);

        assertEquals("", history.getSubtitleSource());
        assertNull(history.getSubtitleSourceObject());
    }

    @Test
    public void dirtyColumnValueDegradesToNoPreference() {
        History history = new History();
        history.setSubtitleSource("{not json");

        assertNull(history.getSubtitleSourceObject());
        // 脏数据保留在列里不算问题，读不出来即等于无偏好。
        assertEquals("{not json", history.getSubtitleSource());
    }

    @Test
    public void defaultHistoryHasNoSubtitleSource() {
        History history = new History();

        assertEquals("", history.getSubtitleSource());
        assertNull(history.getSubtitleSourceObject());
    }

    private static SubtitleSource source() {
        return SubtitleSource.of(
                Sub.create("简体中文", "/storage/emulated/0/a.srt", "zh", "application/x-subrip"),
                "https://site/ep12.m3u8");
    }
}
