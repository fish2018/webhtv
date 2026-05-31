package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.animation.Animation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public class CustomTitleView extends MaterialTextView {

    private Listener listener;
    private Animation flicker;
    private boolean coolDown;
    private boolean longPress;

    private Site getHome() {
        return VodConfig.get().getHome();
    }

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
            listener.onReloadConfig();
            return true;
        });
    }

    private boolean hasEvent(KeyEvent event) {
        return !getHome().isEmpty() && (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) || KeyUtil.isEnterKey(event) || (KeyUtil.isUpKey(event) && !coolDown));
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
        if (KeyUtil.isEnterKey(event)) onEnter(event);
        else if (KeyUtil.isActionDown(event) && KeyUtil.isUpKey(event)) onKeyUp();
        else if (KeyUtil.isActionDown(event) && KeyUtil.isLeftKey(event)) listener.setSite(getSite(false));
        else if (KeyUtil.isActionDown(event) && KeyUtil.isRightKey(event)) listener.setSite(getSite(true));
    }

    private void onEnter(KeyEvent event) {
        if (KeyUtil.isActionDown(event) && (event.isLongPress() || event.getRepeatCount() > 0)) {
            longPress = true;
            listener.onReloadConfig();
        } else if (KeyUtil.isActionUp(event)) {
            if (!longPress) listener.showDialog();
            longPress = false;
        }
    }

    private void onKeyUp() {
        App.post(() -> coolDown = false, 3000);
        listener.onRefresh();
        coolDown = true;
    }

    private Site getSite(boolean next) {
        List<Site> items = getSites();
        if (items.isEmpty()) return new Site();
        int position = items.indexOf(getHome());
        if (position < 0) position = 0;
        if (next) position = (position + 1) % items.size();
        else position = (position - 1 + items.size()) % items.size();
        return items.get(position);
    }

    private List<Site> getSites() {
        return VodConfig.get().getSites().stream().filter(site -> !site.isHide()).toList();
    }

    public interface Listener extends SiteListener {

        void showDialog();

        void onRefresh();

        void onReloadConfig();
    }
}
