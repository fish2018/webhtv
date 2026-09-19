package com.fongmi.android.tv.player.exo.ass;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class AssInputTest {
    private static final String SCRIPT = "[Script Info]\nTitle: 中文字幕，逗号不截断\n[Events]\n"
            + "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,中文测试字幕，保留逗号和组合字符 é\n";

    @Test public void boundedCopyRespectsPositionLimitAndSliceOffset() {
        byte[] bytes = {99, 98, 10, 20, 30, 97};
        ByteBuffer slice = ByteBuffer.wrap(bytes, 1, 5).slice();
        slice.position(1); slice.limit(4);
        assertArrayEquals(new byte[]{10, 20, 30}, AssInput.copy(slice.asReadOnlyBuffer()));
        assertEquals(1, slice.position());
    }

    @Test public void mergedExternalIdsAreRecognizedWithoutAcceptingContainerIds() {
        assertTrue(AssInput.isExternalId(AssInput.EXTERNAL_ID_PREFIX + "0"));
        assertTrue(AssInput.isExternalId("1:" + AssInput.EXTERNAL_ID_PREFIX + "0"));
        assertTrue(AssInput.isExternalId("0:2:" + AssInput.EXTERNAL_ID_PREFIX + "12"));
        assertFalse(AssInput.isExternalId(null));
        assertFalse(AssInput.isExternalId("1:2"));
        assertFalse(AssInput.isExternalId("other:" + AssInput.EXTERNAL_ID_PREFIX + "0"));
        assertFalse(AssInput.isExternalId("1::" + AssInput.EXTERNAL_ID_PREFIX + "0"));
        assertFalse(AssInput.isExternalId(AssInput.EXTERNAL_ID_PREFIX));
    }

    @Test public void utf16IsDecodedBeforeTerminalNulHandling() {
        byte[] text = (SCRIPT + "\0\0").getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[text.length + 2];
        bytes[0] = (byte) 0xff; bytes[1] = (byte) 0xfe;
        System.arraycopy(text, 0, bytes, 2, text.length);
        assertEquals(SCRIPT, new String(AssInput.normalize(bytes), StandardCharsets.UTF_8));
    }

    @Test public void legacyChineseEncodingKeepsWholeDialogue() {
        // The existing Media3 detector needs representative text; short legacy
        // samples are ambiguous (the former fixture also detects as KOI8-R).
        String chinese = "[Script Info]\nTitle: 字幕\n[Events]\n"
                + "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                + "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,"
                + "这是用于检测字幕字符集的中文文本，这是用于检测字幕字符集的中文文本。正文,逗号保留。\n";
        assertEquals(chinese, new String(AssInput.normalize(chinese.getBytes(Charset.forName("GB18030"))), StandardCharsets.UTF_8));
    }

    @Test public void subtitleDelayAndRendererOffsetAreSubtractedOnceWithFloorRounding() {
        assertEquals(1499, AssInput.timeMs(1_002_499_999L, 1_000_000_000L, 1_000_000L));
        assertEquals(-1, AssInput.timeMs(999_999, 1_000_000, 0));
        assertEquals(2500, AssInput.timeMs(2_000_000, 0, -500_000));
        assertThrows(ArithmeticException.class, () -> AssInput.timeMs(Long.MIN_VALUE, 1, 0));
    }

    @Test public void oversizedAndContainerInputsAreRejectedBeforeNative() {
        assertThrows(IllegalArgumentException.class, () -> AssInput.copy(ByteBuffer.allocate(AssInput.MAX_INPUT_BYTES + 1)));
        assertThrows(IllegalArgumentException.class, () -> AssInput.normalize("Dialogue: 0,0,Default,text".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> AssInput.normalize((SCRIPT + "[Fonts]\nfontname:test").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> AssInput.normalize((SCRIPT + "\0more").getBytes(StandardCharsets.UTF_8)));
    }
}
