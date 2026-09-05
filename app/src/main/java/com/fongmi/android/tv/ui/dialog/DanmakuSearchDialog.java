package com.fongmi.android.tv.ui.dialog;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.DanmakuMatchCache;
import com.fongmi.android.tv.bean.DanmakuTitle;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.title.MediaTitleLearningExample;
import com.fongmi.android.tv.title.MediaTitleLearningStore;
import com.fongmi.android.tv.ui.custom.CustomRecyclerView;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public final class DanmakuSearchDialog extends DialogFragment implements Callback {

    private final ResultAdapter adapter;
    private final Map<String, List<Danmaku>> groups;
    private final List<MaterialTextView> sourceViews;
    private TextInputEditText keywordView;
    private MaterialTextView searchView;
    private HorizontalScrollView sourceScroller;
    private LinearLayout searchRow;
    private LinearLayout sourceTabs;
    private CustomRecyclerView recycler;
    private CircularProgressIndicator progress;
    private TextView empty;
    private CharSequence keyword;
    private PlayerManager player;
    private volatile Call activeCall;
    private boolean autoSearch;
    private boolean hideKeyword;
    private boolean restoreParent;
    private String selectedSource;
    private String siteKey;
    private String vodId;
    private String rawTitle;
    private String episodeName;

    public static DanmakuSearchDialog create() {
        return new DanmakuSearchDialog();
    }

    public DanmakuSearchDialog() {
        this.adapter = new ResultAdapter(this::onItemClick);
        this.groups = new LinkedHashMap<>();
        this.sourceViews = new ArrayList<>();
    }

    public DanmakuSearchDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public DanmakuSearchDialog restoreParent(boolean restoreParent) {
        this.restoreParent = restoreParent;
        return this;
    }

    public DanmakuSearchDialog keyword(CharSequence keyword) {
        this.keyword = keyword;
        return this;
    }

    public DanmakuSearchDialog autoSearch(boolean autoSearch) {
        this.autoSearch = autoSearch;
        return this;
    }

    public DanmakuSearchDialog hideKeyword(boolean hideKeyword) {
        this.hideKeyword = hideKeyword;
        return this;
    }

    public DanmakuSearchDialog identity(String siteKey, String vodId, String rawTitle, String episodeName) {
        this.siteKey = clean(siteKey);
        this.vodId = clean(vodId);
        this.rawTitle = clean(rawTitle);
        this.episodeName = clean(episodeName);
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuSearchDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        keywordView = createKeywordView();
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(createContentView());
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(d -> {
            bindEvents();
            setKeyword(keyword == null ? getTitle() : keyword);
            if (hideKeyword) searchRow.setVisibility(GONE);
            if (autoSearch) keywordView.post(this::search);
            else Util.showKeyboard(keywordView);
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Util.isFullscreen(getActivity())) Util.hideSystemUI(window);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean landscape = metrics.widthPixels > metrics.heightPixels;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(window.getAttributes());
        params.width = landscape || Util.isLeanback() ? Math.min((int) (metrics.widthPixels * 0.76f), dp(920)) : Math.min(metrics.widthPixels - dp(32), dp(560));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        activeCall = null;
        DanmakuApi.cancel();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        FragmentActivity activity = getActivity();
        if (!restoreParent || activity == null || activity.isFinishing()) return;
        DanmakuDialog.create().player(player).identity(siteKey, vodId, rawTitle, episodeName).show(activity);
    }

    private LinearLayout createContentView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(24));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackground(round(Color.parseColor("#F226282C"), 10, Color.TRANSPARENT));
        root.addView(createTitleView(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        root.addView(createSearchRow(), createTopParams(16));
        root.addView(createSourceScroller(), createTopParams(14));
        root.addView(createResultFrame(), createTopParams(16));
        return root;
    }

    private MaterialTextView createTitleView() {
        MaterialTextView title = new MaterialTextView(requireContext());
        title.setText(getString(R.string.danmaku) + getString(R.string.play_search));
        title.setTextColor(Color.WHITE);
        title.setTextSize(Util.isLeanback() ? 20 : 18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        return title;
    }

    private LinearLayout createSearchRow() {
        searchView = createSearchButton();
        keywordView.setId(View.generateViewId());
        searchView.setId(View.generateViewId());
        keywordView.setNextFocusRightId(searchView.getId());
        searchView.setNextFocusLeftId(keywordView.getId());

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams keywordParams = new LinearLayout.LayoutParams(0, dp(Util.isLeanback() ? 48 : 44), 1);
        row.addView(keywordView, keywordParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(Util.isLeanback() ? 112 : 92), dp(Util.isLeanback() ? 48 : 44));
        actionParams.setMarginStart(dp(Util.isLeanback() ? 14 : 10));
        row.addView(searchView, actionParams);
        return searchRow = row;
    }

    private TextInputEditText createKeywordView() {
        TextInputEditText edit = new TextInputEditText(requireContext());
        edit.setSingleLine(true);
        edit.setMaxLines(1);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        edit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(Color.parseColor("#99FFFFFF"));
        edit.setTextSize(Util.isLeanback() ? 16 : 14);
        edit.setHint(R.string.search_keyword);
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setBackground(round(Color.parseColor("#263A3C41"), 9, Color.parseColor("#33FFFFFF")));
        return edit;
    }

    private MaterialTextView createSearchButton() {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(R.string.play_search);
        view.setTextColor(Color.WHITE);
        view.setTextSize(Util.isLeanback() ? 16 : 14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(buttonBackground("#FF1A73E8", "#CC1A73E8", 7));
        return view;
    }

    private HorizontalScrollView createSourceScroller() {
        sourceTabs = new LinearLayout(requireContext());
        sourceTabs.setOrientation(LinearLayout.HORIZONTAL);
        sourceTabs.setGravity(Gravity.CENTER_VERTICAL);
        sourceTabs.setClipChildren(false);
        sourceTabs.setClipToPadding(false);

        sourceScroller = new HorizontalScrollView(requireContext());
        sourceScroller.setHorizontalScrollBarEnabled(false);
        sourceScroller.setFillViewport(true);
        sourceScroller.setOverScrollMode(HorizontalScrollView.OVER_SCROLL_NEVER);
        sourceScroller.setClipChildren(false);
        sourceScroller.setClipToPadding(false);
        sourceScroller.setVisibility(GONE);
        sourceScroller.addView(sourceTabs, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return sourceScroller;
    }

    private FrameLayout createResultFrame() {
        FrameLayout frame = new FrameLayout(requireContext());
        frame.setClipChildren(false);
        frame.setClipToPadding(false);

        progress = new CircularProgressIndicator(requireContext());
        progress.setIndeterminate(true);
        progress.setIndicatorColor(Color.parseColor("#E6FFFFFF"));
        progress.setIndicatorSize(dp(Util.isLeanback() ? 36 : 32));
        progress.setTrackThickness(dp(Util.isLeanback() ? 3 : 2));
        progress.setVisibility(GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        progressParams.setMargins(0, dp(24), 0, dp(24));
        frame.addView(progress, progressParams);

        empty = new TextView(requireContext());
        empty.setGravity(Gravity.CENTER);
        empty.setText(R.string.error_empty);
        empty.setTextColor(Color.parseColor("#99FFFFFF"));
        empty.setTextSize(Util.isLeanback() ? 16 : 14);
        empty.setVisibility(GONE);
        frame.addView(empty, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(Util.isLeanback() ? 96 : 72), Gravity.CENTER));

        recycler = new CustomRecyclerView(requireContext());
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setItemAnimator(null);
        recycler.setHasFixedSize(false);
        recycler.setMaxHeight(dp(Util.isLeanback() ? 312 : 260));
        recycler.setAdapter(adapter);
        recycler.setVisibility(GONE);
        recycler.setClipChildren(false);
        recycler.setClipToPadding(false);
        recycler.setId(View.generateViewId());
        keywordView.setNextFocusDownId(recycler.getId());
        searchView.setNextFocusDownId(recycler.getId());
        frame.addView(recycler, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return frame;
    }

    private LinearLayout.LayoutParams createTopParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        return params;
    }

    private void bindEvents() {
        keywordView.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && !getKeywordText().isEmpty()) search();
            return true;
        });
        keywordView.setOnKeyListener((view, keyCode, event) -> {
            if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && recycler.getVisibility() == VISIBLE) return focusResultGroup();
            return false;
        });
        searchView.setOnKeyListener((view, keyCode, event) -> {
            if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && recycler.getVisibility() == VISIBLE) return focusResultGroup();
            return false;
        });
        searchView.setOnClickListener(view -> {
            if (!getKeywordText().isEmpty()) search();
        });
    }

    private void onItemClick(Danmaku item) {
        restoreParent = false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "search item click selected=%s name=%s url=%s", item.isSelected(), item.getName(), item.getUrl());
        if (!item.isSelected()) rememberManualDanmaku(item);
        if (player != null) player.setDanmaku(item.isSelected() ? Danmaku.empty() : item);
        dismiss();
    }

    private void rememberManualDanmaku(Danmaku item) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId) || item == null || item.isEmpty()) return;
        DanmakuMatchCache cache = Setting.getDanmakuMatchCache();
        MediaTitleLearningExample example = cache.put(siteKey, vodId, first(episodeName, getEpisode().toString().trim()), getKeywordText(), first(rawTitle, getTitle().toString()), item);
        Setting.putDanmakuMatchCache(cache);
        if (example != null) MediaTitleLearningStore.load().put(example);
    }

    private void setKeyword(CharSequence text) {
        CharSequence value = text == null ? "" : text;
        keywordView.setText(value);
        keywordView.setSelection(value.length());
    }

    private String getKeywordText() {
        return keywordView == null || keywordView.getText() == null ? "" : keywordView.getText().toString().trim();
    }

    private String first(String first, String second) {
        return !TextUtils.isEmpty(first) ? first : clean(second);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private CharSequence getTitle() {
        return player == null || player.getMetadata() == null || player.getMetadata().title == null ? "" : player.getMetadata().title;
    }

    private CharSequence getEpisode() {
        return player == null || player.getMetadata() == null || player.getMetadata().artist == null ? "" : player.getMetadata().artist;
    }

    private void showProgress() {
        selectedSource = null;
        groups.clear();
        adapter.clear();
        renderSourceTabs();
        empty.setVisibility(GONE);
        recycler.setVisibility(GONE);
        sourceScroller.setVisibility(GONE);
        progress.setVisibility(VISIBLE);
    }

    private void hideProgress(boolean emptyResult) {
        progress.setVisibility(GONE);
        recycler.setVisibility(emptyResult ? GONE : VISIBLE);
        sourceScroller.setVisibility(!emptyResult && groups.size() > 1 ? VISIBLE : GONE);
        empty.setVisibility(emptyResult ? VISIBLE : GONE);
    }

    private void search() {
        showProgress();
        activeCall = null;
        Util.hideKeyboard(keywordView);
        Call call = DanmakuApi.newCall(getKeywordText(), getEpisode().toString().trim());
        if (call == null) {
            hideProgress(true);
            Notify.show(R.string.danmaku_api_invalid);
            return;
        }
        activeCall = call;
        call.enqueue(this);
    }

    private void onSuccess(List<Danmaku> items) {
        syncCurrentState(items);
        buildGroups(items);
        renderSourceTabs();
        showSourceItems();
        hideProgress(groups.isEmpty());
        focusResultGroup();
    }

    private void syncCurrentState(List<Danmaku> items) {
        String selectedUrl = getSelectedDanmakuUrl();
        for (Danmaku item : items) item.setSelected(!TextUtils.isEmpty(selectedUrl) && selectedUrl.equals(item.getUrl()));
    }

    private String getSelectedDanmakuUrl() {
        if (player == null || player.getDanmakus() == null) return "";
        for (Danmaku item : player.getDanmakus()) if (item != null && item.isSelected()) return item.getUrl();
        return "";
    }

    private void buildGroups(List<Danmaku> items) {
        groups.clear();
        for (Danmaku item : items) {
            String source = normalizeSource(item.getSourceName());
            if (!groups.containsKey(source)) groups.put(source, new ArrayList<>());
            groups.get(source).add(item);
        }
        selectedSource = chooseInitialSource();
    }

    private String chooseInitialSource() {
        for (Map.Entry<String, List<Danmaku>> entry : groups.entrySet()) {
            for (Danmaku item : entry.getValue()) if (item.isSelected()) return entry.getKey();
        }
        if (!groups.isEmpty()) return groups.keySet().iterator().next();
        return "";
    }

    private void renderSourceTabs() {
        sourceViews.clear();
        sourceTabs.removeAllViews();
        sourceScroller.setVisibility(groups.size() > 1 ? VISIBLE : GONE);
        int index = 0;
        for (String source : groups.keySet()) {
            MaterialTextView view = createSourceView(source);
            LinearLayout.LayoutParams params = createSourceParams(groups.size(), index++);
            sourceTabs.addView(view, params);
            sourceViews.add(view);
        }
        updateSourceTabs();
    }

    private LinearLayout.LayoutParams createSourceParams(int count, int index) {
        int height = dp(Util.isLeanback() ? 42 : 36);
        LinearLayout.LayoutParams params = count <= 3 ? new LinearLayout.LayoutParams(0, height, 1) : new LinearLayout.LayoutParams(dp(Util.isLeanback() ? 150 : 112), height);
        if (index > 0) params.setMarginStart(dp(Util.isLeanback() ? 10 : 8));
        return params;
    }

    private MaterialTextView createSourceView(String source) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(displaySource(source));
        view.setTag(source);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextSize(Util.isLeanback() ? 15 : 13);
        view.setTextColor(sourceTextColor());
        view.setBackground(sourceBackground());
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(this::onSourceClick);
        return view;
    }

    private void onSourceClick(View view) {
        Object tag = view.getTag();
        if (!(tag instanceof String source)) return;
        selectedSource = source;
        updateSourceTabs();
        showSourceItems();
        recycler.scrollToPosition(0);
        if (Util.isLeanback()) view.requestFocus();
    }

    private void updateSourceTabs() {
        for (MaterialTextView view : sourceViews) view.setSelected(TextUtils.equals(selectedSource, String.valueOf(view.getTag())));
    }

    private void showSourceItems() {
        adapter.clear();
        adapter.addAll(groups.get(selectedSource), getKeywordText());
        recycler.setVisibility(adapter.getItemCount() == 0 ? GONE : VISIBLE);
    }

    private boolean focusResultGroup() {
        if (Util.isLeanback() && sourceScroller.getVisibility() == VISIBLE && !sourceViews.isEmpty()) {
            for (MaterialTextView view : sourceViews) if (view.isSelected()) return view.requestFocus();
            return sourceViews.get(0).requestFocus();
        }
        return recycler.requestFocus();
    }

    private void onError(Exception e) {
        hideProgress(true);
        Notify.show(e.getMessage());
        if (hideKeyword) dismissAllowingStateLoss();
    }

    private String normalizeSource(String source) {
        if (TextUtils.isEmpty(source)) return "";
        return source.matches("[A-Za-z0-9_\\-]+") ? source.toUpperCase() : source;
    }

    private String displaySource(String source) {
        return TextUtils.isEmpty(source) || "默认".equals(source) ? getString(R.string.danmaku) : source;
    }

    private Drawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private Drawable buttonBackground(String focused, String normal, int radius) {
        GradientDrawable focusedDrawable = new GradientDrawable();
        focusedDrawable.setColor(Color.parseColor(focused));
        focusedDrawable.setCornerRadius(dp(radius));
        focusedDrawable.setStroke(dp(2), Color.WHITE);

        GradientDrawable normalDrawable = new GradientDrawable();
        normalDrawable.setColor(Color.parseColor(normal));
        normalDrawable.setCornerRadius(dp(radius));
        return new android.graphics.drawable.StateListDrawable() {{
            addState(new int[]{android.R.attr.state_focused}, focusedDrawable);
            addState(new int[]{}, normalDrawable);
        }};
    }

    private Drawable sourceBackground() {
        GradientDrawable selected = new GradientDrawable();
        selected.setColor(Color.parseColor("#FF1A73E8"));
        selected.setCornerRadius(dp(7));
        selected.setStroke(dp(1), Color.parseColor("#806FA8FF"));

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.parseColor("#663A7AFE"));
        focused.setCornerRadius(dp(7));
        focused.setStroke(dp(2), Color.WHITE);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.parseColor("#2EFFFFFF"));
        normal.setCornerRadius(dp(7));
        normal.setStroke(dp(1), Color.parseColor("#24FFFFFF"));

        return new android.graphics.drawable.StateListDrawable() {{
            addState(new int[]{android.R.attr.state_focused, android.R.attr.state_selected}, focused);
            addState(new int[]{android.R.attr.state_selected}, selected);
            addState(new int[]{android.R.attr.state_focused}, focused);
            addState(new int[]{}, normal);
        }};
    }

    private ColorStateList sourceTextColor() {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_selected}, new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{Color.WHITE, Color.WHITE, Color.parseColor("#E6FFFFFF")});
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) {
        if (call != activeCall) return;
        try {
            String body = response.body() == null ? "" : response.body().string();
            List<Danmaku> items = DanmakuApi.arrayFrom(body);
            if (items.isEmpty()) throw new Exception(ResUtil.getString(R.string.error_empty));
            App.post(() -> onSuccess(items));
        } catch (Exception e) {
            App.post(() -> onError(e));
        }
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        if (call != activeCall) return;
        App.post(() -> onError(e));
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    private static final class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        private final OnClickListener listener;
        private final List<Object> rows;

        private interface OnClickListener {

            void onItemClick(Danmaku item);
        }

        private ResultAdapter(OnClickListener listener) {
            this.listener = listener;
            this.rows = new ArrayList<>();
        }

        private void clear() {
            int size = rows.size();
            rows.clear();
            if (size > 0) notifyItemRangeRemoved(0, size);
        }

        /**
         * 按剧名二次分组装配列表：同一剧名≥2条折叠成一个 Header（默认折叠），
         * 单条或解析不出剧名的直接作为普通项平铺。含已选中弹幕的分组默认展开。
         * 分组头按关键词相似度排序，子项按集数排序。
         */
        private void addAll(List<Danmaku> values, String keyword) {
            if (values == null) return;
            // titleKey -> 该组弹幕；null key 单独收集为平铺项
            LinkedHashMap<String, List<Danmaku>> grouped = new LinkedHashMap<>();
            List<Danmaku> ungrouped = new ArrayList<>();
            for (Danmaku item : values) {
                String key = DanmakuTitle.titleKey(item);
                if (key == null) {
                    ungrouped.add(item);
                } else {
                    grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
                }
            }
            // 组内按集数排序
            for (List<Danmaku> items : grouped.values()) DanmakuTitle.sortByEpisode(items);
            DanmakuTitle.sortByEpisode(ungrouped);
            // 分组头按关键词相似度排序
            List<String> titleKeys = new ArrayList<>(grouped.keySet());
            DanmakuTitle.sortByKeyword(titleKeys, keyword);
            for (String key : titleKeys) {
                List<Danmaku> items = grouped.get(key);
                if (items.size() < 2) {
                    ungrouped.addAll(items);
                } else {
                    boolean hasSelected = false;
                    for (Danmaku item : items) if (item.isSelected()) { hasSelected = true; break; }
                    Header header = new Header(key, items, hasSelected);
                    rows.add(header);
                    if (hasSelected) rows.addAll(items); // 默认展开：预插入子项
                }
            }
            rows.addAll(ungrouped);
            if (!rows.isEmpty()) notifyItemRangeInserted(0, rows.size());
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof Header ? TYPE_HEADER : TYPE_ITEM;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialTextView view = new MaterialTextView(parent.getContext());
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(parent.getContext(), Util.isLeanback() ? 52 : 48));
            params.setMargins(0, 0, 0, dp(parent.getContext(), Util.isLeanback() ? 12 : 10));
            view.setLayoutParams(params);
            return viewType == TYPE_HEADER ? new HeaderHolder(view) : new ItemHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (holder instanceof HeaderHolder) ((HeaderHolder) holder).bind((Header) row, position);
            else ((ItemHolder) holder).bind((Danmaku) row);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        /** 切换某分组的展开/折叠状态，插入或移除其子项。 */
        private void toggleHeader(int position) {
            Header header = (Header) rows.get(position);
            header.expanded = !header.expanded;
            notifyItemChanged(position);
            if (header.expanded) {
                rows.addAll(position + 1, header.items);
                notifyItemRangeInserted(position + 1, header.items.size());
            } else {
                int count = header.items.size();
                for (int i = 0; i < count; i++) rows.remove(position + 1);
                notifyItemRangeRemoved(position + 1, count);
            }
        }

        private abstract static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(@NonNull MaterialTextView text) {
                super(text);
            }
        }

        /** 剧名折叠分组头 */
        private final class HeaderHolder extends ViewHolder implements View.OnClickListener {

            private final MaterialTextView text;

            private HeaderHolder(@NonNull MaterialTextView text) {
                super(text);
                this.text = text;
                this.text.setGravity(Gravity.CENTER_VERTICAL);
                this.text.setPadding(dp(text.getContext(), Util.isLeanback() ? 18 : 12), 0, dp(text.getContext(), Util.isLeanback() ? 18 : 12), 0);
                this.text.setSingleLine(true);
                this.text.setEllipsize(TextUtils.TruncateAt.END);
                this.text.setTextSize(Util.isLeanback() ? 16 : 14);
                this.text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                this.text.setTextColor(Color.WHITE);
                this.text.setClickable(true);
                this.text.setFocusable(true);
                this.text.setOnClickListener(this);
            }

            private void bind(Header header, int position) {
                text.setText(DanmakuTitle.headerTitle(header.title, header.items.size(), header.expanded));
                text.setBackground(headerBackground(text.getContext(), header.expanded));
            }

            @Override
            public void onClick(View view) {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                toggleHeader(position);
            }
        }

        /** 普通弹幕项 */
        private final class ItemHolder extends ViewHolder implements View.OnClickListener {

            private final MaterialTextView text;

            private ItemHolder(@NonNull MaterialTextView text) {
                super(text);
                this.text = text;
                this.text.setGravity(Gravity.CENTER_VERTICAL);
                this.text.setPadding(dp(text.getContext(), Util.isLeanback() ? 18 : 12), 0, dp(text.getContext(), Util.isLeanback() ? 18 : 12), 0);
                this.text.setSingleLine(true);
                this.text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                this.text.setTextSize(Util.isLeanback() ? 16 : 14);
                this.text.setTextColor(Color.parseColor("#E6FFFFFF"));
                this.text.setClickable(true);
                this.text.setFocusable(true);
                this.text.setOnClickListener(this);
            }

            private void bind(Danmaku item) {
                text.setText(item.getName());
                text.setSelected(item.isSelected());
                text.setBackground(rowBackground(text.getContext(), item.isSelected()));
            }

            @Override
            public void onClick(View view) {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                Object row = rows.get(position);
                if (row instanceof Danmaku) listener.onItemClick((Danmaku) row);
            }
        }

        private static final class Header {
            final String title;
            final List<Danmaku> items;
            boolean expanded;

            Header(String title, List<Danmaku> items, boolean expanded) {
                this.title = title;
                this.items = items;
                this.expanded = expanded;
            }
        }

        private static Drawable rowBackground(Context context, boolean selected) {
            GradientDrawable selectedDrawable = row(Color.parseColor("#2E1A73E8"), Color.parseColor("#801A73E8"), context);
            GradientDrawable focusedDrawable = row(Color.parseColor("#333A7AFE"), Color.WHITE, context);
            GradientDrawable normalDrawable = row(Color.parseColor("#18FFFFFF"), Color.parseColor("#24FFFFFF"), context);
            return new android.graphics.drawable.StateListDrawable() {{
                addState(new int[]{android.R.attr.state_focused}, focusedDrawable);
                addState(new int[]{android.R.attr.state_selected}, selectedDrawable);
                addState(new int[]{}, normalDrawable);
            }};
        }

        private static Drawable headerBackground(Context context, boolean expanded) {
            GradientDrawable expandedDrawable = row(Color.parseColor("#3326282C"), Color.parseColor("#806FA8FF"), context);
            GradientDrawable focusedDrawable = row(Color.parseColor("#333A7AFE"), Color.WHITE, context);
            GradientDrawable normalDrawable = row(Color.parseColor("#2EFFFFFF"), Color.parseColor("#40FFFFFF"), context);
            return new android.graphics.drawable.StateListDrawable() {{
                addState(new int[]{android.R.attr.state_focused}, focusedDrawable);
                addState(new int[]{}, expanded ? expandedDrawable : normalDrawable);
            }};
        }

        private static GradientDrawable row(int color, int stroke, Context context) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(dp(context, 7));
            drawable.setStroke(dp(context, stroke == Color.WHITE ? 2 : 1), stroke);
            return drawable;
        }

        private static int dp(Context context, int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
