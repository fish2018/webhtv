package com.fongmi.android.tv.player.mpv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MpvVulkanBackendPolicyTest {

    @Test
    public void automaticModeUsesRememberedStableFallback() {
        assertEquals("auto", MpvVulkanBackendPolicy.resolve("", false));
        assertEquals("stable", MpvVulkanBackendPolicy.resolve("auto", true));
    }

    @Test
    public void explicitUserBackendAlwaysWins() {
        assertEquals("direct", MpvVulkanBackendPolicy.resolve("direct", true));
        assertEquals("legacy", MpvVulkanBackendPolicy.resolve("legacy", true));
        assertEquals("fragment", MpvVulkanBackendPolicy.resolve("fragment", true));
    }

    @Test
    public void selectedPriorityControlsConflictingBackend() {
        assertEquals("legacy", MpvVulkanBackendPolicy.resolveConfigured(
                "legacy", "direct", true));
        assertEquals("direct", MpvVulkanBackendPolicy.resolveConfigured(
                "legacy", "direct", false));
        assertEquals("fragment", MpvVulkanBackendPolicy.resolveConfigured(
                "", "fragment", true));
        assertEquals("legacy", MpvVulkanBackendPolicy.resolveConfigured(
                "legacy", "", false));
    }
}
