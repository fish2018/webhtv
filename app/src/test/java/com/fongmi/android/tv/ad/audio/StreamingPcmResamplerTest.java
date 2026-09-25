package com.fongmi.android.tv.ad.audio;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class StreamingPcmResamplerTest {

    @Test
    public void chunkBoundariesDoNotChangeResampledSamples() {
        short[] source = ramp(441, 3);
        short[] expected = expectedResample(source, 44_100, 16_000);

        StreamingPcmResampler resampler = new StreamingPcmResampler(16_000);
        short[] first = resampler.append(Arrays.copyOfRange(source, 0, 127), 44_100, 1);
        short[] second = resampler.append(Arrays.copyOfRange(source, 127, 319), 44_100, 1);
        short[] tail = resampler.append(Arrays.copyOfRange(source, 319, source.length), 44_100, 1);
        short[] flushed = resampler.flush();

        assertArrayEquals(expected, concat(first, second, tail, flushed));
    }

    private static short[] expectedResample(short[] source, int sourceRate, int targetRate) {
        int targetLength = Math.max(1, (int) Math.round(source.length * targetRate / (double) sourceRate));
        short[] expected = new short[targetLength];
        for (int i = 0; i < targetLength; i++) {
            double position = i * sourceRate / (double) targetRate;
            int left = Math.min(source.length - 1, (int) position);
            int right = Math.min(source.length - 1, left + 1);
            expected[i] = (short) Math.round(source[left] * (1 - (position - left))
                    + source[right] * (position - left));
        }
        return expected;
    }

    private static short[] ramp(int frames, int step) {
        short[] source = new short[frames];
        for (int i = 0; i < frames; i++) source[i] = (short) (i * step);
        return source;
    }

    private static short[] concat(short[]... arrays) {
        int length = 0;
        for (short[] array : arrays) length += array.length;
        short[] result = new short[length];
        int offset = 0;
        for (short[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
