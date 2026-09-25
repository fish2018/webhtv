package com.fongmi.android.tv.ad.audio;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SpectralFingerprintTest {

    private static final AudioFingerprintConfig CONFIG = AudioFingerprintConfig.standard();

    @Test
    public void silenceProducesZeroHashesForEveryPhase() {
        List<int[]> variants = SpectralFingerprint.extractVariants(
                new short[16_000 * 3], 16_000, 1, CONFIG);

        assertEquals(4, variants.size());
        for (int[] sequence : variants) {
            for (int hash : sequence) assertEquals(0, hash);
        }
    }

    @Test
    public void stereoDownmixAndResampleMatchMonoReference() {
        short[] stereo48k = sine(48_000, 3, 500, 2);
        short[] mono16k = sine(16_000, 3, 500, 1);

        assertSequencesEqual(
                SpectralFingerprint.extractVariants(mono16k, 16_000, 1, CONFIG),
                SpectralFingerprint.extractVariants(stereo48k, 48_000, 2, CONFIG));
    }

    @Test
    public void sdkGoldenVectorHasStableHashes() {
        int[] expected = hashes(
                "32f0007c,35c100e0,3b8b01c0,d30a0380,2e0b0700,"
                        + "0c650600,9c560c00,49db0c00,d9161800,70d41800");

        assertArrayEquals(expected,
                SpectralFingerprint.extract(chirp16k(), 16_000, 1, CONFIG));
    }

    @Test
    public void tooShortAudioDoesNotCreateAUsableSequence() {
        assertEquals(0, SpectralFingerprint.extractVariants(
                new short[CONFIG.windowSamples() - 1], 16_000, 1, CONFIG).size());
    }

    private static void assertSequencesEqual(List<int[]> expected, List<int[]> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) assertArrayEquals(expected.get(i), actual.get(i));
    }

    private static int[] hashes(String csv) {
        return Arrays.stream(csv.split(","))
                .mapToInt(value -> (int) Long.parseLong(value, 16))
                .toArray();
    }

    private static short[] sine(int sampleRate, int seconds, double frequency, int channels) {
        short[] output = new short[sampleRate * seconds * channels];
        for (int frame = 0; frame < sampleRate * seconds; frame++) {
            short sample = (short) Math.round(16_000 * Math.sin(2 * Math.PI * frequency * frame / sampleRate));
            for (int channel = 0; channel < channels; channel++) output[frame * channels + channel] = sample;
        }
        return output;
    }

    private static short[] chirp16k() {
        int sampleRate = 16_000;
        int sampleCount = sampleRate * 3;
        double startFrequency = 300;
        double endFrequency = 3_000;
        double rate = (endFrequency - startFrequency) / 3;
        short[] output = new short[sampleCount];
        for (int i = 0; i < output.length; i++) {
            double time = i / (double) sampleRate;
            double phase = 2 * Math.PI * (startFrequency * time + 0.5 * rate * time * time);
            output[i] = (short) Math.round(16_000 * Math.sin(phase));
        }
        return output;
    }
}
