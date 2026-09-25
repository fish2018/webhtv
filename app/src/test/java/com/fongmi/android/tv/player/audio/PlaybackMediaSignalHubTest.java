package com.fongmi.android.tv.player.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class PlaybackMediaSignalHubTest {

    @Test
    public void resetDropsOldGenerationAndKeepsOtherConsumersAlive() {
        ManualExecutor throwingExecutor = new ManualExecutor();
        ManualExecutor healthyExecutor = new ManualExecutor();
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(16);
        List<Long> delivered = new ArrayList<>();
        hub.register("throwing", throwingExecutor, 4, new PlaybackMediaSignalHub.Consumer() {
            @Override
            public void onPcm(PlaybackMediaSignalHub.PcmFrame frame) {
                throw new IllegalStateException("isolated");
            }
        });
        hub.register("healthy", healthyExecutor, 4, new PlaybackMediaSignalHub.Consumer() {
            @Override
            public void onPcm(PlaybackMediaSignalHub.PcmFrame frame) {
                delivered.add(frame.generation());
            }
        });
        PlaybackMediaSignalHub.Session first = hub.beginSession(12_000L);
        hub.publishPcm(first.frame(new float[]{0.1f}, 16_000, 0L));
        hub.resetTimeline(42_000L, PlaybackMediaSignalHub.ResetReason.SEEK);
        throwingExecutor.runAll();
        healthyExecutor.runAll();
        assertTrue(delivered.isEmpty());

        PlaybackMediaSignalHub.Session current = hub.session();
        hub.publishPcm(current.frame(new float[]{0.2f}, 16_000, 0L));
        throwingExecutor.runAll();
        healthyExecutor.runAll();
        assertEquals(List.of(current.generation()), delivered);
    }

    @Test
    public void fullMailboxDropsOldestPcm() {
        ManualExecutor executor = new ManualExecutor();
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        List<Float> received = new ArrayList<>();
        hub.register("bounded", executor, 2, new PlaybackMediaSignalHub.Consumer() {
            @Override
            public void onPcm(PlaybackMediaSignalHub.PcmFrame frame) {
                received.add(frame.monoSamples()[0]);
            }
        });
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        hub.publishPcm(session.frame(new float[]{1f}, 16_000, 0L));
        hub.publishPcm(session.frame(new float[]{2f}, 16_000, 1L));
        hub.publishPcm(session.frame(new float[]{3f}, 16_000, 2L));
        executor.runAll();
        assertEquals(List.of(2f, 3f), received);
    }

    @Test
    public void resetDeliversLifecycleAndCloseStopsLateCallbacks() {
        ManualExecutor executor = new ManualExecutor();
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        List<PlaybackMediaSignalHub.ResetReason> reasons = new ArrayList<>();
        PlaybackMediaSignalHub.Registration registration = hub.register(
                "lifecycle", executor, 4, new PlaybackMediaSignalHub.Consumer() {
                    @Override
                    public void onLifecycle(PlaybackMediaSignalHub.Lifecycle event) {
                        reasons.add(event.reason());
                    }
                });
        hub.beginSession(1_000L);
        executor.runAll();
        hub.resetTimeline(2_000L, PlaybackMediaSignalHub.ResetReason.SEEK);
        registration.close();
        registration.close();
        executor.runAll();
        assertTrue(registration.isClosed());
        assertEquals(List.of(PlaybackMediaSignalHub.ResetReason.SOURCE_CHANGED), reasons);
    }

    @Test
    public void captureLeaseIsReferenceCounted() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaSignalHub.CaptureLease first = hub.requestCapture(
                PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO);
        PlaybackMediaSignalHub.CaptureLease second = hub.requestCapture(
                PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO);
        assertTrue(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        first.close();
        assertTrue(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        second.close();
        assertTrue(!hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
    }

    @Test
    public void pipelineAttachmentIsExplicitAndClearedOnClose() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(2);
        assertTrue(!hub.isPipelineAttached());
        PlaybackMediaSignalHub.PipelineLease first = hub.attachPipeline();
        assertTrue(hub.isPipelineAttached());
        PlaybackMediaSignalHub.PipelineLease second = hub.attachPipeline();
        assertTrue(!first.isCurrent());
        assertTrue(second.isCurrent());
        first.close();
        assertTrue(hub.isPipelineAttached());
        hub.close();
        assertTrue(!hub.isPipelineAttached());
        assertTrue(!second.isCurrent());
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        void runAll() {
            while (!tasks.isEmpty()) tasks.removeFirst().run();
        }
    }
}
