package com.fongmi.android.tv.subtitle;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

@UnstableApi
public final class RealtimeSubtitleBufferSizeProvider implements DefaultAudioSink.AudioTrackBufferSizeProvider {

    public static final int LOOKAHEAD_MS = 10_000;

    private final DefaultAudioSink.AudioTrackBufferSizeProvider delegate;

    public RealtimeSubtitleBufferSizeProvider() {
        this(DefaultAudioSink.AudioTrackBufferSizeProvider.DEFAULT);
    }

    RealtimeSubtitleBufferSizeProvider(DefaultAudioSink.AudioTrackBufferSizeProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getBufferSizeInBytes(int minBufferSizeInBytes, int encoding, int outputMode, int pcmFrameSize, int sampleRate, int bitrate, double maxAudioTrackPlaybackSpeed) {
        int normal = delegate.getBufferSizeInBytes(minBufferSizeInBytes, encoding, outputMode, pcmFrameSize, sampleRate, bitrate, maxAudioTrackPlaybackSpeed);
        if (!Util.isEncodingLinearPcm(encoding) || pcmFrameSize <= 0 || sampleRate <= 0) return normal;
        return Math.max(normal, lookaheadBytes(sampleRate, pcmFrameSize, LOOKAHEAD_MS));
    }

    static int lookaheadBytes(int sampleRate, int pcmFrameSize, int durationMs) {
        if (sampleRate <= 0 || pcmFrameSize <= 0 || durationMs <= 0) return 0;
        long requested = (long) sampleRate * pcmFrameSize * durationMs / 1_000L;
        long maxAligned = Integer.MAX_VALUE - (Integer.MAX_VALUE % pcmFrameSize);
        long bounded = Math.min(requested, maxAligned);
        return (int) ((bounded / pcmFrameSize) * pcmFrameSize);
    }
}
