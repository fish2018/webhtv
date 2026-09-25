package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.net.http.SslError;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.config.AdBlockStatsStore;
import com.fongmi.android.tv.api.config.RuleConfig;
import com.fongmi.android.tv.api.config.UserAdRuleStore;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.WebDialog;
import com.fongmi.android.tv.utils.RuleIdUtil;
import com.fongmi.android.tv.utils.WebSniffHeaders;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class CustomWebView extends WebView implements DialogInterface.OnDismissListener {

    private static final String TAG = CustomWebView.class.getSimpleName();

    private static final Pattern PLAYER = Pattern.compile("player.*https?://");
    private static final String BLANK = "about:blank";
    private static final int MAX_URLS = 5;

    private final AtomicReference<ParseCallback> callbackRef = new AtomicReference<>();
    /** 调用方指定的嗅探正则（猫源 /msg 的 sniff 会带 rule）；为空时用默认判定。 */
    private Pattern sniff;
    private LinkedHashSet<String> urls;
    private WebResourceResponse empty;
    private WebDialog dialog;
    private Runnable timer;
    private boolean stopped;
    private boolean detect;
    private String click;
    private String from;
    private String key;
    private String url;
    private Map<String, String> pageHeaders;
    private int mediaProbeCount;

    public static CustomWebView create(@NonNull Context context) {
        return new CustomWebView(context);
    }

    private CustomWebView(@NonNull Context context) {
        super(context);
        initSettings();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initSettings() {
        timer = () -> stop(true);
        urls = new LinkedHashSet<>();
        empty = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
        WebViewUtil.configureBase(this, "parse");
        WebSettings setting = getSettings();
        setting.setSupportZoom(true);
        setting.setUseWideViewPort(true);
        setting.setDatabaseEnabled(true);
        setting.setDomStorageEnabled(true);
        setting.setJavaScriptEnabled(true);
        setting.setBuiltInZoomControls(true);
        setting.setDisplayZoomControls(false);
        setting.setLoadWithOverviewMode(true);
        setting.setUserAgentString(Setting.getUa());
        setting.setMediaPlaybackRequiresUserGesture(false);
        setting.setJavaScriptCanOpenWindowsAutomatically(false);
        setting.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        setWebChromeClient(webChromeClient());
        setWebViewClient(webViewClient());
    }

    public CustomWebView start(String key, String from, Map<String, String> headers, String url, String click, ParseCallback callback, boolean detect) {
        SpiderDebug.log(TAG, "key=%s, from=%s, click=%s, url=%s, headers=%s", key, from, click, url, headers);
        App.post(timer, Constant.TIMEOUT_PARSE_WEB);
        callbackRef.set(callback);
        this.detect = detect;
        this.click = click;
        this.from = from;
        this.key = key;
        this.url = url;
        this.mediaProbeCount = 0;
        start(headers);
        return this;
    }

    private void start(Map<String, String> headers) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        Map<String, String> pageHeaders = WebSniffHeaders.forPage(headers, getSettings().getUserAgentString());
        this.pageHeaders = new HashMap<>(pageHeaders);
        if (!this.pageHeaders.containsKey(HttpHeaders.REFERER)) this.pageHeaders.put(HttpHeaders.REFERER, url);
        checkHeader(url, pageHeaders);
        SpiderDebug.log("webview-parse", "page headers input=%s applied=%s ua=%s", headers == null ? "[]" : headers.keySet(), pageHeaders.keySet(), getSettings().getUserAgentString());
        loadUrl(url, pageHeaders);
    }

    private void checkHeader(String url, Map<String, String> headers) {
        for (String key : headers.keySet()) {
            if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) getSettings().setUserAgentString(headers.get(key));
            else if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) CookieManager.getInstance().setCookie(url, headers.get(key));
        }
    }

    private WebViewClient webViewClient() {
        return new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String host = request.getUrl().getHost();
                if (TextUtils.isEmpty(host) || isAd(host)) return empty;
                Map<String, String> headers = request.getRequestHeaders();
                if (url.contains("/cdn-cgi/challenge-platform/")) post(() -> showDialog());
                boolean player = PLAYER.matcher(url).find();
                boolean video = isVideoFormat(url) && !isPlaceholderVideoUrl(url);
                if (SpiderDebug.isEnabled() && (player || video || host.contains("hls") || url.contains("m3u8") || url.contains("mp4") || url.contains("video"))) {
                    SpiderDebug.log("webview-sniff", "from=%s host=%s path=%s player=%s video=%s", from, host, request.getUrl().getPath(), player, video);
                }
                if (detect && player && addUrl(url)) onParseAdd(headers, url);
                else if (video) onParseSuccess(headers, url);
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (needsWasmSignShim(url)) view.evaluateJavascript(WASM_SIGN_SHIM, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.equals(BLANK)) return;
                SpiderDebug.log("webview-parse", "page finished key=%s from=%s url=%s title=%s", key, from, url, view.getTitle());
                evaluate(getScript(url), 0);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                SpiderDebug.log("webview-parse", "resource error main=%s code=%s desc=%s url=%s", request.isForMainFrame(), error.getErrorCode(), error.getDescription(), request.getUrl());
            }

            @Override
            @SuppressLint("WebViewClientOnReceivedSslError")
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        };
    }

    private WebChromeClient webChromeClient() {
        return new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                if (message != null) SpiderDebug.log("webview-console", "%s %s:%s %s", message.messageLevel(), message.sourceId(), message.lineNumber(), message.message());
                return super.onConsoleMessage(message);
            }
        };
    }


    /**
     * 部分解析站（虾米/jx.m3u8.tv/jx.hls.one 等同源引擎）的签名脚本依赖
     * WebAssembly reference-types 与多值返回，只有 Chrome 96+ 的 WebView 才能实例化。
     * 老模拟器 WebView（如 91）实例化失败后签名不会生成，解析接口根本不会被调用，
     * 表现为“打开解析页后一直嗅探不到地址”。
     *
     * 这里在页面脚本执行前注入同样的签名兜底实现：
     * 先按原样调用 WebAssembly.instantiate，成功则完全走原逻辑；只有失败时才回退到
     * 等价的 JS HMAC-SHA256（sign = HMAC(key, be64(tm) || url)），
     * 让解析页自己完成后续的请求、解密与播放器构建。
     */
    private static final String WASM_SIGN_SHIM = """
(function () {
  if (window.__whWasmShim) return;
  window.__whWasmShim = 1;
  if (typeof WebAssembly === 'undefined' || typeof WebAssembly.instantiate !== 'function') return;
  var KEY = ']QAK[I]DGUHp4$%,145"0$3&3%4';
  var K0 = [0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19];
  var KT = [0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
            0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
            0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
            0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
            0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
            0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
            0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
            0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];

  function rotr(x, n) { return ((x >>> n) | (x << (32 - n))) >>> 0; }

  function sha256(bytes) {
    var len = bytes.length;
    var withPad = new Uint8Array(((len + 9 + 63) >> 6) << 6);
    withPad.set(bytes, 0);
    withPad[len] = 0x80;
    var bitLenHi = Math.floor(len / 0x20000000);
    var bitLenLo = (len << 3) >>> 0;
    var dv = new DataView(withPad.buffer);
    dv.setUint32(withPad.length - 8, bitLenHi);
    dv.setUint32(withPad.length - 4, bitLenLo);
    var h = K0.slice(0), w = new Array(64);
    for (var off = 0; off < withPad.length; off += 64) {
      for (var i = 0; i < 16; i++) w[i] = dv.getUint32(off + i * 4);
      for (i = 16; i < 64; i++) {
        var s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3);
        var s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10);
        w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
      }
      var a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
      for (i = 0; i < 64; i++) {
        var S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
        var ch = (e & f) ^ (~e & g);
        var t1 = (hh + S1 + ch + KT[i] + w[i]) >>> 0;
        var S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
        var mj = (a & b) ^ (a & c) ^ (b & c);
        var t2 = (S0 + mj) >>> 0;
        hh = g; g = f; f = e; e = (d + t1) >>> 0;
        d = c; c = b; b = a; a = (t1 + t2) >>> 0;
      }
      h[0] = (h[0] + a) >>> 0; h[1] = (h[1] + b) >>> 0; h[2] = (h[2] + c) >>> 0; h[3] = (h[3] + d) >>> 0;
      h[4] = (h[4] + e) >>> 0; h[5] = (h[5] + f) >>> 0; h[6] = (h[6] + g) >>> 0; h[7] = (h[7] + hh) >>> 0;
    }
    var out = new Uint8Array(32);
    var odv = new DataView(out.buffer);
    for (i = 0; i < 8; i++) odv.setUint32(i * 4, h[i]);
    return out;
  }

  function hmacSha256(keyBytes, msgBytes) {
    var block = new Uint8Array(64);
    var k = keyBytes.length > 64 ? sha256(keyBytes) : keyBytes;
    block.set(k, 0);
    var ipad = new Uint8Array(64), opad = new Uint8Array(64);
    for (var i = 0; i < 64; i++) { ipad[i] = block[i] ^ 0x36; opad[i] = block[i] ^ 0x5c; }
    var inner = new Uint8Array(64 + msgBytes.length);
    inner.set(ipad, 0); inner.set(msgBytes, 64);
    var ih = sha256(inner);
    var outer = new Uint8Array(96);
    outer.set(opad, 0); outer.set(ih, 64);
    return sha256(outer);
  }

  var encoder = typeof TextEncoder !== 'undefined' ? new TextEncoder() : null;
  function utf8Bytes(str) {
    if (encoder) return encoder.encode(str);
    var s = unescape(encodeURIComponent(str)), b = new Uint8Array(s.length);
    for (var i = 0; i < s.length; i++) b[i] = s.charCodeAt(i);
    return b;
  }

  function hex64(tm, url) {
    var msg = new Uint8Array(8 + utf8Bytes(url).length);
    var v = tm;
    for (var i = 7; i >= 0; i--) { msg[i] = Number(v & BigInt(0xff)); v = v >> BigInt(8); }
    msg.set(utf8Bytes(url), 8);
    return hmacSha256(Uint8Array.from(KEY.split('').map(function (c) { return c.charCodeAt(0); })), msg);
  }

  function fallbackInstance() {
    var buffer = new ArrayBuffer(1 << 20);
    var heap = new Uint8Array(buffer);
    var next = 4096;
    return {
      instance: {
        exports: {
          memory: { buffer: buffer },
          alloc: function (size) { var p = next; next = (next + size + 15) & ~15; return p; },
          free: function () { },
          gen_sign: function (tm, ptr, len, out) {
            var url = '';
            for (var i = 0; i < len; i++) url += String.fromCharCode(heap[ptr + i]);
            url = decodeURIComponent(escape(url));
            var sig = hex64(tm, url);
            heap.set(sig, out);
            return 0;
          }
        }
      }
    };
  }

  var real = WebAssembly.instantiate;
  WebAssembly.instantiate = function () {
    var args = arguments;
    var p;
    try { p = real.apply(WebAssembly, args); } catch (e) { p = Promise.reject(e); }
    return Promise.resolve(p).then(function (r) { return r; }, function () {
      try { console.log('[wh-wasm] wasm unavailable, using JS HMAC shim'); } catch (e) { }
      return fallbackInstance();
    });
  };
})();
""";

    /** 只有这几个解析站需要 WASM 签名兜底，避免影响其它解析/嗅探站点。 */
    private static boolean needsWasmSignShim(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("xmflv.com") || lower.contains("m3u8.tv") || lower.contains("hls.one");
    }

    private boolean addUrl(String url) {
        if (urls.size() > MAX_URLS) urls.clear();
        return urls.add(url);
    }

    private void showDialog() {
        if (dialog != null || App.activity() == null) return;
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
        dialog = WebDialog.create(this).show();
        App.removeCallbacks(timer);
    }

    private void hideDialog() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        stop(true);
    }

    private List<String> getScript(String url) {
        List<String> script = new ArrayList<>(Sniffer.getScript(Uri.parse(url)));
        if (TextUtils.isEmpty(click) || script.contains(click)) return script;
        script.add(0, click);
        return script;
    }

    private void evaluate(List<String> script, int index) {
        if (index >= script.size()) {
            probeDomMedia(0);
            return;
        }
        String js = script.get(index);
        if (TextUtils.isEmpty(js)) {
            evaluate(script, index + 1);
        } else {
            evaluateJavascript(js, value -> evaluate(script, index + 1));
        }
    }

    /**
     * 部分解析页给 video/src 分配的是没有 .m3u8/.mp4 后缀的签名地址，
     * 这类地址不会经过 URL 正则嗅探；从 DOM/performance 资源中补采一次。
     */
    private void probeDomMedia(int attempt) {
        if (stopped || attempt > 6 || mediaProbeCount++ > 12) return;
        String js = "(function(){var a=[];var seen=[];function walk(d){if(!d||seen.indexOf(d)>=0)return;seen.push(d);try{d.querySelectorAll('video,audio,video source,audio source').forEach(function(v){var u=v.currentSrc||v.src;if(u)a.push({u:u,k:'video'});});d.querySelectorAll('iframe,frame').forEach(function(f){try{walk(f.contentDocument);}catch(e){}});try{d.defaultView.performance.getEntriesByType('resource').forEach(function(e){if(e.initiatorType==='video'||e.initiatorType==='audio')a.push({u:e.name,k:'resource'});});}catch(e){}}walk(document);return JSON.stringify(a);})()";
        evaluateJavascript(js, value -> {
            try {
                String payload = value == null ? "" : new org.json.JSONTokener(value).nextValue().toString();
                org.json.JSONArray array = new org.json.JSONArray(payload);
                if (SpiderDebug.isEnabled() && array.length() > 0) SpiderDebug.log("webview-probe", "from=%s attempt=%d candidates=%s", from, attempt, payload);
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    String candidate = item.optString("u", "");
                    String kind = item.optString("k", "");
                    if (("video".equals(kind) || "resource".equals(kind)) && isHttpMediaCandidate(candidate)) {
                        onParseSuccess(pageHeaders == null ? new HashMap<>() : new HashMap<>(pageHeaders), candidate);
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
            if (!stopped) postDelayed(() -> probeDomMedia(attempt + 1), 1000);
        });
    }

    private boolean isHttpMediaCandidate(String candidate) {
        if (TextUtils.isEmpty(candidate) || !candidate.regionMatches(true, 0, "http", 0, 4)) return false;
        if (candidate.equals(url) || candidate.contains("/favicon")) return false;
        return !isPlaceholderVideoUrl(candidate) && (Sniffer.isVideoFormat(candidate) || !candidate.contains(".js") && !candidate.contains(".css") && !candidate.contains(".html"));
    }

    private boolean isPlaceholderVideoUrl(String candidate) {
        String lower = candidate == null ? "" : candidate.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("/404_") || lower.contains("/404.mp4") || lower.contains("/error.mp4");
    }

    private boolean isAd(String host) {
        for (String ad : RuleConfig.get().getAds()) {
            if (Util.containOrMatch(host, ad)) {
                // 记录拦截统计
                String ruleId = findRuleIdByAdPattern(ad);
                AdBlockStatsStore.recordBlock(key, ruleId);
                return true;
            }
        }
        return false;
    }

    /**
     * 根据广告规则字符串查找对应的规则 ID
     */
    private String findRuleIdByAdPattern(String adPattern) {
        if (TextUtils.isEmpty(adPattern)) return "unknown";

        // 查找用户自定义规则
        for (UserAdRule rule : UserAdRuleStore.load()) {
            if (!rule.isEnabled()) continue;
            if (matchesRule(adPattern, rule.getHosts()) ||
                matchesRule(adPattern, rule.getRegex()) ||
                matchesRule(adPattern, rule.getExclude())) {
                return rule.getId();
            }
        }

        // 查找默认规则（Rule 使用 getHosts() 而非 getAds()，且需要计算 ID）
        for (Rule rule : RuleConfig.get().getDefaultRules()) {
            List<String> ruleHosts = rule.getHosts();
            if (ruleHosts != null && ruleHosts.contains(adPattern)) {
                return RuleIdUtil.computeRuleId(rule);
            }
        }

        return "unknown";
    }

    /**
     * 检查广告规则字符串是否存在于规则的某个字段列表中
     */
    private boolean matchesRule(String adPattern, List<String> ruleField) {
        if (ruleField == null || ruleField.isEmpty()) return false;
        for (String item : ruleField) {
            if (item != null && adPattern.equals(item.trim())) return true;
        }
        return false;
    }

    /** 指定自定义嗅探正则，需在 start() 之前调用。 */
    public CustomWebView sniff(Pattern pattern) {
        this.sniff = pattern;
        return this;
    }

    private boolean isVideoFormat(String url) {
        try {
            if (!detect && url.equals(this.url)) return false;
            if (sniff != null) return sniff.matcher(url).find();
            Spider spider = VodConfig.get().getSite(key).spider();
            if (spider.manualVideoCheck()) return spider.isVideoFormat(url);
            return Sniffer.isVideoFormat(url);
        } catch (Exception ignored) {
            return Sniffer.isVideoFormat(url);
        }
    }

    private void onParseAdd(Map<String, String> headers, String url) {
        ParseCallback cb = callbackRef.get();
        if (cb == null) return;
        post(() -> CustomWebView.create(App.get()).start(key, from, headers, url, click, cb, false));
    }

    private void onParseSuccess(Map<String, String> headers, String url) {
        ParseCallback cb = callbackRef.getAndSet(null);
        if (cb != null) cb.onParseSuccess(headers, url, from);
        post(() -> stop(false));
    }

    private void onParseError() {
        ParseCallback cb = callbackRef.getAndSet(null);
        if (cb != null) cb.onParseError();
    }

    public void stop(boolean error) {
        if (stopped) return;
        stopped = true;
        hideDialog();
        stopLoading();
        loadUrl(BLANK);
        App.removeCallbacks(timer);
        if (error) onParseError();
        else callbackRef.set(null);
    }
}
