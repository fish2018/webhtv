package com.fongmi.android.tv.setting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MpvOutputModePolicyTest {

    @Test
    public void directModeRequiresHardwareDecodeOnSupportedDevice() {
        assertTrue(MpvPerformanceSetting.resolveSurfaceDirect(MpvPerformanceSetting.OUTPUT_SURFACE_DIRECT, false, true, true, false));
        assertFalse(MpvPerformanceSetting.resolveSurfaceDirect(MpvPerformanceSetting.OUTPUT_SURFACE_DIRECT, true, true, false, false));
    }

    @Test
    public void knownBadDeviceBlocksExplicitSurfaceDirect() {
        assertFalse(MpvPerformanceSetting.resolveSurfaceDirect(MpvPerformanceSetting.OUTPUT_SURFACE_DIRECT, true, true, true, true));
    }

    @Test
    public void gpuModeNeverUsesSurfaceDirect() {
        assertFalse(MpvPerformanceSetting.resolveSurfaceDirect(MpvPerformanceSetting.OUTPUT_GPU, true, true, true, false));
    }

    @Test
    public void autoModeStaysOnGpuDuringSurfaceStabilityGuard() {
        assertFalse(MpvPerformanceSetting.resolveSurfaceDirect(MpvPerformanceSetting.OUTPUT_AUTO, true, true, true, false));
        assertFalse(MpvPerformanceSetting.resolveSurfaceDirect(MpvPerformanceSetting.OUTPUT_AUTO, false, true, true, false));
        assertFalse(MpvPerformanceSetting.resolveSurfaceDirect(MpvPerformanceSetting.OUTPUT_AUTO, true, false, true, false));
    }

    @Test
    public void zeroCopyGuardOnlyAffectsKnownBadDevices() {
        assertEquals("mediacodec-copy", MpvPerformanceSetting.resolveHwdecOption(MpvPerformanceSetting.HWDEC_AUTO, true));
        assertEquals("mediacodec-copy", MpvPerformanceSetting.resolveHwdecOption(MpvPerformanceSetting.HWDEC_DIRECT, true));
        assertEquals("mediacodec,mediacodec-copy", MpvPerformanceSetting.resolveHwdecOption(MpvPerformanceSetting.HWDEC_AUTO, false));
        assertEquals("mediacodec", MpvPerformanceSetting.resolveHwdecOption(MpvPerformanceSetting.HWDEC_DIRECT, false));
        assertEquals("mediacodec-copy", MpvPerformanceSetting.resolveHwdecOption(MpvPerformanceSetting.HWDEC_COPY, false));
    }

    @Test
    public void protectedDeviceTextReportsEffectiveCopyMode() {
        assertEquals("自动（设备保护：兼容复制）", MpvPerformanceSetting.resolveHwdecText(MpvPerformanceSetting.HWDEC_AUTO, true));
        assertEquals("零拷贝（设备保护：兼容复制）", MpvPerformanceSetting.resolveHwdecText(MpvPerformanceSetting.HWDEC_DIRECT, true));
    }

    @Test
    public void frameRateModePreservesUserSelection() {
        assertEquals(MpvPerformanceSetting.FRAME_RATE_SEAMLESS, MpvPerformanceSetting.resolveFrameRateMode(MpvPerformanceSetting.FRAME_RATE_SEAMLESS));
        assertEquals(MpvPerformanceSetting.FRAME_RATE_OFF, MpvPerformanceSetting.resolveFrameRateMode(MpvPerformanceSetting.FRAME_RATE_OFF));
    }
}