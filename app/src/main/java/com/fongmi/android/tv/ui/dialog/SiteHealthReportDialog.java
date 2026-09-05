package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;

import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.AdBlockStatsStore;
import com.fongmi.android.tv.databinding.DialogSiteHealthReportBinding;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

public class SiteHealthReportDialog extends BaseAlertDialog {

    private DialogSiteHealthReportBinding binding;
    private SiteHealthStore.Report report;
    private Filter filter = Filter.ALL;
    private Sort sort = Sort.RECENT;

    public static void show(Fragment fragment) {
        new SiteHealthReportDialog().show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity) {
        new SiteHealthReportDialog().show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteHealthReportBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        Window window = getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (ResUtil.getScreenWidth(requireContext()) * (ResUtil.isLand(requireContext()) ? 0.62f : 0.94f));
        params.height = (int) (ResUtil.getScreenHeight(requireContext()) * (ResUtil.isLand(requireContext()) ? 0.78f : 0.82f));
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        binding.close.requestFocus();
    }

    @Override
    protected void initView() {
        report = SiteHealthStore.report();
        updateFilters();
        updateSorts();
        render();
    }

    @Override
    protected void initEvent() {
        binding.filterAll.setOnClickListener(view -> setFilter(Filter.ALL));
        binding.filterBad.setOnClickListener(view -> setFilter(Filter.BAD));
        binding.filterWarn.setOnClickListener(view -> setFilter(Filter.WARN));
        binding.sortFailures.setOnClickListener(view -> setSort(Sort.FAILURES));
        binding.sortRecent.setOnClickListener(view -> setSort(Sort.RECENT));
        binding.sortRate.setOnClickListener(view -> setSort(Sort.RATE));
        binding.sortSamples.setOnClickListener(view -> setSort(Sort.SAMPLES));
        binding.close.setOnClickListener(view -> dismiss());
    }

    private void setFilter(Filter filter) {
        if (this.filter == filter) return;
        this.filter = filter;
        updateFilters();
        render();
    }

    private void setSort(Sort sort) {
        if (this.sort == sort) return;
        this.sort = sort;
        updateSorts();
        render();
    }

    private void updateFilters() {
        updateFilterButton(binding.filterAll, filter == Filter.ALL);
        updateFilterButton(binding.filterBad, filter == Filter.BAD);
        updateFilterButton(binding.filterWarn, filter == Filter.WARN);
    }

    private void updateSorts() {
        updateFilterButton(binding.sortFailures, sort == Sort.FAILURES);
        updateFilterButton(binding.sortRecent, sort == Sort.RECENT);
        updateFilterButton(binding.sortRate, sort == Sort.RATE);
        updateFilterButton(binding.sortSamples, sort == Sort.SAMPLES);
    }

    private void updateFilterButton(MaterialButton button, boolean selected) {
        button.setAlpha(selected ? 1.0f : 0.62f);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void render() {
        while (binding.rows.getChildCount() > 1) binding.rows.removeViewAt(1);
        binding.summary.setText(summaryText(report.summary));
        int visible = 0;
        for (SiteHealthStore.Row row : sortedRows()) {
            if (!matches(row)) continue;
            binding.rows.addView(rowView(row));
            visible++;
        }
        binding.empty.setText(report.isEmpty() ? R.string.site_health_report_empty : R.string.site_health_report_filter_empty);
        binding.empty.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
    }

    private List<SiteHealthStore.Row> sortedRows() {
        List<SiteHealthStore.Row> rows = new ArrayList<>(report.rows);
        rows.sort(this::compareRows);
        return rows;
    }

    private int compareRows(SiteHealthStore.Row a, SiteHealthStore.Row b) {
        int result;
        switch (sort) {
            case FAILURES:
                result = Integer.compare(b.failureCount(), a.failureCount());
                break;
            case RATE:
                result = Float.compare(successRate(b), successRate(a));
                break;
            case SAMPLES:
                result = Integer.compare(b.sampleCount(), a.sampleCount());
                break;
            default:
                result = Long.compare(lastFailAt(b), lastFailAt(a));
                break;
        }
        return result != 0 ? result : Long.compare(b.updatedAt, a.updatedAt);
    }

    private long lastFailAt(SiteHealthStore.Row row) {
        return Math.max(Math.max(row.search.lastFailAt, row.detail.lastFailAt), Math.max(row.parse.lastFailAt, row.play.lastFailAt));
    }

    private float successRate(SiteHealthStore.Row row) {
        int samples = row.sampleCount();
        return samples == 0 ? -1 : (samples - row.failureCount()) * 100f / samples;
    }

    private boolean matches(SiteHealthStore.Row row) {
        return switch (filter) {
            case ALL -> true;
            case BAD -> row.status == SiteHealthStore.Status.BAD;
            case WARN -> row.status == SiteHealthStore.Status.WARN;
        };
    }

    private String summaryText(SiteHealthStore.Summary summary) {
        if (TextUtils.isEmpty(summary.topFailureReason)) {
            return getString(R.string.site_health_report_summary, summary.siteCount, summary.sampleCount, summary.failureCount, summary.degradedCount);
        }
        return getString(R.string.site_health_report_summary_reason, summary.siteCount, summary.sampleCount, summary.failureCount, summary.degradedCount, reasonLabel(summary.topFailureReason), summary.topFailureCount);
    }

    private View rowView(SiteHealthStore.Row row) {
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setBackgroundResource(R.drawable.shape_site_health_report_row);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        root.setLayoutParams(params);

        LinearLayoutCompat header = new LinearLayoutCompat(requireContext());
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayoutCompat.HORIZONTAL);
        root.addView(header, new LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT));

        MaterialTextView title = text(row.siteName, 16, R.color.black, Typeface.BOLD);
        header.addView(title, new LinearLayoutCompat.LayoutParams(0, LinearLayoutCompat.LayoutParams.WRAP_CONTENT, 1));

        MaterialTextView status = text(statusLabel(row.status), 13, statusColor(row.status), Typeface.BOLD);
        status.setGravity(android.view.Gravity.END);
        LinearLayoutCompat.LayoutParams statusParams = new LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, 0, dp(8), 0);
        header.addView(status, statusParams);

        MaterialTextView toggle = text("+", 16, R.color.black_50, Typeface.BOLD);
        toggle.setGravity(android.view.Gravity.CENTER);
        header.addView(toggle, new LinearLayoutCompat.LayoutParams(dp(20), LinearLayoutCompat.LayoutParams.WRAP_CONTENT));

        root.addView(metaText(getString(R.string.site_health_report_row_meta, row.sampleCount(), row.failureCount())));
        addStage(root, R.string.site_health_stage_search, row.search);
        addStage(root, R.string.site_health_stage_detail, row.detail);
        addStage(root, R.string.site_health_stage_parse, row.parse);
        addStage(root, R.string.site_health_stage_play, row.play);
        addAdBlockStats(root, row.siteKey);

        String reason = row.topFailureReason();
        if (!TextUtils.isEmpty(reason)) root.addView(metaText(getString(R.string.site_health_report_reason, reasonLabel(reason), row.topFailureCount())));
        LinearLayoutCompat errors = recentErrors(row);
        root.addView(errors);
        boolean expandable = errors.getChildCount() > 0;
        toggle.setVisibility(expandable ? View.VISIBLE : View.INVISIBLE);
        errors.setVisibility(View.GONE);
        if (expandable) {
            root.setClickable(true);
            root.setFocusable(true);
            root.setOnClickListener(view -> {
                boolean expanded = errors.getVisibility() != View.VISIBLE;
                errors.setVisibility(expanded ? View.VISIBLE : View.GONE);
                toggle.setText(expanded ? "-" : "+");
            });
        }
        return root;
    }

    private void addStage(LinearLayoutCompat root, int labelRes, SiteHealthStore.Stage stage) {
        LinearLayoutCompat block = new LinearLayoutCompat(requireContext());
        block.setOrientation(LinearLayoutCompat.VERTICAL);
        LinearLayoutCompat.LayoutParams blockParams = new LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT);
        blockParams.setMargins(0, dp(8), 0, 0);
        root.addView(block, blockParams);

        MaterialTextView label = text(stageText(labelRes, stage), 13, R.color.black_80, Typeface.NORMAL);
        block.addView(label, new LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT));

        ProgressBar progress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(Math.max(0, stage.successRate()));
        LinearLayoutCompat.LayoutParams progressParams = new LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, dp(4));
        progressParams.setMargins(0, dp(4), 0, 0);
        block.addView(progress, progressParams);
    }

    private void addAdBlockStats(LinearLayoutCompat root, String siteKey) {
        long blocked = AdBlockStatsStore.getSiteBlockedCount(siteKey);
        // 始终显示广告拦截统计（即使为 0，让用户知道该功能在工作）
        int colorRes = blocked > 0 ? R.color.site_health_warn : R.color.black_80;
        MaterialTextView view = text(getString(R.string.site_health_report_ad_blocked, blocked), 13, colorRes, Typeface.NORMAL);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        view.setLayoutParams(params);
        root.addView(view);
    }

    private String stageText(int labelRes, SiteHealthStore.Stage stage) {
        String rate = stage.successRate() < 0 ? getString(R.string.site_health_report_no_data) : getString(R.string.site_health_report_rate, stage.successRate());
        String text = getString(R.string.site_health_report_stage, getString(labelRes), rate, stage.success, stage.sampleCount());
        if (stage.empty > 0) text += " · " + getString(R.string.site_health_report_empty_count, stage.empty);
        if (stage.lastCost > 0) text += " · " + getString(R.string.site_health_report_cost, stage.lastCost);
        String reason = stage.topReason();
        if (!TextUtils.isEmpty(reason)) text += " · " + getString(R.string.site_health_report_reason_tag, reasonLabel(reason), stage.topReasonCount());
        return text;
    }

    private LinearLayoutCompat recentErrors(SiteHealthStore.Row row) {
        LinearLayoutCompat block = new LinearLayoutCompat(requireContext());
        block.setOrientation(LinearLayoutCompat.VERTICAL);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        block.setLayoutParams(params);
        addRecentError(block, R.string.site_health_stage_search, row.search);
        addRecentError(block, R.string.site_health_stage_detail, row.detail);
        addRecentError(block, R.string.site_health_stage_parse, row.parse);
        addRecentError(block, R.string.site_health_stage_play, row.play);
        if (block.getChildCount() > 0) {
            MaterialTextView title = text(getString(R.string.site_health_report_recent_errors), 12, R.color.black_70, Typeface.BOLD);
            block.addView(title, 0);
        }
        block.addView(clearButton(row));
        return block;
    }

    private MaterialButton clearButton(SiteHealthStore.Row row) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(R.string.site_health_clear_site);
        button.setMinHeight(dp(36));
        button.setMinWidth(0);
        button.setOnClickListener(view -> confirmClearSite(row));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, dp(36));
        params.setMargins(0, dp(10), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void confirmClearSite(SiteHealthStore.Row row) {
        new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.site_health_clear_site_title)
                .setMessage(getString(R.string.site_health_clear_site_message, row.siteName))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    SiteHealthStore.clear(row.siteKey);
                    report = SiteHealthStore.report();
                    render();
                })
                .show();
    }

    private void addRecentError(LinearLayoutCompat block, int labelRes, SiteHealthStore.Stage stage) {
        if (TextUtils.isEmpty(stage.lastError)) return;
        String time = formatTime(stage.lastFailAt);
        String label = getString(labelRes);
        String text = TextUtils.isEmpty(time)
                ? getString(R.string.site_health_report_recent_error, label, stage.lastError)
                : getString(R.string.site_health_report_recent_error_time, label, time, stage.lastError);
        block.addView(metaText(text));
    }

    private String formatTime(long time) {
        return time <= 0 ? "" : DateFormat.format("MM-dd HH:mm", time).toString();
    }

    private MaterialTextView metaText(String value) {
        MaterialTextView view = text(value, 12, R.color.black_60, Typeface.NORMAL);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, 0);
        view.setLayoutParams(params);
        return view;
    }

    private MaterialTextView text(String value, int sp, int colorRes, int style) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(ResUtil.getColor(colorRes));
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private String reasonLabel(String reason) {
        if (TextUtils.isEmpty(reason)) return "";
        return switch (reason) {
            case "EMPTY_RESULT" -> getString(R.string.site_health_reason_empty_result);
            case "TIMEOUT" -> getString(R.string.site_health_reason_timeout);
            case "HTTP_403" -> getString(R.string.site_health_reason_http_403);
            case "HTTP_404" -> getString(R.string.site_health_reason_http_404);
            case "HTTP_429" -> getString(R.string.site_health_reason_http_429);
            case "HTTP_5XX" -> getString(R.string.site_health_reason_http_5xx);
            case "NETWORK" -> getString(R.string.site_health_reason_network);
            case "PARSE_FAILED" -> getString(R.string.site_health_reason_parse_failed);
            case "VIP_REQUIRED" -> getString(R.string.site_health_reason_vip_required);
            case "GEO_BLOCKED" -> getString(R.string.site_health_reason_geo_blocked);
            case "DRM_OR_ENCRYPTED" -> getString(R.string.site_health_reason_drm_or_encrypted);
            case "MANIFEST_FAILED" -> getString(R.string.site_health_reason_manifest_failed);
            case "CODEC_UNSUPPORTED" -> getString(R.string.site_health_reason_codec_unsupported);
            case "CORS_BLOCKED" -> getString(R.string.site_health_reason_cors_blocked);
            case "SEARCH_FAILED" -> getString(R.string.site_health_reason_search_failed);
            case "DETAIL_FAILED" -> getString(R.string.site_health_reason_detail_failed);
            case "PLAY_FAILED" -> getString(R.string.site_health_reason_play_failed);
            default -> reason;
        };
    }

    private String statusLabel(SiteHealthStore.Status status) {
        return switch (status) {
            case GOOD -> getString(R.string.site_health_status_good);
            case BAD -> getString(R.string.site_health_status_bad);
            case WARN -> getString(R.string.site_health_status_warn);
            default -> getString(R.string.site_health_status_unknown);
        };
    }

    private int statusColor(SiteHealthStore.Status status) {
        return switch (status) {
            case GOOD -> R.color.site_health_good;
            case BAD -> R.color.site_health_bad;
            case WARN -> R.color.site_health_warn;
            default -> R.color.black_50;
        };
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    private enum Filter {ALL, BAD, WARN}

    private enum Sort {FAILURES, RECENT, RATE, SAMPLES}
}
