package com.fongmi.android.tv.ui.dialog;

import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.VideoAspectMode;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;

public final class VideoAspectRatioDialog {

    private VideoAspectRatioDialog() {
    }

    public static void show(FragmentActivity activity, Runnable onApply) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        TextInputLayout widthLayout = inputLayout(activity, R.string.aspect_ratio_width);
        TextInputLayout heightLayout = inputLayout(activity, R.string.aspect_ratio_height);
        TextInputEditText width = (TextInputEditText) widthLayout.getEditText();
        TextInputEditText height = (TextInputEditText) heightLayout.getEditText();
        if (width == null || height == null) return;
        width.setId(View.generateViewId());
        height.setId(View.generateViewId());
        float savedWidth = PlayerSetting.getCustomAspectWidth();
        float savedHeight = PlayerSetting.getCustomAspectHeight();
        if (!VideoAspectMode.isValidDimensions(savedWidth, savedHeight)) {
            savedWidth = VideoAspectMode.DEFAULT_CUSTOM_WIDTH;
            savedHeight = VideoAspectMode.DEFAULT_CUSTOM_HEIGHT;
        }
        width.setText(format(savedWidth));
        height.setText(format(savedHeight));

        TextView separator = new TextView(activity);
        separator.setText(":");
        separator.setTextSize(22f);
        separator.setGravity(Gravity.CENTER);
        separator.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(widthLayout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(separator, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(heightLayout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), 0);
        container.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.aspect_ratio_title)
                .setMessage(R.string.aspect_ratio_hint)
                .setView(container)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(view -> {
                float widthValue = parse(width);
                float heightValue = parse(height);
                if (!VideoAspectMode.isValidDimensions(widthValue, heightValue)) {
                    widthLayout.setError(activity.getString(R.string.aspect_ratio_invalid));
                    heightLayout.setError(activity.getString(R.string.aspect_ratio_invalid));
                    return;
                }
                widthLayout.setError(null);
                heightLayout.setError(null);
                PlayerSetting.putCustomAspectRatio(widthValue, heightValue);
                if (onApply != null) onApply.run();
                dialog.dismiss();
            });
            configureFocus(width, height, negative, positive);
            width.requestFocus();
            if (width.getText() != null) width.setSelection(width.length());
        });
        dialog.show();
        LightDialog.apply(dialog);
    }

    private static void configureFocus(TextInputEditText width, TextInputEditText height, Button negative, Button positive) {
        ensureId(negative);
        ensureId(positive);
        boolean leanback = Util.isLeanback();
        for (View view : new View[]{width, height, negative, positive}) {
            view.setFocusable(true);
            if (leanback) view.setFocusableInTouchMode(true);
        }

        width.setNextFocusRightId(height.getId());
        width.setNextFocusDownId(negative.getId());
        height.setNextFocusLeftId(width.getId());
        height.setNextFocusDownId(positive.getId());
        negative.setNextFocusUpId(width.getId());
        negative.setNextFocusRightId(positive.getId());
        positive.setNextFocusUpId(height.getId());
        positive.setNextFocusLeftId(negative.getId());

        width.setOnKeyListener((view, keyCode, event) -> {
            if (!keyDown(event)) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return height.requestFocus();
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return negative.requestFocus();
            return false;
        });
        height.setOnKeyListener((view, keyCode, event) -> {
            if (!keyDown(event)) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return width.requestFocus();
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return positive.requestFocus();
            return false;
        });
        negative.setOnKeyListener((view, keyCode, event) -> {
            if (!keyDown(event)) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return width.requestFocus();
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return positive.requestFocus();
            return false;
        });
        positive.setOnKeyListener((view, keyCode, event) -> {
            if (!keyDown(event)) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return height.requestFocus();
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return negative.requestFocus();
            return false;
        });
    }

    private static void ensureId(View view) {
        if (view.getId() == View.NO_ID) view.setId(View.generateViewId());
    }

    private static boolean keyDown(KeyEvent event) {
        return event != null && event.getAction() == KeyEvent.ACTION_DOWN;
    }

    private static TextInputLayout inputLayout(FragmentActivity activity, int hintRes) {
        TextInputEditText input = new TextInputEditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        TextInputLayout layout = new TextInputLayout(activity);
        layout.setHint(activity.getString(hintRes));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private static float parse(TextInputEditText input) {
        try {
            return Float.parseFloat(input.getText() == null ? "" : input.getText().toString().trim().replace(',', '.'));
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private static String format(float value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static int dp(FragmentActivity activity, int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }
}
