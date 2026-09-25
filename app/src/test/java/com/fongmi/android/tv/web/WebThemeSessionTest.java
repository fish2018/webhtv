package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebThemeSessionTest {

    @Test
    public void snapshotKeepsGenerationAndReferenceStoresTogether() {
        WebThemeSession session = new WebThemeSession();
        WebThemeSession.Snapshot snapshot = session.snapshot();

        assertEquals(session.generation(), snapshot.generation());
        assertSame(session.getPlaySession(), snapshot.playSession());
        assertSame(session.getAccessSession(), snapshot.accessSession());
        assertSame(session.getDetailActionSession(), snapshot.detailActionSession());
    }

    @Test
    public void invalidationAdvancesGenerationAndReplacesOpaqueStores() {
        WebThemeSession session = new WebThemeSession();
        WebThemeSession.Snapshot before = session.snapshot();
        String playRef = before.playSession().issue("source", "vod", "line", "episode", "https://media.example/1");
        String vodRef = before.accessSession().issueRoute("vod");

        int next = session.invalidate();
        WebThemeSession.Snapshot after = session.snapshot();

        assertEquals(before.generation() + 1, next);
        assertFalse(session.isCurrent(before.generation()));
        assertTrue(session.isCurrent(next));
        assertNotSame(before.playSession(), after.playSession());
        assertNotSame(before.accessSession(), after.accessSession());
        assertNotSame(before.detailActionSession(), after.detailActionSession());
        assertNull(after.playSession().resolve(playRef, "source", "vod"));
        assertNull(after.accessSession().resolveVod(vodRef));
    }

    @Test
    public void cancellationAdvancesGenerationWithoutReplacingStores() {
        WebThemeSession session = new WebThemeSession();
        WebThemePlaySession playSession = session.getPlaySession();
        int previous = session.generation();

        int next = session.cancelPending();

        assertEquals(previous + 1, next);
        assertFalse(session.isCurrent(previous));
        assertTrue(session.isCurrent(next));
        assertSame(playSession, session.getPlaySession());
    }
}
