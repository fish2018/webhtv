package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.Sub;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SubtitleSourceTest {

    @Test
    public void encodeDecodeKeepsEveryField() {
        SubtitleSource source = SubtitleSource.of(
                Sub.create("简体中文", "/storage/emulated/0/Movies/a.srt", "zh", "application/x-subrip"),
                "https://site/ep12.m3u8");

        SubtitleSource restored = SubtitleSource.decode(SubtitleSource.encode(source));

        assertNotNull(restored);
        assertEquals("简体中文", restored.getName());
        assertEquals("/storage/emulated/0/Movies/a.srt", restored.getUrl());
        assertEquals("zh", restored.getLang());
        assertEquals("application/x-subrip", restored.getFormat());
        assertEquals("https://site/ep12.m3u8", restored.getEpisodeUrl());
        assertEquals(SubtitleSource.MODE_EXTERNAL, restored.getMode());
        assertEquals(source.getTime(), restored.getTime());
    }

    @Test
    public void remoteIsDetectedFromUrlScheme() {
        assertTrue(source("https://sub.example/a.srt").isRemote());
        assertFalse(source("/storage/emulated/0/a.srt").isRemote());
        assertFalse(source("/data/user/0/pkg/cache/subtitle_asset/x.srt").isRemote());
    }

    @Test
    public void ofRejectsUnusableSubtitle() {
        assertNull(SubtitleSource.of(null, "ep"));
        assertNull(SubtitleSource.of(Sub.create("name", "", "zh", "text/plain"), "ep"));
    }

    @Test
    public void encodeReturnsEmptyForUnusableSource() {
        assertEquals("", SubtitleSource.encode(null));
        assertEquals("", SubtitleSource.encode(new SubtitleSource()));
    }

    @Test
    public void decodeSurvivesDirtyData() {
        assertNull(SubtitleSource.decode(null));
        assertNull(SubtitleSource.decode(""));
        assertNull(SubtitleSource.decode("{"));
        assertNull(SubtitleSource.decode("not json at all"));
        assertNull(SubtitleSource.decode("[1,2,3]"));
        // 结构合法但没有 url，不能当成可用偏好。
        assertNull(SubtitleSource.decode("{\"mode\":\"external\",\"name\":\"x\"}"));
    }

    @Test
    public void toSubRoundTripsThroughSub() {
        Sub sub = source("/storage/emulated/0/a.ass").toSub();

        assertNotNull(sub);
        assertEquals("/storage/emulated/0/a.ass", sub.getUrl());
        assertEquals("名字", sub.getName());
        assertEquals("zh", sub.getLang());
    }

    @Test
    public void missingEpisodeUrlBecomesEmptyNotNull() {
        assertEquals("", SubtitleSource.of(
                Sub.create("n", "/a.srt", "zh", "text/plain"), null).getEpisodeUrl());
    }

    private static SubtitleSource source(String url) {
        return SubtitleSource.of(Sub.create("名字", url, "zh", "application/x-subrip"), "ep");
    }
}
