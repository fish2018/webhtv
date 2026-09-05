package com.fongmi.android.tv.ad.audio;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Probe Rules v1 严格解析器，把社区 {@code rules.json} 转成本项目的规则集。
 *
 * <p>上游 {@code spectral-sequence-v1} 与本项目 {@code spectral-sequence-v2} 只是版本号命名不同：
 * 采样率、窗长、hop、频带数、相位偏移和 32 位 hash 位布局一致，因此本类只做分发外壳转换，
 * 不重算指纹。v1 用四个带 {@code phaseMs} 标签的相位数组，v2 用 {@code fingerprint} 加
 * {@code variants}；{@code phaseMs=0} 映射为主指纹，其余三个相位映射为变体。
 *
 * <p>校验按上游规则合同执行，且任何一条不满足都拒绝整个文件，不静默跳过单条规则。
 * {@code test} 元数据严格校验后丢弃，不参与匹配。
 */
public final class ProbeRuleCodec {

    public static final String FORMAT = "ad-audio-probe-rules";
    public static final int SCHEMA_VERSION = 1;
    public static final String CONVERTER_VERSION = "probe-v1-to-v2.1";
    public static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
    public static final int MAX_RULES = 1_024;
    /** 上游把 revision 与 test.adStartMs 都限制在 JavaScript 安全整数范围内。 */
    public static final long MAX_SAFE_INTEGER = 9007199254740991L;

    static final int[] PHASES_MS = {0, 64, 128, 192};

    private static final int WINDOW_MS = 512;
    private static final int HOP_MS = 256;
    private static final int MIN_ANCHOR_DURATION_MS = 2_000;
    private static final int MAX_ANCHOR_DURATION_MS = 5_000;
    private static final long MIN_DURATION_MS = 1_000L;
    private static final long MAX_DURATION_MS = 600_000L;
    private static final int MAX_SEQUENCE_FRAMES = 64;
    private static final int MAX_TOTAL_HASHES = 65_536;
    private static final int PREFIX_FRAMES = 8;
    private static final int MIN_PREFIX_DISTANCE = 5;
    private static final int MAX_TEST_URL_CHARS = 8_192;
    private static final char BOM = '\uFEFF';
    private static final Pattern RULE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{8}");
    private static final Pattern INTEGER = Pattern.compile("0|[1-9][0-9]*");
    private static final Pattern TEST_URL = Pattern.compile("https?://[^\\s\"]+");

    private ProbeRuleCodec() {
    }

    /** 解析结果；{@code revision} 供下载器做单调递增防回滚。 */
    public record Document(long revision, AudioFingerprintRuleSet ruleSet) {
        public Document {
            if (revision <= 0L || revision > MAX_SAFE_INTEGER) {
                throw new IllegalArgumentException("probe revision is out of range");
            }
            if (ruleSet == null) throw new IllegalArgumentException("rule set is required");
        }
    }

    public static Document fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw invalid("probe rules are empty");
        if (bytes.length > MAX_JSON_BYTES) throw invalid("probe rules are too large");
        return fromJson(decodeUtf8(bytes));
    }

    public static Document fromJson(String json) {
        if (json == null || json.isBlank()) throw invalid("probe rules are empty");
        if (json.length() > MAX_JSON_BYTES) throw invalid("probe rules are too large");
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            require(reader.peek(), JsonToken.BEGIN_OBJECT);
            reader.beginObject();
            Set<String> seen = new HashSet<>();
            String format = null;
            Long schemaVersion = null;
            Long revision = null;
            String algorithm = null;
            List<AudioFingerprintRule> rules = null;
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!seen.add(name)) throw invalid("duplicate root field: " + name);
                switch (name) {
                    case "format" -> format = readString(reader);
                    case "schemaVersion" -> schemaVersion = readLong(reader);
                    case "revision" -> revision = readLong(reader);
                    case "algorithm" -> algorithm = readString(reader);
                    case "rules" -> rules = readRules(reader);
                    default -> throw invalid("unknown root field: " + name);
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("probe rules have trailing data");
            if (!FORMAT.equals(format)) throw invalid("unsupported probe rule format");
            if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
                throw invalid("unsupported probe schema version");
            }
            if (!ProbeRuleSidecar.ALGORITHM_ID.equals(algorithm)) {
                throw invalid("unsupported probe algorithm");
            }
            if (revision == null) throw invalid("probe revision is required");
            if (rules == null) throw invalid("probe rules are required");
            requireConsistentSkipTargets(rules);
            return new Document(revision,
                    new AudioFingerprintRuleSet(AudioFingerprintConfig.standard(), rules));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | IllegalStateException e) {
            throw invalid("invalid probe rule JSON");
        }
    }

    private static List<AudioFingerprintRule> readRules(JsonReader reader) throws IOException {
        require(reader.peek(), JsonToken.BEGIN_ARRAY);
        reader.beginArray();
        List<AudioFingerprintRule> rules = new ArrayList<>();
        int hashes = 0;
        while (reader.hasNext()) {
            if (rules.size() == MAX_RULES) throw invalid("too many probe rules");
            ParsedRule parsed = readRule(reader);
            hashes += parsed.hashCount();
            if (hashes > MAX_TOTAL_HASHES) throw invalid("too many probe hashes");
            rules.add(parsed.rule());
        }
        reader.endArray();
        return rules;
    }

    private static ParsedRule readRule(JsonReader reader) throws IOException {
        require(reader.peek(), JsonToken.BEGIN_OBJECT);
        reader.beginObject();
        Set<String> seen = new HashSet<>();
        String id = null;
        Long durationMs = null;
        Long anchorOffsetMs = null;
        Long anchorDurationMs = null;
        int[][] sequences = null;
        Long testAdStartMs = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (!seen.add(name)) throw invalid("duplicate rule field: " + name);
            switch (name) {
                case "id" -> id = readString(reader);
                case "durationMs" -> durationMs = readLong(reader);
                case "anchorOffsetMs" -> anchorOffsetMs = readLong(reader);
                case "anchorDurationMs" -> anchorDurationMs = readLong(reader);
                case "fingerprints" -> sequences = readFingerprints(reader);
                case "test" -> testAdStartMs = readTest(reader);
                default -> throw invalid("unknown rule field: " + name);
            }
        }
        reader.endObject();
        if (id == null || durationMs == null || anchorOffsetMs == null
                || anchorDurationMs == null || sequences == null) {
            throw invalid("probe rule is incomplete");
        }
        if (!RULE_ID.matcher(id).matches()) throw invalid("invalid probe rule id: " + id);
        if (durationMs < MIN_DURATION_MS || durationMs > MAX_DURATION_MS) {
            throw invalid("durationMs is out of range: " + id);
        }
        if (anchorDurationMs < MIN_ANCHOR_DURATION_MS || anchorDurationMs > MAX_ANCHOR_DURATION_MS) {
            throw invalid("anchorDurationMs is out of range: " + id);
        }
        if (anchorOffsetMs + anchorDurationMs > durationMs) {
            throw invalid("anchor range is invalid: " + id);
        }
        // 先卡 adStartMs 本身，否则 adStartMs + durationMs 会回绕成负数而绕过上限检查。
        if (testAdStartMs != null && (testAdStartMs > MAX_SAFE_INTEGER
                || testAdStartMs > MAX_SAFE_INTEGER - durationMs)) {
            throw invalid("test adStartMs is out of range: " + id);
        }
        int hashCount = 0;
        for (int phase = 0; phase < PHASES_MS.length; phase++) {
            int expected = expectedFrames(anchorDurationMs, PHASES_MS[phase]);
            if (sequences[phase].length != expected) {
                throw invalid("phase " + PHASES_MS[phase] + " expects " + expected
                        + " hashes but has " + sequences[phase].length + ": " + id);
            }
            requireDistinctPrefix(sequences[phase], PHASES_MS[phase], id);
            hashCount += expected;
        }
        AudioFingerprintRule rule = new AudioFingerprintRule(id, durationMs, anchorOffsetMs,
                anchorDurationMs, sequences[0],
                List.of(sequences[1], sequences[2], sequences[3]));
        return new ParsedRule(rule, hashCount);
    }

    private static int[][] readFingerprints(JsonReader reader) throws IOException {
        require(reader.peek(), JsonToken.BEGIN_ARRAY);
        reader.beginArray();
        int[][] sequences = new int[PHASES_MS.length][];
        int count = 0;
        while (reader.hasNext()) {
            if (++count > PHASES_MS.length) throw invalid("too many fingerprint phases");
            readPhase(reader, sequences);
        }
        reader.endArray();
        for (int phase = 0; phase < sequences.length; phase++) {
            if (sequences[phase] == null) {
                throw invalid("missing fingerprint phase " + PHASES_MS[phase]);
            }
        }
        return sequences;
    }

    private static void readPhase(JsonReader reader, int[][] sequences) throws IOException {
        require(reader.peek(), JsonToken.BEGIN_OBJECT);
        reader.beginObject();
        Set<String> seen = new HashSet<>();
        Long phaseMs = null;
        int[] hashes = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (!seen.add(name)) throw invalid("duplicate fingerprint field: " + name);
            switch (name) {
                case "phaseMs" -> phaseMs = readLong(reader);
                case "hashes" -> hashes = readHashes(reader);
                default -> throw invalid("unknown fingerprint field: " + name);
            }
        }
        reader.endObject();
        if (phaseMs == null || hashes == null) throw invalid("fingerprint phase is incomplete");
        int index = phaseIndex(phaseMs);
        if (sequences[index] != null) throw invalid("duplicate fingerprint phase " + phaseMs);
        sequences[index] = hashes;
    }

    private static int[] readHashes(JsonReader reader) throws IOException {
        require(reader.peek(), JsonToken.BEGIN_ARRAY);
        reader.beginArray();
        int[] buffer = new int[MAX_SEQUENCE_FRAMES];
        int size = 0;
        while (reader.hasNext()) {
            if (size == MAX_SEQUENCE_FRAMES) throw invalid("fingerprint sequence is too long");
            String hash = readString(reader);
            if (!HASH.matcher(hash).matches()) throw invalid("invalid fingerprint hash: " + hash);
            buffer[size++] = (int) Long.parseLong(hash, 16);
        }
        reader.endArray();
        return Arrays.copyOf(buffer, size);
    }

    private static Long readTest(JsonReader reader) throws IOException {
        require(reader.peek(), JsonToken.BEGIN_OBJECT);
        reader.beginObject();
        Set<String> seen = new HashSet<>();
        String url = null;
        Long adStartMs = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (!seen.add(name)) throw invalid("duplicate test field: " + name);
            switch (name) {
                case "url" -> url = readString(reader);
                case "adStartMs" -> adStartMs = readLong(reader);
                default -> throw invalid("unknown test field: " + name);
            }
        }
        reader.endObject();
        if (url == null || adStartMs == null) throw invalid("test metadata is incomplete");
        if (url.length() > MAX_TEST_URL_CHARS || !TEST_URL.matcher(url).matches()) {
            throw invalid("invalid test url");
        }
        return adStartMs;
    }

    private static int expectedFrames(long anchorDurationMs, int phaseMs) {
        long usable = anchorDurationMs - phaseMs - WINDOW_MS;
        if (usable < 0) throw invalid("anchor is too short for phase " + phaseMs);
        return (int) (usable / HOP_MS) + 1;
    }

    private static void requireDistinctPrefix(int[] sequence, int phaseMs, String id) {
        int limit = Math.min(PREFIX_FRAMES, sequence.length);
        for (int i = 1; i < limit; i++) {
            if (Integer.bitCount(sequence[0] ^ sequence[i]) > MIN_PREFIX_DISTANCE) return;
        }
        throw invalid("phase " + phaseMs + " prefix is not distinct enough: " + id);
    }

    private static void requireConsistentSkipTargets(List<AudioFingerprintRule> rules) {
        int[][] prefixes = new int[rules.size()][];
        for (int i = 0; i < rules.size(); i++) {
            int[] fingerprint = rules.get(i).fingerprint();
            prefixes[i] = fingerprint.length <= PREFIX_FRAMES
                    ? fingerprint : Arrays.copyOf(fingerprint, PREFIX_FRAMES);
        }
        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                if (!sharesPrefix(prefixes[i], prefixes[j])) continue;
                if (endOffset(rules.get(i)) != endOffset(rules.get(j))) {
                    throw invalid("probe rules " + rules.get(i).id() + " and " + rules.get(j).id()
                            + " share a fingerprint prefix but disagree on the ad end offset");
                }
            }
        }
    }

    private static boolean sharesPrefix(int[] left, int[] right) {
        int limit = Math.min(left.length, right.length);
        for (int i = 0; i < limit; i++) if (left[i] != right[i]) return false;
        return true;
    }

    private static long endOffset(AudioFingerprintRule rule) {
        return rule.durationMs() - rule.anchorOffsetMs();
    }

    private static int phaseIndex(long phaseMs) {
        for (int i = 0; i < PHASES_MS.length; i++) if (PHASES_MS[i] == phaseMs) return i;
        throw invalid("unsupported fingerprint phase " + phaseMs);
    }

    private static long readLong(JsonReader reader) throws IOException {
        require(reader.peek(), JsonToken.NUMBER);
        String raw = reader.nextString();
        if (!INTEGER.matcher(raw).matches()) throw invalid("expected a plain decimal integer");
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw invalid("integer is out of range: " + raw);
        }
    }

    private static String readString(JsonReader reader) throws IOException {
        require(reader.peek(), JsonToken.STRING);
        return reader.nextString();
    }

    private static void require(JsonToken actual, JsonToken expected) {
        if (actual != expected) throw invalid("expected " + expected + " but found " + actual);
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return value.isEmpty() || value.charAt(0) != BOM ? value : value.substring(1);
        } catch (CharacterCodingException e) {
            throw invalid("probe rules must be valid UTF-8");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private record ParsedRule(AudioFingerprintRule rule, int hashCount) {
    }
}
