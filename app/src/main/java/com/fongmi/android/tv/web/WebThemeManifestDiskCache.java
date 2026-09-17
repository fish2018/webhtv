package com.fongmi.android.tv.web;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

final class WebThemeManifestDiskCache implements WebThemeManifestLoader.PersistentCache {

    private static final String DIRECTORY_NAME = "webtheme-manifests-v1";
    private static final String CACHE_MAGIC_V3 = "WEBHTV_THEME_MANIFEST_CACHE_V3\n";
    private static final String CACHE_MAGIC_V2 = "WEBHTV_THEME_MANIFEST_CACHE_V2\n";
    private static final int MAX_CACHE_METADATA_BYTES = 4096;
    private static final int MAX_CACHE_BYTES = WebThemeManifest.MAX_MANIFEST_BYTES
            * WebThemeManifestLoader.MAX_VERSIONS_PER_ENTRY + MAX_CACHE_METADATA_BYTES;
    private static final Object LOCK = new Object();

    private final File directory;

    WebThemeManifestDiskCache(File directory) {
        this.directory = directory;
    }

    static WebThemeManifestDiskCache create(Context context) {
        Context application = context.getApplicationContext();
        Context owner = application == null ? context : application;
        File root = owner.getNoBackupFilesDir();
        if (root == null) root = owner.getFilesDir();
        return new WebThemeManifestDiskCache(new File(root, DIRECTORY_NAME));
    }

    @Override
    public WebThemeManifestLoader.StoredCache read(String cacheKey) throws IOException {
        synchronized (LOCK) {
            ensureDirectory();
            File target = dataFile(cacheKey);
            recover(target);
            if (!target.isFile()) return null;
            String payload = WebThemeManifestLoader.read(new FileInputStream(target), MAX_CACHE_BYTES);
            WebThemeManifestLoader.StoredCache stored = decode(payload);
            target.setLastModified(System.currentTimeMillis());
            return stored;
        }
    }

    @Override
    public void write(String cacheKey, WebThemeManifestLoader.StoredCache stored) throws IOException {
        byte[] bytes = encode(stored);
        synchronized (LOCK) {
            ensureDirectory();
            File target = dataFile(cacheKey);
            File temporary = companion(target, ".tmp");
            File backup = companion(target, ".bak");
            recover(target);
            delete(temporary);
            delete(backup);
            writeAndSync(temporary, bytes);
            boolean backedUp = target.isFile();
            if (backedUp && !target.renameTo(backup)) {
                delete(temporary);
                throw new IOException("Unable to back up theme manifest cache");
            }
            if (!temporary.renameTo(target)) {
                if (backedUp) backup.renameTo(target);
                delete(temporary);
                throw new IOException("Unable to publish theme manifest cache");
            }
            delete(backup);
            target.setLastModified(System.currentTimeMillis());
            prune(target);
        }
    }

    @Override
    public void remove(String cacheKey) {
        synchronized (LOCK) {
            File target = dataFile(cacheKey);
            delete(target);
            delete(companion(target, ".tmp"));
            delete(companion(target, ".bak"));
        }
    }

    private void ensureDirectory() throws IOException {
        if (directory.isDirectory()) return;
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Unable to create theme manifest cache");
        }
    }

    private File dataFile(String cacheKey) {
        return new File(directory, sha256(cacheKey) + ".json");
    }

    private void recover(File target) throws IOException {
        File temporary = companion(target, ".tmp");
        File backup = companion(target, ".bak");
        if (target.isFile()) {
            delete(temporary);
            delete(backup);
            return;
        }
        if (backup.isFile() && !backup.renameTo(target)) {
            throw new IOException("Unable to restore theme manifest cache");
        }
        delete(temporary);
    }

    private void prune(File current) {
        File[] files = directory.listFiles((parent, name) -> name.endsWith(".json"));
        if (files == null || files.length <= WebThemeManifestLoader.MAX_CACHE_ENTRIES) return;
        Arrays.sort(files, (first, second) -> {
            if (first.equals(current)) return second.equals(current) ? 0 : 1;
            if (second.equals(current)) return -1;
            int modified = Long.compare(first.lastModified(), second.lastModified());
            return modified != 0 ? modified : first.getName().compareTo(second.getName());
        });
        int removeCount = files.length - WebThemeManifestLoader.MAX_CACHE_ENTRIES;
        for (int index = 0; index < removeCount; index++) {
            File target = files[index];
            delete(target);
            delete(companion(target, ".tmp"));
            delete(companion(target, ".bak"));
        }
    }

    private static byte[] encode(WebThemeManifestLoader.StoredCache stored) throws IOException {
        if (stored == null || stored.current() == null) {
            throw new IOException("Theme manifest cache entry is missing");
        }
        WebThemeManifestLoader.StoredManifest current = stored.current();
        WebThemeManifestLoader.StoredManifest previous = stored.previous();
        validateManifest(current);
        if (previous != null) validateManifest(previous);
        String previousJson = previous == null ? "" : previous.json();
        String metadata = CACHE_MAGIC_V3
                + (stored.activationPending() ? "1" : "0") + "\n"
                + stored.blockedRevision() + "\n"
                + current.validatedAt() + "\n"
                + encodeHex(current.etag()) + "\n"
                + current.json().length() + "\n"
                + (previous == null ? -1 : previous.validatedAt()) + "\n"
                + (previous == null ? "" : encodeHex(previous.etag())) + "\n"
                + (previous == null ? -1 : previousJson.length()) + "\n";
        if (metadata.getBytes(StandardCharsets.UTF_8).length > MAX_CACHE_METADATA_BYTES) {
            throw new IOException("Theme manifest cache metadata is too large");
        }
        byte[] bytes = (metadata + current.json() + previousJson).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CACHE_BYTES) {
            throw new IOException("Theme manifest cache is too large");
        }
        return bytes;
    }

    private static WebThemeManifestLoader.StoredCache decode(String payload) throws IOException {
        if (payload.startsWith(CACHE_MAGIC_V3)) return decodeV3(payload);
        WebThemeManifestLoader.StoredManifest current = payload.startsWith(CACHE_MAGIC_V2)
                ? decodeV2(payload)
                : decodeLegacy(payload);
        return new WebThemeManifestLoader.StoredCache(current, null, false, "");
    }

    private static WebThemeManifestLoader.StoredCache decodeV3(String payload) throws IOException {
        String[] fields = payload.substring(CACHE_MAGIC_V3.length()).split("\n", 9);
        if (fields.length != 9) throw new IOException("Invalid theme manifest cache metadata");
        int metadataLength = payload.length() - fields[8].length();
        if (payload.substring(0, metadataLength).getBytes(StandardCharsets.UTF_8).length
                > MAX_CACHE_METADATA_BYTES) {
            throw new IOException("Invalid theme manifest cache metadata");
        }
        boolean activationPending;
        if ("1".equals(fields[0])) activationPending = true;
        else if ("0".equals(fields[0])) activationPending = false;
        else throw new IOException("Invalid theme manifest cache activation state");

        String blockedRevision = fields[1];
        if (!blockedRevision.isEmpty() && !blockedRevision.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid theme manifest cache revision");
        }
        long currentValidatedAt = parseTimestamp(fields[2]);
        String currentEtag = decodeHex(fields[3]);
        int currentLength = parseLength(fields[4], false);
        long previousValidatedAt = parseTimestamp(fields[5], true);
        String previousEtag = decodeHex(fields[6]);
        int previousLength = parseLength(fields[7], true);
        if ((previousLength < 0) != (previousValidatedAt < 0)
                || (previousLength < 0 && !previousEtag.isEmpty())) {
            throw new IOException("Invalid theme manifest cache history");
        }

        String body = fields[8];
        long totalLength = (long) currentLength + Math.max(previousLength, 0);
        if (totalLength != body.length()) throw new IOException("Invalid theme manifest cache payload");
        String currentJson = body.substring(0, currentLength);
        String previousJson = previousLength < 0 ? null : body.substring(currentLength);
        WebThemeManifestLoader.StoredManifest current = new WebThemeManifestLoader.StoredManifest(
                currentJson, currentEtag, currentValidatedAt);
        WebThemeManifestLoader.StoredManifest previous = previousJson == null ? null
                : new WebThemeManifestLoader.StoredManifest(previousJson, previousEtag, previousValidatedAt);
        validateManifest(current);
        if (previous != null) validateManifest(previous);
        if (!blockedRevision.isEmpty()
                && (activationPending || previous == null
                || !blockedRevision.equals(WebThemeManifestLoader.revision(previous.json())))) {
            throw new IOException("Invalid theme manifest cache rollback state");
        }
        return new WebThemeManifestLoader.StoredCache(
                current, previous, activationPending, blockedRevision);
    }

    private static WebThemeManifestLoader.StoredManifest decodeV2(String payload) throws IOException {
        int validatedEnd = payload.indexOf('\n', CACHE_MAGIC_V2.length());
        int etagEnd = validatedEnd < 0 ? -1 : payload.indexOf('\n', validatedEnd + 1);
        if (validatedEnd < 0 || etagEnd < 0
                || etagEnd + 1 > MAX_CACHE_METADATA_BYTES) {
            throw new IOException("Invalid theme manifest cache metadata");
        }
        long validatedAt = parseTimestamp(payload.substring(CACHE_MAGIC_V2.length(), validatedEnd));
        String etag = decodeHex(payload.substring(validatedEnd + 1, etagEnd));
        WebThemeManifestLoader.StoredManifest stored = new WebThemeManifestLoader.StoredManifest(
                payload.substring(etagEnd + 1), etag, validatedAt);
        validateManifest(stored);
        return stored;
    }

    private static WebThemeManifestLoader.StoredManifest decodeLegacy(String payload) throws IOException {
        WebThemeManifestLoader.StoredManifest stored = new WebThemeManifestLoader.StoredManifest(
                payload, "", 0);
        validateManifest(stored);
        return stored;
    }

    private static long parseTimestamp(String value) throws IOException {
        return parseTimestamp(value, false);
    }

    private static long parseTimestamp(String value, boolean allowMissing) throws IOException {
        try {
            long timestamp = Long.parseLong(value);
            if (timestamp < 0 && !(allowMissing && timestamp == -1)) {
                throw new IOException("Invalid theme manifest cache timestamp");
            }
            return timestamp;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid theme manifest cache timestamp", e);
        }
    }

    private static int parseLength(String value, boolean allowMissing) throws IOException {
        try {
            int length = Integer.parseInt(value);
            if (length < 0 && !(allowMissing && length == -1)) {
                throw new IOException("Invalid theme manifest cache length");
            }
            return length;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid theme manifest cache length", e);
        }
    }

    private static void validateManifest(WebThemeManifestLoader.StoredManifest stored) throws IOException {
        if (stored.validatedAt() < 0) throw new IOException("Invalid theme manifest cache timestamp");
        if (stored.json().getBytes(StandardCharsets.UTF_8).length > WebThemeManifest.MAX_MANIFEST_BYTES) {
            throw new IOException("Theme manifest is too large");
        }
    }

    private static void writeAndSync(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static File companion(File target, String suffix) {
        return new File(target.getParentFile(), target.getName() + suffix);
    }

    private static void delete(File file) {
        if (file.exists()) file.delete();
    }

    private static String sha256(String value) {
        try {
            return encodeHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String encodeHex(String value) {
        return encodeHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte part : bytes) {
            hex.append(Character.forDigit((part >>> 4) & 0x0f, 16));
            hex.append(Character.forDigit(part & 0x0f, 16));
        }
        return hex.toString();
    }

    private static String decodeHex(String value) throws IOException {
        if ((value.length() & 1) != 0) throw new IOException("Invalid theme manifest cache ETag");
        byte[] bytes = new byte[value.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            int high = Character.digit(value.charAt(index * 2), 16);
            int low = Character.digit(value.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IOException("Invalid theme manifest cache ETag");
            bytes[index] = (byte) ((high << 4) | low);
        }
        return WebThemeManifestLoader.read(new ByteArrayInputStream(bytes), bytes.length);
    }
}
