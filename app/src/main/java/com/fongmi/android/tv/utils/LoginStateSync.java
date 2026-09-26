package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Xml;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.gitcloud.secure.GitCloudTokenStore;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class LoginStateSync {

    public static final String PART_NAME = "loginStateFiles";

    private static final int BUFFER_SIZE = 128 * 1024;
    private static final long MAX_SYNC_FILE_SIZE = 4L * 1024 * 1024;
    private static final long MAX_SCAN_FILE_SIZE = 512L * 1024;
    private static final int TEXT_SAMPLE_SIZE = 4 * 1024;
    private static final int TEXT_PREVIEW_SIZE = 256 * 1024;
    private static final int MAX_TREE_ITEMS = 500;
    private static final int MAX_PENDING_PATHS = 512;
    private static final String ROOT_APP = "app";
    private static final String ROOT_SDCARD = "sdcard";
    private static final String ROOT_APP_NAME = "App 私有目录";
    private static final String ROOT_SDCARD_NAME = "共享存储";
    private static final String GIT_CLOUD_TOKENS_ENTRY = ROOT_APP + "/git_cloud_tokens.json";
    private static final String GIT_CLOUD_TOKENS_PREF = ROOT_APP + "/shared_prefs/git_cloud_tokens.xml";
    private static final Set<String> TEXT_EXTENSIONS = new LinkedHashSet<>(Arrays.asList(
            "cfg", "conf", "config", "cookie", "cookies", "css", "csv", "htm", "html", "ini", "js",
            "json", "log", "md", "mjs", "properties", "text", "toml", "ts", "txt", "xml", "yaml", "yml"
    ));
    private static final Set<String> BINARY_EXTENSIONS = new LinkedHashSet<>(Arrays.asList(
            "7z", "aar", "apk", "bin", "bmp", "class", "db", "dex", "gif", "gz", "jar", "jpeg", "jpg",
            "keystore", "mp3", "mp4", "o", "otf", "p12", "png", "prof", "so", "sqlite", "sqlite3",
            "ttf", "webm", "webp", "zip"
    ));

    private static final long MAX_CONTENT_SCAN_BYTES = 32L * 1024 * 1024;
    private static final String LEARNING_CACHE_DIR = "login-state-learning";
    private static final String SNAPSHOT_CACHE_FILE = "snapshot.json";
    private static final String FINDINGS_CACHE_FILE = "findings.json";
    private static final Object LEARNING_LOCK = new Object();
    private static volatile File cachedAppRoot;
    private static volatile File cachedSdcardRoot;

    private static final List<String> LOGIN_MARKERS = Arrays.asList(
            "cookie", "cookies", "ck", "token", "access_token", "refresh_token", "auth", "oauth",
            "session", "sid", "passport", "csrf", "kps", "__puus", "__pus", "member",
            "quark", "uc", "alipan", "aliyun", "baidu", "pan", "drive", "mydrive", "usestat"
    );

    public static void beginLearning() {
        long startedAt = SystemClock.elapsedRealtime();
        Snapshot snapshot = new Snapshot(snapshotEntries());
        synchronized (LEARNING_LOCK) {
            String json = App.gson().toJson(snapshot);
            boolean cached = writeLearningCache(SNAPSHOT_CACHE_FILE, json);
            if (cached) {
                if (!TextUtils.isEmpty(Setting.getLoginStateSnapshot())) Setting.putLoginStateSnapshot("");
            } else {
                Setting.putLoginStateSnapshot(json);
            }
            clearLearningCache(FINDINGS_CACHE_FILE);
            if (!TextUtils.isEmpty(Setting.getLoginStateFindings())) Setting.putLoginStateFindings("");
        }
        SpiderDebug.log("sync", "login state learning started files=%d elapsedMs=%d", snapshot.entries.size(), SystemClock.elapsedRealtime() - startedAt);
    }

    public static LearnResult finishLearning() {
        long startedAt = SystemClock.elapsedRealtime();
        String snapshotJson;
        synchronized (LEARNING_LOCK) {
            snapshotJson = readLearningCache(SNAPSHOT_CACHE_FILE);
            if (TextUtils.isEmpty(snapshotJson)) snapshotJson = Setting.getLoginStateSnapshot();
        }
        Snapshot before = snapshotFromJson(snapshotJson);
        if (before.entries.isEmpty()) {
            SpiderDebug.log("sync", "login state learning finish skipped reason=no-snapshot elapsedMs=%d", SystemClock.elapsedRealtime() - startedAt);
            return new LearnResult(Collections.emptyList(), Collections.emptyList(), false);
        }
        List<Entry> after = snapshotEntries();
        long scanFinishedAt = SystemClock.elapsedRealtime();
        Map<String, Entry> old = map(before.entries);
        List<Candidate> candidates = new ArrayList<>();
        ScanBudget budget = new ScanBudget(MAX_CONTENT_SCAN_BYTES);
        for (Entry entry : after) {
            Entry previous = old.get(entry.path);
            if (previous != null && previous.modified == entry.modified && previous.size == entry.size) continue;
            Candidate candidate = candidate(entry, budget);
            if (candidate != null) candidates.add(candidate);
        }
        List<String> selected = mergeLearned(candidates, true);
        List<String> pending = mergePending(candidates);
        long scoreFinishedAt = SystemClock.elapsedRealtime();
        synchronized (LEARNING_LOCK) {
            clearLearningCache(SNAPSHOT_CACHE_FILE);
            String json = App.gson().toJson(candidates);
            boolean cached = writeLearningCache(FINDINGS_CACHE_FILE, json);
            if (cached) {
                if (!TextUtils.isEmpty(Setting.getLoginStateFindings())) Setting.putLoginStateFindings("");
            } else {
                Setting.putLoginStateFindings(json);
            }
            if (!TextUtils.isEmpty(Setting.getLoginStateSnapshot())) Setting.putLoginStateSnapshot("");
        }
        SpiderDebug.log("sync", "login state learning finished changed=%d selected=%d pending=%d contentBytes=%d skippedContent=%d scanMs=%d scoreMs=%d totalMs=%d", candidates.size(), selected.size(), pending.size(), budget.consumed(), budget.skipped(), scanFinishedAt - startedAt, scoreFinishedAt - scanFinishedAt, SystemClock.elapsedRealtime() - startedAt);
        return new LearnResult(selected, pending, true);
    }

    public static boolean hasLearningSnapshot() {
        return hasLearningCache(SNAPSHOT_CACHE_FILE) || !TextUtils.isEmpty(Setting.getLoginStateSnapshot());
    }

    public static int learnedCount() {
        return learnedPaths().size();
    }

    public static List<String> learnedPaths() {
        return normalizeLines(Setting.getLoginStatePaths());
    }

    public static List<String> pendingPaths() {
        List<String> result = prunePending(learnedPaths(), normalizeLines(Setting.getLoginStatePendingPaths()));
        return result.size() <= MAX_PENDING_PATHS ? result : new ArrayList<>(result.subList(0, MAX_PENDING_PATHS));
    }

    public static List<Candidate> findings() {
        try {
            String json;
            synchronized (LEARNING_LOCK) {
                json = readLearningCache(FINDINGS_CACHE_FILE);
                if (TextUtils.isEmpty(json)) json = Setting.getLoginStateFindings();
            }
            Candidate[] array = App.gson().fromJson(json, Candidate[].class);
            if (array == null) return Collections.emptyList();
            List<Candidate> result = new ArrayList<>(Arrays.asList(array));
            result.removeIf(item -> item == null || isIgnoredLearningPath(item.path));
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static void savePaths(List<String> paths) {
        List<String> learned = normalizePathList(paths);
        Setting.putLoginStatePaths(pathsText(learned));
        Setting.putLoginStatePendingPaths(pathsText(prunePending(learned, pendingPaths())));
    }

    public static void resetLearningResults() {
        clearLearningCache(FINDINGS_CACHE_FILE);
        Setting.putLoginStatePendingPaths("");
        if (!TextUtils.isEmpty(Setting.getLoginStateFindings())) Setting.putLoginStateFindings("");
    }

    public static List<String> confirmPending() {
        LinkedHashSet<String> merged = new LinkedHashSet<>(learnedPaths());
        merged.addAll(pendingPaths());
        List<String> result = new ArrayList<>(merged);
        Setting.putLoginStatePaths(pathsText(result));
        Setting.putLoginStatePendingPaths("");
        return result;
    }

    public static String pathsText(List<String> paths) {
        return TextUtils.join("\n", normalizePathList(paths));
    }

    public static List<PathState> pathStates(List<String> paths) {
        List<PathState> result = new ArrayList<>();
        for (String path : normalizePathList(paths)) result.add(pathState(path));
        return result;
    }

    public static PathState pathState(String path) {
        path = normalize(path);
        try {
            File file = fileForPath(path);
            FileKind kind = kind(path, file);
            return new PathState(path, displayPath(path), file.exists(), file.isFile(), file.exists() ? file.length() : 0, file.exists() ? file.lastModified() : 0, kind.type, kind.text, kind.previewable);
        } catch (Exception e) {
            return new PathState(path, displayPath(path), false, false, 0, 0, "missing", false, false);
        }
    }

    public static Tree tree(String path) {
        path = normalize(path);
        List<TreeItem> items = new ArrayList<>();
        String parent = parent(path);
        boolean valid = true;
        try {
            if (path.isEmpty()) {
                items.add(new TreeItem(ROOT_APP_NAME, ROOT_APP, true, 0, appRoot().lastModified(), false));
                items.add(new TreeItem(ROOT_SDCARD_NAME, ROOT_SDCARD, true, 0, sdcardRoot().lastModified(), false));
                return new Tree(path, ".", true, items, items.size(), false);
            }
            Parsed parsed = parse(path);
            if (parsed == null) return new Tree("", ".", false, items, 0, false);
            File root = parsed.root;
            File dir = (parsed.relative.isEmpty() ? root : new File(root, parsed.relative)).getCanonicalFile();
            if (!inside(root, dir) || !dir.isDirectory()) {
                path = "";
                parent = ".";
                valid = false;
            } else {
                File[] files = dir.listFiles(file -> isVisible(root, parsed.key, file));
                if (files != null) Arrays.sort(files, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                int total = files == null ? 0 : files.length;
                int count = 0;
                for (File file : files == null ? new File[0] : files) {
                    if (count++ >= MAX_TREE_ITEMS) break;
                    String child = relative(root, parsed.key, file);
                    if (!child.isEmpty()) items.add(new TreeItem(file.getName(), child, file.isDirectory(), file.isFile() ? file.length() : 0, file.lastModified(), true, kind(child, file)));
                }
                return new Tree(path, parent, valid, items, total, total > items.size());
            }
        } catch (Exception e) {
            valid = false;
        }
        return new Tree(path, parent, valid, items, items.size(), false);
    }

    public static String normalizePath(String path) {
        return normalize(path);
    }

    public static String displayPath(String path) {
        Parsed parsed = parse(path);
        if (parsed == null) return "";
        return parsed.root.getAbsolutePath() + (parsed.relative.isEmpty() ? "" : "/" + parsed.relative);
    }

    public static String read(String path) throws IOException {
        return preview(path).getContent();
    }

    public static TextPreview preview(String path) throws IOException {
        path = normalize(path);
        File file = fileForPath(path);
        if (!isReadableFile(path, file)) throw new IOException("文件不可访问");
        TextProbe probe = probeText(readHead(file, (int) Math.min(file.length(), TEXT_SAMPLE_SIZE)), extension(file.getName()));
        if (!probe.text) throw new IOException("仅支持查看文本文件");
        int limit = (int) Math.min(file.length(), (long) TEXT_PREVIEW_SIZE + 4);
        byte[] data = readHead(file, limit);
        boolean truncated = file.length() > TEXT_PREVIEW_SIZE;
        int length = truncated ? safeTextLength(data, TEXT_PREVIEW_SIZE, probe.charset) : data.length;
        String content = decodeText(data, length, probe.charset);
        boolean editable = !truncated && file.length() <= TEXT_PREVIEW_SIZE;
        return new TextPreview(path, displayPath(path), content, file.length(), truncated, editable, true, probe.charset.name());
    }

    public static void write(String path, String text) throws IOException {
        path = normalize(path);
        if (!isSafePath(path)) throw new IOException("路径无效");
        byte[] data = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        if (data.length > TEXT_PREVIEW_SIZE) throw new IOException("文本超过可编辑上限");
        File file = fileForPath(path);
        Parsed parsed = parse(path);
        if (parsed == null || !inside(parsed.root, file)) throw new IOException("路径无效");
        if (file.exists() && file.isFile() && !kind(path, file).text) throw new IOException("仅支持编辑文本文件");
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(create(file)), BUFFER_SIZE)) {
            output.write(data);
        }
        savePaths(appendPath(learnedPaths(), path));
        SpiderDebug.log("sync", "login state file saved path=%s size=%d", path, data.length);
    }

    public static Archive createArchive() throws IOException {
        List<Entry> entries = selectedEntries();
        JsonObject gitCloudTokens = GitCloudTokenStore.exportTokens();
        if (entries.isEmpty() && gitCloudTokens.size() == 0) {
            SpiderDebug.log("sync", "archive login state skipped reason=no-selected-paths");
            return null;
        }
        File archive = File.createTempFile("webhtv-login-state-", ".zip", Path.cache());
        int count = 0;
        long size = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archive), BUFFER_SIZE))) {
            for (Entry entry : entries) {
                String path = normalize(entry.path);
                if (isGitCloudTokenPrefsPath(path)) continue;
                File file = fileForPath(path);
                if (!isSyncableFile(path, file)) {
                    SpiderDebug.log("sync", "login state skip path=%s exists=%s file=%s size=%d", path, file.exists(), file.isFile(), file.exists() ? file.length() : 0);
                    continue;
                }
                ZipEntry zipEntry = new ZipEntry(path);
                zipEntry.setTime(file.lastModified());
                zos.putNextEntry(zipEntry);
                long written = copy(file, zos, buffer);
                zos.closeEntry();
                count++;
                size += written;
            }
            if (gitCloudTokens.size() > 0) {
                byte[] data = App.gson().toJson(gitCloudTokens).getBytes(StandardCharsets.UTF_8);
                ZipEntry zipEntry = new ZipEntry(GIT_CLOUD_TOKENS_ENTRY);
                zipEntry.setTime(System.currentTimeMillis());
                zos.putNextEntry(zipEntry);
                zos.write(data);
                zos.closeEntry();
                count++;
                size += data.length;
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
        Archive result = new Archive(archive, count, size, archive.length());
        SpiderDebug.log("sync", "archive login state count=%d size=%d zip=%d file=%s", result.count, result.rawSize, result.zipSize, archive.getAbsolutePath());
        return result;
    }

    public static int restoreArchive(File archive) throws IOException {
        if (archive == null || !archive.isFile() || archive.length() <= 0) return 0;
        int count = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive), BUFFER_SIZE))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String path = normalize(entry.getName());
                if (path.isEmpty() || entry.isDirectory() || !isSafePath(path) || isIgnoredLearningPath(path)) {
                    zis.closeEntry();
                    continue;
                }
                if (GIT_CLOUD_TOKENS_ENTRY.equals(path)) {
                    int restored = restoreGitCloudTokens(readEntry(zis, buffer));
                    if (restored > 0) count++;
                    zis.closeEntry();
                    continue;
                }
                if (isGitCloudTokenPrefsPath(path)) {
                    zis.closeEntry();
                    continue;
                }
                if (isSharedPrefsPath(path)) {
                    int restored = restoreSharedPrefs(path, readEntry(zis, buffer));
                    if (restored > 0) count++;
                    zis.closeEntry();
                    continue;
                }
                File out = fileForPath(path);
                Parsed parsed = parse(path);
                if (parsed == null || !inside(parsed.root, out)) {
                    zis.closeEntry();
                    continue;
                }
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(create(out)), BUFFER_SIZE)) {
                    int read;
                    while ((read = zis.read(buffer)) != -1) bos.write(buffer, 0, read);
                }
                if (entry.getTime() > 0) out.setLastModified(entry.getTime());
                count++;
                zis.closeEntry();
            }
        }
        SpiderDebug.log("sync", "restore login state count=%d file=%s", count, archive.getAbsolutePath());
        return count;
    }

    private static int restoreGitCloudTokens(byte[] data) throws IOException {
        if (data == null || data.length == 0) return 0;
        try {
            JsonObject object = App.gson().fromJson(new String(data, StandardCharsets.UTF_8), JsonObject.class);
            return GitCloudTokenStore.importTokens(object);
        } catch (Throwable e) {
            throw new IOException("Git cloud token restore failed", e);
        }
    }

    private static List<Entry> selectedEntries() {
        List<Entry> result = new ArrayList<>();
        for (String path : learnedPaths()) {
            try {
                collect(fileForPath(path), path, result, false);
            } catch (Exception ignored) {
            }
        }
        return dedupe(result);
    }

    private static List<Entry> snapshotEntries() {
        List<Entry> result = new ArrayList<>();
        collect(appRoot(), ROOT_APP, result, true);
        for (String path : externalLearningPaths()) {
            try {
                collect(fileForPath(path), path, result, true);
            } catch (Exception ignored) {
            }
        }
        return dedupe(result);
    }

    private static void collect(File file, String path, List<Entry> result, boolean excludeNoise) {
        try {
            path = normalize(path);
            if (isIgnoredLearningPath(path)) return;
            Parsed parsed = parse(path);
            if (parsed == null || !file.exists()) return;
            File current = file.getCanonicalFile();
            if (!inside(parsed.root, current) || (excludeNoise && isExcludedFromSnapshot(path, current))) return;
            if (current.isDirectory()) {
                File[] children = current.listFiles();
                if (children == null) return;
                for (File child : children) collect(child, path + "/" + child.getName(), result, excludeNoise);
                return;
            }
            if (!current.isFile() || !isSafePath(path)) return;
            result.add(new Entry(path, current.length(), current.lastModified()));
        } catch (Exception ignored) {
        }
    }

    private static Candidate candidate(Entry entry, ScanBudget budget) {
        String path = normalize(entry.path);
        if (isIgnoredLearningPath(path)) return null;
        Parsed parsed = parse(path);
        if (parsed == null) return null;
        File file;
        try {
            file = fileForPath(path);
        } catch (Exception e) {
            return null;
        }
        String relative = parsed.relative.toLowerCase(Locale.ROOT);
        boolean cache = isCacheRelative(relative);
        Score score = scorePath(path, relative, file, cache, entry.size, budget);
        boolean selected = score.level == Level.HIGH && !cache;
        return new Candidate(path, displayPath(path), score.level.name().toLowerCase(Locale.ROOT), score.reason, selected, entry.size, entry.modified);
    }

    private static Score scorePath(String path, String relative, File file, boolean cache, long size, ScanBudget budget) {
        String name = basename(relative);
        if (size > MAX_SYNC_FILE_SIZE) return new Score(Level.LOW, "文件较大，需要确认是否为登录态");
        if (isSharedPrefsPath(path)) {
            List<String> keys = sharedPrefLoginKeys(file);
            if (!keys.isEmpty()) return new Score(Level.HIGH, "SharedPreferences 登录字段：" + TextUtils.join(", ", keys));
            if (nameLooksLikeLogin(name)) return new Score(Level.HIGH, "SharedPreferences 文件名疑似登录态");
            return new Score(Level.MEDIUM, "SharedPreferences 在学习期间发生变化");
        }
        if (isWebViewCookies(relative)) return new Score(Level.HIGH, "WebView Cookie 数据库");
        if (isWebViewRuntime(relative)) return new Score(Level.MEDIUM, "WebView 存储在学习期间发生变化");
        if (relative.startsWith("databases/")) return new Score(Level.MEDIUM, "数据库在学习期间发生变化");
        if (nameLooksLikeLogin(name)) {
            if (cache) return new Score(Level.MEDIUM, "缓存目录内疑似登录态文件，需要确认");
            return new Score(Level.HIGH, "文件名疑似登录态");
        }
        if (file.length() <= MAX_SCAN_FILE_SIZE) {
            if (!budget.tryAcquire(file.length())) return new Score(Level.LOW, "变化文件过多，已跳过内容扫描");
            ContentScore content = scoreContent(file);
            if (content.hit) {
                if (cache) return new Score(Level.MEDIUM, "缓存目录内包含登录关键字，需要确认");
                return new Score(content.strong ? Level.HIGH : Level.MEDIUM, "文件内容包含登录关键字：" + content.marker);
            }
        }
        if (cache) return new Score(Level.LOW, "缓存文件在学习期间发生变化");
        return new Score(Level.LOW, "文件在学习期间发生变化");
    }

    private static List<String> sharedPrefLoginKeys(File file) {
        List<String> result = new ArrayList<>();
        try (FileInputStream input = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, StandardCharsets.UTF_8.name());
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                String name = parser.getAttributeValue(null, "name");
                if (!TextUtils.isEmpty(name) && keyLooksLikeLogin(name)) result.add(name);
            }
        } catch (Exception ignored) {
        }
        return result.size() > 8 ? result.subList(0, 8) : result;
    }

    private static ContentScore scoreContent(File file) {
        byte[] data = readHead(file, (int) Math.min(file.length(), MAX_SCAN_FILE_SIZE));
        if (data.length == 0 || !looksText(data)) return ContentScore.none();
        String text = new String(data, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        for (String marker : LOGIN_MARKERS) {
            if (!text.contains(marker)) continue;
            boolean strong = marker.contains("cookie") || marker.contains("token") || marker.equals("quark") || marker.equals("kps") || marker.equals("__puus") || marker.equals("passport");
            return new ContentScore(true, strong, marker);
        }
        return ContentScore.none();
    }

    private static List<String> mergeLearned(List<Candidate> candidates, boolean selectedOnly) {
        LoginStatePathIndex learned = new LoginStatePathIndex(learnedPaths());
        for (Candidate candidate : candidates) {
            if (selectedOnly && !candidate.selected) continue;
            learned.add(candidate.path);
        }
        List<String> result = learned.asList();
        Setting.putLoginStatePaths(pathsText(result));
        return result;
    }

    private static List<String> mergePending(List<Candidate> candidates) {
        LoginStatePathIndex pending = new LoginStatePathIndex(pendingPaths());
        LoginStatePathIndex learned = new LoginStatePathIndex(learnedPaths());
        int omitted = 0;
        for (Candidate candidate : candidates) {
            // Low-confidence changes are kept in findings for inspection, but
            // must not turn a busy cache directory into thousands of pending
            // login-state paths. Only medium-confidence candidates need a
            // manual confirmation; high-confidence candidates are selected.
            if (candidate.selected || !"medium".equals(candidate.confidence) || learned.hasAncestor(candidate.path)) continue;
            if (pending.size() >= MAX_PENDING_PATHS && !pending.hasAncestor(candidate.path) && !pending.hasDescendant(candidate.path)) {
                omitted++;
                continue;
            }
            pending.add(candidate.path);
        }
        List<String> result = prunePending(learned.asList(), pending.asList());
        Setting.putLoginStatePendingPaths(pathsText(result));
        if (omitted > 0) SpiderDebug.log("sync", "login state pending capped limit=%d omitted=%d", MAX_PENDING_PATHS, omitted);
        return result;
    }

    private static void addCovered(LinkedHashSet<String> paths, String path) {
        path = normalize(path);
        if (path.isEmpty() || !isSafePath(path)) return;
        LoginStatePathIndex index = new LoginStatePathIndex(paths);
        index.add(path);
        paths.clear();
        paths.addAll(index.asList());
    }

    private static List<String> prunePending(List<String> learned, List<String> pending) {
        LoginStatePathIndex selected = new LoginStatePathIndex(normalizePathList(learned));
        LoginStatePathIndex result = new LoginStatePathIndex();
        for (String path : normalizePathList(pending)) if (!selected.hasAncestor(path)) result.add(path);
        return result.asList();
    }

    private static boolean covers(String parent, String child) {
        parent = normalize(parent);
        child = normalize(child);
        return !parent.isEmpty() && (parent.equals(child) || child.startsWith(parent + "/"));
    }

    private static List<String> normalizeLines(String text) {
        if (TextUtils.isEmpty(text)) return new ArrayList<>();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String line : text.split("[\\r\\n]+")) {
            String path = normalize(line);
            if (!path.isEmpty() && isSafePath(path) && !isIgnoredLearningPath(path)) values.add(path);
        }
        return new LoginStatePathIndex(values).asList();
    }

    private static List<String> normalizePathList(List<String> paths) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String path : paths == null ? Collections.<String>emptyList() : paths) {
            path = normalize(path);
            if (!path.isEmpty() && isSafePath(path) && !isIgnoredLearningPath(path)) values.add(path);
        }
        return new LoginStatePathIndex(values).asList();
    }

    private static List<String> appendPath(List<String> paths, String path) {
        LinkedHashSet<String> result = new LinkedHashSet<>(paths == null ? Collections.emptyList() : paths);
        addCovered(result, path);
        return new ArrayList<>(result);
    }

    private static List<Entry> dedupe(List<Entry> entries) {
        Map<String, Entry> map = new HashMap<>();
        for (Entry entry : entries) map.put(entry.path, entry);
        List<Entry> result = new ArrayList<>(map.values());
        result.sort((a, b) -> a.path.compareToIgnoreCase(b.path));
        return result;
    }

    private static Map<String, Entry> map(List<Entry> entries) {
        Map<String, Entry> map = new HashMap<>();
        for (Entry entry : entries) map.put(entry.path, entry);
        return map;
    }

    private static Snapshot snapshotFromJson(String json) {
        try {
            Snapshot snapshot = App.gson().fromJson(json, Snapshot.class);
            return snapshot == null || snapshot.entries == null ? new Snapshot(Collections.emptyList()) : snapshot;
        } catch (Exception e) {
            return new Snapshot(Collections.emptyList());
        }
    }

    private static List<String> externalLearningPaths() {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (String path : SyncFiles.getPaths(Setting.getSyncPaths())) {
            path = normalize(ROOT_SDCARD + "/" + path);
            if (!path.isEmpty()) paths.add(path);
        }
        for (String path : learnedPaths()) {
            Parsed parsed = parse(path);
            if (parsed != null && ROOT_SDCARD.equals(parsed.key) && !parsed.relative.isEmpty()) paths.add(path);
        }
        return new ArrayList<>(paths);
    }

    private static boolean isExcludedFromSnapshot(String path, File file) {
        Parsed parsed = parse(path);
        if (parsed == null) return true;
        String relative = parsed.relative.toLowerCase(Locale.ROOT);
        if (relative.isEmpty()) return false;
        if (isIgnoredLearningPath(relative)) return true;
        if (relative.equals("code_cache") || relative.startsWith("code_cache/")) return true;
        if (relative.startsWith("files/chaquopy/")) return true;
        if (relative.startsWith("files/pvideo-") || relative.equals("files/profileinstalled")) return true;
        if (relative.contains("/http cache/") || relative.contains("/gpucache/") || relative.contains("/cache storage/")) return true;
        if (relative.startsWith("cache/image_manager_disk_cache/")) return true;
        if (relative.startsWith("cache/" + LEARNING_CACHE_DIR + "/")) return true;
        if (relative.startsWith("no_backup/" + LEARNING_CACHE_DIR + "/")) return true;
        if (relative.startsWith("cache/jar/oat/")) return true;
        if (relative.equals("cache/tv.lck")) return true;
        if (relative.equals("app_webview/browsermetrics-spare.pma") || relative.equals("app_webview/webview_data.lock")) return true;
        if (relative.endsWith(".cur.prof")) return true;
        return false;
    }

    private static boolean isSyncableFile(String path, File file) throws IOException {
        Parsed parsed = parse(path);
        return parsed != null
                && isSafePath(path)
                && inside(parsed.root, file)
                && file.isFile()
                && file.length() <= MAX_SYNC_FILE_SIZE;
    }

    private static boolean isReadableFile(String path, File file) throws IOException {
        Parsed parsed = parse(path);
        return parsed != null
                && isSafePath(path)
                && inside(parsed.root, file)
                && file.isFile();
    }

    private static boolean isSharedPrefsPath(String path) {
        return !TextUtils.isEmpty(sharedPrefsName(path));
    }

    private static boolean isGitCloudTokenPrefsPath(String path) {
        return TextUtils.equals(normalize(path), GIT_CLOUD_TOKENS_PREF);
    }

    private static String sharedPrefsName(String path) {
        Parsed parsed = parse(path);
        if (parsed == null || !ROOT_APP.equals(parsed.key)) return "";
        String prefix = "shared_prefs/";
        String relative = parsed.relative;
        if (!relative.startsWith(prefix) || !relative.endsWith(".xml")) return "";
        String file = relative.substring(prefix.length());
        if (file.isEmpty() || file.contains("/")) return "";
        return file.substring(0, file.length() - 4);
    }

    private static int restoreSharedPrefs(String path, byte[] data) throws IOException {
        String name = sharedPrefsName(path);
        if (TextUtils.isEmpty(name) || data == null || data.length == 0) return 0;
        try {
            SharedPreferences.Editor editor = App.get().getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            int count = parseSharedPrefs(data, editor);
            if (count <= 0) return 0;
            if (!editor.commit()) throw new IOException("SharedPreferences commit failed");
            return count;
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
    }

    private static int parseSharedPrefs(byte[] data, SharedPreferences.Editor editor) throws Exception {
        int count = 0;
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(data), StandardCharsets.UTF_8.name());
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event != XmlPullParser.START_TAG) continue;
            String tag = parser.getName();
            String name = parser.getAttributeValue(null, "name");
            if (TextUtils.isEmpty(name)) continue;
            switch (tag) {
                case "string" -> {
                    editor.putString(name, parser.nextText());
                    count++;
                }
                case "boolean" -> {
                    editor.putBoolean(name, Boolean.parseBoolean(parser.getAttributeValue(null, "value")));
                    count++;
                }
                case "int" -> {
                    editor.putInt(name, Integer.parseInt(parser.getAttributeValue(null, "value")));
                    count++;
                }
                case "long" -> {
                    editor.putLong(name, Long.parseLong(parser.getAttributeValue(null, "value")));
                    count++;
                }
                case "float" -> {
                    editor.putFloat(name, Float.parseFloat(parser.getAttributeValue(null, "value")));
                    count++;
                }
                case "set" -> {
                    editor.putStringSet(name, readStringSet(parser));
                    count++;
                }
            }
        }
        return count;
    }

    private static Set<String> readStringSet(XmlPullParser parser) throws Exception {
        Set<String> values = new LinkedHashSet<>();
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.END_TAG && "set".equals(parser.getName())) break;
            if (event == XmlPullParser.START_TAG && "string".equals(parser.getName())) values.add(parser.nextText());
        }
        return values;
    }

    private static boolean isSafePath(String path) {
        Parsed parsed = parse(path);
        return parsed != null && !parsed.relative.isEmpty();
    }

    private static boolean keyLooksLikeLogin(String key) {
        String value = key.toLowerCase(Locale.ROOT);
        if (value.length() <= 2 && !"ck".equals(value)) return false;
        for (String marker : LOGIN_MARKERS) {
            if ("ck".equals(marker)) {
                if (value.equals("ck") || value.endsWith("_ck") || value.endsWith("-ck")) return true;
            } else if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nameLooksLikeLogin(String name) {
        String value = name.toLowerCase(Locale.ROOT);
        if (value.equals("fid") || value.endsWith("_fid") || value.endsWith("-fid")) return true;
        if (value.equals("cookies") || value.equals("cookie") || value.endsWith(".cookies")) return true;
        return keyLooksLikeLogin(value);
    }

    private static boolean isCacheRelative(String relative) {
        if (relative.equals("cache") || relative.startsWith("cache/")) return true;
        for (String segment : relative.split("/")) {
            if ("cache".equals(segment) || "code_cache".equals(segment) || "code cache".equals(segment) || "gpucache".equals(segment)) return true;
        }
        return false;
    }

    private static boolean isWebViewCookies(String relative) {
        return relative.equals("app_webview/default/cookies")
                || relative.equals("app_hws_webview/default/cookies")
                || relative.equals("app_webview/default/network/cookies")
                || relative.equals("app_hws_webview/default/network/cookies");
    }

    private static boolean isWebViewRuntime(String relative) {
        return relative.startsWith("app_webview/") || relative.startsWith("app_hws_webview/");
    }

    private static String basename(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private static boolean looksText(byte[] data) {
        return probeText(data, "").text;
    }

    private static FileKind kind(String path, File file) {
        if (file == null || !file.exists()) return FileKind.missing();
        if (file.isDirectory()) return FileKind.dir();
        if (!file.isFile()) return FileKind.binaryFile();
        String ext = extension(file.getName());
        if (file.length() == 0) return FileKind.textFile();
        if (BINARY_EXTENSIONS.contains(ext)) return FileKind.binaryFile();
        TextProbe probe = probeText(readHead(file, (int) Math.min(file.length(), TEXT_SAMPLE_SIZE)), ext);
        return probe.text ? FileKind.textFile() : FileKind.binaryFile();
    }

    private static TextProbe probeText(byte[] data, String extension) {
        if (data == null || data.length == 0) return new TextProbe(true, StandardCharsets.UTF_8);
        if (hasUtf8Bom(data)) return new TextProbe(canDecode(data, 3, data.length - 3, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        if (hasUtf16LeBom(data)) return new TextProbe(true, StandardCharsets.UTF_16LE);
        if (hasUtf16BeBom(data)) return new TextProbe(true, StandardCharsets.UTF_16BE);
        int control = 0;
        for (byte b : data) {
            int v = b & 0xff;
            if (v == 0) return new TextProbe(false, StandardCharsets.UTF_8);
            if (v < 32 && v != 9 && v != 10 && v != 12 && v != 13) control++;
        }
        if (control > Math.max(1, data.length / 100)) return new TextProbe(false, StandardCharsets.UTF_8);
        if (canDecode(data, 0, data.length, StandardCharsets.UTF_8)) return new TextProbe(true, StandardCharsets.UTF_8);
        int trimmed = Math.max(0, data.length - 4);
        if (trimmed > 0 && canDecode(data, 0, trimmed, StandardCharsets.UTF_8)) return new TextProbe(true, StandardCharsets.UTF_8);
        return new TextProbe(TEXT_EXTENSIONS.contains(extension), StandardCharsets.UTF_8);
    }

    private static boolean canDecode(byte[] data, int offset, int length, Charset charset) {
        if (length <= 0) return true;
        CharsetDecoder decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(data, offset, length));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static String decodeText(byte[] data, int length, Charset charset) {
        if (length <= 0) return "";
        int offset = 0;
        if (charset.equals(StandardCharsets.UTF_8) && length >= 3 && hasUtf8Bom(data)) offset = 3;
        else if (charset.equals(StandardCharsets.UTF_16LE) && length >= 2 && hasUtf16LeBom(data)) offset = 2;
        else if (charset.equals(StandardCharsets.UTF_16BE) && length >= 2 && hasUtf16BeBom(data)) offset = 2;
        return new String(data, offset, Math.max(0, length - offset), charset);
    }

    private static int safeTextLength(byte[] data, int max, Charset charset) {
        int length = Math.min(Math.max(0, max), data == null ? 0 : data.length);
        if (charset.equals(StandardCharsets.UTF_16LE) || charset.equals(StandardCharsets.UTF_16BE)) return length - (length % 2);
        int floor = Math.max(0, length - 4);
        while (length > floor && !canDecode(data, 0, length, charset)) length--;
        return canDecode(data, 0, length, charset) ? length : Math.min(Math.max(0, max), data.length);
    }

    private static boolean hasUtf8Bom(byte[] data) {
        return data.length >= 3 && (data[0] & 0xff) == 0xef && (data[1] & 0xff) == 0xbb && (data[2] & 0xff) == 0xbf;
    }

    private static boolean hasUtf16LeBom(byte[] data) {
        return data.length >= 2 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xfe;
    }

    private static boolean hasUtf16BeBom(byte[] data) {
        return data.length >= 2 && (data[0] & 0xff) == 0xfe && (data[1] & 0xff) == 0xff;
    }

    private static String extension(String name) {
        if (TextUtils.isEmpty(name)) return "";
        int index = name.lastIndexOf('.');
        if (index < 0 || index == name.length() - 1) return "";
        return name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] readHead(File file, int length) {
        byte[] data = new byte[Math.max(length, 0)];
        if (data.length == 0) return data;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
            int offset = 0;
            int read;
            while (offset < data.length && (read = input.read(data, offset, data.length - offset)) > 0) offset += read;
            return offset == data.length ? data : Arrays.copyOf(data, offset);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static File learningCacheFile(String name) {
        File directory = new File(App.get().getNoBackupFilesDir(), LEARNING_CACHE_DIR);
        if (!directory.exists()) directory.mkdirs();
        return new File(directory, name);
    }

    private static boolean hasLearningCache(String name) {
        File file = learningCacheFile(name);
        return file.isFile() && file.length() > 0;
    }

    private static String readLearningCache(String name) {
        File file = learningCacheFile(name);
        if (!file.isFile() || file.length() <= 0) return "";
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE);
             ByteArrayOutputStream output = new ByteArrayOutputStream(8192)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean writeLearningCache(String name, String value) {
        File file = learningCacheFile(name);
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temp), BUFFER_SIZE)) {
            output.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            output.flush();
            if (!temp.renameTo(file)) {
                try (BufferedOutputStream fallback = new BufferedOutputStream(new FileOutputStream(file), BUFFER_SIZE)) {
                    fallback.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
                }
                temp.delete();
            }
            return file.isFile() && file.length() > 0;
        } catch (Exception e) {
            temp.delete();
            SpiderDebug.log("sync", "login state cache write failed file=%s error=%s", name, e.getMessage());
            return false;
        }
    }

    private static void clearLearningCache(String name) {
        File file = learningCacheFile(name);
        if (file.exists()) file.delete();
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        if (temp.exists()) temp.delete();
    }

    private static File appRoot() {
        File cached = cachedAppRoot;
        if (cached != null) return cached;
        try {
            cached = new File(App.get().getApplicationInfo().dataDir).getCanonicalFile();
        } catch (Exception e) {
            cached = new File(App.get().getApplicationInfo().dataDir);
        }
        cachedAppRoot = cached;
        return cached;
    }

    private static File sdcardRoot() {
        File cached = cachedSdcardRoot;
        if (cached != null) return cached;
        try {
            cached = Path.root().getCanonicalFile();
        } catch (Exception e) {
            cached = Path.root();
        }
        cachedSdcardRoot = cached;
        return cached;
    }

    private static File fileForPath(String path) throws IOException {
        Parsed parsed = parse(path);
        if (parsed == null) throw new IOException("Invalid path");
        return (parsed.relative.isEmpty() ? parsed.root : new File(parsed.root, parsed.relative)).getCanonicalFile();
    }

    private static File create(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (!file.exists()) file.createNewFile();
        file.setReadable(true, true);
        file.setWritable(true, true);
        return file;
    }

    private static long copy(File file, ZipOutputStream zos, byte[] buffer) throws IOException {
        long size = 0;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                zos.write(buffer, 0, read);
                size += read;
            }
        }
        return size;
    }

    private static byte[] readEntry(ZipInputStream zis, byte[] buffer) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int read;
        while ((read = zis.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    private static boolean inside(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private static String relative(File root, String key, File file) {
        try {
            File canonicalRoot = root.getCanonicalFile();
            File canonicalFile = file.getCanonicalFile();
            if (!inside(canonicalRoot, canonicalFile)) return "";
            String relative = canonicalRoot.toPath().relativize(canonicalFile.toPath()).toString().replace(File.separatorChar, '/');
            return relative.isEmpty() ? key : key + "/" + relative;
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isVisible(File root, String key, File file) {
        String path = relative(root, key, file);
        return !path.isEmpty() && !isIgnoredLearningPath(path);
    }

    static boolean isIgnoredLearningPath(String path) {
        if (path == null) return false;
        String value = path.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return false;
        String name = basename(value);
        if (name.equals("webhtv-debug-log.txt") || name.startsWith("webhtv-debug-log-")) return true;
        if (name.endsWith(".log") || isRotatedLog(name)) return true;
        if (isDiagnosticTextFile(name)) return true;
        for (String segment : value.split("/")) {
            if (segment.equals("log") || segment.equals("logs") || segment.equals("debug-log") || segment.equals("debug_logs")
                    || segment.equals("debug-logs") || segment.equals("trace") || segment.equals("traces")
                    || segment.equals("tombstone") || segment.equals("tombstones") || segment.equals("crash-log")
                    || segment.equals("crash_logs") || segment.equals("crash-logs")) return true;
        }
        return false;
    }

    private static boolean isRotatedLog(String name) {
        int marker = name.lastIndexOf(".log.");
        if (marker < 1) return false;
        String suffix = name.substring(marker + 5);
        if (suffix.equals("gz") || suffix.equals("zip")) return true;
        if (suffix.endsWith(".gz")) suffix = suffix.substring(0, suffix.length() - 3);
        if (suffix.isEmpty()) return false;
        for (int i = 0; i < suffix.length(); i++) if (!Character.isDigit(suffix.charAt(i))) return false;
        return true;
    }

    private static boolean isDiagnosticTextFile(String name) {
        if (!name.endsWith(".txt") && !name.endsWith(".text")) return false;
        return name.startsWith("logcat") || name.startsWith("debug-log") || name.startsWith("debug_log")
                || name.startsWith("crash-log") || name.startsWith("crash_log") || name.startsWith("tombstone")
                || name.startsWith("anr-trace") || name.startsWith("anr_trace") || name.startsWith("playback-trace")
                || name.startsWith("playback_trace");
    }

    private static String parent(String path) {
        path = normalize(path);
        if (path.equals(ROOT_APP) || path.equals(ROOT_SDCARD)) return ".";
        int index = path.lastIndexOf('/');
        return path.isEmpty() ? "." : index < 0 ? "" : path.substring(0, index);
    }

    private static String normalize(String path) {
        if (path == null) return "";
        File localFile = com.github.catvod.utils.Path.local(path);
        String absolute = localFile != null ? localFile.getAbsolutePath() : path;
        String appRoot = appRoot().getAbsolutePath().replace('\\', '/');
        String dataRoot = new File(App.get().getApplicationInfo().dataDir).getAbsolutePath().replace('\\', '/');
        String sdcardRoot = sdcardRoot().getAbsolutePath().replace('\\', '/');
        String value = absolute.trim().replace('\\', '/');
        if (value.startsWith(appRoot)) value = ROOT_APP + "/" + value.substring(appRoot.length());
        else if (value.startsWith(dataRoot)) value = ROOT_APP + "/" + value.substring(dataRoot.length());
        else if (value.startsWith(sdcardRoot)) value = ROOT_SDCARD + "/" + value.substring(sdcardRoot.length());
        else if (value.startsWith(ROOT_APP + ":")) value = ROOT_APP + "/" + value.substring((ROOT_APP + ":").length());
        else if (value.startsWith(ROOT_SDCARD + ":")) value = ROOT_SDCARD + "/" + value.substring((ROOT_SDCARD + ":").length());
        while (value.startsWith("/")) value = value.substring(1);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty() || ".".equals(value) || value.equals("..") || value.contains("../") || value.contains("/..")) return "";
        if (value.equals(ROOT_APP) || value.startsWith(ROOT_APP + "/")) return clean(ROOT_APP, value.substring(ROOT_APP.length()));
        if (value.equals(ROOT_SDCARD) || value.startsWith(ROOT_SDCARD + "/")) return clean(ROOT_SDCARD, value.substring(ROOT_SDCARD.length()));
        return clean(ROOT_APP, value);
    }

    private static String clean(String key, String relative) {
        relative = relative == null ? "" : relative;
        while (relative.startsWith("/")) relative = relative.substring(1);
        while (relative.endsWith("/")) relative = relative.substring(0, relative.length() - 1);
        if (relative.isEmpty()) return key;
        if (relative.equals("..") || relative.contains("../") || relative.contains("/..")) return "";
        return key + "/" + relative;
    }

    private static Parsed parse(String path) {
        String value = normalize(path);
        if (TextUtils.isEmpty(value)) return null;
        int index = value.indexOf('/');
        String key = index < 0 ? value : value.substring(0, index);
        String relative = index < 0 ? "" : value.substring(index + 1);
        if (ROOT_APP.equals(key)) return new Parsed(key, appRoot(), relative);
        if (ROOT_SDCARD.equals(key)) return new Parsed(key, sdcardRoot(), relative);
        return null;
    }

    private enum Level {
        HIGH, MEDIUM, LOW
    }

    private record Score(Level level, String reason) {
    }

    private static final class ScanBudget {

        private final long total;
        private long remaining;
        private int skipped;

        private ScanBudget(long bytes) {
            total = remaining = Math.max(0, bytes);
        }

        private synchronized boolean tryAcquire(long bytes) {
            long requested = Math.max(0, Math.min(bytes, MAX_SCAN_FILE_SIZE));
            if (requested > remaining) {
                skipped++;
                return false;
            }
            remaining -= requested;
            return true;
        }

        private synchronized long consumed() {
            return total - remaining;
        }

        private synchronized int skipped() {
            return skipped;
        }
    }

    private record ContentScore(boolean hit, boolean strong, String marker) {
        private static ContentScore none() {
            return new ContentScore(false, false, "");
        }
    }

    private record TextProbe(boolean text, Charset charset) {
    }

    private record FileKind(String type, boolean text, boolean previewable) {
        private static FileKind dir() {
            return new FileKind("dir", false, false);
        }

        private static FileKind missing() {
            return new FileKind("missing", false, false);
        }

        private static FileKind textFile() {
            return new FileKind("text", true, true);
        }

        private static FileKind binaryFile() {
            return new FileKind("binary", false, false);
        }
    }

    private static class Parsed {
        private final String key;
        private final File root;
        private final String relative;

        private Parsed(String key, File root, String relative) {
            this.key = key;
            this.root = root;
            this.relative = relative == null ? "" : relative;
        }
    }

    private static class Snapshot {
        @SerializedName("entries")
        private List<Entry> entries;

        private Snapshot(List<Entry> entries) {
            this.entries = entries == null ? Collections.emptyList() : entries;
        }
    }

    private static class Entry {
        @SerializedName("path")
        private String path;
        @SerializedName("size")
        private long size;
        @SerializedName("modified")
        private long modified;

        private Entry(String path, long size, long modified) {
            this.path = path;
            this.size = size;
            this.modified = modified;
        }
    }

    public static class Candidate {
        @SerializedName("path")
        private final String path;
        @SerializedName("displayPath")
        private final String displayPath;
        @SerializedName("confidence")
        private final String confidence;
        @SerializedName("reason")
        private final String reason;
        @SerializedName("selected")
        private final boolean selected;
        @SerializedName("size")
        private final long size;
        @SerializedName("modified")
        private final long modified;

        private Candidate(String path, String displayPath, String confidence, String reason, boolean selected, long size, long modified) {
            this.path = path;
            this.displayPath = displayPath;
            this.confidence = confidence;
            this.reason = reason;
            this.selected = selected;
            this.size = size;
            this.modified = modified;
        }

        public String getPath() {
            return path;
        }

        public String getDisplayPath() {
            return displayPath;
        }

        public String getConfidence() {
            return confidence;
        }

        public String getReason() {
            return reason;
        }

        public boolean isSelected() {
            return selected;
        }

        public long getSize() {
            return size;
        }

        public long getModified() {
            return modified;
        }
    }

    public static class LearnResult {
        private final List<String> selected;
        private final List<String> pending;
        private final boolean learned;

        private LearnResult(List<String> selected, List<String> pending, boolean learned) {
            this.selected = selected;
            this.pending = pending;
            this.learned = learned;
        }

        public List<String> getSelected() {
            return selected;
        }

        public List<String> getPending() {
            return pending;
        }

        public boolean isLearned() {
            return learned;
        }
    }

    public static class PathState {
        private final String path;
        private final String displayPath;
        private final boolean exists;
        private final boolean file;
        private final long size;
        private final long modified;
        private final String fileType;
        private final boolean text;
        private final boolean previewable;

        private PathState(String path, String displayPath, boolean exists, boolean file, long size, long modified, String fileType, boolean text, boolean previewable) {
            this.path = path;
            this.displayPath = displayPath;
            this.exists = exists;
            this.file = file;
            this.size = size;
            this.modified = modified;
            this.fileType = fileType;
            this.text = text;
            this.previewable = previewable;
        }

        public String getPath() {
            return path;
        }

        public String getDisplayPath() {
            return displayPath;
        }

        public boolean isExists() {
            return exists;
        }

        public boolean isFile() {
            return file;
        }

        public long getSize() {
            return size;
        }

        public long getModified() {
            return modified;
        }

        public String getFileType() {
            return fileType;
        }

        public boolean isText() {
            return text;
        }

        public boolean isPreviewable() {
            return previewable;
        }
    }

    public static class Tree {
        private final String path;
        private final String parent;
        private final boolean valid;
        private final List<TreeItem> items;
        private final int total;
        private final boolean truncated;

        private Tree(String path, String parent, boolean valid, List<TreeItem> items, int total, boolean truncated) {
            this.path = path;
            this.parent = parent;
            this.valid = valid;
            this.items = items;
            this.total = total;
            this.truncated = truncated;
        }

        public String getPath() {
            return path;
        }

        public String getParent() {
            return parent;
        }

        public boolean isValid() {
            return valid;
        }

        public List<TreeItem> getItems() {
            return items;
        }

        public int getTotal() {
            return total;
        }

        public boolean isTruncated() {
            return truncated;
        }
    }

    public static class TreeItem {
        private final String name;
        private final String path;
        private final boolean dir;
        private final long size;
        private final long modified;
        private final boolean selectable;
        private final String fileType;
        private final boolean text;
        private final boolean previewable;

        public TreeItem(String name, String path, boolean dir, long size, long modified, boolean selectable) {
            this(name, path, dir, size, modified, selectable, dir ? FileKind.dir() : FileKind.binaryFile());
        }

        public TreeItem(String name, String path, boolean dir, long size, long modified, boolean selectable, FileKind kind) {
            this.name = name;
            this.path = path;
            this.dir = dir;
            this.size = size;
            this.modified = modified;
            this.selectable = selectable;
            this.fileType = kind.type;
            this.text = kind.text;
            this.previewable = kind.previewable;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public boolean isDir() {
            return dir;
        }

        public long getSize() {
            return size;
        }

        public long getModified() {
            return modified;
        }

        public boolean isSelectable() {
            return selectable;
        }

        public String getFileType() {
            return fileType;
        }

        public boolean isText() {
            return text;
        }

        public boolean isPreviewable() {
            return previewable;
        }
    }

    public static class TextPreview {
        private final String path;
        private final String displayPath;
        private final String content;
        private final long size;
        private final boolean truncated;
        private final boolean editable;
        private final boolean text;
        private final String encoding;

        private TextPreview(String path, String displayPath, String content, long size, boolean truncated, boolean editable, boolean text, String encoding) {
            this.path = path;
            this.displayPath = displayPath;
            this.content = content;
            this.size = size;
            this.truncated = truncated;
            this.editable = editable;
            this.text = text;
            this.encoding = encoding;
        }

        public String getPath() {
            return path;
        }

        public String getDisplayPath() {
            return displayPath;
        }

        public String getContent() {
            return content;
        }

        public long getSize() {
            return size;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public boolean isEditable() {
            return editable;
        }

        public boolean isText() {
            return text;
        }

        public String getEncoding() {
            return encoding;
        }
    }

    public static class Archive {
        private final File file;
        private final int count;
        private final long rawSize;
        private final long zipSize;

        private Archive(File file, int count, long rawSize, long zipSize) {
            this.file = file;
            this.count = count;
            this.rawSize = rawSize;
            this.zipSize = zipSize;
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

        public void delete() {
            Path.clear(file);
        }
    }
}
