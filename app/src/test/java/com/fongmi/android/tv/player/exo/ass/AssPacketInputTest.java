package com.fongmi.android.tv.player.exo.ass;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

public class AssPacketInputTest {
    private static final long OFFSET = 1_000_000_000_000L;
    private static ByteBuffer sample(int order, String duration, String text) {
        return ByteBuffer.wrap(("Dialogue: 0:00:00:00," + duration + "," + order
                + ",2,OP_CN,,0,0,0,," + text).getBytes(StandardCharsets.UTF_8));
    }

    @Test public void identifiesOnlyThePublishedMatroskaSampleContract() {
        byte[] header = "[Script Info]\n[Events]\n".getBytes(StandardCharsets.UTF_8);
        assertTrue(AssPacketInput.matches(List.of(AssPacketInput.FORMAT.clone(), header)));
        assertFalse(AssPacketInput.matches(List.of(header)));
        assertFalse(AssPacketInput.matches(List.of(header, header)));
        assertFalse(AssPacketInput.matches(List.of(AssPacketInput.FORMAT, new byte[0])));
        assertFalse(AssPacketInput.matches(List.of(AssPacketInput.FORMAT, header, header)));
    }

    @Test public void preservesOriginalReadOrderLayerUtf8OverridesAndTextCommas() {
        AssPacketInput input = new AssPacketInput();
        String text = "{\\fnWEOYTLVC\\k20\\blur2}中文,Text,保留";
        ByteBuffer sample = sample(42, "0:00:01:25", text);
        assertTrue(input.add(sample.asReadOnlyBuffer(), OFFSET + 1_234_999, OFFSET));
        assertEquals(0, sample.position());
        AssPacketInput.Packet packet = input.after(0).get(0);
        assertEquals(1234, packet.startMs());
        assertEquals(1250, packet.durationMs());
        assertEquals("42,2,OP_CN,,0,0,0,," + text, new String(packet.data(), StandardCharsets.UTF_8));
        assertEquals(packet.data().length, input.bytes());
    }

    @Test public void retainsOrderedPacketsAcrossSeekAndDeduplicatesOriginalReadOrder() {
        AssPacketInput input = new AssPacketInput();
        assertTrue(input.add(sample(42, "0:00:10:00", "long"), OFFSET, OFFSET));
        assertTrue(input.add(sample(7, "0:00:01:00", "overlap"), OFFSET + 2_000_000, OFFSET));
        List<AssPacketInput.Packet> beforeSeek = input.after(0);
        assertFalse(input.add(sample(42, "0:00:10:00", "long"), OFFSET, OFFSET));
        assertEquals(2, input.size());
        assertTrue(input.after(2).isEmpty());
        assertTrue(input.add(sample(8, "0:00:01:00", "new"), OFFSET + 3_000_000, OFFSET));
        assertEquals(2, beforeSeek.size());
        assertEquals(1, input.after(2).size());
        assertEquals(3, input.after(0).size());
        assertEquals(0, new AssPacketInput().size());
    }

    @Test public void handlesRendererOffsetWithoutApplyingSubtitleDelayToPackets() {
        AssPacketInput input = new AssPacketInput();
        input.add(sample(0, "1:02:03.45", "negative preroll"), OFFSET - 1, OFFSET);
        assertEquals(-1, input.after(0).get(0).startMs());
        assertEquals(3_723_450, input.after(0).get(0).durationMs());
        assertEquals(500, AssInput.timeMs(OFFSET + 1_000_000, OFFSET, 500_000));
        assertThrows(ArithmeticException.class,
                () -> new AssPacketInput().add(sample(1, "0:00:01:00", "x"), Long.MIN_VALUE, 1));
    }

    @Test public void rejectsMalformedPrefixDurationOrderAndOversizedPackets() {
        assertThrows(IllegalArgumentException.class, () -> new AssPacketInput().add(
                ByteBuffer.wrap("Dialogue: invalid".getBytes(StandardCharsets.UTF_8)), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AssPacketInput().add(sample(0, "0:60:00:00", "x"), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AssPacketInput().add(sample(-1, "0:00:01:00", "x"), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AssPacketInput().add(
                ByteBuffer.allocate(AssInput.MAX_INPUT_BYTES + 1), 0, 0));
    }

    @Test public void capsDistinctEventsButStillAcceptsDuplicatePrerollAtTheLimit() {
        AssPacketInput input = new AssPacketInput();
        for (int i = 0; i < AssInput.MAX_EVENTS; i++) input.add(sample(i, "0:00:01:00", "x"), i * 1000L, 0);
        assertFalse(input.add(sample(0, "0:00:01:00", "x"), 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> input.add(sample(AssInput.MAX_EVENTS, "0:00:01:00", "x"), 0, 0));
        assertEquals(AssInput.MAX_EVENTS, input.size());
    }

    @Test public void capsRetainedBytesBeforeAddingTheNextPacket() {
        AssPacketInput input = new AssPacketInput();
        String text = "x".repeat(AssInput.MAX_INPUT_BYTES - 100);
        input.add(sample(0, "0:00:01:00", text), 0, 0);
        input.add(sample(1, "0:00:01:00", text), 0, 0);
        int bytes = input.bytes();
        assertThrows(IllegalArgumentException.class, () -> input.add(sample(2, "0:00:01:00", text), 0, 0));
        assertEquals(bytes, input.bytes());
        assertEquals(2, input.size());
    }

    @Test public void codecPrivateAllowsLegacyMissingEventsButNeverPreloadsDialogue() {
        byte[] header = "[Script Info]\nScriptType: v4.00+\n[V4+ Styles]\n".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(header, AssInput.normalizeHeader(header));
        assertThrows(IllegalArgumentException.class, () -> AssInput.normalize(header));
        byte[] full = (new String(header, StandardCharsets.UTF_8)
                + "[Events]\nDialogue: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,x\n").getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> AssInput.normalizeHeader(full));
    }
}
