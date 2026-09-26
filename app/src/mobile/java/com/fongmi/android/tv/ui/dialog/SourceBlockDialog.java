package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSitePickerBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SourceBlockItem;
import com.fongmi.android.tv.ui.adapter.SourceCheckAdapter;

import java.util.ArrayList;
import java.util.List;

public class SourceBlockDialog extends BaseBottomSheetDialog {

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

    public void show(Fragment parent) {
        show(parent.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogSitePickerBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.search.setVisibility(View.VISIBLE);
        binding.search.setFocusable(false);
        binding.search.setCursorVisible(false);
        binding.search.setText(R.string.title_block_source);
        binding.search.setTextSize(18);
        binding.search.setTypeface(null, android.graphics.Typeface.BOLD);
        binding.search.setTextColor(getResources().getColor(R.color.black));
        binding.search.setPadding(48,32,48,16);
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
