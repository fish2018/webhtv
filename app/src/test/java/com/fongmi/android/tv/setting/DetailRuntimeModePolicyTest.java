package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DetailRuntimeModePolicyTest {

    @Test
    public void directModeNeverUsesSourceOrNetwork() {
        DetailRuntimeModePolicy.Decision decision = resolve(Setting.DETAIL_OPEN_DIRECT, true, true, TmdbSourceState.RENDERABLE);

        assertEquals(Setting.DETAIL_OPEN_DIRECT, decision.runtimeMode());
        assertFalse(decision.sourceOnly());
        assertFalse(decision.networkAllowed());
    }

    @Test
    public void sitePolicyCanAlwaysForceOriginalDetail() {
        DetailRuntimeModePolicy.Decision decision = resolve(Setting.DETAIL_OPEN_ENHANCED, true, false, TmdbSourceState.RENDERABLE);

        assertEquals(Setting.DETAIL_OPEN_DIRECT, decision.runtimeMode());
        assertFalse(decision.sourceOnly());
        assertFalse(decision.networkAllowed());
    }

    @Test
    public void keylessModeFallsBackUnlessSourceIsRenderable() {
        DetailRuntimeModePolicy.Decision absent = resolve(Setting.DETAIL_OPEN_ENHANCED, false, true, TmdbSourceState.ABSENT_OR_INVALID);
        DetailRuntimeModePolicy.Decision identityOnly = resolve(Setting.DETAIL_OPEN_ENHANCED, false, true, TmdbSourceState.IDENTITY_ONLY);
        DetailRuntimeModePolicy.Decision renderable = resolve(Setting.DETAIL_OPEN_ENHANCED, false, true, TmdbSourceState.RENDERABLE);

        assertEquals(Setting.DETAIL_OPEN_DIRECT, absent.runtimeMode());
        assertEquals(Setting.DETAIL_OPEN_DIRECT, identityOnly.runtimeMode());
        assertEquals(Setting.DETAIL_OPEN_ENHANCED, renderable.runtimeMode());
        assertTrue(renderable.sourceOnly());
        assertFalse(renderable.networkAllowed());
    }

    @Test
    public void networkCapabilityIsIndependentFromSourceAvailability() {
        TmdbSourceState[] states = TmdbSourceState.values();
        for (int mode : new int[]{Setting.DETAIL_OPEN_ORIGINAL_ENHANCED, Setting.DETAIL_OPEN_FUSION, Setting.DETAIL_OPEN_ENHANCED, Setting.DETAIL_OPEN_PLAYER}) {
            for (TmdbSourceState state : states) {
                DetailRuntimeModePolicy.Decision decision = resolve(mode, true, true, state);
                assertEquals(mode, decision.runtimeMode());
                assertTrue(decision.networkAllowed());
                assertFalse(decision.sourceOnly());
            }
        }
    }

    private static DetailRuntimeModePolicy.Decision resolve(int mode, boolean ready, boolean allowed, TmdbSourceState state) {
        return DetailRuntimeModePolicy.resolve(new DetailRuntimeModePolicy.Input(mode, ready, allowed, state));
    }
}
