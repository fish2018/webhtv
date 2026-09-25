package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InterfaceFailoverPolicyTest {

    @Test
    public void invalidModeFallsBackToDefault() {
        assertEquals(InterfaceFailoverPolicy.AUTO, InterfaceFailoverPolicy.clampMode(-1));
        assertEquals(InterfaceFailoverPolicy.AUTO, InterfaceFailoverPolicy.clampMode(99));
    }

    @Test
    public void autoAndConfirmTriggerFailover() {
        assertTrue(InterfaceFailoverPolicy.shouldFailover(InterfaceFailoverPolicy.AUTO));
        assertTrue(InterfaceFailoverPolicy.shouldFailover(InterfaceFailoverPolicy.CONFIRM));
        assertFalse(InterfaceFailoverPolicy.shouldFailover(InterfaceFailoverPolicy.OFF));
    }

    @Test
    public void attemptLimitNeverExceedsThree() {
        assertEquals(0, InterfaceFailoverPolicy.attemptLimit(0));
        assertEquals(1, InterfaceFailoverPolicy.attemptLimit(1));
        assertEquals(3, InterfaceFailoverPolicy.attemptLimit(8));
    }

    @Test
    public void invalidModesFallBackToDefault() {
        assertTrue(InterfaceFailoverPolicy.shouldFailover(-1));
        assertTrue(InterfaceFailoverPolicy.shouldFailover(99));
    }

    @Test
    public void fallbackLimitLeavesRoomForTheOriginAttempt() {
        assertEquals(0, InterfaceFailoverPolicy.fallbackLimit(0));
        assertEquals(0, InterfaceFailoverPolicy.fallbackLimit(1));
        assertEquals(2, InterfaceFailoverPolicy.fallbackLimit(3));
        assertEquals(2, InterfaceFailoverPolicy.fallbackLimit(8));
    }
}
