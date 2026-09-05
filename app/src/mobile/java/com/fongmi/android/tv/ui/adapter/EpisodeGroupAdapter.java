package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterEpisodeGroupBinding;
import com.fongmi.android.tv.ui.helper.EpisodeRangePolicy;

import java.util.ArrayList;
import java.util.List;

public class EpisodeGroupAdapter extends RecyclerView.Adapter<EpisodeGroupAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Group> items;

    public EpisodeGroupAdapter(OnClickListener listener) {
        this.listener = listener;
        this.items = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Group item);
    }

    public void addAll(List<Group> items) {
        this.items.clear();
        this.items.addAll(items);
        notifyDataSetChanged();
    }

    public List<Group> getItems() {
        return items;
    }

    public int getPosition() {
        for (int i = 0; i < items.size(); i++) if (items.get(i).selected) return i;
        return 0;
    }

    public void setSelected(Group group) {
        for (Group item : items) item.selected = item == group;
        notifyItemRangeChanged(0, getItemCount());
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterEpisodeGroupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Group item = items.get(position);
        holder.binding.text.setText(item.name);
        holder.binding.text.setSelected(item.selected);
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }

    public static List<Group> build(int size, int selectedIndex, boolean reverse) {
        return build(size, selectedIndex, reverse, 0);
    }

    public static List<Group> build(int size, int selectedIndex, boolean reverse, int maxGroupSize) {
        List<Group> groups = new ArrayList<>();
        for (EpisodeRangePolicy.Range range : EpisodeRangePolicy.build(size, selectedIndex, reverse, maxGroupSize)) {
            Group group = new Group(range.label(), range.start(), range.end());
            group.selected = range.selected();
            groups.add(group);
        }
        return groups;
    }

    public static class Group {

        public final String name;
        public final int start;
        public final int end;
        public boolean selected;

        public Group(String name, int start, int end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpisodeGroupBinding binding;

        ViewHolder(@NonNull AdapterEpisodeGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
