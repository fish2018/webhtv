package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;

import com.fongmi.android.tv.bean.Rule;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RuleIdUtilTest {

    @Test
    public void computeRuleIdIgnoresNullListEntries() {
        Rule withNulls = Rule.create(
                "rule",
                Arrays.asList("b.example", null, "a.example"),
                Arrays.asList(null, "regex"),
                Arrays.asList("exclude", null));
        Rule withoutNulls = Rule.create(
                "rule",
                Arrays.asList("a.example", "b.example"),
                Collections.singletonList("regex"),
                Collections.singletonList("exclude"));

        assertEquals(RuleIdUtil.computeRuleId(withoutNulls), RuleIdUtil.computeRuleId(withNulls));
    }
}
