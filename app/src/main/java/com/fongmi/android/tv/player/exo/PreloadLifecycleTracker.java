package com.fongmi.android.tv.player.exo;

import java.util.ArrayList;
import java.util.List;

final class PreloadLifecycleTracker {

    private long nextSessionId;
    private long nextTaskId;
    private long sessionId;
    private long activeTaskId = -1;
    private long activeTaskGeneration = -1;
    private long activeTaskStartMs;
    private long activeTaskLengthMs;
    private State state = State.STOPPED;
    private String stateReason = "";

    synchronized SessionEvent beginSession() {
        sessionId = ++nextSessionId;
        activeTaskId = -1;
        activeTaskGeneration = -1;
        activeTaskStartMs = 0;
        activeTaskLengthMs = 0;
        state = State.STOPPED;
        stateReason = "";
        return new SessionEvent(SessionEvent.Type.START, sessionId, "start");
    }

    synchronized SessionEvent endSession(String reason) {
        if (sessionId <= 0) return null;
        SessionEvent event = new SessionEvent(SessionEvent.Type.END, sessionId, normalizeReason(reason));
        sessionId = 0;
        activeTaskId = -1;
        activeTaskGeneration = -1;
        activeTaskStartMs = 0;
        activeTaskLengthMs = 0;
        state = State.STOPPED;
        stateReason = "";
        return event;
    }

    synchronized StateEvent transition(State next, String reason) {
        if (sessionId <= 0 || next == null) return null;
        String normalizedReason = normalizeReason(reason);
        if (state == next && stateReason.equals(normalizedReason)) return null;
        StateEvent event = new StateEvent(sessionId, state, next, normalizedReason);
        state = next;
        stateReason = normalizedReason;
        return event;
    }

    synchronized List<TaskEvent> startTask(long generation, long startMs, long lengthMs) {
        if (sessionId <= 0) return List.of();
        List<TaskEvent> events = new ArrayList<>(2);
        TaskEvent replaced = endTaskLocked(TaskEvent.Outcome.REPLACED);
        if (replaced != null) events.add(replaced);
        activeTaskId = ++nextTaskId;
        activeTaskGeneration = generation;
        activeTaskStartMs = Math.max(0, startMs);
        activeTaskLengthMs = Math.max(0, lengthMs);
        events.add(new TaskEvent(TaskEvent.Type.START, sessionId, activeTaskId, activeTaskGeneration, activeTaskStartMs, activeTaskLengthMs, null));
        return events;
    }

    synchronized TaskEvent endTask(TaskEvent.Outcome outcome) {
        return endTaskLocked(outcome == null ? TaskEvent.Outcome.CANCELLED : outcome);
    }

    synchronized long sessionId() {
        return sessionId;
    }

    synchronized boolean hasActiveTask() {
        return sessionId > 0 && activeTaskId >= 0;
    }

    private TaskEvent endTaskLocked(TaskEvent.Outcome outcome) {
        if (sessionId <= 0 || activeTaskId < 0) return null;
        TaskEvent event = new TaskEvent(TaskEvent.Type.END, sessionId, activeTaskId, activeTaskGeneration, activeTaskStartMs, activeTaskLengthMs, outcome);
        activeTaskId = -1;
        activeTaskGeneration = -1;
        activeTaskStartMs = 0;
        activeTaskLengthMs = 0;
        return event;
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason;
    }

    enum State {
        STOPPED("stopped"),
        WAIT_FIRST_FRAME("wait-first-frame"),
        WAIT_INITIAL_BUFFER("wait-initial-buffer"),
        WAIT_RECOVERY_BUFFER("wait-recovery-buffer"),
        WAIT_NEXT_RANGE("wait-next-range"),
        WAIT_RETRY("wait-retry"),
        PRELOADING("preloading"),
        CANCELLED_BUFFERING("cancelled-buffering"),
        CANCELLED_SEEK("cancelled-seek"),
        NO_RANGE("no-range"),
        PAUSED_AUTO("paused-auto"),
        PAUSED_USER("paused-user"),
        PAUSED_MEMORY("paused-memory"),
        PAUSED_STORAGE("paused-storage"),
        SKIPPED("skipped");

        private final String label;

        State(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record SessionEvent(Type type, long sessionId, String reason) {
        enum Type {
            START,
            END
        }
    }

    record StateEvent(long sessionId, State from, State to, String reason) {
    }

    record TaskEvent(Type type, long sessionId, long taskId, long generation, long startMs, long lengthMs, Outcome outcome) {
        enum Type {
            START,
            END
        }

        enum Outcome {
            COMPLETED("completed"),
            CANCELLED("cancelled"),
            REPLACED("replaced"),
            PREPARE_ERROR("prepare-error"),
            DOWNLOAD_ERROR("download-error"),
            INTERNAL_ERROR("internal-error"),
            START_ERROR("start-error");

            private final String label;

            Outcome(String label) {
                this.label = label;
            }

            String label() {
                return label;
            }
        }
    }
}
