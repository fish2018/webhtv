package androidx.media3.mpvplayer;

import androidx.annotation.Nullable;
import androidx.media3.common.C;

import java.util.Locale;

final class MpvSubtitleSourcePolicy {

    private MpvSubtitleSourcePolicy() {
    }

    static boolean requiresFileDescriptor(String scheme, String path) {
        if (path == null || !path.startsWith("/")) return false;
        if (path.startsWith("//")) return false; // UNC path or network location
        return scheme == null || scheme.isEmpty() || "file".equalsIgnoreCase(scheme);
    }

    @Nullable
    static String lavfFormat(String path, String mimeType) {
        String value = path == null ? "" : path.toLowerCase(Locale.US);
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        int dot = value.lastIndexOf('.');
        String extension = dot < 0 ? "" : value.substring(dot + 1);
        if ("srt".equals(extension) || "subrip".equals(extension)) return "srt";
        if ("ass".equals(extension) || "ssa".equals(extension)) return "ass";
        if ("vtt".equals(extension) || "webvtt".equals(extension)) return "webvtt";
        if ("ttml".equals(extension) || "dfxp".equals(extension)) return "ttml";
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        if (mime.contains("subrip")) return "srt";
        if (mime.contains("vtt")) return "webvtt";
        if (mime.contains("ass") || mime.contains("ssa")) return "ass";
        if (mime.contains("ttml") || mime.contains("dfxp")) return "ttml";
        return null;
    }

    static String addMode(int selectionFlags) {
        return (selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0 ? "select" : "auto";
    }
}
