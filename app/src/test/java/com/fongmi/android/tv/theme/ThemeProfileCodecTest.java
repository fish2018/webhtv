package com.fongmi.android.tv.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ThemeProfileCodecTest {

    @Test
    public void roundTripNormalizesShortAndLowercaseColors() {
        String json = "{\"schemaVersion\":1,\"format\":\"webhtv-theme\",\"name\":\"Demo\","
                + "\"seedSource\":\"custom\",\"seedColor\":\"#abc\","
                + "\"colors\":{\"light\":{\"primary\":\"#112233\"}}}";
        ThemeProfile profile = ThemeProfileCodec.parse(json);
        assertEquals("#AABBCC", profile.seedColor);
        assertEquals("#112233", profile.colors.light.primary);
        assertEquals("#AABBCC", ThemeProfileCodec.parse(ThemeProfileCodec.encode(profile)).seedColor);
    }

    @Test
    public void rejectsMalformedHexColors() {
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"schemaVersion\":1,\"format\":\"webhtv-theme\",\"seedColor\":\"#GGG\"}"));
    }

    @Test
    public void rejectsTransparentColors() {
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"schemaVersion\":1,\"format\":\"webhtv-theme\",\"seedColor\":\"#80112233\"}"));
    }

    @Test
    public void rejectsExecutableFieldsAndNonObjectRoots() {
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"schemaVersion\":1,\"format\":\"webhtv-theme\",\"script\":\"alert(1)\"}"));
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse("[]"));
    }
}
