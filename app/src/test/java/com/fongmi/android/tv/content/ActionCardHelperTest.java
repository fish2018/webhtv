package com.fongmi.android.tv.content;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonParser;

import org.junit.Test;

public class ActionCardHelperTest {

    @Test
    public void emptyResponseIsSuccessfulAndSilent() {
        assertEquals("", ActionCardHelper.nonJsonResponse(null));
        assertEquals("", ActionCardHelper.nonJsonResponse(""));
        assertEquals("", ActionCardHelper.nonJsonResponse("  \n\t"));
        assertEquals("", ActionCardHelper.nonJsonResponse("null"));
        assertEquals("", ActionCardHelper.nonJsonResponse("  null  "));
    }

    @Test
    public void nonJsonTextIsPreservedForTheUser() {
        assertEquals("动作执行失败", ActionCardHelper.nonJsonResponse("  动作执行失败  "));
    }

    @Test
    public void invalidActionEndpointsStillReturnAnErrorMessage() {
        assertEquals("站点不存在", messageOf(ActionCardHelper.error("站点不存在")));
        assertEquals("动作执行失败", messageOf(ActionCardHelper.error(null)));
    }

    private static String messageOf(String response) {
        return JsonParser.parseString(response).getAsJsonObject().get("msg").getAsString();
    }
}
