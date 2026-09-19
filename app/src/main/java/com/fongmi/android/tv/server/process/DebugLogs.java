package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticLogBuffer;
import com.github.catvod.crawler.diagnostics.DiagnosticReport;
import com.github.catvod.crawler.diagnostics.DiagnosticAccess;
import com.github.catvod.crawler.diagnostics.DiagnosticCapture;
import com.fongmi.android.tv.player.DiagnosticControls;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public class DebugLogs implements Process {
    private static final java.util.concurrent.atomic.AtomicBoolean archiveBusy = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.ScheduledExecutorService exportTimer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "diagnostic-export-expiry"); thread.setDaemon(true); return thread;
    });

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/debug/diag/") || url.startsWith("/debug/logs") || url.startsWith("/debug/mpd") || url.startsWith("/debug/stream") || url.startsWith("/debug/clear") || url.startsWith("/debug/enable") || url.startsWith("/debug/disable");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (url.startsWith("/debug/diag/")) return diagnosticAction(session, url, files);
        if (url.startsWith("/debug/enable") || url.startsWith("/debug/disable") || url.startsWith("/debug/clear")) {
            Response rejection = controlRequestError(session);
            if (rejection != null) return rejection;
        }
        if (url.startsWith("/debug/enable")) {
            Setting.putDebugLog(true);
            return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, ""), "/debug/logs");
        }
        if (url.startsWith("/debug/disable")) {
            Setting.putDebugLog(false);
            return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, ""), "/debug/logs");
        }
        if (url.startsWith("/debug/clear")) {
            DebugLogStore.clear();
            if (DebugLogStore.isEnabled()) Setting.logDebugEnvironment("clear");
            return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, ""), "/debug/logs");
        }
        if (url.startsWith("/debug/stream")) return stream(session);
        if (url.startsWith("/debug/mpd")) return mpd();
        if (url.startsWith("/debug/logs.txt")) return download();
        return page();
    }

    private Response mpd() {
        try {
            File file = new File(App.get().getCacheDir(), "youtube-mpd.xml");
            return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/dash+xml", new FileInputStream(file), file.length()), null);
        } catch (Exception e) {
            return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "MPD unavailable"), null);
        }
    }

    private Response page() {
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html());
        return noCache(response, null);
    }

    private Response download() {
        DiagnosticLogBuffer.Export export = DiagnosticReport.text(DebugLogStore.export());
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", export.input, export.length);
        response.addHeader("Content-Disposition", "attachment; filename=webhtv-debug-log.txt");
        response.addHeader("X-Content-Type-Options", "nosniff");
        response.addHeader("X-Diagnostic-Completeness", export.partial ? "partial" : "declared-window");
        return noCache(response, null);
    }

    private boolean originAllowed(IHTTPSession session) {
        java.util.Set<String> hosts = new java.util.HashSet<>();
        for (String address : new String[]{Server.get().getAddress("/debug/logs"), Server.get().getAddress(false)}) {
            try { hosts.add(java.net.URI.create(address).getRawAuthority().toLowerCase(java.util.Locale.ROOT)); } catch (RuntimeException ignored) {}
        }
        return DiagnosticAccess.sameOrigin(session.getHeaders().get("origin"), session.getHeaders().get("host"), hosts);
    }

    private Response controlRequestError(IHTTPSession session) {
        if (session.getMethod() != NanoHTTPD.Method.POST) return diagnosticMessage(Response.Status.METHOD_NOT_ALLOWED, "此操作需要 POST");
        if (!originAllowed(session)) return diagnosticMessage(Response.Status.FORBIDDEN, "请求来源不匹配，请从设备日志页面操作");
        if (!DiagnosticControls.ACCESS.allowAction()) return diagnosticMessage(Response.Status.TOO_MANY_REQUESTS, "操作过快，请稍后重试");
        return null;
    }

    private Response diagnosticAction(IHTTPSession session, String url, Map<String, String> files) {
        if (url.equals("/debug/diag/status") && session.getMethod() == NanoHTTPD.Method.GET)
            return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", DiagnosticControls.status().toString()), null);
        Response rejection = controlRequestError(session);
        if (rejection != null) return rejection;
        try {
            if (Long.parseLong(session.getHeaders().getOrDefault("content-length", "0")) > 2048) return diagnosticMessage(Response.Status.BAD_REQUEST, "请求过长");
            String raw = files.getOrDefault("postData", "{}");
            if (raw.length() > 2048) return diagnosticMessage(Response.Status.BAD_REQUEST, "请求过长");
            JsonObject data = JsonParser.parseString(raw.isEmpty() ? "{}" : raw).getAsJsonObject();
            switch (url) {
                case "/debug/diag/mark" -> DiagnosticControls.mark(field(data, "symptom"));
                case "/debug/diag/deep" -> {
                    if (!data.has("consent") || !data.get("consent").getAsBoolean()) return diagnosticMessage(Response.Status.BAD_REQUEST, "需要确认限时统计");
                    DiagnosticControls.startDepth(data.has("seconds") ? data.get("seconds").getAsInt() : 60);
                }
                case "/debug/diag/stop" -> DiagnosticCapture.stop("user-stopped");
                case "/debug/diag/fel-bind" -> {
                    if (!data.has("consent") || !data.get("consent").getAsBoolean()) return diagnosticMessage(Response.Status.BAD_REQUEST, "需要确认暂停播放进行对照");
                    DiagnosticControls.startFelBindProbe();
                }
                case "/debug/diag/fel-bind-stop" -> DiagnosticControls.stopFelBindProbe();
                case "/debug/diag/export" -> { return archive(); }
                default -> { return diagnosticMessage(Response.Status.NOT_FOUND, "未找到操作"); }
            }
            return diagnosticMessage(Response.Status.OK, "已记录");
        } catch (RuntimeException error) {
            return diagnosticMessage(Response.Status.BAD_REQUEST, error instanceof IllegalStateException || error instanceof IllegalArgumentException ? error.getMessage() : "无效请求");
        }
    }

    private String field(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonPrimitive() ? value.get(key).getAsString() : ""; }

    private Response diagnosticMessage(Response.Status status, String message) {
        JsonObject value = new JsonObject(); value.addProperty("ok", status == Response.Status.OK); value.addProperty("message", message);
        return noCache(NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", value.toString()), null);
    }

    private Response archive() {
        if (!DebugLogStore.isEnabled()) return diagnosticMessage(Response.Status.BAD_REQUEST, "请先开启调试日志");
        if (!archiveBusy.compareAndSet(false, true)) return diagnosticMessage(Response.Status.SERVICE_UNAVAILABLE, "已有诊断包正在导出，请稍后重试");
        DiagnosticLogBuffer.Export source = DebugLogStore.export();
        try {
            try (java.io.InputStream ignored = source.openAgain()) { /* Reject a non-repeatable snapshot before HTTP 200. */ }
            java.io.PipedInputStream input = new java.io.PipedInputStream(64 << 10);
            java.io.PipedOutputStream output = new java.io.PipedOutputStream(input);
            java.util.concurrent.ScheduledFuture<?> expiry = exportTimer.schedule(() -> {
                try { input.close(); output.close(); } catch (java.io.IOException ignored) {}
            }, 60, java.util.concurrent.TimeUnit.SECONDS);
            Thread worker = new Thread(() -> {
                try (source; output) { DiagnosticReport.archive(source, output); }
                catch (java.io.IOException | RuntimeException ignored) { DebugLogStore.collectorFailure(); }
                finally { expiry.cancel(false); archiveBusy.set(false); }
            }, "diagnostic-report-export");
            worker.setDaemon(true); worker.start();
            Response response = NanoHTTPD.newChunkedResponse(Response.Status.OK, "application/zip", input);
            response.addHeader("Content-Disposition", "attachment; filename=webhtv-av-report.zip");
            response.addHeader("X-Content-Type-Options", "nosniff");
            return noCache(response, null);
        } catch (java.io.IOException | RuntimeException error) {
            try { source.close(); } catch (java.io.IOException ignored) {}
            archiveBusy.set(false);
            return diagnosticMessage(Response.Status.INTERNAL_ERROR, "诊断包生成失败，请重试 TXT 下载");
        }
    }

    private Response stream(IHTTPSession session) {
        if (session.getParms().containsKey("afterSeq")) return incrementalStream(session);
        long version = DebugLogStore.version();
        boolean unchanged = version == paramLong(session, "v", -1);
        String text = unchanged ? null : DebugLogStore.text();
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", "{\"enabled\":" + DebugLogStore.isEnabled() + ",\"size\":" + DebugLogStore.size() + ",\"bytes\":" + DebugLogStore.bytes() + ",\"version\":" + version + ",\"text\":" + (unchanged ? "null" : "\"" + json(text) + "\"") + "}");
        return noCache(response, null);
    }

    private Response incrementalStream(IHTTPSession session) {
        DiagnosticLogBuffer.Snapshot snapshot = DebugLogStore.incremental(paramLong(session, "afterSeq", -1),
                session.getParms().get("run"), paramLong(session, "generation", -1));
        JsonObject result = new JsonObject();
        result.addProperty("enabled", DebugLogStore.isEnabled());
        if (snapshot == null) {
            result.addProperty("reset", true); result.addProperty("text", "调试日志未开启\n");
            result.addProperty("runId", ""); result.addProperty("generation", -1);
            result.addProperty("newestSeq", -1); result.addProperty("size", 0); result.addProperty("bytes", 0);
        } else {
            result.addProperty("runId", snapshot.runId()); result.addProperty("generation", snapshot.generation());
            result.addProperty("version", snapshot.version()); result.addProperty("oldestSeq", snapshot.oldestSeq());
            result.addProperty("newestSeq", snapshot.newestSeq()); result.addProperty("reset", snapshot.reset());
            result.addProperty("gap", snapshot.gap()); result.addProperty("text", snapshot.text());
            result.addProperty("size", snapshot.lines().size()); result.addProperty("bytes", snapshot.health().get("diskBytes").getAsLong());
            result.add("health", snapshot.health());
        }
        return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", result.toString()), null);
    }

    private long paramLong(IHTTPSession session, String key, long fallback) {
        try {
            return Long.parseLong(session.getParms().get(key));
        } catch (Exception e) {
            return fallback;
        }
    }

    private Response noCache(Response response, String location) {
        response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.addHeader("Pragma", "no-cache");
        if (!TextUtils.isEmpty(location)) response.addHeader("Location", location);
        return response;
    }

    private String html() {
        String logs = escape(DebugLogStore.text());
        String localUrl = Server.get().getAddress("/debug/logs");
        String xbpqUrl = Server.get().getAddress("/proxy?do=log");
        String lanUrl = Server.get().getAddress(false) + "/debug/logs";
        boolean enabled = DebugLogStore.isEnabled();
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">"
                + "<title>调试日志</title><style>" + css() + "</style></head><body>"
                + "<main><header class=\"console-head\"><div class=\"brand\"><h1>调试日志</h1><span id=\"meta\" class=\"meta\" data-version=\"" + DebugLogStore.version() + "\">" + (enabled ? "记录中" : "已关闭") + " · " + DebugLogStore.bytes() / 1024 + " KB</span></div>"
                + "<div class=\"primary-actions\"><a id=\"download\" class=\"action\" href=\"/debug/logs.txt\" download=\"webhtv-debug-log.txt\" title=\"下载 TXT 日志\" aria-label=\"下载 TXT 日志\">下载</a><button id=\"clear\" class=\"danger\" data-debug-action=\"/debug/clear\" aria-label=\"清空日志\">清空</button><button id=\"pause\" aria-pressed=\"false\">暂停</button><button id=\"tools-open\" aria-haspopup=\"dialog\" aria-controls=\"log-drawer\">工具<span id=\"filter-count\" class=\"count\" hidden></span></button></div>"
                + "<div id=\"search-panel\" class=\"search-panel\" hidden><input id=\"filter\" type=\"search\" aria-label=\"搜索日志\" placeholder=\"搜索日志关键词\"><button id=\"search-hide\">收起</button></div>"
                + "<div class=\"category-row\"><nav class=\"tabs\" aria-label=\"日志分类\"><button class=\"chip on\" data-mode=\"all\" aria-pressed=\"true\">全部</button><button class=\"chip\" data-mode=\"error\">错误</button><button class=\"chip\" data-mode=\"api\">站源</button><button class=\"chip\" data-mode=\"console\">Console</button><button class=\"chip\" data-mode=\"diagnostic\">音视频诊断</button><button class=\"chip\" data-mode=\"player\">播放</button><button class=\"chip\" data-mode=\"proxy\">代理</button><button class=\"chip\" data-mode=\"webhome\">WebHome</button><button class=\"chip\" data-mode=\"webview\">WebView</button><button class=\"chip\" data-mode=\"pan\">网盘</button><button class=\"chip\" data-mode=\"server\">服务</button><button class=\"chip\" data-mode=\"sync\">同步</button><button class=\"chip\" data-mode=\"startup\">启动</button></nav><button id=\"search-toggle\" aria-expanded=\"false\" aria-controls=\"search-panel\">搜索</button></div></header>"
                + "<div id=\"logs\" class=\"logs\"></div><pre id=\"raw\" class=\"fallback\">" + logs + "</pre></main>"
                + diagnosticHtml(localUrl, lanUrl, xbpqUrl, enabled)
                + "<script>" + scriptEnhanced() + diagnosticScript() + toolbarScript() + "</script></body></html>";
    }

    private String css() {
        return """
            html{box-sizing:border-box;overflow-x:hidden;background:#f4f6f8;color:#1f2328;font:14px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif}
            *,*:before,*:after{box-sizing:inherit;min-width:0}[hidden]{display:none!important}
            body{margin:0}body.drawer-open{overflow:hidden}main{width:100%;padding:0 8px 8px}
            button,a,input,select{font:inherit}button,a{touch-action:manipulation}
            button,a.action{display:inline-flex;align-items:center;justify-content:center;min-height:40px;appearance:none;border:1px solid #d0d7de;border-radius:7px;background:#fff;color:#24292f;padding:6px 12px;cursor:pointer;white-space:nowrap;text-decoration:none}
            button:hover,a.action:hover{background:#f1f5f9}button.on,button.filtered{background:#ddf4ff;border-color:#80ccff;color:#0969da}
            a:focus-visible,button:focus-visible,summary:focus-visible,input:focus-visible,select:focus-visible{outline:2px solid #0969da;outline-offset:2px}
            .console-head{position:sticky;top:0;z-index:20;display:grid;grid-template-columns:minmax(0,1fr) auto;grid-template-areas:"brand actions" "tabs tabs" "search search";align-items:center;column-gap:8px;padding:4px 8px 0;margin-bottom:8px;background:#fff;border-bottom:1px solid #d8dee4;box-shadow:0 2px 8px #1f232808}
            .brand{grid-area:brand;overflow:hidden}h1{margin:0;font-size:16px;line-height:21px;font-weight:650}.meta{display:block;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:11px;line-height:16px;color:#656d76}.meta.partial{color:#9a6700}
            .primary-actions{grid-area:actions;display:flex;gap:4px}.primary-actions button,.primary-actions a.action,#search-toggle{min-width:44px;min-height:44px;padding:6px 9px;border-color:transparent;font-size:13px}.primary-actions .danger{color:#cf222e}
            .count{margin-left:4px;min-width:16px;padding:0 4px;border-radius:8px;background:#0969da;color:#fff;font-size:11px;line-height:16px}
            .category-row{grid-area:tabs;display:flex;align-items:center}.category-row>button{flex:0 0 auto}.tabs{flex:1;display:flex;gap:0;overflow-x:auto;overflow-y:hidden;flex-wrap:nowrap;white-space:nowrap;scrollbar-width:none;-webkit-overflow-scrolling:touch}.tabs::-webkit-scrollbar{display:none}
            .tabs .chip{flex:0 0 auto;min-height:44px;padding:7px 12px;border:0;border-bottom:2px solid transparent;border-radius:0;background:transparent;color:#57606a;font-size:13px}
            .tabs .chip.on{color:#0969da;border-bottom-color:#0969da;font-weight:650}.tabs .chip:focus-visible{outline-offset:-3px}
            .search-panel{grid-area:search;display:flex;align-items:center;gap:6px;padding:4px 0 8px}
            input:not([type=checkbox]),select{min-width:0;width:100%;max-width:100%;min-height:40px;border:1px solid #d0d7de;border-radius:7px;padding:8px 10px;background:#fff;color:inherit}
            #filter{flex:1;width:100%;font-size:16px}.search-panel button{min-height:40px;font-size:13px}
            .drawer-backdrop{position:fixed;top:0;right:0;bottom:0;left:0;z-index:100;background:#1f232866}
            .log-drawer{position:fixed;top:0;right:0;bottom:0;left:auto;z-index:101;display:none;flex-direction:column;width:460px;max-width:100%;height:100%;max-height:100%;margin:0;padding:0;border:0;background:#fff;color:inherit;box-shadow:-8px 0 32px #1f232826}
            .log-drawer[open]{display:flex}.log-drawer::backdrop{background:#1f232866}
            .drawer-head{display:flex;flex:0 0 auto;align-items:center;justify-content:space-between;gap:8px;padding:10px 16px 4px}.drawer-head h2{margin:0;font-size:18px}.drawer-head button{width:44px;height:44px;padding:0;border:0;font-size:24px}
            .tool-tabs{display:flex;flex:0 0 auto;padding:0 16px;border-bottom:1px solid #d8dee4}.tool-tabs button{flex:1;min-height:44px;border:0;border-bottom:2px solid transparent;border-radius:0;background:transparent;color:#656d76}.tool-tabs button[aria-selected=true]{border-bottom-color:#0969da;color:#0969da;font-weight:650}
            .drawer-body{flex:1 1 auto;min-height:0;overflow-y:auto;overscroll-behavior:contain;padding:16px}.drawer-body h3{margin:0 0 10px;font-size:14px}
            .field-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.field{display:flex;flex-direction:column;gap:5px;color:#57606a;font-size:13px}
            .field select,.field input{color:#24292f}.form-section+.form-section{margin-top:20px;padding-top:16px;border-top:1px solid #eaeef2}
            .action-row{display:flex;flex-wrap:wrap;align-items:center;gap:8px;margin-top:10px}.action-row>input{flex:1;min-width:120px}.action-row>select{flex:1;min-width:100px}.action-row>.primary{background:#0969da;border-color:#0969da;color:white}
            .simple{display:flex;align-items:center;gap:9px;min-height:44px;cursor:pointer}.simple input{flex:0 0 18px;width:18px;height:18px;margin:0;padding:0}
            .hint{margin:8px 0;color:#656d76;font-size:12px;line-height:1.6}.hint:empty{display:none}.summary{white-space:normal;overflow-wrap:anywhere}
            .drawer-feedback{flex:0 0 auto;max-height:100px;overflow:auto;margin:0;padding:10px 16px;color:#0969da;background:#f0f7ff;font-size:13px;line-height:1.5}.drawer-feedback:empty{display:none}
            .addr{display:grid;gap:8px;font-size:12px}.addr a{display:block;padding:10px;border-radius:7px;background:#f6f8fa;color:#0969da;overflow-wrap:anywhere;word-break:break-all;text-decoration:none}
            .logs{display:grid;gap:6px;width:100%;overflow:hidden}.fallback{margin:0}
            .entry,.fallback{width:100%;overflow:hidden;background:#fff;border:1px solid #d8dee4;border-radius:7px;padding:8px 10px}
            .entry.ok{border-left:4px solid #1a7f37}.entry.warn{border-left:4px solid #bf8700}.entry.err{border-left:4px solid #cf222e}.entry.raw{border-left:4px solid #8c959f}
            .top{display:flex;gap:8px;align-items:center;overflow:hidden}.badge{flex:0 0 auto;border-radius:999px;padding:2px 7px;background:#eaeef2;color:#57606a;font-size:12px}
            .entry.ok .badge{background:#dafbe1;color:#116329}.entry.warn .badge{background:#fff8c5;color:#7d4e00}.entry.err .badge{background:#ffebe9;color:#cf222e}
            .title{font-weight:650;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.time{margin-left:auto;color:#8c959f;font-size:12px;white-space:nowrap}
            .detail,.rawline,.fallback{overflow-wrap:anywhere;word-break:break-all;white-space:pre-wrap}.detail{margin-top:4px;color:#57606a}
            .rawline{display:block;margin-top:6px;padding-top:6px;border-top:1px dashed #d8dee4;color:#6e7781}
            .rawline,.fallback{font:12px/1.45 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}body.simple .rawline{display:none}
            @media(min-width:800px){.console-head{grid-template-columns:minmax(0,1fr) 260px auto;grid-template-areas:"brand search actions" "tabs tabs tabs"}.search-panel{padding:0}#search-hide{display:none}.tabs .chip{min-height:36px}.primary-actions button,.primary-actions a.action{min-height:40px}}
            @media(max-width:600px){.log-drawer{top:auto;right:0;bottom:0;left:0;width:100%;height:auto;max-height:88vh;max-height:88dvh;border-radius:16px 16px 0 0;box-shadow:0 -8px 32px #1f232826}.drawer-body{padding-bottom:20px;padding-bottom:max(20px,env(safe-area-inset-bottom))}.drawer-body button,.drawer-body a.action,.drawer-body select,.drawer-body input:not([type=checkbox]){min-height:44px}}
            @media(max-width:480px){main{padding:0 4px 4px}.console-head{padding:4px 6px 0}.title{white-space:normal}.entry{padding:7px 8px}.detail{font-size:13px}}
            """;
    }

    private String scriptEnhanced() {
        return "const rawEl=document.getElementById('raw'),logs=document.getElementById('logs'),meta=document.getElementById('meta'),summary=document.getElementById('summary'),filter=document.getElementById('filter'),simple=document.getElementById('simple'),pause=document.getElementById('pause'),download=document.getElementById('download');"
                + "let raw=rawEl.textContent,mode='all',paused=false,stick=true,lastVersion=Number(meta.dataset.version||0),lastSeq=-1,lastRun='',lastGeneration=-1;simple.checked=true;document.body.classList.add('simple');addEventListener('scroll',()=>{if(toolsOpen())return;resetX();stick=(innerHeight+scrollY)>=(document.body.scrollHeight-80)},{passive:true});setInterval(resetX,500);"
                + "document.querySelectorAll('.chip').forEach(b=>{b.setAttribute('aria-pressed',b.classList.contains('on'));b.tabIndex=b.classList.contains('on')?0:-1;b.onclick=()=>selectCategory(b)});filter.oninput=()=>{render();updateFilterState()};simple.onchange=()=>{document.body.classList.toggle('simple',simple.checked);render();resetX()};pause.onclick=()=>{paused=!paused;pause.textContent=paused?'继续':'暂停';pause.classList.toggle('on',paused);pause.setAttribute('aria-pressed',paused)};"
                + "download.onclick=()=>{paused=true;pause.textContent='继续';pause.classList.add('on');pause.setAttribute('aria-pressed','true')};"
                + "function esc(s){return String(s||'').replace(/[&<>\"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',\"'\":'&#39;'}[c]))}"
                + "function resetX(){try{const y=scrollY||pageYOffset||0;(document.scrollingElement||document.documentElement).scrollLeft=0;document.documentElement.scrollLeft=0;document.body.scrollLeft=0;if((document.scrollingElement||document.documentElement).scrollLeft||document.body.scrollLeft)scrollTo(0,y)}catch(e){}}"
                + "function part(s,k){const i=s.indexOf(k);if(i<0)return'';let v=s.slice(i+k.length),e=v.length;[' ',',',']'].forEach(c=>{const p=v.indexOf(c);if(p>=0&&p<e)e=p});return v.slice(0,e)}"
                + "function between(s,a,b){const i=s.indexOf(a);if(i<0)return'';const j=s.indexOf(b,i+a.length);return j<0?s.slice(i+a.length):s.slice(i+a.length,j)}"
                + "function proxyName(s){return between(s,'proxy=[',']').replace('SOCKS @ ','SOCKS ').replace('/<unresolved>','')}"
                + "function parse(line){const a=line.indexOf(' ['),b=line.indexOf('] ',a+2),c=line.indexOf(': ',b+2);return{line,time:a>0?line.slice(0,a):'',thread:a>0&&b>0?line.slice(a+2,b):'',tag:b>0&&c>0?line.slice(b+2,c):'',msg:c>0?line.slice(c+2):line}}"
                + "function base(r){const e={kind:'raw',state:'raw',badge:r.tag||'日志',title:r.tag||'原始日志',detail:r.msg||r.line,raw:r.line,time:r.time};if(r.tag==='av-diag'){try{e.diag=JSON.parse(r.msg)}catch(ignore){}}return e}"
                + "function crawlerConsole(r){const name=r.tag==='quickjs'?'QuickJS':r.tag==='python-spider'?'Python':'';if(!name)return null;const e=base(r),match=/^\\[(DEBUG|INFO|WARN|ERROR|STDERR)\\] ?/.exec(r.msg),level=match?match[1]:'';e.kind='console';e.badge=name;e.detail=match?r.msg.slice(match[0].length):r.msg;e.state=level==='ERROR'?'err':level==='WARN'?'warn':level==='STDERR'?'raw':'ok';if(!match)e.state=/error|exception|uncaught/i.test(r.msg)?'err':/warning|warn/i.test(r.msg)?'warn':'raw';e.title=name+(level==='STDERR'?' 标准错误流':level?' · '+level:' 控制台输出');return e}"
                + "function diagBadge(ev){if(ev.startsWith('audio.'))return '音频';if(ev.startsWith('video.')||ev.startsWith('surface.'))return '视频';if(ev.startsWith('env.'))return '环境';if(ev.startsWith('mpv.'))return 'MPV';if(ev.startsWith('diag.'))return '诊断';if(ev.startsWith('play.'))return '播放';if(ev.startsWith('input.'))return '输入';if(ev.startsWith('process.'))return '进程';return '诊断'}"
                + "function explain(r){const crawler=crawlerConsole(r);if(crawler)return crawler;const text=(r.tag+': '+r.msg),low=text.toLowerCase();let e=base(r);if(r.tag==='av-diag'){try{const d=JSON.parse(r.msg);if(d.schemaVersion===1&&typeof d.event==='string'){e.kind='diagnostic';e.state=d.level==='error'?'err':d.level==='warn'?'warn':'raw';e.badge=diagBadge(d.event);e.title=d.event;e.detail=JSON.stringify(d);return e}}catch(ignore){}}if(r.tag==='webview-console'||r.tag==='webhome-console'){e.kind='console';e.state=(low.includes('error')||low.includes('exception')||low.includes('uncaught'))?'err':(low.includes('warning')||low.includes('warn'))?'warn':'ok';e.badge='Console';e.title=r.tag==='webhome-console'?'WebHome 控制台输出':'网页控制台输出';e.detail=r.msg;return e}if(r.tag==='server'){e.kind='server';e.state='raw';e.badge='服务';e.title='App 本机 HTTP 服务收到请求';e.detail=r.msg;return e}if(low.includes('error')||low.includes('exception')||low.includes('failed')||low.includes('timeout')||low.includes('失败')||low.includes('崩溃')||low.includes('异常')){e.kind='error';e.state='err';e.badge='错误';e.title='发现错误或异常';return e}"
                + "if(r.tag==='startup'){e.kind='startup';e.state='ok';e.badge='启动';e.title='启动阶段耗时';e.detail=r.msg;return e}"
                + "if(r.tag==='debug'){e.kind='server';e.state='ok';e.badge='调试';e.title=r.msg.includes('ready')?'调试日志服务已准备':'调试日志状态变化';e.detail=r.msg;return e}"
                + "if(r.tag==='env'){e.kind='startup';e.state='ok';e.badge='环境';e.title='设备和系统环境';e.detail=r.msg;return e}"
                + "if(r.tag==='web-resource'){e.kind='server';e.state=r.msg.includes('->')?'ok':'raw';e.badge='资源';e.title=r.msg.includes('->')?'Web 资源代理返回响应':'Web 资源代理发起请求';e.detail=r.msg;return e}"
                + "if(r.tag==='sync'){e.kind='sync';e.state='ok';e.badge='同步';e.title=r.msg.includes('archive')?'正在打包同步目录':r.msg.includes('restore')?'正在恢复同步目录':'一键同步';e.detail=r.msg;return e}"
                + "if(r.tag==='pan-check'||r.tag==='pan-check-net'){e.kind='pan';e.state=r.msg.includes('state=valid')||r.msg.includes('-> 200')?'ok':'raw';e.badge='网盘';e.title=r.tag==='pan-check-net'?(r.msg.includes('->')?'网盘检测网络响应':'网盘检测网络请求'):'网盘链接检测';e.detail=r.msg;return e}"
                + "if(r.tag==='webview'||r.tag==='webview-parse'||r.tag==='webhome-webview'){e.kind='webview';e.state=(low.includes('error')||low.includes('gone')||low.includes('crash'))?'err':'ok';e.badge='WebView';e.title=r.msg.includes('provider')?'当前 WebView 内核':r.msg.includes('resource error')?'网页资源加载失败':r.msg.includes('page finished')?'网页加载完成':r.msg.includes('page started')?'网页开始加载':'WebView 事件';e.detail=r.msg;return e}"
                + "if(r.tag&&r.tag.startsWith('webhome')){e.kind='webhome';e.state=r.msg.includes('-> 200')||r.msg.includes('invoke')?'ok':'raw';e.badge='WebHome';e.title=r.tag==='webhome-net'?(r.msg.includes('->')?'WebHome 网络响应':'WebHome 网络请求'):'WebHome 开放能力调用';e.detail=r.msg;return e}"
                + "if(['home','homeVideo','category','detail','search','action'].includes(r.tag)){e.kind='api';e.state='raw';e.badge='站源';e.title=r.tag==='search'?'站源搜索':r.tag==='detail'?'站源详情':r.tag==='category'?'站源分类':r.tag==='action'?'站源动作':'站源首页';e.detail=r.msg;return e}"
                + "if(r.tag==='SpiderDebug'){e.kind='api';e.state=low.includes('成功')||low.includes('通过')?'ok':'raw';e.badge='接口';e.title=low.includes('代理程序')?'接口代理程序':'接口插件日志';e.detail=r.msg;return e}"
                + "if(r.tag==='proxy'&&r.msg.includes('app proxy enabled')){e.kind='proxy';e.state='ok';e.badge='代理';e.title='壳代理已启用';e.detail='已加载 '+part(r.msg,'rules=')+' 条规则，默认代理 '+part(r.msg,'defaultUrl=');return e}"
                + "if(r.tag==='proxy'&&r.msg.includes('app proxy disabled')){e.kind='proxy';e.state='warn';e.badge='代理';e.title='壳代理已关闭';e.detail='配置仍可保留，但当前不会代理请求';return e}"
                + "if(r.tag==='proxy'&&r.msg.includes('select hit')){e.kind='proxy';e.state='ok';e.badge='代理命中';e.title='请求命中壳代理';e.detail=(part(r.msg,'host=')||part(r.msg,'uri='))+' 命中规则 '+(part(r.msg,'rule=')||'-')+'，使用 '+proxyName(r.msg);return e}"
                + "if(r.tag==='proxy'&&r.msg.includes('local-target')){e.kind='proxy';e.state='warn';e.badge='直连';e.title='本机服务直连';e.detail='访问 127.0.0.1 这类 App 本机服务，按设计不走壳代理';return e}"
                + "if(r.tag==='proxy'&&r.msg.includes('request uri=/proxy')){e.kind='server';e.state='raw';e.badge='服务';e.title='App 内置代理接口收到请求';e.detail=r.msg;return e}"
                + "if(r.tag==='proxy'&&r.msg.includes('response do=')){e.kind='server';e.state=r.msg.includes('status=200')?'ok':'warn';e.badge='服务';e.title='App 内置代理接口返回响应';e.detail=r.msg;return e}"
                + "if(r.tag==='okhttp-player'&&r.msg.includes('connectStart')){e.kind='player';e.state=r.msg.includes('proxy=SOCKS')?'ok':'warn';e.badge='播放';e.title=r.msg.includes('proxy=SOCKS')?'播放器通过壳代理连接':'播放器直连';e.detail=(part(r.msg,'url=')||'')+' · '+(between(r.msg,'proxy=',',')||'');return e}"
                + "if(r.tag==='okhttp-player'&&r.msg.includes('connectionAcquired')){e.kind='player';e.state=r.msg.includes('via proxy')?'ok':'warn';e.badge='播放';e.title=r.msg.includes('via proxy')?'播放器连接已走代理':'播放器连接已建立';e.detail=part(r.msg,'url=')||r.msg;return e}"
                + "if(r.tag==='okhttp-player'&&r.msg.includes('response')){e.kind='player';e.state='ok';e.badge='响应';e.title='播放器收到响应';e.detail='状态 '+(part(r.msg,'code=')||'-')+' · '+(part(r.msg,'contentType=')||'')+' · '+(part(r.msg,'url=')||'');return e}"
                + "if(r.tag==='okhttp-player'&&r.msg.includes('start')){e.kind='player';e.state='raw';e.badge='播放';e.title='播放器开始请求';e.detail=part(r.msg,'url=')||r.msg;return e}"
                + "if(['player','player-engine','playback-flow','exo-source'].includes(r.tag)){e.kind='player';e.state=low.includes('error')?'err':'ok';e.badge='播放';e.title=r.tag==='playback-flow'?'播放页面/服务链路':r.tag==='player-engine'?'播放器内核事件':r.tag==='exo-source'?'媒体源创建':'播放解析/状态';e.detail=r.msg;return e}return e}"
                + "function pass(e,key){if(!diagPass(e))return false;const all=(e.raw+' '+e.title+' '+e.detail).toLowerCase();if(key&&!all.includes(key))return false;if(mode==='all')return !(e.kind==='server'&&e.state==='raw');if(mode==='error')return e.kind==='error'||e.state==='err'||e.diag&&e.diag.level==='fatal';return e.kind===mode}"
                + "function render(){try{const key=filter.value.trim().toLowerCase();const rows=raw.split('\\n').filter(Boolean).map(parse).map(explain);let shown=0,hit=0,err=0,playerProxy=0,webview=0,consoleCount=0,api=0;const html=[];rows.forEach(e=>{if(e.kind==='proxy'&&e.title.includes('命中'))hit++;if(e.kind==='error'||e.state==='err')err++;if(e.kind==='player'&&(e.title.includes('代理')||e.raw.includes('via proxy')))playerProxy++;if(e.kind==='webview')webview++;if(e.kind==='console')consoleCount++;if(e.kind==='api')api++;if(!pass(e,key))return;shown++;html.push('<div class=\"entry '+e.state+'\"><div class=\"top\"><span class=\"badge\">'+esc(e.badge)+'</span><span class=\"title\">'+esc(e.title)+'</span><span class=\"time\">'+esc(e.time)+'</span></div><div class=\"detail\">'+esc(e.detail).split('\\\\n').join('\\n')+'</div><code class=\"rawline\">'+esc(e.raw).split('\\\\n').join('\\n')+'</code></div>')});logs.innerHTML=html.join('')||'<div class=\"entry raw\"><div class=\"detail\">没有匹配日志</div></div>';summary.textContent='显示 '+shown+'/'+rows.length+' 行 · 错误 '+err+' 条 · 代理命中 '+hit+' 次 · 播放代理链路 '+playerProxy+' 次 · Console '+consoleCount+' 条 · WebView '+webview+' 条 · 站源 '+api+' 条';summary.title=summary.textContent;rawEl.hidden=true;resetX()}catch(err){rawEl.hidden=false;logs.innerHTML='<div class=\"entry err\"><div class=\"detail\">日志页面渲染失败，已显示原始日志：'+esc(err&&err.message?err.message:err)+'</div></div>';summary.textContent='渲染失败 · 已显示原始日志';resetX()}}"
                + "async function poll(){try{if(!paused){const r=await fetch('/debug/stream?afterSeq='+lastSeq+'&run='+encodeURIComponent(lastRun)+'&generation='+lastGeneration,{cache:'no-store'});const j=await r.json();lastSeq=j.newestSeq;lastRun=j.runId;lastGeneration=j.generation;const h=j.health||{};meta.textContent=(h.completeness==='partial'?'日志不完整':j.enabled?'记录中':'已关闭')+' · '+Math.ceil((j.bytes||0)/1024)+' KB';meta.classList.toggle('partial',h.completeness==='partial');meta.title=h.completeness==='partial'?'日志不完整，详见下载说明':meta.textContent;if(j.reset||j.gap||j.text){raw=(j.reset||j.gap?'':raw)+(j.text||'');if(raw.length>524288){const cut=raw.indexOf('\\n',raw.length-524288);raw=cut<0?'':raw.slice(cut+1)}rawEl.textContent=raw;render();if(stick&&!toolsOpen())scrollTo(0,document.body.scrollHeight)}}}catch(e){meta.textContent='日志连接失败，稍后重试'}setTimeout(poll,1500)}render();poll();";
    }

    private String diagnosticHtml(String localUrl, String lanUrl, String xbpqUrl, boolean enabled) {
        return String.format(java.util.Locale.ROOT, """
            <div id="drawer-backdrop" class="drawer-backdrop" hidden></div>
            <dialog id="log-drawer" class="log-drawer" role="dialog" aria-labelledby="drawer-title" aria-modal="true">
              <div class="drawer-head"><h2 id="drawer-title">日志工具</h2><button id="tools-close" aria-label="关闭日志工具">×</button></div>
              <nav class="tool-tabs" role="tablist" aria-label="日志工具分组">
                <button id="tool-tab-filters" role="tab" data-tool="filters" aria-controls="tool-panel-filters" aria-selected="true">筛选</button>
                <button id="tool-tab-diagnostics" role="tab" data-tool="diagnostics" aria-controls="tool-panel-diagnostics" aria-selected="false" tabindex="-1">诊断</button>
                <button id="tool-tab-utilities" role="tab" data-tool="utilities" aria-controls="tool-panel-utilities" aria-selected="false" tabindex="-1">工具</button>
              </nav>
              <div class="drawer-body">
                <section id="tool-panel-filters" role="tabpanel" aria-labelledby="tool-tab-filters">
                  <div class="form-section"><h3>音视频筛选</h3><div class="field-grid">
                    <label class="field">播放记录<select id="diag-trace"><option value="">所有播放记录</option></select></label>
                    <label class="field">播放尝试<select id="diag-attempt"><option value="">所有尝试</option></select></label>
                    <label class="field">链路<select id="diag-domain"><option value="">所有链路</option><option value="video">视频</option><option value="audio">音频</option></select></label>
                    <label class="field">优先级<select id="diag-priority"><option value="">所有优先级</option><option value="critical">关键事件</option><option value="error">错误</option></select></label>
                  </div><p class="hint">按播放记录和音视频链路缩小范围。</p><div class="action-row"><button id="filter-reset">重置筛选</button></div></div>
                  <div class="form-section"><h3>显示方式</h3><label class="simple"><input id="simple" type="checkbox" autocomplete="off" checked><span>只看解释，隐藏原始行</span></label></div>
                </section>
                <section id="tool-panel-diagnostics" role="tabpanel" aria-labelledby="tool-tab-diagnostics" hidden>
                  <p id="diag-status" class="hint" role="status"></p>
                  <div class="form-section"><h3>标记故障</h3><p class="hint">出现问题时标记，保留前后日志方便定位。</p><div class="action-row"><select id="diag-symptom" aria-label="故障现象"><option>黑屏</option><option>画面不动</option><option>无声</option><option>断音</option><option>音画不同步</option><option>其他</option></select><button id="diag-mark" class="primary">标记此刻</button></div></div>
                  <div class="form-section"><h3>限时深度统计</h3><p class="hint">只记录画面和声音的统计数值，不保存图像或声音；到期自动停止。</p><div class="action-row"><button id="diag-deep">开启 60 秒</button><button id="diag-stop">停止统计</button></div></div>
                  <div class="form-section"><h3>FEL 卡顿定位</h3><p class="hint">仅用于 MPV Vulkan FEL。暂时暂停播放，最多 30 秒，结束后恢复；只记录耗时，不读取画面。</p><div class="action-row"><button id="diag-fel-bind" disabled>运行绑定对照</button><button id="diag-fel-cancel" disabled>取消对照</button></div><p id="diag-fel-status" class="hint" role="status"></p></div>
                </section>
                <section id="tool-panel-utilities" role="tabpanel" aria-labelledby="tool-tab-utilities" hidden>
                  <div class="form-section"><h3>诊断包</h3><div class="action-row"><button id="diag-zip">下载诊断 ZIP</button></div><p class="hint">包含保留日志、报告和结构化事件；仅需 TXT 时使用顶部「下载」。</p></div>
                  <div class="form-section"><h3>采集管理</h3><div class="action-row"><a class="action" href="/debug/logs">刷新页面</a></div><div class="action-row"><button data-debug-action="%s">%s</button><button id="info-toggle" aria-expanded="false" aria-controls="address-panel">地址说明</button></div>
                    <section id="address-panel" hidden><p class="hint">页面显示最近日志窗口，下载包含保留的轮转日志。关闭采集会清空日志。音视频就绪回调不代表实际看见或听见。</p><div class="addr"><a href="%s">本机地址：%s</a><a href="%s">局域网地址：%s</a><a href="%s">xbpq地址：%s</a></div></section>
                  </div><div class="form-section"><h3>日志概况</h3><p id="summary" class="hint summary"></p></div>
                </section>
              </div>
              <p id="diag-feedback" class="drawer-feedback" role="status"></p>
            </dialog>
            """, enabled ? "/debug/disable" : "/debug/enable", enabled ? "关闭采集" : "开启采集",
                escape(localUrl), escape(localUrl), escape(lanUrl), escape(lanUrl), escape(xbpqUrl), escape(xbpqUrl));
    }

    private String diagnosticScript() {
        return """
            function diagEl(id){return document.getElementById('diag-'+id)}
            function diagSay(text){diagEl('feedback').textContent=text;if(!toolsOpen())openTools('diagnostics')}
            async function diagPost(path,data,binary){const r=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data||{}),cache:'no-store'});if(!r.ok){let j;try{j=await r.json()}catch(e){}throw Error(j&&j.message||'操作失败，请重试')}return binary?r.blob():r.json()}
            function diagRun(action){return Promise.resolve().then(action).catch(e=>diagSay(e.message||'操作失败'))}
            diagEl('mark').onclick=()=>diagRun(async()=>{await diagPost('/debug/diag/mark',{symptom:diagEl('symptom').value});diagSay('已标记，继续记录后 15 秒；随后下载可包含故障前后上下文')});
            diagEl('deep').onclick=()=>{if(confirm('开启本次播放的 60 秒深度统计？只记录低分辨率画面和 PCM 数值，不保存图像或声音；到期自动停止。'))diagRun(async()=>{await diagPost('/debug/diag/deep',{seconds:60,consent:true});diagSay('已开启限时统计')})};
            diagEl('stop').onclick=()=>diagRun(async()=>{await diagPost('/debug/diag/stop');diagSay('深度统计已停止')});
            diagEl('fel-bind').onclick=()=>{if(confirm('暂时暂停当前播放，运行最多 30 秒的 FEL 绑定对照？结束后恢复播放，不读取或保存画面。'))diagRun(async()=>{await diagPost('/debug/diag/fel-bind',{consent:true});diagEl('fel-bind').disabled=true;diagSay('已请求绑定对照，结果将写入日志')})};
            diagEl('fel-cancel').onclick=()=>diagRun(async()=>{await diagPost('/debug/diag/fel-bind-stop');diagSay('已请求取消，正在释放诊断资源')});
            diagEl('zip').onclick=()=>diagRun(async()=>{diagSay('正在生成诊断包…');const blob=await diagPost('/debug/diag/export',{},true);if(!blob.size)throw Error('诊断包为空，请重试 TXT 下载');const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='webhtv-av-report.zip';document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);diagSay('诊断包已生成，含日志、可读报告、结构化事件和校验信息')});
            document.querySelectorAll('[data-debug-action]').forEach(button=>button.onclick=()=>diagRun(async()=>{const r=await fetch(button.dataset.debugAction,{method:'POST',headers:{'Content-Type':'application/json'},body:'{}',cache:'no-store'});if(!r.ok){const j=await r.json();throw Error(j.message)}location.reload()}));
            function diagPass(e){const trace=diagEl('trace').value,attempt=diagEl('attempt').value,domain=diagEl('domain').value,priority=diagEl('priority').value;if(!trace&&!attempt&&!domain&&!priority)return true;const d=e.diag;if(!d)return false;if(trace&&d.trace!==trace||attempt&&String(d.attemptId)!==attempt)return false;const name=d.event||'',p=d.observed&&d.observed.property&&d.observed.property.value||'';if(domain==='video'&&!/video|surface|display|pixel/.test(name+' '+p))return false;if(domain==='audio'&&!/audio|pcm|volume|mute|avsync/.test(name+' '+p))return false;if(priority==='critical'&&d.priority!=='critical'&&!/error|fatal|warn/.test(d.level))return false;if(priority==='error'&&!/error|fatal/.test(d.level))return false;return true}
            ['trace','attempt','domain','priority'].forEach(id=>diagEl(id).onchange=()=>{updateFilterState();render()});
            function diagOptions(id,values,label){const el=diagEl(id),selected=el.value,all=Array.from(values).slice(-100);if(selected&&!all.includes(selected))all.push(selected);const signature=all.join('|');if(el.dataset.signature===signature)return;el.dataset.signature=signature;el.textContent='';const first=document.createElement('option');first.value='';first.textContent=label;el.appendChild(first);all.forEach(value=>{const option=document.createElement('option');option.value=value;option.textContent=value;el.appendChild(option)});el.value=selected}
            function diagFilters(){const traces=new Set(),attempts=new Set();raw.split('\\n').forEach(line=>{const r=parse(line);if(r.tag!=='av-diag')return;try{const d=JSON.parse(r.msg);if(d.trace&&d.trace!=='none')traces.add(d.trace);if(d.attemptId)attempts.add(String(d.attemptId))}catch(e){}});diagOptions('trace',traces,'所有播放记录');diagOptions('attempt',attempts,'所有尝试')}
            async function diagStatus(){try{const r=await fetch('/debug/diag/status',{cache:'no-store'}),j=await r.json();diagEl('status').textContent=(j.engine||'暂无播放')+(j.deepRemainingMs>0?' · 深度统计剩余 '+Math.ceil(j.deepRemainingMs/1000)+' 秒':' · 标准记录');const s=j.felBindProbeState||'unavailable',labels={unavailable:'当前播放不支持对照',ready:'可运行',queued:'等待播放器响应',armed:'等待下一帧',quiescing:'准备暂停播放',running:'正在测量',cancelling:'正在取消',completed:'已完成，结果见日志',cancelled:'已取消',failed:'未完成，请查看日志并确认正在播放', 'timed-out':'已到时间上限，结果见日志'};diagEl('fel-status').textContent=labels[s]||s;diagEl('fel-bind').disabled=s==='unavailable'||j.felBindProbeActive||j.deepRemainingMs>0;diagEl('fel-cancel').disabled=!j.felBindProbeActive;diagEl('deep').disabled=!!j.felBindProbeActive;diagFilters()}catch(e){}setTimeout(diagStatus,3000)}diagStatus();
            """;
    }

    private String toolbarScript() {
        return """
            const drawer=document.getElementById('log-drawer'),toolsButton=document.getElementById('tools-open'),backdrop=document.getElementById('drawer-backdrop'),searchPanel=document.getElementById('search-panel'),searchToggle=document.getElementById('search-toggle'),wideSearch=matchMedia('(min-width:800px)');
            let drawerOpener=null,activeTool='filters',searchExpanded=false;
            function toolsOpen(){return document.getElementById('log-drawer').hasAttribute('open')}
            function selectToolTab(name,focus){activeTool=name;document.querySelectorAll('[data-tool]').forEach(button=>{const active=button.dataset.tool===name;button.setAttribute('aria-selected',active);button.tabIndex=active?0:-1;document.getElementById(button.getAttribute('aria-controls')).hidden=!active;if(active&&focus)button.focus()});document.querySelector('.drawer-body').scrollTop=0}
            function openTools(name){const opening=!toolsOpen();if(opening){drawerOpener=document.activeElement;document.body.classList.add('drawer-open');if(typeof drawer.showModal==='function')drawer.showModal();else{drawer.setAttribute('open','');backdrop.hidden=false;document.querySelector('main').setAttribute('aria-hidden','true')}}selectToolTab(name||activeTool,opening)}
            function afterToolsClose(){document.body.classList.remove('drawer-open');backdrop.hidden=true;document.querySelector('main').removeAttribute('aria-hidden');if(drawerOpener&&document.contains(drawerOpener))drawerOpener.focus({preventScroll:true})}
            function closeTools(){if(!toolsOpen())return;if(typeof drawer.close==='function')drawer.close();else{drawer.removeAttribute('open');afterToolsClose()}}
            toolsButton.onclick=()=>openTools();document.getElementById('tools-close').onclick=closeTools;backdrop.onclick=closeTools;
            drawer.addEventListener('close',afterToolsClose);drawer.addEventListener('cancel',event=>{event.preventDefault();closeTools()});
            drawer.addEventListener('click',event=>{if(event.target===drawer){const r=drawer.getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)closeTools()}});
            drawer.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();closeTools()}if(event.key==='Tab'){const nodes=Array.from(drawer.querySelectorAll('button,a[href],input,select,summary,[tabindex]')).filter(el=>!el.disabled&&el.tabIndex>=0&&el.getClientRects().length);const first=nodes[0],last=nodes[nodes.length-1];if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus()}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus()}}});
            function arrowItem(event,buttons){const index=buttons.indexOf(event.target);if(index<0)return null;let next;if(event.key==='ArrowRight')next=(index+1)%buttons.length;else if(event.key==='ArrowLeft')next=(index+buttons.length-1)%buttons.length;else if(event.key==='Home')next=0;else if(event.key==='End')next=buttons.length-1;else return null;event.preventDefault();return buttons[next]}
            document.querySelectorAll('[data-tool]').forEach(button=>button.onclick=()=>selectToolTab(button.dataset.tool,false));
            document.querySelector('.tool-tabs').onkeydown=event=>{const button=arrowItem(event,Array.from(document.querySelectorAll('[data-tool]')));if(button)selectToolTab(button.dataset.tool,true)};
            function selectCategory(button){document.querySelectorAll('.chip').forEach(item=>{const active=item===button;item.classList.toggle('on',active);item.setAttribute('aria-pressed',active);item.tabIndex=active?0:-1});mode=button.dataset.mode;render();if(!toolsOpen())button.scrollIntoView({block:'nearest',inline:'nearest'})}
            document.querySelector('.tabs').onkeydown=event=>{const button=arrowItem(event,Array.from(document.querySelectorAll('.chip')));if(button){button.focus({preventScroll:true});selectCategory(button)}};
            function layoutSearch(){const open=wideSearch.matches||searchExpanded;searchPanel.hidden=!open;searchToggle.hidden=wideSearch.matches;searchToggle.setAttribute('aria-expanded',open)}
            function hideSearch(){searchExpanded=false;layoutSearch();searchToggle.focus({preventScroll:true})}
            searchToggle.onclick=()=>{searchExpanded=!searchExpanded;layoutSearch();if(searchExpanded)filter.focus()};document.getElementById('search-hide').onclick=hideSearch;
            filter.addEventListener('keydown',event=>{if(event.key==='Escape'&&!wideSearch.matches){event.preventDefault();hideSearch()}});
            if(wideSearch.addEventListener)wideSearch.addEventListener('change',layoutSearch);else wideSearch.addListener(layoutSearch);
            function updateFilterState(){const count=['trace','attempt','domain','priority'].filter(id=>diagEl(id).value).length+(filter.value.trim()?1:0),badge=document.getElementById('filter-count');badge.textContent=count;badge.hidden=!count;toolsButton.classList.toggle('filtered',count>0);toolsButton.setAttribute('aria-label',count?'日志工具，'+count+'项筛选生效':'日志工具');searchToggle.classList.toggle('on',!!filter.value.trim());searchToggle.title=filter.value.trim()?'搜索：'+filter.value.trim():'搜索日志'}
            document.getElementById('filter-reset').onclick=()=>{filter.value='';['trace','attempt','domain','priority'].forEach(id=>diagEl(id).value='');updateFilterState();selectCategory(document.querySelector('[data-mode=all]'))};
            document.getElementById('info-toggle').onclick=()=>{const panel=document.getElementById('address-panel'),button=document.getElementById('info-toggle');panel.hidden=!panel.hidden;button.setAttribute('aria-expanded',!panel.hidden)};
            layoutSearch();updateFilterState();
            """;
    }

    private String json(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    private String escape(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
