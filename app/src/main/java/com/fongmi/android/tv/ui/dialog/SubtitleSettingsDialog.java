package com.fongmi.android.tv.ui.dialog;

import android.text.InputType;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class SubtitleSettingsDialog {

    public interface ValueCallback {
        void onValue(String value);
    }

    public interface IntCallback {
        void onValue(int value);
    }

    private SubtitleSettingsDialog() {
    }

    public static void showPreferredLanguage(FragmentActivity activity, String[] labels, String[] values, String currentValue, ValueCallback callback) {
        if (activity == null || labels == null || values == null || labels.length == 0 || values.length == 0) return;
        int checked = indexOf(values, currentValue);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.player_subtitle_language)
                .setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    if (which >= 0 && which < values.length && callback != null) callback.onValue(values[which]);
                    d.dismiss();
                })
                .show();
        LightDialog.apply(dialog);
    }

    public static void showAssrtToken(FragmentActivity activity, String currentToken, ValueCallback callback) {
        if (activity == null) return;
        TextInputEditText input = new TextInputEditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(TextUtils.isEmpty(currentToken) ? "" : currentToken);
        if (input.getText() != null) input.setSelection(input.length());

        TextInputLayout layout = new TextInputLayout(activity);
        layout.setHint(activity.getString(R.string.player_subtitle_assrt_token));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(activity, 20);
        container.setPadding(horizontal, dp(activity, 8), horizontal, 0);
        container.addView(layout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.player_subtitle_assrt_token)
                .setView(container)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, which) -> {
                    if (callback != null) callback.onValue(input.getText() == null ? "" : input.getText().toString().trim());
                })
                .show();
        LightDialog.apply(dialog);
    }

    public static void showRealtimeModel(FragmentActivity activity, String[] labels, int checked, boolean canDelete, IntCallback selection, Runnable deleteAction) {
        if (activity == null || labels == null || labels.length == 0) return;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.subtitle_realtime_model_select_title)
                .setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    if (selection != null) selection.onValue(which);
                    d.dismiss();
                });
        if (canDelete) builder.setNeutralButton(R.string.subtitle_realtime_model_delete_action, (d, which) -> showDeleteRealtimeModel(activity, deleteAction));
        AlertDialog dialog = builder.show();
        LightDialog.apply(dialog);
    }

    public static void showDeleteRealtimeModel(FragmentActivity activity, Runnable action) {
        if (activity == null) return;
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.subtitle_realtime_model_delete_title)
                .setMessage(R.string.subtitle_realtime_model_delete_message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, which) -> {
                    if (action != null) action.run();
                })
                .show();
        LightDialog.apply(dialog);
    }

    public static void showRealtimeCompatibility(FragmentActivity activity, Runnable action) {
        if (activity == null) return;
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.subtitle_realtime_pcm_title)
                .setMessage(R.string.subtitle_realtime_pcm_required)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, which) -> {
                    if (action != null) action.run();
                })
                .show();
        LightDialog.apply(dialog);
    }

    public static void showRealtimeSpeedCompatibility(FragmentActivity activity, Runnable action) {
        if (activity == null) return;
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.subtitle_realtime_speed_title)
                .setMessage(R.string.subtitle_realtime_speed_required)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, which) -> {
                    if (action != null) action.run();
                })
                .show();
        LightDialog.apply(dialog);
    }

    public static void showNumber(FragmentActivity activity, int titleRes, int currentValue, int minValue, int maxValue, IntCallback callback) {
        if (activity == null) return;
        TextInputEditText input = new TextInputEditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(clamp(currentValue, minValue, maxValue)));
        if (input.getText() != null) input.setSelection(input.length());

        TextInputLayout layout = new TextInputLayout(activity);
        layout.setHint(activity.getString(titleRes));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(activity, 20);
        container.setPadding(horizontal, dp(activity, 8), horizontal, 0);
        container.addView(layout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(titleRes)
                .setView(container)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, which) -> {
                    if (callback != null) callback.onValue(clamp(parseInt(input.getText() == null ? "" : input.getText().toString(), currentValue), minValue, maxValue));
                })
                .show();
        LightDialog.apply(dialog);
    }

    private static int indexOf(String[] values, String currentValue) {
        for (int i = 0; i < values.length; i++) if (TextUtils.equals(values[i], currentValue)) return i;
        return 0;
    }

    private static int dp(FragmentActivity activity, int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (Throwable e) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
