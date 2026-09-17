package com.fongmi.android.tv.ad.audio;

public record AudioFingerprintConfig(int sampleRate, int windowMs, int hopMs, int bandCount) {

    private static final int MIN_SAMPLE_RATE = 8_000;
    private static final int MAX_SAMPLE_RATE = 192_000;
    private static final int MIN_WINDOW_MS = 128;
    private static final int MAX_WINDOW_MS = 4_000;
    private static final int MIN_HOP_MS = 64;
    private static final int MIN_BAND_COUNT = 8;
    private static final int MAX_BAND_COUNT = 16;
    private static final int MAX_FFT_SIZE = 1 << 15;
    private static final long MAX_FFT_POINTS_PER_SECOND = 1L << 17;

    public AudioFingerprintConfig {
        if (sampleRate < MIN_SAMPLE_RATE || sampleRate > MAX_SAMPLE_RATE) {
            throw new IllegalArgumentException("sampleRate out of range");
        }
        if (windowMs < MIN_WINDOW_MS || windowMs > MAX_WINDOW_MS) {
            throw new IllegalArgumentException("windowMs out of range");
        }
        if (hopMs < MIN_HOP_MS || hopMs > windowMs) {
            throw new IllegalArgumentException("hopMs out of range");
        }
        if (bandCount < MIN_BAND_COUNT || bandCount > MAX_BAND_COUNT) {
            throw new IllegalArgumentException("bandCount out of range");
        }
        int windowSamples = Math.max(1, (int) Math.round(sampleRate * windowMs / 1_000.0));
        int fftSize = 1;
        while (fftSize < windowSamples && fftSize <= MAX_FFT_SIZE) fftSize <<= 1;
        long transformsPerSecond = (1_000L + hopMs - 1) / hopMs;
        if (fftSize > MAX_FFT_SIZE || fftSize * transformsPerSecond > MAX_FFT_POINTS_PER_SECOND) {
            throw new IllegalArgumentException("fingerprint FFT workload is too large");
        }
    }

    public static AudioFingerprintConfig standard() {
        return new AudioFingerprintConfig(16_000, 512, 256, 16);
    }

    public int windowSamples() {
        return samplesFor(windowMs);
    }

    public int hopSamples() {
        return samplesFor(hopMs);
    }

    private int samplesFor(int durationMs) {
        return Math.max(1, (int) Math.round(sampleRate * durationMs / 1_000.0));
    }
}
