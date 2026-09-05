package com.fongmi.android.tv.player.audio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class PlaybackMediaSignalHub implements AutoCloseable {

    public enum ResetReason { SEEK, SOURCE_CHANGED, AUDIO_FLUSH, ENGINE_REBUILD, RELEASE }
    public enum ConsumerKind { REALTIME_SUBTITLE, AD_AUDIO, TEST }

    public record Session(long id, long generation, long mediaAnchorMs) {
        public PcmFrame frame(float[] monoSamples, int sampleRate, long captureStartTimeMs) {
            return new PcmFrame(id, generation, monoSamples, sampleRate, captureStartTimeMs);
        }
    }

    public record PcmFrame(long sessionId, long generation, float[] monoSamples,
                           int sampleRate, long captureStartTimeMs) {
        public PcmFrame {
            Objects.requireNonNull(monoSamples, "monoSamples");
        }
    }

    public record Lifecycle(long sessionId, long generation, ResetReason reason,
                            long mediaAnchorMs) {
        public Lifecycle {
            Objects.requireNonNull(reason, "reason");
        }
    }

    public interface Consumer {
        default void onPcm(PcmFrame frame) {
        }

        default void onLifecycle(Lifecycle event) {
        }

        default void onFailure(RuntimeException error) {
        }
    }

    public interface Registration extends AutoCloseable {
        boolean isClosed();

        @Override
        void close();
    }

    public interface CaptureLease extends AutoCloseable {
        ConsumerKind kind();

        boolean isClosed();

        @Override
        void close();
    }

    public interface PipelineLease extends AutoCloseable {
        boolean isCurrent();

        @Override
        void close();
    }

    private final Object lock = new Object();
    private final int maxRegistrations;
    private final List<ConsumerRegistration> registrations = new ArrayList<>();
    private final EnumMap<ConsumerKind, Integer> captureLeases = new EnumMap<>(ConsumerKind.class);
    private Session session = new Session(0L, 0L, 0L);
    private long nextSessionId;
    private PipelineLeaseImpl activePipeline;
    private boolean closed;

    public PlaybackMediaSignalHub(int maxRegistrations) {
        if (maxRegistrations <= 0) throw new IllegalArgumentException("maxRegistrations must be positive");
        this.maxRegistrations = maxRegistrations;
    }

    public Registration register(String id, Executor executor, int mailboxCapacity, Consumer consumer) {
        ConsumerRegistration registration = new ConsumerRegistration(
                Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(executor, "executor"),
                mailboxCapacity,
                Objects.requireNonNull(consumer, "consumer"));
        synchronized (lock) {
            ensureOpen();
            if (registrations.size() >= maxRegistrations) {
                throw new IllegalStateException("too many media signal consumers");
            }
            for (ConsumerRegistration current : registrations) {
                if (!current.isClosed() && current.id.equals(id)) {
                    throw new IllegalArgumentException("consumer id is already registered: " + id);
                }
            }
            registrations.add(registration);
        }
        return registration;
    }

    public Session beginSession(long mediaAnchorMs) {
        List<ConsumerRegistration> snapshot;
        Lifecycle event;
        synchronized (lock) {
            ensureOpen();
            session = new Session(++nextSessionId, 0L, Math.max(0L, mediaAnchorMs));
            event = lifecycle(ResetReason.SOURCE_CHANGED);
            snapshot = snapshotRegistrations();
        }
        resetRegistrations(snapshot, event);
        return session;
    }

    public Session resetTimeline(long mediaAnchorMs, ResetReason reason) {
        Objects.requireNonNull(reason, "reason");
        List<ConsumerRegistration> snapshot;
        Lifecycle event;
        synchronized (lock) {
            ensureOpen();
            long generation = session.generation() == Long.MAX_VALUE ? 0L : session.generation() + 1L;
            session = new Session(session.id(), generation, Math.max(0L, mediaAnchorMs));
            event = lifecycle(reason);
            snapshot = snapshotRegistrations();
        }
        resetRegistrations(snapshot, event);
        return session;
    }

    public Session session() {
        synchronized (lock) {
            return session;
        }
    }

    public CaptureLease requestCapture(ConsumerKind kind) {
        Objects.requireNonNull(kind, "kind");
        synchronized (lock) {
            ensureOpen();
            captureLeases.put(kind, captureLeases.getOrDefault(kind, 0) + 1);
        }
        return new CaptureLeaseImpl(kind);
    }

    public boolean isCaptureRequested(ConsumerKind kind) {
        Objects.requireNonNull(kind, "kind");
        synchronized (lock) {
            return !closed && captureLeases.getOrDefault(kind, 0) > 0;
        }
    }

    public boolean isCaptureRequested() {
        synchronized (lock) {
            return !closed && !captureLeases.isEmpty();
        }
    }

    public PipelineLease attachPipeline() {
        synchronized (lock) {
            ensureOpen();
            PipelineLeaseImpl lease = new PipelineLeaseImpl();
            activePipeline = lease;
            return lease;
        }
    }

    public void detachPipeline() {
        synchronized (lock) {
            activePipeline = null;
        }
    }

    public boolean isPipelineAttached() {
        synchronized (lock) {
            return !closed && activePipeline != null;
        }
    }

    public void publishPcm(PcmFrame frame) {
        Objects.requireNonNull(frame, "frame");
        List<ConsumerRegistration> snapshot;
        synchronized (lock) {
            if (closed || frame.sessionId() != session.id()
                    || frame.generation() != session.generation()) return;
            snapshot = snapshotRegistrations();
        }
        for (ConsumerRegistration registration : snapshot) registration.offerPcm(frame);
    }

    @Override
    public void close() {
        List<ConsumerRegistration> snapshot;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            snapshot = snapshotRegistrations();
            registrations.clear();
            captureLeases.clear();
            activePipeline = null;
        }
        for (ConsumerRegistration registration : snapshot) registration.close();
    }

    private Lifecycle lifecycle(ResetReason reason) {
        return new Lifecycle(session.id(), session.generation(), reason, session.mediaAnchorMs());
    }

    private List<ConsumerRegistration> snapshotRegistrations() {
        return new ArrayList<>(registrations);
    }

    private static void resetRegistrations(List<ConsumerRegistration> registrations, Lifecycle event) {
        for (ConsumerRegistration registration : registrations) registration.reset(event);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("media signal hub is closed");
    }

    private interface Signal {
    }

    private record PcmSignal(PcmFrame frame) implements Signal {
    }

    private record LifecycleSignal(Lifecycle event) implements Signal {
    }

    private final class ConsumerRegistration implements Registration {
        private final Object mailboxLock = new Object();
        private final String id;
        private final Executor executor;
        private final int mailboxCapacity;
        private final Consumer consumer;
        private final ArrayDeque<Signal> mailbox = new ArrayDeque<>();
        private boolean drainScheduled;
        private boolean registrationClosed;

        private ConsumerRegistration(String id, Executor executor, int mailboxCapacity, Consumer consumer) {
            if (mailboxCapacity <= 0) throw new IllegalArgumentException("mailboxCapacity must be positive");
            this.id = id;
            this.executor = executor;
            this.mailboxCapacity = mailboxCapacity;
            this.consumer = consumer;
        }

        private void offerPcm(PcmFrame frame) {
            synchronized (mailboxLock) {
                if (registrationClosed) return;
                while (mailbox.size() >= mailboxCapacity) mailbox.removeFirst();
                mailbox.addLast(new PcmSignal(frame));
                scheduleDrainLocked();
            }
        }

        private void reset(Lifecycle event) {
            synchronized (mailboxLock) {
                if (registrationClosed) return;
                mailbox.clear();
                mailbox.addLast(new LifecycleSignal(event));
                scheduleDrainLocked();
            }
        }

        private void scheduleDrainLocked() {
            if (drainScheduled) return;
            drainScheduled = true;
            try {
                executor.execute(this::drain);
            } catch (RuntimeException error) {
                drainScheduled = false;
                notifyFailure(error);
            }
        }

        private void drain() {
            while (true) {
                Signal signal;
                synchronized (mailboxLock) {
                    if (registrationClosed) {
                        mailbox.clear();
                        drainScheduled = false;
                        return;
                    }
                    signal = mailbox.pollFirst();
                    if (signal == null) {
                        drainScheduled = false;
                        return;
                    }
                }
                try {
                    if (signal instanceof PcmSignal pcm) consumer.onPcm(pcm.frame());
                    else if (signal instanceof LifecycleSignal lifecycle) consumer.onLifecycle(lifecycle.event());
                } catch (RuntimeException error) {
                    notifyFailure(error);
                }
            }
        }

        private void notifyFailure(RuntimeException error) {
            try {
                consumer.onFailure(error);
            } catch (RuntimeException ignored) {
            }
        }

        @Override
        public boolean isClosed() {
            synchronized (mailboxLock) {
                return registrationClosed;
            }
        }

        @Override
        public void close() {
            synchronized (mailboxLock) {
                if (registrationClosed) return;
                registrationClosed = true;
                mailbox.clear();
            }
            synchronized (lock) {
                registrations.remove(this);
            }
        }
    }

    private final class CaptureLeaseImpl implements CaptureLease {
        private final ConsumerKind kind;
        private boolean leaseClosed;

        private CaptureLeaseImpl(ConsumerKind kind) {
            this.kind = kind;
        }

        @Override
        public ConsumerKind kind() {
            return kind;
        }

        @Override
        public boolean isClosed() {
            synchronized (lock) {
                return leaseClosed;
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (leaseClosed) return;
                leaseClosed = true;
                int count = captureLeases.getOrDefault(kind, 0);
                if (count <= 1) captureLeases.remove(kind);
                else captureLeases.put(kind, count - 1);
            }
        }
    }

    private final class PipelineLeaseImpl implements PipelineLease {

        @Override
        public boolean isCurrent() {
            synchronized (lock) {
                return !closed && activePipeline == this;
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (activePipeline == this) activePipeline = null;
            }
        }
    }
}
