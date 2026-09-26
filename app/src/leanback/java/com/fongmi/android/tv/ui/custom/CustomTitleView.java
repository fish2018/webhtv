package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.animation.Animation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

public class CustomTitleView extends MaterialTextView {

    private Listener listener;
    private Animation flicker;

    public CustomTitleView(@NonNull Context context) {
        super(context);
    }

    public CustomTitleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        flicker = ResUtil.getAnim(R.anim.flicker);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        setOnClickListener(v -> listener.showDialog());
        setOnLongClickListener(v -> {
            if (listener != null) listener.onRefresh();
            return true;
        });
    }

    private boolean hasEvent(KeyEvent event) {
        return KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) startAnimation(flicker);
        else clearAnimation();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!hasEvent(event)) return super.dispatchKeyEvent(event);
        onKeyDown(event);
        return true;
    }

    private void onKeyDown(KeyEvent event) {
        if (KeyUtil.isActionDown(event) && KeyUtil.isLeftKey(event)) listener.onTitleLeft();
        else if (KeyUtil.isActionDown(event) && KeyUtil.isRightKey(event)) listener.onTitleRight();
    }

    public interface Listener extends SiteListener {

        void showDialog();

        void onRefresh();

        void reloadConfig();

        void onTitleLeft();

        void onTitleRight();
    }
}
