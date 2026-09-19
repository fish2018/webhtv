package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HistoryProgressFormatterTest {

    @Test
    public void hidesMissingZeroAndInvalidPositions() {
        assertEquals("", HistoryProgressFormatter.format(-9223372036854775807L, 120000));
        assertEquals("", HistoryProgressFormatter.format(Long.MIN_VALUE, 120000));
        assertEquals("", HistoryProgressFormatter.format(-1, 120000));
        assertEquals("", HistoryProgressFormatter.format(0, 120000));
    }

    @Test
    public void showsMinutesAndSeconds() {
        assertEquals("00:01", HistoryProgressFormatter.format(1000, 120000));
        assertEquals("00:59", HistoryProgressFormatter.format(59000, 120000));
        assertEquals("01:00", HistoryProgressFormatter.format(60000, 120000));
        assertEquals("12:34", HistoryProgressFormatter.format(754000, 3600000));
    }

    @Test
    public void doesNotRoundForwardAtSecondOrHourBoundaries() {
        assertEquals("00:00", HistoryProgressFormatter.format(999, 120000));
        assertEquals("00:59", HistoryProgressFormatter.format(59999, 120000));
        assertEquals("59:59", HistoryProgressFormatter.format(3599999, 7200000));
    }

    @Test
    public void showsHoursWithoutWrappingAtOneDay() {
        assertEquals("1:00:00", HistoryProgressFormatter.format(3600000, 7200000));
        assertEquals("1:02:34", HistoryProgressFormatter.format(3754000, 7200000));
        assertEquals("25:02:03", HistoryProgressFormatter.format(90123000, 100000000));
    }

    @Test
    public void keepsPositionWhenDurationIsUnknown() {
        assertEquals("12:34", HistoryProgressFormatter.format(754000, 0));
        assertEquals("12:34", HistoryProgressFormatter.format(754000, -1));
        assertEquals("12:34", HistoryProgressFormatter.format(754000, -9223372036854775807L));
    }

    @Test
    public void clampsOnlyDisplayToKnownDuration() {
        assertEquals("01:00", HistoryProgressFormatter.format(120000, 60000));
        assertEquals("01:00", HistoryProgressFormatter.format(60000, 60000));
        assertEquals("01:00", HistoryProgressFormatter.format(Long.MAX_VALUE, 60000));
    }

    @Test
    public void supportsLongPositionsWithoutIntOrAdditionOverflow() {
        assertEquals("596:31:23", HistoryProgressFormatter.format(2147483648L, 0));
        assertEquals("2562047788015:12:55", HistoryProgressFormatter.format(Long.MAX_VALUE, 0));
    }
}
