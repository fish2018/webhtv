package com.fongmi.android.tv.player.mpv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MpvAutoRenderPolicyTest {

    @Test
    public void enablesVulkanForUnsupportedNativeDv5() {
        assertDecision(MpvAutoRenderPolicy.Action.ENABLE_VULKAN,
                "dv5-gpu-mapping-vulkan", true, true, 5,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                true, true, false, false);
    }

    @Test
    public void preservesManualRenderOverride() {
        assertDecision(MpvAutoRenderPolicy.Action.KEEP, "render-overridden",
                false, true, 5,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                true, true, false, false);
    }

    @Test
    public void preservesNativeOrUnknownDolbyVisionRouting() {
        assertDecision(MpvAutoRenderPolicy.Action.KEEP, "native-dv5-supported",
                true, true, 5,
                MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED,
                true, true, false, false);
        assertDecision(MpvAutoRenderPolicy.Action.KEEP, "native-dv5-unknown",
                true, true, 5,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN,
                true, true, false, false);
    }

    @Test
    public void preservesNonDv5AndSoftwareDecode() {
        assertDecision(MpvAutoRenderPolicy.Action.KEEP, "not-dv5",
                true, true, 7,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                true, true, false, false);
        assertDecision(MpvAutoRenderPolicy.Action.KEEP, "software-decode",
                true, false, 5,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                true, true, false, false);
    }

    @Test
    public void requiresBothVulkanCapabilities() {
        assertDecision(MpvAutoRenderPolicy.Action.KEEP, "vulkan-unavailable",
                true, true, 5,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                false, true, false, false);
        assertDecision(MpvAutoRenderPolicy.Action.KEEP, "vulkan-unavailable",
                true, true, 5,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                true, false, false, false);
    }

    @Test
    public void doesNotRebuildAnActiveOrFailedItem() {
        assertDecision(MpvAutoRenderPolicy.Action.KEEP, "already-vulkan",
                true, true, 5,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                true, true, true, false);
        assertDecision(MpvAutoRenderPolicy.Action.KEEP, "item-fallback",
                true, true, 5,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                true, true, false, true);
    }

    @Test
    public void manualFelUsesVulkanWithoutRequiringANativeDolbyDecoder() {
        for (MpvAutoOutputPolicy.DolbyVisionSupport support
                : MpvAutoOutputPolicy.DolbyVisionSupport.values()) {
            MpvAutoRenderPolicy.Decision decision = MpvAutoRenderPolicy.evaluate(
                    true, true, 7, support, true, true, false, false, true);
            assertEquals(MpvAutoRenderPolicy.Action.ENABLE_VULKAN, decision.action());
            assertEquals("dv7-fel-reconstruction-vulkan", decision.reason());
        }
    }

    @Test
    public void felStillHonorsManualRenderAndOneShotFallback() {
        assertEquals(MpvAutoRenderPolicy.Action.KEEP, MpvAutoRenderPolicy.evaluate(
                false, true, 7, MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN,
                true, true, false, false, true).action());
        assertEquals(MpvAutoRenderPolicy.Action.KEEP, MpvAutoRenderPolicy.evaluate(
                true, true, 7, MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN,
                true, true, false, true, true).action());
        assertEquals(MpvAutoRenderPolicy.Action.KEEP, MpvAutoRenderPolicy.evaluate(
                true, true, 7, MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN,
                true, false, false, false, true).action());
        assertEquals(MpvAutoRenderPolicy.Action.KEEP, MpvAutoRenderPolicy.evaluate(
                true, true, -1, MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN,
                true, true, false, false, true).action());
    }

    private static void assertDecision(MpvAutoRenderPolicy.Action action,
                                       String reason,
                                       boolean renderAutomatic,
                                       boolean hardDecode,
                                       int profile,
                                       MpvAutoOutputPolicy.DolbyVisionSupport support,
                                       boolean nativeVulkan,
                                       boolean deviceVulkan,
                                       boolean currentlyVulkan,
                                       boolean disabledForItem) {
        MpvAutoRenderPolicy.Decision decision = MpvAutoRenderPolicy.evaluate(
                renderAutomatic, hardDecode, profile, support,
                nativeVulkan, deviceVulkan, currentlyVulkan, disabledForItem);
        assertEquals(action, decision.action());
        assertEquals(reason, decision.reason());
    }
}
