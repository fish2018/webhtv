package com.fongmi.android.tv.subtitle.translate;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SrtSubtitleCueParserTest {

    @Test
    public void parse_keepsTimingOrderAndMultilineText() {
        String srt = "\uFEFF1\r\n"
                + "00:00:01,200 --> 00:00:03,400\r\n"
                + "Hello there\r\n"
                + "General Kenobi\r\n"
                + "\r\n"
                + "2\r\n"
                + "00:00:04,000 --> 00:00:05,250\r\n"
                + "Next line\r\n";

        List<SubtitleCue> cues = new SrtSubtitleCueParser().parse(srt);

        assertEquals(2, cues.size());
        assertEquals(1, cues.get(0).getIndex());
        assertEquals(1200L, cues.get(0).getStartMs());
        assertEquals(3400L, cues.get(0).getEndMs());
        assertEquals(List.of("Hello there", "General Kenobi"), cues.get(0).getTextLines());
        assertEquals(2, cues.get(1).getIndex());
        assertEquals(4000L, cues.get(1).getStartMs());
        assertEquals(5250L, cues.get(1).getEndMs());
        assertEquals(List.of("Next line"), cues.get(1).getTextLines());
    }

    @Test
    public void write_outputsStableSrtWithoutChangingTimings() {
        List<SubtitleCue> cues = List.of(
                new SubtitleCue(7, 1200L, 3400L, List.of("你好", "将军")),
                new SubtitleCue(8, 4000L, 5250L, List.of("下一句"))
        );

        String text = new SrtSubtitleCueWriter().write(cues);

        assertEquals("1\n"
                + "00:00:01,200 --> 00:00:03,400\n"
                + "你好\n"
                + "将军\n"
                + "\n"
                + "2\n"
                + "00:00:04,000 --> 00:00:05,250\n"
                + "下一句\n", text);
    }
}
