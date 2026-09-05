package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TmdbSearchDialogSelectionSourceTest {

    @Test
    public void manualTmdbDialogHighlightsAndFocusesCurrentMatch() throws Exception {
        String activity = readMainJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String dialog = readMainJava("com", "fongmi", "android", "tv", "ui", "dialog", "TmdbSearchDialog.java");
        String adapter = readMainJava("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbAdapter.java");
        String layout = readMainRes("layout", "adapter_tmdb_item.xml");
        String selector = readMainResIfExists("drawable", "selector_tmdb_search_item.xml");

        assertTrue("manual TMDB search must pass the current match into the dialog",
                activity.contains(".selectedItem(matchedTmdbItem)"));
        assertTrue("the dialog must retain the current match while search results are replaced",
                dialog.contains("private TmdbItem selectedItem;")
                        && dialog.contains("public TmdbSearchDialog selectedItem(TmdbItem selectedItem)")
                        && dialog.contains("adapter.setSelectedItem(selectedItem);"));
        assertTrue("the dialog must reveal and focus the current match instead of always using row zero",
                dialog.contains("adapter.getSelectedPosition()")
                        && dialog.contains("focusResult(position)")
                        && dialog.contains("scrollToPositionWithOffset(position, 0)"));
        assertTrue("pressing down from the search field must return to the current match when available",
                dialog.contains("if (KeyUtil.isDownKey(event)) return focusPreferredResult();")
                        && dialog.contains("private boolean focusPreferredResult()"));
        assertTrue("the adapter must expose the current row through persistent selected state and a label",
                adapter.contains("holder.binding.getRoot().setSelected(selected);")
                        && adapter.contains("holder.binding.current.setVisibility(selected ? View.VISIBLE : View.GONE);")
                        && adapter.contains("TmdbRecommendationRows.sameIdentity(item, selectedItem)"));
        assertTrue("the result row must use a selected-state background and include a current-match label",
                layout.contains("android:background=\"@drawable/selector_tmdb_search_item\"")
                        && layout.contains("android:id=\"@+id/current\""));
        assertTrue("the row selector must distinguish both the current match and remote-control focus",
                selector.contains("android:state_selected=\"true\"")
                        && selector.contains("android:state_focused=\"true\""));
    }

    @Test
    public void leanbackTmdbDialogUsesScreenRelativeSizingAndReadableRows() throws Exception {
        String dialog = readMainJava("com", "fongmi", "android", "tv", "ui", "dialog", "TmdbSearchDialog.java");
        String dialogLayout = readMainRes("layout", "dialog_result_list.xml");
        Path root = Path.of("").toAbsolutePath();
        Path leanbackItemPath = root.resolve(Path.of("app", "src", "leanback", "res", "layout", "adapter_tmdb_item.xml"));
        if (!Files.exists(leanbackItemPath)) leanbackItemPath = root.resolve(Path.of("src", "leanback", "res", "layout", "adapter_tmdb_item.xml"));
        String leanbackItemLayout = Files.exists(leanbackItemPath) ? read(leanbackItemPath) : "";

        assertTrue("TV dialog width must scale with the current display instead of stopping at a small fixed dp cap",
                dialog.contains("boolean leanback = Util.isLeanback();")
                        && dialog.contains("metrics.widthPixels * 0.88f"));
        assertTrue("TV result list height must scale with the current display",
                dialog.contains("metrics.heightPixels * 0.52f"));
        assertTrue("TV search controls must use readable text and remote-friendly focus targets",
                dialogLayout.contains("android:textSize=\"24sp\"")
                        && dialogLayout.contains("android:textSize=\"20sp\"")
                        && dialogLayout.contains("android:minWidth=\"120dp\"")
                        && dialogLayout.contains("android:minHeight=\"64dp\""));
        assertTrue("TV result rows must have their own larger leanback layout",
                leanbackItemLayout.contains("android:minHeight=\"144dp\"")
                        && leanbackItemLayout.contains("android:layout_width=\"88dp\"")
                        && leanbackItemLayout.contains("android:layout_height=\"120dp\"")
                        && leanbackItemLayout.contains("android:textSize=\"22sp\"")
                        && leanbackItemLayout.contains("android:textSize=\"16sp\""));
    }


    private static String readMainJava(String... parts) throws Exception {
        Path path = mainPath("java");
        for (String part : parts) path = path.resolve(part);
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String readMainRes(String... parts) throws Exception {
        return read(mainResourcePath(parts));
    }

    private static String readMainResIfExists(String... parts) throws Exception {
        Path path = mainResourcePath(parts);
        return Files.exists(path) ? read(path) : "";
    }

    private static Path mainResourcePath(String... parts) {
        Path path = mainPath("res");
        for (String part : parts) path = path.resolve(part);
        return path;
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Path mainPath(String directory) {
        Path root = Path.of("").toAbsolutePath();
        Path appPath = root.resolve(Path.of("app", "src", "main", directory));
        return Files.exists(appPath) ? appPath : root.resolve(Path.of("src", "main", directory));
    }
}
