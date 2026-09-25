package com.fongmi.android.tv.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import com.google.gson.JsonObject;

import org.junit.Test;

public class GameContentHandlerTest {

    @Test
    public void browserAction_acceptsTopLevelBrowserCard() {
        JsonObject action = GameContentHandler.browserAction(
                "{\"type\":\"browser\",\"url\":\"https://games.example/a\",\"title\":\"小游戏\"}");

        assertNotNull(action);
        assertEquals("https://games.example/a", GameContentHandler.urlOf(action));
        assertEquals("小游戏", GameContentHandler.titleOf(action, "fallback"));
    }

    @Test
    public void browserAction_acceptsNestedOpenUrlCard() {
        JsonObject action = GameContentHandler.browserAction(
                "{\"action\":{\"actionId\":\"OPEN_URL\",\"url\":\"http://games.example\"}}");

        assertNotNull(action);
        assertEquals("http://games.example", GameContentHandler.urlOf(action));
    }

    @Test
    public void browserAction_rejectsInputAndNonWebSchemes() {
        assertNull(GameContentHandler.browserAction("{\"type\":\"input\",\"tip\":\"URL\"}"));
        assertNull(GameContentHandler.browserAction("{\"type\":\"browser\",\"url\":\"javascript:alert(1)\"}"));
        assertFalse(new GameContentHandler().canHandleUrl("game://javascript:alert(1)"));
    }

    @Test
    public void browserUrl_acceptsOnlyHttpSchemes() {
        assertTrue(GameContentHandler.isWebUrl("https://example.com"));
        assertTrue(GameContentHandler.isWebUrl("HTTP://example.com"));
        assertFalse(GameContentHandler.isWebUrl("file:///tmp/game.html"));
        assertFalse(GameContentHandler.isWebUrl("javascript:alert(1)"));
    }

    @Test
    public void browserActivity_hidesTheReaderLoadingOverlayAfterNavigation() throws Exception {
        java.io.File root = new java.io.File(System.getProperty("user.dir"));
        while (root != null && !new java.io.File(root,
                "app/src/main/java/com/fongmi/android/tv/ui/web/GameWebActivity.java").isFile()) {
            root = root.getParentFile();
        }
        assertNotNull(root);
        String source = new String(java.nio.file.Files.readAllBytes(new java.io.File(root,
                "app/src/main/java/com/fongmi/android/tv/ui/web/GameWebActivity.java").toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(source.contains("loading = findViewById(R.id.loading);"));
        assertTrue(source.contains("public void onPageFinished(WebView view, String url)"));
        assertTrue(source.contains("hideLoading();"));
        assertTrue(source.contains("return !isWebUrl(target);"));
    }
}
