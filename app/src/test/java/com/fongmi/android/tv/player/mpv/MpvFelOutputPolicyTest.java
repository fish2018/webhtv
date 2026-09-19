package com.fongmi.android.tv.player.mpv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MpvFelOutputPolicyTest {

    @Test
    public void nativeDv7DoesNotStealAnExplicitFelRequest() {
        MpvAutoOutputPolicy.Decision nativeDecision = MpvAutoOutputPolicy.evaluate(
                3840, 2160, true, true, false, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED, 7);
        assertTrue(nativeDecision.eligible());
        MpvAutoOutputPolicy.Decision fel =
                MpvAutoOutputPolicy.forFelReconstruction(nativeDecision, true);
        assertFalse(fel.eligible());
        assertEquals("dv7-fel-reconstruction", fel.reason());
        assertEquals(MpvAutoOutputPolicy.Transition.LEAVE_SURFACE_DIRECT,
                MpvAutoOutputPolicy.transition(fel.eligible(), true));
        assertEquals(MpvAutoOutputPolicy.Transition.KEEP_GPU,
                MpvAutoOutputPolicy.transition(fel.eligible(), false));
    }

    @Test
    public void everyExistingDecisionIsReturnedUnchangedWithoutFel() {
        for (boolean tv : new boolean[]{false, true}) {
            for (boolean hardware : new boolean[]{false, true}) {
                for (int profile : new int[]{-1, 5, 7, 8}) {
                    for (MpvAutoOutputPolicy.DolbyVisionSupport support
                            : MpvAutoOutputPolicy.DolbyVisionSupport.values()) {
                        MpvAutoOutputPolicy.Decision decision = MpvAutoOutputPolicy.evaluate(
                                3840, 2160, hardware, tv, false, false, support, profile);
                        assertSame(decision,
                                MpvAutoOutputPolicy.forFelReconstruction(decision, false));
                    }
                }
            }
        }
    }
}
