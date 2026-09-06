package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSitePickerBinding;
import com.fongmi.android.tv.ui.adapter.SiteCheckAdapter;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchSourceHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SearchSitePickerDialog extends BaseAlertDialog {

    private DialogSitePickerBinding binding;
    private SiteCheckAdapter adapter;
    private final List<String> initial;
    private final boolean tagMode;
    private Callback callback;

    public interface Callback {
        void onConfirm(List<String> keys);
    }

    public static SearchSitePickerDialog createSite(List<String> initial, Callback callback) {
        SearchSitePickerDialog d = new SearchSitePickerDialog(initial, false);
        d.callback = callback;
        return d;
    }

    public static SearchSitePickerDialog createTag(List<String> initial, Callback callback) {
        SearchSitePickerDialog d = new SearchSitePickerDialog(initial, true);
        d.callback = callback;
        return d;
    }

    private SearchSitePickerDialog(List<String> initial, boolean tagMode) {
        this.initial = initial == null ? new ArrayList<>() : new ArrayList<>(initial);
        this.tagMode = tagMode;
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
        setupVisibility();
        adapter = new SiteCheckAdapter();
        binding.recycler.setAdapter(adapter);
        adapter.setItems(SearchSourceHelper.buildPickerSites(tagMode), new HashSet<>(initial));
        updateCount();
    }

    private void setupVisibility() {
        boolean isSite = !tagMode;
        binding.search.setVisibility(isSite ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.count.setVisibility(isSite ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void updateCount() {
        if (binding.count.getVisibility() == android.view.View.VISIBLE) {
            binding.count.setText(getString(R.string.search_site_count_of, adapter.getCheckedCount(), adapter.getTotalCount()));
        }
    }

    @Override
    protected void initEvent() {
        Window w = getDialog() != null ? getDialog().getWindow() : null;
        if (w != null) {
            WindowManager.LayoutParams p = w.getAttributes();
            p.width = (int) (ResUtil.getScreenWidth() * 0.7f);
            w.setAttributes(p);
        }
        binding.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (adapter != null) adapter.filter(s.toString());
            }
        });
        binding.selectAll.setOnClickListener(v -> { adapter.selectAll(); updateCount(); });
        binding.selectNone.setOnClickListener(v -> { adapter.selectNone(); updateCount(); });
        binding.selectInvert.setOnClickListener(v -> { adapter.selectInvert(); updateCount(); });
        binding.confirm.setOnClickListener(v -> {
            if (callback != null) callback.onConfirm(new ArrayList<>(adapter.getCheckedKeys()));
            dismiss();
        });
    }
}
