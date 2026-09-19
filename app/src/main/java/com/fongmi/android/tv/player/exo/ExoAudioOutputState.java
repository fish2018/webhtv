package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;
import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioOutputProvider;
import androidx.media3.exoplayer.audio.ForwardingAudioOutput;

import java.util.concurrent.atomic.AtomicReference;

/** The actual output owned by one player, independent of delayed analytics callbacks. */
public final class ExoAudioOutputState {

    private final AtomicReference<Snapshot> current =
            new AtomicReference<>(Snapshot.empty());

    AudioOutput track(AudioOutput output, AudioOutputProvider.OutputConfig config) {
        // A fresh snapshot is also the output's identity, even for identical configurations.
        Snapshot owned = new Snapshot(config.encoding, config.sampleRate,
                Integer.bitCount(config.channelMask), config.isTunneling,
                config.isOffload, true);
        AudioOutput tracked = new ForwardingAudioOutput(output) {
            @Override
            public void release() {
                // Invalidate on release request. The platform's completion callback may arrive
                // after another AudioTrack has already initialized, or after a player rebuild.
                current.compareAndSet(owned, Snapshot.empty());
                super.release();
            }
        };
        current.set(owned);
        return tracked;
    }

    public Snapshot snapshot() {
        return current.get();
    }

    public record Snapshot(int encoding, int sampleRate, int channels,
                           boolean tunneling, boolean offload, boolean initialized) {
        private static final Snapshot EMPTY =
                new Snapshot(C.ENCODING_INVALID, 0, 0, false, false, false);

        public static Snapshot empty() {
            return EMPTY;
        }
    }
}
