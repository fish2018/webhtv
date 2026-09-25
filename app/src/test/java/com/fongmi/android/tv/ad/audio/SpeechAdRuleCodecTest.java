package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class SpeechAdRuleCodecTest {

    @Test
    public void parsesBomCommentsBothCommasAndRangeSeparator() {
        String source = "\uFEFF# default rules\n"
                + "// ignored line\n"
                + "广告之后*>马上回来，30 // trailing comment\n"
                + "中场休息*>广告时间，[2，30]\n"
                + "广告之后马上回来,30\n";

        SpeechAdRuleSet rules = SpeechAdRuleCodec.parse(source);

        assertEquals(3, rules.rules().size());
        assertEquals("广告之后*>马上回来,30", rules.rules().get(0).canonical());
        assertEquals(0L, rules.rules().get(0).preRollMs());
        assertEquals(30_000L, rules.rules().get(0).postRollMs());
        assertEquals("中场休息*>广告时间,[2,30]", rules.rules().get(1).canonical());
        assertEquals(2_000L, rules.rules().get(1).preRollMs());
        assertEquals(30_000L, rules.rules().get(1).postRollMs());
        assertEquals(SpeechAdRuleCodec.serialize(rules), rules.toText());
    }

    @Test
    public void normalizesAndDeduplicatesEquivalentRules() {
        SpeechAdRuleSet rules = SpeechAdRuleCodec.parse(
                "ＤＯＷＮＬＯＡＤ，1\n download,1\n下载,1");

        assertEquals(2, rules.rules().size());
        assertEquals("download,1", rules.rules().get(0).canonical());
        assertEquals(rules.rules().get(0), rules.find(rules.rules().get(0).id()));
    }

    @Test
    public void acceptsEmptyInputAndKeepsRulesImmutable() {
        assertTrue(SpeechAdRuleCodec.parse(null).isEmpty());
        assertTrue(SpeechAdRuleCodec.parse("").isEmpty());

        SpeechAdRuleSet rules = SpeechAdRuleCodec.parse("广告,1");
        try {
            rules.rules().add(rules.rules().get(0));
            fail("rules must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertFalse(rules.isEmpty());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingAction() {
        SpeechAdRuleCodec.parse("广告之后");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedRangeAction() {
        SpeechAdRuleCodec.parse("广告，[2]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeAction() {
        SpeechAdRuleCodec.parse("广告,-1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsActionBeyondBound() {
        SpeechAdRuleCodec.parse("广告,121");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyAndWildcardOnlySegments() {
        SpeechAdRuleCodec.parse("广告>>马上回来,1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTooManySegments() {
        SpeechAdRuleCodec.parse(String.join(">", Collections.nCopies(9, "句")) + ",1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWildcardOnlyRule() {
        SpeechAdRuleCodec.parse("*,1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTooManyRules() {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < SpeechAdRuleCodec.MAX_RULES + 1; i++) {
            source.append("广告").append(i).append(",1\n");
        }
        SpeechAdRuleCodec.parse(source.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizedDocument() {
        SpeechAdRuleCodec.parse("广告,1\n".repeat(20_000));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSubsecondRollInProgrammaticRule() {
        SpeechAdRule.Segment segment = new SpeechAdRule.Segment(
                List.of("广告"), List.of(), false, false);
        new SpeechAdRule("speech-test", List.of(segment), 500L, 0L);
    }

    @Test
    public void canonicalTextRoundTripsIncludingRangesAndRepeatedWildcards() {
        SpeechAdRuleSet rules = SpeechAdRuleCodec.parse("本片**冠名，[4,30]\n广告*>回来[2,35]");
        SpeechAdRuleSet restored = SpeechAdRuleCodec.parse(rules.toText());
        assertEquals(rules.toText(), restored.toText());
        assertEquals(rules.rules().get(0).id(), restored.rules().get(0).id());
        assertEquals("本片*冠名,[4,30]", rules.rules().get(0).canonical());
    }

    @Test
    public void segmentLimitAppliesToTheWholePatternNotEachLiteral() {
        assertThrows(IllegalArgumentException.class,
                () -> SpeechAdRuleCodec.parse("a".repeat(80) + "*" + "b".repeat(80) + ",1"));
        assertEquals(1, SpeechAdRuleCodec.parse("a".repeat(128) + ",1").rules().size());
    }

    @Test
    public void malformedSuffixesRejectTheWholeDocumentWithLineNumber() {
        for (String invalid : List.of("广告,1,2", "广告,[2,3],4", "广告,1.5", "广告,+1",
                "广告,[2,]", "广告,1e2", "广告,[0,121]", "广告>>回来,1", "***,1")) {
            IllegalArgumentException error = assertThrows(invalid, IllegalArgumentException.class,
                    () -> SpeechAdRuleCodec.parse("正常,1\n" + invalid));
            assertTrue(error.getMessage(), error.getMessage().startsWith("line 2:"));
        }
    }

    @Test
    public void inputLimitCountsUtf8BytesAndDoesNotLogRuleText() {
        String oversized = "#" + "字".repeat(22_000);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SpeechAdRuleCodec.parse(oversized));
        assertFalse(error.getMessage().contains("字"));
    }

    @Test
    public void programmaticAdjacentLiteralsBehaveLikeCanonicalText() {
        SpeechAdRule.Segment segment = new SpeechAdRule.Segment(
                List.of("a", "b"), List.of(false), false, false);
        assertEquals(3, segment.find("ax ab", 0).start());
        assertEquals(5, segment.find("ax ab", 0).end());
    }
}
