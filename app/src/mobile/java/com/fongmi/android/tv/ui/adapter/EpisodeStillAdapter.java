package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.List;

public class EpisodeStillAdapter extends RecyclerView.Adapter<EpisodeStillAdapter.ViewHolder> {

    public interface OnClickListener {
        void onItemClick(String url, int position);
    }

    private final List<String> photos;
    private final OnClickListener listener;

    public EpisodeStillAdapter(List<String> photos, OnClickListener listener) {
        this.photos = photos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_tmdb_photo, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String url = photos.get(position);
        Glide.with(holder.photo.getContext())
                .load(tmdbImageUrl(url, "w780"))
                .placeholder(R.color.black)
                .error(R.color.black)
                .centerCrop()
                .override(ResUtil.dp2px(220), ResUtil.dp2px(124))
                .into(holder.photo);
        holder.itemView.setOnClickListener(view -> {
            if (listener != null) listener.onItemClick(url, position);
        });
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    private static String tmdbImageUrl(String url, String size) {
        if (url == null || url.isEmpty()) return "";
        String result = url.replaceFirst("(/t/p/)([^/]+)(/)", "$1" + size + "$3");
        return result.equals(url) ? url.replaceFirst("/(w\\d+|h\\d+|original)/", "/" + size + "/") : result;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView photo;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemView.setFocusable(false);
            itemView.setFocusableInTouchMode(false);
            this.photo = itemView.findViewById(R.id.photo);
        }
    }
}
