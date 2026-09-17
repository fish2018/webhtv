package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoBackupPolicyTest {

    @Test
    public void runsOnlyForAnExplicitFinishWithPermission() {
        assertTrue(AutoBackupPolicy.shouldRun(true, true, true, false));
        assertFalse(AutoBackupPolicy.shouldRun(false, true, true, false));
        assertFalse(AutoBackupPolicy.shouldRun(true, false, true, false));
        assertFalse(AutoBackupPolicy.shouldRun(true, true, false, false));
        assertFalse(AutoBackupPolicy.shouldRun(true, true, true, true));
    }

    @Test
    public void effectiveStateRequiresPreferenceAndPermission() {
        assertTrue(AutoBackupPolicy.isEffective(true, true));
        assertFalse(AutoBackupPolicy.isEffective(true, false));
        assertFalse(AutoBackupPolicy.isEffective(false, true));
    }
}
