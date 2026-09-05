package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Test;

public class WebThemeSchemaTest {

    @Test
    public void schemaVersionsAndPermissionsStayAlignedWithTheRuntimeRegistry() throws Exception {
        JsonObject schema = schema();
        JsonObject properties = schema.getAsJsonObject("properties");

        assertEquals(WebThemeManifest.SCHEMA_VERSION,
                properties.getAsJsonObject("schemaVersion").get("const").getAsInt());
        assertEquals(WebThemeManifest.HOST_API_VERSION,
                properties.getAsJsonObject("minHostApi").get("maximum").getAsInt());
        assertEquals(WebThemeManifest.MAX_MANIFEST_BYTES,
                schema.get("x-webhtv-maxBytes").getAsInt());

        JsonObject permissionPages = properties.getAsJsonObject("permissions")
                .getAsJsonObject("properties");
        assertEquals(WebThemeCapabilityRegistry.supportedPermissions(WebThemePage.HOME),
                strings(permissionPages.getAsJsonObject("home").getAsJsonObject("items").getAsJsonArray("enum")));
        assertEquals(WebThemeCapabilityRegistry.supportedPermissions(WebThemePage.DETAIL),
                strings(permissionPages.getAsJsonObject("detail").getAsJsonObject("items").getAsJsonArray("enum")));
    }

    @Test
    public void schemaMarksUnimplementedPresentationFieldsAsReserved() throws Exception {
        JsonObject properties = schema().getAsJsonObject("properties");

        assertEquals("reserved", properties.getAsJsonObject("player").get("x-webhtv-status").getAsString());
        assertEquals("reserved", properties.getAsJsonObject("tokens").get("x-webhtv-status").getAsString());
    }

    private static JsonObject schema() throws Exception {
        Path path = repoPath("webhome-devkit/schemas/webtheme-v2.schema.json");
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> strings(JsonArray array) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement element : array) values.add(element.getAsString());
        return values;
    }

    private static Path repoPath(String relative) {
        Path direct = Path.of(relative);
        if (Files.exists(direct)) return direct;
        Path parent = Path.of("..").resolve(relative);
        if (Files.exists(parent)) return parent;
        throw new IllegalStateException("Missing repository file: " + relative);
    }
}
