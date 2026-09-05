package com.fongmi.android.tv.setting;

import android.os.Build;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StoragePermissionPolicyTest {

    @Test
    public void preAndroidQRequiresReadAndWrite() {
        assertTrue(Setting.hasLegacyFileAccess(Build.VERSION_CODES.P, Build.VERSION_CODES.P, true, true, false));
        assertFalse(Setting.hasLegacyFileAccess(Build.VERSION_CODES.P, Build.VERSION_CODES.P, true, false, false));
    }

    @Test
    public void androidQAcceptsWriteOrLegacyStorage() {
        assertTrue(Setting.hasLegacyFileAccess(Build.VERSION_CODES.Q, Build.VERSION_CODES.P, true, true, false));
        assertTrue(Setting.hasLegacyFileAccess(Build.VERSION_CODES.Q, Build.VERSION_CODES.P, true, false, true));
        assertFalse(Setting.hasLegacyFileAccess(Build.VERSION_CODES.Q, Build.VERSION_CODES.P, true, false, false));
    }

    @Test
    public void androidRLegacyAccessDependsOnTargetSdkAndReadPermission() {
        assertTrue(Setting.hasLegacyFileAccess(Build.VERSION_CODES.R, Build.VERSION_CODES.P, true, false, true));
        assertFalse(Setting.hasLegacyFileAccess(Build.VERSION_CODES.R, Build.VERSION_CODES.R, true, true, true));
        assertFalse(Setting.hasLegacyFileAccess(Build.VERSION_CODES.R, Build.VERSION_CODES.P, false, true, true));
    }
}
