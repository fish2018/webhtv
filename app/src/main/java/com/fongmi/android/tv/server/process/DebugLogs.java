package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.crawler.DebugLogStore;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public class DebugLogs implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/debug/logs") || url.startsWith("/debug/stream") || url.startsWith("/debug/clear") || url.startsWith("/debug/enable") || url.startsWith("/debug/disable");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (url.startsWith("/debug/enable")) {
            DebugLogStore.setEnabled(true);
            return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, ""), "/debug/logs");
        }
        if (url.startsWith("/debug/disable")) {
            DebugLogStore.setEnabled(false);
            return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, ""), "/debug/logs");
        }
        if (url.startsWith("/debug/clear")) {
            DebugLogStore.clear();
            return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, ""), "/debug/logs");
        }
        if (url.startsWith("/debug/stream")) return stream();
        if (url.startsWith("/debug/logs.txt")) return download();
        return page();
    }

    private Response page() {
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html());
        return noCache(response, null);
    }

    private Response download() {
        String text = DebugLogStore.text();
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", text);
        response.addHeader("Content-Disposition", "attachment; filename=webhtv-debug-log.txt");
        response.addHeader("Content-Length", String.valueOf(text.getBytes(StandardCharsets.UTF_8).length));
        return noCache(response, null);
    }

    private Response stream() {
        String text = DebugLogStore.text();
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", "{\"enabled\":" + DebugLogStore.isEnabled() + ",\"size\":" + DebugLogStore.size() + ",\"text\":\"" + json(text) + "\"}");
        return noCache(response, null);
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
        String lanUrl = Server.get().getAddress(false) + "/debug/logs";
        boolean enabled = DebugLogStore.isEnabled();
        return "<!doctype html>"
                + "<html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">"
                + "<title>调试日志</title>"
                + "<style>"
                + "html,body{margin:0;background:#101114;color:#e8eaed;font:14px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;}"
                + "header{position:sticky;top:0;z-index:2;display:flex;gap:8px;align-items:center;padding:10px;background:rgba(16,17,20,.94);border-bottom:1px solid rgba(255,255,255,.1);backdrop-filter:blur(12px)}"
                + "h1{margin:0 10px 0 0;font-size:17px;font-weight:650;white-space:nowrap}.meta{margin-left:auto;color:#a9adb7;font-size:12px;white-space:nowrap}"
                + "a,button{appearance:none;border:1px solid rgba(255,255,255,.18);border-radius:7px;background:rgba(255,255,255,.08);color:#f5f7fa;padding:7px 10px;text-decoration:none;font:inherit;cursor:pointer}"
                + "a:active,button:active{background:rgba(255,255,255,.16)}main{padding:12px}.hint{margin:0 0 10px;color:#a9adb7;font-size:12px}"
                + ".addr{display:grid;gap:6px;margin:0 0 10px;color:#c6cad2;font-size:12px}.addr a{display:block;overflow-wrap:anywhere;padding:8px 10px}"
                + "pre{box-sizing:border-box;margin:0;min-height:calc(100vh - 96px);white-space:pre-wrap;word-break:break-word;overflow-wrap:anywhere;border:1px solid rgba(255,255,255,.1);border-radius:8px;background:#08090b;color:#d7dae0;padding:12px;font:12px/1.55 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}"
                + "@media(max-width:520px){header{flex-wrap:wrap}.meta{width:100%;margin-left:0}a,button{padding:6px 8px}main{padding:8px}pre{font-size:11px}}"
                + "</style></head><body>"
                + "<header><h1>调试日志</h1><a href=\"/debug/logs\">刷新</a><a href=\"/debug/logs.txt\">下载</a><a href=\"/debug/clear\">清空</a><a href=\"" + (enabled ? "/debug/disable" : "/debug/enable") + "\">" + (enabled ? "关闭" : "开启") + "</a><span id=\"meta\" class=\"meta\">" + (enabled ? "开启" : "关闭") + " · " + DebugLogStore.size() + " 行</span></header>"
                + "<main><p class=\"hint\">本页显示 App 当前进程内调试日志。调试日志默认关闭，开启后记录 WebHome、SDK、HTTP 服务、爬虫请求和播放链路；关闭会自动清空。</p>"
                + "<div class=\"addr\"><a href=\"" + escape(localUrl) + "\">本机地址：" + escape(localUrl) + "</a><a href=\"" + escape(lanUrl) + "\">局域网地址：" + escape(lanUrl) + "</a></div><pre id=\"logs\">" + logs + "</pre></main>"
                + "<script>const pre=document.getElementById('logs'),meta=document.getElementById('meta');let stick=true;addEventListener('scroll',()=>{stick=(innerHeight+scrollY)>=(document.body.scrollHeight-60)});async function poll(){try{const r=await fetch('/debug/stream?_='+Date.now(),{cache:'no-store'});const j=await r.json();pre.textContent=j.text;meta.textContent=(j.enabled?'开启':'关闭')+' · '+j.size+' 行';if(stick)scrollTo(0,document.body.scrollHeight)}catch(e){}setTimeout(poll,1000)}poll();</script>"
                + "</body></html>";
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
