package com.fongmi.android.tv.player.audio;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

public final class PlaybackMediaAudioProcessor implements AudioProcessor {

    private final PlaybackMediaSignalHub hub;
    private final PlaybackMediaClock clock;
    private final PlaybackMediaAudioPipeline.PipelineGate pipelineGate;
    private AudioFormat pendingFormat = AudioFormat.NOT_SET;
    private AudioFormat inputFormat = AudioFormat.NOT_SET;
    private ByteBuffer buffer = EMPTY_BUFFER;
    private ByteBuffer outputBuffer = EMPTY_BUFFER;
    private boolean inputEnded;
    private long streamFrameCount;

    public PlaybackMediaAudioProcessor(PlaybackMediaSignalHub hub, PlaybackMediaClock clock) {
        this(hub, clock, PlaybackMediaAudioPipeline.attachGate(hub));
    }

    PlaybackMediaAudioProcessor(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                PlaybackMediaAudioPipeline.PipelineGate pipelineGate) {
        this.hub = Objects.requireNonNull(hub, "hub");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pipelineGate = Objects.requireNonNull(pipelineGate, "pipelineGate");
    }

    @Override
    public AudioFormat configure(AudioFormat inputAudioFormat) {
        int encoding = inputAudioFormat.encoding;
        pendingFormat = encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT
                ? inputAudioFormat : AudioFormat.NOT_SET;
        return pendingFormat;
    }

    @Override
    public boolean isActive() {
        return pendingFormat != AudioFormat.NOT_SET;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) return;
        int bytesPerSample = inputFormat.encoding == C.ENCODING_PCM_FLOAT ? 4 : 2;
        int frameSize = Math.max(1, inputFormat.channelCount * bytesPerSample);
        int frameCount = inputBuffer.remaining() / frameSize;
        long startTimeMs = framesToRoundedMs(streamFrameCount, inputFormat.sampleRate);
        long endFrameCount = saturatedAdd(streamFrameCount, frameCount);
        if (pipelineGate.isBoundToCurrentSession() && hub.isCaptureRequested()) {
            float[] samples = toMono(inputBuffer, inputFormat.channelCount, bytesPerSample);
            if (samples.length > 0) {
                PlaybackMediaSignalHub.Session session = hub.session();
                hub.publishPcm(session.frame(samples, inputFormat.sampleRate, startTimeMs));
                clock.onCaptured(session.generation(), framesToCeilMs(endFrameCount, inputFormat.sampleRate));
            }
        }
        streamFrameCount = endFrameCount;
        int size = inputBuffer.remaining();
        if (buffer.capacity() < size) buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        else buffer.clear();
        buffer.put(inputBuffer).flip();
        outputBuffer = buffer;
    }

    @Override
    public void queueEndOfStream() {
        inputEnded = true;
    }

    @Override
    public ByteBuffer getOutput() {
        ByteBuffer output = outputBuffer;
        outputBuffer = EMPTY_BUFFER;
        return output;
    }

    @Override
    public boolean isEnded() {
        return inputEnded && !outputBuffer.hasRemaining();
    }

    @Override
    public void flush(StreamMetadata streamMetadata) {
        inputFormat = pendingFormat;
        outputBuffer = EMPTY_BUFFER;
        inputEnded = false;
        streamFrameCount = 0L;
        if (!pipelineGate.isCurrent()) return;
        PlaybackMediaSignalHub.Session current = hub.session();
        PlaybackMediaSignalHub.Session reset = hub.resetTimeline(
                current.mediaAnchorMs(), PlaybackMediaSignalHub.ResetReason.AUDIO_FLUSH);
        clock.reset(reset.generation(), reset.mediaAnchorMs());
        pipelineGate.bind(reset);
    }

    @Override
    public void reset() {
        pendingFormat = AudioFormat.NOT_SET;
        inputFormat = AudioFormat.NOT_SET;
        buffer = EMPTY_BUFFER;
        outputBuffer = EMPTY_BUFFER;
        inputEnded = false;
        streamFrameCount = 0L;
    }

    public static float[] toMono(ByteBuffer input, int channels, int bytesPerSample) {
        if (channels <= 0 || (bytesPerSample != 2 && bytesPerSample != 4)) return new float[0];
        ByteBuffer source = input.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
        int frames = source.remaining() / (channels * bytesPerSample);
        float[] output = new float[frames];
        for (int frame = 0; frame < frames; frame++) {
            float sum = 0f;
            for (int channel = 0; channel < channels; channel++) {
                sum += bytesPerSample == 2 ? source.getShort() / 32768f : source.getFloat();
            }
            output[frame] = sum / channels;
        }
        return output;
    }

    public static float[] resample(float[] input, int fromRate, int toRate) {
        if (input == null || input.length == 0 || fromRate <= 0 || toRate <= 0) return new float[0];
        if (fromRate == toRate) return input;
        int outputLength = Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                (long) input.length * toRate / fromRate));
        float[] output = new float[outputLength];
        double step = fromRate / (double) toRate;
        for (int i = 0; i < outputLength; i++) {
            double source = i * step;
            int left = Math.min((int) source, input.length - 1);
            int right = Math.min(left + 1, input.length - 1);
            float fraction = (float) (source - left);
            output[i] = input[left] + (input[right] - input[left]) * fraction;
        }
        return output;
    }

    private static long framesToRoundedMs(long frames, int sampleRate) {
        return sampleRate <= 0 ? 0L : Math.max(0L, Math.round(frames * 1_000.0 / sampleRate));
    }

    private static long framesToCeilMs(long frames, int sampleRate) {
        return sampleRate <= 0 ? 0L : Math.max(0L, (long) Math.ceil(frames * 1_000.0 / sampleRate));
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
