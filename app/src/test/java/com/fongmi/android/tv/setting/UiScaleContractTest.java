package com.fongmi.android.tv.setting;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class UiScaleContractTest {

    @Test
    public void scaleOptionsSupportCompactStandardAndLargerTouchLayouts() throws Exception {
        String setting = read("app/src/main/java/com/fongmi/android/tv/setting/Setting.java");
        assertTrue(setting.contains("UI_SCALE_SMALLER -> 0.8f"));
        assertTrue(setting.contains("UI_SCALE_STANDARD -> 1.0f"));
        assertTrue(setting.contains("UI_SCALE_LARGE -> 1.1f"));
        assertTrue(setting.contains("UI_SCALE_LARGER -> 1.2f"));
        assertTrue(setting.contains("UI_SCALE_LARGEST -> 1.3f"));
        assertTrue(setting.contains("int baseDensity = metrics.densityDpi;"));
        assertFalse(setting.contains("stableDensity * factor"));
        assertTrue(setting.contains("UI_SCALE_SMALLER, UI_SCALE_MORE_COMPACT"));
        assertTrue(setting.contains("UI_SCALE_STANDARD, UI_SCALE_LARGE, UI_SCALE_LARGER, UI_SCALE_LARGEST"));
    }

    @Test
    public void everyLocaleExposesTheSameNineUiScaleChoices() throws Exception {
        assertScaleArray(read("app/src/main/res/values/strings.xml"), "Follow system", "Largest");
        assertScaleArray(read("app/src/main/res/values-zh-rCN/strings.xml"), "跟随系统", "特大");
        assertScaleArray(read("app/src/main/res/values-zh-rTW/strings.xml"), "跟隨系統", "特大");
    }

    private static void assertScaleArray(String xml, String first, String last) {
        int start = xml.indexOf("<string-array name=\"select_ui_scale\">");
        int end = xml.indexOf("</string-array>", start);
        String array = xml.substring(start, end);
        assertTrue(array.contains("<item>" + first + "</item>"));
        assertTrue(array.contains("<item>" + last + "</item>"));
        assertTrue(array.split("<item>", -1).length - 1 == 9);
    }

    private static String read(String name) throws Exception {
        Path path = Path.of(name);
        if (!Files.exists(path)) path = Path.of("..").resolve(name).normalize();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
