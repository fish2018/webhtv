package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Clock;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ForwardingRenderer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream;

/** Retains Media3's renderer/clock and uses its recovery contract without preparing a new source. */
final class ExoStartupAudioRenderer extends ForwardingRenderer {
    private final ExoCompressedAudioDirectPolicy policy;
    private int index;
    private MediaSource.MediaPeriodId mediaPeriodId;

    ExoStartupAudioRenderer(Renderer renderer, ExoCompressedAudioDirectPolicy policy) {
        super(renderer);
        this.policy = policy;
    }

    @Override
    public void init(int index, PlayerId playerId, Clock clock) {
        this.index = index;
        super.init(index, playerId, clock);
    }

    @Override
    public void enable(RendererConfiguration configuration, Format[] formats, SampleStream stream,
                       long positionUs, boolean joining, boolean mayRenderStartOfStream,
                       long startPositionUs, long offsetUs, MediaSource.MediaPeriodId mediaPeriodId)
            throws ExoPlaybackException {
        this.mediaPeriodId = mediaPeriodId;
        super.enable(configuration, formats, stream, positionUs, joining, mayRenderStartOfStream,
                startPositionUs, offsetUs, mediaPeriodId);
    }

    @Override
    public void replaceStream(Format[] formats, SampleStream stream, long startPositionUs,
                              long offsetUs, MediaSource.MediaPeriodId mediaPeriodId)
            throws ExoPlaybackException {
        this.mediaPeriodId = mediaPeriodId;
        super.replaceStream(formats, stream, startPositionUs, offsetUs, mediaPeriodId);
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        super.render(positionUs, elapsedRealtimeUs);
        Format stalledFormat = policy.maybeRequestStartupPcmFallback(
                getState() == STATE_STARTED && isReady() && !isEnded());
        if (stalledFormat != null) {
            // A typed application observation, not a fabricated AudioTrack platform error.
            // Media3 reselects and seeks in its existing period/sample queues; the App's costly
            // startInternal/prepare path remains only the fallback if internal recovery fails.
            throw ExoPlaybackException.createForRenderer(new StartupStallException(), getName(),
                    index, stalledFormat, C.FORMAT_HANDLED, mediaPeriodId, true,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED);
        }
    }

    @Override
    public long getDurationToProgressUs(long positionUs, long elapsedRealtimeUs) {
        return Math.min(super.getDurationToProgressUs(positionUs, elapsedRealtimeUs),
                policy.startupProgressIntervalUs());
    }

    static final class StartupStallException extends Exception {
        StartupStallException() {
            super("Vendor audio output accepted startup data but its playback head did not advance");
        }
    }
}
