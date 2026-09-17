package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * TMDB 剧照横向滚动适配器。
 */
public class TmdbPhotoAdapter extends RecyclerView.Adapter<TmdbPhotoAdapter.ViewHolder> {

    private final List<String> items = new ArrayList<>();
    private Listener legacyListener;
    private OnItemClickListener listener;
    private boolean legacyMode;
    private boolean light;
    private final boolean poster;

    public interface OnItemClickListener {
        void onItemClick(String url, int position);
    }

    public interface Listener {
        void onItemClick(int position, String url);
    }

    public TmdbPhotoAdapter() {
        this(false);
    }

    public TmdbPhotoAdapter(boolean poster) {
        this.poster = poster;
    }

    public TmdbPhotoAdapter(Listener listener) {
        this(listener, false);
    }

    public TmdbPhotoAdapter(Listener listener, boolean poster) {
        this.poster = poster;
        this.legacyListener = listener;
        this.legacyMode = true;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setLight(boolean light) {
        legacyMode = true;
        if (this.light == light) return;
        this.light = light;
        // 避免大数据集触发 RecyclerView 复用崩溃，改用 notifyDataSetChanged 全量刷新
        if (items.size() > 0) notifyDataSetChanged();
    }

    public void setItems(List<String> photos) {
        List<String> next = photos == null ? List.of() : photos;
        if (items.equals(next)) return;
        items.clear();
        items.addAll(next);
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

    public List<String> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_tmdb_photo, parent, false);
        if (poster) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.width = ResUtil.dp2px(148);
            params.height = ResUtil.dp2px(222);
            view.setLayoutParams(params);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), position, listener, light, poster);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView photo;
        private final MaterialCardView card;

        public ViewHolder(@NonNull android.view.View itemView) {
            super(itemView);
            if (!Util.isLeanback()) {
                itemView.setFocusable(false);
                itemView.setFocusableInTouchMode(false);
            }
            card = (MaterialCardView) itemView;
            photo = itemView.findViewById(R.id.photo);
        }

        void bind(String url, int position, OnItemClickListener listener, boolean light, boolean poster) {
            TmdbCardFocusHelper.bind(card, light ? 0xEEFFFFFF : 0xCC16202A, light ? 0x33647480 : 0x33FFFFFF);
            int label = poster ? R.string.tmdb_posters_label : R.string.tmdb_photos_label;
            ImgUtil.load(photo.getContext().getString(label), url, photo);

            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onItemClick(url, position));
            }
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (!legacyMode) return;
        holder.itemView.setOnClickListener(view -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION || legacyListener == null) return;
            legacyListener.onItemClick(position, items.get(position));
        });
    }
}
