package com.fongmi.android.tv.lab;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LabProcessIdentityTest {

    private static final int UID = 10095;

    @Test
    public void acceptsTrackedPidByStartTimeEvenWhenProcessGroupChanged() throws Exception {
        Path proc = Files.createTempDirectory("lab-proc");
        process(proc, 123, 999, 4321, UID);

        assertTrue(LabProcessIdentity.pidMatches(proc.toFile(), 123, 4321, UID));
        assertFalse(LabProcessIdentity.pidMatches(proc.toFile(), 123, 4322, UID));
    }

    @Test
    public void acceptsTrackedGroupMemberAfterWrapperExit() throws Exception {
        Path proc = Files.createTempDirectory("lab-proc");
        process(proc, 123, 123, 100, UID);
        process(proc, 456, 123, 200, UID);

        int[] members = LabProcessIdentity.groupPids(proc.toFile(), 123, UID);
        org.junit.Assert.assertEquals(2, members.length);
        assertTrue(contains(members, 123));
        assertTrue(contains(members, 456));
        assertTrue(LabProcessIdentity.pidMatches(proc.toFile(), 456, 200, UID));
    }

    @Test
    public void rejectsReusedPidOrForeignGroupMember() throws Exception {
        Path proc = Files.createTempDirectory("lab-proc");
        process(proc, 123, 123, 100, UID);
        process(proc, 456, 123, 200, 10096);

        assertFalse(LabProcessIdentity.pidMatches(proc.toFile(), 123, 101, UID));
        assertFalse(contains(LabProcessIdentity.groupPids(proc.toFile(), 123, UID), 456));
    }

    private static void process(Path proc, int pid, int pgid, long startTime, int uid) throws Exception {
        Path dir = proc.resolve(String.valueOf(pid));
        Files.createDirectories(dir);
        String[] statFields = new String[20];
        Arrays.fill(statFields, "0");
        statFields[0] = "S";
        statFields[1] = "1";
        statFields[2] = String.valueOf(pgid);
        statFields[19] = String.valueOf(startTime);
        Files.write(dir.resolve("stat"), (pid + " (sh) " + String.join(" ", statFields))
                .getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("status"), ("Name: sh\nUid:\t" + uid + "\t" + uid + "\t" + uid + "\t" + uid + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static boolean contains(int[] values, int expected) {
        for (int value : values) {
            if (value == expected) return true;
        }
        return false;
    }
}
