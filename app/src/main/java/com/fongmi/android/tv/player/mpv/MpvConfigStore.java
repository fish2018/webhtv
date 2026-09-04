package com.fongmi.android.tv.player.mpv;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

public final class MpvConfigStore {

    private static final String KEY_NAME = "mpv_config_name";
    private static final String KEY_SOURCE = "mpv_config_source";
    private static final String KEY_TYPE = "mpv_config_type";
    private static final String KEY_HISTORY = "mpv_config_history";
    private static final String KEY_PROFILES = "mpv_config_profiles";
    private static final String KEY_SELECTED = "mpv_config_selected";
    private static final String KEY_MIGRATED = "mpv_config_profiles_migrated";
    public static final String TARGET_MPV_CONF = "mpv.conf";
    public static final String TARGET_INPUT_CONF = "input.conf";
    public static final String TARGET_SCRIPTS = "scripts";
    private static final String TYPE_DEFAULT = "default";
    private static final String TYPE_FILE = "file";
    private static final String TYPE_URL = "url";
    private static final String TYPE_TEXT = "text";
    private static final String CONFIG_DIR = "mpv";
    private static final String CONFIG_FILE = "mpv.conf";
    private static final String PROFILE_DIR = "profiles";
    private static final int MAX_PROFILE_BYTES = 1024 * 1024;
    private MpvConfigStore() {
    }

    public static File configDir() {
        File dir = new File(App.get().getFilesDir(), CONFIG_DIR);
        if (!dir.exists()) dir.mkdirs();
        ensureSubDir(TARGET_SCRIPTS);
        ensureSubDir("script-opts");
        ensureSubDir("fonts");
        ensureSubDir("shaders");
        ensureSubDir(PROFILE_DIR);
        return dir;
    }

    public static File configFile() {
        return new File(configDir(), CONFIG_FILE);
    }

    public static File targetFile(String target) {
        if (TARGET_INPUT_CONF.equals(target)) return new File(configDir(), TARGET_INPUT_CONF);
        return configFile();
    }

    public static File scriptsDir() {
        return new File(configDir(), TARGET_SCRIPTS);
    }

    public static boolean hasGpuVideoProcessing() {
        File[] scripts = scriptsDir().listFiles(file -> file.isFile() && isScriptName(file.getName()));
        if (scripts != null && scripts.length > 0) return true;
        try {
            return containsGpuVideoProcessing(readText(configFile()));
        } catch (IOException e) {
            return true;
        }
    }

    public static String getOptionValue(String name) {
        if (TextUtils.isEmpty(name)) return "";
        try {
            return findOptionValue(readText(configFile()), name);
        } catch (IOException e) {
            return "";
        }
    }

    static String findOptionValue(String content, String name) {
        if (content == null || name == null) return "";
        String expected = name.trim().toLowerCase(Locale.ROOT);
        String result = "";
        for (String raw : content.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment).trim();
            if (line.startsWith("--")) line = line.substring(2);
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            String option = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            if (expected.equals(option)) result = line.substring(separator + 1).trim();
        }
        return result;
    }

    static boolean containsGpuVideoProcessing(String content) {
        if (content == null || content.isEmpty()) return false;
        String[] lines = content.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim().toLowerCase(Locale.ROOT);
            if (line.isEmpty() || line.startsWith("#")) continue;
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment).trim();
            int separator = line.indexOf('=');
            String option = (separator < 0 ? line : line.substring(0, separator)).trim();
            String value = separator < 0 ? "" : line.substring(separator + 1).trim();
            if ((option.equals("vf") || option.startsWith("vf-") || option.equals("lavfi-complex")) && !value.isEmpty()) return true;
            if (option.startsWith("glsl-shader") || option.equals("icc-profile") || option.startsWith("tone-mapping")) return true;
            if ((option.equals("deband") || option.equals("interpolation")) && !isDisabledOptionValue(value)) return true;
            if (option.equals("video-output-levels")) return true;
            if (option.equals("scale") || option.equals("cscale") || option.equals("dscale") || option.equals("tscale")) return true;
        }
        return false;
    }

    private static boolean isDisabledOptionValue(String value) {
        return value.isEmpty() || value.equals("no") || value.equals("false") || value.equals("0");
    }

    public static void ensureReady() {
        File file = configFile();
        if (!file.isFile() || file.length() == 0) {
            writeText(defaultConfig());
            putDefault(TARGET_MPV_CONF);
            return;
        }
        if (TYPE_DEFAULT.equals(Prefers.getString(key(KEY_TYPE, TARGET_MPV_CONF), TYPE_DEFAULT))) {
            try {
                String expected = defaultConfig();
                if (!TextUtils.equals(readText(file), expected)) writeText(expected);
            } catch (IOException ignored) {
            }
        }
    }

    public static String getName() {
        return getName(TARGET_MPV_CONF);
    }

    public static String getName(String target) {
        return Prefers.getString(key(KEY_NAME, target));
    }

    public static String getSource() {
        return getSource(TARGET_MPV_CONF);
    }

    public static String getSource(String target) {
        return Prefers.getString(key(KEY_SOURCE, target));
    }

    public static boolean isUrl() {
        return isUrl(TARGET_MPV_CONF);
    }

    public static boolean isUrl(String target) {
        return TYPE_URL.equals(Prefers.getString(key(KEY_TYPE, target), TYPE_DEFAULT));
    }

    public static String summary() {
        return "mpv.conf: " + summary(TARGET_MPV_CONF);
    }

    public static String summary(String target) {
        if (TARGET_SCRIPTS.equals(target)) return scriptsSummary();
        String type = Prefers.getString(key(KEY_TYPE, target), TYPE_DEFAULT);
        String name = getName(target);
        String source = getSource(target);
        if (TextUtils.isEmpty(type) || TYPE_DEFAULT.equals(type)) return ResUtil.getString(R.string.mpv_config_default);
        if (!TextUtils.isEmpty(name)) return name;
        if (TYPE_FILE.equals(type)) return TextUtils.isEmpty(source) ? ResUtil.getString(R.string.mpv_config_local) : fileName(source);
        if (TYPE_URL.equals(type)) return TextUtils.isEmpty(source) ? ResUtil.getString(R.string.mpv_config_url) : source;
        return ResUtil.getString(R.string.mpv_config_untitled);
    }

    public static void applyDefault() {
        applyDefault(TARGET_MPV_CONF);
    }

    public static synchronized void applyDefault(String target) {
        if (TARGET_SCRIPTS.equals(target)) {
            clearScripts();
            putDefault(target);
            return;
        }
        if (TARGET_INPUT_CONF.equals(target)) {
            File file = targetFile(target);
            if (file.isFile()) file.delete();
        } else {
            writeText(defaultConfig());
        }
        putDefault(target);
        Prefers.put(selectedKey(target), "default");
    }

    public static void applyFile(String path, String name) throws IOException {
        applyFile(TARGET_MPV_CONF, path, name);
    }

    public static synchronized void applyFile(String target, String path, String name) throws IOException {
        String text = readLocal(path);
        validateContent(text);
        String displayName = TextUtils.isEmpty(name) ? fileName(path) : name;
        if (TARGET_SCRIPTS.equals(target)) {
            writeTextChecked(scriptFile(path, displayName), text);
        } else {
            writeTargetChecked(target, text);
            recordAppliedProfile(target, TYPE_FILE, path, displayName, text);
        }
        Prefers.put(key(KEY_NAME, target), displayName);
        Prefers.put(key(KEY_SOURCE, target), path);
        Prefers.put(key(KEY_TYPE, target), TYPE_FILE);
        addHistory(target, TYPE_FILE, displayName, path);
    }

    public static void applyUrl(String url, String name) throws IOException {
        applyUrl(TARGET_MPV_CONF, url, name);
    }

    public static synchronized void applyUrl(String target, String url, String name) throws IOException {
        if (!isHttpUrl(url)) throw new IOException(App.get().getString(R.string.mpv_config_url_invalid));
        String text = readUrl(url);
        validateContent(text);
        String displayName = TextUtils.isEmpty(name) ? fileName(url) : name;
        if (TARGET_SCRIPTS.equals(target)) {
            writeTextChecked(scriptFile(url, displayName), text);
        } else {
            writeTargetChecked(target, text);
            recordAppliedProfile(target, TYPE_URL, url, displayName, text);
        }
        Prefers.put(key(KEY_NAME, target), displayName);
        Prefers.put(key(KEY_SOURCE, target), url);
        Prefers.put(key(KEY_TYPE, target), TYPE_URL);
        addHistory(target, TYPE_URL, displayName, url);
    }

    public static void applySource(String target, String source, String name) throws IOException {
        if (isHttpUrl(source)) applyUrl(target, source, name);
        else applyFile(target, source, name);
    }

    public static synchronized List<ConfigProfile> profiles(String target) {
        if (TARGET_SCRIPTS.equals(target)) return scriptProfiles();
        ensureProfiles(target);
        List<ConfigProfile> result = new ArrayList<>();
        if (TARGET_MPV_CONF.equals(target)) {
            ConfigProfile defaultProfile = new ConfigProfile();
            defaultProfile.id = "default";
            defaultProfile.name = ResUtil.getString(R.string.mpv_config_default);
            defaultProfile.type = TYPE_DEFAULT;
            result.add(defaultProfile);
        }
        List<ConfigProfile> saved = readProfiles(target);
        Collections.sort(saved, (a, b) -> Long.compare(b.time, a.time));
        result.addAll(saved);
        String selected = selectedId(target);
        for (ConfigProfile profile : result) profile.active = TextUtils.equals(profile.id, selected);
        return result;
    }

    public static synchronized String selectedProfileId(String target) {
        if (TARGET_SCRIPTS.equals(target)) return "";
        ensureProfiles(target);
        return selectedId(target);
    }

    public static synchronized String profileContent(String target, String id) throws IOException {
        if (TARGET_SCRIPTS.equals(target)) {
            File file = safeScriptFile(id);
            if (!file.isFile()) throw missingProfile();
            return readText(file);
        }
        ensureProfiles(target);
        if ("default".equals(id)) return defaultContent(target);
        List<ConfigProfile> profiles = readProfiles(target);
        ConfigProfile profile = findProfile(profiles, id);
        if (profile == null) throw missingProfile();
        File snapshot = profileSnapshot(target, id);
        if (snapshot.isFile()) return readText(snapshot);
        if (!TextUtils.isEmpty(profile.content)) {
            writeTextChecked(snapshot, profile.content);
            profile.content = null;
            saveProfiles(target, profiles);
            return readText(snapshot);
        }
        String content = readSource(profile.type, profile.source);
        validateContent(content);
        writeTextChecked(snapshot, content);
        return content;
    }

    public static synchronized String saveTextProfile(String target, String id, String name, String content) throws IOException {
        validateContent(content);
        if (TextUtils.isEmpty(content) && !TARGET_INPUT_CONF.equals(target)) throw new IOException(App.get().getString(R.string.mpv_config_empty));
        String displayName = TextUtils.isEmpty(name) ? ResUtil.getString(R.string.mpv_config_untitled) : name.trim();
        if (TARGET_SCRIPTS.equals(target)) return saveScript(id, displayName, content);
        ensureProfiles(target);
        List<ConfigProfile> profiles = readProfiles(target);
        ConfigProfile profile = "default".equals(id) ? null : findProfile(profiles, id);
        if (profile == null) {
            profile = new ConfigProfile();
            profile.id = UUID.randomUUID().toString();
            profiles.add(profile);
        }
        File snapshot = profileSnapshot(target, profile.id);
        writeTextChecked(snapshot, content);
        profile.name = displayName;
        profile.type = TYPE_TEXT;
        profile.source = "";
        profile.content = null;
        profile.time = System.currentTimeMillis();
        if (TextUtils.equals(profile.id, selectedId(target))) writeTargetChecked(target, content);
        saveProfiles(target, profiles);
        return profile.id;
    }

    public static synchronized String importProfile(String target, String source, String name) throws IOException {
        String type = isHttpUrl(source) ? TYPE_URL : TYPE_FILE;
        String content = readSource(type, source);
        validateContent(content);
        String displayName = TextUtils.isEmpty(name) ? fileName(source) : name.trim();
        if (TARGET_SCRIPTS.equals(target)) {
            File file = uniqueScriptFile(displayName);
            writeTextChecked(file, content);
            return file.getName();
        }
        ensureProfiles(target);
        List<ConfigProfile> profiles = readProfiles(target);
        ConfigProfile profile = new ConfigProfile();
        profile.id = UUID.randomUUID().toString();
        profile.name = displayName;
        profile.type = type;
        profile.source = source;
        profile.time = System.currentTimeMillis();
        writeTextChecked(profileSnapshot(target, profile.id), content);
        profiles.add(profile);
        saveProfiles(target, profiles);
        return profile.id;
    }

    public static synchronized void selectProfile(String target, String id) throws IOException {
        if (TARGET_SCRIPTS.equals(target)) return;
        ensureProfiles(target);
        if ("default".equals(id)) {
            writeTargetChecked(target, defaultContent(target));
            putDefault(target);
            Prefers.put(selectedKey(target), "default");
            return;
        }
        ConfigProfile profile = findProfile(readProfiles(target), id);
        if (profile == null) throw missingProfile();
        String content = profileContent(target, id);
        writeTargetChecked(target, content);
        Prefers.put(key(KEY_NAME, target), profile.name);
        Prefers.put(key(KEY_SOURCE, target), profile.source == null ? "" : profile.source);
        Prefers.put(key(KEY_TYPE, target), profile.type);
        Prefers.put(selectedKey(target), profile.id);
    }

    public static synchronized boolean deleteProfile(String target, String id) throws IOException {
        if (TARGET_SCRIPTS.equals(target)) {
            File file = safeScriptFile(id);
            if (!file.exists()) return true;
            if (!file.delete()) throw writeFailed();
            return true;
        }
        if (TextUtils.isEmpty(id) || "default".equals(id)) return false;
        ensureProfiles(target);
        List<ConfigProfile> profiles = readProfiles(target);
        ConfigProfile profile = findProfile(profiles, id);
        if (profile == null) return false;
        if (TextUtils.equals(selectedId(target), id)) selectProfile(target, "default");
        profiles.remove(profile);
        File snapshot = profileSnapshot(target, id);
        if (snapshot.exists() && !snapshot.delete()) throw writeFailed();
        saveProfiles(target, profiles);
        return true;
    }

    public static synchronized String renameProfile(String target, String id, String name) throws IOException {
        String displayName = name == null ? "" : name.trim();
        if (TextUtils.isEmpty(displayName)) throw new IOException(App.get().getString(R.string.mpv_config_name_required));
        if (TARGET_SCRIPTS.equals(target)) {
            File source = safeScriptFile(id);
            if (!source.isFile()) throw missingProfile();
            String fileName = safeScriptName(displayName);
            File output = new File(scriptsDir(), fileName);
            if (source.equals(output)) return source.getName();
            if (output.exists()) throw new IOException(App.get().getString(R.string.mpv_config_script_exists));
            writeTextChecked(output, readText(source));
            if (!source.delete()) {
                output.delete();
                throw writeFailed();
            }
            return output.getName();
        }
        if (TextUtils.isEmpty(id) || "default".equals(id)) throw missingProfile();
        ensureProfiles(target);
        List<ConfigProfile> profiles = readProfiles(target);
        ConfigProfile profile = findProfile(profiles, id);
        if (profile == null) throw missingProfile();
        profile.name = displayName;
        saveProfiles(target, profiles);
        if (TextUtils.equals(selectedId(target), id)) Prefers.put(key(KEY_NAME, target), displayName);
        return profile.id;
    }

    public static boolean hasHistory(String target) {
        return !getAvailableHistory(target).isEmpty();
    }

    public static CharSequence[] historyLabels(String target) {
        List<History> items = getAvailableHistory(target);
        CharSequence[] labels = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) labels[i] = label(items.get(i));
        return labels;
    }

    public static void applyHistory(String target, int index) throws IOException {
        List<History> items = getAvailableHistory(target);
        if (index < 0 || index >= items.size()) throw new IOException(App.get().getString(R.string.mpv_config_history_empty));
        History item = items.get(index);
        if (TYPE_FILE.equals(item.type)) applyFile(target, item.source, item.name);
        else if (TYPE_URL.equals(item.type)) applyUrl(target, item.source, item.name);
    }

    public static boolean removeHistory(String target, int index) {
        List<History> items = getHistory(target);
        List<History> available = getAvailableHistory(target, items);
        if (index < 0 || index >= available.size()) return false;
        items.remove(available.get(index));
        Prefers.put(key(KEY_HISTORY, target), serializeHistory(items));
        return true;
    }

    private static void ensureProfiles(String target) {
        List<ConfigProfile> profiles = readProfiles(target);
        boolean changed = normalizeProfiles(target, profiles);
        if (!Prefers.getBoolean(migratedKey(target))) {
            String currentType = Prefers.getString(key(KEY_TYPE, target), TYPE_DEFAULT);
            String currentSource = getSource(target);
            String selected = "default";
            if (!TYPE_DEFAULT.equals(currentType) && !TextUtils.isEmpty(currentSource)) {
                String id = legacyId(currentType, currentSource);
                ConfigProfile profile = upsertLegacy(profiles, id, currentType, currentSource, getName(target), System.currentTimeMillis());
                copyCurrentToSnapshot(target, profile.id);
                selected = profile.id;
                changed = true;
            }
            for (History history : getHistory(target)) {
                if (history == null || TextUtils.isEmpty(history.source)) continue;
                String id = legacyId(history.type, history.source);
                if (findProfile(profiles, id) != null) continue;
                upsertLegacy(profiles, id, history.type, history.source, history.name, history.time);
                changed = true;
            }
            Prefers.put(selectedKey(target), selected);
            Prefers.put(migratedKey(target), true);
        }
        if (TextUtils.isEmpty(selectedId(target)) || (!"default".equals(selectedId(target)) && findProfile(profiles, selectedId(target)) == null)) {
            Prefers.put(selectedKey(target), "default");
        }
        if (changed || TextUtils.isEmpty(Prefers.getString(profilesKey(target)))) saveProfiles(target, profiles);
    }

    private static boolean normalizeProfiles(String target, List<ConfigProfile> profiles) {
        boolean changed = false;
        Set<String> ids = new HashSet<>();
        for (Iterator<ConfigProfile> iterator = profiles.iterator(); iterator.hasNext(); ) {
            ConfigProfile profile = iterator.next();
            if (profile == null || !isSafeProfileId(profile.id) || "default".equals(profile.id) || !ids.add(profile.id)) {
                iterator.remove();
                changed = true;
                continue;
            }
            if (TextUtils.isEmpty(profile.name)) {
                profile.name = ResUtil.getString(R.string.mpv_config_untitled);
                changed = true;
            }
            if (!TextUtils.isEmpty(profile.content)) {
                File snapshot = profileSnapshot(target, profile.id);
                if (!snapshot.isFile()) writeText(snapshot, profile.content);
                profile.content = null;
                changed = true;
            }
        }
        return changed;
    }

    private static ConfigProfile upsertLegacy(List<ConfigProfile> profiles, String id, String type, String source, String name, long time) {
        ConfigProfile profile = findProfile(profiles, id);
        if (profile == null) {
            profile = new ConfigProfile();
            profile.id = id;
            profiles.add(profile);
        }
        profile.name = TextUtils.isEmpty(name) ? fileName(source) : name;
        profile.type = type;
        profile.source = source;
        profile.time = time <= 0 ? System.currentTimeMillis() : time;
        return profile;
    }

    private static void recordAppliedProfile(String target, String type, String source, String name, String content) throws IOException {
        ensureProfiles(target);
        List<ConfigProfile> profiles = readProfiles(target);
        String id = legacyId(type, source);
        ConfigProfile profile = upsertLegacy(profiles, id, type, source, name, System.currentTimeMillis());
        writeTextChecked(profileSnapshot(target, id), content);
        saveProfiles(target, profiles);
        Prefers.put(selectedKey(target), profile.id);
    }

    private static void copyCurrentToSnapshot(String target, String id) {
        try {
            File current = targetFile(target);
            if (current.isFile()) writeTextChecked(profileSnapshot(target, id), readText(current));
        } catch (IOException ignored) {
        }
    }

    private static String saveScript(String id, String name, String content) throws IOException {
        String fileName = safeScriptName(name);
        File previous = TextUtils.isEmpty(id) ? null : safeScriptFile(id);
        File output = previous == null || !safeScriptName(id).equals(fileName) ? new File(scriptsDir(), fileName) : previous;
        if (previous != null && !previous.equals(output) && output.exists()) throw new IOException(App.get().getString(R.string.mpv_config_script_exists));
        writeTextChecked(output, content);
        if (previous != null && !previous.equals(output) && previous.exists() && !previous.delete()) throw writeFailed();
        return output.getName();
    }

    private static List<ConfigProfile> scriptProfiles() {
        List<ConfigProfile> result = new ArrayList<>();
        File[] files = scriptsDir().listFiles(file -> file.isFile() && isScriptName(file.getName()));
        if (files == null) return result;
        for (File file : files) {
            ConfigProfile profile = new ConfigProfile();
            profile.id = file.getName();
            profile.name = file.getName();
            profile.type = TYPE_TEXT;
            profile.source = file.getAbsolutePath();
            profile.time = file.lastModified();
            result.add(profile);
        }
        Collections.sort(result, Comparator.comparing(item -> item.name.toLowerCase()));
        return result;
    }

    private static File profileSnapshot(String target, String id) {
        File dir = new File(configDir(), PROFILE_DIR + File.separator + safeTarget(target));
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, id + ".conf");
    }

    private static File safeScriptFile(String id) throws IOException {
        if (TextUtils.isEmpty(id) || !TextUtils.equals(id, fileName(id)) || !isScriptName(id)) throw missingProfile();
        return new File(scriptsDir(), id);
    }

    private static File uniqueScriptFile(String name) {
        String safe = safeScriptName(name);
        File file = new File(scriptsDir(), safe);
        if (!file.exists()) return file;
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String extension = dot > 0 ? safe.substring(dot) : ".lua";
        for (int i = 2; i < 1000; i++) {
            file = new File(scriptsDir(), base + " (" + i + ")" + extension);
            if (!file.exists()) return file;
        }
        return new File(scriptsDir(), base + "-" + System.currentTimeMillis() + extension);
    }

    private static String safeScriptName(String name) {
        String file = fileName(name).replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (TextUtils.isEmpty(file) || ".".equals(file) || "..".equals(file)) file = "script-" + System.currentTimeMillis();
        if (!isScriptName(file)) file += ".lua";
        return file;
    }

    private static boolean isScriptName(String name) {
        return name != null && (name.toLowerCase().endsWith(".lua") || name.toLowerCase().endsWith(".js"));
    }

    private static String readSource(String type, String source) throws IOException {
        if (TYPE_URL.equals(type)) return readUrl(source);
        if (TYPE_FILE.equals(type)) return readLocal(source);
        throw missingProfile();
    }

    private static String readUrl(String url) throws IOException {
        String text = OkHttp.string(url, 15000);
        if (TextUtils.isEmpty(text)) throw new IOException(App.get().getString(R.string.mpv_config_download_empty));
        return text;
    }

    private static String readLocal(String path) throws IOException {
        if (TextUtils.isEmpty(path)) throw new IOException(App.get().getString(R.string.mpv_config_empty));
        File file = Path.local(path);
        if (!file.isFile()) file = new File(path);
        if (!file.isFile() || file.length() == 0) throw new IOException(App.get().getString(R.string.mpv_config_file_invalid));
        String text = readText(file);
        if (TextUtils.isEmpty(text)) throw new IOException(App.get().getString(R.string.mpv_config_file_invalid));
        return text;
    }

    private static String defaultContent(String target) {
        return TARGET_MPV_CONF.equals(target) ? defaultConfig() : "";
    }

    private static void writeTargetChecked(String target, String content) throws IOException {
        File file = targetFile(target);
        if (TARGET_INPUT_CONF.equals(target) && TextUtils.isEmpty(content)) {
            if (file.exists() && !file.delete()) throw writeFailed();
        } else {
            writeTextChecked(file, content);
        }
    }

    private static void validateContent(String content) throws IOException {
        int size = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_PROFILE_BYTES) throw new IOException(App.get().getString(R.string.mpv_config_too_large));
    }

    private static IOException missingProfile() {
        return new IOException(App.get().getString(R.string.mpv_config_profile_missing));
    }

    private static IOException writeFailed() {
        return new IOException(App.get().getString(R.string.mpv_config_write_failed));
    }

    private static void putDefault(String target) {
        Prefers.put(key(KEY_NAME, target), "");
        Prefers.put(key(KEY_SOURCE, target), "");
        Prefers.put(key(KEY_TYPE, target), TYPE_DEFAULT);
    }

    private static String readText(File file) throws IOException {
        return new String(Path.readToByte(file), StandardCharsets.UTF_8);
    }

    private static void writeText(String text) {
        writeText(configFile(), text);
    }

    private static void writeText(File file, String text) {
        writeBytes(file, (text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(File file, byte[] data) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
            output.flush();
        } catch (IOException ignored) {
        }
    }

    private static void writeTextChecked(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw writeFailed();
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            throw writeFailed();
        }
    }

    private static String fileName(String value) {
        if (TextUtils.isEmpty(value)) return "";
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        String name = slash >= 0 && slash + 1 < value.length() ? value.substring(slash + 1) : value;
        return TextUtils.isEmpty(name) ? CONFIG_FILE : name;
    }

    private static boolean isHttpUrl(String value) {
        if (TextUtils.isEmpty(value)) return false;
        return value.regionMatches(true, 0, "http://", 0, 7) || value.regionMatches(true, 0, "https://", 0, 8);
    }

    private static File scriptFile(String source, String name) {
        return new File(scriptsDir(), safeScriptName(TextUtils.isEmpty(name) ? fileName(source) : name));
    }

    private static List<ConfigProfile> readProfiles(String target) {
        try {
            return parseProfilesJson(Prefers.getString(profilesKey(target), "[]"));
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    private static void saveProfiles(String target, List<ConfigProfile> profiles) {
        Prefers.put(profilesKey(target), serializeProfiles(profiles));
    }

    static List<ConfigProfile> parseProfilesJson(String json) {
        List<ConfigProfile> profiles = new ArrayList<>();
        JsonArray array = parseArray(json);
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) continue;
            try {
                JsonObject object = element.getAsJsonObject();
                if (!validStringField(object, "id") || !validStringField(object, "name")
                        || !validStringField(object, "type") || !validStringField(object, "source")
                        || !validNullableStringField(object, "content") || !validLongField(object, "time")) continue;
                ConfigProfile profile = new ConfigProfile();
                profile.id = stringValue(object, "id");
                profile.name = stringValue(object, "name");
                profile.type = stringValue(object, "type");
                profile.source = stringValue(object, "source");
                profile.content = nullableStringValue(object, "content");
                profile.time = longValue(object, "time");
                profiles.add(profile);
            } catch (Throwable ignored) {
            }
        }
        return profiles;
    }

    static String serializeProfiles(List<ConfigProfile> profiles) {
        JsonArray array = new JsonArray();
        if (profiles == null) return array.toString();
        for (ConfigProfile profile : profiles) {
            if (profile == null) continue;
            JsonObject object = new JsonObject();
            object.addProperty("id", value(profile.id));
            object.addProperty("name", value(profile.name));
            object.addProperty("type", value(profile.type));
            object.addProperty("source", value(profile.source));
            if (profile.content == null) object.add("content", null);
            else object.addProperty("content", profile.content);
            object.addProperty("time", profile.time);
            array.add(object);
        }
        return array.toString();
    }

    private static ConfigProfile findProfile(List<ConfigProfile> profiles, String id) {
        if (profiles == null || TextUtils.isEmpty(id)) return null;
        for (ConfigProfile profile : profiles) if (profile != null && TextUtils.equals(profile.id, id)) return profile;
        return null;
    }

    private static String profilesKey(String target) {
        return key(KEY_PROFILES, target);
    }

    private static String selectedKey(String target) {
        return key(KEY_SELECTED, target);
    }

    private static String migratedKey(String target) {
        return key(KEY_MIGRATED, target);
    }

    private static String selectedId(String target) {
        return Prefers.getString(selectedKey(target));
    }

    private static String legacyId(String type, String source) {
        return "legacy-" + Integer.toHexString((String.valueOf(type) + "|" + source).hashCode());
    }

    private static boolean isSafeProfileId(String id) {
        return !TextUtils.isEmpty(id) && id.matches("[A-Za-z0-9_-]+");
    }

    private static String safeTarget(String target) {
        return target.replace('.', '_').replace('/', '_');
    }

    private static List<History> getHistory(String target) {
        try {
            return parseHistoryJson(Prefers.getString(key(KEY_HISTORY, target), "[]"));
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    private static List<History> parseHistoryJson(String json) {
        List<History> items = new ArrayList<>();
        JsonArray array = parseArray(json);
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) continue;
            try {
                JsonObject object = element.getAsJsonObject();
                if (!validStringField(object, "type") || !validStringField(object, "name")
                        || !validStringField(object, "source") || !validLongField(object, "time")) continue;
                History item = new History();
                item.type = stringValue(object, "type");
                item.name = stringValue(object, "name");
                item.source = stringValue(object, "source");
                item.time = longValue(object, "time");
                items.add(item);
            } catch (Throwable ignored) {
            }
        }
        return items;
    }

    private static String serializeHistory(List<History> items) {
        JsonArray array = new JsonArray();
        if (items == null) return array.toString();
        for (History item : items) {
            if (item == null) continue;
            JsonObject object = new JsonObject();
            object.addProperty("type", value(item.type));
            object.addProperty("name", value(item.name));
            object.addProperty("source", value(item.source));
            object.addProperty("time", item.time);
            array.add(object);
        }
        return array.toString();
    }

    private static JsonArray parseArray(String json) {
        if (json == null || json.trim().isEmpty()) return new JsonArray();
        try {
            JsonElement root = JsonParser.parseString(json);
            return root != null && root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
        } catch (Throwable e) {
            return new JsonArray();
        }
    }

    private static boolean validStringField(JsonObject object, String key) {
        return !object.has(key) || object.get(key).isJsonNull()
                || object.get(key).isJsonPrimitive() && object.getAsJsonPrimitive(key).isString();
    }

    private static boolean validNullableStringField(JsonObject object, String key) {
        return validStringField(object, key);
    }

    private static boolean validLongField(JsonObject object, String key) {
        return !object.has(key) || object.get(key).isJsonNull()
                || object.get(key).isJsonPrimitive() && object.getAsJsonPrimitive(key).isNumber();
    }

    private static String stringValue(JsonObject object, String key) {
        return !object.has(key) || object.get(key).isJsonNull() ? "" : object.get(key).getAsString();
    }

    private static String nullableStringValue(JsonObject object, String key) {
        return !object.has(key) || object.get(key).isJsonNull() ? null : object.get(key).getAsString();
    }

    private static long longValue(JsonObject object, String key) {
        return !object.has(key) || object.get(key).isJsonNull() ? 0L : object.get(key).getAsLong();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static List<History> getAvailableHistory(String target) {
        return getAvailableHistory(target, getHistory(target));
    }

    private static List<History> getAvailableHistory(String target, List<History> items) {
        List<History> available = new ArrayList<>();
        String currentType = Prefers.getString(key(KEY_TYPE, target), TYPE_DEFAULT);
        String currentSource = getSource(target);
        for (History item : items) {
            boolean current = TextUtils.equals(item.type, currentType) && TextUtils.equals(item.source, currentSource);
            if (!current) available.add(item);
        }
        return available;
    }

    private static void addHistory(String target, String type, String name, String source) {
        if (TextUtils.isEmpty(source)) return;
        List<History> items = getHistory(target);
        for (int i = items.size() - 1; i >= 0; i--) {
            History item = items.get(i);
            if (TextUtils.equals(item.type, type) && TextUtils.equals(item.source, source)) items.remove(i);
        }
        History item = new History();
        item.type = type;
        item.name = name;
        item.source = source;
        item.time = System.currentTimeMillis();
        items.add(0, item);
        while (items.size() > 20) items.remove(items.size() - 1);
        Prefers.put(key(KEY_HISTORY, target), serializeHistory(items));
    }

    private static String label(History item) {
        String name = TextUtils.isEmpty(item.name) ? fileName(item.source) : item.name;
        String type = TYPE_FILE.equals(item.type) ? ResUtil.getString(R.string.mpv_config_local) : ResUtil.getString(R.string.mpv_config_url);
        return name + " · " + type;
    }

    private static String scriptsSummary() {
        File[] files = scriptsDir().listFiles(file -> file.isFile() && isScriptName(file.getName()));
        int count = files == null ? 0 : files.length;
        if (count <= 0) return ResUtil.getString(R.string.mpv_config_default);
        return ResUtil.getString(R.string.mpv_config_scripts_count, count);
    }

    private static void clearScripts() {
        File[] files = scriptsDir().listFiles(file -> file.isFile() && isScriptName(file.getName()));
        if (files == null) return;
        for (File file : files) file.delete();
    }

    private static String key(String base, String target) {
        if (TARGET_MPV_CONF.equals(target)) return base;
        return base + "_" + safeTarget(target);
    }

    private static void ensureSubDir(String name) {
        File dir = new File(App.get().getFilesDir(), CONFIG_DIR + File.separator + name);
        if (!dir.exists()) dir.mkdirs();
    }

    static String defaultConfig() {
        return "# WebHTV MPV default config\n"
                + "# Loaded by libmpv from files/mpv/mpv.conf. Keep Android-only output options in app code.\n"
                + "\n"
                + "profile=fast\n"
                + "http-allow-redirect=yes\n"
                + "sub-ass=yes\n"
                + "sub-ass-override=" + MpvSubtitleStylePolicy.ASS_OVERRIDE + "\n"
                + "embeddedfonts=yes\n"
                + "sub-fix-timing=yes\n"
                + "sub-use-margins=yes\n"
                + "sub-font-provider=fontconfig\n"
                + "volume-max=100\n";
    }

    private static final class History {
        String type;
        String name;
        String source;
        long time;
    }

    public static final class ConfigProfile {
        public String id;
        public String name;
        public String type;
        public String source;
        public String content;
        public long time;
        public boolean active;

        public boolean isDefault() {
            return TYPE_DEFAULT.equals(type);
        }

        public boolean isImported() {
            return TYPE_FILE.equals(type) || TYPE_URL.equals(type);
        }

        public String typeLabel() {
            if (isDefault()) return ResUtil.getString(R.string.mpv_config_default);
            if (TYPE_URL.equals(type)) return ResUtil.getString(R.string.mpv_config_url);
            if (TYPE_FILE.equals(type)) return ResUtil.getString(R.string.mpv_config_local);
            return ResUtil.getString(R.string.mpv_config_text);
        }
    }
}
