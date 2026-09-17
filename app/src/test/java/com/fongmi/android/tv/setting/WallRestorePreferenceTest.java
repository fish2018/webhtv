package com.fongmi.android.tv.setting;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WallRestorePreferenceTest {
    @Test
    public void appBackupRestoreDoesNotOverwriteSelectedBuiltinWall() throws Exception {
        Path root = Path.of("src/main/java/com/fongmi/android/tv");
        String config = read(root, "api/config", "WallConfig.java");
        assertTrue(config.contains("initPreservingSelection()"));
        assertTrue(config.contains("preserveSelection"));
        assertTrue(config.contains("if (!preserveSelection) {"));
        assertTrue(config.contains("Setting.putWall(0);"));
        assertTrue(config.contains("Setting.putWallType(type);"));

        String backup = read(root, "utils/AppBackup.java");
        assertTrue(backup.contains("WallConfig.get().initPreservingSelection().load();"));

        String selective = read(root, "bean/Backup.java");
        assertTrue(selective.contains("WallConfig.get().initPreservingSelection().load();"));
    }

    private static String read(Path root, String... segments) throws Exception {
        return new String(Files.readAllBytes(root.resolve(String.join("/", segments))),
                StandardCharsets.UTF_8);
    }
}
