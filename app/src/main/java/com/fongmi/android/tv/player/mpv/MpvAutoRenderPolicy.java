package com.fongmi.android.tv.player.mpv;

public final class MpvAutoRenderPolicy {

    private MpvAutoRenderPolicy() {
    }

    public static Decision evaluate(boolean renderAutomatic,
                                    boolean hardDecode,
                                    int dolbyVisionProfile,
                                    MpvAutoOutputPolicy.DolbyVisionSupport dolbyVisionSupport,
                                    boolean nativeVulkan,
                                    boolean deviceVulkan,
                                    boolean currentlyVulkan,
                                    boolean disabledForItem) {
        if (disabledForItem) return keep("item-fallback");
        if (currentlyVulkan) return keep("already-vulkan");
        if (!renderAutomatic) return keep("render-overridden");
        if (!hardDecode) return keep("software-decode");
        if (dolbyVisionProfile != 5) return keep("not-dv5");
        if (dolbyVisionSupport != MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED) {
            return keep(dolbyVisionSupport == MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED
                    ? "native-dv5-supported" : "native-dv5-unknown");
        }
        if (!nativeVulkan || !deviceVulkan) return keep("vulkan-unavailable");
        return new Decision(Action.ENABLE_VULKAN, "dv5-gpu-mapping-vulkan");
    }

    private static Decision keep(String reason) {
        return new Decision(Action.KEEP, reason);
    }

    public enum Action {
        KEEP,
        ENABLE_VULKAN
    }

    public record Decision(Action action, String reason) {
    }
}
