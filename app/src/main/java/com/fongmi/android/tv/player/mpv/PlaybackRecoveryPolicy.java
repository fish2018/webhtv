package com.fongmi.android.tv.player.mpv;

/** Pure decisions shared by the watchdog and the separate recovery process. */
public final class PlaybackRecoveryPolicy {
    public static final long STALL_MS = 8000;

    private PlaybackRecoveryPolicy() {}

    public record Target(int pid, int uid, long startTicks, String processName) {}

    public static boolean shouldPrompt(long nowMs, long heartbeatPostedMs,
                                       boolean foreground, boolean alreadyPrompted) {
        return foreground && !alreadyPrompted && heartbeatPostedMs > 0
                && nowMs >= heartbeatPostedMs && nowMs - heartbeatPostedMs >= STALL_MS;
    }

    public static boolean mayTerminate(Target expected, Target actual, int selfPid,
                                       int selfUid, String mainProcessName) {
        return expected != null && actual != null && expected.equals(actual)
                && expected.pid() > 0 && expected.pid() != selfPid
                && expected.uid() == selfUid && expected.startTicks() > 0
                && mainProcessName != null && mainProcessName.equals(expected.processName());
    }

    /** /proc/PID ownership may be root for non-dumpable release processes. */
    public static int processUid(String status) {
        if (status == null) return -1;
        int uid = -1;
        for (String line : status.split("\n")) {
            if (!line.startsWith("Uid:")) continue;
            if (uid >= 0) return -1; // ambiguous or malformed status
            String[] fields = line.substring(4).trim().split("\\s+");
            if (fields.length != 4) return -1;
            try {
                for (String field : fields) {
                    if (!field.matches("[0-9]+") || Integer.parseInt(field) < 0) return -1;
                }
                uid = Integer.parseInt(fields[0]); // real UID, like Android Process
            } catch (NumberFormatException error) {
                return -1;
            }
        }
        return uid;
    }

    public static long processStartTicks(String stat) {
        if (stat == null) return -1;
        int endName = stat.lastIndexOf(')');
        if (endName < 0) return -1;
        String[] fields = stat.substring(endName + 1).trim().split("\\s+");
        if (fields.length <= 19) return -1;
        try {
            long value = Long.parseLong(fields[19]); // /proc/PID/stat field 22
            return value > 0 ? value : -1;
        } catch (NumberFormatException error) {
            return -1;
        }
    }
}
