package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvHardwarePolicyTest {

    @Test
    public void blocksKnownHiSiliconAndKirinDevices() {
        assertTrue(MpvHardwarePolicy.blocksZeroCopy("HUAWEI", "kirin9000", "hisilicon"));
        assertTrue(MpvHardwarePolicy.blocksZeroCopy("HONOR", "kirin980", ""));
        assertTrue(MpvHardwarePolicy.blocksZeroCopy("HUAWEI", "hi3660", ""));
        assertTrue(MpvHardwarePolicy.blocksZeroCopy("HUAWEI", "unknown", ""));
        assertTrue(MpvHardwarePolicy.blocksZeroCopy("", "unknown", "HiSilicon Technologies"));
    }

    @Test
    public void preservesZeroCopyOnUnrelatedHardware() {
        assertFalse(MpvHardwarePolicy.blocksZeroCopy("Google", "tensor", "Google"));
        assertFalse(MpvHardwarePolicy.blocksZeroCopy("Samsung", "exynos", "Samsung"));
        assertFalse(MpvHardwarePolicy.blocksZeroCopy("HUAWEI", "qcom", "Qualcomm"));
        assertFalse(MpvHardwarePolicy.blocksZeroCopy("HONOR", "mt6877", "MediaTek"));
        assertFalse(MpvHardwarePolicy.blocksZeroCopy(null, null, null));
    }

    @Test
    public void blocksAndroidEmulatorsFromZeroCopy() {
        assertTrue(MpvHardwarePolicy.blocksZeroCopy(
                "Google", "ranchu", "", "google/sdk_gphone64_arm64/emulator", "sdk_gphone64_arm64"));
        assertTrue(MpvHardwarePolicy.blocksZeroCopy(
                "unknown", "goldfish", "", "generic/sdk/generic", "Android SDK built for x86"));
    }

    @Test
    public void blocksArmTranslationOnDisguisedX86Emulators() {
        assertTrue(MpvHardwarePolicy.blocksZeroCopy(
                "HUAWEI",
                "qcom",
                "Qualcomm",
                "Android/aosp_marlin/marlin:9/release-keys",
                "LIO-AN00",
                new String[] {"x86_64", "x86", "arm64-v8a"}));
    }

    @Test
    public void preservesZeroCopyForPhysicalDevicesWithGenericProductText() {
        assertFalse(MpvHardwarePolicy.blocksZeroCopy(
                "Google", "tensor", "Google", "google/panther/panther", "Pixel 7"));
        assertFalse(MpvHardwarePolicy.blocksZeroCopy(
                "Samsung", "exynos", "Samsung", "samsung/generic_device/release", "SM-S9180"));
    }
}
