package com.fongmi.android.tv.setting;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MpvVulkanBackendSettingTest {

    @Test
    public void mapsUiModesToNativeBackends() {
        assertEquals("legacy", MpvPerformanceSetting.vulkanBackendOption(-1));
        assertEquals("legacy", MpvPerformanceSetting.vulkanBackendOption(
                MpvPerformanceSetting.VULKAN_BACKEND_AUTO));
        assertEquals("direct", MpvPerformanceSetting.vulkanBackendOption(
                MpvPerformanceSetting.VULKAN_BACKEND_DIRECT));
        assertEquals("legacy", MpvPerformanceSetting.vulkanBackendOption(
                MpvPerformanceSetting.VULKAN_BACKEND_LEGACY));
        assertEquals("stable", MpvPerformanceSetting.vulkanBackendOption(
                MpvPerformanceSetting.VULKAN_BACKEND_STABLE));
        assertEquals("legacy", MpvPerformanceSetting.vulkanBackendOption(99));
    }
}
