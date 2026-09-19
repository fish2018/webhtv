package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public abstract class BaseGlassDialog extends DialogFragment {

    protected abstract ViewBinding getBinding();

    protected abstract void initView();

    protected void initEvent() {
    }

    protected float getWidthRatio() {
        return 0.7f;
    }

    protected boolean onMenuKey() {
        return false;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        ViewBinding binding = getBinding();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        builder.setView(binding.getRoot());
        Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (KeyUtil.isMenuKey(event)) {
                if (onMenuKey()) {
                    dismiss();
                    return true;
                }
            }
            return false;
        });
        View root = binding.getRoot().findViewById(getRootId());
        if (root != null) root.setBackground(glassBackground());
        View handle = binding.getRoot().findViewById(getHandleId());
        if (handle != null) handle.setBackground(handleBackground());
        initView();
        initEvent();
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setLayout((int) (ResUtil.getScreenWidth() * Math.min(getWidthRatio(), 0.9f)), ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.CENTER);
    }

    protected void updateWindowWidth() {
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        window.setLayout((int) (ResUtil.getScreenWidth() * Math.min(getWidthRatio(), 0.9f)), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    protected int getRootId() {
        return R.id.root;
    }

    protected int getHandleId() {
        return R.id.handle;
    }

    public static GradientDrawable glassBackground() {
        int wallColor = Setting.getWallColor();
        if (wallColor == 0) wallColor = Setting.getBuiltInWallColor(Setting.getWall());
        int r = (int) (((wallColor >> 16) & 0xFF) * 0.42);
        int g = (int) (((wallColor >> 8) & 0xFF) * 0.42);
        int b = (int) ((wallColor & 0xFF) * 0.42);
        int rgb = (r << 16) | (g << 8) | b;
        int[] colors = new int[]{0xEE000000 | rgb, 0xE6000000 | rgb, 0xDE000000 | rgb};
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(ResUtil.dp2px(22));
        drawable.setStroke(ResUtil.dp2px(1), 0x66FFFFFF);
        return drawable;
    }

    protected GradientDrawable handleBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x55FFFFFF);
        drawable.setCornerRadius(ResUtil.dp2px(2));
        return drawable;
    }

    protected void focusFirstItem(RecyclerView recycler) {
        if (recycler == null) return;
        recycler.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                recycler.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(0);
                if (vh != null) vh.itemView.requestFocus();
            }
        });
    }

    public static StateListDrawable glassItemBackground() {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_pressed}, roundRect(0x66FFFFFF, 6, 2, 0xFFFFFFFF));
        drawable.addState(new int[]{android.R.attr.state_focused}, roundRect(0x66FFFFFF, 6, 2, 0xFFFFFFFF));
        drawable.addState(new int[]{android.R.attr.state_selected}, roundRect(0x44FFFFFF, 6, 1, 0x66FFFFFF));
        drawable.addState(new int[]{}, roundRect(0x33FFFFFF, 6, 1, 0x40FFFFFF));
        return drawable;
    }

    private static GradientDrawable roundRect(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(ResUtil.dp2px(radiusDp));
        if (strokeDp > 0) drawable.setStroke(ResUtil.dp2px(strokeDp), strokeColor);
        return drawable;
    }
}
