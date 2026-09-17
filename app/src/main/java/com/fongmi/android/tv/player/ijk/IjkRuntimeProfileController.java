package com.fongmi.android.tv.player.ijk;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackErrorClassifier;

import java.util.EnumSet;
import java.util.Set;

/**
 * Session-isolated IJK runtime learning and bounded temporary kernel fallback.
 * Network transfer and player construction are deliberately outside this lock.
 */
public final class IjkRuntimeProfileController {

    static final long FIRST_FRAME_TIMEOUT_ACTIVE_MS = 10_000L;

    private final ProfileAccess profiles;
    private final EnumSet<IjkRuntimeProfilePolicy.Path> visited;
    private final EnumSet<IjkRuntimeProfilePolicy.Path> failuresRecorded;
    private final EnumSet<IjkRuntimeProfilePolicy.Path> loggedPersistentExclusions;
    private PlaybackAutoContext.SessionToken session =
            PlaybackAutoContext.SessionToken.none();
    private IjkRuntimeProfileKey.Key profileKey;
    private IjkRuntimeProfileKey.Key pendingFallbackKey;
    private IjkRuntimeProfilePolicy.Path currentPath =
            IjkRuntimeProfilePolicy.Path.IJK_HARD;
    private IjkRuntimeProfilePolicy.Path pendingFallbackSource;
    private RuntimeSample lastSample = RuntimeSample.unknown();
    private long firstFrameAtElapsedMs = -1;
    private long lastHealthSampleAtElapsedMs = -1;
    private long activeObservedDurationMs;
    private long firstFrameWaitActiveDurationMs;
    private long lastFirstFrameWaitSampleAtElapsedMs = -1;
    private boolean lastHealthSampleActive;
    private int baselineRebufferCount;
    private long baselineDroppedFrames = -1;
    private long baselineNativeHeapBytes = -1;
    private long baselinePssBytes = -1;
    private int persistedRebufferCount;
    private int fallbackCount;
    private boolean managed;
    private boolean prepared;
    private boolean firstFrame;
    private boolean firstFrameRecorded;
    private boolean stableRecorded;
    private boolean observationRecorded;
    private boolean attemptFailureRecorded;

    public IjkRuntimeProfileController() {
        this(IjkRuntimeProfiles.process());
    }

    IjkRuntimeProfileController(ProfileAccess profiles) {
        this.profiles = profiles;
        this.visited = EnumSet.noneOf(IjkRuntimeProfilePolicy.Path.class);
        this.failuresRecorded = EnumSet.noneOf(
                IjkRuntimeProfilePolicy.Path.class);
        this.loggedPersistentExclusions = EnumSet.noneOf(
                IjkRuntimeProfilePolicy.Path.class);
    }

    public synchronized void beginSession(
            PlaybackAutoContext.SessionToken token) {
        reset(token);
    }

    public synchronized boolean activate(
            PlaybackAutoContext.SessionToken token,
            IjkRuntimeProfilePolicy.Path initialPath,
            Facts facts,
            RuntimeSample sample,
            long nowElapsedMs) {
        if (!isCurrent(token) || managed) return false;
        Facts currentFacts = facts == null ? Facts.inactive() : facts;
        if (!token.equals(currentFacts.session()) || !currentFacts.automatic()) {
            return false;
        }
        managed = true;
        currentPath = initialPath == null
                ? IjkRuntimeProfilePolicy.Path.IJK_HARD : initialPath;
        visited.add(currentPath);
        bindProfileKey(currentFacts);
        beginAttempt(sample);
        return true;
    }

    public synchronized void onPlaybackAttemptStarted(
            PlaybackAutoContext.SessionToken token,
            RuntimeSample sample) {
        if (!isManaged(token)) return;
        beginAttempt(sample);
    }

    public synchronized void onPrepared(
            PlaybackAutoContext.SessionToken token) {
        onPrepared(token, -1);
    }

    public synchronized void onPrepared(
            PlaybackAutoContext.SessionToken token,
            long nowElapsedMs) {
        if (!isManaged(token) || prepared) return;
        prepared = true;
        firstFrameWaitActiveDurationMs = 0;
        lastFirstFrameWaitSampleAtElapsedMs = nowElapsedMs < 0
                ? -1 : nowElapsedMs;
    }

    public synchronized Decision handleFirstFrameTimeout(
            PlaybackAutoContext.SessionToken token,
            Facts facts,
            RuntimeSample sample,
            long nowElapsedMs,
            long nowEpochMs) {
        if (!isManaged(token)) return null;
        Facts currentFacts = facts == null ? Facts.inactive() : facts;
        if (!token.equals(currentFacts.session())) return null;
        bindProfileKey(currentFacts);
        updateSample(sample);
        if (!currentPath.ijk()
                || !prepared
                || firstFrame
                || attemptFailureRecorded) return null;
        if (!currentFacts.hasVideoTrackEvidence()
                || lastSample.outputFrameRateUsable()) {
            resetFirstFrameWait(nowElapsedMs);
            return null;
        }
        advanceFirstFrameWait(lastSample.active(), nowElapsedMs);
        if (!lastSample.active()
                || firstFrameWaitActiveDurationMs
                < FIRST_FRAME_TIMEOUT_ACTIVE_MS) return null;
        return handleFailure(
                token,
                currentFacts,
                lastSample,
                new FailureEvent(
                        PlaybackErrorClassifier.Stage.OUTPUT,
                        androidx.media3.common.PlaybackException
                                .ERROR_CODE_DECODING_FAILED,
                        0,
                        0,
                        true),
                nowElapsedMs,
                nowEpochMs);
    }

    public synchronized Observation onFirstFrame(
            PlaybackAutoContext.SessionToken token,
            Facts facts,
            RuntimeSample sample,
            long nowElapsedMs,
            long nowEpochMs) {
        if (!isManaged(token)) return Observation.hold(Reason.NOT_MANAGED);
        Facts currentFacts = facts == null ? Facts.inactive() : facts;
        if (!token.equals(currentFacts.session())) {
            return Observation.hold(Reason.STALE_SESSION);
        }
        bindProfileKey(currentFacts);
        updateSample(sample);
        if (firstFrame) return Observation.hold(Reason.ALREADY_RECORDED);
        firstFrame = true;
        firstFrameAtElapsedMs = Math.max(0, nowElapsedMs);
        lastHealthSampleAtElapsedMs = firstFrameAtElapsedMs;
        activeObservedDurationMs = 0;
        firstFrameWaitActiveDurationMs = 0;
        lastFirstFrameWaitSampleAtElapsedMs = -1;
        lastHealthSampleActive = lastSample.active();
        baselineRebufferCount = lastSample.rebufferCount();
        baselineDroppedFrames = lastSample.droppedFramesUsable()
                ? lastSample.droppedFrames() : -1;
        boolean recorded = recordFirstFrameIfPossible(nowEpochMs);
        return new Observation(
                recorded ? ObservationAction.FIRST_FRAME
                        : ObservationAction.HOLD,
                Reason.FIRST_FRAME,
                profileId(),
                currentPath,
                IjkRuntimeProfilePolicy.HealthAssessment.hold(
                        IjkRuntimeProfilePolicy.HealthReason.CONFIRMING),
                metrics(currentFacts, lastSample),
                recorded,
                false);
    }

    public synchronized Observation observe(
            PlaybackAutoContext.SessionToken token,
            Facts facts,
            RuntimeSample sample,
            long nowElapsedMs,
            long nowEpochMs) {
        if (!isManaged(token)) return Observation.hold(Reason.NOT_MANAGED);
        Facts currentFacts = facts == null ? Facts.inactive() : facts;
        if (!token.equals(currentFacts.session())) {
            return Observation.hold(Reason.STALE_SESSION);
        }
        bindProfileKey(currentFacts);
        updateSample(sample);
        recordFirstFrameIfPossible(nowEpochMs);
        advanceActiveObservation(lastSample.active(), nowElapsedMs);
        if (attemptFailureRecorded || stableRecorded || !firstFrame) {
            return Observation.hold(
                    attemptFailureRecorded
                            ? Reason.ATTEMPT_FAILED : Reason.CONFIRMING);
        }
        IjkRuntimeProfilePolicy.HealthAssessment health =
                IjkRuntimeProfilePolicy.assessHealth(
                        healthInput(currentFacts, lastSample, nowElapsedMs));
        IjkRuntimeProfileStore.Metrics metrics = metrics(
                currentFacts, lastSample, health.renderedRatioPermille());
        if (health.stable() && profileKey != null) {
            IjkRuntimeProfileStore.Entry entry = profiles.recordStableSuccess(
                    profileKey,
                    currentPath,
                    metricsForPersistence(metrics),
                    nowEpochMs);
            stableRecorded = entry != null;
            if (stableRecorded) markMetricsPersisted(metrics);
            boolean fallbackSucceeded = false;
            if (stableRecorded
                    && pendingFallbackKey != null
                    && pendingFallbackSource != null) {
                profiles.recordFallback(
                        pendingFallbackKey,
                        pendingFallbackSource,
                        IjkRuntimeProfileStore.FallbackResult.SUCCESS,
                        nowEpochMs);
                pendingFallbackKey = null;
                pendingFallbackSource = null;
                fallbackSucceeded = true;
            }
            return new Observation(
                    stableRecorded ? ObservationAction.STABLE
                            : ObservationAction.HOLD,
                    stableRecorded ? Reason.STABLE : Reason.PROFILE_UNAVAILABLE,
                    profileId(),
                    currentPath,
                    health,
                    metrics,
                    stableRecorded,
                    fallbackSucceeded);
        }
        if (profileKey != null
                && health.reason()
                != IjkRuntimeProfilePolicy.HealthReason.CONFIRMING
                && health.reason()
                != IjkRuntimeProfilePolicy.HealthReason.FRAME_EVIDENCE_UNKNOWN
                && health.reason()
                != IjkRuntimeProfilePolicy.HealthReason.DROP_EVIDENCE_UNKNOWN
                && !observationRecorded) {
            observationRecorded = profiles.recordObservation(
                    profileKey,
                    currentPath,
                    metricsForPersistence(metrics),
                    nowEpochMs) != null;
            if (observationRecorded) {
                markMetricsPersisted(metrics);
                return new Observation(
                        ObservationAction.OBSERVATION,
                        Reason.UNHEALTHY_OBSERVATION,
                        profileId(),
                        currentPath,
                        health,
                        metrics,
                        true,
                        false);
            }
        }
        return new Observation(
                ObservationAction.HOLD,
                health.reason()
                        == IjkRuntimeProfilePolicy.HealthReason.CONFIRMING
                        ? Reason.CONFIRMING : Reason.HEALTH_UNKNOWN,
                profileId(),
                currentPath,
                health,
                metrics,
                false,
                false);
    }

    public synchronized Decision handleFailure(
            PlaybackAutoContext.SessionToken token,
            Facts facts,
            RuntimeSample sample,
            FailureEvent event,
            long nowElapsedMs,
            long nowEpochMs) {
        if (!isManaged(token)) return Decision.hold(Reason.NOT_MANAGED);
        Facts currentFacts = facts == null ? Facts.inactive() : facts;
        if (!token.equals(currentFacts.session())) {
            return Decision.hold(Reason.STALE_SESSION);
        }
        bindProfileKey(currentFacts);
        updateSample(sample);
        FailureEvent failure = event == null
                ? FailureEvent.unknown() : event;
        IjkRuntimeProfilePolicy.FailureAssessment assessment =
                IjkRuntimeProfilePolicy.assessFailure(
                        new IjkRuntimeProfilePolicy.FailureInput(
                                currentPath,
                                failure.stage(),
                                failure.errorCode(),
                                failure.ijkWhat(),
                                failure.ijkExtra(),
                                prepared || failure.prepared(),
                                firstFrame,
                                currentFacts.actualDecodeMode(),
                                profileKey != null));
        IjkRuntimeProfileStore.Metrics metrics = metrics(
                currentFacts, lastSample);
        boolean fallbackFailureRecorded = false;
        if (pendingFallbackKey != null
                && pendingFallbackSource != null
                && assessment.fallbackEligible()) {
            profiles.recordFallback(
                    pendingFallbackKey,
                    pendingFallbackSource,
                    IjkRuntimeProfileStore.FallbackResult.FAILURE,
                    nowEpochMs);
            pendingFallbackKey = null;
            pendingFallbackSource = null;
            fallbackFailureRecorded = true;
        }

        boolean failurePersisted = false;
        if (assessment.persistable()
                && profileKey != null
                && failuresRecorded.add(currentPath)) {
            IjkRuntimeProfileStore.Entry entry = profiles.recordFailure(
                    profileKey,
                    currentPath,
                    assessment.kind(),
                    metricsForPersistence(metrics),
                    nowEpochMs);
            failurePersisted = entry != null;
            if (failurePersisted) markMetricsPersisted(metrics);
        } else if (profileKey != null && !observationRecorded) {
            observationRecorded = profiles.recordObservation(
                    profileKey,
                    currentPath,
                    metricsForPersistence(metrics),
                    nowEpochMs) != null;
            if (observationRecorded) markMetricsPersisted(metrics);
        }
        attemptFailureRecorded = true;

        IjkRuntimeProfilePolicy.Path failedPath = currentPath;
        visited.add(failedPath);
        if (profileKey != null && assessment.fallbackEligible()) {
            profiles.logExclusion(profileKey, failedPath, true);
        }
        if (!assessment.fallbackEligible()) {
            return new Decision(
                    Action.HOLD,
                    failedPath,
                    null,
                    assessment,
                    Reason.FALLBACK_NOT_ELIGIBLE,
                    profileId(),
                    fallbackCount,
                    failurePersisted,
                    fallbackFailureRecorded);
        }
        if (fallbackCount
                >= IjkRuntimeProfilePolicy.MAX_FALLBACKS_PER_PLAYBACK) {
            return new Decision(
                    Action.HOLD,
                    failedPath,
                    null,
                    assessment,
                    Reason.FALLBACK_LIMIT,
                    profileId(),
                    fallbackCount,
                    failurePersisted,
                    fallbackFailureRecorded);
        }

        EnumSet<IjkRuntimeProfilePolicy.Path> blocked = visited.clone();
        if (profileKey != null) {
            for (IjkRuntimeProfilePolicy.Path path
                    : IjkRuntimeProfilePolicy.Path.values()) {
                if (!profiles.isExcluded(profileKey, path, nowEpochMs)) {
                    continue;
                }
                blocked.add(path);
                if (loggedPersistentExclusions.add(path)) {
                    profiles.logExclusion(profileKey, path, false);
                }
            }
        }
        IjkRuntimeProfilePolicy.Path preferred = profileKey == null
                ? null : profiles.preferredVerifiedPath(
                profileKey,
                blocked,
                currentFacts.softEligible(),
                nowEpochMs);
        IjkRuntimeProfilePolicy.Path target =
                IjkRuntimeProfilePolicy.nextFallback(
                        failedPath,
                        blocked,
                        currentFacts.softEligible(),
                        preferred);
        if (target == null) {
            return new Decision(
                    Action.HOLD,
                    failedPath,
                    null,
                    assessment,
                    Reason.NO_FALLBACK_PATH,
                    profileId(),
                    fallbackCount,
                    failurePersisted,
                    fallbackFailureRecorded);
        }

        fallbackCount++;
        visited.add(target);
        pendingFallbackKey = profileKey;
        pendingFallbackSource = profileKey == null ? null : failedPath;
        currentPath = target;
        beginAttempt(lastSample);
        return new Decision(
                Action.SWITCH,
                failedPath,
                target,
                assessment,
                preferred == target
                        ? Reason.VERIFIED_FALLBACK : Reason.SWITCH_FALLBACK,
                profileId(),
                fallbackCount,
                failurePersisted,
                fallbackFailureRecorded);
    }

    public synchronized void onSwitchStartFailed(
            PlaybackAutoContext.SessionToken token,
            long nowEpochMs) {
        if (!isManaged(token)) return;
        if (pendingFallbackKey != null && pendingFallbackSource != null) {
            profiles.recordFallback(
                    pendingFallbackKey,
                    pendingFallbackSource,
                    IjkRuntimeProfileStore.FallbackResult.FAILURE,
                    nowEpochMs);
        }
        pendingFallbackKey = null;
        pendingFallbackSource = null;
        attemptFailureRecorded = true;
    }

    public synchronized void finishSession(
            PlaybackAutoContext.SessionToken token,
            Facts facts,
            RuntimeSample sample,
            long nowEpochMs) {
        if (!isManaged(token)) return;
        Facts currentFacts = facts == null ? Facts.inactive() : facts;
        if (token.equals(currentFacts.session())) bindProfileKey(currentFacts);
        updateSample(sample);
        recordFirstFrameIfPossible(nowEpochMs);
        if (profileKey != null
                && !stableRecorded
                && !attemptFailureRecorded
                && !observationRecorded) {
            IjkRuntimeProfileStore.Metrics metrics = metrics(
                    currentFacts, lastSample);
            observationRecorded = profiles.recordObservation(
                    profileKey,
                    currentPath,
                    metricsForPersistence(metrics),
                    nowEpochMs) != null;
            if (observationRecorded) markMetricsPersisted(metrics);
        }
        pendingFallbackKey = null;
        pendingFallbackSource = null;
    }

    public synchronized void cancel(
            PlaybackAutoContext.SessionToken token) {
        if (!isCurrent(token)) return;
        managed = false;
        pendingFallbackKey = null;
        pendingFallbackSource = null;
    }

    public synchronized void endSession(
            PlaybackAutoContext.SessionToken token) {
        if (!isCurrent(token)) return;
        reset(PlaybackAutoContext.SessionToken.none());
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                session,
                managed,
                currentPath,
                Set.copyOf(visited),
                profileId(),
                fallbackCount,
                prepared,
                firstFrame,
                stableRecorded,
                attemptFailureRecorded,
                pendingFallbackSource != null);
    }

    private void bindProfileKey(Facts facts) {
        if (profileKey != null || facts == null || facts.evidence == null) return;
        profileKey = IjkRuntimeProfileKey.from(
                profiles.environment(), facts.evidence);
    }

    private boolean recordFirstFrameIfPossible(long nowEpochMs) {
        if (!firstFrame
                || firstFrameRecorded
                || profileKey == null
                || nowEpochMs <= 0) return false;
        firstFrameRecorded = profiles.recordFirstFrame(
                profileKey, currentPath, nowEpochMs) != null;
        return firstFrameRecorded;
    }

    private void beginAttempt(RuntimeSample sample) {
        lastSample = sample == null ? RuntimeSample.unknown() : sample;
        firstFrameAtElapsedMs = -1;
        lastHealthSampleAtElapsedMs = -1;
        activeObservedDurationMs = 0;
        firstFrameWaitActiveDurationMs = 0;
        lastFirstFrameWaitSampleAtElapsedMs = -1;
        lastHealthSampleActive = false;
        baselineRebufferCount = lastSample.rebufferCount();
        baselineDroppedFrames = lastSample.droppedFramesUsable()
                ? lastSample.droppedFrames() : -1;
        baselineNativeHeapBytes = lastSample.nativeHeapBytes();
        baselinePssBytes = lastSample.pssBytes();
        persistedRebufferCount = 0;
        prepared = false;
        firstFrame = false;
        firstFrameRecorded = false;
        stableRecorded = false;
        observationRecorded = false;
        attemptFailureRecorded = false;
    }

    private void updateSample(RuntimeSample sample) {
        if (sample != null) lastSample = sample;
    }

    private void resetFirstFrameWait(long nowElapsedMs) {
        firstFrameWaitActiveDurationMs = 0;
        lastFirstFrameWaitSampleAtElapsedMs = Math.max(0, nowElapsedMs);
    }

    private void advanceFirstFrameWait(
            boolean active,
            long nowElapsedMs) {
        long now = Math.max(0, nowElapsedMs);
        // 与 IjkFirstFrameWatchdog 保持一致：按当前采样活跃累加上一段间隔，
        // 否则 prepared 后首个采样周期不计时，实际超时会偏长一个周期。
        if (lastFirstFrameWaitSampleAtElapsedMs >= 0
                && now >= lastFirstFrameWaitSampleAtElapsedMs
                && active) {
            firstFrameWaitActiveDurationMs = saturatingAdd(
                    firstFrameWaitActiveDurationMs,
                    now - lastFirstFrameWaitSampleAtElapsedMs);
        }
        lastFirstFrameWaitSampleAtElapsedMs = now;
    }

    private IjkRuntimeProfilePolicy.HealthInput healthInput(
            Facts facts,
            RuntimeSample sample,
            long nowElapsedMs) {
        long observedDuration = firstFrameAtElapsedMs < 0
                || nowElapsedMs < firstFrameAtElapsedMs
                ? 0 : activeObservedDurationMs;
        long dropped = droppedDelta(sample);
        return new IjkRuntimeProfilePolicy.HealthInput(
                currentPath,
                sample.active(),
                firstFrame,
                observedDuration,
                rebufferDelta(sample),
                facts.targetFrameRateUsable(),
                facts.targetFrameRate(),
                sample.decodeFrameRateUsable(),
                sample.decodeFrameRate(),
                sample.outputFrameRateUsable(),
                sample.outputFrameRate(),
                sample.dropRateUsable(),
                sample.dropRatePermille(),
                sample.droppedFramesUsable() && baselineDroppedFrames >= 0,
                dropped);
    }

    private IjkRuntimeProfileStore.Metrics metrics(
            Facts facts,
            RuntimeSample sample) {
        return metrics(facts, sample, renderedRatio(facts, sample));
    }

    private IjkRuntimeProfileStore.Metrics metrics(
            Facts facts,
            RuntimeSample sample,
            int renderedRatioPermille) {
        return new IjkRuntimeProfileStore.Metrics(
                rebufferDelta(sample),
                sample.dropRateUsable() ? sample.dropRatePermille() : -1,
                renderedRatioPermille,
                growth(baselineNativeHeapBytes, sample.nativeHeapBytes()),
                growth(baselinePssBytes, sample.pssBytes()));
    }

    private int renderedRatio(Facts facts, RuntimeSample sample) {
        if (facts.targetFrameRateUsable()
                && sample.outputFrameRateUsable()) {
            return ratioPermille(
                    sample.outputFrameRate(), facts.targetFrameRate());
        }
        if (currentPath.ijk()
                && sample.decodeFrameRateUsable()
                && sample.outputFrameRateUsable()) {
            return ratioPermille(
                    sample.outputFrameRate(), sample.decodeFrameRate());
        }
        return -1;
    }

    private IjkRuntimeProfileStore.Metrics metricsForPersistence(
            IjkRuntimeProfileStore.Metrics metrics) {
        IjkRuntimeProfileStore.Metrics current = metrics == null
                ? IjkRuntimeProfileStore.Metrics.EMPTY : metrics;
        return new IjkRuntimeProfileStore.Metrics(
                Math.max(0,
                        current.rebufferCount() - persistedRebufferCount),
                current.dropRatePermille(),
                current.renderedRatioPermille(),
                current.nativeHeapGrowthBytes(),
                current.pssGrowthBytes());
    }

    private void advanceActiveObservation(
            boolean active,
            long nowElapsedMs) {
        if (!firstFrame) return;
        long now = Math.max(0, nowElapsedMs);
        if (lastHealthSampleAtElapsedMs >= 0
                && now >= lastHealthSampleAtElapsedMs
                && lastHealthSampleActive) {
            activeObservedDurationMs = saturatingAdd(
                    activeObservedDurationMs,
                    now - lastHealthSampleAtElapsedMs);
        }
        lastHealthSampleAtElapsedMs = now;
        lastHealthSampleActive = active;
    }

    private void markMetricsPersisted(
            IjkRuntimeProfileStore.Metrics metrics) {
        if (metrics == null) return;
        persistedRebufferCount = Math.max(
                persistedRebufferCount, metrics.rebufferCount());
    }

    private int rebufferDelta(RuntimeSample sample) {
        return Math.max(0, sample.rebufferCount() - baselineRebufferCount);
    }

    private long droppedDelta(RuntimeSample sample) {
        if (!sample.droppedFramesUsable() || baselineDroppedFrames < 0) return 0;
        return Math.max(0, sample.droppedFrames() - baselineDroppedFrames);
    }

    private static long growth(long baseline, long current) {
        if (baseline < 0 || current < 0) return -1;
        return Math.max(0, current - baseline);
    }

    private static long saturatingAdd(long first, long second) {
        if (second <= 0) return Math.max(0, first);
        return first > Long.MAX_VALUE - second
                ? Long.MAX_VALUE : Math.max(0, first) + second;
    }

    private static int ratioPermille(float numerator, float denominator) {
        if (!Float.isFinite(numerator)
                || !Float.isFinite(denominator)
                || numerator <= 0
                || denominator <= 0) return -1;
        double ratio = numerator / (double) denominator * 1_000d;
        if (!Double.isFinite(ratio)) return -1;
        return (int) Math.max(0,
                Math.min(10_000, Math.round(ratio)));
    }

    private String profileId() {
        return IjkRuntimeProfileKey.shortId(profileKey);
    }

    private boolean isCurrent(PlaybackAutoContext.SessionToken token) {
        return token != null && token.active() && token.equals(session);
    }

    private boolean isManaged(PlaybackAutoContext.SessionToken token) {
        return managed && isCurrent(token);
    }

    private void reset(PlaybackAutoContext.SessionToken token) {
        session = token == null
                ? PlaybackAutoContext.SessionToken.none() : token;
        profileKey = null;
        pendingFallbackKey = null;
        currentPath = IjkRuntimeProfilePolicy.Path.IJK_HARD;
        pendingFallbackSource = null;
        lastSample = RuntimeSample.unknown();
        visited.clear();
        failuresRecorded.clear();
        loggedPersistentExclusions.clear();
        firstFrameAtElapsedMs = -1;
        lastHealthSampleAtElapsedMs = -1;
        activeObservedDurationMs = 0;
        firstFrameWaitActiveDurationMs = 0;
        lastFirstFrameWaitSampleAtElapsedMs = -1;
        lastHealthSampleActive = false;
        baselineRebufferCount = 0;
        baselineDroppedFrames = -1;
        baselineNativeHeapBytes = -1;
        baselinePssBytes = -1;
        persistedRebufferCount = 0;
        fallbackCount = 0;
        managed = false;
        prepared = false;
        firstFrame = false;
        firstFrameRecorded = false;
        stableRecorded = false;
        observationRecorded = false;
        attemptFailureRecorded = false;
    }

    public enum Action {
        HOLD("hold"),
        SWITCH("switch");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum ObservationAction {
        HOLD("hold"),
        FIRST_FRAME("first-frame"),
        OBSERVATION("observation"),
        STABLE("stable");

        private final String label;

        ObservationAction(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Reason {
        STALE_SESSION("stale-session"),
        NOT_MANAGED("not-managed"),
        PROFILE_UNAVAILABLE("profile-unavailable"),
        FIRST_FRAME("first-frame"),
        ALREADY_RECORDED("already-recorded"),
        CONFIRMING("confirming"),
        HEALTH_UNKNOWN("health-unknown"),
        UNHEALTHY_OBSERVATION("unhealthy-observation"),
        STABLE("stable"),
        ATTEMPT_FAILED("attempt-failed"),
        FALLBACK_NOT_ELIGIBLE("fallback-not-eligible"),
        FALLBACK_LIMIT("fallback-limit"),
        NO_FALLBACK_PATH("no-fallback-path"),
        VERIFIED_FALLBACK("verified-fallback"),
        SWITCH_FALLBACK("switch-fallback"),
        SWITCH_START_FAILED("switch-start-failed");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record FailureEvent(
            PlaybackErrorClassifier.Stage stage,
            int errorCode,
            int ijkWhat,
            int ijkExtra,
            boolean prepared) {

        public FailureEvent {
            stage = stage == null
                    ? PlaybackErrorClassifier.Stage.UNKNOWN : stage;
        }

        static FailureEvent unknown() {
            return new FailureEvent(
                    PlaybackErrorClassifier.Stage.UNKNOWN,
                    androidx.media3.common.PlaybackException
                            .ERROR_CODE_UNSPECIFIED,
                    0,
                    0,
                    false);
        }
    }

    public record RuntimeSample(
            boolean active,
            int rebufferCount,
            boolean decodeFrameRateUsable,
            float decodeFrameRate,
            boolean outputFrameRateUsable,
            float outputFrameRate,
            boolean dropRateUsable,
            int dropRatePermille,
            boolean droppedFramesUsable,
            long droppedFrames,
            long nativeHeapBytes,
            long pssBytes) {

        public RuntimeSample {
            rebufferCount = Math.max(0, rebufferCount);
            decodeFrameRate = positiveFinite(decodeFrameRate)
                    ? decodeFrameRate : 0f;
            outputFrameRate = positiveFinite(outputFrameRate)
                    ? outputFrameRate : 0f;
            decodeFrameRateUsable &= decodeFrameRate > 0;
            outputFrameRateUsable &= outputFrameRate > 0;
            dropRatePermille = Math.max(0, dropRatePermille);
            droppedFrames = Math.max(0, droppedFrames);
            nativeHeapBytes = Math.max(-1, nativeHeapBytes);
            pssBytes = Math.max(-1, pssBytes);
        }

        public static RuntimeSample unknown() {
            return new RuntimeSample(
                    false, 0, false, 0f, false, 0f,
                    false, 0, false, 0, -1, -1);
        }

        private static boolean positiveFinite(float value) {
            return Float.isFinite(value) && value > 0;
        }
    }

    public record Decision(
            Action action,
            IjkRuntimeProfilePolicy.Path fromPath,
            IjkRuntimeProfilePolicy.Path targetPath,
            IjkRuntimeProfilePolicy.FailureAssessment assessment,
            Reason reason,
            String profileId,
            int fallbackCount,
            boolean failurePersisted,
            boolean fallbackFailureRecorded) {

        public Decision {
            action = action == null ? Action.HOLD : action;
            fromPath = fromPath == null
                    ? IjkRuntimeProfilePolicy.Path.IJK_HARD : fromPath;
            assessment = assessment == null
                    ? IjkRuntimeProfilePolicy.FailureAssessment.hold(
                    IjkRuntimeProfilePolicy.FailureKind.UNKNOWN,
                    IjkRuntimeProfilePolicy.Reason
                            .EXTERNAL_UNKNOWN_FAILURE)
                    : assessment;
            reason = reason == null ? Reason.NOT_MANAGED : reason;
            profileId = profileId == null ? "none" : profileId;
            fallbackCount = Math.max(0, fallbackCount);
        }

        static Decision hold(Reason reason) {
            return new Decision(
                    Action.HOLD,
                    IjkRuntimeProfilePolicy.Path.IJK_HARD,
                    null,
                    IjkRuntimeProfilePolicy.FailureAssessment.hold(
                            IjkRuntimeProfilePolicy.FailureKind.UNKNOWN,
                            IjkRuntimeProfilePolicy.Reason
                                    .EXTERNAL_UNKNOWN_FAILURE),
                    reason,
                    "none",
                    0,
                    false,
                    false);
        }

        public boolean requestsSwitch() {
            return action == Action.SWITCH && targetPath != null;
        }
    }

    public record Observation(
            ObservationAction action,
            Reason reason,
            String profileId,
            IjkRuntimeProfilePolicy.Path path,
            IjkRuntimeProfilePolicy.HealthAssessment health,
            IjkRuntimeProfileStore.Metrics metrics,
            boolean recorded,
            boolean fallbackSucceeded) {

        public Observation {
            action = action == null ? ObservationAction.HOLD : action;
            reason = reason == null ? Reason.NOT_MANAGED : reason;
            profileId = profileId == null ? "none" : profileId;
            path = path == null
                    ? IjkRuntimeProfilePolicy.Path.IJK_HARD : path;
            health = health == null
                    ? IjkRuntimeProfilePolicy.HealthAssessment.hold(
                    IjkRuntimeProfilePolicy.HealthReason.INACTIVE)
                    : health;
            metrics = metrics == null
                    ? IjkRuntimeProfileStore.Metrics.EMPTY : metrics;
        }

        static Observation hold(Reason reason) {
            return new Observation(
                    ObservationAction.HOLD,
                    reason,
                    "none",
                    IjkRuntimeProfilePolicy.Path.IJK_HARD,
                    IjkRuntimeProfilePolicy.HealthAssessment.hold(
                            IjkRuntimeProfilePolicy.HealthReason.INACTIVE),
                    IjkRuntimeProfileStore.Metrics.EMPTY,
                    false,
                    false);
        }

        public boolean material() {
            return action != ObservationAction.HOLD;
        }

        public int rebufferCount() {
            return metrics.rebufferCount();
        }

        public int dropRatePermille() {
            return metrics.dropRatePermille();
        }

        public int renderedRatioPermille() {
            return metrics.renderedRatioPermille();
        }

        public long nativeHeapGrowthBytes() {
            return metrics.nativeHeapGrowthBytes();
        }

        public long pssGrowthBytes() {
            return metrics.pssGrowthBytes();
        }
    }

    public record Snapshot(
            PlaybackAutoContext.SessionToken session,
            boolean managed,
            IjkRuntimeProfilePolicy.Path currentPath,
            Set<IjkRuntimeProfilePolicy.Path> visited,
            String profileId,
            int fallbackCount,
            boolean prepared,
            boolean firstFrame,
            boolean stableRecorded,
            boolean attemptFailureRecorded,
            boolean fallbackOutcomePending) {
    }

    public static final class Facts {

        private final PlaybackAutoContext.SessionToken session;
        private final IjkRuntimeProfileKey.Evidence evidence;
        private final PlaybackAutoContext.DecodeMode actualDecodeMode;
        private final boolean automatic;
        private final boolean softEligible;
        private final boolean targetFrameRateUsable;
        private final float targetFrameRate;

        private Facts(
                PlaybackAutoContext.SessionToken session,
                IjkRuntimeProfileKey.Evidence evidence,
                PlaybackAutoContext.DecodeMode actualDecodeMode,
                boolean automatic,
                boolean softEligible,
                boolean targetFrameRateUsable,
                float targetFrameRate) {
            this.session = session == null
                    ? PlaybackAutoContext.SessionToken.none() : session;
            this.evidence = evidence;
            this.actualDecodeMode = actualDecodeMode == null
                    ? PlaybackAutoContext.DecodeMode.UNKNOWN
                    : actualDecodeMode;
            this.automatic = automatic;
            this.softEligible = softEligible;
            this.targetFrameRateUsable = targetFrameRateUsable
                    && Float.isFinite(targetFrameRate)
                    && targetFrameRate > 0;
            this.targetFrameRate = this.targetFrameRateUsable
                    ? targetFrameRate : 0f;
        }

        public static Facts fromContext(
                PlaybackAutoContext context,
                boolean automatic,
                long nowElapsedMs) {
            PlaybackAutoContext current = context == null
                    ? PlaybackAutoContext.empty() : context;
            long now = Math.max(0, nowElapsedMs);
            PlaybackAutoContext.TrackFacts video = current.media().videoTrack();
            PlaybackAutoContext.DecoderFacts decoder = current.media().decoder();
            PlaybackAutoContext.OutputFacts output = current.media().output();
            PlaybackAutoContext.DeviceFacts device = current.device();
            PlaybackAutoContext.Fact<PlaybackAutoContext.Protocol> protocol =
                    current.resource().protocol();
            PlaybackAutoContext.Fact<PlaybackAutoContext.StreamKind> stream =
                    current.resource().streamKind();
            PlaybackAutoContext.Fact<String> mime = video.mimeType();
            PlaybackAutoContext.Fact<String> codecs = video.codecs();
            PlaybackAutoContext.Fact<Integer> profile = video.profile();
            PlaybackAutoContext.Fact<Integer> level = video.level();
            PlaybackAutoContext.Fact<Integer> width = video.width();
            PlaybackAutoContext.Fact<Integer> height = video.height();
            PlaybackAutoContext.Fact<Float> frameRate = video.frameRate();
            PlaybackAutoContext.Fact<PlaybackAutoContext.HdrType> hdr =
                    video.hdrType();
            PlaybackAutoContext.Fact<PlaybackAutoContext.ColorSnapshot> color =
                    video.color();
            PlaybackAutoContext.Fact<Boolean> secure =
                    decoder.secureVideoDecoder();
            PlaybackAutoContext.Fact<PlaybackAutoContext.RenderTarget> target =
                    output.renderTarget();
            boolean decoderSequenceCurrent = decoder.trackSequence()
                    == current.media().trackSequence();
            int secureState = decoderSequenceCurrent && secure.isUsable(now)
                    ? secure.value() ? 1 : 0 : -1;
            PlaybackAutoContext.ColorSnapshot colorValue = color.isUsable(now)
                    ? color.value() : PlaybackAutoContext.ColorSnapshot.unknown();
            IjkRuntimeProfileKey.Evidence evidence =
                    new IjkRuntimeProfileKey.Evidence(
                            protocol.isUsable(now)
                                    ? protocol.value()
                                    : PlaybackAutoContext.Protocol.UNKNOWN,
                            stream.isUsable(now)
                                    ? stream.value()
                                    : PlaybackAutoContext.StreamKind.UNKNOWN,
                            mime.isUsable(now) ? mime.value() : "",
                            codecs.isUsable(now) ? codecs.value() : "",
                            profile.isUsable(now) ? profile.value() : 0,
                            level.isUsable(now) ? level.value() : 0,
                            width.isUsable(now) ? width.value() : 0,
                            height.isUsable(now) ? height.value() : 0,
                            frameRate.isUsable(now)
                                    && frameRate.value() > 0
                                    ? Math.round(frameRate.value() * 1_000f)
                                    : 0,
                            hdr.isUsable(now)
                                    ? hdr.value()
                                    : PlaybackAutoContext.HdrType.UNKNOWN,
                            colorValue.colorSpace(),
                            colorValue.colorRange(),
                            colorValue.colorTransfer(),
                            colorValue.hdrStaticMetadata(),
                            secureState,
                            target.isUsable(now)
                                    ? target.value()
                                    : PlaybackAutoContext.RenderTarget.UNKNOWN);

            PlaybackAutoContext.DecodeMode actualDecode =
                    decoderSequenceCurrent
                    && decoder.videoDecodeMode().isUsable(now)
                    ? decoder.videoDecodeMode().value()
                    : PlaybackAutoContext.DecodeMode.UNKNOWN;
            long bitrate = usablePositive(video.peakBitrateBitsPerSecond(), now)
                    ? video.peakBitrateBitsPerSecond().value()
                    : usablePositive(video.averageBitrateBitsPerSecond(), now)
                    ? video.averageBitrateBitsPerSecond().value()
                    : usablePositive(
                    current.runtime().mediaBitrateBitsPerSecond(), now)
                    ? current.runtime().mediaBitrateBitsPerSecond().value() : 0;
            PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot>
                    memoryFact = device.memorySnapshot();
            PlaybackAutoContext.MemorySnapshot memory =
                    memoryFact.isUsable(now)
                    ? memoryFact.value()
                    : PlaybackAutoContext.MemorySnapshot.unknown();
            boolean videoFactsUsable = width.isUsable(now)
                    && height.isUsable(now)
                    && frameRate.isUsable(now)
                    && frameRate.value() > 0
                    && bitrate > 0;
            boolean systemFactsUsable = memoryFact.isUsable(now)
                    && memory.hasJavaMetrics()
                    && memory.lowRamDevice() != null
                    && device.memoryPressure().isUsable(now)
                    && device.thermalState().isUsable(now);
            IjkRuntimeProfilePolicy.SoftEligibilityInput softInput =
                    new IjkRuntimeProfilePolicy.SoftEligibilityInput(
                            automatic,
                            videoFactsUsable,
                            width.isUsable(now) ? width.value() : 0,
                            height.isUsable(now) ? height.value() : 0,
                            frameRate.isUsable(now)
                                    && frameRate.value() > 0
                                    ? Math.round(frameRate.value() * 1_000f)
                                    : 0,
                            bitrate,
                            systemFactsUsable,
                            memory.lowRamDevice() == null
                                    || memory.lowRamDevice(),
                            memory.javaHeapHeadroomBytes() == null
                                    ? 0 : memory.javaHeapHeadroomBytes(),
                            device.memoryPressure().isUsable(now)
                                    ? device.memoryPressure().value()
                                    : PlaybackAutoContext.MemoryPressure.UNKNOWN,
                            device.thermalState().isUsable(now)
                                    ? device.thermalState().value()
                                    : PlaybackAutoContext.ThermalState.UNKNOWN);
            return new Facts(
                    current.session(),
                    evidence,
                    actualDecode,
                    automatic,
                    IjkRuntimeProfilePolicy.allowIjkSoftware(softInput),
                    frameRate.isUsable(now) && frameRate.value() > 0,
                    frameRate.isUsable(now) ? frameRate.value() : 0f);
        }

        static Facts forTest(
                PlaybackAutoContext.SessionToken session,
                IjkRuntimeProfileKey.Evidence evidence,
                PlaybackAutoContext.DecodeMode actualDecodeMode,
                boolean automatic,
                boolean softEligible,
                float targetFrameRate) {
            return new Facts(
                    session,
                    evidence,
                    actualDecodeMode,
                    automatic,
                    softEligible,
                    targetFrameRate > 0,
                    targetFrameRate);
        }

        static Facts inactive() {
            return new Facts(
                    PlaybackAutoContext.SessionToken.none(),
                    null,
                    PlaybackAutoContext.DecodeMode.UNKNOWN,
                    false,
                    false,
                    false,
                    0f);
        }

        private static boolean usablePositive(
                PlaybackAutoContext.Fact<Long> fact,
                long nowElapsedMs) {
            return fact != null
                    && fact.isUsable(nowElapsedMs)
                    && fact.value() > 0;
        }

        public PlaybackAutoContext.SessionToken session() {
            return session;
        }

        public PlaybackAutoContext.DecodeMode actualDecodeMode() {
            return actualDecodeMode;
        }

        public boolean automatic() {
            return automatic;
        }

        public boolean softEligible() {
            return softEligible;
        }

        public boolean hasVideoTrackEvidence() {
            return evidence != null
                    && evidence.width() > 0
                    && evidence.height() > 0;
        }
        public boolean targetFrameRateUsable() {
            return targetFrameRateUsable;
        }

        public float targetFrameRate() {
            return targetFrameRate;
        }
    }

    interface ProfileAccess {

        IjkRuntimeProfileKey.Environment environment();

        boolean isExcluded(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                long nowEpochMs);

        IjkRuntimeProfilePolicy.Path preferredVerifiedPath(
                IjkRuntimeProfileKey.Key key,
                Set<IjkRuntimeProfilePolicy.Path> blocked,
                boolean softEligible,
                long nowEpochMs);

        IjkRuntimeProfileStore.Entry recordFirstFrame(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                long nowEpochMs);

        IjkRuntimeProfileStore.Entry recordFailure(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                IjkRuntimeProfilePolicy.FailureKind failureKind,
                IjkRuntimeProfileStore.Metrics metrics,
                long nowEpochMs);

        IjkRuntimeProfileStore.Entry recordStableSuccess(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                IjkRuntimeProfileStore.Metrics metrics,
                long nowEpochMs);

        IjkRuntimeProfileStore.Entry recordObservation(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                IjkRuntimeProfileStore.Metrics metrics,
                long nowEpochMs);

        IjkRuntimeProfileStore.Entry recordFallback(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                IjkRuntimeProfileStore.FallbackResult fallbackResult,
                long nowEpochMs);

        void logExclusion(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                boolean transientOnly);
    }
}
