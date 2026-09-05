package com.fongmi.android.tv.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DialogGlassChipStyleTest {

    @Test
    public void trackAndTitleSelectionsShareTheLightGlassFocusStyle() throws Exception {
        String selector = readMainResource("drawable", "selector_dialog_glass_chip.xml");
        String active = readMainResource("drawable", "shape_dialog_glass_chip_active.xml");
        String text = readMainResource("color", "selector_dialog_glass_chip_text.xml");
        String track = readLeanbackResource("layout", "adapter_track.xml");
        String title = readLeanbackResource("layout", "adapter_title.xml");

        assertEquals("all focused/selected/checked/activated chip states must share one active drawable",
                5, occurrences(selector, "@drawable/shape_dialog_glass_chip_active"));
        assertTrue("active chips must use the light gray fill from the requested selected style",
                active.contains("android:color=\"#E6E6EA\""));
        assertTrue("active chips must keep a bright two-dp inner border",
                active.contains("android:width=\"2dp\"")
                        && active.contains("android:color=\"#F2FFFFFF\""));
        assertTrue("active chips must include the blue-gray lower focus edge",
                active.contains("android:color=\"#6B6F9F\""));
        assertEquals("checked, activated, selected and focused text must all become dark",
                4, occurrences(text, "android:color=\"#FF17171B\""));
        assertTrue("subtitle rows must use the shared chip background and text selectors",
                usesSharedSelectors(track));
        assertTrue("title rows must use the shared chip background and text selectors",
                usesSharedSelectors(title));
    }

    @Test
    public void mobileCategoryAndFilterDialogsShareTheControlSheetStyle() throws Exception {
        String category = readMobileResource("layout", "adapter_type_dialog.xml");
        String filter = readMobileResource("layout", "adapter_value.xml");

        assertTrue("category rows must use the mobile control-sheet selectors",
                usesControlSheetSelectors(category));
        assertTrue("filter rows must use the same mobile control-sheet selectors",
                usesControlSheetSelectors(filter));
        assertTrue("filter rows must preserve a stable 34dp touch target",
                filter.contains("android:layout_height=\"34dp\""));
    }

    private static boolean usesSharedSelectors(String layout) {
        return layout.contains("android:background=\"@drawable/selector_dialog_glass_chip\"")
                && layout.contains("android:textColor=\"@color/selector_dialog_glass_chip_text\"");
    }

    private static boolean usesControlSheetSelectors(String layout) {
        return layout.contains("android:background=\"@drawable/selector_control_sheet_button\"")
                && layout.contains("android:textColor=\"@color/selector_control_sheet_text\"");
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }

    private static String readMainResource(String type, String file) throws Exception {
        return read(findAppPath().resolve(Path.of("src", "main", "res", type, file)));
    }

    private static String readLeanbackResource(String type, String file) throws Exception {
        return read(findAppPath().resolve(Path.of("src", "leanback", "res", type, file)));
    }

    private static String readMobileResource(String type, String file) throws Exception {
        return read(findAppPath().resolve(Path.of("src", "mobile", "res", type, file)));
    }

    private static Path findAppPath() {
        return Files.exists(Path.of("src", "main")) ? Path.of(".") : Path.of("app");
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
