package com.fongmi.android.tv.viewing;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.service.AiCompletionClient;
import com.fongmi.android.tv.setting.Setting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 观影报告 AI 深度分析层。
 * <p>
 * 在本地统计结果之上，调用可配置的 AI 服务生成个性化画像：观影人格、风格标签、
 * 题材洞察、成就徽章与亮点。AI 未配置或调用失败时静默降级，不影响本地报告展示。
 */
public class ViewingReportAiAnalyzer {

    private static final int MAX_ACTORS = 12;
    private static final int MAX_GENRES = 10;

    private final AiConfig config;

    public ViewingReportAiAnalyzer() {
        this(AiConfig.objectFrom(Setting.getAiConfig()));
    }

    public ViewingReportAiAnalyzer(AiConfig config) {
        this.config = config == null ? new AiConfig().sanitize() : config.sanitize();
    }

    public boolean isReady() {
        return config.isReady();
    }

    /**
     * 对报告执行 AI 深度分析并就地填充 AI 字段。返回是否成功。
     * 该方法为同步网络调用，必须在子线程执行。
     */
    public boolean analyze(ViewingReport report) {
        if (report == null || report.isEmpty()) return false;
        if (!config.isReady()) return false;
        long start = System.currentTimeMillis();
        try {
            String prompt = buildPrompt(report);
            AiCompletionClient.RequestSpec spec = AiCompletionClient.requestSpec(config, prompt);
            AiCompletionClient.logRequest("ai-report", "viewing-report", config, spec, "range=" + (report.getRange() == null ? "ALL" : report.getRange().name()));
            Request request = AiCompletionClient.buildRequest(spec);
            try (Response response = client().newCall(request).execute()) {
                long cost = System.currentTimeMillis() - start;
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    AiCompletionClient.logResponse("ai-report", "viewing-report", response.code(), cost, body, "success=false");
                    return false;
                }
                String text = AiCompletionClient.extractCompletionText(body, config);
                if (TextUtils.isEmpty(text)) {
                    AiCompletionClient.logResponse("ai-report", "viewing-report", response.code(), cost, body, "extracted=empty");
                    return false;
                }
                boolean applied = apply(report, text);
                AiCompletionClient.logResponse("ai-report", "viewing-report", response.code(), cost, body, "applied=" + applied);
                return applied;
            }
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - start;
            AiCompletionClient.logError("ai-report", "viewing-report", cost, e, "range=" + (report.getRange() == null ? "ALL" : report.getRange().name()));
            return false;
        }
    }

    private okhttp3.OkHttpClient client() {
        return com.github.catvod.net.OkHttp.client();
    }

    // ---- prompt 构造 ----

    private String buildPrompt(ViewingReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(config.getViewingReportPrompt()).append("\n\n");
        sb.append("【观影数据】\n");
        sb.append("统计范围: ").append(report.getRange() == null ? "全部" : report.getRange().getDisplayLabel()).append("\n");
        sb.append("总观看时长: ").append(report.getTotalWatchMinutes()).append(" 分钟\n");
        sb.append("观看作品数: ").append(report.getTotalVodCount()).append(" 部\n");
        sb.append("观看记录数: ").append(report.getTotalEpisodeCount()).append(" 条\n");
        sb.append("平均单条时长: ").append(String.format(Locale.ROOT, "%.1f", report.getAverageWatchMinutes())).append(" 分钟\n");
        sb.append("完播率: ").append(Math.round(report.getCompletionRate() * 100)).append("%\n");
        sb.append("周末观看占比: ").append(Math.round(report.getWeekendRatio() * 100)).append("%\n");
        sb.append("深夜观看次数: ").append(report.getLateNightCount()).append("\n");
        if (report.getTopTimeSlot() != null) sb.append("最活跃时段: ").append(report.getTopTimeSlot().getLabel()).append("\n");
        sb.append("时段分布: ").append(slotText(report)).append("\n");
        sb.append("剧集/电影比例: ").append(Math.round(report.getTvRatio() * 100)).append("% / ").append(Math.round(report.getMovieRatio() * 100)).append("%\n");
        appendStats(sb, "题材偏好", report.getTopGenres(), MAX_GENRES);
        appendStats(sb, "地区偏好", report.getTopAreas(), MAX_GENRES);
        appendStats(sb, "常看演员", report.getTopActors(), MAX_ACTORS);
        appendStats(sb, "常看导演", report.getTopDirectors(), MAX_GENRES);
        appendBadges(sb, report);
        return sb.toString();
    }

    private String slotText(ViewingReport report) {
        Map<ViewingReport.TimeSlot, Integer> dist = report.getTimeSlotDistribution();
        if (dist == null || dist.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ViewingReport.TimeSlot, Integer> e : dist.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append("、");
            sb.append(e.getKey().getLabel()).append(e.getValue());
        }
        return sb.length() == 0 ? "无" : sb.toString();
    }

    private void appendStats(StringBuilder sb, String label, List<ViewingReport.CountStat> stats, int limit) {
        if (stats == null || stats.isEmpty()) return;
        sb.append(label).append(": ");
        int count = 0;
        for (ViewingReport.CountStat stat : stats) {
            if (count >= limit) break;
            if (count > 0) sb.append("、");
            sb.append(stat.getName()).append("(").append(stat.getCount()).append(")");
            count++;
        }
        sb.append("\n");
    }

    private void appendBadges(StringBuilder sb, ViewingReport report) {
        if (report.getBadges() == null || report.getBadges().isEmpty()) return;
        sb.append("已获本地徽章: ");
        int count = 0;
        for (ViewingReport.Badge badge : report.getBadges()) {
            if (count > 0) sb.append("、");
            sb.append(badge.getName());
            count++;
        }
        sb.append("\n");
    }

    // ---- 结果解析 ----

    private boolean apply(ViewingReport report, String text) {
        JsonObject root = parseJson(text);
        if (root == null) return false;
        boolean applied = false;

        String summary = string(root, "summary");
        if (!TextUtils.isEmpty(summary)) {
            report.setAiSummary(summary);
            applied = true;
        }

        String persona = string(root, "persona");
        if (TextUtils.isEmpty(persona)) persona = string(root, "aiPersona");
        if (!TextUtils.isEmpty(persona)) report.setAiPersona(persona);

        List<String> tags = stringArray(root, "tags");
        if (!tags.isEmpty()) {
            report.getStyleTags().clear();
            report.getStyleTags().addAll(tags);
            applied = true;
        }

        List<String> insights = new ArrayList<>();
        String genreInsights = string(root, "genreInsights");
        if (!TextUtils.isEmpty(genreInsights)) insights.add(genreInsights);
        insights.addAll(stringArray(root, "highlights"));
        String hint = string(root, "recommendationHint");
        if (!TextUtils.isEmpty(hint)) insights.add(hint);
        if (!insights.isEmpty()) {
            report.getInsights().clear();
            report.getInsights().addAll(insights);
            applied = true;
        }

        mergeBadges(report, root);

        if (applied) report.setAiAnalyzed(true);
        return applied;
    }

    private void mergeBadges(ViewingReport report, JsonObject root) {
        if (!root.has("badges") || !root.get("badges").isJsonArray()) return;
        JsonArray array = root.getAsJsonArray("badges");
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject badge = element.getAsJsonObject();
            String id = string(badge, "id");
            String name = string(badge, "name");
            String reason = string(badge, "reason");
            if (TextUtils.isEmpty(name)) continue;
            if (hasBadge(report, id, name)) continue;
            report.getBadges().add(new ViewingReport.Badge(
                    TextUtils.isEmpty(id) ? "ai_" + name : id, name, reason, iconFor(id)));
        }
    }

    private boolean hasBadge(ViewingReport report, String id, String name) {
        for (ViewingReport.Badge badge : report.getBadges()) {
            if (!TextUtils.isEmpty(id) && id.equals(badge.getId())) return true;
            if (name.equals(badge.getName())) return true;
        }
        return false;
    }

    private String iconFor(String id) {
        if (id == null) return "🏅";
        switch (id) {
            case "drama_king": return "📺";
            case "night_owl": return "🌙";
            case "indie_explorer": return "🔍";
            case "marathon": return "🏃";
            case "loyal_fan": return "❤️";
            case "globe_trotter": return "🌏";
            case "early_bird": return "🌅";
            default: return "🏅";
        }
    }

    private JsonObject parseJson(String text) {
        String cleaned = stripCodeFence(text);
        try {
            JsonElement element = JsonParser.parseString(cleaned);
            if (element != null && element.isJsonObject()) return element.getAsJsonObject();
        } catch (Throwable ignored) {
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JsonElement element = JsonParser.parseString(cleaned.substring(start, end + 1));
                if (element != null && element.isJsonObject()) return element.getAsJsonObject();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private String stripCodeFence(String text) {
        String value = text.trim();
        if (value.startsWith("```")) {
            int firstBreak = value.indexOf('\n');
            if (firstBreak >= 0) value = value.substring(firstBreak + 1);
            if (value.endsWith("```")) value = value.substring(0, value.length() - 3);
        }
        return value.trim();
    }

    private String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) return "";
        return Objects.toString(object.get(key).getAsString(), "").trim();
    }

    private List<String> stringArray(JsonObject object, String key) {
        List<String> result = new ArrayList<>();
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) return result;
        for (JsonElement element : object.getAsJsonArray(key)) {
            if (element.isJsonPrimitive()) {
                String value = Objects.toString(element.getAsString(), "").trim();
                if (!TextUtils.isEmpty(value)) result.add(value);
            }
        }
        return result;
    }
}
