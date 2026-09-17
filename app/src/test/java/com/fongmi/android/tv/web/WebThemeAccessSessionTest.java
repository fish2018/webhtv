package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Map;

public class WebThemeAccessSessionTest {

    @Test
    public void homeIdentifiersAndFiltersAreOpaqueAndResolvableOnlyInsideTheSession() {
        WebThemeAccessSession session = new WebThemeAccessSession();
        JsonObject root = new JsonObject();
        JsonArray classes = new JsonArray();
        JsonObject type = new JsonObject();
        type.addProperty("typeId", "https://provider.example/category?token=secret");
        classes.add(type);
        root.add("classes", classes);
        JsonObject filters = new JsonObject();
        JsonArray group = new JsonArray();
        JsonObject filter = new JsonObject();
        filter.addProperty("key", "year");
        JsonArray values = new JsonArray();
        JsonObject value = new JsonObject();
        value.addProperty("value", "2026-secret");
        values.add(value);
        filter.add("values", values);
        group.add(filter);
        filters.add("https://provider.example/category?token=secret", group);
        root.add("filters", filters);
        JsonArray items = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("vodId", "https://provider.example/detail?token=secret");
        item.addProperty("kind", "action");
        item.addProperty("action", "https://provider.example/action?token=secret");
        items.add(item);
        root.add("items", items);

        session.protectHome(root);

        String typeRef = classes.get(0).getAsJsonObject().get("typeId").getAsString();
        String vodRef = items.get(0).getAsJsonObject().get("vodId").getAsString();
        JsonObject protectedFilter = root.getAsJsonObject("filters").getAsJsonArray(typeRef)
                .get(0).getAsJsonObject();
        String keyRef = protectedFilter.get("key").getAsString();
        String valueRef = protectedFilter.getAsJsonArray("values").get(0).getAsJsonObject()
                .get("value").getAsString();

        assertTrue(typeRef.startsWith("type_"));
        assertTrue(vodRef.startsWith("vod_"));
        assertFalse(item.has("action"));
        assertFalse(root.toString().contains("token=secret"));
        assertEquals("https://provider.example/category?token=secret", session.resolveType(typeRef));
        assertEquals("https://provider.example/detail?token=secret", session.resolveVod(vodRef));
        assertEquals(Map.of("year", "2026-secret"), session.resolveExtend(Map.of(keyRef, valueRef)));
        assertThrows(SecurityException.class, () -> session.resolveExtend(Map.of(keyRef, "value_unknown")));
    }

    @Test
    public void detailRouteAndPayloadReuseTheSameOpaqueVodReference() {
        WebThemeAccessSession session = new WebThemeAccessSession();
        String routeRef = session.issueRoute("raw-vod-id");
        JsonObject detail = new JsonObject();
        JsonObject item = new JsonObject();
        item.addProperty("vodId", "raw-vod-id");
        detail.add("item", item);

        session.protectDetail(detail);

        assertEquals(routeRef, item.get("vodId").getAsString());
        assertEquals("raw-vod-id", session.resolveVod(routeRef));
        assertNull(session.resolveVod("vod_unknown"));
        assertNull(session.resolveType("type_unknown"));
    }

    @Test
    public void categoryKeepsThePublicTypeReferenceAndProtectsReturnedVodIds() {
        WebThemeAccessSession session = new WebThemeAccessSession();
        JsonObject home = new JsonObject();
        JsonArray classes = new JsonArray();
        JsonObject type = new JsonObject();
        type.addProperty("typeId", "raw-type");
        classes.add(type);
        home.add("classes", classes);
        session.protectHome(home);
        String typeRef = type.get("typeId").getAsString();

        JsonObject category = new JsonObject();
        JsonObject query = new JsonObject();
        query.addProperty("typeId", "raw-type");
        category.add("query", query);
        JsonArray items = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("vodId", "raw-vod");
        items.add(item);
        category.add("items", items);

        session.protectCategory(category, typeRef);

        assertEquals(typeRef, query.get("typeId").getAsString());
        assertTrue(item.get("vodId").getAsString().startsWith("vod_"));
        assertFalse(category.toString().contains("raw-vod"));
        assertEquals("raw-vod", session.resolveVod(item.get("vodId").getAsString()));
    }
}
