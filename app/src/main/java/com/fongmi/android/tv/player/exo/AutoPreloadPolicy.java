package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackRoute;

/** Pure foreground-first EXO preload policy. It never changes Media3 ABR. */
final class AutoPreloadPolicy {

    static final int PAUSED_THREADS = 0;
    static final int NORMAL_THREADS = 1;
    static final int FAST_THREADS = 2;
    static final long DEGRADED_DURATION_MS = 10_000;
    static final long NORMAL_DURATION_MS = 20_000;
    static final long FAST_DURATION_MS = 30_000;
    static final long EXTERNAL_LOOPBACK_DURATION_MS = 40_000;
    static final long THROUGHPUT_MAX_AGE_MS = 65_000;
    static final long TREND_MAX_AGE_MS = 20_000;

    private static final long FRONT_BUFFER_PAUSE_MS = 6_000;
    private static final long NORMAL_BUFFER_MS = 12_000;
    private static final long FAST_BUFFER_MS = 20_000;
    private static final long FAST_FALLBACK_BUFFER_MS = 12_000;
    private static final long EXTERNAL_LOOPBACK_RESUME_BUFFER_MS = 12_000;
    private static final long APP_PROXY_RECOVERY_BUFFER_MS =
            ExoNetworkGuardBufferPolicy.LOOPBACK_FLOOR_MS;
    private static final long EXTERNAL_LOOPBACK_NORMAL_BUFFER_MS = 20_000;
    private static final long NORMAL_STABLE_MS = 20_000;
    private static final long RESUME_DELAY_MS = 10_000;
    private static final long WEAK_RESUME_DELAY_MS = 15_000;
    private static final long FAST_STABLE_MS = 30_000;
    private static final long FAST_COOLDOWN_MS = 60_000;
    private static final long CRITICAL_TIME_TO_EMPTY_MS = 15_000;
    private static final long WARNING_TIME_TO_EMPTY_MS = 45_000;
    private static final long PAUSE_SLOPE_MS_PER_SECOND = -500;
    private static final long NORMAL_SLOPE_MS_PER_SECOND = -250;
    private static final double PAUSE_RATIO = 1.15;
    private static final double SHORT_PAUSE_RATIO = 1.00;
    private static final double RESUME_RATIO = 1.40;
    private static final double SHORT_RESUME_RATIO = 1.20;
    private static final double NORMAL_RATIO = 2.00;
    private static final double NORMAL_SHORT_RATIO = 1.50;
    private static final double FAST_RATIO = 3.00;
    private static final double FAST_WINDOW_RATIO = 2.50;
    private static final double FAST_FALLBACK_RATIO = 2.00;
    private static final double FAST_FALLBACK_SHORT_RATIO = 1.50;
    private static final int NORMAL_MAX_PREDICTION_ERROR_PERMILLE = 750;

    private Mode mode = Mode.DEGRADED;
    private long resumeAfterMs;
    private long fastBlockedUntilMs;
    private long normalStableSinceMs = Long.MIN_VALUE;
    private long fastStableSinceMs = Long.MIN_VALUE;
    private int lastRebufferCount;
    private Reason lastReason = Reason.EVIDENCE_UNKNOWN;

    Decision evaluate(Inputs input) {
        Inputs safe = input == null ? Inputs.unknown() : input;
        long nowMs = safe.nowElapsedMs();
        if (safe.rebufferCount() > lastRebufferCount) {
            pause(nowMs, WEAK_RESUME_DELAY_MS, Reason.REBUFFER);
        }
        lastRebufferCount = Math.max(lastRebufferCount, safe.rebufferCount());

        Reason hardPause = hardPauseReason(safe);
        if (hardPause != null) {
            pause(nowMs, hardPause == Reason.REBUFFER ? WEAK_RESUME_DELAY_MS : RESUME_DELAY_MS,
                    hardPause);
            return decision(safe.route());
        }

        if (mode == Mode.PAUSED) {
            if (nowMs < resumeAfterMs || !resumeEligible(safe)) {
                return decision(safe.route());
            }
            mode = Mode.DEGRADED;
            normalStableSinceMs = Long.MIN_VALUE;
            fastStableSinceMs = Long.MIN_VALUE;
            lastReason = Reason.RECOVERY_STABILIZING;
        }

        Reason caution = cautionReason(safe, mode == Mode.FAST);
        boolean normalEligible = caution == null;
        boolean fastEligible = normalEligible && fastEligible(safe, false);

        if (mode == Mode.FAST) {
            if (!fastHoldEligible(safe)) {
                mode = normalEligible ? Mode.NORMAL : Mode.DEGRADED;
                normalStableSinceMs = normalEligible ? nowMs : Long.MIN_VALUE;
                fastStableSinceMs = Long.MIN_VALUE;
                lastReason = normalEligible ? Reason.FAST_FALLBACK : caution;
            } else {
                lastReason = Reason.STABLE_FAST;
            }
            return decision(safe.route());
        }

        if (!normalEligible) {
            mode = Mode.DEGRADED;
            normalStableSinceMs = Long.MIN_VALUE;
            fastStableSinceMs = Long.MIN_VALUE;
            lastReason = caution;
            return decision(safe.route());
        }

        if (normalStableSinceMs == Long.MIN_VALUE) normalStableSinceMs = nowMs;
        if (fastEligible) {
            if (fastStableSinceMs == Long.MIN_VALUE) fastStableSinceMs = nowMs;
        } else {
            fastStableSinceMs = Long.MIN_VALUE;
        }

        if (nowMs - normalStableSinceMs >= NORMAL_STABLE_MS) {
            mode = Mode.NORMAL;
            lastReason = Reason.STABLE_NORMAL;
        } else {
            mode = Mode.DEGRADED;
            lastReason = Reason.RECOVERY_STABILIZING;
        }
        if (fastEligible
                && nowMs >= fastBlockedUntilMs
                && fastStableSinceMs != Long.MIN_VALUE
                && nowMs - fastStableSinceMs >= FAST_STABLE_MS) {
            mode = Mode.FAST;
            lastReason = Reason.STABLE_FAST;
        }
        return decision(safe.route());
    }

    void disrupt(long nowMs) {
        disrupt(nowMs, Reason.DISRUPTED);
    }

    void disrupt(long nowMs, Reason reason) {
        pause(Math.max(0, nowMs), RESUME_DELAY_MS,
                reason == null ? Reason.DISRUPTED : reason);
    }

    private Reason hardPauseReason(Inputs input) {
        if (!input.sessionMatches()) return Reason.SESSION_MISMATCH;
        if (input.memoryPreloadPaused()) return Reason.MEMORY_PRESSURE;
        SystemEvidence system = input.system();
        if (system.networkUsable()) {
            if (Boolean.FALSE.equals(system.available())) return Reason.NETWORK_UNAVAILABLE;
            if (Boolean.FALSE.equals(system.validated())) return Reason.NETWORK_UNVALIDATED;
            if (system.dataSaver() == PlaybackAutoContext.DataSaverState.ENABLED) {
                return Reason.DATA_SAVER;
            }
        }
        if (system.powerUsable()
                && system.power() == PlaybackAutoContext.PowerState.POWER_SAVE) {
            return Reason.POWER_SAVE;
        }
        if (system.thermalUsable()
                && (system.thermal() == PlaybackAutoContext.ThermalState.SEVERE
                || system.thermal() == PlaybackAutoContext.ThermalState.CRITICAL)) {
            return Reason.THERMAL_PRESSURE;
        }
        if (unknownAppProxyRecovery(input)
                && input.bufferedMs() < APP_PROXY_RECOVERY_BUFFER_MS) {
            return Reason.FOREGROUND_RECOVERY;
        }
        if (input.loading() && input.bufferedMs() < PreCachePolicy.INITIAL_SAFE_BUFFER_MS) {
            return Reason.FRONT_BUFFER_LOW;
        }
        if (input.bufferedMs() < FRONT_BUFFER_PAUSE_MS) return Reason.FRONT_BUFFER_LOW;

        TrendEvidence trend = TrendEvidence.from(input.trend(), input.nowElapsedMs());
        if (trend.usable()) {
            if (trend.timeToEmptyMs() >= 0
                    && trend.timeToEmptyMs() <= CRITICAL_TIME_TO_EMPTY_MS) {
                return Reason.TIME_TO_EMPTY;
            }
            if (trend.slopeMsPerSecond() <= PAUSE_SLOPE_MS_PER_SECOND
                    && input.bufferedMs() < NORMAL_BUFFER_MS) {
                return Reason.BUFFER_DECLINING;
            }
        }

        ThroughputEvidence throughput = input.throughput();
        if (throughput.usable()
                && throughput.pathTrust() != ExoThroughputPathPolicy.Trust.BLOCKED
                && input.mediaBitrateBitsPerSecond() > 0) {
            double effectiveRatio = ratio(
                    throughput.effectiveBitsPerSecond(), input.mediaBitrateBitsPerSecond());
            double shortRatio = ratio(
                    throughput.shortBitsPerSecond(), input.mediaBitrateBitsPerSecond());
            if (effectiveRatio > 0 && effectiveRatio < PAUSE_RATIO) {
                return Reason.THROUGHPUT_DEFICIT;
            }
            if (shortRatio > 0 && shortRatio < SHORT_PAUSE_RATIO) {
                return Reason.SHORT_WINDOW_DEFICIT;
            }
        }
        return null;
    }

    private boolean resumeEligible(Inputs input) {
        long requiredBuffer = unknownAppProxyRecovery(input)
                ? APP_PROXY_RECOVERY_BUFFER_MS
                : input.route() == PlaybackRoute.EXTERNAL_LOOPBACK_PROXY
                ? EXTERNAL_LOOPBACK_RESUME_BUFFER_MS
                : NORMAL_BUFFER_MS;
        if (input.bufferedMs() < requiredBuffer) return false;
        TrendEvidence trend = TrendEvidence.from(input.trend(), input.nowElapsedMs());
        if (trend.usable()) {
            if (trend.timeToEmptyMs() >= 0
                    && trend.timeToEmptyMs() <= WARNING_TIME_TO_EMPTY_MS) return false;
            if (trend.slopeMsPerSecond() < NORMAL_SLOPE_MS_PER_SECOND) return false;
        }
        ThroughputEvidence throughput = input.throughput();
        if (!throughput.usable()
                || throughput.pathTrust() == ExoThroughputPathPolicy.Trust.BLOCKED
                || input.mediaBitrateBitsPerSecond() <= 0) {
            return true;
        }
        double effectiveRatio = ratio(
                throughput.effectiveBitsPerSecond(), input.mediaBitrateBitsPerSecond());
        double shortRatio = ratio(
                throughput.shortBitsPerSecond(), input.mediaBitrateBitsPerSecond());
        return (effectiveRatio <= 0 || effectiveRatio >= RESUME_RATIO)
                && (shortRatio <= 0 || shortRatio >= SHORT_RESUME_RATIO);
    }

    private Reason cautionReason(Inputs input, boolean holdingFast) {
        SystemEvidence system = input.system();
        if (system.networkUsable()) {
            if (Boolean.TRUE.equals(system.roaming())) return Reason.ROAMING;
            if (Boolean.TRUE.equals(system.metered())) return Reason.METERED;
        }
        if (system.networkCostUsable()) {
            if (system.networkCost() == PlaybackAutoContext.NetworkCost.ROAMING) {
                return Reason.ROAMING;
            }
            if (system.networkCost() == PlaybackAutoContext.NetworkCost.METERED) {
                return Reason.METERED;
            }
        }
        if (system.thermalUsable()
                && system.thermal() == PlaybackAutoContext.ThermalState.MODERATE) {
            return Reason.THERMAL_MODERATE;
        }
        if (system.networkUsable()
                && system.dataSaver() == PlaybackAutoContext.DataSaverState.WHITELISTED) {
            return Reason.DATA_SAVER_WHITELISTED;
        }
        if (!system.explicitlySafe()) return Reason.SYSTEM_EVIDENCE_UNKNOWN;
        if (system.transport() == PlaybackAutoContext.NetworkTransport.VPN) {
            return Reason.VPN;
        }
        if (input.route() == PlaybackRoute.OTHER) return Reason.PATH_LIMITED;
        if (input.bufferedMs() < NORMAL_BUFFER_MS) return Reason.FRONT_BUFFER_MARGIN;

        TrendEvidence trend = TrendEvidence.from(input.trend(), input.nowElapsedMs());
        if (!trend.usable()) return Reason.BUFFER_EVIDENCE_UNKNOWN;
        if (trend.timeToEmptyMs() >= 0
                && trend.timeToEmptyMs() <= WARNING_TIME_TO_EMPTY_MS) {
            return Reason.TIME_TO_EMPTY;
        }
        if (trend.slopeMsPerSecond() < NORMAL_SLOPE_MS_PER_SECOND) {
            return Reason.BUFFER_DECLINING;
        }

        if (input.route() == PlaybackRoute.EXTERNAL_LOOPBACK_PROXY) {
            if (input.bufferedMs() < EXTERNAL_LOOPBACK_NORMAL_BUFFER_MS) {
                return Reason.EXTERNAL_BUFFER_MARGIN;
            }
            return null;
        }

        ThroughputEvidence throughput = input.throughput();
        if (!throughput.usable()) return Reason.THROUGHPUT_EVIDENCE_UNKNOWN;
        if (throughput.pathTrust() != ExoThroughputPathPolicy.Trust.TRUSTED
                || !confidenceAtLeast(
                throughput.pathConfidence(), PlaybackAutoContext.Confidence.MEDIUM)) {
            return Reason.PATH_LIMITED;
        }
        if (throughput.preloadContended() && !input.preloadActive()) {
            return Reason.PRELOAD_CONTENTION;
        }
        if (!confidenceAtLeast(
                throughput.confidence(), PlaybackAutoContext.Confidence.LOW)) {
            return Reason.THROUGHPUT_EVIDENCE_UNKNOWN;
        }
        if (throughput.predictionErrorPermille() < 0
                || throughput.predictionErrorPermille()
                > NORMAL_MAX_PREDICTION_ERROR_PERMILLE) {
            return Reason.PREDICTION_ERROR;
        }
        if (input.mediaBitrateBitsPerSecond() <= 0) return Reason.MEDIA_BITRATE_UNKNOWN;
        double effectiveRatio = ratio(
                throughput.effectiveBitsPerSecond(), input.mediaBitrateBitsPerSecond());
        double shortRatio = ratio(
                throughput.shortBitsPerSecond(), input.mediaBitrateBitsPerSecond());
        if (effectiveRatio < NORMAL_RATIO || shortRatio < NORMAL_SHORT_RATIO) {
            return Reason.THROUGHPUT_MARGIN;
        }
        if (materiallyBelow(
                throughput.shortBitsPerSecond(), throughput.longBitsPerSecond(), 80)) {
            return Reason.SHORT_WINDOW_DECLINE;
        }
        if (holdingFast && !fastHoldEligible(input)) return Reason.FAST_FALLBACK;
        return null;
    }

    private boolean fastEligible(Inputs input, boolean holdingFast) {
        if (!supportsFast(input.route()) || input.bufferedMs() < FAST_BUFFER_MS) return false;
        SystemEvidence system = input.system();
        if (!system.explicitlySafe()
                || system.transport() == PlaybackAutoContext.NetworkTransport.VPN) return false;
        TrendEvidence trend = TrendEvidence.from(input.trend(), input.nowElapsedMs());
        if (!trend.usable()
                || !trend.confidenceAtLeast(ForwardBufferTrend.Confidence.MEDIUM)
                || trend.slopeMsPerSecond() < 0) return false;

        ThroughputEvidence throughput = input.throughput();
        if (!throughput.usable()
                || throughput.pathTrust() != ExoThroughputPathPolicy.Trust.TRUSTED
                || !confidenceAtLeast(
                throughput.pathConfidence(), PlaybackAutoContext.Confidence.MEDIUM)
                || !confidenceAtLeast(
                throughput.confidence(), PlaybackAutoContext.Confidence.MEDIUM)
                || throughput.longSampleCount() < ExoThroughputEstimator.MIN_UPGRADE_SAMPLES
                || throughput.longWindowMs() < ExoThroughputEstimator.MIN_UPGRADE_WINDOW_MS
                || throughput.predictionErrorPermille() < 0
                || throughput.predictionErrorPermille()
                > ExoThroughputEstimator.MAX_UPGRADE_ERROR_PERMILLE
                || throughput.preloadContended() && !(holdingFast && input.preloadActive())
                || input.mediaBitrateBitsPerSecond() <= 0) return false;
        return ratio(throughput.effectiveBitsPerSecond(), input.mediaBitrateBitsPerSecond())
                >= FAST_RATIO
                && ratio(throughput.shortBitsPerSecond(), input.mediaBitrateBitsPerSecond())
                >= FAST_WINDOW_RATIO
                && ratio(throughput.longBitsPerSecond(), input.mediaBitrateBitsPerSecond())
                >= FAST_WINDOW_RATIO
                && !materiallyBelow(
                throughput.shortBitsPerSecond(), throughput.longBitsPerSecond(), 80);
    }

    private boolean fastHoldEligible(Inputs input) {
        if (!supportsFast(input.route())
                || input.bufferedMs() < FAST_FALLBACK_BUFFER_MS
                || !input.system().explicitlySafe()
                || input.system().transport() == PlaybackAutoContext.NetworkTransport.VPN) {
            return false;
        }
        TrendEvidence trend = TrendEvidence.from(input.trend(), input.nowElapsedMs());
        if (!trend.usable() || trend.slopeMsPerSecond() < NORMAL_SLOPE_MS_PER_SECOND) {
            return false;
        }
        ThroughputEvidence throughput = input.throughput();
        if (!throughput.usable()
                || throughput.pathTrust() != ExoThroughputPathPolicy.Trust.TRUSTED
                || !confidenceAtLeast(
                throughput.pathConfidence(), PlaybackAutoContext.Confidence.MEDIUM)
                || throughput.predictionErrorPermille() < 0
                || throughput.predictionErrorPermille()
                > NORMAL_MAX_PREDICTION_ERROR_PERMILLE
                || throughput.preloadContended() && !input.preloadActive()
                || materiallyBelow(
                throughput.shortBitsPerSecond(), throughput.longBitsPerSecond(), 80)
                || input.mediaBitrateBitsPerSecond() <= 0) return false;
        return ratio(throughput.effectiveBitsPerSecond(), input.mediaBitrateBitsPerSecond())
                >= FAST_FALLBACK_RATIO
                && ratio(throughput.shortBitsPerSecond(), input.mediaBitrateBitsPerSecond())
                >= FAST_FALLBACK_SHORT_RATIO;
    }

    private void pause(long nowMs, long delayMs, Reason reason) {
        mode = Mode.PAUSED;
        resumeAfterMs = Math.max(resumeAfterMs, nowMs + Math.max(0, delayMs));
        fastBlockedUntilMs = Math.max(fastBlockedUntilMs, nowMs + FAST_COOLDOWN_MS);
        normalStableSinceMs = Long.MIN_VALUE;
        fastStableSinceMs = Long.MIN_VALUE;
        lastReason = reason == null ? Reason.DISRUPTED : reason;
    }

    private Decision decision(PlaybackRoute route) {
        if (mode == Mode.PAUSED) {
            return new Decision(PAUSED_THREADS, 0, mode.label(), lastReason.label());
        }
        if (route == PlaybackRoute.EXTERNAL_LOOPBACK_PROXY) {
            long durationMs = mode == Mode.NORMAL
                    ? EXTERNAL_LOOPBACK_DURATION_MS : DEGRADED_DURATION_MS;
            return new Decision(NORMAL_THREADS, durationMs, mode.label(), lastReason.label());
        }
        return switch (mode) {
            case FAST -> new Decision(
                    FAST_THREADS, FAST_DURATION_MS, mode.label(), lastReason.label());
            case NORMAL -> new Decision(
                    NORMAL_THREADS, NORMAL_DURATION_MS, mode.label(), lastReason.label());
            default -> new Decision(
                    NORMAL_THREADS, DEGRADED_DURATION_MS, mode.label(), lastReason.label());
        };
    }

    private static boolean supportsFast(PlaybackRoute route) {
        return route == PlaybackRoute.DIRECT_REMOTE_HTTP
                || route == PlaybackRoute.APP_LOCAL_SERVICE;
    }

    private static boolean unknownAppProxyRecovery(Inputs input) {
        return input.route() == PlaybackRoute.APP_LOCAL_SERVICE
                && input.mediaBitrateBitsPerSecond() <= 0
                && input.rebufferCount() > 0;
    }

    private static boolean confidenceAtLeast(
            PlaybackAutoContext.Confidence actual,
            PlaybackAutoContext.Confidence required) {
        return confidenceRank(actual) >= confidenceRank(required);
    }

    private static int confidenceRank(PlaybackAutoContext.Confidence confidence) {
        if (confidence == null) return 0;
        return switch (confidence) {
            case UNKNOWN -> 0;
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
        };
    }

    private static boolean materiallyBelow(long candidate, long reference, int percent) {
        if (candidate <= 0 || reference <= 0 || percent <= 0) return false;
        return (double) candidate * 100d < (double) reference * percent;
    }

    private static double ratio(long bandwidth, long mediaBitrate) {
        if (bandwidth <= 0 || mediaBitrate <= 0) return 0;
        return (double) bandwidth / (double) mediaBitrate;
    }

    record Inputs(
            PlaybackAutoContext.SessionToken session,
            boolean sessionMatches,
            PlaybackRoute route,
            long bufferedMs,
            long mediaBitrateBitsPerSecond,
            int rebufferCount,
            boolean loading,
            ForwardBufferTrend.Snapshot trend,
            ThroughputEvidence throughput,
            SystemEvidence system,
            boolean memoryPreloadPaused,
            boolean preloadActive,
            long nowElapsedMs) {

        Inputs {
            session = session == null
                    ? PlaybackAutoContext.SessionToken.none() : session;
            route = route == null ? PlaybackRoute.OTHER : route;
            bufferedMs = Math.max(0, bufferedMs);
            mediaBitrateBitsPerSecond = Math.max(0, mediaBitrateBitsPerSecond);
            rebufferCount = Math.max(0, rebufferCount);
            trend = trend == null ? ForwardBufferTrend.Snapshot.unknown() : trend;
            throughput = throughput == null
                    ? ThroughputEvidence.unknown() : throughput;
            system = system == null ? SystemEvidence.unknown() : system;
            nowElapsedMs = Math.max(0, nowElapsedMs);
        }

        static Inputs capture(
                long nowElapsedMs,
                PlaybackAutoContext.SessionToken expectedSession,
                PlaybackRoute route,
                long bufferedMs,
                long mediaBitrateBitsPerSecond,
                int rebufferCount,
                boolean loading,
                ForwardBufferTrend.Snapshot trend,
                ExoThroughputEstimator.Snapshot throughput,
                PlaybackAutoContext context,
                boolean memoryPreloadPaused,
                boolean preloadActive) {
            PlaybackAutoContext.SessionToken expected = expectedSession == null
                    ? PlaybackAutoContext.SessionToken.none() : expectedSession;
            PlaybackAutoContext safeContext = context == null
                    ? PlaybackAutoContext.empty() : context;
            boolean sessionMatches = expected.active()
                    && expected.equals(safeContext.session())
                    && (!safeContext.kernel().isUsable(nowElapsedMs)
                    || safeContext.kernel().value() == PlaybackAutoContext.Kernel.EXO);
            return new Inputs(
                    expected,
                    sessionMatches,
                    route,
                    bufferedMs,
                    mediaBitrateBitsPerSecond,
                    rebufferCount,
                    loading,
                    trend,
                    ThroughputEvidence.capture(
                            expected, throughput, nowElapsedMs),
                    sessionMatches
                            ? SystemEvidence.capture(safeContext.device(), nowElapsedMs)
                            : SystemEvidence.unknown(),
                    memoryPreloadPaused,
                    preloadActive,
                    nowElapsedMs);
        }

        static Inputs unknown() {
            return new Inputs(
                    PlaybackAutoContext.SessionToken.none(),
                    false,
                    PlaybackRoute.OTHER,
                    0,
                    0,
                    0,
                    false,
                    ForwardBufferTrend.Snapshot.unknown(),
                    ThroughputEvidence.unknown(),
                    SystemEvidence.unknown(),
                    false,
                    false,
                    0);
        }
    }

    record ThroughputEvidence(
            boolean usable,
            long effectiveBitsPerSecond,
            long shortBitsPerSecond,
            long longBitsPerSecond,
            int longSampleCount,
            long longWindowMs,
            int predictionErrorPermille,
            PlaybackAutoContext.Confidence confidence,
            ExoThroughputPathPolicy.Trust pathTrust,
            PlaybackAutoContext.Confidence pathConfidence,
            boolean preloadContended,
            long observedAtElapsedMs) {

        ThroughputEvidence {
            effectiveBitsPerSecond = Math.max(0, effectiveBitsPerSecond);
            shortBitsPerSecond = Math.max(0, shortBitsPerSecond);
            longBitsPerSecond = Math.max(0, longBitsPerSecond);
            longSampleCount = Math.max(0, longSampleCount);
            longWindowMs = Math.max(0, longWindowMs);
            confidence = confidence == null
                    ? PlaybackAutoContext.Confidence.UNKNOWN : confidence;
            pathTrust = pathTrust == null
                    ? ExoThroughputPathPolicy.Trust.LIMITED : pathTrust;
            pathConfidence = pathConfidence == null
                    ? PlaybackAutoContext.Confidence.UNKNOWN : pathConfidence;
        }

        static ThroughputEvidence capture(
                PlaybackAutoContext.SessionToken expectedSession,
                ExoThroughputEstimator.Snapshot snapshot,
                long nowElapsedMs) {
            ExoThroughputEstimator.Snapshot safe = snapshot == null
                    ? ExoThroughputEstimator.Snapshot.empty() : snapshot;
            long ageMs = safe.observedAtElapsedMs() < 0
                    ? Long.MAX_VALUE : nowElapsedMs - safe.observedAtElapsedMs();
            boolean usable = expectedSession != null
                    && expectedSession.active()
                    && expectedSession.equals(safe.session())
                    && ageMs >= 0
                    && ageMs <= THROUGHPUT_MAX_AGE_MS
                    && safe.effectiveEstimateBitsPerSecond() > 0;
            return new ThroughputEvidence(
                    usable,
                    safe.effectiveEstimateBitsPerSecond(),
                    safe.shortEstimateBitsPerSecond(),
                    safe.longEstimateBitsPerSecond(),
                    safe.longSampleCount(),
                    safe.longWindowMs(),
                    safe.predictionErrorPermille(),
                    safe.confidence(),
                    safe.pathTrust(),
                    safe.pathConfidence(),
                    safe.preloadContended(),
                    safe.observedAtElapsedMs());
        }

        static ThroughputEvidence unknown() {
            return new ThroughputEvidence(
                    false, 0, 0, 0, 0, 0, -1,
                    PlaybackAutoContext.Confidence.UNKNOWN,
                    ExoThroughputPathPolicy.Trust.LIMITED,
                    PlaybackAutoContext.Confidence.UNKNOWN,
                    false,
                    -1);
        }
    }

    record SystemEvidence(
            boolean networkUsable,
            Boolean available,
            Boolean validated,
            Boolean metered,
            Boolean roaming,
            PlaybackAutoContext.NetworkTransport transport,
            PlaybackAutoContext.DataSaverState dataSaver,
            boolean networkCostUsable,
            PlaybackAutoContext.NetworkCost networkCost,
            boolean powerUsable,
            PlaybackAutoContext.PowerState power,
            boolean thermalUsable,
            PlaybackAutoContext.ThermalState thermal) {

        SystemEvidence {
            transport = transport == null
                    ? PlaybackAutoContext.NetworkTransport.UNKNOWN : transport;
            dataSaver = dataSaver == null
                    ? PlaybackAutoContext.DataSaverState.UNKNOWN : dataSaver;
            networkCost = networkCost == null
                    ? PlaybackAutoContext.NetworkCost.UNKNOWN : networkCost;
            power = power == null
                    ? PlaybackAutoContext.PowerState.UNKNOWN : power;
            thermal = thermal == null
                    ? PlaybackAutoContext.ThermalState.UNKNOWN : thermal;
        }

        static SystemEvidence capture(
                PlaybackAutoContext.DeviceFacts device,
                long nowElapsedMs) {
            PlaybackAutoContext.DeviceFacts safe = device == null
                    ? PlaybackAutoContext.DeviceFacts.unknown() : device;
            PlaybackAutoContext.Fact<PlaybackAutoContext.NetworkSnapshot> networkFact =
                    safe.networkSnapshot();
            PlaybackAutoContext.Fact<PlaybackAutoContext.NetworkCost> costFact =
                    safe.networkCost();
            PlaybackAutoContext.Fact<PlaybackAutoContext.PowerState> powerFact =
                    safe.powerState();
            PlaybackAutoContext.Fact<PlaybackAutoContext.ThermalState> thermalFact =
                    safe.thermalState();
            boolean networkUsable = networkFact.isUsable(nowElapsedMs);
            PlaybackAutoContext.NetworkSnapshot network = networkUsable
                    ? networkFact.value() : PlaybackAutoContext.NetworkSnapshot.unknown();
            return new SystemEvidence(
                    networkUsable,
                    network.available(),
                    network.validated(),
                    network.metered(),
                    network.roaming(),
                    network.transport(),
                    network.dataSaverState(),
                    costFact.isUsable(nowElapsedMs),
                    costFact.isUsable(nowElapsedMs)
                            ? costFact.value() : PlaybackAutoContext.NetworkCost.UNKNOWN,
                    powerFact.isUsable(nowElapsedMs),
                    powerFact.isUsable(nowElapsedMs)
                            ? powerFact.value() : PlaybackAutoContext.PowerState.UNKNOWN,
                    thermalFact.isUsable(nowElapsedMs),
                    thermalFact.isUsable(nowElapsedMs)
                            ? thermalFact.value() : PlaybackAutoContext.ThermalState.UNKNOWN);
        }

        static SystemEvidence unknown() {
            return new SystemEvidence(
                    false,
                    null,
                    null,
                    null,
                    null,
                    PlaybackAutoContext.NetworkTransport.UNKNOWN,
                    PlaybackAutoContext.DataSaverState.UNKNOWN,
                    false,
                    PlaybackAutoContext.NetworkCost.UNKNOWN,
                    false,
                    PlaybackAutoContext.PowerState.UNKNOWN,
                    false,
                    PlaybackAutoContext.ThermalState.UNKNOWN);
        }

        boolean explicitlySafe() {
            return networkUsable
                    && Boolean.TRUE.equals(available)
                    && Boolean.TRUE.equals(validated)
                    && Boolean.FALSE.equals(metered)
                    && Boolean.FALSE.equals(roaming)
                    && dataSaver == PlaybackAutoContext.DataSaverState.DISABLED
                    && networkCostUsable
                    && networkCost == PlaybackAutoContext.NetworkCost.UNMETERED
                    && powerUsable
                    && power == PlaybackAutoContext.PowerState.NORMAL
                    && thermalUsable
                    && thermal == PlaybackAutoContext.ThermalState.NOMINAL;
        }
    }

    private record TrendEvidence(
            boolean usable,
            long slopeMsPerSecond,
            long timeToEmptyMs,
            ForwardBufferTrend.Confidence confidence) {

        static TrendEvidence from(
                ForwardBufferTrend.Snapshot snapshot,
                long nowElapsedMs) {
            ForwardBufferTrend.Snapshot safe = snapshot == null
                    ? ForwardBufferTrend.Snapshot.unknown() : snapshot;
            long ageMs = safe.sampledAtElapsedMs() < 0
                    ? Long.MAX_VALUE : nowElapsedMs - safe.sampledAtElapsedMs();
            boolean usable = safe.known()
                    && ageMs >= 0
                    && ageMs <= TREND_MAX_AGE_MS;
            return new TrendEvidence(
                    usable,
                    safe.slopeMsPerSecond(),
                    safe.timeToEmptyMs(),
                    safe.confidence());
        }

        boolean confidenceAtLeast(ForwardBufferTrend.Confidence required) {
            return trendConfidenceRank(confidence) >= trendConfidenceRank(required);
        }

        private static int trendConfidenceRank(ForwardBufferTrend.Confidence confidence) {
            if (confidence == null) return 0;
            return switch (confidence) {
                case UNKNOWN -> 0;
                case LOW -> 1;
                case MEDIUM -> 2;
                case HIGH -> 3;
            };
        }
    }

    record Decision(int threads, long durationMs, String mode, String reason) {

        boolean enabled() {
            return threads > 0 && durationMs > 0;
        }

        boolean moreRestrictiveThan(Decision previous) {
            if (previous == null) return false;
            if (threads != previous.threads()) return threads < previous.threads();
            return durationMs < previous.durationMs();
        }
    }

    enum Reason {
        SESSION_MISMATCH("session-mismatch"),
        MEMORY_PRESSURE("memory-pressure"),
        REBUFFER("rebuffer"),
        NETWORK_CHANGED("network-changed"),
        NETWORK_UNAVAILABLE("network-unavailable"),
        NETWORK_UNVALIDATED("network-unvalidated"),
        METERED("metered"),
        ROAMING("roaming"),
        DATA_SAVER("data-saver"),
        DATA_SAVER_WHITELISTED("data-saver-whitelisted"),
        POWER_SAVE("power-save"),
        THERMAL_PRESSURE("thermal-pressure"),
        THERMAL_MODERATE("thermal-moderate"),
        FOREGROUND_RECOVERY("foreground-recovery"),
        FRONT_BUFFER_LOW("front-buffer-low"),
        FRONT_BUFFER_MARGIN("front-buffer-margin"),
        BUFFER_DECLINING("buffer-declining"),
        BUFFER_EVIDENCE_UNKNOWN("buffer-evidence-unknown"),
        TIME_TO_EMPTY("time-to-empty"),
        THROUGHPUT_DEFICIT("throughput-deficit"),
        SHORT_WINDOW_DEFICIT("short-window-deficit"),
        THROUGHPUT_MARGIN("throughput-margin"),
        SHORT_WINDOW_DECLINE("short-window-decline"),
        THROUGHPUT_EVIDENCE_UNKNOWN("throughput-evidence-unknown"),
        MEDIA_BITRATE_UNKNOWN("media-bitrate-unknown"),
        PREDICTION_ERROR("prediction-error"),
        PATH_LIMITED("path-limited"),
        VPN("vpn"),
        PRELOAD_CONTENTION("preload-contention"),
        SYSTEM_EVIDENCE_UNKNOWN("system-evidence-unknown"),
        EXTERNAL_BUFFER_MARGIN("external-buffer-margin"),
        EVIDENCE_UNKNOWN("evidence-unknown"),
        RECOVERY_STABILIZING("recovery-stabilizing"),
        STABLE_NORMAL("stable-normal"),
        STABLE_FAST("stable-fast"),
        FAST_FALLBACK("fast-fallback"),
        DISRUPTED("disrupted");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private enum Mode {
        PAUSED("paused"),
        DEGRADED("degraded"),
        NORMAL("normal"),
        FAST("fast");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }
}
