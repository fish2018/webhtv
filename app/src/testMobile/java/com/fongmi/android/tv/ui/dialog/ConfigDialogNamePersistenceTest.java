package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigDialogNamePersistenceTest {

    @Test
    public void existingConfigStillUpdatesSubmittedName() throws Exception {
        assertUpdatesExistingName("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java");
        assertUpdatesExistingName("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java");
    }

    @Test
    public void vodAndLiveDialogsPrefillCurrentConfigNameOnMobileAndTv() throws Exception {
        assertPrefillsCurrentConfigName("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java");
        assertPrefillsCurrentConfigName("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java");
    }

    private static void assertUpdatesExistingName(String file) throws Exception {
        String source = read(file);
        assertTrue(file, source.contains("exists != null ? exists.name(name).update() :"));
    }

    private static void assertPrefillsCurrentConfigName(String file) throws Exception {
        String source = read(file);
        assertTrue(file, source.contains("case 0 -> VodConfig.get().getConfig();"));
        assertTrue(file, source.contains("case 1 -> LiveConfig.get().getConfig();"));
        assertTrue(file, source.contains("binding.name.setText(config.getName());"));
    }

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
