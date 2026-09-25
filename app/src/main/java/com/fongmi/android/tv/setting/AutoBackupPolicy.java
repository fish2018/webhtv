package com.fongmi.android.tv.setting;

public final class AutoBackupPolicy {

    private AutoBackupPolicy() {
    }

    public static boolean isEffective(boolean enabled, boolean hasFileAccess) {
        return enabled && hasFileAccess;
    }

    public static boolean shouldRun(boolean enabled, boolean hasFileAccess, boolean finishing, boolean changingConfigurations) {
        return finishing && !changingConfigurations && isEffective(enabled, hasFileAccess);
    }
}
