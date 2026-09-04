package com.fongmi.android.tv.ui.custom;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LutQuickPanel extends FrameLayout {

    private static final long FAVORITE_DOUBLE_CLICK_MS = 450;
    private static final int PANEL_WIDTH_DP = 252;

    private MaterialTextView all;
    private MaterialTextView delay;
    private MaterialTextView favorite;
    private MaterialTextView empty;
    private RecyclerView recycler;
    private final PanelAdapter adapter;
    private final View panel;
    private PlayerManager player;
    private PlayerView playerView;
    private Runnable refresh;
    private ImportCallback importCallback;
    private int selectSeq;
    private boolean favoriteOnly;
    private String lastClickId;
    private long lastClickTime;

    public LutQuickPanel(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setVisibility(GONE);
        setClipChildren(true);
        panel = createPanel();
        adapter = new PanelAdapter();
        recycler.setAdapter(adapter);
        addView(panel);
    }

    public void toggle(PlayerManager player, PlayerView playerView, Runnable refresh, ImportCallback importCallback) {
        bind(player, playerView, refresh, importCallback);
        if (getVisibility() == VISIBLE) hide();
        else show();
    }

    public void bind(PlayerManager player, PlayerView playerView, Runnable refresh, ImportCallback importCallback) {
        this.player = player;
        this.playerView = playerView;
        this.refresh = refresh;
        this.importCallback = importCallback;
        if (getVisibility() == VISIBLE) refreshList();
    }

    public void refreshList() {
        refreshList(false);
    }

    private void refreshList(boolean rescan) {
        if (adapter == null) return;
        renderList(LutStore.getCachedPresets());
        if (rescan || !LutStore.hasCache()) LutStore.refreshPresetsAsync(this::renderList);
    }

    private void renderList(List<LutPreset> presets) {
        if (adapter == null) return;
        List<Entry> items = new ArrayList<>();
        Set<String> favorites = LutSetting.favoriteIds();
        if (!favoriteOnly) items.add(Entry.original());
        for (LutPreset preset : presets) {
            if (favoriteOnly && !favorites.contains(preset.getId())) continue;
            items.add(Entry.preset(preset));
        }
        empty.setText(favoriteOnly ? R.string.lut_empty_favorites : R.string.lut_empty_presets);
        empty.setVisibility(items.isEmpty() || (!favoriteOnly && items.size() <= 1) ? VISIBLE : GONE);
        adapter.setItems(items);
        if (getVisibility() == VISIBLE && shouldFocusSelectedEntry()) recycler.post(this::focusSelectedEntry);
        delay.setText(ResUtil.getString(R.string.lut_preview_delay_value, LutSetting.getPreviewSeconds()));
        updateAllButton();
        updateFavoriteButton();
    }

    public void selectImported(LutPreset preset, PlayerManager player, PlayerView playerView, Runnable refresh) {
        favoriteOnly = false;
        bind(player, playerView, refresh, importCallback);
        setVisibility(VISIBLE);
        refreshList(true);
        select(preset);
    }

    public boolean hideIfVisible() {
        if (getVisibility() != VISIBLE) return false;
        hide();
        return true;
    }

    public boolean focusSelectedEntry() {
        int selected = adapter.getSelectedPosition();
        return focusEntry(selected == RecyclerView.NO_POSITION ? 0 : selected);
    }

    private boolean shouldFocusSelectedEntry() {
        View focus = findFocus();
        return focus == null || focus == recycler || isChildOf(recycler, focus);
    }

    private void show() {
        setVisibility(VISIBLE);
        refreshList(true);
        updatePanelWidth();
        panel.post(() -> {
            panel.setTranslationX(panel.getWidth());
            panel.animate().translationX(0).setDuration(180).start();
            focusSelectedEntry();
        });
    }

    private void hide() {
        panel.animate().translationX(panel.getWidth()).setDuration(160).withEndAction(() -> {
            setVisibility(GONE);
            panel.setTranslationX(0);
        }).start();
    }

    private void select(LutPreset preset) {
        if (player == null) return;
        int seq = ++selectSeq;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-ui", "quick select preset=%s enabledBefore=%s current=%s", preset == null ? "original" : preset.getId(), LutSetting.isEnabled(), LutSetting.getPresetId());
        if (seq != selectSeq) return;
        if (!player.selectLut(preset, preset != null)) return;
        notifyChanged();
    }

    private void notifyChanged() {
        if (refresh != null) refresh.run();
        adapter.notifyDataSetChanged();
        recycler.post(this::focusSelectedEntry);
    }

    private void toggleFavoriteOnly() {
        favoriteOnly = !favoriteOnly;
        resetClickTracking();
        refreshList();
        recycler.post(this::focusSelectedEntry);
    }

    private void showAll() {
        favoriteOnly = false;
        resetClickTracking();
        refreshList();
        recycler.post(this::focusSelectedEntry);
    }

    private void onEntryClick(Entry entry) {
        if (entry.preset != null && isFavoriteDoubleClick(entry.preset)) {
            toggleFavorite(entry.preset);
            return;
        }
        select(entry.preset);
    }

    private boolean isFavoriteDoubleClick(LutPreset preset) {
        long now = SystemClock.uptimeMillis();
        String id = preset.getId();
        boolean doubleClick = id.equals(lastClickId) && now - lastClickTime <= FAVORITE_DOUBLE_CLICK_MS;
        lastClickId = id;
        lastClickTime = now;
        return doubleClick;
    }

    private void toggleFavorite(LutPreset preset) {
        boolean enabled = LutSetting.toggleFavorite(preset);
        LutStore.resortCache();
        Notify.show(enabled ? R.string.lut_favorited : R.string.lut_unfavorited);
        resetClickTracking();
        refreshList();
        if (refresh != null) refresh.run();
    }

    private void resetClickTracking() {
        lastClickId = null;
        lastClickTime = 0;
    }

    private void updateAllButton() {
        if (all == null) return;
        applyBg(all, !favoriteOnly, all.hasFocus());
    }

    private void updateFavoriteButton() {
        if (favorite == null) return;
        applyBg(favorite, favoriteOnly, favorite.hasFocus());
    }

    private View createPanel() {
        FrameLayout container = new FrameLayout(getContext());
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(dp(PANEL_WIDTH_DP), LayoutParams.MATCH_PARENT, Gravity.END);
        container.setLayoutParams(containerParams);
        container.setBackgroundColor(0xE6101118);
        container.setPadding(dp(12), dp(12), dp(12), dp(12));
        androidx.appcompat.widget.LinearLayoutCompat column = new androidx.appcompat.widget.LinearLayoutCompat(getContext());
        column.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.VERTICAL);
        container.addView(column, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        androidx.appcompat.widget.LinearLayoutCompat header = new androidx.appcompat.widget.LinearLayoutCompat(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.HORIZONTAL);
        column.addView(header, new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        MaterialTextView title = text(R.string.player_lut, 16, true);
        header.addView(title, new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        MaterialTextView reset = chip();
        reset.setText(R.string.lut_reset);
        reset.setOnClickListener(view -> select(null));
        header.addView(reset);
        MaterialTextView close = chip();
        close.setText(R.string.lut_close);
        close.setOnClickListener(view -> hide());
        header.addView(close);

        androidx.appcompat.widget.LinearLayoutCompat tools = new androidx.appcompat.widget.LinearLayoutCompat(getContext());
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.HORIZONTAL);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams toolsParams = new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        toolsParams.setMargins(0, dp(10), 0, 0);
        column.addView(tools, toolsParams);

        delay = chip();
        delay.setOnClickListener(view -> cycleDelay());
        tools.addView(delay);

        all = chip();
        all.setText(R.string.lut_all);
        all.setOnClickListener(view -> showAll());
        all.setOnFocusChangeListener((v, focused) -> applyBg((MaterialTextView) v, !favoriteOnly, focused));
        tools.addView(all);

        favorite = chip();
        favorite.setText(R.string.lut_favorite);
        favorite.setOnClickListener(view -> toggleFavoriteOnly());
        favorite.setOnFocusChangeListener((v, focused) -> applyBg((MaterialTextView) v, favoriteOnly, focused));
        tools.addView(favorite);

        MaterialTextView importView = chip();
        importView.setText(R.string.lut_local);
        importView.setOnClickListener(view -> {
            if (importCallback != null) importCallback.onImportLut();
        });
        tools.addView(importView);

        MaterialTextView dirView = chip();
        dirView.setText(R.string.lut_directory);
        dirView.setOnClickListener(view -> {
            if (importCallback != null) importCallback.onSelectLutDir();
        });
        tools.addView(dirView);

        empty = text(R.string.lut_empty_presets, 14, false);
        empty.setGravity(Gravity.CENTER);
        column.addView(empty, new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));

        recycler = new RecyclerView(getContext());
        recycler.setOverScrollMode(OVER_SCROLL_NEVER);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        column.addView(recycler, new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        return container;
    }

    private void updatePanelWidth() {
        ViewGroup.LayoutParams params = panel.getLayoutParams();
        if (params == null) return;
        params.width = dp(PANEL_WIDTH_DP);
        panel.setLayoutParams(params);
    }

    private boolean focusEntry(int position) {
        if (recycler == null || recycler.getVisibility() != View.VISIBLE || position < 0 || position >= adapter.getItemCount()) return false;
        recycler.scrollToPosition(position);
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        if (holder != null && holder.itemView.requestFocus()) return true;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            if (recycler.getChildAdapterPosition(child) == position && child.requestFocus()) return true;
        }
        recycler.post(() -> {
            RecyclerView.ViewHolder next = recycler.findViewHolderForAdapterPosition(position);
            if (next != null) {
                next.itemView.requestFocus();
                return;
            }
            for (int i = 0; i < recycler.getChildCount(); i++) {
                View child = recycler.getChildAt(i);
                if (recycler.getChildAdapterPosition(child) == position) {
                    child.requestFocus();
                    return;
                }
            }
        });
        return true;
    }

    private boolean isChildOf(ViewGroup parent, View child) {
        for (View view = child; view != null; ) {
            if (view == parent) return true;
            if (!(view.getParent() instanceof View next)) return false;
            view = next;
        }
        return false;
    }

    private void cycleDelay() {
        int current = LutSetting.getPreviewSeconds();
        int next = current < 2 ? 2 : current < 3 ? 3 : current < 5 ? 5 : current < 8 ? 8 : 1;
        LutSetting.putPreviewSeconds(next);
        delay.setText(ResUtil.getString(R.string.lut_preview_delay_value, next));
    }

    private MaterialTextView chip() {
        MaterialTextView view = text(0, 13, false);
        view.setFocusable(true);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.WHITE);
        view.setPadding(dp(6), dp(6), dp(6), dp(6));
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams params = new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(5));
        view.setLayoutParams(params);
        applyBg(view, false, false);
        view.setOnFocusChangeListener((v, focused) -> applyBg((MaterialTextView) v, false, focused));
        return view;
    }

    private MaterialTextView text(int resId, int sp, boolean bold) {
        MaterialTextView view = new MaterialTextView(getContext());
        if (resId != 0) view.setText(resId);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sp);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private void applyBg(MaterialTextView view, boolean selected, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? 0xFF2F80ED : focused ? 0x55FFFFFF : 0x22FFFFFF);
        drawable.setStroke(dp(1), selected || focused ? 0xFFFFFFFF : 0x33FFFFFF);
        drawable.setCornerRadius(dp(6));
        view.setBackground(drawable);
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    public interface ImportCallback {
        void onImportLut();

        void onSelectLutDir();
    }

    private static class Entry {
        private final LutPreset preset;

        private Entry(LutPreset preset) {
            this.preset = preset;
        }

        static Entry original() {
            return new Entry(null);
        }

        static Entry preset(LutPreset preset) {
            return new Entry(preset);
        }

        boolean isOriginal() {
            return preset == null;
        }

        String getText() {
            return preset == null ? ResUtil.getString(R.string.lut_original) : preset.getName();
        }

        boolean isSelected() {
            return preset == null ? !LutSetting.isEnabled() : LutSetting.isEnabled() && preset.getId().equals(LutSetting.getPresetId());
        }
    }

    private class PanelAdapter extends RecyclerView.Adapter<PanelAdapter.ViewHolder> {
        private final List<Entry> items = new ArrayList<>();

        void setItems(List<Entry> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialTextView view = text(0, 14, false);
            view.setFocusable(true);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setSingleLine(true);
            view.setPadding(dp(12), 0, dp(12), 0);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            params.setMargins(0, dp(8), 0, 0);
            view.setLayoutParams(params);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Entry entry = items.get(position);
            holder.text.setText(entry.getText());
            applyBg(holder.text, entry.isSelected(), holder.text.hasFocus());
            holder.text.setOnFocusChangeListener((view, focused) -> applyBg((MaterialTextView) view, entry.isSelected(), focused));
            holder.text.setOnClickListener(view -> onEntryClick(entry));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        int getSelectedPosition() {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).isSelected()) return i;
            }
            return RecyclerView.NO_POSITION;
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final MaterialTextView text;

            private ViewHolder(@NonNull MaterialTextView itemView) {
                super(itemView);
                this.text = itemView;
            }
        }
    }
}
