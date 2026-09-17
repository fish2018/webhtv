package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.AdDetectionResult;
import com.fongmi.android.tv.databinding.DialogAdRulePreviewBinding;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AdRulePreviewDialog extends BaseAlertDialog {

    private static final float LOW_CONFIDENCE_THRESHOLD = 0.5f;

    private DialogAdRulePreviewBinding binding;
    private AdDetectionResult result;
    private Callback callback;

    public interface Callback {
        void onConfirm(AdDetectionResult result);
    }

    public static AdRulePreviewDialog create(AdDetectionResult result) {
        AdRulePreviewDialog dialog = new AdRulePreviewDialog();
        dialog.result = result;
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
        return binding = DialogAdRulePreviewBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        if (result == null) {
            dismiss();
            return;
        }
        int percent = Math.round(result.getConfidence() * 100);
        binding.confidenceLabel.setText(getString(R.string.ad_rule_confidence_value, percent));
        binding.warning.setVisibility(result.getConfidence() < LOW_CONFIDENCE_THRESHOLD ? View.VISIBLE : View.GONE);
        binding.reasoning.setText(result.getReasoning());
        binding.hosts.setText(joinOrEmpty(result.getHostsBlacklist()));
        binding.regex.setText(joinOrEmpty(result.getRegexPatterns()));
        binding.exclude.setText(joinOrEmpty(result.getExcludePatterns()));
    }

    @Override
    protected void initEvent() {
        binding.confirm.setOnClickListener(v -> onConfirm());
        binding.cancel.setOnClickListener(v -> dismiss());
    }

    private void onConfirm() {
        dismiss();
        if (callback != null) callback.onConfirm(result);
    }

    private String joinOrEmpty(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return getString(R.string.ad_rule_preview_none);
        return String.join("\n", list);
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
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.6f : 0.9f)), ResUtil.dp2px(560));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
