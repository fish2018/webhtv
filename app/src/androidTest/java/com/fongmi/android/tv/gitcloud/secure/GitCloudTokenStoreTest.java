package com.fongmi.android.tv.gitcloud.secure;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.Base64;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;

import javax.crypto.spec.SecretKeySpec;

public class GitCloudTokenStoreTest extends TestCase {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String PREFIX = "local-v1:";
    private Provider original;
    private int position;
    private File directory;
    private Context context;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        original = Security.getProvider(KEYSTORE);
        Provider[] providers = Security.getProviders();
        for (int i = 0; i < providers.length; i++) if (providers[i] == original) position = i + 1;
        Security.removeProvider(KEYSTORE);
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        directory = new File(target.getCacheDir(), "git-token-test-" + System.nanoTime());
        assertTrue(directory.mkdirs());
        context = new ContextWrapper(target) {
            @Override public File getNoBackupFilesDir() { return directory; }
        };
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Security.removeProvider(KEYSTORE);
            if (original != null) Security.insertProviderAt(original, position);
            File[] files = directory == null ? null : directory.listFiles();
            if (files != null) for (File file : files) assertTrue(file.delete());
            if (directory != null) assertTrue(directory.delete());
        } finally {
            super.tearDown();
        }
    }

    public void testGithubAndCnbRoundTripWithoutKeystore() throws Exception {
        String github = "test-only-github-token";
        String cnb = "test-only-cnb-token";
        String first = GitCloudTokenStore.encrypt(context, github);
        String second = GitCloudTokenStore.encrypt(context, cnb);
        assertTrue(first.startsWith(PREFIX));
        assertTrue(second.startsWith(PREFIX));
        assertEquals(github, GitCloudTokenStore.decrypt(context, first));
        assertEquals(cnb, GitCloudTokenStore.decrypt(context, second));
        assertFalse(first.equals(GitCloudTokenStore.encrypt(context, github)));
        assertEquals(32, keyFile().length());
    }

    public void testLocalCiphertextRemainsReadableWhenKeystoreReturns() throws Exception {
        String value = GitCloudTokenStore.encrypt(context, "test-only-token");
        registerExistingKey();
        assertEquals("test-only-token", GitCloudTokenStore.decrypt(context, value));
    }

    public void testAvailableKeystoreRemainsPreferred() throws Exception {
        if (original != null) Security.insertProviderAt(original, position);
        else registerExistingKey();
        String value = GitCloudTokenStore.encrypt(context, "existing-system-key-path");
        assertFalse(value.startsWith(PREFIX));
        assertEquals("existing-system-key-path", GitCloudTokenStore.decrypt(context, value));
        assertFalse(keyFile().exists());
    }

    public void testLegacyCiphertextFormatWithNistAes256GcmVector() throws Exception {
        registerExistingKey();
        // NIST AES-256-GCM: zero key, 96-bit zero IV, one zero plaintext block.
        byte[] body = hex("000000000000000000000000"
                + "cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919");
        String legacy = Base64.encodeToString(body, Base64.NO_WRAP);
        assertEquals(new String(new char[16]), GitCloudTokenStore.decrypt(context, legacy));
        assertFalse(keyFile().exists());
    }

    public void testRegisteredButUnusableProviderDoesNotFallBack() throws Exception {
        Security.addProvider(new Provider(KEYSTORE, 1, "unusable test provider") {{
            put("KeyStore.AndroidKeyStore", "missing.test.KeyStoreImplementation");
        }});
        try {
            GitCloudTokenStore.encrypt(context, "test-only-token");
            fail("A registered but broken provider must not change key ownership");
        } catch (KeyStoreException expected) {
            assertFalse(keyFile().exists());
        }
    }

    public void testMissingLocalKeyIsNotRecreatedWhileReading() throws Exception {
        String value = GitCloudTokenStore.encrypt(context, "test-only-token");
        assertTrue(keyFile().delete());
        try {
            GitCloudTokenStore.decrypt(context, value);
            fail("Missing key must fail without creating a replacement");
        } catch (KeyStoreException expected) {
            assertFalse(keyFile().exists());
        }
    }

    public void testDamagedLocalKeyIsNotOverwritten() throws Exception {
        try (FileOutputStream output = new FileOutputStream(keyFile())) { output.write(7); }
        try {
            GitCloudTokenStore.encrypt(context, "test-only-token");
            fail("Damaged key must not be replaced");
        } catch (KeyStoreException expected) {
            assertEquals(1, keyFile().length());
        }
    }

    public void testTamperedCiphertextFailsAuthentication() throws Exception {
        String value = GitCloudTokenStore.encrypt(context, "test-only-token");
        byte[] bytes = Base64.decode(value.substring(PREFIX.length()), Base64.DEFAULT);
        bytes[bytes.length - 1] ^= 1;
        try {
            GitCloudTokenStore.decrypt(context, PREFIX + Base64.encodeToString(bytes, Base64.NO_WRAP));
            fail("GCM must reject a modified tag");
        } catch (javax.crypto.BadPaddingException expected) {
            assertEquals(32, keyFile().length());
        }
    }

    private File keyFile() { return new File(directory, "git_cloud_token.key"); }

    private void registerExistingKey() {
        Security.addProvider(new Provider(KEYSTORE, 1, "existing key test provider") {{
            put("KeyStore.AndroidKeyStore", ExistingKeyStore.class.getName());
        }});
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    public static final class ExistingKeyStore extends KeyStoreSpi {
        @Override public Key engineGetKey(String alias, char[] password) { return new SecretKeySpec(new byte[32], "AES"); }
        @Override public boolean engineContainsAlias(String alias) { return "webhtv_git_cloud_token".equals(alias); }
        @Override public void engineLoad(InputStream stream, char[] password) { }
        @Override public void engineStore(OutputStream stream, char[] password) { }
        @Override public Certificate[] engineGetCertificateChain(String alias) { return null; }
        @Override public Certificate engineGetCertificate(String alias) { return null; }
        @Override public Date engineGetCreationDate(String alias) { return new Date(0); }
        @Override public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) { throw new UnsupportedOperationException(); }
        @Override public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) { throw new UnsupportedOperationException(); }
        @Override public void engineSetCertificateEntry(String alias, Certificate certificate) { throw new UnsupportedOperationException(); }
        @Override public void engineDeleteEntry(String alias) { throw new UnsupportedOperationException(); }
        @Override public Enumeration<String> engineAliases() { return Collections.enumeration(Collections.singleton("webhtv_git_cloud_token")); }
        @Override public int engineSize() { return 1; }
        @Override public boolean engineIsKeyEntry(String alias) { return engineContainsAlias(alias); }
        @Override public boolean engineIsCertificateEntry(String alias) { return false; }
        @Override public String engineGetCertificateAlias(Certificate certificate) { return null; }
    }
}
