package com.fongmi.android.tv.utils;

/**
 * 播放页亮度的纯计算逻辑，不依赖 Android 框架，便于单测。
 * <p>
 * 约定：-1 表示「跟随系统」(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)，
 * 0~1 表示窗口亮度覆盖值。
 */
public class BrightnessPolicy {

    public static final float FOLLOW_SYSTEM = -1f;
    public static final float NO_LIMIT = -1f;
    public static final int DEFAULT_SCALE = 255;
    private static final float FALLBACK = 0.5f;
    private static final int FALLBACK_SCALE = 255;

    /**
     * 把系统亮度设置值归一化到 0~1。
     * <p>
     * Settings.System.SCREEN_BRIGHTNESS 的量程由设备决定（255/1023/2047/4095 都有），
     * 不能写死除以 255，否则在大量程机型上会算出远大于 1 的基准值。
     */
    public static float normalize(int raw, int scale) {
        if (raw < 0) return FALLBACK;
        int max = scale > 0 ? scale : FALLBACK_SCALE;
        if (raw > max) max = raw;
        return clamp(raw / (float) max);
    }

    /**
     * 合并用户手势亮度与夜间模式上限，得到最终写入窗口的值。
     *
     * @param user  用户手势设定值，负数表示未设定（跟随系统）
     * @param limit 夜间模式亮度上限，负数表示无上限
     */
    public static float merge(float user, float limit) {
        boolean hasUser = user >= 0;
        boolean hasLimit = limit >= 0;
        if (!hasUser && !hasLimit) return FOLLOW_SYSTEM;
        if (!hasUser) return clamp(limit);
        if (!hasLimit) return clamp(user);
        return clamp(Math.min(user, limit));
    }

    /**
     * 手势滑动增量转换为新的亮度值。deltaY 向上为正。
     */
    public static float scroll(float base, float deltaY, int height) {
        if (height <= 0) return clamp(base);
        return clamp(base + deltaY * 2.0f / height);
    }

    public static float clamp(float value) {
        if (value < 0) return 0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    /**
     * 判断持久化的亮度值是否是旧算法的污染产物。
     * <p>
     * 旧版把 SCREEN_BRIGHTNESS 写死除以 255，只有在量程大于 255 的机型上才会算出
     * 大于 1 的基准值并被夹到 1.0。255 量程机型上的 1.0 是用户主动设的最亮，必须保留。
     */
    public static boolean isLegacyPollutedValue(float saved, int scale) {
        return saved >= 1.0f && scale > DEFAULT_SCALE;
    }
}
