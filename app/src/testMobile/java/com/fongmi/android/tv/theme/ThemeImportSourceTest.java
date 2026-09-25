package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Source contracts protect the SAF/draft-only boundary of the import/export UI. */
public class ThemeImportSourceTest {

    @Test
    public void importUsesSafAndOnlyReturnsDraft() throws Exception {
        String source = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ThemeImportDialog.java");
        assertTrue(source.contains("new ActivityResultContracts.OpenDocument()"));
        assertTrue(source.contains("getContentResolver().openInputStream(uri)"));
        assertTrue(source.contains("listener.onImported(pending.copy())"));
        assertTrue(!source.contains("ThemeProfileStore.apply"));
        assertTrue(source.contains("ThemeTransfer.fetch(url)"));
    }

    @Test
    public void editorWiresImportExportAndShare() throws Exception {
        String source = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ThemeEditorDialog.java");
        String layout = read("app/src/mobile/res/layout/dialog_theme_editor.xml");
        assertTrue(source.contains("ThemeImportDialog.show"));
        assertTrue(source.contains("ThemeExport.createDocumentIntent"));
        assertTrue(source.contains("ThemeExport.shareIntent"));
        assertTrue(layout.contains("@+id/buttonImport"));
        assertTrue(layout.contains("@+id/buttonExport"));
        assertTrue(layout.contains("@+id/buttonShare"));
    }

    @Test
    public void exportUsesSystemDocumentAndChooser() throws Exception {
        String source = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ThemeExport.java");
        assertTrue(source.contains("Intent.ACTION_CREATE_DOCUMENT"));
        assertTrue(source.contains("Intent.ACTION_SEND"));
        assertTrue(source.contains("Intent.createChooser"));
        assertTrue(source.contains("openOutputStream(uri)"));
    }

    private String read(String path) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
