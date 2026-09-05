package com.fongmi.android.tv.ui.adapter;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InlineEpisodeAdapter extends RecyclerView.Adapter<InlineEpisodeAdapter.ViewHolder> {

    private static final int COLOR_NORMAL = 0x99263442;
    private static final int COLOR_ACTIVE = 0xCC2AA46B;
    private static final int COLOR_FOCUS = 0xFF2196F3;
    private static final int COLOR_FOCUS_BG = 0xFFEAF2F8;
    private static final int COLOR_TEXT = 0xFFEAF2F8;
    private static final int COLOR_FOCUS_TEXT = 0xFF0B5CAD;

    public interface Listener {
        void onItemClick(Episode item);

        boolean onItemLongClick(MaterialButton button, Episode item);
    }

    private final Listener listener;
    private final List<Episode> items = new ArrayList<>();
    private final Map<Episode, String> titles = new HashMap<>();
    private Episode selected;
    private boolean light;

    public InlineEpisodeAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Episode> values, Episode selected) {
        setItems(values, selected, Map.of());
    }

    public void setItems(List<Episode> values, Episode selected, Map<Episode, String> titles) {
        items.clear();
        if (values != null) items.addAll(values);
        this.titles.clear();
        if (titles != null) this.titles.putAll(titles);
        this.selected = selected;
        notifyDataSetChanged();
    }

    public void setLight(boolean light) {
        if (this.light == light) return;
        this.light = light;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MaterialButton button = new MaterialButton(parent.getContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        button.setMarqueeRepeatLimit(-1);
        button.setAllCaps(false);
        button.setTextSize(16f);
        button.setPadding(ResUtil.dp2px(10), 0, ResUtil.dp2px(10), 0);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(42));
        params.setMargins(ResUtil.dp2px(8), ResUtil.dp2px(5), ResUtil.dp2px(8), ResUtil.dp2px(5));
        button.setLayoutParams(params);
        return new ViewHolder(button);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Episode item = items.get(position);
        boolean active = item.equals(selected);
        holder.button.setText(getTitle(item));
        holder.button.setOnFocusChangeListener(null);
        applyState(holder.button, active, holder.button.hasFocus());
        holder.button.setOnFocusChangeListener((view, focused) -> applyState(holder.button, active, focused));
        holder.button.setOnClickListener(view -> listener.onItemClick(item));
        holder.button.setOnLongClickListener(view -> listener.onItemLongClick(holder.button, item));
    }

    private void applyState(MaterialButton button, boolean active, boolean focused) {
        button.setSelected(active || focused);
        int normalText = light ? 0xFF1E2A36 : COLOR_TEXT;
        int activeText = light ? 0xFF0F1D2A : 0xFFFFFFFF;
        int focusText = COLOR_FOCUS_TEXT;
        int normalBg = light ? 0xFFF1F5F9 : COLOR_NORMAL;
        int activeBg = light ? 0xFFDCF5E6 : COLOR_ACTIVE;
        int focusBg = light ? 0xFFD8E7FF : COLOR_FOCUS_BG;
        int normalStroke = light ? 0x33556778 : 0x44FFFFFF;
        int activeStroke = light ? 0xFF1D8F5A : 0xFF2AA46B;
        button.setTextColor(active ? activeText : focused ? focusText : normalText);
        button.setBackgroundTintList(ColorStateList.valueOf(active ? activeBg : focused ? focusBg : normalBg));
        button.setStrokeColor(ColorStateList.valueOf(active ? activeStroke : focused ? COLOR_FOCUS : normalStroke));
        button.setStrokeWidth(ResUtil.dp2px(active || focused ? 2 : 1));
    }

    private String getTitle(Episode item) {
        String title = titles.get(item);
        return TextUtils.isEmpty(title) ? EpisodeAdapter.getTitle(item) : title;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final MaterialButton button;

        ViewHolder(@NonNull MaterialButton button) {
            super(button);
            this.button = button;
        }
    }
}
