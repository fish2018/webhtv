package com.fongmi.android.tv.player.mpv;

public final class MpvAutoOutputPolicy {

    private MpvAutoOutputPolicy() {
    }

    public static Decision evaluate(int width, int height, boolean hardDecode,
                                    boolean leanback, boolean lutOrFilterActive,
                                    boolean customGpuProcessing) {
        return evaluate(width, height, hardDecode, leanback, lutOrFilterActive,
                customGpuProcessing, DolbyVisionSupport.UNKNOWN, -1);
    }

    public static Decision evaluate(int width, int height, boolean hardDecode,
                                    boolean leanback, boolean lutOrFilterActive,
                                    boolean customGpuProcessing,
                                    DolbyVisionSupport dolbyVisionSupport,
                                    int dolbyVisionProfile) {
        return evaluate(width, height, hardDecode, leanback, lutOrFilterActive,
                customGpuProcessing, dolbyVisionSupport, dolbyVisionProfile, false);
    }

    public static Decision evaluate(int width, int height, boolean hardDecode,
                                    boolean leanback, boolean lutOrFilterActive,
                                    boolean customGpuProcessing,
                                    DolbyVisionSupport dolbyVisionSupport,
                                    int dolbyVisionProfile,
                                    boolean dv7Hdr10FallbackEnabled) {
        return evaluate(width, height, hardDecode, leanback, lutOrFilterActive,
                customGpuProcessing, dolbyVisionSupport, dolbyVisionProfile,
                dv7Hdr10FallbackEnabled, DolbyVisionSupport.UNKNOWN);
    }

    public static Decision evaluate(int width, int height, boolean hardDecode,
                                    boolean leanback, boolean lutOrFilterActive,
                                    boolean customGpuProcessing,
                                    DolbyVisionSupport dolbyVisionSupport,
                                    int dolbyVisionProfile,
                                    boolean dv7Hdr10FallbackEnabled,
                                    DolbyVisionSupport hevcHdr10Support) {
        if (!leanback) return new Decision(false, "not-tv");
        if (!hardDecode) return new Decision(false, "software-decode");
        if (lutOrFilterActive) return new Decision(false, "lut-or-filter-active");
        if (customGpuProcessing) return new Decision(false, "custom-gpu-processing");
        if (dolbyVisionProfile > 0) {
            if (dolbyVisionSupport == DolbyVisionSupport.SUPPORTED) {
                return new Decision(true, "dolby-vision-hw-supported");
            }
            if (dolbyVisionProfile == 7
                    && dv7Hdr10FallbackEnabled
                    && dolbyVisionSupport == DolbyVisionSupport.UNSUPPORTED) {
                return new Decision(true, "dv7-hdr10-base-layer");
            }
            if (dolbyVisionProfile == 8
                    && dolbyVisionSupport == DolbyVisionSupport.UNSUPPORTED
                    && hevcHdr10Support == DolbyVisionSupport.SUPPORTED) {
                return new Decision(true, "dv8-hdr10-base-layer");
            }
            return new Decision(false, dolbyVisionSupport == DolbyVisionSupport.UNKNOWN
                    ? "dolby-vision-hw-unknown" : "dolby-vision-hw-unsupported");
        }
        return new Decision(true, "tv-hardware-decode");
    }

    /** A same-item rebuild must not retry an output that already failed. */
    public static Decision afterSurfaceFailure(Decision candidate, boolean failedForItem) {
        return failedForItem ? new Decision(false, "surface-direct-failed-for-item") : candidate;
    }

    /** A manually selected FEL reconstruction needs both layers in gpu-next. */
    public static Decision forFelReconstruction(Decision candidate, boolean enabled) {
        return enabled ? new Decision(false, "dv7-fel-reconstruction") : candidate;
    }

    /** Select the initial TV output before MPV has reported a video size. */
    public static boolean canStartSurfaceDirect(boolean hardDecode, boolean leanback,
                                                 boolean lutOrFilterActive,
                                                 boolean customGpuProcessing) {
        return evaluate(1, 1, hardDecode, leanback, lutOrFilterActive,
                customGpuProcessing).eligible();
    }

    public static Transition transition(boolean eligible, boolean currentlyDirect) {
        if (eligible) return currentlyDirect ? Transition.KEEP_SURFACE_DIRECT : Transition.ENTER_SURFACE_DIRECT;
        return currentlyDirect ? Transition.LEAVE_SURFACE_DIRECT : Transition.KEEP_GPU;
    }

    public static boolean canEvaluateWithoutTracks(int width, int height) {
        return width > 0 && height > 0;
    }

    public static boolean canRevealDirectFrame(boolean automaticOutput,
                                               boolean outputEvaluated,
                                               boolean playbackReady,
                                               boolean surfaceDirect,
                                               int width,
                                               int height) {
        return automaticOutput
                && !outputEvaluated
                && playbackReady
                && surfaceDirect
                && width > 0
                && height > 0;
    }

    public enum Transition {
        KEEP_GPU,
        ENTER_SURFACE_DIRECT,
        KEEP_SURFACE_DIRECT,
        LEAVE_SURFACE_DIRECT
    }

    public record Decision(boolean eligible, String reason) {
    }

    public enum DolbyVisionSupport {
        UNKNOWN,
        SUPPORTED,
        UNSUPPORTED
    }
}
