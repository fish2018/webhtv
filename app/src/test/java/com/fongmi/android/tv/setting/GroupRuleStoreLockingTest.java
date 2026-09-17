package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupRuleStoreLockingTest {

    @Test
    public void storesInvalidateConfigOnlyAfterLeavingTheirPrivateLock() throws Exception {
        String aiStore = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "AiGroupRuleStore.java")));
        String userStore = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "GroupRuleStore.java")));

        assertFalse(aiStore.contains("public static synchronized"));
        assertFalse(userStore.contains("public static synchronized"));
        assertTrue(aiStore.contains("private static final Object LOCK"));
        assertTrue(userStore.contains("private static final Object LOCK"));
        assertTrue(aiStore.contains("}\n        GroupRuleConfig.invalidate();"));
        assertTrue(userStore.contains("}\n        GroupRuleConfig.invalidate();"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
