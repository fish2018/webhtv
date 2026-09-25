package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AdBlockTimeFormatterTest {

    @Test
    public void formatsPlayerStyleClockWithTenths() {
        assertEquals("00:25:29.1", AdBlockTimeFormatter.formatSeconds(1529.1));
        assertEquals("00:00:03.7", AdBlockTimeFormatter.formatSeconds(3.733));
    }

    @Test
    public void carriesRoundedTenthsAcrossClockFields() {
        assertEquals("00:01:00.0", AdBlockTimeFormatter.formatSeconds(59.96));
        assertEquals("01:00:00.0", AdBlockTimeFormatter.formatSeconds(3599.96));
    }

    @Test
    public void clampsInvalidOrNegativeValuesToZero() {
        assertEquals("00:00:00.0", AdBlockTimeFormatter.formatSeconds(-1));
        assertEquals("00:00:00.0", AdBlockTimeFormatter.formatSeconds(Double.NaN));
        assertEquals("00:00:00.0", AdBlockTimeFormatter.formatSeconds(Double.POSITIVE_INFINITY));
    }
}
