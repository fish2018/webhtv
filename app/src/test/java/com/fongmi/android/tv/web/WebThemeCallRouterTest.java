package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class WebThemeCallRouterTest {

    @Test
    public void groupsTheStableBridgeSurface() {
        assertEquals(WebThemeCallRouter.Api.HOME, WebThemeCallRouter.groupOf("theme.info"));
        assertEquals(WebThemeCallRouter.Api.HOME, WebThemeCallRouter.groupOf("vod.home"));
        assertEquals(WebThemeCallRouter.Api.LIST, WebThemeCallRouter.groupOf("vod.category"));
        assertEquals(WebThemeCallRouter.Api.DETAIL, WebThemeCallRouter.groupOf("vod.detail"));
        assertEquals(WebThemeCallRouter.Api.DETAIL, WebThemeCallRouter.groupOf("favorite.set"));
        assertEquals(WebThemeCallRouter.Api.DETAIL, WebThemeCallRouter.groupOf("history.item"));
        assertEquals(WebThemeCallRouter.Api.PLAYER, WebThemeCallRouter.groupOf("player.playVod"));
        assertEquals(WebThemeCallRouter.Api.NAVIGATION, WebThemeCallRouter.groupOf("navigation.openDetail"));
        assertEquals(WebThemeCallRouter.Api.NAVIGATION, WebThemeCallRouter.groupOf("app.openSetting"));
        assertEquals(WebThemeCallRouter.Api.UI, WebThemeCallRouter.groupOf("image.preview"));
        assertEquals(WebThemeCallRouter.Api.UI, WebThemeCallRouter.groupOf("ui.getViewport"));
    }

    @Test
    public void rejectsUnknownMethodsBeforeDispatch() {
        assertThrows(SecurityException.class, () -> WebThemeCallRouter.groupOf("unknown.method"));
    }

    @Test
    public void validatesPermissionAndActiveGenerationBeforeDispatch() throws Exception {
        WebThemeCallRouter router = new WebThemeCallRouter();
        WebHomeTarget target = WebHomeTarget.resolve("https://source.example/home", false, "", "");
        AtomicBoolean dispatched = new AtomicBoolean();

        String result = router.invoke("vod.home", new JsonObject(), target, () -> true,
                (api, method, payload, active) -> {
                    dispatched.set(true);
                    assertEquals(WebThemeCallRouter.Api.HOME, api);
                    assertEquals("vod.home", method);
                    assertTrue(active.getAsBoolean());
                    return "ok";
                });

        assertEquals("ok", result);
        assertTrue(dispatched.get());
        assertThrows(IllegalStateException.class,
                () -> router.invoke("vod.home", new JsonObject(), target, () -> false,
                        (api, method, payload, active) -> "unreachable"));
    }

    @Test
    public void rejectsPermissionBeforeDispatchAndGenerationChangesAfterDispatch() {
        WebThemeCallRouter router = new WebThemeCallRouter();
        WebHomeTarget target = WebHomeTarget.resolve("https://source.example/home", false, "", "");
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean dispatched = new AtomicBoolean();

        assertThrows(SecurityException.class,
                () -> router.invoke("favorite.set", null, target, active::get,
                        (api, method, payload, current) -> {
                            dispatched.set(true);
                            return "unreachable";
                        }));
        assertFalse(dispatched.get());

        assertThrows(IllegalStateException.class,
                () -> router.invoke("vod.home", null, target, active::get,
                        (api, method, payload, current) -> {
                            dispatched.set(true);
                            assertTrue(payload.isJsonObject());
                            active.set(false);
                            return "stale";
                        }));
        assertTrue(dispatched.get());
    }
}
