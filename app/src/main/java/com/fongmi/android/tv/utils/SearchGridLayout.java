package com.fongmi.android.tv.utils;

/** Pure sizing helpers for search-result grids. */
public final class SearchGridLayout {

    private SearchGridLayout() {
    }

    /**
     * Resolves a grid span count from the target card size and the available width.
     * All dimensions are pixel values; a narrow window keeps two small cards or one large card.
     */
    public static int resolveSpanCount(int maxColumns, int targetWidth, int availableWidth, int minItemWidth, int itemMargin) {
        int columns = Math.max(1, maxColumns);
        int available = Math.max(0, availableWidth);
        int targetSpan = targetWidth > 0 ? available / targetWidth : columns;
        int minimumCellWidth = Math.max(1, minItemWidth) + Math.max(0, itemMargin) * 2;
        int safeSpan = available / minimumCellWidth;
        if (safeSpan < 2) return Math.min(columns, targetSpan >= 2 ? 2 : 1);
        return Math.max(1, Math.min(columns, Math.min(targetSpan, safeSpan)));
    }

    /** Returns a positive item width for the resolved span count. */
    public static int resolveItemWidth(int resultWidth, int resultPadding, int spanCount, int itemMargin) {
        int span = Math.max(1, spanCount);
        int occupied = Math.max(0, resultPadding) + Math.max(0, itemMargin) * 2 * span;
        return Math.max(1, (Math.max(0, resultWidth) - occupied) / span);
    }
}