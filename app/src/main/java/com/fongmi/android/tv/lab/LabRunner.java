package com.fongmi.android.tv.lab;

import android.content.Context;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LabRunner {

    public interface OutputListener {
        void onOutput(String text);

        void onExit(int code);
    }

    private static final Map<String, Process> RUNNING = new ConcurrentHashMap<>();
    private static final Map<String, StringBuilder> LOGS = new ConcurrentHashMap<>();
    private static final Map<String, java.io.OutputStream> STDIN = new ConcurrentHashMap<>();

    private LabRunner() {
    }

    public static boolean isRunning(String key) {
        Process process = RUNNING.get(key);
        if (process != null && process.isAlive()) return true;
        return LabProcManager.recoveredAlive(key);
    }

    public static int runningCount() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, Process> entry : RUNNING.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isAlive()) keys.add(entry.getKey());
        }
        for (String key : LabProcManager.recoveredKeys()) {
            keys.add(key);
        }
        return keys.size();
    }

    public static java.util.List<String> runningKeys() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, Process> entry : RUNNING.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isAlive()) keys.add(entry.getKey());
        }
        keys.addAll(LabProcManager.recoveredKeys());
        return new java.util.ArrayList<>(keys);
    }

    public static void stop(String key) {
        Process process = RUNNING.remove(key);
        java.io.OutputStream stdin = STDIN.remove(key);
        if (stdin != null) {
            try {
                stdin.close();
            } catch (Throwable ignored) {
            }
        }
        if (process != null) {
            try {
                process.destroy();
            } catch (Throwable ignored) {
            }
            try {
                process.destroyForcibly();
            } catch (Throwable ignored) {
            }
            try {
                java.lang.reflect.Field field = process.getClass().getDeclaredField("pid");
                field.setAccessible(true);
                int pid = field.getInt(process);
                LabProcManager.killTree(pid);
            } catch (Throwable ignored) {
            }
            LabProcManager.untrack(key);
        } else {
            LabProcManager.stop(key);
        }
        LabProcManager.updateService();
    }

    public static void stopAll() {
        for (String key : new java.util.ArrayList<>(RUNNING.keySet())) stop(key);
        LabProcManager.stopAll();
        LabProcManager.updateService();
    }

    public static String getLog(String key) {
        StringBuilder sb = LOGS.get(key);
        if (sb != null && sb.length() > 0) return sb.toString();
        File file = LabProcManager.logFor(key);
        if (file != null && file.exists()) {
            try {
                return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    public static void clearLog(String key) {
        LOGS.remove(key);
    }

    public static Process getProcess(String key) {
        return RUNNING.get(key);
    }

    public static boolean writeInput(String key, String line) {
        java.io.OutputStream stdin = STDIN.get(key);
        if (stdin == null) return false;
        try {
            stdin.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Process run(Context context, LabModels.Item item, LabModels.Command command,
                              Map<String, String> vars, OutputListener listener) {
        String key = item.name + "/" + command.id;
        String expanded = expand(context, item, command.command, vars);
        return runShellCommand(context, item, key, expanded, listener);
    }

    public static Process runCustom(Context context, LabModels.Item item, String commandText,
                                    Map<String, String> vars, String key, OutputListener listener) {
        String expanded = expand(context, item, commandText, vars);
        return runShellCommand(context, item, key, expanded, listener);
    }

    public static Process runShellCommand(Context context, LabModels.Item item, String key,
                                          String command, OutputListener listener) {
        stop(key);
        try {
            if (TextUtils.isEmpty(command)) throw new IOException("命令为空");
            File cwd = LabEnv.packageRoot(context, item);
            ProcessBuilder builder = new ProcessBuilder("/system/bin/setsid", "/system/bin/sh", "-c", command);
            builder.directory(cwd);
            builder.redirectErrorStream(true);
            applyEnv(builder.environment(), context, item);
            Process process = builder.start();
            RUNNING.put(key, process);
            try {
                STDIN.put(key, process.getOutputStream());
            } catch (Throwable ignored) {
            }
            String cmdId = key.contains("/") ? key.substring(key.indexOf('/') + 1) : key;
            LabProcManager.track(key, pidOf(process), item.name, cmdId);
            LabProcManager.updateService();
            pump(process, key, command, listener);
            return process;
        } catch (Exception e) {
            if (listener != null) {
                listener.onOutput("错误: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "\n");
                listener.onExit(-1);
            }
            return null;
        }
    }

    public static Process runShell(Context context, String shell, String key, OutputListener listener) {
        stop(key);
        try {
            File cwd = LabEnv.localRoot().exists() ? LabEnv.localRoot() : context.getFilesDir();
            ProcessBuilder builder = new ProcessBuilder("/system/bin/sh", "-c", shell);
            builder.directory(cwd);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            RUNNING.put(key, process);
            LabProcManager.updateService();
            pump(process, key, shell, listener);
            return process;
        } catch (Exception e) {
            if (listener != null) {
                listener.onOutput("错误: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "\n");
                listener.onExit(-1);
            }
            return null;
        }
    }

    public static void applyEnv(Map<String, String> env, Context context, LabModels.Item item) {
        LabEnv.ensure7z(context);
        LabEnv.ensureProot(context);
        File packageDir = LabEnv.packageRoot(context, item);
        Set<String> pathSet = new LinkedHashSet<>();
        pathSet.add(LabEnv.sharedBin(context).getAbsolutePath());
        pathSet.add(LabEnv.prootRoot(context).getAbsolutePath());
        pathSet.add(new File(packageDir, "bin").getAbsolutePath());
        File base = LabEnv.baseRoot(context);
        File[] dirs = base.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                if (!dir.isDirectory() || "bin".equals(dir.getName()) || dir.equals(packageDir)) continue;
                File bin = new File(dir, "bin");
                if (bin.exists() && bin.isDirectory()) pathSet.add(bin.getAbsolutePath());
            }
        }
        String original = env.get("PATH");
        if (original != null) {
            for (String part : original.split(":")) {
                if (!part.contains("com.termux")) pathSet.add(part);
            }
        }
        pathSet.add("/system/bin");
        pathSet.add("/system/xbin");
        pathSet.add("/vendor/bin");
        pathSet.add("/sbin");
        if (item.var_path != null && item.var_path.containsKey("PATH")) {
            String value = item.var_path.get("PATH");
            if (!TextUtils.isEmpty(value)) {
                for (String part : value.split(":")) {
                    if (!part.contains("com.termux")) {
                        pathSet.add(part.startsWith("/") ? part : new File(packageDir, part).getAbsolutePath());
                    }
                }
            }
        }
        env.put("PATH", join(pathSet, ":"));
        String libDir = LabEnv.sharedBin(context).getAbsolutePath();
        String oldLd = env.get("LD_LIBRARY_PATH");
        if (TextUtils.isEmpty(oldLd)) env.put("LD_LIBRARY_PATH", libDir);
        else if (!oldLd.contains(libDir)) env.put("LD_LIBRARY_PATH", libDir + ":" + oldLd);
        // proot 是动态链接的，它的依赖 .so 放在 sharedBin/proot/ 下，得单独进搜索路径
        File prootDir = LabEnv.prootRoot(context);
        if (prootDir.isDirectory()) {
            String prootPath = prootDir.getAbsolutePath();
            String ld = env.get("LD_LIBRARY_PATH");
            if (!ld.contains(prootPath)) env.put("LD_LIBRARY_PATH", ld + ":" + prootPath);
            env.putAll(LabEnv.prootEnv(context));
        }
        if (item.var_path != null) {
            for (Map.Entry<String, String> e : item.var_path.entrySet()) {
                String key = e.getKey();
                if ("PATH".equals(key)) continue;
                String value = e.getValue();
                if (TextUtils.isEmpty(value)) continue;
                if (!value.startsWith("/") && !value.startsWith("$")) {
                    value = new File(packageDir, value).getAbsolutePath();
                }
                value = replacePlaceholders(context, item, value);
                if ("LD_LIBRARY_PATH".equals(key)) {
                    String old = env.get("LD_LIBRARY_PATH");
                    if (TextUtils.isEmpty(old)) env.put(key, value);
                    else env.put(key, value + ":" + old);
                } else {
                    env.put(key, value);
                }
            }
        }
        Map<String, String> userSettings = LabConfig.get().loadUserSettings(item.name);
        if (item.settings != null && !userSettings.isEmpty()) {
            for (LabModels.Setting setting : item.settings) {
                String value = userSettings.get(setting.key);
                if (value == null || value.isEmpty()) value = setting.defaultValue;
                if (value != null && !value.isEmpty()) {
                    env.put(setting.key, replacePlaceholders(context, item, value));
                }
            }
        }
        applyBinEnvVars(env, context);
        applyGlobalProxy(env, item);
        if (item.name != null && "nodejs".equalsIgnoreCase(item.name)) {
            putIfAbsent(env, "HOME", packageDir.getAbsolutePath());
            String nodeOptions = env.get("NODE_OPTIONS");
            if (nodeOptions == null || !nodeOptions.contains("--max-old-space-size")) {
                env.put("NODE_OPTIONS", nodeOptions == null ? "--max-old-space-size=512" : nodeOptions + " --max-old-space-size=512");
            }
        }
    }

    private static void applyBinEnvVars(Map<String, String> env, Context context) {
        LabModels.LabRoot root = LabConfig.get().getLabRoot();
        if (root == null || root.lists == null) return;
        for (LabModels.Item other : root.lists) {
            if (other == null || other.name == null) continue;
            if (!LabEnv.installed(context, other)) continue;
            String rel = other.binary_path;
            if (TextUtils.isEmpty(rel)) rel = other.cmd_name;
            if (TextUtils.isEmpty(rel)) continue;
            File bin = new File(LabEnv.packageRoot(context, other), rel);
            if (bin.exists()) {
                env.put(other.name.toUpperCase(Locale.ROOT) + "_BIN", bin.getAbsolutePath());
            }
        }
    }

    private static void applyGlobalProxy(Map<String, String> env, LabModels.Item item) {
        if (!LabConfig.get().getGlobalProxy()) return;
        if (item.name != null && "mihomo".equalsIgnoreCase(item.name)) return;
        int port = LabConfig.get().getGlobalProxyPort();
        if (port <= 0) return;
        String http = "http://127.0.0.1:" + port;
        String socks = "socks5://127.0.0.1:" + (port + 1);
        putIfAbsent(env, "http_proxy", http);
        putIfAbsent(env, "https_proxy", http);
        putIfAbsent(env, "HTTP_PROXY", http);
        putIfAbsent(env, "HTTPS_PROXY", http);
        putIfAbsent(env, "all_proxy", socks);
        putIfAbsent(env, "ALL_PROXY", socks);
        String noProxy = LabConfig.get().getGlobalProxyNoProxy();
        if (!TextUtils.isEmpty(noProxy)) {
            putIfAbsent(env, "no_proxy", noProxy);
            putIfAbsent(env, "NO_PROXY", noProxy);
        }
    }

    private static void putIfAbsent(Map<String, String> env, String key, String value) {
        if (!env.containsKey(key) || TextUtils.isEmpty(env.get(key))) {
            env.put(key, value);
        }
    }

    private static String join(Set<String> set, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(s);
        }
        return sb.toString();
    }

    private static int pidOf(Process process) {
        try {
            java.lang.reflect.Field field = process.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            return field.getInt(process);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void pump(Process process, String key, String command, OutputListener listener) {
        StringBuilder log = LOGS.computeIfAbsent(key, k -> new StringBuilder());
        File logFile = LabProcManager.logFor(key);
        java.io.FileWriter fileWriter = null;
        if (logFile != null) {
            try {
                logFile.getParentFile().mkdirs();
                fileWriter = new java.io.FileWriter(logFile, true);
                fileWriter.write("$ " + command + "\n\n");
                fileWriter.flush();
            } catch (Exception ignored) {
            }
        }
        java.io.FileWriter writer = fileWriter;
        Thread out = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (listener != null) listener.onOutput(line + "\n");
                    if (writer != null) {
                        try {
                            writer.write(line + "\n");
                            writer.flush();
                        } catch (Exception ignored) {
                        }
                    }
                    synchronized (log) {
                        log.append(line).append('\n');
                        if (log.length() > 200000) log.delete(0, log.length() / 2);
                    }
                }
            } catch (Exception ignored) {
            }
        });
        out.start();
        new Thread(() -> {
            try {
                int code = process.waitFor();
                try {
                    out.join(500);
                } catch (InterruptedException ignored) {
                }
                RUNNING.remove(key, process);
                STDIN.remove(key);
                try {
                    if (writer != null) writer.close();
                } catch (Exception ignored) {
                }
                int pid = pidOf(process);
                boolean daemon = false;
                long deadline = System.currentTimeMillis() + 2000;
                while (System.currentTimeMillis() < deadline) {
                    if (LabProcManager.trackGroup(key, pid)) {
                        daemon = true;
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                if (daemon) {
                    if (listener != null) listener.onOutput("\n[命令已转入后台运行]\n");
                    LabProcManager.updateService();
                } else {
                    LabProcManager.untrack(key);
                    LabProcManager.updateService();
                    if (listener != null) listener.onExit(code);
                }
            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    public static String expand(Context context, LabModels.Item item, String template, Map<String, String> vars) {
        if (template == null) return "";
        String result = expandNested(context, item, template, vars, 0);
        return adaptPackageLookup(replacePlaceholders(context, item, result), context);
    }

    private static String expandNested(Context context, LabModels.Item item, String text, Map<String, String> vars, int depth) {
        if (text == null || depth > 5) return text;
        Matcher matcher = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}").matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            if (matcher.start() > 0 && text.charAt(matcher.start() - 1) == '$') {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String value = vars == null ? null : vars.get(matcher.group(1));
            if (value == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacePlaceholders(context, item, value)));
            changed = true;
        }
        matcher.appendTail(sb);
        if (!changed) return sb.toString();
        return expandNested(context, item, sb.toString(), vars, depth + 1);
    }

    private static String adaptPackageLookup(String command, Context context) {
        if (command == null || !command.contains("pm list packages")) return command;
        String pkg = context.getPackageName();
        return Pattern.compile("\\$\\(\\s*pm list packages.*?\\)", Pattern.DOTALL)
                .matcher(command)
                .replaceAll(Matcher.quoteReplacement(pkg));
    }

    private static String replacePlaceholders(Context context, LabModels.Item item, String value) {
        File packageDir = LabEnv.packageRoot(context, item);
        return value
                .replace("{serverPort}", LabConfig.serverPort())
                .replace("{tvPath}", new File(LabEnv.localRoot().getParentFile(), "TV").getAbsolutePath())
                .replace("{dataPath}", new File(packageDir, "data").getAbsolutePath())
                .replace("{cachePath}", context.getCacheDir().getAbsolutePath())
                .replace("{envRootPath}", packageDir.getAbsolutePath())
                .replace("{wwwroot}", new File(LabEnv.localRoot(), "wwwroot").getAbsolutePath())
                .replace("{sdcard}", "/storage/emulated/0");
    }

    public static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\' && !inSingle) {
                escaped = true;
            } else if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (escaped) current.append('\\');
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }
}
