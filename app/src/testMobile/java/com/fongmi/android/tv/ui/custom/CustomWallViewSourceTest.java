package com.fongmi.android.tv.ui.custom;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CustomWallViewSourceTest {

    @Test
    public void customWallpaperDecodePreservesSmoothGradients() throws Exception {
        String source = readMainSource("ui/custom/CustomWallView.java");

        assertTrue("custom wallpapers must use 32-bit color to avoid visible RGB_565 banding",
                source.contains("options.inPreferredConfig = Bitmap.Config.ARGB_8888;"));
        assertFalse("custom wallpaper decoding must not quantize smooth gradients to RGB_565",
                source.contains("options.inPreferredConfig = Bitmap.Config.RGB_565;"));
    }

    private static String readMainSource(String file) throws Exception {
        Path app = Files.exists(Path.of("src", "main")) ? Path.of(".") : Path.of("app");
        Path path = app.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", file));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
