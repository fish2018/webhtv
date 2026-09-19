package com.fongmi.android.tv.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DriveCheckServiceTest {

    private static final String[] POSITIVE_METADATA = {
            "\"list\":[{\"fid\":\"file\"}]",
            "\"count\":1",
            "\"shareinfo\":{\"snap_id\":\"snapshot\"}",
            "\"shareinfo\":{\"share_title\":\"title\"}"
    };

    @Test
    public void expiredResponseWithFilesIsBad() {
        JsonObject response = json("""
                {
                  "state": true,
                  "errno": 0,
                  "data": {
                    "share_state": 7,
                    "shareinfo": {"forbid_reason": "链接已过期"},
                    "count": 1,
                    "list": [{"fid": "file"}]
                  }
                }
                """);

        assertEquals(DriveCheckService.STATE_BAD,
                DriveCheckService.classify115ShareState(response.getAsJsonObject("data")));
    }

    @Test
    public void expiredStateOverridesEveryPositiveMetadataHintWithoutAReason() {
        for (String metadata : POSITIVE_METADATA) {
            assertEquals(metadata, DriveCheckService.STATE_BAD,
                    DriveCheckService.classify115ShareState(json("{\"share_state\":7," + metadata + "}")));
        }
    }

    @Test
    public void expiredStateInShareInfoIsBad() {
        assertEquals(DriveCheckService.STATE_BAD, DriveCheckService.classify115ShareState(
                json("{\"count\":1,\"shareinfo\":{\"share_state\":7}}")));
    }

    @Test
    public void forbidReasonOverridesActiveStateAndFiles() {
        for (String reason : new String[]{"链接已过期", "链接已取消", "违规链接已屏蔽"}) {
            JsonObject data = json("{\"share_state\":1,\"count\":1,\"shareinfo\":{}}");
            data.getAsJsonObject("shareinfo").addProperty("forbid_reason", reason);

            assertEquals(reason, DriveCheckService.STATE_BAD, DriveCheckService.classify115ShareState(data));
        }
    }

    @Test
    public void passwordReasonWithFilesIsLocked() {
        for (String reason : new String[]{"提取码错误", "请输入密码"}) {
            JsonObject data = json("{\"count\":1,\"shareinfo\":{}}");
            data.getAsJsonObject("shareinfo").addProperty("forbid_reason", reason);

            assertEquals(reason, DriveCheckService.STATE_LOCKED, DriveCheckService.classify115ShareState(data));
        }
    }

    @Test
    public void explicitExpiryTakesPriorityOverPasswordReason() {
        assertEquals(DriveCheckService.STATE_BAD, DriveCheckService.classify115ShareState(
                json("{\"share_state\":7,\"count\":1,\"shareinfo\":{\"forbid_reason\":\"请输入密码\"}}")));
    }

    @Test
    public void activeShareWithoutAFileListIsOk() {
        assertEquals(DriveCheckService.STATE_OK,
                DriveCheckService.classify115ShareState(json("{\"share_state\":1}")));
    }

    @Test
    public void legacyResponsesCanStillUsePositiveMetadata() {
        for (String metadata : POSITIVE_METADATA) {
            assertEquals(metadata, DriveCheckService.STATE_OK,
                    DriveCheckService.classify115ShareState(json("{" + metadata + "}")));
        }
    }

    @Test
    public void blankForbidReasonDoesNotInvalidateAnActiveShare() {
        JsonObject data = json("{\"share_state\":1,\"shareinfo\":{}}");
        data.getAsJsonObject("shareinfo").addProperty("forbid_reason", " \n ");

        assertEquals(DriveCheckService.STATE_OK, DriveCheckService.classify115ShareState(data));
    }

    @Test
    public void missingPositiveEvidenceIsStillBad() {
        assertEquals(DriveCheckService.STATE_BAD,
                DriveCheckService.classify115ShareState(json("{}")));
    }

    @Test
    public void only115UsesANewCacheNamespace() {
        String url = "https://115.com/s/swff16b3ngy?password=CNYY";
        assertNotEquals("115|" + url, DriveCheckService.cacheKey("115", url));
        assertEquals("115:v2|" + url, DriveCheckService.cacheKey("115", url));

        for (String diskType : new String[]{"aliyun", "quark", "uc", "baidu", "tianyi", "123", "xunlei", "mobile"}) {
            assertEquals(diskType + "|" + url, DriveCheckService.cacheKey(diskType, url));
        }
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
