package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExoDv5GpuRendererTest {

    @Test
    public void rendererFactoryRequiresExplicitOptInAndCompleteProbe() {
        ExoDv5Native.Probe available = new ExoDv5Native.Probe(
                true, ExoDv5Native.REQUIRED_CAPABILITIES, "available");
        ExoDv5Native.Probe unavailable = new ExoDv5Native.Probe(
                false, ExoDv5Native.CAPABILITY_IMAGE_READER, "missing-capability");

        assertFalse(ExoDv5GpuRendererFactory.shouldCreate(false, available));
        assertFalse(ExoDv5GpuRendererFactory.shouldCreate(true, unavailable));
        assertFalse(ExoDv5GpuRendererFactory.shouldCreate(true, null));
        assertTrue(ExoDv5GpuRendererFactory.shouldCreate(true, available));
    }

    @Test
    public void frameActionWaitsSchedulesAndDropsWithinBounds() {
        assertEquals(
                ExoDv5VideoSink.FrameAction.WAIT,
                ExoDv5VideoSink.frameAction(false, false, 0));
        assertEquals(
                ExoDv5VideoSink.FrameAction.RENDER,
                ExoDv5VideoSink.frameAction(false, true, 0));
        assertEquals(
                ExoDv5VideoSink.FrameAction.WAIT,
                ExoDv5VideoSink.frameAction(true, false, 50_001));
        assertEquals(
                ExoDv5VideoSink.FrameAction.RENDER,
                ExoDv5VideoSink.frameAction(true, false, 50_000));
        assertEquals(
                ExoDv5VideoSink.FrameAction.RENDER,
                ExoDv5VideoSink.frameAction(true, false, -30_000));
        assertEquals(
                ExoDv5VideoSink.FrameAction.DROP,
                ExoDv5VideoSink.frameAction(true, false, -30_001));
    }

    @Test
    public void imageTimestampUsesPresentationTimeAndSaturates() {
        assertEquals(1_234_000L,
                ExoDv5VideoSink.imageTimestampNsFor(1_234));
        assertEquals(-1_234_000L,
                ExoDv5VideoSink.imageTimestampNsFor(-1_234));
        assertEquals(Long.MAX_VALUE,
                ExoDv5VideoSink.imageTimestampNsFor(Long.MAX_VALUE));
        assertEquals(Long.MIN_VALUE,
                ExoDv5VideoSink.imageTimestampNsFor(Long.MIN_VALUE));
    }

    @Test
    public void probeCapabilityBitsAreIndependentlyVisible() {
        ExoDv5Native.Probe probe = new ExoDv5Native.Probe(
                false,
                ExoDv5Native.CAPABILITY_IMAGE_READER
                        | ExoDv5Native.CAPABILITY_AHB_IMPORT,
                "missing-capability");

        assertTrue(probe.has(ExoDv5Native.CAPABILITY_IMAGE_READER));
        assertTrue(probe.has(ExoDv5Native.CAPABILITY_AHB_IMPORT));
        assertFalse(probe.has(ExoDv5Native.CAPABILITY_YCBCR_CONVERSION));
        assertFalse(probe.has(ExoDv5Native.CAPABILITY_VULKAN_12));
    }

    @Test
    public void emptyStatsExposeRenderOutcomeCounters() {
        ExoDv5Native.Stats stats = ExoDv5Native.Stats.empty();

        assertEquals(0, stats.renderedFrames());
        assertEquals(0, stats.renderFailures());
    }

    @Test
    public void extractsRpuFromAnnexBWithoutMutatingInput() {
        ByteBuffer sample = ByteBuffer.wrap(new byte[] {
                0, 0, 0, 1, 0x40, 1, 9,
                0, 0, 1, 0x7c, 1, 2, 3,
        });
        sample.position(2);
        int position = sample.position();

        List<byte[]> rpus = ExoDv5GpuRenderer.findRpuNalus(sample);

        assertEquals(position, sample.position());
        assertEquals(1, rpus.size());
        assertArrayEquals(new byte[] {0x7c, 1, 2, 3}, rpus.get(0));
    }

    @Test
    public void extractsRpuFromFourByteLengthPrefixedAccessUnit() {
        ByteBuffer sample = ByteBuffer.wrap(new byte[] {
                0, 0, 0, 3, 0x40, 1, 9,
                0, 0, 0, 4, 0x7c, 1, 2, 3,
        });

        List<byte[]> rpus = ExoDv5GpuRenderer.findRpuNalus(sample);

        assertEquals(1, rpus.size());
        assertArrayEquals(new byte[] {0x7c, 1, 2, 3}, rpus.get(0));
    }
}
