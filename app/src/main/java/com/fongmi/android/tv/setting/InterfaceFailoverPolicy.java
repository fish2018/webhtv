package com.fongmi.android.tv.setting;

public final class InterfaceFailoverPolicy {

    public static final int OFF = 0;
    public static final int AUTO = 1;
    public static final int CONFIRM = 2;
    public static final int DEFAULT_MODE = AUTO;
    public static final int MAX_ATTEMPTS = 3;

    private InterfaceFailoverPolicy() {
    }

    public static int clampMode(int mode) {
        if (mode < OFF || mode > CONFIRM) return DEFAULT_MODE;
        return mode;
    }

    public static boolean isAuto(int mode) {
        return clampMode(mode) == AUTO;
    }

    public static boolean isConfirm(int mode) {
        return clampMode(mode) == CONFIRM;
    }

    public static boolean shouldFailover(int mode) {
        return isAuto(mode) || isConfirm(mode);
    }

    public static int attemptLimit(int candidateCount) {
        if (candidateCount < 0) candidateCount = 0;
        return Math.min(candidateCount, MAX_ATTEMPTS);
    }

    public static int fallbackLimit(int candidateCount) {
        return Math.max(0, attemptLimit(candidateCount) - 1);
    }
}
