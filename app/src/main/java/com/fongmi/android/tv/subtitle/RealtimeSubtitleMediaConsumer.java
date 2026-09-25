package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;

import java.util.Objects;

public final class RealtimeSubtitleMediaConsumer implements PlaybackMediaSignalHub.Consumer {

    public interface Listener {
        boolean isListening();

        void onPcm(PlaybackMediaSignalHub.PcmFrame frame);

        void onReset(PlaybackMediaSignalHub.Lifecycle event);
    }

    private final Listener listener;

    public RealtimeSubtitleMediaConsumer(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public void onPcm(PlaybackMediaSignalHub.PcmFrame frame) {
        if (listener.isListening()) listener.onPcm(frame);
    }

    @Override
    public void onLifecycle(PlaybackMediaSignalHub.Lifecycle event) {
        listener.onReset(event);
    }
}
