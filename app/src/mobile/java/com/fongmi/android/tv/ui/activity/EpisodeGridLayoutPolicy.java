package com.fongmi.android.tv.ui.activity;

final class EpisodeGridLayoutPolicy {

    private EpisodeGridLayoutPolicy() {
    }

    static int getMaxSpan(boolean landscapeLayout, boolean pad) {
        return landscapeLayout || pad ? 6 : 4;
    }

    /**
     * 原生增强回退网格的列数。长标题交给用户的“长标题列数”设置，这里只决定短标题的紧凑列数，
     * 避免设置项在回退模式下变成没反应的死开关。
     */
    static int getOriginalEnhancedFallbackSpan(int itemCount, int maxTitleLength, int userColumn) {
        if (itemCount <= 1) return 1;
        if (maxTitleLength >= 12) return Math.max(1, userColumn);
        return 3;
    }

    static int getAvailableWidth(int measuredWidth, int screenWidth, int screenHeight, int fallbackInset, boolean landscapeLayout, boolean landscapeConfiguration) {
        if (measuredWidth > 0 && landscapeLayout == landscapeConfiguration) return measuredWidth;
        int layoutWidth = landscapeLayout ? Math.max(screenWidth, screenHeight) : Math.min(screenWidth, screenHeight);
        return Math.max(1, layoutWidth - Math.max(0, fallbackInset));
    }
}
