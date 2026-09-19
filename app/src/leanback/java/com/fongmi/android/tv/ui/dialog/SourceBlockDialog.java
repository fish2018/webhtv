package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSitePickerBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SourceBlockItem;
import com.fongmi.android.tv.ui.adapter.SourceCheckAdapter;

import java.util.ArrayList;
import java.util.List;

public class SourceBlockDialog extends BaseGlassDialog {

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
    protected float getWidthRatio() {
        return 0.7f;
    }

    @Override
    protected void initView() {
        binding.search.setVisibility(View.GONE);
        binding.count.setVisibility(View.GONE);
        binding.selectAll.setBackground(BaseGlassDialog.glassItemBackground());
        binding.selectNone.setBackground(BaseGlassDialog.glassItemBackground());
        binding.selectInvert.setBackground(BaseGlassDialog.glassItemBackground());
        binding.confirm.setBackground(BaseGlassDialog.glassItemBackground());
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
