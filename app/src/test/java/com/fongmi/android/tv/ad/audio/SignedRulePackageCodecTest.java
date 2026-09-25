package com.fongmi.android.tv.ad.audio;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SignedRulePackageCodecTest {

    private static final String PAYLOAD_DIGEST =
            "94db293f86613c918ea2135ff6cfde34fb6219dafd721ac194d7f35c1074a3d5";
    private static final String SIGNATURE =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String SIGNING_INPUT_HEX =
            "7765626874762e61642d617564696f2e7061636b6167652f763100"
                    + "00116f6666696369616c2e61642d617564696f"
                    + "000000000000002a"
                    + "000001a00b4d2400"
                    + "000001a0a5cbec00"
                    + "000972656c656173652d31"
                    + "000745443235353139"
                    + PAYLOAD_DIGEST;

    @Test
    public void validEnvelopeParsesAndProducesExactSigningInput() throws Exception {
        SignedRulePackageCodec.Parsed parsed = SignedRulePackageCodec.parse(bytes(validEnvelope()));

        assertEquals(1, parsed.packageSchemaVersion());
        assertEquals("official.ad-audio", parsed.packageId());
        assertEquals(42L, parsed.revision());
        assertEquals(1_786_896_000_000L, parsed.createdAtEpochMs());
        assertEquals(1_789_488_000_000L, parsed.expiresAtEpochMs());
        assertEquals("release-1", parsed.keyId());
        assertEquals("ED25519", parsed.algorithm());
        assertEquals(canonicalPayload(), parsed.canonicalPayload());
        assertArrayEquals(hex(PAYLOAD_DIGEST), parsed.payloadDigest());
        assertArrayEquals(hex(SIGNING_INPUT_HEX), parsed.signingInput());
        assertEquals(64, parsed.signature().length);
        assertEquals(0, parsed.ruleSet().rules().size());

        parsed.payloadDigest()[0] ^= 1;
        parsed.signature()[0] ^= 1;
        parsed.signingInput()[0] ^= 1;
        assertArrayEquals(hex(PAYLOAD_DIGEST), parsed.payloadDigest());
        assertEquals(0, parsed.signature()[0]);
        assertArrayEquals(hex(SIGNING_INPUT_HEX), parsed.signingInput());
    }

    @Test
    public void unknownFieldsAreRejectedAtEveryDefinedObject() {
        assertMalformed(validEnvelope().replace("\"packageId\"", "\"unknown\":0,\"packageId\""));
        assertMalformed(validEnvelope().replace("\"keyId\"", "\"unknown\":0,\"keyId\""));
        assertCode(SignedRulePackageException.Code.PAYLOAD_INVALID,
                () -> SignedRulePackageCodec.parse(bytes(validEnvelope()
                        .replace("\"rules\":[]", "\"unknown\":0,\"rules\":[]"))));
    }

    @Test
    public void duplicateFieldsAreRejectedAtEveryNestingLevel() {
        assertMalformed(validEnvelope().replace("\"revision\":42,",
                "\"revision\":42,\"revision\":43,"));
        assertMalformed(validEnvelope().replace("\"keyId\":\"release-1\",",
                "\"keyId\":\"release-1\",\"keyId\":\"release-2\","));
        assertMalformed(validEnvelope().replace("\"schemaVersion\":2",
                "\"schemaVersion\":2,\"schemaVersion\":2"));
        assertMalformed(validEnvelope().replace("\"id\":\"spectral-sequence-v2\"}",
                "\"id\":\"spectral-sequence-v2\",\"id\":\"spectral-sequence-v2\"}"));
        assertMalformed(validEnvelope().replace("\"rules\":[]", "\"rules\":[{"
                + "\"id\":\"ad-1\",\"id\":\"ad-2\",\"durationMs\":15000,"
                + "\"anchorOffsetMs\":0,\"anchorDurationMs\":3000,"
                + "\"fingerprint\":[\"00000000\",\"00000001\",\"00000002\",\"00000003\"]}]"));
    }

    @Test
    public void trailingJsonIsRejected() {
        assertMalformed(validEnvelope() + "{}");
    }

    @Test
    public void decimalRevisionIsRejected() {
        assertMalformed(validEnvelope().replace("\"revision\":42", "\"revision\":42.0"));
    }

    @Test
    public void illegalPackageAndKeyIdsAreRejected() {
        assertMalformed(validEnvelope().replace("official.ad-audio", "official/ad-audio"));
        assertMalformed(validEnvelope().replace("release-1", "release key"));
    }

    @Test
    public void uppercaseDigestIsRejected() {
        assertMalformed(validEnvelope().replace(PAYLOAD_DIGEST,
                PAYLOAD_DIGEST.toUpperCase(Locale.ROOT)));
    }

    @Test
    public void paddedBase64UrlIsRejected() {
        assertMalformed(validEnvelope().replace(SIGNATURE + "\"", SIGNATURE + "=\""));
    }

    @Test
    public void wrongEd25519SignatureLengthIsRejected() {
        assertMalformed(validEnvelope().replace(SIGNATURE, "AA"));
    }

    @Test
    public void deeplyNestedJsonIsRejectedBeforeTreeConstruction() {
        StringBuilder nested = new StringBuilder("\"junk\":");
        for (int i = 0; i < 80; i++) nested.append('[');
        nested.append('0');
        for (int i = 0; i < 80; i++) nested.append(']');
        nested.append(',');
        assertMalformed(validEnvelope().replaceFirst("\\{", "{" + nested));
    }

    @Test
    public void tokenBudgetIsEnforcedBeforeTreeConstruction() {
        StringBuilder values = new StringBuilder("\"junk\":[");
        for (int i = 0; i <= SignedRulePackageCodec.MAX_JSON_TOKENS; i++) {
            if (i > 0) values.append(',');
            values.append('0');
        }
        values.append("],");
        assertMalformed(validEnvelope().replaceFirst("\\{", "{" + values));
    }

    @Test
    public void objectMemberBudgetIsEnforcedBeforeTreeConstruction() {
        StringBuilder members = new StringBuilder("\"junk\":{");
        for (int i = 0; i < 65; i++) {
            if (i > 0) members.append(',');
            members.append('\"').append('f').append(i).append("\":0");
        }
        members.append("},");
        assertMalformed(validEnvelope().replaceFirst("\\{", "{" + members));
    }

    @Test
    public void invalidUtf8IsRejected() {
        assertCode(SignedRulePackageException.Code.PACKAGE_MALFORMED,
                () -> SignedRulePackageCodec.parse(new byte[]{'{', (byte) 0xc3, '}'}));
    }

    @Test
    public void rawPackageSizeIsRejectedBeforeDecoding() {
        assertCode(SignedRulePackageException.Code.PACKAGE_TOO_LARGE,
                () -> SignedRulePackageCodec.parse(
                        new byte[SignedRulePackageCodec.MAX_PACKAGE_BYTES + 1]));
    }

    private static void assertMalformed(String json) {
        assertCode(SignedRulePackageException.Code.PACKAGE_MALFORMED,
                () -> SignedRulePackageCodec.parse(bytes(json)));
    }

    private static void assertCode(SignedRulePackageException.Code expected, ThrowingRunnable action) {
        try {
            action.run();
            fail("Expected " + expected);
        } catch (SignedRulePackageException e) {
            assertEquals(expected, e.code());
            assertEquals(expected.name(), e.getMessage());
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception", e);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String canonicalPayload() {
        return "{\"schemaVersion\":2,\"algorithm\":{"
                + "\"id\":\"spectral-sequence-v2\",\"sampleRate\":16000,"
                + "\"windowMs\":512,\"hopMs\":256,\"bandCount\":16},\"rules\":[]}";
    }

    private static String nonCanonicalPayload() {
        return "{\"rules\":[],\"algorithm\":{"
                + "\"bandCount\":16,\"hopMs\":256,\"windowMs\":512,"
                + "\"sampleRate\":16000,\"id\":\"spectral-sequence-v2\"},"
                + "\"schemaVersion\":2}";
    }

    private static String validEnvelope() {
        return "{"
                + "\"packageSchemaVersion\":1,"
                + "\"packageId\":\"official.ad-audio\","
                + "\"revision\":42,"
                + "\"createdAtEpochMs\":1786896000000,"
                + "\"expiresAtEpochMs\":1789488000000,"
                + "\"payloadSha256\":\"" + PAYLOAD_DIGEST + "\","
                + "\"payload\":" + nonCanonicalPayload() + ','
                + "\"signature\":{"
                + "\"keyId\":\"release-1\","
                + "\"algorithm\":\"ED25519\","
                + "\"value\":\"" + SIGNATURE + "\"}}";
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
