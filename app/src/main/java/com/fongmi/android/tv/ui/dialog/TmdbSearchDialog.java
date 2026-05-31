package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.databinding.DialogResultListBinding;
import com.fongmi.android.tv.ui.adapter.TmdbAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;
import java.util.List;

public class TmdbSearchDialog {

    public interface Listener {
        void onItemClick(TmdbItem item);
    }

    public interface SearchListener {
        void onSearch(String keyword, TmdbSearchDialog dialog);
    }

    public interface SkipListener {
        void onSkip();
    }

    private final Activity activity;
    private final DialogResultListBinding binding;
    private AlertDialog dialog;
    private TmdbAdapter adapter;
    private String title;
    private String query;
    private List<TmdbItem> items;
    private Listener listener;
    private SearchListener searchListener;
    private SkipListener skipListener;

    public static TmdbSearchDialog create(Activity activity) {
        return new TmdbSearchDialog(activity);
    }

    public TmdbSearchDialog(Activity activity) {
        this.activity = activity;
        this.binding = DialogResultListBinding.inflate(LayoutInflater.from(activity));
    }

    public TmdbSearchDialog title(String title) {
        this.title = title;
        return this;
    }

    public TmdbSearchDialog items(List<TmdbItem> items) {
        this.items = items;
        return this;
    }

    public TmdbSearchDialog query(String query) {
        this.query = query;
        return this;
    }

    public TmdbSearchDialog listener(Listener listener) {
        this.listener = listener;
        return this;
    }

    public TmdbSearchDialog searchListener(SearchListener searchListener) {
        this.searchListener = searchListener;
        return this;
    }

    public TmdbSearchDialog skipListener(SkipListener skipListener) {
        this.skipListener = skipListener;
        return this;
    }

    public void show() {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        if (activity.isFinishing() || activity.isDestroyed()) return;
        dialog.show();
        configureWindow();
        binding.title.setText(title);
        configureActions();
        configureSearch();
        updateStatus();
        binding.recycler.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new TmdbAdapter(item -> {
            setLoading(true);
            if (listener != null) listener.onItemClick(item);
            dialog.dismiss();
        });
        binding.recycler.setAdapter(adapter);
        adapter.setItems(items == null ? Collections.emptyList() : items);
    }

    public void loading() {
        setLoading(true);
        binding.status.setText(R.string.detail_tmdb_searching);
        if (adapter != null) adapter.setItems(Collections.emptyList());
    }

    public void updateItems(List<TmdbItem> items) {
        this.items = items;
        setLoading(false);
        updateStatus();
        if (adapter != null) adapter.setItems(items == null ? Collections.emptyList() : items);
    }

    private void configureWindow() {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        boolean landscape = metrics.widthPixels > metrics.heightPixels;
        int chromeHeight = dp(searchListener != null ? 250 : 180);
        int listHeight = Math.min(dp(320), Math.max(dp(180), metrics.heightPixels - chromeHeight));
        ViewGroup.LayoutParams params = binding.recycler.getLayoutParams();
        params.height = listHeight;
        binding.recycler.setLayoutParams(params);

        Window window = dialog.getWindow();
        if (window == null) return;
        int horizontalMargin = dp(landscape ? 96 : 32);
        int availableWidth = Math.max(dp(280), metrics.widthPixels - horizontalMargin);
        int dialogWidth = Math.min(dp(760), Math.min(metrics.widthPixels - dp(16), availableWidth));
        window.setDimAmount(0);
        window.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void configureActions() {
        binding.cancel.setOnClickListener(view -> dialog.dismiss());
        binding.skip.setVisibility(skipListener != null ? View.VISIBLE : View.GONE);
        binding.skip.setOnClickListener(view -> {
            if (skipListener != null) skipListener.onSkip();
            dialog.dismiss();
        });
        setLoading(false);
    }

    private void configureSearch() {
        boolean searchable = searchListener != null;
        binding.searchBar.setVisibility(searchable ? View.VISIBLE : View.GONE);
        if (!searchable) return;
        binding.query.setText(query);
        binding.query.setSelectAllOnFocus(true);
        binding.querySearch.setOnClickListener(view -> search());
        binding.query.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            search();
            return true;
        });
    }

    private void search() {
        String keyword = binding.query.getText() == null ? "" : binding.query.getText().toString().trim();
        if (TextUtils.isEmpty(keyword) || searchListener == null) return;
        searchListener.onSearch(keyword, this);
    }

    private void setLoading(boolean loading) {
        binding.actionProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.querySearch.setEnabled(!loading);
        binding.query.setEnabled(!loading);
        binding.recycler.setEnabled(!loading);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void updateStatus() {
        binding.status.setText(items == null || items.isEmpty() ? activity.getString(R.string.detail_tmdb_empty) : activity.getString(R.string.detail_tmdb_result_count, items.size()));
    }
}
