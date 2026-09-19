package com.fongmi.android.tv.player.exo.ass;

import android.content.Context;
import android.graphics.fonts.Font;
import android.graphics.fonts.SystemFonts;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/** Independent fontconfig state; no font downloads or MPV configuration ownership. */
final class AssFonts {
    static String prepare(Context context) throws IOException {
        Set<File> directories = new LinkedHashSet<>();
        if (Build.VERSION.SDK_INT >= 29) {
            for (Font font : SystemFonts.getAvailableFonts()) {
                File file = font.getFile();
                if (file != null && file.canRead()) directories.add(file.getParentFile());
            }
        }
        for (String path : new String[]{"/system/fonts", "/product/fonts", "/system_ext/fonts", "/vendor/fonts", "/odm/fonts"}) {
            File dir = new File(path);
            if (dir.isDirectory() && dir.canRead()) directories.add(dir);
        }
        if (directories.isEmpty()) throw new IOException("no-readable-system-fonts");
        File cache = new File(context.getCacheDir(), "exo-ass/fonts");
        if (!cache.isDirectory() && !cache.mkdirs()) throw new IOException("font-cache-directory");
        File config = new File(cache, "fonts.conf");
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?><fontconfig>");
        for (File dir : directories) xml.append("<dir>").append(escape(dir.getCanonicalPath())).append("</dir>");
        xml.append("<cachedir>").append(escape(cache.getCanonicalPath())).append("</cachedir>");
        xml.append("<alias><family>sans-serif</family><prefer><family>Roboto</family><family>Noto Sans</family>");
        xml.append("<family>Noto Sans CJK SC</family><family>Droid Sans Fallback</family></prefer></alias>");
        xml.append("<alias><family>Arial</family><prefer><family>Liberation Sans</family><family>Roboto</family></prefer></alias>");
        xml.append("</fontconfig>");
        // Each worker has its own file; concurrent player replacement cannot observe a partial file.
        File unique = File.createTempFile("fonts-", ".conf", cache);
        try (FileOutputStream output = new FileOutputStream(unique)) {
            output.write(xml.toString().getBytes(StandardCharsets.UTF_8));
        }
        return unique.getAbsolutePath();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private AssFonts() { }
}
