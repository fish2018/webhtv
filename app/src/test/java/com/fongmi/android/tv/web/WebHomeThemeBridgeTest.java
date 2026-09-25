package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.Test;

public class WebHomeThemeBridgeTest {

    @Test
    public void remotePageMustStayInsideTheSupportedRange() {
        JsonObject payload = new JsonObject();
        assertEquals(1, WebHomeThemeBridge.positiveInt(payload, "page", 1));

        payload.addProperty("page", WebHomeThemeBridge.MAX_PAGE);
        assertEquals(WebHomeThemeBridge.MAX_PAGE, WebHomeThemeBridge.positiveInt(payload, "page", 1));

        assertInvalidPage(new JsonPrimitive(0));
        assertInvalidPage(new JsonPrimitive(WebHomeThemeBridge.MAX_PAGE + 1));
        assertInvalidPage(new JsonPrimitive("2"));
        assertInvalidPage(new JsonPrimitive(2.5));
        assertInvalidPage(new JsonPrimitive(true));
        assertInvalidPage(new JsonObject());
        assertInvalidPage(JsonNull.INSTANCE);
    }

    @Test
    public void favoriteBooleanRejectsCoercedOrMalformedValues() {
        JsonObject payload = new JsonObject();
        payload.addProperty("favorite", true);
        assertTrue(WebHomeThemeBridge.requiredBoolean(payload, "favorite"));

        payload.addProperty("favorite", false);
        assertFalse(WebHomeThemeBridge.requiredBoolean(payload, "favorite"));

        payload.addProperty("favorite", "false");
        assertInvalidFavorite(payload);
        payload.addProperty("favorite", 0);
        assertInvalidFavorite(payload);
        payload.add("favorite", new JsonObject());
        assertInvalidFavorite(payload);
        assertInvalidFavorite(new JsonObject());
    }

    @Test
    public void optionalBooleanUsesFallbackOnlyWhenTheFieldIsAbsent() {
        JsonObject payload = new JsonObject();
        assertTrue(WebHomeThemeBridge.optionalBoolean(payload, "cached", true));
        assertFalse(WebHomeThemeBridge.optionalBoolean(payload, "cached", false));

        payload.addProperty("cached", true);
        assertTrue(WebHomeThemeBridge.optionalBoolean(payload, "cached", false));
        payload.addProperty("cached", false);
        assertFalse(WebHomeThemeBridge.optionalBoolean(payload, "cached", true));

        payload.addProperty("cached", "false");
        assertInvalidOptionalBoolean(payload);
        payload.addProperty("cached", 0);
        assertInvalidOptionalBoolean(payload);
        payload.add("cached", new JsonObject());
        assertInvalidOptionalBoolean(payload);
        payload.add("cached", JsonNull.INSTANCE);
        assertInvalidOptionalBoolean(payload);
    }

    private static void assertInvalidPage(JsonElement page) {
        JsonObject payload = new JsonObject();
        payload.add("page", page);
        try {
            WebHomeThemeBridge.positiveInt(payload, "page", 1);
            fail("Expected an invalid page error for " + page);
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void assertInvalidFavorite(JsonObject payload) {
        try {
            WebHomeThemeBridge.requiredBoolean(payload, "favorite");
            fail("Expected favorite to require a JSON boolean");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void assertInvalidOptionalBoolean(JsonObject payload) {
        try {
            WebHomeThemeBridge.optionalBoolean(payload, "cached", false);
            fail("Expected cached to require a JSON boolean");
        } catch (IllegalArgumentException expected) {
        }
    }
}
