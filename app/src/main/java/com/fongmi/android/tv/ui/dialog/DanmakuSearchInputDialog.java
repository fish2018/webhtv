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
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public final class DanmakuSearchInputDialog extends DialogFragment implements Callback {

    private final ResultAdapter adapter;
    private final Map<String, List<Danmaku>> groups;
    private TextInputEditText input;
    private MaterialButton search;
    private HorizontalScrollView sourceScroll;
    private LinearLayout sourceTabs;
    private FrameLayout resultFrame;
    private CustomRecyclerView recycler;
    private CircularProgressIndicator progress;
    private TextView empty;
    private PlayerManager player;
    private volatile Call activeCall;
    private boolean selected;
    private boolean restoreParent;
    private String selectedSource;
    private String siteKey;
    private String vodId;
    private String rawTitle;
    private String episodeName;

    public static DanmakuSearchInputDialog create() {
        return new DanmakuSearchInputDialog();
    }

    public DanmakuSearchInputDialog() {
        this.adapter = new ResultAdapter(this::onItemClick);
        this.groups = new LinkedHashMap<>();
    }

    public DanmakuSearchInputDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public DanmakuSearchInputDialog restoreParent(boolean restoreParent) {
        this.restoreParent = restoreParent;
        return this;
    }

    public DanmakuSearchInputDialog identity(String siteKey, String vodId, String rawTitle, String episodeName) {
        this.siteKey = clean(siteKey);
        this.vodId = clean(vodId);
        this.rawTitle = clean(rawTitle);
        this.episodeName = clean(episodeName);
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuSearchInputDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable android.os.Bundle savedInstanceState) {
        input = createInput();
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(createContentView());
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(d -> {
            bindEvents();
            if (Util.isLeanback()) search.requestFocus();
            else Util.showKeyboard(input);
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
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(window.getAttributes());
        params.width = Math.max(dp(300), Math.min(metrics.widthPixels - dp(32), dp(560)));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        FragmentActivity activity = getActivity();
        if (selected || !restoreParent || activity == null || activity.isFinishing()) return;
        DanmakuDialog.create().player(player).identity(siteKey, vodId, rawTitle, episodeName).show(activity);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        activeCall = null;
        DanmakuApi.cancel();
    }

    private void onItemClick(Danmaku item) {
        selected = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "search dialog item click selected=%s name=%s url=%s", item.isSelected(), item.getName(), item.getUrl());
        if (!item.isSelected()) rememberManualDanmaku(item);
        player.setDanmaku(item.isSelected() ? Danmaku.empty() : item);
        dismiss();
    }

    private void rememberManualDanmaku(Danmaku item) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId) || item == null || item.isEmpty()) return;
        DanmakuMatchCache cache = Setting.getDanmakuMatchCache();
        MediaTitleLearningExample example = cache.put(siteKey, vodId, first(episodeName, getEpisode()), getKeyword(), first(rawTitle, getTitle()), item);
        Setting.putDanmakuMatchCache(cache);
        if (example != null) MediaTitleLearningStore.load().put(example);
    }

    private TextInputEditText createInput() {
        TextInputEditText edit = new TextInputEditText(requireContext());
        edit.setSingleLine(true);
        edit.setMaxLines(1);
        edit.setBackground(null);
        edit.setPadding(0, 0, 0, 0);
        edit.setGravity(Gravity.CENTER_VERTICAL);
        edit.setTextColor(Color.parseColor("#202124"));
        edit.setHintTextColor(Color.parseColor("#6F7782"));
        edit.setTextSize(18);
        edit.setHint(R.string.search_keyword);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        edit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        CharSequence title = player == null || player.getMetadata() == null ? "" : player.getMetadata().title;
        edit.setText(title == null ? "" : title);
        if (edit.getText() != null) edit.setSelection(edit.getText().length());
        return edit;
    }

    private LinearLayout createContentView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(16));
        root.setBackground(round(Color.WHITE, 24, Color.TRANSPARENT));
        root.addView(createHeader(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));
        root.addView(createSearchRow(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        sourceTabs = new LinearLayout(requireContext());
        sourceTabs.setOrientation(LinearLayout.HORIZONTAL);
        sourceTabs.setGravity(Gravity.CENTER_VERTICAL);

        sourceScroll = new HorizontalScrollView(requireContext());
        sourceScroll.setHorizontalScrollBarEnabled(false);
        sourceScroll.setOverScrollMode(HorizontalScrollView.OVER_SCROLL_NEVER);
        sourceScroll.setVisibility(GONE);
        sourceScroll.setClipChildren(false);
        sourceScroll.setClipToPadding(false);
        sourceScroll.addView(sourceTabs, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        tabParams.setMargins(0, dp(10), 0, 0);
        root.addView(sourceScroll, tabParams);

        resultFrame = createResultFrame();
        resultFrame.setVisibility(GONE);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        resultParams.setMargins(0, dp(8), 0, 0);
        root.addView(resultFrame, resultParams);
        return root;
    }

    private LinearLayout createHeader() {
        MaterialTextView title = new MaterialTextView(requireContext());
        title.setText(R.string.play_search);
        title.setTextColor(Color.parseColor("#202124"));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);

        MaterialButton close = new MaterialButton(requireContext());
        close.setText("X");
        close.setTextSize(15);
        close.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setMinWidth(0);
        close.setMinHeight(dp(34));
        close.setMinimumHeight(dp(34));
        close.setPadding(0, 0, 0, 0);
        close.setCornerRadius(dp(17));
        close.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        close.setTextColor(Color.parseColor("#5F6368"));
        close.setOnClickListener(v -> dismiss());

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        row.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
        return row;
    }

    private LinearLayout createSearchRow() {
        input.setId(View.generateViewId());
        LinearLayout inputBox = new LinearLayout(requireContext());
        inputBox.setOrientation(LinearLayout.HORIZONTAL);
        inputBox.setGravity(Gravity.CENTER_VERTICAL);
        inputBox.setPadding(dp(14), 0, dp(14), 0);
        inputBox.setBackground(round(Color.parseColor("#F8FAFD"), 12, Color.parseColor("#DADCE0")));
        inputBox.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        search = actionButton(getString(R.string.play_search), true);
        search.setId(View.generateViewId());
        input.setNextFocusRightId(search.getId());
        search.setNextFocusLeftId(input.getId());
        search.setIconResource(R.drawable.ic_action_search);
        search.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        search.setIconPadding(dp(4));
        search.setIconTint(ColorStateList.valueOf(Color.WHITE));
        search.setMinHeight(dp(56));
        search.setMinimumHeight(dp(56));
        search.setCornerRadius(dp(12));
        styleSearchButton(search);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(56), 1);
        inputParams.setMargins(0, 0, dp(8), 0);
        row.addView(inputBox, inputParams);
        row.addView(search, new LinearLayout.LayoutParams(dp(96), dp(56)));
        return row;
    }

    private FrameLayout createResultFrame() {
        FrameLayout frame = new FrameLayout(requireContext());
        frame.setMinimumHeight(dp(72));

        recycler = new CustomRecyclerView(requireContext());
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setItemAnimator(null);
        recycler.setHasFixedSize(false);
        recycler.setMaxHeight(dp(304));
        recycler.setAdapter(adapter);
        recycler.setVisibility(GONE);
        recycler.setId(View.generateViewId());
        recycler.setClipChildren(false);
        recycler.setClipToPadding(false);
        input.setNextFocusDownId(recycler.getId());
        search.setNextFocusDownId(recycler.getId());
        frame.addView(recycler, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        empty = new TextView(requireContext());
        empty.setGravity(Gravity.CENTER);
        empty.setTextColor(Color.parseColor("#5F6368"));
        empty.setTextSize(14);
        empty.setVisibility(GONE);
        empty.setMinHeight(dp(72));
        frame.addView(empty, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(72), Gravity.CENTER));

        progress = new CircularProgressIndicator(requireContext());
        progress.setIndeterminate(true);
        progress.setIndicatorColor(Color.parseColor("#0B57D0"));
        progress.setIndicatorSize(dp(32));
        progress.setTrackThickness(dp(2));
        progress.setVisibility(GONE);
        frame.addView(progress, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return frame;
    }

    private void bindEvents() {
        input.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            search();
            return true;
        });
        input.setOnKeyListener((view, keyCode, event) -> KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && focusResult());
        search.setOnKeyListener((view, keyCode, event) -> KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && focusResult());
        search.setOnClickListener(v -> search());
    }

    private void search() {
        String keyword = input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            input.setError(getString(R.string.error_empty));
            return;
        }
        input.setError(null);
        showProgress();
        activeCall = null;
        Util.hideKeyboard(input);
        Call call = DanmakuApi.newCall(keyword, getEpisode());
        if (call == null) {
            showError(getString(R.string.danmaku_api_invalid));
            return;
        }
        activeCall = call;
        call.enqueue(this);
    }

    private String getEpisode() {
        CharSequence episode = player == null || player.getMetadata() == null ? "" : player.getMetadata().artist;
        return episode == null ? "" : episode.toString().trim();
    }

    private String getTitle() {
        CharSequence title = player == null || player.getMetadata() == null ? "" : player.getMetadata().title;
        return title == null ? "" : title.toString().trim();
    }

    private String getKeyword() {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String first(String first, String second) {
        return !TextUtils.isEmpty(first) ? first : clean(second);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private void showProgress() {
        selectedSource = null;
        groups.clear();
        adapter.clear();
        sourceTabs.removeAllViews();
        sourceScroll.setVisibility(GONE);
        resultFrame.setVisibility(VISIBLE);
        recycler.setVisibility(GONE);
        empty.setVisibility(GONE);
        progress.setVisibility(VISIBLE);
        search.setEnabled(false);
    }

    private void hideProgress() {
        progress.setVisibility(GONE);
        search.setEnabled(true);
    }

    private void showError(String message) {
        if (TextUtils.isEmpty(message)) message = getString(R.string.error_empty);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "search dialog failed error=%s", message);
        hideProgress();
        sourceScroll.setVisibility(GONE);
        resultFrame.setVisibility(VISIBLE);
        recycler.setVisibility(GONE);
        empty.setText(message);
        empty.setVisibility(VISIBLE);
        Notify.show(message);
    }

    private void onSuccess(List<Danmaku> items) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "search dialog success count=%d", items.size());
        hideProgress();
        syncCurrentState(items);
        groups.clear();
        for (Danmaku item : items) {
            String source = item.getSourceName();
            if (!groups.containsKey(source)) groups.put(source, new ArrayList<>());
            groups.get(source).add(item);
        }
        if (groups.isEmpty()) {
            showError(getString(R.string.error_empty));
            return;
        }
        selectedSource = chooseInitialSource();
        renderSourceTabs();
        showSourceItems();
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

    private String chooseInitialSource() {
        for (Map.Entry<String, List<Danmaku>> entry : groups.entrySet()) {
            for (Danmaku item : entry.getValue()) if (item.isSelected()) return entry.getKey();
        }
        return groups.keySet().iterator().next();
    }

    private void renderSourceTabs() {
        sourceTabs.removeAllViews();
        sourceScroll.setVisibility(groups.isEmpty() ? GONE : VISIBLE);
        for (String source : groups.keySet()) {
            List<Danmaku> items = groups.get(source);
            boolean active = TextUtils.equals(source, selectedSource);
            MaterialButton tab = actionButton(source + " " + (items == null ? 0 : items.size()), active);
            tab.setSingleLine(true);
            tab.setEllipsize(TextUtils.TruncateAt.END);
            tab.setSelected(active);
            tab.setOnClickListener(v -> {
                selectedSource = source;
                renderSourceTabs();
                showSourceItems();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
            params.setMargins(0, 0, dp(8), 0);
            sourceTabs.addView(tab, params);
        }
    }

    private void showSourceItems() {
        hideProgress();
        adapter.clear();
        List<Danmaku> items = groups.get(selectedSource);
        if (items == null || items.isEmpty()) {
            recycler.setVisibility(GONE);
            empty.setText(R.string.error_empty);
            empty.setVisibility(VISIBLE);
            return;
        }
        empty.setVisibility(GONE);
        recycler.setVisibility(VISIBLE);
        adapter.addAll(items, input.getText() == null ? "" : input.getText().toString().trim());
        focusResult();
    }

    private boolean focusResult() {
        if (recycler == null || recycler.getVisibility() != VISIBLE || adapter.getItemCount() == 0) return false;
        recycler.scrollToPosition(adapter.getSelected());
        recycler.requestFocus();
        return true;
    }

    private MaterialButton actionButton(String text, boolean primary) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(32));
        button.setMinimumHeight(dp(32));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setCornerRadius(dp(8));
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        if (primary) {
            button.setBackgroundTintList(buttonBackground("#C7DBFF", "#DCEAFF", "#EDF4FF", "#F3F6FA"));
            button.setTextColor(buttonText("#0B57D0", "#174EA6", "#8AA8D8"));
            button.setStrokeColor(buttonStroke("#0B57D0", "#AECBFA", "#E8EAED"));
            button.setStrokeWidth(dp(2));
        } else {
            button.setBackgroundTintList(buttonBackground("#E4EEFF", "#F1F4F8", "#FFFFFF", "#F3F6FA"));
            button.setTextColor(buttonText("#174EA6", "#202124", "#9AA0A6"));
            button.setStrokeColor(buttonStroke("#0B57D0", "#DADCE0", "#E8EAED"));
            button.setStrokeWidth(dp(1));
        }
        return button;
    }

    private void styleSearchButton(MaterialButton button) {
        button.setBackgroundTintList(buttonBackground("#0B57D0", "#174EA6", "#174EA6", "#E8EAED"));
        button.setTextColor(buttonText("#FFFFFF", "#FFFFFF", "#9AA0A6"));
        button.setStrokeColor(buttonStroke("#FFFFFF", "#174EA6", "#E8EAED"));
        button.setStrokeWidth(dp(2));
    }

    private ColorStateList buttonBackground(String focused, String selected, String normal, String disabled) {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{android.R.attr.state_focused}, new int[]{android.R.attr.state_selected}, new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{Color.parseColor(focused), Color.parseColor(focused), Color.parseColor(selected), Color.parseColor(disabled), Color.parseColor(normal)});
    }

    private ColorStateList buttonText(String focused, String normal, String disabled) {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{android.R.attr.state_selected}, new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{Color.parseColor(focused), Color.parseColor(focused), Color.parseColor(disabled), Color.parseColor(normal)});
    }

    private ColorStateList buttonStroke(String focused, String normal, String disabled) {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{Color.parseColor(focused), Color.parseColor(disabled), Color.parseColor(normal)});
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
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
            App.post(() -> showError(e.getMessage()));
        }
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        if (call != activeCall) return;
        App.post(() -> showError(e.getMessage()));
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
            notifyItemRangeRemoved(0, size);
        }

        /**
         * 按剧名二次分组装配列表：同一剧名≥2条折叠成一个 Header（默认折叠），
         * 单条或解析不出剧名的直接作为普通项平铺。含已选中弹幕的分组默认展开。
         * 分组头按关键词相似度排序，子项按集数排序。
         */
        private void addAll(List<Danmaku> values, String keyword) {
            if (values == null) return;
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
                    if (hasSelected) rows.addAll(items);
                }
            }
            rows.addAll(ungrouped);
            if (!rows.isEmpty()) notifyItemRangeInserted(0, rows.size());
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof Header ? TYPE_HEADER : TYPE_ITEM;
        }

        private int getSelected() {
            for (int i = 0; i < rows.size(); i++) {
                Object row = rows.get(i);
                if (row instanceof Danmaku && ((Danmaku) row).isSelected()) return i;
            }
            return 0;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialTextView button = new MaterialTextView(parent.getContext());
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(parent.getContext(), 42));
            params.setMargins(0, 0, 0, dp(parent.getContext(), 8));
            button.setLayoutParams(params);
            return viewType == TYPE_HEADER ? new HeaderHolder(button) : new ItemHolder(button);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (holder instanceof HeaderHolder) ((HeaderHolder) holder).bind((Header) row);
            else ((ItemHolder) holder).bind((Danmaku) row);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

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
            ViewHolder(@NonNull MaterialTextView button) {
                super(button);
            }
        }

        /** 剧名折叠分组头 */
        private final class HeaderHolder extends ViewHolder implements View.OnClickListener {

            private final MaterialTextView button;

            private HeaderHolder(@NonNull MaterialTextView button) {
                super(button);
                this.button = button;
                this.button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                this.button.setMinHeight(dp(button.getContext(), 42));
                this.button.setMinimumHeight(dp(button.getContext(), 42));
                this.button.setPadding(dp(button.getContext(), 12), 0, dp(button.getContext(), 12), 0);
                this.button.setSingleLine(true);
                this.button.setEllipsize(TextUtils.TruncateAt.END);
                this.button.setTextSize(14);
                this.button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                this.button.setClickable(true);
                this.button.setFocusable(true);
                this.button.setOnClickListener(this);
            }

            private void bind(Header header) {
                button.setText(DanmakuTitle.headerTitle(header.title, header.items.size(), header.expanded));
                button.setTextColor(Color.parseColor("#174EA6"));
                button.setBackground(headerBackground(button.getContext()));
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

            private final MaterialTextView button;

            private ItemHolder(@NonNull MaterialTextView button) {
                super(button);
                this.button = button;
                this.button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                this.button.setMinHeight(dp(button.getContext(), 42));
                this.button.setMinimumHeight(dp(button.getContext(), 42));
                this.button.setPadding(dp(button.getContext(), 12), 0, dp(button.getContext(), 12), 0);
                this.button.setSingleLine(true);
                this.button.setEllipsize(TextUtils.TruncateAt.END);
                this.button.setTextSize(14);
                this.button.setClickable(true);
                this.button.setFocusable(true);
                this.button.setFocusableInTouchMode(true);
                this.button.setOnClickListener(this);
            }

            private void bind(Danmaku item) {
                button.setText(item.isSelected() ? "\u2713 " + item.getName() : item.getName());
                button.setSelected(item.isSelected());
                button.setTextColor(rowTextColor(item.isSelected()));
                button.setTypeface(Typeface.DEFAULT, item.isSelected() ? Typeface.BOLD : Typeface.NORMAL);
                button.setBackground(rowBackground(button.getContext(), item.isSelected()));
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
            StateListDrawable drawable = new StateListDrawable();
            drawable.addState(new int[]{android.R.attr.state_focused}, round(context, Color.parseColor("#DCEAFF"), Color.parseColor("#0B57D0"), 2));
            if (selected) drawable.addState(new int[]{android.R.attr.state_selected}, round(context, Color.parseColor("#EAF2FF"), Color.parseColor("#0B57D0"), 2));
            drawable.addState(new int[]{}, round(context, selected ? Color.parseColor("#EAF2FF") : Color.parseColor("#F8FAFD"), selected ? Color.parseColor("#AECBFA") : Color.parseColor("#E3E7EE"), 1));
            return drawable;
        }

        private static ColorStateList rowTextColor(boolean selected) {
            return new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_focused}, new int[]{android.R.attr.state_selected}, new int[]{}},
                    new int[]{Color.parseColor("#0B57D0"), Color.parseColor("#174EA6"), selected ? Color.parseColor("#174EA6") : Color.parseColor("#202124")});
        }

        private static GradientDrawable round(Context context, int color, int stroke, int strokeWidth) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(dp(context, 8));
            drawable.setStroke(dp(context, strokeWidth), stroke);
            return drawable;
        }

        private static Drawable headerBackground(Context context) {
            GradientDrawable content = new GradientDrawable();
            content.setColor(Color.parseColor("#EAF2FF"));
            content.setCornerRadius(dp(context, 8));
            content.setStroke(dp(context, 1), Color.parseColor("#AECBFA"));
            return new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#1A0B57D0")), content, null);
        }

        private static int dp(Context context, int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
