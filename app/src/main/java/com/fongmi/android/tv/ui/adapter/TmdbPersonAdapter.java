package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.databinding.AdapterTmdbPersonBinding;
import com.fongmi.android.tv.ui.helper.TmdbCinemaTheme;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class TmdbPersonAdapter extends RecyclerView.Adapter<TmdbPersonAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(TmdbPerson item);
    }

    private final Listener listener;
    private final List<TmdbPerson> items = new ArrayList<>();
    private boolean cinema;
    private boolean light;

    public TmdbPersonAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<TmdbPerson> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    /**
     * 直接重新绑定当前已附着的可见 ViewHolder，不依赖 RecyclerView 的布局遍历。
     * 用于 RecyclerView 嵌套在 NestedScrollView(wrap_content) 中、requestLayout 被祖先的
     * stuck layout 标志吞掉、notifyDataSetChanged 无法触发重绑的场景。用 getLayoutPosition()
     * 而非 getBindingAdapterPosition()：后者在有未派发的适配器更新时返回 NO_POSITION。
     */
    public void rebindAttached(RecyclerView recyclerView) {
        for (int index = 0; index < recyclerView.getChildCount(); index++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index));
            int position = holder.getLayoutPosition();
            if (!(holder instanceof ViewHolder) || position == RecyclerView.NO_POSITION || position >= items.size()) continue;
            onBindViewHolder((ViewHolder) holder, position);
        }
    }

    public void setCinema(boolean cinema) {
        this.cinema = cinema;
        notifyDataSetChanged();
    }

    public void setLight(boolean light) {
        this.light = light;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTmdbPersonBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TmdbPerson item = items.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.subtitle.setText(item.getSubtitle());
        TmdbCinemaTheme.Palette palette = TmdbCinemaTheme.palette(light);
        boolean darkChrome = !light;
        holder.binding.name.setTextColor(cinema ? palette.primary() : darkChrome ? 0xFFFFFFFF : 0xFF12202D);
        holder.binding.subtitle.setTextColor(cinema ? palette.secondary() : darkChrome ? 0x99FFFFFF : 0x9912202D);
        applyCardStyle(holder);
        TmdbCardFocusHelper.bind(holder.binding.getRoot(), cinema ? palette.card() : (light ? 0xFFFFFFFF : 0xFF16202A), cinema ? palette.cardStroke() : (light ? 0x33424B57 : 0x33FFFFFF), 1);
        ImgUtil.load(item.getName(), item.getProfileUrl(), holder.binding.photo);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    private void applyCardStyle(ViewHolder holder) {
        ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
        params.width = dp(holder.itemView, cinema ? 250 : 90);
        holder.binding.getRoot().setLayoutParams(params);
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.setMarginEnd(dp(holder.itemView, cinema ? 18 : 12));
            holder.binding.getRoot().setLayoutParams(marginParams);
        }
        holder.binding.content.setOrientation(cinema ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        holder.binding.content.setGravity(cinema ? android.view.Gravity.CENTER_VERTICAL : 0);
        ViewGroup.LayoutParams photoParams = holder.binding.photo.getLayoutParams();
        photoParams.width = dp(holder.itemView, cinema ? 86 : 90);
        photoParams.height = dp(holder.itemView, cinema ? 86 : 118);
        holder.binding.photo.setLayoutParams(photoParams);
        if (photoParams instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.setMargins(0, 0, 0, 0);
            holder.binding.photo.setLayoutParams(marginParams);
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(cinema ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, cinema ? 1f : 0f);
        textParams.setMarginStart(dp(holder.itemView, cinema ? 14 : 0));
        holder.binding.text.setLayoutParams(textParams);
        holder.binding.text.setPadding(dp(holder.itemView, cinema ? 0 : 8), dp(holder.itemView, cinema ? 0 : 8), dp(holder.itemView, cinema ? 0 : 8), dp(holder.itemView, cinema ? 0 : 8));
        holder.binding.name.setTextSize(cinema ? 17f : 12f);
        holder.binding.subtitle.setTextSize(cinema ? 13f : 10f);
        holder.binding.subtitle.setMaxLines(cinema ? 1 : 2);
        holder.binding.photo.setClipToOutline(true);
        if (cinema) holder.binding.photo.setBackgroundColor(TmdbCinemaTheme.palette(light).imagePlaceholder());
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbPersonBinding binding;

        ViewHolder(@NonNull AdapterTmdbPersonBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
