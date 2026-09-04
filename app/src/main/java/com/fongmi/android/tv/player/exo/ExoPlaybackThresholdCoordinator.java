package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackAutoContextStore;

/** Session-safe hysteresis and episode locking for automatic playback thresholds. */
final class ExoPlaybackThresholdCoordinator {

    static final long RECOVERY_STABLE_MS = 30_000L;
    static final long RECOVERY_STEP_MS = 15_000L;
    static final long SEEK_RECOVERY_TIMEOUT_MS = 30_000L;
    static final int SEEK_START_BUFFER_MS = 1_000;

    private static final ExoPlaybackThresholdCoordinator PROCESS =
            new ExoPlaybackThresholdCoordinator();

    private State state = State.empty();

    static ExoPlaybackThresholdCoordinator process() {
        return PROCESS;
    }

    synchronized Update observe(ExoPlaybackThresholdPolicy.Inputs rawInputs) {
        ExoPlaybackThresholdPolicy.Inputs raw = rawInputs == null
                ? ExoPlaybackThresholdPolicy.Inputs.unknown() : rawInputs;
        PlaybackAutoContext.SessionToken session = raw.session();
        if (!session.active()) return Update.inactive();
        long now = raw.nowElapsedMs();
        if (!session.equals(state.session())) {
            state = State.begin(session);
        }

        int observedRebufferCount = Math.max(
                state.maxRebufferCount(), raw.rebufferCount());
        boolean rebufferStarted = raw.currentlyRebuffering()
                && !state.currentlyRebuffering();
        boolean rebufferCountIncreased = observedRebufferCount
                > state.maxRebufferCount();
        long lastRebufferAtMs = state.lastRebufferAtMs();
        if (rebufferStarted || rebufferCountIncreased) {
            lastRebufferAtMs = now;
        }
        long rebufferAgeMs = lastRebufferAtMs < 0
                ? -1 : Math.max(0, now - lastRebufferAtMs);
        ExoPlaybackThresholdPolicy.Inputs inputs = raw.withRebufferHistory(
                observedRebufferCount,
                raw.currentlyRebuffering(),
                rebufferAgeMs);
        ExoPlaybackThresholdPolicy.Decision policy =
                ExoPlaybackThresholdPolicy.resolve(inputs);

        int previousStartMs = state.startBufferMs();
        int previousRebufferMs = state.rebufferMs();
        int startMs = previousStartMs;
        int rebufferMs = previousRebufferMs;
        long stableSinceMs = state.stableSinceMs();
        long lastLowerAtMs = state.lastLowerAtMs();
        Action action = Action.HOLD;

        if (startMs <= 0 || rebufferMs <= 0) {
            startMs = policy.startBufferMs();
            rebufferMs = policy.rebufferMs();
            stableSinceMs = policy.loweringEligible() ? now : -1;
            action = Action.RESET;
        } else if (policy.immediateDecrease()
                && (policy.startBufferMs() < startMs
                || policy.rebufferMs() < rebufferMs)) {
            startMs = policy.startBufferMs();
            rebufferMs = policy.rebufferMs();
            stableSinceMs = -1;
            action = Action.CAP;
        } else if (policy.startBufferMs() > startMs
                || policy.rebufferMs() > rebufferMs) {
            startMs = Math.max(startMs, policy.startBufferMs());
            rebufferMs = Math.max(rebufferMs, policy.rebufferMs());
            stableSinceMs = -1;
            action = Action.RAISE;
        } else if (policy.loweringEligible()
                && (policy.startBufferMs() < startMs
                || policy.rebufferMs() < rebufferMs)) {
            if (stableSinceMs < 0) stableSinceMs = now;
            boolean stableLongEnough = now - stableSinceMs >= RECOVERY_STABLE_MS;
            boolean stepAllowed = lastLowerAtMs < 0
                    || now - lastLowerAtMs >= RECOVERY_STEP_MS;
            if (stableLongEnough && stepAllowed) {
                startMs = ExoPlaybackThresholdPolicy.lowerOneStep(
                        startMs, policy.startBufferMs());
                rebufferMs = ExoPlaybackThresholdPolicy.lowerOneStep(
                        rebufferMs, policy.rebufferMs());
                lastLowerAtMs = now;
                action = Action.LOWER;
            }
        } else if (!policy.loweringEligible()) {
            stableSinceMs = -1;
        }

        boolean changed = startMs != previousStartMs
                || rebufferMs != previousRebufferMs;
        state = new State(
                session,
                startMs,
                rebufferMs,
                stableSinceMs,
                lastLowerAtMs,
                lastRebufferAtMs,
                observedRebufferCount,
                raw.currentlyRebuffering(),
                state.seekPendingAtMs(),
                state.lock(),
                policy);
        return new Update(
                session,
                previousStartMs,
                previousRebufferMs,
                startMs,
                rebufferMs,
                changed,
                action,
                policy);
    }

    synchronized Selection lockEpisode(
            Episode episode,
            ExoPlaybackThresholdPolicy.Inputs inputs) {
        Episode safeEpisode = episode == null ? Episode.STARTUP : episode;
        Update update = observe(inputs);
        if (!update.session().active()) return Selection.inactive(safeEpisode);
        EpisodeLock current = state.lock();
        if (current != null
                && current.session().equals(update.session())
                && current.episode() == safeEpisode) {
            if (update.policy().immediateDecrease()) {
                int cappedStartMs = Math.min(
                        current.startBufferMs(), update.startBufferMs());
                int cappedRebufferMs = Math.min(
                        current.rebufferMs(), update.rebufferMs());
                if (cappedStartMs < current.startBufferMs()
                        || cappedRebufferMs < current.rebufferMs()) {
                    EpisodeLock capped = new EpisodeLock(
                            current.session(),
                            current.episode(),
                            cappedStartMs,
                            cappedRebufferMs,
                            update.policy());
                    state = state.withLock(capped);
                    return new Selection(
                            capped.session(),
                            capped.episode(),
                            capped.startBufferMs(),
                            capped.rebufferMs(),
                            capped.policy(),
                            true,
                            Action.CAP);
                }
            }
            return new Selection(
                    current.session(),
                    current.episode(),
                    current.startBufferMs(),
                    current.rebufferMs(),
                    current.policy(),
                    false,
                    update.action());
        }
        EpisodeLock locked = new EpisodeLock(
                update.session(),
                safeEpisode,
                update.startBufferMs(),
                update.rebufferMs(),
                update.policy());
        state = state.withLock(locked);
        return new Selection(
                locked.session(),
                locked.episode(),
                locked.startBufferMs(),
                locked.rebufferMs(),
                locked.policy(),
                true,
                Action.LOCK);
    }

    synchronized void endEpisode(PlaybackAutoContext.SessionToken session) {
        if (session == null || !session.equals(state.session())) return;
        state = state.withLock(null).withSeekPendingAtMs(0);
    }

    synchronized void markSeek(
            PlaybackAutoContext.SessionToken session,
            long nowElapsedMs) {
        if (session == null || !session.active()) return;
        if (!session.equals(state.session())) state = State.begin(session);
        state = state.withLock(null).withSeekPendingAtMs(
                Math.max(1, nowElapsedMs));
    }

    synchronized boolean isSeekPending(
            PlaybackAutoContext.SessionToken session,
            long nowElapsedMs) {
        boolean pending = session != null
                && session.active()
                && session.equals(state.session())
                && state.seekPendingAtMs() > 0;
        if (!pending) return false;
        if (Math.max(0, nowElapsedMs) - state.seekPendingAtMs()
                <= SEEK_RECOVERY_TIMEOUT_MS) return true;
        state = state.withSeekPendingAtMs(0);
        return false;
    }

    synchronized void disrupt(PlaybackAutoContext.SessionToken session) {
        if (session == null || !session.equals(state.session())) return;
        state = state.disrupt();
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(
                state.session(),
                state.startBufferMs(),
                state.rebufferMs(),
                state.stableSinceMs(),
                state.lastRebufferAtMs(),
                state.lock() == null ? null : state.lock().episode(),
                state.policy());
    }

    static ExoPlaybackThresholdPolicy.Inputs captureInputs(
            int configuredStartBufferMs,
            int configuredRebufferMs,
            long bufferedDurationMs,
            long targetLiveOffsetMs,
            boolean rebuffering,
            long nowElapsedMs) {
        long now = Math.max(0, nowElapsedMs);
        PlaybackAutoContext context = PlaybackAutoContextStore.process().snapshot();
        PlaybackAutoContext.SessionToken session = currentExoSession(context);
        if (!session.active()) {
            return new ExoPlaybackThresholdPolicy.Inputs(
                    session,
                    configuredStartBufferMs,
                    configuredRebufferMs,
                    PlaybackAutoContext.ResourceFacts.unknown(),
                    PlaybackAutoContext.PathFacts.unknown(),
                    ExoThroughputEstimator.Snapshot.empty(),
                    ForwardBufferTrend.Snapshot.unknown(),
                    bufferedDurationMs,
                    0,
                    PlaybackAutoContext.Confidence.UNKNOWN,
                    0,
                    0,
                    rebuffering,
                    -1,
                    targetLiveOffsetMs,
                    now);
        }

        PlaybackAnalyticsListener.Snapshot analytics =
                PlaybackAnalyticsListener.getSnapshot();
        int rebufferCount = analytics.rebufferCount();
        if (rebuffering && analytics.everReady()
                && analytics.rebufferStartMs() <= 0) {
            rebufferCount = rebufferCount == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : rebufferCount + 1;
        }
        long rebufferTotalMs = analytics.rebufferTotalMs();
        if (analytics.rebufferStartMs() > 0) {
            rebufferTotalMs = saturatingAdd(
                    rebufferTotalMs,
                    Math.max(0, now - analytics.rebufferStartMs()));
        }

        ForwardBufferTrend.Snapshot trend = newerKnownTrend(
                PlaybackAnalyticsListener.getBufferTrend(),
                PlaybackAnalyticsListener.getLastStableBufferTrend());
        MediaDemand media = mediaDemand(analytics);
        ExoThroughputEstimator.Snapshot throughput =
                ExoThroughputCoordinator.process().snapshot();
        return new ExoPlaybackThresholdPolicy.Inputs(
                session,
                configuredStartBufferMs,
                configuredRebufferMs,
                context.resource(),
                context.path(),
                throughput,
                trend,
                bufferedDurationMs,
                media.bitsPerSecond(),
                media.confidence(),
                rebufferCount,
                rebufferTotalMs,
                rebuffering || analytics.rebufferStartMs() > 0,
                -1,
                targetLiveOffsetMs,
                now);
    }

    static PlaybackAutoContext.SessionToken currentSession() {
        return currentExoSession(PlaybackAutoContextStore.process().snapshot());
    }

    private static MediaDemand mediaDemand(
            PlaybackAnalyticsListener.Snapshot analytics) {
        ObservedMediaBitrateEstimator.Estimate estimate =
                PlaybackAnalyticsListener.getMediaBitrateEstimate();
        if (estimate.bitrateBitsPerSecond() > 0
                && estimate.confidence() != ObservedMediaBitrateEstimator.Confidence.UNKNOWN
                && estimate.confidence() != ObservedMediaBitrateEstimator.Confidence.LOW) {
            return new MediaDemand(
                    estimate.bitrateBitsPerSecond(),
                    mapConfidence(estimate.confidence()));
        }
        long selected = ExoPlaybackDiagnostics.combinedBitrate(
                analytics.videoFormat(), analytics.audioFormat());
        return selected > 0
                ? new MediaDemand(selected, PlaybackAutoContext.Confidence.MEDIUM)
                : new MediaDemand(0, PlaybackAutoContext.Confidence.UNKNOWN);
    }

    private static PlaybackAutoContext.Confidence mapConfidence(
            ObservedMediaBitrateEstimator.Confidence confidence) {
        if (confidence == null) return PlaybackAutoContext.Confidence.UNKNOWN;
        return switch (confidence) {
            case HIGH -> PlaybackAutoContext.Confidence.HIGH;
            case MEDIUM -> PlaybackAutoContext.Confidence.MEDIUM;
            case LOW -> PlaybackAutoContext.Confidence.LOW;
            case UNKNOWN -> PlaybackAutoContext.Confidence.UNKNOWN;
        };
    }

    private static ForwardBufferTrend.Snapshot newerKnownTrend(
            ForwardBufferTrend.Snapshot current,
            ForwardBufferTrend.Snapshot previous) {
        ForwardBufferTrend.Snapshot safeCurrent = current == null
                ? ForwardBufferTrend.Snapshot.unknown() : current;
        ForwardBufferTrend.Snapshot safePrevious = previous == null
                ? ForwardBufferTrend.Snapshot.unknown() : previous;
        if (!safeCurrent.known()) return safePrevious;
        if (!safePrevious.known()) return safeCurrent;
        return safeCurrent.sampledAtElapsedMs() >= safePrevious.sampledAtElapsedMs()
                ? safeCurrent : safePrevious;
    }

    private static PlaybackAutoContext.SessionToken currentExoSession(
            PlaybackAutoContext context) {
        if (context == null || !context.active()) {
            return PlaybackAutoContext.SessionToken.none();
        }
        if (!context.session().traceId().equals(
                PlaybackAnalyticsListener.getPlaybackTraceId())) {
            return PlaybackAutoContext.SessionToken.none();
        }
        if (context.kernel().hasValue()
                && context.kernel().value() != PlaybackAutoContext.Kernel.EXO) {
            return PlaybackAutoContext.SessionToken.none();
        }
        return context.session();
    }

    private static long saturatingAdd(long first, long second) {
        long safeFirst = Math.max(0, first);
        long safeSecond = Math.max(0, second);
        return safeFirst > Long.MAX_VALUE - safeSecond
                ? Long.MAX_VALUE : safeFirst + safeSecond;
    }

    enum Episode {
        SEEK("seek"),
        STARTUP("startup"),
        REBUFFER("rebuffer");

        private final String label;

        Episode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    enum Action {
        RESET("reset"),
        HOLD("hold"),
        RAISE("raise"),
        LOWER("lower"),
        CAP("cap"),
        LOCK("lock"),
        INACTIVE("inactive");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record Update(
            PlaybackAutoContext.SessionToken session,
            int previousStartBufferMs,
            int previousRebufferMs,
            int startBufferMs,
            int rebufferMs,
            boolean changed,
            Action action,
            ExoPlaybackThresholdPolicy.Decision policy) {

        static Update inactive() {
            return new Update(
                    PlaybackAutoContext.SessionToken.none(),
                    0,
                    0,
                    0,
                    0,
                    false,
                    Action.INACTIVE,
                    ExoPlaybackThresholdPolicy.resolve(
                            ExoPlaybackThresholdPolicy.Inputs.unknown()));
        }
    }

    record Selection(
            PlaybackAutoContext.SessionToken session,
            Episode episode,
            int startBufferMs,
            int rebufferMs,
            ExoPlaybackThresholdPolicy.Decision policy,
            boolean newlyLocked,
            Action action) {

        static Selection inactive(Episode episode) {
            return new Selection(
                    PlaybackAutoContext.SessionToken.none(),
                    episode,
                    0,
                    0,
                    ExoPlaybackThresholdPolicy.resolve(
                            ExoPlaybackThresholdPolicy.Inputs.unknown()),
                    false,
                    Action.INACTIVE);
        }

        int thresholdMs() {
            if (episode == Episode.SEEK) {
                return Math.min(SEEK_START_BUFFER_MS, startBufferMs);
            }
            return episode == Episode.REBUFFER ? rebufferMs : startBufferMs;
        }
    }

    record Snapshot(
            PlaybackAutoContext.SessionToken session,
            int startBufferMs,
            int rebufferMs,
            long stableSinceMs,
            long lastRebufferAtMs,
            Episode lockedEpisode,
            ExoPlaybackThresholdPolicy.Decision policy) {
    }

    private record State(
            PlaybackAutoContext.SessionToken session,
            int startBufferMs,
            int rebufferMs,
            long stableSinceMs,
            long lastLowerAtMs,
            long lastRebufferAtMs,
            int maxRebufferCount,
            boolean currentlyRebuffering,
            long seekPendingAtMs,
            EpisodeLock lock,
            ExoPlaybackThresholdPolicy.Decision policy) {

        static State empty() {
            return begin(PlaybackAutoContext.SessionToken.none());
        }

        static State begin(PlaybackAutoContext.SessionToken session) {
            return new State(
                    session == null
                            ? PlaybackAutoContext.SessionToken.none() : session,
                    0,
                    0,
                    -1,
                    -1,
                    -1,
                    0,
                    false,
                    0,
                    null,
                    null);
        }

        State withLock(EpisodeLock episodeLock) {
            return new State(
                    session,
                    startBufferMs,
                    rebufferMs,
                    stableSinceMs,
                    lastLowerAtMs,
                    lastRebufferAtMs,
                    maxRebufferCount,
                    currentlyRebuffering,
                    seekPendingAtMs,
                    episodeLock,
                    policy);
        }

        State withSeekPendingAtMs(long pendingAtMs) {
            return new State(
                    session,
                    startBufferMs,
                    rebufferMs,
                    stableSinceMs,
                    lastLowerAtMs,
                    lastRebufferAtMs,
                    maxRebufferCount,
                    currentlyRebuffering,
                    Math.max(0, pendingAtMs),
                    lock,
                    policy);
        }

        State disrupt() {
            return new State(
                    session,
                    startBufferMs,
                    rebufferMs,
                    -1,
                    lastLowerAtMs,
                    lastRebufferAtMs,
                    maxRebufferCount,
                    false,
                    0,
                    null,
                    policy);
        }
    }

    private record EpisodeLock(
            PlaybackAutoContext.SessionToken session,
            Episode episode,
            int startBufferMs,
            int rebufferMs,
            ExoPlaybackThresholdPolicy.Decision policy) {
    }

    private record MediaDemand(
            long bitsPerSecond,
            PlaybackAutoContext.Confidence confidence) {
    }
}
