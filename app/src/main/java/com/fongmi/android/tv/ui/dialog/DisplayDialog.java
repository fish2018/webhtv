package com.fongmi.android.tv.ui.dialog;

import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogDisplayBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DisplayDialog extends BaseAlertDialog {

    private static final float DIALOG_WIDTH_LANDSCAPE = 0.42f;
    private static final float DIALOG_WIDTH_PORTRAIT = 0.92f;

    private DialogDisplayBinding binding;
    private Runnable callback;
    private Mode mode = Mode.DISPLAY;

    public static void show(FragmentActivity activity, Runnable callback) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof DisplayDialog) return;
        new DisplayDialog().callback(callback).show(activity.getSupportFragmentManager(), null);
    }

    public static void showPlayerOsd(FragmentActivity activity, Runnable callback) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof DisplayDialog) return;
        new DisplayDialog().mode(Mode.PLAYER_OSD).callback(callback).show(activity.getSupportFragmentManager(), null);
    }

    private DisplayDialog callback(Runnable callback) {
        this.callback = callback;
        return this;
    }

    private DisplayDialog mode(Mode mode) {
        this.mode = mode;
        return this;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogDisplayBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        if (mode == Mode.PLAYER_OSD) {
            initPlayerOsdView();
            return;
        }
        initDisplayView();
    }

    private void initDisplayView() {
        binding.displayTime.setSelected(PlayerSetting.isDisplayTime());
        binding.displayTraffic.setSelected(PlayerSetting.isDisplayTraffic());
        binding.displaySize.setSelected(PlayerSetting.isDisplaySize());
        binding.displayProgress.setSelected(PlayerSetting.isDisplayProgress());
        binding.displayMini.setSelected(PlayerSetting.isDisplayMini());
        binding.displayTitle.setSelected(PlayerSetting.isDisplayTitle());
    }

    private void initPlayerOsdView() {
        initDisplayView();
        binding.displayParams.setVisibility(View.VISIBLE);
        binding.displayParams.setSelected(PlayerSetting.isOsdDiagnostics());
    }

    @Override
    protected void initEvent() {
        if (mode == Mode.PLAYER_OSD) {
            initPlayerOsdEvent();
            return;
        }
        initDisplayEvent();
    }

    private void initDisplayEvent() {
        binding.displayTime.setOnClickListener(v -> setDisplay(binding.displayTime, Display.TIME));
        binding.displayTraffic.setOnClickListener(v -> setDisplay(binding.displayTraffic, Display.TRAFFIC));
        binding.displaySize.setOnClickListener(v -> setDisplay(binding.displaySize, Display.SIZE));
        binding.displayProgress.setOnClickListener(v -> setDisplay(binding.displayProgress, Display.PROGRESS));
        binding.displayMini.setOnClickListener(v -> setDisplay(binding.displayMini, Display.MINI));
        binding.displayTitle.setOnClickListener(v -> setDisplay(binding.displayTitle, Display.TITLE));
    }

    private void initPlayerOsdEvent() {
        initDisplayEvent();
        binding.displayParams.setOnClickListener(v -> {
            boolean selected = !binding.displayParams.isSelected();
            binding.displayParams.setSelected(selected);
            PlayerSetting.putOsdDiagnostics(selected);
            if (callback != null) callback.run();
        });
    }

    private void setDisplay(TextView view, Display display) {
        boolean selected = !view.isSelected();
        view.setSelected(selected);
        switch (display) {
            case TIME:
                PlayerSetting.putDisplayTime(selected);
                break;
            case TRAFFIC:
                PlayerSetting.putDisplayTraffic(selected);
                break;
            case SIZE:
                PlayerSetting.putDisplaySize(selected);
                break;
            case PROGRESS:
                PlayerSetting.putDisplayProgress(selected);
                break;
            case MINI:
                PlayerSetting.putDisplayMini(selected);
                break;
            case TITLE:
                PlayerSetting.putDisplayTitle(selected);
                break;
        }
        if (callback != null) callback.run();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        setWidth(ResUtil.isLand(requireContext()) ? DIALOG_WIDTH_LANDSCAPE : DIALOG_WIDTH_PORTRAIT);
    }

    private enum Display {
        TIME, TRAFFIC, SIZE, PROGRESS, MINI, TITLE
    }

    private enum Mode {
        DISPLAY, PLAYER_OSD
    }
}
