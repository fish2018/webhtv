package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.slider.Slider;

import java.util.Locale;

public final class SpeedSettingDialog {

    public interface FloatCallback {
        void onValue(float value);
    }

    private SpeedSettingDialog() {
    }

    public static void show(FragmentActivity activity, int titleRes, float currentValue, float minValue, float maxValue, float stepSize, FloatCallback callback) {
        if (activity == null || minValue > maxValue || stepSize <= 0) return;

        Slider slider = new Slider(activity);
        slider.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        slider.setValueFrom(minValue);
        slider.setValueTo(maxValue);
        slider.setStepSize(stepSize);
        slider.setTickVisible(false);
        slider.setLabelFormatter(value -> format(value));
        slider.setFocusable(true);
        slider.setFocusableInTouchMode(true);
        ColorStateList active = ColorStateList.valueOf(Color.parseColor("#1A73E8"));
        ColorStateList inactive = ColorStateList.valueOf(Color.parseColor("#D0D5DD"));
        slider.setTrackActiveTintList(active);
        slider.setTrackInactiveTintList(inactive);
        slider.setThumbTintList(active);
        slider.setHaloTintList(ColorStateList.valueOf(Color.parseColor("#1F1A73E8")));
        slider.setValue(snap(currentValue, minValue, maxValue, stepSize));

        FrameLayout container = new FrameLayout(activity);
        int horizontal = ResUtil.dp2px(12);
        int vertical = ResUtil.dp2px(8);
        container.setPadding(horizontal, vertical, horizontal, 0);
        container.addView(slider, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        Dialog[] holder = new Dialog[1];
        View.OnClickListener onPositive = v -> {
            if (callback != null) callback.onValue(snap(slider.getValue(), minValue, maxValue, stepSize));
            if (holder[0] != null) holder[0].dismiss();
        };

        Dialog dialog = LightDialog.create(activity, activity.getString(titleRes), container,
                activity.getString(R.string.dialog_positive), onPositive,
                activity.getString(R.string.dialog_negative), null);
        holder[0] = dialog;

        slider.setOnKeyListener((view, keyCode, event) -> {
            if (!KeyUtil.isEnterKey(event)) return false;
            onPositive.onClick(view);
            return true;
        });

        dialog.show();
        slider.post(slider::requestFocus);
    }

    private static float snap(float value, float min, float max, float step) {
        float clamped = Math.max(min, Math.min(value, max));
        float snapped = min + Math.round((clamped - min) / step) * step;
        return Math.round(Math.max(min, Math.min(snapped, max)) * 100f) / 100f;
    }

    private static String format(float value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
