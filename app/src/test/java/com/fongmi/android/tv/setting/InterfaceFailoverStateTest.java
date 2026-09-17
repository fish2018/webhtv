package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InterfaceFailoverStateTest {

    @Test
    public void automaticRoundUsesEachCandidateOnceAndStopsAtThreeAttempts() {
        InterfaceFailoverState state = new InterfaceFailoverState(
                InterfaceFailoverPolicy.AUTO, "a", Arrays.asList("b", "b", "c", "d"));

        assertEquals("b", state.nextAutomatic());
        assertTrue(state.shouldContinueAfterFailure());
        assertEquals("c", state.nextAutomatic());
        assertFalse(state.shouldContinueAfterFailure());
        assertNull(state.nextAutomatic());
    }

    @Test
    public void confirmRoundWaitsForSelectionAndAllowsOnlyOneAttempt() {
        InterfaceFailoverState state = new InterfaceFailoverState(
                InterfaceFailoverPolicy.CONFIRM, "a", Arrays.asList("b", "c"));

        assertNull(state.nextAutomatic());
        assertEquals("c", state.select(1));
        assertNull(state.select(0));
        assertFalse(state.shouldContinueAfterFailure());
    }

    @Test
    public void cancelledRoundNeverStartsAnAttempt() {
        InterfaceFailoverState state = new InterfaceFailoverState(
                InterfaceFailoverPolicy.AUTO, "a", Arrays.asList("b", "c"));

        state.cancel();

        assertNull(state.nextAutomatic());
        assertFalse(state.shouldContinueAfterFailure());
        assertNull(state.select(0));
    }
}
