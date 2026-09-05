package com.fongmi.android.tv.player.audio;

import android.os.SystemClock;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioOutputProvider;
import androidx.media3.exoplayer.audio.ForwardingAudioOutput;
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider;

import java.nio.ByteBuffer;
import java.util.Objects;

@UnstableApi
public final class PlaybackMediaAudioOutputProvider extends ForwardingAudioOutputProvider {

    public interface ClockSink {
        void onSample(long outputId, long writtenUs, long positionUs,
                      long sampledElapsedMs, boolean playing);

        void onReleased(long outputId);
    }

    private final ClockSink sink;
    private long nextOutputId;

    public PlaybackMediaAudioOutputProvider(AudioOutputProvider delegate, ClockSink sink) {
        super(delegate);
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public AudioOutput getAudioOutput(OutputConfig config) throws InitializationException {
        AudioOutput output = super.getAudioOutput(config);
        long outputId = ++nextOutputId;
        int channelCount = Integer.bitCount(config.channelMask);
        int frameSize = Util.isEncodingLinearPcm(config.encoding) && channelCount > 0
                ? Util.getPcmFrameSize(config.encoding, channelCount) : C.LENGTH_UNSET;
        sink.onSample(outputId, 0L, 0L, SystemClock.elapsedRealtime(), false);
        return new ForwardingAudioOutput(output) {
            private long writtenFrames;
            private boolean playing;

            @Override
            public boolean write(ByteBuffer buffer, int encodedAccessUnitCount,
                                 long presentationTimeUs) throws WriteException {
                int before = buffer.remaining();
                boolean handled = super.write(buffer, encodedAccessUnitCount, presentationTimeUs);
                int consumed = before - buffer.remaining();
                if (frameSize > 0 && consumed > 0) writtenFrames += consumed / frameSize;
                sample(super.getPositionUs());
                return handled;
            }

            @Override
            public long getPositionUs() {
                long positionUs = super.getPositionUs();
                sample(positionUs);
                return positionUs;
            }

            @Override
            public void play() {
                super.play();
                playing = true;
                sample(super.getPositionUs());
            }

            @Override
            public void pause() {
                playing = false;
                sample(super.getPositionUs());
                super.pause();
            }

            @Override
            public void stop() {
                playing = false;
                sample(super.getPositionUs());
                super.stop();
            }

            @Override
            public void flush() {
                super.flush();
                writtenFrames = 0L;
                sample(0L);
            }

            @Override
            public void release() {
                sink.onReleased(outputId);
                super.release();
            }

            private void sample(long positionUs) {
                long writtenUs = config.sampleRate > 0
                        ? writtenFrames * 1_000_000L / config.sampleRate : 0L;
                sink.onSample(outputId, writtenUs, Math.max(0L, positionUs),
                        SystemClock.elapsedRealtime(), playing);
            }
        };
    }
}
