package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Layout;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogMultiThreadProxyBinding;
import com.fongmi.android.tv.server.proxy.MultiThreadProxy;
import com.fongmi.android.tv.server.proxy.ProxyDomainRuleSet;
import com.fongmi.android.tv.server.proxy.ProxyRuntimeConfig;
import com.fongmi.android.tv.server.proxy.ProxyRuntimeConfigValidator;
import com.fongmi.android.tv.setting.MultiThreadProxySetting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

public class MultiThreadProxyDialog extends BaseAlertDialog {

    @FunctionalInterface
    public interface Callback {
        void onSaved(boolean applyNow);
    }

    private DialogMultiThreadProxyBinding binding;
    private String currentUrl;
    private Callback callback;

    public static void show(Fragment fragment, Runnable callback) {
        show(fragment, null, applyNow -> {
            if (callback != null) callback.run();
        });
    }

    public static void show(Fragment fragment, String currentUrl, Callback callback) {
        MultiThreadProxyDialog dialog = new MultiThreadProxyDialog();
        dialog.currentUrl = currentUrl;
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), "multi-thread-proxy");
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        show(activity, null, applyNow -> {
            if (callback != null) callback.run();
        });
    }

    public static void show(FragmentActivity activity, String currentUrl, Callback callback) {
        MultiThreadProxyDialog dialog = new MultiThreadProxyDialog();
        dialog.currentUrl = currentUrl;
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), "multi-thread-proxy");
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogMultiThreadProxyBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog)
                .setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        int width = ResUtil.getScreenWidth(requireContext());
        int height = ResUtil.getScreenHeight(requireContext());
        boolean land = ResUtil.isLand(requireContext());
        WindowManager.LayoutParams params = window.getAttributes();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (width * (land ? 0.62f : 0.94f));
        params.height = (int) (height * (land ? 0.88f : 0.82f));
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        binding.enabled.requestFocus();
    }

    @Override
    protected void initView() {
        ProxyRuntimeConfig config = MultiThreadProxySetting.get();
        binding.enabled.setChecked(config.enabled());
        binding.rangeConcurrency.setText(String.valueOf(config.rangeConcurrency()));
        int shards = config.shardMode() == ProxyRuntimeConfig.ShardMode.COUNT && config.shardCount() > 0
                ? config.shardCount()
                : ProxyRuntimeConfig.DEFAULT_SHARD_COUNT;
        binding.shardCount.setText(String.valueOf(shards));
        binding.domainRules.setText(MultiThreadProxySetting.getDomainRulesText());
    }

    @Override
    protected void initEvent() {
        binding.cancel.setOnClickListener(view -> dismiss());
        binding.extractCurrentDomain.setOnClickListener(view -> extractCurrentDomain());
        binding.save.setOnClickListener(view -> save());
        wireRemoteFocus();
    }

    private void wireRemoteFocus() {
        wireDpadFocus(binding.enabled, null, binding.rangeConcurrency, null, null);
        wireDpadFocus(binding.rangeConcurrency, binding.enabled, binding.shardCount, null, null);
        wireDpadFocus(binding.shardCount, binding.rangeConcurrency, binding.extractCurrentDomain, null, null);
        wireDpadFocus(binding.extractCurrentDomain, binding.shardCount, binding.domainRules, null, null);
        wireMultilineDpadFocus(binding.domainRules, binding.extractCurrentDomain, binding.save);
        wireDpadFocus(binding.cancel, binding.domainRules, null, null, binding.save);
        wireDpadFocus(binding.save, binding.domainRules, null, binding.cancel, null);
    }

    private static void wireMultilineDpadFocus(EditText input, View upTarget, View downTarget) {
        input.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && upTarget != null && isCursorAtFirstLine(input)) {
                return requestFocus(upTarget);
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && downTarget != null && isCursorAtLastLine(input)) {
                return requestFocus(downTarget);
            }
            return false;
        });
    }

    private static void wireDpadFocus(View view, View up, View down, View left, View right) {
        view.setOnKeyListener((target, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && up != null) return requestFocus(up);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && down != null) return requestFocus(down);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && left != null) return requestFocus(left);
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && right != null) return requestFocus(right);
            return false;
        });
    }

    private static boolean requestFocus(View view) {
        view.requestFocus();
        return true;
    }

    private static boolean isCursorAtFirstLine(EditText input) {
        Layout layout = input.getLayout();
        if (layout == null) return false;
        return layout.getLineForOffset(selection(input)) <= 0;
    }

    private static boolean isCursorAtLastLine(EditText input) {
        Layout layout = input.getLayout();
        if (layout == null) return false;
        return layout.getLineForOffset(selection(input)) >= layout.getLineCount() - 1;
    }

    private static int selection(EditText input) {
        return Math.max(0, input.getSelectionStart());
    }

    private void extractCurrentDomain() {
        binding.error.setVisibility(View.GONE);
        String domain = ProxyDomainRuleSet.extractHost(currentUrl);
        if (TextUtils.isEmpty(domain)) {
            Notify.show(R.string.multi_thread_proxy_domain_unavailable);
            return;
        }
        try {
            int threads = positive(binding.rangeConcurrency.getText().toString(), "threads");
            int shards = positive(binding.shardCount.getText().toString(), "shards");
            ProxyDomainRuleSet rules = ProxyDomainRuleSet.parse(binding.domainRules.getText().toString());
            for (ProxyDomainRuleSet.Rule rule : rules.rules()) {
                if (rule.domains().contains(domain)) {
                    Notify.show(R.string.multi_thread_proxy_domain_exists);
                    return;
                }
            }
            String current = binding.domainRules.getText().toString().trim();
            String line = domain + "=" + threads + "," + shards;
            String next = current.isEmpty() ? line : current + "\n" + line;
            binding.domainRules.setText(next);
            binding.domainRules.setSelection(next.length());
            Notify.show(getString(R.string.multi_thread_proxy_domain_added, domain));
        } catch (Exception e) {
            showError(e);
        }
    }

    private void save() {
        binding.error.setVisibility(View.GONE);
        try {
            int threads = positive(binding.rangeConcurrency.getText().toString(), "threads");
            int shards = positive(binding.shardCount.getText().toString(), "shards");
            ProxyDomainRuleSet rules = ProxyDomainRuleSet.parse(binding.domainRules.getText().toString());
            ProxyRuntimeConfig current = MultiThreadProxySetting.get();
            int rangeWorkers = Math.max(threads, rules.maxConcurrency());
            ProxyRuntimeConfig next = withSettings(current, binding.enabled.isChecked(), rangeWorkers, threads, shards);
            ProxyRuntimeConfigValidator.requireValid(next);
            if (callback != null && !TextUtils.isEmpty(currentUrl)) {
                showApplyChoice(next, rules);
            } else {
                applyAndPersist(next, rules, false);
            }
        } catch (Exception e) {
            showError(e);
        }
    }

    private void showApplyChoice(ProxyRuntimeConfig next, ProxyDomainRuleSet rules) {
        FragmentActivity activity = requireActivity();
        dismiss();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.multi_thread_proxy_apply_title)
                .setMessage(R.string.multi_thread_proxy_apply_message)
                .setNegativeButton(R.string.multi_thread_proxy_apply_later,
                        (dialog, which) -> persistForLater(next, rules))
                .setPositiveButton(R.string.multi_thread_proxy_apply_now,
                        (dialog, which) -> applyAndPersist(next, rules, true))
                .setOnCancelListener(dialog -> persistForLater(next, rules))
                .show();
    }

    private void persistForLater(ProxyRuntimeConfig next, ProxyDomainRuleSet rules) {
        MultiThreadProxySetting.put(next);
        MultiThreadProxySetting.putDomainRules(rules);
        if (callback != null) callback.onSaved(false);
        Notify.show(R.string.multi_thread_proxy_saved);
    }

    private void applyAndPersist(ProxyRuntimeConfig next, ProxyDomainRuleSet rules, boolean reloadCurrent) {
        try {
            MultiThreadProxy.apply(next, rules);
            MultiThreadProxySetting.put(next);
            MultiThreadProxySetting.putDomainRules(rules);
            dismiss();
            if (callback != null) {
                if (reloadCurrent) callback.onSaved(true);
                else callback.onSaved(false);
            }
            Notify.show(reloadCurrent ? R.string.multi_thread_proxy_applied : R.string.multi_thread_proxy_saved);
        } catch (Exception e) {
            showError(e);
        }
    }

    private void showError(Exception e) {
        String message = getString(R.string.multi_thread_proxy_save_failed) + ": "
                + Objects.toString(e.getMessage(), "unknown error");
        Dialog dialog = getDialog();
        if (binding != null && dialog != null && dialog.isShowing()) {
            binding.error.setText(message);
            binding.error.setVisibility(View.VISIBLE);
        } else {
            Notify.show(message);
        }
    }

    private static int positive(String value, String name) {
        try {
            int result = Integer.parseInt(value.trim());
            if (result <= 0) throw new IllegalArgumentException(name + " must be positive");
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer", e);
        }
    }

    private static ProxyRuntimeConfig withSettings(
            ProxyRuntimeConfig current,
            boolean enabled,
            int rangeWorkers,
            int rangeConcurrency,
            int shardCount) {
        return new ProxyRuntimeConfig(
                current.schemaVersion(),
                enabled,
                current.portMode(),
                current.configuredPort(),
                current.serverWorkers(),
                current.connectionQueueCapacity(),
                rangeWorkers,
                rangeConcurrency,
                ProxyRuntimeConfig.ShardMode.COUNT,
                shardCount,
                current.chunkSizeBytes(),
                current.maxSessions(),
                current.reorderWindowBlocks(),
                current.bufferBudgetBytes(),
                current.retryCount(),
                current.connectTimeoutMillis(),
                current.readTimeoutMillis(),
                current.fileThresholdBytes());
    }
}
