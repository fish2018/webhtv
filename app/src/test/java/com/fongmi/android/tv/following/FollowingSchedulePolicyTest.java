package com.fongmi.android.tv.following;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FollowingSchedulePolicyTest {

    @Test
    public void nextAirUsesBoundedOneShotInsteadOfExactAlarm() {
        long now = 1_000_000;
        long nextAir = now + TimeUnit.HOURS.toMillis(3);
        long check = FollowingSchedulePolicy.nextCheckAt(now, FollowingMetadataSnapshot.RETURNING, nextAir);
        assertEquals(nextAir + FollowingSchedulePolicy.NEXT_AIR_GRACE, check);
    }

    @Test
    public void justCompletedPastAirDateWaitsAtLeastOneHour() {
        long now = 1_000_000;
        long nextAir = now - TimeUnit.MINUTES.toMillis(1);
        long check = FollowingSchedulePolicy.nextCheckAt(now, FollowingMetadataSnapshot.RETURNING, nextAir);
        assertEquals(now + FollowingSchedulePolicy.MIN_ONE_SHOT_AFTER_CHECK, check);
    }

    @Test
    public void plannedShowStillChecksAtLeastDailyBeforeAirDate() {
        long now = 1_000_000;
        long nextAir = now + TimeUnit.DAYS.toMillis(10);
        long check = FollowingSchedulePolicy.nextCheckAt(now, FollowingMetadataSnapshot.PLANNED, nextAir);
        assertEquals(now + TimeUnit.HOURS.toMillis(24), check);
    }

    @Test
    public void foregroundUsesShortIntervalWithoutChangingEndedAndPlannedPolicy() {
        long now = 1_000_000;
        assertEquals(now + FollowingSchedulePolicy.FOREGROUND_INTERVAL,
                FollowingSchedulePolicy.nextCheckAt(now, FollowingMetadataSnapshot.RETURNING, 0, true));
        assertEquals(now + FollowingSchedulePolicy.FOREGROUND_INTERVAL,
                FollowingSchedulePolicy.nextCheckAt(now, FollowingMetadataSnapshot.UNKNOWN, 0, true));
        assertEquals(now + TimeUnit.HOURS.toMillis(24),
                FollowingSchedulePolicy.nextCheckAt(now, FollowingMetadataSnapshot.PLANNED, 0, true));
        assertEquals(now + TimeUnit.DAYS.toMillis(7),
                FollowingSchedulePolicy.nextCheckAt(now, FollowingMetadataSnapshot.ENDED, 0, true));
    }

    @Test
    public void foregroundReturningWithNextAirStillChecksWithinCadence() {
        long now = 1_000_000;
        long check = FollowingSchedulePolicy.nextCheckAt(now, FollowingMetadataSnapshot.RETURNING,
                now + TimeUnit.DAYS.toMillis(3), true);
        assertEquals(now + FollowingSchedulePolicy.FOREGROUND_INTERVAL, check);
    }

    @Test
    public void failureBackoffGrowsAndCapsAtOneDay() {
        assertEquals(TimeUnit.MINUTES.toMillis(15), FollowingSchedulePolicy.backoffAt(0, 1));
        assertEquals(TimeUnit.MINUTES.toMillis(30), FollowingSchedulePolicy.backoffAt(0, 2));
        assertEquals(TimeUnit.HOURS.toMillis(1), FollowingSchedulePolicy.backoffAt(0, 3));
        assertTrue(FollowingSchedulePolicy.backoffAt(0, 99) >= TimeUnit.DAYS.toMillis(1));
    }
}
