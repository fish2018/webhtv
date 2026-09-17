package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.UserAdRuleStore;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.databinding.DialogAdRuleEditBinding;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class AdRuleEditDialog extends BaseAlertDialog {

    private DialogAdRuleEditBinding binding;
    private UserAdRule rule;
    private Callback callback;

    public interface Callback {
        void onRuleSaved();
    }

    public static AdRuleEditDialog create(UserAdRule rule) {
        AdRuleEditDialog dialog = new AdRuleEditDialog();
        dialog.rule = rule;
        return dialog;
    }

    public void show(FragmentActivity activity, Callback callback) {
        this.callback = callback;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogAdRuleEditBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        if (rule != null) {
            binding.name.setText(rule.getName());
            binding.hosts.setText(String.join("\n", rule.getHosts()));
            binding.regex.setText(String.join("\n", rule.getRegex()));
            binding.exclude.setText(String.join("\n", rule.getExclude()));
        }
    }

    @Override
    protected void initEvent() {
        binding.confirm.setOnClickListener(v -> onConfirm());
        binding.cancel.setOnClickListener(v -> dismiss());
    }

    private void onConfirm() {
        String name = text(binding.name);
        if (name.isEmpty()) name = "手动规则_" + System.currentTimeMillis();
        List<String> hosts = lines(binding.hosts);
        List<String> regex = lines(binding.regex);
        List<String> exclude = lines(binding.exclude);
        if (hosts.isEmpty() && regex.isEmpty() && exclude.isEmpty()) {
            Notify.show(R.string.ad_rule_empty_input);
            return;
        }
        String badRegex = firstInvalidRegex(regex, exclude);
        if (badRegex != null) {
            Notify.show(getString(R.string.ad_rule_invalid_regex, badRegex));
            return;
        }
        UserAdRule target = rule != null ? rule : UserAdRule.createManual(name);
        target.setName(name);
        target.setHosts(hosts);
        target.setRegex(regex);
        target.setExclude(exclude);
        if (rule != null) UserAdRuleStore.update(target);
        else UserAdRuleStore.add(target);
        dismiss();
        if (callback != null) callback.onRuleSaved();
    }

    private String text(android.widget.EditText view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private List<String> lines(android.widget.EditText view) {
        List<String> result = new ArrayList<>();
        for (String line : text(view).split("\\r?\\n")) {
            String value = line.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private String firstInvalidRegex(List<String>... groups) {
        for (List<String> group : groups) {
            for (String pattern : group) {
                try {
                    java.util.regex.Pattern.compile(pattern);
                } catch (Throwable e) {
                    return pattern;
                }
            }
        }
        return null;
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow();
    }

    private void configureWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.7f : 0.95f)), ResUtil.dp2px(640));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
