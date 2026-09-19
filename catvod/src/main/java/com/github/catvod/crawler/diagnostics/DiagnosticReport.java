package com.github.catvod.crawler.diagnostics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Offline evidence reduction over the immutable exported bytes, never over player objects. */
public final class DiagnosticReport {
    private static final Pattern EVENT = Pattern.compile("^(?:# restored-history )?\\d{4}-\\d{2}-\\d{2} [^\\[]+\\[[^\\]]*] av-diag: (\\{.*)$");
    private static final Set<String> FACTS = Set.of("decoderName", "mime", "width", "height", "sampleRate", "channels", "channelMask",
            "encoding", "routeType", "routeRole", "volume", "systemVolume", "muted", "focusResult", "focusChange", "effectiveGain",
            "stage", "nativeHook", "symptom", "presentation", "videoSelected", "audioSelected", "protectedMedia", "parameter",
            "oldValue", "newValue", "note", "source", "backend", "exitReason", "traceAvailable", "lastError");
    private DiagnosticReport() {}

    public static JsonObject parseEvent(String line) {
        if (line == null || line.length() > 64_000) return null;
        java.util.regex.Matcher matcher = EVENT.matcher(line);
        if (!matcher.matches()) return null;
        try {
            JsonObject event = JsonParser.parseString(matcher.group(1)).getAsJsonObject();
            return event.has("schemaVersion") && event.has("processRunId") && event.has("seq") && event.has("event") ? event : null;
        } catch (RuntimeException ignored) { return null; }
    }

    public static final class Analysis {
        private final LinkedHashMap<String, Session> sessions = new LinkedHashMap<>();
        private JsonObject health = new JsonObject();
        private boolean partial;
        private long events;
        private Analysis(boolean partial) { this.partial = partial; }
        public boolean partial() { return partial; }
        public long events() { return events; }
        public JsonObject snapshots() {
            JsonObject result = new JsonObject(); JsonArray values = new JsonArray();
            for (Session session : sessions.values()) values.add(session.json());
            result.add("sessions", values); result.add("health", health); result.addProperty("reducerBounded", partial); return result;
        }
        public String text() {
            StringBuilder out = new StringBuilder("WebHTV 音视频诊断报告\n");
            out.append("完整性：").append(partial ? "partial（存在丢失、截断或观测缺口）" : "仅声明的采集范围/留存窗口；逐层可用性见下文").append('\n');
            out.append("本报告只使用导出文件内的证据。READY、首帧回调、写入成功均不证明实际屏幕/扬声器正常。\n");
            out.append("结构化事件行数（含固定快照重复项）：").append(events).append("\n\n");
            if (sessions.isEmpty()) out.append("没有可关联的播放事件。下一步：开启诊断后原样复现一次并标记症状。\n");
            for (Session session : sessions.values()) {
                out.append("播放：").append(session.trace).append(" / ").append(session.instance).append(" / attempt ").append(session.attempt).append('\n');
                out.append("进程：").append(session.run).append("；内核：").append(session.engine).append('\n');
                out.append("视频最后已证实边界：").append(boundary(session.video, true)).append("；证据 ").append(session.videoId).append('\n');
                out.append("音频最后已证实边界：").append(boundary(session.audio, false)).append("；证据 ").append(session.audioId).append('\n');
                for (Map.Entry<String, JsonObject> entry : session.facts.entrySet())
                    out.append(entry.getKey()).append("：").append(entry.getValue()).append('\n');
                out.append("失败证据：").append(session.errors.isEmpty() ? "未记录到（不代表无故障）" : session.errors).append('\n');
                out.append("缺少证据：").append(session.video < 0 ? "视频输出未观测；" : "")
                        .append(session.audio < 0 ? "音频输出未观测；" : "").append("物理显示/实际可闻始终不可直接观测。\n");
                out.append("下一步：").append(session.errors.isEmpty() ? "在原播放参数下标记故障；需要区分内容与输出时主动开启一次限时统计。"
                        : "围绕上述最早失败操作，仅改变一个相关参数，分别下载修改前后的日志进行比较。").append("\n\n");
            }
            return out.toString();
        }
        private void accept(JsonObject event) {
            events++;
            String trace = string(event, "trace", "none"), instance = string(event, "playerInstanceId", "unknown"), run = string(event, "processRunId", "unknown");
            long attempt = number(event, "attemptId", 0), seq = number(event, "seq", 0);
            if (attempt <= 0 && "none".equals(trace)) return;
            String key = run + "/" + number(event, "captureGeneration", 0) + "/" + trace + "/" + instance + "/" + number(event, "mediaGeneration", 0) + "/" + attempt;
            Session session = sessions.get(key);
            if (session == null) {
                if (sessions.size() >= 16) { sessions.remove(sessions.keySet().iterator().next()); partial = true; }
                session = new Session(run, trace, instance, attempt, string(event, "engine", "unknown")); sessions.put(key, session);
            }
            session.accept(event, seq);
        }
    }

    private static final class Session {
        final String run, trace, instance, engine;
        final long attempt;
        final LinkedHashMap<String, JsonObject> facts = new LinkedHashMap<>();
        final LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        int video = -1, audio = -1;
        String videoId = "none", audioId = "none";
        Session(String run, String trace, String instance, long attempt, String engine) {
            this.run = run; this.trace = trace; this.instance = instance; this.attempt = attempt; this.engine = engine;
        }
        void accept(JsonObject event, long seq) {
            String name = string(event, "event", "unknown"), id = run + ":" + number(event, "captureGeneration", 0) + ":" + seq;
            JsonObject observed = object(event, "observed");
            for (String key : FACTS) if (observed.has(key)) {
                JsonObject prior = facts.get(key);
                if (prior != null && number(prior, "seq", 0) > seq) continue;
                JsonObject fact = new JsonObject(); fact.addProperty("seq", seq); fact.addProperty("evidenceClass", string(event, "evidenceClass", "unknown"));
                fact.add("fact", observed.get(key).deepCopy()); facts.put(key, fact);
            }
            int v = (int) factNumber(observed, "videoEvidenceLevel", -1), a = (int) factNumber(observed, "audioEvidenceLevel", -1);
            if (name.equals("video.sample.summary") && factNumber(observed, "inputBuffers", 0) > 0) v = Math.max(v, 1);
            if (name.equals("video.output.summary")) {
                if (factNumber(observed, "outputBuffers", 0) > 0) v = Math.max(v, 2);
                if (factNumber(observed, "submittedBuffers", 0) > 0) v = Math.max(v, 3);
            }
            if (name.equals("video.surface-render-callback") && factNumber(observed, "renderCallbacks", 0) > 0) v = Math.max(v, 4);
            if (name.equals("audio.decoder.output") && factNumber(observed, "submittedBuffers", 0) > 0) a = Math.max(a, 2);
            if (name.equals("audio.output.write") && factNumber(observed, "acceptedBytes", 0) > 0) a = Math.max(a, 3);
            if (name.equals("audio.output.playhead") && factNumber(observed, "headDeltaFrames", 0) > 0) a = Math.max(a, 4);
            if (v > video) { video = v; videoId = id; } if (a > audio) { audio = a; audioId = id; }
            if (Set.of("error", "fatal").contains(string(event, "level", "info")) || factNumber(observed, "videoPartialFailure", 0) == 1) {
                errors.put(id, name + " " + factString(observed, "stage") + " " + factString(observed, "message"));
                while (errors.size() > 4) {
                    java.util.Iterator<String> keys = errors.keySet().iterator(); keys.next(); keys.next(); keys.remove();
                }
            }
        }
        JsonObject json() {
            JsonObject result = new JsonObject(); result.addProperty("runId", run); result.addProperty("trace", trace);
            result.addProperty("playerInstanceId", instance); result.addProperty("attemptId", attempt); result.addProperty("engine", engine);
            result.addProperty("videoEvidenceLevel", video); result.addProperty("audioEvidenceLevel", audio);
            JsonObject values = new JsonObject(); facts.forEach(values::add); result.add("facts", values); return result;
        }
    }

    public static Analysis analyze(InputStream input, boolean partial) throws IOException {
        Analysis result = new Analysis(partial);
        scan(input, (line, event) -> {
            if (event != null) result.accept(event);
            else if (line.startsWith("#") && line.contains("\"completeness\"")) {
                try { JsonObject value = JsonParser.parseString(line.substring(line.indexOf('{'))).getAsJsonObject();
                    if (value.has("completeness")) { result.health = value; if ("partial".equals(string(value, "completeness", "partial"))) result.partial = true; }
                } catch (RuntimeException ignored) { result.partial = true; }
            }
        });
        return result;
    }

    public static DiagnosticLogBuffer.Export text(DiagnosticLogBuffer.Export source) {
        try (InputStream copy = source.openAgain()) {
            Analysis analysis = analyze(copy, source.partial);
            // Append: the original manifest's all-preceding-bytes checksum remains valid.
            byte[] summary = ("\n# Human-readable report follows the immutable snapshot\n" + analysis.text()).getBytes(StandardCharsets.UTF_8);
            return new DiagnosticLogBuffer.Export(new SequenceInputStream(source.input, new ByteArrayInputStream(summary)),
                    summary.length + source.length, analysis.partial());
        } catch (IOException | RuntimeException ignored) { return source; }
    }

    /** Caller owns source until all passes finish; ZIP is streamed, with no second full disk copy. */
    public static void archive(DiagnosticLogBuffer.Export source, OutputStream output) throws IOException {
        JsonObject files = new JsonObject();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            Analysis analysis;
            try (InputStream copy = source.openAgain()) { analysis = analyze(copy, source.partial); }
            put(zip, files, "report.txt", new ByteArrayInputStream(analysis.text().getBytes(StandardCharsets.UTF_8)));
            put(zip, files, "webhtv-debug-log.txt", source.input);
            zip.putNextEntry(new ZipEntry("av-events.jsonl")); MessageDigest digest = RollingDiagnosticFile.sha256(); long[] size = {0};
            try (InputStream copy = source.openAgain()) {
                scan(copy, (line, event) -> {
                    if (event == null) return;
                    byte[] data = (event.toString() + "\n").getBytes(StandardCharsets.UTF_8); zip.write(data); digest.update(data); size[0] += data.length;
                });
            }
            zip.closeEntry(); metadata(files, "av-events.jsonl", size[0], digest);
            put(zip, files, "session-snapshots.json", new ByteArrayInputStream(analysis.snapshots().toString().getBytes(StandardCharsets.UTF_8)));
            JsonObject manifest = new JsonObject(); manifest.addProperty("schemaVersion", 1); manifest.addProperty("completeness", analysis.partial() ? "partial" : "declared-window");
            manifest.addProperty("rawMediaIncluded", false); manifest.addProperty("eventsIncludePinnedDuplicates", true); manifest.add("files", files);
            zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
    }

    private interface LineConsumer { void accept(String line, JsonObject event) throws IOException; }
    private static void scan(InputStream input, LineConsumer consumer) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8), 8192);
        StringBuilder line = new StringBuilder(); int c; long count = 0; boolean truncated = false;
        while ((c = reader.read()) != -1) {
            if (++count > 24L * 1024 * 1024) throw new IOException("Diagnostic snapshot exceeds budget");
            if (c == '\n') { String value = line.toString(); consumer.accept(value, truncated ? null : parseEvent(value)); line.setLength(0); truncated = false; }
            else if (line.length() < 64_000) line.append((char) c); else truncated = true;
        }
        if (line.length() > 0) consumer.accept(line.toString(), truncated ? null : parseEvent(line.toString()));
    }

    private static void put(ZipOutputStream zip, JsonObject manifest, String name, InputStream input) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); MessageDigest digest = RollingDiagnosticFile.sha256();
        byte[] bytes = new byte[8192]; long count = 0; int length;
        while ((length = input.read(bytes)) >= 0) { if (length == 0) continue; count += length;
            if (count > 24L * 1024 * 1024) throw new IOException("Diagnostic entry exceeds budget");
            zip.write(bytes, 0, length); digest.update(bytes, 0, length);
        }
        zip.closeEntry(); metadata(manifest, name, count, digest);
    }
    private static void metadata(JsonObject files, String name, long size, MessageDigest digest) {
        JsonObject value = new JsonObject(); value.addProperty("bytes", size); value.addProperty("sha256", RollingDiagnosticFile.hex(digest.digest())); files.add(name, value);
    }
    private static String boundary(int level, boolean video) {
        if (level < 0) return "未观测";
        return switch (Math.min(4, level)) { case 0 -> "轨道/配置"; case 1 -> "输入sample"; case 2 -> "解码输出";
            case 3 -> video ? "提交到输出（不等于实际显示）" : "输出接受payload（不等于实际发声）";
            default -> video ? "系统帧回调（不等于物理显示）" : "音频播放头前进（不等于实际发声）"; };
    }
    private static JsonObject object(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonObject() ? value.getAsJsonObject(key) : new JsonObject(); }
    private static String string(JsonObject value, String key, String fallback) { try { return value.has(key) ? value.get(key).getAsString() : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static long number(JsonObject value, String key, long fallback) { try { return value.has(key) ? value.get(key).getAsLong() : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static long factNumber(JsonObject observed, String key, long fallback) {
        JsonObject fact = object(observed, key); if (!"known".equals(string(fact, "status", "unknown"))) return fallback;
        JsonElement value = fact.get("value"); if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean() ? 1 : 0;
        return number(fact, "value", fallback);
    }
    private static String factString(JsonObject observed, String key) { JsonObject fact = object(observed, key); return "known".equals(string(fact, "status", "unknown")) ? string(fact, "value", "unknown") : "unknown"; }
}
