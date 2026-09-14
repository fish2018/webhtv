package com.fongmi.android.tv.ui.adapter;

import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSiteBinding;
import com.fongmi.android.tv.databinding.AdapterSiteSwitchBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteBlockSetting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.setting.SiteOrderStore;
import com.fongmi.android.tv.ui.dialog.BaseGlassDialog;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    private static final int TYPE_SWITCH = 0;
    private static final int TYPE_ACTION = 1;

    private final OnClickListener listener;
    private final List<Site> allItems;
    private final List<Site> mItems;
    private final long adapterStart;
    private boolean firstBindLogged;
    private int displayLimit = Integer.MAX_VALUE;
    private int type;

    public SiteAdapter(OnClickListener listener) {
        this.adapterStart = System.currentTimeMillis();
        this.listener = listener;
        this.allItems = new ArrayList<>();
        this.mItems = new ArrayList<>();
        setHasStableIds(true);
        this.addAll();
        log("created total=%sms items=%s", cost(), mItems.size());
    }

    public interface OnClickListener {
        void onItemClick(Site item);
    }

    public interface OnDeleteListener {
        void onDelete(Site item);
    }

    public void removeSite(Site item) {
        int position = mItems.indexOf(item);
        if (position < 0) return;
        mItems.remove(position);
        allItems.remove(item);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, Math.max(0, getItemCount() - position));
    }

    public void setType(int type) {
        int oldViewType = getViewType();
        this.type = type;
        if (oldViewType != getViewType()) notifyDataSetChanged();
        else notifyItemRangeChanged(0, getItemCount());
    }

    public void filter(String keyword) {
        String text = keyword == null ? "" : keyword.trim().toLowerCase(Locale.getDefault());
        mItems.clear();
        for (Site site : allItems) if (text.isEmpty() || site.getName().toLowerCase(Locale.getDefault()).contains(text) || site.getKey().toLowerCase(Locale.getDefault()).contains(text)) mItems.add(site);
        displayLimit = Integer.MAX_VALUE;
        notifyDataSetChanged();
    }

    public void selectAll() {
        setEnable(type != 3);
    }

    public void cancelAll() {
        setEnable(type == 3);
    }

    public void refresh() {
        allItems.clear();
        mItems.clear();
        addAll();
        notifyDataSetChanged();
    }

    private void addAll() {
        long collectStart = System.currentTimeMillis();
        for (Site site : VodConfig.get().getSites()) {
            if (site.isHide() || SiteBlockSetting.isBlocked(site)) continue;
            allItems.add(site);
        }
        log("collect sites cost=%sms visible=%s", cost(collectStart), allItems.size());
        SiteOrderStore.sortSites(allItems);
        if (Setting.isSiteHealthDialogSort()) {
            long sortStart = System.currentTimeMillis();
            SiteHealthStore.sortSites(allItems);
            log("health sort cost=%sms visible=%s", cost(sortStart), allItems.size());
        }
        mItems.addAll(allItems);
    }

    public List<Site> getItems() {
        return mItems;
    }

    public int getTotalCount() {
        return mItems.size();
    }

    public void setDisplayLimit(int displayLimit) {
        this.displayLimit = Math.max(1, displayLimit);
    }

    public void showAll() {
        int before = getItemCount();
        displayLimit = Integer.MAX_VALUE;
        int after = getItemCount();
        if (after > before) notifyItemRangeInserted(before, after - before);
    }

    @Override
    public int getItemCount() {
        return Math.min(mItems.size(), displayLimit);
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= mItems.size()) return RecyclerView.NO_ID;
        String key = mItems.get(position).getKey();
        return key == null ? position : key.hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return getViewType();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return viewType == TYPE_ACTION ? new ViewHolder(AdapterSiteBinding.inflate(inflater, parent, false)) : new ViewHolder(AdapterSiteSwitchBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site item = mItems.get(position);
        if (!firstBindLogged) {
            firstBindLogged = true;
            log("first bind position=%s name=%s total=%sms", position, item.getName(), cost());
        }
        holder.bind(item);
    }

    private boolean getChecked(Site item) {
        if (type == 1) return item.isSearchable();
        if (type == 2) return item.isChangeable();
        return false;
    }

    private void setEnable(boolean enable) {
        if (type == 1) for (Site site : mItems) site.setSearchable(enable).save();
        if (type == 2) for (Site site : mItems) site.setChangeable(enable).save();
        notifyItemRangeChanged(0, getItemCount());
    }

    private int getViewType() {
        return type == 0 ? TYPE_SWITCH : TYPE_ACTION;
    }

    private long cost() {
        return cost(adapterStart);
    }

    private long cost(long start) {
        return System.currentTimeMillis() - start;
    }

    private void log(String msg, Object... args) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("site-dialog", msg, args);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSiteBinding actionBinding;
        private final AdapterSiteSwitchBinding switchBinding;
        private final android.os.Handler handler = new android.os.Handler();
        private Runnable longPressRunnable;
        private boolean longPressFired;
        private Site item;

        ViewHolder(@NonNull AdapterSiteBinding binding) {
            super(binding.getRoot());
            this.actionBinding = binding;
            this.switchBinding = null;
            binding.text.setGravity(Gravity.CENTER);
            binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> binding.text.setSelected(hasFocus || isSelected()));
        }

        ViewHolder(@NonNull AdapterSiteSwitchBinding binding) {
            super(binding.getRoot());
            this.actionBinding = null;
            this.switchBinding = binding;
            binding.text.setGravity(Gravity.CENTER);
            binding.getRoot().setBackground(BaseGlassDialog.glassItemBackground());
            binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> binding.text.setSelected(hasFocus || isSelected()));
        }

        void bind(Site item) {
            this.item = item;
            handler.removeCallbacksAndMessages(null);
            longPressFired = false;
            final Site captureItem = item;
            final OnClickListener captureListener = listener;
            final int captureType = type;
            longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (longPressFired) return;
                    longPressFired = true;
                    if (captureType == 0) {
                        if (captureItem.isFile()) {
                            if (captureListener instanceof OnDeleteListener) {
                                ((OnDeleteListener) captureListener).onDelete(captureItem);
                            }
                        }
                    } else {
                        if (captureType == 1) {
                            boolean enable = !captureItem.isSearchable();
                            setEnable(enable);
                        } else if (captureType == 2) {
                            boolean enable = !captureItem.isChangeable();
                            setEnable(enable);
                        }
                    }
                }
            };
        
            itemView.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                    handler.removeCallbacks(longPressRunnable);
                    longPressFired = false;
                    return false;
                }
        
                if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER) return false;
        
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        longPressFired = false;
                        handler.removeCallbacks(longPressRunnable);
                        handler.postDelayed(longPressRunnable, 500);
                    }
                    return false;
                }
        
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    handler.removeCallbacks(longPressRunnable);
                    boolean fired = longPressFired;
                    longPressFired = false;
                    if (itemView.getParent() == null) return true;
                    if (!fired) {
                        int pos = getBindingAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            if (captureType == 0) {
                                captureListener.onItemClick(captureItem);
                            }
                            if (captureType == 1) {
                                captureItem.setSearchable(!captureItem.isSearchable()).save();
                                notifyItemChanged(pos);
                            }
                            if (captureType == 2) {
                                captureItem.setChangeable(!captureItem.isChangeable()).save();
                                notifyItemChanged(pos);
                            }
                        }
                    }
                    return true;
                }
                return false;
            });

            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                if (captureType == 0) {
                    captureListener.onItemClick(captureItem);
                } else if (captureType == 1) {
                    captureItem.setSearchable(!captureItem.isSearchable()).save();
                    notifyItemChanged(pos);
                } else if (captureType == 2) {
                    captureItem.setChangeable(!captureItem.isChangeable()).save();
                    notifyItemChanged(pos);
                }
            });

            if (actionBinding != null) {
                actionBinding.text.setText(item.getName());
                actionBinding.health.setBackgroundTintList(ColorStateList.valueOf(SiteHealthStore.getColor(item)));
                actionBinding.check.setChecked(getChecked(item));
                actionBinding.text.setSelected(item.isSelected());
                actionBinding.getRoot().setSelected(item.isSelected());
                actionBinding.delete.setVisibility(item.isFile() && type != 0 ? View.VISIBLE : View.GONE);
                actionBinding.delete.setOnClickListener(v -> {
                    if (listener instanceof OnDeleteListener) ((OnDeleteListener) listener).onDelete(item);
                });
            } else {
                switchBinding.text.setText(item.getName());
                switchBinding.health.setBackgroundTintList(ColorStateList.valueOf(SiteHealthStore.getColor(item)));
                switchBinding.text.setSelected(item.isSelected());
                switchBinding.getRoot().setSelected(item.isSelected());
            }
        }

        private boolean isSelected() {
            return item != null && item.isSelected();
        }
    }
}
