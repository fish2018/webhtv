package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ItemFollowingBinding;
import com.fongmi.android.tv.following.Following;
import com.fongmi.android.tv.following.FollowingSource;
import com.fongmi.android.tv.following.FollowingUpdatePolicy;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FollowingAdapter extends RecyclerView.Adapter<FollowingAdapter.Holder> {

    public interface Listener {
        void onOpenDetail(Following item, FollowingSource source);

        void onContinue(Following item, FollowingSource source);

        void onFollowNextSeason(Following item);

        void onCheck(Following item);

        void onRead(Following item);

        void onToggleNotify(Following item);

        void onChangeSource(Following item);

        void onDelete(Following item);
    }

    public static final class Row {
        public final Following following;
        public final FollowingSource source;

        public Row(Following following, FollowingSource source) {
            this.following = following;
            this.source = source;
        }
    }

    private final Listener listener;
    private final List<Row> items = new ArrayList<>();

    public FollowingAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setItems(List<Row> rows) {
        items.clear();
        if (rows != null) items.addAll(rows);
        notifyDataSetChanged();
    }

    public int indexOf(String identityKey) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).following.identityKey.equals(identityKey)) return i;
        }
        return -1;
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).following.identityKey.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemFollowingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = items.get(position);
        Following item = row.following;
        FollowingSource source = row.source;
        holder.binding.title.setText(item.vodName);
        ImgUtil.load(item.vodName, item.vodPic, holder.binding.image);
        int released = FollowingUpdatePolicy.releasedEpisode(item);
        if (released > 0) holder.binding.official.setText(holder.itemView.getContext().getString(R.string.following_official, released));
        else holder.binding.official.setText(R.string.following_official_unknown);
        if (source != null && source.playableEpisode > 0) {
            holder.binding.source.setText(holder.itemView.getContext().getString(R.string.following_source, source.playableEpisode));
        } else {
            holder.binding.source.setText(R.string.following_source_unknown);
        }
        if (item.watchedEpisode > 0) holder.binding.progress.setText(holder.itemView.getContext().getString(R.string.following_watched, item.watchedEpisode));
        else holder.binding.progress.setText(R.string.following_source_unknown);
        if (item.unwatchedCount > 0) holder.binding.progress.append("\n" + holder.itemView.getContext().getString(R.string.following_unwatched, item.unwatchedCount));
        else if (released > 0) holder.binding.progress.append("\n" + holder.itemView.getContext().getString(R.string.following_unwatched_none));
        if (item.hasUpdate && item.unwatchedCount > 0) {
            holder.binding.badge.setVisibility(View.VISIBLE);
            holder.binding.badge.setText(holder.itemView.getContext().getString(R.string.following_new_count, item.unwatchedCount));
        } else {
            holder.binding.badge.setVisibility(View.GONE);
        }
        if (item.lastError == null || item.lastError.isBlank()) {
            holder.binding.error.setVisibility(View.GONE);
        } else {
            holder.binding.error.setVisibility(View.VISIBLE);
            holder.binding.error.setText(item.lastError);
        }
        holder.binding.notify.setText(item.notifyEnabled ? R.string.following_notify_on : R.string.following_notify_off);
        holder.itemView.setOnClickListener(view -> listener.onOpenDetail(item, source));
        if (item.latestReleasedSeason > item.trackedSeason) {
            holder.binding.nextSeason.setVisibility(View.VISIBLE);
            holder.binding.nextSeason.setText(holder.itemView.getContext().getString(R.string.following_next_season, item.latestReleasedSeason + 1));
        } else {
            holder.binding.nextSeason.setVisibility(View.GONE);
        }
        holder.binding.nextSeason.setOnClickListener(view -> listener.onFollowNextSeason(item));
        holder.binding.continuePlay.setOnClickListener(view -> listener.onContinue(item, source));
        holder.binding.check.setOnClickListener(view -> listener.onCheck(item));
        holder.binding.read.setVisibility(item.hasUpdate ? View.VISIBLE : View.GONE);
        holder.binding.read.setOnClickListener(view -> listener.onRead(item));
        holder.binding.notify.setOnClickListener(view -> listener.onToggleNotify(item));
        holder.binding.sourceChange.setOnClickListener(view -> listener.onChangeSource(item));
        holder.binding.delete.setOnClickListener(view -> listener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public Row rowAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    public List<Row> getItems() {
        return new ArrayList<>(items);
    }

    public void markReadLocally(Collection<String> identityKeys) {
        if (identityKeys == null || identityKeys.isEmpty()) return;
        for (int i = 0; i < items.size(); i++) {
            Following item = items.get(i).following;
            if (!identityKeys.contains(item.identityKey) || !item.hasUpdate) continue;
            FollowingUpdatePolicy.markRead(item, 0);
            notifyItemChanged(i);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemFollowingBinding binding;

        Holder(ItemFollowingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
