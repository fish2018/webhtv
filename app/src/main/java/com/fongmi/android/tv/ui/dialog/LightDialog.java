package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

public final class LightDialog {

    private LightDialog() {
    }

    public static Dialog create(Context context, CharSequence title, View content) {
        return create(context, title, content, null, null, null, null);
    }

    public static Dialog create(Context context, CharSequence title, View content, float landFactor, float portFactor, int maxDp) {
        return createInternal(context, title, content, null, null, null, null, null, null, landFactor, portFactor, maxDp);
    }

    public static Dialog create(Context context, CharSequence title, View content, String positive, View.OnClickListener onPositive, String negative, View.OnClickListener onNegative) {
        return create(context, title, content, positive, onPositive, negative, onNegative, null, null);
    }

    public static Dialog create(Context context, CharSequence title, View content, String positive, View.OnClickListener onPositive, String negative, View.OnClickListener onNegative, String neutral, View.OnClickListener onNeutral) {
        return createInternal(context, title, content, positive, onPositive, negative, onNegative, neutral, onNeutral, 0.52f, 0.9f, 560);
    }

    private static Dialog createInternal(Context context, CharSequence title, View content, String positive, View.OnClickListener onPositive, String negative, View.OnClickListener onNegative, String neutral, View.OnClickListener onNeutral, float landFactor, float portFactor, int maxDp) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root(context, title, content, positive, listener(dialog, onPositive), negative, listener(dialog, onNegative), neutral, listener(dialog, onNeutral)));
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(d -> applyWindow(dialog, context, landFactor, portFactor, maxDp));
        return dialog;
    }

    private static View.OnClickListener listener(Dialog dialog, View.OnClickListener listener) {
        return view -> {
            if (listener != null) listener.onClick(view);
            else dialog.dismiss();
        };
    }

    private static View root(Context context, CharSequence title, View content, String positive, View.OnClickListener onPositive, String negative, View.OnClickListener onNegative, String neutral, View.OnClickListener onNeutral) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.shape_shell_proxy_dialog);
        int actionCount = (positive == null ? 0 : 1) + (negative == null ? 0 : 1) + (neutral == null ? 0 : 1);
        int vertical = ResUtil.dp2px(24);
        int horizontal = ResUtil.dp2px(actionCount >= 3 ? 18 : 24);
        root.setPadding(horizontal, vertical, horizontal, vertical);

        if (title != null) {
            MaterialTextView titleView = new MaterialTextView(context);
            titleView.setText(title);
            titleView.setTextColor(Color.parseColor("#202124"));
            titleView.setTextSize(18);
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (content != null) {
            LinearLayout.LayoutParams contentParams;
            if (content instanceof ScrollView) {
                contentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
                contentParams.weight = 1f;
            } else {
                contentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            contentParams.topMargin = title == null ? 0 : ResUtil.dp2px(16);
            root.addView(content, contentParams);
        }

        if (positive != null || negative != null || neutral != null) root.addView(actions(context, positive, onPositive, negative, onNegative, neutral, onNeutral), actionParams());
        return root;
    }

    private static LinearLayout actions(Context context, String positive, View.OnClickListener onPositive, String negative, View.OnClickListener onNegative, String neutral, View.OnClickListener onNeutral) {
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        int count = (positive == null ? 0 : 1) + (negative == null ? 0 : 1) + (neutral == null ? 0 : 1);
        boolean compact = count >= 3;
        int index = 0;
        if (neutral != null) actions.addView(button(context, neutral, false, onNeutral, compact, index++ == 0));
        if (negative != null) actions.addView(button(context, negative, false, onNegative, compact, index++ == 0));
        if (positive != null) actions.addView(button(context, positive, true, onPositive, compact, index == 0));
        return actions;
    }

    private static LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = ResUtil.dp2px(18);
        return params;
    }

    private static MaterialButton button(Context context, String text, boolean primary, View.OnClickListener listener, boolean compact, boolean first) {
        MaterialButton button = new MaterialButton(context);
        button.setAllCaps(false);
        button.setText(text);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setEllipsize(null);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(compact ? 14 : 15);
        button.setIncludeFontPadding(false);
        button.setPadding(ResUtil.dp2px(compact ? 12 : 16), 0, ResUtil.dp2px(compact ? 12 : 16), 0);
        if (compact) TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(button, 10, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        button.setMinWidth(ResUtil.dp2px(compact ? 64 : 88));
        button.setMinimumWidth(0);
        button.setMinHeight(ResUtil.dp2px(compact ? 36 : 40));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(ResUtil.dp2px(6));
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        button.setTextColor(ContextCompat.getColorStateList(context, primary ? R.color.dialog_primary_button_text : R.color.dialog_outlined_button_text));
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, primary ? R.color.dialog_primary_button_bg : R.color.dialog_outlined_button_bg));
        button.setStrokeColor(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_stroke));
        button.setStrokeWidth(primary ? 0 : ResUtil.dp2px(1));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(compact ? 36 : 40));
        params.leftMargin = first ? 0 : ResUtil.dp2px(compact ? 6 : 12);
        button.setLayoutParams(params);
        return button;
    }

    private static void applyWindow(Dialog dialog, Context context, float landFactor, float portFactor, int maxDp) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(context);
        params.width = Math.min(Math.round(ResUtil.getScreenWidth(context) * (land ? landFactor : portFactor)), ResUtil.dp2px(maxDp));
        params.height = hasScrollContent(window) ? Math.round(ResUtil.getScreenHeight(context) * 0.85f) : WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    private static boolean hasScrollContent(Window window) {
        View contentParent = window.getDecorView().findViewById(android.R.id.content);
        if (!(contentParent instanceof ViewGroup)) return false;
        View root = ((ViewGroup) contentParent).getChildAt(0);
        if (!(root instanceof ViewGroup)) return false;
        for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            if (((ViewGroup) root).getChildAt(i) instanceof ScrollView) return true;
        }
        return false;
    }
}
