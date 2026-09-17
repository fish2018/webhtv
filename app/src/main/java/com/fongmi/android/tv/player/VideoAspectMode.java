package com.fongmi.android.tv.player;

import androidx.media3.ui.AspectRatioFrameLayout;

public final class VideoAspectMode {

    public static final int ORIGINAL = 0;
    public static final int RATIO_16_9 = 1;
    public static final int RATIO_4_3 = 2;
    public static final int FILL = 3;
    public static final int ZOOM = 4;
    public static final int RATIO_21_9 = 5;
    public static final int ADAPTIVE = 6;
    public static final int CUSTOM = 7;
    public static final float DEFAULT_CUSTOM_WIDTH = 21f;
    public static final float DEFAULT_CUSTOM_HEIGHT = 9f;
    public static final float DEFAULT_CUSTOM_RATIO = DEFAULT_CUSTOM_WIDTH / DEFAULT_CUSTOM_HEIGHT;
    private static final float DEFAULT_ADAPTIVE_RATIO = 16f / 9f;
    private static final float MIN_ASPECT_RATIO = 0.2f;
    private static final float MAX_ASPECT_RATIO = 5f;
    private static final int[] DISPLAY_ORDER = {
            ORIGINAL, RATIO_16_9, RATIO_4_3, RATIO_21_9, FILL, ZOOM, ADAPTIVE, CUSTOM
    };

    private VideoAspectMode() {
    }

    public static int sanitize(int mode) {
        return mode >= ORIGINAL && mode <= CUSTOM ? mode : ORIGINAL;
    }

    public static boolean isCustom(int mode) {
        return sanitize(mode) == CUSTOM;
    }

    public static int[] displayOrder() {
        return DISPLAY_ORDER.clone();
    }

    public static int displayIndex(int mode) {
        mode = sanitize(mode);
        for (int index = 0; index < DISPLAY_ORDER.length; index++) {
            if (DISPLAY_ORDER[index] == mode) return index;
        }
        return 0;
    }

    public static int modeAtDisplayIndex(int index) {
        return index >= 0 && index < DISPLAY_ORDER.length ? DISPLAY_ORDER[index] : ORIGINAL;
    }

    public static boolean isValidRatio(float ratio) {
        return Float.isFinite(ratio) && ratio > 0f;
    }

    public static boolean isValidDimensions(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f) return false;
        float ratio = width / height;
        return ratio >= MIN_ASPECT_RATIO && ratio <= MAX_ASPECT_RATIO;
    }

    public static Spec resolve(int mode, float viewportAspectRatio, float customAspectRatio) {
        mode = sanitize(mode);
        return switch (mode) {
            case RATIO_16_9 -> new Spec(mode, AspectRatioFrameLayout.RESIZE_MODE_16_9, 16f / 9f, false);
            case RATIO_4_3 -> new Spec(mode, AspectRatioFrameLayout.RESIZE_MODE_4_3, 4f / 3f, false);
            case FILL -> new Spec(mode, AspectRatioFrameLayout.RESIZE_MODE_FILL, 0f, true);
            case ZOOM -> new Spec(mode, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 0f, false);
            case RATIO_21_9 -> new Spec(mode, AspectRatioFrameLayout.RESIZE_MODE_FIT, 21f / 9f, false);
            case ADAPTIVE -> new Spec(mode, AspectRatioFrameLayout.RESIZE_MODE_FIT, validOr(viewportAspectRatio, DEFAULT_ADAPTIVE_RATIO), false);
            case CUSTOM -> new Spec(mode, AspectRatioFrameLayout.RESIZE_MODE_FIT, isValidDimensions(customAspectRatio, 1f) ? customAspectRatio : DEFAULT_CUSTOM_RATIO, false);
            default -> new Spec(ORIGINAL, AspectRatioFrameLayout.RESIZE_MODE_FIT, 0f, false);
        };
    }

    private static float validOr(float ratio, float fallback) {
        return isValidDimensions(ratio, 1f) ? ratio : fallback;
    }

    public record Spec(int mode, int resizeMode, float targetAspectRatio, boolean stretch) {

        public boolean hasTargetAspectRatio() {
            return VideoAspectMode.isValidRatio(targetAspectRatio);
        }
    }
}
