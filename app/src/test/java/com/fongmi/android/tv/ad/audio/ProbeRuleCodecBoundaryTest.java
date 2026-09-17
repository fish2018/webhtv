package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/** 评审清单里的边界与溢出项，逐条钉住。 */
public class ProbeRuleCodecBoundaryTest {

    /** revision 超出 JS 安全整数必须拒绝，且不能逃出成 NumberFormatException。 */
    @Test
    public void revisionBeyondSafeIntegerIsRejectedAsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> ProbeRuleCodec.fromJson(valid().replace("\"revision\":2", "\"revision\":9007199254740992")));
    }

    /** 20 位数字超 long 上限，Long.parseLong 会抛 NumberFormatException，必须被转成 IAE。 */
    @Test
    public void revisionWiderThanLongIsRejectedAsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> ProbeRuleCodec.fromJson(valid().replace("\"revision\":2", "\"revision\":99999999999999999999")));
    }

    /** durationMs 上下边界都是闭区间。 */
    @Test
    public void durationBoundsAreInclusive() {
        assertEquals(1, ProbeRuleCodec.fromJson(json(rule("a", 600_000, 0, 5_000))).ruleSet().rules().size());
        assertThrows(IllegalArgumentException.class,
                () -> ProbeRuleCodec.fromJson(json(rule("a", 600_001, 0, 5_000))));
        assertThrows(IllegalArgumentException.class,
                () -> ProbeRuleCodec.fromJson(json(rule("a", 999, 0, 5_000))));
    }

    /** anchorDurationMs 2000..5000 闭区间。 */
    @Test
    public void anchorDurationBoundsAreInclusive() {
        assertEquals(1, ProbeRuleCodec.fromJson(json(rule("a", 20_000, 0, 2_000))).ruleSet().rules().size());
        assertEquals(1, ProbeRuleCodec.fromJson(json(rule("a", 20_000, 0, 5_000))).ruleSet().rules().size());
        assertThrows(IllegalArgumentException.class,
                () -> ProbeRuleCodec.fromJson(json(rule("a", 20_000, 0, 1_999))));
        assertThrows(IllegalArgumentException.class,
                () -> ProbeRuleCodec.fromJson(json(rule("a", 20_000, 0, 5_001))));
    }

    /** anchorOffset + anchorDuration == durationMs 恰好合法（边界不能少算一）。 */
    @Test
    public void anchorTouchingTheAdEndIsAccepted() {
        assertEquals(1, ProbeRuleCodec.fromJson(json(rule("a", 5_000, 0, 5_000))).ruleSet().rules().size());
        assertThrows(IllegalArgumentException.class,
                () -> ProbeRuleCodec.fromJson(json(rule("a", 5_000, 1, 5_000))));
    }

    /** test.adStartMs 加上 durationMs 溢出 long 时不能因为回绕而通过检查。 */
    @Test
    public void testAdStartOverflowIsRejected() {
        String rule = rule("a", 20_000, 0, 5_000);
        String withTest = rule.substring(0, rule.length() - 1)
                + ",\"test\":{\"url\":\"https://e.com/a.m3u8\",\"adStartMs\":9223372036854775000}}";
        assertThrows(IllegalArgumentException.class, () -> ProbeRuleCodec.fromJson(json(withTest)));
    }

    private static String valid() {
        return json(rule("a", 20_000, 0, 5_000));
    }

    private static String json(String rules) {
        return "{\"format\":\"ad-audio-probe-rules\",\"schemaVersion\":1,\"revision\":2,"
                + "\"algorithm\":\"spectral-sequence-v1\",\"rules\":[" + rules + "]}";
    }

    private static String rule(String id, long durationMs, long anchorOffsetMs, long anchorDurationMs) {
        StringBuilder phases = new StringBuilder();
        for (int p = 0; p < ProbeRuleCodec.PHASES_MS.length; p++) {
            int phaseMs = ProbeRuleCodec.PHASES_MS[p];
            if (p > 0) phases.append(',');
            phases.append("{\"phaseMs\":").append(phaseMs).append(",\"hashes\":[");
            int n = (int) ((anchorDurationMs - phaseMs - 512) / 256) + 1;
            for (int f = 0; f < n; f++) {
                if (f > 0) phases.append(',');
                phases.append('"').append(String.format("%08x", 0x0f1e2d3c ^ (f * 0x9e3779b9))).append('"');
            }
            phases.append("]}");
        }
        return "{\"id\":\"" + id + "\",\"durationMs\":" + durationMs
                + ",\"anchorOffsetMs\":" + anchorOffsetMs
                + ",\"anchorDurationMs\":" + anchorDurationMs
                + ",\"fingerprints\":[" + phases + "]}";
    }
}
