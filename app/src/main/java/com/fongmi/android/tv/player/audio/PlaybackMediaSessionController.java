package com.fongmi.android.tv.player.audio;

import java.util.Objects;

public final class PlaybackMediaSessionController {

    private final PlaybackMediaSignalHub hub;
    private final PlaybackMediaClock clock;
    private boolean released;

    public PlaybackMediaSessionController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock) {
        this.hub = Objects.requireNonNull(hub, "hub");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized PlaybackMediaSignalHub.Session begin(long mediaAnchorMs) {
        ensureOpen();
        PlaybackMediaSignalHub.Session session = hub.beginSession(mediaAnchorMs);
        clock.reset(session.generation(), session.mediaAnchorMs());
        return session;
    }

    public synchronized PlaybackMediaSignalHub.Session beforeSeek(long targetMediaPositionMs) {
        return reset(targetMediaPositionMs, PlaybackMediaSignalHub.ResetReason.SEEK);
    }

    public synchronized PlaybackMediaSignalHub.Session reset(
            long mediaAnchorMs, PlaybackMediaSignalHub.ResetReason reason) {
        ensureOpen();
        PlaybackMediaSignalHub.Session session = hub.resetTimeline(mediaAnchorMs, reason);
        clock.reset(session.generation(), session.mediaAnchorMs());
        return session;
    }

    public synchronized void release() {
        beforeRelease();
        close();
    }

    public synchronized void beforeRelease() {
        if (released) return;
        reset(hub.session().mediaAnchorMs(), PlaybackMediaSignalHub.ResetReason.RELEASE);
        hub.detachPipeline();
    }

    public synchronized void close() {
        if (released) return;
        released = true;
        hub.close();
    }

    private void ensureOpen() {
        if (released) throw new IllegalStateException("media session controller is released");
    }
}
