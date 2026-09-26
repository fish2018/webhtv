package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogSearchSourceBinding;
import com.fongmi.android.tv.ui.adapter.SearchSourceAdapter;
import com.fongmi.android.tv.utils.SearchModeStore;
import com.fongmi.android.tv.utils.SearchSourceHelper;
import com.fongmi.android.tv.utils.SearchSourceItem;

import java.util.ArrayList;
import java.util.List;

public class SearchSourceDialog extends BaseBottomSheetDialog {

    private DialogSearchSourceBinding binding;
    private SearchSourceAdapter adapter;

    public static SearchSourceDialog create() {
        return new SearchSourceDialog();
    }

    public void show(Fragment fragment) {
        for (Fragment f : fragment.getChildFragmentManager().getFragments()) if (f instanceof SearchSourceDialog) return;
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogSearchSourceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        adapter = new SearchSourceAdapter();
        adapter.setItems(SearchSourceHelper.buildItems(), SearchModeStore.getMode());
        binding.recycler.setAdapter(adapter);
        adapter.setOnConfigListener(this::openConfig);
        adapter.setOnModeChangeListener(mode -> {
            SearchModeStore.putMode(mode);
            dismiss();
        });
    }

    private void openConfig(SearchSourceItem item) {
        int mode = item.mode;
        if (mode == SearchModeStore.MODE_WHITE || mode == SearchModeStore.MODE_BLACK) {
            List<String> initial = mode == SearchModeStore.MODE_WHITE
                    ? SearchModeStore.getWhiteList() : SearchModeStore.getBlackList();
            SearchSitePickerDialog.createSite(null, new ArrayList<>(initial), selected -> {
                if (mode == SearchModeStore.MODE_WHITE) SearchModeStore.putWhiteList(new ArrayList<>(selected));
                else SearchModeStore.putBlackList(new ArrayList<>(selected));
                SearchModeStore.putMode(mode);
                dismiss();
            }).show(this);
        } else if (mode == SearchModeStore.MODE_TAG) {
            SearchSitePickerDialog.createTag(new ArrayList<>(SearchModeStore.getTagGroups()), selected -> {
                SearchModeStore.putTagGroups(new ArrayList<>(selected));
                SearchModeStore.putMode(mode);
                dismiss();
            }).show(this);
        }
    }
}
