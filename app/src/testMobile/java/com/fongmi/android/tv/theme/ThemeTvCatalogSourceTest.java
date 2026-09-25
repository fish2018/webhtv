package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class ThemeTvCatalogSourceTest {

    @Test
    public void leanbackBindsAfterDynamicViewsAndKeepsPlayerBoundary() throws Exception {
        String activity = read("app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        String controller = read("app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(activity.contains("ThemeController.applyLeanback(this)"));
        assertTrue(controller.contains("applyLeanbackView"));
        assertTrue(controller.contains("isPlayerRoot(view)"));
        assertTrue(controller.contains("state_activated"));
        assertTrue(controller.contains("leanbackBackgroundColors"));
    }

    @Test
    public void catalogIsBundledAndHashProtected() throws Exception {
        String index = read("app/src/main/assets/themes/index.json");
        String digest = read("app/src/main/assets/themes/index.sha256").trim();
        assertTrue(index.contains("\"schemaVersion\":1"));
        assertTrue(index.contains("themes/profiles/default.json"));
        assertTrue(index.contains("themes/previews/default.svg"));
        assertTrue(digest.matches("[0-9a-f]{64}"));
        assertTrue(ThemeCatalog.matchesDigest(index, digest));
        assertTrue(read("app/src/main/java/com/fongmi/android/tv/theme/ThemeCatalogStore.java")
                .contains("KEY_LAST_GOOD"));
    }

    @Test
    public void coreTvResourcesUseSemanticAttributes() throws Exception {
        assertTrue(read("app/src/leanback/res/drawable/shape_item_focused.xml").contains("?attr/colorPrimary"));
        assertTrue(read("app/src/leanback/res/drawable/shape_site_item_normal.xml").contains("?attr/colorSurfaceVariant"));
        assertTrue(read("app/src/leanback/res/drawable/shape_group_button_focused.xml").contains("?attr/colorOnPrimary"));
        assertTrue(read("app/src/leanback/res/drawable/selector_video_item.xml").contains("?attr/colorPrimary"));
    }

    private String read(String path) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
