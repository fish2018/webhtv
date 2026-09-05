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
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * 剧集图片适配器 - 显示本集所有剧照
 */
public class EpisodePhotoAdapter extends RecyclerView.Adapter<EpisodePhotoAdapter.ViewHolder> {

    private final List<String> mPhotos;
    private final OnClickListener mListener;
    private final int photoWidth = 220;  // dp
    private final int photoHeight = 124; // dp (16:9)
    private boolean light;

    public interface OnClickListener {
        void onItemClick(String url, int position);
    }

    public EpisodePhotoAdapter(List<String> photos) {
        this(photos, null);
    }

    public EpisodePhotoAdapter(List<String> photos, OnClickListener listener) {
        this.mPhotos = photos;
        this.mListener = listener;
    }

    public void setLight(boolean light) {
        this.light = light;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_tmdb_photo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String photoUrl = mPhotos.get(position);
        holder.bindFocus(light);
        Glide.with(holder.imageView.getContext())
                .load(tmdbImageUrl(photoUrl, "w780"))
                .placeholder(R.color.black)
                .error(R.color.black)
                .centerCrop()
                .override(ResUtil.dp2px(photoWidth), ResUtil.dp2px(photoHeight))
                .into(holder.imageView);
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) mListener.onItemClick(photoUrl, position);
        });
    }

    @Override
    public int getItemCount() {
        return mPhotos.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView card;
        private final ImageView imageView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            imageView = itemView.findViewById(R.id.photo);
        }

        private void bindFocus(boolean light) {
            TmdbCardFocusHelper.bind(card, light ? 0xEEFFFFFF : 0xCC16202A, light ? 0x33647480 : 0x33FFFFFF);
        }
    }

    private static String tmdbImageUrl(String url, String size) {
        if (url == null || url.isEmpty()) return "";
        String result = url.replaceFirst("(/t/p/)([^/]+)(/)", "$1" + size + "$3");
        return result.equals(url) ? url.replaceFirst("/(w\\d+|h\\d+|original)/", "/" + size + "/") : result;
    }
}
