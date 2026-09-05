package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.web.WebHomeTarget;
import com.fongmi.android.tv.web.WebThemeManifestRollback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

public final class WebHomeThemeDialog {

    private static final int MODE_DISABLED = 0;
    private static final int MODE_ECLIPSE = 1;
    private static final int MODE_CUSTOM = 2;

    private WebHomeThemeDialog() {
    }

    public static void show(Activity activity, Runnable onChanged) {
        String[] items = {
                activity.getString(R.string.setting_disable),
                activity.getString(R.string.setting_web_home_theme_eclipse),
                activity.getString(R.string.setting_web_home_theme_custom)
        };
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
                activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.setting_web_home_theme)
                .setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(items, selectedMode(), (dialog, which) -> {
                    dialog.dismiss();
                    if (which == MODE_DISABLED) apply(false, null, onChanged);
                    else if (which == MODE_ECLIPSE) apply(true, WebHomeTarget.ECLIPSE_URL, onChanged);
                    else showCustomUrl(activity, onChanged);
                });
        if (canShowRecovery()) {
            builder.setNeutralButton(R.string.setting_web_home_theme_recovery,
                    (dialog, which) -> showRecovery(activity, onChanged));
        }
        AlertDialog alert = builder.show();
        LightDialog.apply(alert);
    }

    public static String summary(Context context) {
        if (!Setting.isWebHomeThemeEnabled()) return context.getString(R.string.setting_disable);
        String url = Setting.getWebHomeThemeUrl();
        if (WebHomeTarget.ECLIPSE_URL.equals(url)) return context.getString(R.string.setting_web_home_theme_eclipse);
        String host = host(url);
        String custom = context.getString(R.string.setting_web_home_theme_custom);
        return TextUtils.isEmpty(host) ? custom : custom + " · " + host;
    }

    private static int selectedMode() {
        if (!Setting.isWebHomeThemeEnabled()) return MODE_DISABLED;
        return WebHomeTarget.ECLIPSE_URL.equals(Setting.getWebHomeThemeUrl()) ? MODE_ECLIPSE : MODE_CUSTOM;
    }

    private static boolean canShowRecovery() {
        return Setting.isWebHomeThemeEnabled()
                && WebThemeManifestRollback.supports(Setting.getWebHomeThemeUrl());
    }

    private static void showRecovery(Activity activity, Runnable onChanged) {
        String url = Setting.getWebHomeThemeUrl();
        Task.execute(() -> {
            WebThemeManifestRollback.Action action = WebThemeManifestRollback.action(activity, url);
            App.post(() -> {
                if (!isAlive(activity) || !Setting.isWebHomeThemeEnabled()
                        || !url.equals(Setting.getWebHomeThemeUrl())) return;
                if (action == WebThemeManifestRollback.Action.NONE) {
                    Notify.show(R.string.setting_web_home_theme_recovery_unavailable);
                    return;
                }
                showRecoveryConfirmation(activity, url, action, onChanged);
            });
        });
    }

    private static void showRecoveryConfirmation(Activity activity, String url,
            WebThemeManifestRollback.Action action, Runnable onChanged) {
        int message = action == WebThemeManifestRollback.Action.ROLLBACK
                ? R.string.setting_web_home_theme_rollback_message
                : R.string.setting_web_home_theme_retry_message;
        AlertDialog alert = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.setting_web_home_theme_recovery)
                .setMessage(message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive,
                        (dialog, which) -> applyRecovery(activity, url, action, onChanged))
                .show();
        LightDialog.apply(alert);
    }

    private static void applyRecovery(Activity activity, String url,
            WebThemeManifestRollback.Action action, Runnable onChanged) {
        Task.execute(() -> {
            try {
                boolean applied = WebThemeManifestRollback.apply(activity, url, action);
                App.post(() -> {
                    if (!isAlive(activity)) return;
                    if (!applied) {
                        Notify.show(R.string.setting_web_home_theme_recovery_unavailable);
                        return;
                    }
                    Notify.show(R.string.setting_web_home_theme_recovery_success);
                    if (onChanged != null) onChanged.run();
                    RefreshEvent.home();
                });
            } catch (IOException e) {
                App.post(() -> {
                    if (isAlive(activity)) Notify.show(R.string.setting_web_home_theme_recovery_failed);
                });
            }
        });
    }

    private static boolean isAlive(Activity activity) {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void showCustomUrl(Activity activity, Runnable onChanged) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(R.string.setting_web_home_theme_url_hint);
        String current = Setting.getWebHomeThemeUrl();
        if (WebHomeTarget.ECLIPSE_URL.equals(current)) current = "";
        input.setText(current);
        input.setSelection(input.length());

        FrameLayout container = new FrameLayout(activity);
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        container.setPadding(padding, 0, padding, 0);
        container.addView(input, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog alert = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.setting_web_home_theme_custom)
                .setView(container)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
        alert.setOnShowListener(ignored -> alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String url = input.getText() == null ? "" : input.getText().toString().trim();
            if (!WebHomeTarget.isSafeThemeUrl(url) || !isRemoteUrl(url)) {
                Notify.show(R.string.setting_web_home_theme_invalid_url);
                return;
            }
            if (requiresRemoteConfirmation(Setting.getWebHomeThemeTrustedUrl(), url)) {
                showRemoteConfirmation(activity, url, onChanged, alert);
            } else {
                apply(true, url, onChanged);
                alert.dismiss();
            }
        }));
        alert.show();
        LightDialog.apply(alert);
        input.requestFocus();
    }

    private static boolean isRemoteUrl(String url) {
        return url.regionMatches(true, 0, "https://", 0, 8);
    }

    static boolean requiresRemoteConfirmation(String trustedUrl, String nextUrl) {
        String current = trustedUrl == null ? "" : trustedUrl.trim();
        String next = nextUrl == null ? "" : nextUrl.trim();
        return !current.equals(next);
    }

    private static void showRemoteConfirmation(Activity activity, String url, Runnable onChanged, AlertDialog editor) {
        AlertDialog warning = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.setting_web_home_theme_remote_warning_title)
                .setMessage(activity.getString(R.string.setting_web_home_theme_remote_warning_message, host(url)))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    Setting.putWebHomeThemeTrustedUrl(url);
                    apply(true, url, onChanged);
                    editor.dismiss();
                })
                .show();
        LightDialog.apply(warning);
    }

    private static void apply(boolean enabled, String url, Runnable onChanged) {
        Setting.putWebHomeThemeEnabled(enabled);
        if (url != null) Setting.putWebHomeThemeUrl(url);
        if (onChanged != null) onChanged.run();
        RefreshEvent.home();
    }

    private static String host(String url) {
        try {
            return Uri.parse(url).getHost();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
