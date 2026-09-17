package com.fongmi.android.tv.web;

import com.google.gson.JsonObject;

import java.util.function.BooleanSupplier;

/** Routes the stable theme bridge surface into page-scoped API groups. */
final class WebThemeCallRouter {

    enum Api {
        HOME,
        DETAIL,
        LIST,
        NAVIGATION,
        PLAYER,
        UI
    }

    @FunctionalInterface
    interface Dispatcher {
        String dispatch(Api api, String method, JsonObject payload, BooleanSupplier active) throws Exception;
    }

    static Api groupOf(String method) {
        if (method == null) throw new SecurityException("PERMISSION_DENIED");
        return switch (method) {
            case "theme.info", "vod.home" -> Api.HOME;
            case "vod.category" -> Api.LIST;
            case "vod.detail", "favorite.status", "favorite.set", "history.item",
                    "person.open", "recommendation.open", "recommendation.info",
                    "recommendation.feedback", "episode.info" -> Api.DETAIL;
            case "player.playVod" -> Api.PLAYER;
            case "navigation.openDetail", "navigation.openNativeDetail", "external.open",
                    "app.search", "app.openVod", "app.openSite", "app.openSetting",
                    "navigation.back", "navigation.reload" -> Api.NAVIGATION;
            case "image.preview", "image.save", "ui.getViewport" -> Api.UI;
            default -> throw new SecurityException("PERMISSION_DENIED");
        };
    }

    String invoke(String method, JsonObject payload, WebHomeTarget target, BooleanSupplier active,
            Dispatcher dispatcher) throws Exception {
        requireActive(active);
        Api api = groupOf(method);
        if (!allows(target, method)) throw new SecurityException("PERMISSION_DENIED");
        JsonObject safe = payload == null ? new JsonObject() : payload;
        String result = dispatcher.dispatch(api, method, safe, active);
        requireActive(active);
        return result;
    }

    private static boolean allows(WebHomeTarget target, String method) {
        return target != null && target.isV2()
                ? WebHomeThemePolicy.allowsMethod(target.getPage(), target.getPermissions(), method)
                : target != null && !target.isManifest() && WebHomeThemePolicy.allowsMethod(method);
    }

    private static void requireActive(BooleanSupplier active) {
        if (active == null || !active.getAsBoolean()) throw new IllegalStateException("SOURCE_CHANGED");
    }
}
