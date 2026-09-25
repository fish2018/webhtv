package com.fongmi.android.tv.ad.audio;

import com.google.crypto.tink.Configuration;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.signature.Ed25519PrivateKeyManager;
import com.google.crypto.tink.signature.SignatureConfig;

import java.security.GeneralSecurityException;

final class SignedRulePackageTestKeys {

    private static final PublicKeySign SIGNER;
    private static final PublicKeyVerify VERIFIER;

    static {
        try {
            SignatureConfig.register();
            KeysetHandle privateHandle = KeysetHandle.generateNew(
                    Ed25519PrivateKeyManager.rawEd25519Template());
            Configuration configuration = RegistryConfiguration.get();
            SIGNER = privateHandle.getPrimitive(configuration, PublicKeySign.class);
            VERIFIER = privateHandle.getPublicKeysetHandle()
                    .getPrimitive(configuration, PublicKeyVerify.class);
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static PublicKeySign signer() {
        return SIGNER;
    }

    static PublicKeyVerify verifier() {
        return VERIFIER;
    }

    private SignedRulePackageTestKeys() {
    }
}
