package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class WebThemePlaySessionTest {

    @Test
    public void playRef_isOpaqueSourceLockedAndInvalidatedOnReset() {
        WebThemePlaySession session = new WebThemePlaySession();
        String rawUrl = "https://media.example/secret.m3u8";

        String ref = session.issue("source-a", "vod-1", "线路一", "第 1 集", rawUrl);

        assertFalse(ref.contains(rawUrl));
        WebThemePlaySession.Selection selection = session.resolve(ref, "source-a", "vod-1");
        assertEquals("线路一", selection.getFlag());
        assertEquals("第 1 集", selection.getEpisodeName());
        assertEquals(rawUrl, selection.getEpisodeUrl());
        assertNull(session.resolve(ref, "source-b", "vod-1"));
        assertNull(session.resolve(ref, "source-a", "vod-2"));

        session.reset();
        assertNull(session.resolve(ref, "source-a", "vod-1"));
    }

    @Test
    public void playRef_sessionHasABoundedNumberOfEntries() {
        WebThemePlaySession session = new WebThemePlaySession();
        for (int i = 0; i < WebThemePlaySession.MAX_REFERENCES; i++) {
            session.issue("source", "vod", "line", "episode-" + i, "url-" + i);
        }

        assertThrows(IllegalStateException.class,
                () -> session.issue("source", "vod", "line", "overflow", "url"));
    }

    @Test
    public void playRef_rejectsIntentFieldsThatExceedBinderSafeLimits() {
        WebThemePlaySession session = new WebThemePlaySession();

        assertThrows(IllegalArgumentException.class, () -> session.issue("source", "vod", "line", "episode",
                "x".repeat(WebThemePlaySession.MAX_EPISODE_URL_LENGTH + 1)));
    }

    @Test
    public void playRef_longContextFieldsRemainResolvableAndDeduplicated() {
        WebThemePlaySession session = new WebThemePlaySession();
        String sourceKey = "s".repeat(300);
        String vodId = "v".repeat(3_000);
        String flag = "f".repeat(700);
        String episodeName = "n".repeat(700);
        session.begin(sourceKey, vodId);

        String first = session.issue(sourceKey, vodId, flag, episodeName, "url");
        String second = session.issue(sourceKey, vodId, flag, episodeName, "url");
        WebThemePlaySession.Selection selection = session.resolve(first, sourceKey, vodId);

        assertEquals(first, second);
        assertNotNull(selection);
        assertEquals(512, selection.getFlag().length());
        assertEquals(512, selection.getEpisodeName().length());
    }
}
