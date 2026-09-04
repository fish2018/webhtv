package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExoDv5GpuMappingPolicyTest {

    @Test
    public void detectsOnlyDolbyVisionProfile5() {
        assertTrue(ExoDv5GpuMappingPolicy.isProfile5(
                "video/dolby-vision", "dvhe.05.06"));
        assertTrue(ExoDv5GpuMappingPolicy.isProfile5(
                "video/dolby-vision", " DVH1.05.09 , hev1.2.4.L153.B0"));
        assertFalse(ExoDv5GpuMappingPolicy.isProfile5(
                "video/dolby-vision", "dvhe.07.06"));
        assertFalse(ExoDv5GpuMappingPolicy.isProfile5(
                "video/dolby-vision", "dvh1.08.06"));
        assertFalse(ExoDv5GpuMappingPolicy.isProfile5(
                "video/hevc", "dvhe.05.06"));
    }

    @Test
    public void nativeDolbyVisionWinsWhenDisplayAndDecoderAreAvailable() {
        ExoDv5GpuMappingPolicy.Decision decision = decide(input()
                .nativeDisplay(true)
                .nativeDecoder(true)
                .codecToneMapAccepted(true)
                .rendererAvailable(true)
                .probePassed(true)
                .experimentEnabled(true));

        assertRoute(
                decision,
                ExoDv5GpuMappingPolicy.Route.NATIVE_DOLBY_VISION,
                ExoDv5GpuMappingPolicy.Reason.NATIVE_DOLBY_VISION_AVAILABLE);
    }

    @Test
    public void nativeDolbyVisionRequiresBothDisplayAndDecoder() {
        ExoDv5GpuMappingPolicy.Decision decision = decide(input()
                .nativeDisplay(true)
                .rendererAvailable(true)
                .probePassed(true)
                .experimentEnabled(true));

        assertRoute(
                decision,
                ExoDv5GpuMappingPolicy.Route.GPU_MAPPING,
                ExoDv5GpuMappingPolicy.Reason.GPU_MAPPING_ELIGIBLE);
    }

    @Test
    public void acceptedCodecToneMapRequiresApi31AndPrecedesGpuMapping() {
        ExoDv5GpuMappingPolicy.Decision accepted = decide(input()
                .apiLevel(31)
                .codecToneMapAccepted(true)
                .rendererAvailable(true)
                .probePassed(true)
                .experimentEnabled(true));
        ExoDv5GpuMappingPolicy.Decision tooOld = decide(input()
                .apiLevel(30)
                .codecToneMapAccepted(true)
                .rendererAvailable(true)
                .probePassed(true)
                .experimentEnabled(true));

        assertRoute(
                accepted,
                ExoDv5GpuMappingPolicy.Route.CODEC_TONE_MAP,
                ExoDv5GpuMappingPolicy.Reason.CODEC_TONE_MAP_ACCEPTED);
        assertEquals(ExoDv5GpuMappingPolicy.Route.GPU_MAPPING, tooOld.route());
    }

    @Test
    public void gpuMappingRequiresEveryRuntimeGate() {
        ExoDv5GpuMappingPolicy.Decision decision = decide(input()
                .rendererAvailable(true)
                .probePassed(true)
                .experimentEnabled(true));

        assertRoute(
                decision,
                ExoDv5GpuMappingPolicy.Route.GPU_MAPPING,
                ExoDv5GpuMappingPolicy.Reason.GPU_MAPPING_ELIGIBLE);
        assertTrue(decision.usesGpuMapping());
        assertEquals(ExoDv5GpuMappingPolicy.Reason.NONE,
                decision.gpuBlockReason());
    }

    @Test
    public void drmNeverEntersGpuMapping() {
        ExoDv5GpuMappingPolicy.Decision decision = decide(input()
                .drmProtected(true)
                .rendererAvailable(true)
                .probePassed(true)
                .experimentEnabled(true)
                .legacyFallbackAllowed(true));

        assertBlocked(
                decision, ExoDv5GpuMappingPolicy.Reason.DRM_REQUIRES_SECURE_OUTPUT);
    }

    @Test
    public void gpuMappingReportsEachMissingCapability() {
        assertBlocked(decide(input().apiLevel(25)
                        .rendererAvailable(true).probePassed(true).experimentEnabled(true)),
                ExoDv5GpuMappingPolicy.Reason.API_BELOW_26);
        assertBlocked(decide(input().rendererAvailable(true).probePassed(true)),
                ExoDv5GpuMappingPolicy.Reason.EXPERIMENT_DISABLED);
        assertBlocked(decide(input().tunneling(true)
                        .rendererAvailable(true).probePassed(true).experimentEnabled(true)),
                ExoDv5GpuMappingPolicy.Reason.TUNNELING_UNSUPPORTED);
        assertBlocked(decide(input().hevcDecoder(false)
                        .rendererAvailable(true).probePassed(true).experimentEnabled(true)),
                ExoDv5GpuMappingPolicy.Reason.HEVC_DECODER_UNAVAILABLE);
        assertBlocked(decide(input().probePassed(true).experimentEnabled(true)),
                ExoDv5GpuMappingPolicy.Reason.RENDERER_UNAVAILABLE);
        assertBlocked(decide(input().rendererAvailable(true).experimentEnabled(true)),
                ExoDv5GpuMappingPolicy.Reason.VULKAN_AHB_PROBE_FAILED);
    }

    @Test
    public void legacyFallbackIsExplicitAndRetainsGpuBlockReason() {
        ExoDv5GpuMappingPolicy.Decision allowed = decide(input()
                .legacyFallbackAllowed(true));
        ExoDv5GpuMappingPolicy.Decision rejected = decide(input());

        assertRoute(
                allowed,
                ExoDv5GpuMappingPolicy.Route.LEGACY_HDR10_FALLBACK,
                ExoDv5GpuMappingPolicy.Reason.LEGACY_HDR10_FALLBACK_ALLOWED);
        assertEquals(
                ExoDv5GpuMappingPolicy.Reason.EXPERIMENT_DISABLED,
                allowed.gpuBlockReason());
        assertBlocked(
                rejected, ExoDv5GpuMappingPolicy.Reason.EXPERIMENT_DISABLED);
    }

    @Test
    public void nonProfile5NeverSelectsAnyFallback() {
        ExoDv5GpuMappingPolicy.Decision decision = decide(input().profile5(false));

        assertBlocked(decision, ExoDv5GpuMappingPolicy.Reason.NOT_PROFILE_5);
    }

    private static void assertRoute(
            ExoDv5GpuMappingPolicy.Decision decision,
            ExoDv5GpuMappingPolicy.Route route,
            ExoDv5GpuMappingPolicy.Reason reason) {
        assertEquals(route, decision.route());
        assertEquals(reason, decision.reason());
    }

    private static void assertBlocked(
            ExoDv5GpuMappingPolicy.Decision decision,
            ExoDv5GpuMappingPolicy.Reason reason) {
        assertRoute(decision, ExoDv5GpuMappingPolicy.Route.UNSUPPORTED, reason);
        assertEquals(reason, decision.gpuBlockReason());
        assertFalse(decision.usesGpuMapping());
    }

    private static ExoDv5GpuMappingPolicy.Decision decide(InputBuilder input) {
        return ExoDv5GpuMappingPolicy.decide(input.build());
    }

    private static InputBuilder input() {
        return new InputBuilder();
    }

    private static final class InputBuilder {

        private boolean profile5 = true;
        private boolean drmProtected;
        private boolean nativeDisplay;
        private boolean nativeDecoder;
        private int apiLevel = 34;
        private boolean codecToneMapAccepted;
        private boolean hevcDecoder = true;
        private boolean rendererAvailable;
        private boolean probePassed;
        private boolean experimentEnabled;
        private boolean tunneling;
        private boolean legacyFallbackAllowed;

        InputBuilder profile5(boolean value) {
            profile5 = value;
            return this;
        }

        InputBuilder drmProtected(boolean value) {
            drmProtected = value;
            return this;
        }

        InputBuilder nativeDisplay(boolean value) {
            nativeDisplay = value;
            return this;
        }

        InputBuilder nativeDecoder(boolean value) {
            nativeDecoder = value;
            return this;
        }

        InputBuilder apiLevel(int value) {
            apiLevel = value;
            return this;
        }

        InputBuilder codecToneMapAccepted(boolean value) {
            codecToneMapAccepted = value;
            return this;
        }

        InputBuilder hevcDecoder(boolean value) {
            hevcDecoder = value;
            return this;
        }

        InputBuilder rendererAvailable(boolean value) {
            rendererAvailable = value;
            return this;
        }

        InputBuilder probePassed(boolean value) {
            probePassed = value;
            return this;
        }

        InputBuilder experimentEnabled(boolean value) {
            experimentEnabled = value;
            return this;
        }

        InputBuilder tunneling(boolean value) {
            tunneling = value;
            return this;
        }

        InputBuilder legacyFallbackAllowed(boolean value) {
            legacyFallbackAllowed = value;
            return this;
        }

        ExoDv5GpuMappingPolicy.Input build() {
            return new ExoDv5GpuMappingPolicy.Input(
                    profile5,
                    drmProtected,
                    nativeDisplay,
                    nativeDecoder,
                    apiLevel,
                    codecToneMapAccepted,
                    hevcDecoder,
                    rendererAvailable,
                    probePassed,
                    experimentEnabled,
                    tunneling,
                    legacyFallbackAllowed);
        }
    }
}
