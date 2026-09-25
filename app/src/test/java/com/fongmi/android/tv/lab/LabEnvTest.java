package com.fongmi.android.tv.lab;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class LabEnvTest {

    @Test
    public void packagedAbiWinsOverDeviceAbiOrder() {
        assertEquals(
                "arm64-v8a",
                LabEnv.resolveArch("arm64_v8a", new String[]{"armeabi-v7a", "arm64-v8a"}));
        assertEquals(
                "armeabi-v7a",
                LabEnv.resolveArch("armeabi_v7a", new String[]{"arm64-v8a", "armeabi-v7a"}));
    }

    @Test
    public void fallsBackToSupportedArmAbiOnlyWhenPackagedAbiIsUnknown() {
        assertEquals(
                "armeabi-v7a",
                LabEnv.resolveArch("", new String[]{"x86_64", "armeabi-v7a"}));
    }

    @Test
    public void unknownArchitectureDoesNotDefaultToArm64() {
        assertEquals("", LabEnv.resolveArch("x86_64", new String[]{"x86_64", "x86"}));
        assertEquals("", LabEnv.resolveArch("x86_64", new String[]{"arm64-v8a", "armeabi-v7a"}));
        assertEquals("", LabEnv.resolveArch("unknown", new String[]{"arm64-v8a"}));
        assertEquals("", LabEnv.resolveArch(null, null));
    }

    @Test
    public void downloadMustMatchSelectedAbi() {
        LabModels.Item item = new LabModels.Item();
        LabModels.Download arm64 = new LabModels.Download();
        arm64.arch = "arm64-v8a";
        LabModels.Download armv7 = new LabModels.Download();
        armv7.arch = "armeabi-v7a";
        item.downloads = Arrays.asList(arm64, armv7);

        assertSame(armv7, LabEnv.findDownload(item, "armeabi_v7a"));
        assertSame(arm64, LabEnv.findDownload(item, "arm64-v8a"));
        assertNull(LabEnv.findDownload(item, "x86_64"));
    }

    @Test
    public void missingAbiDoesNotFallBackToFirstDownload() {
        LabModels.Item item = new LabModels.Item();
        LabModels.Download arm64 = new LabModels.Download();
        arm64.arch = "arm64-v8a";
        item.downloads = Arrays.asList(arm64);

        assertNull(LabEnv.findDownload(item, "armeabi-v7a"));
    }

    @Test
    public void selectedDownloadUsesNormalizedAbiForVersion() {
        LabModels.Item item = new LabModels.Item();
        item.version = "fallback";
        LabModels.Download arm64 = new LabModels.Download();
        String selectedArch = LabEnv.arch();
        assertTrue(selectedArch.equals("arm64-v8a") || selectedArch.equals("armeabi-v7a"));
        arm64.arch = selectedArch.replace('-', '_');
        arm64.version = "3.12";
        item.downloads = Arrays.asList(arm64);

        assertSame(arm64, LabEnv.findDownload(item, selectedArch));
        assertEquals("3.12", LabEnv.displayVersion(item));
    }
}
