package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityViewingReportBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.viewing.ViewingReport;
import com.fongmi.android.tv.viewing.ViewingReportAiAnalyzer;
import com.fongmi.android.tv.viewing.ViewingReportCache;
import com.fongmi.android.tv.viewing.ViewingReportGenerator;
import com.fongmi.android.tv.viewing.ViewingReportRange;
import com.google.android.material.chip.Chip;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ViewingReportActivity extends BaseActivity {

    private ActivityViewingReportBinding binding;
    private ExecutorService executor;
    private ViewingReportRange range;

    public static void start(Activity activity, ViewingReportRange range) {
        Intent intent = new Intent(activity, ViewingReportActivity.class);
        intent.putExtra("range", range.name());
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = ActivityViewingReportBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        String rangeName = getIntent().getStringExtra("range");
        range = ViewingReportRange.valueOf(rangeName == null ? "ALL" : rangeName);
        binding.reportTitle.setText(R.string.viewing_report_title);
        binding.reportRange.setText(range.getDisplayLabel());
        binding.refreshButton.setOnClickListener(v -> onRefresh());
        executor = Executors.newSingleThreadExecutor();
        loadReport(false);
    }

    private void onRefresh() {
        binding.refreshButton.setEnabled(false);
        loadReport(true);
    }

    private void loadReport(boolean forceRefresh) {
        showProgress(getString(R.string.viewing_report_loading_local));
        executor.execute(() -> {
            if (forceRefresh) ViewingReportCache.clear(range);
            else {
                ViewingReport cached = ViewingReportCache.read(range);
                if (cached != null && !cached.isEmpty()) {
                    App.post(() -> display(cached));
                    return;
                }
            }
            App.post(() -> showProgress(getString(R.string.viewing_report_generating)));
            ViewingReport report = new ViewingReportGenerator().generate(range);
            if (report.isEmpty()) {
                App.post(() -> showEmpty());
                return;
            }
            App.post(() -> showProgress(getString(R.string.viewing_report_ai_analyzing)));
            ViewingReportAiAnalyzer analyzer = new ViewingReportAiAnalyzer();
            if (analyzer.isReady()) analyzer.analyze(report);
            ViewingReportCache.write(report);
            App.post(() -> display(report));
        });
    }

    private void showProgress(String text) {
        binding.progressText.setText(text);
        binding.progressBox.setVisibility(View.VISIBLE);
        binding.emptyText.setVisibility(View.GONE);
        binding.content.setVisibility(View.GONE);
    }

    private void showEmpty() {
        binding.refreshButton.setEnabled(true);
        binding.emptyText.setText(R.string.viewing_report_empty);
        binding.emptyText.setVisibility(View.VISIBLE);
        binding.progressBox.setVisibility(View.GONE);
        binding.content.setVisibility(View.GONE);
    }

    private void display(ViewingReport report) {
        binding.refreshButton.setEnabled(true);
        binding.progressBox.setVisibility(View.GONE);
        binding.emptyText.setVisibility(View.GONE);
        binding.content.setVisibility(View.VISIBLE);
        fillOverview(report);
        fillTimePattern(report);
        fillPreference(report);
        fillPeople(report);
        fillAi(report);
        fillBadges(report);
    }

    private void fillOverview(ViewingReport report) {
        double hours = report.getTotalWatchMinutes() / 60.0;
        binding.overviewHours.setText(String.format(Locale.getDefault(), "%.1f %s", hours, getString(R.string.viewing_report_unit_hours)));
        String sub = String.format(Locale.getDefault(),
                "📺 %s %d %s · %d %s\n🏆 %s %.0f%%",
                getString(R.string.viewing_report_watched),
                report.getTotalVodCount(),
                getString(R.string.viewing_report_unit_vods),
                report.getTotalEpisodeCount(),
                getString(R.string.viewing_report_unit_episodes),
                getString(R.string.viewing_report_completion_rate),
                report.getCompletionRate() * 100);
        binding.overviewSub.setText(sub);
    }

    private void fillTimePattern(ViewingReport report) {
        StringBuilder sb = new StringBuilder();
        if (report.getTopTimeSlot() != null) {
            sb.append(getString(R.string.viewing_report_top_time_slot)).append(": ")
                    .append(report.getTopTimeSlot().getLabel()).append("\n");
        }
        sb.append(getString(R.string.viewing_report_weekend_ratio)).append(": ")
                .append(Math.round(report.getWeekendRatio() * 100)).append("%\n");
        sb.append(getString(R.string.viewing_report_avg_watch)).append(": ")
                .append(String.format(Locale.getDefault(), "%.1f", report.getAverageWatchMinutes()))
                .append(" ").append(getString(R.string.viewing_report_unit_minutes)).append("\n");
        sb.append(getString(R.string.viewing_report_late_night)).append(": ")
                .append(report.getLateNightCount()).append(" ").append(getString(R.string.viewing_report_unit_times));
        binding.timeContent.setText(sb.toString());
    }

    private void fillPreference(ViewingReport report) {
        if (report.getTopGenres().isEmpty() && report.getTvRatio() == 0 && report.getMovieRatio() == 0) {
            binding.genreCard.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, report.getTopGenres().size()); i++) {
            ViewingReport.CountStat stat = report.getTopGenres().get(i);
            if (i > 0) sb.append("\n");
            double percent = (double) stat.getCount() / report.getTotalVodCount() * 100;
            sb.append(emojiFor(stat.getName())).append(" ").append(stat.getName())
                    .append(" ").append(Math.round(percent)).append("%");
        }
        if (report.getTvRatio() > 0 || report.getMovieRatio() > 0) {
            sb.append("\n\n").append(getString(R.string.viewing_report_tv_movie_ratio)).append(": ")
                    .append(Math.round(report.getTvRatio() * 100)).append("% / ")
                    .append(Math.round(report.getMovieRatio() * 100)).append("%");
        }
        if (report.getCompletionRate() > 0) {
            sb.append("\n").append(getString(R.string.viewing_report_completion_rate)).append(": ")
                    .append(Math.round(report.getCompletionRate() * 100)).append("%");
        }
        binding.genreContent.setText(sb.toString());
    }

    private void fillPeople(ViewingReport report) {
        if (report.getTopActors().isEmpty() && report.getTopDirectors().isEmpty()) {
            binding.peopleCard.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (!report.getTopActors().isEmpty()) {
            sb.append("👤 ").append(getString(R.string.viewing_report_top_actors)).append(":\n");
            for (int i = 0; i < Math.min(5, report.getTopActors().size()); i++) {
                ViewingReport.CountStat stat = report.getTopActors().get(i);
                if (i > 0) sb.append("\n");
                sb.append("  ").append(stat.getName()).append(" ×").append(stat.getCount());
            }
        }
        if (!report.getTopDirectors().isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("🎬 ").append(getString(R.string.viewing_report_top_directors)).append(":\n");
            for (int i = 0; i < Math.min(3, report.getTopDirectors().size()); i++) {
                ViewingReport.CountStat stat = report.getTopDirectors().get(i);
                if (i > 0) sb.append("\n");
                sb.append("  ").append(stat.getName()).append(" ×").append(stat.getCount());
            }
        }
        binding.peopleContent.setText(sb.toString());
    }

    private void fillAi(ViewingReport report) {
        if (!report.isAiAnalyzed() || TextUtils.isEmpty(report.getAiSummary())) {
            binding.aiCard.setVisibility(View.GONE);
            return;
        }
        binding.aiCard.setVisibility(View.VISIBLE);
        binding.aiSummary.setText(report.getAiSummary());
        binding.tagGroup.removeAllViews();
        for (String tag : report.getStyleTags()) {
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setClickable(false);
            chip.setCheckable(false);
            binding.tagGroup.addView(chip);
        }
        if (!report.getInsights().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < report.getInsights().size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append("• ").append(report.getInsights().get(i));
            }
            binding.aiInsights.setText(sb.toString());
            binding.aiInsights.setVisibility(View.VISIBLE);
        } else {
            binding.aiInsights.setVisibility(View.GONE);
        }
    }

    private void fillBadges(ViewingReport report) {
        if (report.getBadges().isEmpty()) {
            binding.badgeCard.setVisibility(View.GONE);
            return;
        }
        binding.badgeCard.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < report.getBadges().size(); i++) {
            ViewingReport.Badge badge = report.getBadges().get(i);
            if (i > 0) sb.append("\n\n");
            sb.append(badge.getIcon()).append(" ").append(badge.getName());
            if (!TextUtils.isEmpty(badge.getDescription())) {
                sb.append("\n  ").append(badge.getDescription());
            }
        }
        binding.badgeContent.setText(sb.toString());
    }

    private String emojiFor(String genre) {
        if (genre.contains("悬疑") || genre.contains("推理") || genre.contains("犯罪")) return "🔍";
        if (genre.contains("爱情") || genre.contains("浪漫")) return "💕";
        if (genre.contains("动作") || genre.contains("冒险")) return "🎬";
        if (genre.contains("喜剧")) return "😄";
        if (genre.contains("科幻")) return "🚀";
        if (genre.contains("恐怖") || genre.contains("惊悚")) return "😱";
        if (genre.contains("剧情")) return "🎭";
        if (genre.contains("动画")) return "🎨";
        if (genre.contains("纪录")) return "📽️";
        return "🎬";
    }

    @Override
    protected void onDestroy() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }
}
