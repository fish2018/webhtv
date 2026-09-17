package com.fongmi.android.tv.ui.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PlayerDetailControllerSourceTest {

    private static final Path SOURCE = Paths.get("src/main/java/com/fongmi/android/tv/ui/detail/PlayerDetailController.java");

    @Test
    public void directPlayWaitsForExplicitUserAction() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        assertTrue(source.contains("protected boolean autoPlay()"));
        assertTrue(source.contains("return false; // 详情直放必须由用户点击播放"));
        assertFalse(source.contains("return true; // 详情直放模式自动播放"));
    }
}
