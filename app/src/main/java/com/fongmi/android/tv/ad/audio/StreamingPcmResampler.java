package com.fongmi.android.tv.ad.audio;

import java.util.Arrays;

final class StreamingPcmResampler {

    private final int targetRate;
    private short[] buffer = new short[0];
    private int bufferOffset;
    private int bufferCount;
    private long bufferStartFrame;
    private long receivedFrames;
    private long outputIndex;
    private int sourceRate;
    private int channels;

    StreamingPcmResampler(int targetRate) {
        if (targetRate < 8_000 || targetRate > 192_000) {
            throw new IllegalArgumentException("target sample rate is invalid");
        }
        this.targetRate = targetRate;
    }

    short[] append(short[] samples, int sampleRate, int channelCount) {
        validate(samples, sampleRate, channelCount);
        if (sourceRate == 0) {
            sourceRate = sampleRate;
            channels = channelCount;
        } else if (sourceRate != sampleRate || channels != channelCount) {
            throw new IllegalArgumentException("PCM format changed without reset");
        }
        int frames = samples.length / channelCount;
        ensureCapacity(bufferCount + frames);
        for (int frame = 0; frame < frames; frame++) {
            long sum = 0;
            int offset = frame * channelCount;
            for (int channel = 0; channel < channelCount; channel++) sum += samples[offset + channel];
            buffer[bufferOffset + bufferCount++] = (short) (sum / channelCount);
        }
        receivedFrames += frames;
        return drain(false);
    }

    short[] flush() {
        if (sourceRate == 0 || receivedFrames == 0) {
            reset();
            return new short[0];
        }
        short[] output = drain(true);
        reset();
        return output;
    }

    void reset() {
        bufferOffset = 0;
        bufferCount = 0;
        bufferStartFrame = 0;
        receivedFrames = 0;
        outputIndex = 0;
        sourceRate = 0;
        channels = 0;
    }

    private short[] drain(boolean endOfInput) {
        ShortArrayBuilder output = new ShortArrayBuilder();
        long finalOutputCount = endOfInput
                ? Math.max(1L, Math.round(receivedFrames * (targetRate / (double) sourceRate)))
                : Long.MAX_VALUE;
        while (outputIndex < finalOutputCount) {
            if (outputIndex > Long.MAX_VALUE / sourceRate) {
                throw new IllegalStateException("PCM stream is too long");
            }
            long numerator = outputIndex * sourceRate;
            long leftFrame = numerator / targetRate;
            long remainder = numerator % targetRate;
            long rightFrame = remainder == 0 ? leftFrame : leftFrame + 1;
            if (!endOfInput && rightFrame >= receivedFrames) break;
            if (leftFrame >= receivedFrames) break;
            if (rightFrame >= receivedFrames) rightFrame = receivedFrames - 1;
            short left = sampleAt(leftFrame);
            short right = sampleAt(rightFrame);
            double fraction = remainder / (double) targetRate;
            output.add((short) Math.round(left * (1.0 - fraction) + right * fraction));
            outputIndex++;
        }
        discardConsumedFrames();
        return output.toArray();
    }

    private short sampleAt(long frame) {
        long relative = frame - bufferStartFrame;
        if (relative < 0 || relative >= bufferCount) throw new IllegalStateException("PCM buffer underflow");
        return buffer[bufferOffset + (int) relative];
    }

    private void discardConsumedFrames() {
        if (sourceRate == 0 || outputIndex > Long.MAX_VALUE / sourceRate) return;
        long keepFrom = outputIndex * sourceRate / targetRate;
        int discard = (int) Math.min(bufferCount, Math.max(0L, keepFrom - bufferStartFrame));
        bufferOffset += discard;
        bufferCount -= discard;
        bufferStartFrame += discard;
        if (bufferCount == 0) bufferOffset = 0;
    }

    private void ensureCapacity(int required) {
        if (required <= buffer.length - bufferOffset) return;
        if (required <= buffer.length) {
            System.arraycopy(buffer, bufferOffset, buffer, 0, bufferCount);
            bufferOffset = 0;
            return;
        }
        int capacity = Math.max(required, Math.max(32, buffer.length * 2));
        short[] expanded = new short[capacity];
        System.arraycopy(buffer, bufferOffset, expanded, 0, bufferCount);
        buffer = expanded;
        bufferOffset = 0;
    }

    private static void validate(short[] samples, int sampleRate, int channels) {
        if (samples == null || samples.length == 0 || sampleRate < 8_000 || sampleRate > 192_000
                || channels < 1 || channels > 8 || samples.length % channels != 0) {
            throw new IllegalArgumentException("PCM input is invalid");
        }
    }

    private static final class ShortArrayBuilder {
        private short[] values = new short[32];
        private int size;

        void add(short value) {
            if (size == values.length) values = Arrays.copyOf(values, values.length * 2);
            values[size++] = value;
        }

        short[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
