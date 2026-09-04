package com.fongmi.android.tv.player.exo;

import android.os.SystemClock;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.DefaultAllocator;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackAutoContextStore;
import com.fongmi.android.tv.player.PlaybackTelemetry;
import com.fongmi.android.tv.player.PlaybackTelemetryCoordinator;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** DefaultLoadControl extension that calculates automatic target bytes at track-selection edges. */
final class AutoTargetLoadControl extends DefaultLoadControl {

    private final DefaultAllocator allocator;
    private final ExoBufferBudget.Budget fallbackBudget;
    private final int configuredTargetBytes;
    private final ExoTargetBufferCoordinator coordinator;
    private final ExoMemoryPressureCoordinator memoryPressureCoordinator;
    private final ExoPreloadTrafficCoordinator preloadTrafficCoordinator;
    private final boolean automaticTargetBytes;
    private final boolean automaticBackBuffer;
    private final ConcurrentHashMap<PlayerId, TargetState> targetStates;
    private final ConcurrentHashMap<PlayerId, ModeState> modeStates;
    private final ConcurrentHashMap<PlayerId, ExoPreloadTrafficCoordinator.Registration>
            media3PreloadTraffic;
    private final Set<PlayerId> preparedPlayers;
    private volatile ExoMemoryPressureCoordinator.Registration memoryPressureRegistration;

    AutoTargetLoadControl(
            ExoLoadControlPolicy.AutomaticConfiguration configuration,
            int backBufferMs,
            int configuredTargetBytes,
            ExoBufferBudget.Budget fallbackBudget) {
        this(
                new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                configuration,
                backBufferMs,
                configuredTargetBytes,
                fallbackBudget,
                ExoTargetBufferCoordinator.process(),
                ExoMemoryPressureCoordinator.process(),
                ExoPreloadTrafficCoordinator.process(),
                true,
                true);
    }

    AutoTargetLoadControl(
            ExoLoadControlPolicy.AutomaticConfiguration configuration,
            int backBufferMs,
            int configuredTargetBytes,
            ExoBufferBudget.Budget fallbackBudget,
            boolean automaticTargetBytes,
            boolean automaticBackBuffer) {
        this(
                new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                configuration,
                backBufferMs,
                configuredTargetBytes,
                fallbackBudget,
                ExoTargetBufferCoordinator.process(),
                ExoMemoryPressureCoordinator.process(),
                ExoPreloadTrafficCoordinator.process(),
                automaticTargetBytes,
                automaticBackBuffer);
    }

    AutoTargetLoadControl(
            DefaultAllocator allocator,
            ExoLoadControlPolicy.AutomaticConfiguration configuration,
            int backBufferMs,
            int configuredTargetBytes,
            ExoBufferBudget.Budget fallbackBudget,
            ExoTargetBufferCoordinator coordinator) {
        this(
                allocator,
                configuration,
                backBufferMs,
                configuredTargetBytes,
                fallbackBudget,
                coordinator,
                ExoMemoryPressureCoordinator.process(),
                ExoPreloadTrafficCoordinator.process(),
                true,
                true);
    }

    AutoTargetLoadControl(
            DefaultAllocator allocator,
            ExoLoadControlPolicy.AutomaticConfiguration configuration,
            int backBufferMs,
            int configuredTargetBytes,
            ExoBufferBudget.Budget fallbackBudget,
            ExoTargetBufferCoordinator coordinator,
            ExoMemoryPressureCoordinator memoryPressureCoordinator) {
        this(
                allocator,
                configuration,
                backBufferMs,
                configuredTargetBytes,
                fallbackBudget,
                coordinator,
                memoryPressureCoordinator,
                ExoPreloadTrafficCoordinator.process(),
                true,
                true);
    }

    AutoTargetLoadControl(
            DefaultAllocator allocator,
            ExoLoadControlPolicy.AutomaticConfiguration configuration,
            int backBufferMs,
            int configuredTargetBytes,
            ExoBufferBudget.Budget fallbackBudget,
            ExoTargetBufferCoordinator coordinator,
            ExoMemoryPressureCoordinator memoryPressureCoordinator,
            ExoPreloadTrafficCoordinator preloadTrafficCoordinator) {
        this(
                allocator,
                configuration,
                backBufferMs,
                configuredTargetBytes,
                fallbackBudget,
                coordinator,
                memoryPressureCoordinator,
                preloadTrafficCoordinator,
                true,
                true);
    }

    AutoTargetLoadControl(
            DefaultAllocator allocator,
            ExoLoadControlPolicy.AutomaticConfiguration configuration,
            int backBufferMs,
            int configuredTargetBytes,
            ExoBufferBudget.Budget fallbackBudget,
            ExoTargetBufferCoordinator coordinator,
            ExoMemoryPressureCoordinator memoryPressureCoordinator,
            ExoPreloadTrafficCoordinator preloadTrafficCoordinator,
            boolean automaticTargetBytes,
            boolean automaticBackBuffer) {
        super(
                allocator,
                configuration.streaming().minBufferMs(),
                configuration.local().minBufferMs(),
                configuration.streaming().maxBufferMs(),
                configuration.local().maxBufferMs(),
                configuration.streamingStartBufferMs(),
                configuration.localStartBufferMs(),
                configuration.streamingRebufferMs(),
                configuration.localRebufferMs(),
                automaticTargetBytes
                        ? C.LENGTH_UNSET : fallbackBudget.effectiveTargetBytes(),
                configuration.streamingPrioritizeTime(),
                configuration.localPrioritizeTime(),
                backBufferMs,
                true,
                Collections.singletonMap(PlayerId.PRELOAD.name, DEFAULT_TARGET_BUFFER_BYTES_FOR_PRELOAD));
        this.allocator = allocator;
        this.configuredTargetBytes = Math.max(0, configuredTargetBytes);
        this.fallbackBudget = fallbackBudget;
        this.coordinator = coordinator;
        this.memoryPressureCoordinator = memoryPressureCoordinator;
        this.preloadTrafficCoordinator = preloadTrafficCoordinator;
        this.automaticTargetBytes = automaticTargetBytes;
        this.automaticBackBuffer = automaticBackBuffer;
        this.targetStates = new ConcurrentHashMap<>();
        this.modeStates = new ConcurrentHashMap<>();
        this.media3PreloadTraffic = new ConcurrentHashMap<>();
        this.preparedPlayers = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void onPrepared(PlayerId playerId) {
        closeMedia3PreloadTraffic(playerId);
        targetStates.remove(playerId);
        modeStates.remove(playerId);
        preparedPlayers.add(playerId);
        ensureMemoryPressureRegistration();
        super.onPrepared(playerId);
    }

    @Override
    public void onTracksSelected(
            LoadControl.Parameters parameters,
            TrackGroupArray trackGroups,
            ExoTrackSelection[] trackSelections) {
        super.onTracksSelected(parameters, trackGroups, trackSelections);
        if (PlayerId.PRELOAD.equals(parameters.playerId)) return;
        long now = SystemClock.elapsedRealtime();
        PlaybackAutoContext context = PlaybackAutoContextStore.process().snapshot();
        TargetState targetState = targetStates.get(parameters.playerId);
        if (targetState == null) return;
        PlaybackAutoContext modeContext = targetState.session().active()
                && targetState.session().equals(context.session())
                ? context : PlaybackAutoContext.empty();
        ExoLoadControlModePolicy.TrackProfile tracks =
                ExoLoadControlModePolicy.TrackProfile.inspect(trackGroups, trackSelections);
        ExoLoadControlModePolicy.Decision mode = resolveMode(
                modeContext,
                tracks,
                targetState.decision(),
                now);
        ModeState previous = modeStates.put(
                parameters.playerId,
                new ModeState(targetState.session(), tracks, mode));
        ExoPlaybackDiagnostics.logLoadControlMode(mode);
        if (targetState.session().active()) {
            publishModeTelemetry(
                    targetState.session(),
                    previous == null ? null : previous.decision(),
                    mode,
                    targetState.decision(),
                    modeContext,
                    now);
        }
        applyAllocatorTarget();
    }

    @Override
    public void onStopped(PlayerId playerId) {
        closeMedia3PreloadTraffic(playerId);
        targetStates.remove(playerId);
        modeStates.remove(playerId);
        preparedPlayers.remove(playerId);
        super.onStopped(playerId);
        applyAllocatorTarget();
        if (preparedPlayers.isEmpty()) closeMemoryPressureRegistration();
    }

    @Override
    public void onReleased(PlayerId playerId) {
        closeMedia3PreloadTraffic(playerId);
        targetStates.remove(playerId);
        modeStates.remove(playerId);
        preparedPlayers.remove(playerId);
        super.onReleased(playerId);
        applyAllocatorTarget();
        if (preparedPlayers.isEmpty()) closeMemoryPressureRegistration();
    }

    @Override
    public boolean shouldContinueLoading(LoadControl.Parameters parameters) {
        boolean delegateLoading = super.shouldContinueLoading(parameters);
        if (PlayerId.PRELOAD.equals(parameters.playerId)) {
            applyAllocatorTarget();
            boolean allowed = delegateLoading && !isPreloadPaused();
            setMedia3PreloadTraffic(parameters.playerId, allowed);
            return allowed;
        }
        refreshTargetForActualFormats(parameters.playerId);
        applyAllocatorTarget();
        ExoMemoryPressurePolicy.Decision memory = currentMemoryDecision(parameters.playerId);
        if (automaticTargetBytes && memory != null && memory.degraded()) {
            Allocator playerAllocator = getAllocator(parameters.playerId);
            if (!ExoLoadControlModePolicy.canAllocate(
                    playerAllocator.getTotalBytesAllocated(),
                    playerAllocator.getIndividualAllocationLength(),
                    memory.effectiveTargetBytes())) {
                return false;
            }
        }
        if (delegateLoading) return true;
        ExoLoadControlModePolicy.Decision mode = currentModeDecision(parameters.playerId);
        if (!mode.mode().controlledTimePriority()) return false;
        if (AutoLoadControl.reachedAdaptiveThreshold(
                parameters.bufferedDurationUs,
                parameters.playbackSpeed,
                parameters.targetLiveOffsetUs,
                ExoLoadControlModePolicy.SINGLE_TRACK_RESCUE_BUFFER_MS)) {
            return false;
        }
        return canContinueControlledRescue(mode);
    }

    boolean canContinueControlledRescue(PlayerId playerId) {
        ExoMemoryPressurePolicy.Decision memory = currentMemoryDecision(playerId);
        return (memory == null || memory.expansionAllowed())
                && canContinueControlledRescue(currentModeDecision(playerId));
    }

    ExoMemoryPressurePolicy.Decision currentMemoryDecision(PlayerId playerId) {
        TargetState targetState = targetStates.get(playerId);
        if (targetState == null || !targetState.session().active()) return null;
        return memoryPressureCoordinator.currentDecision(targetState.session());
    }

    boolean isBackBufferSuppressed(PlayerId playerId) {
        if (!automaticBackBuffer) return false;
        ExoMemoryPressurePolicy.Decision decision = currentMemoryDecision(playerId);
        return decision != null && decision.backBufferSuppressed();
    }

    boolean isPreloadPaused() {
        ExoMemoryPressurePolicy.Decision decision = memoryPressureCoordinator.currentDecision();
        return decision != null && decision.preloadPaused();
    }

    boolean isTargetBufferSizeReached(PlayerId playerId) {
        if (playerId == null || !targetStates.containsKey(playerId)) return false;
        AllocatorTarget target = currentAllocatorTarget();
        return target.targetBytes() > 0
                && allocator.getTotalBytesAllocated() >= target.targetBytes();
    }

    private void setMedia3PreloadTraffic(PlayerId playerId, boolean active) {
        if (!active) {
            closeMedia3PreloadTraffic(playerId);
            return;
        }
        PlaybackAutoContext context = PlaybackAutoContextStore.process().snapshot();
        PlaybackAutoContext.SessionToken session = currentExoSession(context);
        if (!session.active()) {
            closeMedia3PreloadTraffic(playerId);
            return;
        }
        ExoPreloadTrafficCoordinator.Registration existing =
                media3PreloadTraffic.get(playerId);
        if (existing != null && existing.active()
                && session.equals(existing.session())) return;
        closeMedia3PreloadTraffic(playerId);
        ExoPreloadTrafficCoordinator.Registration acquired =
                preloadTrafficCoordinator.acquire(
                        session, ExoPreloadTrafficCoordinator.Source.MEDIA3);
        if (acquired.active()) media3PreloadTraffic.put(playerId, acquired);
    }

    private void closeMedia3PreloadTraffic(PlayerId playerId) {
        ExoPreloadTrafficCoordinator.Registration registration =
                media3PreloadTraffic.remove(playerId);
        if (registration != null) registration.close();
    }

    ExoLoadControlModePolicy.Decision currentModeDecision(PlayerId playerId) {
        ModeState modeState = modeStates.get(playerId);
        TargetState targetState = targetStates.get(playerId);
        if (modeState == null || targetState == null
                || !modeState.session().equals(targetState.session())) {
            return ExoLoadControlModePolicy.Decision.unknown();
        }
        if (!modeState.session().active()) return modeState.decision();
        PlaybackAutoContext context = PlaybackAutoContextStore.process().snapshot();
        if (!modeState.session().equals(context.session())) {
            return ExoLoadControlModePolicy.Decision.unknown();
        }
        return resolveMode(
                context,
                modeState.tracks(),
                targetState.decision(),
                SystemClock.elapsedRealtime());
    }

    @Override
    protected int calculateTargetBufferBytes(
            LoadControl.Parameters parameters,
            ExoTrackSelection[] trackSelections) {
        long now = SystemClock.elapsedRealtime();
        PlaybackAutoContext context = PlaybackAutoContextStore.process().snapshot();
        PlaybackAutoContext.SessionToken session = currentExoSession(context);
        PlaybackAutoContext.DeviceFacts device = session.active()
                ? context.device() : PlaybackAutoContext.DeviceFacts.unknown();
        ObservedMediaBitrateEstimator.Estimate estimate =
                PlaybackAnalyticsListener.getMediaBitrateEstimate();
        ExoTargetBufferPolicy.MediaDemand mediaDemand =
                resolveMediaDemand(trackSelections, estimate);
        ExoLoadControlModePolicy.TrackProfile tracks =
                ExoLoadControlModePolicy.TrackProfile.inspect(null, trackSelections);
        ExoTargetBufferPolicy.UnknownMediaFallback unknownMediaFallback =
                ExoTargetBufferPolicy.unknownMediaFallback(
                        context.resource(),
                        context.path(),
                        tracks.hasVideo(),
                        tracks.adaptiveVideo(),
                        now);
        ExoTargetBufferPolicy.Decision baseline = calculateDecision(
                mediaDemand,
                unknownMediaFallback,
                PlaybackAutoContext.DeviceFacts.unknown(),
                now);
        ExoTargetBufferPolicy.Decision observed = calculateDecision(
                mediaDemand,
                unknownMediaFallback,
                device,
                now);
        TargetState previousState = targetStates.get(parameters.playerId);
        ExoTargetBufferPolicy.Decision previousObserved = previousState != null
                && session.equals(previousState.session())
                ? previousState.observedDecision() : null;
        boolean published = coordinator.publish(session, baseline, now);
        targetStates.put(
                parameters.playerId,
                new TargetState(
                        session,
                        baseline,
                        observed,
                        DemandKey.from(trackSelections)));
        memoryPressureCoordinator.publishBaseline(
                session,
                baseline,
                configuredTargetBytes,
                fallbackBudget,
                device,
                now);
        ExoPlaybackDiagnostics.logTargetDecision(observed);
        if (published) {
            publishTelemetry(session, previousObserved, observed, context, now);
        }
        return automaticTargetBytes
                ? baseline.targetBytes()
                : fallbackBudget.effectiveTargetBytes();
    }

    private void refreshTargetForActualFormats(PlayerId playerId) {
        TargetState previous = targetStates.get(playerId);
        if (previous == null || !previous.session().active()) return;
        PlaybackAutoContext context = PlaybackAutoContextStore.process().snapshot();
        if (!previous.session().equals(currentExoSession(context))) return;
        PlaybackAnalyticsListener.Snapshot analytics = PlaybackAnalyticsListener.getSnapshot();
        DemandKey actualKey = DemandKey.from(
                analytics.videoFormat(), analytics.audioFormat());
        long now = SystemClock.elapsedRealtime();
        ModeState modeState = modeStates.get(playerId);
        boolean adaptiveVideo = modeState != null
                && previous.session().equals(modeState.session())
                && modeState.tracks().adaptiveVideo();
        boolean hasVideo = modeState != null
                && previous.session().equals(modeState.session())
                && modeState.tracks().hasVideo();
        ExoTargetBufferPolicy.UnknownMediaFallback unknownMediaFallback =
                ExoTargetBufferPolicy.unknownMediaFallback(
                        context.resource(),
                        context.path(),
                        hasVideo,
                        adaptiveVideo,
                        now);
        boolean demandChanged = actualKey.known()
                && !actualKey.equals(previous.demandKey());
        boolean fallbackChanged = unknownMediaFallback
                != previous.decision().unknownMediaFallback();
        if (!demandChanged && !fallbackChanged) return;

        ExoTargetBufferPolicy.MediaDemand mediaDemand = actualKey.known()
                ? resolveMediaDemand(
                analytics.videoFormat(),
                analytics.audioFormat(),
                PlaybackAnalyticsListener.getMediaBitrateEstimate())
                : previous.decision().mediaDemand();
        ExoTargetBufferPolicy.Decision baseline = calculateDecision(
                mediaDemand,
                unknownMediaFallback,
                PlaybackAutoContext.DeviceFacts.unknown(),
                now);
        ExoTargetBufferPolicy.Decision observed = calculateDecision(
                mediaDemand,
                unknownMediaFallback,
                context.device(),
                now);
        TargetState next = new TargetState(
                previous.session(),
                baseline,
                observed,
                actualKey.known() ? actualKey : previous.demandKey());
        if (!targetStates.replace(playerId, previous, next)) return;

        boolean published = coordinator.publish(previous.session(), baseline, now);
        memoryPressureCoordinator.publishBaseline(
                previous.session(),
                baseline,
                configuredTargetBytes,
                fallbackBudget,
                context.device(),
                now);
        ExoPlaybackDiagnostics.logTargetDecision(observed);
        if (published) {
            publishTelemetry(
                    previous.session(),
                    previous.observedDecision(),
                    observed,
                    context,
                    now);
        }
        applyAllocatorTarget();
    }

    private ExoLoadControlModePolicy.Decision resolveMode(
            PlaybackAutoContext context,
            ExoLoadControlModePolicy.TrackProfile tracks,
            ExoTargetBufferPolicy.Decision actualTarget,
            long now) {
        PlaybackAutoContext safeContext = context == null ? PlaybackAutoContext.empty() : context;
        ExoTargetBufferPolicy.Decision currentSafety = ExoTargetBufferPolicy.resolve(
                actualTarget.mediaDemand(),
                configuredTargetBytes,
                fallbackBudget,
                actualTarget.unknownMediaFallback(),
                safeContext.device(),
                now);
        return ExoLoadControlModePolicy.resolve(
                safeContext.resource(),
                safeContext.path(),
                tracks,
                actualTarget,
                currentSafety,
                now);
    }

    private boolean heapGuardAllows() {
        Runtime runtime = Runtime.getRuntime();
        return ExoLoadControlModePolicy.heapGuardAllows(
                runtime.maxMemory(),
                runtime.totalMemory(),
                runtime.freeMemory(),
                allocator.getUnusedBytesAllocated());
    }

    private boolean canContinueControlledRescue(ExoLoadControlModePolicy.Decision mode) {
        return automaticTargetBytes
                && mode.mode().controlledTimePriority()
                && heapGuardAllows()
                && ExoLoadControlModePolicy.canAllocate(
                        allocator.getTotalBytesAllocated(),
                        allocator.getIndividualAllocationLength(),
                        mode.hardCapacityBytes());
    }

    private void ensureMemoryPressureRegistration() {
        if (memoryPressureRegistration != null) return;
        synchronized (this) {
            if (memoryPressureRegistration == null) {
                memoryPressureRegistration = memoryPressureCoordinator.addListener(
                        this::onMemoryPressureDecision);
            }
        }
    }

    private void closeMemoryPressureRegistration() {
        ExoMemoryPressureCoordinator.Registration registration;
        synchronized (this) {
            registration = memoryPressureRegistration;
            memoryPressureRegistration = null;
        }
        if (registration != null) registration.close();
    }

    private void onMemoryPressureDecision(ExoMemoryPressureCoordinator.Update update) {
        if (update == null || update.decision() == null) return;
        boolean relevant = false;
        for (TargetState targetState : targetStates.values()) {
            if (update.session().equals(targetState.session())) {
                relevant = true;
                break;
            }
        }
        if (!relevant) return;
        if (automaticTargetBytes) {
            coordinator.publishEffectiveTarget(
                    update.session(),
                    update.decision().effectiveTargetBytes(),
                    update.decision().degraded(),
                    update.publishedAtElapsedMs());
        }
        applyAllocatorTarget();
        ExoPlaybackDiagnostics.logMemoryPressureDecision(
                update.decision(),
                allocator.getTotalBytesAllocated(),
                allocator.getUnusedBytesAllocated());
        publishMemoryPressureTelemetry(update);
    }

    private void applyAllocatorTarget() {
        if (targetStates.isEmpty()) return;
        AllocatorTarget target = currentAllocatorTarget();
        allocator.setTargetBufferSize(target.targetBytes());
        if (target.preloadPaused()) allocator.trim();
    }

    private AllocatorTarget currentAllocatorTarget() {
        int targetBytes = 0;
        boolean preloadPaused = false;
        for (TargetState targetState : targetStates.values()) {
            ExoMemoryPressurePolicy.Decision memory =
                    memoryPressureCoordinator.currentDecision(targetState.session());
            int baseline = automaticTargetBytes
                    ? targetState.decision().targetBytes()
                    : fallbackBudget.effectiveTargetBytes();
            int effective = !automaticTargetBytes || memory == null
                    ? baseline
                    : Math.min(baseline, memory.effectiveTargetBytes());
            targetBytes = saturatedAdd(targetBytes, effective);
            preloadPaused |= memory != null && memory.preloadPaused();
        }
        // Media3 applies PRELOAD through the constructor overwrite, so it never
        // enters targetStates and must be added exactly once here.
        if (preparedPlayers.contains(PlayerId.PRELOAD) && !preloadPaused) {
            targetBytes = saturatedAdd(
                    targetBytes,
                    DEFAULT_TARGET_BUFFER_BYTES_FOR_PRELOAD);
        }
        return new AllocatorTarget(targetBytes, preloadPaused);
    }

    private void publishMemoryPressureTelemetry(
            ExoMemoryPressureCoordinator.Update update) {
        ExoMemoryPressurePolicy.Decision previous = update.previous();
        ExoMemoryPressurePolicy.Decision decision = update.decision();
        if (previous == null && !decision.degraded()) return;
        long now = Math.max(0, update.publishedAtElapsedMs());
        PlaybackAutoContext context = PlaybackAutoContextStore.process().snapshot();
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemoryPressure> pressureFact =
                context.device().memoryPressure();
        boolean changed = previous == null
                || previous.effectiveTargetBytes() != decision.effectiveTargetBytes()
                || previous.mode() != decision.mode();
        PlaybackTelemetryCoordinator.process().publishDecision(
                update.session(),
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.LOAD_CONTROL,
                        changed
                                ? PlaybackTelemetry.DecisionOutcome.APPLIED
                                : PlaybackTelemetry.DecisionOutcome.HELD,
                        previous == null
                                ? "unknown" : Integer.toString(previous.effectiveTargetBytes()),
                        Integer.toString(decision.effectiveTargetBytes()),
                        decision.mode().label(),
                        decision.reason().label(),
                        decision.degraded() ? decision.reason().label() : "none",
                        List.of(
                                PlaybackTelemetry.DecisionInput.text("memory_mode", decision.mode().label(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("baseline_bytes", decision.baselineTargetBytes(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("safe_bytes", decision.safeTargetBytes(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("effective_bytes", decision.effectiveTargetBytes(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                factInput("memory_pressure", decision.observedPressure().label(), pressureFact, now),
                                PlaybackTelemetry.DecisionInput.number("normal_samples", decision.normalSamples(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("cooldown_remaining_ms", decision.cooldownRemainingMs(now), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("allocator_allocated", allocator.getTotalBytesAllocated(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("allocator_unused", allocator.getUnusedBytesAllocated(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("preload_paused", decision.preloadPaused(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("back_suppressed", decision.backBufferSuppressed(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH))),
                now);
    }

    private static int saturatedAdd(int first, int second) {
        if (first >= Integer.MAX_VALUE - second) return Integer.MAX_VALUE;
        return first + Math.max(0, second);
    }

    private static void publishModeTelemetry(
            PlaybackAutoContext.SessionToken session,
            ExoLoadControlModePolicy.Decision previous,
            ExoLoadControlModePolicy.Decision decision,
            ExoTargetBufferPolicy.Decision target,
            PlaybackAutoContext context,
            long now) {
        PlaybackAutoContext.Fact<PlaybackAutoContext.Protocol> protocol =
                context.resource().protocol();
        PlaybackAutoContext.Fact<PlaybackAutoContext.StreamKind> stream =
                context.resource().streamKind();
        PlaybackAutoContext.Fact<PlaybackAutoContext.ManifestFacts> manifest =
                context.resource().manifest();
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot> memorySnapshot =
                context.device().memorySnapshot();
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemoryPressure> memoryPressure =
                context.device().memoryPressure();
        ExoLoadControlModePolicy.TrackProfile tracks = decision.tracks();
        PlaybackTelemetryCoordinator.process().publishDecision(
                session,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.LOAD_CONTROL,
                        PlaybackTelemetry.DecisionOutcome.APPLIED,
                        previous == null ? "unknown" : previous.mode().label(),
                        decision.mode().label(),
                        decision.mode().label(),
                        decision.reason().label(),
                        "none",
                        List.of(
                                factInput("protocol", decision.protocol().label(), protocol, now),
                                factInput("stream_kind", decision.streamKind().label(), stream, now),
                                PlaybackTelemetry.DecisionInput.bool("adaptive_video", tracks.adaptiveVideo(), PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("video_candidates", tracks.selectedVideoCandidates(), PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                manifestVariantInput(decision.manifestVariantCount(), manifest, now),
                                modeBitrateInput(decision, target.mediaDemand()),
                                PlaybackTelemetry.DecisionInput.number("target_bytes", decision.targetBytes(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                decision.targetDurationMs() < 0
                                        ? PlaybackTelemetry.DecisionInput.unknown("target_duration_ms")
                                        : PlaybackTelemetry.DecisionInput.number("target_duration_ms", decision.targetDurationMs(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.MEDIUM),
                                decision.rescueBytes() <= 0
                                        ? PlaybackTelemetry.DecisionInput.unknown("rescue_bytes")
                                        : PlaybackTelemetry.DecisionInput.number("rescue_bytes", decision.rescueBytes(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                memoryInput("hard_capacity_bytes", decision.hardCapacityBytes(), memorySnapshot, now),
                                memoryBooleanInput("hard_protection", decision.hardProtectionAvailable(), memorySnapshot, now),
                                factInput("memory_pressure", decision.memoryPressure().label(), memoryPressure, now))),
                now);
    }

    private static <T> PlaybackTelemetry.DecisionInput factInput(
            String name,
            String value,
            PlaybackAutoContext.Fact<T> fact,
            long now) {
        if (fact == null || !fact.isUsable(now)) return PlaybackTelemetry.DecisionInput.unknown(name);
        return PlaybackTelemetry.DecisionInput.text(name, value, fact.source(), fact.confidence());
    }

    private static PlaybackTelemetry.DecisionInput manifestVariantInput(
            int variants,
            PlaybackAutoContext.Fact<PlaybackAutoContext.ManifestFacts> fact,
            long now) {
        if (variants < 0 || fact == null || !fact.isUsable(now)) {
            return PlaybackTelemetry.DecisionInput.unknown("manifest_variants");
        }
        return PlaybackTelemetry.DecisionInput.number(
                "manifest_variants", variants, fact.source(), fact.confidence());
    }

    private static PlaybackTelemetry.DecisionInput modeBitrateInput(
            ExoLoadControlModePolicy.Decision decision,
            ExoTargetBufferPolicy.MediaDemand media) {
        long bitrate = decision.bitrateBitsPerSecond();
        if (bitrate <= 0) return PlaybackTelemetry.DecisionInput.unknown("control_bitrate_bps");
        if (media.burstReliable() && media.burstBitsPerSecond() == bitrate) {
            return bitrateInput(
                    "control_bitrate_bps", bitrate, media.burstSource(), media.burstConfidence());
        }
        return bitrateInput(
                "control_bitrate_bps", bitrate, media.averageSource(), media.averageConfidence());
    }

    private static PlaybackTelemetry.DecisionInput memoryInput(
            String name,
            long value,
            PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot> fact,
            long now) {
        if (value <= 0 || fact == null || !fact.isUsable(now)) {
            return PlaybackTelemetry.DecisionInput.unknown(name);
        }
        return PlaybackTelemetry.DecisionInput.number(name, value, fact.source(), fact.confidence());
    }

    private static PlaybackTelemetry.DecisionInput memoryBooleanInput(
            String name,
            boolean value,
            PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot> fact,
            long now) {
        if (fact == null || !fact.isUsable(now)) return PlaybackTelemetry.DecisionInput.unknown(name);
        return PlaybackTelemetry.DecisionInput.bool(name, value, fact.source(), fact.confidence());
    }

    ExoTargetBufferPolicy.Decision calculateDecision(
            ExoTrackSelection[] trackSelections,
            ObservedMediaBitrateEstimator.Estimate estimate,
            PlaybackAutoContext.DeviceFacts deviceFacts,
            long elapsedRealtimeMs) {
        return calculateDecision(
                resolveMediaDemand(trackSelections, estimate),
                deviceFacts,
                elapsedRealtimeMs);
    }

    ExoTargetBufferPolicy.Decision calculateBaselineDecision(
            ExoTrackSelection[] trackSelections,
            ObservedMediaBitrateEstimator.Estimate estimate,
            long elapsedRealtimeMs) {
        return calculateDecision(
                resolveMediaDemand(trackSelections, estimate),
                PlaybackAutoContext.DeviceFacts.unknown(),
                elapsedRealtimeMs);
    }

    private ExoTargetBufferPolicy.Decision calculateDecision(
            ExoTargetBufferPolicy.MediaDemand mediaDemand,
            PlaybackAutoContext.DeviceFacts deviceFacts,
            long elapsedRealtimeMs) {
        return calculateDecision(
                mediaDemand,
                ExoTargetBufferPolicy.UnknownMediaFallback.MINIMUM,
                deviceFacts,
                elapsedRealtimeMs);
    }

    private ExoTargetBufferPolicy.Decision calculateDecision(
            ExoTargetBufferPolicy.MediaDemand mediaDemand,
            ExoTargetBufferPolicy.UnknownMediaFallback unknownMediaFallback,
            PlaybackAutoContext.DeviceFacts deviceFacts,
            long elapsedRealtimeMs) {
        return ExoTargetBufferPolicy.resolve(
                mediaDemand,
                configuredTargetBytes,
                fallbackBudget,
                unknownMediaFallback,
                deviceFacts,
                elapsedRealtimeMs);
    }

    static ExoTargetBufferPolicy.MediaDemand resolveMediaDemand(
            ExoTrackSelection[] trackSelections,
            ObservedMediaBitrateEstimator.Estimate estimate) {
        ExoTargetBufferPolicy.MediaDemand selected = selectedTrackDemand(trackSelections);
        return resolveMediaDemand(selected, estimate);
    }

    static ExoTargetBufferPolicy.MediaDemand resolveMediaDemand(
            Format video,
            Format audio,
            ObservedMediaBitrateEstimator.Estimate estimate) {
        return resolveMediaDemand(selectedFormatDemand(video, audio), estimate);
    }

    private static ExoTargetBufferPolicy.MediaDemand resolveMediaDemand(
            ExoTargetBufferPolicy.MediaDemand selected,
            ObservedMediaBitrateEstimator.Estimate estimate) {
        long average = selected.averageBitsPerSecond();
        ExoTargetBufferPolicy.DemandSource averageSource = selected.averageSource();
        PlaybackAutoContext.Confidence averageConfidence = selected.averageConfidence();
        long burst = selected.burstBitsPerSecond();
        ExoTargetBufferPolicy.DemandSource burstSource = selected.burstSource();
        PlaybackAutoContext.Confidence burstConfidence = selected.burstConfidence();

        if (estimate != null && estimate.averageReliable()) {
            average = estimate.averageBitrateBitsPerSecond();
            averageSource = demandSource(estimate.averageSource());
            averageConfidence = confidence(estimate.averageConfidence());
        }
        if (estimate != null && estimate.burstReliable()) {
            burst = estimate.burstBitrateBitsPerSecond();
            burstSource = demandSource(estimate.burstSource());
            burstConfidence = confidence(estimate.burstConfidence());
        }
        if (average > 0 && (burst <= 0 || burst < average)) {
            burst = average;
            burstSource = averageSource;
            burstConfidence = averageConfidence;
        }
        return new ExoTargetBufferPolicy.MediaDemand(
                average,
                averageSource,
                averageConfidence,
                burst,
                burstSource,
                burstConfidence);
    }

    private static ExoTargetBufferPolicy.MediaDemand selectedTrackDemand(
            ExoTrackSelection[] trackSelections) {
        long average = 0;
        long burst = 0;
        if (trackSelections != null) {
            for (ExoTrackSelection selection : trackSelections) {
                if (selection == null) continue;
                Format format = selection.getSelectedFormat();
                if (format == null) continue;
                long formatAverage = averageBitrate(format);
                long formatBurst = peakBitrate(format, formatAverage);
                average = safeAdd(average, formatAverage);
                burst = safeAdd(burst, formatBurst);
            }
        }
        if (average <= 0 && burst <= 0) return ExoTargetBufferPolicy.MediaDemand.unknown();
        if (average <= 0) average = burst;
        if (burst < average) burst = average;
        return new ExoTargetBufferPolicy.MediaDemand(
                average,
                ExoTargetBufferPolicy.DemandSource.SELECTED_TRACK,
                PlaybackAutoContext.Confidence.MEDIUM,
                burst,
                ExoTargetBufferPolicy.DemandSource.SELECTED_TRACK,
                PlaybackAutoContext.Confidence.MEDIUM);
    }

    private static ExoTargetBufferPolicy.MediaDemand selectedFormatDemand(
            Format... formats) {
        long average = 0;
        long burst = 0;
        if (formats != null) {
            for (Format format : formats) {
                if (format == null) continue;
                long formatAverage = averageBitrate(format);
                long formatBurst = peakBitrate(format, formatAverage);
                average = safeAdd(average, formatAverage);
                burst = safeAdd(burst, formatBurst);
            }
        }
        if (average <= 0 && burst <= 0) {
            return ExoTargetBufferPolicy.MediaDemand.unknown();
        }
        if (average <= 0) average = burst;
        if (burst < average) burst = average;
        return new ExoTargetBufferPolicy.MediaDemand(
                average,
                ExoTargetBufferPolicy.DemandSource.SELECTED_TRACK,
                PlaybackAutoContext.Confidence.MEDIUM,
                burst,
                ExoTargetBufferPolicy.DemandSource.SELECTED_TRACK,
                PlaybackAutoContext.Confidence.MEDIUM);
    }

    private static long averageBitrate(Format format) {
        if (format.averageBitrate > 0) return format.averageBitrate;
        if (format.bitrate > 0) return format.bitrate;
        return Math.max(0, format.peakBitrate);
    }

    private static long peakBitrate(Format format, long averageBitrate) {
        return format.peakBitrate > 0 ? Math.max(format.peakBitrate, averageBitrate) : averageBitrate;
    }

    private static PlaybackAutoContext.SessionToken currentExoSession(PlaybackAutoContext context) {
        if (context == null || !context.active()) return PlaybackAutoContext.SessionToken.none();
        if (!context.session().traceId().equals(PlaybackAnalyticsListener.getPlaybackTraceId())) {
            return PlaybackAutoContext.SessionToken.none();
        }
        if (context.kernel().hasValue() && context.kernel().value() != PlaybackAutoContext.Kernel.EXO) {
            return PlaybackAutoContext.SessionToken.none();
        }
        return context.session();
    }

    private static void publishTelemetry(
            PlaybackAutoContext.SessionToken session,
            ExoTargetBufferPolicy.Decision previous,
            ExoTargetBufferPolicy.Decision decision,
            PlaybackAutoContext context,
            long now) {
        ExoTargetBufferPolicy.MediaDemand media = decision.mediaDemand();
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot> memorySnapshot =
                context.device().memorySnapshot();
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemoryPressure> memoryPressure =
                context.device().memoryPressure();
        PlaybackTelemetryCoordinator.process().publishDecision(
                session,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.LOAD_CONTROL,
                        PlaybackTelemetry.DecisionOutcome.APPLIED,
                        previous == null ? "unknown" : Integer.toString(previous.targetBytes()),
                        Integer.toString(decision.targetBytes()),
                        Integer.toString(decision.targetBytes()),
                        decision.limitingFactor().label(),
                        "none",
                        List.of(
                                bitrateInput("average_bps", media.averageBitsPerSecond(), media.averageSource(), media.averageConfidence()),
                                bitrateInput("burst_bps", media.burstBitsPerSecond(), media.burstSource(), media.burstConfidence()),
                                computedInput("average_need_bytes", decision.averageDemandBytes(), media.averageSource(), media.averageConfidence()),
                                computedInput("burst_need_bytes", decision.burstDemandBytes(), media.burstSource(), media.burstConfidence()),
                                PlaybackTelemetry.DecisionInput.number("media_tier_bytes", decision.mediaTierBytes(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.text("unknown_media_fallback", decision.unknownMediaFallback().label(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("heap_budget_bytes", decision.heapBudgetBytes(), PlaybackAutoContext.ValueSource.SYSTEM_API, PlaybackAutoContext.Confidence.MEDIUM),
                                memoryBudgetInput("java_headroom_budget_bytes", decision.javaHeadroomBudgetBytes(), memorySnapshot),
                                memoryBudgetInput("system_budget_bytes", decision.systemBudgetBytes(), memorySnapshot),
                                deviceBudgetInput(decision, memorySnapshot, memoryPressure),
                                decision.configuredCapBytes() > 0
                                        ? PlaybackTelemetry.DecisionInput.number("configured_cap_bytes", decision.configuredCapBytes(), PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH)
                                        : PlaybackTelemetry.DecisionInput.text("configured_cap_bytes", "auto", PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH),
                                pressureInput(decision, memoryPressure))),
                now);
    }

    private static PlaybackTelemetry.DecisionInput bitrateInput(
            String name,
            long value,
            ExoTargetBufferPolicy.DemandSource source,
            PlaybackAutoContext.Confidence confidence) {
        if (value <= 0 || source == ExoTargetBufferPolicy.DemandSource.UNKNOWN
                || confidence == PlaybackAutoContext.Confidence.UNKNOWN) {
            return PlaybackTelemetry.DecisionInput.unknown(name);
        }
        return PlaybackTelemetry.DecisionInput.number(name, value, source.telemetrySource(), confidence);
    }

    private static PlaybackTelemetry.DecisionInput computedInput(
            String name,
            long value,
            ExoTargetBufferPolicy.DemandSource source,
            PlaybackAutoContext.Confidence confidence) {
        return value <= 0 ? PlaybackTelemetry.DecisionInput.unknown(name)
                : PlaybackTelemetry.DecisionInput.number(name, value, source.telemetrySource(), confidence);
    }

    private static PlaybackTelemetry.DecisionInput memoryBudgetInput(
            String name,
            long value,
            PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot> fact) {
        if (value < 0 || fact == null || !fact.hasValue()) return PlaybackTelemetry.DecisionInput.unknown(name);
        return PlaybackTelemetry.DecisionInput.number(name, value, fact.source(), fact.confidence());
    }

    private static PlaybackTelemetry.DecisionInput pressureInput(
            ExoTargetBufferPolicy.Decision decision,
            PlaybackAutoContext.Fact<PlaybackAutoContext.MemoryPressure> fact) {
        if (!decision.memoryPressureUsable() || fact == null || !fact.hasValue()) {
            return PlaybackTelemetry.DecisionInput.unknown("memory_pressure");
        }
        return PlaybackTelemetry.DecisionInput.text(
                "memory_pressure",
                decision.memoryPressure().label(),
                fact.source(),
                fact.confidence());
    }

    private static PlaybackTelemetry.DecisionInput deviceBudgetInput(
            ExoTargetBufferPolicy.Decision decision,
            PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot> snapshot,
            PlaybackAutoContext.Fact<PlaybackAutoContext.MemoryPressure> pressure) {
        if (decision.memorySnapshotUsable() && snapshot != null && snapshot.hasValue()) {
            return PlaybackTelemetry.DecisionInput.number(
                    "device_budget_bytes", decision.deviceBudgetBytes(), snapshot.source(), snapshot.confidence());
        }
        if (decision.memoryPressureUsable() && pressure != null && pressure.hasValue()) {
            return PlaybackTelemetry.DecisionInput.number(
                    "device_budget_bytes", decision.deviceBudgetBytes(), pressure.source(), pressure.confidence());
        }
        return PlaybackTelemetry.DecisionInput.number(
                "device_budget_bytes",
                decision.deviceBudgetBytes(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.MEDIUM);
    }

    private static ExoTargetBufferPolicy.DemandSource demandSource(
            ObservedMediaBitrateEstimator.Source source) {
        if (source == null) return ExoTargetBufferPolicy.DemandSource.UNKNOWN;
        return switch (source) {
            case CONTENT_LENGTH -> ExoTargetBufferPolicy.DemandSource.CONTENT_LENGTH;
            case FORMAT -> ExoTargetBufferPolicy.DemandSource.FORMAT;
            case OBSERVED_LOAD -> ExoTargetBufferPolicy.DemandSource.OBSERVED_LOAD;
            case BYTE_SLOPE -> ExoTargetBufferPolicy.DemandSource.BYTE_SLOPE;
            case OBSERVED -> ExoTargetBufferPolicy.DemandSource.OBSERVED;
            case HYBRID -> ExoTargetBufferPolicy.DemandSource.HYBRID;
            case UNKNOWN -> ExoTargetBufferPolicy.DemandSource.UNKNOWN;
        };
    }

    private static PlaybackAutoContext.Confidence confidence(
            ObservedMediaBitrateEstimator.Confidence confidence) {
        if (confidence == null) return PlaybackAutoContext.Confidence.UNKNOWN;
        return switch (confidence) {
            case HIGH -> PlaybackAutoContext.Confidence.HIGH;
            case MEDIUM -> PlaybackAutoContext.Confidence.MEDIUM;
            case LOW -> PlaybackAutoContext.Confidence.LOW;
            case UNKNOWN -> PlaybackAutoContext.Confidence.UNKNOWN;
        };
    }

    private static long safeAdd(long first, long second) {
        if (first <= 0) return Math.max(0, second);
        if (second <= 0) return first;
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private record TargetState(
            PlaybackAutoContext.SessionToken session,
            ExoTargetBufferPolicy.Decision decision,
            ExoTargetBufferPolicy.Decision observedDecision,
            DemandKey demandKey) {

        private TargetState {
            session = session == null ? PlaybackAutoContext.SessionToken.none() : session;
            observedDecision = observedDecision == null ? decision : observedDecision;
            demandKey = demandKey == null ? DemandKey.unknown() : demandKey;
        }
    }

    private record DemandKey(long averageBitsPerSecond, long burstBitsPerSecond) {

        private static DemandKey from(ExoTrackSelection[] selections) {
            ExoTargetBufferPolicy.MediaDemand demand = selectedTrackDemand(selections);
            return new DemandKey(
                    demand.averageBitsPerSecond(), demand.burstBitsPerSecond());
        }

        private static DemandKey from(Format video, Format audio) {
            ExoTargetBufferPolicy.MediaDemand demand = selectedFormatDemand(video, audio);
            return new DemandKey(
                    demand.averageBitsPerSecond(), demand.burstBitsPerSecond());
        }

        private static DemandKey unknown() {
            return new DemandKey(0, 0);
        }

        private boolean known() {
            return averageBitsPerSecond > 0 || burstBitsPerSecond > 0;
        }
    }

    private record ModeState(
            PlaybackAutoContext.SessionToken session,
            ExoLoadControlModePolicy.TrackProfile tracks,
            ExoLoadControlModePolicy.Decision decision) {

        private ModeState {
            session = session == null ? PlaybackAutoContext.SessionToken.none() : session;
            tracks = tracks == null ? ExoLoadControlModePolicy.TrackProfile.unknown() : tracks;
            decision = decision == null ? ExoLoadControlModePolicy.Decision.unknown() : decision;
        }
    }

    private record AllocatorTarget(int targetBytes, boolean preloadPaused) {

        private AllocatorTarget {
            targetBytes = Math.max(0, targetBytes);
        }
    }
}
