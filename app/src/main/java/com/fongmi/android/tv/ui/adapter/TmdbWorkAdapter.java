package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.databinding.AdapterTmdbWorkBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class TmdbWorkAdapter extends RecyclerView.Adapter<TmdbWorkAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(TmdbItem item);
    }

    private final Listener listener;
    private final List<TmdbItem> items = new ArrayList<>();
    private boolean light;

    public TmdbWorkAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<TmdbItem> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    public void setLight(boolean light) {
        this.light = light;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTmdbWorkBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TmdbItem item = items.get(position);
        boolean phone = isPhoneWidth(holder.itemView);
        holder.binding.getRoot().setMinimumHeight(phone ? dp(holder.itemView, 132) : 0);
        holder.binding.title.setMaxLines(phone ? 2 : 1);
        holder.binding.overview.setMaxLines(phone ? 2 : 3);
        holder.binding.title.setText(item.getTitle());
        holder.binding.meta.setText(formatMeta(item.getSubtitle()));
        holder.binding.meta.setVisibility(TextUtils.isEmpty(item.getSubtitle()) ? View.GONE : View.VISIBLE);
        holder.binding.credit.setText(item.getCredit());
        holder.binding.credit.setVisibility(TextUtils.isEmpty(item.getCredit()) ? View.GONE : View.VISIBLE);
        holder.binding.overview.setText(item.getOverview());
        holder.binding.overview.setVisibility(TextUtils.isEmpty(item.getOverview()) ? View.GONE : View.VISIBLE);
        TmdbCardFocusHelper.bind(holder.binding.getRoot(), light ? 0xFFF5F8FB : 0x261C2833, light ? 0x22424B57 : 0x1FFFFFFF);
        holder.binding.title.setTextColor(light ? 0xFF12202D : 0xFFFFFFFF);
        holder.binding.meta.setTextColor(light ? 0x9912202D : 0x99FFFFFF);
        holder.binding.credit.setTextColor(light ? 0xFF1D6E4A : 0xFFCFE8FF);
        holder.binding.overview.setTextColor(light ? 0xCC12202D : 0xCCFFFFFF);
        ImgUtil.load(item.getTitle(), item.getPosterUrl(), holder.binding.poster);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    private String formatMeta(String meta) {
        if (TextUtils.isEmpty(meta)) return "";
        return meta.replace(" \u00b7 \u8bc4\u5206", "\n\u8bc4\u5206");
    }

    private boolean isPhoneWidth(View view) {
        return view.getResources().getConfiguration().smallestScreenWidthDp < 600;
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbWorkBinding binding;

        ViewHolder(@NonNull AdapterTmdbWorkBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
