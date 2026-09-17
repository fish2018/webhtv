package com.fongmi.android.tv.ad.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AudioFingerprintMatcher {

    private static final long UNSET_TIME_MS = Long.MIN_VALUE;
    private static final int MAX_CHUNK_SECONDS = 30;
    private static final int MAX_CHUNK_TARGET_FRAMES = 1_000_000;
    private static final int MAX_CHUNK_SAMPLES = 1_000_000;

    public enum Type { START_MATCHED, FULL_MATCHED }

    public enum Status { NO_MATCH, MATCHED, RESET, INVALID_INPUT, INTERNAL_ERROR }

    public record PcmChunk(short[] samples, int sampleRate, int channels, long startTimeMs) {
    }

    public record MatchEvent(Type type, String ruleId, long startTimeMs, long endTimeMs,
                             float confidence, int matchedFrames) {
    }

    public record FeedResult(Status status, List<MatchEvent> events, String message) {
        public FeedResult {
            events = events == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(events));
            message = message == null ? "" : message;
        }

        static FeedResult of(Status status, List<MatchEvent> events, String message) {
            return new FeedResult(status, events, message);
        }
    }

    public record Config(int minPrefixFrames, int maxHammingBits, float prefixMatchRatio,
                         float fullMatchRatio, long cooldownMs, long maxTimelineGapMs) {
        public Config {
            if (minPrefixFrames < 2) throw new IllegalArgumentException("minPrefixFrames is too small");
            if (maxHammingBits < 0 || maxHammingBits > 16) {
                throw new IllegalArgumentException("maxHammingBits is out of range");
            }
            if (!Float.isFinite(prefixMatchRatio) || !Float.isFinite(fullMatchRatio)
                    || prefixMatchRatio < 0.5f || prefixMatchRatio > 1.0f
                    || fullMatchRatio < 0.5f || fullMatchRatio > prefixMatchRatio) {
                throw new IllegalArgumentException("match ratios are invalid");
            }
            if (cooldownMs < 0 || maxTimelineGapMs < 500) {
                throw new IllegalArgumentException("timing values are invalid");
            }
        }

        public static Config conservative() {
            return new Config(6, 7, 0.84f, 0.78f, 5_000, 2_500);
        }

        public static Config fastStart() {
            return new Config(2, 5, 1.0f, 0.78f, 5_000, 2_500);
        }
    }

    private final AudioFingerprintRuleSet ruleSet;
    private final Config config;
    private final List<CompiledRule> compiledRules;
    private final StreamingPcmResampler resampler;
    private final SpectralFingerprint.Workspace fingerprintWorkspace;
    private int[] history;
    private int historyStart;
    private int historyCount;
    private final Map<String, Candidate> activeCandidates = new HashMap<>();
    private final Map<String, Long> nextAllowedTime = new HashMap<>();
    private short[] pendingSamples = new short[0];
    private int pendingCount;
    private long expectedChunkTimeMs = UNSET_TIME_MS;
    private long nextFrameTimeMs;
    private int inputSampleRate;
    private int inputChannels;
    private int maxHistoryFrames;

    public AudioFingerprintMatcher(AudioFingerprintRuleSet ruleSet, Config config) {
        if (ruleSet == null || config == null) throw new IllegalArgumentException("matcher arguments are required");
        this.ruleSet = ruleSet;
        this.config = config;
        this.compiledRules = compileRules(ruleSet.rules());
        this.resampler = new StreamingPcmResampler(ruleSet.config().sampleRate());
        this.fingerprintWorkspace = new SpectralFingerprint.Workspace(
                ruleSet.config().windowSamples(), ruleSet.config().sampleRate(), ruleSet.config().bandCount());
        for (CompiledRule rule : compiledRules) {
            for (int[] sequence : rule.sequences) maxHistoryFrames = Math.max(maxHistoryFrames, sequence.length);
        }
        maxHistoryFrames = Math.max(maxHistoryFrames, config.minPrefixFrames());
        history = new int[maxHistoryFrames];
    }

    public synchronized FeedResult feed(PcmChunk chunk) {
        if (!isValid(chunk)) return FeedResult.of(Status.INVALID_INPUT, List.of(), "PCM chunk is invalid");
        long inputFrames = chunk.samples().length / chunk.channels();
        long targetFrames = (long) Math.ceil(inputFrames * (ruleSet.config().sampleRate() / (double) chunk.sampleRate()));
        if (targetFrames > MAX_CHUNK_TARGET_FRAMES) {
            return FeedResult.of(Status.INVALID_INPUT, List.of(), "PCM chunk is too large");
        }
        if (compiledRules.isEmpty()) return FeedResult.of(Status.NO_MATCH, List.of(), "");
        boolean timelineReset = false;
        try {
            if (expectedChunkTimeMs != UNSET_TIME_MS
                    && exceedsGap(chunk.startTimeMs(), expectedChunkTimeMs, config.maxTimelineGapMs())) {
                resetInternal();
                timelineReset = true;
            }
            if (inputSampleRate != 0
                    && (inputSampleRate != chunk.sampleRate() || inputChannels != chunk.channels())) {
                resetInternal();
                timelineReset = true;
            }
            if (expectedChunkTimeMs == UNSET_TIME_MS) nextFrameTimeMs = chunk.startTimeMs();
            inputSampleRate = chunk.sampleRate();
            inputChannels = chunk.channels();

            short[] mono = resampler.append(chunk.samples(), chunk.sampleRate(), chunk.channels());
            expectedChunkTimeMs = safeAdd(chunk.startTimeMs(), Math.max(1L,
                    Math.round((chunk.samples().length / (double) chunk.channels()) * 1_000.0 / chunk.sampleRate())));
            appendSamples(mono);

            List<MatchEvent> events = evaluateAvailableSamples();
            if (timelineReset) return FeedResult.of(Status.RESET, events, "PCM timeline reset");
            if (!events.isEmpty()) return FeedResult.of(Status.MATCHED, events, "");
            return FeedResult.of(Status.NO_MATCH, List.of(), "");
        } catch (RuntimeException e) {
            resetInternal();
            return FeedResult.of(Status.INTERNAL_ERROR, List.of(), e.getMessage());
        }
    }

    public synchronized void reset() {
        resetInternal();
    }

    public synchronized FeedResult finish() {
        if (compiledRules.isEmpty()) {
            resetInternal();
            return FeedResult.of(Status.NO_MATCH, List.of(), "");
        }
        try {
            appendSamples(resampler.flush());
            List<MatchEvent> events = evaluateAvailableSamples();
            resetInternal();
            return events.isEmpty()
                    ? FeedResult.of(Status.NO_MATCH, List.of(), "")
                    : FeedResult.of(Status.MATCHED, events, "");
        } catch (RuntimeException e) {
            resetInternal();
            return FeedResult.of(Status.INTERNAL_ERROR, List.of(), e.getMessage());
        }
    }

    private List<MatchEvent> evaluateAvailableSamples() {
        List<MatchEvent> events = new ArrayList<>();
        int windowSamples = ruleSet.config().windowSamples();
        int hopSamples = ruleSet.config().hopSamples();
        while (pendingCount >= windowSamples) {
            int hash = fingerprintWorkspace.hashWindow(pendingSamples, 0);
            appendHistory(hash);
            evaluate(nextFrameTimeMs, events);
            discardSamples(hopSamples);
            nextFrameTimeMs = safeAdd(nextFrameTimeMs, ruleSet.config().hopMs());
        }
        return events;
    }

    private void evaluate(long frameTimeMs, List<MatchEvent> events) {
        expireCandidates(frameTimeMs);
        for (CompiledRule rule : compiledRules) {
            Candidate active = activeCandidates.get(rule.id);
            if (active != null) {
                SequenceMatch full = bestMatch(rule, Integer.MAX_VALUE);
                if (full.length > 0 && full.length == full.sequenceLength
                        && full.ratio >= config.fullMatchRatio()) {
                    events.add(new MatchEvent(Type.FULL_MATCHED, rule.id, active.startTimeMs,
                            active.endTimeMs, full.ratio, full.length));
                    activeCandidates.remove(rule.id);
                    nextAllowedTime.put(rule.id, safeAdd(active.endTimeMs, config.cooldownMs()));
                }
                continue;
            }
            long allowedAt = nextAllowedTime.getOrDefault(rule.id, Long.MIN_VALUE);
            if (frameTimeMs < allowedAt) continue;
            SequenceMatch full = bestFullMatchAtMost(rule, config.minPrefixFrames());
            if (full.length > 0 && full.length == full.sequenceLength
                    && full.sequenceLength <= config.minPrefixFrames()
                    && full.ratio >= config.fullMatchRatio()) {
                long startTimeMs = matchStartTime(frameTimeMs, full.length, rule.anchorOffsetMs);
                long endTimeMs = safeAdd(startTimeMs, rule.durationMs);
                events.add(new MatchEvent(Type.START_MATCHED, rule.id, startTimeMs, endTimeMs,
                        full.ratio, full.length));
                events.add(new MatchEvent(Type.FULL_MATCHED, rule.id, startTimeMs, endTimeMs,
                        full.ratio, full.length));
                nextAllowedTime.put(rule.id, safeAdd(endTimeMs, config.cooldownMs()));
                continue;
            }
            SequenceMatch prefix = bestMatch(rule, config.minPrefixFrames());
            if (prefix.length <= 0 || prefix.ratio < config.prefixMatchRatio()) continue;
            long startTimeMs = matchStartTime(frameTimeMs, prefix.length, rule.anchorOffsetMs);
            long endTimeMs = safeAdd(startTimeMs, rule.durationMs);
            activeCandidates.put(rule.id, new Candidate(startTimeMs, endTimeMs));
            events.add(new MatchEvent(Type.START_MATCHED, rule.id, startTimeMs, endTimeMs,
                    prefix.ratio, prefix.length));
        }
    }

    private SequenceMatch bestMatch(CompiledRule rule, int requestedLength) {
        SequenceMatch best = SequenceMatch.NONE;
        for (int[] sequence : rule.sequences) {
            int length = Math.min(requestedLength, sequence.length);
            float ratio = suffixMatchRatio(sequence, length);
            if (ratio > best.ratio) best = new SequenceMatch(ratio, length, sequence.length);
        }
        return best;
    }

    private SequenceMatch bestFullMatchAtMost(CompiledRule rule, int maxLength) {
        SequenceMatch best = SequenceMatch.NONE;
        for (int[] sequence : rule.sequences) {
            if (sequence.length > maxLength) continue;
            float ratio = suffixMatchRatio(sequence, sequence.length);
            if (ratio > best.ratio) best = new SequenceMatch(ratio, sequence.length, sequence.length);
        }
        return best;
    }

    private float suffixMatchRatio(int[] sequence, int length) {
        if (length <= 0 || historyCount < length) return 0.0f;
        int skip = historyCount - length;
        int matched = 0;
        for (int index = skip; index < historyCount; index++) {
            int actual = history[(historyStart + index) % history.length];
            int expected = sequence[index - skip];
            if (Integer.bitCount(actual ^ expected) <= config.maxHammingBits()) matched++;
        }
        return matched / (float) length;
    }

    private void expireCandidates(long frameTimeMs) {
        activeCandidates.entrySet().removeIf(entry ->
                frameTimeMs > safeAdd(entry.getValue().endTimeMs, ruleSet.config().hopMs()));
    }

    private void appendHistory(int hash) {
        if (historyCount < history.length) {
            history[(historyStart + historyCount) % history.length] = hash;
            historyCount++;
        } else {
            history[historyStart] = hash;
            historyStart = (historyStart + 1) % history.length;
        }
    }

    private void appendSamples(short[] samples) {
        if (samples.length == 0) return;
        ensurePendingCapacity(pendingCount + samples.length);
        System.arraycopy(samples, 0, pendingSamples, pendingCount, samples.length);
        pendingCount += samples.length;
    }

    private void ensurePendingCapacity(int required) {
        if (required <= pendingSamples.length) return;
        int capacity = Math.max(required, Math.max(8_192, pendingSamples.length * 2));
        short[] expanded = new short[capacity];
        System.arraycopy(pendingSamples, 0, expanded, 0, pendingCount);
        pendingSamples = expanded;
    }

    private void discardSamples(int count) {
        if (count >= pendingCount) {
            pendingCount = 0;
            return;
        }
        System.arraycopy(pendingSamples, count, pendingSamples, 0, pendingCount - count);
        pendingCount -= count;
    }

    private void resetInternal() {
        pendingCount = 0;
        historyStart = 0;
        historyCount = 0;
        activeCandidates.clear();
        nextAllowedTime.clear();
        resampler.reset();
        expectedChunkTimeMs = UNSET_TIME_MS;
        nextFrameTimeMs = 0;
        inputSampleRate = 0;
        inputChannels = 0;
    }

    private static boolean isValid(PcmChunk chunk) {
        if (chunk == null || chunk.samples() == null || chunk.samples().length == 0
                || chunk.samples().length > MAX_CHUNK_SAMPLES
                || chunk.sampleRate() < 8_000 || chunk.sampleRate() > 192_000
                || chunk.channels() < 1 || chunk.channels() > 8
                || chunk.samples().length % chunk.channels() != 0 || chunk.startTimeMs() < 0) {
            return false;
        }
        long frames = chunk.samples().length / chunk.channels();
        return frames <= (long) chunk.sampleRate() * MAX_CHUNK_SECONDS;
    }

    private long matchStartTime(long frameTimeMs, int matchedFrames, long anchorOffsetMs) {
        long lookBehindMs = (matchedFrames - 1L) * ruleSet.config().hopMs() + anchorOffsetMs;
        return frameTimeMs > lookBehindMs ? frameTimeMs - lookBehindMs : 0L;
    }

    private static boolean exceedsGap(long first, long second, long maxGap) {
        return first >= second ? first - second > maxGap : second - first > maxGap;
    }

    private static long safeAdd(long value, long delta) {
        if (delta > 0 && value > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
        if (delta < 0 && value < Long.MIN_VALUE - delta) return Long.MIN_VALUE;
        return value + delta;
    }

    private static List<CompiledRule> compileRules(List<AudioFingerprintRule> rules) {
        List<CompiledRule> result = new ArrayList<>(rules.size());
        for (AudioFingerprintRule rule : rules) {
            List<int[]> sequences = rule.allSequences();
            result.add(new CompiledRule(rule.id(), rule.durationMs(), rule.anchorOffsetMs(),
                    sequences.toArray(new int[0][])));
        }
        return Collections.unmodifiableList(result);
    }

    private record CompiledRule(String id, long durationMs, long anchorOffsetMs, int[][] sequences) {
    }

    private record Candidate(long startTimeMs, long endTimeMs) {
    }

    private record SequenceMatch(float ratio, int length, int sequenceLength) {
        private static final SequenceMatch NONE = new SequenceMatch(0.0f, 0, 0);
    }
}
