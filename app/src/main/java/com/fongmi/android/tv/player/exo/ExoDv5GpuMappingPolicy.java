package com.fongmi.android.tv.player.exo;

import java.util.Locale;

/** Pure route selection policy for Dolby Vision Profile 5 playback. */
final class ExoDv5GpuMappingPolicy {

    static final int MIN_GPU_MAPPING_API = 26;
    static final int MIN_CODEC_TONE_MAP_API = 31;

    private ExoDv5GpuMappingPolicy() {
    }

    static Decision decide(Input input) {
        Input safe = input == null ? Input.unsupported() : input;
        if (!safe.profile5()) {
            return unsupported(Reason.NOT_PROFILE_5);
        }
        if (safe.nativeDolbyVisionDisplayAvailable()
                && safe.nativeDolbyVisionDecoderAvailable()) {
            return selected(
                    Route.NATIVE_DOLBY_VISION,
                    Reason.NATIVE_DOLBY_VISION_AVAILABLE);
        }
        if (safe.apiLevel() >= MIN_CODEC_TONE_MAP_API
                && safe.codecToneMapRequestAccepted()) {
            return selected(Route.CODEC_TONE_MAP, Reason.CODEC_TONE_MAP_ACCEPTED);
        }

        Reason gpuBlockReason = gpuBlockReason(safe);
        if (gpuBlockReason == Reason.NONE) {
            return selected(Route.GPU_MAPPING, Reason.GPU_MAPPING_ELIGIBLE);
        }
        if (!safe.drmProtected() && safe.legacyHdr10FallbackAllowed()) {
            return new Decision(
                    Route.LEGACY_HDR10_FALLBACK,
                    Reason.LEGACY_HDR10_FALLBACK_ALLOWED,
                    gpuBlockReason);
        }
        return new Decision(Route.UNSUPPORTED, gpuBlockReason, gpuBlockReason);
    }

    static boolean isProfile5(String sampleMimeType, String codecs) {
        if (!"video/dolby-vision".equals(sampleMimeType)
                || codecs == null
                || codecs.isBlank()) {
            return false;
        }
        int comma = codecs.indexOf(',');
        String firstCodec = (comma < 0 ? codecs : codecs.substring(0, comma))
                .trim()
                .toLowerCase(Locale.US);
        return firstCodec.startsWith("dvhe.05.")
                || firstCodec.startsWith("dvh1.05.");
    }

    private static Reason gpuBlockReason(Input input) {
        if (input.drmProtected()) return Reason.DRM_REQUIRES_SECURE_OUTPUT;
        if (input.apiLevel() < MIN_GPU_MAPPING_API) return Reason.API_BELOW_26;
        if (!input.experimentalGpuMappingEnabled()) return Reason.EXPERIMENT_DISABLED;
        if (input.tunnelingRequested()) return Reason.TUNNELING_UNSUPPORTED;
        if (!input.hardwareHevcDecoderAvailable()) return Reason.HEVC_DECODER_UNAVAILABLE;
        if (!input.independentRendererAvailable()) return Reason.RENDERER_UNAVAILABLE;
        if (!input.vulkanAhbProbePassed()) return Reason.VULKAN_AHB_PROBE_FAILED;
        return Reason.NONE;
    }

    private static Decision selected(Route route, Reason reason) {
        return new Decision(route, reason, Reason.NONE);
    }

    private static Decision unsupported(Reason reason) {
        return new Decision(Route.UNSUPPORTED, reason, reason);
    }

    enum Route {
        NATIVE_DOLBY_VISION,
        CODEC_TONE_MAP,
        GPU_MAPPING,
        LEGACY_HDR10_FALLBACK,
        UNSUPPORTED
    }

    enum Reason {
        NONE,
        NOT_PROFILE_5,
        NATIVE_DOLBY_VISION_AVAILABLE,
        CODEC_TONE_MAP_ACCEPTED,
        GPU_MAPPING_ELIGIBLE,
        DRM_REQUIRES_SECURE_OUTPUT,
        API_BELOW_26,
        EXPERIMENT_DISABLED,
        TUNNELING_UNSUPPORTED,
        HEVC_DECODER_UNAVAILABLE,
        RENDERER_UNAVAILABLE,
        VULKAN_AHB_PROBE_FAILED,
        LEGACY_HDR10_FALLBACK_ALLOWED
    }

    record Input(
            boolean profile5,
            boolean drmProtected,
            boolean nativeDolbyVisionDisplayAvailable,
            boolean nativeDolbyVisionDecoderAvailable,
            int apiLevel,
            boolean codecToneMapRequestAccepted,
            boolean hardwareHevcDecoderAvailable,
            boolean independentRendererAvailable,
            boolean vulkanAhbProbePassed,
            boolean experimentalGpuMappingEnabled,
            boolean tunnelingRequested,
            boolean legacyHdr10FallbackAllowed) {

        static Input unsupported() {
            return new Input(
                    false,
                    false,
                    false,
                    false,
                    0,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false);
        }
    }

    record Decision(Route route, Reason reason, Reason gpuBlockReason) {

        boolean usesGpuMapping() {
            return route == Route.GPU_MAPPING;
        }
    }
}
