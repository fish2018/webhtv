package com.fongmi.android.tv.ad.audio;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AdAudioDetectionMultiplexer
        implements AdAudioSignalProvider.Listener, AutoCloseable {

    private static final long MAX_CANDIDATE_DURATION_MS = 10L * 60L * 1_000L;
    private static final long RETENTION_AFTER_END_MS = 30_000L;
    private static final int MAX_CAPACITY = 4_096;

    private final int capacity;
    private final AdAudioSignalProvider.Listener output;
    private final Set<String> allowedRuleIds;
    private final LinkedHashMap<CandidateKey, CandidateEntry> candidates =
            new LinkedHashMap<>();

    private AdAudioSignalProvider.SessionContext context;
    private String ruleVersion;
    private long emitted;
    private long upgrades;
    private long duplicates;
    private long stale;
    private long invalid;
    private long evicted;
    private long expired;
    private long providerErrors;
    private long listenerErrors;
    private long resets;
    private boolean closed;

    public AdAudioDetectionMultiplexer(
            AdAudioSignalProvider.SessionContext context,
            String ruleVersion, int capacity,
            AdAudioSignalProvider.Listener output) {
        this(context, ruleVersion, null, capacity, output);
    }

    public AdAudioDetectionMultiplexer(
            AdAudioSignalProvider.SessionContext context,
            String ruleVersion, Set<String> allowedRuleIds, int capacity,
            AdAudioSignalProvider.Listener output) {
        this.context = Objects.requireNonNull(context, "context");
        this.ruleVersion = requireRuleVersion(ruleVersion);
        this.allowedRuleIds = allowedRuleIds == null ? null : Set.copyOf(allowedRuleIds);
        if (capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("capacity is out of range");
        }
        this.capacity = capacity;
        this.output = Objects.requireNonNull(output, "output");
    }

    @Override
    public void onCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
        AdAudioSignalProvider.AdAudioCandidate toEmit = null;
        synchronized (this) {
            if (closed) return;
            if (candidate == null) {
                invalid++;
                return;
            }
            if (!isCurrent(candidate)) {
                stale++;
                return;
            }
            if (!isAllowedRule(candidate) || isOversized(candidate)) {
                invalid++;
                return;
            }
            CandidateKey key = CandidateKey.of(candidate);
            CandidateEntry current = candidates.get(key);
            if (current == null) {
                evictIfFullLocked();
                candidates.put(key, new CandidateEntry(candidate, candidate.fullMatch()));
                emitted++;
                toEmit = candidate;
            } else if (!current.emittedFull && candidate.fullMatch()) {
                current.best = candidate;
                current.emittedFull = true;
                emitted++;
                upgrades++;
                toEmit = candidate;
            } else {
                if (isBetter(candidate, current.best)) current.best = candidate;
                duplicates++;
            }
        }
        notifyCandidate(toEmit);
    }

    @Override
    public void onProviderError(AdAudioSignalProvider.ProviderError error) {
        synchronized (this) {
            if (closed) return;
            if (error == null) {
                invalid++;
                return;
            }
            providerErrors++;
        }
        try {
            output.onProviderError(error);
        } catch (RuntimeException ignored) {
            recordListenerError();
        }
    }

    @Override
    public void onTimelineReset(AdAudioSignalProvider.TimelineReset reset) {
        synchronized (this) {
            if (closed) return;
            if (reset == null || isOlder(reset.sessionId(), reset.generation())) {
                stale++;
                return;
            }
            context = new AdAudioSignalProvider.SessionContext(
                    reset.sessionId(), reset.generation(),
                    context.mediaId(), context.mediaUrl(), context.headers());
            candidates.clear();
            resets++;
        }
        try {
            output.onTimelineReset(reset);
        } catch (RuntimeException ignored) {
            recordListenerError();
        }
    }

    public synchronized void reset(AdAudioSignalProvider.SessionContext context,
                                   String ruleVersion) {
        if (closed) return;
        this.context = Objects.requireNonNull(context, "context");
        this.ruleVersion = requireRuleVersion(ruleVersion);
        candidates.clear();
        resets++;
    }

    public synchronized void onHostPosition(AdAudioSignalProvider.HostPosition position) {
        if (closed || position == null) return;
        if (position.sessionId() != context.sessionId()
                || position.generation() != context.generation()) {
            stale++;
            return;
        }
        Iterator<Map.Entry<CandidateKey, CandidateEntry>> iterator =
                candidates.entrySet().iterator();
        while (iterator.hasNext()) {
            CandidateKey key = iterator.next().getKey();
            if (saturatedAdd(key.endMs, RETENTION_AFTER_END_MS) < position.positionMs()) {
                iterator.remove();
                expired++;
            }
        }
    }

    public synchronized int trackedCandidateCount() {
        return candidates.size();
    }

    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(emitted, upgrades, duplicates, stale, invalid,
                evicted, expired, providerErrors, listenerErrors, resets);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        candidates.clear();
    }

    private boolean isCurrent(AdAudioSignalProvider.AdAudioCandidate candidate) {
        return candidate.sessionId() == context.sessionId()
                && candidate.generation() == context.generation()
                && candidate.ruleVersion().equals(ruleVersion);
    }

    private boolean isAllowedRule(AdAudioSignalProvider.AdAudioCandidate candidate) {
        return allowedRuleIds == null || allowedRuleIds.contains(candidate.ruleId());
    }

    private static boolean isOversized(AdAudioSignalProvider.AdAudioCandidate candidate) {
        return candidate.endMs() > saturatedAdd(
                candidate.startMs(), MAX_CANDIDATE_DURATION_MS);
    }

    private boolean isOlder(long sessionId, long generation) {
        return sessionId < context.sessionId()
                || (sessionId == context.sessionId() && generation <= context.generation());
    }

    private void evictIfFullLocked() {
        if (candidates.size() < capacity) return;
        Iterator<CandidateKey> iterator = candidates.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
            evicted++;
        }
    }

    private void notifyCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
        if (candidate == null) return;
        try {
            output.onCandidate(candidate);
        } catch (RuntimeException ignored) {
            recordListenerError();
        }
    }

    private synchronized void recordListenerError() {
        listenerErrors++;
    }

    private static boolean isBetter(AdAudioSignalProvider.AdAudioCandidate candidate,
                                    AdAudioSignalProvider.AdAudioCandidate current) {
        if (candidate.fullMatch() != current.fullMatch()) return candidate.fullMatch();
        int similarity = Double.compare(candidate.similarity(), current.similarity());
        if (similarity != 0) return similarity > 0;
        return candidate.providerId().compareTo(current.providerId()) < 0;
    }

    private static String requireRuleVersion(String value) {
        if (value == null || value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException("ruleVersion is invalid");
        }
        return value;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    public record Diagnostics(long emitted, long upgrades, long duplicates,
                              long stale, long invalid, long evicted,
                              long expired, long providerErrors,
                              long listenerErrors, long resets) {
    }

    private record CandidateKey(long sessionId, long generation,
                                String ruleVersion, String ruleId,
                                long startMs, long endMs) {
        private static CandidateKey of(AdAudioSignalProvider.AdAudioCandidate candidate) {
            return new CandidateKey(candidate.sessionId(), candidate.generation(),
                    candidate.ruleVersion(), candidate.ruleId(),
                    candidate.startMs(), candidate.endMs());
        }
    }

    private static final class CandidateEntry {
        private AdAudioSignalProvider.AdAudioCandidate best;
        private boolean emittedFull;

        private CandidateEntry(AdAudioSignalProvider.AdAudioCandidate best,
                               boolean emittedFull) {
            this.best = best;
            this.emittedFull = emittedFull;
        }
    }
}
