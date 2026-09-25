package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.slider.Slider;
import com.google.android.material.textview.MaterialTextView;

public final class SliderNumberDialog {

    public interface IntCallback {
        void onValue(int value);
    }

    private SliderNumberDialog() {
    }

    public static void show(FragmentActivity activity, int titleRes, int currentValue, int minValue, int maxValue, IntCallback callback) {
        show(activity, titleRes, currentValue, minValue, maxValue, "", 0, callback);
    }

    public static void show(FragmentActivity activity, int titleRes, int currentValue, int minValue, int maxValue, String suffix, int hintRes, IntCallback callback) {
        if (activity == null || minValue > maxValue) return;
        int current = clamp(currentValue, minValue, maxValue);
        String valueSuffix = suffix == null ? "" : suffix;

        MaterialTextView valueView = new MaterialTextView(activity);
        valueView.setText(formatValue(current, valueSuffix));
        valueView.setTextColor(Color.parseColor("#202124"));
        valueView.setTextSize(28);
        valueView.setGravity(Gravity.CENTER);

        Slider slider = new Slider(activity);
        slider.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        slider.setValueFrom(minValue);
        slider.setValueTo(maxValue);
        slider.setStepSize(1);
        slider.setTickVisible(false);
        slider.setValue(current);
        slider.setFocusable(true);
        slider.setFocusableInTouchMode(true);
        ColorStateList active = ColorStateList.valueOf(Color.parseColor("#3D5AFE"));
        ColorStateList inactive = ColorStateList.valueOf(Color.parseColor("#D0D5DD"));
        slider.setTrackActiveTintList(active);
        slider.setTrackInactiveTintList(inactive);
        slider.setThumbTintList(active);
        slider.setHaloTintList(active);

        LinearLayout container = new LinearLayout(activity);
        container.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = ResUtil.dp2px(12);
        container.setPadding(horizontal, 0, horizontal, 0);
        container.addView(valueView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (hintRes != 0) {
            MaterialTextView hintView = new MaterialTextView(activity);
            hintView.setText(hintRes);
            hintView.setTextColor(Color.parseColor("#5F6368"));
            hintView.setTextSize(14);
            hintView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintParams.topMargin = ResUtil.dp2px(8);
            container.addView(hintView, hintParams);
        }
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sliderParams.topMargin = ResUtil.dp2px(8);
        container.addView(slider, sliderParams);

        int[] selected = {current};
        slider.addOnChangeListener((source, value, fromUser) -> {
            selected[0] = Math.round(value);
            valueView.setText(formatValue(selected[0], valueSuffix));
        });

        Dialog[] holder = new Dialog[1];
        View.OnClickListener onPositive = v -> {
            if (callback != null) callback.onValue(clamp(selected[0], minValue, maxValue));
            if (holder[0] != null) holder[0].dismiss();
        };

        Dialog dialog = LightDialog.create(activity, activity.getString(titleRes), container,
                activity.getString(R.string.dialog_positive), onPositive,
                activity.getString(R.string.dialog_negative), null);
        holder[0] = dialog;

        slider.setOnKeyListener((view, keyCode, event) -> {
            if (KeyUtil.isEnterKey(event)) {
                onPositive.onClick(view);
                return true;
            }
            return false;
        });

        dialog.setOnShowListener(d -> slider.requestFocus());
        dialog.show();
    }

    private static String formatValue(int value, String suffix) {
        return value + suffix;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
