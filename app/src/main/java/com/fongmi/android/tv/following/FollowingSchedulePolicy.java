package com.fongmi.android.tv.following;

import java.util.concurrent.TimeUnit;

public final class FollowingSchedulePolicy {

    public static final long PERIODIC_INTERVAL = TimeUnit.HOURS.toMillis(6);
    public static final long FOREGROUND_INTERVAL = TimeUnit.MINUTES.toMillis(15);
    public static final int BACKGROUND_BATCH_SIZE = 5;
    public static final int FOREGROUND_BATCH_SIZE = 20;
    public static final long PERIODIC_FLEX = TimeUnit.HOURS.toMillis(1);
    public static final long MIN_ONE_SHOT_DELAY = TimeUnit.MINUTES.toMillis(15);
    public static final long MIN_ONE_SHOT_AFTER_CHECK = TimeUnit.HOURS.toMillis(1);
    public static final long NEXT_AIR_GRACE = TimeUnit.MINUTES.toMillis(30);

    private FollowingSchedulePolicy() {
    }

    public static long nextCheckAt(long now, String status, long nextAirAt) {
        return nextCheckAt(now, status, nextAirAt, false);
    }

    public static long nextCheckAt(long now, String status, long nextAirAt, boolean foreground) {
        String normalized = FollowingMetadataSnapshot.normalizeStatus(status);
        long nextAirCheckAt = nextAirCheckAt(now, nextAirAt);
        if (FollowingMetadataSnapshot.RETURNING.equals(normalized)) {
            long interval = now + (foreground ? FOREGROUND_INTERVAL : PERIODIC_INTERVAL);
            return nextAirCheckAt > 0 ? Math.min(interval, nextAirCheckAt) : interval;
        }
        if (FollowingMetadataSnapshot.PLANNED.equals(normalized)) {
            long daily = now + TimeUnit.HOURS.toMillis(24);
            return nextAirCheckAt > 0 ? Math.min(daily, nextAirCheckAt) : daily;
        }
        if (FollowingMetadataSnapshot.ENDED.equals(normalized) || FollowingMetadataSnapshot.CANCELED.equals(normalized)) {
            return now + TimeUnit.DAYS.toMillis(7);
        }
        return now + (foreground ? FOREGROUND_INTERVAL : PERIODIC_INTERVAL);
    }

    private static long nextAirCheckAt(long now, long nextAirAt) {
        if (nextAirAt <= 0) return 0;
        if (nextAirAt <= now) return now + MIN_ONE_SHOT_AFTER_CHECK;
        return Math.max(now + MIN_ONE_SHOT_AFTER_CHECK, nextAirAt + NEXT_AIR_GRACE);
    }

    public static long backoffAt(long now, int failureCount) {
        long delay = switch (Math.max(1, failureCount)) {
            case 1 -> TimeUnit.MINUTES.toMillis(15);
            case 2 -> TimeUnit.MINUTES.toMillis(30);
            case 3 -> TimeUnit.HOURS.toMillis(1);
            case 4 -> TimeUnit.HOURS.toMillis(2);
            case 5 -> TimeUnit.HOURS.toMillis(6);
            default -> TimeUnit.DAYS.toMillis(1);
        };
        return now + delay;
    }

    public static boolean isPermanentFailure(Throwable error) {
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        String lower = message.toLowerCase();
        return lower.contains("401") || lower.contains("403") || lower.contains("未配置")
                || lower.contains("配置缺失") || lower.contains("api key")
                || lower.contains("access token");
    }
}
