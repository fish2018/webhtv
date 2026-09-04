package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogApkPushProgressBinding;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class ApkPushProgressDialog extends BaseAlertDialog {

    private static final String TAG = "apk_push_progress";

    private DialogApkPushProgressBinding binding;
    private Listener listener;
    private int progress = -1;
    private long bytes;
    private long total;
    private long speed;
    private long elapsed;

    public static ApkPushProgressDialog open(FragmentActivity activity, Listener listener) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return null;
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved()) return null;
        Fragment fragment = manager.findFragmentByTag(TAG);
        ApkPushProgressDialog dialog;
        if (fragment instanceof ApkPushProgressDialog) {
            dialog = (ApkPushProgressDialog) fragment;
        } else {
            dialog = new ApkPushProgressDialog();
            dialog.show(manager, TAG);
        }
        dialog.listener = listener;
        return dialog;
    }

    public static void dismiss(FragmentActivity activity) {
        if (activity == null) return;
        Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag(TAG);
        if (fragment instanceof ApkPushProgressDialog) ((ApkPushProgressDialog) fragment).dismissAllowingStateLoss();
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogApkPushProgressBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot()).setCancelable(false);
    }

    @Override
    protected void initView() {
        binding.progress.setMax(100);
        render();
    }

    @Override
    protected void initEvent() {
        binding.cancel.setOnClickListener(view -> {
            binding.cancel.setEnabled(false);
            if (listener != null) listener.onCancel();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        setCancelable(false);
        if (getDialog() != null) getDialog().setCanceledOnTouchOutside(false);
        configureWindow();
        binding.cancel.requestFocus();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    public void setProgress(int progress, long bytes, long total, long speed, long elapsed) {
        this.progress = progress;
        this.bytes = Math.max(0, bytes);
        this.total = total;
        this.speed = Math.max(0, speed);
        this.elapsed = Math.max(0, elapsed);
        render();
    }

    private void render() {
        if (binding == null) return;
        boolean indeterminate = progress < 0 || total <= 0;
        int value = Math.max(0, Math.min(100, progress));
        binding.progress.setIndeterminate(indeterminate);
        if (!indeterminate) binding.progress.setProgress(value);
        binding.progressText.setText(getProgressText(indeterminate, value));
        binding.cancel.setEnabled(true);
    }

    private String getProgressText(boolean indeterminate, int value) {
        String sizeText;
        if (!indeterminate) {
            sizeText = getString(R.string.apk_push_url_progress_known, value, FileUtil.byteCountToDisplaySize(bytes), FileUtil.byteCountToDisplaySize(total));
        } else {
            sizeText = getString(R.string.apk_push_url_progress_unknown, FileUtil.byteCountToDisplaySize(bytes));
        }
        if (speed <= 0 || elapsed <= 0) return sizeText;
        String speedText = FileUtil.byteCountToDisplaySize(speed);
        String elapsedText = formatDuration(elapsed);
        if (!indeterminate && total > bytes) {
            long remaining = Math.max(0, total - bytes) * 1000 / speed;
            return sizeText + "\n" + getString(R.string.apk_push_url_progress_detail_remaining, speedText, formatDuration(remaining), elapsedText);
        }
        return sizeText + "\n" + getString(R.string.apk_push_url_progress_detail, speedText, elapsedText);
    }

    private String formatDuration(long time) {
        String text = Util.timeMs(Math.max(0, time));
        return TextUtils.isEmpty(text) ? "00:00" : text;
    }

    private void configureWindow() {
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int width = Math.min(ResUtil.dp2px(720), (int) (screenWidth * 0.72f));
        width = Math.min(width, screenWidth - ResUtil.dp2px(32));
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = width;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    public interface Listener {

        void onCancel();
    }
}
