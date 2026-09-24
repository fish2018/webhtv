package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogSpeedBinding;
import com.fongmi.android.tv.impl.SpeedListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.KeyUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SpeedDialog extends BaseAlertDialog {

    private DialogSpeedBinding binding;

    public static void show(Fragment fragment) {
        new SpeedDialog().show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity) {
        new SpeedDialog().show(activity.getSupportFragmentManager(), null);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = LightDialog.create(requireContext(), getString(com.fongmi.android.tv.R.string.player_speed), getBinding().getRoot());
        initView();
        initEvent();
        return dialog;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSpeedBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.slider.setValue(PlayerSetting.getSpeed());
    }

    @Override
    protected void initEvent() {
        binding.slider.addOnChangeListener((slider, value, fromUser) -> ((SpeedListener) requireParentFragment()).setSpeed(value));
        binding.slider.setOnKeyListener((view, keyCode, event) -> {
            boolean enter = KeyUtil.isEnterKey(event);
            if (enter) dismiss();
            return enter;
        });
    }

}
