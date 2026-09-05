package com.fongmi.android.tv.ad.audio;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.fongmi.android.tv.App;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public final class AdAudioRuleStore implements AdAudioRuleSource {

    public static final int MAX_IMPORT_BYTES = 2 * 1024 * 1024;

    private static final String FILE_NAME = "ad-audio-rules.json";
    private static final String TEMP_FILE_NAME = FILE_NAME + ".tmp";
    private static final String LOCAL_SOURCE_ID = "local";

    private final Path directory;
    private final Path rulesFile;
    private final Path temporaryFile;
    private AdAudioRuleSnapshot snapshot;

    public AdAudioRuleStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("directory is required");
        this.directory = directory;
        this.rulesFile = directory.resolve(FILE_NAME);
        this.temporaryFile = directory.resolve(TEMP_FILE_NAME);
        this.snapshot = emptySnapshot("");
    }

    public static AdAudioRuleStore get() {
        return Holder.INSTANCE;
    }

    @Override
    public synchronized AdAudioRuleSnapshot load() {
        if (!Files.exists(rulesFile)) {
            snapshot = emptySnapshot("");
            return snapshot;
        }
        try (InputStream input = Files.newInputStream(rulesFile)) {
            snapshot = parse(readUtf8(input));
        } catch (IOException | IllegalArgumentException e) {
            snapshot = emptySnapshot("RULE_LOAD_FAILED");
        }
        return snapshot;
    }

    public synchronized AdAudioRuleSnapshot current() {
        return snapshot;
    }

    public synchronized AdAudioRuleSnapshot importJson(String json) {
        if (json == null) throw new IllegalArgumentException("rule JSON is required");
        byte[] input = json.getBytes(StandardCharsets.UTF_8);
        if (input.length > MAX_IMPORT_BYTES) throw new IllegalArgumentException("rule JSON is too large");
        return persist(json);
    }

    public synchronized AdAudioRuleSnapshot importStream(InputStream input) throws IOException {
        if (input == null) throw new IllegalArgumentException("rule input is required");
        return persist(readUtf8(input));
    }

    public synchronized AdAudioRuleSnapshot importUri(ContentResolver resolver, Uri uri) throws IOException {
        if (resolver == null || uri == null) throw new IllegalArgumentException("rule URI is required");
        String mimeType = resolver.getType(uri);
        String displayName = displayName(resolver, uri);
        if (!isJsonSource(mimeType, displayName)) throw new IllegalArgumentException("rule file must be JSON");
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("rule file cannot be opened");
            return importStream(input);
        }
    }

    public synchronized AdAudioRuleSnapshot clear() {
        try {
            Files.deleteIfExists(temporaryFile);
            Files.deleteIfExists(rulesFile);
        } catch (IOException e) {
            throw new IllegalStateException("failed to clear ad audio rules", e);
        }
        snapshot = emptySnapshot("");
        return snapshot;
    }

    private AdAudioRuleSnapshot persist(String json) {
        AudioFingerprintRuleSet ruleSet = decodeImport(json);
        String canonicalJson = AudioFingerprintRuleCodec.toJson(ruleSet);
        byte[] data = canonicalJson.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_IMPORT_BYTES) throw new IllegalArgumentException("rule JSON is too large");
        AdAudioRuleSnapshot next = new AdAudioRuleSnapshot(
                LOCAL_SOURCE_ID, versionOf(data), ruleSet, List.of(), "");
        try {
            Files.createDirectories(directory);
            try (FileOutputStream output = new FileOutputStream(temporaryFile.toFile())) {
                output.write(data);
                output.getFD().sync();
            }
            moveAtomically(temporaryFile, rulesFile);
            snapshot = next;
            return snapshot;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
            }
            throw new IllegalStateException("failed to persist ad audio rules", e);
        }
    }

    /**
     * 导入同时接受本项目 SDK v2 规则和社区 Probe Rules v1 规则；两种格式都会被规范化成 v2 后落盘，
     * 因此 {@link #parse(String)} 只需处理 v2。
     *
     * <p>只有 v1 有 {@code format} 根字段，v2 会拒绝它，所以按这个根字段分流。不能用
     * {@code json.contains("ad-audio-probe-rules")}：v2 的规则 id 是自由字符串，
     * 一个 id 恰好等于该值的合法 v2 文件会被误判成 v1 而整包拒绝。
     */
    private static AudioFingerprintRuleSet decodeImport(String json) {
        if (hasProbeFormatField(json)) return ProbeRuleCodec.fromJson(json).ruleSet();
        return AudioFingerprintRuleCodec.fromJson(json);
    }

    /** 判断顶层对象是否有 {@code "format"} 键，只扫根层级，不看嵌套对象里的同名键。 */
    private static boolean hasProbeFormatField(String json) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) return false;
            reader.beginObject();
            while (reader.hasNext()) {
                if ("format".equals(reader.nextName())) {
                    return reader.peek() == JsonToken.STRING
                            && ProbeRuleCodec.FORMAT.equals(reader.nextString());
                }
                reader.skipValue();
            }
            return false;
        } catch (IOException | RuntimeException e) {
            // 结构本身有问题，交给 v2 codec 报出具体错误。
            return false;
        }
    }

    private static AdAudioRuleSnapshot parse(String json) {
        AudioFingerprintRuleSet ruleSet = AudioFingerprintRuleCodec.fromJson(json);
        byte[] canonical = AudioFingerprintRuleCodec.toJson(ruleSet).getBytes(StandardCharsets.UTF_8);
        return new AdAudioRuleSnapshot(
                LOCAL_SOURCE_ID, versionOf(canonical), ruleSet, List.of(), "");
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(16_384);
        byte[] buffer = new byte[8_192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) continue;
            total += count;
            if (total > MAX_IMPORT_BYTES) throw new IllegalArgumentException("rule JSON is too large");
            output.write(buffer, 0, count);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(output.toByteArray()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("rule JSON must be UTF-8", e);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isJsonSource(String mimeType, String displayName) {
        if ("application/json".equalsIgnoreCase(mimeType) || "text/json".equalsIgnoreCase(mimeType)) return true;
        return displayName != null && displayName.toLowerCase(java.util.Locale.ROOT).endsWith(".json");
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) {
        }
        return uri.getLastPathSegment();
    }

    private static String versionOf(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(16);
            for (int i = 0; i < 8; i++) result.append(String.format(java.util.Locale.ROOT, "%02x", digest[i] & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static AdAudioRuleSnapshot emptySnapshot(String error) {
        return new AdAudioRuleSnapshot(
                LOCAL_SOURCE_ID, "", AudioFingerprintRuleSet.empty(), List.of(), error);
    }

    private static final class Holder {
        private static final AdAudioRuleStore INSTANCE =
                new AdAudioRuleStore(App.get().getFilesDir().toPath());
    }
}
