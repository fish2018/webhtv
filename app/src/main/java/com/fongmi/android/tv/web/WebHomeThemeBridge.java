package com.fongmi.android.tv.web;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.text.TextUtils;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.service.RecommendationFeedbackStore;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.activity.TmdbDetailActivity;
import com.fongmi.android.tv.ui.activity.TmdbPersonActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.activity.WebThemeDetailActivity;
import com.fongmi.android.tv.ui.dialog.AiRecommendationInfoDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeDetailDialog;
import com.fongmi.android.tv.ui.dialog.WebThemeImageViewer;
import com.fongmi.android.tv.ui.helper.TmdbNavigation;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Native implementation for the minimal, origin-bound global theme protocol. */
final class WebHomeThemeBridge {

    static final int MAX_PAGE = 10_000;

    private final HomeWebController controller;
    private final Activity activity;
    private final WebThemeCallRouter callRouter;

    WebHomeThemeBridge(HomeWebController controller, Activity activity) {
        this.controller = controller;
        this.activity = activity;
        this.callRouter = new WebThemeCallRouter();
    }

    String invoke(String method, JsonObject payload, BooleanSupplier active) throws Exception {
        requireActive(active);
        HomeWebController.ThemeRuntimeSnapshot runtime = controller.getThemeRuntimeSnapshot();
        CallContext context = new CallContext(runtime);
        return callRouter.invoke(method, payload, context.target, active,
                (api, routedMethod, safe, routedActive) -> dispatch(api, routedMethod, safe, context, routedActive));
    }

    private String dispatch(WebThemeCallRouter.Api api, String method, JsonObject payload,
            CallContext context, BooleanSupplier active) throws Exception {
        return switch (api) {
            case HOME -> switch (method) {
                case "theme.info" -> controller.getThemeInfoJson(context.runtime);
                case "vod.home" -> vodHome(payload, context, active);
                default -> throw new SecurityException("PERMISSION_DENIED");
            };
            case LIST -> vodCategory(payload, context, active);
            case DETAIL -> switch (method) {
                case "vod.detail" -> vodDetail(payload, context, active);
                case "favorite.status" -> favoriteStatus(payload, context);
                case "favorite.set" -> favoriteSet(payload, context, active);
                case "history.item" -> historyItem(payload, context);
                case "person.open" -> openPerson(payload, context, active);
                case "recommendation.open" -> openRecommendation(payload, context, active);
                case "recommendation.info" -> recommendationInfo(payload, context, active);
                case "recommendation.feedback" -> recommendationFeedback(payload, context, active);
                case "episode.info" -> episodeInfo(payload, context, active);
                default -> throw new SecurityException("PERMISSION_DENIED");
            };
            case PLAYER -> playVod(payload, context, active);
            case NAVIGATION -> switch (method) {
                case "navigation.openDetail" -> openDetail(payload, context, active);
                case "navigation.openNativeDetail" -> openNativeDetail(payload, context, active);
                case "external.open" -> openExternal(payload, context, active);
                case "app.search" -> search(payload, active);
                case "app.openVod" -> openVod(active);
                case "app.openSite" -> openSite(active);
                case "app.openSetting" -> openSetting(active);
                case "navigation.back" -> back(active);
                case "navigation.reload" -> reload(active);
                default -> throw new SecurityException("PERMISSION_DENIED");
            };
            case UI -> switch (method) {
                case "image.preview" -> previewImage(payload, context, active);
                case "image.save" -> saveImage(payload, context, active);
                case "ui.getViewport" -> controller.getViewportJson();
                default -> throw new SecurityException("PERMISSION_DENIED");
            };
        };
    }

    private String vodHome(JsonObject payload, CallContext context, BooleanSupplier active) throws Exception {
        Site site = activeSite(payload, context);
        Result result = SiteApi.homeContent(site);
        requireActive(active);
        JsonObject response = WebHomeVodContract.home(site, result, Util.isLeanback(), isLandscape(),
                suggestedColumns());
        if (context.isV2()) context.accessSession.protectHome(response);
        return response.toString();
    }

    private String vodCategory(JsonObject payload, CallContext context, BooleanSupplier active) throws Exception {
        Site site = activeSite(payload, context);
        String publicTypeId = limited(Json.safeString(payload, "typeId"), 256);
        if (TextUtils.isEmpty(publicTypeId)) throw new IllegalArgumentException("typeId is required");
        String typeId = context.isV2() ? context.accessSession.resolveType(publicTypeId) : publicTypeId;
        if (TextUtils.isEmpty(typeId)) throw new SecurityException("Unknown type reference");
        int page = positiveInt(payload, "page", 1);
        boolean filter = optionalBoolean(payload, "filter", false);
        HashMap<String, String> publicExtend = extend(payload);
        HashMap<String, String> extend = context.isV2()
                ? context.accessSession.resolveExtend(publicExtend) : publicExtend;
        Result result = SiteApi.categoryContent(site.getKey(), typeId, String.valueOf(page), filter, extend);
        requireActive(active);
        JsonObject response = WebHomeVodContract.category(site, typeId, page, filter, extend, result,
                Util.isLeanback(), isLandscape(), suggestedColumns());
        if (context.isV2()) context.accessSession.protectCategory(response, publicTypeId);
        return response.toString();
    }

    private String playVod(JsonObject payload, CallContext context, BooleanSupplier active) {
        Site site = activeSite(payload, context);
        WebThemeRoute route = context.route;
        String publicVodId = limited(Json.safeString(payload, "vodId"), 2048);
        String vodId = context.isV2() && !TextUtils.isEmpty(publicVodId)
                ? context.accessSession.resolveVod(publicVodId) : publicVodId;
        if (!TextUtils.isEmpty(publicVodId) && TextUtils.isEmpty(vodId)) {
            throw new SecurityException("Unknown VOD reference");
        }
        if (context.target != null && context.target.getPage() == WebThemePage.DETAIL) {
            if (TextUtils.isEmpty(vodId)) vodId = route.getVodId();
            if (!route.getVodId().equals(vodId)) throw new SecurityException("Cross-detail playback is not allowed");
        }
        if (TextUtils.isEmpty(vodId)) throw new IllegalArgumentException("vodId is required");
        Vod detail = context.detailVod;
        String title = limited(Json.safeString(payload, "title"), 512);
        if (TextUtils.isEmpty(title)) title = limited(detail == null ? route.getTitle() : detail.getName(), 512);
        String pic = limited(Json.safeString(payload, "pic"), 4096);
        if (TextUtils.isEmpty(pic)) pic = limited(detail == null ? route.getPic() : detail.getPic(), 4096);
        String mark = limited(detail == null ? route.getRemarks() : detail.getRemarks(), 1024);
        boolean resume = optionalBoolean(payload, "resume", false);
        String playRef = limited(Json.safeString(payload, "playRef"), 128);
        WebThemePlaySession.Selection selection = TextUtils.isEmpty(playRef) ? null
                : context.playSession.resolve(playRef, site.getKey(), vodId);
        if (!TextUtils.isEmpty(playRef) && selection == null) throw new SecurityException("Invalid playRef");
        String siteKey = site.getKey();
        String finalVodId = vodId;
        String finalTitle = title;
        String finalPic = pic;
        postIfActive(active, () -> {
            Site current = controller.getContentSite();
            if (current == null || !siteKey.equals(current.getKey())) return;
            controller.prepareNativePlayback(() -> {
                if (!active.getAsBoolean()) return;
                if (selection == null) VideoActivity.start(activity, siteKey, finalVodId, finalTitle, finalPic, mark);
                else VideoActivity.startDirect(activity, siteKey, finalVodId, finalTitle, finalPic, mark,
                        selection.getFlag(), selection.getEpisodeName(), selection.getEpisodeUrl(), resume);
            });
        });
        return "{}";
    }

    private String vodDetail(JsonObject payload, CallContext context, BooleanSupplier active) throws Exception {
        Site site = activeSite(payload, context);
        WebThemeRoute route = activeDetailRoute(payload, context);
        boolean cached = optionalBoolean(payload, "cached", false);
        Vod vod = cached ? context.detailVod : null;
        boolean loaded = vod == null;
        if (loaded) {
            Result result = SiteApi.detailContent(site.getKey(), route.getVodId());
            requireActive(active);
            vod = result == null ? null : result.getVod();
            if (vod == null) vod = new Vod();
        }
        if (!route.getVodId().equals(vod.getId())) vod.setId(route.getVodId());
        if (TextUtils.isEmpty(vod.getName())) vod.setName(route.getTitle());
        if (TextUtils.isEmpty(vod.getPic())) vod.setPic(route.getPic());
        if (TextUtils.isEmpty(vod.getRemarks())) vod.setRemarks(route.getRemarks());
        if (TextUtils.isEmpty(vod.getContent())) vod.setContent(route.getContent());
        vod.setSite(site);
        String key = detailKey(site.getKey(), route.getVodId());
        Set<String> permissions = context.target.getPermissions();
        boolean canReadFavorite = permissions.contains("favorite.read");
        boolean canReadHistory = permissions.contains("history.read");
        Keep keep = canReadFavorite ? Keep.find(VodConfig.getCid(), key) : null;
        History history = canReadHistory ? History.findPlayback(key, vod.getName(), vod.getFlags()) : null;
        WebThemePlaySession playSession = loaded ? new WebThemePlaySession() : context.playSession;
        JsonObject response = WebHomeVodContract.detail(site, vod, playSession, keep != null, history,
                canReadFavorite, canReadHistory, permissions.contains("favorite.write"),
                permissions.contains("player.playVod"), permissions.contains("app.search"), context.detailMetadata,
                context.detailActionSession, permissions);
        if (context.isV2()) context.accessSession.protectDetail(response);
        String result = response.toString();
        if (loaded) {
            Vod detailVod = vod;
            postIfActive(active, () -> controller.setDetailVodIfCurrent(context.runtime, detailVod, playSession));
        }
        return result;
    }

    private String favoriteStatus(JsonObject payload, CallContext context) {
        Site site = activeSite(payload, context);
        WebThemeRoute route = activeDetailRoute(payload, context);
        return favoriteJson(Keep.find(VodConfig.getCid(), detailKey(site.getKey(), route.getVodId())) != null);
    }

    private String favoriteSet(JsonObject payload, CallContext context, BooleanSupplier active) {
        boolean favorite = requiredBoolean(payload, "favorite");
        Site site = activeSite(payload, context);
        WebThemeRoute route = activeDetailRoute(payload, context);
        String key = detailKey(site.getKey(), route.getVodId());
        Keep keep = Keep.find(VodConfig.getCid(), key);
        if (!favorite) {
            requireActive(active);
            if (keep != null) keep.delete();
            return favoriteJson(false);
        }
        Vod vod = context.detailVod;
        if (keep == null) keep = new Keep();
        keep.setKey(key);
        keep.setSiteName(site.getName());
        keep.setVodName(vod == null || TextUtils.isEmpty(vod.getName()) ? route.getTitle() : vod.getName());
        keep.setVodPic(vod == null || TextUtils.isEmpty(vod.getPic()) ? route.getPic() : vod.getPic());
        keep.setCreateTime(System.currentTimeMillis());
        keep.setType(0);
        requireActive(active);
        keep.save(VodConfig.getCid());
        return favoriteJson(true);
    }

    private String historyItem(JsonObject payload, CallContext context) {
        Site site = activeSite(payload, context);
        WebThemeRoute route = activeDetailRoute(payload, context);
        Vod vod = context.detailVod;
        History history = History.findPlayback(detailKey(site.getKey(), route.getVodId()),
                vod == null ? route.getTitle() : vod.getName(), vod == null ? java.util.List.of() : vod.getFlags());
        JsonObject root = new JsonObject();
        if (history == null) return root.toString();
        root.addProperty("positionMs", Math.max(0, history.getPosition()));
        root.addProperty("durationMs", Math.max(0, history.getDuration()));
        root.addProperty("updatedAt", Math.max(0, history.getUpdateTime()));
        return root.toString();
    }

    private String openDetail(JsonObject payload, CallContext context, BooleanSupplier active) {
        Site site = activeSite(payload, context);
        String publicVodId = limited(Json.safeString(payload, "vodId"), 2048);
        if (TextUtils.isEmpty(publicVodId)) throw new IllegalArgumentException("vodId is required");
        String vodId = context.isV2() ? context.accessSession.resolveVod(publicVodId) : publicVodId;
        if (TextUtils.isEmpty(vodId)) throw new SecurityException("Unknown VOD reference");
        String title = limited(Json.safeString(payload, "title"), 512);
        String pic = limited(Json.safeString(payload, "pic"), 4096);
        String remarks = limited(Json.safeString(payload, "remarks"), 1024);
        String content = limited(Json.safeString(payload, "content"), 20_000);
        WebHomeTarget target = context.target;
        boolean themed = target != null && target.isV2()
                && target.getManifest().getPage(WebThemePage.DETAIL) != null;
        postIfActive(active, () -> {
            // 猫源配置站点的动作项（设置页、弹幕服务）点了本意是开网页，不是看片；
            // 详情页会先建起来闪一下，所以在这里先分流，和原生列表入口一致
            if (com.fongmi.android.tv.api.CatAction.openWebsite(site.getKey(), vodId, pic)) return;
            if (themed) WebThemeDetailActivity.start(activity, target.getManifest().getManifestUrl(),
                    site.getKey(), vodId, title, pic, remarks, content);
            else TmdbDetailActivity.start(activity, site.getKey(), vodId, title, pic, remarks);
        });
        return "{}";
    }

    private String openNativeDetail(JsonObject payload, CallContext context, BooleanSupplier active) {
        Site site = activeSite(payload, context);
        WebThemeRoute route = activeDetailRoute(payload, context);
        Vod vod = context.detailVod;
        String title = vod == null || TextUtils.isEmpty(vod.getName()) ? route.getTitle() : vod.getName();
        String pic = vod == null || TextUtils.isEmpty(vod.getPic()) ? route.getPic() : vod.getPic();
        String remarks = vod == null || TextUtils.isEmpty(vod.getRemarks()) ? route.getRemarks() : vod.getRemarks();
        postIfActive(active, () -> {
            TmdbDetailActivity.start(activity, site.getKey(), route.getVodId(), title, pic, remarks);
            activity.finish();
        });
        return "{}";
    }

    private WebThemeRoute activeDetailRoute(JsonObject payload, CallContext context) {
        if (context.target == null || context.target.getPage() != WebThemePage.DETAIL) {
            throw new SecurityException("Detail page is not active");
        }
        WebThemeRoute route = context.route;
        String requestedRef = limited(Json.safeString(payload, "vodId"), 2048);
        String requested = context.isV2() && !TextUtils.isEmpty(requestedRef)
                ? context.accessSession.resolveVod(requestedRef) : requestedRef;
        if (!TextUtils.isEmpty(requestedRef) && TextUtils.isEmpty(requested)) {
            throw new SecurityException("Unknown VOD reference");
        }
        if (!TextUtils.isEmpty(requested) && !route.getVodId().equals(requested)) {
            throw new SecurityException("Cross-detail access is not allowed");
        }
        return route;
    }

    private static String detailKey(String siteKey, String vodId) {
        return siteKey + AppDatabase.SYMBOL + vodId;
    }

    private static String favoriteJson(boolean favorite) {
        JsonObject object = new JsonObject();
        object.addProperty("favorite", favorite);
        return object.toString();
    }

    private String openPerson(JsonObject payload, CallContext context, BooleanSupplier active) {
        activeDetailRoute(payload, context);
        Site site = activeSite(payload, context);
        TmdbPerson person = context.detailActionSession.resolvePerson(requiredRef(payload, "personRef"));
        if (person == null) throw new IllegalArgumentException("Unknown person reference");
        postIfActive(active, () -> TmdbPersonActivity.start(activity, person, site.getKey()));
        return acceptedJson();
    }

    private String previewImage(JsonObject payload, CallContext context, BooleanSupplier active) {
        activeDetailRoute(payload, context);
        WebThemeDetailActionSession.ImageSelection image = context.detailActionSession
                .resolveImage(requiredRef(payload, "imageRef"));
        if (image == null) throw new IllegalArgumentException("Unknown image reference");
        postIfActive(active, () -> WebThemeImageViewer.show(activity, image.gallery(), image.index()));
        return acceptedJson();
    }

    private String saveImage(JsonObject payload, CallContext context, BooleanSupplier active) {
        activeDetailRoute(payload, context);
        WebThemeDetailActionSession.ImageSelection image = context.detailActionSession
                .resolveImage(requiredRef(payload, "imageRef"));
        if (image == null) throw new IllegalArgumentException("Unknown image reference");
        postIfActive(active, () -> WebThemeImageViewer.save(activity, image.url()));
        return acceptedJson();
    }

    private String openRecommendation(JsonObject payload, CallContext context, BooleanSupplier active) {
        activeDetailRoute(payload, context);
        Site site = activeSite(payload, context);
        WebThemeDetailActionSession.Recommendation recommendation = context.detailActionSession
                .resolveRecommendation(requiredRef(payload, "recommendationRef"));
        if (recommendation == null) throw new IllegalArgumentException("Unknown recommendation reference");
        WebHomeTarget target = context.target;
        String manifestUrl = target.getManifest().getManifestUrl();
        postIfActive(active, () -> TmdbNavigation.open(activity, recommendation.item(), site, (matchedSite, match) -> {
            if (!active.getAsBoolean()) return;
            WebThemeDetailActivity.start(activity, manifestUrl, matchedSite.getKey(), match.getId(), match.getName(),
                    match.getPic(), match.getRemarks(), match.getContent());
        }, null, active));
        return acceptedJson();
    }

    private String recommendationInfo(JsonObject payload, CallContext context, BooleanSupplier active) {
        activeDetailRoute(payload, context);
        String ref = requiredRef(payload, "recommendationRef");
        WebThemeDetailActionSession.Recommendation recommendation = context.detailActionSession
                .resolveRecommendation(ref);
        if (recommendation == null) throw new IllegalArgumentException("Unknown recommendation reference");
        postIfActive(active, () -> AiRecommendationInfoDialog.show(activity, recommendation.item(),
                recommendation.source(), ignored -> {
                    if (!active.getAsBoolean()) return;
                    context.detailActionSession.markNotInterested(ref);
                    controller.dispatchDetailChanged();
                }, active));
        return acceptedJson();
    }

    private String recommendationFeedback(JsonObject payload, CallContext context, BooleanSupplier active) {
        activeDetailRoute(payload, context);
        String action = limited(Json.safeString(payload, "action"), 32);
        if (!"notInterested".equals(action)) throw new IllegalArgumentException("Unsupported feedback action");
        String ref = requiredRef(payload, "recommendationRef");
        WebThemeDetailActionSession.Recommendation recommendation = context.detailActionSession
                .resolveRecommendation(ref);
        if (recommendation == null) throw new IllegalArgumentException("Unknown recommendation reference");
        postIfActive(active, () -> {
            if (context.detailActionSession.markNotInterested(ref) == null) return;
            RecommendationFeedbackStore.add(recommendation.item(), recommendation.source());
            controller.dispatchDetailChanged();
        });
        return acceptedJson();
    }

    private String openExternal(JsonObject payload, CallContext context, BooleanSupplier active) {
        activeDetailRoute(payload, context);
        WebThemeDetailActionSession.ExternalLink link = context.detailActionSession
                .resolveExternal(requiredRef(payload, "linkRef"));
        if (link == null) throw new IllegalArgumentException("Unknown external link reference");
        postIfActive(active, () -> {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link.url())));
            } catch (RuntimeException ignored) {
                Notify.show(R.string.detail_external_open_failed);
            }
        });
        return acceptedJson();
    }

    private String episodeInfo(JsonObject payload, CallContext context, BooleanSupplier active) {
        activeDetailRoute(payload, context);
        Episode episode = context.detailActionSession.resolveEpisode(requiredRef(payload, "episodeRef"));
        if (episode == null) throw new IllegalArgumentException("Unknown episode reference");
        if (!(activity instanceof FragmentActivity host)) {
            throw new IllegalStateException("Episode detail is unavailable");
        }
        postIfActive(active, () -> EpisodeDetailDialog.show(host, episode));
        return acceptedJson();
    }

    private static String requiredRef(JsonObject payload, String key) {
        String ref = limited(Json.safeString(payload, key), 128);
        if (TextUtils.isEmpty(ref)) throw new IllegalArgumentException(key + " is required");
        return ref;
    }

    private static String acceptedJson() {
        return "{\"accepted\":true}";
    }

    private String search(JsonObject payload, BooleanSupplier active) {
        String keyword = limited(Json.safeString(payload, "keyword"), 256);
        if (TextUtils.isEmpty(keyword)) throw new IllegalArgumentException("keyword is required");
        postIfActive(active, () -> SearchActivity.start(activity, keyword, null, "", ""));
        return "{}";
    }

    private Site activeSite(JsonObject payload, CallContext context) {
        Site site = context.site;
        if (site == null || TextUtils.isEmpty(site.getKey())) throw new IllegalStateException("No active VOD source");
        String requested = limited(Json.safeString(payload, "siteKey"), 256);
        if (!TextUtils.isEmpty(requested) && !requested.equals(site.getKey())) {
            throw new SecurityException("Cross-source VOD access is not allowed");
        }
        return site;
    }

    private HashMap<String, String> extend(JsonObject payload) {
        HashMap<String, String> result = new HashMap<>();
        JsonElement element = payload.get("extend");
        if (element == null || !element.isJsonObject()) return result;
        int count = 0;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (count++ >= 32 || entry.getValue() == null || !entry.getValue().isJsonPrimitive()) continue;
            String key = limited(entry.getKey(), 64);
            String value = limited(entry.getValue().getAsString(), 512);
            if (!TextUtils.isEmpty(key)) result.put(key, value);
        }
        return result;
    }

    static int positiveInt(JsonObject payload, String key, int fallback) {
        if (payload == null || !payload.has(key)) return fallback;
        JsonElement element = payload.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        try {
            int value = element.getAsJsonPrimitive().getAsBigDecimal().intValueExact();
            if (value < 1 || value > MAX_PAGE) throw new IllegalArgumentException(key + " is out of range");
            return value;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(key + " must be an integer", e);
        }
    }

    static boolean requiredBoolean(JsonObject payload, String key) {
        JsonElement element = payload == null ? null : payload.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isBoolean()) throw new IllegalArgumentException(key + " must be a boolean");
        return primitive.getAsBoolean();
    }

    static boolean optionalBoolean(JsonObject payload, String key, boolean fallback) {
        return payload == null || !payload.has(key) ? fallback : requiredBoolean(payload, key);
    }

    private static final class CallContext {

        private final HomeWebController.ThemeRuntimeSnapshot runtime;
        private final WebHomeTarget target;
        private final Site site;
        private final WebThemeRoute route;
        private final Vod detailVod;
        private final WebThemeDetailMetadata detailMetadata;
        private final WebThemePlaySession playSession;
        private final WebThemeAccessSession accessSession;
        private final WebThemeDetailActionSession detailActionSession;

        private CallContext(HomeWebController.ThemeRuntimeSnapshot runtime) {
            WebThemePageHost.Snapshot page = runtime.page();
            WebThemeSession.Snapshot session = runtime.session();
            this.runtime = runtime;
            this.target = page.target();
            this.site = page.site();
            this.route = page.route();
            this.detailVod = page.detailVod();
            this.detailMetadata = page.detailMetadata();
            this.playSession = session.playSession();
            this.accessSession = session.accessSession();
            this.detailActionSession = session.detailActionSession();
        }

        private boolean isV2() {
            return target != null && target.isV2();
        }
    }

    private boolean isLandscape() {
        return App.get().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int suggestedColumns() {
        if (Util.isLeanback()) return 6;
        return isLandscape() ? 5 : 3;
    }

    private String openVod(BooleanSupplier active) {
        postIfActive(active, controller::openVod);
        return "{}";
    }

    private String openSite(BooleanSupplier active) {
        postIfActive(active, controller::openSite);
        return "{}";
    }

    private String openSetting(BooleanSupplier active) {
        postIfActive(active, controller::openSetting);
        return "{}";
    }

    private String back(BooleanSupplier active) {
        postIfActive(active, () -> {
            if (!controller.handleBack() && controller.getThemePage() == WebThemePage.DETAIL) activity.finish();
        });
        return "{}";
    }

    private String reload(BooleanSupplier active) {
        postIfActive(active, controller::reload);
        return "{}";
    }

    private static void postIfActive(BooleanSupplier active, Runnable action) {
        App.post(() -> {
            if (active.getAsBoolean()) action.run();
        });
    }

    private static void requireActive(BooleanSupplier active) {
        if (active == null || !active.getAsBoolean()) throw new IllegalStateException("SOURCE_CHANGED");
    }

    private static String limited(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
