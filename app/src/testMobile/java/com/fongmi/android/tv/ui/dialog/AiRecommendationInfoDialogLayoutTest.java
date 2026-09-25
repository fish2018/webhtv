package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiRecommendationInfoDialogLayoutTest {

    @Test
    public void recommendationDialogUsesScrollableDetailsAndExplicitTvFocus() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AiRecommendationInfoDialog.java")));
        String lightDialog = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "LightDialog.java")));
        String layout = read(findMainResPath().resolve(Path.of("layout", "dialog_ai_recommendation_info.xml")));

        assertTrue("Recommendation dialog should use the shared compact light-dialog shell",
                source.contains("LightDialog.create("));
        String compactSource = source.replaceAll("\\s+", " ");
        assertTrue("Short TV viewports need the title inside the compact content instead of the outer shell",
                compactSource.contains("LightDialog.create( activity, null, view"));
        assertFalse("Default AlertDialog buttons produce the oversized footer and unreliable DPAD focus",
                source.contains(".setNegativeButton(") || source.contains(".setPositiveButton("));
        assertTrue("The safe confirm action should receive initial TV focus",
                source.contains("confirm.post(confirm::requestFocus)"));

        assertTrue("Dialog should expose source, metadata and data-availability details from the loaded item",
                layout.contains("@+id/dialogTitle")
                        && layout.contains("@+id/sourceLabel")
                        && layout.contains("@+id/attributes")
                        && layout.contains("@+id/dataStatus"));
        assertTrue("Content overview and recommendation reason must be independent sections",
                layout.contains("@+id/overviewCard")
                        && layout.contains("@+id/overview")
                        && layout.contains("@+id/reasonCard")
                        && layout.contains("@+id/reason")
                        && source.contains("item.getOverview()")
                        && source.contains("item.getRecommendationReason()"));
        assertFalse("Opening recommendation detail must not trigger another metadata request",
                source.contains("loadDoubanRating(")
                        || source.contains("Task.execute(")
                        || source.contains("new PersonalRecommendationService("));

        assertTrue("Compact metadata should leave substantially more first-screen room for the synopsis",
                layout.contains("@+id/summaryCard")
                        && singleTag(layout, "summaryCard").contains("layout_height=\"100dp\""));
        assertTrue("The whole dialog should use actual window pixels instead of compatibility screenHeightDp",
                source.contains("ResUtil.getScreenHeight(activity)")
                        && source.contains("calculateDialogHeightPx(")
                        && source.contains("DIALOG_HEIGHT_FRACTION = 0.82f")
                        && source.contains("DIALOG_VERTICAL_MARGIN_DP = 32")
                        && !source.contains("getConfiguration().screenHeightDp"));
        assertTrue("The shared shell must receive an explicit adaptive height so Android cannot clip the footer",
                compactSource.contains("620, dialogHeightPx)")
                        && lightDialog.contains("contentParams.weight = 1.0f")
                        && lightDialog.contains("params.height = heightPx > 0 ? heightPx : WindowManager.LayoutParams.WRAP_CONTENT"));
        String rootArea = layout.substring(0, Math.min(layout.length(), 500));
        assertTrue("The custom content should fill the sized shell",
                rootArea.contains("android:layout_height=\"match_parent\""));
        assertTrue("Long descriptions should consume only the space left above the always-visible actions",
                layout.contains("androidx.core.widget.NestedScrollView")
                        && layout.contains("@+id/detailScroll")
                        && singleTag(layout, "detailScroll").contains("layout_height=\"0dp\"")
                        && singleTag(layout, "detailScroll").contains("layout_weight=\"1\"")
                        && singleTag(layout, "detailScroll").contains("fillViewport=\"true\""));
        assertFalse("Overview must not be ellipsized to one line", singleTag(layout, "overview").contains("maxLines=\"1\"")
                || singleTag(layout, "overview").contains("ellipsize=\"end\""));
        assertFalse("Recommendation reason must not be ellipsized to one line", singleTag(layout, "reason").contains("maxLines=\"1\"")
                || singleTag(layout, "reason").contains("ellipsize=\"end\""));

        assertFalse("Description text must not steal DPAD focus from the scroll container",
                singleTag(layout, "overview").contains("textIsSelectable=\"true\"")
                        || singleTag(layout, "reason").contains("textIsSelectable=\"true\""));

        assertTrue("The scroll container, not its text children, should own TV focus",
                singleTag(layout, "overview").contains("focusable=\"false\"")
                        && singleTag(layout, "reason").contains("focusable=\"false\""));

        assertTrue("Dialog should render its own compact actions",
                layout.contains("@+id/notInterested") && layout.contains("@+id/confirm"));
        assertTrue("The scroll area and actions should have an explicit vertical DPAD path",
                layout.contains("android:nextFocusDown=\"@id/notInterested\"")
                        && occurrences(layout, "android:nextFocusUp=\"@id/detailScroll\"") >= 2);
        assertTrue("Left and right DPAD navigation should cycle between both actions",
                layout.contains("android:nextFocusLeft=\"@id/confirm\"")
                        && layout.contains("android:nextFocusRight=\"@id/confirm\"")
                        && layout.contains("android:nextFocusLeft=\"@id/notInterested\"")
                        && layout.contains("android:nextFocusRight=\"@id/notInterested\""));
    }

    @Test
    public void adaptiveHeightUsesMostOfViewportAndPreservesOuterMargins() {
        assertEquals(886, AiRecommendationInfoDialog.calculateDialogHeightPx(1080, 64));
        assertEquals(590, AiRecommendationInfoDialog.calculateDialogHeightPx(720, 64));
        assertEquals(1771, AiRecommendationInfoDialog.calculateDialogHeightPx(2160, 64));
        assertEquals(40, AiRecommendationInfoDialog.calculateDialogHeightPx(100, 60));
        assertEquals(0, AiRecommendationInfoDialog.calculateDialogHeightPx(0, 64));
    }

    private static String singleTag(String layout, String id) {
        String marker = "android:id=\"@+id/" + id + "\"";
        int start = layout.indexOf(marker);
        if (start < 0) return "";
        int end = layout.indexOf("/>", start);
        return end < 0 ? layout.substring(start) : layout.substring(start, end);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }
}
