package com.fongmi.android.tv.ui.adapter;

import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.card.MaterialCardView;

final class TmdbCardFocusHelper {

    private static final int FOCUS_STROKE = 0xFFFFD166;
    private static final int FOCUS_ELEVATION_DP = 8;
    private static final int FOCUS_STROKE_DP = 3;

    private TmdbCardFocusHelper() {
    }

    static void bind(MaterialCardView card, int backgroundColor, int strokeColor) {
        bind(card, backgroundColor, strokeColor, 1);
    }

    static void bind(MaterialCardView card, int backgroundColor, int strokeColor, int strokeWidthDp) {
        card.setOnFocusChangeListener(null);
        apply(card, card.hasFocus(), backgroundColor, strokeColor, strokeWidthDp);
        card.setOnFocusChangeListener((view, focused) -> apply(card, focused, backgroundColor, strokeColor, strokeWidthDp));
    }

    private static void apply(MaterialCardView card, boolean focused, int backgroundColor, int strokeColor, int strokeWidthDp) {
        card.setCardBackgroundColor(backgroundColor);
        card.setStrokeColor(focused ? FOCUS_STROKE : strokeColor);
        card.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : strokeWidthDp));
        card.setCardElevation(ResUtil.dp2px(focused ? FOCUS_ELEVATION_DP : 0));
        card.setTranslationZ(ResUtil.dp2px(focused ? FOCUS_ELEVATION_DP : 0));
    }
}
