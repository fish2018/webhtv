package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSiteHealthBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SiteHealthDialog extends BaseAlertDialog {

    private DialogSiteHealthBinding binding;
    private Runnable callback;
    private boolean healthSort;
    private boolean dialogSort;

    public static void show(Fragment fragment, Runnable callback) {
        SiteHealthDialog dialog = new SiteHealthDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        SiteHealthDialog dialog = new SiteHealthDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteHealthBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        Window window = getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (ResUtil.getScreenWidth(requireContext()) * (ResUtil.isLand(requireContext()) ? 0.44f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        binding.healthSort.requestFocus();
    }

    @Override
    protected void initView() {
        healthSort = Setting.isSiteHealthSort();
        dialogSort = Setting.isSiteHealthDialogSort();
        updateButton(binding.healthSort, healthSort);
        updateButton(binding.dialogSort, dialogSort);
    }

    @Override
    protected void initEvent() {
        binding.healthSort.setOnClickListener(view -> {
            healthSort = !healthSort;
            updateButton(binding.healthSort, healthSort);
        });
        binding.dialogSort.setOnClickListener(view -> {
            dialogSort = !dialogSort;
            updateButton(binding.dialogSort, dialogSort);
        });
        binding.report.setOnClickListener(view -> SiteHealthReportDialog.show(requireActivity()));
        binding.negative.setOnClickListener(view -> dismiss());
        binding.positive.setOnClickListener(view -> onPositive());
    }

    private void updateButton(MaterialButton button, boolean enabled) {
        button.setText(enabled ? R.string.setting_enable : R.string.setting_disable);
        button.setAlpha(enabled ? 1.0f : 0.65f);
    }

    private void onPositive() {
        Setting.putSiteHealthSort(healthSort);
        Setting.putSiteHealthDialogSort(dialogSort);
        if (callback != null) callback.run();
        dismiss();
    }
}
