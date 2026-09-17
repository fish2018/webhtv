package com.fongmi.android.tv.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackExperimentPolicyTest {

    @Test
    public void missingConfigurationInitializesStableAndFailsClosed() {
        PlaybackExperimentPolicy.Resolution result =
                PlaybackExperimentPolicy.resolve(null);

        assertEquals(PlaybackExperimentPolicy.Status.UNINITIALIZED,
                result.status());
        assertTrue(result.writeBack());
        assertTrue(result.failClosed());
        assertFalse(result.state().enabled());
        assertTrue(result.state().exoEnabled());
        assertTrue(result.state().mpvEnabled());
        assertTrue(result.state().ijkEnabled());
    }

    @Test
    public void stablePolicyKeepsSafetyAndProductionAutomationEnabled() {
        PlaybackExperimentPolicy.State state =
                PlaybackExperimentPolicy.State.stable();

        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.DISK_CAPACITY_PROTECTION));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.MEMORY_PRESSURE_SHRINK));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.READ_ONLY_TELEMETRY));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.EXO_NETWORK_SPEED));
        assertFalse(state.allows(
                PlaybackExperimentPolicy.Action.EXO_FRAME_SCHEDULING_AB));
        assertFalse(state.allows(
                PlaybackExperimentPolicy.Action.SHARED_PROFILE_AB_VALIDATION));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.MPV_AUTO_PRELOAD));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.IJK_RUNTIME_KERNEL_FALLBACK));
        assertEquals("playback-auto-production-v2",
                PlaybackExperimentPolicy.STABLE_STRATEGY_ID);
        assertEquals(PlaybackExperimentPolicy.STABLE_STRATEGY_ID,
                state.strategyId());
    }

    @Test
    public void automaticOptimizationsIgnoreLegacyExperimentSwitches() {
        PlaybackExperimentPolicy.State state =
                new PlaybackExperimentPolicy.State(
                        PlaybackExperimentPolicy.CURRENT_SCHEMA_VERSION,
                        false, false, false, false);

        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.EXO_AUTO_PRELOAD));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.EXO_SHORT_DRAMA_QUEUE));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.MPV_HLS_RUNTIME_RELOAD));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.IJK_DECODE_REBUILD));
        assertFalse(state.allows(
                PlaybackExperimentPolicy.Action.EXO_FRAME_SCHEDULING_AB));
    }

    @Test
    public void domainsOnlyGateInternalExperiments() {
        PlaybackExperimentPolicy.State state =
                new PlaybackExperimentPolicy.State(
                        PlaybackExperimentPolicy.CURRENT_SCHEMA_VERSION,
                        true, true, false, true);

        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.EXO_AUTO_PRELOAD));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.EXO_FRAME_SCHEDULING_AB));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.SHARED_PROFILE_AB_VALIDATION));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.MPV_CACHE_EXPANSION));
        assertTrue(state.allows(
                PlaybackExperimentPolicy.Action.IJK_BUFFER_RELOAD));
        assertFalse(state.domainEnabled(
                PlaybackExperimentPolicy.Domain.MPV));
        assertEquals("playback-internal-experiment-v2",
                PlaybackExperimentPolicy.EXPERIMENT_STRATEGY_ID);
        assertEquals(PlaybackExperimentPolicy.EXPERIMENT_STRATEGY_ID,
                state.strategyId());
    }

    @Test
    public void corruptCurrentConfigurationIsReplacedByStablePolicy() {
        PlaybackExperimentPolicy.Resolution result =
                PlaybackExperimentPolicy.resolve(
                        new PlaybackExperimentPolicy.RawState(
                                PlaybackExperimentPolicy.CURRENT_SCHEMA_VERSION,
                                true, true, "bad", true));

        assertEquals(PlaybackExperimentPolicy.Status.CORRUPT,
                result.status());
        assertTrue(result.writeBack());
        assertTrue(result.failClosed());
        assertFalse(result.state().enabled());
    }

    @Test
    public void corruptSchemaTypeIsReplacedByStablePolicy() {
        PlaybackExperimentPolicy.Resolution result =
                PlaybackExperimentPolicy.resolve(
                        new PlaybackExperimentPolicy.RawState(
                                "one", true, true, true, true));

        assertEquals(PlaybackExperimentPolicy.Status.CORRUPT,
                result.status());
        assertTrue(result.writeBack());
        assertTrue(result.failClosed());
    }

    @Test
    public void fractionalSchemaNumberCannotMasqueradeAsCurrentVersion() {
        PlaybackExperimentPolicy.Resolution result =
                PlaybackExperimentPolicy.resolve(
                        new PlaybackExperimentPolicy.RawState(
                                1.5d, true, true, true, true));

        assertEquals(PlaybackExperimentPolicy.Status.CORRUPT,
                result.status());
        assertTrue(result.writeBack());
        assertTrue(result.failClosed());
        assertFalse(result.state().enabled());
    }

    @Test
    public void futureSchemaFailsClosedWithoutDowngradingStoredData() {
        PlaybackExperimentPolicy.Resolution result =
                PlaybackExperimentPolicy.resolve(
                        new PlaybackExperimentPolicy.RawState(
                                99, true, true, true, true));

        assertEquals(PlaybackExperimentPolicy.Status.FUTURE_SCHEMA,
                result.status());
        assertFalse(result.writeBack());
        assertTrue(result.failClosed());
        assertTrue(result.state().allows(
                PlaybackExperimentPolicy.Action.EXO_VIDEO_CONSTRAINT));
        assertFalse(result.state().allows(
                PlaybackExperimentPolicy.Action.EXO_FRAME_SCHEDULING_AB));
    }

    @Test
    public void schemaZeroMigrationDisablesLegacyExperimentOptIn() {
        PlaybackExperimentPolicy.Resolution result =
                PlaybackExperimentPolicy.resolve(
                        new PlaybackExperimentPolicy.RawState(
                                0, true, null, null, null));

        assertEquals(PlaybackExperimentPolicy.Status.MIGRATED,
                result.status());
        assertTrue(result.writeBack());
        assertFalse(result.state().enabled());
        assertTrue(result.state().exoEnabled());
        assertTrue(result.state().mpvEnabled());
        assertTrue(result.state().ijkEnabled());
        assertTrue(result.state().allows(
                PlaybackExperimentPolicy.Action.EXO_AUTO_PRELOAD));
        assertFalse(result.state().allows(
                PlaybackExperimentPolicy.Action.EXO_FRAME_SCHEDULING_AB));
    }

    @Test
    public void schemaOneMigrationDisablesPreviouslyEnabledInternalExperiments() {
        PlaybackExperimentPolicy.Resolution result =
                PlaybackExperimentPolicy.resolve(
                        new PlaybackExperimentPolicy.RawState(
                                1, true, true, true, true));

        assertEquals(PlaybackExperimentPolicy.Status.MIGRATED,
                result.status());
        assertTrue(result.writeBack());
        assertFalse(result.state().enabled());
        assertEquals(PlaybackExperimentPolicy.CURRENT_SCHEMA_VERSION,
                result.state().schemaVersion());
        assertTrue(result.state().allows(
                PlaybackExperimentPolicy.Action.MPV_HLS_RUNTIME_RELOAD));
        assertFalse(result.state().allows(
                PlaybackExperimentPolicy.Action.SHARED_PROFILE_AB_VALIDATION));
    }

    @Test
    public void incompleteSchemaZeroFailsClosedInsteadOfGuessingState() {
        PlaybackExperimentPolicy.Resolution result =
                PlaybackExperimentPolicy.resolve(
                        new PlaybackExperimentPolicy.RawState(
                                0, null, null, null, null));

        assertEquals(PlaybackExperimentPolicy.Status.CORRUPT,
                result.status());
        assertTrue(result.writeBack());
        assertTrue(result.failClosed());
        assertFalse(result.state().enabled());
    }

    @Test
    public void registryHasUniqueVersionedActionIds() {
        assertTrue(PlaybackExperimentPolicy.registryIsValid());
    }

    @Test
    public void mpvAutomaticOutputIsNotRegisteredAsAnExperimentAction() {
        for (PlaybackExperimentPolicy.Action action
                : PlaybackExperimentPolicy.Action.values()) {
            assertFalse("mpv.auto-output-rebuild".equals(action.id()));
        }
    }
}
