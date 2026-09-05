package com.fongmi.android.tv.ui.helper;

public final class DetailThemeVisibility {

    private DetailThemeVisibility() {
    }

    public static boolean showMobileThemeButton(boolean mobile, boolean fullscreen, boolean inlinePiPLayout, boolean pictureInPicture) {
        return mobile && !isPlayerOverlayActive(fullscreen, inlinePiPLayout, pictureInPicture);
    }

    public static boolean showLargeScreenThemeButton(boolean mobile, boolean fullscreen, boolean inlinePiPLayout, boolean pictureInPicture) {
        return !mobile && !isPlayerOverlayActive(fullscreen, inlinePiPLayout, pictureInPicture);
    }

    public static boolean showFusionThemeButton(boolean fusionDetailPage, boolean fullscreen, boolean pictureInPicture) {
        return fusionDetailPage && !fullscreen && !pictureInPicture;
    }

    private static boolean isPlayerOverlayActive(boolean fullscreen, boolean inlinePiPLayout, boolean pictureInPicture) {
        return fullscreen || inlinePiPLayout || pictureInPicture;
    }
}
