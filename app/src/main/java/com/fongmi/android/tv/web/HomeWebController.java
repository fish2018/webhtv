package com.fongmi.android.tv.web;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;

import androidx.webkit.ScriptHandler;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.github.catvod.crawler.SpiderDebug;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.fongmi.android.tv.web.ext.WebHomeExtension;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class HomeWebController {

    private static final String BRIDGE = "fongmiBridge";
    private static final int SLOW_KEY_MS = 24;
    private static final long LOAD_TIMEOUT_MS = 15000;
    private static final long EXTENSION_RELOAD_MIN_INTERVAL_MS = 5000;
    private static final long MEDIA_PAUSE_LAUNCH_TIMEOUT_MS = 250;
    private static final int MAX_REMOTE_MESSAGE_BYTES = 64 * 1024;
    private static final int MAX_REMOTE_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_REMOTE_IN_FLIGHT = 2;
    private static final long REMOTE_ACTION_INTERVAL_MS = 400;
    private static final String PAUSE_PAGE_MEDIA_SCRIPT = """
            (function() {
              const pause = function(root) {
                try {
                  root.querySelectorAll('video,audio').forEach(function(media) {
                    try { media.pause(); } catch (ignored) {}
                  });
                  root.querySelectorAll('iframe').forEach(function(frame) {
                    try {
                      if (frame.contentDocument) pause(frame.contentDocument);
                    } catch (ignored) {}
                  });
                } catch (ignored) {}
              };
              pause(document);
            })();
            """;
    private static HomeWebController active;
    private static boolean extensionReloadRequested;

    record ThemeRuntimeSnapshot(WebThemePageHost.Snapshot page, WebThemeSession.Snapshot session) {
    }

    private record ManifestActivation(Site site, String sourceKey, WebHomeTarget configured,
            WebThemePage page, WebThemeRoute route, String revision, boolean rollbackAvailable) {
        boolean matches(WebHomeTarget target) {
            return target != null && target.isV2() && target.getManifest() != null
                    && configured.getUrl().equals(target.getManifest().getManifestUrl())
                    && page == target.getPage();
        }
    }

    private final Listener listener;
    private final Activity activity;
    private final Set<String> injectedExtensions;
    private final Set<PendingNativePlayback> pendingNativePlaybacks;
    private final RemoteRequestGate remoteRequestGate;
    private final RemoteActionGate remoteActionGate;
    private final WebThemeManifestResolver manifestResolver;
    private final WebThemePageHost pageHost;
    private final WebThemeSession themeSession;
    private final Object themeStateLock = new Object();
    private final Runnable extensionReloadRunnable;
    private final boolean debugTools;
    private volatile WebView webView;
    private CookieManager cookieManager;
    private WebThemeDataIsolation.DataProfile dataProfile = WebThemeDataIsolation.trustedProfile();
    private boolean dataProfileReady = true;
    private HomeWebBridge bridge;
    private WebHomeThemeBridge themeBridge;
    private final float density;
    private ScriptHandler documentStartHandler;
    private boolean remoteMessageListener;
    private String bridgeKey = "";
    private volatile String remoteBridgeOrigin = "";
    private volatile String remoteBridgeNonce = "";
    private volatile int remoteBridgeGeneration;
    private Map<String, String> pageHeaders = Collections.emptyMap();
    private String documentStartKey;
    private String defaultUserAgent;
    private String homePage;
    private String homeIdentity;
    private String lastPageUrl;
    private WebHomeRawAdapter rawAdapter;
    private WebHomeViewport viewport = WebHomeViewport.EMPTY;
    private String lastViewportKey;
    private long pauseAt;
    private long lastKeyAt;
    private long lastExtensionReloadAt;
    private int inlineEvaluationCount;
    private int loadToken;
    private int loadTimeoutRecoveries;
    private int manifestLoadToken;
    private ManifestActivation manifestActivation;
    private boolean manifestRollbackInProgress;
    private volatile boolean bridgeReady;
    private volatile boolean sdkReady;
    private volatile boolean paused;
    private volatile boolean destroyed;

    public HomeWebController(Activity activity, WebView webView, Listener listener) {
        this(activity, webView, listener, false);
    }

    public HomeWebController(Activity activity, WebView webView, Listener listener, boolean debugTools) {
        this.activity = activity;
        this.webView = webView;
        this.listener = listener;
        this.debugTools = debugTools;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.injectedExtensions = new HashSet<>();
        this.pendingNativePlaybacks = new HashSet<>();
        this.remoteRequestGate = new RemoteRequestGate(MAX_REMOTE_IN_FLIGHT);
        this.remoteActionGate = new RemoteActionGate(REMOTE_ACTION_INTERVAL_MS);
        this.manifestResolver = new WebThemeManifestResolver(activity,
                com.fongmi.android.tv.BuildConfig.FLAVOR_mode);
        this.pageHost = new WebThemePageHost();
        this.themeSession = new WebThemeSession();
        this.extensionReloadRunnable = this::consumeExtensionReload;
        active = this;
        init();
    }

    public static void requestExtensionReload() {
        extensionReloadRequested = true;
        HomeWebController controller = active;
        if (controller != null) App.post(controller::consumeExtensionReload);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void init() {
        if (debugTools) WebView.setWebContentsDebuggingEnabled(true);
        WebViewUtil.configureHome(webView);
        cookieManager = dataProfile.isolated()
                ? WebViewCompat.getProfile(webView).getCookieManager()
                : CookieManager.getInstance();
        defaultUserAgent = webView.getSettings().getUserAgentString();
        if (Util.isLeanback()) webView.setNextFocusUpId(R.id.title);
        webView.setBackgroundColor(Color.TRANSPARENT);
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, !dataProfile.isolated());
        webView.setOnFocusChangeListener((v, hasFocus) -> SpiderDebug.log("webhome-focus", "webview focus=%s visible=%s url=%s", hasFocus, isVisible(), safeLogUrl(webView.getUrl())));
        webView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> injectViewport());
        themeBridge = new WebHomeThemeBridge(this, activity);
        bridge = new HomeWebBridge(this, activity, webView, themeBridge);
        bridgeKey = "";
        remoteMessageListener = false;
        webView.setWebViewClient(client());
        webView.setWebChromeClient(chrome());
        WebViewUtil.logProvider("webhome");
    }

    private boolean configureBridge(WebHomeTarget resolved, boolean renewRemote) {
        boolean remote = resolved != null && resolved.isRemoteGlobal();
        String origin = remote ? resolved.getOriginRule() : "";
        String desiredKey = remote ? "remote:" + origin : "trusted";
        if ((!remote || !renewRemote) && desiredKey.equals(bridgeKey) && (!remote || remoteMessageListener)) return true;
        invalidateRemoteSession();
        webView.removeJavascriptInterface(BRIDGE);
        bridgeKey = "";
        webView.getSettings().setMixedContentMode(remote ? WebSettings.MIXED_CONTENT_NEVER_ALLOW : WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.getSettings().setAllowFileAccess(!remote);
        webView.getSettings().setAllowContentAccess(!remote);
        cookieManager.setAcceptThirdPartyCookies(webView, !remote);
        if (!remote) {
            webView.addJavascriptInterface(bridge, BRIDGE);
            bridgeKey = desiredKey;
            return true;
        }
        if (TextUtils.isEmpty(origin)) return false;
        remoteBridgeOrigin = origin;
        remoteBridgeNonce = newRemoteNonce();
        int generation = remoteBridgeGeneration;
        if (!registerRemoteMessageListener(origin, generation)) {
            invalidateRemoteSession();
            return false;
        }
        bridgeKey = desiredKey;
        return true;
    }

    private boolean registerRemoteMessageListener(String allowedOrigin, int generation) {
        remoteMessageListener = false;
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false;
        try {
            WebViewCompat.addWebMessageListener(webView, "fongmiRemote", Collections.singleton(allowedOrigin),
                    (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                        String actualOrigin = sourceOrigin == null ? null : sourceOrigin.toString();
                        String data = message.getData();
                        if (!WebHomeThemePolicy.allowsMessage(allowedOrigin, actualOrigin, isMainFrame)
                                || message.getType() != WebMessageCompat.TYPE_STRING || data == null
                                || data.length() > MAX_REMOTE_MESSAGE_BYTES
                                || data.getBytes(StandardCharsets.UTF_8).length > MAX_REMOTE_MESSAGE_BYTES) return;
                        if (!isRemoteSession(allowedOrigin, generation)) return;
                        if (!remoteRequestGate.tryAcquire()) {
                            replyRemoteError(data, WebThemeErrorCode.RATE_LIMITED, replyProxy);
                            return;
                        }
                        ThemeRuntimeSnapshot runtime = getThemeRuntimeSnapshot();
                        int themeGeneration = runtime.session().generation();
                        try {
                            Task.execute(() -> {
                                try {
                                    handleRemoteMessage(themeBridge, data, allowedOrigin, generation,
                                            themeGeneration, replyProxy);
                                } finally {
                                    remoteRequestGate.release();
                                }
                            });
                        } catch (Throwable e) {
                            remoteRequestGate.release();
                            replyRemoteError(data, WebThemeErrorCode.PAGE_UNAVAILABLE, replyProxy);
                        }
                    });
            remoteMessageListener = true;
        } catch (Throwable e) {
            SpiderDebug.log("webhome-security", "remote message listener unavailable error=%s", e.getMessage());
        }
        return remoteMessageListener;
    }

    private void handleRemoteMessage(WebHomeThemeBridge bridge, String message, String expectedOrigin,
                                     int generation, int themeGeneration, JavaScriptReplyProxy replyProxy) {
        JsonObject response = new JsonObject();
        String requestNonce = "";
        try {
            if (!isRemoteThemeSession(expectedOrigin, generation, themeGeneration)) throw new IllegalStateException("SOURCE_CHANGED");
            JsonObject request = WebCall.object(message);
            String id = limitedRemoteValue(request.has("id") ? request.get("id").getAsString() : "", 128);
            String method = limitedRemoteValue(request.has("method") ? request.get("method").getAsString() : "", 128);
            requestNonce = limitedRemoteValue(request.has("session") ? request.get("session").getAsString() : "", 128);
            JsonObject payload = request.has("payload") && request.get("payload").isJsonObject()
                    ? request.getAsJsonObject("payload") : new JsonObject();
            response.addProperty("id", id);
            try {
                if (!isRemoteThemeSession(expectedOrigin, generation, requestNonce, themeGeneration)) {
                    throw new IllegalStateException("SOURCE_CHANGED");
                }
                if (!remoteActionGate.tryAcquire(method, System.nanoTime() / 1_000_000L)) {
                    throw new IllegalStateException("RATE_LIMITED");
                }
                String nonce = requestNonce;
                String result = bridge.invoke(method, payload,
                        () -> isRemoteThemeSession(expectedOrigin, generation, nonce, themeGeneration));
                if (!isRemoteThemeSession(expectedOrigin, generation, requestNonce, themeGeneration)) {
                    throw new IllegalStateException("SOURCE_CHANGED");
                }
                if (result != null && (result.length() > MAX_REMOTE_RESPONSE_BYTES
                        || result.getBytes(StandardCharsets.UTF_8).length > MAX_REMOTE_RESPONSE_BYTES)) {
                    throw new IllegalStateException("RESPONSE_TOO_LARGE");
                }
                response.add("result", TextUtils.isEmpty(result) ? null : com.github.catvod.utils.Json.parse(result));
            } catch (Throwable e) {
                addRemoteError(response, WebThemeErrorCode.from(e));
            }
        } catch (Throwable e) {
            addRemoteError(response, "SOURCE_CHANGED".equals(e.getMessage())
                    ? WebThemeErrorCode.SOURCE_CHANGED : WebThemeErrorCode.INVALID_REQUEST);
        }
        String nonce = requestNonce;
        App.post(() -> {
            if (!isRemoteThemeSession(expectedOrigin, generation, nonce, themeGeneration)) {
                response.remove("result");
                addRemoteError(response, WebThemeErrorCode.SOURCE_CHANGED);
            }
            try {
                replyProxy.postMessage(response.toString());
            } catch (Throwable ignored) {
            }
        });
    }

    private boolean isRemoteSession(String expectedOrigin, int generation) {
        return !destroyed && !paused && bridgeReady && generation == remoteBridgeGeneration
                && expectedOrigin.equals(remoteBridgeOrigin);
    }

    private boolean isRemoteSession(String expectedOrigin, int generation, String expectedNonce) {
        return isRemoteSession(expectedOrigin, generation) && !TextUtils.isEmpty(expectedNonce)
                && expectedNonce.equals(remoteBridgeNonce);
    }

    private boolean isRemoteThemeSession(String expectedOrigin, int generation, int themeGeneration) {
        return isRemoteSession(expectedOrigin, generation) && isRemoteBridgeSessionActive(themeGeneration);
    }

    private boolean isRemoteThemeSession(String expectedOrigin, int generation, String expectedNonce,
            int themeGeneration) {
        return isRemoteSession(expectedOrigin, generation, expectedNonce)
                && isRemoteBridgeSessionActive(themeGeneration);
    }

    private boolean isRemoteBridgeSessionActive(int generation) {
        synchronized (themeStateLock) {
            WebHomeTarget current = pageHost.target();
            return themeSession.isCurrent(generation) && !destroyed && !paused && bridgeReady
                    && current != null && current.isRemoteGlobal() && !current.isManifest();
        }
    }

    private static String limitedRemoteValue(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static void addRemoteError(JsonObject response, WebThemeErrorCode error) {
        response.addProperty("error", error.getLegacyCode());
        response.addProperty("errorCode", error.getCode());
    }

    private static void replyRemoteError(String message, WebThemeErrorCode error,
            JavaScriptReplyProxy replyProxy) {
        JsonObject response = new JsonObject();
        try {
            JsonObject request = WebCall.object(message);
            response.addProperty("id", limitedRemoteValue(request.has("id") ? request.get("id").getAsString() : "", 128));
        } catch (Throwable ignored) {
        }
        addRemoteError(response, error);
        try {
            replyProxy.postMessage(response.toString());
        } catch (Throwable ignored) {
        }
    }

    private void invalidateRemoteSession() {
        remoteBridgeGeneration++;
        remoteBridgeOrigin = "";
        remoteBridgeNonce = "";
        remoteActionGate.reset();
        removeRemoteMessageListener();
    }

    private void rotateRemoteDocumentNonce() {
        WebHomeTarget current = pageHost.target();
        if (current != null && current.isRemoteGlobal() && !TextUtils.isEmpty(remoteBridgeOrigin)) {
            remoteBridgeNonce = newRemoteNonce();
        }
    }

    private static String newRemoteNonce() {
        return UUID.randomUUID().toString();
    }

    private void removeRemoteMessageListener() {
        if (!remoteMessageListener) return;
        try {
            WebViewCompat.removeWebMessageListener(webView, "fongmiRemote");
        } catch (Throwable ignored) {
        }
        remoteMessageListener = false;
    }

    public boolean load(Site site) {
        return load(site, false);
    }

    public Site getContentSite() {
        Site current = pageHost.site();
        return current == null ? VodConfig.get().getHome() : current;
    }

    public boolean isGlobalTheme() {
        WebHomeTarget current = pageHost.target();
        return current != null && current.isGlobal();
    }

    public boolean isV2Theme() {
        WebHomeTarget current = pageHost.target();
        return current != null && current.isV2();
    }

    WebThemePage getThemePage() {
        WebHomeTarget current = pageHost.target();
        return current != null && current.isV2() ? current.getPage() : null;
    }

    WebThemeRoute getThemeRoute() {
        return pageHost.route();
    }

    WebThemePlaySession getPlaySession() {
        return themeSession.getPlaySession();
    }

    WebThemeAccessSession getAccessSession() {
        return themeSession.getAccessSession();
    }

    WebThemeDetailActionSession getDetailActionSession() {
        return themeSession.getDetailActionSession();
    }

    ThemeRuntimeSnapshot getThemeRuntimeSnapshot() {
        synchronized (themeStateLock) {
            return new ThemeRuntimeSnapshot(pageHost.snapshot(), themeSession.snapshot());
        }
    }

    private boolean isThemeRuntimeCurrent(WebHomeTarget expectedTarget, int generation) {
        synchronized (themeStateLock) {
            return expectedTarget == pageHost.target() && themeSession.isCurrent(generation);
        }
    }

    Vod getDetailVod() {
        return pageHost.detailVod();
    }

    void setDetailVod(Vod detailVod, WebThemePlaySession playSession) {
        synchronized (themeStateLock) {
            publishDetailVodLocked(detailVod, playSession);
        }
        listener.onDetailVodLoaded(detailVod);
    }

    boolean setDetailVodIfCurrent(ThemeRuntimeSnapshot expected, Vod detailVod,
            WebThemePlaySession playSession) {
        synchronized (themeStateLock) {
            if (expected == null || expected.page().target() != pageHost.target()
                    || !themeSession.isCurrent(expected.session().generation())
                    || destroyed || paused || !bridgeReady) return false;
            publishDetailVodLocked(detailVod, playSession);
        }
        listener.onDetailVodLoaded(detailVod);
        return true;
    }

    private void publishDetailVodLocked(Vod detailVod, WebThemePlaySession playSession) {
        themeSession.replacePlaySession(playSession);
        pageHost.setDetailVod(detailVod);
    }

    WebThemeDetailMetadata getDetailMetadata() {
        return pageHost.detailMetadata();
    }

    public void setDetailMetadata(WebThemeDetailMetadata detailMetadata) {
        synchronized (themeStateLock) {
            pageHost.setDetailMetadata(detailMetadata);
        }
    }

    public void dispatchDetailChanged() {
        if (destroyed || webView == null || getThemePage() != WebThemePage.DETAIL) return;
        String script = "(function(){try{window.dispatchEvent(new CustomEvent('fmdetailchange',{detail:{}}));}catch(e){}})();";
        WebView current = webView;
        current.post(() -> {
            if (destroyed || current != webView || getThemePage() != WebThemePage.DETAIL) return;
            current.evaluateJavascript(script, null);
        });
    }

    WebHomeTarget getThemeTarget() {
        return pageHost.target();
    }

    int getThemeSessionGeneration() {
        synchronized (themeStateLock) {
            return themeSession.generation();
        }
    }

    boolean isThemeSessionActive(int generation) {
        synchronized (themeStateLock) {
            WebHomeTarget current = pageHost.target();
            return themeSession.isCurrent(generation) && !destroyed && !paused && bridgeReady
                    && current != null && current.isV2();
        }
    }

    boolean isLegacyThemeSessionActive(int generation) {
        synchronized (themeStateLock) {
            WebHomeTarget current = pageHost.target();
            return themeSession.isCurrent(generation) && !destroyed && !paused && bridgeReady
                    && current != null && !current.isManifest() && !current.isV2();
        }
    }

    boolean isBridgeSessionActive(boolean v2Theme, int generation) {
        return v2Theme ? isThemeSessionActive(generation) : isLegacyThemeSessionActive(generation);
    }

    boolean isRemoteTheme() {
        WebHomeTarget current = pageHost.target();
        return current != null && current.isRemoteGlobal();
    }

    public boolean load(Site site, boolean force) {
        WebHomeTarget resolved = WebHomeTarget.resolve(site);
        if (site == null || resolved == null) return false;
        if (resolved.isManifest()) return loadManifestPage(site, resolved, WebThemePage.HOME, WebThemeRoute.EMPTY, force);
        manifestActivation = null;
        manifestRollbackInProgress = false;
        manifestLoadToken++;
        return loadResolved(site, resolved, WebThemeRoute.EMPTY, force);
    }

    public boolean loadThemePage(Site site, String manifestUrl, WebThemePage page, WebThemeRoute route) {
        WebHomeTarget configured = WebHomeTarget.resolve("", true, manifestUrl, manifestUrl);
        if (site == null || configured == null || !configured.isManifest() || page == null) return false;
        return loadManifestPage(site, configured, page, route == null ? WebThemeRoute.EMPTY : route, false);
    }

    private boolean loadManifestPage(Site site, WebHomeTarget configured, WebThemePage page,
            WebThemeRoute route, boolean force) {
        if (page == WebThemePage.DETAIL && (route == null || TextUtils.isEmpty(route.getVodId()))) return false;
        manifestActivation = null;
        manifestRollbackInProgress = false;
        bridgeReady = false;
        sdkReady = false;
        invalidateRemoteSession();
        webView.removeJavascriptInterface(BRIDGE);
        bridgeKey = "";
        if (Setting.isWebHomeFullscreen()) listener.applyDefaultChrome(site);
        else listener.setChrome(normalChrome());
        Server.get().start();
        int token = ++manifestLoadToken;
        int generation;
        synchronized (themeStateLock) {
            generation = themeSession.invalidate();
            pageHost.beginDocument(site, configured, route);
        }
        listener.onWebLoading();
        show();
        String sourceKey = site.getKey();
        String manifestUrl = configured.getUrl();
        WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_LOAD_STARTED,
                token, generation, page, configured, manifestUrl, WebThemeRuntimeDiagnostics.Reason.NONE, 0);
        Task.execute(() -> {
            try {
                WebThemeManifestResolver.Resolution resolution = manifestResolver.resolvePageResult(configured, page, force);
                App.post(() -> {
                    if (!isManifestLoadActive(token, sourceKey, manifestUrl)) {
                        WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_LOAD_IGNORED,
                                token, generation, page, configured, manifestUrl,
                                WebThemeRuntimeDiagnostics.Reason.STALE_OPERATION, 0);
                        return;
                    }
                    WebHomeTarget resolved = resolution == null ? null : resolution.target();
                    if (resolution != null && resolution.usedLastKnownGood()) {
                        WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_CACHE_FALLBACK,
                                token, generation, page, configured, manifestUrl,
                                WebThemeRuntimeDiagnostics.manifestFailure(resolution.refreshFailure()), 0);
                    }
                    if (resolution != null && resolution.usedRollback()) {
                        WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_ROLLBACK,
                                token, generation, page, configured, manifestUrl,
                                WebThemeRuntimeDiagnostics.Reason.ROLLBACK, 0);
                    }
                    if (resolved == null) {
                        WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_LOAD_FAILED,
                                token, generation, page, configured, manifestUrl,
                                WebThemeRuntimeDiagnostics.Reason.PAGE_UNAVAILABLE, 0);
                        synchronized (themeStateLock) {
                            pageHost.failPage();
                        }
                        listener.onWebError();
                        return;
                    }
                    WebThemeRuntimeDiagnostics.Reason reason = resolution.usedRollback()
                            ? WebThemeRuntimeDiagnostics.Reason.ROLLBACK
                            : resolution.usedLastKnownGood()
                            ? WebThemeRuntimeDiagnostics.Reason.LAST_KNOWN_GOOD
                            : WebThemeRuntimeDiagnostics.Reason.NONE;
                    WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_LOAD_RESOLVED,
                            token, generation, page, resolved, resolved.getUrl(), reason, 0);
                    armManifestActivation(site, sourceKey, configured, page, route, resolution);
                    if (!loadResolved(site, resolved, route, force)
                            && !rollbackPendingManifest(0)) {
                        listener.onWebError();
                    }
                });
            } catch (Exception e) {
                WebThemeRuntimeDiagnostics.Reason reason = WebThemeRuntimeDiagnostics.manifestFailure(e);
                App.post(() -> {
                    if (!isManifestLoadActive(token, sourceKey, manifestUrl)) {
                        WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_LOAD_IGNORED,
                                token, generation, page, configured, manifestUrl,
                                WebThemeRuntimeDiagnostics.Reason.STALE_OPERATION, 0);
                        return;
                    }
                    WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_LOAD_FAILED,
                            token, generation, page, configured, manifestUrl, reason, 0);
                    synchronized (themeStateLock) {
                        pageHost.failPage();
                    }
                    listener.onWebError();
                });
            }
        });
        return true;
    }

    private boolean isManifestLoadActive(int token, String sourceKey, String manifestUrl) {
        WebThemePageHost.Snapshot page = pageHost.snapshot();
        return !destroyed && token == manifestLoadToken && page.site() != null && page.target() != null
                && sourceKey.equals(page.site().getKey()) && manifestUrl.equals(page.target().getUrl());
    }

    private void armManifestActivation(Site site, String sourceKey, WebHomeTarget configured,
            WebThemePage page, WebThemeRoute route, WebThemeManifestResolver.Resolution resolution) {
        if (resolution == null || !resolution.activationPending()
                || TextUtils.isEmpty(resolution.revision())) {
            manifestActivation = null;
            return;
        }
        manifestActivation = new ManifestActivation(site, sourceKey, configured, page,
                route == null ? WebThemeRoute.EMPTY : route,
                resolution.revision(), resolution.rollbackAvailable());
    }

    private void acceptManifestActivation(WebHomeTarget currentTarget) {
        ManifestActivation pending = manifestActivation;
        if (pending == null) return;
        Site currentSite = pageHost.site();
        manifestActivation = null;
        if (currentSite == null || !pending.sourceKey().equals(currentSite.getKey())
                || !pending.matches(currentTarget)) return;
        Task.execute(() -> manifestResolver.accept(
                pending.configured().getUrl(), pending.revision()));
    }

    private boolean rollbackPendingManifest(int code) {
        if (manifestRollbackInProgress) return true;
        ManifestActivation pending = manifestActivation;
        manifestActivation = null;
        if (pending == null || !pending.rollbackAvailable()) return false;
        manifestRollbackInProgress = true;
        bridgeReady = false;
        sdkReady = false;
        loadToken++;
        int token = ++manifestLoadToken;
        int generation;
        synchronized (themeStateLock) {
            generation = themeSession.invalidate();
        }
        invalidateRemoteSession();
        webView.removeJavascriptInterface(BRIDGE);
        webView.stopLoading();
        bridgeKey = "";
        loadTimeoutRecoveries = 0;
        listener.onWebLoading();
        Task.execute(() -> {
            try {
                WebThemeManifestResolver.Resolution resolution = manifestResolver.rollbackPageResult(
                        pending.configured(), pending.page(), pending.revision());
                App.post(() -> {
                    if (!isManifestRollbackActive(token, pending)) {
                        WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_LOAD_IGNORED,
                                token, generation, pending.page(), pending.configured(),
                                pending.configured().getUrl(), WebThemeRuntimeDiagnostics.Reason.STALE_OPERATION, 0);
                        return;
                    }
                    manifestRollbackInProgress = false;
                    WebHomeTarget resolved = resolution == null ? null : resolution.target();
                    if (resolved == null) {
                        handleMainFrameFailure(WebThemeRuntimeDiagnostics.Reason.PAGE_UNAVAILABLE, 0, null);
                        return;
                    }
                    WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_ROLLBACK,
                            token, generation, pending.page(), resolved, resolved.getUrl(),
                            WebThemeRuntimeDiagnostics.Reason.ROLLBACK, code);
                    if (!loadResolved(pending.site(), resolved, pending.route(), true)) {
                        handleMainFrameFailure(
                                WebThemeRuntimeDiagnostics.Reason.BRIDGE_UNAVAILABLE, 0, null);
                    }
                });
            } catch (Exception e) {
                WebThemeRuntimeDiagnostics.Reason reason = WebThemeRuntimeDiagnostics.manifestFailure(e);
                App.post(() -> {
                    if (!isManifestRollbackActive(token, pending)) {
                        WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_LOAD_IGNORED,
                                token, generation, pending.page(), pending.configured(),
                                pending.configured().getUrl(), WebThemeRuntimeDiagnostics.Reason.STALE_OPERATION, 0);
                        return;
                    }
                    manifestRollbackInProgress = false;
                    WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.MANIFEST_LOAD_FAILED,
                            token, generation, pending.page(), pending.configured(),
                            pending.configured().getUrl(), reason, 0);
                    handleMainFrameFailure(reason, 0, null);
                });
            }
        });
        return true;
    }

    private boolean isManifestRollbackActive(int token, ManifestActivation pending) {
        Site current = pageHost.site();
        return !destroyed && token == manifestLoadToken && current != null
                && pending.sourceKey().equals(current.getKey());
    }

    private void logRuntime(WebThemeRuntimeDiagnostics.Event event, int operation, WebThemePage page,
            WebHomeTarget target, String url, WebThemeRuntimeDiagnostics.Reason reason, int code) {
        WebThemeRuntimeDiagnostics.log(event, operation, themeSession.generation(), page, target, url, reason, code);
    }

    static boolean requiresPageReload(boolean force, boolean bridgeReady, String url, String loadedUrl,
            String identity, String loadedIdentity) {
        return force || !bridgeReady || !url.equals(loadedUrl) || !identity.equals(loadedIdentity);
    }

    private boolean loadResolved(Site site, WebHomeTarget resolved, WebThemeRoute route, boolean force) {
        if (site == null || resolved == null) return false;
        if (!ensureDataProfile(resolved)) {
            logRuntime(WebThemeRuntimeDiagnostics.Event.FALLBACK,
                    resolved.isV2() ? manifestLoadToken : loadToken, resolved.getPage(), resolved, resolved.getUrl(),
                    WebThemeRuntimeDiagnostics.Reason.DATA_ISOLATION_UNAVAILABLE, 0);
            return false;
        }
        String url = resolved.isGlobal() ? resolved.getUrl() : getHomePage(site);
        String identity = resolved.identity(site.getKey());
        boolean reload = requiresPageReload(force, bridgeReady, url, homePage, identity, homeIdentity);
        if (reload) {
            bridgeReady = false;
            sdkReady = false;
        }
        synchronized (themeStateLock) {
            if (reload) {
                themeSession.invalidate();
                pageHost.beginDocument(site, resolved, route);
            } else {
                themeSession.cancelPending();
                pageHost.updateContext(site, resolved, route);
            }
        }
        if (Setting.isWebHomeFullscreen()) listener.applyDefaultChrome(site);
        else listener.setChrome(normalChrome());
        Server.get().start();
        if (!configureBridge(resolved, reload)) {
            logRuntime(WebThemeRuntimeDiagnostics.Event.FALLBACK,
                    resolved.isV2() ? manifestLoadToken : loadToken, resolved.getPage(), resolved, url,
                    WebThemeRuntimeDiagnostics.Reason.BRIDGE_UNAVAILABLE, 0);
            synchronized (themeStateLock) {
                pageHost.clearTarget();
            }
            return false;
        }
        this.pageHeaders = resolved.isGlobal() ? Collections.emptyMap() : site.getHeader();
        rawAdapter = resolved.isRemoteGlobal() ? null : WebHomeRawAdapter.create(url, pageHeaders);
        if (resolved.injectsSiteExtensions()) {
            prepareExtensions(site);
            registerDocumentStartScripts();
        } else {
            removeDocumentStartScripts();
        }
        if (reload) {
            lastViewportKey = "";
            injectedExtensions.clear();
            homePage = url;
            homeIdentity = identity;
            loadUrl(force ? reloadUrl(homePage) : homePage);
        }
        show();
        return true;
    }

    public void reload() {
        bridgeReady = false;
        sdkReady = false;
        synchronized (themeStateLock) {
            themeSession.invalidate();
            pageHost.clearDetail();
        }
        WebHomeTarget currentTarget = pageHost.target();
        if (currentTarget != null && currentTarget.isRemoteGlobal() && !configureBridge(currentTarget, true)) {
            handleMainFrameFailure(WebThemeRuntimeDiagnostics.Reason.BRIDGE_UNAVAILABLE, 0,
                    "Remote theme bridge unavailable");
            return;
        }
        if (TextUtils.isEmpty(homePage)) {
            webView.reload();
        } else {
            webView.clearCache(false);
            loadUrl(reloadUrl(homePage));
        }
    }

    public void reloadExtensions() {
        extensionReloadRequested = true;
        consumeExtensionReload();
    }

    private void loadUrl(String url) {
        Map<String, String> headers = pageHeaders;
        String userAgent = header(headers, HttpHeaders.USER_AGENT);
        if (!TextUtils.isEmpty(userAgent)) webView.getSettings().setUserAgentString(userAgent);
        else if (!TextUtils.isEmpty(defaultUserAgent)) webView.getSettings().setUserAgentString(defaultUserAgent);
        Map<String, String> requestHeaders = requestHeaders(url, headers);
        lastPageUrl = url;
        int token = ++loadToken;
        SpiderDebug.log("webhome-webview", "load url=%s ua=%s headers=%s", safeLogUrl(url), !TextUtils.isEmpty(userAgent), requestHeaders.keySet());
        if (requestHeaders.isEmpty()) webView.loadUrl(url);
        else webView.loadUrl(url, requestHeaders);
        webView.postDelayed(() -> handleLoadTimeout(token, url), LOAD_TIMEOUT_MS);
    }

    private void handleLoadTimeout(int token, String url) {
        if (token != loadToken || !isVisible() || activity.isFinishing() || activity.isDestroyed()) return;
        SpiderDebug.log("webhome-webview", "load timeout url=%s current=%s recoveries=%s",
                safeLogUrl(url), safeLogUrl(webView.getUrl()), loadTimeoutRecoveries);
        WebThemePageHost.Snapshot page = pageHost.snapshot();
        if (TextUtils.isEmpty(homePage) || loadTimeoutRecoveries++ > 0) {
            logRuntime(WebThemeRuntimeDiagnostics.Event.FALLBACK, token,
                    page.target() == null ? null : page.target().getPage(), page.target(), url,
                    WebThemeRuntimeDiagnostics.Reason.LOAD_TIMEOUT, loadTimeoutRecoveries);
            if (rollbackPendingManifest(loadTimeoutRecoveries)) return;
            listener.onWebError();
            return;
        }
        String target = !TextUtils.isEmpty(lastPageUrl) && !isEmptyDocumentUrl(lastPageUrl) ? lastPageUrl : homePage;
        logRuntime(WebThemeRuntimeDiagnostics.Event.DOCUMENT_RECOVERY, token,
                page.target() == null ? null : page.target().getPage(), page.target(), target,
                WebThemeRuntimeDiagnostics.Reason.LOAD_TIMEOUT, loadTimeoutRecoveries);
        if (!recreateWebView()) {
            logRuntime(WebThemeRuntimeDiagnostics.Event.FALLBACK, token,
                    page.target() == null ? null : page.target().getPage(), page.target(), target,
                    WebThemeRuntimeDiagnostics.Reason.LOAD_TIMEOUT, loadTimeoutRecoveries);
            if (rollbackPendingManifest(loadTimeoutRecoveries)) return;
            listener.onWebError();
            return;
        }
        listener.onWebLoading();
        loadUrl(reloadUrl(target, true));
    }

    private Map<String, String> requestHeaders(String url, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return Collections.emptyMap();
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (TextUtils.isEmpty(key) || value == null) continue;
            if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) continue;
            if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) {
                cookieManager.setCookie(url, value);
                continue;
            }
            result.put(key, value);
        }
        return result;
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) return "";
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return "";
    }

    public void evaluate(String script, ValueCallback<String> callback) {
        webView.post(() -> webView.evaluateJavascript(script, callback));
    }

    public void dispatchDebugConsole(String level, String message) {
        if (!debugTools) return;
        String text = (TextUtils.isEmpty(level) ? "log" : level).toUpperCase(Locale.ROOT) + " " + (message == null ? "" : message);
        App.post(() -> listener.onWebConsole(text));
    }

    public void dispatchDebugNetwork(String type, String method, String url, int status, long durationMs, String detail) {
        if (!debugTools) return;
        App.post(() -> listener.onWebNetwork(type, method, url, status, durationMs, detail));
    }

    public void show() {
        active = this;
        webView.setVisibility(View.VISIBLE);
        focusWebView("show");
        consumeExtensionReload();
    }

    public void hide() {
        webView.setVisibility(View.GONE);
    }


    public void setTopMargin(int margin) {
        ViewGroup.LayoutParams layoutParams = webView.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams params) || params.topMargin == margin) return;
        params.topMargin = margin;
        webView.setLayoutParams(params);
    }

    public boolean isVisible() {
        return webView.getVisibility() == View.VISIBLE;
    }

    public boolean isReady() {
        return bridgeReady && !destroyed;
    }

    public boolean dispatchFocusedClick() {
        WebView current = webView;
        if (destroyed || paused || !bridgeReady || current == null || !isVisible()) return false;
        current.evaluateJavascript("""
                (function() {
                  var node=document.activeElement;
                  if(!node||node===document.body||node===document.documentElement)return false;
                  if(typeof node.click==='function'){
                    node.click();
                    return true;
                  }
                  var event=new MouseEvent('click',{bubbles:true,cancelable:true,view:window,button:0});
                  node.dispatchEvent(event);
                  return true;
                })();
                """, null);
        return true;
    }

    public boolean dispatchFocusedLongPress() {
        WebView current = webView;
        if (destroyed || paused || !bridgeReady || current == null || !isVisible()) return false;
        current.evaluateJavascript("""
                (function() {
                  var node=document.activeElement;
                  if(!node||node===document.body||node===document.documentElement)return false;
                  try{
                    var event=new MouseEvent('contextmenu',{bubbles:true,cancelable:true,view:window,button:2});
                    node.dispatchEvent(event);
                    return event.defaultPrevented;
                  }catch(error){
                    var fallback=document.createEvent('Event');
                    fallback.initEvent('contextmenu',true,true);
                    node.dispatchEvent(fallback);
                    return fallback.defaultPrevented;
                  }
                })();
                """, null);
        return true;
    }

    public boolean handleBack() {
        if (!isVisible()) return false;
        if (!webView.canGoBack()) return false;
        String current = webView.getUrl();
        if (samePage(current, homePage)) {
            SpiderDebug.log("webhome-webview", "back home boundary current=%s", safeLogUrl(current));
            return false;
        }
        String previous = previousHistoryUrl();
        if (!sameSite(current, previous)) {
            SpiderDebug.log("webhome-webview", "back boundary current=%s previous=%s", safeLogUrl(current), safeLogUrl(previous));
            return false;
        }
        webView.goBack();
        return true;
    }

    private String previousHistoryUrl() {
        try {
            WebBackForwardList list = webView.copyBackForwardList();
            int index = list.getCurrentIndex() - 1;
            WebHistoryItem item = index >= 0 ? list.getItemAtIndex(index) : null;
            return item == null ? "" : item.getUrl();
        } catch (Throwable e) {
            SpiderDebug.log("webhome-webview", "back history unavailable error=%s", e.getMessage());
            return "";
        }
    }

    private boolean sameSite(String current, String target) {
        if (TextUtils.isEmpty(current) || TextUtils.isEmpty(target)) return false;
        Uri currentUri = Uri.parse(current);
        Uri targetUri = Uri.parse(target);
        String currentScheme = UrlUtil.scheme(currentUri);
        String targetScheme = UrlUtil.scheme(targetUri);
        String currentHost = UrlUtil.host(currentUri);
        String targetHost = UrlUtil.host(targetUri);
        if (currentHost.isEmpty() || targetHost.isEmpty()) return current.equals(target);
        return currentScheme.equals(targetScheme) && currentHost.equals(targetHost) && port(currentUri) == port(targetUri);
    }

    private boolean samePage(String current, String target) {
        if (!sameSite(current, target)) return false;
        Uri currentUri = Uri.parse(current);
        Uri targetUri = Uri.parse(target);
        return path(currentUri).equals(path(targetUri))
                && cleanQuery(currentUri).equals(cleanQuery(targetUri))
                && fragment(currentUri).equals(fragment(targetUri));
    }

    private String path(Uri uri) {
        String path = uri.getEncodedPath();
        return TextUtils.isEmpty(path) ? "/" : path;
    }

    private String fragment(Uri uri) {
        String fragment = uri.getEncodedFragment();
        return fragment == null ? "" : fragment;
    }

    private String cleanQuery(Uri uri) {
        String query = uri.getEncodedQuery();
        if (TextUtils.isEmpty(query)) return "";
        StringBuilder result = new StringBuilder();
        for (String part : query.split("&")) {
            int index = part.indexOf('=');
            String name = index >= 0 ? part.substring(0, index) : part;
            if ("_fm_reload".equals(name) || "_fm_restore".equals(name)) continue;
            if (result.length() > 0) result.append('&');
            result.append(part);
        }
        return result.toString();
    }

    private int port(Uri uri) {
        int port = uri.getPort();
        if (port >= 0) return port;
        String scheme = UrlUtil.scheme(uri);
        if ("http".equals(scheme)) return 80;
        if ("https".equals(scheme)) return 443;
        return -1;
    }

    public void setToolbar(boolean visible) {
        if (!Setting.isWebHomeFullscreen()) {
            listener.setChrome(normalChrome());
            return;
        }
        listener.setToolbar(visible);
    }

    public void setChrome(JsonObject payload) {
        if (!Setting.isWebHomeFullscreen()) {
            listener.setChrome(normalChrome());
            return;
        }
        listener.setChrome(payload);
    }

    public void restoreChrome() {
        if (!Setting.isWebHomeFullscreen()) {
            listener.setChrome(normalChrome());
            return;
        }
        listener.restoreChrome();
    }

    private JsonObject normalChrome() {
        JsonObject object = new JsonObject();
        object.addProperty("mode", WebHomeChrome.NORMAL);
        return object;
    }

    public String getViewportJson() {
        return viewport.json(density, webView.getWidth(), webView.getHeight());
    }

    String getThemeInfoJson() {
        return getThemeInfoJson(getThemeRuntimeSnapshot());
    }

    String getThemeInfoJson(ThemeRuntimeSnapshot runtime) {
        if (runtime == null) throw new IllegalStateException("Theme V2 is not active");
        WebThemePageHost.Snapshot page = runtime.page();
        WebHomeTarget currentTarget = page.target();
        WebThemeRoute currentRoute = page.route();
        WebThemeAccessSession currentAccessSession = runtime.session().accessSession();
        if (currentTarget == null || !currentTarget.isV2()) {
            throw new IllegalStateException("Theme V2 is not active");
        }
        JsonObject root = new JsonObject();
        root.addProperty("hostApiVersion", WebThemeManifest.HOST_API_VERSION);
        root.addProperty("page", currentTarget.getPage().getKey());

        JsonObject theme = new JsonObject();
        theme.addProperty("id", currentTarget.getManifest().getId());
        theme.addProperty("name", currentTarget.getManifest().getName());
        theme.addProperty("version", currentTarget.getManifest().getVersion());
        root.add("theme", theme);

        JsonObject client = new JsonObject();
        client.addProperty("isLeanback", Util.isLeanback());
        client.addProperty("isLandscape", activity.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
        client.addProperty("width", webView.getWidth());
        client.addProperty("height", webView.getHeight());
        client.addProperty("density", density);
        root.add("client", client);

        Site contentSite = page.site();
        if (contentSite == null) contentSite = VodConfig.get().getHome();
        JsonObject source = new JsonObject();
        source.addProperty("key", contentSite == null ? "" : contentSite.getKey());
        source.addProperty("name", contentSite == null ? "" : contentSite.getName());
        root.add("source", source);
        root.add("route", currentRoute.json(currentAccessSession.issueRoute(currentRoute.getVodId())));

        JsonArray capabilities = new JsonArray();
        for (String capability : WebThemeCapabilityRegistry.capabilities(currentTarget.getPage(),
                currentTarget.getPermissions())) capabilities.add(capability);
        root.add("capabilities", capabilities);
        return root.toString();
    }

    public void setViewport(WebHomeViewport viewport) {
        this.viewport = viewport == null ? WebHomeViewport.EMPTY : viewport;
        injectViewport();
    }

    public void openVod() {
        listener.openVod();
    }

    public void openSite() {
        listener.openSite();
    }

    public void openSetting() {
        listener.openSetting();
    }

    public void onResume() {
        boolean wasPaused = paused;
        if (wasPaused) {
            synchronized (themeStateLock) {
                themeSession.cancelPending();
            }
        }
        paused = false;
        if (isVisible()) active = this;
        synchronized (this) {
            inlineEvaluationCount = 0;
        }
        webView.onResume();
        recoverAfterResume();
        consumeExtensionReload();
    }

    public void onPause() {
        boolean wasPaused = paused;
        paused = true;
        if (!wasPaused) {
            synchronized (themeStateLock) {
                themeSession.cancelPending();
            }
        }
        pauseAt = System.currentTimeMillis();
        dispatchLifecycle("fmpause", "{time:" + pauseAt + "}");
        webView.onPause();
    }

    public boolean beginInlineEvaluation() {
        synchronized (this) {
            if (!paused) return false;
            inlineEvaluationCount++;
            if (inlineEvaluationCount > 1) return true;
        }
        App.post(() -> {
            if (!paused) return;
            SpiderDebug.log("webhome-inline", "resume WebView for inline evaluation url=%s", safeLogUrl(webView.getUrl()));
            webView.onResume();
        });
        return true;
    }

    public void endInlineEvaluation(boolean active) {
        if (!active) return;
        boolean pause;
        synchronized (this) {
            if (inlineEvaluationCount > 0) inlineEvaluationCount--;
            pause = paused && inlineEvaluationCount == 0;
        }
        if (!pause) return;
        App.post(() -> {
            if (!paused) return;
            SpiderDebug.log("webhome-inline", "pause WebView after inline evaluation url=%s", safeLogUrl(webView.getUrl()));
            pausePageMedia();
            webView.onPause();
        });
    }

    private void pausePageMedia() {
        try {
            webView.evaluateJavascript(PAUSE_PAGE_MEDIA_SCRIPT, null);
        } catch (Throwable e) {
            SpiderDebug.log("webhome-inline", "pause page media failed: %s", e.getMessage());
        }
    }

    public void prepareNativePlayback(Runnable launch) {
        if (launch == null || destroyed) return;
        if (webView == null) {
            if (!activity.isFinishing() && !activity.isDestroyed()) launch.run();
            return;
        }
        WebView target = webView;
        PendingNativePlayback pending = new PendingNativePlayback(launch);
        pendingNativePlaybacks.add(pending);
        target.postDelayed(pending, MEDIA_PAUSE_LAUNCH_TIMEOUT_MS);
        try {
            target.evaluateJavascript(PAUSE_PAGE_MEDIA_SCRIPT, ignored -> {
                target.removeCallbacks(pending);
                pending.run();
            });
        } catch (Throwable e) {
            SpiderDebug.log("webhome-inline", "prepare native playback media pause failed: %s", e.getMessage());
            target.removeCallbacks(pending);
            pending.run();
        }
    }

    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        bridgeReady = false;
        sdkReady = false;
        manifestLoadToken++;
        manifestActivation = null;
        manifestRollbackInProgress = false;
        synchronized (themeStateLock) {
            themeSession.invalidate();
            pageHost.clearDetail();
        }
        invalidateRemoteSession();
        cancelPendingNativePlaybacks();
        removeDocumentStartScripts();
        rawAdapter = null;
        webView.stopLoading();
        webView.destroy();
        if (debugTools) WebView.setWebContentsDebuggingEnabled(false);
        if (active == this) active = null;
    }

    private void cancelPendingNativePlaybacks() {
        for (PendingNativePlayback pending : new HashSet<>(pendingNativePlaybacks)) {
            pending.cancel();
            webView.removeCallbacks(pending);
        }
        pendingNativePlaybacks.clear();
    }

    private final class PendingNativePlayback implements Runnable {

        private final NativePlaybackLaunchState state;
        private final Runnable launch;

        private PendingNativePlayback(Runnable launch) {
            this.state = new NativePlaybackLaunchState();
            this.launch = launch;
        }

        @Override
        public void run() {
            pendingNativePlaybacks.remove(this);
            if (!state.tryLaunch(destroyed, activity.isFinishing(), activity.isDestroyed())) return;
            launch.run();
        }

        private void cancel() {
            state.cancel();
        }
    }

    static final class NativePlaybackLaunchState {

        private final AtomicBoolean completed = new AtomicBoolean();

        boolean tryLaunch(boolean controllerDestroyed, boolean activityFinishing, boolean activityDestroyed) {
            return !controllerDestroyed && !activityFinishing && !activityDestroyed && completed.compareAndSet(false, true);
        }

        void cancel() {
            completed.set(true);
        }
    }

    static final class RemoteRequestGate {

        private final AtomicInteger inFlight = new AtomicInteger();
        private final int limit;

        RemoteRequestGate(int limit) {
            this.limit = Math.max(1, limit);
        }

        boolean tryAcquire() {
            int current;
            do {
                current = inFlight.get();
                if (current >= limit) return false;
            } while (!inFlight.compareAndSet(current, current + 1));
            return true;
        }

        void release() {
            int current;
            do {
                current = inFlight.get();
                if (current == 0) return;
            } while (!inFlight.compareAndSet(current, current - 1));
        }

        int inFlight() {
            return inFlight.get();
        }
    }

    static final class RemoteActionGate {

        private final long intervalMs;
        private long lastActionAt = Long.MIN_VALUE;

        RemoteActionGate(long intervalMs) {
            this.intervalMs = Math.max(0, intervalMs);
        }

        synchronized boolean tryAcquire(String method, long now) {
            if (!isSideEffect(method)) return true;
            if (lastActionAt != Long.MIN_VALUE && now >= lastActionAt
                    && now - lastActionAt < intervalMs) return false;
            lastActionAt = now;
            return true;
        }

        synchronized void reset() {
            lastActionAt = Long.MIN_VALUE;
        }

        private static boolean isSideEffect(String method) {
            return switch (method == null ? "" : method) {
                case "favorite.set", "player.playVod", "navigation.openDetail",
                        "navigation.openNativeDetail", "app.search", "app.openVod", "app.openSite",
                        "app.openSetting", "person.open", "image.preview", "image.save",
                        "recommendation.open", "recommendation.info", "recommendation.feedback",
                        "external.open", "episode.info", "navigation.back", "navigation.reload" -> true;
                default -> false;
            };
        }
    }

    private void consumeExtensionReload() {
        WebThemePageHost.Snapshot page = pageHost.snapshot();
        Site current = page.site();
        WebHomeTarget currentTarget = page.target();
        if (currentTarget == null || !currentTarget.injectsSiteExtensions() || !extensionReloadRequested
                || paused || !isVisible() || current == null || TextUtils.isEmpty(homePage)) return;
        long now = System.currentTimeMillis();
        long wait = EXTENSION_RELOAD_MIN_INTERVAL_MS - (now - lastExtensionReloadAt);
        if (wait > 0) {
            webView.removeCallbacks(extensionReloadRunnable);
            webView.postDelayed(extensionReloadRunnable, wait);
            return;
        }
        extensionReloadRequested = false;
        lastExtensionReloadAt = now;
        WebHomeExtensionRegistry.get().refresh(current, () -> {
            Site latest = pageHost.site();
            if (latest == null || !current.getKey().equals(latest.getKey())) return;
            registerDocumentStartScripts();
            reload();
        });
    }

    private boolean ensureDataProfile(WebHomeTarget target) {
        WebThemeDataIsolation.DataProfile desired;
        try {
            desired = WebThemeDataIsolation.profileFor(target);
        } catch (RuntimeException e) {
            SpiderDebug.log("webhome-security", "remote data profile rejected error=%s", e.getMessage());
            return false;
        }
        if (dataProfileReady
                && !WebThemeDataIsolation.requiresReplacement(dataProfile, desired)) return true;
        if (desired.isolated() && !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            SpiderDebug.log("webhome-security", "remote data profile unavailable provider_feature=multi_profile");
            return false;
        }
        return replaceWebView(desired, false);
    }

    private boolean replaceWebView(WebThemeDataIsolation.DataProfile desired, boolean restoreTarget) {
        ViewGroup parent = webView.getParent() instanceof ViewGroup ? (ViewGroup) webView.getParent() : null;
        if (parent == null) return false;
        int index = parent.indexOfChild(webView);
        int id = webView.getId();
        int visibility = webView.getVisibility();
        ViewGroup.LayoutParams params = webView.getLayoutParams();
        WebView replacement = null;
        try {
            if (desired.isolated()) ProfileStore.getInstance().getOrCreateProfile(desired.name());
            replacement = new WebView(activity);
            if (desired.isolated()) WebViewCompat.setProfile(replacement, desired.name());
        } catch (Throwable e) {
            if (replacement != null) replacement.destroy();
            SpiderDebug.log("webhome-security", "webview data profile creation failed isolated=%s error=%s",
                    desired.isolated(), e.getMessage());
            return false;
        }
        bridgeReady = false;
        sdkReady = false;
        synchronized (themeStateLock) {
            themeSession.invalidate();
        }
        invalidateRemoteSession();
        cancelPendingNativePlaybacks();
        try {
            removeDocumentStartScripts();
            webView.stopLoading();
            parent.removeView(webView);
            webView.destroy();
        } catch (Throwable ignored) {
        }
        replacement.setId(id);
        replacement.setVisibility(visibility);
        parent.addView(replacement, Math.max(0, index), params);
        webView = replacement;
        dataProfile = desired;
        dataProfileReady = false;
        try {
            init();
            dataProfileReady = true;
            WebHomeTarget currentTarget = pageHost.target();
            if (restoreTarget && currentTarget != null && !configureBridge(currentTarget, true)) return false;
            if (restoreTarget) registerDocumentStartScripts();
            SpiderDebug.log("webhome-security", "webview data profile selected isolated=%s", desired.isolated());
            return true;
        } catch (Throwable e) {
            SpiderDebug.log("webhome-security", "webview data profile init failed isolated=%s error=%s",
                    desired.isolated(), e.getMessage());
            return false;
        }
    }


    private boolean recreateWebView() {
        return replaceWebView(dataProfile, true);
    }

    private void recoverAfterResume() {
        if (!isVisible()) return;
        if (recoverEmptyDocument()) return;
        webView.setBackgroundColor(Color.TRANSPARENT);
        focusWebView("resume");
        webView.requestLayout();
        webView.invalidate();
        webView.postInvalidateOnAnimation();
        nudgeCompositor();
        dispatchResume(0);
        dispatchResume(80);
        dispatchResume(260);
    }

    private boolean recoverEmptyDocument() {
        String current = webView.getUrl();
        if (!isEmptyDocumentUrl(current) || TextUtils.isEmpty(homePage)) return false;
        String target = !TextUtils.isEmpty(lastPageUrl) && !isEmptyDocumentUrl(lastPageUrl) ? lastPageUrl : homePage;
        SpiderDebug.log("webhome-webview", "restore reload reason=empty-url current=%s target=%s", safeLogUrl(current), safeLogUrl(target));
        WebThemePageHost.Snapshot page = pageHost.snapshot();
        bridgeReady = false;
        sdkReady = false;
        synchronized (themeStateLock) {
            themeSession.invalidate();
        }
        logRuntime(WebThemeRuntimeDiagnostics.Event.DOCUMENT_RECOVERY, loadToken,
                page.target() == null ? null : page.target().getPage(), page.target(), target,
                WebThemeRuntimeDiagnostics.Reason.EMPTY_DOCUMENT, 0);
        listener.onWebLoading();
        lastViewportKey = "";
        injectedExtensions.clear();
        loadUrl(reloadUrl(target, true));
        return true;
    }

    private boolean isEmptyDocumentUrl(String url) {
        return TextUtils.isEmpty(url) || "about:blank".equalsIgnoreCase(url);
    }

    private void dispatchResume(long delay) {
        webView.postDelayed(() -> {
            injectViewport();
            long now = System.currentTimeMillis();
            long pausedMs = pauseAt > 0 ? Math.max(0, now - pauseAt) : 0;
            dispatchLifecycle("fmresume", "{time:" + now + ",pausedMs:" + pausedMs + "}");
        }, delay);
    }

    private void nudgeCompositor() {
        webView.setAlpha(0.99f);
        webView.postDelayed(() -> {
            webView.setAlpha(1f);
            webView.invalidate();
            webView.postInvalidateOnAnimation();
        }, 50);
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isVisible() || !Util.isLeanback() || !isRemoteKey(event)) return false;
        long start = System.currentTimeMillis();
        long gap = lastKeyAt > 0 ? start - lastKeyAt : -1;
        lastKeyAt = start;
        focusWebView("key");
        boolean handled = webView.dispatchKeyEvent(event);
        long cost = System.currentTimeMillis() - start;
        if (cost >= SLOW_KEY_MS || (KeyUtil.isActionDown(event) && event.getRepeatCount() > 0 && cost >= 12)) {
            SpiderDebug.log("webhome-key", "slow action=%s key=%s repeat=%s handled=%s cost=%sms gap=%sms focus=%s url=%s",
                    event.getAction(), event.getKeyCode(), event.getRepeatCount(), handled, cost, gap, webView.hasFocus(), safeLogUrl(webView.getUrl()));
        }
        return handled;
    }

    public boolean requestFocus(String reason) {
        return focusWebView(reason);
    }

    private boolean isRemoteKey(KeyEvent event) {
        return KeyUtil.isUpKey(event)
                || KeyUtil.isDownKey(event)
                || KeyUtil.isLeftKey(event)
                || KeyUtil.isRightKey(event)
                || KeyUtil.isEnterKey(event);
    }

    private boolean focusWebView(String reason) {
        if (webView.hasFocus()) return true;
        boolean ok = webView.requestFocus();
        SpiderDebug.log("webhome-focus", "request reason=%s ok=%s visible=%s width=%s height=%s url=%s", reason, ok, isVisible(), webView.getWidth(), webView.getHeight(), safeLogUrl(webView.getUrl()));
        return ok;
    }

    private void dispatchLifecycle(String event, String detail) {
        String script = "(function(){try{window.dispatchEvent(new CustomEvent('" + event + "',{detail:" + detail + "}));}catch(e){}})();";
        WebView current = webView;
        current.post(() -> {
            if (destroyed || current != webView) return;
            current.evaluateJavascript(script, null);
        });
    }

    private boolean isCurrentPageFinish(WebView view, String url) {
        return !destroyed && view == webView && isCurrentPageFinish(url, view.getUrl());
    }

    static boolean isCurrentPageFinish(String finishedUrl, String currentUrl) {
        return Objects.equals(finishedUrl, currentUrl);
    }

    private WebViewClient client() {
        return new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (destroyed || view != webView) return;
                WebHomeTarget currentTarget = pageHost.target();
                if (currentTarget != null && currentTarget.isRemoteGlobal() && !currentTarget.allowsMainFrameUrl(url)) {
                    SpiderDebug.log("webhome-security", "blocked remote theme navigation url=%s origin=%s", safeLogUrl(url), currentTarget.getOriginRule());
                    view.stopLoading();
                    return;
                }
                bridgeReady = false;
                sdkReady = false;
                int generation;
                synchronized (themeStateLock) {
                    generation = themeSession.invalidate();
                }
                rotateRemoteDocumentNonce();
                WebThemeRuntimeDiagnostics.log(WebThemeRuntimeDiagnostics.Event.DOCUMENT_LOAD_STARTED,
                        loadToken, generation, currentTarget == null ? null : currentTarget.getPage(),
                        currentTarget, url, WebThemeRuntimeDiagnostics.Reason.NONE, 0);
                listener.onWebRequest("PAGE", safeLogUrl(url), true);
                lastPageUrl = url;
                lastViewportKey = "";
                injectedExtensions.clear();
                markDocumentStartInjected();
                listener.onWebLoading();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (manifestRollbackInProgress) return;
                if (!isCurrentPageFinish(view, url)) return;
                WebHomeTarget currentTarget = pageHost.target();
                if (currentTarget == null) return;
                if (currentTarget.isRemoteGlobal() && !currentTarget.allowsMainFrameUrl(url)) return;
                acceptManifestActivation(currentTarget);
                logRuntime(WebThemeRuntimeDiagnostics.Event.DOCUMENT_READY, loadToken,
                        currentTarget.getPage(), currentTarget, url, WebThemeRuntimeDiagnostics.Reason.NONE, 0);
                loadToken++;
                if (currentTarget.isV2()) {
                    synchronized (themeStateLock) {
                        themeSession.cancelPending();
                    }
                }
                loadTimeoutRecoveries = 0;
                lastPageUrl = url;
                injectSdk();
                focusWebView("page-finished");
                listener.onWebReady();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (destroyed || view != webView) return;
                SpiderDebug.log("webhome-webview", "resource error main=%s code=%s url=%s",
                        request.isForMainFrame(), error.getErrorCode(), safeLogUrl(request.getUrl()));
                listener.onWebConsole("ERROR " + error.getErrorCode() + " " + error.getDescription() + " " + safeLogUrl(request.getUrl()));
                if (request.isForMainFrame()) {
                    handleMainFrameFailure(WebThemeRuntimeDiagnostics.Reason.WEB_RESOURCE_ERROR,
                            error.getErrorCode(), error.getDescription());
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (destroyed || view != webView) return;
                int status = errorResponse.getStatusCode();
                String reason = errorResponse.getReasonPhrase();
                SpiderDebug.log("webhome-webview", "http error main=%s code=%s url=%s",
                        request.isForMainFrame(), status, safeLogUrl(request.getUrl()));
                listener.onWebConsole("HTTP " + status + " " + reason + " " + safeLogUrl(request.getUrl()));
                if (request.isForMainFrame()) {
                    handleMainFrameFailure(WebThemeRuntimeDiagnostics.Reason.HTTP_ERROR,
                            status, "HTTP " + status + " " + reason);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (destroyed || view != webView) return true;
                WebHomeTarget currentTarget = pageHost.target();
                boolean blocked = currentTarget != null && currentTarget.isRemoteGlobal()
                        && !currentTarget.allowsMainFrameUrl(request.getUrl().toString());
                if (blocked) SpiderDebug.log("webhome-security", "blocked remote theme navigation url=%s origin=%s",
                        safeLogUrl(request.getUrl()), currentTarget.getOriginRule());
                return blocked;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (destroyed || view != webView) return blockedNavigation();
                listener.onWebRequest(request.getMethod(), request.getUrl().toString(), request.isForMainFrame(), request.getRequestHeaders());
                WebHomeTarget currentTarget = pageHost.target();
                if (currentTarget != null && currentTarget.isRemoteGlobal() && request.isForMainFrame()
                        && !currentTarget.allowsMainFrameUrl(request.getUrl().toString())) return blockedNavigation();
                WebResourceResponse raw = rawAdapter == null ? null : rawAdapter.intercept(request);
                return raw == null ? super.shouldInterceptRequest(view, request) : raw;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (destroyed || view != webView) return true;
                SpiderDebug.log("webhome-webview", "render process gone didCrash=%s priority=%s", detail.didCrash(), detail.rendererPriorityAtExit());
                WebThemePageHost.Snapshot page = pageHost.snapshot();
                int code = detail.didCrash() ? 1 : 0;
                boolean rollbackCandidate = manifestRollbackInProgress
                        || manifestActivation != null && manifestActivation.rollbackAvailable();
                if (rollbackCandidate) {
                    logRuntime(WebThemeRuntimeDiagnostics.Event.FALLBACK, loadToken,
                            page.target() == null ? null : page.target().getPage(), page.target(), homePage,
                            WebThemeRuntimeDiagnostics.Reason.RENDER_PROCESS_GONE, code);
                    if (recreateWebView() && rollbackPendingManifest(code)) return true;
                    manifestActivation = null;
                    manifestRollbackInProgress = false;
                    listener.onWebError();
                    return true;
                }
                if (recreateWebView() && !TextUtils.isEmpty(homePage)) {
                    logRuntime(WebThemeRuntimeDiagnostics.Event.DOCUMENT_RECOVERY, loadToken,
                            page.target() == null ? null : page.target().getPage(), page.target(), homePage,
                            WebThemeRuntimeDiagnostics.Reason.RENDER_PROCESS_GONE, code);
                    listener.onWebLoading();
                    loadUrl(reloadUrl(homePage, true));
                } else {
                    logRuntime(WebThemeRuntimeDiagnostics.Event.FALLBACK, loadToken,
                            page.target() == null ? null : page.target().getPage(), page.target(), homePage,
                            WebThemeRuntimeDiagnostics.Reason.RENDER_PROCESS_GONE, code);
                    listener.onWebError();
                }
                return true;
            }
        };
    }

    private void handleMainFrameFailure(WebThemeRuntimeDiagnostics.Reason reason, int code,
            CharSequence message) {
        WebThemePageHost.Snapshot page = pageHost.snapshot();
        logRuntime(WebThemeRuntimeDiagnostics.Event.FALLBACK, loadToken,
                page.target() == null ? null : page.target().getPage(), page.target(), webView.getUrl(), reason, code);
        if (rollbackPendingManifest(code)) return;
        loadToken++;
        manifestLoadToken++;
        bridgeReady = false;
        sdkReady = false;
        synchronized (themeStateLock) {
            themeSession.invalidate();
            pageHost.failPage();
        }
        invalidateRemoteSession();
        bridgeKey = "";
        homePage = null;
        homeIdentity = null;
        pageHeaders = Collections.emptyMap();
        rawAdapter = null;
        if (!TextUtils.isEmpty(message)) Notify.show(message.toString());
        listener.onWebError();
    }

    private WebChromeClient chrome() {
        return new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                if (message != null) {
                    WebThemeRuntimeDiagnostics.logConsole(
                            message.messageLevel(), message.lineNumber(), message.sourceId(), message.message());
                    String line = String.format(Locale.ROOT, "%s %s:%s %s", message.messageLevel(),
                            safeLogUrl(message.sourceId()), message.lineNumber(), message.message());
                    listener.onWebConsole(line);
                }
                return super.onConsoleMessage(message);
            }
        };
    }

    private void injectSdk() {
        WebView current = webView;
        ThemeRuntimeSnapshot runtime = getThemeRuntimeSnapshot();
        WebHomeTarget currentTarget = runtime.page().target();
        int generation = runtime.session().generation();
        if (destroyed || current == null || currentTarget == null) return;
        injectViewport();
        String sdk = currentTarget.isRemoteGlobal() ? getRemoteSdk() : getSdk();
        bridgeReady = true;
        current.evaluateJavascript(sdk, value -> {
            if (destroyed || current != webView || !isThemeRuntimeCurrent(currentTarget, generation)) return;
            sdkReady = true;
            injectExtensions(WebHomeExtension.RUN_AT_END);
            current.postDelayed(() -> {
                if (destroyed || current != webView || !isThemeRuntimeCurrent(currentTarget, generation)) return;
                injectExtensions(WebHomeExtension.RUN_AT_IDLE);
            }, 600);
        });
    }

    static String safeLogUrl(Object value) {
        return WebThemeRuntimeDiagnostics.safeUrl(value);
    }

    static String safeLogUrl(String value) {
        return WebThemeRuntimeDiagnostics.safeUrl(value);
    }

    private WebResourceResponse blockedNavigation() {
        byte[] body = "Cross-origin navigation is not allowed for remote themes."
                .getBytes(StandardCharsets.UTF_8);
        return new WebResourceResponse("text/plain", "utf-8", 403, "Forbidden",
                Collections.singletonMap(HttpHeaders.CACHE_CONTROL, "no-store"), new ByteArrayInputStream(body));
    }

    private String getRemoteSdk() {
        String session = remoteBridgeNonce;
        return """
                (function(){
                  const session='%s';
                  if(window.fongmi&&window.fongmi.__remote&&window.fongmi.__session===session){return;}
                  const callbacks={};
                  let seq=0;
                  const bridge=window.fongmiRemote;
                  const invoke=function(method,payload){
                    return new Promise(function(resolve,reject){
                      if(!bridge||typeof bridge.postMessage!=='function'){reject(new Error('bridge unavailable'));return;}
                      const id='fm_'+Date.now()+'_'+(++seq);
                      callbacks[id]={resolve:resolve,reject:reject};
                      bridge.postMessage(JSON.stringify({id:id,session:session,method:method,payload:payload||{}}));
                    });
                  };
                  if(bridge)bridge.onmessage=function(event){
                    try{
                      const data=JSON.parse(event.data||'{}');
                      const callback=callbacks[data.id];
                      if(!callback)return;
                      delete callbacks[data.id];
                      if(data.error){
                        const error=new Error(data.error);
                        error.code=data.errorCode||data.error;
                        callback.reject(error);
                      }else callback.resolve(data.result);
                    }catch(ignore){}
                  };
                  window.fongmi={
                    __remote:true,
                    __session:session,
                    theme:{info:function(){return invoke('theme.info',{});}},
                    vod:{home:function(options){return invoke('vod.home',options);},category:function(typeId,page,options){return invoke('vod.category',Object.assign({},options||{},{typeId:typeId,page:page||1}));},detail:function(options){return invoke('vod.detail',options||{});}},
                    favorite:{status:function(options){return invoke('favorite.status',options||{});},set:function(favorite,options){return invoke('favorite.set',Object.assign({},options||{},{favorite:favorite}));}},
                    history:{item:function(options){return invoke('history.item',options||{});}},
                    person:{open:function(personRef){return invoke('person.open',{personRef:personRef});}},
                    image:{preview:function(imageRef){return invoke('image.preview',{imageRef:imageRef});},save:function(imageRef){return invoke('image.save',{imageRef:imageRef});}},
                    recommendation:{open:function(recommendationRef){return invoke('recommendation.open',{recommendationRef:recommendationRef});},info:function(recommendationRef){return invoke('recommendation.info',{recommendationRef:recommendationRef});},feedback:function(recommendationRef,action){return invoke('recommendation.feedback',{recommendationRef:recommendationRef,action:action});}},
                    external:{open:function(linkRef){return invoke('external.open',{linkRef:linkRef});}},
                    episode:{info:function(episodeRef){return invoke('episode.info',{episodeRef:episodeRef});}},
                    app:{search:function(keyword,options){return invoke('app.search',Object.assign({},options||{},{keyword:keyword}));},openVod:function(){return invoke('app.openVod',{});},openSite:function(){return invoke('app.openSite',{});},openSetting:function(){return invoke('app.openSetting',{});}},
                    player:{playVod:function(siteKey,vodId,title,pic,options){return invoke('player.playVod',Object.assign({},options||{},{siteKey:siteKey,vodId:vodId,title:title,pic:pic}));}},
                    ui:{getViewport:function(){return invoke('ui.getViewport',{});}},
                    navigation:{back:function(){return invoke('navigation.back',{});},reload:function(){return invoke('navigation.reload',{});},openDetail:function(options){return invoke('navigation.openDetail',options||{});},openNativeDetail:function(options){return invoke('navigation.openNativeDetail',options||{});}}
                  };
                  window.fm={vodHome:window.fongmi.vod.home,vodCategory:window.fongmi.vod.category,vodDetail:window.fongmi.vod.detail,vod:window.fongmi.player.playVod,themeInfo:window.fongmi.theme.info,openDetail:window.fongmi.navigation.openDetail,openNativeDetail:window.fongmi.navigation.openNativeDetail,favoriteStatus:window.fongmi.favorite.status,favoriteSet:window.fongmi.favorite.set,detailHistory:window.fongmi.history.item,person:window.fongmi.person,image:window.fongmi.image,recommendation:window.fongmi.recommendation,external:window.fongmi.external,episode:window.fongmi.episode,back:window.fongmi.navigation.back,reload:window.fongmi.navigation.reload,search:window.fongmi.app.search,openVod:window.fongmi.app.openVod,openSite:window.fongmi.app.openSite,openSetting:window.fongmi.app.openSetting};
                  window.dispatchEvent(new CustomEvent('fmsdk'));
                })();
                """.replace("%s", session);
    }

    private void prepareExtensions(Site site) {
        String key = site.getKey();
        WebHomeExtensionRegistry.get().prepare(site, () -> {
            Site current = pageHost.site();
            if (current == null || !key.equals(current.getKey())) return;
            registerDocumentStartScripts();
            injectExtensions(WebHomeExtension.RUN_AT_END);
            webView.postDelayed(() -> injectExtensions(WebHomeExtension.RUN_AT_IDLE), 600);
        });
    }

    private void registerDocumentStartScripts() {
        removeDocumentStartScripts();
        WebThemePageHost.Snapshot page = pageHost.snapshot();
        Site site = page.site();
        WebHomeTarget target = page.target();
        if (target == null || !target.injectsSiteExtensions() || site == null || !isDocumentStartSupported()) return;
        String script = documentStartScript(site);
        if (TextUtils.isEmpty(script)) return;
        try {
            documentStartHandler = WebViewCompat.addDocumentStartJavaScript(webView, script, Collections.singleton("*"));
            documentStartKey = site.getKey();
            SpiderDebug.log("webhome-ext", "document-start registered site=%s", documentStartKey);
        } catch (Throwable e) {
            documentStartHandler = null;
            documentStartKey = "";
            SpiderDebug.log("webhome-ext", "document-start register failed site=%s error=%s", site.getKey(), e.getMessage());
        }
    }

    private void removeDocumentStartScripts() {
        try {
            if (documentStartHandler != null) documentStartHandler.remove();
        } catch (Throwable e) {
            SpiderDebug.log("webhome-ext", "document-start remove failed error=%s", e.getMessage());
        }
        documentStartHandler = null;
        documentStartKey = "";
    }

    private String documentStartScript(Site site) {
        StringBuilder script = new StringBuilder();
        for (WebHomeExtension extension : WebHomeExtensionRegistry.get().get(site.getKey())) {
            if (!WebHomeExtension.RUN_AT_START.equals(extension.getRunAt())) continue;
            if (script.length() == 0) script.append(getSdk());
            script.append('\n').append(extension.script(site.getKey()));
        }
        return script.toString();
    }

    private void markDocumentStartInjected() {
        WebThemePageHost.Snapshot page = pageHost.snapshot();
        Site site = page.site();
        WebHomeTarget target = page.target();
        if (target == null || !target.injectsSiteExtensions() || site == null || TextUtils.isEmpty(documentStartKey)
                || !documentStartKey.equals(site.getKey())) return;
        for (WebHomeExtension extension : WebHomeExtensionRegistry.get().get(site.getKey())) {
            if (!WebHomeExtension.RUN_AT_START.equals(extension.getRunAt())) continue;
            injectedExtensions.add(extension.getId());
            WebHomeExtensionRegistry.get().recordInject(extension, site.getKey(), WebHomeExtension.RUN_AT_START);
        }
    }

    private boolean isDocumentStartSupported() {
        try {
            return WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        } catch (Throwable e) {
            return false;
        }
    }

    private void injectExtensions(String runAt) {
        WebThemePageHost.Snapshot page = pageHost.snapshot();
        Site site = page.site();
        WebHomeTarget target = page.target();
        if (target == null || !target.injectsSiteExtensions() || !sdkReady || site == null || !isVisible()) return;
        for (WebHomeExtension extension : WebHomeExtensionRegistry.get().get(site.getKey())) {
            if (!extension.shouldInjectAt(runAt)) continue;
            if (!injectedExtensions.add(extension.getId())) continue;
            if (WebHomeExtension.RUN_AT_START.equals(extension.getRunAt())) SpiderDebug.log("webhome-ext", "document-start downgraded id=%s site=%s", extension.getId(), site.getKey());
            SpiderDebug.log("webhome-ext", "inject id=%s runAt=%s target=%s site=%s", extension.getId(), extension.getRunAt(), runAt, site.getKey());
            WebHomeExtensionRegistry.get().recordInject(extension, site.getKey(), runAt);
            if (debugTools) dispatchDebugConsole("EXT", "inject id=" + extension.getId() + " runAt=" + extension.getRunAt() + " target=" + runAt);
            webView.evaluateJavascript(extension.script(site.getKey()), null);
        }
    }

    private void injectViewport() {
        if (webView.getWidth() <= 0 || webView.getHeight() <= 0) return;
        String key = viewport.key(density, webView.getWidth(), webView.getHeight());
        if (key.equals(lastViewportKey)) return;
        lastViewportKey = key;
        String script = viewport.script(density, webView.getWidth(), webView.getHeight());
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private String reloadUrl(String url) {
        return reloadUrl(url, false);
    }

    private String reloadUrl(String url, boolean restore) {
        try {
            Uri.Builder builder = Uri.parse(url).buildUpon().appendQueryParameter("_fm_reload", String.valueOf(System.currentTimeMillis()));
            if (restore) builder.appendQueryParameter("_fm_restore", "1");
            return builder.build().toString();
        } catch (Throwable e) {
            return url + (url.contains("?") ? "&" : "?") + "_fm_reload=" + System.currentTimeMillis() + (restore ? "&_fm_restore=1" : "");
        }
    }

    private String getHomePage(Site site) {
        String url = site.getHomePage();
        if (UrlUtil.scheme(url).isEmpty()) url = UrlUtil.resolve(VodConfig.getUrl(), url);
        return UrlUtil.convert(url);
    }

    private String getSdk() {
        return String.format(Locale.ROOT, """
                (function(){
                  if(window.fm&&window.fongmi){window.dispatchEvent(new CustomEvent('fmsdk'));return;}
                  if(document&&document.documentElement)document.documentElement.classList.add('fm-native');
                  window.fongmiClient={mode:'%s',isLeanback:%s};
                  const callbacks={};
                  let seq=0;
                  function invoke(method,payload){
                    return new Promise((resolve,reject)=>{
                      const id='fm_'+Date.now()+'_'+(++seq);
                      callbacks[id]={resolve,reject};
                      fongmiBridge.invoke(id,method,JSON.stringify(payload||{}));
                    });
                  }
                  function hydrate(data){
                    if(!data||!data.__fmResultId)return data;
                    const resultId=data.__fmResultId;
                    const length=fongmiBridge.resultLength(resultId);
                    let text='';
                    for(let start=0;start<length;start+=60000)text+=fongmiBridge.resultChunk(resultId,start);
                    fongmiBridge.clearResult(resultId);
                    return JSON.parse(text);
                  }
                  window.fongmiNative={
                    resolve:(id,data)=>{ if(callbacks[id]){ callbacks[id].resolve(hydrate(data)); delete callbacks[id]; } },
                    reject:(id,message,code)=>{ if(callbacks[id]){ const error=new Error(message||''); if(code)error.code=code; callbacks[id].reject(error); delete callbacks[id]; } }
                  };
                  if(!window.__fmUrlHook&&window.history){
                    window.__fmUrlHook=true;
                    const emit=()=>window.dispatchEvent(new CustomEvent('fmurlchange',{detail:{url:location.href}}));
                    const rawPush=history.pushState;
                    const rawReplace=history.replaceState;
                    history.pushState=function(){const r=rawPush.apply(this,arguments);emit();return r;};
                    history.replaceState=function(){const r=rawReplace.apply(this,arguments);emit();return r;};
                    window.addEventListener('popstate',emit);
                  }
                  %s
                  const player={
                    playUrl:(url,title,options)=>invoke('player.playUrl',Object.assign({},options||{},{url,title})),
                    playVod:(siteKey,vodId,title,pic,options)=>invoke('player.playVod',Object.assign({},options||{},{siteKey,vodId,title,pic})),
                    playVodInline:(payload)=>invoke('player.playVodInline',payload||{}),
                    preloadArtwork:(pic,wallPic)=>invoke('player.preloadArtwork',{pic,wallPic}),
                    control:(action)=>invoke('player.control',{action}),
                    status:()=>invoke('player.status',{})
                  };
                  const net={
                    request:(url,options)=>invoke('net.request',Object.assign({},options||{},{url})),
                    resourceUrl:(url,options)=>fongmiBridge.resourceUrl(url,JSON.stringify(options||{}))
                  };
                  const vod={
                    home:(options)=>invoke('vod.home',options||{}),
                    category:(typeId,page,options)=>invoke('vod.category',Object.assign({},options||{},{typeId,page:page||1})),
                    detail:(options)=>invoke('vod.detail',options||{})
                  };
                  const favorite={
                    status:(options)=>invoke('favorite.status',options||{}),
                    set:(value,options)=>invoke('favorite.set',Object.assign({},options||{},{favorite:value}))
                  };
                  const detailHistory={item:(options)=>invoke('history.item',options||{})};
                  const person={open:(personRef)=>invoke('person.open',{personRef})};
                  const image={
                    preview:(imageRef)=>invoke('image.preview',{imageRef}),
                    save:(imageRef)=>invoke('image.save',{imageRef})
                  };
                  const recommendation={
                    open:(recommendationRef)=>invoke('recommendation.open',{recommendationRef}),
                    info:(recommendationRef)=>invoke('recommendation.info',{recommendationRef}),
                    feedback:(recommendationRef,action)=>invoke('recommendation.feedback',{recommendationRef,action})
                  };
                  const external={open:(linkRef)=>invoke('external.open',{linkRef})};
                  const episode={info:(episodeRef)=>invoke('episode.info',{episodeRef})};
                  const cache={
                    get:(key,rule)=>invoke('cache.get',{key,rule}),
                    set:(key,value,rule)=>invoke('cache.set',{key,value,rule}),
                    del:(key,rule)=>invoke('cache.del',{key,rule})
                  };
                  const pan={
                    check:(items)=>invoke('pan.check',{items}),
                    play:(payload)=>invoke('pan.play',payload||{})
                  };
                  const ext={
                    info:()=>invoke('ext.info',{}),
                    log:(message,data)=>invoke('ext.log',{message,data}),
                    toast:(message)=>invoke('ext.toast',{message})
                  };
                  const ui={
                    setToolbar:(visible)=>invoke('ui.setToolbar',{visible:visible!==false}),
                    setChrome:(options)=>invoke('ui.setChrome',options||{}),
                    restoreChrome:()=>invoke('ui.restoreChrome',{}),
                    getViewport:()=>invoke('ui.getViewport',{})
                  };
                  window.fongmi={invoke,player,net,vod,cache,
                    theme:{info:()=>invoke('theme.info',{})},
                    favorite,
                    history:detailHistory,
                    person,
                    image,
                    recommendation,
                    external,
                    episode,
                    app:{
                      search:(keyword,options)=>invoke('app.search',Object.assign({},options||{},{keyword})),
                      openVod:()=>invoke('app.openVod',{}),
                      openSite:()=>invoke('app.openSite',{}),
                      openLive:()=>invoke('app.openLive',{}),
                      openKeep:()=>invoke('app.openKeep',{}),
                      openSetting:()=>invoke('app.openSetting',{}),
                      history:()=>invoke('app.history',{})
                    },
                    pan,
                    ext,
                    device:{info:()=>invoke('device.info',{})},
                    site:{info:()=>invoke('site.info',{})},
                    config:{info:()=>invoke('config.info',{})},
                    ui,
                    navigation:{
                      back:()=>invoke('navigation.back',{}),
                      reload:()=>invoke('navigation.reload',{}),
                      openDetail:(options)=>invoke('navigation.openDetail',options||{}),
                      openNativeDetail:(options)=>invoke('navigation.openNativeDetail',options||{})
                    }
                  };
                  window.fm={
                    req:net.request,
                    res:net.resourceUrl,
                    play:player.playUrl,
                    vod:player.playVod,
                    vodHome:vod.home,
                    vodCategory:vod.category,
                    vodDetail:vod.detail,
                    vodInline:player.playVodInline,
                    preloadArtwork:player.preloadArtwork,
                    ctrl:player.control,
                    stat:player.status,
                    search:window.fongmi.app.search,
                    openVod:window.fongmi.app.openVod,
                    openSite:window.fongmi.app.openSite,
                    openLive:window.fongmi.app.openLive,
                    openKeep:window.fongmi.app.openKeep,
                    openSetting:window.fongmi.app.openSetting,
                    history:window.fongmi.app.history,
                    themeInfo:window.fongmi.theme.info,
                    openDetail:window.fongmi.navigation.openDetail,
                    openNativeDetail:window.fongmi.navigation.openNativeDetail,
                    favoriteStatus:favorite.status,
                    favoriteSet:favorite.set,
                    detailHistory:detailHistory.item,
                    person,
                    image,
                    recommendation,
                    external,
                    episode,
                    pan,
                    check:window.fongmi.pan.check,
                    cache,
                    ext,
                    ui,
                    device:window.fongmi.device.info,
                    site:window.fongmi.site.info,
                    config:window.fongmi.config.info,
                    back:window.fongmi.navigation.back,
                    reload:window.fongmi.navigation.reload
                  };
                  window.dispatchEvent(new CustomEvent('fmsdk'));
                })();
                """, com.fongmi.android.tv.BuildConfig.FLAVOR_mode, com.fongmi.android.tv.utils.Util.isLeanback(), debugTools ? debugSdkHook() : "");
    }

    private String debugSdkHook() {
        return """
                  if(!window.__fmConsoleHook){
                    window.__fmConsoleHook=true;
                    ['log','info','warn','error','debug'].forEach(function(level){
                      const raw=console[level]||console.log;
                      console[level]=function(){
                        const args=Array.prototype.slice.call(arguments);
                        try{fongmiBridge.console(level,args.map(function(v){try{return typeof v==='string'?v:JSON.stringify(v);}catch(e){return String(v);}}).join(' '));}catch(e){}
                        return raw&&raw.apply(console,args);
                      };
                    });
                  }
                  if(!window.__fmNetworkHook){
                    window.__fmNetworkHook=true;
                    const absolute=function(url){try{return new URL(String(url),location.href).href;}catch(e){return String(url||'');}};
                    const clip=function(value){value=String(value||'');return value.length>2000?value.slice(0,2000)+'\\n...truncated':value;};
                    const bodyText=function(body){
                      if(!body)return '';
                      if(typeof body==='string')return 'payload:\\n'+clip(body);
                      if(body instanceof URLSearchParams)return 'payload:\\n'+clip(body.toString());
                      if(body instanceof FormData){
                        const out=[];
                        try{body.forEach(function(v,k){out.push(k+'='+(v&&v.name?'[file '+v.name+']':String(v)));});}catch(e){}
                        return 'payload:\\n'+clip(out.join('\\n'));
                      }
                      return 'payloadType='+Object.prototype.toString.call(body)+'\\npayloadBytes='+String(body).length;
                    };
                    const headers=function(headers){
                      const out=[];
                      try{
                        if(headers&&headers.forEach)headers.forEach(function(v,k){out.push(k+': '+v);});
                        else if(headers)Object.keys(headers).forEach(function(k){out.push(k+': '+headers[k]);});
                      }catch(e){}
                      return out.join('\\n');
                    };
                    const rawFetch=window.fetch;
                    if(rawFetch){
                      window.fetch=function(input,init){
                        const started=Date.now();
                        const method=(init&&init.method)||(input&&input.method)||'GET';
                        const url=absolute(input&&input.url?input.url:input);
                        const requestHeaders=headers((init&&init.headers)||(input&&input.headers));
                        const body=bodyText(init&&init.body);
                        try{fongmiBridge.network('FETCH_START',method,url,0,0,[requestHeaders,body].filter(Boolean).join('\\n'));}catch(e){}
                        return rawFetch.apply(this,arguments).then(function(resp){
                          try{fongmiBridge.network('FETCH_DONE',method,url,resp.status||0,Date.now()-started,['type='+(resp.type||''),'headers:',headers(resp.headers)].join('\\n'));}catch(e){}
                          return resp;
                        }).catch(function(err){
                          try{fongmiBridge.network('FETCH_ERROR',method,url,0,Date.now()-started,String(err&&err.message||err));}catch(e){}
                          throw err;
                        });
                      };
                    }
                    const RawXHR=window.XMLHttpRequest;
                    if(RawXHR&&RawXHR.prototype){
                      const rawOpen=RawXHR.prototype.open;
                      const rawSend=RawXHR.prototype.send;
                      RawXHR.prototype.open=function(method,url){
                        this.__fmMethod=method||'GET';
                        this.__fmUrl=absolute(url);
                        return rawOpen.apply(this,arguments);
                      };
                      RawXHR.prototype.send=function(){
                        const xhr=this;
                        const started=Date.now();
                        const body=arguments.length?bodyText(arguments[0]):'';
                        try{fongmiBridge.network('XHR_START',xhr.__fmMethod||'GET',xhr.__fmUrl||'',0,0,body);}catch(e){}
                        xhr.addEventListener('loadend',function(){
                          try{fongmiBridge.network('XHR_DONE',xhr.__fmMethod||'GET',xhr.__fmUrl||'',xhr.status||0,Date.now()-started,[xhr.statusText||'',xhr.getAllResponseHeaders&&xhr.getAllResponseHeaders()||''].filter(Boolean).join('\\n'));}catch(e){}
                        });
                        xhr.addEventListener('error',function(){
                          try{fongmiBridge.network('XHR_ERROR',xhr.__fmMethod||'GET',xhr.__fmUrl||'',xhr.status||0,Date.now()-started,'error');}catch(e){}
                        });
                        return rawSend.apply(this,arguments);
                      };
                    }
                  }
                """;
    }

    public interface Listener {

        void onWebLoading();

        void onWebReady();

        void onWebError();

        default void setToolbar(boolean visible) {
        }

        default void applyDefaultChrome(Site site) {
        }

        default void setChrome(JsonObject payload) {
        }

        default void restoreChrome() {
        }

        default WebHomeViewport getViewport() {
            return WebHomeViewport.EMPTY;
        }

        default void openVod() {
        }

        default void openSite() {
        }

        default void openSetting() {
        }

        default void onWebConsole(String line) {
        }

        default void onDetailVodLoaded(Vod vod) {
        }

        default void onWebRequest(String method, String url, boolean mainFrame) {
        }

        default void onWebRequest(String method, String url, boolean mainFrame, Map<String, String> headers) {
            onWebRequest(method, url, mainFrame);
        }

        default void onWebNetwork(String type, String method, String url, int status, long durationMs, String detail) {
        }
    }
}
