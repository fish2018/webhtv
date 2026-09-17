package com.fongmi.android.tv.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WebHomeRemoteBridgeWiringTest {

    @Test
    public void remoteThemeUsesOriginBoundMinimalBridgeWithoutNativeRawProxy() throws Exception {
        String controller = readMain("HomeWebController.java");
        String bridge = readMain("WebHomeThemeBridge.java");

        assertTrue(controller.contains("WebViewCompat.addWebMessageListener(webView, \"fongmiRemote\", Collections.singleton(allowedOrigin)"));
        assertTrue(controller.contains("WebHomeThemePolicy.allowsMessage(allowedOrigin, actualOrigin, isMainFrame)"));
        assertTrue(controller.contains("resolved.isRemoteGlobal() ? null : WebHomeRawAdapter.create(url, pageHeaders)"));
        assertTrue(controller.contains("String sdk = currentTarget.isRemoteGlobal() ? getRemoteSdk() : getSdk();"));
        assertTrue(controller.contains("if (!remote) {\n            webView.addJavascriptInterface(bridge, BRIDGE);"));
        assertFalse(bridge.contains("@JavascriptInterface"));
        assertFalse(bridge.contains("WebCall.request"));
        assertFalse(bridge.contains("Cookie"));
        assertFalse(bridge.contains("AppCache"));
        assertFalse(bridge.contains("Extension"));
    }

    @Test
    public void remoteSdkFormattingUsesAndroidCompatibleStringApi() throws Exception {
        String controller = readMain("HomeWebController.java");
        String sdk = methodBody(controller, "private String getRemoteSdk()", "private void prepareExtensions(");

        assertTrue(sdk.contains("\"\"\".replace(\"%s\", session);"));
        assertFalse(sdk.contains(".formatted("));
    }

    @Test
    public void remoteThemePinsRequestsToOneDocumentSession() throws Exception {
        String controller = readMain("HomeWebController.java");
        String registration = methodBody(controller, "private boolean registerRemoteMessageListener(",
                "private void handleRemoteMessage(");

        assertTrue(registration.contains("int generation"));
        assertFalse(registration.contains("int generation = remoteBridgeGeneration;"));
        assertTrue(controller.contains("String requestNonce"));
        assertTrue(controller.contains("isRemoteThemeSession(expectedOrigin, generation, requestNonce, themeGeneration)"));
        assertTrue(registration.contains("ThemeRuntimeSnapshot runtime = getThemeRuntimeSnapshot()"));
        assertTrue(registration.contains("int themeGeneration = runtime.session().generation()"));
        assertTrue(controller.contains("session:session"));
        assertTrue(controller.contains("window.fongmi.__session===session"));
        assertTrue(controller.contains("data.getBytes(StandardCharsets.UTF_8).length > MAX_REMOTE_MESSAGE_BYTES"));
        assertTrue(controller.contains("result.getBytes(StandardCharsets.UTF_8).length > MAX_REMOTE_RESPONSE_BYTES"));
    }

    @Test
    public void remoteThemeGenerationGuardKeepsLegacyAndV2TargetsCompatible() throws Exception {
        String controller = readMain("HomeWebController.java");
        String guards = methodBody(controller, "private boolean isRemoteThemeSession(",
                "private static String limitedRemoteValue(");

        assertTrue(guards.contains("isRemoteBridgeSessionActive(themeGeneration)"));
        assertTrue(guards.contains("current.isRemoteGlobal()"));
        assertTrue(guards.contains("!current.isManifest()"));
        assertFalse(guards.contains("isThemeSessionActive(themeGeneration)"));
    }

    @Test
    public void remoteThemeInvalidatesRecreatedViewsAndMainFrameHttpFailures() throws Exception {
        String controller = readMain("HomeWebController.java");
        String replace = methodBody(controller, "private boolean replaceWebView(", "private boolean recreateWebView()");

        assertTrue(ordered(replace, "themeSession.invalidate();", "invalidateRemoteSession();"));
        assertTrue(ordered(replace, "invalidateRemoteSession();", "webView.destroy();"));
        assertTrue(controller.contains("public void onReceivedHttpError("));
        assertTrue(controller.contains("handleMainFrameFailure("));
    }

    @Test
    public void remoteThemeUsesOriginScopedDataProfilesAndFailsClosedWithoutSupport() throws Exception {
        String controller = readMain("HomeWebController.java");
        String isolation = readMain("WebThemeDataIsolation.java");
        String load = methodBody(controller, "private boolean loadResolved(", "public void reload()");
        String ensure = methodBody(controller, "private boolean ensureDataProfile(",
                "private boolean replaceWebView(");
        String replace = methodBody(controller, "private boolean replaceWebView(",
                "private boolean recreateWebView()");

        assertTrue(ordered(load, "ensureDataProfile(resolved)", "configureBridge(resolved, reload)"));
        assertTrue(ensure.contains("WebViewFeature.MULTI_PROFILE"));
        assertTrue(ensure.contains("return false;"));
        assertTrue(replace.contains("ProfileStore.getInstance().getOrCreateProfile(desired.name())"));
        assertTrue(ordered(replace, "replacement = new WebView(activity);",
                "WebViewCompat.setProfile(replacement, desired.name())"));
        assertTrue(ordered(replace, "WebViewCompat.setProfile(replacement, desired.name())",
                "replacement.setId(id)"));
        assertTrue(controller.contains("WebViewCompat.getProfile(webView).getCookieManager()"));
        assertTrue(controller.contains("cookieManager.setAcceptThirdPartyCookies(webView, !remote)"));
        assertTrue(controller.contains("Reason.DATA_ISOLATION_UNAVAILABLE"));
        assertTrue(isolation.contains("MessageDigest.getInstance(\"SHA-256\")"));
        assertFalse(isolation.contains("getOriginRule() +"));
    }


    @Test
    public void themeInfoCapabilitiesComeFromTheSharedRegistry() throws Exception {
        String controller = readMain("HomeWebController.java");

        assertTrue(controller.contains("WebThemeCapabilityRegistry.capabilities("));
        assertFalse(controller.contains("declared.add(\"theme.info@1\")"));
        assertFalse(controller.contains("permission + \"@1\""));
    }

    @Test
    public void webThemeErrorsExposeCanonicalCodesWithoutChangingLegacyMessages() throws Exception {
        String controller = readMain("HomeWebController.java");
        String bridge = readMain("HomeWebBridge.java");

        assertTrue(controller.contains("response.addProperty(\"errorCode\", error.getCode())"));
        assertTrue(controller.contains("new Error(data.error)"));
        assertTrue(controller.contains("error.code=data.errorCode||data.error"));
        assertTrue(controller.contains("if(code)error.code=code"));
        assertTrue(controller.contains("WebThemeErrorCode.RATE_LIMITED"));
        assertTrue(controller.contains("WebThemeErrorCode.PAGE_UNAVAILABLE"));
        assertTrue(bridge.contains("WebThemeErrorCode.from(error)"));
        assertTrue(bridge.contains("mapped.getCode()"));
    }

    private static boolean ordered(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second, Math.max(0, firstIndex + first.length()));
        return firstIndex >= 0 && secondIndex > firstIndex;
    }

    private static String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue("Missing source token: " + startToken, start >= 0);
        assertTrue("Missing source token after " + startToken + ": " + endToken, end > start);
        return source.substring(start, end);
    }

    private static String readMain(String name) throws Exception {
        Path root = Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "web");
        if (!Files.exists(root)) root = Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "web");
        return new String(Files.readAllBytes(root.resolve(name)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
