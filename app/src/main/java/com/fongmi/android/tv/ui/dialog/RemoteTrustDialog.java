package com.fongmi.android.tv.ui.dialog;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogRemoteTrustConfirmBinding;
import com.fongmi.android.tv.databinding.DialogRemoteTrustAddDeviceBinding;
import com.fongmi.android.tv.databinding.DialogRemoteTrustQrBinding;
import com.fongmi.android.tv.databinding.DialogRemoteTrustTextCommandBinding;
import com.fongmi.android.tv.remote.RemoteAgent;
import com.fongmi.android.tv.remote.RemoteAgentService;
import com.fongmi.android.tv.remote.RemoteLog;
import com.fongmi.android.tv.remote.RemoteClient;
import com.fongmi.android.tv.remote.RemoteConfigSiteParser;
import com.fongmi.android.tv.remote.RemoteModels.BindCodeResponse;
import com.fongmi.android.tv.remote.RemoteModels.ClaimResponse;
import com.fongmi.android.tv.remote.RemoteModels.CommandDetailResponse;
import com.fongmi.android.tv.remote.RemoteModels.CommandResponse;
import com.fongmi.android.tv.remote.RemoteModels.DevicesResponse;
import com.fongmi.android.tv.remote.RemoteModels.RemoteBindGrant;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCapabilities;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommand;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommandResult;
import com.fongmi.android.tv.remote.RemoteModels.RemoteDevice;
import com.fongmi.android.tv.remote.RemoteModels.RemoteGroup;
import com.fongmi.android.tv.remote.RemoteModels.RemoteProfile;
import com.fongmi.android.tv.remote.RemoteModels.ServerCapabilities;
import com.fongmi.android.tv.remote.RemoteStore;
import com.fongmi.android.tv.remote.RemoteTokens;
import com.fongmi.android.tv.bean.SyncOptions;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.lang.ref.WeakReference;

import okhttp3.FormBody;
import okhttp3.Request;

public final class RemoteTrustDialog {

    private static final int PAGE_DEVICES = 0;
    private static final int PAGE_DETAIL = 1;
    private static final int PAGE_SETTINGS = 2;
    private static final long DETECT_RETRY_MS = 5_000L;
    private static final long DEVICE_REFRESH_RETRY_MS = 3_000L;
    private static final int DEVICE_REFRESH_RETRY_MAX = 8;
    private static final long DEVICE_STATUS_REFRESH_MS = 15_000L;
    private static final long DEVICE_ONLINE_STALE_MS = 60_000L;
    private static final long BIND_CODE_FALLBACK_TTL_MS = 10 * 60 * 1000L;
    private static final long BIND_CODE_REFRESH_SKEW_MS = 45 * 1000L;
    private static final long BIND_CODE_REFRESH_MIN_MS = 15 * 1000L;
    private static final int HOME_STATUS_NONE = 0;
    private static final int HOME_STATUS_SETTING = 1;
    private static final int HOME_STATUS_SUCCESS = 2;
    private static final int HOME_STATUS_FAILED = 3;
    private static final Object REMOTE_SYNC_LOCK = new Object();
    private static final int REMOTE_SYNC_STEPS = 5;
    private static WeakReference<FragmentActivity> scanActivity;
    private static WeakReference<Binding> scanBinding;
    private static WeakReference<FragmentActivity> activeActivity;
    private static WeakReference<Binding> activeBinding;
    private static RemoteSyncUiState remoteSyncState = new RemoteSyncUiState();

    private RemoteTrustDialog() {
    }

    public static void show(Fragment fragment, Runnable callback) {
        show(fragment.requireActivity(), callback);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        Binding binding = build(activity);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setView(binding.root)
                .create();
        binding.dialog = dialog;
        binding.callback = callback;
        activeActivity = new WeakReference<>(activity);
        activeBinding = new WeakReference<>(binding);
        binding.detectRetry = () -> {
            if (binding.dialog == null || !binding.dialog.isShowing()) return;
            RemoteProfile profile = currentProfile(binding);
            if (profile == null || !profile.enabled) return;
            if (binding.busy || binding.detectingService) {
                scheduleDetectRetry(binding);
                return;
            }
            binding.autoDetectStarted = false;
            detectService(activity, binding, true);
        };
        binding.deviceRefreshRetry = () -> retryRefreshDevices(activity, binding);
        binding.deviceStatusRefresh = () -> refreshDeviceStatus(activity, binding);
        binding.bindCodeRefresh = () -> refreshBindCodeIfNeeded(activity, binding);
        render(activity, binding);
        dialog.setOnShowListener(d -> {
            configureWindow(activity, dialog);
            binding.close.setOnClickListener(v -> dialog.dismiss());
            binding.bindCodeButton.setOnClickListener(v -> copyCode(activity, binding));
            binding.enableToggle.setOnClickListener(v -> toggleEnabled(activity, binding));
            binding.statusButton.setOnClickListener(v -> {
                binding.statusExpanded = !binding.statusExpanded;
                render(activity, binding);
            });
            binding.addDeviceButton.setOnClickListener(v -> {
                if (currentProfile(binding) == null) {
                    binding.page = PAGE_SETTINGS;
                    render(activity, binding);
                    return;
                }
                showAddDeviceDialog(activity, binding);
            });
            binding.serviceButton.setOnClickListener(v -> {
                binding.serverEditing = currentProfile(binding) == null;
                binding.page = binding.page == PAGE_SETTINGS ? PAGE_DEVICES : PAGE_SETTINGS;
                render(activity, binding);
            });
            binding.settingsBackButton.setOnClickListener(v -> {
                hideKeyboard(activity, binding.server);
                resetServerInput(binding);
                binding.serverEditing = false;
                binding.page = PAGE_DEVICES;
                render(activity, binding);
            });
            bindServerInput(activity, binding);
            ensureDeviceStatusRefresh(activity, binding);
        });
        dialog.setOnDismissListener(d -> {
            if (binding.serverQrDialog != null && binding.serverQrDialog.isShowing()) binding.serverQrDialog.dismiss();
            App.removeCallbacks(binding.detectRetry, binding.deviceRefreshRetry, binding.deviceStatusRefresh, binding.bindCodeRefresh);
            clearScanTarget(binding);
            clearActive(binding);
        });
        showModal(dialog);
    }

    public static void onRelaySetupSaved(String serverUrl) {
        FragmentActivity activity = activeActivity == null ? null : activeActivity.get();
        Binding binding = activeBinding == null ? null : activeBinding.get();
        if (activity == null || binding == null || TextUtils.isEmpty(serverUrl)) return;
        App.post(() -> applyRelaySetupSaved(activity, binding, serverUrl));
    }

    public static void onScanResult(String address) {
        FragmentActivity activity = scanActivity == null ? null : scanActivity.get();
        Binding binding = scanBinding == null ? null : scanBinding.get();
        if (activity == null || binding == null || TextUtils.isEmpty(address)) return;
        clearScanTarget(binding);
        if (isRemoteTrustSetupUrl(address)) {
            showSendServerUrlDialog(activity, binding, address);
            return;
        }
        binding.server.setText(address);
        binding.page = PAGE_SETTINGS;
        binding.serverEditing = true;
        render(activity, binding);
        Notify.show(R.string.remote_trust_scan_filled);
    }

    private static Binding build(Context context) {
        Binding binding = new Binding();
        binding.root = new LinearLayoutCompat(context);
        binding.root.setOrientation(LinearLayoutCompat.VERTICAL);
        binding.root.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        binding.root.setBackground(background(context, "#FFFFFF", Color.TRANSPARENT, 16));

        LinearLayoutCompat header = row(context);
        MaterialTextView title = text(context, context.getString(R.string.setting_remote_trust), 18, "#202124", true);
        header.addView(title);
        binding.bindCodeButton = pillButton(context, context.getString(R.string.remote_trust_bind_code_empty));
        LinearLayoutCompat.LayoutParams codeParams = new LinearLayoutCompat.LayoutParams(0, dp(context, 34), 1);
        codeParams.setMarginStart(dp(context, 8));
        header.addView(binding.bindCodeButton, codeParams);
        binding.enableToggle = compactButton(context, R.string.setting_enable);
        LinearLayoutCompat.LayoutParams enableParams = new LinearLayoutCompat.LayoutParams(dp(context, 56), dp(context, 34));
        enableParams.setMarginStart(dp(context, 8));
        header.addView(binding.enableToggle, enableParams);
        binding.close = closeButton(context);
        LinearLayoutCompat.LayoutParams closeParams = new LinearLayoutCompat.LayoutParams(dp(context, 34), dp(context, 34));
        closeParams.setMarginStart(dp(context, 6));
        header.addView(binding.close, closeParams);
        binding.root.addView(header, matchWrap());

        binding.summary = text(context, "", 13, "#5F6368", false);
        binding.summary.setPadding(0, dp(context, 2), 0, dp(context, 6));
        binding.root.addView(binding.summary, matchWrap());

        binding.toolbar = row(context);
        binding.statusButton = statusButton(context, context.getString(R.string.remote_trust_status_unbound));
        binding.toolbar.addView(binding.statusButton, weight());
        binding.addDeviceButton = outline(context, context.getString(R.string.remote_trust_add_short));
        binding.toolbar.addView(binding.addDeviceButton, fixed(context, 54, 34));
        binding.serviceButton = iconButton(context, R.drawable.ic_remote_settings, context.getString(R.string.remote_trust_service_entry));
        binding.toolbar.addView(binding.serviceButton, fixed(context, 38, 34));
        binding.settingsBackButton = outline(context, context.getString(R.string.remote_trust_back_devices));
        binding.settingsBackButton.setTextSize(12);
        binding.settingsBackButton.setVisibility(View.GONE);
        binding.toolbar.addView(binding.settingsBackButton, fixed(context, 58, 34));
        binding.root.addView(binding.toolbar, topMargin(matchWrap(), 6));

        binding.scroll = new NestedScrollView(context);
        binding.scroll.setFillViewport(false);

        binding.content = new LinearLayoutCompat(context);
        binding.content.setOrientation(LinearLayoutCompat.VERTICAL);
        binding.scroll.addView(binding.content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        binding.root.addView(binding.scroll, topMargin(matchWrap(), 10));

        binding.serverLayout = (TextInputLayout) LayoutInflater.from(context).inflate(R.layout.view_remote_trust_server_input, binding.content, false);
        binding.server = binding.serverLayout.findViewById(R.id.server);
        setupEditableText(binding.server, false);
        binding.enabled = check(context, R.string.remote_trust_enable);
        binding.enabled.setChecked(true);
        binding.keepOnline = check(context, R.string.remote_trust_keep_online);
        binding.keepOnline.setChecked(true);
        return binding;
    }

    private static void configureWindow(Context context, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(context) * (ResUtil.isLand(context) ? 0.72f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private static void configureSmallModalWindow(Context context, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(context);
        int width = Math.min(Math.round(ResUtil.getScreenWidth(context) * (land ? 0.58f : 0.92f)), dp(context, 560));
        params.width = Math.max(width, dp(context, 320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private static AlertDialog showModal(AlertDialog dialog) {
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        return dialog;
    }

    private static void render(Context context, Binding binding) {
        initFields(binding);
        binding.actions.clear();
        binding.content.removeAllViews();
        binding.summary.setText(currentSummary(context, binding) + (Setting.hasFileAccess() ? "" : "\n" + context.getString(R.string.remote_trust_file_permission_hint)));
        updateHeader(context, binding);
        if (binding.page == PAGE_SETTINGS) renderSettings(context, binding);
        else if (binding.page == PAGE_DETAIL) renderDeviceDetail(context, binding);
        else renderDevices(context, binding);
        setBusy(binding, binding.busy);
        ensureAuto(activityOf(context), binding);
        ensureDeviceStatusRefresh(activityOf(context), binding);
        if (binding.callback != null) binding.callback.run();
    }

    private static void initFields(Binding binding) {
        if (binding.initialized) return;
        binding.initialized = true;
        RemoteProfile profile = RemoteStore.firstProfile();
        if (profile == null) {
            binding.enabled.setChecked(true);
            return;
        }
        binding.server.setText(TextUtils.isEmpty(profile.serverUrl) ? profile.serverOrigin : profile.serverUrl);
        binding.enabled.setChecked(profile.enabled);
        binding.keepOnline.setChecked(true);
        if (profile.enabled && !profile.keepOnline) {
            profile.keepOnline = true;
            RemoteStore.upsertProfile(profile);
            RemoteAgent.get().start();
        }
    }

    private static void updateHeader(Context context, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        boolean enabled = profile == null ? binding.enabled.isChecked() : profile.enabled;
        binding.enableToggle.setText(enabled ? R.string.setting_disable : R.string.setting_enable);
        binding.enabled.setChecked(enabled);
        binding.bindCodeButton.setText(bindCodeText(context, binding, profile));
        binding.toolbar.setVisibility(binding.page == PAGE_DETAIL ? View.GONE : View.VISIBLE);
        boolean settings = binding.page == PAGE_SETTINGS;
        binding.addDeviceButton.setVisibility(settings ? View.GONE : View.VISIBLE);
        binding.serviceButton.setVisibility(settings ? View.GONE : View.VISIBLE);
        binding.settingsBackButton.setVisibility(settings ? View.VISIBLE : View.GONE);
        binding.addDeviceButton.setEnabled(profile != null);
        binding.serviceButton.setEnabled(true);
        binding.settingsBackButton.setEnabled(true);
        String status = statusText(context, binding, profile);
        binding.statusButton.setText(status);
        applyStatusStyle(context, binding.statusButton, profile, status);
    }

    private static String bindCodeText(Context context, Binding binding, RemoteProfile profile) {
        if (profile == null) return context.getString(R.string.remote_trust_bind_code_unavailable);
        if (binding.creatingBindCode) return context.getString(R.string.remote_trust_bind_code_loading);
        if (!hasFreshBindCode(binding)) return context.getString(R.string.remote_trust_bind_code_empty);
        return context.getString(R.string.remote_trust_bind_code_inline, binding.bindCode);
    }

    private static String statusText(Context context, Binding binding, RemoteProfile profile) {
        if (profile == null) return context.getString(R.string.remote_trust_status_unbound);
        if (!profile.enabled) return context.getString(R.string.setting_disable);
        if (binding.detectingService) return context.getString(R.string.remote_trust_detect_service);
        if (!TextUtils.isEmpty(binding.serviceStateText)) return binding.serviceStateText;
        return context.getString(R.string.remote_trust_service_unchecked);
    }

    private static String currentSummary(Context context, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) return context.getString(R.string.remote_trust_status_unbound);
        return currentStatusSummary(context, profile);
    }

    private static void applyStatusStyle(Context context, MaterialButton button, RemoteProfile profile, String state) {
        int bg = Color.parseColor("#F1F3F4");
        int fg = Color.parseColor("#5F6368");
        int stroke = Color.parseColor("#DADCE0");
        if (profile != null && profile.enabled && TextUtils.equals(state, context.getString(R.string.remote_trust_service_ok))) {
            bg = Color.parseColor("#E6F4EA");
            fg = Color.parseColor("#137333");
            stroke = Color.parseColor("#CEEAD6");
        } else if (profile != null && profile.enabled && TextUtils.equals(state, context.getString(R.string.remote_trust_service_error))) {
            bg = Color.parseColor("#FCE8E6");
            fg = Color.parseColor("#B3261E");
            stroke = Color.parseColor("#F2B8B5");
        } else if (profile != null && profile.enabled) {
            bg = Color.parseColor("#E8F0FE");
            fg = Color.parseColor("#174EA6");
            stroke = Color.parseColor("#D2E3FC");
        }
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setTextColor(fg);
        button.setStrokeColor(ColorStateList.valueOf(stroke));
    }

    private static void ensureAuto(FragmentActivity activity, Binding binding) {
        if (activity == null || binding.busy) return;
        RemoteProfile profile = currentProfile(binding);
        if (profile == null || !profile.enabled) return;
        if (!TextUtils.isEmpty(binding.bindCode) && !hasFreshBindCode(binding)) {
            clearBindCode(binding);
            binding.autoBindAttempted = false;
        }
        if (TextUtils.isEmpty(binding.bindCode) && !binding.autoBindAttempted) {
            createBindCode(activity, binding, false, true);
            return;
        }
        scheduleBindCodeRefresh(binding);
        if (!binding.autoDetected && !binding.autoDetectStarted) detectService(activity, binding, true);
    }

    private static FragmentActivity activityOf(Context context) {
        return context instanceof FragmentActivity ? (FragmentActivity) context : null;
    }

    private static void toggleEnabled(FragmentActivity activity, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        boolean enabled = profile == null ? binding.enabled.isChecked() : profile.enabled;
        enabled = !enabled;
        binding.enabled.setChecked(enabled);
        if (profile != null) {
            profile.enabled = enabled;
            profile.keepOnline = true;
            RemoteStore.upsertProfile(profile);
            if (!enabled) clearRemoteCache(binding);
            if (enabled) RemoteAgent.get().start();
            else RemoteAgent.get().stop();
        }
        render(activity, binding);
    }

    private static void bindServerInput(FragmentActivity activity, Binding binding) {
        binding.server.setSingleLine(true);
        binding.server.setImeOptions(EditorInfo.IME_ACTION_DONE);
        binding.server.setOnEditorActionListener((view, actionId, event) -> {
            boolean done = actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (!done) return false;
            hideKeyboard(activity, binding.server);
            binding.server.clearFocus();
            saveServerSettings(activity, binding);
            return true;
        });
        binding.server.setOnClickListener(v -> showKeyboard(activity, binding.server));
    }

    private static void startScan(FragmentActivity activity, Binding binding) {
        try {
            Class<?> clazz = Class.forName("com.fongmi.android.tv.ui.activity.ScanActivity");
            scanActivity = new WeakReference<>(activity);
            scanBinding = new WeakReference<>(binding);
            activity.startActivity(new Intent(activity, clazz));
        } catch (Throwable e) {
            clearScanTarget(binding);
            Notify.show(R.string.remote_trust_scan_unavailable);
        }
    }

    private static boolean isRemoteTrustSetupUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            return "/remote/trust/setup".equals(uri.getPath());
        } catch (Throwable e) {
            return false;
        }
    }

    private static void showSendServerUrlDialog(FragmentActivity activity, Binding binding, String setupUrl) {
        TextInputEditText input = input(activity, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, true);
        input.setText(currentServerText(binding));
        LinearLayoutCompat root = dialogRoot(activity);
        root.addView(inputLayout(activity, R.string.remote_trust_server_url, input), matchWrap());
        MaterialTextView hint = text(activity, activity.getString(R.string.remote_trust_send_server_hint), 12, "#5F6368", false);
        hint.setPadding(0, dp(activity, 8), 0, 0);
        root.addView(hint, matchWrap());
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.remote_trust_send_server_title)
                .setView(root)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.remote_trust_send_server_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String serverUrl = textOf(input);
            if (TextUtils.isEmpty(RemoteTokens.normalizeOrigin(serverUrl))) {
                Notify.show(R.string.remote_trust_server_required);
                return;
            }
            dialog.dismiss();
            sendServerUrlToRemoteSetup(activity, setupUrl, serverUrl);
        }));
        showModal(dialog);
    }

    private static void sendServerUrlToRemoteSetup(FragmentActivity activity, String setupUrl, String serverUrl) {
        setWindowBusy(activity, true);
        Task.execute(() -> {
            try {
                FormBody body = new FormBody.Builder().add("serverUrl", serverUrl).build();
                Request request = new Request.Builder().url(setupUrl).post(body).build();
                try (okhttp3.Response response = OkHttp.client(5000).newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IllegalStateException("HTTP " + response.code());
                }
                App.post(() -> {
                    setWindowBusy(activity, false);
                    Notify.show(R.string.remote_trust_send_server_done);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    setWindowBusy(activity, false);
                    Notify.show(activity.getString(R.string.remote_trust_send_server_failed, conciseError(activity, e)));
                });
            }
        });
    }

    private static void setWindowBusy(FragmentActivity activity, boolean busy) {
        Window window = activity == null ? null : activity.getWindow();
        if (window == null) return;
        if (busy) window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        else window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private static void clearScanTarget(Binding binding) {
        Binding current = scanBinding == null ? null : scanBinding.get();
        if (current != null && current != binding) return;
        scanActivity = null;
        scanBinding = null;
    }

    private static void clearActive(Binding binding) {
        Binding current = activeBinding == null ? null : activeBinding.get();
        if (current != null && current != binding) return;
        activeActivity = null;
        activeBinding = null;
    }

    private static void applyRelaySetupSaved(FragmentActivity activity, Binding binding, String serverUrl) {
        String origin = RemoteTokens.normalizeOrigin(serverUrl);
        if (TextUtils.isEmpty(origin)) return;
        RemoteProfile profile = RemoteStore.getProfileByOrigin(origin);
        if (profile == null) return;
        if (binding.serverQrDialog != null && binding.serverQrDialog.isShowing()) binding.serverQrDialog.dismiss();
        binding.serverQrDialog = null;
        hideKeyboard(activity, binding.server);
        binding.server.setText(TextUtils.isEmpty(profile.serverUrl) ? profile.serverOrigin : profile.serverUrl);
        binding.enabled.setChecked(profile.enabled);
        binding.keepOnline.setChecked(true);
        binding.serverEditing = false;
        binding.page = PAGE_SETTINGS;
        binding.serviceStateText = "";
        binding.serviceDetailText = "";
        binding.diagnostics = "";
        binding.autoBindAttempted = false;
        binding.creatingBindCode = false;
        clearBindCode(binding);
        resetDetect(binding);
        render(activity, binding);
    }

    private static void hideKeyboard(Context context, View view) {
        InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null && view != null) manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private static void showKeyboard(Context context, TextInputEditText input) {
        if (input == null) return;
        input.requestFocusFromTouch();
        input.postDelayed(() -> {
            InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 80);
    }

    private static void setupEditableText(TextInputEditText input, boolean multiline) {
        input.setSelectAllOnFocus(false);
        input.setHorizontallyScrolling(true);
        input.setHorizontalScrollBarEnabled(true);
        input.setVerticalScrollBarEnabled(multiline);
        input.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.post(() -> disallowParentIntercept(view, false));
                if (action == MotionEvent.ACTION_UP) showKeyboard(view.getContext(), input);
            } else {
                disallowParentIntercept(view, true);
            }
            return false;
        });
    }

    private static void disallowParentIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    private static void renderDevices(Context context, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        if (binding.statusExpanded) binding.content.addView(statusDetailPanel(context, profile, binding), matchWrap());
        binding.content.addView(sectionTitle(context, R.string.remote_trust_device_list), topMargin(matchWrap(), binding.statusExpanded ? 12 : 0));

        List<DeviceRow> rows = deviceRows(profile);
        if (rows.isEmpty()) {
            binding.content.addView(emptyPanel(context, context.getString(hasGroups(profile) ? R.string.remote_trust_wait_devices : R.string.remote_trust_no_devices)), topMargin(matchWrap(), 8));
            return;
        }
        for (DeviceRow row : rows) {
            MaterialButton item = deviceButton(context, deviceText(context, profile, row.device), row.device);
            bindAction(binding, item);
            item.setOnClickListener(v -> {
                binding.selectedGroupId = row.group.groupId;
                binding.selectedDeviceId = row.device.deviceId;
                binding.lastResult = "";
                binding.page = PAGE_DETAIL;
                render(context, binding);
            });
            binding.content.addView(item, topMargin(matchWrap(), 8));
        }
    }

    private static void renderDeviceDetail(Context context, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        DeviceRow row = selectedRow(profile, binding);
        if (row == null) {
            binding.page = PAGE_DEVICES;
            renderDevices(context, binding);
            return;
        }
        String syncResult = remoteSyncResultFor(profile, row);
        if (!TextUtils.isEmpty(syncResult)) binding.lastResult = syncResult;
        LinearLayoutCompat top = row(context);
        MaterialButton deviceStatus = statusButton(context, deviceName(row.device) + " · " + deviceState(context, row.device) + " · " + deviceRole(context, profile, row.device));
        applyDeviceStyle(context, deviceStatus, isDeviceOnline(row.device));
        deviceStatus.setOnClickListener(v -> {
            binding.deviceStatusExpanded = !binding.deviceStatusExpanded;
            render(context, binding);
        });
        bindAction(binding, deviceStatus);
        top.addView(deviceStatus, weight());
        MaterialButton back = smallOutlineAction(binding, context, R.string.remote_trust_back_devices);
        back.setOnClickListener(v -> {
            binding.deviceStatusExpanded = false;
            binding.page = PAGE_DEVICES;
            render(context, binding);
        });
        top.addView(back, fixed(context, 58, 34));
        binding.content.addView(top, matchWrap());

        if (binding.deviceStatusExpanded) {
            binding.content.addView(panel(context, deviceDetailText(context, profile, row.group, row.device)), topMargin(matchWrap(), 8));
        }

        LinearLayoutCompat row1 = row(context);
        MaterialButton search = primaryAction(binding, context, R.string.remote_trust_action_search);
        MaterialButton push = tonalAction(binding, context, R.string.remote_trust_action_push);
        search.setOnClickListener(v -> showTextCommandDialog((FragmentActivity) context, binding, R.string.remote_trust_action_search, R.string.remote_trust_search_keyword, "action.search", "word"));
        push.setOnClickListener(v -> showTextCommandDialog((FragmentActivity) context, binding, R.string.remote_trust_action_push, R.string.remote_trust_push_url, "action.push", "url"));
        row1.addView(search, weight());
        row1.addView(push, leftWeight(context));
        binding.content.addView(row1, topMargin(matchWrap(), 12));

        LinearLayoutCompat row2 = row(context);
        MaterialButton config = tonalAction(binding, context, R.string.remote_trust_action_config);
        boolean syncRunning = remoteSyncRunningFor(profile, row);
        MaterialButton sync = primary(context, context.getString(syncRunning ? R.string.remote_trust_sync_running : R.string.remote_trust_action_sync));
        if (syncRunning) sync.setEnabled(false);
        else bindAction(binding, sync);
        config.setOnClickListener(v -> showConfigDialog((FragmentActivity) context, binding));
        sync.setOnClickListener(v -> confirmRemoteSync((FragmentActivity) context, binding));
        row2.addView(config, weight());
        row2.addView(sync, leftWeight(context));
        binding.content.addView(row2, topMargin(matchWrap(), 8));

        if (!TextUtils.isEmpty(binding.lastResult)) {
            binding.content.addView(commandResultPanel(context, binding), topMargin(matchWrap(), 14));
        }
    }

    private static void renderSettings(Context context, Binding binding) {
        String state = statusText(context, binding, currentProfile(binding));
        if (binding.statusExpanded) binding.content.addView(servicePanel(context, binding, state, binding.serviceDetailText), matchWrap());

        RemoteProfile profile = currentProfile(binding);
        if (profile == null) binding.serverEditing = true;
        if (binding.serverEditing) {
            binding.content.addView(serverTitleRow(context, binding), topMargin(matchWrap(), binding.statusExpanded ? 12 : 0));
            detach(binding.serverLayout);
            binding.content.addView(binding.serverLayout, topMargin(matchWrap(), 8));
            MaterialButton save = primaryAction(binding, context, R.string.remote_trust_done);
            save.setOnClickListener(v -> saveServerSettings((FragmentActivity) context, binding));
            binding.content.addView(save, topMargin(fixedHeight(context, 36), 8));
        } else {
            binding.content.addView(serverInfoPanel(context, binding, profile), topMargin(matchWrap(), binding.statusExpanded ? 12 : 0));
        }

        if (!Setting.hasFileAccess()) {
            MaterialButton permission = outlineAction(binding, context, R.string.remote_trust_file_permission);
            permission.setOnClickListener(v -> requestFileAccess((FragmentActivity) context, binding));
            binding.content.addView(permission, topMargin(fixedHeight(context, 34), 8));
        }

        MaterialButton advanced = outlineAction(binding, context, binding.advancedExpanded ? R.string.remote_trust_advanced_hide : R.string.remote_trust_advanced);
        advanced.setOnClickListener(v -> {
            binding.advancedExpanded = !binding.advancedExpanded;
            render(context, binding);
        });
        binding.content.addView(advanced, topMargin(fixedHeight(context, 34), 14));
        if (binding.advancedExpanded) {
            renderDeviceCleanup(context, binding, profile);
            MaterialButton clear = dangerAction(binding, context, R.string.remote_trust_reset_local);
            clear.setOnClickListener(v -> confirmClear((FragmentActivity) context, binding));
            binding.content.addView(clear, topMargin(fixedHeight(context, 36), 12));
        }
    }

    private static void renderDeviceCleanup(Context context, Binding binding, RemoteProfile profile) {
        List<DeviceRow> rows = deviceRows(profile);
        if (rows.isEmpty()) return;
        binding.content.addView(sectionTitle(context, R.string.remote_trust_added_devices), topMargin(matchWrap(), 12));
        for (DeviceRow row : rows) {
            LinearLayoutCompat item = card(context);
            LinearLayoutCompat line = row(context);
            MaterialTextView text = text(context, deviceText(context, profile, row.device), 12, "#3C4043", false);
            text.setMaxLines(2);
            text.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(text, weight());
            MaterialButton delete = iconButton(context, R.drawable.ic_action_delete, context.getString(R.string.remote_trust_delete_device));
            delete.setOnClickListener(v -> confirmDeleteDevice((FragmentActivity) context, binding, row));
            bindAction(binding, delete);
            line.addView(delete, fixed(context, 36, 32));
            item.addView(line, matchWrap());
            binding.content.addView(item, topMargin(matchWrap(), 8));
        }
    }

    private static void saveServerSettings(FragmentActivity activity, Binding binding) {
        String serverUrl = textOf(binding.server);
        String origin = RemoteTokens.normalizeOrigin(serverUrl);
        if (TextUtils.isEmpty(origin)) {
            Notify.show(R.string.remote_trust_server_required);
            return;
        }
        RemoteProfile current = RemoteStore.getProfileByOrigin(origin);
        if (current != null && TextUtils.equals(serverUrl.trim(), current.serverUrl) && current.keepOnline && current.enabled == binding.enabled.isChecked()) {
            binding.serverEditing = false;
            if (TextUtils.isEmpty(binding.bindCode)) binding.autoBindAttempted = false;
            if (!binding.autoDetected) detectService(activity, binding, true);
            render(activity, binding);
            return;
        }
        applyServer(activity, binding);
    }

    private static void applyServerIfNeeded(FragmentActivity activity, Binding binding) {
        if (binding.busy) return;
        String serverUrl = textOf(binding.server);
        if (TextUtils.isEmpty(serverUrl)) return;
        String origin = RemoteTokens.normalizeOrigin(serverUrl);
        if (TextUtils.isEmpty(origin)) {
            Notify.show(R.string.remote_trust_server_required);
            return;
        }
        RemoteProfile current = TextUtils.isEmpty(origin) ? null : RemoteStore.getProfileByOrigin(origin);
        if (current != null && TextUtils.equals(serverUrl.trim(), current.serverUrl) && current.keepOnline && current.enabled == binding.enabled.isChecked()) {
            if (!binding.autoDetected) detectService(activity, binding, true);
            return;
        }
        applyServer(activity, binding);
    }

    private static void applyServer(FragmentActivity activity, Binding binding) {
        RemoteProfile profile;
        try {
            profile = prepare(binding, true);
        } catch (Throwable e) {
            Notify.show(e.getMessage());
            return;
        }
        setBusy(binding, true);
        Task.execute(() -> {
            try {
                new RemoteClient(profile).register();
                RemoteStore.upsertProfile(profile);
                RemoteAgent.get().start();
                App.post(() -> {
                    setBusy(binding, false);
                    resetDetect(binding);
                    binding.serverEditing = false;
                    binding.autoBindAttempted = false;
                    clearBindCode(binding);
                    Notify.show(R.string.remote_trust_register_done);
                    render(activity, binding);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    setBusy(binding, false);
                    Notify.show(e.getMessage());
                });
            }
        });
    }

    private static void detectService(FragmentActivity activity, Binding binding, boolean quiet) {
        String serverUrl = textOf(binding.server);
        String origin = RemoteTokens.normalizeOrigin(serverUrl);
        if (TextUtils.isEmpty(origin)) {
            if (!quiet) Notify.show(R.string.remote_trust_server_required);
            return;
        }
        binding.autoDetectStarted = true;
        binding.detectingService = true;
        updateHeader(activity, binding);
        RemoteProfile probe = new RemoteProfile();
        probe.serverUrl = serverUrl.trim();
        probe.serverOrigin = origin;
        Task.execute(() -> {
            try {
                ServerCapabilities capabilities = new RemoteClient(probe).capabilities();
                String detail = formatCapabilities(activity, capabilities);
                String diagnostics = origin + "/api/server/capabilities\n" + App.gson().toJson(capabilities);
                App.post(() -> {
                    binding.detectingService = false;
                    binding.serviceStateText = activity.getString(R.string.remote_trust_service_ok);
                    binding.serviceDetailText = detail;
                    binding.diagnostics = diagnostics;
                    binding.autoDetected = true;
                    binding.autoDetectStarted = false;
                    if (TextUtils.isEmpty(binding.bindCode)) binding.autoBindAttempted = false;
                    App.removeCallbacks(binding.detectRetry);
                    render(activity, binding);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    binding.detectingService = false;
                    binding.serviceStateText = activity.getString(R.string.remote_trust_service_error);
                    binding.serviceDetailText = activity.getString(R.string.remote_trust_service_failed_with_reason, conciseError(activity, e));
                    binding.diagnostics = origin + "/api/server/capabilities\n" + e.getMessage();
                    binding.autoDetected = false;
                    binding.autoDetectStarted = true;
                    scheduleDetectRetry(binding);
                    render(activity, binding);
                });
            }
        });
    }

    private static void showBindCodeDialog(FragmentActivity activity, Binding binding) {
        if (currentProfile(binding) == null) {
            binding.page = PAGE_SETTINGS;
            render(activity, binding);
            Notify.show(R.string.remote_trust_no_profile);
            return;
        }
        if (!hasFreshBindCode(binding)) {
            clearBindCode(binding);
            createBindCode(activity, binding, true);
            return;
        }
        LinearLayoutCompat root = dialogRoot(activity);
        MaterialTextView code = text(activity, binding.bindCode, 28, "#202124", true);
        code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        code.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        root.addView(code, matchWrap());
        root.addView(caption(activity, R.string.remote_trust_bind_code_hint), topMargin(matchWrap(), 8));
        showModal(new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.remote_trust_bind_local_title)
                .setView(root)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setNeutralButton(R.string.remote_trust_refresh_bind_code, (dialog, which) -> createBindCode(activity, binding, true))
                .setPositiveButton(R.string.remote_trust_copy, (dialog, which) -> copyCode(activity, binding))
                .create());
    }

    private static void createBindCode(FragmentActivity activity, Binding binding, boolean reopen) {
        createBindCode(activity, binding, reopen, false);
    }

    private static void createBindCode(FragmentActivity activity, Binding binding, boolean reopen, boolean quiet) {
        if (binding.creatingBindCode) return;
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) {
            binding.page = PAGE_SETTINGS;
            render(activity, binding);
            if (!quiet) Notify.show(R.string.remote_trust_no_profile);
            return;
        }
        binding.autoBindAttempted = true;
        binding.creatingBindCode = true;
        RemoteBindGrant grant = new RemoteBindGrant();
        grant.bindGrantToken = RemoteTokens.randomCapability("bgt");
        grant.grantId = RemoteTokens.bindGrantId(profile.serverOrigin, grant.bindGrantToken);
        grant.createdAt = System.currentTimeMillis();
        RemoteStore.addBindGrant(profile.serverOrigin, grant);
        updateHeader(activity, binding);
        Task.execute(() -> {
            try {
                RemoteClient client = new RemoteClient(profile);
                client.register();
                BindCodeResponse response = client.createBindCode(grant);
                RemoteStore.upsertProfile(profile);
                RemoteAgent.get().start();
                App.post(() -> {
                    binding.creatingBindCode = false;
                    binding.bindCode = response == null ? "" : response.code;
                    binding.bindCodeExpiresAt = bindCodeExpiresAt(response);
                    scheduleBindCodeRefresh(binding);
                    if (!quiet) Notify.show(R.string.remote_trust_bind_code_done);
                    render(activity, binding);
                    if (reopen) showBindCodeDialog(activity, binding);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    binding.creatingBindCode = false;
                    if (!quiet) Notify.show(e.getMessage());
                    render(activity, binding);
                });
            }
        });
    }

    private static void showAddDeviceDialog(FragmentActivity activity, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) {
            binding.page = PAGE_SETTINGS;
            render(activity, binding);
            Notify.show(R.string.remote_trust_no_profile);
            return;
        }
        DialogRemoteTrustAddDeviceBinding dialogBinding = DialogRemoteTrustAddDeviceBinding.inflate(LayoutInflater.from(activity));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setView(dialogBinding.getRoot())
                .create();
        dialogBinding.negative.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.positive.setOnClickListener(v -> {
            String value = textOf(dialogBinding.code);
            if (TextUtils.isEmpty(value)) {
                Notify.show(R.string.remote_trust_code_required);
                return;
            }
            dialog.dismiss();
            addDevice(activity, binding, value, textOf(dialogBinding.alias));
        });
        dialogBinding.alias.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialogBinding.positive.performClick();
                return true;
            }
            return false;
        });
        dialog.setOnShowListener(d -> {
            configureSmallModalWindow(activity, dialog);
            dialogBinding.code.requestFocus();
        });
        showModal(dialog);
    }

    private static void addDevice(FragmentActivity activity, Binding binding, String code, String alias) {
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) {
            Notify.show(R.string.remote_trust_no_profile);
            return;
        }
        if (isLocalBindCode(binding, code)) {
            Notify.show(R.string.remote_trust_no_self_add);
            return;
        }
        String groupToken = firstGroupToken(profile);
        setBusy(binding, true);
        Task.execute(() -> {
            try {
                RemoteClient client = new RemoteClient(profile);
                client.register();
                ClaimResponse response = client.claim(code, groupToken, alias);
                if (isLocalClaim(profile, response)) throw new IllegalStateException(App.get().getString(R.string.remote_trust_no_self_add));
                RemoteGroup group = RemoteStore.upsertClaimGroup(profile.serverOrigin, response, alias);
                RemoteProfile updated = RemoteStore.getProfileByOrigin(profile.serverOrigin);
                if (updated != null) {
                    RemoteClient updatedClient = new RemoteClient(updated);
                    updatedClient.register();
                    if (group != null) refreshGroup(updatedClient, updated.serverOrigin, group);
                }
                RemoteAgent.get().start();
                App.post(() -> {
                    setBusy(binding, false);
                    binding.selectedGroupId = "";
                    binding.selectedDeviceId = "";
                    binding.page = PAGE_DEVICES;
                    binding.pendingDeviceRefreshes = DEVICE_REFRESH_RETRY_MAX;
                    Notify.show(R.string.remote_trust_add_done);
                    render(activity, binding);
                    scheduleDeviceRefresh(binding);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    setBusy(binding, false);
                    Notify.show(isBindCodeExpired(e) ? activity.getString(R.string.remote_trust_bind_code_expired) : e.getMessage());
                });
            }
        });
    }

    private static boolean isLocalBindCode(Binding binding, String code) {
        if (binding == null || TextUtils.isEmpty(code) || TextUtils.isEmpty(binding.bindCode)) return false;
        return hasFreshBindCode(binding) && TextUtils.equals(code.trim(), binding.bindCode.trim());
    }

    private static boolean isLocalClaim(RemoteProfile profile, ClaimResponse response) {
        if (profile == null || response == null || TextUtils.isEmpty(profile.deviceId)) return false;
        String deviceId = response.device == null ? response.deviceId : response.device.deviceId;
        return TextUtils.equals(profile.deviceId, deviceId);
    }

    private static boolean hasFreshBindCode(Binding binding) {
        if (binding == null || TextUtils.isEmpty(binding.bindCode)) return false;
        return binding.bindCodeExpiresAt <= 0 || binding.bindCodeExpiresAt > System.currentTimeMillis() + BIND_CODE_REFRESH_SKEW_MS;
    }

    private static void clearBindCode(Binding binding) {
        if (binding == null) return;
        binding.bindCode = "";
        binding.bindCodeExpiresAt = 0;
        if (binding.bindCodeRefresh != null) App.removeCallbacks(binding.bindCodeRefresh);
    }

    private static long bindCodeExpiresAt(BindCodeResponse response) {
        int expiresIn = response == null ? 0 : response.expiresIn;
        long ttl = expiresIn <= 0 ? BIND_CODE_FALLBACK_TTL_MS : Math.max(1, expiresIn) * 1000L;
        return System.currentTimeMillis() + ttl;
    }

    private static void scheduleBindCodeRefresh(Binding binding) {
        if (binding == null || binding.bindCodeRefresh == null || binding.dialog == null || !binding.dialog.isShowing()) return;
        if (TextUtils.isEmpty(binding.bindCode) || binding.bindCodeExpiresAt <= 0) return;
        long delay = binding.bindCodeExpiresAt - System.currentTimeMillis() - BIND_CODE_REFRESH_SKEW_MS;
        App.post(binding.bindCodeRefresh, Math.max(BIND_CODE_REFRESH_MIN_MS, delay));
    }

    private static void refreshBindCodeIfNeeded(FragmentActivity activity, Binding binding) {
        if (activity == null || binding == null || binding.dialog == null || !binding.dialog.isShowing()) return;
        RemoteProfile profile = currentProfile(binding);
        if (profile == null || !profile.enabled || binding.busy || binding.creatingBindCode) {
            scheduleBindCodeRefresh(binding);
            return;
        }
        if (!hasFreshBindCode(binding)) {
            clearBindCode(binding);
            binding.autoBindAttempted = false;
            createBindCode(activity, binding, false, true);
            return;
        }
        scheduleBindCodeRefresh(binding);
    }

    private static boolean isBindCodeExpired(Throwable e) {
        String message = e == null ? "" : e.getMessage();
        if (TextUtils.isEmpty(message)) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("bind code expired") || lower.contains("binding code expired") || lower.contains("expired") || message.contains("过期") || message.contains("失效");
    }

    private static int refreshGroup(RemoteClient client, String serverOrigin, RemoteGroup group) throws Exception {
        DevicesResponse response = client.listDevices(group);
        List<RemoteDevice> devices = response == null ? new ArrayList<>() : response.devices;
        RemoteStore.upsertDevices(serverOrigin, group.groupId, devices);
        return devices == null ? 0 : devices.size();
    }

    private static void ensureDeviceStatusRefresh(FragmentActivity activity, Binding binding) {
        if (activity == null || binding.dialog == null || !binding.dialog.isShowing()) return;
        if (binding.page == PAGE_SETTINGS || binding.deviceStatusRefresh == null) return;
        if (binding.deviceStatusRefreshScheduled || binding.deviceStatusRefreshing) return;
        RemoteProfile profile = currentProfile(binding);
        if (profile == null || profile.groups == null || profile.groups.isEmpty()) return;
        if (!profile.enabled) return;
        long elapsed = System.currentTimeMillis() - binding.lastDeviceStatusRefreshAt;
        long delay = binding.lastDeviceStatusRefreshAt <= 0 || elapsed >= DEVICE_STATUS_REFRESH_MS ? 0 : DEVICE_STATUS_REFRESH_MS - elapsed;
        binding.deviceStatusRefreshScheduled = true;
        App.post(binding.deviceStatusRefresh, delay);
    }

    private static void refreshDeviceStatus(FragmentActivity activity, Binding binding) {
        binding.deviceStatusRefreshScheduled = false;
        if (activity == null || binding.dialog == null || !binding.dialog.isShowing()) return;
        if (binding.page == PAGE_SETTINGS) return;
        RemoteProfile profile = currentProfile(binding);
        if (profile == null || profile.groups == null || profile.groups.isEmpty()) return;
        if (!profile.enabled) return;
        if (binding.deviceStatusRefreshing) {
            ensureDeviceStatusRefresh(activity, binding);
            return;
        }
        binding.deviceStatusRefreshing = true;
        Task.execute(() -> {
            try {
                RemoteProfile latest = currentProfile(binding);
                if (latest != null && latest.groups != null && !latest.groups.isEmpty()) {
                    RemoteClient client = new RemoteClient(latest);
                    for (RemoteGroup group : new ArrayList<>(latest.groups)) refreshGroup(client, latest.serverOrigin, group);
                }
            } catch (Throwable e) {
                RemoteLog.log("device status refresh failed error=%s", e.getMessage());
            } finally {
                App.post(() -> {
                    binding.deviceStatusRefreshing = false;
                    binding.lastDeviceStatusRefreshAt = System.currentTimeMillis();
                    if (binding.dialog != null && binding.dialog.isShowing() && binding.page != PAGE_SETTINGS) render(activity, binding);
                });
            }
        });
    }

    private static void scheduleDeviceRefresh(Binding binding) {
        if (binding.deviceRefreshRetry == null || binding.dialog == null || !binding.dialog.isShowing()) return;
        App.post(binding.deviceRefreshRetry, DEVICE_REFRESH_RETRY_MS);
    }

    private static void retryRefreshDevices(FragmentActivity activity, Binding binding) {
        if (activity == null || binding.dialog == null || !binding.dialog.isShowing()) return;
        RemoteProfile profile = currentProfile(binding);
        if (profile == null || profile.groups == null || profile.groups.isEmpty()) return;
        if (!deviceRows(profile).isEmpty()) return;
        if (binding.pendingDeviceRefreshes-- <= 0) return;
        Task.execute(() -> {
            try {
                RemoteProfile latest = currentProfile(binding);
                if (latest == null || latest.groups == null || latest.groups.isEmpty()) return;
                RemoteClient client = new RemoteClient(latest);
                client.register();
                for (RemoteGroup group : new ArrayList<>(latest.groups)) refreshGroup(client, latest.serverOrigin, group);
                App.post(() -> {
                    render(activity, binding);
                    if (deviceRows(currentProfile(binding)).isEmpty()) scheduleDeviceRefresh(binding);
                });
            } catch (Throwable ignored) {
                App.post(() -> scheduleDeviceRefresh(binding));
            }
        });
    }

    private static void showTextCommandDialog(FragmentActivity activity, Binding binding, int title, int hint, String type, String payloadKey) {
        DialogRemoteTrustTextCommandBinding dialogBinding = DialogRemoteTrustTextCommandBinding.inflate(LayoutInflater.from(activity));
        dialogBinding.title.setText(title);
        dialogBinding.inputLayout.setHint(activity.getString(hint));
        dialogBinding.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        dialogBinding.positive.setText("action.push".equals(type) ? R.string.remote_trust_send_push : R.string.remote_trust_send_search);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setView(dialogBinding.getRoot())
                .create();
        dialogBinding.negative.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.positive.setOnClickListener(v -> {
            String value = textOf(dialogBinding.input);
            if (TextUtils.isEmpty(value)) {
                Notify.show(hint);
                return;
            }
            JsonObject payload = new JsonObject();
            payload.addProperty(payloadKey, value);
            dialog.dismiss();
            sendCommand(activity, binding, type, payload);
        });
        dialogBinding.input.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialogBinding.positive.performClick();
                return true;
            }
            return false;
        });
        dialog.setOnShowListener(d -> {
            configureSmallModalWindow(activity, dialog);
            dialogBinding.input.requestFocus();
        });
        showModal(dialog);
    }

    private static void showConfigDialog(FragmentActivity activity, Binding binding) {
        LinearLayoutCompat root = panelDialogRoot(activity);
        ConfigDialogState state = new ConfigDialogState();

        state.titleRow = row(activity);
        state.title = text(activity, activity.getString(R.string.remote_trust_action_config), 18, "#202124", true);
        state.titleRow.addView(state.title, weight());
        state.homeBack = outline(activity, activity.getString(R.string.remote_trust_back_devices));
        state.homeBack.setTextSize(12);
        state.homeBack.setVisibility(View.GONE);
        state.titleRow.addView(state.homeBack, fixed(activity, 58, 34));
        root.addView(state.titleRow, matchWrap());

        state.typeRow = new MaterialButtonToggleGroup(activity);
        state.typeRow.setSingleSelection(true);
        state.typeRow.setSelectionRequired(true);
        state.vod = tab(activity, R.string.remote_trust_config_type_vod);
        state.live = tab(activity, R.string.remote_trust_config_type_live);
        state.wall = tab(activity, R.string.remote_trust_config_type_wall);
        state.vod.setId(View.generateViewId());
        state.live.setId(View.generateViewId());
        state.wall.setId(View.generateViewId());
        state.vod.setChecked(true);
        state.typeRow.addView(state.vod, weight());
        state.typeRow.addView(state.live, weight());
        state.typeRow.addView(state.wall, weight());
        root.addView(state.typeRow, topMargin(matchWrap(), 10));

        state.header = row(activity);
        state.summary = text(activity, activity.getString(R.string.remote_trust_config_manage_hint), 13, "#5F6368", false);
        state.header.addView(state.summary, weight());
        state.refresh = outline(activity, activity.getString(R.string.remote_trust_refresh_short));
        state.header.addView(state.refresh, fixed(activity, 72, 34));
        state.close = outline(activity, activity.getString(R.string.remote_trust_back_devices));
        state.header.addView(state.close, fixed(activity, 64, 34));
        root.addView(state.header, topMargin(matchWrap(), 8));

        state.formActionsRow = row(activity);
        state.addSave = primary(activity, activity.getString(R.string.remote_trust_config_upsert_short));
        state.formActionsRow.addView(state.addSave, weight());
        state.addBack = outline(activity, activity.getString(R.string.remote_trust_back_devices));
        state.formActionsRow.addView(state.addBack, leftWeight(activity, 6));
        state.formActionsRow.setVisibility(View.GONE);
        root.addView(state.formActionsRow, topMargin(matchWrap(), 8));

        state.actionsRow = row(activity);
        state.home = tonal(activity, activity.getString(R.string.remote_trust_config_home_short));
        state.add = primary(activity, activity.getString(R.string.remote_trust_config_add));
        state.edit = tonal(activity, activity.getString(R.string.remote_trust_config_edit));
        state.delete = outline(activity, activity.getString(R.string.remote_trust_config_delete_short));
        state.home.setTextSize(12);
        state.add.setTextSize(12);
        state.edit.setTextSize(12);
        state.delete.setTextSize(12);
        state.delete.setTextColor(Color.parseColor("#B3261E"));
        state.delete.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#F2B8B5")));
        state.actionsRow.addView(state.home, weight());
        state.actionsRow.addView(state.add, leftWeight(activity, 6));
        state.actionsRow.addView(state.edit, leftWeight(activity, 6));
        state.actionsRow.addView(state.delete, leftWeight(activity, 6));
        root.addView(state.actionsRow, topMargin(matchWrap(), 8));

        state.contentScroll = new NestedScrollView(activity);
        state.contentScroll.setFillViewport(false);
        state.contentScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        state.content = new LinearLayoutCompat(activity);
        state.content.setOrientation(LinearLayoutCompat.VERTICAL);
        state.contentScroll.addView(state.content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(state.contentScroll, topMargin(new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, configContentHeight(activity)), 10));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setView(root)
                .create();
        state.dialog = dialog;
        state.render = () -> {
            if (state.adding) renderConfigAddContent(activity, binding, state);
            else if (state.editing) renderConfigEditContent(activity, binding, state);
            else renderRemoteConfigList(activity, binding, state);
        };
        state.vod.setOnClickListener(v -> selectConfigType(state, 0));
        state.live.setOnClickListener(v -> selectConfigType(state, 1));
        state.wall.setOnClickListener(v -> selectConfigType(state, 2));
        state.home.setOnClickListener(v -> {
            if (state.selected != null) showRemoteHomeDialog(activity, binding, state, configPayload(state.selected));
        });
        state.edit.setOnClickListener(v -> {
            if (state.selected != null) enterConfigEdit(activity, binding, state);
        });
        state.delete.setOnClickListener(v -> {
            if (state.selected != null) confirmConfigDelete(activity, binding, state, configPayload(state.selected));
        });
        state.homeBack.setOnClickListener(v -> {
            state.settingHomeKey = "";
            renderRemoteConfigList(activity, binding, state);
        });
        state.close.setOnClickListener(v -> {
            if (state.dialog != null) state.dialog.dismiss();
        });
        state.add.setOnClickListener(v -> enterConfigAdd(activity, binding, state));
        state.refresh.setOnClickListener(v -> refreshRemoteConfigList(activity, binding, state, true));
        state.addSave.setOnClickListener(v -> {
            if (state.editing) saveConfigEdit(activity, binding, state);
            else saveConfigAdd(activity, binding, state);
        });
        state.addBack.setOnClickListener(v -> {
            if (state.editing) exitConfigEdit(activity, binding, state);
            else exitConfigAdd(activity, binding, state);
        });
        dialog.setOnShowListener(v -> {
            configureConfigWindow(activity, dialog);
            refreshRemoteConfigList(activity, binding, state, false);
        });
        showModal(dialog);
    }

    private static void configureConfigWindow(Context context, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(context);
        int screen = ResUtil.getScreenWidth(context);
        params.width = land ? (int) (screen * 0.84f) : Math.min(screen - dp(context, 16), (int) (screen * 0.96f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private static int configContentHeight(Context context) {
        boolean land = ResUtil.isLand(context);
        int height = (int) (ResUtil.getScreenHeight(context) * (land ? 0.44f : 0.34f));
        return Math.max(dp(context, land ? 220 : 190), Math.min(dp(context, land ? 360 : 300), height));
    }

    private static void selectConfigType(ConfigDialogState state, int type) {
        state.type = type;
        state.selected = null;
        state.addSelected = null;
        state.settingHomeKey = "";
        if (state.render != null) state.render.run();
    }

    private static void refreshRemoteConfigList(FragmentActivity activity, Binding binding, ConfigDialogState state, boolean force) {
        String key = remoteCacheKey(binding);
        JsonArray cached = binding.configCache.get(key);
        if (!force && cached != null) {
            state.items = cached;
            renderRemoteConfigList(activity, binding, state);
            prefetchVodSites(activity, binding, state);
            return;
        }
        state.content.removeAllViews();
        state.content.addView(emptyPanel(activity, activity.getString(R.string.remote_trust_config_loading)), matchWrap());
        sendCommand(activity, binding, "config.list", new JsonObject(), false, command -> {
            if (state.dialog == null || !state.dialog.isShowing()) return;
            RemoteCommandResult result = command == null ? null : command.result;
            if (result == null || !result.ok) {
                state.content.removeAllViews();
                String message = result == null || TextUtils.isEmpty(result.message) ? activity.getString(R.string.remote_trust_config_load_failed) : result.message;
                state.content.addView(emptyPanel(activity, message), matchWrap());
                updateConfigActions(activity, state);
                return;
            }
            JsonObject data = result.data == null || !result.data.isJsonObject() ? new JsonObject() : result.data.getAsJsonObject();
            state.items = data.has("items") && data.get("items").isJsonArray() ? uniqueConfigs(data.getAsJsonArray("items")) : new JsonArray();
            binding.configCache.put(key, state.items);
            renderRemoteConfigList(activity, binding, state);
            prefetchVodSites(activity, binding, state);
        });
    }

    private static void renderRemoteConfigList(FragmentActivity activity, Binding binding, ConfigDialogState state) {
        state.adding = false;
        state.editing = false;
        state.homePicking = false;
        state.content.removeAllViews();
        state.vod.setChecked(state.type == 0);
        state.live.setChecked(state.type == 1);
        state.wall.setChecked(state.type == 2);
        JsonArray items = filterConfigs(state.items == null ? new JsonArray() : state.items, state.type);
        if (items.size() == 0) {
            state.selected = null;
            updateConfigActions(activity, state);
            state.content.addView(emptyPanel(activity, activity.getString(R.string.remote_trust_config_remote_empty)), matchWrap());
            return;
        }
        JsonObject selected = findConfig(items, state.selected);
        state.selected = selected == null ? defaultSelected(items) : selected;
        updateConfigActions(activity, state);
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            state.content.addView(remoteConfigItem(activity, binding, state, element.getAsJsonObject()), topMargin(matchWrap(), 8));
        }
    }

    private static JsonArray filterConfigs(JsonArray source, int type) {
        JsonArray array = new JsonArray();
        for (JsonElement element : source) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (payloadType(item) == type) array.add(item);
        }
        return array;
    }

    private static JsonArray uniqueConfigs(JsonArray source) {
        JsonArray array = new JsonArray();
        List<String> keys = new ArrayList<>();
        for (JsonElement element : source) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String key = payloadType(item) + "|" + safe(item, "url");
            if (keys.contains(key)) continue;
            keys.add(key);
            array.add(item);
        }
        return array;
    }

    private static LinearLayoutCompat remoteConfigItem(FragmentActivity activity, Binding binding, ConfigDialogState state, JsonObject item) {
        boolean active = bool(item, "active");
        boolean selected = sameConfig(state.selected, item);
        int useStatus = configUseStatusFor(state, item);
        LinearLayoutCompat card = card(activity);
        card.setBackground(configItemBackground(activity, active, selected));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            state.selected = item;
            renderRemoteConfigList(activity, binding, state);
        });
        LinearLayoutCompat top = row(activity);
        LinearLayoutCompat textBox = new LinearLayoutCompat(activity);
        textBox.setOrientation(LinearLayoutCompat.VERTICAL);
        String title = configTitle(item);
        String urlValue = safe(item, "url");
        MaterialTextView name = text(activity, title, 13, "#202124", active);
        name.setSingleLine(false);
        name.setHorizontallyScrolling(false);
        name.setMaxLines(TextUtils.equals(title, urlValue) ? 3 : 1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        textBox.addView(name, matchWrap());
        if (!TextUtils.equals(title, urlValue) && !TextUtils.isEmpty(urlValue)) {
            MaterialTextView url = text(activity, urlValue, 12, "#5F6368", false);
            url.setSingleLine(false);
            url.setHorizontallyScrolling(false);
            url.setMaxLines(2);
            url.setEllipsize(TextUtils.TruncateAt.END);
            url.setPadding(0, dp(activity, 3), 0, 0);
            textBox.addView(url, matchWrap());
        }
        top.addView(textBox, weight());
        MaterialButton use = configUseButton(activity, active, useStatus);
        use.setEnabled(!active && useStatus != HOME_STATUS_SETTING && !configBusy(state));
        use.setOnClickListener(v -> useRemoteConfig(activity, binding, state, item));
        top.addView(use, fixed(activity, useStatus == HOME_STATUS_SETTING ? 74 : 58, 32));
        card.addView(top, matchWrap());
        addConfigUseLine(activity, card, state, item);
        addConfigHomeLine(activity, card, state, item, active);
        return card;
    }

    private static MaterialButton configUseButton(Context context, boolean active, int status) {
        if (status == HOME_STATUS_SETTING) return tonal(context, context.getString(R.string.remote_trust_config_use_setting));
        if (active) return tonal(context, context.getString(R.string.remote_trust_config_current));
        return primary(context, context.getString(R.string.remote_trust_config_use_short));
    }

    private static void addConfigUseLine(Context context, LinearLayoutCompat card, ConfigDialogState state, JsonObject item) {
        int status = configUseStatusFor(state, item);
        if (status == HOME_STATUS_NONE) return;
        MaterialTextView view = text(context, configUseStatusText(context, state), 12, homeStatusColor(status), true);
        view.setPadding(0, dp(context, 6), 0, 0);
        card.addView(view, matchWrap());
    }

    private static int configUseStatusFor(ConfigDialogState state, JsonObject item) {
        if (state == null || state.useStatus == HOME_STATUS_NONE) return HOME_STATUS_NONE;
        return TextUtils.equals(state.useStatusKey, configKey(item)) ? state.useStatus : HOME_STATUS_NONE;
    }

    private static String configUseStatusText(Context context, ConfigDialogState state) {
        if (state.useStatus == HOME_STATUS_SUCCESS) return context.getString(R.string.remote_trust_config_use_status_success);
        if (state.useStatus == HOME_STATUS_FAILED) return context.getString(R.string.remote_trust_config_use_status_failed);
        return context.getString(R.string.remote_trust_config_use_status_setting);
    }

    private static void addConfigHomeLine(Context context, LinearLayoutCompat card, ConfigDialogState state, JsonObject item, boolean active) {
        if (payloadType(item) != 0) return;
        int status = homeStatusFor(state, item);
        if (status != HOME_STATUS_NONE) {
            MaterialTextView home = text(context, homeStatusText(context, state), 12, homeStatusColor(status), true);
            home.setPadding(0, dp(context, 6), 0, 0);
            card.addView(home, matchWrap());
            return;
        }
        if (active && !TextUtils.isEmpty(safe(item, "homeName"))) {
            MaterialTextView home = text(context, context.getString(R.string.remote_trust_config_home_label, safe(item, "homeName")), 12, "#137333", false);
            home.setPadding(0, dp(context, 5), 0, 0);
            card.addView(home, matchWrap());
        }
    }

    private static int homeStatusFor(ConfigDialogState state, JsonObject item) {
        if (state == null || state.homeStatus == HOME_STATUS_NONE) return HOME_STATUS_NONE;
        return TextUtils.equals(state.homeStatusKey, configKey(item)) ? state.homeStatus : HOME_STATUS_NONE;
    }

    private static String homeStatusText(Context context, ConfigDialogState state) {
        String name = TextUtils.isEmpty(state.homeStatusName) ? "-" : state.homeStatusName;
        if (state.homeStatus == HOME_STATUS_SUCCESS) return context.getString(R.string.remote_trust_config_home_status_success, name);
        if (state.homeStatus == HOME_STATUS_FAILED) return context.getString(R.string.remote_trust_config_home_status_failed, name);
        return context.getString(R.string.remote_trust_config_home_status_setting, name);
    }

    private static String homeStatusColor(int status) {
        if (status == HOME_STATUS_SUCCESS) return "#137333";
        if (status == HOME_STATUS_FAILED) return "#B3261E";
        return "#174EA6";
    }

    private static void updateConfigActions(FragmentActivity activity, ConfigDialogState state) {
        updateConfigChrome(activity, state);
        if (state.adding || state.editing) return;
        boolean has = state.selected != null;
        boolean busy = configBusy(state);
        state.add.setEnabled(!busy);
        state.home.setEnabled(has && payloadType(state.selected) == 0 && !busy);
        state.edit.setEnabled(has && !busy);
        state.delete.setEnabled(has && !busy);
    }

    private static void updateConfigChrome(FragmentActivity activity, ConfigDialogState state) {
        boolean adding = state.adding;
        boolean editing = state.editing;
        boolean homePicking = state.homePicking;
        boolean form = adding || editing;
        boolean busy = configBusy(state);
        state.title.setText(homePicking ? activity.getString(R.string.remote_trust_config_home) : activity.getString(R.string.remote_trust_action_config));
        state.homeBack.setVisibility(homePicking ? View.VISIBLE : View.GONE);
        state.close.setVisibility(form || homePicking ? View.GONE : View.VISIBLE);
        state.summary.setText(homePicking ? activity.getString(R.string.remote_trust_config_home) : editing ? editConfigSummary(activity, state) : adding ? addConfigSummary(activity, state) : configListSummary(activity, state));
        state.typeRow.setVisibility(editing || homePicking ? View.GONE : View.VISIBLE);
        state.header.setVisibility(homePicking ? View.GONE : View.VISIBLE);
        state.add.setVisibility(form || homePicking ? View.GONE : View.VISIBLE);
        state.refresh.setVisibility(form || homePicking ? View.GONE : View.VISIBLE);
        state.actionsRow.setVisibility(form || homePicking ? View.GONE : View.VISIBLE);
        state.formActionsRow.setVisibility(form ? View.VISIBLE : View.GONE);
        state.addSave.setEnabled(!busy);
        state.addBack.setEnabled(!busy);
        setConfigDialogCancelVisible(state, !form && !homePicking);
    }

    private static void setConfigDialogCancelVisible(ConfigDialogState state, boolean visible) {
        if (state == null || state.dialog == null) return;
        android.widget.Button button = state.dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (button != null) button.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private static boolean configBusy(ConfigDialogState state) {
        return state != null && (state.homeStatus == HOME_STATUS_SETTING || state.useStatus == HOME_STATUS_SETTING);
    }

    private static String configListSummary(Context context, ConfigDialogState state) {
        JsonArray items = filterConfigs(state.items == null ? new JsonArray() : state.items, state.type);
        return context.getString(R.string.remote_trust_config_summary, typeName(state.type), items.size());
    }

    private static String addConfigSummary(Context context, ConfigDialogState state) {
        return context.getString(state.addLocalMode ? R.string.remote_trust_config_add_local_summary : R.string.remote_trust_config_add_manual_summary, typeName(state.type));
    }

    private static String editConfigSummary(Context context, ConfigDialogState state) {
        return context.getString(R.string.remote_trust_config_edit_summary, typeName(state.editType));
    }

    private static void enterConfigAdd(FragmentActivity activity, Binding binding, ConfigDialogState state) {
        state.adding = true;
        state.addLocalMode = true;
        state.addSelected = null;
        renderConfigAddContent(activity, binding, state);
    }

    private static void exitConfigAdd(FragmentActivity activity, Binding binding, ConfigDialogState state) {
        state.adding = false;
        state.addSelected = null;
        renderRemoteConfigList(activity, binding, state);
    }

    private static void enterConfigEdit(FragmentActivity activity, Binding binding, ConfigDialogState state) {
        state.editing = true;
        state.editOriginal = configPayload(state.selected);
        state.editType = payloadType(state.editOriginal);
        renderConfigEditContent(activity, binding, state);
    }

    private static void exitConfigEdit(FragmentActivity activity, Binding binding, ConfigDialogState state) {
        state.editing = false;
        state.editOriginal = null;
        renderRemoteConfigList(activity, binding, state);
    }

    private static void renderConfigAddContent(FragmentActivity activity, Binding binding, ConfigDialogState state) {
        state.adding = true;
        state.content.removeAllViews();
        state.vod.setChecked(state.type == 0);
        state.live.setChecked(state.type == 1);
        state.wall.setChecked(state.type == 2);
        MaterialButtonToggleGroup modeRow = new MaterialButtonToggleGroup(activity);
        modeRow.setSingleSelection(true);
        modeRow.setSelectionRequired(true);
        MaterialButton local = tab(activity, R.string.remote_trust_config_mode_local);
        MaterialButton manual = tab(activity, R.string.remote_trust_config_mode_manual);
        local.setId(View.generateViewId());
        manual.setId(View.generateViewId());
        local.setChecked(state.addLocalMode);
        manual.setChecked(!state.addLocalMode);
        modeRow.addView(local, weight());
        modeRow.addView(manual, weight());
        state.content.addView(modeRow, matchWrap());

        LinearLayoutCompat body = new LinearLayoutCompat(activity);
        body.setOrientation(LinearLayoutCompat.VERTICAL);
        state.content.addView(body, topMargin(matchWrap(), 10));

        local.setOnClickListener(v -> {
            state.addLocalMode = true;
            state.addSelected = null;
            renderConfigAddContent(activity, binding, state);
        });
        manual.setOnClickListener(v -> {
            state.addLocalMode = false;
            state.addSelected = null;
            renderConfigAddContent(activity, binding, state);
        });
        renderConfigAddBody(activity, binding, state, body);
        updateConfigActions(activity, state);
    }

    private static void renderConfigAddBody(FragmentActivity activity, Binding binding, ConfigDialogState state, LinearLayoutCompat content) {
        content.removeAllViews();
        if (state.addLocalMode) {
            List<Config> configs = Config.getAll(state.type);
            if (configs.isEmpty()) {
                content.addView(emptyPanel(activity, activity.getString(R.string.remote_trust_config_local_empty)), matchWrap());
                return;
            }
            for (Config config : configs) {
                LinearLayoutCompat item = localConfigItem(activity, binding, state, configPayload(state.type, config.getUrl(), config.getName()));
                content.addView(item, topMargin(matchWrap(), 6));
            }
            return;
        }
        state.addUrl = input(activity, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, true);
        state.addName = input(activity, InputType.TYPE_CLASS_TEXT, true);
        content.addView(inputLayout(activity, R.string.remote_trust_config_url, state.addUrl), matchWrap());
        content.addView(inputLayout(activity, R.string.remote_trust_config_name, state.addName), topMargin(matchWrap(), 8));
    }

    private static void renderConfigEditContent(FragmentActivity activity, Binding binding, ConfigDialogState state) {
        state.editing = true;
        state.content.removeAllViews();
        state.editUrl = input(activity, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, true);
        state.editName = input(activity, InputType.TYPE_CLASS_TEXT, true);
        state.editUrl.setText(safe(state.editOriginal, "url"));
        state.editName.setText(safe(state.editOriginal, "name"));
        state.content.addView(inputLayout(activity, R.string.remote_trust_config_url, state.editUrl), matchWrap());
        state.content.addView(inputLayout(activity, R.string.remote_trust_config_name, state.editName), topMargin(matchWrap(), 8));
        updateConfigActions(activity, state);
    }

    private static void saveConfigEdit(FragmentActivity activity, Binding binding, ConfigDialogState state) {
        JsonObject payload = configPayload(activity, state.editType, state.editUrl, state.editName);
        if (payload == null) return;
        JsonObject original = TextUtils.isEmpty(safe(state.editOriginal, "url")) ? null : configPayload(state.editOriginal);
        Runnable done = () -> {
            upsertConfig(state.items, payload);
            binding.configCache.put(remoteCacheKey(binding), state.items);
            state.selected = findConfig(state.items, payload);
            state.editing = false;
            state.editOriginal = null;
            renderRemoteConfigList(activity, binding, state);
        };
        if (original != null && !sameConfig(original, payload)) {
            done = () -> runConfigCommand(activity, binding, state, "config.delete", original, () -> {
                removeConfig(state.items, original);
                upsertConfig(state.items, payload);
                binding.configCache.put(remoteCacheKey(binding), state.items);
                state.selected = findConfig(state.items, payload);
                state.editing = false;
                state.editOriginal = null;
                renderRemoteConfigList(activity, binding, state);
            });
        }
        runConfigCommand(activity, binding, state, "config.upsert", payload, done);
    }

    private static LinearLayoutCompat localConfigItem(FragmentActivity activity, Binding binding, ConfigDialogState state, JsonObject payload) {
        Context context = activity;
        LinearLayoutCompat item = card(context);
        boolean selected = sameConfig(state.addSelected, payload);
        item.setBackground(configItemBackground(context, false, selected));
        item.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        item.setClickable(true);
        item.setFocusable(true);
        MaterialTextView url = text(context, safe(payload, "url"), 13, "#202124", selected);
        url.setTextIsSelectable(false);
        url.setClickable(false);
        url.setFocusable(false);
        url.setLongClickable(false);
        url.setMaxLines(3);
        url.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(url, matchWrap());
        item.setOnClickListener(v -> {
            state.addSelected = payload;
            renderConfigAddContent(activity, binding, state);
        });
        return item;
    }

    private static void saveConfigAdd(FragmentActivity activity, Binding binding, ConfigDialogState state) {
        JsonObject payload;
        if (state.addLocalMode) {
            payload = state.addSelected;
            if (payload == null) {
                Notify.show(R.string.remote_trust_config_select);
                return;
            }
        } else {
            payload = configPayload(activity, state.type, state.addUrl, state.addName);
            if (payload == null) return;
        }
        runConfigCommand(activity, binding, state, "config.upsert", payload, () -> {
            upsertConfig(state.items, payload);
            binding.configCache.put(remoteCacheKey(binding), state.items);
            state.selected = findConfig(state.items, payload);
            state.adding = false;
            state.addSelected = null;
            renderRemoteConfigList(activity, binding, state);
        });
    }

    private static int payloadType(JsonObject payload) {
        try {
            return payload == null || !payload.has("type") ? 0 : payload.get("type").getAsInt();
        } catch (Throwable e) {
            return 0;
        }
    }

    private static String configTitle(JsonObject payload) {
        String name = safe(payload, "name");
        return TextUtils.isEmpty(name) ? safe(payload, "url") : name;
    }

    private static JsonObject configPayload(FragmentActivity activity, int type, TextInputEditText url, TextInputEditText name) {
        String value = textOf(url);
        if (TextUtils.isEmpty(value)) {
            Notify.show(R.string.remote_trust_config_url_required);
            return null;
        }
        return configPayload(type, value, textOf(name));
    }

    private static JsonObject configPayload(int type, String url, String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", Math.max(0, Math.min(2, type)));
        payload.addProperty("url", url);
        payload.addProperty("name", name);
        return payload;
    }

    private static JsonObject configPayload(JsonObject object) {
        JsonObject payload = configPayload(payloadType(object), safe(object, "url"), safe(object, "name"));
        if (object != null && object.has("interfaceKey")) payload.addProperty("interfaceKey", safe(object, "interfaceKey"));
        if (object != null && object.has("urls")) payload.add("urls", object.get("urls"));
        if (object != null && object.has("homeKey")) payload.addProperty("homeKey", safe(object, "homeKey"));
        if (object != null && object.has("homeName")) payload.addProperty("homeName", safe(object, "homeName"));
        return payload;
    }

    private static void useRemoteConfig(FragmentActivity activity, Binding binding, ConfigDialogState state, JsonObject item) {
        if (item == null || configBusy(state)) return;
        JsonObject payload = configPayload(item);
        int type = payloadType(payload);
        state.usePreviousKey = activeConfigKey(state.items, type);
        state.useStatusKey = configKey(payload);
        state.useStatus = HOME_STATUS_SETTING;
        markActive(state.items, payload);
        state.selected = findConfig(state.items, payload);
        binding.configCache.put(remoteCacheKey(binding), state.items);
        renderRemoteConfigList(activity, binding, state);
        sendCommand(activity, binding, "config.use", payload, false, command -> {
            RemoteCommandResult result = command == null ? null : command.result;
            if (result == null || !result.ok) {
                failUseRemoteConfig(activity, binding, state, payload, result == null ? "" : result.message);
                return;
            }
            updateConfigCacheFromResult(binding, state, result);
            markActive(state.items, payload);
            state.useStatus = HOME_STATUS_SUCCESS;
            state.selected = findConfig(state.items, payload);
            binding.configCache.put(remoteCacheKey(binding), state.items);
            renderRemoteConfigList(activity, binding, state);
        }, e -> failUseRemoteConfig(activity, binding, state, payload, e == null ? "" : e.getMessage()));
    }

    private static void failUseRemoteConfig(FragmentActivity activity, Binding binding, ConfigDialogState state, JsonObject payload, String message) {
        state.useStatus = HOME_STATUS_FAILED;
        restoreActiveConfig(state.items, payloadType(payload), state.usePreviousKey);
        state.selected = findConfig(state.items, payload);
        binding.configCache.put(remoteCacheKey(binding), state.items);
        if (TextUtils.isEmpty(message)) Notify.show(R.string.remote_trust_config_load_failed);
        else Notify.show(message);
        if (state.dialog != null && state.dialog.isShowing()) renderRemoteConfigList(activity, binding, state);
    }

    private static void runConfigCommand(FragmentActivity activity, Binding binding, ConfigDialogState state, String type, JsonObject payload, Runnable success) {
        if (payload == null) return;
        sendCommand(activity, binding, type, payload, false, command -> {
            RemoteCommandResult result = command == null ? null : command.result;
            if (result == null || !result.ok) {
                if (result == null || TextUtils.isEmpty(result.message)) Notify.show(R.string.remote_trust_config_load_failed);
                else Notify.show(result.message);
                return;
            }
            updateConfigCacheFromResult(binding, state, result);
            if (success != null) success.run();
        });
    }

    private static void showRemoteHomeDialog(FragmentActivity activity, Binding binding, ConfigDialogState state, JsonObject payload) {
        if (payload == null) return;
        state.homePicking = true;
        state.settingHomeKey = "";
        updateConfigActions(activity, state);
        JsonArray cached = binding.siteCache.get(siteCacheKey(binding, payload));
        if (cached != null) {
            showHomeSitePicker(activity, binding, state, payload, cached);
            return;
        }
        state.content.removeAllViews();
        state.content.addView(emptyPanel(activity, activity.getString(R.string.remote_trust_config_home_loading)), matchWrap());
        Task.execute(() -> {
            try {
                JsonArray sites = parseLocalSites(payload);
                if (sites.size() == 0) throw new IllegalStateException("Empty sites");
                App.post(() -> handleHomeSites(activity, binding, state, payload, sites));
            } catch (Throwable e) {
                App.post(() -> fetchRemoteHomeSites(activity, binding, state, payload));
            }
        });
    }

    private static void fetchRemoteHomeSites(FragmentActivity activity, Binding binding, ConfigDialogState state, JsonObject payload) {
        sendCommand(activity, binding, "config.sites", payload, false, command -> {
            RemoteCommandResult result = command == null ? null : command.result;
            if (result == null || !result.ok) {
                if (result == null || TextUtils.isEmpty(result.message)) Notify.show(R.string.remote_trust_config_load_failed);
                else Notify.show(result.message);
                renderRemoteConfigList(activity, binding, state);
                return;
            }
            JsonObject data = result == null || result.data == null || !result.data.isJsonObject() ? new JsonObject() : result.data.getAsJsonObject();
            handleHomeSites(activity, binding, state, payload, sitesFromData(data));
        });
    }

    private static void handleHomeSites(FragmentActivity activity, Binding binding, ConfigDialogState state, JsonObject payload, JsonArray sites) {
        if (sites == null || sites.size() == 0) {
            Notify.show(R.string.remote_trust_config_home_empty);
            renderRemoteConfigList(activity, binding, state);
            return;
        }
        binding.siteCache.put(siteCacheKey(binding, payload), sites);
        applyHomeNameFromSites(state.items, payload, sites);
        showHomeSitePicker(activity, binding, state, payload, sites);
    }

    private static JsonArray parseLocalSites(JsonObject payload) throws Exception {
        Config config = new Config().type(0).url(safe(payload, "url")).name(safe(payload, "name"));
        return sitesFromData(RemoteConfigSiteParser.parse(config, safe(payload, "homeKey"), safe(payload, "homeName")));
    }

    private static JsonArray sitesFromData(JsonObject data) {
        return data != null && data.has("sites") && data.get("sites").isJsonArray() ? data.getAsJsonArray("sites") : new JsonArray();
    }

    private static void showHomeSitePicker(FragmentActivity activity, Binding binding, ConfigDialogState state, JsonObject payload, JsonArray sites) {
        state.homePicking = true;
        updateConfigActions(activity, state);
        state.content.removeAllViews();
        for (JsonElement element : sites) {
            if (!element.isJsonObject()) continue;
            JsonObject site = element.getAsJsonObject();
            String keyValue = safe(site, "key");
            boolean setting = TextUtils.equals(state.settingHomeKey, keyValue);
            LinearLayoutCompat item = card(activity);
            item.setBackground(configItemBackground(activity, bool(site, "selected"), false));
            item.setAlpha(TextUtils.isEmpty(state.settingHomeKey) || setting ? 1.0f : 0.55f);
            item.setClickable(TextUtils.isEmpty(state.settingHomeKey));
            item.setFocusable(TextUtils.isEmpty(state.settingHomeKey));
            LinearLayoutCompat top = row(activity);
            MaterialTextView name = text(activity, safe(site, "name"), 14, "#202124", true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(name, weight());
            if (setting) top.addView(siteStateLabel(activity, R.string.remote_trust_config_home_setting, "#174EA6"), fixed(activity, 74, 28));
            else if (bool(site, "selected")) top.addView(siteStateLabel(activity, R.string.remote_trust_config_current, "#137333"), fixed(activity, 58, 28));
            item.addView(top, matchWrap());
            MaterialTextView key = text(activity, safe(site, "key"), 12, "#5F6368", false);
            key.setPadding(0, dp(activity, 4), 0, 0);
            key.setMaxLines(1);
            key.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(key, matchWrap());
            item.setOnClickListener(v -> setRemoteHomeSite(activity, binding, state, payload, sites, site));
            state.content.addView(item, topMargin(matchWrap(), 8));
        }
        if (state.contentScroll != null && TextUtils.isEmpty(state.settingHomeKey)) state.contentScroll.post(() -> state.contentScroll.scrollTo(0, 0));
    }

    private static MaterialTextView siteStateLabel(Context context, int resId, String color) {
        MaterialTextView view = text(context, context.getString(resId), 12, color, true);
        view.setGravity(Gravity.CENTER);
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private static void setRemoteHomeSite(FragmentActivity activity, Binding binding, ConfigDialogState state, JsonObject payload, JsonArray sites, JsonObject site) {
        String siteKey = safe(site, "key");
        if (TextUtils.isEmpty(siteKey) || state.homeStatus == HOME_STATUS_SETTING) return;
        state.settingHomeKey = "";
        state.homeStatusKey = configKey(payload);
        state.homeStatusName = safe(site, "name");
        state.homeStatus = HOME_STATUS_SETTING;
        state.selected = findConfig(state.items, payload);
        renderRemoteConfigList(activity, binding, state);
        JsonObject next = payload.deepCopy();
        next.addProperty("key", siteKey);
        sendCommand(activity, binding, "config.home", next, false, command -> {
            RemoteCommandResult result = command == null ? null : command.result;
            if (result == null || !result.ok) {
                state.homeStatus = HOME_STATUS_FAILED;
                if (result == null || TextUtils.isEmpty(result.message)) Notify.show(R.string.remote_trust_config_load_failed);
                else Notify.show(result.message);
                renderRemoteConfigList(activity, binding, state);
                return;
            }
            JsonArray latest = resultSites(result, sites);
            markSelectedSite(latest, site);
            binding.siteCache.put(siteCacheKey(binding, payload), latest);
            markActive(state.items, payload);
            applyHomeNameFromSites(state.items, payload, latest);
            if (TextUtils.isEmpty(safe(findConfig(state.items, payload), "homeName"))) setHomeName(state.items, payload, safe(site, "name"));
            state.homeStatus = HOME_STATUS_SUCCESS;
            state.homeStatusName = safe(site, "name");
            state.selected = findConfig(state.items, payload);
            binding.configCache.put(remoteCacheKey(binding), state.items);
            renderRemoteConfigList(activity, binding, state);
        }, e -> {
            state.homeStatus = HOME_STATUS_FAILED;
            if (state.dialog != null && state.dialog.isShowing()) renderRemoteConfigList(activity, binding, state);
        });
    }

    private static JsonArray resultSites(RemoteCommandResult result, JsonArray fallback) {
        if (result != null && result.data != null && result.data.isJsonObject()) {
            JsonObject data = result.data.getAsJsonObject();
            if (data.has("sites") && data.get("sites").isJsonArray()) return data.getAsJsonArray("sites");
        }
        return fallback == null ? new JsonArray() : fallback;
    }

    private static void confirmConfigDelete(FragmentActivity activity, Binding binding, ConfigDialogState state, JsonObject payload) {
        if (payload == null) return;
        ChoiceDialog.showConfirm(activity, R.string.remote_trust_config_delete, activity.getString(R.string.remote_trust_config_delete_message, payload.has("url") ? payload.get("url").getAsString() : ""), R.string.dialog_confirm, () -> runConfigCommand(activity, binding, state, "config.delete", payload, () -> {
            removeConfig(state.items, payload);
            binding.configCache.put(remoteCacheKey(binding), state.items);
            state.selected = null;
            renderRemoteConfigList(activity, binding, state);
        }));
    }

    private static void prefetchVodSites(FragmentActivity activity, Binding binding, ConfigDialogState state) {
        if (state == null || state.items == null) return;
        for (JsonElement element : state.items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (payloadType(item) != 0) continue;
            if (!bool(item, "active")) continue;
            JsonObject payload = configPayload(item);
            String key = siteCacheKey(binding, payload);
            if (binding.siteCache.containsKey(key)) continue;
            Task.execute(() -> {
                try {
                    JsonArray sites = parseLocalSites(payload);
                    if (sites.size() == 0) throw new IllegalStateException("Empty sites");
                    App.post(() -> cachePrefetchedSites(activity, binding, state, payload, key, sites));
                } catch (Throwable e) {
                    App.post(() -> fetchCommandQuiet(activity, binding, "config.sites", payload, command -> {
                        RemoteCommandResult result = command == null ? null : command.result;
                        JsonObject data = result == null || result.data == null || !result.data.isJsonObject() ? new JsonObject() : result.data.getAsJsonObject();
                        JsonArray sites = sitesFromData(data);
                        if (sites.size() == 0) return;
                        cachePrefetchedSites(activity, binding, state, payload, key, sites);
                    }));
                }
            });
        }
    }

    private static void cachePrefetchedSites(FragmentActivity activity, Binding binding, ConfigDialogState state, JsonObject payload, String key, JsonArray sites) {
        binding.siteCache.put(key, sites);
        applyHomeNameFromSites(state.items, payload, sites);
        if (state.dialog != null && state.dialog.isShowing() && state.type == 0 && !state.homePicking && !state.adding && !state.editing) {
            renderRemoteConfigList(activity, binding, state);
        }
    }

    private static void fetchCommandQuiet(FragmentActivity activity, Binding binding, String type, JsonObject payload, CommandHandler handler) {
        RemoteProfile profile = currentProfile(binding);
        DeviceRow selected = selectedRow(profile, binding);
        if (profile == null || selected == null) return;
        Task.execute(() -> {
            try {
                RemoteClient client = new RemoteClient(profile);
                CommandResponse response = client.createCommand(selected.group, selected.device.deviceId, type, payload);
                String commandId = response == null ? "" : response.commandId;
                if (TextUtils.isEmpty(commandId) && response != null && response.command != null) commandId = response.command.id;
                RemoteCommand command = waitCommand(client, selected.group, commandId, response == null ? null : response.command);
                App.post(() -> {
                    if (handler != null) handler.handle(command);
                });
            } catch (Throwable ignored) {
            }
        });
    }

    private static String remoteCacheKey(Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        DeviceRow selected = selectedRow(profile, binding);
        if (profile == null || selected == null) return "";
        return profile.serverOrigin + "|" + selected.group.groupId + "|" + selected.device.deviceId;
    }

    private static String siteCacheKey(Binding binding, JsonObject payload) {
        return remoteCacheKey(binding) + "|site|" + payloadType(payload) + "|" + safe(payload, "url");
    }

    private static String configKey(JsonObject payload) {
        return payloadType(payload) + "|" + safe(payload, "url");
    }

    private static void clearRemoteCache(Binding binding) {
        binding.configCache.clear();
        binding.siteCache.clear();
    }

    private static void updateConfigCacheFromResult(Binding binding, ConfigDialogState state, RemoteCommandResult result) {
        if (result == null || result.data == null || !result.data.isJsonObject()) return;
        JsonObject data = result.data.getAsJsonObject();
        if (!data.has("items") || !data.get("items").isJsonArray()) return;
        JsonArray items = uniqueConfigs(data.getAsJsonArray("items"));
        binding.configCache.put(remoteCacheKey(binding), items);
        if (state != null) state.items = items;
    }

    private static JsonObject defaultSelected(JsonArray items) {
        for (JsonElement element : items) if (element.isJsonObject() && bool(element.getAsJsonObject(), "active")) return element.getAsJsonObject();
        for (JsonElement element : items) if (element.isJsonObject()) return element.getAsJsonObject();
        return null;
    }

    private static JsonObject findConfig(JsonArray items, JsonObject target) {
        if (items == null || target == null) return null;
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (sameConfig(item, target)) return item;
        }
        return null;
    }

    private static boolean sameConfig(JsonObject a, JsonObject b) {
        return a != null && b != null && payloadType(a) == payloadType(b) && TextUtils.equals(safe(a, "url"), safe(b, "url"));
    }

    private static void upsertConfig(JsonArray items, JsonObject payload) {
        if (items == null || payload == null) return;
        JsonObject current = findConfig(items, payload);
        if (current == null) {
            JsonObject item = payload.deepCopy();
            item.addProperty("typeName", typeName(payloadType(payload)));
            item.addProperty("active", false);
            items.add(item);
            return;
        }
        current.addProperty("name", safe(payload, "name"));
        current.addProperty("url", safe(payload, "url"));
    }

    private static void removeConfig(JsonArray items, JsonObject payload) {
        JsonObject current = findConfig(items, payload);
        if (items != null && current != null) items.remove(current);
    }

    private static void markActive(JsonArray items, JsonObject payload) {
        if (items == null || payload == null) return;
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (payloadType(item) == payloadType(payload)) item.addProperty("active", sameConfig(item, payload));
        }
    }

    private static String activeConfigKey(JsonArray items, int type) {
        if (items == null) return "";
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (payloadType(item) == type && bool(item, "active")) return configKey(item);
        }
        return "";
    }

    private static void restoreActiveConfig(JsonArray items, int type, String key) {
        if (items == null) return;
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (payloadType(item) == type) item.addProperty("active", !TextUtils.isEmpty(key) && TextUtils.equals(configKey(item), key));
        }
    }

    private static void setHomeName(JsonArray items, JsonObject payload, String homeName) {
        JsonObject current = findConfig(items, payload);
        if (current != null) current.addProperty("homeName", homeName);
    }

    private static void applyHomeNameFromSites(JsonArray items, JsonObject payload, JsonArray sites) {
        for (JsonElement element : sites) {
            if (!element.isJsonObject()) continue;
            JsonObject site = element.getAsJsonObject();
            if (bool(site, "selected")) {
                setHomeName(items, payload, safe(site, "name"));
                return;
            }
        }
    }

    private static void markSelectedSite(JsonArray sites, JsonObject selected) {
        for (JsonElement element : sites) {
            if (!element.isJsonObject()) continue;
            JsonObject site = element.getAsJsonObject();
            site.addProperty("selected", sameSite(site, selected));
        }
    }

    private static boolean sameSite(JsonObject a, JsonObject b) {
        return a != null && b != null && TextUtils.equals(safe(a, "key"), safe(b, "key"));
    }

    private static String typeName(int type) {
        if (type == 1) return App.get().getString(R.string.remote_trust_config_type_live);
        if (type == 2) return App.get().getString(R.string.remote_trust_config_type_wall);
        return App.get().getString(R.string.remote_trust_config_type_vod);
    }

    private static void confirmRemoteSync(FragmentActivity activity, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        DeviceRow selected = selectedRow(profile, binding);
        if (profile == null || selected == null) {
            Notify.show(R.string.remote_trust_no_device_selected);
            return;
        }
        if (remoteSyncRunningFor(profile, selected)) {
            binding.page = PAGE_DETAIL;
            binding.lastResult = remoteSyncResultFor(profile, selected);
            render(activity, binding);
            return;
        }
        if (TextUtils.equals(profile.deviceId, selected.device.deviceId)) {
            Notify.show(R.string.remote_trust_sync_self_forbidden);
            return;
        }
        DialogRemoteTrustConfirmBinding dialogBinding = DialogRemoteTrustConfirmBinding.inflate(LayoutInflater.from(activity));
        dialogBinding.title.setText(R.string.remote_trust_action_sync);
        dialogBinding.message.setText(activity.getString(R.string.remote_trust_sync_confirm, deviceName(selected.device)));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setView(dialogBinding.getRoot())
                .create();
        dialogBinding.negative.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.positive.setOnClickListener(v -> {
            dialog.dismiss();
            startRemoteSync(activity, binding, selected);
        });
        dialog.setOnShowListener(d -> configureSmallModalWindow(activity, dialog));
        showModal(dialog);
    }

    private static void startRemoteSync(FragmentActivity activity, Binding binding, DeviceRow selected) {
        RemoteProfile profile = currentProfile(binding);
        if (profile == null || selected == null) {
            Notify.show(R.string.remote_trust_no_device_selected);
            return;
        }
        setBusy(binding, true);
        binding.page = PAGE_DETAIL;
        JsonObject creating = syncStatus("creating");
        binding.lastResult = formatSyncProgress(activity, selected, creating, false);
        rememberRemoteSync(profile, selected, "", binding.lastResult, syncStep(creating, false), true);
        render(activity, binding);
        Task.execute(() -> {
            try {
                RemoteClient client = new RemoteClient(profile);
                client.register();
                JsonObject response = client.createSync(selected.group, profile.deviceId, selected.device.deviceId, App.gson().toJsonTree(SyncOptions.defaults()).getAsJsonObject());
                JsonObject sync = object(response, "sync");
                String syncId = safe(sync, "id");
                if (TextUtils.isEmpty(syncId)) throw new IllegalStateException(activity.getString(R.string.remote_trust_sync_missing_id));
                postSyncProgress(activity, binding, profile, selected, syncId, sync, false, true);
                boolean done = isSyncTerminal(sync);
                for (int i = 0; i < 60 && !done; i++) {
                    Thread.sleep(i == 0 ? 1200 : 2000);
                    JsonObject detail = client.getSync(selected.group, syncId);
                    sync = object(detail, "sync");
                    done = isSyncTerminal(sync);
                    postSyncProgress(activity, binding, profile, selected, syncId, sync, false, true);
                }
                postSyncProgress(activity, binding, profile, selected, syncId, sync, !done, false);
            } catch (Throwable e) {
                failRemoteSync(activity, binding, profile, selected, e);
            }
        });
    }

    private static void postSyncProgress(FragmentActivity activity, Binding binding, RemoteProfile profile, DeviceRow selected, String syncId, JsonObject sync, boolean timeout, boolean busy) {
        String result = formatSyncProgress(App.get(), selected, sync, timeout);
        rememberRemoteSync(profile, selected, syncId, result, syncStep(sync, timeout), busy);
        App.post(() -> {
            if (binding.dialog == null || !binding.dialog.isShowing()) return;
            setBusy(binding, busy);
            binding.page = PAGE_DETAIL;
            binding.lastResult = result;
            render(activity, binding);
        });
    }

    private static void failRemoteSync(FragmentActivity activity, Binding binding, RemoteProfile profile, DeviceRow selected, Throwable e) {
        JsonObject failed = syncStatus("failed");
        failed.addProperty("message", empty(e.getMessage()));
        String result = formatSyncProgress(App.get(), selected, failed, false);
        rememberRemoteSync(profile, selected, "", result, syncStep(failed, false), false);
        App.post(() -> {
            setBusy(binding, false);
            binding.page = PAGE_DETAIL;
            binding.lastResult = result;
            Notify.show(e.getMessage());
            if (binding.dialog != null && binding.dialog.isShowing()) render(activity, binding);
        });
    }

    private static boolean isSyncTerminal(JsonObject sync) {
        String status = safe(sync, "status");
        return TextUtils.equals(status, "done") || status.endsWith("_failed") || resultFailed(sync, "exportResult") || resultFailed(sync, "restoreResult");
    }

    private static JsonObject syncStatus(String status) {
        JsonObject object = new JsonObject();
        object.addProperty("status", status);
        return object;
    }

    private static boolean resultFailed(JsonObject sync, String key) {
        JsonObject result = object(sync, key);
        return result.has("ok") && !bool(result, "ok");
    }

    private static String formatSyncProgress(Context context, DeviceRow selected, JsonObject sync, boolean timeout) {
        StringBuilder builder = new StringBuilder();
        int step = syncStep(sync, timeout);
        boolean failed = syncFailed(sync, timeout);
        builder.append(context.getString(R.string.remote_trust_action_sync));
        builder.append('\n').append(context.getString(R.string.remote_trust_sync_progress, step, REMOTE_SYNC_STEPS));
        builder.append('\n').append(context.getString(R.string.remote_trust_result_status)).append(": ").append(syncStateText(context, sync, timeout));
        builder.append('\n').append(syncStepLines(context, step, failed, TextUtils.equals(safe(sync, "status"), "done")));
        builder.append('\n').append(context.getString(R.string.remote_trust_sync_device)).append(": ").append(deviceName(selected.device));
        String parts = syncPartsText(context, object(sync, "parts"));
        if (!TextUtils.isEmpty(parts)) builder.append('\n').append(context.getString(R.string.remote_trust_sync_uploaded)).append(": ").append(parts);
        String message = syncMessage(sync);
        if (!TextUtils.isEmpty(message)) builder.append('\n').append(context.getString(R.string.remote_trust_sync_message)).append(": ").append(message);
        String counts = syncCounts(context, sync);
        if (!TextUtils.isEmpty(counts)) builder.append('\n').append(counts);
        return builder.toString();
    }

    private static int syncStep(JsonObject sync, boolean timeout) {
        if (timeout || syncFailed(sync, false)) return Math.max(1, Math.min(REMOTE_SYNC_STEPS, syncStepByStatus(safe(sync, "status"))));
        return syncStepByStatus(safe(sync, "status"));
    }

    private static int syncStepByStatus(String status) {
        if (TextUtils.equals(status, "done")) return 5;
        if (status.contains("restore")) return 4;
        if (TextUtils.equals(status, "exported")) return 4;
        if (status.contains("export") || status.contains("upload")) return 3;
        if (TextUtils.equals(status, "exporting")) return 3;
        if (TextUtils.equals(status, "created")) return 2;
        return 1;
    }

    private static boolean syncFailed(JsonObject sync, boolean timeout) {
        return timeout || resultFailed(sync, "exportResult") || resultFailed(sync, "restoreResult") || safe(sync, "status").endsWith("_failed") || TextUtils.equals(safe(sync, "status"), "failed");
    }

    private static String syncStepLines(Context context, int current, boolean failed, boolean completed) {
        StringBuilder builder = new StringBuilder();
        addSyncStepLine(context, builder, 1, current, failed, completed, R.string.remote_trust_sync_step_create);
        addSyncStepLine(context, builder, 2, current, failed, completed, R.string.remote_trust_sync_step_export);
        addSyncStepLine(context, builder, 3, current, failed, completed, R.string.remote_trust_sync_step_upload);
        addSyncStepLine(context, builder, 4, current, failed, completed, R.string.remote_trust_sync_step_restore);
        addSyncStepLine(context, builder, 5, current, failed, completed, R.string.remote_trust_sync_step_finish);
        return builder.toString();
    }

    private static void addSyncStepLine(Context context, StringBuilder builder, int step, int current, boolean failed, boolean completed, int name) {
        if (builder.length() > 0) builder.append('\n');
        int state = completed || step < current ? R.string.remote_trust_sync_step_done : step == current ? (failed ? R.string.remote_trust_sync_step_failed : R.string.remote_trust_sync_step_current) : R.string.remote_trust_sync_step_pending;
        builder.append(context.getString(R.string.remote_trust_sync_step_line, step, context.getString(name), context.getString(state)));
    }

    private static String syncStateText(Context context, JsonObject sync, boolean timeout) {
        if (timeout) return context.getString(R.string.remote_trust_sync_state_timeout);
        if (syncFailed(sync, false)) return context.getString(R.string.remote_trust_sync_state_failed);
        String status = safe(sync, "status");
        if (TextUtils.equals(status, "creating")) return context.getString(R.string.remote_trust_sync_state_creating);
        if (TextUtils.equals(status, "done")) return context.getString(R.string.remote_trust_sync_state_done);
        if (TextUtils.equals(status, "exported")) return context.getString(R.string.remote_trust_sync_state_exported);
        if (TextUtils.equals(status, "exporting")) return context.getString(R.string.remote_trust_sync_state_exporting);
        return context.getString(R.string.remote_trust_sync_state_created);
    }

    private static String syncMessage(JsonObject sync) {
        String message = safe(sync, "message");
        if (!TextUtils.isEmpty(message)) return message;
        JsonObject export = object(sync, "exportResult");
        JsonObject restore = object(sync, "restoreResult");
        message = safe(restore, "message");
        if (TextUtils.isEmpty(message)) message = safe(export, "message");
        return message;
    }

    private static String syncCounts(Context context, JsonObject sync) {
        JsonObject restore = object(sync, "restoreResult");
        JsonObject data = object(restore, "data");
        if (!data.has("syncFiles") && !data.has("loginStateFiles")) return "";
        return context.getString(R.string.remote_trust_sync_result_counts, safe(data, "syncFiles"), safe(data, "loginStateFiles"));
    }

    private static String syncPartsText(Context context, JsonObject parts) {
        if (parts.size() == 0) return "";
        List<String> result = new ArrayList<>();
        addSyncPart(context, result, parts, "backup", R.string.remote_trust_sync_part_backup);
        addSyncPart(context, result, parts, "remoteRelay", R.string.remote_trust_sync_part_remote_relay);
        addSyncPart(context, result, parts, "syncFiles", R.string.remote_trust_sync_part_sync_files);
        addSyncPart(context, result, parts, "loginStateFiles", R.string.remote_trust_sync_part_login_state);
        return TextUtils.join(", ", result);
    }

    private static void addSyncPart(Context context, List<String> result, JsonObject parts, String key, int label) {
        JsonObject part = object(parts, key);
        if (part.size() == 0) return;
        long size = longValue(part, "size");
        result.add(context.getString(label) + (size > 0 ? " " + formatBytes(size) : ""));
    }

    private static void rememberRemoteSync(RemoteProfile profile, DeviceRow selected, String syncId, String result, int step, boolean running) {
        if (profile == null || selected == null || selected.group == null || selected.device == null) return;
        synchronized (REMOTE_SYNC_LOCK) {
            remoteSyncState.serverOrigin = profile.serverOrigin;
            remoteSyncState.groupId = selected.group.groupId;
            remoteSyncState.targetDeviceId = selected.device.deviceId;
            remoteSyncState.targetName = deviceName(selected.device);
            remoteSyncState.syncId = syncId;
            remoteSyncState.result = result;
            remoteSyncState.step = step;
            remoteSyncState.running = running;
            remoteSyncState.updatedAt = System.currentTimeMillis();
        }
    }

    private static String remoteSyncResultFor(RemoteProfile profile, DeviceRow selected) {
        synchronized (REMOTE_SYNC_LOCK) {
            return sameRemoteSync(profile, selected) ? remoteSyncState.result : "";
        }
    }

    private static boolean remoteSyncRunningFor(RemoteProfile profile, DeviceRow selected) {
        synchronized (REMOTE_SYNC_LOCK) {
            return sameRemoteSync(profile, selected) && remoteSyncState.running;
        }
    }

    private static boolean sameRemoteSync(RemoteProfile profile, DeviceRow selected) {
        return profile != null && selected != null && selected.group != null && selected.device != null
                && TextUtils.equals(remoteSyncState.serverOrigin, profile.serverOrigin)
                && TextUtils.equals(remoteSyncState.groupId, selected.group.groupId)
                && TextUtils.equals(remoteSyncState.targetDeviceId, selected.device.deviceId);
    }

    private static void sendCommand(FragmentActivity activity, Binding binding, String type, JsonObject payload) {
        sendCommand(activity, binding, type, payload, null);
    }

    private static void sendCommand(FragmentActivity activity, Binding binding, String type, JsonObject payload, CommandHandler handler) {
        sendCommand(activity, binding, type, payload, true, handler);
    }

    private static void sendCommand(FragmentActivity activity, Binding binding, String type, JsonObject payload, boolean renderResult, CommandHandler handler) {
        sendCommand(activity, binding, type, payload, renderResult, handler, null);
    }

    private static void sendCommand(FragmentActivity activity, Binding binding, String type, JsonObject payload, boolean renderResult, CommandHandler handler, CommandErrorHandler errorHandler) {
        RemoteProfile profile = currentProfile(binding);
        DeviceRow selected = selectedRow(profile, binding);
        if (profile == null || selected == null) {
            Notify.show(R.string.remote_trust_no_device_selected);
            return;
        }
        setBusy(binding, true);
        binding.lastResult = "";
        Task.execute(() -> {
            try {
                RemoteClient client = new RemoteClient(profile);
                CommandResponse response = client.createCommand(selected.group, selected.device.deviceId, type, payload);
                String commandId = response == null ? "" : response.commandId;
                if (TextUtils.isEmpty(commandId) && response != null && response.command != null) commandId = response.command.id;
                RemoteCommand command = waitCommand(client, selected.group, commandId, response == null ? null : response.command);
                String result = formatCommand(activity, type, command);
                App.post(() -> {
                    setBusy(binding, false);
                    binding.lastResult = result;
                    if (renderResult) {
                        binding.page = PAGE_DETAIL;
                        render(activity, binding);
                    }
                    if (handler != null) handler.handle(command);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    setBusy(binding, false);
                    binding.lastResult = e.getMessage();
                    Notify.show(e.getMessage());
                    if (renderResult) render(activity, binding);
                    if (errorHandler != null) errorHandler.handle(e);
                });
            }
        });
    }

    private static RemoteCommand waitCommand(RemoteClient client, RemoteGroup group, String commandId, RemoteCommand fallback) throws Exception {
        if (TextUtils.isEmpty(commandId)) return fallback;
        RemoteCommand command = fallback;
        for (int i = 0; i < 8; i++) {
            Thread.sleep(i == 0 ? 700 : 1000);
            CommandDetailResponse detail = client.getCommand(group, commandId);
            if (detail != null && detail.command != null) command = detail.command;
            if (command != null && ("done".equals(command.status) || "failed".equals(command.status))) break;
        }
        return command;
    }

    private static String formatCommand(Context context, String type, RemoteCommand command) {
        if (command == null) return context.getString(R.string.remote_trust_empty_result);
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.remote_trust_result_action)).append(": ").append(commandName(context, type));
        builder.append('\n').append(context.getString(R.string.remote_trust_result_status)).append(": ").append(commandStatus(command.status));
        RemoteCommandResult result = command.result;
        if (result == null) {
            builder.append('\n').append(context.getString(R.string.remote_trust_command_waiting));
            return builder.toString();
        }
        builder.append('\n').append(context.getString(R.string.remote_trust_result_state)).append(": ").append(result.ok ? context.getString(R.string.remote_trust_command_success) : context.getString(R.string.remote_trust_command_failed));
        if (!TextUtils.isEmpty(result.message)) builder.append(": ").append(result.message);
        String data = formatData(result.data);
        if (!TextUtils.isEmpty(data)) builder.append("\n\n").append(data);
        return builder.toString();
    }

    private static String commandName(Context context, String type) {
        if ("device.status".equals(type)) return context.getString(R.string.remote_trust_action_status);
        if ("config.list".equals(type)) return context.getString(R.string.remote_trust_config_list);
        if ("config.upsert".equals(type)) return context.getString(R.string.remote_trust_config_upsert);
        if ("config.use".equals(type)) return context.getString(R.string.remote_trust_config_use);
        if ("config.delete".equals(type)) return context.getString(R.string.remote_trust_config_delete);
        if ("config.sites".equals(type)) return context.getString(R.string.remote_trust_config_home);
        if ("config.home".equals(type)) return context.getString(R.string.remote_trust_config_home);
        if ("remoteSync.export".equals(type) || "remoteSync.restore".equals(type)) return context.getString(R.string.remote_trust_action_sync);
        if ("action.search".equals(type)) return context.getString(R.string.remote_trust_action_search);
        if ("action.push".equals(type)) return context.getString(R.string.remote_trust_action_push);
        if ("log.recent".equals(type) || "device.log.recent".equals(type)) return context.getString(R.string.remote_trust_action_log);
        return type;
    }

    private static String commandStatus(String status) {
        return TextUtils.isEmpty(status) ? "queued" : status;
    }

    private static String formatData(JsonElement data) {
        if (data == null || data.isJsonNull()) return "";
        if (data.isJsonObject()) {
            JsonObject object = data.getAsJsonObject();
            if (object.has("lines") && object.get("lines").isJsonArray()) return lines(object.getAsJsonArray("lines"));
            if (object.has("items") && object.get("items").isJsonArray()) return configItems(object.getAsJsonArray("items"));
            if (object.has("sites") && object.get("sites").isJsonArray()) return siteItems(object.getAsJsonArray("sites"));
            if (object.has("syncFiles") || object.has("loginStateFiles")) return syncSummary(object);
            return keyValues(object);
        }
        if (data.isJsonArray()) return arrayValues(data.getAsJsonArray());
        return data.getAsString();
    }

    private static String configItems(JsonArray array) {
        StringBuilder builder = new StringBuilder();
        builder.append(App.get().getString(R.string.remote_trust_result_config_count, array.size()));
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) continue;
            JsonObject item = array.get(i).getAsJsonObject();
            builder.append('\n').append(bool(item, "active") ? "* " : "- ");
            builder.append(safe(item, "typeName")).append(" · ").append(safe(item, "desc"));
            String url = safe(item, "url");
            if (!TextUtils.isEmpty(url)) builder.append('\n').append("  ").append(url);
        }
        return builder.toString();
    }

    private static String siteItems(JsonArray array) {
        StringBuilder builder = new StringBuilder();
        builder.append(App.get().getString(R.string.remote_trust_result_site_count, array.size()));
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) continue;
            JsonObject item = array.get(i).getAsJsonObject();
            builder.append('\n').append(bool(item, "selected") ? "* " : "- ");
            builder.append(safe(item, "name")).append(" · ").append(safe(item, "key"));
        }
        return builder.toString();
    }

    private static String syncSummary(JsonObject object) {
        StringBuilder builder = new StringBuilder();
        if (object.has("syncId")) builder.append("Sync ID: ").append(safe(object, "syncId")).append('\n');
        builder.append("Files: ").append(safe(object, "syncFiles"));
        builder.append('\n').append("Login files: ").append(safe(object, "loginStateFiles"));
        return builder.toString();
    }

    private static String keyValues(JsonObject object) {
        StringBuilder builder = new StringBuilder();
        for (String key : object.keySet()) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(label(key)).append(": ");
            if (value.isJsonObject()) builder.append(keyValues(value.getAsJsonObject()).replace("\n", "\n  "));
            else if (value.isJsonArray()) builder.append(arrayValues(value.getAsJsonArray()));
            else builder.append(value.getAsString());
        }
        return builder.toString();
    }

    private static String arrayValues(JsonArray array) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) builder.append('\n');
            JsonElement value = array.get(i);
            builder.append("- ");
            if (value.isJsonObject()) builder.append(keyValues(value.getAsJsonObject()).replace("\n", "\n  "));
            else builder.append(value.isJsonNull() ? "" : value.getAsString());
        }
        return builder.toString();
    }

    private static String label(String key) {
        if (TextUtils.isEmpty(key)) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) builder.append(' ');
            builder.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return builder.toString();
    }

    private static String safe(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try {
            return object.get(key).getAsString();
        } catch (Throwable e) {
            return "";
        }
    }

    private static JsonObject object(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonObject()) return new JsonObject();
        return object.getAsJsonObject(key);
    }

    private static long longValue(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0;
            return object.get(key).getAsLong();
        } catch (Throwable e) {
            return 0;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && object.get(key).getAsBoolean();
        } catch (Throwable e) {
            return false;
        }
    }

    private static String lines(JsonArray array) {
        StringBuilder builder = new StringBuilder();
        builder.append(App.get().getString(R.string.remote_trust_result_log_lines, array.size())).append('\n');
        int start = Math.max(0, array.size() - 120);
        for (int i = start; i < array.size(); i++) {
            if (i > start) builder.append('\n');
            builder.append(array.get(i).getAsString());
        }
        return builder.toString();
    }

    private static RemoteProfile prepare(Binding binding, boolean keepOnline) {
        String serverUrl = textOf(binding.server);
        return RemoteStore.prepareProfile(serverUrl, binding.enabled.isChecked(), keepOnline);
    }

    private static RemoteProfile currentProfile(Binding binding) {
        String serverUrl = textOf(binding.server);
        if (TextUtils.isEmpty(serverUrl)) return RemoteStore.firstProfile();
        String origin = RemoteTokens.normalizeOrigin(serverUrl);
        return TextUtils.isEmpty(origin) ? null : RemoteStore.getProfileByOrigin(origin);
    }

    private static void requestFileAccess(FragmentActivity activity, Binding binding) {
        PermissionUtil.requestFile(activity, granted -> {
            if (granted) RemoteStore.save(RemoteStore.get());
            render(activity, binding);
        });
    }

    private static void resetServerInput(Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) profile = RemoteStore.firstProfile();
        if (profile == null) return;
        binding.server.setText(TextUtils.isEmpty(profile.serverUrl) ? profile.serverOrigin : profile.serverUrl);
        binding.enabled.setChecked(profile.enabled);
    }

    private static void confirmClear(FragmentActivity activity, Binding binding) {
        ChoiceDialog.showConfirm(activity, R.string.remote_trust_reset_local, activity.getString(R.string.remote_trust_clear_message), R.string.dialog_confirm, () -> {
            RemoteStore.clear();
            RemoteAgent.get().stop();
            RemoteAgentService.stop(activity);
            binding.initialized = false;
            clearBindCode(binding);
            binding.selectedGroupId = "";
            binding.selectedDeviceId = "";
            binding.serviceStateText = "";
            binding.serviceDetailText = "";
            binding.diagnostics = "";
            binding.statusExpanded = false;
            binding.advancedExpanded = false;
            binding.autoBindAttempted = false;
            resetDetect(binding);
            binding.creatingBindCode = false;
            clearRemoteCache(binding);
            binding.page = PAGE_DEVICES;
            render(activity, binding);
        });
    }

    private static void confirmDeleteDevice(FragmentActivity activity, Binding binding, DeviceRow row) {
        if (row == null || row.group == null || row.device == null) return;
        ChoiceDialog.showConfirm(activity, R.string.remote_trust_delete_device, activity.getString(R.string.remote_trust_delete_device_message, deviceName(row.device)), R.string.dialog_confirm, () -> {
            RemoteProfile profile = currentProfile(binding);
            if (profile == null) return;
            if (RemoteStore.removeDevice(profile.serverOrigin, row.group.groupId, row.device.deviceId)) {
                binding.selectedGroupId = "";
                binding.selectedDeviceId = "";
                Notify.show(R.string.remote_trust_delete_device_done);
                render(activity, binding);
            }
        });
    }

    private static void copyCode(Context context, Binding binding) {
        if (TextUtils.isEmpty(binding.bindCode)) return;
        copyText(context, context.getString(R.string.setting_remote_trust), binding.bindCode, R.string.remote_trust_bind_code_copied);
    }

    private static void copyText(Context context, String label, String text, int message) {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) return;
        manager.setPrimaryClip(ClipData.newPlainText(label, text));
        Notify.show(message);
    }

    private static String localStatus(Context context, RemoteProfile profile) {
        if (profile == null) return context.getString(R.string.remote_trust_no_profile);
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.remote_trust_server_url)).append(": ").append(displayOrigin(profile.serverOrigin));
        builder.append('\n').append(context.getString(R.string.remote_trust_device_id)).append(": ").append(shortId(profile.deviceId));
        builder.append('\n').append(currentStatusSummary(context, profile));
        return builder.toString();
    }

    private static String currentStatusSummary(Context context, RemoteProfile profile) {
        String status = profile.keepOnline ? context.getString(R.string.remote_trust_status_online) : context.getString(profile.enabled ? R.string.remote_trust_status_enabled : R.string.setting_disable);
        int groups = profile.groups == null ? 0 : profile.groups.size();
        int devices = deviceRows(profile).size();
        return context.getString(R.string.remote_trust_current_status_summary, status, groups, devices);
    }

    private static List<DeviceRow> deviceRows(RemoteProfile profile) {
        List<DeviceRow> rows = new ArrayList<>();
        if (profile == null || profile.groups == null) return rows;
        for (RemoteGroup group : profile.groups) {
            if (group == null || group.devices == null) continue;
            for (RemoteDevice device : group.devices) {
                if (device != null && !TextUtils.isEmpty(device.deviceId) && !TextUtils.equals(profile.deviceId, device.deviceId)) rows.add(new DeviceRow(group, device));
            }
        }
        return rows;
    }

    private static boolean hasGroups(RemoteProfile profile) {
        return profile != null && profile.groups != null && !profile.groups.isEmpty();
    }

    private static DeviceRow selectedRow(RemoteProfile profile, Binding binding) {
        if (profile == null) return null;
        for (DeviceRow row : deviceRows(profile)) {
            if (TextUtils.equals(row.group.groupId, binding.selectedGroupId) && TextUtils.equals(row.device.deviceId, binding.selectedDeviceId)) return row;
        }
        return null;
    }

    private static String firstGroupToken(RemoteProfile profile) {
        if (profile == null || profile.groups == null) return "";
        for (RemoteGroup group : profile.groups) if (group != null && !TextUtils.isEmpty(group.groupToken)) return group.groupToken;
        return "";
    }

    private static String deviceText(Context context, RemoteProfile profile, RemoteDevice device) {
        return deviceName(device) + " · " + deviceRole(context, profile, device) + " · " + deviceTypeText(context, device);
    }

    private static String deviceDetailText(Context context, RemoteProfile profile, RemoteGroup group, RemoteDevice device) {
        StringBuilder builder = new StringBuilder();
        builder.append(deviceRole(context, profile, device)).append(" · ").append(deviceState(context, device)).append(deviceTime(device));
        builder.append('\n').append(groupName(context, group));
        builder.append('\n').append(context.getString(R.string.remote_trust_device_id)).append(": ").append(shortId(device.deviceId));
        if (!TextUtils.isEmpty(device.appVersion)) builder.append('\n').append(context.getString(R.string.remote_trust_app_version)).append(": ").append(device.appVersion);
        return builder.toString();
    }

    private static String deviceName(RemoteDevice device) {
        return TextUtils.isEmpty(device.name) ? shortId(device.deviceId) : device.name;
    }

    private static String deviceState(Context context, RemoteDevice device) {
        return isDeviceOnline(device) ? context.getString(R.string.remote_trust_device_online) : context.getString(R.string.remote_trust_device_offline);
    }

    private static boolean isDeviceOnline(RemoteDevice device) {
        if (device == null || !device.online || device.lastSeen <= 0) return false;
        return System.currentTimeMillis() - device.lastSeen < DEVICE_ONLINE_STALE_MS;
    }

    private static String deviceTypeText(Context context, RemoteDevice device) {
        if (device.type == 0) return context.getString(R.string.sync_device_tv);
        if (device.type == 1) return context.getString(R.string.sync_device_mobile);
        return context.getString(R.string.remote_trust_sync_device);
    }

    private static int deviceTypeIcon(RemoteDevice device) {
        return device.type == 0 ? R.drawable.ic_sync_tv : R.drawable.ic_sync_phone;
    }

    private static String deviceTime(RemoteDevice device) {
        return device.lastSeen <= 0 ? "" : " · " + new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(new Date(device.lastSeen));
    }

    private static String selfSuffix(Context context, RemoteProfile profile, RemoteDevice device) {
        return profile != null && TextUtils.equals(profile.deviceId, device.deviceId) ? " · " + context.getString(R.string.remote_trust_self_device) : "";
    }

    private static String deviceRole(Context context, RemoteProfile profile, RemoteDevice device) {
        return profile != null && TextUtils.equals(profile.deviceId, device.deviceId) ? context.getString(R.string.remote_trust_self_device) : context.getString(R.string.remote_trust_controlled_device);
    }

    private static String groupName(Context context, RemoteGroup group) {
        return TextUtils.isEmpty(group.name) ? context.getString(R.string.remote_trust_group_title, shortId(group.groupId)) : group.name;
    }

    private static String formatCapabilities(Context context, ServerCapabilities server) {
        if (server == null) return "";
        RemoteCapabilities capabilities = server.capabilities == null ? new RemoteCapabilities() : server.capabilities;
        List<String> support = new ArrayList<>();
        support.add(context.getString(R.string.remote_trust_cap_device));
        if (capabilities.configManage) support.add(context.getString(R.string.remote_trust_cap_config));
        if (capabilities.remoteSync) support.add(context.getString(R.string.remote_trust_cap_sync));
        if (capabilities.pushAction) support.add(context.getString(R.string.remote_trust_cap_push));
        if (capabilities.recentLog) support.add(context.getString(R.string.remote_trust_cap_log));
        String supportText = support.isEmpty() ? context.getString(R.string.remote_trust_support_none) : TextUtils.join(", ", support);
        String info = context.getString(R.string.remote_trust_service_info,
                empty(server.serverMode),
                empty(server.relayMode),
                supportText,
                formatBytes(server.maxSyncPartBytes));
        if (TextUtils.equals(server.relayMode, "origin-token-memory") && !capabilities.persistentStorage) {
            info += "\n" + context.getString(R.string.remote_trust_service_memory_warning);
        }
        return info;
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "-";
        long mb = bytes / 1024 / 1024;
        return mb > 0 ? mb + " MB" : bytes + " B";
    }

    private static String empty(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private static String conciseError(Context context, Throwable e) {
        String message = e == null ? "" : e.getMessage();
        if (TextUtils.isEmpty(message)) return context.getString(R.string.remote_trust_service_request_failed);
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("failed to connect") || lower.contains("timeout") || lower.contains("timed out") || lower.contains("unable to resolve") || lower.contains("connection refused")) {
            return context.getString(R.string.remote_trust_service_connect_failed);
        }
        return shorten(message.replace('\n', ' '), 72);
    }

    private static String displayOrigin(String value) {
        if (TextUtils.isEmpty(value)) return "-";
        String text = value.replace("https://", "").replace("http://", "");
        if (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return shortenMiddle(text, 34);
    }

    private static String shorten(String value, int max) {
        if (TextUtils.isEmpty(value) || value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String shortenMiddle(String value, int max) {
        if (TextUtils.isEmpty(value) || value.length() <= max) return value;
        int keep = Math.max(8, (max - 1) / 2);
        return value.substring(0, keep) + "…" + value.substring(value.length() - keep);
    }

    private static String shortId(String value) {
        if (TextUtils.isEmpty(value)) return "";
        return value.length() <= 8 ? value : value.substring(value.length() - 8);
    }

    private static String textOf(TextInputEditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static void setBusy(Binding binding, boolean busy) {
        binding.busy = busy;
        binding.bindCodeButton.setEnabled(!busy && hasFreshBindCode(binding));
        binding.enableToggle.setEnabled(!busy);
        binding.statusButton.setEnabled(true);
        binding.serviceButton.setEnabled(true);
        if (binding.addDeviceButton != null) binding.addDeviceButton.setEnabled(currentProfile(binding) != null);
        if (binding.settingsBackButton != null) binding.settingsBackButton.setEnabled(true);
        binding.server.setEnabled(true);
        binding.enabled.setEnabled(!busy);
        binding.keepOnline.setEnabled(!busy);
        for (MaterialButton button : binding.actions) button.setEnabled(!busy);
    }

    private static void resetDetect(Binding binding) {
        binding.autoDetected = false;
        binding.autoDetectStarted = false;
        binding.detectingService = false;
        App.removeCallbacks(binding.detectRetry);
    }

    private static void scheduleDetectRetry(Binding binding) {
        if (binding.detectRetry == null || binding.dialog == null || !binding.dialog.isShowing()) return;
        App.post(binding.detectRetry, DETECT_RETRY_MS);
    }

    private static void detach(View view) {
        if (view == null || !(view.getParent() instanceof ViewGroup)) return;
        ((ViewGroup) view.getParent()).removeView(view);
    }

    private static LinearLayoutCompat dialogRoot(Context context) {
        LinearLayoutCompat root = new LinearLayoutCompat(context);
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(context, 2), dp(context, 4), dp(context, 2), 0);
        return root;
    }

    private static LinearLayoutCompat panelDialogRoot(Context context) {
        LinearLayoutCompat root = new LinearLayoutCompat(context);
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        root.setBackground(background(context, "#FFFFFF", Color.TRANSPARENT, 16));
        return root;
    }

    private static LinearLayoutCompat row(Context context) {
        LinearLayoutCompat row = new LinearLayoutCompat(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayoutCompat.HORIZONTAL);
        return row;
    }

    private static MaterialTextView sectionTitle(Context context, int resId) {
        return sectionTitle(context, context.getString(resId));
    }

    private static MaterialTextView sectionTitle(Context context, String value) {
        MaterialTextView view = text(context, value, 15, "#202124", true);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private static MaterialTextView caption(Context context, int resId) {
        return text(context, context.getString(resId), 12, "#5F6368", false);
    }

    private static MaterialTextView panel(Context context, String value) {
        MaterialTextView view = text(context, value, 13, "#3C4043", false);
        view.setTextIsSelectable(true);
        view.setLineSpacing(0, 1.08f);
        view.setPadding(dp(context, 10), dp(context, 9), dp(context, 10), dp(context, 9));
        view.setBackground(background(context, "#F8F9FA", Color.parseColor("#DADCE0"), 8));
        return view;
    }

    private static LinearLayoutCompat statusDetailPanel(Context context, RemoteProfile profile, Binding binding) {
        LinearLayoutCompat card = card(context);
        MaterialTextView title = text(context, context.getString(R.string.remote_trust_local_status), 14, "#202124", true);
        card.addView(title, matchWrap());
        MaterialTextView detail = text(context, localStatus(context, profile) + serviceSuffix(binding), 12, "#5F6368", false);
        detail.setPadding(0, dp(context, 4), 0, 0);
        card.addView(detail, matchWrap());
        return card;
    }

    private static String serviceSuffix(Binding binding) {
        if (TextUtils.isEmpty(binding.serviceDetailText)) return "";
        return "\n" + binding.serviceDetailText;
    }

    private static LinearLayoutCompat serverTitleRow(Context context, Binding binding) {
        LinearLayoutCompat top = row(context);
        MaterialTextView title = text(context, context.getString(R.string.remote_trust_settings_title), 15, "#202124", true);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        top.addView(title, weight());
        MaterialButton qr = iconButton(context, R.drawable.ic_remote_qr, context.getString(R.string.remote_trust_server_qr));
        qr.setOnClickListener(v -> showServerQr((FragmentActivity) context, binding));
        bindAction(binding, qr);
        top.addView(qr, fixed(context, 36, 32));
        MaterialButton scan = iconButton(context, R.drawable.ic_remote_scan, context.getString(R.string.remote_trust_scan_server));
        scan.setOnClickListener(v -> startScan((FragmentActivity) context, binding));
        bindAction(binding, scan);
        top.addView(scan, fixed(context, 36, 32));
        return top;
    }

    private static void showServerQr(FragmentActivity activity, Binding binding) {
        Server.get().start();
        String server = Server.get().getAddress(false) + "/remote/trust/setup";
        DialogRemoteTrustQrBinding dialogBinding = DialogRemoteTrustQrBinding.inflate(LayoutInflater.from(activity));
        dialogBinding.image.setImageBitmap(QRCode.getLightBitmap(server, 220, 1));
        dialogBinding.value.setText(server);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setView(dialogBinding.getRoot())
                .create();
        dialogBinding.negative.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            if (binding.serverQrDialog == dialog) binding.serverQrDialog = null;
        });
        dialog.setOnShowListener(d -> configureSmallModalWindow(activity, dialog));
        binding.serverQrDialog = showModal(dialog);
    }

    private static String currentServerText(Binding binding) {
        String server = textOf(binding.server);
        if (!TextUtils.isEmpty(server)) return server;
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) profile = RemoteStore.firstProfile();
        if (profile == null) return "";
        return TextUtils.isEmpty(profile.serverUrl) ? profile.serverOrigin : profile.serverUrl;
    }

    private static LinearLayoutCompat serverInfoPanel(Context context, Binding binding, RemoteProfile profile) {
        LinearLayoutCompat card = card(context);
        LinearLayoutCompat top = serverTitleRow(context, binding);
        MaterialButton edit = iconButton(context, R.drawable.ic_git_cloud_edit, context.getString(R.string.remote_trust_edit_server));
        edit.setOnClickListener(v -> {
            binding.serverEditing = true;
            render(context, binding);
        });
        bindAction(binding, edit);
        top.addView(edit, fixed(context, 36, 32));
        card.addView(top, matchWrap());
        String server = profile == null ? "-" : (TextUtils.isEmpty(profile.serverUrl) ? profile.serverOrigin : profile.serverUrl);
        MaterialTextView text = text(context, server, 13, "#3C4043", false);
        text.setTextIsSelectable(true);
        text.setLineSpacing(0, 1.08f);
        text.setPadding(0, dp(context, 6), 0, 0);
        card.addView(text, matchWrap());
        return card;
    }

    private static LinearLayoutCompat commandResultPanel(Context context, Binding binding) {
        LinearLayoutCompat card = card(context);
        LinearLayoutCompat top = row(context);
        MaterialTextView title = text(context, context.getString(R.string.remote_trust_command_log_title), 14, "#202124", true);
        top.addView(title, weight());
        MaterialButton copy = iconButton(context, R.drawable.ic_remote_copy, context.getString(R.string.remote_trust_copy));
        copy.setOnClickListener(v -> copyText(context, context.getString(R.string.remote_trust_command_log_title), binding.lastResult, R.string.remote_trust_result_copied));
        bindAction(binding, copy);
        top.addView(copy, fixed(context, 36, 32));
        card.addView(top, matchWrap());
        NestedScrollView scroll = new NestedScrollView(context);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        MaterialTextView detail = text(context, binding.lastResult, 12, "#3C4043", false);
        detail.setTextIsSelectable(true);
        detail.setLineSpacing(0, 1.08f);
        detail.setPadding(0, dp(context, 6), dp(context, 4), dp(context, 6));
        scroll.addView(detail, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, topMargin(fixedHeight(context, 180), 4));
        return card;
    }

    private static LinearLayoutCompat servicePanel(Context context, Binding binding, String state, String detail) {
        LinearLayoutCompat card = card(context);
        LinearLayoutCompat row = row(context);
        MaterialTextView title = text(context, state, 14, "#202124", true);
        row.addView(title, weight());
        if (!TextUtils.isEmpty(binding.diagnostics)) {
            MaterialButton copy = iconButton(context, R.drawable.ic_remote_copy, context.getString(R.string.remote_trust_copy_diagnostics));
            copy.setOnClickListener(v -> copyText(context, context.getString(R.string.setting_remote_trust), binding.diagnostics, R.string.remote_trust_diagnostics_copied));
            bindAction(binding, copy);
            row.addView(copy, fixed(context, 36, 32));
        }
        card.addView(row, matchWrap());
        if (!TextUtils.isEmpty(detail)) {
            MaterialTextView text = text(context, detail, 12, "#5F6368", false);
            text.setPadding(0, dp(context, 4), 0, 0);
            text.setMaxLines(5);
            text.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(text, matchWrap());
        }
        return card;
    }

    private static LinearLayoutCompat emptyPanel(Context context, String value) {
        LinearLayoutCompat card = card(context);
        MaterialTextView text = text(context, value, 13, "#5F6368", false);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(context, 8), 0, dp(context, 8));
        card.addView(text, matchWrap());
        return card;
    }

    private static LinearLayoutCompat card(Context context) {
        LinearLayoutCompat view = new LinearLayoutCompat(context);
        view.setOrientation(LinearLayoutCompat.VERTICAL);
        view.setPadding(dp(context, 10), dp(context, 9), dp(context, 10), dp(context, 9));
        view.setBackground(background(context, "#F8F9FA", Color.parseColor("#DADCE0"), 8));
        return view;
    }

    private static MaterialTextView text(Context context, String value, int sp, String color, boolean bold) {
        MaterialTextView view = new MaterialTextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor(color));
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private static TextInputEditText input(Context context, int inputType, boolean singleLine) {
        TextInputEditText input = new TextInputEditText(context);
        input.setInputType(inputType);
        input.setSingleLine(singleLine);
        input.setTextSize(14);
        input.setMinHeight(dp(context, 46));
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setSelectAllOnFocus(false);
        input.setTextColor(Color.BLACK);
        input.setHintTextColor(Color.parseColor("#666666"));
        return input;
    }

    private static TextInputLayout inputLayout(Context context, int hint, TextInputEditText input) {
        TextInputLayout layout = new TextInputLayout(context);
        layout.setHint(context.getString(hint));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(Color.WHITE);
        layout.setBoxStrokeColor(ContextCompat.getColor(context, R.color.dialog_outlined_button_stroke));
        layout.setHintTextColor(ColorStateList.valueOf(Color.parseColor("#5F6368")));
        layout.setBoxCornerRadii(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        layout.addView(input, matchWrap());
        return layout;
    }

    private static com.google.android.material.checkbox.MaterialCheckBox check(Context context, int resId) {
        com.google.android.material.checkbox.MaterialCheckBox box = new com.google.android.material.checkbox.MaterialCheckBox(context);
        box.setText(resId);
        box.setTextSize(14);
        box.setButtonTintList(ContextCompat.getColorStateList(context, R.color.dialog_checkbox_tint));
        box.setPadding(0, 0, 0, 0);
        return box;
    }

    private static MaterialButton tab(Context context, int resId) {
        MaterialButton button = segment(context, context.getString(resId));
        button.setCheckable(true);
        return button;
    }

    private static MaterialButton primaryAction(Binding binding, Context context, int resId) {
        MaterialButton button = primary(context, context.getString(resId));
        bindAction(binding, button);
        return button;
    }

    private static MaterialButton tonalAction(Binding binding, Context context, int resId) {
        MaterialButton button = tonal(context, context.getString(resId));
        bindAction(binding, button);
        return button;
    }

    private static MaterialButton outlineAction(Binding binding, Context context, int resId) {
        MaterialButton button = outline(context, context.getString(resId));
        bindAction(binding, button);
        return button;
    }

    private static MaterialButton dangerAction(Binding binding, Context context, int resId) {
        MaterialButton button = outlineAction(binding, context, resId);
        button.setTextColor(Color.parseColor("#B3261E"));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#F2B8B5")));
        return button;
    }

    private static MaterialButton smallOutlineAction(Binding binding, Context context, int resId) {
        MaterialButton button = outlineAction(binding, context, resId);
        button.setTextSize(12);
        return button;
    }

    private static MaterialButton button(Context context, int resId) {
        return button(context, context.getString(resId));
    }

    private static MaterialButton button(Context context, String text) {
        MaterialButton button = new MaterialButton(context);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(context, 32));
        button.setMinimumHeight(dp(context, 32));
        button.setPadding(dp(context, 6), 0, dp(context, 6), 0);
        button.setMaxLines(2);
        button.setEllipsize(TextUtils.TruncateAt.END);
        return button;
    }

    private static MaterialButton primary(Context context, String text) {
        MaterialButton button = button(context, text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.dialog_primary_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(context, R.color.dialog_primary_button_text));
        return button;
    }

    private static MaterialButton tonal(Context context, String text) {
        MaterialButton button = button(context, text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.dialog_tonal_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(context, R.color.dialog_tonal_button_text));
        return button;
    }

    private static MaterialButton outline(Context context, String text) {
        MaterialButton button = button(context, text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_text));
        button.setStrokeColor(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_stroke));
        button.setStrokeWidth(dp(context, 1));
        return button;
    }

    private static MaterialButton segment(Context context, String text) {
        MaterialButton button = outline(context, text);
        button.setBackgroundTintList(segmentBackground());
        button.setTextColor(segmentText());
        button.setStrokeColor(segmentStroke());
        return button;
    }

    private static MaterialButton closeButton(Context context) {
        MaterialButton button = button(context, "×");
        button.setTextSize(20);
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_bg));
        button.setTextColor(Color.parseColor("#5F6368"));
        return button;
    }

    private static MaterialButton compactButton(Context context, int resId) {
        MaterialButton button = tonal(context, context.getString(resId));
        button.setTextSize(12);
        return button;
    }

    private static MaterialButton pillButton(Context context, String text) {
        MaterialButton button = outline(context, text);
        button.setTextSize(12);
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        return button;
    }

    private static MaterialButton statusButton(Context context, String text) {
        MaterialButton button = pillButton(context, text);
        button.setStrokeWidth(dp(context, 1));
        return button;
    }

    private static void applyDeviceStyle(Context context, MaterialButton button, boolean online) {
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(online ? "#E6F4EA" : "#FCE8E6")));
        button.setTextColor(Color.parseColor(online ? "#137333" : "#B3261E"));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor(online ? "#CEEAD6" : "#F2B8B5")));
    }

    private static MaterialButton iconButton(Context context, int icon, String label) {
        MaterialButton button = outline(context, "");
        button.setContentDescription(label);
        button.setIconResource(icon);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(0);
        button.setIconSize(dp(context, 18));
        button.setIconTint(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_text));
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private static MaterialButton listButton(Context context, String text) {
        MaterialButton button = outline(context, text);
        button.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        button.setTextColor(Color.parseColor("#202124"));
        button.setMinHeight(dp(context, 56));
        button.setMaxLines(3);
        return button;
    }

    private static MaterialButton deviceButton(Context context, String text, RemoteDevice device) {
        MaterialButton button = listButton(context, text);
        boolean online = isDeviceOnline(device);
        applyDeviceStyle(context, button, online);
        button.setIconResource(deviceTypeIcon(device));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(dp(context, 8));
        button.setIconSize(dp(context, 18));
        button.setIconTint(ColorStateList.valueOf(Color.parseColor(online ? "#137333" : "#B3261E")));
        return button;
    }

    private static void bindAction(Binding binding, MaterialButton button) {
        binding.actions.add(button);
    }

    private static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(Color.parseColor("#E8EAED"));
        view.setMinimumHeight(dp(context, 1));
        return view;
    }

    private static GradientDrawable background(Context context, String color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(dp(context, radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static GradientDrawable configItemBackground(Context context, boolean active, boolean selected) {
        if (active) return background(context, "#E6F4EA", Color.parseColor("#CEEAD6"), 8);
        if (selected) return background(context, "#E8F0FE", Color.parseColor("#D2E3FC"), 8);
        return background(context, "#F8F9FA", Color.parseColor("#DADCE0"), 8);
    }

    private static ColorStateList segmentBackground() {
        return new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        }, new int[]{
                Color.parseColor("#0B57D0"),
                Color.parseColor("#F1F3F4"),
                Color.parseColor("#E8F0FE"),
                Color.parseColor("#E8F0FE"),
                Color.WHITE
        });
    }

    private static ColorStateList segmentText() {
        return new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_enabled},
                new int[]{}
        }, new int[]{
                Color.WHITE,
                Color.parseColor("#9AA0A6"),
                Color.parseColor("#202124")
        });
    }

    private static ColorStateList segmentStroke() {
        return new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        }, new int[]{
                Color.parseColor("#0B57D0"),
                Color.parseColor("#0B57D0"),
                Color.parseColor("#0B57D0"),
                Color.parseColor("#C8CDD2")
        });
    }

    private static LinearLayoutCompat.LayoutParams matchWrap() {
        return new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayoutCompat.LayoutParams weight() {
        return new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private static LinearLayoutCompat.LayoutParams leftWeight(Context context) {
        LinearLayoutCompat.LayoutParams params = weight();
        params.setMarginStart(dp(context, 8));
        return params;
    }

    private static LinearLayoutCompat.LayoutParams leftWeight(Context context, int marginDp) {
        LinearLayoutCompat.LayoutParams params = weight();
        params.setMarginStart(dp(context, marginDp));
        return params;
    }

    private static LinearLayoutCompat.LayoutParams fixed(Context context, int widthDp, int heightDp) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(dp(context, widthDp), dp(context, heightDp));
        params.setMarginStart(dp(context, 8));
        return params;
    }

    private static LinearLayoutCompat.LayoutParams fixedHeight(Context context, int heightDp) {
        return new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp));
    }

    private static LinearLayoutCompat.LayoutParams compactWrap() {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = 0;
        params.bottomMargin = 0;
        return params;
    }

    private static LinearLayoutCompat.LayoutParams topMargin(LinearLayoutCompat.LayoutParams params, int topDp) {
        params.topMargin = dp(App.get(), topDp);
        return params;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class DeviceRow {
        private final RemoteGroup group;
        private final RemoteDevice device;

        private DeviceRow(RemoteGroup group, RemoteDevice device) {
            this.group = group;
            this.device = device;
        }
    }

    private interface CommandHandler {
        void handle(RemoteCommand command);
    }

    private interface CommandErrorHandler {
        void handle(Throwable e);
    }

    private static final class RemoteSyncUiState {
        private String serverOrigin = "";
        private String groupId = "";
        private String targetDeviceId = "";
        private String targetName = "";
        private String syncId = "";
        private String result = "";
        private int step;
        private boolean running;
        private long updatedAt;
    }

    private static final class ConfigDialogState {
        private AlertDialog dialog;
        private NestedScrollView contentScroll;
        private LinearLayoutCompat content;
        private LinearLayoutCompat titleRow;
        private LinearLayoutCompat header;
        private LinearLayoutCompat actionsRow;
        private LinearLayoutCompat formActionsRow;
        private MaterialButtonToggleGroup typeRow;
        private MaterialTextView title;
        private MaterialTextView summary;
        private MaterialButton vod;
        private MaterialButton live;
        private MaterialButton wall;
        private MaterialButton homeBack;
        private MaterialButton close;
        private MaterialButton add;
        private MaterialButton refresh;
        private MaterialButton addSave;
        private MaterialButton addBack;
        private MaterialButton home;
        private MaterialButton edit;
        private MaterialButton delete;
        private Runnable render;
        private JsonArray items = new JsonArray();
        private JsonObject selected;
        private String settingHomeKey = "";
        private String homeStatusKey = "";
        private String homeStatusName = "";
        private int homeStatus = HOME_STATUS_NONE;
        private String usePreviousKey = "";
        private String useStatusKey = "";
        private int useStatus = HOME_STATUS_NONE;
        private int type;
        private boolean adding;
        private boolean homePicking;
        private boolean addLocalMode = true;
        private JsonObject addSelected;
        private TextInputEditText addUrl;
        private TextInputEditText addName;
        private boolean editing;
        private int editType;
        private JsonObject editOriginal;
        private TextInputEditText editUrl;
        private TextInputEditText editName;
    }

    private static final class Binding {
        private LinearLayoutCompat root;
        private NestedScrollView scroll;
        private LinearLayoutCompat toolbar;
        private AlertDialog dialog;
        private AlertDialog serverQrDialog;
        private Runnable callback;
        private Runnable detectRetry;
        private Runnable deviceRefreshRetry;
        private Runnable deviceStatusRefresh;
        private Runnable bindCodeRefresh;
        private LinearLayoutCompat content;
        private MaterialTextView summary;
        private MaterialButton close;
        private MaterialButton bindCodeButton;
        private MaterialButton enableToggle;
        private MaterialButton statusButton;
        private MaterialButton serviceButton;
        private MaterialButton settingsBackButton;
        private MaterialButton addDeviceButton;
        private TextInputEditText server;
        private TextInputLayout serverLayout;
        private com.google.android.material.checkbox.MaterialCheckBox enabled;
        private com.google.android.material.checkbox.MaterialCheckBox keepOnline;
        private final List<MaterialButton> actions = new ArrayList<>();
        private boolean initialized;
        private boolean busy;
        private boolean statusExpanded;
        private boolean deviceStatusExpanded;
        private boolean advancedExpanded;
        private boolean serverEditing;
        private boolean autoBindAttempted;
        private boolean autoDetectStarted;
        private boolean autoDetected;
        private boolean detectingService;
        private boolean creatingBindCode;
        private boolean deviceStatusRefreshScheduled;
        private boolean deviceStatusRefreshing;
        private int page = PAGE_DEVICES;
        private int pendingDeviceRefreshes;
        private long lastDeviceStatusRefreshAt;
        private long bindCodeExpiresAt;
        private String bindCode = "";
        private String selectedGroupId = "";
        private String selectedDeviceId = "";
        private String lastResult = "";
        private String serviceStateText = "";
        private String serviceDetailText = "";
        private String diagnostics = "";
        private final Map<String, JsonArray> configCache = new HashMap<>();
        private final Map<String, JsonArray> siteCache = new HashMap<>();
    }
}
