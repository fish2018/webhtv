package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.AiConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class AiCompletionClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int CONNECT_TIMEOUT_SECONDS = 15;
    private static final int READ_TIMEOUT_SECONDS = 60;
    private static final int CALL_TIMEOUT_SECONDS = 75;
    private static final int MAX_OUTPUT_TOKENS = 4096;
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String[] KNOWN_COMPAT_SUFFIXES = {
            "/api/claudecode",
            "/api/anthropic",
            "/apps/anthropic",
            "/api/coding",
            "/claudecode",
            "/anthropic",
            "/step_plan",
            "/coding",
            "/claude"
    };

    private AiCompletionClient() {
    }

    public static TestResult testConfig(AiConfig config) {
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        if (!safe.isReady()) return TestResult.failed("请先启用 AI 服务，并填写端点、API key 和模型。");
        try {
            Request request = buildRequest(requestSpec(safe, "这是 AI 服务连通性测试。请只返回 JSON: {\"ok\":true,\"message\":\"connected\"}"));
            try (Response response = client().newCall(request).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) return TestResult.failed("HTTP " + response.code() + ": " + excerpt(responseBody));
                String text = extractCompletionText(responseBody, safe);
                if (isBlank(text)) return TestResult.failed("接口已响应，但没有解析到 AI 输出。");
                return TestResult.success(1, excerpt(text));
            }
        } catch (Throwable e) {
            return TestResult.failed(e.getMessage());
        }
    }

    public static ModelFetchResult fetchModels(AiConfig config) {
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        if (!safe.isModelFetchReady()) return ModelFetchResult.failed("请先填写端点和 API key。");
        List<String> candidates = buildModelUrlCandidates(safe);
        if (candidates.isEmpty()) return ModelFetchResult.failed("无法从端点推导模型列表地址。");
        String lastError = "";
        for (String url : candidates) {
            try {
                Request.Builder builder = new Request.Builder().url(url).get();
                applyModelFetchHeaders(builder, safe);
                try (Response response = client().newCall(builder.build()).execute()) {
                    String responseBody = response.body() == null ? "" : response.body().string();
                    if (response.isSuccessful()) return ModelFetchResult.success(parseModelList(responseBody, safe));
                    lastError = "HTTP " + response.code() + ": " + excerpt(responseBody);
                    if (response.code() == 404 || response.code() == 405) continue;
                    return ModelFetchResult.failed(lastError);
                }
            } catch (Throwable e) {
                lastError = e.getMessage();
            }
        }
        return ModelFetchResult.failed(isBlank(lastError) ? "模型列表获取失败。" : lastError);
    }

    public static RequestSpec requestSpec(AiConfig config, String prompt) {
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        JsonObject body = new JsonObject();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        switch (safe.getProtocol()) {
            case AiConfig.PROTOCOL_OPENAI_CHAT:
                body.addProperty("model", safe.getModel());
                JsonArray messages = new JsonArray();
                JsonObject message = new JsonObject();
                message.addProperty("role", "user");
                message.addProperty("content", Objects.toString(prompt, ""));
                messages.add(message);
                body.add("messages", messages);
                headers.put("Authorization", "Bearer " + safe.getApiKey());
                break;
            case AiConfig.PROTOCOL_ANTHROPIC_MESSAGES:
                body.addProperty("model", safe.getModel());
                body.addProperty("max_tokens", MAX_OUTPUT_TOKENS);
                JsonArray anthropicMessages = new JsonArray();
                JsonObject anthropicMessage = new JsonObject();
                anthropicMessage.addProperty("role", "user");
                anthropicMessage.addProperty("content", Objects.toString(prompt, ""));
                anthropicMessages.add(anthropicMessage);
                body.add("messages", anthropicMessages);
                headers.put("x-api-key", safe.getApiKey());
                headers.put("anthropic-version", ANTHROPIC_VERSION);
                break;
            case AiConfig.PROTOCOL_GEMINI_NATIVE:
                JsonArray contents = new JsonArray();
                JsonObject content = new JsonObject();
                content.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", Objects.toString(prompt, ""));
                parts.add(part);
                content.add("parts", parts);
                contents.add(content);
                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("maxOutputTokens", MAX_OUTPUT_TOKENS);
                body.add("contents", contents);
                body.add("generationConfig", generationConfig);
                headers.put("x-goog-api-key", safe.getApiKey());
                break;
            case AiConfig.PROTOCOL_OPENAI_RESPONSES:
            default:
                body.addProperty("model", safe.getModel());
                body.addProperty("input", Objects.toString(prompt, ""));
                body.addProperty("max_output_tokens", MAX_OUTPUT_TOKENS);
                headers.put("Authorization", "Bearer " + safe.getApiKey());
                break;
        }
        String userAgent = sanitizeUserAgent(safe.getCustomUserAgent());
        if (!isBlank(userAgent)) headers.put("User-Agent", userAgent);
        return new RequestSpec(buildCompletionUrl(safe), body, headers);
    }

    public static Request buildRequest(RequestSpec spec) {
        Request.Builder builder = new Request.Builder()
                .url(spec.getUrl())
                .post(RequestBody.create(spec.getBody().toString(), JSON));
        for (Map.Entry<String, String> header : spec.getHeaders().entrySet()) {
            if (!isBlank(header.getValue())) builder.header(header.getKey(), header.getValue());
        }
        return builder.build();
    }

    /**
     * 记录 AI 请求日志。委托给 {@link AiDebugLog}，供 service 包外的调用方（如观影报告）复用统一日志埋点。
     * 仅在 SpiderDebug 开启时输出，API key 等敏感头会被脱敏。
     */
    public static void logRequest(String tag, String scene, AiConfig config, RequestSpec spec, String extra) {
        AiDebugLog.request(tag, scene, config, spec, extra);
    }

    /**
     * 记录 AI 响应日志。委托给 {@link AiDebugLog}。
     */
    public static void logResponse(String tag, String scene, int code, long cost, String body, String extra) {
        AiDebugLog.response(tag, scene, code, cost, body, extra);
    }

    /**
     * 记录 AI 异常日志。委托给 {@link AiDebugLog}。
     */
    public static void logError(String tag, String scene, long cost, Throwable error, String extra) {
        AiDebugLog.error(tag, scene, cost, error, extra);
    }

    public static String extractOutputText(String body) {
        if (isBlank(body)) return "";
        try {
            JsonElement element = JsonParser.parseString(body);
            if (element == null || !element.isJsonObject()) return "";
            JsonObject root = element.getAsJsonObject();
            String direct = string(root, "output_text");
            if (!isBlank(direct)) return direct;
            JsonArray output = array(root, "output");
            StringBuilder builder = new StringBuilder();
            for (JsonElement item : output) {
                appendContent(builder, item);
            }
            return builder.toString().trim();
        } catch (Throwable e) {
            return "";
        }
    }

    public static String extractCompletionText(String body, AiConfig config) {
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        String text;
        switch (safe.getProtocol()) {
            case AiConfig.PROTOCOL_OPENAI_CHAT:
                text = extractOpenAiChatText(body);
                break;
            case AiConfig.PROTOCOL_ANTHROPIC_MESSAGES:
                text = extractAnthropicText(body);
                break;
            case AiConfig.PROTOCOL_GEMINI_NATIVE:
                text = extractGeminiText(body);
                break;
            case AiConfig.PROTOCOL_OPENAI_RESPONSES:
            default:
                text = extractOutputText(body);
                break;
        }
        if (!isBlank(text)) return text;
        text = extractOutputText(body);
        if (!isBlank(text)) return text;
        text = extractOpenAiChatText(body);
        if (!isBlank(text)) return text;
        text = extractAnthropicText(body);
        if (!isBlank(text)) return text;
        return extractGeminiText(body);
    }

    static List<String> buildModelUrlCandidates(AiConfig config) {
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        String endpoint = cleanBaseEndpoint(safe.getEndpoint());
        if (AiConfig.PROTOCOL_GEMINI_NATIVE.equals(safe.getProtocol())) return buildGeminiModelUrlCandidates(endpoint);
        return buildOpenAiCompatibleModelUrlCandidates(endpoint, isKnownFullCompletionEndpoint(endpoint));
    }

    static List<ModelInfo> parseModelList(String body, AiConfig config) {
        List<ModelInfo> models = new ArrayList<>();
        if (isBlank(body)) return models;
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        try {
            JsonElement element = JsonParser.parseString(body);
            if (element == null || !element.isJsonObject()) return models;
            JsonObject root = element.getAsJsonObject();
            for (JsonElement item : array(root, "data")) {
                if (!item.isJsonObject()) continue;
                JsonObject object = item.getAsJsonObject();
                addModel(models, firstString(object, "id", "name"), firstString(object, "owned_by", "ownedBy", "owner"));
            }
            for (JsonElement item : array(root, "models")) {
                if (!item.isJsonObject()) continue;
                JsonObject object = item.getAsJsonObject();
                if (AiConfig.PROTOCOL_GEMINI_NATIVE.equals(safe.getProtocol()) && !supportsGeminiGenerateContent(object)) continue;
                String ownedBy = AiConfig.PROTOCOL_GEMINI_NATIVE.equals(safe.getProtocol()) ? "Google" : firstString(object, "owned_by", "ownedBy", "owner");
                addModel(models, stripGeminiModelPrefix(firstString(object, "id", "name", "baseModelId")), ownedBy);
            }
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
        Collections.sort(models, (left, right) -> left.getId().compareToIgnoreCase(right.getId()));
        return models;
    }

    static String sanitizeUserAgent(String userAgent) {
        String value = Objects.toString(userAgent, "").trim();
        if (value.isEmpty()) return "";
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 0x20 && c != '\t') || c == 0x7F) return "";
        }
        return value;
    }

    private static OkHttpClient client() {
        return com.github.catvod.net.OkHttp.client().newBuilder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    private static void applyModelFetchHeaders(Request.Builder builder, AiConfig config) {
        if (AiConfig.PROTOCOL_GEMINI_NATIVE.equals(config.getProtocol())) {
            builder.header("x-goog-api-key", config.getApiKey());
        } else {
            builder.header("Authorization", "Bearer " + config.getApiKey());
            if (AiConfig.PROTOCOL_ANTHROPIC_MESSAGES.equals(config.getProtocol())) {
                builder.header("x-api-key", config.getApiKey());
                builder.header("anthropic-version", ANTHROPIC_VERSION);
            }
        }
        String userAgent = sanitizeUserAgent(config.getCustomUserAgent());
        if (!isBlank(userAgent)) builder.header("User-Agent", userAgent);
    }

    private static String buildCompletionUrl(AiConfig config) {
        String endpoint = cleanBaseEndpoint(config.getEndpoint());
        switch (config.getProtocol()) {
            case AiConfig.PROTOCOL_OPENAI_CHAT:
                if (endpoint.endsWith("/chat/completions")) return endpoint;
                return appendCompletionPath(endpoint, "chat/completions");
            case AiConfig.PROTOCOL_ANTHROPIC_MESSAGES:
                if (endpoint.endsWith("/messages")) return endpoint;
                return appendCompletionPath(endpoint, "messages");
            case AiConfig.PROTOCOL_GEMINI_NATIVE:
                return buildGeminiGenerateContentUrl(endpoint, config.getModel());
            case AiConfig.PROTOCOL_OPENAI_RESPONSES:
            default:
                if (endpoint.endsWith("/responses")) return endpoint;
                return appendCompletionPath(endpoint, "responses");
        }
    }

    private static String appendCompletionPath(String endpoint, String path) {
        if (isBlank(endpoint)) return "";
        if (endsWithVersionSegment(endpoint)) return endpoint + "/" + path;
        return endpoint + "/v1/" + path;
    }

    private static String buildGeminiGenerateContentUrl(String endpoint, String model) {
        if (endpoint.contains(":generateContent")) return endpoint;
        String value = endpoint;
        String modelPath = geminiModelPath(model);
        if (value.endsWith("/models")) return value + "/" + stripGeminiModelPrefix(modelPath) + ":generateContent";
        if (!endsWithGeminiVersionSegment(value)) value = value + "/v1beta";
        return value + "/" + modelPath + ":generateContent";
    }

    private static List<String> buildOpenAiCompatibleModelUrlCandidates(String endpoint, boolean fullUrl) {
        List<String> candidates = new ArrayList<>();
        if (isBlank(endpoint)) return candidates;
        if (fullUrl) {
            int idx = endpoint.indexOf("/v1/");
            if (idx >= 0) candidates.add(endpoint.substring(0, idx) + "/v1/models");
            int lastSlash = endpoint.lastIndexOf('/');
            int schemeEnd = endpoint.indexOf("://") + 3;
            if (candidates.isEmpty() && lastSlash > schemeEnd) candidates.add(endpoint.substring(0, lastSlash) + "/v1/models");
            return unique(candidates);
        }
        if (endsWithVersionSegment(endpoint)) {
            candidates.add(endpoint + "/models");
            if (!endpoint.endsWith("/v1")) candidates.add(endpoint + "/v1/models");
        } else {
            candidates.add(endpoint + "/v1/models");
        }
        String stripped = stripCompatSuffix(endpoint);
        if (!isBlank(stripped) && stripped.contains("://")) {
            candidates.add(stripped + "/v1/models");
            candidates.add(stripped + "/models");
        }
        return unique(candidates);
    }

    private static List<String> buildGeminiModelUrlCandidates(String endpoint) {
        List<String> candidates = new ArrayList<>();
        if (isBlank(endpoint)) return candidates;
        if (endpoint.contains(":generateContent")) {
            int idx = endpoint.indexOf("/models/");
            if (idx >= 0) candidates.add(endpoint.substring(0, idx) + "/models");
        } else if (endpoint.endsWith("/models")) {
            candidates.add(endpoint);
        } else if (endsWithGeminiVersionSegment(endpoint)) {
            candidates.add(endpoint + "/models");
        } else {
            candidates.add(endpoint + "/v1beta/models");
        }
        return unique(candidates);
    }

    private static String extractOpenAiChatText(String body) {
        if (isBlank(body)) return "";
        try {
            JsonElement element = JsonParser.parseString(body);
            if (element == null || !element.isJsonObject()) return "";
            JsonObject root = element.getAsJsonObject();
            StringBuilder builder = new StringBuilder();
            for (JsonElement choice : array(root, "choices")) {
                if (!choice.isJsonObject()) continue;
                JsonObject choiceObject = choice.getAsJsonObject();
                appendContent(builder, object(choiceObject, "message").get("content"));
                appendContent(builder, object(choiceObject, "delta").get("content"));
                appendTextValue(builder, string(choiceObject, "text"));
            }
            return builder.toString().trim();
        } catch (Throwable e) {
            return "";
        }
    }

    private static String extractAnthropicText(String body) {
        if (isBlank(body)) return "";
        try {
            JsonElement element = JsonParser.parseString(body);
            if (element == null || !element.isJsonObject()) return "";
            JsonObject root = element.getAsJsonObject();
            StringBuilder builder = new StringBuilder();
            appendTextValue(builder, string(root, "completion"));
            for (JsonElement content : array(root, "content")) {
                if (content.isJsonObject()) appendText(builder, content.getAsJsonObject());
                else if (content.isJsonPrimitive()) appendTextValue(builder, content.getAsString());
            }
            return builder.toString().trim();
        } catch (Throwable e) {
            return "";
        }
    }

    private static String extractGeminiText(String body) {
        if (isBlank(body)) return "";
        try {
            JsonElement element = JsonParser.parseString(body);
            if (element == null || !element.isJsonObject()) return "";
            JsonObject root = element.getAsJsonObject();
            StringBuilder builder = new StringBuilder();
            appendTextValue(builder, string(root, "text"));
            for (JsonElement candidate : array(root, "candidates")) {
                if (!candidate.isJsonObject()) continue;
                JsonObject content = object(candidate.getAsJsonObject(), "content");
                for (JsonElement part : array(content, "parts")) {
                    if (part.isJsonObject()) appendText(builder, part.getAsJsonObject());
                    else if (part.isJsonPrimitive()) appendTextValue(builder, part.getAsString());
                }
            }
            return builder.toString().trim();
        } catch (Throwable e) {
            return "";
        }
    }

    private static void appendText(StringBuilder builder, JsonObject object) {
        String text = string(object, "text");
        if (isBlank(text)) text = string(object, "output_text");
        if (isBlank(text)) text = string(object, "value");
        if (isBlank(text)) text = string(object, "arguments");
        appendTextValue(builder, text);
        appendStructuredValue(builder, object, "text");
        appendStructuredValue(builder, object, "output_text");
        appendContent(builder, object == null ? null : object.get("content"));
        appendContent(builder, object == null ? null : object.get("parts"));
    }

    private static void appendContent(StringBuilder builder, JsonElement content) {
        if (content == null || content.isJsonNull()) return;
        if (content.isJsonPrimitive()) {
            appendTextValue(builder, content.getAsString());
            return;
        }
        if (content.isJsonObject()) {
            appendText(builder, content.getAsJsonObject());
            return;
        }
        if (!content.isJsonArray()) return;
        for (JsonElement item : content.getAsJsonArray()) {
            if (item.isJsonObject()) appendText(builder, item.getAsJsonObject());
            else if (item.isJsonPrimitive()) appendTextValue(builder, item.getAsString());
        }
    }

    private static void appendStructuredValue(StringBuilder builder, JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonPrimitive()) return;
        appendContent(builder, object.get(key));
    }

    private static void appendTextValue(StringBuilder builder, String text) {
        if (isBlank(text)) return;
        if (builder.length() > 0) builder.append('\n');
        builder.append(text.trim());
    }

    private static JsonObject object(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) return "";
        return Objects.toString(object.get(key).getAsString(), "").trim();
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = string(object, key);
            if (!isBlank(value)) return value;
        }
        return "";
    }

    private static void addModel(List<ModelInfo> models, String id, String ownedBy) {
        String value = Objects.toString(id, "").trim();
        if (isBlank(value)) return;
        for (ModelInfo model : models) if (model.getId().equals(value)) return;
        models.add(new ModelInfo(value, ownedBy));
    }

    private static boolean supportsGeminiGenerateContent(JsonObject object) {
        JsonArray methods = array(object, "supportedGenerationMethods");
        if (methods.size() == 0) return true;
        for (JsonElement method : methods) {
            if (method.isJsonPrimitive() && "generateContent".equals(method.getAsString())) return true;
        }
        return false;
    }

    private static String geminiModelPath(String model) {
        String value = Objects.toString(model, "").trim();
        if (value.startsWith("models/") || value.startsWith("publishers/")) return value;
        return "models/" + stripGeminiModelPrefix(value);
    }

    private static String stripGeminiModelPrefix(String model) {
        String value = Objects.toString(model, "").trim();
        return value.startsWith("models/") ? value.substring("models/".length()) : value;
    }

    private static String cleanBaseEndpoint(String endpoint) {
        String value = Objects.toString(endpoint, "").trim();
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static boolean isKnownFullCompletionEndpoint(String endpoint) {
        return endpoint.endsWith("/responses")
                || endpoint.endsWith("/chat/completions")
                || endpoint.endsWith("/messages")
                || endpoint.contains(":generateContent");
    }

    private static boolean endsWithVersionSegment(String url) {
        String last = url == null ? "" : url.substring(url.lastIndexOf('/') + 1);
        if (last.length() < 2 || last.charAt(0) != 'v') return false;
        for (int i = 1; i < last.length(); i++) if (!Character.isDigit(last.charAt(i))) return false;
        return true;
    }

    private static boolean endsWithGeminiVersionSegment(String url) {
        String last = url == null ? "" : url.substring(url.lastIndexOf('/') + 1);
        if (last.length() < 2 || last.charAt(0) != 'v') return false;
        int i = 1;
        while (i < last.length() && Character.isDigit(last.charAt(i))) i++;
        if (i == 1) return false;
        String suffix = last.substring(i);
        return suffix.isEmpty() || "beta".equals(suffix) || "alpha".equals(suffix);
    }

    private static String stripCompatSuffix(String endpoint) {
        for (String suffix : KNOWN_COMPAT_SUFFIXES) {
            if (endpoint.endsWith(suffix)) return endpoint.substring(0, endpoint.length() - suffix.length());
        }
        return "";
    }

    private static List<String> unique(List<String> values) {
        List<String> unique = new ArrayList<>();
        for (String value : values) {
            if (isBlank(value) || unique.contains(value)) continue;
            unique.add(value);
        }
        return unique;
    }

    private static String excerpt(String text) {
        String value = Objects.toString(text, "").replace('\n', ' ').trim();
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    public static final class RequestSpec {

        private final String url;
        private final JsonObject body;
        private final Map<String, String> headers;

        private RequestSpec(String url, JsonObject body, Map<String, String> headers) {
            this.url = url == null ? "" : url;
            this.body = body == null ? new JsonObject() : body;
            this.headers = headers == null ? new LinkedHashMap<>() : headers;
        }

        public String getUrl() {
            return url;
        }

        public JsonObject getBody() {
            return body;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }
    }

    public static final class ModelInfo {

        private final String id;
        private final String ownedBy;

        private ModelInfo(String id, String ownedBy) {
            this.id = id == null ? "" : id.trim();
            this.ownedBy = ownedBy == null ? "" : ownedBy.trim();
        }

        public String getId() {
            return id;
        }

        public String getOwnedBy() {
            return ownedBy;
        }
    }

    public static final class ModelFetchResult {

        private final boolean success;
        private final String message;
        private final List<ModelInfo> models;

        private ModelFetchResult(boolean success, String message, List<ModelInfo> models) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.models = models == null ? new ArrayList<>() : models;
        }

        static ModelFetchResult success(List<ModelInfo> models) {
            return new ModelFetchResult(true, "", models);
        }

        static ModelFetchResult failed(String message) {
            return new ModelFetchResult(false, message, new ArrayList<>());
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public List<ModelInfo> getModels() {
            return models;
        }
    }

    public static final class TestResult {

        private final boolean success;
        private final String message;
        private final int count;
        private final String sampleTitle;

        private TestResult(boolean success, String message, int count, String sampleTitle) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.count = count;
            this.sampleTitle = sampleTitle == null ? "" : sampleTitle;
        }

        static TestResult success(int count, String sampleTitle) {
            return new TestResult(true, "", count, sampleTitle);
        }

        static TestResult failed(String message) {
            return new TestResult(false, message, 0, "");
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public int getCount() {
            return count;
        }

        public String getSampleTitle() {
            return sampleTitle;
        }
    }
}
