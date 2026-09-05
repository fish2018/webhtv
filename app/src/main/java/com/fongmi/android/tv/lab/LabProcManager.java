package com.fongmi.android.tv.lab;

import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LabProcManager {

    private static final Gson GSON = new Gson();
    private static final Map<String, Integer> RECOVERED = new ConcurrentHashMap<>();
    private static final Map<String, Long> STARTS = new ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, Long>> MEMBERS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> GROUPS = new ConcurrentHashMap<>();
    private static final Map<String, File> LOGS = new ConcurrentHashMap<>();
    private static volatile boolean recovered;

    private LabProcManager() {
    }

    public static File pidsDir() {
        File dir = new File(LabConfig.get().getRoot(), "pids");
        dir.mkdirs();
        return dir;
    }

    public static File logsDir() {
        File dir = new File(LabConfig.get().getRoot(), "logs");
        dir.mkdirs();
        return dir;
    }

    public static File logFile(int pid, String pkg) {
        return new File(logsDir(), (pid > 0 ? pid : 0) + "_" + pkg + ".log");
    }

    private static File stateFile() {
        return new File(App.get().getFilesDir(), "lab_process_state.json");
    }

    private static File pidFile(String key) {
        return new File(pidsDir(), key.replace('/', '_') + ".pid");
    }

    public static synchronized void recover() {
        if (recovered) return;
        recovered = true;
        try {
            Map<String, JsonObject> state = loadState();
            List<String> dead = new ArrayList<>();
            for (Map.Entry<String, JsonObject> entry : state.entrySet()) {
                try {
                    int pid = entry.getValue().get("pid").getAsInt();
                    int pgid = entry.getValue().has("pgid") ? entry.getValue().get("pgid").getAsInt() : pid;
                    boolean isolated = !entry.getValue().has("isolated") || entry.getValue().get("isolated").getAsBoolean();
                    long start = entry.getValue().has("start") ? entry.getValue().get("start").getAsLong() : 0;
                    Map<Integer, Long> members = parseMembers(entry.getValue());
                    if (isTrackedAlive(pid, start, members)) {
                        RECOVERED.put(entry.getKey(), pid);
                        STARTS.put(entry.getKey(), start);
                        MEMBERS.put(entry.getKey(), members);
                        if (isolated) GROUPS.put(entry.getKey(), pgid);
                        JsonObject entryState = entry.getValue();
                        if (entryState.has("log")) {
                            LOGS.put(entry.getKey(), new File(entryState.get("log").getAsString()));
                        } else if (entryState.has("pkg")) {
                            LOGS.put(entry.getKey(), logFile(pid, entryState.get("pkg").getAsString()));
                        }
                    } else {
                        dead.add(entry.getKey());
                    }
                } catch (Exception e) {
                    dead.add(entry.getKey());
                }
            }
            for (String key : dead) state.remove(key);
            saveState(state);
        } catch (Exception ignored) {
        }
    }

    public static synchronized void track(String key, int pid, String pkg, String cmd) {
        // LabRunner 始终用 setsid 启动，wrapper 进程 pid 就是新进程组 id；
        // wrapper 可能很快退出，不能依赖读取时的瞬时 pgrp 值。
        int pgid = pid;
        boolean isolated = true;
        long start = LabProcessIdentity.startTime(new File("/proc"), pid);
        try {
            File log = logFile(pid, pkg);
            File pf = pidFile(key);
            pf.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(pf)) {
                writer.write(String.valueOf(pid));
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("pid", pid);
            entry.addProperty("start", start);
            entry.addProperty("pgid", pgid > 0 ? pgid : pid);
            entry.addProperty("isolated", isolated);
            entry.addProperty("pkg", pkg);
            entry.addProperty("cmd", cmd);
            entry.addProperty("log", log.getAbsolutePath());
            Map<String, JsonObject> state = loadState();
            state.put(key, entry);
            saveState(state);
            RECOVERED.remove(key);
            STARTS.put(key, start);
            Map<Integer, Long> members = new LinkedHashMap<>();
            members.put(pid, start);
            MEMBERS.put(key, members);
            if (isolated) GROUPS.put(key, pgid > 0 ? pgid : pid);
            else GROUPS.remove(key);
            LOGS.put(key, log);
        } catch (Exception ignored) {
        }
    }

    public static synchronized void untrack(String key) {
        try {
            pidFile(key).delete();
        } catch (Exception ignored) {
        }
        Map<String, JsonObject> state = loadState();
        state.remove(key);
        saveState(state);
        RECOVERED.remove(key);
        STARTS.remove(key);
        MEMBERS.remove(key);
        GROUPS.remove(key);
        LOGS.remove(key);
    }

    public static File logFor(String key) {
        return LOGS.get(key);
    }

    public static synchronized boolean trackGroup(String key, int pgid) {
        int uid = android.os.Process.myUid();
        int[] pids = LabProcessIdentity.groupPids(new File("/proc"), pgid, uid);
        if (key == null || pids.length == 0) return false;

        Map<String, JsonObject> state = loadState();
        JsonObject entry = state.get(key);
        if (entry == null) return false;

        Map<Integer, Long> members = new LinkedHashMap<>();
        for (int pid : pids) members.put(pid, LabProcessIdentity.startTime(new File("/proc"), pid));
        entry.add("members", GSON.toJsonTree(members, new TypeToken<Map<Integer, Long>>() {
        }.getType()));
        saveState(state);
        MEMBERS.put(key, members);
        return true;
    }

    private static boolean isTrackedAlive(int pid, long start, Map<Integer, Long> members) {
        int uid = android.os.Process.myUid();
        File proc = new File("/proc");
        if (LabProcessIdentity.pidMatches(proc, pid, start, uid)) return true;
        for (Map.Entry<Integer, Long> member : members.entrySet()) {
            if (LabProcessIdentity.pidMatches(proc, member.getKey(), member.getValue(), uid)) return true;
        }
        return false;
    }

    private static Map<Integer, Long> parseMembers(JsonObject entry) {
        if (!entry.has("members")) return new LinkedHashMap<>();
        Map<Integer, Long> members = GSON.fromJson(
                entry.get("members"), new TypeToken<Map<Integer, Long>>() {
                }.getType());
        return members == null ? new LinkedHashMap<>() : members;
    }

    public static boolean recoveredAlive(String key) {
        Integer pid = RECOVERED.get(key);
        Long start = STARTS.get(key);
        int uid = android.os.Process.myUid();
        if (pid != null && LabProcessIdentity.pidMatches(new File("/proc"), pid, start == null ? 0 : start, uid)) {
            return true;
        }
        Map<Integer, Long> members = MEMBERS.get(key);
        if (members != null) {
            for (Map.Entry<Integer, Long> member : members.entrySet()) {
                if (LabProcessIdentity.pidMatches(new File("/proc"), member.getKey(), member.getValue(), uid)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int recoveredCount() {
        int count = 0;
        for (String key : trackedKeys()) {
            if (recoveredAlive(key)) count++;
        }
        return count;
    }

    public static List<String> recoveredKeys() {
        List<String> keys = new ArrayList<>();
        for (String key : trackedKeys()) {
            if (recoveredAlive(key)) keys.add(key);
        }
        return keys;
    }

    private static List<String> trackedKeys() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        keys.addAll(RECOVERED.keySet());
        keys.addAll(STARTS.keySet());
        keys.addAll(MEMBERS.keySet());
        keys.addAll(GROUPS.keySet());
        return new ArrayList<>(keys);
    }

    public static synchronized void stop(String key) {
        Integer pid = RECOVERED.remove(key);
        Integer pgid = GROUPS.remove(key);
        if (pid != null && pid > 0) killTree(pid);
        if (pgid != null && pgid > 0 && (pid == null || pid != pgid)) {
            try {
                Runtime.getRuntime().exec(new String[]{"kill", "-9", "-" + String.valueOf(pgid)}).waitFor();
            } catch (Exception ignored) {
            }
        }
        untrack(key);
    }

    public static synchronized void stopAll() {
        for (String key : trackedKeys()) stop(key);
    }

    public static void killTree(int pid) {
        if (pid <= 0) return;
        List<Integer> descendants = new ArrayList<>();
        collectDescendants(pid, descendants);
        for (int i = descendants.size() - 1; i >= 0; i--) killOne(descendants.get(i));
        killOne(pid);
        try {
            Runtime.getRuntime().exec(new String[]{"kill", "-9", "-" + String.valueOf(pid)}).waitFor();
        } catch (Exception ignored) {
        }
    }

    private static void collectDescendants(int pid, List<Integer> out) {
        File[] dirs = new File("/proc").listFiles();
        if (dirs == null) return;
        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            String name = dir.getName();
            boolean numeric = true;
            for (int i = 0; i < name.length(); i++) {
                if (!Character.isDigit(name.charAt(i))) {
                    numeric = false;
                    break;
                }
            }
            if (!numeric) continue;
            try {
                int child = Integer.parseInt(name);
                if (parentPid(child) == pid) {
                    out.add(child);
                    collectDescendants(child, out);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static int parentPid(int pid) {
        try {
            String stat = new String(java.nio.file.Files.readAllBytes(new File("/proc/" + pid + "/stat").toPath()), java.nio.charset.StandardCharsets.UTF_8);
            int end = stat.lastIndexOf(')');
            if (end < 0 || end + 2 >= stat.length()) return -1;
            String[] fields = stat.substring(end + 2).trim().split("\\s+");
            if (fields.length < 2) return -1;
            return Integer.parseInt(fields[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    private static int pgidOf(int pid) {
        return pgrpOf(pid);
    }

    private static int pgrpOf(int pid) {
        try {
            String stat = new String(java.nio.file.Files.readAllBytes(new File("/proc/" + pid + "/stat").toPath()), java.nio.charset.StandardCharsets.UTF_8);
            int end = stat.lastIndexOf(')');
            if (end < 0 || end + 2 >= stat.length()) return -1;
            String[] fields = stat.substring(end + 2).trim().split("\\s+");
            if (fields.length < 3) return -1;
            return Integer.parseInt(fields[2]);
        } catch (Exception e) {
            return -1;
        }
    }

    private static void killOne(int pid) {
        try {
            Runtime.getRuntime().exec(new String[]{"kill", String.valueOf(pid)}).waitFor();
        } catch (Exception ignored) {
        }
        try {
            Runtime.getRuntime().exec(new String[]{"kill", "-9", String.valueOf(pid)}).waitFor();
        } catch (Exception ignored) {
        }
    }

    private static Map<String, JsonObject> loadState() {
        try {
            File file = stateFile();
            if (!file.exists()) return new LinkedHashMap<>();
            Map<String, JsonObject> map = GSON.fromJson(new FileReader(file), new TypeToken<Map<String, JsonObject>>() {
            }.getType());
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static void saveState(Map<String, JsonObject> state) {
        try (FileWriter writer = new FileWriter(stateFile())) {
            GSON.toJson(state, writer);
        } catch (Exception ignored) {
        }
    }

    public static void updateService() {
        LabRuntimeService.update();
    }
}
