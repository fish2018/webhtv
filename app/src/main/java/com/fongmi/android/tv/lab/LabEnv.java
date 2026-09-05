package com.fongmi.android.tv.lab;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Notify;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class LabEnv {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private LabEnv() {
    }

    public static File baseRoot(Context context) {
        return new File(context.getFilesDir(), "lab");
    }

    public static File sharedBin(Context context) {
        File dir = new File(baseRoot(context), "bin");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File ensure7z(Context context) {
        try {
            File bin = sharedBin(context);
            File marker = new File(bin, "7zz.version");
            String expected = appVersionCode(context) + ":" + arch();
            String current = marker.exists() ? readSmall(marker) : "";
            if (!expected.equals(current)) {
                copyAsset(context, "lab/7zz", new File(bin, "7zz"));
                copyAsset(context, "lab/libc++_shared.so", new File(bin, "libc++_shared.so"));
                writeSmall(marker, expected);
            }
            File sevenZz = new File(bin, "7zz");
            sevenZz.setExecutable(true, false);
            new File(bin, "libc++_shared.so").setExecutable(true, false);
            File sevenZ = new File(bin, "7z");
            if (!sevenZ.exists()) link(sevenZz, sevenZ);
            return sevenZz;
        } catch (Exception e) {
            return null;
        }
    }

    /** proot 运行时目录名，见 assets/lab/proot/。 */
    private static final String PROOT_DIR = "proot";

    /**
     * 刚解出来的发行版 rootfs 缺 DNS 配置，apt 会以"Temporary failure resolving"之类
     * 难以定位的方式失败，所以这里补上 resolv.conf / hosts，并预建 proot 要绑定的挂载点。
     */
    private static void prepareRootfs(File rootfs) {
        try {
            File etc = new File(rootfs, "etc");
            etc.mkdirs();
            File resolv = new File(etc, "resolv.conf");
            if (!resolv.exists() || resolv.length() == 0) {
                writeSmall(resolv, "nameserver 8.8.8.8\nnameserver 223.5.5.5\n");
            }
            File hosts = new File(etc, "hosts");
            if (!hosts.exists() || hosts.length() == 0) {
                writeSmall(hosts, "127.0.0.1 localhost\n::1 localhost\n");
            }
            for (String dir : new String[]{"dev", "proc", "sys", "root", "tmp"}) {
                new File(rootfs, dir).mkdirs();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 释放 proot 运行时（proot 本体 + loader + 两个依赖 .so）。
     * 取自 Termux 的 aarch64/arm 包，是动态链接的，所以调用方必须同时把
     * {@link #prootEnv} 里的 LD_LIBRARY_PATH 和 PROOT_LOADER 带上。
     */
    public static File ensureProot(Context context) {
        try {
            File dir = prootRoot(context);
            File marker = new File(dir, "proot.version");
            String expected = appVersionCode(context) + ":" + arch();
            String current = marker.exists() ? readSmall(marker) : "";
            if (!expected.equals(current)) {
                dir.mkdirs();
                for (String name : new String[]{"proot", "loader", "loader32", "libtalloc.so.2", "libandroid-shmem.so"}) {
                    try {
                        copyAsset(context, "lab/" + PROOT_DIR + "/" + name, new File(dir, name));
                    } catch (Exception ignored) {
                        // loader32 只存在于 arm64 包里，armv7 缺它是正常的
                    }
                }
                writeSmall(marker, expected);
            }
            File proot = new File(dir, "proot");
            if (!proot.exists()) return null;
            File[] files = dir.listFiles();
            if (files != null) for (File file : files) file.setExecutable(true, false);
            return proot;
        } catch (Exception e) {
            return null;
        }
    }

    public static File prootRoot(Context context) {
        return new File(sharedBin(context), PROOT_DIR);
    }

    /** proot 运行所需的环境变量，叠加到命令的 environment 上。 */
    public static Map<String, String> prootEnv(Context context) {
        Map<String, String> env = new HashMap<>();
        File dir = prootRoot(context);
        env.put("PROOT_LOADER", new File(dir, "loader").getAbsolutePath());
        File loader32 = new File(dir, "loader32");
        if (loader32.exists()) env.put("PROOT_LOADER_32", loader32.getAbsolutePath());
        env.put("PROOT_TMP_DIR", context.getCacheDir().getAbsolutePath());
        return env;
    }

    private static String readSmall(File file) {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeSmall(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copyAsset(Context context, String assetPath, File target) throws IOException {
        if (target.exists()) target.delete();
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        try (InputStream in = context.getAssets().open(assetPath); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[16384];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
        }
    }

    public static File packageRoot(Context context, LabModels.Item item) {
        File dir = new File(baseRoot(context), item.name);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File localRoot() {
        return new File(LabConfig.get().getRoot());
    }

    public static int appVersionCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    public static void syncVodPlusAssets(Context context) {
        syncAssets(context, "VodPlus", new File(LabConfig.get().getRoot()), false);
    }

    private static void syncAssets(Context context, String assetPath, File dest, boolean overwrite) {
        try {
            String[] children = context.getAssets().list(assetPath);
            if (children != null && children.length > 0) {
                if (!dest.exists()) dest.mkdirs();
                for (String child : children) {
                    syncAssets(context, assetPath + "/" + child, new File(dest, child), overwrite);
                }
                return;
            }
            if (dest.exists() && !overwrite) return;
            if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
            try (InputStream in = context.getAssets().open(assetPath); FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[16384];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            }
        } catch (Exception ignored) {
        }
    }

    /** 包声明的可执行文件名，裸 gz 解压时用它命名落地文件。 */
    private static String binaryName(LabModels.Item item) {
        String rel = TextUtils.isEmpty(item.binary_path) ? item.cmd_name : item.binary_path;
        return TextUtils.isEmpty(rel) ? item.name : new File(rel).getName();
    }

    public static File binary(Context context, LabModels.Item item) {
        String rel = item.binary_path;
        if (TextUtils.isEmpty(rel)) rel = item.cmd_name;
        if (TextUtils.isEmpty(rel)) return null;
        File packageDir = packageRoot(context, item);
        File[] candidates = {
                new File(packageDir, rel),
                new File(packageDir, "bin/" + new File(rel).getName()),
                new File(packageDir, item.cmd_name == null ? item.name : item.cmd_name),
                new File(sharedBin(context), new File(rel).getName()),
                new File(sharedBin(context), item.cmd_name == null ? item.name : item.cmd_name)
        };
        for (File candidate : candidates) {
            if (candidate.exists()) return candidate;
        }
        File local = new File(localRoot(), rel);
        if (local.exists()) return local;
        return candidates[0];
    }

    /**
     * rootfs 类包（Linux 发行版基底）的压缩包内容是整个根文件系统，要解到 rootfs/ 子目录，
     * 而且判断"已安装"看的是 rootfs 非空而不是某个可执行文件。
     * 名字以 ubuntu 开头即算，这样 ubuntu-php / ubuntu-python3 这类各自独立的包都能命中。
     */
    static boolean isRootfs(LabModels.Item item) {
        if (item == null) return false;
        if (Boolean.TRUE.equals(item.rootfs)) return true;
        String name = item.name == null ? "" : item.name.toLowerCase(Locale.ROOT);
        return name.equals("ubuntu") || name.startsWith("ubuntu-") || name.startsWith("ubuntu_");
    }

    public static boolean installed(Context context, LabModels.Item item) {
        if (isRootfs(item)) {
            return nonEmpty(new File(packageRoot(context, item), "rootfs"));
        }
        File bin = binary(context, item);
        if (bin != null && bin.exists()) return true;
        File packageDir = packageRoot(context, item);
        File binDir = new File(packageDir, "bin");
        File runnerDir = new File(packageDir, "runner");
        return nonEmpty(binDir) || nonEmpty(runnerDir) || nonEmpty(new File(packageDir, "rootfs"));
    }

    private static boolean nonEmpty(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return false;
        File[] files = dir.listFiles();
        return files != null && files.length > 0;
    }

    public static String arch() {
        if (Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if (abi.contains("arm64")) return "arm64-v8a";
                if (abi.contains("armeabi-v7a") || abi.contains("armeabi")) return "armeabi-v7a";
            }
        }
        return "arm64-v8a";
    }

    public interface InstallCallback {
        void onProgress(String message);

        /** 下载精确进度：已下载字节 / 总字节（total<=0 表示服务器未返回长度） */
        default void onDownloadProgress(long done, long total) {
        }

        /** 解压精确进度：已解压字节 / 总解压字节（total<=0 表示无法预估） */
        default void onUnzipProgress(long done, long total) {
        }

        /**
         * 解压已完成、正在做不可量化的收尾（chmod 整目录、创建 symlink、写入 installed version 等）。
         * UI 应把状态文字切到"正在完成安装…"并保持进度条 100% 满格，
         * 直到 {@link #onDone()} 触发时再关闭弹窗/显示安装完成——保证进度与真实状态同步。
         */
        default void onFinalizing() {
        }

        void onDone();

        void onError(String message);
    }

    /** 解压进度回调：done=已写入字节，total=压缩包解压后总字节（<=0 表示未知） */
    public interface ExtractProgress {
        void onExtract(long done, long total);
    }

    public static void install(Context context, LabModels.Item item, InstallCallback callback) {
        install(context, item, null, callback);
    }

    public static void install(Context context, LabModels.Item item, String mirror, InstallCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                LabModels.Download download = pick(item, mirror);
                if (download == null) throw new IOException("没有可用下载地址");
                File packageDir = packageRoot(context, item);
                packageDir.mkdirs();
                for (String dir : item.mkdirList()) {
                    try {
                        new File(expandPath(dir)).mkdirs();
                    } catch (Exception ignored) {
                    }
                }
                boolean ubuntuRootfs = isRootfs(item);
                File root = ubuntuRootfs ? new File(packageDir, "rootfs") : packageDir;
                root.mkdirs();
                String url = download.getUrlByMirror(mirror);
                File archive = new File(context.getCacheDir(), "lab_" + item.name + "_" + sanitizeFileName(fileName(url)));
                if (!loadArchive(context, url, archive, item, callback)) {
                    throw new IOException("找不到压缩包：" + fileName(url) + "\n已搜索：\n" + searchPaths());
                }
                String archiveName = archive.getName().toLowerCase(Locale.ROOT);
                if (callback != null) App.post(() -> callback.onProgress("解压中 ..."));
                extract(archive, root, binaryName(item), (done, total) -> {
                    if (callback != null) callback.onUnzipProgress(done, total);
                });
                if (!TextUtils.isEmpty(download.liburl)) {
                    File libArchive = new File(context.getCacheDir(), "lab_" + item.name + "_lib_" + sanitizeFileName(fileName(download.liburl)));
                    if (!loadArchive(context, download.liburl, libArchive, item, callback)) {
                        throw new IOException("找不到依赖压缩包：" + fileName(download.liburl) + "\n已搜索：\n" + searchPaths());
                    }
                    if (callback != null) App.post(() -> callback.onProgress("解压依赖组件 ..."));
                    extract(libArchive, root, (done, total) -> {
                        if (callback != null) callback.onUnzipProgress(done, total);
                    });
                    libArchive.delete();
                }
                archive.delete();
                if (callback != null) App.post(callback::onFinalizing);
                if (ubuntuRootfs) {
                    ensureProot(context);
                    prepareRootfs(root);
                }
                chmod(packageDir);
                normalizeBinary(item, packageDir);
                chmod(new File(packageDir, "bin"));
                symlinkShared(context, item, packageDir);
                LabConfig.get().saveInstalledVersion(item.name, displayVersion(item));
                if (callback != null) App.post(callback::onDone);
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (callback != null) App.post(() -> callback.onError(message));
            }
        });
    }

    private static void normalizeBinary(LabModels.Item item, File packageDir) {
        String rel = item.binary_path;
        if (TextUtils.isEmpty(rel)) rel = item.cmd_name;
        if (TextUtils.isEmpty(rel)) return;
        String baseName = new File(rel).getName();
        File target = new File(packageDir, "bin/" + baseName);
        if (target.exists()) return;
        File found = findFile(packageDir, baseName, 0);
        if (found != null && found.isFile()) {
            try {
                target.getParentFile().mkdirs();
                com.github.catvod.utils.Path.copy(found, target);
                chmod(target);
            } catch (Exception ignored) {
            }
        }
    }

    private static File findFile(File dir, String name, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 8) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && name.equals(file.getName())) return file;
        }
        for (File file : files) {
            if (file.isDirectory() && !"rootfs".equals(file.getName())) {
                File found = findFile(file, name, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void symlinkShared(Context context, LabModels.Item item, File packageDir) {
        File shared = sharedBin(context);
        File binDir = new File(packageDir, "bin");
        if (binDir.exists() && binDir.isDirectory()) {
            File[] files = binDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        link(file, new File(shared, file.getName()));
                    }
                }
            }
        }
        String name = item.cmd_name == null ? item.name : item.cmd_name;
        File binary = binary(context, item);
        if (binary != null && binary.exists() && !new File(shared, name).exists()) {
            link(binary, new File(shared, name));
        }
    }

    private static void link(File target, File link) {
        try {
            if (link.exists()) link.delete();
            Runtime.getRuntime().exec(new String[]{"ln", "-sf", target.getAbsolutePath(), link.getAbsolutePath()}).waitFor();
        } catch (Exception ignored) {
        }
    }

    private static boolean loadArchive(Context context, String url, File target, LabModels.Item item, InstallCallback callback) throws IOException {
        String fileName = fileName(url);
        File local = findLocalArchive(url);
        if (local.exists()) {
            if (callback != null) App.post(() -> callback.onProgress("使用本地文件 " + fileName + " ..."));
            com.github.catvod.utils.Path.copy(local, target);
            return true;
        }
        String expanded = expandUrl(url);
        if (callback != null) App.post(() -> callback.onProgress("下载 " + fileName + " ..."));
        try {
            download(expanded, target, callback, fileName);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String localArchivePath(String url) {
        return LabConfig.get().getRoot() + "/EnvFiles/" + fileName(url);
    }

    private static File findLocalArchive(String url) {
        String name = fileName(url);
        String root = LabConfig.get().getRoot();
        String[] dirs = {
                root + "/EnvFiles",
                root + "/EnvFiles/二进制",
                root + "/EnvFiles/环境市场",
                "/storage/emulated/0/peekpro/EnvFiles",
                "/storage/emulated/0/peekpro/EnvFiles/二进制",
                "/storage/emulated/0/peekpro/EnvFiles/环境市场",
                "/storage/emulated/0/peekpro/scripts",
                "/storage/emulated/0/peekpro/Proxy",
                "/storage/emulated/0/peekpro",
                root + "/Download",
                "/storage/emulated/0/Download",
                root
        };
        List<String> names = new ArrayList<>();
        names.add(name);
        if ("php.zip".equals(name)) {
            names.add("php-arm64.zip");
            names.add("php_arm64.zip");
        }
        for (String dir : dirs) {
            for (String candidate : names) {
                File file = new File(dir, candidate);
                if (file.exists()) return file;
            }
        }
        return new File(localArchivePath(url));
    }

    private static String searchPaths() {
        String root = LabConfig.get().getRoot();
        return root + "/EnvFiles\n"
                + root + "/EnvFiles/二进制\n"
                + root + "/EnvFiles/环境市场\n"
                + "/storage/emulated/0/peekpro/EnvFiles\n"
                + "/storage/emulated/0/peekpro/EnvFiles/二进制\n"
                + "/storage/emulated/0/peekpro/EnvFiles/环境市场\n"
                + "/storage/emulated/0/peekpro/scripts\n"
                + "/storage/emulated/0/peekpro/Proxy\n"
                + "/storage/emulated/0/peekpro\n"
                + root + "/Download\n"
                + "/storage/emulated/0/Download\n"
                + root;
    }

    private static String expandPath(String path) {
        return path.replace("{serverPort}", LabConfig.serverPort())
                .replace("{dataPath}", LabConfig.dataPath())
                .replace("{sdcard}", "/storage/emulated/0");
    }

    public static void uninstall(Context context, LabModels.Item item) {
        File packageDir = packageRoot(context, item);
        deleteQuietly(packageDir);
        File shared = sharedBin(context);
        File[] links = shared.listFiles();
        if (links != null) {
            for (File link : links) {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"readlink", link.getAbsolutePath()});
                    String target = new String(readAll(process.getInputStream()), StandardCharsets.UTF_8).trim();
                    process.waitFor();
                    if (target.startsWith(packageDir.getAbsolutePath())) link.delete();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
        return out.toByteArray();
    }

    private static LabModels.Download pick(LabModels.Item item, String mirror) {
        if (item.downloads == null || item.downloads.isEmpty()) return null;
        String arch = arch();
        for (LabModels.Download d : item.downloads) {
            if (arch.equals(d.arch)) return d;
        }
        return item.downloads.get(0);
    }

    public static String displayVersion(LabModels.Item item) {
        if (item.downloads != null) {
            for (LabModels.Download download : item.downloads) {
                if (arch().equals(download.arch) && download.version != null && !download.version.isEmpty()) {
                    return download.version;
                }
            }
        }
        return item.version == null ? "" : item.version;
    }

    public static int compareVersions(String a, String b) {
        String[] left = (a == null ? "" : a.replaceAll("[^0-9.]", "")).split("\\.");
        String[] right = (b == null ? "" : b.replaceAll("[^0-9.]", "")).split("\\.");
        int max = Math.max(left.length, right.length);
        for (int i = 0; i < max; i++) {
            int l = i < left.length ? parseInt(left[i]) : 0;
            int r = i < right.length ? parseInt(right[i]) : 0;
            if (l > r) return 1;
            if (l < r) return -1;
        }
        return 0;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String expandUrl(String url) {
        return url.replace("{serverPort}", LabConfig.serverPort())
                .replace("{dataPath}", LabConfig.dataPath())
                .replace("{sdcard}", "/storage/emulated/0");
    }

    private static String fileName(String url) {
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : url;
    }

    private static String sanitizeFileName(String name) {
        if (name == null) return "archive";
        int query = name.indexOf('?');
        if (query >= 0) name = name.substring(0, query);
        int hash = name.indexOf('#');
        if (hash >= 0) name = name.substring(0, hash);
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() ? "archive" : cleaned;
    }

    private static void download(String url, File target) throws IOException {
        download(url, target, null, null);
    }

    private static void download(String url, File target, InstallCallback callback, String fileName) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("User-Agent", "WebHTV-Lab");
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("下载失败 HTTP " + code);
        long total = conn.getContentLengthLong();
        long downloaded = 0;
        long lastPost = 0;
        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[16384];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                downloaded += len;
                if (downloaded - lastPost >= 102400 || downloaded >= total) {
                    lastPost = downloaded;
                    final long d = downloaded, t = total;
                    if (callback != null) App.post(() -> callback.onDownloadProgress(d, t));
                }
            }
        } finally {
            conn.disconnect();
        }
        final long d = downloaded, t = total;
        if (callback != null) App.post(() -> callback.onDownloadProgress(d, t));
    }

    /** 人类可读的文件大小，用于下载进度文案 */
    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int u = (int) (Math.log(bytes) / Math.log(1024));
        String[] units = {"KB", "MB", "GB", "TB"};
        int idx = Math.min(u - 1, units.length - 1);
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024, idx + 1), units[idx]);
    }

    private static void extract(File archive, File root) throws IOException {
        extract(archive, root, null);
    }

    private static void extract(File archive, File root, ExtractProgress cb) throws IOException {
        extract(archive, root, null, cb);
    }

    /**
     * @param singleName 裸 gzip（整个包就是一个压缩过的可执行文件，如 mihomo 官方 release）解压后的落地文件名；
     *                   传 null 时回落到压缩包自身的名字。
     */
    private static void extract(File archive, File root, String singleName, ExtractProgress cb) throws IOException {
        long total = cb != null ? measure(archive) : 0;
        if (tryExtractByMagic(archive, root, singleName, total, cb)) return;
        String name = archive.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            extractZip(archive, root, total, cb);
        } else if (name.endsWith(".7z")) {
            extract7z(archive, root, total, cb);
        } else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            try (InputStream in = new GzipCompressorInputStream(new FileInputStream(archive))) {
                extractTar(in, root, total, cb);
            }
        } else if (name.endsWith(".gz")) {
            extractGzip(archive, root, singleName, total, cb);
        } else if (name.endsWith(".tar.xz") || name.endsWith(".deb")) {
            if (name.endsWith(".deb")) {
                extractDeb(archive, root, total, cb);
            } else {
                try (InputStream in = new XZCompressorInputStream(new FileInputStream(archive))) {
                    extractTar(in, root, total, cb);
                }
            }
        } else {
            throw new IOException("不支持的压缩格式: " + archive.getName());
        }
    }

    private static boolean tryExtractByMagic(File archive, File root, String singleName, long total, ExtractProgress cb) throws IOException {
        byte[] magic = new byte[8];
        int offset = 0;
        try (InputStream in = new FileInputStream(archive)) {
            while (offset < magic.length) {
                int read = in.read(magic, offset, magic.length - offset);
                if (read < 0) break;
                offset += read;
            }
        }
        if (offset >= 2 && magic[0] == 'P' && magic[1] == 'K') {
            extractZip(archive, root, total, cb);
            return true;
        }
        if (offset >= 6 && (magic[0] & 0xFF) == 0x37 && (magic[1] & 0xFF) == 0x7A
                && (magic[2] & 0xFF) == 0xBC && (magic[3] & 0xFF) == 0xAF
                && (magic[4] & 0xFF) == 0x27 && (magic[5] & 0xFF) == 0x1C) {
            extract7z(archive, root, total, cb);
            return true;
        }
        if (offset >= 2 && (magic[0] & 0xFF) == 0x1F && (magic[1] & 0xFF) == 0x8B) {
            extractGzip(archive, root, singleName, total, cb);
            return true;
        }
        if (offset >= 6 && (magic[0] & 0xFF) == 0xFD && (magic[1] & 0xFF) == 0x37
                && (magic[2] & 0xFF) == 0x7A && (magic[3] & 0xFF) == 0x58
                && (magic[4] & 0xFF) == 0x5A && (magic[5] & 0xFF) == 0x00) {
            try (InputStream in = new XZCompressorInputStream(new FileInputStream(archive))) {
                extractTar(in, root, total, cb);
            }
            return true;
        }
        if (offset >= 8 && "!<arch>\n".equals(new String(magic, 0, 8, StandardCharsets.US_ASCII))) {
            extractDeb(archive, root, total, cb);
            return true;
        }
        return false;
    }

    /**
     * gzip 包有两种：tar.gz（内层是 tar 归档）和裸 gz（整包就是一个压缩过的可执行文件，
     * 如 mihomo 官方 release）。按内层 tar 标记区分，裸 gz 直接落地成 bin/ 下的单个文件。
     */
    private static void extractGzip(File archive, File root, String singleName, long total, ExtractProgress cb) throws IOException {
        if (gzipHasTar(archive)) {
            try (InputStream in = new GzipCompressorInputStream(new FileInputStream(archive))) {
                extractTar(in, root, total, cb);
            }
            return;
        }
        String name = TextUtils.isEmpty(singleName) ? stripGzSuffix(archive.getName()) : singleName;
        File target = new File(root, "bin/" + name);
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        Progress p = new Progress(total, cb);
        try (InputStream in = new GzipCompressorInputStream(new FileInputStream(archive)); FileOutputStream out = new FileOutputStream(target)) {
            copyProg(in, out, p);
        }
        if (cb != null) cb.onExtract(total > 0 ? total : p.done, total > 0 ? total : p.done);
    }

    /** tar 头在偏移 257 处有 "ustar" 标记，据此判断 gzip 内层是否为 tar 归档。 */
    private static boolean gzipHasTar(File archive) {
        try (InputStream in = new GzipCompressorInputStream(new FileInputStream(archive))) {
            byte[] header = new byte[265];
            int offset = 0;
            while (offset < header.length) {
                int read = in.read(header, offset, header.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset < header.length) return false;
            return "ustar".equals(new String(header, 257, 5, StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
    }

    private static String stripGzSuffix(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".gz") ? name.substring(0, name.length() - 3) : name;
    }

    private static void extractDeb(File archive, File root, long total, ExtractProgress cb) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(archive))) {
            byte[] magic = new byte[8];
            readFully(in, magic);
            if (!"!<arch>\n".equals(new String(magic, StandardCharsets.US_ASCII))) {
                throw new IOException("无效的 deb 包");
            }
            while (true) {
                byte[] header = new byte[60];
                if (!readFully(in, header)) break;
                String name = new String(header, 0, 16, StandardCharsets.US_ASCII).trim();
                String sizeText = new String(header, 48, 10, StandardCharsets.US_ASCII).trim();
                long size = Long.parseLong(sizeText);
                if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                if ("data.tar.xz".equals(name) || "data.tar.gz".equals(name)) {
                    byte[] data = new byte[(int) size];
                    readFully(in, data);
                    try (InputStream decomp = "data.tar.xz".equals(name)
                            ? new XZCompressorInputStream(new ByteArrayInputStream(data))
                            : new GzipCompressorInputStream(new ByteArrayInputStream(data))) {
                        extractTar(decomp, root, total, cb);
                    }
                    return;
                }
                skipFully(in, size);
                if ((size & 1L) == 1L) in.read();
            }
        }
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int offset = 0;
        while (offset < buf.length) {
            int len = in.read(buf, offset, buf.length - offset);
            if (len < 0) return offset == 0 ? false : true;
            offset += len;
        }
        return true;
    }

    private static void skipFully(InputStream in, long size) throws IOException {
        long remaining = size;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() == -1) throw new IOException("deb 数据截断");
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    private static void extractZip(File archive, File root, long total, ExtractProgress cb) throws IOException {
        Progress p = new Progress(total, cb);
        try (ZipFile zip = new ZipFile(archive, StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                File target = safeTarget(root, entry.getName());
                if (entry.isDirectory()) {
                    target.mkdirs();
                    continue;
                }
                target.getParentFile().mkdirs();
                try (InputStream in = zip.getInputStream(entry); FileOutputStream out = new FileOutputStream(target)) {
                    copyProg(in, out, p);
                }
                if (cb != null) cb.onExtract(p.done, total);
            }
        }
        if (cb != null) cb.onExtract(total, total);
    }

    private static void extract7z(File archive, File root, long total, ExtractProgress cb) throws IOException {
        if (cb == null && extract7zNative(archive, root)) return;
        Progress p = new Progress(total, cb);
        try (SevenZFile sevenZ = new SevenZFile(archive)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                File target = safeTarget(root, entry.getName());
                if (entry.isDirectory()) {
                    target.mkdirs();
                    continue;
                }
                target.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buf = new byte[16384];
                    int len;
                    long since = 0;
                    while ((len = sevenZ.read(buf)) != -1) {
                        out.write(buf, 0, len);
                        p.done += len; since += len;
                        if (since >= 262144) { since = 0; if (cb != null) cb.onExtract(p.done, total); }
                    }
                }
                if (cb != null) cb.onExtract(p.done, total);
            }
        }
        if (cb != null) cb.onExtract(total, total);
    }

    private static boolean extract7zNative(File archive, File root) throws IOException {
        try {
            if (Build.VERSION.SDK_INT < 24) return false;
            File sevenZz = ensure7z(App.get());
            if (sevenZz == null || !sevenZz.exists() || !sevenZz.canExecute()) return false;
            if (!root.exists()) root.mkdirs();
            ProcessBuilder pb = new ProcessBuilder(
                    sevenZz.getAbsolutePath(),
                    "x", "-y",
                    "-o" + root.getAbsolutePath(),
                    archive.getAbsolutePath());
            pb.redirectErrorStream(true);
            Map<String, String> env = pb.environment();
            String libDir = sevenZz.getParentFile().getAbsolutePath();
            String oldLd = env.get("LD_LIBRARY_PATH");
            env.put("LD_LIBRARY_PATH", TextUtils.isEmpty(oldLd) ? libDir : libDir + ":" + oldLd);
            Process process = pb.start();
            try (InputStream in = process.getInputStream()) {
                byte[] buf = new byte[4096];
                while (in.read(buf) != -1) {
                }
            }
            int code = process.waitFor();
            File[] files = root.listFiles();
            return code == 0 && files != null && files.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void extractTar(InputStream in, File root, long total, ExtractProgress cb) throws IOException {
        Progress p = new Progress(total, cb);
        try (TarArchiveInputStream tar = new TarArchiveInputStream(in)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                File target = safeTarget(root, entry.getName());
                if (entry.isDirectory()) {
                    target.mkdirs();
                    continue;
                }
                if (target.getParentFile() != null) target.getParentFile().mkdirs();
                // 发行版 rootfs 里 /bin /lib /sbin 都是指向 usr/* 的符号链接，
                // 当成普通空文件写下去会让动态加载器和所有命令都找不到，rootfs 直接不可用。
                if (entry.isSymbolicLink()) {
                    makeSymlink(target, entry.getLinkName());
                    continue;
                }
                if (entry.isLink()) {
                    makeHardlink(root, target, entry.getLinkName());
                    continue;
                }
                try (FileOutputStream out = new FileOutputStream(target)) {
                    copyProg(tar, out, p);
                }
                boolean executable = (entry.getMode() & 0111) != 0;
                if (executable) target.setExecutable(true, false);
                if (cb != null) cb.onExtract(p.done, total);
            }
        }
        if (cb != null) cb.onExtract(total, total);
    }

    /** 按归档里声明的原样建符号链接（相对/绝对都保留），失败再退回 ln -s。 */
    private static void makeSymlink(File target, String linkName) {
        if (TextUtils.isEmpty(linkName)) return;
        try {
            deleteQuietly(target);
            java.nio.file.Files.createSymbolicLink(target.toPath(), new File(linkName).toPath());
        } catch (Throwable e) {
            try {
                Runtime.getRuntime().exec(new String[]{"ln", "-sfn", linkName, target.getAbsolutePath()}).waitFor();
            } catch (Exception ignored) {
            }
        }
    }

    /** 硬链接指向归档中较早出现的条目；建不了就退化成复制。 */
    private static void makeHardlink(File root, File target, String linkName) {
        if (TextUtils.isEmpty(linkName)) return;
        try {
            File source = safeTarget(root, linkName);
            if (!source.exists()) return;
            deleteQuietly(target);
            try {
                java.nio.file.Files.createLink(target.toPath(), source.toPath());
            } catch (Throwable e) {
                com.github.catvod.utils.Path.copy(source, target);
                if (source.canExecute()) target.setExecutable(true, false);
            }
        } catch (Exception ignored) {
        }
    }

    private static File safeTarget(File root, String name) throws IOException {
        File target = new File(root, name);
        String rootPath = root.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        if (!targetPath.startsWith(rootPath + File.separator) && !targetPath.equals(rootPath)) {
            throw new IOException("非法路径: " + name);
        }
        return target;
    }

    private static void copy(InputStream in, FileOutputStream out) throws IOException {
        byte[] buf = new byte[16384];
        int len;
        while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
    }

    private static final class Progress {
        long done;
        final long total;
        final ExtractProgress cb;

        Progress(long total, ExtractProgress cb) {
            this.total = total;
            this.cb = cb;
        }
    }

    /** 带进度累加的拷贝：每写入约 256KB 回调一次，结束再补一次 100% */
    private static void copyProg(InputStream in, OutputStream out, Progress p) throws IOException {
        byte[] buf = new byte[16384];
        int len;
        long since = 0;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
            if (p != null) {
                p.done += len;
                since += len;
                if (since >= 262144) {
                    since = 0;
                    if (p.cb != null) p.cb.onExtract(p.done, p.total);
                }
            }
        }
        if (p != null && p.cb != null) p.cb.onExtract(p.done, p.total);
    }

    /** 预估压缩包解压后的总字节数；无法预估时返回 0（UI 退化为不确定进度） */
    private static long measure(File archive) {
        try {
            String lower = archive.getName().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".zip")) return measureZip(archive);
            if (lower.endsWith(".7z")) return measure7z(archive);
            if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
                try (InputStream in = new GzipCompressorInputStream(new FileInputStream(archive))) {
                    return measureTarStream(in);
                }
            }
            if (lower.endsWith(".tar.xz")) {
                try (InputStream in = new XZCompressorInputStream(new FileInputStream(archive))) {
                    return measureTarStream(in);
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static long measureZip(File archive) throws IOException {
        long total = 0;
        try (ZipFile zip = new ZipFile(archive, StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory()) total += e.getSize();
            }
        }
        return total;
    }

    private static long measure7z(File archive) throws IOException {
        long total = 0;
        try (SevenZFile sevenZ = new SevenZFile(archive)) {
            SevenZArchiveEntry e;
            while ((e = sevenZ.getNextEntry()) != null) {
                if (!e.isDirectory()) total += e.getSize();
            }
        }
        return total;
    }

    private static long measureTarStream(InputStream in) throws IOException {
        long total = 0;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(in)) {
            TarArchiveEntry e;
            while ((e = tar.getNextTarEntry()) != null) {
                if (!e.isDirectory()) total += e.getSize();
            }
        }
        return total;
    }

    private static void chmod(File root) {
        if (root == null || !root.exists()) return;
        // 一次性递归 chmod 整目录：nodejs 静态版解压后有几万文件，
        // 逐文件 exec("chmod 755") 会产生几万次进程 fork + waitFor，是"解压 100% 后仍卡几秒"的真正元凶。
        // 改用一条 chmod -R 让内核在 C 层递归，耗时从"秒级"降到"毫秒级"，
        // 从而保证「解压 100% → 收尾 → 关闭弹窗/提示安装完成」三者几乎瞬时同步。
        try {
            Runtime.getRuntime().exec(new String[]{"chmod", "-R", "755", root.getAbsolutePath()}).waitFor();
            return;
        } catch (Exception ignored) {
        }
        // 回退：保持旧行为（逐文件），仅在上面的递归命令不可用时走这里
        if (!root.isDirectory()) return;
        File[] files = root.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                chmod(file);
            } else if (!file.canExecute() && isBinary(file.getName())) {
                try {
                    Runtime.getRuntime().exec(new String[]{"chmod", "755", file.getAbsolutePath()}).waitFor();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean isBinary(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains(".") && !lower.endsWith(".so")) return false;
        return true;
    }

    private static void deleteQuietly(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File f : files) deleteQuietly(f);
        }
        file.delete();
    }

    public static void toastError(String message) {
        Notify.show("实验室: " + message);
    }

    public interface ProgressCallback {
        void onProgress(String message);
    }

    public static String dependencyCheckPath(LabModels.CommandDownload dep) {
        if (dep == null || TextUtils.isEmpty(dep.check_file)) return null;
        if (dep.check_file.startsWith("/")) return dep.check_file;
        return new File(LabConfig.get().getRoot(), dep.check_file).getAbsolutePath();
    }

    public static String dependencySavePath(LabModels.CommandDownload dep) {
        if (dep == null || TextUtils.isEmpty(dep.save_to)) return null;
        String path = expandPath(dep.save_to);
        if (path.startsWith("/")) return path;
        return new File(LabConfig.get().getRoot(), path).getAbsolutePath();
    }

    public static String dependencyExtractPath(LabModels.CommandDownload dep) {
        if (dep == null || TextUtils.isEmpty(dep.extract_to)) return LabConfig.get().getRoot();
        String path = expandPath(dep.extract_to);
        if (path.startsWith("/")) return path;
        return new File(LabConfig.get().getRoot(), path).getAbsolutePath();
    }

    public static boolean dependencyNeeded(LabModels.CommandDownload dep) {
        if (dep == null || TextUtils.isEmpty(dep.url)) return false;
        String save = dependencySavePath(dep);
        if (save != null) return !new File(save).exists();
        String check = dependencyCheckPath(dep);
        if (check != null) return !new File(check).exists();
        return !new File(dependencyExtractPath(dep), depMarker(dep)).exists();
    }

    private static String depMarker(LabModels.CommandDownload dep) {
        String url = dep.url == null ? "" : dep.url;
        return ".lab_dep_ok_" + Integer.toHexString(url.hashCode());
    }

    public static void prepareDependency(Context context, LabModels.CommandDownload dep, ProgressCallback callback) throws IOException {
        if (dep == null) return;
        if (!dependencyNeeded(dep)) return;
        String rawUrl = dep.url;
        if (TextUtils.isEmpty(rawUrl)) {
            if (dep.required) throw new IOException("缺少依赖下载地址");
            return;
        }
        String url = expandUrl(rawUrl);
        String name = fileName(url);
        File target = new File(context.getCacheDir(), "lab_dep_" + name);
        File local = findLocalArchive(url);
        if (local.exists()) {
            if (callback != null) App.post(() -> callback.onProgress("使用本地文件 " + name + " ..."));
            com.github.catvod.utils.Path.copy(local, target);
        } else {
            if (callback != null) App.post(() -> callback.onProgress("下载 " + name + " ..."));
            download(url, target);
        }
        String save = dependencySavePath(dep);
        if (save != null) {
            File saveFile = new File(save);
            saveFile.getParentFile().mkdirs();
            if (callback != null) App.post(() -> callback.onProgress("保存到 " + saveFile.getAbsolutePath() + " ..."));
            com.github.catvod.utils.Path.copy(target, saveFile);
            target.delete();
            return;
        }
        File extractRoot = new File(dependencyExtractPath(dep));
        extractRoot.mkdirs();
        File staging = new File(context.getCacheDir(), "lab_extract_" + System.currentTimeMillis());
        staging.mkdirs();
        if (callback != null) App.post(() -> callback.onProgress("解压到 " + extractRoot.getAbsolutePath() + " ..."));
        extract(target, staging);
        chmod(staging);
        File[] top = staging.listFiles();
        File source = staging;
        if (top != null) {
            if (top.length == 1 && top[0].isDirectory()) {
                source = top[0];
            } else {
                String targetName = extractRoot.getName();
                for (File entry : top) {
                    if (entry.isDirectory() && entry.getName().equals(targetName)) {
                        source = entry;
                        break;
                    }
                }
            }
        }
        mergeTree(source, extractRoot);
        deleteQuietly(staging);
        target.delete();
        String check = dependencyCheckPath(dep);
        if (check != null) {
            File checkFile = new File(check);
            if (!checkFile.exists()) {
                File found = findFile(extractRoot, checkFile.getName(), 8);
                if (found != null && found.isFile()) {
                    try {
                        checkFile.getParentFile().mkdirs();
                        com.github.catvod.utils.Path.copy(found, checkFile);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        try {
            new File(extractRoot, depMarker(dep)).createNewFile();
        } catch (Exception ignored) {
        }
    }

    private static void mergeTree(File src, File dst) throws IOException {
        mergeTree(src, dst, 0);
    }

    private static void mergeTree(File src, File dst, int depth) throws IOException {
        if (depth > 40) return; // 防御性护栏：防止畸形压缩包（如符号链接环/异常嵌套）导致无限递归、栈溢出
        if (src == null || !src.exists()) return;
        if (src.isDirectory()) {
            if (!dst.exists()) dst.mkdirs();
            File[] children = src.listFiles();
            if (children == null) return;
            for (File child : children) mergeTree(child, new File(dst, child.getName()), depth + 1);
        } else {
            if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
            com.github.catvod.utils.Path.copy(src, dst);
        }
    }

    public static File exportPackage(Context context, LabModels.Item item, List<LabCustomCommands.CustomCommand> customs) throws Exception {
        File exportDir = new File(LabConfig.get().getRoot(), "export");
        exportDir.mkdirs();
        File out = new File(exportDir, item.name + "_" + arch() + ".lab.7z");
        if (out.exists()) out.delete();
        SevenZOutputFile sevenZ = new SevenZOutputFile(out);
        Gson gson = new Gson();
        java.util.Map<String, Object> manifest = new java.util.LinkedHashMap<>();
        manifest.put("name", item.name);
        manifest.put("version", item.version == null ? "" : item.version);
        manifest.put("arch", arch());
        manifest.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        write7zText(sevenZ, "manifest.json", gson.toJson(manifest));
        write7zText(sevenZ, "package.json", gson.toJson(item));
        write7zText(sevenZ, "commands.json", gson.toJson(customs == null ? new ArrayList<>() : customs));
        File pkg = packageRoot(context, item);
        if (pkg.exists()) addTree(sevenZ, pkg, "binary/");
        sevenZ.close();
        return out;
    }

    private static void write7zText(SevenZOutputFile sevenZ, String name, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        SevenZArchiveEntry entry = new SevenZArchiveEntry();
        entry.setName(name);
        entry.setSize(bytes.length);
        sevenZ.putArchiveEntry(entry);
        sevenZ.write(bytes);
        sevenZ.closeArchiveEntry();
    }

    private static void addTree(SevenZOutputFile sevenZ, File dir, String prefix) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = prefix + file.getName();
            if (file.isDirectory()) {
                SevenZArchiveEntry entry = new SevenZArchiveEntry();
                entry.setName(name + "/");
                entry.setDirectory(true);
                sevenZ.putArchiveEntry(entry);
                sevenZ.closeArchiveEntry();
                addTree(sevenZ, file, name + "/");
            } else {
                SevenZArchiveEntry entry = new SevenZArchiveEntry();
                entry.setName(name);
                entry.setSize(file.length());
                sevenZ.putArchiveEntry(entry);
                try (FileInputStream in = new FileInputStream(file)) {
                    byte[] buf = new byte[16384];
                    int len;
                    while ((len = in.read(buf)) != -1) sevenZ.write(buf, 0, len);
                }
                sevenZ.closeArchiveEntry();
            }
        }
    }

    public static boolean importPackage(Context context, File archive) throws Exception {
        String manifestJson = null;
        String packageJson = null;
        String commandsJson = null;
        SevenZFile sevenZ = new SevenZFile(archive);
        SevenZArchiveEntry entry;
        while ((entry = sevenZ.getNextEntry()) != null) {
            String name = entry.getName();
            if ("manifest.json".equals(name)) manifestJson = read7zText(sevenZ, entry);
            else if ("package.json".equals(name)) packageJson = read7zText(sevenZ, entry);
            else if ("commands.json".equals(name)) commandsJson = read7zText(sevenZ, entry);
        }
        sevenZ.close();
        if (packageJson == null) throw new IOException("无效的离线包: 缺少 package.json");
        LabModels.Item item = new Gson().fromJson(packageJson, LabModels.Item.class);
        if (item == null || item.name == null || item.name.isEmpty() || item.name.contains("/") || item.name.contains("..")) {
            throw new IOException("无效的离线包: 包名非法");
        }
        File pkg = packageRoot(context, item);
        deleteQuietly(pkg);
        pkg.mkdirs();
        SevenZFile z2 = new SevenZFile(archive);
        String rootPath = pkg.getCanonicalPath();
        while ((entry = z2.getNextEntry()) != null) {
            String name = entry.getName();
            if (!name.startsWith("binary/")) continue;
            String rel = name.substring(7);
            if (rel.isEmpty()) continue;
            File target = new File(pkg, rel);
            String targetPath = target.getCanonicalPath();
            if (!targetPath.startsWith(rootPath + File.separator) && !targetPath.equals(rootPath)) {
                throw new IOException("路径越界: " + name);
            }
            if (entry.isDirectory()) {
                target.mkdirs();
            } else {
                target.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buf = new byte[16384];
                    int len;
                    while ((len = z2.read(buf)) != -1) out.write(buf, 0, len);
                }
            }
        }
        z2.close();
        chmod(pkg);
        symlinkShared(context, item, pkg);
        LabConfig.get().saveImported(packageJson);
        if (commandsJson != null && !commandsJson.isEmpty()) {
            try {
                List<LabCustomCommands.CustomCommand> list = new Gson().fromJson(commandsJson, new TypeToken<List<LabCustomCommands.CustomCommand>>() {
                }.getType());
                if (list != null) LabCustomCommands.save(item.name, list);
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    private static String read7zText(SevenZFile sevenZ, SevenZArchiveEntry entry) throws IOException {
        long size = entry.getSize();
        if (size < 0 || size > 10485760) throw new IOException("entry 大小异常或超过 10MB 限制: " + entry.getName());
        byte[] buf = new byte[(int) size];
        int off = 0;
        while (off < buf.length) {
            int len = sevenZ.read(buf, off, buf.length - off);
            if (len < 0) break;
            off += len;
        }
        return new String(buf, 0, off, StandardCharsets.UTF_8);
    }
}
