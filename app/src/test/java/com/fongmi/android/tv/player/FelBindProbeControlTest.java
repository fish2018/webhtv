package com.fongmi.android.tv.player;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FelBindProbeControlTest {
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final List<String> commands = new ArrayList<>();
    private final FelBindProbeControl control = new FelBindProbeControl("player-1");

    private void ready() {
        control.connect(queue::add, action -> { commands.add(action); return true; });
        control.begin();
        control.observed("0:ready");
    }
    private void drain() { while (!queue.isEmpty()) queue.remove().run(); }

    @Test public void explicitRequestOnlyAndExclusive() {
        ready(); drain(); assertTrue(commands.isEmpty());
        control.start(); assertTrue(control.active()); assertTrue(commands.isEmpty());
        assertThrows(IllegalStateException.class, control::start);
        drain(); assertEquals(List.of("start"), commands);
        control.observed("1:completed"); assertFalse(control.active());
    }

    @Test public void queuedStartCannotFollowAnotherMedia() {
        ready(); control.start(); control.begin(); control.observed("0:ready"); drain();
        assertTrue(commands.isEmpty()); assertEquals("ready", control.state());
    }

    @Test public void queuedCancellationDoesNotStartNativeWork() {
        ready(); control.start(); control.cancel(); drain();
        assertTrue(commands.isEmpty()); assertEquals("cancelled", control.state());
    }

    @Test public void cancellationRemainsExclusiveUntilNativeCleanup() {
        ready(); control.start(); drain(); control.observed("1:running");
        control.cancel(); drain(); assertTrue(control.active());
        assertEquals(List.of("start", "cancel"), commands);
        assertThrows(IllegalStateException.class, control::start);
        control.observed("1:cancelled"); assertFalse(control.active());
    }

    @Test public void staleTerminalCannotFinishNewRequest() {
        ready(); control.start(); drain(); control.observed("1:completed");
        control.start(); control.observed("1:completed");
        assertTrue(control.active()); drain();
        control.observed("2:completed"); assertFalse(control.active());
    }

    @Test public void endInvalidatesPostedCommandsAndReadbacks() {
        ready(); control.start(); control.end(); drain(); control.observed("1:running");
        assertTrue(commands.isEmpty()); assertEquals("unavailable", control.state());
    }

    @Test public void nativeRejectionIsReportedAndCanRetry() {
        ready(); control.connect(queue::add, action -> false); control.start(); drain();
        assertEquals("failed", control.state()); assertFalse(control.active());
        control.start(); assertEquals("queued", control.state());
    }

    @Test public void unknownUnavailableAndMalformedStatesCannotArm() {
        control.begin(); control.observed("oops"); control.observed("3:bogus");
        assertThrows(IllegalStateException.class, control::start);
        control.observed("-1:ready"); assertEquals("unavailable", control.state());
    }
}
