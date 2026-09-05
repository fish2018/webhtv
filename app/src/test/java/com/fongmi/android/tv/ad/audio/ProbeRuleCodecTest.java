package com.fongmi.android.tv.ad.audio;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Probe Rules v1 与本项目 {@code spectral-sequence-v2} 的兼容契约。
 *
 * <p>固件 {@code probe-rules-v1-community.json} 是 0o755/m3u8-ad-audio-rules 在 revision 2
 * 时的真实文件，用来保证解析器不是只能吃自造样本。
 */
public class ProbeRuleCodecTest {

    private static final String COMMUNITY_RULES = "/probe-rules-v1-community.json";
    private static final int SEED = 0x0f1e2d3c;

    @Test
    public void communityRulesJsonConvertsToProjectRuleSet() throws IOException {
        ProbeRuleCodec.Document document = ProbeRuleCodec.fromBytes(communityRules());

        assertEquals(2L, document.revision());
        assertEquals(4, document.ruleSet().rules().size());
        assertEquals(AudioFingerprintConfig.standard(), document.ruleSet().config());

        AudioFingerprintRule first = document.ruleSet().rules().get(0);
        assertEquals("ad-1786989757783-1", first.id());
        assertEquals(47_000L, first.durationMs());
        assertEquals(0L, first.anchorOffsetMs());
        assertEquals(5_000L, first.anchorDurationMs());
        assertEquals(4, first.allSequences().size());
    }

    @Test
    public void phaseZeroBecomesMainFingerprintAndRemainingPhasesBecomeVariants() throws IOException {
        AudioFingerprintRule rule = ProbeRuleCodec.fromBytes(communityRules()).ruleSet().rules().get(0);

        assertEquals(0x687f0807, rule.fingerprint()[0]);
        assertEquals(3, rule.variants().size());
        assertEquals(0x4e370617, rule.variants().get(0)[0]);
        assertEquals(0x4f960397, rule.variants().get(1)[0]);
        assertEquals(0x4f9601d7, rule.variants().get(2)[0]);
        assertEquals(18, rule.fingerprint().length);
        assertEquals(18, rule.variants().get(0).length);
        assertEquals(18, rule.variants().get(1).length);
        assertEquals(17, rule.variants().get(2).length);
    }

    /**
     * 上游相位标签必须与 {@link SpectralFingerprint} 的取相位步长一致，否则 v1 的 {@code phaseMs}
     * 就不能直接当成本项目的变体顺序。
     */
    @Test
    public void projectPhaseOffsetsMatchProbePhaseLabels() {
        AudioFingerprintConfig config = AudioFingerprintConfig.standard();
        int phaseStep = Math.max(1, config.hopSamples() / 4);

        for (int phase = 0; phase < ProbeRuleCodec.PHASES_MS.length; phase++) {
            long phaseMs = Math.round(phase * phaseStep * 1_000.0 / config.sampleRate());
            assertEquals(ProbeRuleCodec.PHASES_MS[phase], phaseMs);
        }
    }

    /** 每个相位的帧数必须等于本项目取窗循环在同样锚点长度下真正产出的帧数。 */
    @Test
    public void frameCountsMatchTheProjectExtractionLoop() throws IOException {
        AudioFingerprintConfig config = AudioFingerprintConfig.standard();

        for (AudioFingerprintRule rule : ProbeRuleCodec.fromBytes(communityRules()).ruleSet().rules()) {
            for (int phase = 0; phase < ProbeRuleCodec.PHASES_MS.length; phase++) {
                assertEquals("phase " + ProbeRuleCodec.PHASES_MS[phase],
                        framesFromExtractionLoop(rule.anchorDurationMs(),
                                ProbeRuleCodec.PHASES_MS[phase], config),
                        rule.allSequences().get(phase).length);
            }
        }
    }

    /**
     * 反演社区 hash 的位布局：低 16 位是「频带 &gt;= 均值」，高 16 位是「频带 &gt;= 下一频带」，
     * 且 band15 与 band0 环绕比较。两者互相约束，随机布局无法全部满足。
     */
    @Test
    public void communityHashesUseTheProjectBitLayout() throws IOException {
        int checked = 0;
        for (AudioFingerprintRule rule : ProbeRuleCodec.fromBytes(communityRules()).ruleSet().rules()) {
            for (int[] sequence : rule.allSequences()) {
                for (int hash : sequence) {
                    if (hash == 0) continue;
                    checked += assertBandLayout(hash);
                }
            }
        }
        assertTrue("constraints checked: " + checked, checked > 500);
    }

    @Test
    public void testMetadataIsValidatedThenDropped() {
        String json = json(withTest(rule("ad-one", 20_000, 0, 5_000, SEED),
                "{\"url\":\"https://example.com/a.m3u8\",\"adStartMs\":15000}"));

        assertEquals(1, ProbeRuleCodec.fromJson(json).ruleSet().rules().size());
    }

    @Test
    public void byteOrderMarkIsAccepted() {
        byte[] bytes = ("\uFEFF" + valid()).getBytes(StandardCharsets.UTF_8);

        assertEquals(1, ProbeRuleCodec.fromBytes(bytes).ruleSet().rules().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedFormatIsRejected() {
        ProbeRuleCodec.fromJson(valid().replace("ad-audio-probe-rules", "ad-audio-rules"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedSchemaVersionIsRejected() {
        ProbeRuleCodec.fromJson(valid().replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
    }

    /** 本项目自己的 v2 规则文件必须走 {@link AudioFingerprintRuleCodec}，不能从这里进来。 */
    @Test(expected = IllegalArgumentException.class)
    public void projectAlgorithmIdIsRejected() {
        ProbeRuleCodec.fromJson(valid()
                .replace("spectral-sequence-v1", AudioFingerprintRuleSet.ALGORITHM_ID));
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroRevisionIsRejected() {
        ProbeRuleCodec.fromJson(valid().replace("\"revision\":2", "\"revision\":0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonIntegerRevisionIsRejected() {
        ProbeRuleCodec.fromJson(valid().replace("\"revision\":2", "\"revision\":2.0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateRootFieldIsRejected() {
        ProbeRuleCodec.fromJson(valid().replace("\"revision\":2", "\"revision\":2,\"revision\":3"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownRootFieldIsRejected() {
        ProbeRuleCodec.fromJson(valid().replace("\"revision\":2", "\"revision\":2,\"enabled\":true"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void trailingDataIsRejected() {
        ProbeRuleCodec.fromJson(valid() + "{}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownRuleFieldIsRejected() {
        ProbeRuleCodec.fromJson(valid().replace("\"durationMs\":20000", "\"durationMs\":20000,\"enabled\":true"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void uppercaseRuleIdIsRejected() {
        ProbeRuleCodec.fromJson(valid().replace("\"ad-one\"", "\"AD-ONE\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void uppercaseHashIsRejected() {
        ProbeRuleCodec.fromJson(valid().replaceFirst("\"([0-9a-f]{8})\"", "\"0F1E2D3C\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingPhaseIsRejected() {
        ProbeRuleCodec.fromJson(valid()
                .replaceFirst(",\\{\"phaseMs\":192,\"hashes\":\\[[^\\]]*]}", ""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicatePhaseIsRejected() {
        ProbeRuleCodec.fromJson(valid().replace("\"phaseMs\":64", "\"phaseMs\":0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedPhaseIsRejected() {
        ProbeRuleCodec.fromJson(valid().replace("\"phaseMs\":64", "\"phaseMs\":32"));
    }

    /** 帧数少一帧就说明锚点长度与序列不自洽，必须整包拒绝而不是当成短规则收下。 */
    @Test(expected = IllegalArgumentException.class)
    public void wrongFrameCountIsRejected() {
        ProbeRuleCodec.fromJson(valid().replaceFirst(",\"[0-9a-f]{8}\"]", "]"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void anchorShorterThanUpstreamMinimumIsRejected() {
        ProbeRuleCodec.fromJson(json(rule("ad-one", 20_000, 0, 1_536, SEED)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void anchorBeyondDurationIsRejected() {
        ProbeRuleCodec.fromJson(json(rule("ad-one", 4_000, 2_000, 5_000, SEED)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void indistinctPrefixIsRejected() {
        ProbeRuleCodec.fromJson(json(flatRule("ad-one", 20_000, 5_000)));
    }

    /** 前缀相同但推导出的广告终点不同，运行时无法安全裁决，整包拒绝。 */
    @Test(expected = IllegalArgumentException.class)
    public void sharedPrefixWithDifferentEndOffsetIsRejected() {
        ProbeRuleCodec.fromJson(json(rule("ad-one", 20_000, 0, 5_000, SEED)
                + "," + rule("ad-two", 30_000, 0, 5_000, SEED)));
    }

    @Test
    public void sharedPrefixWithTheSameEndOffsetIsAccepted() {
        String json = json(rule("ad-one", 20_000, 0, 5_000, SEED)
                + "," + rule("ad-two", 25_000, 5_000, 5_000, SEED));

        assertEquals(2, ProbeRuleCodec.fromJson(json).ruleSet().rules().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownTestFieldIsRejected() {
        ProbeRuleCodec.fromJson(json(withTest(rule("ad-one", 20_000, 0, 5_000, SEED),
                "{\"url\":\"https://example.com/a.m3u8\",\"adStartMs\":15000,\"note\":\"x\"}")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonHttpTestUrlIsRejected() {
        ProbeRuleCodec.fromJson(json(withTest(rule("ad-one", 20_000, 0, 5_000, SEED),
                "{\"url\":\"file:///tmp/a.m3u8\",\"adStartMs\":15000}")));
    }

    private byte[] communityRules() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(COMMUNITY_RULES)) {
            assertNotNull(stream);
            return stream.readAllBytes();
        }
    }

    private static String valid() {
        return json(rule("ad-one", 20_000, 0, 5_000, SEED));
    }

    private static String json(String rules) {
        return "{\"format\":\"ad-audio-probe-rules\",\"schemaVersion\":1,\"revision\":2,"
                + "\"algorithm\":\"spectral-sequence-v1\",\"rules\":[" + rules + "]}";
    }

    private static String rule(String id, long durationMs, long anchorOffsetMs,
                               long anchorDurationMs, int seed) {
        return "{\"id\":\"" + id + "\",\"durationMs\":" + durationMs
                + ",\"anchorOffsetMs\":" + anchorOffsetMs
                + ",\"anchorDurationMs\":" + anchorDurationMs
                + ",\"fingerprints\":[" + fingerprints(anchorDurationMs, seed, false) + "]}";
    }

    /** 前八帧全部相同，用来触发区分度不足的拒绝路径。 */
    private static String flatRule(String id, long durationMs, long anchorDurationMs) {
        return "{\"id\":\"" + id + "\",\"durationMs\":" + durationMs
                + ",\"anchorOffsetMs\":0,\"anchorDurationMs\":" + anchorDurationMs
                + ",\"fingerprints\":[" + fingerprints(anchorDurationMs, SEED, true) + "]}";
    }

    private static String withTest(String rule, String test) {
        return rule.substring(0, rule.length() - 1) + ",\"test\":" + test + "}";
    }

    private static String fingerprints(long anchorDurationMs, int seed, boolean flat) {
        StringBuilder phases = new StringBuilder();
        for (int phase = 0; phase < ProbeRuleCodec.PHASES_MS.length; phase++) {
            int phaseMs = ProbeRuleCodec.PHASES_MS[phase];
            if (phase > 0) phases.append(',');
            phases.append("{\"phaseMs\":").append(phaseMs).append(",\"hashes\":[");
            int count = frames(anchorDurationMs, phaseMs);
            for (int frame = 0; frame < count; frame++) {
                if (frame > 0) phases.append(',');
                int hash = flat ? seed : seed ^ (frame * 0x9e3779b9);
                phases.append('"').append(String.format("%08x", hash)).append('"');
            }
            phases.append("]}");
        }
        return phases.toString();
    }

    private static int frames(long anchorDurationMs, int phaseMs) {
        return (int) ((anchorDurationMs - phaseMs - 512) / 256) + 1;
    }

    private static int framesFromExtractionLoop(long anchorDurationMs, int phaseMs,
                                                AudioFingerprintConfig config) {
        int total = (int) Math.round(anchorDurationMs * config.sampleRate() / 1_000.0);
        int offset = (int) Math.round(phaseMs * config.sampleRate() / 1_000.0);
        int frames = 0;
        for (int start = offset; start + config.windowSamples() <= total; start += config.hopSamples()) {
            frames++;
        }
        return frames;
    }

    private static int assertBandLayout(int hash) {
        int checked = 0;
        for (int band = 0; band < 16; band++) {
            boolean aboveMean = (hash & (1 << band)) != 0;
            if (aboveMean == ((hash & (1 << ((band + 1) % 16))) != 0)) continue;
            assertEquals("band " + band + " of " + Integer.toHexString(hash),
                    aboveMean, (hash & (1 << (band + 16))) != 0);
            checked++;
        }
        return checked;
    }
}
