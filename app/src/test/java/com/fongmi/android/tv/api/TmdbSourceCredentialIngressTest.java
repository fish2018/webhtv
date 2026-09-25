package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Result;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbSourceCredentialIngressTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final Gson GSON = new Gson();

    @Test
    public void extractsValidRootKeyAndRemovesItFromSanitizedJson() {
        String raw = "{\"tmdb_api_key\":\"" + KEY + "\",\"list\":[{\"vod_id\":\"1\",\"vod_name\":\"Example\"}]}";

        TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(raw);

        assertEquals(KEY, ingress.getCandidateKey());
        assertFalse(ingress.getSanitizedJson().contains(KEY));
        assertFalse(ingress.getSanitizedJson().contains(TmdbSourceCredentialIngress.ROOT_FIELD));
        assertEquals("1", GSON.fromJson(ingress.getSanitizedJson(), Result.class).getVod().getId());
    }

    @Test
    public void missingRootKeyLeavesLegacyJsonUntouched() {
        String raw = "{\"list\":[{\"vod_id\":\"1\",\"vod_name\":\"Legacy\"}]}";

        TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(raw);

        assertEquals("", ingress.getCandidateKey());
        assertEquals(raw, ingress.getSanitizedJson());
    }

    @Test
    public void extractsSubscriptionConfigRootKeyAndPreservesSites() {
        String raw = "{\"tmdb_api_key\":\"" + KEY + "\",\"sites\":[{\"key\":\"demo\",\"api\":\"https://source.example/api\"}]}";

        TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(raw);
        JsonObject sanitized = GSON.fromJson(ingress.getSanitizedJson(), JsonObject.class);

        assertEquals(KEY, ingress.getCandidateKey());
        assertFalse(sanitized.has(TmdbSourceCredentialIngress.ROOT_FIELD));
        assertEquals("demo", sanitized.getAsJsonArray("sites").get(0).getAsJsonObject().get("key").getAsString());
    }

    @Test
    public void invalidRootKeysAreRemovedButNeverAccepted() {
        String[] values = {
                "\"short\"",
                "\"has space 1234567890\"",
                "\"line\\nbreak_1234567890\"",
                "12345678901234567890",
                "null",
                "{\"nested\":true}"
        };

        for (String value : values) {
            String raw = "{\"tmdb_api_key\":" + value + ",\"list\":[{\"vod_id\":\"1\"}]}";
            TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(raw);

            assertEquals("", ingress.getCandidateKey());
            assertFalse(ingress.getSanitizedJson().contains(TmdbSourceCredentialIngress.ROOT_FIELD));
            assertEquals("1", GSON.fromJson(ingress.getSanitizedJson(), Result.class).getVod().getId());
        }
    }

    @Test
    public void duplicateRootKeyIsRemovedAndRejectedAsAmbiguous() {
        String raw = "{\"tmdb_api_key\":\"" + KEY + "\",\"tmdb_api_key\":\"fedcba9876543210fedcba9876543210\",\"list\":[{\"vod_id\":\"1\"}]}";

        TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(raw);

        assertEquals("", ingress.getCandidateKey());
        assertFalse(ingress.getSanitizedJson().contains(KEY));
        assertFalse(ingress.getSanitizedJson().contains("fedcba9876543210"));
    }

    @Test
    public void oversizedKeyIsRemovedButOrdinaryDetailStillParses() {
        String oversized = "a".repeat(257);
        String raw = "{\"tmdb_api_key\":\"" + oversized + "\",\"list\":[{\"vod_id\":\"1\",\"vod_name\":\"Still valid\"}]}";

        TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(raw);

        assertEquals("", ingress.getCandidateKey());
        assertEquals("Still valid", GSON.fromJson(ingress.getSanitizedJson(), Result.class).getVod().getName());
    }

    @Test
    public void malformedJsonContainingRootKeyIsNeverEchoedToCallers() {
        String raw = "{\"tmdb_api_key\":\"" + KEY + "\",\"list\":[";

        TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(raw);

        assertEquals("", ingress.getCandidateKey());
        assertEquals("{}", ingress.getSanitizedJson());
        assertFalse(ingress.getSanitizedJson().contains(KEY));
    }

    @Test
    public void sanitizedResultAndStringNeverContainKey() {
        Result result = GSON.fromJson(TmdbSourceCredentialIngress.extractRootAndStrip(
                "{\"tmdb_api_key\":\"" + KEY + "\",\"list\":[{\"vod_id\":\"1\",\"vod_name\":\"Example\"}]}").getSanitizedJson(), Result.class);

        assertFalse(GSON.toJson(result).contains(KEY));
        assertFalse(GSON.toJson(result.getVod()).contains(KEY));
        assertTrue(TmdbSourceCredentialIngress.normalizeApiKey(KEY).equals(KEY));
    }
}
