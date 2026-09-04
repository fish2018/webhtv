package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.databinding.DialogApkPushMethodBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ApkPushMethodDialog extends BaseAlertDialog {

    private static final String TAG = "apk_push_method";

    private final Device device;
    private DialogApkPushMethodBinding binding;
    private Listener listener;

    private ApkPushMethodDialog(Device device) {
        this.device = device;
    }

    public static ApkPushMethodDialog create(Device device) {
        return new ApkPushMethodDialog(device);
    }

    public ApkPushMethodDialog listener(Listener listener) {
        this.listener = listener;
        return this;
    }

    public void show(FragmentActivity activity) {
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) == null) show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogApkPushMethodBinding.inflate(LayoutInflater.from(requireActivity()));
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.target.setText(getString(R.string.apk_push_method_target, device == null ? "" : device.getName()));
    }

    @Override
    protected void initEvent() {
        binding.local.setOnClickListener(view -> select(true));
        binding.link.setOnClickListener(view -> select(false));
        binding.cancel.setOnClickListener(view -> dismiss());
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.88f);
    }

    private void select(boolean local) {
        Listener callback = listener;
        dismissAllowingStateLoss();
        if (callback == null || device == null) return;
        if (local) callback.onLocal(device);
        else callback.onLink(device);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    public interface Listener {

        void onLocal(Device device);

        void onLink(Device device);
    }
}
