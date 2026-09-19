package com.fongmi.android.tv.player.mpv;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class MpvConfigStoreTest {

    @Test
    public void newAndLegacyScriptsDefaultToEnabledIndependentlyOfButton() {
        assertTrue(new MpvConfigStore.CustomButton().scriptEnabled);
        assertTrue(new MpvConfigStore.ConfigProfile().scriptEnabled);
        for (String extra : Arrays.asList("", ",\"scriptEnabled\":null")) {
            List<MpvConfigStore.CustomButton> buttons = MpvConfigStore.parseCustomButtonsJson(
                    "[{\"id\":\"old\",\"title\":\"Old script\",\"enabled\":false,\"trigger\":\"startup\"" + extra + "}]");
            assertEquals(1, buttons.size());
            assertTrue(buttons.get(0).scriptEnabled);
            assertFalse(buttons.get(0).enabled);
            assertEquals("startup", buttons.get(0).trigger);
        }
    }

    @Test
    public void scriptEnabledRoundTripsWithoutChangingButtonTimingOrSource() {
        for (boolean buttonEnabled : Arrays.asList(false, true)) {
            for (String trigger : Arrays.asList("click", "long", "startup")) {
                MpvConfigStore.CustomButton original = scriptButton("saved", trigger, buttonEnabled);
                original.content = "short_source()";
                original.longPressContent = "long_source()";
                original.onStartup = "startup_source()";
                original.scriptEnabled = false;
                String json = MpvConfigStore.serializeCustomButtons(Collections.singletonList(original));
                MpvConfigStore.CustomButton restored = MpvConfigStore.parseCustomButtonsJson(json).get(0);
                assertTrue(json.contains("\"scriptEnabled\":false"));
                assertFalse(restored.scriptEnabled);
                assertEquals(buttonEnabled, restored.enabled);
                assertEquals(trigger, restored.trigger);
                assertEquals(original.script, restored.script);
                assertEquals(original.content, restored.content);
                assertEquals(original.longPressContent, restored.longPressContent);
                assertEquals(original.onStartup, restored.onStartup);
                restored.scriptEnabled = true;
                MpvConfigStore.CustomButton reenabled = MpvConfigStore.parseCustomButtonsJson(
                        MpvConfigStore.serializeCustomButtons(Collections.singletonList(restored))).get(0);
                assertTrue(reenabled.scriptEnabled);
                assertEquals(buttonEnabled, reenabled.enabled);
                assertEquals(trigger, reenabled.trigger);
            }
        }
    }

    @Test
    public void disabledScriptNeverLoadsForAnyTriggerOrButtonVisibility() {
        for (String trigger : Arrays.asList("click", "long", "startup")) {
            for (boolean buttonEnabled : Arrays.asList(false, true)) {
                MpvConfigStore.CustomButton button = scriptButton("disabled", trigger, buttonEnabled);
                button.scriptEnabled = false;
                assertEquals("", MpvConfigStore.buildCustomButtonScript(Collections.singletonList(button), script -> {
                    throw new AssertionError("Disabled script must not even be read");
                }));
                assertFalse(button.isButtonVisible());
                button.scriptEnabled = true;
                String enabledLua = MpvConfigStore.buildCustomButtonScript(Collections.singletonList(button), script -> "restored_action()");
                assertTrue(enabledLua.contains("restored_action()"));
                assertEquals(trigger, button.trigger);
                assertEquals(buttonEnabled, button.isButtonVisible());
            }
        }
    }

    @Test
    public void scriptWithoutButtonAlwaysUsesStartup() {
        for (String trigger : Arrays.asList(null, "", "click", "long", "startup", "unknown")) {
            assertEquals("startup", MpvConfigStore.normalizeScriptTrigger(false, trigger));
        }
    }

    @Test
    public void enabledScriptPreservesSavedTriggerAndDefaultsToClick() {
        assertEquals("click", MpvConfigStore.normalizeScriptTrigger(true, null));
        assertEquals("click", MpvConfigStore.normalizeScriptTrigger(true, "unknown"));
        for (String trigger : Arrays.asList("click", "long", "startup")) {
            assertEquals(trigger, MpvConfigStore.normalizeScriptTrigger(true, trigger));
        }
    }

    @Test
    public void startupAndClickShareOneHandlerAndOneCopyOfScriptContent() {
        MpvConfigStore.CustomButton button = scriptButton("startup", "startup", true);
        button.onStartup = "stale_metadata()";
        String lua = MpvConfigStore.buildCustomButtonScript(Collections.singletonList(button), script -> "toggle_script()");

        assertTrue(lua.contains("buttons[\"startup\"].short = function()\ntoggle_script()\nend\n"));
        assertTrue(lua.contains("run(buttons[\"startup\"].short)\n"));
        assertEquals(lua.indexOf("toggle_script()"), lua.lastIndexOf("toggle_script()"));
        assertFalse(lua.contains("stale_metadata"));
        assertFalse(lua.contains(".long ="));
    }

    @Test
    public void disabledScriptRunsOnceWithoutExposingButtonHandler() {
        String lua = MpvConfigStore.buildCustomButtonScript(
                Collections.singletonList(scriptButton("hidden", "long", false)), script -> "hidden_startup()");

        assertTrue(lua.contains("run(function()\nhidden_startup()\nend)"));
        assertFalse(lua.contains("buttons[\"hidden\"]"));
    }

    @Test
    public void clickAndLongScriptsDoNotRunOnStartup() {
        List<MpvConfigStore.CustomButton> buttons = Arrays.asList(
                scriptButton("click", "click", true), scriptButton("hold", "long", true));
        String lua = MpvConfigStore.buildCustomButtonScript(buttons, script -> "user_action()");

        assertTrue(lua.contains("buttons[\"click\"].short = function()"));
        assertTrue(lua.contains("buttons[\"hold\"].long = function()"));
        assertFalse(lua.contains("buttons[\"click\"].long"));
        assertFalse(lua.contains("buttons[\"hold\"].short"));
        assertFalse(lua.contains("run(buttons["));
        assertFalse(lua.contains("run(function()"));
    }

    @Test
    public void missingScriptAndInvalidButtonDoNotDiscardOtherHandlers() {
        List<MpvConfigStore.CustomButton> buttons = Arrays.asList(null,
                scriptButton("bad\"]", "startup", true), scriptButton("missing", "startup", true),
                scriptButton("good", "click", true));
        String lua = MpvConfigStore.buildCustomButtonScript(buttons, script -> {
            if ("missing.lua".equals(script)) throw new IOException("missing script");
            return "good_action()";
        });

        assertFalse(lua.contains("bad"));
        assertFalse(lua.contains("missing"));
        assertTrue(lua.contains("buttons[\"good\"].short"));
        assertEquals("", MpvConfigStore.buildCustomButtonScript(Collections.emptyList(), script -> ""));
    }

    @Test
    public void generatedLuaRunsStartupAndTogglesWithoutDuplicatingState() throws Exception {
        String interpreter = System.getenv("WEBHTV_TEST_LUA");
        assumeTrue("Set WEBHTV_TEST_LUA to run the generated bridge with a real Lua interpreter",
                interpreter != null && !interpreter.isEmpty());
        List<MpvConfigStore.CustomButton> buttons = new ArrayList<>();
        buttons.add(scriptButton("startup", "startup", true));
        buttons.add(scriptButton("hidden", "click", false));
        buttons.add(scriptButton("click", "click", true));
        buttons.add(scriptButton("hold", "long", true));
        buttons.add(scriptButton("broken", "startup", true));
        buttons.add(scriptButton("returns", "startup", true));
        MpvConfigStore.CustomButton legacy = scriptButton("legacy", "", true);
        legacy.script = "";
        legacy.onStartup = "local legacy_state = 7";
        legacy.content = "legacy_count = legacy_state; legacy_state = legacy_state + 1";
        legacy.longPressContent = "legacy_long = legacy_state";
        buttons.add(legacy);
        MpvConfigStore.CustomButton disabledLegacy = scriptButton("disabled_legacy", "", false);
        disabledLegacy.script = "";
        disabledLegacy.onStartup = "disabled_legacy_ran = true";
        buttons.add(disabledLegacy);
        for (String trigger : Arrays.asList("click", "long", "startup")) {
            MpvConfigStore.CustomButton disabledScript = scriptButton("disabled_script_" + trigger, trigger, true);
            disabledScript.scriptEnabled = false;
            buttons.add(disabledScript);
        }
        String lua = MpvConfigStore.buildCustomButtonScript(buttons, script -> switch (script) {
            case "startup.lua" -> "startup_count = (startup_count or 0) + 1\n"
                    + "toggle_state = not toggle_state\n"
                    + "if toggle_state then live_timers = live_timers + 1 else live_timers = live_timers - 1 end";
            case "hidden.lua" -> "hidden_count = (hidden_count or 0) + 1";
            case "click.lua" -> "click_count = (click_count or 0) + 1";
            case "hold.lua" -> "long_count = (long_count or 0) + 1";
            case "broken.lua" -> "error('expected startup error')";
            case "returns.lua" -> "return_count = (return_count or 0) + 1; return";
            default -> throw new IOException("unexpected script " + script);
        });
        Path generated = Files.createTempFile("webhtv-custom-buttons-", ".lua");
        Process process = null;
        try {
            Files.writeString(generated, lua, StandardCharsets.UTF_8);
            Path fixture = Path.of(getClass().getResource("/mpv/custom_button_runtime_test.lua").toURI());
            process = new ProcessBuilder(interpreter, fixture.toString(), generated.toString())
                    .redirectErrorStream(true).start();
            assertTrue("Lua runtime test timed out", process.waitFor(10, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(output, 0, process.exitValue());
            assertTrue(output, output.contains("PASS: startup/click/long/hidden/legacy/error isolation"));
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(generated);
        }
    }

    private static MpvConfigStore.CustomButton scriptButton(String id, String trigger, boolean enabled) {
        MpvConfigStore.CustomButton button = new MpvConfigStore.CustomButton();
        button.id = id;
        button.title = id;
        button.script = id + ".lua";
        button.trigger = trigger;
        button.enabled = enabled;
        return button;
    }

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
