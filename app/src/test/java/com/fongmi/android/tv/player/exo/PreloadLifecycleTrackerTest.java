package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PreloadLifecycleTrackerTest {

    @Test
    public void stateTransitionsAreEdgeTriggeredAndKeepReasons() {
        PreloadLifecycleTracker tracker = new PreloadLifecycleTracker();
        PreloadLifecycleTracker.SessionEvent session = tracker.beginSession();

        PreloadLifecycleTracker.StateEvent waiting = tracker.transition(PreloadLifecycleTracker.State.WAIT_FIRST_FRAME, "session-start");
        assertNull(tracker.transition(PreloadLifecycleTracker.State.WAIT_FIRST_FRAME, "session-start"));
        PreloadLifecycleTracker.StateEvent changedReason = tracker.transition(PreloadLifecycleTracker.State.WAIT_FIRST_FRAME, "player-buffering");

        assertEquals(1, session.sessionId());
        assertEquals(PreloadLifecycleTracker.State.STOPPED, waiting.from());
        assertEquals(PreloadLifecycleTracker.State.WAIT_FIRST_FRAME, waiting.to());
        assertEquals("player-buffering", changedReason.reason());
    }

    @Test
    public void preloadingTransitionShowsResumeSource() {
        PreloadLifecycleTracker tracker = new PreloadLifecycleTracker();
        tracker.beginSession();
        tracker.transition(PreloadLifecycleTracker.State.PAUSED_AUTO, "auto-constrained");

        PreloadLifecycleTracker.StateEvent resumed = tracker.transition(PreloadLifecycleTracker.State.PRELOADING, "task-start");

        assertEquals(PreloadLifecycleTracker.State.PAUSED_AUTO, resumed.from());
        assertEquals(PreloadLifecycleTracker.State.PRELOADING, resumed.to());
    }

    @Test
    public void startingNewTaskClosesPreviousTaskAsReplaced() {
        PreloadLifecycleTracker tracker = new PreloadLifecycleTracker();
        tracker.beginSession();

        List<PreloadLifecycleTracker.TaskEvent> first = tracker.startTask(3, 10_000, 20_000);
        List<PreloadLifecycleTracker.TaskEvent> second = tracker.startTask(3, 40_000, 15_000);

        assertEquals(1, first.size());
        assertEquals(PreloadLifecycleTracker.TaskEvent.Type.START, first.get(0).type());
        assertEquals(2, second.size());
        assertEquals(PreloadLifecycleTracker.TaskEvent.Outcome.REPLACED, second.get(0).outcome());
        assertEquals(PreloadLifecycleTracker.TaskEvent.Type.START, second.get(1).type());
        assertEquals(first.get(0).taskId() + 1, second.get(1).taskId());
    }

    @Test
    public void taskCompletionAndCancellationAreRecordedOnlyOnce() {
        PreloadLifecycleTracker tracker = new PreloadLifecycleTracker();
        tracker.beginSession();
        tracker.startTask(8, 5_000, 10_000);

        PreloadLifecycleTracker.TaskEvent completed = tracker.endTask(PreloadLifecycleTracker.TaskEvent.Outcome.COMPLETED);
        assertNull(tracker.endTask(PreloadLifecycleTracker.TaskEvent.Outcome.CANCELLED));

        assertEquals(PreloadLifecycleTracker.TaskEvent.Type.END, completed.type());
        assertEquals(PreloadLifecycleTracker.TaskEvent.Outcome.COMPLETED, completed.outcome());
        assertEquals(8, completed.generation());
    }

    @Test
    public void internalWorkerFailureIsRecordedOnlyOnce() {
        PreloadLifecycleTracker tracker = new PreloadLifecycleTracker();
        tracker.beginSession();
        tracker.startTask(9, 5_000, 10_000);

        PreloadLifecycleTracker.TaskEvent failed = tracker.endTask(PreloadLifecycleTracker.TaskEvent.Outcome.INTERNAL_ERROR);

        assertEquals(PreloadLifecycleTracker.TaskEvent.Type.END, failed.type());
        assertEquals(PreloadLifecycleTracker.TaskEvent.Outcome.INTERNAL_ERROR, failed.outcome());
        assertEquals("internal-error", failed.outcome().label());
        assertNull(tracker.endTask(PreloadLifecycleTracker.TaskEvent.Outcome.CANCELLED));
    }

    @Test
    public void sessionsAreIndependentAndEndClearsActiveTask() {
        PreloadLifecycleTracker tracker = new PreloadLifecycleTracker();
        long firstSession = tracker.beginSession().sessionId();
        tracker.startTask(1, 0, 5_000);
        PreloadLifecycleTracker.SessionEvent ended = tracker.endSession("replace-media");
        long secondSession = tracker.beginSession().sessionId();

        assertEquals(firstSession, ended.sessionId());
        assertEquals("replace-media", ended.reason());
        assertTrue(secondSession > firstSession);
        assertNull(tracker.endTask(PreloadLifecycleTracker.TaskEvent.Outcome.CANCELLED));
    }

    @Test
    public void storagePauseCanFollowAnActiveTaskCancellation() {
        PreloadLifecycleTracker tracker = new PreloadLifecycleTracker();
        tracker.beginSession();
        tracker.startTask(2, 10_000, 20_000);

        assertTrue(tracker.hasActiveTask());
        tracker.endTask(PreloadLifecycleTracker.TaskEvent.Outcome.CANCELLED);
        PreloadLifecycleTracker.StateEvent paused = tracker.transition(PreloadLifecycleTracker.State.PAUSED_STORAGE, "storage-reclaim-required");

        assertFalse(tracker.hasActiveTask());
        assertEquals(PreloadLifecycleTracker.State.PAUSED_STORAGE, paused.to());
        assertEquals("storage-reclaim-required", paused.reason());
    }

    @Test
    public void memoryPauseIsDistinctFromNetworkAndStoragePauses() {
        PreloadLifecycleTracker tracker = new PreloadLifecycleTracker();
        tracker.beginSession();
        tracker.startTask(4, 20_000, 10_000);
        tracker.endTask(PreloadLifecycleTracker.TaskEvent.Outcome.CANCELLED);

        PreloadLifecycleTracker.StateEvent paused = tracker.transition(
                PreloadLifecycleTracker.State.PAUSED_MEMORY,
                "critical-pressure");

        assertEquals(PreloadLifecycleTracker.State.PAUSED_MEMORY, paused.to());
        assertEquals("critical-pressure", paused.reason());
    }
}
