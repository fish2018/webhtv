package com.fongmi.android.tv.subtitle;

import static org.junit.Assert.assertEquals;

import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RealtimeSubtitleMediaConsumerTest {

    @Test
    public void forwardsPcmOnlyWhileListeningAndAlwaysForwardsReset() {
        RecordingListener listener = new RecordingListener();
        RealtimeSubtitleMediaConsumer consumer = new RealtimeSubtitleMediaConsumer(listener);
        PlaybackMediaSignalHub.PcmFrame frame = new PlaybackMediaSignalHub.PcmFrame(
                2L, 3L, new float[]{0.25f}, 16_000, 40L);

        consumer.onPcm(frame);
        listener.listening = true;
        consumer.onPcm(frame);
        consumer.onLifecycle(new PlaybackMediaSignalHub.Lifecycle(
                2L, 4L, PlaybackMediaSignalHub.ResetReason.SEEK, 90_000L));

        assertEquals(List.of(frame), listener.frames);
        assertEquals(List.of(PlaybackMediaSignalHub.ResetReason.SEEK), listener.resets);
    }

    private static final class RecordingListener implements RealtimeSubtitleMediaConsumer.Listener {
        private final List<PlaybackMediaSignalHub.PcmFrame> frames = new ArrayList<>();
        private final List<PlaybackMediaSignalHub.ResetReason> resets = new ArrayList<>();
        private boolean listening;

        @Override
        public boolean isListening() {
            return listening;
        }

        @Override
        public void onPcm(PlaybackMediaSignalHub.PcmFrame frame) {
            frames.add(frame);
        }

        @Override
        public void onReset(PlaybackMediaSignalHub.Lifecycle event) {
            resets.add(event.reason());
        }
    }
}
