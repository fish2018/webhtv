package com.fongmi.android.tv.ui.dialog;

import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSyncPathBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.SyncFiles;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SyncPathDialog extends BaseAlertDialog {

    private DialogSyncPathBinding binding;
    private Runnable callback;

    public static void show(Fragment fragment, Runnable callback) {
        SyncPathDialog dialog = new SyncPathDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSyncPathBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.paths.setText(SyncFiles.getPathsText(SyncFiles.getPaths(Setting.getSyncPaths())));
        binding.paths.setSelection(binding.paths.length());
    }

    @Override
    protected void initEvent() {
        binding.paths.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive();
            return true;
        });
        binding.reset.setOnClickListener(v -> {
            binding.paths.setText(SyncFiles.DEFAULT_PATHS);
            binding.paths.setSelection(binding.paths.length());
        });
        binding.negative.setOnClickListener(v -> dismiss());
        binding.positive.setOnClickListener(v -> onPositive());
    }

    private void onPositive() {
        String text = binding.paths.getText() == null ? "" : binding.paths.getText().toString();
        Setting.putSyncPaths(SyncFiles.getPathsText(SyncFiles.getPaths(text)));
        if (callback != null) callback.run();
        dismiss();
    }
}
