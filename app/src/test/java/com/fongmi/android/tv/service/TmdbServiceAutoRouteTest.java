package com.fongmi.android.tv.service;

import com.fongmi.android.tv.utils.TmdbProxy;

import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TmdbServiceAutoRouteTest {

    @After
    public void clearHealth() {
        TmdbProxy.RouteSelector.clearForTest();
    }

    @Test
    public void lowerLatencyRouteComesFirst() {
        TmdbProxy.RouteSelector.success(TmdbProxy.RouteSelector.Kind.API, TmdbProxy.OFFICIAL_API, 300);
        TmdbProxy.RouteSelector.success(TmdbProxy.RouteSelector.Kind.API, TmdbProxy.ITV666, 80);

        assertEquals(List.of(TmdbProxy.ITV666, TmdbProxy.OFFICIAL_API),
                TmdbProxy.RouteSelector.order(TmdbProxy.RouteSelector.Kind.API, TmdbProxy.autoApiCandidates()));
    }

    @Test
    public void failedRouteIsTemporarilyMovedBehindHealthyRoute() {
        TmdbProxy.RouteSelector.failure(TmdbProxy.RouteSelector.Kind.API, TmdbProxy.OFFICIAL_API);

        List<String> routes = TmdbProxy.RouteSelector.order(TmdbProxy.RouteSelector.Kind.API, TmdbProxy.autoApiCandidates());
        assertEquals(TmdbProxy.ITV666, routes.get(0));
        assertFalse(routes.isEmpty());
    }
}
