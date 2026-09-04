package com.fongmi.android.tv.player.mpv;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvConfigStoreTest {

    @Test
    public void readsTheLastExplicitAImageReaderBackend() {
        String config = "android-vulkan-aimagereader-backend=auto\n"
                + "# android-vulkan-aimagereader-backend=fragment\n"
                + "--android-vulkan-aimagereader-backend=direct # low power\n";
        assertEquals("direct", MpvConfigStore.findOptionValue(
                config, "android-vulkan-aimagereader-backend"));
    }

    @Test
    public void defaultConfig_allowsNativeAssScaling() {
        String config = MpvConfigStore.defaultConfig();

        assertTrue(config.contains("sub-ass-override=scale\n"));
        assertFalse(config.contains("sub-ass-override=yes\n"));
        assertTrue(config.contains("sub-font-provider=fontconfig\n"));
        assertFalse(config.contains("sub-font-provider=none\n"));
    }

    @Test
    public void parseProfilesJson_returnsEmptyForBrokenOrNonArrayJson() {
        assertTrue(MpvConfigStore.parseProfilesJson("broken").isEmpty());
        assertTrue(MpvConfigStore.parseProfilesJson("{\"id\":\"one\"}").isEmpty());
        assertTrue(MpvConfigStore.parseProfilesJson(null).isEmpty());
    }

    @Test
    public void parseProfilesJson_skipsNonObjectsAndWrongFieldTypes() {
        String json = "[null,1,\"bad\","
                + "{\"id\":{},\"name\":\"bad id\"},"
                + "{\"id\":\"bad-time\",\"time\":\"recent\"},"
                + "{\"id\":\"good\",\"name\":\"Remote\",\"type\":\"url\","
                + "\"source\":\"https://example.com/mpv.conf\",\"content\":null,\"time\":123}]";

        List<MpvConfigStore.ConfigProfile> profiles = MpvConfigStore.parseProfilesJson(json);

        assertEquals(1, profiles.size());
        assertEquals("good", profiles.get(0).id);
        assertEquals("Remote", profiles.get(0).name);
        assertEquals("url", profiles.get(0).type);
        assertEquals(123L, profiles.get(0).time);
    }

    @Test
    public void serializeProfiles_usesStableSchemaAndRoundTrips() {
        MpvConfigStore.ConfigProfile profile = new MpvConfigStore.ConfigProfile();
        profile.id = "profile-1";
        profile.name = "Cinema";
        profile.type = "text";
        profile.source = "";
        profile.content = "profile=fast";
        profile.time = 456L;
        profile.active = true;
        List<MpvConfigStore.ConfigProfile> input = new ArrayList<>();
        input.add(profile);

        String json = MpvConfigStore.serializeProfiles(input);
        List<MpvConfigStore.ConfigProfile> output = MpvConfigStore.parseProfilesJson(json);

        assertTrue(json.contains("\"id\":\"profile-1\""));
        assertTrue(json.contains("\"content\":\"profile=fast\""));
        assertTrue(!json.contains("active"));
        assertEquals(1, output.size());
        assertEquals("Cinema", output.get(0).name);
        assertEquals("profile=fast", output.get(0).content);
        assertEquals(456L, output.get(0).time);
    }
}
