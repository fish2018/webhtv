package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * TMDB 人物作品列表适配器
 */
public class TmdbPersonWorkAdapter extends RecyclerView.Adapter<TmdbPersonWorkAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(TmdbItem item);
    }

    private final List<TmdbItem> items = new ArrayList<>();
    private final Listener listener;
    private boolean light;

    public TmdbPersonWorkAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<TmdbItem> values) {
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    public void setLight(boolean light) {
        this.light = light;
        notifyDataSetChanged();
    }

    /**
     * 追加一批数据（用于懒加载分批显示）。
     */
    public void addItems(List<TmdbItem> values) {
        if (values == null || values.isEmpty()) return;
        int start = items.size();
        items.addAll(values);
        notifyItemRangeInserted(start, values.size());
    }

    public int getLoadedCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tmdb_person_work, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TmdbItem item = items.get(position);
        applyTheme(holder);
        holder.title.setText(item.getTitle());

        // 角色（饰 XXX 或职位）
        String credit = item.getCredit();
        if (credit != null && !credit.isEmpty()) {
            holder.character.setText(credit);
            holder.character.setVisibility(View.VISIBLE);
        } else {
            holder.character.setVisibility(View.GONE);
        }

        // 类型 + 年份（去掉副标题里的"评分"部分，避免与角标重复）
        String subtitle = item.getSubtitle();
        String yearText = subtitle != null ? subtitle.replaceAll("\\s*·\\s*评分\\s*[\\d.]+", "") : "";
        holder.year.setText(yearText);

        String overview = item.getOverview();
        if (overview != null && !overview.isEmpty()) {
            holder.overview.setText(overview);
            holder.overview.setVisibility(View.VISIBLE);
        } else {
            holder.overview.setVisibility(View.GONE);
        }

        // 评分角标
        double rating = item.getRating();
        if (rating > 0) {
            holder.rating.setText(String.format("%.1f", rating));
            holder.ratingBadge.setVisibility(View.VISIBLE);
        } else {
            holder.ratingBadge.setVisibility(View.GONE);
        }

        ImgUtil.load(item.getTitle(), toHighResUrl(item.getPosterUrl()), holder.poster, true, 400, 600);

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    private void applyTheme(ViewHolder holder) {
        if (holder.itemView instanceof MaterialCardView card) {
            TmdbCardFocusHelper.bind(card, light ? 0xFFFFFFFF : 0x261C2833, light ? 0x33424B57 : 0x1FFFFFFF);
        }
        holder.poster.setBackgroundColor(light ? 0xFFE7EDF3 : 0xFF25313D);
        holder.title.setTextColor(light ? 0xFF12202D : 0xFFFFFFFF);
        holder.year.setTextColor(light ? 0x9912202D : 0x99FFFFFF);
        holder.character.setTextColor(light ? 0xFF1D6E4A : 0xFFCFE8FF);
        holder.overview.setTextColor(light ? 0xCC12202D : 0xCCFFFFFF);
        holder.rating.setTextColor(0xFFFFFFFF);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * 转换为高清图片 URL（w780 尺寸，平衡清晰度与加载速度）。
     */
    private static String toHighResUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        int marker = url.indexOf("/t/p/");
        if (marker < 0) return url;
        int sizeStart = marker + "/t/p/".length();
        int sizeEnd = url.indexOf('/', sizeStart);
        if (sizeEnd < 0) return url;
        return url.substring(0, sizeStart) + "w780" + url.substring(sizeEnd);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView poster;
        TextView title;
        TextView character;
        TextView year;
        TextView overview;
        TextView rating;
        LinearLayout ratingBadge;

        ViewHolder(View view) {
            super(view);
            if (!Util.isLeanback()) {
                itemView.setFocusable(false);
                itemView.setFocusableInTouchMode(false);
            }
            poster = view.findViewById(R.id.poster);
            title = view.findViewById(R.id.title);
            character = view.findViewById(R.id.character);
            year = view.findViewById(R.id.year);
            overview = view.findViewById(R.id.overview);
            rating = view.findViewById(R.id.rating);
            ratingBadge = view.findViewById(R.id.ratingBadge);
        }
    }
}
