package com.fongmi.android.tv.ad.audio;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AudioFingerprintMatcherTest {

    private static final AudioFingerprintConfig CONFIG = AudioFingerprintConfig.standard();
    private static final int[] CHIRP_HASHES = hashes(
            "32f0007c,35c100e0,3b8b01c0,d30a0380,2e0b0700,"
                    + "0c650600,9c560c00,49db0c00,d9161800,70d41800");

    @Test
    public void emitsStartThenFullMatchWithPredictedEnd() {
        AudioFingerprintMatcher matcher = matcherFor(chirpRule());
        List<AudioFingerprintMatcher.MatchEvent> events = feedInChunks(matcher, chirp16k());

        assertEquals(2, events.size());
        assertEquals(AudioFingerprintMatcher.Type.START_MATCHED, events.get(0).type());
        assertEquals("chirp-ad", events.get(0).ruleId());
        assertEquals(0, events.get(0).startTimeMs());
        assertEquals(15_000, events.get(0).endTimeMs());
        assertEquals(6, events.get(0).matchedFrames());
        assertEquals(AudioFingerprintMatcher.Type.FULL_MATCHED, events.get(1).type());
        assertTrue(events.get(1).confidence() >= 0.78f);
    }

    @Test
    public void shortRuleEmitsStartAndFullMatchOnTheSameFrame() {
        AudioFingerprintRule shortRule = new AudioFingerprintRule(
                "short-ad", 15_000, 0, 1_280,
                Arrays.copyOf(CHIRP_HASHES, AudioFingerprintRule.MIN_SEQUENCE_FRAMES), List.of());
        AudioFingerprintMatcher matcher = matcherFor(shortRule);

        List<AudioFingerprintMatcher.MatchEvent> events = feedInChunks(matcher, chirp16k());

        assertEquals(2, events.size());
        assertEquals(AudioFingerprintMatcher.Type.START_MATCHED, events.get(0).type());
        assertEquals(AudioFingerprintMatcher.Type.FULL_MATCHED, events.get(1).type());
    }

    @Test
    public void exactWindowBoundaryDoesNotRequireAnExtraPcmSample() {
        int frames = AudioFingerprintRule.MIN_SEQUENCE_FRAMES;
        int sampleCount = CONFIG.windowSamples() + (frames - 1) * CONFIG.hopSamples();
        AudioFingerprintRule shortRule = new AudioFingerprintRule(
                "exact-boundary", 15_000, 0, 1_280,
                Arrays.copyOf(CHIRP_HASHES, frames), List.of());

        List<AudioFingerprintMatcher.MatchEvent> events = feedInChunks(
                matcherFor(shortRule), Arrays.copyOf(chirp16k(), sampleCount));

        assertEquals(2, events.size());
        assertEquals(AudioFingerprintMatcher.Type.FULL_MATCHED, events.get(1).type());
    }

    @Test
    public void finishFlushesTheFinalFractionalResample() {
        short[] source = Arrays.copyOf(chirp(8_000, 2), 10_240);
        List<int[]> variants = SpectralFingerprint.extractVariants(source, 8_000, 1, CONFIG);
        AudioFingerprintRule rule = new AudioFingerprintRule(
                "fractional-tail", 15_000, 0, 1_280, variants.get(0), List.of());
        AudioFingerprintMatcher matcher = matcherFor(rule);

        assertTrue(matcher.feed(new AudioFingerprintMatcher.PcmChunk(source, 8_000, 1, 0))
                .events().isEmpty());
        AudioFingerprintMatcher.FeedResult result = matcher.finish();

        assertEquals(AudioFingerprintMatcher.Status.MATCHED, result.status());
        assertEquals(2, result.events().size());
        assertEquals(AudioFingerprintMatcher.Type.FULL_MATCHED, result.events().get(1).type());
    }

    @Test
    public void resamplingIsInvariantToPcmChunkBoundaries() {
        short[] source = chirp(48_000, 3);
        List<int[]> variants = SpectralFingerprint.extractVariants(source, 48_000, 1, CONFIG);
        AudioFingerprintRule rule = new AudioFingerprintRule(
                "chunk-invariant", 15_000, 0, 3_000, variants.get(0),
                variants.subList(1, variants.size()));

        List<AudioFingerprintMatcher.MatchEvent> whole = feedInChunks(
                matcherFor(rule), source, 48_000, source.length);
        List<AudioFingerprintMatcher.MatchEvent> split = feedInChunks(
                matcherFor(rule), source, 48_000, 1_024);

        assertEquals(types(whole), types(split));
        assertEquals(2, split.size());
        assertEquals(AudioFingerprintMatcher.Type.FULL_MATCHED, split.get(1).type());
    }

    @Test
    public void timelineGapResetsPendingCandidate() {
        AudioFingerprintMatcher matcher = matcherFor(chirpRule());
        short[] first = Arrays.copyOf(chirp16k(), 10_000);
        short[] second = Arrays.copyOfRange(chirp16k(), 10_000, 20_000);

        assertEquals(AudioFingerprintMatcher.Status.NO_MATCH,
                matcher.feed(new AudioFingerprintMatcher.PcmChunk(first, 16_000, 1, 0)).status());
        assertEquals(AudioFingerprintMatcher.Status.RESET,
                matcher.feed(new AudioFingerprintMatcher.PcmChunk(second, 16_000, 1, 10_000)).status());
    }

    @Test
    public void timelineResetRemainsObservableWhenTheNewChunkMatches() {
        AudioFingerprintMatcher matcher = matcherFor(chirpRule());
        matcher.feed(new AudioFingerprintMatcher.PcmChunk(new short[10_000], 16_000, 1, 0));

        AudioFingerprintMatcher.FeedResult result = matcher.feed(
                new AudioFingerprintMatcher.PcmChunk(chirp16k(), 16_000, 1, 10_000));

        assertEquals(AudioFingerprintMatcher.Status.RESET, result.status());
        assertTrue(!result.events().isEmpty());
    }

    @Test
    public void malformedChunkIsRejectedWithoutThrowing() {
        AudioFingerprintMatcher matcher = matcherFor(chirpRule());

        AudioFingerprintMatcher.FeedResult result = matcher.feed(null);

        assertEquals(AudioFingerprintMatcher.Status.INVALID_INPUT, result.status());
        assertTrue(result.events().isEmpty());
    }

    @Test
    public void oversizedOrMalformedPcmIsRejected() {
        AudioFingerprintMatcher matcher = matcherFor(chirpRule());

        assertEquals(AudioFingerprintMatcher.Status.INVALID_INPUT,
                matcher.feed(new AudioFingerprintMatcher.PcmChunk(new short[16_000 * 31], 16_000, 1, 0)).status());
        assertEquals(AudioFingerprintMatcher.Status.INVALID_INPUT,
                matcher.feed(new AudioFingerprintMatcher.PcmChunk(new short[9], 16_000, 9, 0)).status());
        assertEquals(AudioFingerprintMatcher.Status.INVALID_INPUT,
                matcher.feed(new AudioFingerprintMatcher.PcmChunk(new short[10], 16_000, 3, 0)).status());
        assertEquals(AudioFingerprintMatcher.Status.INVALID_INPUT,
                matcher.feed(new AudioFingerprintMatcher.PcmChunk(
                        new short[1_000_008], 192_000, 8, 0)).status());
    }

    @Test(expected = IllegalArgumentException.class)
    public void matcherConfigRejectsNaNRatio() {
        new AudioFingerprintMatcher.Config(6, 7, Float.NaN, 0.78f, 5_000, 2_500);
    }

    @Test
    public void silenceDoesNotCreateMatch() {
        AudioFingerprintMatcher matcher = matcherFor(chirpRule());

        AudioFingerprintMatcher.FeedResult result = matcher.feed(
                new AudioFingerprintMatcher.PcmChunk(new short[16_000 * 3], 16_000, 1, 0));

        assertEquals(AudioFingerprintMatcher.Status.NO_MATCH, result.status());
        assertTrue(result.events().isEmpty());
    }

    private static AudioFingerprintMatcher matcherFor(AudioFingerprintRule rule) {
        return new AudioFingerprintMatcher(
                new AudioFingerprintRuleSet(CONFIG, List.of(rule)),
                AudioFingerprintMatcher.Config.conservative());
    }

    private static AudioFingerprintRule chirpRule() {
        return new AudioFingerprintRule("chirp-ad", 15_000, 0, 3_000, CHIRP_HASHES, List.of());
    }

    private static List<AudioFingerprintMatcher.MatchEvent> feedInChunks(
            AudioFingerprintMatcher matcher, short[] samples) {
        return feedInChunks(matcher, samples, 16_000, samples.length);
    }

    private static List<AudioFingerprintMatcher.MatchEvent> feedInChunks(
            AudioFingerprintMatcher matcher, short[] samples, int sampleRate, int chunkFrames) {
        List<AudioFingerprintMatcher.MatchEvent> events = new ArrayList<>();
        for (int offset = 0; offset < samples.length; offset += chunkFrames) {
            int length = Math.min(chunkFrames, samples.length - offset);
            events.addAll(matcher.feed(new AudioFingerprintMatcher.PcmChunk(
                    Arrays.copyOfRange(samples, offset, offset + length),
                    sampleRate, 1, offset * 1_000L / sampleRate)).events());
        }
        return events;
    }

    private static List<AudioFingerprintMatcher.Type> types(List<AudioFingerprintMatcher.MatchEvent> events) {
        return events.stream().map(AudioFingerprintMatcher.MatchEvent::type).toList();
    }

    private static int[] hashes(String csv) {
        return Arrays.stream(csv.split(","))
                .mapToInt(value -> (int) Long.parseLong(value, 16))
                .toArray();
    }

    private static short[] chirp16k() {
        return chirp(16_000, 3);
    }

    private static short[] chirp(int sampleRate, int seconds) {
        int sampleCount = sampleRate * seconds;
        double startFrequency = 300;
        double endFrequency = 3_000;
        double rate = (endFrequency - startFrequency) / seconds;
        short[] output = new short[sampleCount];
        for (int i = 0; i < output.length; i++) {
            double time = i / (double) sampleRate;
            double phase = 2 * Math.PI * (startFrequency * time + 0.5 * rate * time * time);
            output[i] = (short) Math.round(16_000 * Math.sin(phase));
        }
        return output;
    }
}
