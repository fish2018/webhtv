package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbVideo;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** Displays validated TMDB YouTube videos as a horizontal rail. */
public class TmdbVideoAdapter extends RecyclerView.Adapter<TmdbVideoAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(TmdbVideo item);
    }

    private final List<TmdbVideo> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<TmdbVideo> videos) {
        List<TmdbVideo> next = videos == null ? List.of() : videos;
        if (sameItems(next)) return;
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    public List<TmdbVideo> getItems() {
        return new ArrayList<>(items);
    }

    public void rebindAttached(RecyclerView recyclerView) {
        for (int index = 0; index < recyclerView.getChildCount(); index++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index));
            int position = holder.getLayoutPosition();
            if (!(holder instanceof ViewHolder) || position == RecyclerView.NO_POSITION || position >= items.size()) continue;
            onBindViewHolder((ViewHolder) holder, position);
        }
    }

    private boolean sameItems(List<TmdbVideo> next) {
        if (items.size() != next.size()) return false;
        for (int i = 0; i < items.size(); i++) {
            TmdbVideo before = items.get(i);
            TmdbVideo after = next.get(i);
            if (before == null || after == null || !before.getIdentity().equals(after.getIdentity()) || before.getScope() != after.getScope()) return false;
        }
        return true;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_tmdb_video, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView card;
        private final ImageView poster;
        private final TextView title;
        private final TextView subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            poster = itemView.findViewById(R.id.poster);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            if (!Util.isLeanback()) {
                itemView.setFocusable(false);
                itemView.setFocusableInTouchMode(false);
            }
        }

        void bind(TmdbVideo item, OnItemClickListener listener) {
            String name = item.getName().isEmpty() ? item.getDisplayType() : item.getName();
            title.setText(name);
            subtitle.setText(item.getDisplayType() + " ? " + item.getScopeLabel());
            ImgUtil.load(name, item.getThumbnailUrl(), poster, true, 300, 169);
            itemView.setOnClickListener(view -> {
                if (listener != null) listener.onItemClick(item);
            });
            itemView.setOnFocusChangeListener((view, focused) -> {
                float scale = focused ? 1.04f : 1.0f;
                view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
                card.setStrokeWidth(ResUtil.dp2px(focused ? 2 : 1));
                card.setStrokeColor(focused ? 0xFFFFD166 : 0x33FFFFFF);
            });
        }
    }
}
