package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MpvDv7HandlingSettingTest {

    @Test
    public void oldStoredValuesAndDefaultRemainUnchanged() {
        assertEquals(0, PlaybackPerformanceSetting.DV7_HANDLING_P81);
        assertEquals(1, PlaybackPerformanceSetting.DV7_HANDLING_HDR10);
        assertEquals(0, PlaybackPerformanceSetting.clampMpvDv7Handling(-1));
        assertEquals(0, PlaybackPerformanceSetting.clampMpvDv7Handling(3));
        assertEquals(0, PlaybackPerformanceSetting.clampMpvDv7Handling(Integer.MAX_VALUE));
    }

    @Test
    public void thirdValueIsReachableOnlyByExplicitSelection() {
        assertEquals(1, PlaybackPerformanceSetting.nextMpvDv7HandlingMode(0));
        assertEquals(2, PlaybackPerformanceSetting.nextMpvDv7HandlingMode(1));
        assertEquals(0, PlaybackPerformanceSetting.nextMpvDv7HandlingMode(2));
        assertEquals(2, PlaybackPerformanceSetting.clampMpvDv7Handling(2));
    }

    @Test
    public void labelMatchesTheApprovedName() {
        assertEquals("升级P8.1", PlaybackPerformanceSetting.mpvDv7HandlingText(0));
        assertEquals("降级HDR10", PlaybackPerformanceSetting.mpvDv7HandlingText(1));
        assertEquals("FEL 双层重建", PlaybackPerformanceSetting.mpvDv7HandlingText(2));
    }
}
