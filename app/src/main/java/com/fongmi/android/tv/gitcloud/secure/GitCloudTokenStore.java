package com.fongmi.android.tv.gitcloud.secure;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.AtomicFile;
import android.util.Base64;

import com.fongmi.android.tv.App;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class GitCloudTokenStore {

    private static final String PREF = "git_cloud_tokens";
    private static final String ALIAS = "webhtv_git_cloud_token";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String LOCAL_PREFIX = "local-v1:";
    private static final String LOCAL_KEY_FILE = "git_cloud_token.key";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private GitCloudTokenStore() {
    }

    public static void put(String key, String token) throws Exception {
        if (TextUtils.isEmpty(key)) return;
        prefs().edit().putString(key, encrypt(App.get(), token == null ? "" : token)).apply();
    }

    public static String get(String key) throws Exception {
        if (TextUtils.isEmpty(key)) return "";
        String value = prefs().getString(key, "");
        return TextUtils.isEmpty(value) ? "" : decrypt(App.get(), value);
    }

    public static void remove(String key) {
        if (TextUtils.isEmpty(key)) return;
        prefs().edit().remove(key).apply();
    }

    public static JsonObject exportTokens() {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, ?> entry : prefs().getAll().entrySet()) {
            if (TextUtils.isEmpty(entry.getKey()) || !(entry.getValue() instanceof String)) continue;
            try {
                String token = decrypt(App.get(), (String) entry.getValue());
                if (!TextUtils.isEmpty(token)) object.addProperty(entry.getKey(), token);
            } catch (Throwable ignored) {
            }
        }
        return object;
    }

    public static int importTokens(JsonObject object) {
        if (object == null || object.size() == 0) return 0;
        int count = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (TextUtils.isEmpty(entry.getKey()) || entry.getValue() == null || entry.getValue().isJsonNull()) continue;
            try {
                String token = entry.getValue().getAsString();
                if (TextUtils.isEmpty(token)) continue;
                put(entry.getKey(), token);
                count++;
            } catch (Throwable ignored) {
            }
        }
        return count;
    }

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static String encrypt(Context context, String text) throws Exception {
        SecretKey secret;
        String prefix = "";
        try {
            secret = key();
        } catch (KeyStoreException error) {
            // A missing OEM provider is different from an existing, unusable system key.
            if (Security.getProvider(ANDROID_KEYSTORE) != null) throw error;
            secret = localKey(context, true);
            prefix = LOCAL_PREFIX;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret);
        byte[] iv = cipher.getIV();
        byte[] body = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + body.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(body, 0, out, iv.length, body.length);
        return prefix + Base64.encodeToString(out, Base64.NO_WRAP);
    }

    static String decrypt(Context context, String value) throws Exception {
        boolean local = value.startsWith(LOCAL_PREFIX);
        byte[] input = Base64.decode(local ? value.substring(LOCAL_PREFIX.length()) : value, Base64.DEFAULT);
        if (input.length <= IV_BYTES) return "";
        byte[] iv = new byte[IV_BYTES];
        byte[] body = new byte[input.length - IV_BYTES];
        System.arraycopy(input, 0, iv, 0, IV_BYTES);
        System.arraycopy(input, IV_BYTES, body, 0, body.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, local ? localKey(context, false) : key(), new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
    }

    private static synchronized SecretKey localKey(Context context, boolean create) throws Exception {
        File path = new File(context.getNoBackupFilesDir(), LOCAL_KEY_FILE);
        AtomicFile file = new AtomicFile(path);
        byte[] bytes;
        try {
            bytes = file.readFully();
        } catch (FileNotFoundException error) {
            if (!create || path.exists() || new File(path + ".bak").exists()) {
                throw new KeyStoreException("Token 本地密钥不可用，请重新添加账号", error);
            }
            bytes = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(bytes);
            FileOutputStream output = file.startWrite();
            try {
                output.write(bytes);
                output.getFD().sync();
                file.finishWrite(output);
            } catch (Exception failure) {
                file.failWrite(output);
                throw failure;
            }
            // AtomicFile reports some rename failures only through logs. Never save a token
            // until its key is confirmed on disk.
            if (!Arrays.equals(bytes, file.readFully())) throw new KeyStoreException("Token 本地密钥保存失败");
        }
        if (bytes.length != KEY_BYTES) throw new KeyStoreException("Token 本地密钥损坏，请重新添加账号");
        return new SecretKeySpec(bytes, "AES");
    }

    private static synchronized SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(ANDROID_KEYSTORE);
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
