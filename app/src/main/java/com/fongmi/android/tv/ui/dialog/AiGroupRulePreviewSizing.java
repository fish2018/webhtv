package com.fongmi.android.tv.ui.dialog;

final class AiGroupRulePreviewSizing {

    private static final float LANDSCAPE_WIDTH_RATIO = 0.92f;
    private static final float PORTRAIT_WIDTH_RATIO = 0.94f;
    private static final float LANDSCAPE_DIALOG_HEIGHT_RATIO = 0.90f;
    private static final float PORTRAIT_DIALOG_HEIGHT_RATIO = 0.92f;

    private AiGroupRulePreviewSizing() {
    }

    static Size calculate(int screenWidth, int screenHeight, int horizontalMargin, int reservedVerticalSpace) {
        if (screenWidth <= 0 || screenHeight <= 0) return new Size(0, 0, 0);
        boolean landscape = screenWidth >= screenHeight;
        float widthRatio = landscape ? LANDSCAPE_WIDTH_RATIO : PORTRAIT_WIDTH_RATIO;
        float heightRatio = landscape ? LANDSCAPE_DIALOG_HEIGHT_RATIO : PORTRAIT_DIALOG_HEIGHT_RATIO;
        int safeWidth = Math.max(0, screenWidth - horizontalMargin * 2);
        int safeHeight = Math.max(0, screenHeight - horizontalMargin * 2);
        int width = Math.min(Math.round(screenWidth * widthRatio), safeWidth);
        int dialogHeight = Math.min(Math.round(screenHeight * heightRatio), safeHeight);
        int contentHeight = Math.max(0, dialogHeight - reservedVerticalSpace);
        return new Size(width, dialogHeight, contentHeight);
    }

    record Size(int width, int dialogHeight, int contentHeight) {
    }
}
