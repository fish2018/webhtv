package com.fongmi.android.tv.player;

import com.fongmi.android.tv.player.engine.PlayerEngine;

import java.util.Locale;

/** Pure presentation helpers for Dolby Vision source and runtime fallback facts. */
public final class DolbyVisionFormatLabel {

    private DolbyVisionFormatLabel() {
    }

    public static String formatName(PlayerEngine.VideoPlaybackDetails details) {
        if (details == null || !details.hasDolbyVisionSource()) return "";
        return "Dolby Vision DV." + profile(sourceProfile(details));
    }

    public static String codecText(PlayerEngine.VideoPlaybackDetails details) {
        if (details == null || !details.hasDolbyVisionSource()) return "";
        String codec = firstCodec(details.sourceCodecs());
        if (codec.isEmpty()) codec = fallbackCodec(details);
        if (details.dolbyVisionP81Conversion()) return codec + "（升级P8.1）";
        return details.dolbyVisionHdr10Fallback()
                ? codec + "（降级HDR10）" : codec;
    }

    private static String fallbackCodec(PlayerEngine.VideoPlaybackDetails details) {
        String value = "dvhe." + profile(details.dolbyVisionProfile());
        return details.dolbyVisionLevel() >= 0
                ? value + "." + profile(details.dolbyVisionLevel()) : value;
    }

    private static String firstCodec(String codecs) {
        if (codecs == null) return "";
        String value = codecs.trim();
        int comma = value.indexOf(',');
        return (comma < 0 ? value : value.substring(0, comma)).trim();
    }

    private static int sourceProfile(PlayerEngine.VideoPlaybackDetails details) {
        String codec = firstCodec(details.sourceCodecs()).toLowerCase(Locale.US);
        if (codec.startsWith("dvhe.") || codec.startsWith("dvh1.")) {
            int end = codec.indexOf('.', 5);
            String value = end < 0 ? codec.substring(5) : codec.substring(5, end);
            try {
                int profile = Integer.parseInt(value);
                if (profile > 0) return profile;
            } catch (NumberFormatException ignored) {
            }
        }
        return details.dolbyVisionProfile();
    }

    private static String profile(int value) {
        return String.format(Locale.US, "%02d", value);
    }
}
