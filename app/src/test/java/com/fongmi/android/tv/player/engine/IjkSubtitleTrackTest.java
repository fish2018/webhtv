package com.fongmi.android.tv.player.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.text.CueGroup;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class IjkSubtitleTrackTest {

    @Test
    public void parseSubrip_returnsActiveCuesForPosition() {
        String srt = """
                1
                00:00:01,000 --> 00:00:02,000
                hello

                2
                00:00:03,000 --> 00:00:04,000
                world
                """;

        IjkSubtitleTrack track = IjkSubtitleTrack.parse(srt.getBytes(StandardCharsets.UTF_8), MimeTypes.APPLICATION_SUBRIP, null, null, 0, 0);

        assertEquals("hello", textAt(track, 1500));
        assertTrue(track.getCueGroup(2500).cues.isEmpty());
        assertEquals("world", textAt(track, 3500));
    }

    @Test
    public void parseWebvtt_returnsActiveCuesForPosition() {
        String webvtt = """
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                vtt line
                """;

        IjkSubtitleTrack track = IjkSubtitleTrack.parse(webvtt.getBytes(StandardCharsets.UTF_8), MimeTypes.TEXT_VTT, null, null, 0, 0);

        assertEquals("vtt line", textAt(track, 1500));
        assertTrue(track.getCueGroup(2500).cues.isEmpty());
    }

    private static String textAt(IjkSubtitleTrack track, long positionMs) {
        CueGroup group = track.getCueGroup(positionMs);
        assertEquals(1, group.cues.size());
        return group.cues.get(0).text.toString();
    }
}
