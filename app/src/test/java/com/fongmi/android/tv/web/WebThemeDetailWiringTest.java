package com.fongmi.android.tv.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WebThemeDetailWiringTest {

    @Test
    public void detailPlaybackResolvesSessionReferenceBeforeStartingNativePlayer() throws Exception {
        String bridge = read("src/main/java/com/fongmi/android/tv/web/WebHomeThemeBridge.java");

        assertTrue(bridge.contains("context.playSession.resolve(playRef, site.getKey(), vodId)"));
        assertTrue(bridge.contains("VideoActivity.startDirect(activity"));
        assertFalse(bridge.contains("addProperty(\"episodeUrl\""));
    }

    @Test
    public void detailPlaybackCanExplicitlyResumeHistoryInBothPlayerFlavors() throws Exception {
        String bridge = read("src/main/java/com/fongmi/android/tv/web/WebHomeThemeBridge.java");
        String mobile = read("src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String leanback = read("src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

        assertTrue(bridge.contains("optionalBoolean(payload, \"resume\", false)"));
        assertTrue(bridge.contains("selection.getEpisodeUrl(), resume"));
        assertTrue(mobile.contains("public static void startDirect(Activity activity, String key, String id"));
        assertTrue(leanback.contains("public static void startDirect(Activity activity, String key, String id"));
        assertTrue(mobile.contains("EXTRA_RESUME_FROM_HISTORY, resumeFromHistory"));
        assertTrue(leanback.contains("EXTRA_RESUME_FROM_HISTORY, resumeFromHistory"));
    }

    @Test
    public void trustedV2BridgePinsCallsToTheInvokingThemeGeneration() throws Exception {
        String bridge = read("src/main/java/com/fongmi/android/tv/web/HomeWebBridge.java");
        String controller = read("src/main/java/com/fongmi/android/tv/web/HomeWebController.java");

        assertTrue(bridge.contains("HomeWebController.ThemeRuntimeSnapshot runtime = controller.getThemeRuntimeSnapshot()"));
        assertTrue(bridge.contains("int themeGeneration = runtime.session().generation()"));
        assertTrue(bridge.contains("controller.isThemeSessionActive(themeGeneration)"));
        assertTrue(controller.contains("if (currentTarget.isV2()) {"));
        assertTrue(controller.contains("themeSession.cancelPending();"));
        assertTrue(controller.contains("history:detailHistory"));
    }

    @Test
    public void themeBridgeRejectsResultsWhenGenerationChangesDuringProviderCalls() throws Exception {
        String bridge = read("src/main/java/com/fongmi/android/tv/web/WebHomeThemeBridge.java");
        String router = read("src/main/java/com/fongmi/android/tv/web/WebThemeCallRouter.java");
        String invoke = section(bridge, "String invoke", "private String dispatch");
        String dispatch = section(bridge, "private String dispatch", "private String vodHome");
        String home = section(bridge, "private String vodHome", "private String vodCategory");
        String category = section(bridge, "private String vodCategory", "private String playVod");
        String detail = section(bridge, "private String vodDetail", "private String favoriteStatus");

        assertTrue(invoke.contains("CallContext context = new CallContext(runtime)"));
        assertTrue(invoke.contains("callRouter.invoke(method, payload, context.target, active"));
        assertTrue(dispatch.contains("controller.getThemeInfoJson(context.runtime)"));
        assertTrue(router.contains("target.getPermissions()"));
        assertFalse(invoke.contains("controller.getThemeTarget().getPermissions()"));
        assertTrue(home.indexOf("requireActive(active);") > home.indexOf("SiteApi.homeContent"));
        assertTrue(category.indexOf("requireActive(active);") > category.indexOf("SiteApi.categoryContent"));
        assertTrue(detail.indexOf("requireActive(active);") > detail.indexOf("SiteApi.detailContent"));
        assertTrue(detail.contains("controller.setDetailVodIfCurrent(context.runtime"));
    }

    @Test
    public void detailBridgeReadsLocalStateOnlyWithDeclaredReadPermissions() throws Exception {
        String bridge = read("src/main/java/com/fongmi/android/tv/web/WebHomeThemeBridge.java");
        String detail = section(bridge, "private String vodDetail", "private String favoriteStatus");

        assertTrue(detail.contains("permissions.contains(\"favorite.read\")"));
        assertTrue(detail.contains("permissions.contains(\"history.read\")"));
        assertTrue(detail.contains("canReadFavorite ? Keep.find"));
        assertTrue(detail.contains("canReadHistory ? History.findPlayback"));
    }

    @Test
    public void themeInfoUsesOneTargetAndRouteSnapshot() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/web/HomeWebController.java");
        String compatibility = section(controller, "String getThemeInfoJson()",
                "String getThemeInfoJson(ThemeRuntimeSnapshot runtime)");
        String method = section(controller, "String getThemeInfoJson(ThemeRuntimeSnapshot runtime)",
                "public void setViewport");

        assertTrue(compatibility.contains("return getThemeInfoJson(getThemeRuntimeSnapshot());"));
        assertFalse(method.contains("getThemeRuntimeSnapshot()"));
        assertTrue(method.contains("WebThemePageHost.Snapshot page = runtime.page();"));
        assertTrue(method.contains("WebHomeTarget currentTarget = page.target();"));
        assertTrue(method.contains("WebThemeRoute currentRoute = page.route();"));
        assertTrue(method.contains("runtime.session().accessSession()"));
        assertTrue(method.contains("currentRoute.json(currentAccessSession.issueRoute(currentRoute.getVodId()))"));
        assertTrue(method.contains("WebThemeCapabilityRegistry.capabilities("));
        assertFalse(method.contains("WebHomeThemePolicy.allowsPermission"));
        assertFalse(method.contains("target.get"));
    }

    @Test
    public void v2ProviderIdentifiersAreResolvedOnlyThroughTheActiveAccessSession() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/web/HomeWebController.java");
        String bridge = read("src/main/java/com/fongmi/android/tv/web/WebHomeThemeBridge.java");
        String home = section(bridge, "private String vodHome", "private String vodCategory");
        String category = section(bridge, "private String vodCategory", "private String playVod");
        String play = section(bridge, "private String playVod", "private String vodDetail");
        String detail = section(bridge, "private String vodDetail", "private String favoriteStatus");
        String openDetail = section(bridge, "private String openDetail", "private String openNativeDetail");

        assertTrue(controller.contains("private final WebThemeSession themeSession;"));
        assertTrue(controller.contains("ThemeRuntimeSnapshot getThemeRuntimeSnapshot()"));
        assertTrue(bridge.contains("controller.getThemeRuntimeSnapshot()"));
        assertTrue(bridge.contains("runtime.session()"));
        assertTrue(bridge.contains("session.accessSession()"));
        assertTrue(home.contains("context.accessSession.protectHome"));
        assertTrue(category.contains("context.accessSession.resolveType"));
        assertTrue(category.contains("context.accessSession.resolveExtend"));
        assertTrue(category.contains("context.accessSession.protectCategory"));
        assertTrue(play.contains("context.accessSession.resolveVod"));
        assertTrue(detail.contains("context.accessSession.protectDetail"));
        assertTrue(openDetail.contains("context.accessSession.resolveVod"));
    }

    @Test
    public void manifestTransitionRevokesOldBridgeBeforePublishingPendingTarget() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/web/HomeWebController.java");
        String bridge = read("src/main/java/com/fongmi/android/tv/web/HomeWebBridge.java");
        String load = section(controller, "private boolean loadManifestPage", "private boolean isManifestLoadActive");
        String execute = section(bridge, "private String execute", "private String vodHome");
        int target = load.indexOf("pageHost.beginDocument(site, configured, route);");

        assertTrue(load.indexOf("invalidateRemoteSession();") >= 0);
        assertTrue(load.indexOf("invalidateRemoteSession();") < target);
        assertTrue(load.indexOf("webView.removeJavascriptInterface(BRIDGE);") < target);
        assertTrue(load.indexOf("bridgeKey = \"\";") < target);
        assertTrue(execute.contains("WebHomeTarget themeTarget = controller.getThemeTarget();"));
        assertTrue(execute.contains("themeTarget.isManifest()"));
    }

    @Test
    public void themeBridgeIsInactiveWhilePausedLoadingOrDestroyed() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/web/HomeWebController.java");
        String remote = section(controller, "private boolean isRemoteSession", "private static String limitedRemoteValue");
        String v2 = section(controller, "boolean isThemeSessionActive", "boolean isRemoteTheme");
        String pause = section(controller, "public void onPause()", "public boolean beginInlineEvaluation");

        assertTrue(remote.contains("!destroyed && !paused && bridgeReady"));
        assertTrue(v2.contains("!destroyed && !paused && bridgeReady"));
        assertTrue(pause.contains("webView.onPause();"));
    }

    @Test
    public void pausingHomeWebViewDoesNotFreezeOtherWebViews() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/web/HomeWebController.java");

        assertTrue(controller.contains("webView.onPause();"));
        assertTrue(controller.contains("webView.onResume();"));
        assertFalse(controller.contains("webView.pauseTimers();"));
        assertFalse(controller.contains("webView.resumeTimers();"));
    }

    @Test
    public void queuedDetailRefreshDoesNotTouchADestroyedWebView() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/web/HomeWebController.java");
        String dispatch = section(controller, "public void dispatchDetailChanged()", "WebHomeTarget getThemeTarget()");

        assertTrue(dispatch.contains("WebView current = webView;"));
        assertTrue(dispatch.contains("if (destroyed || current != webView"));
        assertTrue(dispatch.contains("current.evaluateJavascript"));
    }

    @Test
    public void detailHostIsPrivateAndFallsBackToNativeDetail() throws Exception {
        String activity = read("src/main/java/com/fongmi/android/tv/ui/activity/WebThemeDetailActivity.java");
        String mobile = read("src/mobile/AndroidManifest.xml");
        String leanback = read("src/leanback/AndroidManifest.xml");

        assertTrue(activity.contains("controller.loadThemePage(site, manifestUrl, WebThemePage.DETAIL"));
        assertTrue(activity.contains("TmdbDetailActivity.start(this"));
        assertTrue(activity.contains("public void onWebError()"));
        assertTrue(privateActivity(mobile));
        assertTrue(privateActivity(leanback));
    }

    @Test
    public void detailHostPublishesIncrementalTmdbEnrichmentToTheTheme() throws Exception {
        String activity = read("src/main/java/com/fongmi/android/tv/ui/activity/WebThemeDetailActivity.java");
        String controller = read("src/main/java/com/fongmi/android/tv/web/HomeWebController.java");
        String bridge = read("src/main/java/com/fongmi/android/tv/web/WebHomeThemeBridge.java");

        assertTrue(activity.contains("new TmdbUIAdapter(this)"));
        assertTrue(activity.contains("tmdbAdapter.autoMatch"));
        assertTrue(activity.contains("TmdbSitePolicy.isEnabled(site.getKey(), vodId)"));
        assertTrue(activity.contains("WebThemeDetailMetadata.fromTmdb"));
        assertTrue(activity.contains("controller.dispatchDetailChanged()"));
        assertTrue(controller.contains("new CustomEvent('fmdetailchange'"));
        assertTrue(bridge.contains("optionalBoolean(payload, \"cached\", false)"));
        assertTrue(bridge.contains("this.detailMetadata = page.detailMetadata();"));
        assertTrue(bridge.indexOf("if (!route.getVodId().equals(vod.getId())) vod.setId(route.getVodId());")
                < bridge.indexOf("postIfActive(active, () -> controller.setDetailVodIfCurrent(context.runtime, detailVod, playSession));"));
        assertTrue(bridge.contains("vod.setSite(site);"));
    }

    @Test
    public void detailTmdbActionsResolveOpaqueReferencesBeforeNativeSideEffects() throws Exception {
        String bridge = read("src/main/java/com/fongmi/android/tv/web/WebHomeThemeBridge.java");
        String recommendationInfo = section(bridge, "private String recommendationInfo", "private String recommendationFeedback");
        String recommendationFeedback = section(bridge, "private String recommendationFeedback", "private String openExternal");
        String controller = read("src/main/java/com/fongmi/android/tv/web/HomeWebController.java");
        String activity = read("src/main/java/com/fongmi/android/tv/ui/activity/WebThemeDetailActivity.java");
        String viewer = read("src/main/java/com/fongmi/android/tv/ui/dialog/WebThemeImageViewer.java");
        String recommendationDialog = read("src/main/java/com/fongmi/android/tv/ui/dialog/AiRecommendationInfoDialog.java");
        String detailPage = read("src/main/assets/webhome/eclipse-detail.html");

        assertTrue(bridge.contains("case \"person.open\""));
        assertTrue(bridge.contains("case \"episode.info\""));
        assertTrue(bridge.contains("resolvePerson(requiredRef(payload, \"personRef\"))"));
        assertTrue(bridge.contains("resolveImage(requiredRef(payload, \"imageRef\"))"));
        assertTrue(bridge.contains("resolveRecommendation(requiredRef(payload, \"recommendationRef\"))"));
        assertTrue(bridge.contains("resolveExternal(requiredRef(payload, \"linkRef\"))"));
        assertTrue(bridge.contains("resolveEpisode(requiredRef(payload, \"episodeRef\"))"));
        assertTrue(bridge.contains("TmdbNavigation.open"));
        assertTrue(recommendationInfo.contains("if (!active.getAsBoolean()) return;"));
        assertTrue(recommendationInfo.contains("}, active));"));
        assertTrue(recommendationFeedback.indexOf("postIfActive(active")
                < recommendationFeedback.indexOf("markNotInterested"));
        assertTrue(recommendationDialog.contains("if (active != null && !active.getAsBoolean())"));
        assertFalse(bridge.contains("Json.safeString(payload, \"url\")"));
        assertFalse(bridge.contains("catch (Throwable ignored)"));
        assertTrue(controller.contains("private final WebThemeSession themeSession;"));
        assertTrue(bridge.contains("session.detailActionSession()"));
        assertTrue(controller.contains("person:{open:"));
        assertTrue(controller.contains("const recommendation={"));
        assertTrue(controller.contains("episode:{info:"));
        assertTrue(activity.contains("getPersonalTmdbRecommendations()"));
        assertTrue(activity.contains("getPersonalDoubanRecommendations()"));
        assertTrue(activity.contains("getPersonalAiRecommendations()"));
        assertTrue(activity.contains("RefreshEvent.Type.VOD_PERSONAL"));
        assertTrue(activity.contains("dispatchKeyEvent"));
        assertTrue(activity.contains("event.isLongPress() || event.getRepeatCount() > 0"));
        assertTrue(activity.contains("controller.dispatchFocusedLongPress()"));
        assertTrue(activity.contains("controller.dispatchFocusedClick()"));
        assertTrue(activity.contains("postDelayed(confirmLongPressRunnable"));
        assertTrue(controller.contains("new MouseEvent('contextmenu'"));
        assertTrue(controller.contains("node.click()"));
        assertTrue(viewer.contains("TmdbImageSelector.originalUrl"));
        assertTrue(viewer.contains("TmdbImageSaver.save"));
        assertTrue(viewer.contains("setNextFocusDownId"));
        assertTrue(viewer.contains("setOnFocusChangeListener"));
        assertTrue(viewer.contains("view.performClick()"));
        assertTrue(viewer.contains("public boolean performClick()"));
        assertTrue(viewer.contains("setStrokeColor"));
        assertTrue(detailPage.contains("api.episode.info"));
        assertTrue(detailPage.contains("bindLongPress"));
        assertTrue(detailPage.contains("episodeRanges"));
        assertTrue(detailPage.contains("episode-marquee-track"));
    }

    @Test
    public void freshDetailAndReloadInvalidatePreviouslyIssuedPlayReferences() throws Exception {
        String bridge = read("src/main/java/com/fongmi/android/tv/web/WebHomeThemeBridge.java");
        String controller = read("src/main/java/com/fongmi/android/tv/web/HomeWebController.java");
        String detail = section(bridge, "private String vodDetail", "private String favoriteStatus");
        String reload = section(controller, "public void reload()", "public void reloadExtensions()");
        int replacement = detail.indexOf("loaded ? new WebThemePlaySession() : context.playSession");

        assertTrue(replacement > detail.indexOf("SiteApi.detailContent"));
        assertTrue(replacement < detail.indexOf("postIfActive(active, () -> controller.setDetailVodIfCurrent(context.runtime, detailVod, playSession));"));
        assertTrue(reload.contains("themeSession.invalidate();"));
    }

    @Test
    public void detailPageIgnoresRepeatedPlayClicksWhileNativeLaunchIsPending() throws Exception {
        String detail = read("src/main/assets/webhome/eclipse-detail.html");
        String play = section(detail, "function playEpisode", "function playSelected");

        assertTrue(play.contains("if (app.pending) return;"));
    }

    @Test
    public void leanbackCanFocusPeopleAndGalleryItemsOutsideTheFirstViewport() throws Exception {
        String detail = read("src/main/assets/webhome/eclipse-detail.html");

        assertTrue(detail.contains("card.setAttribute('data-focus-row', 'people')"));
        assertTrue(detail.contains("frame.setAttribute('data-focus-row', 'gallery')"));
        assertTrue(detail.contains("card.tabIndex = interactive ? 0 : -1"));
        assertTrue(detail.contains("frame.tabIndex = interactive ? 0 : -1"));
        assertTrue(detail.contains("function ensureRailItemVisible(node)"));
        assertTrue(detail.contains("ensureFocusVisible(best)"));
        assertTrue(detail.contains("scroll-padding-inline: 12px"));
    }

    @Test
    public void eclipseHomePrefersDetailNavigationAndKeepsLegacyPlaybackFallback() throws Exception {
        String home = read("src/main/assets/webhome/eclipse.html");
        int detail = home.indexOf("typeof w.fm.openDetail === 'function'");
        int legacy = home.indexOf("typeof w.fm.vod === 'function'", detail);

        assertTrue(detail >= 0);
        assertTrue(legacy > detail);
    }

    private static boolean privateActivity(String manifest) {
        int start = manifest.indexOf(".ui.activity.WebThemeDetailActivity");
        int end = manifest.indexOf("/>", start);
        return start >= 0 && end > start && manifest.substring(start, end).contains("android:exported=\"false\"");
    }

    private static String read(String relative) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }
}
