package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public abstract class BaseAlertDialog extends DialogFragment {

    protected abstract ViewBinding getBinding();

    protected abstract MaterialAlertDialogBuilder getBuilder();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = getBuilder().create();
        initView();
        initEvent();
        return dialog;
    }

    protected MaterialAlertDialogBuilder builder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.MaterialAlertDialog_WebHTV_Rounded);
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected void setWidth(float factor) {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * factor);
        getDialog().getWindow().setAttributes(params);
    }
}
