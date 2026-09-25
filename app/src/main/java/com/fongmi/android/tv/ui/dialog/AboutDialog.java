package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogAboutBinding;
import com.fongmi.android.tv.databinding.DialogGithubProxyBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.GithubProxyAdapter;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.GithubProxy;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.github.catvod.net.OkHttp;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class AboutDialog {

    private static final int DIALOG_VERTICAL_MARGIN_DP = 96;
    private static final int FULLSCREEN_INSET_DP = 32;
    private static final long PROBE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);

    private AboutDialog() {
    }

    public static void show(FragmentActivity activity, Runnable updateAction) {
        DialogAboutBinding binding = DialogAboutBinding.inflate(LayoutInflater.from(activity));
        binding.version.setText(activity.getString(R.string.about_version, AppVersion.fullName(), BuildConfig.FLAVOR_mode, BuildConfig.FLAVOR_abi));

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(binding.getRoot());
        binding.confirm.setOnClickListener(v -> dialog.dismiss());
        binding.updateSettings.setOnClickListener(v -> {
            dialog.dismiss();
            UpdateSettingsDialog.show(activity);
        });
        binding.checkUpdate.setOnClickListener(v -> {
            dialog.dismiss();
            if (updateAction != null) updateAction.run();
        });
        binding.githubProxy.setOnClickListener(v -> showGithubProxy(activity));
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        configureWindow(activity, dialog, binding);
        binding.confirm.requestFocus();
    }

    private static void showGithubProxy(FragmentActivity activity) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog);
        Context context = builder.getContext();
        DialogGithubProxyBinding binding = DialogGithubProxyBinding.inflate(LayoutInflater.from(context));
        GithubProxyAdapter adapter = new GithubProxyAdapter(new GithubProxyAdapter.OnClickListener() {
            @Override
            public void onActive(String item) {
                Setting.putGithubProxy(GithubProxy.setActive(item));
                refreshGithubProxy(binding);
            }

            @Override
            public void onRemove(String item) {
                Setting.putGithubProxy(GithubProxy.removeSource(item));
                refreshGithubProxy(binding);
            }
        });
        binding.list.setAdapter(adapter);
        binding.list.setHasFixedSize(false);
        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) Setting.putGithubProxyMode(checkedId == R.id.modeStrip
                    ? GithubProxy.MODE_STRIP_SCHEME
                    : GithubProxy.MODE_FULL_URL);
        });
        binding.modeGroup.check(GithubProxy.MODE_STRIP_SCHEME.equals(Setting.getGithubProxyMode())
                ? R.id.modeStrip
                : R.id.modeFull);
        binding.enabled.setChecked(Setting.isGithubProxyEnabled());
        binding.enabled.setOnCheckedChangeListener((buttonView, isChecked) -> Setting.putGithubProxyEnabled(isChecked));
        refreshGithubProxy(binding);

        binding.add.setOnClickListener(v -> {
            String value = String.valueOf(binding.input.getText()).trim();
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                Notify.show(R.string.setting_github_proxy_invalid);
                return;
            }
            Setting.putGithubProxy(GithubProxy.addSource(value));
            binding.input.setText("");
            refreshGithubProxy(binding);
        });
        binding.reset.setOnClickListener(v -> {
            Setting.putGithubProxy(GithubProxy.defaultSources());
            refreshGithubProxy(binding);
        });
        binding.probe.setOnClickListener(v -> probeLatency(binding));

        View.OnKeyListener dpadNav = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && v == binding.enabled) {
                return binding.modeGroup.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && v == binding.enabled) {
                return binding.list.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && v == binding.modeGroup) {
                return binding.input.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && v == binding.modeGroup) {
                return binding.enabled.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && v == binding.input) {
                return binding.reset.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && v == binding.input) {
                return binding.modeGroup.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && v == binding.reset) {
                return binding.input.requestFocus();
            }
            return false;
        };
        binding.enabled.setOnKeyListener(dpadNav);
        binding.probe.setOnKeyListener(dpadNav);
        binding.input.setOnKeyListener(dpadNav);
        binding.reset.setOnKeyListener(dpadNav);

        AlertDialog githubDialog = builder
                .setTitle(R.string.setting_github_proxy)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
        githubDialog.setOnShowListener(d -> {
            binding.list.post(() -> {
                GithubProxyAdapter a = (GithubProxyAdapter) binding.list.getAdapter();
                if (a != null && a.getItemCount() > 0) {
                    binding.list.scrollToPosition(a.getSelected());
                    binding.list.requestFocus();
                }
            });
        });
        githubDialog.show();
        LightDialog.apply(githubDialog);
        configureGithubProxyWindow(activity, githubDialog, binding);
    }

    private static void probeLatency(DialogGithubProxyBinding binding) {
        GithubProxyAdapter adapter = (GithubProxyAdapter) binding.list.getAdapter();
        if (adapter == null || adapter.isProbing()) return;
        List<String> sources = GithubProxy.getSources();
        adapter.startProbe(sources);
        for (String source : sources) {
            Task.submitLarge(() -> {
                long start = System.nanoTime();
                try (var response = OkHttp.newCall(OkHttp.client(PROBE_TIMEOUT_MS), GithubProxy.probeUrl(source), source).execute()) {
                    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                    String value = App.get().getString(R.string.setting_github_proxy_latency, elapsed);
                    App.post(() -> adapter.setLatency(source, value));
                } catch (Exception e) {
                    App.post(() -> adapter.setLatency(source, App.get().getString(R.string.setting_github_proxy_latency_failed)));
                }
            });
        }
    }

    private static void configureGithubProxyWindow(FragmentActivity activity, AlertDialog dialog, DialogGithubProxyBinding binding) {
        boolean leanback = Util.isLeanback();
        Window window = dialog.getWindow();
        if (window == null) return;
        // 手机与电视都铺满全屏：不再按屏幕比例手算宽高，列表改为吃掉剩余空间。
        // 这个弹窗经 MaterialAlertDialogBuilder.setView 装载，root 会被 AlertController
        // 以 MATCH_PARENT 塞进 @id/custom，窗口给满高度后列表即可自由伸展。
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        // 弹窗有 URL 输入框。小窗居中时系统还能上推窗口避让键盘，铺满全屏后没有余量，
        // 必须显式 ADJUST_RESIZE 让窗口自身缩小，否则输入框会被屏幕键盘盖住。
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (leanback) binding.list.requestFocus();
        else binding.list.post(() -> {
            for (int i = 0; i < binding.list.getChildCount(); i++) {
                View child = binding.list.getChildAt(i);
                child.findViewById(R.id.text).setFocusable(false);
                child.findViewById(R.id.remove).setFocusable(false);
            }
        });
    }

    private static void refreshGithubProxy(DialogGithubProxyBinding binding) {
        ((GithubProxyAdapter) binding.list.getAdapter()).setItems(GithubProxy.getSources(), GithubProxy.getActive());
    }

    /**
     * 电视端铺满全屏，不再手算窗口高度。
     *
     * 手算精确高度需要同时算对屏宽高、content 高度、visibleFrame、systemBars inset、
     * Material 背景 inset 和 chrome 高度，每一项都有设备差异，任何一项出错就裁掉内容底部。
     * 铺满全屏后这些变量全部无关，改由 LinearLayout 分配空间：固定高的按钮区永远保住，
     * 滚动区吃掉剩余空间。
     */
    private static boolean configureFullscreenWindow(Window window, DialogAboutBinding binding) {
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.58f;
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);
        // XML 里 root 是 wrap_content，铺满全屏必须显式改成 match_parent，
        // 否则窗口虽然全屏、内容仍只占中间一小块。
        ViewGroup.LayoutParams rootParams = binding.getRoot().getLayoutParams();
        if (rootParams != null) {
            rootParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            rootParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            binding.getRoot().setLayoutParams(rootParams);
        }
        // 电视存在 overscan，正文不能贴边。在 XML 既有 padding 之上再加一圈安全边距。
        int inset = ResUtil.dp2px(FULLSCREEN_INSET_DP);
        View root = binding.getRoot();
        root.setPadding(
                root.getPaddingLeft() + inset,
                root.getPaddingTop() + inset,
                root.getPaddingRight() + inset,
                root.getPaddingBottom() + inset);
        // 滚动区在 XML 里是 wrap_content，全屏下要改成加权填充，否则正文短时按钮会飘在中间。
        ViewGroup.LayoutParams scrollParams = binding.contentScroll.getLayoutParams();
        scrollParams.height = 0;
        if (scrollParams instanceof androidx.appcompat.widget.LinearLayoutCompat.LayoutParams) {
            ((androidx.appcompat.widget.LinearLayoutCompat.LayoutParams) scrollParams).weight = 1;
        }
        binding.contentScroll.setLayoutParams(scrollParams);
        // 铺满后不再需要 maxHeight 约束，交回给 weight 决定。
        // CustomNestedScrollView 以 maxHeight > 0 作为启用条件，所以这里必须给 0 而非 MAX_VALUE：
        // MeasureSpec 的尺寸只有 30 位，MAX_VALUE 会溢出到 mode 位上。
        binding.contentScroll.setMaxHeight(0);
        return true;
    }

    private static boolean configureWindow(FragmentActivity activity, Dialog dialog, DialogAboutBinding binding) {
        Window window = dialog.getWindow();
        if (window == null) return false;
        if (Util.isLeanback()) return configureFullscreenWindow(window, binding);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(activity) * (ResUtil.isLand(activity) ? 0.62f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);

        int availableHeight = activity.findViewById(android.R.id.content).getHeight();
        if (availableHeight == 0) {
            availableHeight = ResUtil.getScreenHeight(activity);
            android.view.WindowInsets insets = window.getDecorView().getRootWindowInsets();
            if (insets != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.graphics.Insets systemBars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                availableHeight -= (systemBars.top + systemBars.bottom);
            }
        }

        int widthSpec = View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        binding.contentScroll.getLayoutParams().height = 1;
        binding.getRoot().measure(widthSpec, heightSpec);
        int chromeHeight = binding.getRoot().getMeasuredHeight() - 1;
        binding.contentScroll.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;

        int maxDialogHeight = Math.min(availableHeight, Math.max(chromeHeight + 1, availableHeight - ResUtil.dp2px(DIALOG_VERTICAL_MARGIN_DP)));
        int maxScrollHeight = Math.max(1, maxDialogHeight - chromeHeight);
        binding.contentScroll.setMaxHeight(maxScrollHeight);
        binding.getRoot().measure(widthSpec, heightSpec);
        int dialogHeight = Math.min(binding.getRoot().getMeasuredHeight(), maxDialogHeight);
        window.setLayout(params.width, dialogHeight);
        return true;
    }
}
