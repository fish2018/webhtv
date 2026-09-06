package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSitePickerBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SourceBlockItem;
import com.fongmi.android.tv.ui.adapter.SourceCheckAdapter;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SourceBlockDialog extends BaseAlertDialog {

    private DialogSitePickerBinding binding;
    private SourceCheckAdapter adapter;
    private Callback callback;

    public interface Callback {
        void onConfirm();
    }

    public static SourceBlockDialog create(Callback callback) {
        SourceBlockDialog d = new SourceBlockDialog();
        d.callback = callback;
        return d;
    }

    public void show(FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSitePickerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.setting_source_block_dialog_title).setView(getBinding().getRoot());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Override
    protected void initView() {
        binding.search.setVisibility(View.GONE);
        binding.count.setVisibility(View.GONE);
        adapter = new SourceCheckAdapter();
        binding.recycler.setAdapter(adapter);
        adapter.setItems(buildSourceItems());
    }

    private List<SourceBlockItem> buildSourceItems() {
        List<SourceBlockItem> items = new ArrayList<>();
        int[] sources = Setting.SOURCE_ALL;
        String[] names = {
            getString(R.string.source_vod_url),
            getString(R.string.source_live_url),
            getString(R.string.source_sites_json),
            getString(R.string.source_sites_js),
            getString(R.string.source_sites_py),
            getString(R.string.source_sites_raw),
            getString(R.string.source_lives_file)
        };
        for (int i = 0; i < sources.length; i++) {
            items.add(new SourceBlockItem(sources[i], names[i]));
        }
        return items;
    }

    @Override
    protected void initEvent() {
        Window w = getDialog() != null ? getDialog().getWindow() : null;
        if (w != null) {
            WindowManager.LayoutParams p = w.getAttributes();
            p.width = (int) (ResUtil.getScreenWidth() * 0.7f);
            w.setAttributes(p);
        }
        binding.selectAll.setOnClickListener(v -> adapter.selectAll());
        binding.selectNone.setOnClickListener(v -> adapter.selectNone());
        binding.selectInvert.setOnClickListener(v -> adapter.selectInvert());
        binding.confirm.setOnClickListener(v -> {
            Setting.putSourceBlockMask(adapter.getMask());
            if (callback != null) callback.onConfirm();
            dismiss();
        });
    }
}
