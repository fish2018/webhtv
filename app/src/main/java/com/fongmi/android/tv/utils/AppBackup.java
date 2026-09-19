package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Backup;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class AppBackup {

    public static final String PREFIX = "bak-";
    public static final String SUFFIX = ".zip";

    private static final String LEGACY_PREFIX = "webhtv-backup-";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT);
    private static final String DATA = "backup.json";
    private static final String MANIFEST = "manifest.json";
    private static final String SHARED = "shared-files.zip";
    private static final String LOGIN = "login-state.zip";
    private static final String APP_FILES = "app-files/";
    private static final int BUFFER_SIZE = 128 * 1024;

    private AppBackup() {
    }

    public static String fileName() {
        return PREFIX + LocalDateTime.now().format(STAMP) + SUFFIX;
    }

    public static boolean isBackup(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName();
        return isBackupZipName(name) || (name.startsWith("tv-") && name.endsWith(".bk.gz"));
    }

    public static boolean isBackupZipName(String name) {
        return hasBackupPrefix(name) && name.endsWith(SUFFIX);
    }

    public static boolean isBackupManifestName(String name) {
        return hasBackupPrefix(name) && name.endsWith(".json");
    }

    public static String backupSortKey(String name) {
        if (!isBackupZipName(name)) return "";
        int prefixLength = name.startsWith(PREFIX) ? PREFIX.length() : LEGACY_PREFIX.length();
        return name.substring(prefixLength, name.length() - SUFFIX.length());
    }

    private static boolean hasBackupPrefix(String name) {
        return name != null && (name.startsWith(PREFIX) || name.startsWith(LEGACY_PREFIX));
    }

    public static File createTemp() throws IOException {
        return createTemp(null);
    }

    public static File createTemp(Progress progress) throws IOException {
        File file = File.createTempFile(PREFIX, SUFFIX, Path.cache());
        create(file, progress);
        return file;
    }

    public static CreateResult create(File target) throws IOException {
        return create(target, null);
    }

    public static synchronized CreateResult create(File target, Progress progress) throws IOException {
        LoginStateSync.Archive login = null;
        try {
            notifyProgress(progress, "整理数据库和设置", 5, 0, 0);
            Backup backup = Backup.create();
            if (backup.getConfig().isEmpty()) throw new IOException("没有可备份的接口配置");
            notifyProgress(progress, "整理登录态和云盘凭据", 30, 0, 0);
            try {
                login = LoginStateSync.createArchive();
            } catch (Throwable e) {
                login = null;
            }
            byte[] data = backup.toString().getBytes(StandardCharsets.UTF_8);
            byte[] manifest = manifest(null, login, 0, 0, backup.getWebHomeExtensionPreferenceCount(), backup.getWebHomeExtensionSourceCount(), "").getBytes(StandardCharsets.UTF_8);
            long total = data.length + manifest.length + appFilesSize(Path.files(), Path.files());
            if (login != null) total += login.getFile().length();
            Counter counter = new Counter(total, progress);
            try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(Path.create(target)), BUFFER_SIZE))) {
                addBytes(output, DATA, data, counter);
                addBytes(output, MANIFEST, manifest, counter);
                if (login != null) addFile(output, LOGIN, login.getFile(), counter);
                addAppFiles(output, Path.files(), Path.files(), counter);
            }
            notifyProgress(progress, "备份完成", 100, target.length(), target.length());
            SpiderDebug.log("backup", "create complete file=%s size=%d", target.getAbsolutePath(), target.length());
            return new CreateResult("");
        } catch (Throwable e) {
            Path.clear(target);
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        } finally {
            if (login != null) login.delete();
        }
    }

    public static RestoreResult restore(File source) throws IOException {
        return restore(source, null);
    }

    public static RestoreResult restore(File source, Progress progress) throws IOException {
        long sourceSize = source == null ? 0 : source.length();
        notifyProgress(progress, "读取备份文件", 5, sourceSize, sourceSize);
        if (!isZip(source)) return restoreLegacy(source, progress);
        File login = null;
        String json = "";
        String manifestJson = "";
        int appFiles = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(source), BUFFER_SIZE))) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    String name = safe(entry.getName());
                    if (name.isEmpty() || entry.isDirectory()) {
                        input.closeEntry();
                        continue;
                    }
                    if (DATA.equals(name)) {
                        json = new String(readEntry(input, buffer), StandardCharsets.UTF_8);
                    } else if (MANIFEST.equals(name)) {
                        manifestJson = new String(readEntry(input, buffer), StandardCharsets.UTF_8);
                    } else if (LOGIN.equals(name)) {
                        login = extractTemp(input, "webhtv-login-restore-", buffer);
                    } else if (name.startsWith(APP_FILES)) {
                        String relative = safe(name.substring(APP_FILES.length()));
                        if (!relative.isEmpty()) {
                            File target = new File(Path.files(), relative).getCanonicalFile();
                            if (inside(Path.files().getCanonicalFile(), target)) {
                                copyEntry(input, target, buffer);
                                appFiles++;
                            }
                        }
                    }
                    input.closeEntry();
                }
            }
            notifyProgress(progress, "校验备份内容", 45, sourceSize, sourceSize);
        } catch (Throwable e) {
            Path.clear(login);
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
        Backup backup = Backup.objectFrom(json);
        try {
            if (backup.getConfig().isEmpty()) throw new IOException("备份缺少接口配置数据");
            String warning = validateManifest(manifestJson, null, backup);
            StringBuilder restoreWarning = new StringBuilder(warning);
            notifyProgress(progress, "恢复登录态和云盘凭据", 55, sourceSize, sourceSize);
            int loginFiles = 0;
            try {
                loginFiles = login == null ? 0 : LoginStateSync.restoreArchive(login);
            } catch (Throwable e) {
                appendWarning(restoreWarning, "登录态和云盘凭据未能完整恢复");
                SpiderDebug.log("backup", "login restore warning error=%s", e.getMessage());
            }
            notifyProgress(progress, "恢复数据库和设置", 70, sourceSize, sourceSize);
            backup.restore(true);
            reload();
            if (restoreWarning.length() > 0) restoreWarning.append("，其余可用数据已恢复");
            String warningText = restoreWarning.toString();
            notifyProgress(progress, warningText.isEmpty() ? "恢复完成" : "恢复完成，但部分数据缺失", 100, sourceSize, sourceSize);
            SpiderDebug.log("backup", "restore complete login=%d app=%d warning=%s", loginFiles, appFiles, warningText);
            return new RestoreResult(0, loginFiles, appFiles, false, warningText);
        } finally {
            Path.clear(login);
        }
    }

    private static RestoreResult restoreLegacy(File source, Progress progress) throws IOException {
        File restore = Path.cache("restore-legacy");
        try {
            FileUtil.gzipDecompress(source, restore);
            Backup backup = Backup.objectFrom(Path.read(restore));
            if (backup.getConfig().isEmpty()) throw new IOException("旧备份文件无有效配置数据");
            notifyProgress(progress, "恢复旧版数据库和设置", 75, source.length(), source.length());
            backup.restore();
            reload();
            notifyProgress(progress, "恢复完成", 100, source.length(), source.length());
            return new RestoreResult(0, 0, 0, true, "");
        } finally {
            Path.clear(restore);
        }
    }

    private static void reload() {
        BaseLoader.get().clear();
        VodConfig.get().clear().init().load(new Callback());
        LiveConfig.get().clear().init().load();
        WallConfig.get().init().load();
        ConfigEvent.common();
        RefreshEvent.keep();
        RefreshEvent.history();
        RefreshEvent.home();
        Backup.refreshWebHomeExtensions();
    }

    private static String manifest(SyncFiles.Archive shared, LoginStateSync.Archive login, int customCspSourceFiles, int customCspFiles, int webHomeExtensionPrefs, int webHomeExtensionSources, String warning) {
        JsonObject object = new JsonObject();
        object.addProperty("app", "WebHTV");
        object.addProperty("version", 3);
        object.addProperty("createdAt", System.currentTimeMillis());
        object.addProperty("sharedFiles", shared == null ? 0 : shared.getCount());
        object.addProperty("loginStateFiles", login == null ? 0 : login.getCount());
        object.addProperty("customCspSourceFiles", customCspSourceFiles);
        object.addProperty("customCspFiles", customCspFiles);
        object.addProperty("webHomeExtensionPrefs", webHomeExtensionPrefs);
        object.addProperty("webHomeExtensionSources", webHomeExtensionSources);
        object.addProperty("warning", warning == null ? "" : warning);
        return App.gson().toJson(object);
    }

    private static String validateManifest(String text, File shared, Backup backup) {
        if (text == null || text.isEmpty()) return "";
        JsonObject object;
        try {
            object = App.gson().fromJson(text, JsonObject.class);
        } catch (Throwable e) {
            SpiderDebug.log("backup", "manifest parse warning error=%s", e.getMessage());
            return "备份清单损坏";
        }
        if (object == null || integer(object, "version") < 3) return "";
        StringBuilder warning = new StringBuilder(string(object, "warning"));
        int expectedCustomCsp = Math.max(integer(object, "customCspSourceFiles"), integer(object, "customCspFiles"));
        try {
            int actualCustomCsp = SyncFiles.countArchiveFiles(shared, SyncFiles.CUSTOM_CSP_PATH);
            if (actualCustomCsp < expectedCustomCsp) appendWarning(warning, "站点注入文件不完整");
        } catch (Throwable e) {
            appendWarning(warning, "站点注入文件无法校验");
        }
        int expectedWebHomePrefs = integer(object, "webHomeExtensionPrefs");
        if (backup.getWebHomeExtensionPreferenceCount() < expectedWebHomePrefs) appendWarning(warning, "WebHome 扩展配置不完整");
        int expectedWebHomeSources = integer(object, "webHomeExtensionSources");
        if (backup.getWebHomeExtensionSourceCount() < expectedWebHomeSources) appendWarning(warning, "WebHome 扩展源不完整");
        if (warning.length() > 0) SpiderDebug.log("backup", "restore manifest warning=%s", warning);
        return warning.toString();
    }

    private static void appendWarning(StringBuilder builder, String message) {
        if (builder.length() > 0) builder.append("、");
        builder.append(message);
    }

    private static int integer(JsonObject object, String key) {
        try {
            return object.has(key) ? Math.max(0, object.get(key).getAsInt()) : 0;
        } catch (Throwable e) {
            return 0;
        }
    }

    private static String string(JsonObject object, String key) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
        } catch (Throwable e) {
            return "";
        }
    }

    private static void addAppFiles(ZipOutputStream output, File root, File file, Counter counter) throws IOException {
        if (file == null || !file.exists() || skipAppFile(root, file)) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) addAppFiles(output, root, child, counter);
            return;
        }
        if (!file.isFile()) return;
        String relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
        addFile(output, APP_FILES + relative, file, counter);
    }

    private static long appFilesSize(File root, File file) {
        if (file == null || !file.exists() || skipAppFile(root, file)) return 0;
        if (file.isFile()) return file.length();
        long size = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) size += appFilesSize(root, child);
        return size;
    }

    private static boolean skipAppFile(File root, File file) {
        String relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
        if (relative.isEmpty()) return false;
        String first = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : relative;
        if ("mpv".equals(first)) return "mpv/subfont.ttf".equalsIgnoreCase(relative);
        if (first.startsWith("wallpaper_") && !"wallpaper_cache".equals(first)) return false;
        return true;
    }

    private static void addBytes(ZipOutputStream output, String name, byte[] data, Counter counter) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(System.currentTimeMillis());
        output.putNextEntry(entry);
        output.write(data);
        output.closeEntry();
        counter.add(data.length);
    }

    private static void addFile(ZipOutputStream output, String name, File file, Counter counter) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(file.lastModified());
        output.putNextEntry(entry);
        byte[] buffer = new byte[BUFFER_SIZE];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                counter.add(read);
            }
        }
        output.closeEntry();
    }

    private static byte[] readEntry(ZipInputStream input, byte[] buffer) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    private static File extractTemp(ZipInputStream input, String prefix, byte[] buffer) throws IOException {
        File file = File.createTempFile(prefix, ".zip", Path.cache());
        try (FileOutputStream output = new FileOutputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        return file;
    }

    private static void copyEntry(ZipInputStream input, File target, byte[] buffer) throws IOException {
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(Path.create(target)), BUFFER_SIZE)) {
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
    }

    private static boolean isZip(File file) throws IOException {
        if (file == null || !file.isFile() || file.length() < 4) return false;
        try (FileInputStream input = new FileInputStream(file)) {
            return input.read() == 'P' && input.read() == 'K';
        }
    }

    private static String safe(String path) {
        if (path == null) return "";
        String value = path.replace('\\', '/');
        while (value.startsWith("/")) value = value.substring(1);
        if (value.isEmpty() || value.equals("..") || value.contains("../") || value.contains("/..")) return "";
        return value;
    }

    private static boolean inside(File root, File file) throws IOException {
        String base = root.getCanonicalPath();
        String path = file.getCanonicalPath();
        return path.equals(base) || path.startsWith(base + File.separator);
    }

    private static void notifyProgress(Progress progress, String stage, int percent, long bytes, long total) {
        if (progress != null) progress.onProgress(stage, percent, bytes, total);
    }

    public interface Progress {

        void onProgress(String stage, int percent, long bytes, long total);
    }

    public static final class CreateResult {

        public final String warning;

        private CreateResult(String warning) {
            this.warning = warning == null ? "" : warning;
        }

        public boolean hasWarning() {
            return !warning.isEmpty();
        }
    }

    private static final class Counter {

        private final long total;
        private final Progress progress;
        private long written;

        private Counter(long total, Progress progress) {
            this.total = total;
            this.progress = progress;
        }

        private void add(long size) {
            written += size;
            int percent = total <= 0 ? 40 : 40 + (int) Math.min(55, written * 55 / total);
            notifyProgress(progress, "写入完整备份", percent, written, total);
        }
    }

    public static final class RestoreResult {

        public final int sharedFiles;
        public final int loginFiles;
        public final int appFiles;
        public final boolean legacy;
        public final String warning;

        private RestoreResult(int sharedFiles, int loginFiles, int appFiles, boolean legacy, String warning) {
            this.sharedFiles = sharedFiles;
            this.loginFiles = loginFiles;
            this.appFiles = appFiles;
            this.legacy = legacy;
            this.warning = warning == null ? "" : warning;
        }

        public int fileCount() {
            return sharedFiles + loginFiles + appFiles;
        }

        public boolean hasWarning() {
            return !warning.isEmpty();
        }
    }
}
