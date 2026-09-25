package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.theme.ThemeTokens;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

/** Lightweight, business-free preview surface for the theme editor. */
public final class ThemePreviewView extends LinearLayout {

    private final MaterialTextView title;
    private final MaterialTextView subtitle;
    private final MaterialButton primary;
    private final MaterialButton secondary;
    private final MaterialTextView selection;
    private final MaterialTextView body;

    public ThemePreviewView(Context context) {
        this(context, null);
    }

    public ThemePreviewView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(16), dp(16), dp(16));
        setMinimumHeight(dp(190));

        title = text(18, true);
        subtitle = text(12, false);
        primary = new MaterialButton(context);
        secondary = new MaterialButton(context);
        selection = text(13, true);
        body = text(13, false);

        addView(title, params(-1, -2));
        addView(subtitle, params(-1, -2));
        addView(buttonRow(), params(-1, dp(48)));
        addView(selection, params(-1, -2));
        addView(body, params(-1, -2));
    }

    public void render(ThemeTokens tokens) {
        int radius = dp(18);
        setBackground(round(tokens.appBackground(), radius, tokens.outline()));
        title.setText("WebHTV");
        subtitle.setText(tokens.mode() + " · " + tokens.backgroundType());
        title.setTextColor(tokens.onSurface());
        subtitle.setTextColor(tokens.onSurfaceVariant());
        primary.setText("应用主题");
        primary.setTextColor(tokens.onPrimary());
        primary.setBackgroundTintList(android.content.res.ColorStateList.valueOf(tokens.primary()));
        secondary.setText("收藏");
        secondary.setTextColor(tokens.primary());
        secondary.setBackgroundTintList(android.content.res.ColorStateList.valueOf(tokens.surfaceElevated()));
        secondary.setStrokeColor(android.content.res.ColorStateList.valueOf(tokens.outline()));
        selection.setText("当前选中 · 选集 03");
        selection.setTextColor(tokens.onPrimaryContainer());
        selection.setBackground(round(tokens.primaryContainer(), dp(10), tokens.primaryContainer()));
        selection.setPadding(dp(10), dp(5), dp(10), dp(5));
        body.setText("预览正文与弱化文字会跟随主题 token 一起更新。");
        body.setTextColor(tokens.onSurfaceVariant());
    }

    private LinearLayout buttonRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        primary.setAllCaps(false);
        secondary.setAllCaps(false);
        row.addView(primary, params(0, -1, 1));
        row.addView(secondary, params(0, -1, 1));
        return row;
    }

    private MaterialTextView text(int size, boolean bold) {
        MaterialTextView view = new MaterialTextView(getContext());
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(view.getTypeface(), bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return view;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private LinearLayout.LayoutParams params(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(Math.max(1, dp(1)), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
