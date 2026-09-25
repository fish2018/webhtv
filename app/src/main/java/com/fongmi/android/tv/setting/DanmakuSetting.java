package com.fongmi.android.tv.setting;

import android.net.Uri;
import android.text.TextUtils;

import androidx.media3.ui.danmaku.DanmakuConfig;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Prefers;

public class DanmakuSetting {

    public static boolean isLoad() {
        return Prefers.getBoolean("danmaku_load");
    }

    public static void putLoad(boolean danmakuLoad) {
        Prefers.put("danmaku_load", danmakuLoad);
    }

    public static boolean isAuto() {
        return Prefers.getBoolean("danmaku_auto");
    }

    public static void putAuto(boolean auto) {
        Prefers.put("danmaku_auto", auto);
    }

    public static boolean isSpiderFirst() {
        return Prefers.getBoolean("danmaku_spider_first");
    }

    public static void putSpiderFirst(boolean spiderFirst) {
        Prefers.put("danmaku_spider_first", spiderFirst);
    }

    public static String getApiUrl() {
        return Prefers.getString("danmaku_api_url", "");
    }

    public static void putApiUrl(String url) {
        Prefers.put("danmaku_api_url", url == null ? "" : url.trim());
    }

    public static boolean isShow() {
        return Prefers.getBoolean("danmaku_show", true);
    }

    public static void putShow(boolean danmakuShow) {
        Prefers.put("danmaku_show", danmakuShow);
    }

    public static float getTextScale() {
        return Prefers.getFloat("danmaku_text_scale", 1f);
    }

    public static void putTextScale(float value) {
        Prefers.put("danmaku_text_scale", value);
    }

    public static float getTransparency() {
        return Prefers.getFloat("danmaku_transparency", 0f);
    }

    public static void putTransparency(float value) {
        Prefers.put("danmaku_transparency", value);
    }

    public static boolean isTextBold() {
        return Prefers.getBoolean("danmaku_text_bold");
    }

    public static void putTextBold(boolean value) {
        Prefers.put("danmaku_text_bold", value);
    }

    public static int getStyleMode() {
        return Prefers.getInt("danmaku_style_mode", DanmakuConfig.STYLE_STROKE);
    }

    public static void putStyleMode(int value) {
        Prefers.put("danmaku_style_mode", value);
    }

    public static int getColorMode() {
        return Prefers.getInt("danmaku_color_mode", DanmakuConfig.COLOR_MODE_DEFAULT);
    }

    public static void putColorMode(int value) {
        Prefers.put("danmaku_color_mode", value);
    }

    public static float getShadowTransparency() {
        return Prefers.getFloat("danmaku_shadow_transparency", 0.1f);
    }

    public static void putShadowTransparency(float value) {
        Prefers.put("danmaku_shadow_transparency", value);
    }

    public static float getStrokeWidthMultiplier() {
        return Prefers.getFloat("danmaku_stroke_width_multiplier", 0.12f);
    }

    public static void putStrokeWidthMultiplier(float value) {
        Prefers.put("danmaku_stroke_width_multiplier", value);
    }

    public static float getProjectionOffsetX() {
        return Prefers.getFloat("danmaku_projection_offset_x", 0.08f);
    }

    public static void putProjectionOffsetX(float value) {
        Prefers.put("danmaku_projection_offset_x", value);
    }

    public static float getProjectionOffsetY() {
        return Prefers.getFloat("danmaku_projection_offset_y", 0.08f);
    }

    public static void putProjectionOffsetY(float value) {
        Prefers.put("danmaku_projection_offset_y", value);
    }

    public static float getProjectionTransparency() {
        return Prefers.getFloat("danmaku_projection_transparency", 0.2f);
    }

    public static void putProjectionTransparency(float value) {
        Prefers.put("danmaku_projection_transparency", value);
    }

    public static long getDurationMs() {
        return Prefers.getLong("danmaku_duration", 8000L);
    }

    public static void putDurationMs(long value) {
        Prefers.put("danmaku_duration", value);
    }

    public static long getFixedDurationMs() {
        return Prefers.getLong("danmaku_fixed_duration", 5000L);
    }

    public static void putFixedDurationMs(long value) {
        Prefers.put("danmaku_fixed_duration", value);
    }

    public static long getTimeOffsetMs() {
        return Prefers.getLong("danmaku_time_offset", 0L);
    }

    public static void putTimeOffsetMs(long value) {
        Prefers.put("danmaku_time_offset", value);
    }

    public static int getMaxOnScreen() {
        return Math.max(1, Prefers.getInt("danmaku_max_on_screen", getDefaultMaxOnScreen()));
    }

    public static void putMaxOnScreen(int value) {
        Prefers.put("danmaku_max_on_screen", Math.max(1, value));
    }

    public static float getScrollAreaRatio() {
        float value = Prefers.getFloat("danmaku_scroll_area_ratio", getDefaultScrollAreaRatio());
        return Math.max(0.01f, Math.min(1f, value));
    }

    public static void putScrollAreaRatio(float value) {
        Prefers.put("danmaku_scroll_area_ratio", Math.max(0.01f, Math.min(1f, value)));
    }

    public static int getMaxScrollLines() {
        return Prefers.getInt("danmaku_max_scroll_lines", 0);
    }

    public static void putMaxScrollLines(int value) {
        Prefers.put("danmaku_max_scroll_lines", value);
    }

    public static int getMaxTopLines() {
        return clampFixedLines(Prefers.getInt("danmaku_max_top_lines", 0));
    }

    public static void putMaxTopLines(int value) {
        Prefers.put("danmaku_max_top_lines", clampFixedLines(value));
    }

    public static int getMaxBottomLines() {
        return clampFixedLines(Prefers.getInt("danmaku_max_bottom_lines", 0));
    }

    public static void putMaxBottomLines(int value) {
        Prefers.put("danmaku_max_bottom_lines", clampFixedLines(value));
    }

    public static int getDisplayLines() {
        int scroll = getMaxScrollLines();
        if (scroll > 0) return clampDisplayLines(scroll);
        int legacy = Math.max(getMaxTopLines(), getMaxBottomLines());
        return legacy > 0 ? clampDisplayLines(legacy) : 3;
    }

    public static void putDisplayLines(int value) {
        value = clampDisplayLines(value);
        putMaxScrollLines(value);
        putMaxTopLines(value);
        putMaxBottomLines(value);
    }

    private static int clampFixedLines(int value) {
        return Math.max(0, Math.min(5, value));
    }

    private static int clampDisplayLines(int value) {
        return Math.max(1, Math.min(5, value));
    }

    private static int getDefaultMaxOnScreen() {
        return Util.isLeanback() ? 80 : 150;
    }

    private static float getDefaultScrollAreaRatio() {
        return Util.isLeanback() ? 0.42f : 0.5f;
    }

    public static float getLineSpacing() {
        return Prefers.getFloat("danmaku_line_spacing", 1.4f);
    }

    public static void putLineSpacing(float value) {
        Prefers.put("danmaku_line_spacing", value);
    }

    public static float getScrollGapRatio() {
        return Prefers.getFloat("danmaku_scroll_gap_ratio", 0f);
    }

    public static void putScrollGapRatio(float value) {
        Prefers.put("danmaku_scroll_gap_ratio", value);
    }

    public static boolean isShowScroll() {
        return Prefers.getBoolean("danmaku_show_scroll", true);
    }

    public static void putShowScroll(boolean value) {
        Prefers.put("danmaku_show_scroll", value);
    }

    public static boolean isShowTop() {
        return Prefers.getBoolean("danmaku_show_top", true);
    }

    public static void putShowTop(boolean value) {
        Prefers.put("danmaku_show_top", value);
    }

    public static boolean isShowBottom() {
        return Prefers.getBoolean("danmaku_show_bottom", true);
    }

    public static void putShowBottom(boolean value) {
        Prefers.put("danmaku_show_bottom", value);
    }

    public static boolean isShowReverse() {
        return Prefers.getBoolean("danmaku_show_reverse", true);
    }

    public static void putShowReverse(boolean value) {
        Prefers.put("danmaku_show_reverse", value);
    }

    public static boolean isShowPositioned() {
        return Prefers.getBoolean("danmaku_show_positioned", true);
    }

    public static void putShowPositioned(boolean value) {
        Prefers.put("danmaku_show_positioned", value);
    }

    public static boolean isShowSubtitle() {
        return Prefers.getBoolean("danmaku_show_subtitle", true);
    }

    public static void putShowSubtitle(boolean value) {
        Prefers.put("danmaku_show_subtitle", value);
    }

    public static boolean isShowSpecial() {
        return Prefers.getBoolean("danmaku_show_special", true);
    }

    public static void putShowSpecial(boolean value) {
        Prefers.put("danmaku_show_special", value);
    }

    public static String getEffectiveApiUrl() {
        String userUrl = getApiUrl();
        if (!TextUtils.isEmpty(userUrl)) return userUrl.trim();
        String configUrl = VodConfig.get().getConfig().getDanmaku();
        return configUrl == null ? "" : configUrl.trim();
    }

    public static String getValidApiUrl() {
        String url = getEffectiveApiUrl();
        return isValidApiUrl(url) ? url : "";
    }

    public static boolean hasValidApiUrl() {
        return !TextUtils.isEmpty(getValidApiUrl());
    }

    public static boolean isValidApiUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        try {
            Uri uri = Uri.parse(url.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && !TextUtils.isEmpty(uri.getHost());
        } catch (Throwable e) {
            return false;
        }
    }

    public static void resetAppearance() {
        DanmakuConfig config = DanmakuConfig.DEFAULT;
        putTextScale(config.textScale);
        putTransparency(config.transparency);
        putTextBold(config.textBold);
        putStyleMode(config.styleMode);
        putShadowTransparency(config.shadowTransparency);
        putStrokeWidthMultiplier(config.strokeWidthMultiplier);
        putProjectionOffsetX(config.projectionOffsetXMultiplier);
        putProjectionOffsetY(config.projectionOffsetYMultiplier);
        putProjectionTransparency(config.projectionTransparency);
        putColorMode(config.colorMode);
    }

    public static void resetTiming() {
        DanmakuConfig config = DanmakuConfig.DEFAULT;
        putDurationMs(config.durationMs);
        putFixedDurationMs(config.fixedDurationMs);
        putTimeOffsetMs(config.timeOffsetMs);
    }

    public static void resetDensity() {
        putMaxOnScreen(getDefaultMaxOnScreen());
        putScrollAreaRatio(getDefaultScrollAreaRatio());
        DanmakuConfig config = DanmakuConfig.DEFAULT;
        putScrollGapRatio(config.scrollGapRatio);
        putLineSpacing(config.lineSpacing);
        putMaxScrollLines(config.maxScrollLines);
        putMaxTopLines(config.maxTopLines);
        putMaxBottomLines(config.maxBottomLines);
    }

    public static void resetDisplay() {
        DanmakuConfig config = DanmakuConfig.DEFAULT;
        putScrollGapRatio(config.scrollGapRatio);
        putLineSpacing(config.lineSpacing);
        putDisplayLines(3);
    }

    public static DanmakuConfig getConfig() {
        return new DanmakuConfig.Builder()
                .setTextScale(getTextScale())
                .setTransparency(getTransparency())
                .setTextBold(isTextBold())
                .setStyleMode(getStyleMode())
                .setShadowTransparency(getShadowTransparency())
                .setStrokeWidthMultiplier(getStrokeWidthMultiplier())
                .setProjectionOffsetXMultiplier(getProjectionOffsetX())
                .setProjectionOffsetYMultiplier(getProjectionOffsetY())
                .setProjectionTransparency(getProjectionTransparency())
                .setColorMode(getColorMode())
                .setDurationMs(getDurationMs())
                .setFixedDurationMs(getFixedDurationMs())
                .setTimeOffsetMs(getTimeOffsetMs())
                .setMaxOnScreen(getMaxOnScreen())
                .setScrollAreaRatio(getScrollAreaRatio())
                .setScrollGapRatio(getScrollGapRatio())
                .setLineSpacing(getLineSpacing())
                .setMaxScrollLines(getDisplayLines())
                .setMaxTopLines(getDisplayLines())
                .setMaxBottomLines(0)
                .setShowScroll(isShowScroll())
                .setShowTop(isShowTop())
                .setShowBottom(false)
                .setShowReverse(isShowReverse())
                .setShowPositioned(isShowPositioned())
                .setShowSubtitle(isShowSubtitle())
                .setShowSpecial(isShowSpecial())
                .build();
    }
}
