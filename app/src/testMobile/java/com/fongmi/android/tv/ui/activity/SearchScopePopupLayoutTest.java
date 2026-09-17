package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchScopePopupLayoutTest {

    @Test
    public void tvScopeMenusShareGlassChromeAndFocusStates() throws Exception {
        String search = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SearchActivity.java")));
        String collect = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "CollectActivity.java")));
        String popup = read(findLeanbackResPath().resolve(Path.of("drawable", "shape_search_scope_popup.xml")));
        String item = read(findLeanbackResPath().resolve(Path.of("drawable", "selector_search_scope_item.xml")));

        assertTrue("search input menu must use the shared popup surface", search.contains("R.drawable.shape_search_scope_popup"));
        assertTrue("search result menu must use the shared popup surface", collect.contains("R.drawable.shape_search_scope_popup"));
        assertTrue("search input menu must use the shared item selector", search.contains("R.drawable.selector_search_scope_item"));
        assertTrue("search result menu must use the shared item selector", collect.contains("R.drawable.selector_search_scope_item"));
        assertTrue("popup surface must use the TV glass treatment", popup.contains("<gradient"));
        assertTrue("popup items must define a focused state", item.contains("state_focused"));
        assertTrue("popup items must define a selected state", item.contains("state_selected"));
        assertFalse("search input menu must not restore the opaque white panel", search.contains("setBackgroundColor(0xFFFFFFFF)"));
        assertFalse("search result menu must not restore the opaque white panel", collect.contains("drawable.setColor(Color.WHITE)"));
    }

    @Test
    public void scopeTriggersExposeDropdownAffordanceOnBothTvPages() throws Exception {
        String search = read(findLeanbackResPath().resolve(Path.of("layout", "activity_search.xml")));
        String collect = read(findLeanbackResPath().resolve(Path.of("layout", "activity_collect.xml")));
        String searchTrigger = viewTag(search, "@+id/searchScope");
        String collectTrigger = viewTag(collect, "@+id/searchGroup");

        assertTrue("search input scope trigger must expose a dropdown icon", search.contains("@drawable/ic_scope_expand_more"));
        assertTrue("search result group trigger must expose a dropdown icon", collect.contains("@drawable/ic_scope_expand_more"));
        assertTrue("TV scope triggers must use the same control height", searchTrigger.contains("android:layout_height=\"48dp\"") && collectTrigger.contains("android:layout_height=\"48dp\""));
    }

    private static String viewTag(String layout, String id) {
        int start = layout.indexOf("android:id=\"" + id + "\"");
        int end = layout.indexOf("/>", start);
        return start < 0 || end < 0 ? "" : layout.substring(start, end);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static Path findLeanbackResPath() {
        Path moduleRelative = Path.of("src", "leanback", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res");
    }
}
