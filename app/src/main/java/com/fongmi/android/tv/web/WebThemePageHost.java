package com.fongmi.android.tv.web;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;

/** Owns the page-specific context published by one WebTheme runtime. */
final class WebThemePageHost {

    private Site site;
    private WebHomeTarget target;
    private WebThemeRoute route = WebThemeRoute.EMPTY;
    private Vod detailVod;
    private WebThemeDetailMetadata detailMetadata = WebThemeDetailMetadata.EMPTY;

    synchronized Site site() {
        return site;
    }

    synchronized WebHomeTarget target() {
        return target;
    }

    synchronized WebThemeRoute route() {
        return route;
    }

    synchronized Vod detailVod() {
        return detailVod;
    }

    synchronized WebThemeDetailMetadata detailMetadata() {
        return detailMetadata;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(site, target, route, detailVod, detailMetadata);
    }

    synchronized void beginDocument(Site site, WebHomeTarget target, WebThemeRoute route) {
        publishContextLocked(site, target, route);
        clearDetailLocked();
    }

    synchronized void updateContext(Site site, WebHomeTarget target, WebThemeRoute route) {
        publishContextLocked(site, target, route);
    }

    synchronized void clearTarget() {
        target = null;
    }

    synchronized void failPage() {
        target = null;
        route = WebThemeRoute.EMPTY;
        clearDetailLocked();
    }

    synchronized void clearDetail() {
        clearDetailLocked();
    }

    synchronized void setDetailVod(Vod detailVod) {
        this.detailVod = detailVod;
    }

    synchronized void setDetailMetadata(WebThemeDetailMetadata detailMetadata) {
        this.detailMetadata = detailMetadata == null ? WebThemeDetailMetadata.EMPTY : detailMetadata;
    }

    private void publishContextLocked(Site site, WebHomeTarget target, WebThemeRoute route) {
        this.site = site;
        this.target = target;
        this.route = route == null ? WebThemeRoute.EMPTY : route;
    }

    private void clearDetailLocked() {
        detailVod = null;
        detailMetadata = WebThemeDetailMetadata.EMPTY;
    }

    record Snapshot(Site site, WebHomeTarget target, WebThemeRoute route, Vod detailVod,
            WebThemeDetailMetadata detailMetadata) {
    }
}
