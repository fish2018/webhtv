package com.fongmi.android.tv.setting;

import com.fongmi.android.tv.player.VideoAspectMode;
import com.github.catvod.utils.Prefers;

public class LiveSetting {

    private static final int LIST_STYLE_GLASS = 0;
    private static final int LIST_STYLE_CLASSIC = 1;

    public static boolean isBoot() {
        return Prefers.getBoolean("boot_live");
    }

    public static void putBoot(boolean boot) {
        Prefers.put("boot_live", boot);
    }

    public static boolean isAcross() {
        return Prefers.getBoolean("across", true);
    }

    public static void putAcross(boolean across) {
        Prefers.put("across", across);
    }

    public static boolean isChange() {
        return Prefers.getBoolean("change", true);
    }

    public static void putChange(boolean change) {
        Prefers.put("change", change);
    }

    public static boolean isInvert() {
        return Prefers.getBoolean("invert");
    }

    public static void putInvert(boolean invert) {
        Prefers.put("invert", invert);
    }

    public static int getScale() {
        return VideoAspectMode.sanitize(Prefers.getInt("scale_live", PlayerSetting.getScale()));
    }

    public static void putScale(int scale) {
        Prefers.put("scale_live", VideoAspectMode.sanitize(scale));
    }

    public static int getListStyle() {
        int style = Prefers.getInt("live_list_style", LIST_STYLE_CLASSIC);
        return style == LIST_STYLE_CLASSIC ? LIST_STYLE_CLASSIC : LIST_STYLE_GLASS;
    }

    public static boolean isListStyleClassic() {
        return getListStyle() == LIST_STYLE_CLASSIC;
    }

    public static void putListStyle(int style) {
        Prefers.put("live_list_style", style == LIST_STYLE_CLASSIC ? LIST_STYLE_CLASSIC : LIST_STYLE_GLASS);
    }

    public static void putListStyleClassic(boolean classic) {
        putListStyle(classic ? LIST_STYLE_CLASSIC : LIST_STYLE_GLASS);
    }

    public static void toggleListStyle() {
        putListStyle(isListStyleClassic() ? LIST_STYLE_GLASS : LIST_STYLE_CLASSIC);
    }
}
