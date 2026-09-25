package com.fongmi.android.tv.service;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.AdDetectionRequest;
import com.fongmi.android.tv.bean.AdDetectionResult;
import com.fongmi.android.tv.bean.AiConfig;
import com.github.catvod.net.OkHttp;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * AI 广告检测服务
 * 调用 AI 分析播放上下文，输出去广规则建议
 */
public final class AiAdDetectionService {

    private static final int CONNECT_TIMEOUT_SECONDS = 15;
    private static final int READ_TIMEOUT_SECONDS = 60;
    private static final int CALL_TIMEOUT_SECONDS = 75;

    private final AiConfig config;

    public AiAdDetectionService(AiConfig config) {
        this.config = config == null ? new AiConfig().sanitize() : config.sanitize();
    }

    public AdDetectionResult analyze(AdDetectionRequest request) {
        if (!config.isReady()) {
            return AdDetectionResult.error("请先在 AI 配置中启用服务，并填写端点、API key 和模型。");
        }
        if (request == null) {
            return AdDetectionResult.error("请求参数为空");
        }
        try {
            String prompt = buildPrompt(request);
            AiCompletionClient.RequestSpec spec = AiCompletionClient.requestSpec(config, prompt);
            AiDebugLog.request("ai-ad-detection", "analyze", config, spec, "site=" + request.getSiteKey());
            Request httpRequest = AiCompletionClient.buildRequest(spec);
            long start = System.currentTimeMillis();
            try (Response response = client().newCall(httpRequest).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                long cost = System.currentTimeMillis() - start;
                AiDebugLog.response("ai-ad-detection", "analyze", response.code(), cost, body, "success=" + response.isSuccessful());
                if (!response.isSuccessful()) {
                    return AdDetectionResult.error("AI 分析失败：HTTP " + response.code());
                }
                String text = AiCompletionClient.extractCompletionText(body, config);
                return parseResult(text);
            }
        } catch (Throwable e) {
            AiDebugLog.error("ai-ad-detection", "analyze", 0, e, "");
            return AdDetectionResult.error("AI 分析失败：" + e.getMessage());
        }
    }

    private String buildPrompt(AdDetectionRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append(config.getAdDetectionPrompt().trim()).append("\n\n");
        sb.append("输入数据：\n");
        sb.append("- 站点：").append(Objects.toString(req.getSiteKey(), "")).append(" (").append(Objects.toString(req.getSiteName(), "")).append(")\n");
        sb.append("- 剧名：").append(Objects.toString(req.getVodName(), "")).append("\n");
        sb.append("- 线路：").append(Objects.toString(req.getFlagName(), "")).append("\n");
        sb.append("- 集名：").append(Objects.toString(req.getEpisodeName(), "")).append("\n");
        sb.append("- 主内容域名：").append(Objects.toString(req.getUrlHost(), "")).append("\n");
        sb.append("- 主内容路径：").append(Objects.toString(req.getUrlPath(), "")).append("\n");
        sb.append("\nm3u8 切片证据：\n");
        if (req.getEvidence() != null && !req.getEvidence().isEmpty()) {
            sb.append(req.getEvidence().toSummary());
        } else {
            sb.append("（未能解析到 m3u8 切片，仅凭 URL 特征分析，请谨慎判断，无明显证据时 confidence 应偏低）");
        }
        return sb.toString();
    }

    private AdDetectionResult parseResult(String text) {
        try {
            String json = extractJson(text);
            if (TextUtils.isEmpty(json)) {
                return AdDetectionResult.error("AI 输出为空或格式错误");
            }
            return App.gson().fromJson(json, AdDetectionResult.class);
        } catch (Throwable e) {
            return AdDetectionResult.error("AI 输出格式错误：" + e.getMessage());
        }
    }

    /**
     * 从 AI 输出中提取 JSON（去除 markdown 代码块、前后缀文字）
     * 复用 AiRecommendationService 的逻辑
     */
    private static String extractJson(String text) {
        String value = Objects.toString(text, "").trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        if (value.startsWith("{") || value.startsWith("[")) return value;
        int objectStart = value.indexOf('{');
        int arrayStart = value.indexOf('[');
        if (objectStart < 0 && arrayStart < 0) return "";
        boolean useArray = arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart);
        int start = useArray ? arrayStart : objectStart;
        int end = useArray ? value.lastIndexOf(']') : value.lastIndexOf('}');
        return end > start ? value.substring(start, end + 1) : "";
    }

    private static OkHttpClient client() {
        return OkHttp.client().newBuilder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }
}
