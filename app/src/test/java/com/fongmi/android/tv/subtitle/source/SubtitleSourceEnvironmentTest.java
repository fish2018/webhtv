package com.fongmi.android.tv.subtitle.source;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

public class SubtitleSourceEnvironmentTest {

    @Test
    public void resolvesWhitelistedPlaceholderWithoutMutatingManifestParams() {
        JsonObject params = object("{\"token\":\"${ASSRT_TOKEN}\",\"literal\":\"keep\"}");
        JsonObject environment = object("{\"ASSRT_TOKEN\":\"secret\"}");

        JsonObject resolved = SubtitleSourceEnvironment.resolve(params, environment);

        assertEquals("secret", resolved.get("token").getAsString());
        assertEquals("keep", resolved.get("literal").getAsString());
        assertEquals("${ASSRT_TOKEN}", params.get("token").getAsString());
    }

    @Test
    public void classInitializationAndPlaceholderParsingDoNotDependOnPlatformRegex() {
        JsonObject params = object("{\"token\":\"${ASSRT_TOKEN}\"}");
        JsonObject environment = object("{\"ASSRT_TOKEN\":\"secret\"}");

        assertEquals("secret", SubtitleSourceEnvironment.resolve(params, environment).get("token").getAsString());
    }

    @Test
    public void replacesMissingPlaceholderWithEmptyStringAndIgnoresInvalidNames() {
        JsonObject params = object("{\"missing\":\"${MISSING_TOKEN}\",\"invalid\":\"${lowercase}\"}");

        JsonObject resolved = SubtitleSourceEnvironment.resolve(params, new JsonObject());

        assertEquals("", resolved.get("missing").getAsString());
        assertEquals("${lowercase}", resolved.get("invalid").getAsString());
    }

    @Test
    public void resolvingAgainFromTemplateUsesLatestEnvironment() {
        JsonObject template = object("{\"token\":\"${ASSRT_TOKEN}\"}");

        assertEquals("first", SubtitleSourceEnvironment.resolve(template, object("{\"ASSRT_TOKEN\":\"first\"}")).get("token").getAsString());
        assertEquals("second", SubtitleSourceEnvironment.resolve(template, object("{\"ASSRT_TOKEN\":\"second\"}")).get("token").getAsString());
        assertEquals("${ASSRT_TOKEN}", template.get("token").getAsString());
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
