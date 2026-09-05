package com.fongmi.android.tv.subtitle;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RealtimeSubtitleBufferSizeProviderTest {

    @Test
    public void lookaheadBufferIsFrameAligned() {
        assertEquals(1_920_000, RealtimeSubtitleBufferSizeProvider.lookaheadBytes(
                48_000, 4, 10_000));
        assertEquals(0, RealtimeSubtitleBufferSizeProvider.lookaheadBytes(
                0, 4, 10_000));
    }
}
