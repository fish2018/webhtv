package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BrightnessPolicyTest {

    private static final float DELTA = 0.0001f;

    @Test
    public void normalizeUsesDeviceScaleInsteadOfHardcoded255() {
        // 小米/OPPO 常见 2047 量程：一半亮度必须归一化为 0.5，而非旧代码的 1024/255≈4.0
        assertEquals(0.5f, BrightnessPolicy.normalize(1024, 2047), 0.001f);
        // 一加 1023 量程
        assertEquals(0.5f, BrightnessPolicy.normalize(512, 1023), 0.001f);
        // 华为部分机型 4095 量程
        assertEquals(0.25f, BrightnessPolicy.normalize(1024, 4095), 0.001f);
        // Pixel 255 量程
        assertEquals(0.5f, BrightnessPolicy.normalize(128, 255), 0.01f);
    }

    @Test
    public void normalizeNeverExceedsOne() {
        for (int scale : new int[]{255, 1023, 2047, 4095}) {
            for (int raw = 0; raw <= scale; raw += Math.max(scale / 16, 1)) {
                float value = BrightnessPolicy.normalize(raw, scale);
                assertTrue("raw=" + raw + " scale=" + scale + " -> " + value, value >= 0f && value <= 1.0f);
            }
        }
    }

    @Test
    public void normalizeFallsBackWhenScaleUnknownOrRawInvalid() {
        // 读不到系统上限时退回 255
        assertEquals(0.5f, BrightnessPolicy.normalize(128, 0), 0.01f);
        assertEquals(0.5f, BrightnessPolicy.normalize(128, -1), 0.01f);
        // raw 超过声明上限时以 raw 为量程，保证不越界
        assertEquals(1.0f, BrightnessPolicy.normalize(2047, 255), DELTA);
        // raw 非法时返回中间值
        assertEquals(0.5f, BrightnessPolicy.normalize(-1, 2047), DELTA);
    }

    @Test
    public void mergeKeepsFollowSystemWhenNeitherUserNorLimitSet() {
        assertEquals(BrightnessPolicy.FOLLOW_SYSTEM, BrightnessPolicy.merge(-1f, -1f), DELTA);
    }

    @Test
    public void mergeAppliesLimitAloneWhenUserNeverAdjusted() {
        assertEquals(0.25f, BrightnessPolicy.merge(-1f, 0.25f), DELTA);
    }

    @Test
    public void mergeAppliesUserAloneWhenNoLimit() {
        assertEquals(0.8f, BrightnessPolicy.merge(0.8f, -1f), DELTA);
    }

    @Test
    public void mergeTakesLowerOfUserAndLimit() {
        // 夜间模式是「上限」，不能反过来把用户调暗的亮度顶高
        assertEquals(0.25f, BrightnessPolicy.merge(0.9f, 0.25f), DELTA);
        assertEquals(0.1f, BrightnessPolicy.merge(0.1f, 0.25f), DELTA);
    }

    @Test
    public void scrollDownwardCanActuallyDim() {
        // 旧 bug：基准值 4.0 时任何滑动都被夹到 1.0，用户无法调暗
        float base = 0.5f;
        int height = 1000;
        float dimmed = BrightnessPolicy.scroll(base, -250f, height);
        assertTrue("向下滑应变暗，实际=" + dimmed, dimmed < base);
        float brighter = BrightnessPolicy.scroll(base, 250f, height);
        assertTrue("向上滑应变亮，实际=" + brighter, brighter > base);
    }

    @Test
    public void scrollFullSwipeCoversWholeRange() {
        assertEquals(1.0f, BrightnessPolicy.scroll(0.5f, 1000f, 1000), DELTA);
        assertEquals(0f, BrightnessPolicy.scroll(0.5f, -1000f, 1000), DELTA);
    }

    @Test
    public void scrollHandlesUnmeasuredView() {
        assertEquals(0.5f, BrightnessPolicy.scroll(0.5f, -300f, 0), DELTA);
        assertEquals(0.5f, BrightnessPolicy.scroll(0.5f, -300f, -1), DELTA);
    }

    @Test
    public void clampBoundsToUnitRange() {
        assertEquals(0f, BrightnessPolicy.clamp(-4.0f), DELTA);
        assertEquals(1.0f, BrightnessPolicy.clamp(4.0f), DELTA);
        assertEquals(0.42f, BrightnessPolicy.clamp(0.42f), DELTA);
    }

    @Test
    public void mergeReturnsOverrideNoneSoNightModeCanBeTurnedOff() {
        // 回归：夜间模式由「有上限」切回「无上限」时，必须产出 -1(BRIGHTNESS_OVERRIDE_NONE)
        // 而不是让调用方短路不写，否则窗口会停留在旧的压暗值，屏幕永久变暗调不回来。
        assertEquals(0.25f, BrightnessPolicy.merge(-1f, 0.25f), DELTA);
        assertEquals(-1f, BrightnessPolicy.merge(-1f, -1f), DELTA);
    }

    @Test
    public void mergeHandlesZeroLimit() {
        assertEquals(0f, BrightnessPolicy.merge(0.5f, 0f), DELTA);
        assertEquals(0f, BrightnessPolicy.merge(-1f, 0f), DELTA);
    }

    @Test
    public void normalizeHandlesZeroAndMaxRaw() {
        assertEquals(0f, BrightnessPolicy.normalize(0, 2047), DELTA);
        assertEquals(1.0f, BrightnessPolicy.normalize(2047, 2047), DELTA);
    }

    @Test
    public void legacyPollutedValueOnlyDiscardedOnLargeScaleDevices() {
        // 2047/1023/4095 量程：1.0 是旧算法夹取出来的污染值，应丢弃
        assertTrue(BrightnessPolicy.isLegacyPollutedValue(1.0f, 2047));
        assertTrue(BrightnessPolicy.isLegacyPollutedValue(1.0f, 1023));
        assertTrue(BrightnessPolicy.isLegacyPollutedValue(1.0f, 4095));
        // 255 量程：旧算法在这里不会溢出，1.0 是用户主动设的最亮，必须保留
        assertFalse(BrightnessPolicy.isLegacyPollutedValue(1.0f, 255));
        // 非最亮值一律保留
        assertFalse(BrightnessPolicy.isLegacyPollutedValue(0.99f, 2047));
        assertFalse(BrightnessPolicy.isLegacyPollutedValue(0.3f, 2047));
        // 未设定过
        assertFalse(BrightnessPolicy.isLegacyPollutedValue(-1f, 2047));
    }

    @Test
    public void brightnessRecoversAfterNightModeTurnedOff() {
        // 夜间模式期间亮度被上限钉住（符合「上限」语义），但这不是永久的：
        // 关掉夜间模式后一次向上手势就能恢复到最亮，不存在永久棘轮。
        float cappedAndPersisted = BrightnessPolicy.merge(BrightnessPolicy.scroll(0.5f, 1000f, 1000), 0.25f);
        assertEquals(0.25f, cappedAndPersisted, DELTA);
        float afterOff = BrightnessPolicy.merge(BrightnessPolicy.scroll(cappedAndPersisted, 1000f, 1000), BrightnessPolicy.NO_LIMIT);
        assertEquals(1.0f, afterOff, DELTA);
    }

    @Test
    public void nightModeCappedGestureNeverPersistsAboveLimit() {
        // 回归：夜间模式(上限0.25)下滑到顶，落盘值必须是 0.25 而非 1.0，
        // 否则切到无上限页面(直播)时会突然全亮，即原 bug 换路径复现。
        float gesture = BrightnessPolicy.scroll(0.5f, 1000f, 1000);
        assertEquals(1.0f, gesture, DELTA);
        float persisted = BrightnessPolicy.merge(gesture, 0.25f);
        assertEquals(0.25f, persisted, DELTA);
    }

    @Test
    public void rememberBrightnessDefaultsOffAndGatesBothDirections() throws Exception {
        // 反馈回归：「播放视频时自动调高亮度」的唯一成因是无条件套用持久化的 player_brightness。
        // 开关必须默认关闭，且同时挡住读与写：只挡读会让手势继续落盘，重新打开开关时又跳回旧值。
        String source = readMainSource("setting/PlayerSetting.java");
        assertTrue("remember-brightness must default to off",
                source.contains("Prefers.getBoolean(KEY_REMEMBER_BRIGHTNESS, false)"));
        int getter = source.indexOf("public static float getBrightness()");
        assertTrue("getBrightness must exist", getter >= 0);
        assertTrue("getBrightness must return FOLLOW_SYSTEM while the switch is off",
                source.indexOf("if (!isRememberBrightness()) return BrightnessPolicy.FOLLOW_SYSTEM;", getter) > getter);
        int setter = source.indexOf("public static void putBrightness(");
        assertTrue("putBrightness must exist", setter >= 0);
        assertTrue("putBrightness must not persist while the switch is off",
                source.indexOf("if (!isRememberBrightness()) return;", setter) > setter);
        // 两个方向都要清：只在关闭时清理是不对称的，旧版用户 prefs 里可能残留一个被夹到 1.0
        // 的污染值，开启开关会立刻把屏幕拉到最亮 —— 正是这个开关要消除的现象。
        int toggle = source.indexOf("public static void putRememberBrightness(");
        assertTrue("putRememberBrightness must exist", toggle >= 0);
        String toggleBody = source.substring(toggle, source.indexOf("\n    }", toggle));
        assertTrue("toggling remember-brightness must drop the remembered value in both directions",
                toggleBody.contains("Prefers.remove(\"player_brightness\");"));
        assertFalse("the cleanup must not be gated on the switch turning off",
                toggleBody.contains("if (!remember)"));
    }

    @Test
    public void playbackPagesHandBrightnessBackToSystemOnExit() throws Exception {
        // 没有还原路径时，同一个 Activity 内关掉开关或退出播放后窗口仍停在旧覆盖值，
        // 表现为「屏幕一直偏亮」。release() 必须写回 FOLLOW_SYSTEM，且两个宿主页都要调用。
        String gesture = readMobileSource("ui/custom/CustomKeyDown.java");
        int release = gesture.indexOf("public void release()");
        assertTrue("CustomKeyDown must expose release()", release >= 0);
        assertTrue("release must hand brightness back to the system",
                gesture.indexOf("applyWindowBrightness(BrightnessPolicy.FOLLOW_SYSTEM);", release) > release);
        for (String activity : new String[]{"VideoActivity", "LiveActivity"}) {
            String source = readMobileSource("ui/activity/" + activity + ".java");
            assertTrue(activity + " must release the gesture controller on destroy",
                    source.contains("if (mKeyDown != null) mKeyDown.release();"));
        }
    }

    private static String readMainSource(String file) throws Exception {
        return readSource("main", file);
    }

    private static String readMobileSource(String file) throws Exception {
        return readSource("mobile", file);
    }

    private static String readSource(String sourceSet, String file) throws Exception {
        Path app = Files.exists(Path.of("src", "main")) ? Path.of(".") : Path.of("app");
        Path path = app.resolve(Path.of("src", sourceSet, "java", "com", "fongmi", "android", "tv", file));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
