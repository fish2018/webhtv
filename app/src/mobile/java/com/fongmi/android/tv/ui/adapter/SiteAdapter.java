package com.fongmi.android.tv.ui.adapter;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSiteBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.setting.SiteBlockSetting;
import com.fongmi.android.tv.setting.SiteOrderStore;
import com.fongmi.android.tv.setting.SiteNameStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Site> mAllItems;
    private final List<Site> mItems;
    private String group;
    private String keyword = "";
    private boolean search;
    private boolean change;
    private boolean block;
    private int column = 1;

    public SiteAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mAllItems = new ArrayList<>();
        this.mItems = new ArrayList<>();
        this.column = 1;
        this.group = "";
        this.addAll();
    }

    public interface OnClickListener {

        void onTextClick(Site item);

        void onSearchClick(int position, Site item);

        void onChangeClick(int position, Site item);

        boolean onTextLongClick(ViewHolder holder);

        boolean onSearchLongClick(Site item);

        boolean onChangeLongClick(Site item);
    }

    public SiteAdapter search(boolean search) {
        this.search = search;
        return this;
    }

    public SiteAdapter change(boolean change) {
        this.change = change;
        return this;
    }

    public SiteAdapter block(boolean block) {
        if (this.block == block) return this;
        this.block = block;
        reload();
        return this;
    }

    public void column(int column) {
        int value = Math.max(1, column);
        if (this.column == value) return;
        this.column = value;
        notifyDataSetChanged();
    }

    private void reload() {
        mAllItems.clear();
        addAll();
    }

    private void addAll() {
        mAllItems.addAll(SiteBlockSetting.filter(VodConfig.get().getSites(), block));
        if (Setting.isSiteHealthDialogSort()) SiteHealthStore.sortSites(mAllItems);
        SiteOrderStore.sortSites(mAllItems);
        filter(group, keyword);
    }

    public List<Site> getItems() {
        return mItems;
    }

    public int getSelectedPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public void filter(String keyword) {
        filter(group, keyword);
    }

    public void filter(String group, String keyword) {
        this.group = group;
        this.keyword = keyword;
        String text = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        boolean searching = !TextUtils.isEmpty(text);
        mItems.clear();
        for (Site site : mAllItems) {
            boolean matchGroup = searching || site.inGroup(group);
            boolean matchKeyword = !searching || SiteNameStore.matchesSearch(site, text);
            if (matchGroup && matchKeyword) mItems.add(site);
        }
        notifyDataSetChanged();
    }

    public boolean drag(int from, int to) {
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
        if (from < 0 || to < 0 || from >= mItems.size() || to >= mItems.size() || from == to) return false;
        Site moving = mItems.get(from);
        Site target = mItems.get(to);
        moveAllItem(moving, target, from < to);
        mItems.remove(from);
        mItems.add(to, moving);
        notifyItemMoved(from, to);
        SiteOrderStore.save(mAllItems);
        return true;
    }

    private void moveAllItem(Site moving, Site target, boolean afterTarget) {
        int oldIndex = mAllItems.indexOf(moving);
        int targetIndex = mAllItems.indexOf(target);
        if (oldIndex < 0 || targetIndex < 0 || oldIndex == targetIndex) return;
        mAllItems.remove(oldIndex);
        if (oldIndex < targetIndex) targetIndex--;
        int insertIndex = afterTarget ? targetIndex + 1 : targetIndex;
        mAllItems.add(Math.max(0, Math.min(insertIndex, mAllItems.size())), moving);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSiteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site item = mItems.get(position);
        boolean blocked = SiteBlockSetting.isBlocked(item);
        boolean on = block || !search || change;
        boolean singleColumn = column == 1;
        holder.binding.text.setText(item.getDisplayName());
        holder.binding.health.setBackgroundTintList(ColorStateList.valueOf(SiteHealthStore.getColor(item)));
        holder.binding.text.setEnabled(on);
        holder.binding.text.setFocusable(false);
        holder.binding.text.setSelected(block ? blocked : on && item.isSelected());
        holder.binding.text.setAlpha(1.0f);
        holder.binding.health.setAlpha(block && blocked ? 0.55f : 1.0f);
        holder.binding.search.setImageResource(getSearchIcon(item));
        holder.binding.change.setImageResource(getChangeIcon(item));
        holder.binding.search.setVisibility(!block && search && singleColumn ? View.VISIBLE : View.GONE);
        holder.binding.change.setVisibility(!block && change && singleColumn ? View.VISIBLE : View.GONE);
        holder.binding.health.setVisibility(singleColumn ? View.VISIBLE : View.GONE);
        holder.binding.text.setOnClickListener(v -> listener.onTextClick(item));
        holder.binding.text.setOnLongClickListener(v -> {
            holder.binding.text.setSelected(true);
            return true;
        });
        holder.binding.search.setOnClickListener(v -> listener.onSearchClick(position, item));
        holder.binding.change.setOnClickListener(v -> listener.onChangeClick(position, item));
        holder.binding.text.setOnLongClickListener(v -> listener.onTextLongClick(holder));
        holder.binding.search.setOnLongClickListener(v -> listener.onSearchLongClick(item));
        holder.binding.change.setOnLongClickListener(v -> listener.onChangeLongClick(item));
    }

    private int getSearchIcon(Site item) {
        return item.isSearchable() ? R.drawable.ic_site_search : R.drawable.ic_site_block;
    }

    private int getChangeIcon(Site item) {
        return item.isChangeable() ? R.drawable.ic_site_change : R.drawable.ic_site_block;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSiteBinding binding;

        ViewHolder(@NonNull AdapterSiteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
