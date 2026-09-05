package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.widget.TextView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterGithubProxyBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GithubProxyAdapter extends RecyclerView.Adapter<GithubProxyAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<String> mItems;
    private final Map<String, String> mLatency = new ConcurrentHashMap<>();
    private final Set<String> mProbing = ConcurrentHashMap.newKeySet();
    private int mSelected;

    public GithubProxyAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onActive(String item);

        void onRemove(String item);
    }

    public void setItems(List<String> items, String active) {
        mItems.clear();
        mItems.addAll(items);
        mLatency.keySet().retainAll(items);
        mSelected = Math.max(0, mItems.indexOf(active));
        notifyDataSetChanged();
    }

    public void setLatency(String item, String value) {
        int position = mItems.indexOf(item);
        mLatency.put(item, value);
        mProbing.remove(item);
        if (position >= 0) notifyItemChanged(position);
    }

    public void startProbe(List<String> items) {
        mProbing.clear();
        mProbing.addAll(items);
        for (String item : items) {
            mLatency.remove(item);
            int position = mItems.indexOf(item);
            if (position >= 0) notifyItemChanged(position);
        }
    }

    public boolean isProbing() {
        return !mProbing.isEmpty();
    }

    public int getSelected() {
        return mSelected;
    }

    public String getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterGithubProxyBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = mItems.get(position);
        TextView latency = holder.itemView.findViewById(R.id.latency);
        holder.binding.text.setText(item);
        latency.setText(mProbing.contains(item)
                ? holder.itemView.getContext().getString(R.string.setting_github_proxy_latency_testing)
                : mLatency.getOrDefault(item, ""));
        holder.binding.text.setActivated(position == mSelected);
        holder.binding.text.setOnClickListener(v -> listener.onActive(item));
        holder.binding.remove.setOnClickListener(v -> listener.onRemove(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterGithubProxyBinding binding;

        public ViewHolder(@NonNull AdapterGithubProxyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
