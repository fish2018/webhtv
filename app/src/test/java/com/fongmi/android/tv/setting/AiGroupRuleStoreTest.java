package com.fongmi.android.tv.setting;

import com.fongmi.android.tv.bean.GroupRule;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AiGroupRuleStoreTest {

    @Test
    public void parse_normalizesRulesAsAiAndKeepsConfigsIsolated() {
        String json = "{\"config-a\":[{\"id\":\"a\",\"name\":\"规则A\",\"regex\":\"#([^#]+)$\",\"source\":\"user\"},{\"id\":\"unsafe\",\"name\":\"危险规则\",\"regex\":\"(.*)+\",\"source\":\"user\"}],\"config-b\":[{\"id\":\"b\",\"name\":\"规则B\",\"regex\":\"\\\\[([^\\\\]]+)\\\\]\"}]}";

        Map<String, List<GroupRule>> result = AiGroupRuleStore.parse(json);

        assertEquals(2, result.size());
        assertEquals(1, result.get("config-a").size());
        assertEquals(GroupRule.SOURCE_AI, result.get("config-a").get(0).getSource());
        assertTrue(result.get("config-a").get(0).isValid());
        assertEquals("b", result.get("config-b").get(0).getId());
    }

    @Test
    public void encode_roundTripsValidAiRules() {
        Map<String, List<GroupRule>> input = new LinkedHashMap<>();
        input.put("config-a", List.of(GroupRule.createAi("AI规则", "#([^#]+)$")));

        Map<String, List<GroupRule>> result = AiGroupRuleStore.parse(AiGroupRuleStore.encode(input));

        assertEquals(1, result.get("config-a").size());
        assertTrue(result.get("config-a").get(0).isAi());
    }
}
