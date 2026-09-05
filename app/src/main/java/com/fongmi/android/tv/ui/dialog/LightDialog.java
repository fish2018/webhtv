package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
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

    public static void apply(AlertDialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        Drawable background = ContextCompat.getDrawable(dialog.getContext(), R.drawable.shape_shell_proxy_dialog);
        if (background == null) return;
        int verticalInset = (int) (dialog.getContext().getResources().getDisplayMetrics().density * 24);
        window.setBackgroundDrawable(new InsetDrawable(background, 0, verticalInset, 0, verticalInset));
        if (Util.isLeanback()) applyAlertWindow(dialog, window);
    }

    static int resolveAlertWidth(int screenWidth, int screenHeight) {
        boolean landscape = screenWidth >= screenHeight;
        float widthFactor = landscape ? 0.52f : 0.9f;
        int designWidth = landscape ? 960 : 540;
        int designHeight = landscape ? 540 : 960;
        float scale = Math.min(screenWidth / (float) designWidth, screenHeight / (float) designHeight);
        return Math.min(Math.round(screenWidth * widthFactor), Math.round(560 * scale));
    }

    static int resolveAlertListMaxHeight(int screenHeight) {
        return Math.round(screenHeight * 0.58f);
    }

    static int resolveAlertWindowMaxHeight(int screenHeight) {
        return Math.round(screenHeight * 0.82f);
    }

    static int resolveAlertWindowHeight(int currentHeight, int currentListHeight, int desiredListHeight, int maxHeight) {
        return Math.min(maxHeight, currentHeight - currentListHeight + desiredListHeight);
    }

    private static void applyAlertWindow(AlertDialog dialog, Window window) {
        Context context = dialog.getContext();
        int screenWidth = ResUtil.getScreenWidth(context);
        int screenHeight = ResUtil.getScreenHeight(context);
        int width = resolveAlertWidth(screenWidth, screenHeight);
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        ListView list = dialog.getListView();
        if (list == null) return;
        int listMaxHeight = resolveAlertListMaxHeight(screenHeight);
        int windowMaxHeight = resolveAlertWindowMaxHeight(screenHeight);
        list.post(() -> resizeAlertList(dialog, window, list, width, listMaxHeight, windowMaxHeight));
    }

    private static void resizeAlertList(AlertDialog dialog, Window window, ListView list, int windowWidth, int listMaxHeight, int windowMaxHeight) {
        if (!dialog.isShowing()) return;
        ListAdapter adapter = list.getAdapter();
        if (adapter == null || adapter.getCount() == 0) return;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(windowWidth, View.MeasureSpec.AT_MOST);
        int contentHeight = list.getListPaddingTop() + list.getListPaddingBottom();
        int measuredItems = 0;
        while (measuredItems < adapter.getCount() && contentHeight < listMaxHeight) {
            View item = adapter.getView(measuredItems, null, list);
            item.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            contentHeight += item.getMeasuredHeight();
            measuredItems++;
        }
        contentHeight += Math.max(0, measuredItems - 1) * list.getDividerHeight();
        if (measuredItems < adapter.getCount()) contentHeight = listMaxHeight;
        int listHeight = Math.min(contentHeight, listMaxHeight);
        int windowHeight = resolveAlertWindowHeight(window.getDecorView().getHeight(), list.getHeight(), listHeight, windowMaxHeight);
        ViewGroup.LayoutParams params = list.getLayoutParams();
        params.height = listHeight;
        list.setLayoutParams(params);
        window.setLayout(windowWidth, windowHeight);
    }

    public static Dialog create(Context context, CharSequence title, View content) {
        return create(context, title, content, null, null, null, null);
    }

    public static Dialog create(Context context, CharSequence title, View content, float landFactor, float portFactor, int maxDp) {
        return createInternal(context, title, content, null, null, null, null, null, null, landFactor, portFactor, maxDp, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    public static Dialog create(Context context, CharSequence title, View content, float landFactor, float portFactor, int maxDp, int heightPx) {
        return createInternal(context, title, content, null, null, null, null, null, null, landFactor, portFactor, maxDp, heightPx);
    }

    public static Dialog create(Context context, CharSequence title, View content, String positive, View.OnClickListener onPositive, String negative, View.OnClickListener onNegative) {
        return create(context, title, content, positive, onPositive, negative, onNegative, null, null);
    }

    public static Dialog create(Context context, CharSequence title, View content, String positive, View.OnClickListener onPositive, String negative, View.OnClickListener onNegative, String neutral, View.OnClickListener onNeutral) {
        return createInternal(context, title, content, positive, onPositive, negative, onNegative, neutral, onNeutral, 0.52f, 0.9f, 560, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private static Dialog createInternal(Context context, CharSequence title, View content, String positive, View.OnClickListener onPositive, String negative, View.OnClickListener onNegative, String neutral, View.OnClickListener onNeutral, float landFactor, float portFactor, int maxDp, int heightPx) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root(context, title, content, positive, listener(dialog, onPositive), negative, listener(dialog, onNegative), neutral, listener(dialog, onNeutral), heightPx > 0));
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(d -> applyWindow(dialog, context, landFactor, portFactor, maxDp, heightPx));
        return dialog;
    }

    private static View.OnClickListener listener(Dialog dialog, View.OnClickListener listener) {
        return view -> {
            if (listener != null) listener.onClick(view);
            else dialog.dismiss();
        };
    }

    private static View root(Context context, CharSequence title, View content, String positive, View.OnClickListener onPositive, String negative, View.OnClickListener onNegative, String neutral, View.OnClickListener onNeutral, boolean fillHeight) {
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
            LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, fillHeight ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT);
            if (fillHeight) contentParams.weight = 1.0f;
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

    private static void applyWindow(Dialog dialog, Context context, float landFactor, float portFactor, int maxDp, int heightPx) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(context);
        params.width = Math.min(Math.round(ResUtil.getScreenWidth(context) * (land ? landFactor : portFactor)), ResUtil.dp2px(maxDp));
        params.height = heightPx > 0 ? heightPx : WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }
}
