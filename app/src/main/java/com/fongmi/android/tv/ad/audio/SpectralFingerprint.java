package com.fongmi.android.tv.ad.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SpectralFingerprint {

    private static final int PHASE_VARIANT_COUNT = 4;
    private static final int MAX_FFT_SIZE = 1 << 20;

    private SpectralFingerprint() {
    }

    public static int[] extract(short[] samples, int sampleRate, int channels,
                                AudioFingerprintConfig config) {
        List<int[]> variants = extractVariants(samples, sampleRate, channels, config);
        return variants.isEmpty() ? new int[0] : variants.get(0).clone();
    }

    public static List<int[]> extractVariants(short[] samples, int sampleRate, int channels,
                                              AudioFingerprintConfig config) {
        if (config == null) throw new IllegalArgumentException("config is required");
        short[] mono = toTargetMono(samples, sampleRate, channels, config.sampleRate());
        if (mono.length == 0) return List.of();
        int windowSamples = millisecondsToSamples(config.windowMs(), config.sampleRate());
        int hopSamples = millisecondsToSamples(config.hopMs(), config.sampleRate());
        int phaseStep = Math.max(1, hopSamples / 4);
        Workspace workspace = new Workspace(windowSamples, config.sampleRate(), config.bandCount());
        List<int[]> variants = new ArrayList<>(PHASE_VARIANT_COUNT);
        for (int phase = 0; phase < PHASE_VARIANT_COUNT; phase++) {
            List<Integer> hashes = new ArrayList<>();
            for (int start = phase * phaseStep; start + windowSamples <= mono.length; start += hopSamples) {
                hashes.add(workspace.hashWindow(mono, start));
            }
            if (hashes.size() >= AudioFingerprintRule.MIN_SEQUENCE_FRAMES) {
                int[] sequence = new int[hashes.size()];
                for (int i = 0; i < hashes.size(); i++) sequence[i] = hashes.get(i);
                variants.add(sequence);
            }
        }
        return Collections.unmodifiableList(variants);
    }

    static short[] toTargetMono(short[] samples, int sampleRate, int channels, int targetRate) {
        if (samples == null || samples.length == 0) return new short[0];
        StreamingPcmResampler resampler = new StreamingPcmResampler(targetRate);
        short[] output = resampler.append(samples, sampleRate, channels);
        short[] tail = resampler.flush();
        if (tail.length == 0) return output;
        short[] combined = Arrays.copyOf(output, output.length + tail.length);
        System.arraycopy(tail, 0, combined, output.length, tail.length);
        return combined;
    }

    static int hashWindow(short[] samples, int start, int windowSamples, int sampleRate, int bandCount) {
        return new Workspace(windowSamples, sampleRate, bandCount).hashWindow(samples, start);
    }

    static final class Workspace {
        private final int windowSamples;
        private final int sampleRate;
        private final int bandCount;
        private final int fftSize;
        private final double[] real;
        private final double[] imaginary;
        private final double[] bands;

        Workspace(int windowSamples, int sampleRate, int bandCount) {
            this.windowSamples = windowSamples;
            this.sampleRate = sampleRate;
            this.bandCount = bandCount;
            fftSize = nextPowerOfTwo(windowSamples);
            if (fftSize > MAX_FFT_SIZE) throw new IllegalArgumentException("window is too large");
            real = new double[fftSize];
            imaginary = new double[fftSize];
            bands = new double[bandCount];
        }

        int hashWindow(short[] samples, int start) {
            Arrays.fill(real, 0.0);
            Arrays.fill(imaginary, 0.0);
            double energy = 0;
            for (int i = 0; i < windowSamples; i++) {
                double normalized = samples[start + i] / 32768.0;
                energy += normalized * normalized;
                double window = 0.5 - Math.cos(i * 2.0 * Math.PI / Math.max(1, windowSamples - 1)) * 0.5;
                real[i] = normalized * window;
            }
            if (energy < 1.0E-7) return 0;

            fft(real, imaginary);
            Arrays.fill(bands, 0.0);
            double maxFrequency = Math.min(6200.0, sampleRate / 2.0 - 1.0);
            double ratio = maxFrequency / 180.0;
            for (int band = 0; band < bandCount; band++) {
                double lower = 180.0 * Math.pow(ratio, band / (double) bandCount);
                double upper = 180.0 * Math.pow(ratio, (band + 1) / (double) bandCount);
                int first = Math.max(1, (int) Math.floor(lower * fftSize / sampleRate));
                int last = Math.min(fftSize / 2,
                        Math.max(first + 1, (int) Math.ceil(upper * fftSize / sampleRate)));
                double power = 0;
                for (int bin = first; bin < last; bin++) {
                    power += real[bin] * real[bin] + imaginary[bin] * imaginary[bin];
                }
                bands[band] = Math.log1p(power / Math.max(1, last - first));
            }

            double mean = 0;
            for (double band : bands) mean += band;
            mean /= bandCount;
            int hash = 0;
            for (int band = 0; band < bandCount && band < 16; band++) {
                if (bands[band] >= mean) hash |= 1 << band;
                if (bands[band] >= bands[(band + 1) % bandCount]) hash |= 1 << (band + 16);
            }
            return hash;
        }
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value && result < MAX_FFT_SIZE) result <<= 1;
        return result;
    }

    private static int millisecondsToSamples(int milliseconds, int sampleRate) {
        return Math.max(1, (int) Math.round(milliseconds * sampleRate / 1000.0));
    }

    private static void fft(double[] real, double[] imaginary) {
        int length = real.length;
        int bitReversed = 0;
        for (int i = 1; i < length; i++) {
            int bit = length >> 1;
            while ((bitReversed & bit) != 0) {
                bitReversed ^= bit;
                bit >>= 1;
            }
            bitReversed ^= bit;
            if (i < bitReversed) {
                double realValue = real[i];
                real[i] = real[bitReversed];
                real[bitReversed] = realValue;
                double imaginaryValue = imaginary[i];
                imaginary[i] = imaginary[bitReversed];
                imaginary[bitReversed] = imaginaryValue;
            }
        }

        for (int blockSize = 2; blockSize <= length; blockSize <<= 1) {
            double angle = -2.0 * Math.PI / blockSize;
            double phaseReal = Math.cos(angle);
            double phaseImaginary = Math.sin(angle);
            int half = blockSize / 2;
            for (int block = 0; block < length; block += blockSize) {
                double currentReal = 1.0;
                double currentImaginary = 0.0;
                for (int i = 0; i < half; i++) {
                    int left = block + i;
                    int right = left + half;
                    double productReal = currentReal * real[right] - currentImaginary * imaginary[right];
                    double productImaginary = currentImaginary * real[right] + currentReal * imaginary[right];
                    real[right] = real[left] - productReal;
                    imaginary[right] = imaginary[left] - productImaginary;
                    real[left] += productReal;
                    imaginary[left] += productImaginary;
                    double nextReal = currentReal * phaseReal - currentImaginary * phaseImaginary;
                    currentImaginary = currentImaginary * phaseReal + currentReal * phaseImaginary;
                    currentReal = nextReal;
                }
            }
        }
    }
}
