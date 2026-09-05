package com.fongmi.android.tv.player.ijk;

import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackErrorClassifier;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class IjkRuntimeProfileControllerTest {

    private static final PlaybackAutoContext.SessionToken SESSION_A =
            new PlaybackAutoContext.SessionToken("p-ijka-1", 1);
    private static final PlaybackAutoContext.SessionToken SESSION_B =
            new PlaybackAutoContext.SessionToken("p-ijkb-2", 2);
    private static final long ELAPSED = 10_000L;
    private static final long EPOCH = 1_800_000_000_000L;

    @Test
    public void standaloneFirstFrameWatchdogTriggersForFixedProfileAttempt() {
        IjkFirstFrameWatchdog watchdog = new IjkFirstFrameWatchdog();
        watchdog.beginSession(SESSION_A);
        watchdog.beginAttempt(SESSION_A, true);
        watchdog.onPrepared(SESSION_A, ELAPSED);

        // 累积活跃时间刚好达到 TIMEOUT_ACTIVE_MS 即触发，不额外多等一个采样周期。
        assertFalse(watchdog.evaluate(
                SESSION_A,
                new IjkFirstFrameWatchdog.RuntimeSample(true, true, false),
                ELAPSED + 5_000).timedOut());
        assertTrue(watchdog.evaluate(
                SESSION_A,
                new IjkFirstFrameWatchdog.RuntimeSample(true, true, false),
                ELAPSED + 10_000).timedOut());
        assertFalse(watchdog.evaluate(
                SESSION_A,
                new IjkFirstFrameWatchdog.RuntimeSample(true, true, false),
                ELAPSED + 20_000).timedOut());
    }

    @Test
    public void standaloneFirstFrameWatchdogCountsOnlyObservedActiveTime() {
        IjkFirstFrameWatchdog watchdog = new IjkFirstFrameWatchdog();
        watchdog.beginSession(SESSION_A);
        watchdog.beginAttempt(SESSION_A, true);
        watchdog.onPrepared(SESSION_A, ELAPSED);

        // 非活跃采样跨越的 10 秒不计入，随后两个活跃采样周期才累计到阈值。
        assertFalse(watchdog.evaluate(
                SESSION_A,
                new IjkFirstFrameWatchdog.RuntimeSample(false, true, false),
                ELAPSED + 20_000).timedOut());
        assertFalse(watchdog.evaluate(
                SESSION_A,
                new IjkFirstFrameWatchdog.RuntimeSample(true, true, false),
                ELAPSED + 25_000).timedOut());
        assertTrue(watchdog.evaluate(
                SESSION_A,
                new IjkFirstFrameWatchdog.RuntimeSample(true, true, false),
                ELAPSED + 30_000).timedOut());
    }

    @Test
    public void standaloneFirstFrameWatchdogIgnoresDisabledOrRenderedAttempts() {
        IjkFirstFrameWatchdog watchdog = new IjkFirstFrameWatchdog();
        watchdog.beginSession(SESSION_A);
        watchdog.beginAttempt(SESSION_A, false);
        watchdog.onPrepared(SESSION_A, ELAPSED);

        assertFalse(watchdog.evaluate(
                SESSION_A,
                new IjkFirstFrameWatchdog.RuntimeSample(true, true, false),
                ELAPSED + 20_000).timedOut());

        watchdog.beginAttempt(SESSION_A, true);
        watchdog.onPrepared(SESSION_A, ELAPSED + 30_000);
        watchdog.onFirstFrame(SESSION_A);
        assertFalse(watchdog.evaluate(
                SESSION_A,
                new IjkFirstFrameWatchdog.RuntimeSample(true, true, false),
                ELAPSED + 50_000).timedOut());

        watchdog.beginAttempt(SESSION_A, true);
        watchdog.onPrepared(SESSION_A, ELAPSED + 60_000);
        assertFalse(watchdog.evaluate(
                SESSION_A,
                new IjkFirstFrameWatchdog.RuntimeSample(true, true, true),
                ELAPSED + 80_000).timedOut());
        assertFalse(watchdog.evaluate(
                SESSION_A,
                new IjkFirstFrameWatchdog.RuntimeSample(true, false, false),
                ELAPSED + 100_000).timedOut());
    }

    @Test
    public void activationRequiresCurrentAutomaticSession() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = new IjkRuntimeProfileController(
                profiles);
        controller.beginSession(SESSION_A);

        assertFalse(controller.activate(
                SESSION_B,
                IjkRuntimeProfilePolicy.Path.IJK_HARD,
                facts(SESSION_B, true, true,
                        PlaybackAutoContext.DecodeMode.HARDWARE),
                ijkSample(true, 0, 30f, 30f, 0),
                ELAPSED));
        assertFalse(controller.activate(
                SESSION_A,
                IjkRuntimeProfilePolicy.Path.IJK_HARD,
                facts(SESSION_A, false, true,
                        PlaybackAutoContext.DecodeMode.HARDWARE),
                ijkSample(true, 0, 30f, 30f, 0),
                ELAPSED));
        assertFalse(controller.snapshot().managed());
    }

    @Test
    public void firstFrameIsRecordedOnceAndStartsIndependentWindow() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);

        IjkRuntimeProfileController.Observation first = controller.onFirstFrame(
                SESSION_A,
                facts(true),
                ijkSample(true, 1, 30f, 30f, 0),
                ELAPSED + 1_000,
                EPOCH);
        IjkRuntimeProfileController.Observation duplicate =
                controller.onFirstFrame(
                        SESSION_A,
                        facts(true),
                        ijkSample(true, 1, 30f, 30f, 0),
                        ELAPSED + 2_000,
                        EPOCH + 1);

        assertEquals(IjkRuntimeProfileController.ObservationAction.FIRST_FRAME,
                first.action());
        assertEquals(IjkRuntimeProfileController.Reason.ALREADY_RECORDED,
                duplicate.reason());
        assertEquals(1, profiles.firstFramePaths.size());
    }

    @Test
    public void preparedIjkVideoWithoutFirstFrameFallsBackAfterActiveDeadline() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);
        controller.onPrepared(SESSION_A, ELAPSED);
        controller.onPrepared(SESSION_A, ELAPSED + 10_000);

        IjkRuntimeProfileController.Decision beforeDeadline =
                controller.handleFirstFrameTimeout(
                        SESSION_A,
                        facts(true),
                        ijkSample(true, 0, 30f, 0f, 0),
                        ELAPSED + 5_000,
                        EPOCH);
        IjkRuntimeProfileController.Decision afterDeadline =
                controller.handleFirstFrameTimeout(
                        SESSION_A,
                        facts(true),
                        ijkSample(true, 0, 30f, 0f, 0),
                        ELAPSED + 15_000,
                        EPOCH + 10_000);

        assertTrue(beforeDeadline == null);
        assertTrue(afterDeadline.requestsSwitch());
        assertEquals(IjkRuntimeProfilePolicy.Path.IJK_SOFT,
                afterDeadline.targetPath());
        assertEquals(IjkRuntimeProfilePolicy.FailureKind.DECODER,
                afterDeadline.assessment().kind());
        assertTrue(afterDeadline.failurePersisted());
    }

    @Test
    public void newPlaybackAttemptRestartsFirstFrameDeadline() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);
        controller.onPrepared(SESSION_A, ELAPSED);

        assertTrue(controller.handleFirstFrameTimeout(
                SESSION_A,
                facts(true),
                ijkSample(true, 0, 30f, 0f, 0),
                ELAPSED + 5_000,
                EPOCH) == null);

        // 新一次起播重置累积，此前的 5 秒不再计入。
        controller.onPlaybackAttemptStarted(
                SESSION_A, ijkSample(true, 0, 30f, 0f, 0));
        controller.onPrepared(SESSION_A, ELAPSED + 10_000);

        assertTrue(controller.handleFirstFrameTimeout(
                SESSION_A,
                facts(true),
                ijkSample(true, 0, 30f, 0f, 0),
                ELAPSED + 15_000,
                EPOCH + 10_000) == null);
        assertTrue(controller.handleFirstFrameTimeout(
                SESSION_A,
                facts(true),
                ijkSample(true, 0, 30f, 0f, 0),
                ELAPSED + 20_000,
                EPOCH + 15_000).requestsSwitch());
    }

    @Test
    public void pausedTimeDoesNotSatisfyFirstFrameDeadline() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);
        controller.onPrepared(SESSION_A, ELAPSED);

        // 首次采样非活跃，跨越的 20 秒不计入；之后两个活跃周期才达阈值。
        assertTrue(controller.handleFirstFrameTimeout(
                SESSION_A,
                facts(true),
                ijkSample(false, 0, 30f, 0f, 0),
                ELAPSED + 20_000,
                EPOCH) == null);
        assertTrue(controller.handleFirstFrameTimeout(
                SESSION_A,
                facts(true),
                ijkSample(true, 0, 30f, 0f, 0),
                ELAPSED + 25_000,
                EPOCH + 5_000) == null);
        assertTrue(controller.handleFirstFrameTimeout(
                SESSION_A,
                facts(true),
                ijkSample(true, 0, 30f, 0f, 0),
                ELAPSED + 30_000,
                EPOCH + 10_000).requestsSwitch());
    }

    @Test
    public void firstFrameTimeoutIgnoresExistingOutputFrames() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);
        controller.onPrepared(SESSION_A, ELAPSED);

        assertTrue(controller.handleFirstFrameTimeout(
                SESSION_A,
                facts(true),
                ijkSample(true, 0, 30f, 30f, 0),
                ELAPSED + 5_000,
                EPOCH) == null);
        assertTrue(controller.handleFirstFrameTimeout(
                SESSION_A,
                facts(true),
                ijkSample(true, 0, 30f, 30f, 0),
                ELAPSED + 15_000,
                EPOCH + 10_000) == null);
    }

    @Test
    public void firstFrameTimeoutIgnoresMediaWithoutVideoEvidence() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);
        IjkRuntimeProfileController.Facts audioOnly =
                IjkRuntimeProfileController.Facts.forTest(
                        SESSION_A,
                        null,
                        PlaybackAutoContext.DecodeMode.HARDWARE,
                        true,
                        true,
                        0f);
        controller.onPrepared(SESSION_A, ELAPSED);

        assertTrue(controller.handleFirstFrameTimeout(
                SESSION_A,
                audioOnly,
                ijkSample(true, 0, 0f, 0f, 0),
                ELAPSED + 5_000,
                EPOCH) == null);
        assertTrue(controller.handleFirstFrameTimeout(
                SESSION_A,
                audioOnly,
                ijkSample(true, 0, 0f, 0f, 0),
                ELAPSED + 20_000,
                EPOCH + 15_000) == null);
        assertFalse(controller.snapshot().attemptFailureRecorded());
    }

    @Test
    public void networkFailureDoesNotSwitchOrPersistPathFailure() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);

        IjkRuntimeProfileController.Decision decision = controller.handleFailure(
                SESSION_A,
                facts(true),
                ijkSample(false, 0, 0f, 0f, 0),
                failure(PlaybackErrorClassifier.Stage.NETWORK_IO,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        0,
                        false),
                ELAPSED + 1,
                EPOCH);

        assertFalse(decision.requestsSwitch());
        assertEquals(IjkRuntimeProfilePolicy.FailureKind.NETWORK,
                decision.assessment().kind());
        assertFalse(decision.failurePersisted());
        assertEquals(0, profiles.failurePaths.size());
    }

    @Test
    public void unknownStartupErrorFallsBackWithoutPersistentExclusion() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, false);

        IjkRuntimeProfileController.Decision decision = controller.handleFailure(
                SESSION_A,
                facts(false),
                IjkRuntimeProfileController.RuntimeSample.unknown(),
                failure(PlaybackErrorClassifier.Stage.UNKNOWN,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                        0,
                        false),
                ELAPSED + 1,
                EPOCH);

        assertTrue(decision.requestsSwitch());
        assertEquals(IjkRuntimeProfilePolicy.Path.MPV, decision.targetPath());
        assertEquals(IjkRuntimeProfilePolicy.FailureKind.STARTUP,
                decision.assessment().kind());
        assertFalse(decision.failurePersisted());
        assertEquals(0, profiles.failurePaths.size());
    }

    @Test
    public void explicitDecoderFailureUsesSafeSoftwarePathFirst() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);

        IjkRuntimeProfileController.Decision decision = decoderFailure(
                controller, true, EPOCH);

        assertTrue(decision.requestsSwitch());
        assertEquals(IjkRuntimeProfilePolicy.Path.IJK_HARD,
                decision.fromPath());
        assertEquals(IjkRuntimeProfilePolicy.Path.IJK_SOFT,
                decision.targetPath());
        assertTrue(decision.failurePersisted());
        assertEquals(List.of(IjkRuntimeProfilePolicy.Path.IJK_HARD),
                profiles.failurePaths);
    }

    @Test
    public void fallbackFailureIsRecordedAndTwoSwitchBudgetIsEnforced() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);

        IjkRuntimeProfileController.Decision first = decoderFailure(
                controller, true, EPOCH);
        IjkRuntimeProfileController.Decision second = decoderFailure(
                controller, true, EPOCH + 1);
        IjkRuntimeProfileController.Decision third = decoderFailure(
                controller, true, EPOCH + 2);

        assertEquals(IjkRuntimeProfilePolicy.Path.IJK_SOFT,
                first.targetPath());
        assertEquals(IjkRuntimeProfilePolicy.Path.MPV, second.targetPath());
        assertFalse(third.requestsSwitch());
        assertEquals(IjkRuntimeProfileController.Reason.FALLBACK_LIMIT,
                third.reason());
        assertEquals(2, third.fallbackCount());
        assertEquals(List.of(
                        IjkRuntimeProfileStore.FallbackResult.FAILURE,
                        IjkRuntimeProfileStore.FallbackResult.FAILURE),
                profiles.fallbackResults);
    }

    @Test
    public void persistentSoftExclusionSkipsDirectlyToMpv() {
        RecordingProfiles profiles = new RecordingProfiles();
        profiles.exclude(profileKey(profiles),
                IjkRuntimeProfilePolicy.Path.IJK_SOFT);
        IjkRuntimeProfileController controller = controller(profiles, true);

        IjkRuntimeProfileController.Decision decision = decoderFailure(
                controller, true, EPOCH + 10);

        assertEquals(IjkRuntimeProfilePolicy.Path.MPV, decision.targetPath());
        assertTrue(profiles.persistentExclusionsLogged.contains(
                IjkRuntimeProfilePolicy.Path.IJK_SOFT));
    }

    @Test
    public void verifiedFallbackPathHasPriorityOverDefaultOrder() {
        RecordingProfiles profiles = new RecordingProfiles();
        profiles.verify(profileKey(profiles), IjkRuntimeProfilePolicy.Path.EXO);
        IjkRuntimeProfileController controller = controller(profiles, true);

        IjkRuntimeProfileController.Decision decision = decoderFailure(
                controller, true, EPOCH + 10);

        assertEquals(IjkRuntimeProfilePolicy.Path.EXO, decision.targetPath());
        assertEquals(IjkRuntimeProfileController.Reason.VERIFIED_FALLBACK,
                decision.reason());
    }

    @Test
    public void stableFallbackRecordsTargetAndSourceOutcomeAfterThirtySeconds() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, false);
        IjkRuntimeProfileController.Decision decision = controller.handleFailure(
                SESSION_A,
                facts(false),
                IjkRuntimeProfileController.RuntimeSample.unknown(),
                failure(PlaybackErrorClassifier.Stage.UNKNOWN,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                        0,
                        false),
                ELAPSED,
                EPOCH);
        assertEquals(IjkRuntimeProfilePolicy.Path.MPV, decision.targetPath());
        controller.onPrepared(SESSION_A);
        controller.onFirstFrame(
                SESSION_A,
                facts(false),
                externalSample(true, 1, 30f, 10),
                ELAPSED + 1_000,
                EPOCH + 1);

        IjkRuntimeProfileController.Observation early = controller.observe(
                SESSION_A,
                facts(false),
                externalSample(true, 1, 30f, 11),
                ELAPSED + 30_999,
                EPOCH + 2);
        IjkRuntimeProfileController.Observation stable = controller.observe(
                SESSION_A,
                facts(false),
                externalSample(true, 1, 30f, 11),
                ELAPSED + 31_000,
                EPOCH + 3);

        assertEquals(IjkRuntimeProfileController.Reason.CONFIRMING,
                early.reason());
        assertEquals(IjkRuntimeProfileController.ObservationAction.STABLE,
                stable.action());
        assertTrue(stable.fallbackSucceeded());
        assertEquals(List.of(IjkRuntimeProfilePolicy.Path.MPV),
                profiles.stablePaths);
        assertEquals(List.of(IjkRuntimeProfilePolicy.Path.IJK_HARD),
                profiles.fallbackSourcePaths);
        assertEquals(List.of(IjkRuntimeProfileStore.FallbackResult.SUCCESS),
                profiles.fallbackResults);
    }

    @Test
    public void fallbackStartupBufferingBeforeNewFirstFrameIsNotRebuffer() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, false);
        controller.handleFailure(
                SESSION_A,
                facts(false),
                IjkRuntimeProfileController.RuntimeSample.unknown(),
                failure(PlaybackErrorClassifier.Stage.UNKNOWN,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                        0,
                        false),
                ELAPSED,
                EPOCH);
        controller.onFirstFrame(
                SESSION_A,
                facts(false),
                externalSample(true, 4, 30f, 20),
                ELAPSED + 1_000,
                EPOCH + 1);

        IjkRuntimeProfileController.Observation stable = controller.observe(
                SESSION_A,
                facts(false),
                externalSample(true, 4, 30f, 20),
                ELAPSED + 31_000,
                EPOCH + 2);

        assertEquals(IjkRuntimeProfileController.ObservationAction.STABLE,
                stable.action());
        assertEquals(0, stable.rebufferCount());
    }

    @Test
    public void rebufferAfterNewFirstFramePreventsStableSuccess() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);
        controller.onFirstFrame(
                SESSION_A,
                facts(true),
                ijkSample(true, 1, 30f, 30f, 0),
                ELAPSED,
                EPOCH);

        IjkRuntimeProfileController.Observation observation = controller.observe(
                SESSION_A,
                facts(true),
                ijkSample(true, 2, 30f, 30f, 0),
                ELAPSED + 30_000,
                EPOCH + 1);

        assertEquals(IjkRuntimeProfileController.ObservationAction.OBSERVATION,
                observation.action());
        assertEquals(IjkRuntimeProfilePolicy.HealthReason.REBUFFERED,
                observation.health().reason());
        assertEquals(0, profiles.stablePaths.size());
        assertEquals(1, profiles.observationPaths.size());
    }

    @Test
    public void failureAfterObservationPersistsOnlyNewRebuffers() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);
        controller.onFirstFrame(
                SESSION_A,
                facts(true),
                ijkSample(true, 0, 30f, 30f, 0),
                ELAPSED,
                EPOCH);

        controller.observe(
                SESSION_A,
                facts(true),
                ijkSample(true, 1, 30f, 30f, 0),
                ELAPSED + 30_000,
                EPOCH + 1);
        controller.handleFailure(
                SESSION_A,
                facts(true),
                ijkSample(false, 2, 0f, 0f, 0),
                failure(PlaybackErrorClassifier.Stage.DECODER,
                        PlaybackException.ERROR_CODE_DECODING_FAILED,
                        0,
                        true),
                ELAPSED + 31_000,
                EPOCH + 2);

        IjkRuntimeProfileStore.Entry entry = profiles.store.lookup(
                profileKey(profiles),
                IjkRuntimeProfilePolicy.Path.IJK_HARD,
                EPOCH + 2).entry();
        assertEquals(2, entry.rebufferCount());
    }

    @Test
    public void pausedTimeDoesNotSatisfyStableWindow() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);
        controller.onFirstFrame(
                SESSION_A,
                facts(true),
                ijkSample(true, 0, 30f, 30f, 0),
                ELAPSED,
                EPOCH);

        controller.observe(
                SESSION_A,
                facts(true),
                ijkSample(false, 0, 30f, 30f, 0),
                ELAPSED + 1_000,
                EPOCH + 1);
        IjkRuntimeProfileController.Observation resumed = controller.observe(
                SESSION_A,
                facts(true),
                ijkSample(true, 0, 30f, 30f, 0),
                ELAPSED + 31_000,
                EPOCH + 2);
        IjkRuntimeProfileController.Observation stable = controller.observe(
                SESSION_A,
                facts(true),
                ijkSample(true, 0, 30f, 30f, 0),
                ELAPSED + 60_000,
                EPOCH + 3);

        assertEquals(IjkRuntimeProfileController.Reason.CONFIRMING,
                resumed.reason());
        assertEquals(IjkRuntimeProfileController.ObservationAction.STABLE,
                stable.action());
    }

    @Test
    public void missingFrameOrDropEvidenceCannotBeLearnedAsHealthy() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);
        controller.onFirstFrame(
                SESSION_A,
                facts(true),
                new IjkRuntimeProfileController.RuntimeSample(
                        true, 0, false, 0f, false, 0f,
                        false, 0, false, 0, -1, -1),
                ELAPSED,
                EPOCH);

        IjkRuntimeProfileController.Observation observation = controller.observe(
                SESSION_A,
                facts(true),
                new IjkRuntimeProfileController.RuntimeSample(
                        true, 0, false, 0f, false, 0f,
                        false, 0, false, 0, -1, -1),
                ELAPSED + 30_000,
                EPOCH + 1);

        assertEquals(IjkRuntimeProfileController.ObservationAction.HOLD,
                observation.action());
        assertEquals(IjkRuntimeProfilePolicy.HealthReason.FRAME_EVIDENCE_UNKNOWN,
                observation.health().reason());
        assertEquals(0, profiles.stablePaths.size());
        assertEquals(0, profiles.observationPaths.size());
    }

    @Test
    public void switchStartFailureRecordsOutcomeAndStopsCurrentAttempt() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, false);
        controller.handleFailure(
                SESSION_A,
                facts(false),
                IjkRuntimeProfileController.RuntimeSample.unknown(),
                failure(PlaybackErrorClassifier.Stage.UNKNOWN,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                        0,
                        false),
                ELAPSED,
                EPOCH);

        controller.onSwitchStartFailed(SESSION_A, EPOCH + 1);

        assertEquals(List.of(IjkRuntimeProfileStore.FallbackResult.FAILURE),
                profiles.fallbackResults);
        assertTrue(controller.snapshot().attemptFailureRecorded());
        assertFalse(controller.snapshot().fallbackOutcomePending());
    }

    @Test
    public void staleSessionAndManualCancelCannotMutateCurrentProfile() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);

        IjkRuntimeProfileController.Decision stale = controller.handleFailure(
                SESSION_B,
                facts(SESSION_B, true, true,
                        PlaybackAutoContext.DecodeMode.HARDWARE),
                ijkSample(false, 0, 0f, 0f, 0),
                failure(PlaybackErrorClassifier.Stage.DECODER,
                        PlaybackException.ERROR_CODE_DECODING_FAILED,
                        0,
                        true),
                ELAPSED,
                EPOCH);
        controller.cancel(SESSION_A);
        IjkRuntimeProfileController.Decision cancelled = controller.handleFailure(
                SESSION_A,
                facts(true),
                ijkSample(false, 0, 0f, 0f, 0),
                failure(PlaybackErrorClassifier.Stage.DECODER,
                        PlaybackException.ERROR_CODE_DECODING_FAILED,
                        0,
                        true),
                ELAPSED + 1,
                EPOCH + 1);

        assertEquals(IjkRuntimeProfileController.Reason.NOT_MANAGED,
                stale.reason());
        assertEquals(IjkRuntimeProfileController.Reason.NOT_MANAGED,
                cancelled.reason());
        assertEquals(0, profiles.failurePaths.size());
    }

    @Test
    public void newSessionClearsVisitedPathsAndFallbackBudget() {
        RecordingProfiles profiles = new RecordingProfiles();
        IjkRuntimeProfileController controller = controller(profiles, true);
        decoderFailure(controller, true, EPOCH);
        assertNotEquals(0, controller.snapshot().fallbackCount());

        controller.beginSession(SESSION_B);

        assertEquals(SESSION_B, controller.snapshot().session());
        assertEquals(0, controller.snapshot().fallbackCount());
        assertTrue(controller.snapshot().visited().isEmpty());
        assertFalse(controller.snapshot().managed());
    }

    private static IjkRuntimeProfileController controller(
            RecordingProfiles profiles,
            boolean softEligible) {
        IjkRuntimeProfileController controller = new IjkRuntimeProfileController(
                profiles);
        controller.beginSession(SESSION_A);
        assertTrue(controller.activate(
                SESSION_A,
                IjkRuntimeProfilePolicy.Path.IJK_HARD,
                facts(softEligible),
                ijkSample(false, 0, 0f, 0f, 0),
                ELAPSED));
        return controller;
    }

    private static IjkRuntimeProfileController.Decision decoderFailure(
            IjkRuntimeProfileController controller,
            boolean softEligible,
            long nowEpochMs) {
        return controller.handleFailure(
                SESSION_A,
                facts(softEligible),
                ijkSample(false, 0, 0f, 0f, 0),
                failure(PlaybackErrorClassifier.Stage.DECODER,
                        PlaybackException.ERROR_CODE_DECODING_FAILED,
                        0,
                        true),
                ELAPSED + nowEpochMs - EPOCH,
                nowEpochMs);
    }

    private static IjkRuntimeProfileController.FailureEvent failure(
            PlaybackErrorClassifier.Stage stage,
            int errorCode,
            int what,
            boolean prepared) {
        return new IjkRuntimeProfileController.FailureEvent(
                stage, errorCode, what, 0, prepared);
    }

    private static IjkRuntimeProfileController.Facts facts(
            boolean softEligible) {
        return facts(
                SESSION_A,
                true,
                softEligible,
                PlaybackAutoContext.DecodeMode.HARDWARE);
    }

    private static IjkRuntimeProfileController.Facts facts(
            PlaybackAutoContext.SessionToken session,
            boolean automatic,
            boolean softEligible,
            PlaybackAutoContext.DecodeMode decodeMode) {
        return IjkRuntimeProfileController.Facts.forTest(
                session,
                evidence(),
                decodeMode,
                automatic,
                softEligible,
                30f);
    }

    private static IjkRuntimeProfileController.RuntimeSample ijkSample(
            boolean active,
            int rebuffers,
            float decodeFps,
            float outputFps,
            int dropRatePermille) {
        return new IjkRuntimeProfileController.RuntimeSample(
                active,
                rebuffers,
                decodeFps > 0,
                decodeFps,
                outputFps > 0,
                outputFps,
                decodeFps > 0,
                dropRatePermille,
                false,
                0,
                10_000,
                20_000);
    }

    private static IjkRuntimeProfileController.RuntimeSample externalSample(
            boolean active,
            int rebuffers,
            float outputFps,
            long droppedFrames) {
        return new IjkRuntimeProfileController.RuntimeSample(
                active,
                rebuffers,
                false,
                0f,
                outputFps > 0,
                outputFps,
                false,
                0,
                true,
                droppedFrames,
                10_000,
                20_000);
    }

    private static IjkRuntimeProfileKey.Evidence evidence() {
        return new IjkRuntimeProfileKey.Evidence(
                PlaybackAutoContext.Protocol.HLS,
                PlaybackAutoContext.StreamKind.VOD,
                MimeTypes.VIDEO_H265,
                "hvc1.2.4.L153.B0",
                2,
                153,
                1_920,
                1_080,
                30_000,
                PlaybackAutoContext.HdrType.HDR10,
                C.COLOR_SPACE_BT2020,
                C.COLOR_RANGE_LIMITED,
                C.COLOR_TRANSFER_ST2084,
                true,
                0,
                PlaybackAutoContext.RenderTarget.SURFACE_VIEW);
    }

    private static IjkRuntimeProfileKey.Key profileKey(
            RecordingProfiles profiles) {
        return IjkRuntimeProfileKey.from(profiles.environment(), evidence());
    }

    private static final class RecordingProfiles
            implements IjkRuntimeProfileController.ProfileAccess {

        private final IjkRuntimeProfileKey.Environment environment =
                IjkRuntimeProfileKey.environment(
                        "fingerprint", 1, "1", "media", "ijk");
        private final IjkRuntimeProfileStore store =
                new IjkRuntimeProfileStore(new MemoryBackend());
        private final List<IjkRuntimeProfilePolicy.Path> firstFramePaths =
                new ArrayList<>();
        private final List<IjkRuntimeProfilePolicy.Path> failurePaths =
                new ArrayList<>();
        private final List<IjkRuntimeProfilePolicy.Path> stablePaths =
                new ArrayList<>();
        private final List<IjkRuntimeProfilePolicy.Path> observationPaths =
                new ArrayList<>();
        private final List<IjkRuntimeProfilePolicy.Path> fallbackSourcePaths =
                new ArrayList<>();
        private final List<IjkRuntimeProfileStore.FallbackResult>
                fallbackResults = new ArrayList<>();
        private final List<IjkRuntimeProfilePolicy.Path>
                persistentExclusionsLogged = new ArrayList<>();

        @Override
        public IjkRuntimeProfileKey.Environment environment() {
            return environment;
        }

        @Override
        public boolean isExcluded(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                long nowEpochMs) {
            return store.lookup(key, path, nowEpochMs).excluded();
        }

        @Override
        public IjkRuntimeProfilePolicy.Path preferredVerifiedPath(
                IjkRuntimeProfileKey.Key key,
                Set<IjkRuntimeProfilePolicy.Path> blocked,
                boolean softEligible,
                long nowEpochMs) {
            return store.entriesForKey(key, nowEpochMs).stream()
                    .filter(IjkRuntimeProfileStore.Entry::verified)
                    .filter(entry -> blocked == null
                            || !blocked.contains(entry.path()))
                    .filter(entry -> entry.path()
                            != IjkRuntimeProfilePolicy.Path.IJK_SOFT
                            || softEligible)
                    .max(Comparator
                            .comparingInt(IjkRuntimeProfileStore.Entry
                                    ::stableSuccessCount)
                            .thenComparingInt(entry -> -entry.failureCount())
                            .thenComparingLong(IjkRuntimeProfileStore.Entry
                                    ::updatedAtEpochMs))
                    .map(IjkRuntimeProfileStore.Entry::path)
                    .orElse(null);
        }

        @Override
        public IjkRuntimeProfileStore.Entry recordFirstFrame(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                long nowEpochMs) {
            firstFramePaths.add(path);
            return store.recordFirstFrame(key, path, nowEpochMs).entry();
        }

        @Override
        public IjkRuntimeProfileStore.Entry recordFailure(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                IjkRuntimeProfilePolicy.FailureKind failureKind,
                IjkRuntimeProfileStore.Metrics metrics,
                long nowEpochMs) {
            failurePaths.add(path);
            return store.recordFailure(
                    key, path, failureKind, metrics, nowEpochMs).entry();
        }

        @Override
        public IjkRuntimeProfileStore.Entry recordStableSuccess(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                IjkRuntimeProfileStore.Metrics metrics,
                long nowEpochMs) {
            stablePaths.add(path);
            return store.recordStableSuccess(
                    key, path, metrics, nowEpochMs).entry();
        }

        @Override
        public IjkRuntimeProfileStore.Entry recordObservation(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                IjkRuntimeProfileStore.Metrics metrics,
                long nowEpochMs) {
            IjkRuntimeProfileStore.RecordResult result = store.recordObservation(
                    key, path, metrics, nowEpochMs);
            if (result.recorded()) observationPaths.add(path);
            return result.entry();
        }

        @Override
        public IjkRuntimeProfileStore.Entry recordFallback(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                IjkRuntimeProfileStore.FallbackResult fallbackResult,
                long nowEpochMs) {
            fallbackSourcePaths.add(path);
            fallbackResults.add(fallbackResult);
            return store.recordFallback(
                    key, path, fallbackResult, nowEpochMs).entry();
        }

        @Override
        public void logExclusion(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path,
                boolean transientOnly) {
            if (!transientOnly) persistentExclusionsLogged.add(path);
        }

        private void exclude(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path) {
            store.recordFailure(
                    key, path, IjkRuntimeProfilePolicy.FailureKind.DECODER,
                    IjkRuntimeProfileStore.Metrics.EMPTY, EPOCH);
            store.recordFailure(
                    key, path, IjkRuntimeProfilePolicy.FailureKind.RUNTIME,
                    IjkRuntimeProfileStore.Metrics.EMPTY, EPOCH + 1);
        }

        private void verify(
                IjkRuntimeProfileKey.Key key,
                IjkRuntimeProfilePolicy.Path path) {
            store.recordStableSuccess(
                    key, path,
                    new IjkRuntimeProfileStore.Metrics(0, 0, 1_000, 0, 0),
                    EPOCH);
        }
    }

    private static final class MemoryBackend
            implements IjkRuntimeProfileStore.Backend {

        private String value = "";

        @Override
        public String read() {
            return value;
        }

        @Override
        public void write(String value) {
            this.value = value == null ? "" : value;
        }

        @Override
        public void clear() {
            value = "";
        }
    }
}
