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
import com.fongmi.android.tv.bean.AdBlockLog;
import com.fongmi.android.tv.bean.AdBlockStats;
import com.fongmi.android.tv.bean.RuleHitRecord;
import com.fongmi.android.tv.databinding.AdapterAdBlockLogBinding;
import com.fongmi.android.tv.databinding.DialogAdBlockStatsBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.widget.AdBlockChartView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            int horizontalMargin = Math.round(16 * metrics.density);
            int verticalMargin = Math.round(16 * metrics.density);
            int width = Math.max(1, metrics.widthPixels - horizontalMargin * 2);
            int height = Math.max(1, metrics.heightPixels - verticalMargin * 2);
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.width = width;
            params.height = height;
            params.gravity = android.view.Gravity.CENTER;
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.getDecorView().setPadding(0, 0, 0, 0);
            window.setAttributes(params);
            window.setLayout(width, height);
            binding.getRoot().setMinimumHeight(height);
        }
    }

    private void initView() {
        binding.reset.setOnClickListener(v -> onReset());
        binding.close.setOnClickListener(v -> dialog.dismiss());
        if (binding.statsTabs.getTabCount() == 0) {
            binding.statsTabs.addTab(binding.statsTabs.newTab().setText(R.string.ad_stats_overview));
            binding.statsTabs.addTab(binding.statsTabs.newTab().setText(R.string.ad_site_rank));
            binding.statsTabs.addTab(binding.statsTabs.newTab().setText(R.string.ad_rule_rank));
            binding.statsTabs.addTab(binding.statsTabs.newTab().setText(R.string.ad_pipeline_rank));
            binding.statsTabs.addTab(binding.statsTabs.newTab().setText(R.string.ad_stats_log));
            binding.statsTabs.addTab(binding.statsTabs.newTab().setText(R.string.ad_stats_chart));
            binding.statsTabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) { showPage(tab.getPosition()); }
                @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
                @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            });
        }
        showPage(binding.statsTabs.getSelectedTabPosition());
    }

    private void showPage(int position) {
        binding.overviewPage.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        binding.sitePage.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        binding.rulePage.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        binding.pipelinePage.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
        binding.logPage.setVisibility(position == 4 ? View.VISIBLE : View.GONE);
        binding.chartPage.setVisibility(position == 5 ? View.VISIBLE : View.GONE);
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

        // 站点维度统计概览与排行使用同一份快照
        List<SiteRankItem> siteRank = buildSiteRank(stats);
        binding.siteCount.setText(String.valueOf(stats.getSiteBlocked().size()));
        if (siteRank.isEmpty()) {
            binding.topSite.setText("-");
            binding.topSiteShare.setText("0%");
        } else {
            SiteRankItem top = siteRank.get(0);
            binding.topSite.setText(top.getDisplayName());
            double share = stats.getTotalBlocked() > 0 ? top.getCount() * 100.0 / stats.getTotalBlocked() : 0.0;
            binding.topSiteShare.setText(String.format(Locale.getDefault(), "%.1f%%", share));
        }

        // 站点、规则和播放链路默认显示汇总项，点击汇总项展开对应的逐条日志。
        if (siteRank.isEmpty()) {
            binding.siteRankEmpty.setVisibility(View.VISIBLE);
            binding.siteRankRecycler.setVisibility(View.GONE);
        } else {
            binding.siteRankEmpty.setVisibility(View.GONE);
            binding.siteRankRecycler.setVisibility(View.VISIBLE);
            binding.siteRankRecycler.setAdapter(new GroupedLogAdapter(siteRank, stats, GroupType.SOURCE));
        }

        List<RuleHitRecord> ruleRank = AdBlockStatsStore.getTopRules(10);
        if (ruleRank.isEmpty()) {
            binding.ruleRankEmpty.setVisibility(View.VISIBLE);
            binding.ruleRankRecycler.setVisibility(View.GONE);
        } else {
            binding.ruleRankEmpty.setVisibility(View.GONE);
            binding.ruleRankRecycler.setVisibility(View.VISIBLE);
            binding.ruleRankRecycler.setAdapter(new RuleGroupedLogAdapter(ruleRank, stats));
        }

        List<SiteRankItem> pipelineRank = buildPipelineRank(stats);
        if (pipelineRank.isEmpty()) {
            binding.pipelineRankEmpty.setVisibility(View.VISIBLE);
            binding.pipelineRankRecycler.setVisibility(View.GONE);
        } else {
            binding.pipelineRankEmpty.setVisibility(View.GONE);
            binding.pipelineRankRecycler.setVisibility(View.VISIBLE);
            binding.pipelineRankRecycler.setAdapter(new GroupedLogAdapter(pipelineRank, stats, GroupType.PIPELINE));
        }

        List<AdBlockLog> logs = stats.getBlockLogs();
        binding.logEmpty.setVisibility(logs.isEmpty() ? View.VISIBLE : View.GONE);
        binding.logTableScroll.setVisibility(logs.isEmpty() ? View.GONE : View.VISIBLE);
        binding.logRecycler.setAdapter(new BlockLogAdapter(logs));

        List<SiteRankItem> chartItems = buildSiteRank(stats);
        binding.chartEmpty.setVisibility(chartItems.isEmpty() ? View.VISIBLE : View.GONE);
        binding.chartView.setVisibility(chartItems.isEmpty() ? View.GONE : View.VISIBLE);
        List<AdBlockChartView.Entry> chartEntries = chartItems.stream()
                .map(item -> new AdBlockChartView.Entry(item.getDisplayName(), item.getCount()))
                .collect(Collectors.toList());
        binding.chartView.setEntries(chartEntries);
    }

    private List<SiteRankItem> buildSiteRank(AdBlockStats stats) {
        Map<String, String> displayNames = new java.util.HashMap<>();
        for (AdBlockLog log : stats.getBlockLogs()) {
            String name = log.getSiteName();
            if (name == null || name.isBlank()) continue;
            if (!log.getSiteKey().isBlank()) displayNames.putIfAbsent(log.getSiteKey(), name);
            displayNames.putIfAbsent(log.getSourceName(), name);
        }
        return stats.getSiteBlocked().entrySet().stream()
                .map(entry -> new SiteRankItem(entry.getKey(),
                        displayNames.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingLong(SiteRankItem::getCount).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<SiteRankItem> buildPipelineRank(AdBlockStats stats) {
        return stats.getPipelineCounts().entrySet().stream()
                .map(entry -> new SiteRankItem(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(SiteRankItem::getCount).reversed())
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
        private final String displayName;
        private final long count;

        public SiteRankItem(String siteKey, long count) {
            this(siteKey, siteKey, count);
        }

        public SiteRankItem(String siteKey, String displayName, long count) {
            this.siteKey = siteKey;
            this.displayName = displayName;
            this.count = count;
        }

        public String getSiteKey() {
            return siteKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public long getCount() {
            return count;
        }
    }

    private enum GroupType { SOURCE, PIPELINE }

    private static class GroupedLogAdapter extends RecyclerView.Adapter<GroupedLogAdapter.ViewHolder> {
        private final List<SiteRankItem> groups;
        private final AdBlockStats stats;
        private final GroupType type;
        private final java.util.Set<String> expanded = new java.util.HashSet<>();
        private final List<Object> visibleItems = new ArrayList<>();

        GroupedLogAdapter(List<SiteRankItem> groups, AdBlockStats stats, GroupType type) {
            this.groups = groups;
            this.stats = stats;
            this.type = type;
            rebuild();
        }

        private void rebuild() {
            visibleItems.clear();
            for (SiteRankItem group : groups) {
                visibleItems.add(group);
                if (expanded.contains(group.getSiteKey())) {
                    visibleItems.addAll(type == GroupType.SOURCE
                            ? stats.getBlockLogsBySite(group.getSiteKey())
                            : stats.getBlockLogsByPipeline(group.getSiteKey()));
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_ad_stats_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Object visible = visibleItems.get(position);
            if (visible instanceof SiteRankItem) {
                SiteRankItem group = (SiteRankItem) visible;
                boolean isExpanded = expanded.contains(group.getSiteKey());
                holder.binding.name.setText((isExpanded ? "▼ " : "▶ ") + group.getDisplayName());
                holder.binding.source.setVisibility(View.GONE);
                holder.binding.count.setText(String.valueOf(group.getCount()));
                holder.itemView.setOnClickListener(view -> {
                    if (!expanded.add(group.getSiteKey())) expanded.remove(group.getSiteKey());
                    rebuild();
                    notifyDataSetChanged();
                });
            } else {
                bindLog(holder, (AdBlockLog) visible);
            }
        }

        private void bindLog(ViewHolder holder, AdBlockLog log) {
            holder.binding.name.setText("  " + log.getAdDomain());
            holder.binding.source.setText(log.getSourceName() + " · " + log.getPipelineName() + " · " + log.getRuleId());
            holder.binding.source.setVisibility(View.VISIBLE);
            holder.binding.count.setText(String.format(Locale.getDefault(), "%.1fs", log.getSegmentDurationSeconds()));
            holder.itemView.setOnClickListener(null);
        }

        @Override
        public int getItemCount() {
            return visibleItems.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final com.fongmi.android.tv.databinding.AdapterAdStatsItemBinding binding;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                binding = com.fongmi.android.tv.databinding.AdapterAdStatsItemBinding.bind(itemView);
            }
        }
    }

    private static class RuleGroupedLogAdapter extends RecyclerView.Adapter<RuleGroupedLogAdapter.ViewHolder> {
        private final List<RuleHitRecord> groups;
        private final AdBlockStats stats;
        private final java.util.Set<String> expanded = new java.util.HashSet<>();
        private final List<Object> visibleItems = new ArrayList<>();

        RuleGroupedLogAdapter(List<RuleHitRecord> groups, AdBlockStats stats) {
            this.groups = groups;
            this.stats = stats;
            rebuild();
        }

        private void rebuild() {
            visibleItems.clear();
            for (RuleHitRecord group : groups) {
                visibleItems.add(group);
                if (expanded.contains(group.getRuleId())) visibleItems.addAll(stats.getBlockLogsByRule(group.getRuleId()));
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_ad_stats_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Object visible = visibleItems.get(position);
            if (visible instanceof RuleHitRecord) {
                RuleHitRecord group = (RuleHitRecord) visible;
                boolean isExpanded = expanded.contains(group.getRuleId());
                String name = "hls.legacy-fallback".equals(group.getRuleId()) ? "内部兜底规则" : group.getRuleName();
                if (name == null || name.isEmpty()) name = group.getRuleId();
                holder.binding.name.setText((isExpanded ? "▼ " : "▶ ") + name);
                holder.binding.source.setText(group.getRuleSource());
                holder.binding.source.setVisibility(View.VISIBLE);
                holder.binding.count.setText(String.valueOf(group.getHitCount()));
                holder.itemView.setOnClickListener(view -> {
                    if (!expanded.add(group.getRuleId())) expanded.remove(group.getRuleId());
                    rebuild();
                    notifyDataSetChanged();
                });
            } else {
                AdBlockLog log = (AdBlockLog) visible;
                holder.binding.name.setText("  " + log.getAdDomain());
                holder.binding.source.setText(log.getSourceName() + " · " + log.getPipelineName());
                holder.binding.source.setVisibility(View.VISIBLE);
                holder.binding.count.setText(String.format(Locale.getDefault(), "%.1fs", log.getSegmentDurationSeconds()));
                holder.itemView.setOnClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            return visibleItems.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final com.fongmi.android.tv.databinding.AdapterAdStatsItemBinding binding;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                binding = com.fongmi.android.tv.databinding.AdapterAdStatsItemBinding.bind(itemView);
            }
        }
    }

    private static class ExpandableLogAdapter extends RecyclerView.Adapter<ExpandableLogAdapter.ViewHolder> {
        private final List<AdBlockLog> items;
        private boolean expanded;

        ExpandableLogAdapter(List<AdBlockLog> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_ad_stats_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AdBlockLog item = items.get(position);
            holder.binding.name.setText(item.getAdDomain());
            holder.binding.source.setText(item.getSourceName() + " · " + item.getPipelineName() + " · " + item.getRuleId());
            holder.binding.source.setVisibility(expanded ? View.VISIBLE : View.GONE);
            holder.binding.count.setText(String.format(Locale.getDefault(), "%.1fs", item.getSegmentDurationSeconds()));
            holder.itemView.setOnClickListener(view -> {
                expanded = !expanded;
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final com.fongmi.android.tv.databinding.AdapterAdStatsItemBinding binding;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                binding = com.fongmi.android.tv.databinding.AdapterAdStatsItemBinding.bind(itemView);
            }
        }
    }

    private List<AdBlockLog> getBlockLogsBySource(AdBlockStats stats, String source) {
        return stats.getBlockLogsBySource(source);
    }

    private List<AdBlockLog> getBlockLogsByRule(AdBlockStats stats, String rule) {
        return stats.getBlockLogsByRule(rule);
    }

    private List<AdBlockLog> getBlockLogsByPipeline(AdBlockStats stats, String pipeline) {
        return stats.getBlockLogsByPipeline(pipeline);
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
            holder.binding.name.setText(item.getDisplayName());
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

    private static class BlockLogAdapter extends RecyclerView.Adapter<BlockLogAdapter.ViewHolder> {
        private final List<AdBlockLog> items;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        BlockLogAdapter(List<AdBlockLog> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            AdapterAdBlockLogBinding binding = AdapterAdBlockLogBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AdBlockLog item = items.get(position);
            String unknown = holder.itemView.getContext().getString(R.string.ad_log_unknown);
            holder.binding.siteName.setText(value(item.getSiteName(), unknown));
            holder.binding.siteDomain.setText(value(item.getSiteDomain(), unknown));
            holder.binding.playbackContext.setText(playbackContext(item, unknown));
            String rule = AdBlockStatsStore.getRuleDisplayName(item.getRuleId());
            String domain = value(item.getAdDomain(), unknown);
            holder.binding.ruleDomain.setText(rule + "\n" + domain);
            holder.binding.blockedAt.setText(item.getBlockedAt() > 0
                    ? timeFormat.format(new Date(item.getBlockedAt())) : unknown);
            holder.binding.segmentStart.setText(item.hasSegmentTiming() ? seconds(item.getSegmentStartSeconds()) : unknown);
            holder.binding.segmentEnd.setText(item.hasSegmentTiming() ? seconds(item.getSegmentEndSeconds()) : unknown);
            holder.binding.segmentDuration.setText(item.hasSegmentTiming() ? seconds(item.getSegmentDurationSeconds()) : unknown);
            holder.binding.pipeline.setText(value(item.getPipelineName(), unknown));
        }

        private static String playbackContext(AdBlockLog item, String unknown) {
            String vod = value(item.getVodName(), unknown);
            String line = value(item.getLineName(), unknown);
            String episode = value(item.getEpisodeName(), unknown);
            return vod + "\n" + line + " · " + episode;
        }

        private static String seconds(double value) {
            return String.format(Locale.getDefault(), "%.1f s", value);
        }

        private static String value(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final AdapterAdBlockLogBinding binding;

            ViewHolder(AdapterAdBlockLogBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
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
