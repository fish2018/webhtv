package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import com.google.android.material.textview.MaterialTextView;

/**
 * 禁用系统默认焦点高亮的 TextView
 * 用于 TV 端选集按钮，避免灰色遮罩覆盖自定义的白色边框
 */
public class NoHighlightTextView extends MaterialTextView {

    public NoHighlightTextView(Context context) {
        super(context);
        init();
    }

    public NoHighlightTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NoHighlightTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 在 API 26+ 上禁用默认焦点高亮
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            setDefaultFocusHighlightEnabled(false);
        }
        // 禁用状态列表动画器（可能导致灰色遮罩）
        setStateListAnimator(null);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        // 强制重绘，确保 background drawable 状态更新
        invalidate();
    }

    @Override
    public void onDrawForeground(Canvas canvas) {
        // 不绘制前景层（包括系统焦点高亮）
        // 跳过 super.onDrawForeground(canvas)
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        // 确保背景 drawable 状态同步更新
        if (getBackground() != null) {
            getBackground().setState(getDrawableState());
        }
    }
}
