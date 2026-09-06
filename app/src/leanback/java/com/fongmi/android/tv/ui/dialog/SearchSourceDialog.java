package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogSearchSourceBinding;
import com.fongmi.android.tv.ui.adapter.SearchSourceAdapter;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchModeStore;
import com.fongmi.android.tv.utils.SearchSourceHelper;
import com.fongmi.android.tv.utils.SearchSourceItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SearchSourceDialog extends BaseAlertDialog {

    private DialogSearchSourceBinding binding;
    private SearchSourceAdapter adapter;

    public static SearchSourceDialog create() {
        return new SearchSourceDialog();
    }

    public void show(FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSearchSourceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
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
        adapter = new SearchSourceAdapter();
        adapter.setItems(SearchSourceHelper.buildItems(), SearchModeStore.getMode());
        binding.recycler.setAdapter(adapter);
        adapter.setOnConfigListener(this::openConfig);
        adapter.setOnModeChangeListener(mode -> {
            SearchModeStore.putMode(mode);
            dismiss();
        });
    }

    @Override
    protected void initEvent() {
        Window w = getDialog() != null ? getDialog().getWindow() : null;
        if (w != null) {
            WindowManager.LayoutParams p = w.getAttributes();
            p.width = (int) (ResUtil.getScreenWidth() * 0.55f);
            w.setAttributes(p);
        }
    }

    private void openConfig(SearchSourceItem item) {
        int mode = item.mode;
        if (mode == SearchModeStore.MODE_WHITE || mode == SearchModeStore.MODE_BLACK) {
            List<String> initial = mode == SearchModeStore.MODE_WHITE
                    ? SearchModeStore.getWhiteList() : SearchModeStore.getBlackList();
            SearchSitePickerDialog.createSite(new ArrayList<>(initial), selected -> {
                if (mode == SearchModeStore.MODE_WHITE) SearchModeStore.putWhiteList(new ArrayList<>(selected));
                else SearchModeStore.putBlackList(new ArrayList<>(selected));
                SearchModeStore.putMode(mode);
                dismiss();
            }).show(requireActivity());
        } else if (mode == SearchModeStore.MODE_TAG) {
            SearchSitePickerDialog.createTag(new ArrayList<>(SearchModeStore.getTagGroups()), selected -> {
                SearchModeStore.putTagGroups(new ArrayList<>(selected));
                SearchModeStore.putMode(mode);
                dismiss();
            }).show(requireActivity());
        }
    }
}
