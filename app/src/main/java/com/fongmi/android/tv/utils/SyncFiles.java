package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.SyncOptions;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SyncFiles {

    public static final String DEFAULT_PATHS = "TV\nTVBox\nTVData";
    public static final String CUSTOM_CSP_PATH = "TV/CustomCsp";
    public static final String PART_NAME = "syncFiles";

    private static final int BUFFER_SIZE = 128 * 1024;

    public static List<String> getPaths(String text) {
        List<String> paths = new ArrayList<>();
        String source = TextUtils.isEmpty(text) ? DEFAULT_PATHS : text;
        for (String line : source.split("[\\r\\n]+")) {
            paths.add(line);
        }
        return normalizeTargets(paths);
    }

    static List<String> normalizeTargets(List<String> paths) {
        List<String> result = new ArrayList<>();
        if (paths == null) return result;
        for (String path : paths) addPath(result, normalize(path));
        return result;
    }

    public static String getPathsText(List<String> paths) {
        return TextUtils.join("\n", paths);
    }

    public static List<String> getPaths(SyncOptions options) {
        List<String> result = new ArrayList<>();
        if (options == null) return result;
        if (options.isSpider()) for (String path : getPaths(options.getPaths())) addPath(result, path);
        if (options.isConfig() || options.isWebHome()) addPath(result, CUSTOM_CSP_PATH);
        return result;
    }

    public static boolean hasPaths(SyncOptions options) {
        return !getPaths(options).isEmpty();
    }

    private static void addPath(List<String> paths, String path) {
        String candidate = normalizeRelative(path);
        if (candidate.isEmpty()) return;
        for (String existing : paths) if (covers(existing, candidate)) return;
        paths.removeIf(existing -> covers(candidate, existing));
        paths.add(candidate);
    }

    private static boolean covers(String parent, String child) {
        String p = normalizeRelative(parent);
        String c = normalizeRelative(child);
        return !p.isEmpty() && (p.equals(c) || c.startsWith(p + "/"));
    }

    public static Archive createArchive(List<String> paths) throws IOException {
        return createArchive(paths, null);
    }

    public static Archive createArchive(List<String> paths, BooleanSupplier running) throws IOException {
        return createArchive(paths, running, null);
    }

    public static Archive createArchive(List<String> paths, BooleanSupplier running, Progress progress) throws IOException {
        return createArchive(Path.root(), Path.cache(), paths, running, progress);
    }

    static Archive createArchive(File root, File cache, List<String> paths) throws IOException {
        return createArchive(root, cache, paths, null, null);
    }

    private static Archive createArchive(File root, File cache, List<String> paths, BooleanSupplier running, Progress progress) throws IOException {
        List<String> targets = normalizeTargets(paths);
        if (targets.isEmpty()) return null;
        root = root.getCanonicalFile();
        File archive = File.createTempFile("webhtv-sync-", ".zip", cache);
        int count = 0;
        long size = 0;
        Stats total = new Stats();
        ArchiveTracker tracker = new ArchiveTracker();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archive), BUFFER_SIZE))) {
            for (String path : targets) {
                checkRunning(running);
                File file = new File(root, path);
                if (!inside(root, file) || !file.exists()) continue;
                Stats stats = add(root, file, zos, buffer, running, progress, total, tracker);
                count += stats.count;
                size += stats.size;
            }
        } catch (Throwable e) {
            Path.clear(archive);
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
        if (count == 0) {
            Path.clear(archive);
            return null;
        }
        Archive result = new Archive(archive, count, size, archive.length(), targets);
        SpiderDebug.log("sync", "archive sync dirs count=%d size=%d zip=%d duplicates=%d file=%s", result.getCount(), result.getRawSize(), result.getZipSize(), tracker.duplicates(), archive.getAbsolutePath());
        return result;
    }

    public static int restoreArchive(File archive) throws IOException {
        if (archive == null || !archive.isFile() || archive.length() <= 0) return 0;
        File root = Path.root().getCanonicalFile();
        int count = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive), BUFFER_SIZE))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String path = normalize(entry.getName());
                if (path.isEmpty()) continue;
                File out = new File(root, path);
                if (!inside(root, out)) continue;
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(Path.create(out)), BUFFER_SIZE)) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) bos.write(buffer, 0, read);
                    }
                    if (entry.getTime() > 0) out.setLastModified(entry.getTime());
                    count++;
                }
                zis.closeEntry();
            }
        }
        SpiderDebug.log("sync", "restore sync dirs count=%d file=%s", count, archive.getAbsolutePath());
        return count;
    }

    public static int countArchiveFiles(File archive, String path) throws IOException {
        if (archive == null || !archive.isFile() || archive.length() <= 0) return 0;
        String target = normalize(path);
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive), BUFFER_SIZE))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = normalize(entry.getName());
                if (!entry.isDirectory() && covers(target, name)) count++;
                zis.closeEntry();
            }
        }
        return count;
    }

    public static int countFiles(String path) throws IOException {
        File root = Path.root().getCanonicalFile();
        File target = new File(root, normalize(path)).getCanonicalFile();
        if (!inside(root, target)) return 0;
        return countFiles(root, target, new ArchiveTracker());
    }

    private static int countFiles(File root, File file, ArchiveTracker tracker) throws IOException {
        File canonical = file.getCanonicalFile();
        if (!inside(root, canonical) || !canonical.exists()) return 0;
        String name = root.toPath().relativize(canonical.toPath()).toString().replace(File.separatorChar, '/');
        if (skipTree(name)) return 0;
        if (canonical.isFile()) return skip(name) ? 0 : 1;
        if (!tracker.visit(canonical)) return 0;
        int count = 0;
        File[] files = canonical.listFiles();
        if (files != null) for (File child : files) count += countFiles(root, child, tracker);
        return count;
    }

    private static Stats add(File root, File file, ZipOutputStream zos, byte[] buffer, BooleanSupplier running, Progress progress, Stats total, ArchiveTracker tracker) throws IOException {
        checkRunning(running);
        File canonical = file.getCanonicalFile();
        if (!inside(root, canonical)) return new Stats();
        String name = root.toPath().relativize(canonical.toPath()).toString().replace(File.separatorChar, '/');
        if (skipTree(name)) return new Stats();
        if (canonical.isDirectory()) {
            Stats stats = new Stats();
            if (!tracker.visit(canonical)) return stats;
            String entryName = name.endsWith("/") ? name : name + "/";
            if (!tracker.addEntry(entryName)) return stats;
            ZipEntry entry = new ZipEntry(entryName);
            entry.setTime(canonical.lastModified());
            zos.putNextEntry(entry);
            zos.closeEntry();
            File[] files = canonical.listFiles();
            if (files == null) return stats;
            for (File child : files) stats.add(add(root, child, zos, buffer, running, progress, total, tracker));
            return stats;
        }
        if (!canonical.isFile() || skip(name) || !tracker.addEntry(name)) return new Stats();
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(canonical.lastModified());
        zos.putNextEntry(entry);
        long size = 0;
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(canonical), BUFFER_SIZE)) {
            int read;
            while ((read = bis.read(buffer)) != -1) {
                checkRunning(running);
                zos.write(buffer, 0, read);
                size += read;
            }
        }
        zos.closeEntry();
        total.add(new Stats(1, size));
        if (progress != null) progress.onProgress(total.count, total.size);
        return new Stats(1, size);
    }

    private static boolean inside(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    public static String normalize(String path) {
        if (path == null) return "";
        String value = path.trim().replace('\\', '/');
        value = value.replace("file://", "");
        if (!value.startsWith("/")) return normalizeRelative(value);
        String root = Path.root().getAbsolutePath().replace('\\', '/');
        if (value.startsWith(root)) value = value.substring(root.length());
        if (value.startsWith("/sdcard/")) value = value.substring("/sdcard/".length());
        return normalizeRelative(value);
    }

    private static String normalizeRelative(String path) {
        if (path == null) return "";
        String value = path.trim().replace('\\', '/');
        while (value.startsWith("/")) value = value.substring(1);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty() || ".".equals(value) || value.contains("../") || value.contains("/..") || value.equals("..")) return "";
        return value;
    }

    static boolean skipTree(String path) {
        if (path == null || path.isEmpty()) return false;
        if ("WebHTV/RemoteTrust".equals(path) || path.startsWith("WebHTV/RemoteTrust/")) return true;
        if ("TV/lib".equals(path) || path.startsWith("TV/lib/")) return true;
        if ("TV/log".equals(path) || path.startsWith("TV/log/")) return true;
        if ("TV/LogVar".equals(path) || path.startsWith("TV/LogVar/")) return true;
        if (path.startsWith("TV/cache_")) return true;
        if ("TV/.subtitle_proxy_cache".equals(path) || path.startsWith("TV/.subtitle_proxy_cache/")) return true;
        if ("TV/.ai_subtitle_cache".equals(path) || path.startsWith("TV/.ai_subtitle_cache/")) return true;
        return "TV/.online_subtitle_cache".equals(path) || path.startsWith("TV/.online_subtitle_cache/");
    }

    private static boolean skip(String path) {
        if (skipTree(path)) return true;
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.matches("(?i)(tv-|webhometv-).*\\.bk\\.gz") || AppBackup.isBackupZipName(name)) return true;
        return ".DS_Store".equals(name) || name.startsWith("._");
    }

    private static void checkRunning(BooleanSupplier running) throws IOException {
        if (running != null && !running.getAsBoolean()) throw new IOException("Canceled");
    }

    static final class ArchiveTracker {

        private final Set<String> directories = new HashSet<>();
        private final Set<String> entries = new HashSet<>();
        private int duplicates;

        boolean visit(File directory) throws IOException {
            if (directories.add(directory.getCanonicalPath())) return true;
            duplicates++;
            return false;
        }

        boolean addEntry(String name) {
            if (entries.add(name)) return true;
            duplicates++;
            return false;
        }

        int duplicates() {
            return duplicates;
        }
    }

    private static class Stats {

        private int count;
        private long size;

        private Stats() {
        }

        private Stats(int count, long size) {
            this.count = count;
            this.size = size;
        }

        private void add(Stats stats) {
            count += stats.count;
            size += stats.size;
        }
    }

    public static class Archive {

        private final File file;
        private final int count;
        private final long rawSize;
        private final long zipSize;
        private final List<String> paths;

        private Archive(File file, int count, long rawSize, long zipSize, List<String> paths) {
            this.file = file;
            this.count = count;
            this.rawSize = rawSize;
            this.zipSize = zipSize;
            this.paths = paths;
        }

        public File getFile() {
            return file;
        }

        public int getCount() {
            return count;
        }

        public long getRawSize() {
            return rawSize;
        }

        public long getZipSize() {
            return zipSize;
        }

        public List<String> getPaths() {
            return paths;
        }

        public void delete() {
            Path.clear(file);
        }
    }

    public interface Progress {

        void onProgress(int count, long size);
    }
}
