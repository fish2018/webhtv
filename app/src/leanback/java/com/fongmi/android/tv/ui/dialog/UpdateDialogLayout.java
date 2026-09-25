package com.fongmi.android.tv.ui.dialog;

final class UpdateDialogLayout {

    private static final float SCREEN_HEIGHT_RATIO = 0.42f;
    private static final float SCREEN_WIDTH_RATIO = 0.84f;

    private UpdateDialogLayout() {
    }

    static int calculateDialogWidth(int screenWidth, int maxWidth, int horizontalMargin) {
        int safeScreenWidth = Math.max(1, screenWidth);
        int safeMargin = Math.min(Math.max(0, horizontalMargin), safeScreenWidth / 12);
        int width = Math.min(Math.max(1, maxWidth), (int) (safeScreenWidth * SCREEN_WIDTH_RATIO));
        return Math.max(1, Math.min(width, safeScreenWidth - safeMargin));
    }

    static int calculateVerticalMargin(int availableHeight, int preferredMargin) {
        int safeHeight = Math.max(1, availableHeight);
        return Math.min(Math.max(0, preferredMargin), safeHeight / 10);
    }

    static int resolveAvailableHeight(int screenHeight, int contentHeight, int visibleHeight) {
        int availableHeight = Math.max(1, screenHeight);
        if (contentHeight > 0) availableHeight = Math.min(availableHeight, contentHeight);
        if (visibleHeight > 0) availableHeight = Math.min(availableHeight, visibleHeight);
        return availableHeight;
    }

    static int calculatePreferredScrollHeight(int screenHeight, int minHeight, int maxHeight) {
        return Math.max(minHeight, Math.min(maxHeight, (int) (screenHeight * SCREEN_HEIGHT_RATIO)));
    }

    static int fitScrollHeight(int preferredHeight, int availableHeight, int chromeHeight, int verticalMargin) {
        int maxDialogHeight = Math.min(availableHeight, Math.max(chromeHeight + 1, availableHeight - Math.max(0, verticalMargin)));
        return Math.max(1, Math.min(preferredHeight, maxDialogHeight - chromeHeight));
    }

    static boolean hasProgressPanelHeightChanged(int previousHeight, int currentHeight) {
        return previousHeight != currentHeight;
    }
}
