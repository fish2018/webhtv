package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.databinding.DialogResultListBinding;
import com.fongmi.android.tv.ui.adapter.TmdbAdapter;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Util;
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
    private TmdbItem selectedItem;
    private Listener listener;
    private SearchListener searchListener;
    private SkipListener skipListener;

    public static TmdbSearchDialog create(Activity activity) {
        return new TmdbSearchDialog(activity);
    }

    public TmdbSearchDialog(Activity activity) {
        this.activity = activity;
        ContextThemeWrapper dialogContext = new ContextThemeWrapper(activity, R.style.Theme_WebHTV_LightDialog);
        this.binding = DialogResultListBinding.inflate(LayoutInflater.from(dialogContext));
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

    public TmdbSearchDialog selectedItem(TmdbItem selectedItem) {
        this.selectedItem = selectedItem;
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
        dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog).setView(binding.getRoot()).create();
        if (activity.isFinishing() || activity.isDestroyed()) return;
        dialog.show();
        LightDialog.apply(dialog);
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
        adapter.setSelectedItem(selectedItem);
        adapter.setItems(items == null ? Collections.emptyList() : items);
        focusInitialTarget();
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
        focusInitialTarget();
    }

    private void configureWindow() {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        boolean landscape = metrics.widthPixels > metrics.heightPixels;
        boolean leanback = Util.isLeanback();
        int chromeHeight = dp(searchListener != null ? (leanback ? 300 : 250) : (leanback ? 220 : 180));
        int availableListHeight = Math.max(1, metrics.heightPixels - chromeHeight);
        int minimumListHeight = Math.min(dp(leanback ? 240 : 180), availableListHeight);
        int preferredListHeight = leanback
                ? Math.round(metrics.heightPixels * 0.52f)
                : dp(320);
        int listHeight = Math.max(minimumListHeight, Math.min(availableListHeight, preferredListHeight));
        ViewGroup.LayoutParams params = binding.recycler.getLayoutParams();
        params.height = listHeight;
        binding.recycler.setLayoutParams(params);

        Window window = dialog.getWindow();
        if (window == null) return;
        int horizontalMargin = dp(landscape ? (leanback ? 64 : 96) : 32);
        int availableWidth = Math.max(1, metrics.widthPixels - horizontalMargin);
        int minimumWidth = Math.min(dp(leanback ? 720 : 280), availableWidth);
        int preferredWidth = leanback ? Math.round(metrics.widthPixels * 0.88f) : dp(760);
        int dialogWidth = Math.max(minimumWidth, Math.min(preferredWidth, availableWidth));
        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
        if (query != null) binding.query.setSelection(query.length());
        binding.query.setSelectAllOnFocus(false);
        binding.query.setOnClickListener(view -> showKeyboard());
        binding.querySearch.setVisibility(View.VISIBLE);
        binding.querySearch.setOnClickListener(view -> search());
        binding.query.setOnEditorActionListener((view, actionId, event) -> {
            if (!isSearchAction(actionId, event)) return false;
            search();
            return true;
        });
        binding.query.setOnKeyListener((view, keyCode, event) -> {
            if (!KeyUtil.isActionDown(event)) return false;
            if (Util.isLeanback() && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                showKeyboard();
                return true;
            }
            if (isSearchConfirmKey(event)) {
                search();
                return true;
            }
            if (KeyUtil.isRightKey(event) && isCursorAtEnd()) return binding.querySearch.requestFocus();
            if (KeyUtil.isDownKey(event)) return focusPreferredResult();
            if (Util.isLeanback()) return false;
            return false;
        });
    }

    private void showKeyboard() {
        Window window = dialog == null ? null : dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        binding.query.setCursorVisible(true);
        int length = binding.query.getText() == null ? 0 : binding.query.getText().length();
        binding.query.setSelection(length);
        Util.showKeyboard(binding.query);
    }

    private void search() {
        String keyword = binding.query.getText() == null ? "" : binding.query.getText().toString().trim();
        if (TextUtils.isEmpty(keyword) || searchListener == null) return;
        Util.hideKeyboard(binding.query);
        searchListener.onSearch(keyword, this);
    }

    private boolean isSearchAction(int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) return true;
        return isSearchConfirmKey(event);
    }

    private boolean isSearchConfirmKey(KeyEvent event) {
        if (event == null || !KeyUtil.isActionDown(event)) return false;
        int keyCode = event.getKeyCode();
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private boolean isCursorAtEnd() {
        return binding.query.getText() != null && binding.query.getSelectionEnd() >= binding.query.getText().length();
    }

    private void focusInitialTarget() {
        if (focusPreferredResult()) return;
        if (searchListener != null) binding.query.requestFocus();
    }

    private boolean focusPreferredResult() {
        int position = adapter == null ? RecyclerView.NO_POSITION : adapter.getSelectedPosition();
        if (position == RecyclerView.NO_POSITION) position = 0;
        return focusResult(position);
    }

    private boolean focusResult(int position) {
        if (adapter == null || position < 0 || position >= adapter.getItemCount()) return false;
        RecyclerView.LayoutManager manager = binding.recycler.getLayoutManager();
        if (manager instanceof LinearLayoutManager linearLayoutManager) {
            linearLayoutManager.scrollToPositionWithOffset(position, 0);
        } else {
            binding.recycler.scrollToPosition(position);
        }
        binding.recycler.post(() -> {
            RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(position);
            if (holder != null) holder.itemView.requestFocus();
            else binding.recycler.requestFocus();
        });
        return true;
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
