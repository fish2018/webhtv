package com.fongmi.android.tv.ad.audio;

import com.fongmi.android.tv.App;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * 社区 Probe Rules v1 的本地缓存，按上游 m3u8-ad-audio-probe 的规则更新语义实现：
 * 只接受更高 revision，同 revision 但内容不同视为发布冲突并保留当前缓存，
 * 新内容先落临时文件、完整解析成功后才原子替换。
 *
 * <p>与 {@link AdAudioRuleStore} 是两个独立槽位，远程规则不会覆盖用户手动导入的规则。
 */
public final class ProbeRuleStore implements AdAudioRuleSource {

    public static final int MAX_DOWNLOAD_BYTES = ProbeRuleCodec.MAX_JSON_BYTES;

    static final String SOURCE_ID = "probe";
    static final String ERROR_ROLLBACK = "PROBE_REVISION_ROLLBACK";
    static final String ERROR_CONFLICT = "PROBE_REVISION_CONFLICT";
    static final String ERROR_LOAD_FAILED = "PROBE_RULE_LOAD_FAILED";

    private static final String FILE_NAME = "ad-audio-probe-rules.json";
    private static final String TEMP_FILE_NAME = FILE_NAME + ".tmp";

    private final Path directory;
    private final Path rulesFile;
    private final Path temporaryFile;
    private AdAudioRuleSnapshot snapshot;

    public ProbeRuleStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("directory is required");
        this.directory = directory;
        this.rulesFile = directory.resolve(FILE_NAME);
        this.temporaryFile = directory.resolve(TEMP_FILE_NAME);
        this.snapshot = emptySnapshot("");
    }

    public static ProbeRuleStore get() {
        return Holder.INSTANCE;
    }

    @Override
    public synchronized AdAudioRuleSnapshot load() {
        byte[] stored = readStored();
        if (stored == null) {
            snapshot = emptySnapshot("");
            return snapshot;
        }
        try {
            snapshot = toSnapshot(ProbeRuleCodec.fromBytes(stored), stored);
        } catch (IllegalArgumentException e) {
            snapshot = emptySnapshot(ERROR_LOAD_FAILED);
        }
        return snapshot;
    }

    public synchronized AdAudioRuleSnapshot current() {
        return snapshot;
    }

    /** 已缓存的 revision，没有可用缓存时返回 0。 */
    public synchronized long revision() {
        byte[] stored = readStored();
        return stored == null ? 0L : storedRevision(stored);
    }

    /**
     * 安装一份下载到的 Probe v1 规则。解析失败、版本回滚或同版本内容冲突都会抛出
     * {@link IllegalArgumentException}，且不会动到已有缓存。
     */
    public synchronized AdAudioRuleSnapshot install(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("probe rules are required");
        }
        if (bytes.length > MAX_DOWNLOAD_BYTES) {
            throw new IllegalArgumentException("probe rules are too large");
        }
        ProbeRuleCodec.Document document = ProbeRuleCodec.fromBytes(bytes);
        byte[] stored = readStored();
        if (stored != null && MessageDigest.isEqual(stored, bytes)) {
            snapshot = toSnapshot(document, bytes);
            return snapshot;
        }
        long current = stored == null ? 0L : storedRevision(stored);
        if (current > 0L && document.revision() < current) {
            throw new IllegalArgumentException(ERROR_ROLLBACK);
        }
        if (current > 0L && document.revision() == current) {
            throw new IllegalArgumentException(ERROR_CONFLICT);
        }
        persist(bytes);
        snapshot = toSnapshot(document, bytes);
        return snapshot;
    }

    public synchronized AdAudioRuleSnapshot clear() {
        try {
            Files.deleteIfExists(temporaryFile);
            Files.deleteIfExists(rulesFile);
        } catch (IOException e) {
            throw new IllegalStateException("failed to clear probe rules", e);
        }
        snapshot = emptySnapshot("");
        return snapshot;
    }

    private void persist(byte[] bytes) {
        try {
            Files.createDirectories(directory);
            try (FileOutputStream output = new FileOutputStream(temporaryFile.toFile())) {
                output.write(bytes);
                output.getFD().sync();
            }
            moveAtomically(temporaryFile, rulesFile);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
            }
            throw new IllegalStateException("failed to persist probe rules", e);
        }
    }

    private byte[] readStored() {
        if (!Files.exists(rulesFile)) return null;
        try {
            byte[] stored = Files.readAllBytes(rulesFile);
            return stored.length == 0 || stored.length > MAX_DOWNLOAD_BYTES ? null : stored;
        } catch (IOException e) {
            return null;
        }
    }

    /** 缓存损坏时返回 0，让新内容可以无条件覆盖，而不是被永久卡住。 */
    private static long storedRevision(byte[] stored) {
        try {
            return ProbeRuleCodec.fromBytes(stored).revision();
        } catch (IllegalArgumentException e) {
            return 0L;
        }
    }

    private static AdAudioRuleSnapshot toSnapshot(ProbeRuleCodec.Document document, byte[] raw) {
        return new AdAudioRuleSnapshot(SOURCE_ID, document.revision() + ":" + digestPrefix(raw),
                document.ruleSet(), List.of(), "");
    }

    private static AdAudioRuleSnapshot emptySnapshot(String error) {
        return new AdAudioRuleSnapshot(
                SOURCE_ID, "", AudioFingerprintRuleSet.empty(), List.of(), error);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String digestPrefix(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                result.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static final class Holder {
        private static final ProbeRuleStore INSTANCE =
                new ProbeRuleStore(App.get().getFilesDir().toPath());
    }
}
