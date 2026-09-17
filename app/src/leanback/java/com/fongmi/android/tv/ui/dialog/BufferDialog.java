package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogBufferBinding;
import com.fongmi.android.tv.impl.BufferListener;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.KeyUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class BufferDialog extends BaseAlertDialog {

    private DialogBufferBinding binding;

    public static void show(FragmentActivity activity) {
        String[] items = new String[15];
        for (int i = 0; i < items.length; i++) items[i] = (i + 1) + activity.getString(R.string.times);
        ChoiceDialog.showSingle(activity, R.string.player_buffer, items, PlayerSetting.getBuffer() - 1, which -> ((BufferListener) activity).setBuffer(which + 1));
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = LightDialog.create(requireContext(), getString(com.fongmi.android.tv.R.string.player_buffer), getBinding().getRoot());
        initView();
        initEvent();
        return dialog;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogBufferBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.slider.setValue(PlayerSetting.getBuffer());
    }

    @Override
    protected void initEvent() {
        binding.slider.addOnChangeListener((slider, value, fromUser) -> ((BufferListener) requireActivity()).setBuffer((int) value));
        binding.slider.setOnKeyListener((view, keyCode, event) -> {
            boolean enter = KeyUtil.isEnterKey(event);
            if (enter) dismiss();
            return enter;
        });
    }

}
