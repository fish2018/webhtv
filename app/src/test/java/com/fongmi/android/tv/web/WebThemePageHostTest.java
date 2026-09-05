package com.fongmi.android.tv.web;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.List;

public class WebThemePageHostTest {

    @Test
    public void startsWithAnEmptyPageSnapshot() {
        WebThemePageHost host = new WebThemePageHost();

        WebThemePageHost.Snapshot snapshot = host.snapshot();

        assertNull(snapshot.site());
        assertNull(snapshot.target());
        assertSame(WebThemeRoute.EMPTY, snapshot.route());
        assertNull(snapshot.detailVod());
        assertSame(WebThemeDetailMetadata.EMPTY, snapshot.detailMetadata());
    }

    @Test
    public void contextSwitchPreservesDetailUntilANewDocumentBegins() {
        WebThemePageHost host = new WebThemePageHost();
        Site firstSite = Site.get("source-a", "Source A");
        Site secondSite = Site.get("source-b", "Source B");
        WebHomeTarget firstTarget = WebHomeTarget.resolve("https://source-a.example/home", true,
                WebHomeTarget.ECLIPSE_URL);
        WebHomeTarget secondTarget = WebHomeTarget.resolve("https://source-b.example/home", true,
                WebHomeTarget.ECLIPSE_URL);
        WebThemeRoute firstRoute = WebThemeRoute.detail("vod-a", "A", "", "");
        WebThemeRoute secondRoute = WebThemeRoute.detail("vod-b", "B", "", "");
        Vod detailVod = new Vod();
        WebThemeDetailMetadata detailMetadata = WebThemeDetailMetadata.fromTmdb(null, new JsonObject(),
                List.of(), List.of(), List.of(), List.of());

        host.beginDocument(firstSite, firstTarget, firstRoute);
        host.setDetailVod(detailVod);
        host.setDetailMetadata(detailMetadata);
        host.updateContext(secondSite, secondTarget, secondRoute);

        WebThemePageHost.Snapshot switched = host.snapshot();
        assertSame(secondSite, switched.site());
        assertSame(secondTarget, switched.target());
        assertSame(secondRoute, switched.route());
        assertSame(detailVod, switched.detailVod());
        assertSame(detailMetadata, switched.detailMetadata());

        host.beginDocument(secondSite, secondTarget, secondRoute);

        WebThemePageHost.Snapshot reloaded = host.snapshot();
        assertNull(reloaded.detailVod());
        assertSame(WebThemeDetailMetadata.EMPTY, reloaded.detailMetadata());
    }

    @Test
    public void pageFailureClearsPageStateButKeepsTheSelectedSource() {
        WebThemePageHost host = populatedHost();
        Site site = host.snapshot().site();

        host.failPage();

        WebThemePageHost.Snapshot failed = host.snapshot();
        assertSame(site, failed.site());
        assertNull(failed.target());
        assertSame(WebThemeRoute.EMPTY, failed.route());
        assertNull(failed.detailVod());
        assertSame(WebThemeDetailMetadata.EMPTY, failed.detailMetadata());
    }

    @Test
    public void bridgePublicationFailureRevokesOnlyTheTarget() {
        WebThemePageHost host = populatedHost();
        WebThemePageHost.Snapshot before = host.snapshot();

        host.clearTarget();

        WebThemePageHost.Snapshot after = host.snapshot();
        assertSame(before.site(), after.site());
        assertNull(after.target());
        assertSame(before.route(), after.route());
        assertSame(before.detailVod(), after.detailVod());
        assertSame(before.detailMetadata(), after.detailMetadata());
    }

    private static WebThemePageHost populatedHost() {
        WebThemePageHost host = new WebThemePageHost();
        Site site = Site.get("source", "Source");
        WebHomeTarget target = WebHomeTarget.resolve("https://source.example/home", true,
                WebHomeTarget.ECLIPSE_URL);
        WebThemeRoute route = WebThemeRoute.detail("vod", "Title", "", "");
        host.beginDocument(site, target, route);
        host.setDetailVod(new Vod());
        host.setDetailMetadata(WebThemeDetailMetadata.fromTmdb(null, new JsonObject(), List.of(), List.of(),
                List.of(), List.of()));
        return host;
    }
}
