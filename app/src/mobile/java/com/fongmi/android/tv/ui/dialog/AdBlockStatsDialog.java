package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.AdBlockStatsStore;
import com.fongmi.android.tv.bean.AdBlockStats;
import com.fongmi.android.tv.bean.RuleHitRecord;
import com.fongmi.android.tv.databinding.DialogAdBlockStatsBinding;
import com.fongmi.android.tv.impl.Callback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 广告拦截统计对话框（Leanback）
 */
public class AdBlockStatsDialog {

    private final DialogAdBlockStatsBinding binding;
    private final AlertDialog dialog;
    private final Activity activity;

    public static AdBlockStatsDialog create(Activity activity) {
        return new AdBlockStatsDialog(activity);
    }

    private AdBlockStatsDialog(Activity activity) {
        this.activity = activity;
        this.binding = DialogAdBlockStatsBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity)
                .setView(binding.getRoot())
                .create();
    }

    public void show() {
        initView();
        loadStats();
        dialog.show();
    }

    private void initView() {
        binding.reset.setOnClickListener(v -> onReset());
        binding.close.setOnClickListener(v -> dialog.dismiss());
    }

    private void loadStats() {
        AdBlockStats stats = AdBlockStatsStore.getStats();

        // 统计概览
        binding.totalBlocked.setText(String.valueOf(stats.getTotalBlocked()));
        binding.aiFeedbackCount.setText(String.valueOf(stats.getAiRuleFeedbackCount()));

        int aiTotal = stats.getAiAnalysisTotal();
        if (aiTotal > 0) {
            binding.aiSuccessRate.setText(String.format(Locale.getDefault(), "%.1f%%", stats.getAiSuccessRate()));
        } else {
            binding.aiSuccessRate.setText("0%");
        }

        // 站点拦截排行
        List<SiteRankItem> siteRank = buildSiteRank(stats);
        if (siteRank.isEmpty()) {
            binding.siteRankEmpty.setVisibility(View.VISIBLE);
            binding.siteRankRecycler.setVisibility(View.GONE);
        } else {
            binding.siteRankEmpty.setVisibility(View.GONE);
            binding.siteRankRecycler.setVisibility(View.VISIBLE);
            binding.siteRankRecycler.setAdapter(new SiteRankAdapter(siteRank));
        }

        // 规则命中排行
        List<RuleHitRecord> ruleRank = AdBlockStatsStore.getTopRules(10);
        if (ruleRank.isEmpty()) {
            binding.ruleRankEmpty.setVisibility(View.VISIBLE);
            binding.ruleRankRecycler.setVisibility(View.GONE);
        } else {
            binding.ruleRankEmpty.setVisibility(View.GONE);
            binding.ruleRankRecycler.setVisibility(View.VISIBLE);
            binding.ruleRankRecycler.setAdapter(new RuleRankAdapter(ruleRank));
        }
    }

    private List<SiteRankItem> buildSiteRank(AdBlockStats stats) {
        return stats.getSiteBlocked().entrySet().stream()
                .map(entry -> new SiteRankItem(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(SiteRankItem::getCount).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    private void onReset() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ad_stats_reset)
                .setMessage(R.string.ad_stats_reset_confirm)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    AdBlockStatsStore.reset();
                    loadStats();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // 站点排行项
    private static class SiteRankItem {
        private final String siteKey;
        private final long count;

        public SiteRankItem(String siteKey, long count) {
            this.siteKey = siteKey;
            this.count = count;
        }

        public String getSiteKey() {
            return siteKey;
        }

        public long getCount() {
            return count;
        }
    }

    // 站点排行适配器
    private static class SiteRankAdapter extends RecyclerView.Adapter<SiteRankAdapter.ViewHolder> {
        private final List<SiteRankItem> items;

        public SiteRankAdapter(List<SiteRankItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_ad_stats_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SiteRankItem item = items.get(position);
            holder.binding.name.setText(item.getSiteKey());
            holder.binding.count.setText(String.valueOf(item.getCount()));
            holder.binding.source.setVisibility(View.GONE);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final com.fongmi.android.tv.databinding.AdapterAdStatsItemBinding binding;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                binding = com.fongmi.android.tv.databinding.AdapterAdStatsItemBinding.bind(itemView);
            }
        }
    }

    // 规则排行适配器
    private static class RuleRankAdapter extends RecyclerView.Adapter<RuleRankAdapter.ViewHolder> {
        private final List<RuleHitRecord> items;

        public RuleRankAdapter(List<RuleHitRecord> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_ad_stats_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RuleHitRecord item = items.get(position);
            holder.binding.name.setText(item.getRuleName());
            holder.binding.count.setText(String.valueOf(item.getHitCount()));
            holder.binding.source.setText(item.getRuleSource());
            holder.binding.source.setVisibility(View.VISIBLE);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final com.fongmi.android.tv.databinding.AdapterAdStatsItemBinding binding;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                binding = com.fongmi.android.tv.databinding.AdapterAdStatsItemBinding.bind(itemView);
            }
        }
    }
}
