package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.AdBlockStatsStore;
import com.fongmi.android.tv.bean.AdBlockLog;
import com.fongmi.android.tv.bean.AdBlockLogFilter;
import com.fongmi.android.tv.bean.AdBlockStats;
import com.fongmi.android.tv.bean.RuleHitRecord;
import com.fongmi.android.tv.databinding.AdapterAdBlockLogBinding;
import com.fongmi.android.tv.databinding.DialogAdBlockStatsBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.AdBlockTimeFormatter;
import com.fongmi.android.tv.widget.AdBlockChartView;
import com.google.android.material.button.MaterialButton;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 广告拦截统计对话框（Leanback）
 */
public class AdBlockStatsDialog {

    private final DialogAdBlockStatsBinding binding;
    private final AlertDialog dialog;
    private final Activity activity;
    private final EnumMap<AdBlockLogFilter.Column, String> logFilters =
            new EnumMap<>(AdBlockLogFilter.Column.class);
    private final EnumMap<AdBlockLogFilter.Column, Set<String>> logSelections =
            new EnumMap<>(AdBlockLogFilter.Column.class);
    private List<AdBlockLog> currentLogs = new ArrayList<>();
    private BlockLogAdapter logAdapter;
    private boolean logFiltersBound;

    public static AdBlockStatsDialog create(Activity activity) {
        return new AdBlockStatsDialog(activity);
    }

    private AdBlockStatsDialog(Activity activity) {
        this.activity = activity;
        this.binding = DialogAdBlockStatsBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setView(binding.getRoot())
                .create();
    }

    public void show() {
        initView();
        loadStats();
        dialog.show();
        configureWindow();
        configureTabFocus();
    }

    private void configureWindow() {
        Window window = dialog.getWindow();
        if (window == null) return;
        int horizontalMargin = ResUtil.dp2px(24);
        int verticalMargin = ResUtil.dp2px(24);
        int width = Math.max(1, ResUtil.getScreenWidth(activity) - horizontalMargin * 2);
        int height = Math.max(1, ResUtil.getScreenHeight(activity) - verticalMargin * 2);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = width;
        params.height = height;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        binding.getRoot().setMinimumHeight(height);
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
        if (!logFiltersBound) {
            bindLogFilters();
            logFiltersBound = true;
        }
        showPage(binding.statsTabs.getSelectedTabPosition());
    }

    private void configureTabFocus() {
        if (binding.statsTabs.getChildCount() == 0) return;
        View strip = binding.statsTabs.getChildAt(0);
        if (!(strip instanceof ViewGroup tabStrip)) return;
        for (int i = 0; i < tabStrip.getChildCount(); i++) {
            View tabView = tabStrip.getChildAt(i);
            tabView.setFocusable(true);
            tabView.setFocusableInTouchMode(true);
            tabView.setBackgroundResource(R.drawable.selector_mpv_tab_focus);
            tabView.setOnFocusChangeListener((view, hasFocus) -> {
                if (!hasFocus) return;
                int position = tabStrip.indexOfChild(view);
                com.google.android.material.tabs.TabLayout.Tab tab = binding.statsTabs.getTabAt(position);
                if (tab != null) tab.select();
            });
        }
        int position = binding.statsTabs.getSelectedTabPosition();
        if (position < 0 || position >= tabStrip.getChildCount()) position = 0;
        if (tabStrip.getChildCount() > 0) {
            View selectedTab = tabStrip.getChildAt(position);
            selectedTab.post(selectedTab::requestFocus);
        }
    }

    private void bindLogFilters() {
        bindCategoricalFilter(binding.siteFilter, AdBlockLogFilter.Column.SITE_NAME, R.string.ad_log_site_name);
        bindCategoricalFilter(binding.siteDomainFilter, AdBlockLogFilter.Column.SITE_DOMAIN, R.string.ad_log_site_domain);
        bindCategoricalFilter(binding.vodFilter, AdBlockLogFilter.Column.VOD_NAME, R.string.ad_log_playback_context);
        bindCategoricalFilter(binding.lineFilter, AdBlockLogFilter.Column.LINE_NAME, R.string.ad_log_line_name);
        bindCategoricalFilter(binding.episodeFilter, AdBlockLogFilter.Column.EPISODE_NAME, R.string.ad_log_episode_name);
        bindCategoricalFilter(binding.ruleFilter, AdBlockLogFilter.Column.RULE, R.string.ad_log_rule_domain);
        bindCategoricalFilter(binding.adDomainFilter, AdBlockLogFilter.Column.AD_DOMAIN, R.string.ad_log_ad_domain);
        bindCategoricalFilter(binding.pipelineFilter, AdBlockLogFilter.Column.PIPELINE, R.string.ad_log_pipeline);
        bindTextFilter(binding.blockedAtFilter, AdBlockLogFilter.Column.BLOCKED_AT);
        bindTextFilter(binding.segmentStartFilter, AdBlockLogFilter.Column.SEGMENT_START);
        bindTextFilter(binding.segmentEndFilter, AdBlockLogFilter.Column.SEGMENT_END);
        bindTextFilter(binding.segmentDurationFilter, AdBlockLogFilter.Column.SEGMENT_DURATION);
    }

    private void bindCategoricalFilter(MaterialButton button, AdBlockLogFilter.Column column, int title) {
        button.setOnClickListener(v -> showCategoricalFilter(button, column, title));
        updateCategoricalButton(button, column);
    }

    private void showCategoricalFilter(MaterialButton button, AdBlockLogFilter.Column column, int title) {
        Set<String> selected = logSelections.get(column);
        AdBlockLogFilterDialog.show(activity, activity.getString(title), currentLogs,
                selected == null ? java.util.Set.of() : new java.util.LinkedHashSet<>(selected),
                log -> displayValue(log, column), values -> {
                    if (values == null || values.isEmpty()) logSelections.remove(column);
                    else logSelections.put(column, new java.util.LinkedHashSet<>(values));
                    updateCategoricalButton(button, column);
                    if (logAdapter != null) {
                        logAdapter.setSelection(column, values);
                        updateLogEmptyState();
                    }
                });
    }

    private void updateCategoricalButton(MaterialButton button, AdBlockLogFilter.Column column) {
        Set<String> selected = logSelections.get(column);
        if (selected == null || selected.isEmpty()) {
            button.setText(R.string.ad_log_filter_hint);
        } else if (selected.size() == 1) {
            button.setText(selected.iterator().next());
        } else {
            button.setText(activity.getString(R.string.ad_log_filter_selected, selected.size()));
        }
        button.setEnabled(!currentLogs.isEmpty());
    }

    private void bindTextFilter(EditText input, AdBlockLogFilter.Column column) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s == null ? "" : s.toString();
                if (query.trim().isEmpty()) logFilters.remove(column);
                else logFilters.put(column, query);
                if (logAdapter != null) {
                    logAdapter.setFilter(column, query);
                    updateLogEmptyState();
                }
            }
        });
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
        currentLogs = new ArrayList<>(logs);
        updateAllCategoricalButtons();
        logAdapter = new BlockLogAdapter(logs, logFilters, logSelections, this::filterValues);
        binding.logRecycler.setAdapter(logAdapter);
        updateLogEmptyState();

        List<SiteRankItem> chartItems = buildSiteRank(stats);
        binding.chartEmpty.setVisibility(chartItems.isEmpty() ? View.VISIBLE : View.GONE);
        binding.chartView.setVisibility(chartItems.isEmpty() ? View.GONE : View.VISIBLE);
        List<AdBlockChartView.Entry> chartEntries = chartItems.stream()
                .map(item -> new AdBlockChartView.Entry(item.getDisplayName(), item.getCount()))
                .collect(Collectors.toList());
        binding.chartView.setEntries(chartEntries);
    }

    private void updateAllCategoricalButtons() {
        updateCategoricalButton(binding.siteFilter, AdBlockLogFilter.Column.SITE_NAME);
        updateCategoricalButton(binding.siteDomainFilter, AdBlockLogFilter.Column.SITE_DOMAIN);
        updateCategoricalButton(binding.vodFilter, AdBlockLogFilter.Column.VOD_NAME);
        updateCategoricalButton(binding.lineFilter, AdBlockLogFilter.Column.LINE_NAME);
        updateCategoricalButton(binding.episodeFilter, AdBlockLogFilter.Column.EPISODE_NAME);
        updateCategoricalButton(binding.ruleFilter, AdBlockLogFilter.Column.RULE);
        updateCategoricalButton(binding.adDomainFilter, AdBlockLogFilter.Column.AD_DOMAIN);
        updateCategoricalButton(binding.pipelineFilter, AdBlockLogFilter.Column.PIPELINE);
    }

    private Map<AdBlockLogFilter.Column, String> filterValues(AdBlockLog item) {
        EnumMap<AdBlockLogFilter.Column, String> values = new EnumMap<>(AdBlockLogFilter.Column.class);
        for (AdBlockLogFilter.Column column : AdBlockLogFilter.Column.values()) {
            values.put(column, displayValue(item, column));
        }
        return values;
    }

    private String displayValue(AdBlockLog item, AdBlockLogFilter.Column column) {
        String unknown = activity.getString(R.string.ad_log_unknown);
        switch (column) {
            case SITE_NAME: return value(item.getSiteName(), unknown);
            case SITE_DOMAIN: return value(item.getSiteDomain(), unknown);
            case VOD_NAME: return value(item.getVodName(), unknown);
            case LINE_NAME: return value(item.getLineName(), unknown);
            case EPISODE_NAME: return value(item.getEpisodeName(), unknown);
            case RULE: return value(AdBlockStatsStore.getRuleDisplayName(item.getRuleId()), unknown);
            case AD_DOMAIN: return value(item.getAdDomain(), unknown);
            case BLOCKED_AT: return item.getBlockedAt() > 0
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(item.getBlockedAt())) : unknown;
            case SEGMENT_START: return item.hasSegmentTiming() ? AdBlockTimeFormatter.formatSeconds(item.getSegmentStartSeconds()) : unknown;
            case SEGMENT_END: return item.hasSegmentTiming() ? AdBlockTimeFormatter.formatSeconds(item.getSegmentEndSeconds()) : unknown;
            case SEGMENT_DURATION: return item.hasSegmentTiming() ? AdBlockTimeFormatter.formatSeconds(item.getSegmentDurationSeconds()) : unknown;
            case PIPELINE: return value(item.getPipelineName(), unknown);
            default: return unknown;
        }
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void updateLogEmptyState() {
        boolean noLogs = logAdapter == null || logAdapter.getSourceItemCount() == 0;
        boolean noMatches = !noLogs && logAdapter.getItemCount() == 0;
        binding.logEmpty.setText(noLogs ? R.string.ad_stats_empty : R.string.ad_stats_filter_empty);
        binding.logEmpty.setVisibility(noLogs || noMatches ? View.VISIBLE : View.GONE);
        binding.logTableScroll.setVisibility(noLogs ? View.GONE : View.VISIBLE);
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

    private void clearTextFilters() {
        binding.blockedAtFilter.setText("");
        binding.segmentStartFilter.setText("");
        binding.segmentEndFilter.setText("");
        binding.segmentDurationFilter.setText("");
        updateAllCategoricalButtons();
    }

    private void onReset() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ad_stats_reset)
                .setMessage(R.string.ad_stats_reset_confirm)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    AdBlockStatsStore.reset();
                    logFilters.clear();
                    logSelections.clear();
                    clearTextFilters();
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

    private static void restoreItemFocus(RecyclerView recycler, int requestedPosition, int itemCount) {
        if (recycler == null || itemCount <= 0) return;
        int target = Math.min(Math.max(0, requestedPosition), itemCount - 1);
        recycler.scrollToPosition(target);
        recycler.post(() -> {
            RecyclerView.ViewHolder targetHolder = recycler.findViewHolderForAdapterPosition(target);
            if (targetHolder != null) targetHolder.itemView.requestFocus();
        });
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
                    int focusedPosition = holder.getBindingAdapterPosition();
                    RecyclerView recycler = (RecyclerView) view.getParent();
                    if (!expanded.add(group.getSiteKey())) expanded.remove(group.getSiteKey());
                    rebuild();
                    notifyDataSetChanged();
                    restoreItemFocus(recycler, focusedPosition, getItemCount());
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
            holder.itemView.setOnClickListener(view -> view.requestFocus());
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
                    int focusedPosition = holder.getBindingAdapterPosition();
                    RecyclerView recycler = (RecyclerView) view.getParent();
                    if (!expanded.add(group.getRuleId())) expanded.remove(group.getRuleId());
                    rebuild();
                    notifyDataSetChanged();
                    restoreItemFocus(recycler, focusedPosition, getItemCount());
                });
            } else {
                AdBlockLog log = (AdBlockLog) visible;
                holder.binding.name.setText("  " + log.getAdDomain());
                holder.binding.source.setText(log.getSourceName() + " · " + log.getPipelineName());
                holder.binding.source.setVisibility(View.VISIBLE);
                holder.binding.count.setText(String.format(Locale.getDefault(), "%.1fs", log.getSegmentDurationSeconds()));
                holder.itemView.setOnClickListener(view -> view.requestFocus());
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
            holder.itemView.setFocusable(true);
            holder.itemView.setFocusableInTouchMode(true);
            holder.itemView.setOnClickListener(view -> view.requestFocus());
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
        private final List<AdBlockLog> sourceItems;
        private List<AdBlockLog> items;
        private final EnumMap<AdBlockLogFilter.Column, String> filters =
                new EnumMap<>(AdBlockLogFilter.Column.class);
        private final EnumMap<AdBlockLogFilter.Column, Set<String>> selections =
                new EnumMap<>(AdBlockLogFilter.Column.class);
        private final AdBlockLogFilter.ValueProvider valueProvider;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        BlockLogAdapter(List<AdBlockLog> items, Map<AdBlockLogFilter.Column, String> initialFilters,
                        Map<AdBlockLogFilter.Column, ? extends Set<String>> initialSelections,
                        AdBlockLogFilter.ValueProvider valueProvider) {
            this.sourceItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
            if (initialFilters != null) filters.putAll(initialFilters);
            if (initialSelections != null) {
                for (Map.Entry<AdBlockLogFilter.Column, ? extends Set<String>> entry : initialSelections.entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        selections.put(entry.getKey(), new java.util.LinkedHashSet<>(entry.getValue()));
                    }
                }
            }
            this.valueProvider = valueProvider;
            applyFilters();
        }

        void setFilter(AdBlockLogFilter.Column column, String query) {
            if (query == null || query.trim().isEmpty()) filters.remove(column);
            else filters.put(column, query);
            applyFilters();
            notifyDataSetChanged();
        }

        void setSelection(AdBlockLogFilter.Column column, Set<String> values) {
            if (values == null || values.isEmpty()) selections.remove(column);
            else selections.put(column, new java.util.LinkedHashSet<>(values));
            applyFilters();
            notifyDataSetChanged();
        }

        int getSourceItemCount() {
            return sourceItems.size();
        }

        private void applyFilters() {
            items = AdBlockLogFilter.filter(sourceItems, filters, selections, valueProvider);
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
            holder.binding.playbackContext.setText(value(item.getVodName(), unknown));
            holder.binding.lineName.setText(value(item.getLineName(), unknown));
            holder.binding.episodeName.setText(value(item.getEpisodeName(), unknown));
            String rule = AdBlockStatsStore.getRuleDisplayName(item.getRuleId());
            String domain = value(item.getAdDomain(), unknown);
            holder.binding.ruleDomain.setText(value(rule, unknown));
            holder.binding.adDomain.setText(domain);
            holder.binding.blockedAt.setText(item.getBlockedAt() > 0
                    ? timeFormat.format(new Date(item.getBlockedAt())) : unknown);
            holder.binding.segmentStart.setText(item.hasSegmentTiming() ? seconds(item.getSegmentStartSeconds()) : unknown);
            holder.binding.segmentEnd.setText(item.hasSegmentTiming() ? seconds(item.getSegmentEndSeconds()) : unknown);
            holder.binding.segmentDuration.setText(item.hasSegmentTiming() ? seconds(item.getSegmentDurationSeconds()) : unknown);
            holder.binding.pipeline.setText(value(item.getPipelineName(), unknown));
        }

        private static String seconds(double value) {
            return AdBlockTimeFormatter.formatSeconds(value);
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
