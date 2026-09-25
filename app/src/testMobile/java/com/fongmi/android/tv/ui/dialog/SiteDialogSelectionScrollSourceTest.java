package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteDialogSelectionScrollSourceTest {

    @Test
    public void groupChangesScrollToTheSelectedSite() throws Exception {
        String source = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/SiteDialog.java");
        int methodStart = source.indexOf("private void onGroupClick(String group, View view)");
        int methodEnd = source.indexOf("private void updateGroupView()", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("filter();"));
        assertTrue(method.contains("binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelectedPosition()));"));
        assertFalse(method.contains("binding.recycler.scrollToPosition(0);"));
    }

    private String read(String path) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
