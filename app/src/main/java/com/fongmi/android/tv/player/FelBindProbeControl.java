package com.fongmi.android.tv.player;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/** A per-player request mailbox. Web status reads never call native code. */
public final class FelBindProbeControl {
    private static final Set<String> STATES = Set.of("unavailable", "ready", "armed", "quiescing",
            "running", "completed", "cancelled", "failed", "timed-out");
    private static final Set<String> ACTIVE = Set.of("queued", "armed", "quiescing", "running", "cancelling");
    private final String owner;
    private Executor executor;
    private Predicate<String> command;
    private long epoch, serial, awaitingSerial = -1;
    private boolean attached, startSent;
    private String state = "unavailable";

    public FelBindProbeControl(String owner) { this.owner = owner; }
    public String owner() { return owner; }

    public synchronized void connect(Executor executor, Predicate<String> command) {
        this.executor = executor;
        this.command = command;
    }

    public synchronized void begin() {
        epoch++; attached = true; startSent = false; serial = 0; awaitingSerial = -1;
        state = "unavailable";
    }
    public synchronized void end() { epoch++; attached = false; state = "unavailable"; }
    public synchronized String state() { return state; }
    public synchronized boolean active() {
        return ACTIVE.contains(state);
    }

    public synchronized void observed(String value) {
        if (!attached || value == null) return;
        int colon = value.indexOf(':');
        if (colon < 1) return;
        long received;
        try { received = Long.parseLong(value.substring(0, colon)); }
        catch (NumberFormatException ignored) { return; }
        String next = value.substring(colon + 1);
        if (received < 0 || !STATES.contains(next)) return;
        // Native serials distinguish a previous run's queued notifications.
        if (awaitingSerial >= 0 && received < awaitingSerial) return;
        serial = received;
        state = next;
        if (!active()) awaitingSerial = -1;
    }

    public synchronized void start() {
        if (!attached || executor == null || state.equals("unavailable"))
            throw new IllegalStateException("请先使用 MPV 播放 Vulkan FEL 视频");
        if (active()) throw new IllegalStateException("绑定对照已在运行");
        state = "queued";
        startSent = false;
        awaitingSerial = serial + 1;
        enqueue("start");
    }

    public synchronized void cancel() {
        if (!attached || !active() || state.equals("cancelling")) return;
        if (!startSent && state.equals("queued")) {
            epoch++; // Invalidate a start still queued on the player thread.
            state = "cancelled";
            awaitingSerial = -1;
            return;
        }
        state = "cancelling";
        enqueue("cancel");
    }

    private void enqueue(String action) {
        long requestedEpoch = epoch;
        executor.execute(() -> {
            Predicate<String> sink;
            synchronized (this) {
                if (!attached || requestedEpoch != epoch) return;
                // Cancellation before the posted start must not run a probe.
                if (action.equals("start") && state.equals("cancelling")) return;
                if (action.equals("start")) startSent = true;
                sink = command;
            }
            boolean accepted;
            try { accepted = sink != null && sink.test(action); }
            catch (RuntimeException ignored) { accepted = false; }
            synchronized (this) {
                if (attached && requestedEpoch == epoch && !accepted) state = "failed";
                // A successful cancel only requests cleanup. Remain exclusive
                // until native publishes its terminal state after cleanup.
            }
        });
    }
}
