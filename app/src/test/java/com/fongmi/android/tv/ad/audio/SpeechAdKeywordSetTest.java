package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.List;

public class SpeechAdKeywordSetTest {

    @Test
    public void parseNormalizesMixedSeparatorsAndDeduplicates() {
        SpeechAdKeywordSet set = SpeechAdKeywordSet.parse(" 澳门，赌场;澳门； 首充, ＤＯＷＮＬＯＡＤ\r\n提现 ");

        assertEquals(List.of("澳门", "赌场", "首充", "download", "提现"), set.values());
    }

    @Test
    public void chineseKeywordsUseSubstringMatching() {
        SpeechAdKeywordSet set = SpeechAdKeywordSet.parse("首充,提现");

        assertEquals("首充", set.firstMatch("现在完成首充即可提现").orElseThrow());
    }

    @Test
    public void asciiKeywordsRequireWordBoundaries() {
        SpeechAdKeywordSet set = SpeechAdKeywordSet.parse("AD,404");

        assertEquals("ad", set.firstMatch("an ad starts now").orElseThrow());
        assertEquals("404", set.firstMatch("error 404 occurred").orElseThrow());
        assertTrue(set.firstMatch("download finished").isEmpty());
        assertTrue(set.firstMatch("ad2 starts now").isEmpty());
        assertTrue(set.firstMatch("error404 occurred").isEmpty());
    }

    @Test
    public void emptyNullAndPunctuationOnlyTokensAreDropped() {
        assertTrue(SpeechAdKeywordSet.parse(null).isEmpty());
        assertTrue(SpeechAdKeywordSet.parse("，；\n---").isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMoreThanConfiguredKeywordCapacity() {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 129; i++) value.append("词").append(i).append(",");

        SpeechAdKeywordSet.parse(value.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsKeywordLongerThanConfiguredLimit() {
        SpeechAdKeywordSet.parse("a".repeat(65));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInputLongerThanConfiguredLimit() {
        SpeechAdKeywordSet.parse("a".repeat(8_193));
    }

    @Test
    public void equalityAndHashCodeDependOnNormalizedOrderedValues() {
        SpeechAdKeywordSet first = SpeechAdKeywordSet.parse(" 澳门，AD,澳门 ");
        SpeechAdKeywordSet same = SpeechAdKeywordSet.parse("澳门;ad");
        SpeechAdKeywordSet differentOrder = SpeechAdKeywordSet.parse("ad;澳门");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, differentOrder);
        assertNotEquals(first, null);
    }

    @Test
    public void valuesCannotBeModified() {
        SpeechAdKeywordSet set = SpeechAdKeywordSet.parse("澳门");

        try {
            set.values().add("赌场");
            fail("values must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertFalse(set.isEmpty());
        }
    }
}