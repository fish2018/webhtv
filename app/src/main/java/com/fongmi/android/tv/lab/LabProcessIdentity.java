package com.fongmi.android.tv.lab;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class LabProcessIdentity {

    private static final int UNKNOWN = -1;

    private LabProcessIdentity() {
    }

    static boolean pidMatches(File procRoot, int pid, long expectedStart, int uid) {
        if (pid <= 0) return false;
        File dir = new File(procRoot, String.valueOf(pid));
        if (!dir.exists() || uid != uidOf(dir)) return false;

        long actualStart = startTime(dir);
        return expectedStart > 0 && actualStart > 0 && actualStart == expectedStart;
    }

    static int[] groupPids(File procRoot, int pgid, int uid) {
        if (pgid <= 0) return new int[0];
        File[] dirs = procRoot.listFiles();
        if (dirs == null) return new int[0];

        int[] result = new int[dirs.length];
        int count = 0;
        for (File dir : dirs) {
            if (!isNumericDir(dir) || uid != uidOf(dir) || pgrpOf(dir) != pgid) continue;
            try {
                result[count++] = Integer.parseInt(dir.getName());
            } catch (NumberFormatException ignored) {
            }
        }
        return count == result.length ? result : java.util.Arrays.copyOf(result, count);
    }

    static long startTime(File procRoot, int pid) {
        return startTime(new File(procRoot, String.valueOf(pid)));
    }

    private static long startTime(File dir) {
        String[] fields = statFields(dir);
        return fields.length > 19 ? parse(fields[19]) : UNKNOWN;
    }

    private static int pgrpOf(File dir) {
        String[] fields = statFields(dir);
        return fields.length > 2 ? (int) parse(fields[2]) : UNKNOWN;
    }

    private static String[] statFields(File dir) {
        try {
            String stat = new String(Files.readAllBytes(new File(dir, "stat").toPath()), StandardCharsets.UTF_8);
            int end = stat.lastIndexOf(')');
            if (end < 0 || end + 2 >= stat.length()) return new String[0];
            return stat.substring(end + 2).trim().split("\\s+");
        } catch (Exception ignored) {
            return new String[0];
        }
    }

    private static int uidOf(File dir) {
        try {
            for (String line : Files.readAllLines(new File(dir, "status").toPath(), StandardCharsets.UTF_8)) {
                if (!line.startsWith("Uid:")) continue;
                String[] fields = line.substring(4).trim().split("\\s+");
                return fields.length > 0 ? (int) parse(fields[0]) : UNKNOWN;
            }
        } catch (Exception ignored) {
        }
        return UNKNOWN;
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return UNKNOWN;
        }
    }

    private static boolean isNumericDir(File dir) {
        String name = dir.getName();
        if (name.isEmpty()) return false;
        for (int i = 0; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) return false;
        }
        return true;
    }
}
