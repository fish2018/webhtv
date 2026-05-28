package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogShellProxyBinding;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ShellProxyDialog extends BaseAlertDialog {

    private DialogShellProxyBinding binding;
    private Runnable callback;
    private boolean append = true;

    public static void show(Fragment fragment) {
        show(fragment, null);
    }

    public static void show(Fragment fragment, Runnable callback) {
        ShellProxyDialog dialog = new ShellProxyDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity) {
        show(activity, null);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        ShellProxyDialog dialog = new ShellProxyDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogShellProxyBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.setting_shell_proxy_config).setView(getBinding().getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        binding.url.setText(Setting.getShellProxyUrl());
        binding.hosts.setText(Setting.getShellProxyHosts());
        binding.url.setSelection(binding.url.length());
    }

    @Override
    protected void initEvent() {
        binding.url.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        binding.hosts.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
        binding.url.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ttp://");
        } else if (append && "s".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ocks5://");
        } else if (append && s.length() == 1) {
            append = false;
            binding.url.getText().insert(0, "socks5://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.length() == 0) {
            append = true;
        }
    }

    private void onPositive(DialogInterface dialog, int which) {
        String url = binding.url.getText() == null ? "" : binding.url.getText().toString().trim();
        String hosts = binding.hosts.getText() == null ? "" : binding.hosts.getText().toString().trim();
        if (!TextUtils.isEmpty(url) && !ProxySetting.isValid(url)) {
            Notify.show(R.string.setting_proxy_invalid);
            return;
        }
        Setting.putShellProxyHosts(TextUtils.isEmpty(hosts) ? "*" : hosts);
        Setting.putShellProxyUrl(url);
        if (callback != null) callback.run();
        dismiss();
    }
}
