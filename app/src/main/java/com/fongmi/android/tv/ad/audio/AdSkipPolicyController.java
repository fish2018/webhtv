package com.fongmi.android.tv.ad.audio;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;

public final class AdSkipPolicyController implements AutoCloseable {

    public enum Mode {
        PROMPT,
        AUTO
    }

    @FunctionalInterface
    public interface CandidateSink {
        void accept(AdAudioSignalProvider.AdAudioCandidate candidate);
    }

    @FunctionalInterface
    public interface ModeResolver {
        Mode modeFor(String providerId);
    }

    private enum Decision {
        PROMPTED,
        AUTO_APPLIED
    }

    private static final int MAX_CAPACITY = 4_096;

    private final int capacity;
    private final CandidateSink promptSink;
    private final CandidateSink autoSink;
    private final LinkedHashMap<CandidateKey, CandidateDecision> decisions =
            new LinkedHashMap<>();

    private AdAudioSignalProvider.SessionContext context;
    private String ruleVersion;
    private Mode mode = Mode.PROMPT;
    private ModeResolver modeResolver = ignored -> mode;
    private long prompted;
    private long automated;
    private long upgrades;
    private long duplicates;
    private long stale;
    private long evicted;
    private long sinkErrors;
    private long resets;
    private long modeSwitches;
    private boolean closed;

    public AdSkipPolicyController(
            AdAudioSignalProvider.SessionContext context,
            String ruleVersion, int capacity,
            CandidateSink promptSink, CandidateSink autoSink) {
        this.context = Objects.requireNonNull(context, "context");
        this.ruleVersion = requireRuleVersion(ruleVersion);
        if (capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("capacity is out of range");
        }
        this.capacity = capacity;
        this.promptSink = Objects.requireNonNull(promptSink, "promptSink");
        this.autoSink = Objects.requireNonNull(autoSink, "autoSink");
    }

    public synchronized Mode mode() {
        return mode;
    }

    public synchronized void setMode(Mode mode) {
        if (closed) return;
        Objects.requireNonNull(mode, "mode");
        if (this.mode == mode) return;
        this.mode = mode;
        modeSwitches++;
    }

    public synchronized void setModeResolver(ModeResolver resolver) {
        if (closed) return;
        modeResolver = Objects.requireNonNull(resolver, "resolver");
        modeSwitches++;
    }

    public void onCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
        CandidateSink sink = null;
        synchronized (this) {
            if (closed) return;
            if (candidate == null || !isCurrent(candidate)) {
                stale++;
                return;
            }
            CandidateKey key = CandidateKey.of(candidate);
            CandidateDecision existing = decisions.get(key);
            if (existing != null) {
                if (existing.decision == Decision.PROMPTED
                        && !existing.best.fullMatch() && candidate.fullMatch()) {
                    existing.best = candidate;
                    upgrades++;
                    prompted++;
                    sink = promptSink;
                } else {
                    duplicates++;
                }
            } else {
                evictIfFullLocked();
                Decision decision = resolvedMode(candidate) == Mode.PROMPT
                        ? Decision.PROMPTED : Decision.AUTO_APPLIED;
                decisions.put(key, new CandidateDecision(candidate, decision));
                if (decision == Decision.PROMPTED) {
                    prompted++;
                    sink = promptSink;
                } else {
                    automated++;
                    sink = autoSink;
                }
            }
        }
        dispatch(sink, candidate);
    }

    public synchronized void onTimelineReset(AdAudioSignalProvider.TimelineReset reset) {
        if (closed) return;
        if (reset == null || isOlder(reset.sessionId(), reset.generation())) {
            stale++;
            return;
        }
        context = new AdAudioSignalProvider.SessionContext(
                reset.sessionId(), reset.generation(),
                context.mediaId(), context.mediaUrl(), context.headers());
        decisions.clear();
        resets++;
    }

    public synchronized void reset(AdAudioSignalProvider.SessionContext context,
                                   String ruleVersion) {
        if (closed) return;
        this.context = Objects.requireNonNull(context, "context");
        this.ruleVersion = requireRuleVersion(ruleVersion);
        decisions.clear();
        resets++;
    }

    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(prompted, automated, upgrades, duplicates,
                stale, evicted, sinkErrors, resets, modeSwitches);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        decisions.clear();
    }

    private Mode resolvedMode(AdAudioSignalProvider.AdAudioCandidate candidate) {
        Mode resolved;
        try {
            resolved = modeResolver.modeFor(candidate.providerId());
        } catch (RuntimeException ignored) {
            resolved = null;
        }
        return resolved == null ? Mode.PROMPT : resolved;
    }

    private boolean isCurrent(AdAudioSignalProvider.AdAudioCandidate candidate) {
        return candidate.sessionId() == context.sessionId()
                && candidate.generation() == context.generation()
                && candidate.ruleVersion().equals(ruleVersion);
    }

    private boolean isOlder(long sessionId, long generation) {
        return sessionId < context.sessionId()
                || (sessionId == context.sessionId() && generation <= context.generation());
    }

    private void evictIfFullLocked() {
        if (decisions.size() < capacity) return;
        Iterator<CandidateKey> iterator = decisions.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
            evicted++;
        }
    }

    private void dispatch(CandidateSink sink,
                          AdAudioSignalProvider.AdAudioCandidate candidate) {
        if (sink == null) return;
        try {
            sink.accept(candidate);
        } catch (RuntimeException ignored) {
            synchronized (this) {
                sinkErrors++;
            }
        }
    }


    private static String requireRuleVersion(String value) {
        if (value == null || value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException("ruleVersion is invalid");
        }
        return value;
    }

    public record Diagnostics(long prompted, long automated, long upgrades,
                              long duplicates, long stale, long evicted,
                              long sinkErrors, long resets, long modeSwitches) {
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

    private static final class CandidateDecision {
        private AdAudioSignalProvider.AdAudioCandidate best;
        private final Decision decision;

        private CandidateDecision(AdAudioSignalProvider.AdAudioCandidate best,
                                  Decision decision) {
            this.best = best;
            this.decision = decision;
        }
    }
}
