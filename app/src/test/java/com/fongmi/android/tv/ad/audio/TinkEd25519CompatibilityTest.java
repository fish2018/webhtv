package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;

import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.PublicKeyVerify;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class TinkEd25519CompatibilityTest {

    @Test
    public void rawEd25519SignatureIsPortableLength() throws Exception {
        byte[] message = "webhtv-ed25519-test".getBytes(StandardCharsets.US_ASCII);
        PublicKeySign signer = SignedRulePackageTestKeys.signer();
        PublicKeyVerify verifier = SignedRulePackageTestKeys.verifier();

        byte[] signature = signer.sign(message);

        assertEquals(64, signature.length);
        verifier.verify(signature, message);
    }
}
