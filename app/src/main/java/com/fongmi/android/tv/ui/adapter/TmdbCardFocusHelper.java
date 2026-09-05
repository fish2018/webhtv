package com.fongmi.android.tv.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;

import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.card.MaterialCardView;

final class TmdbCardFocusHelper {

    private static final int FOCUS_STROKE = 0xFFFFD166;
    private static final int FOCUS_ELEVATION_DP = 8;
    private static final int FOCUS_STROKE_DP = 3;

    private TmdbCardFocusHelper() {
    }

    interface FocusCallback {
        void onFocus(boolean focused);
    }

    static void bind(MaterialCardView card, int backgroundColor, int strokeColor) {
        bind(card, backgroundColor, strokeColor, 1);
    }

    static void bind(MaterialCardView card, int backgroundColor, int strokeColor, int strokeWidthDp) {
        bind(card, backgroundColor, strokeColor, strokeWidthDp, null);
    }

    static void bind(MaterialCardView card, int backgroundColor, int strokeColor, int strokeWidthDp, FocusCallback callback) {
        clearStateOverlay(card);
        card.setOnFocusChangeListener(null);
        apply(card, card.hasFocus(), backgroundColor, strokeColor, strokeWidthDp);
        card.setOnFocusChangeListener((view, focused) -> {
            apply(card, focused, backgroundColor, strokeColor, strokeWidthDp);
            if (callback != null) callback.onFocus(focused);
        });
    }

    private static void clearStateOverlay(MaterialCardView card) {
        card.setSelected(false);
        card.setActivated(false);
        card.setChecked(false);
        card.setForeground(null);
        card.setRippleColor(ColorStateList.valueOf(0x00000000));
        card.setStateListAnimator(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) card.setDefaultFocusHighlightEnabled(false);
    }

    private static void apply(MaterialCardView card, boolean focused, int backgroundColor, int strokeColor, int strokeWidthDp) {
        card.setCardBackgroundColor(backgroundColor);
        card.setStrokeColor(focused ? FOCUS_STROKE : strokeColor);
        card.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : strokeWidthDp));
        card.setCardElevation(ResUtil.dp2px(focused ? FOCUS_ELEVATION_DP : 0));
        card.setTranslationZ(ResUtil.dp2px(focused ? FOCUS_ELEVATION_DP : 0));
        card.setForeground(focused ? foregroundBorder(card, FOCUS_STROKE, FOCUS_STROKE_DP) : null);
        card.animate().cancel();
        card.setScaleX(1f);
        card.setScaleY(1f);
    }

    static GradientDrawable foregroundBorder(MaterialCardView card, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(card.getRadius());
        drawable.setStroke(ResUtil.dp2px(strokeWidthDp), strokeColor);
        return drawable;
    }
}
