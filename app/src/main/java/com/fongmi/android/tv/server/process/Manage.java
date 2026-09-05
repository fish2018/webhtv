package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Backup;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.SyncOptions;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.remote.RemoteStore;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.service.ManageService;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.LoginStateSync;
import com.fongmi.android.tv.utils.ProgressRequestBody;
import com.fongmi.android.tv.utils.ScanTask;
import com.fongmi.android.tv.utils.SyncFiles;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FilterInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public class Manage implements Process {

    private static final int TREE_LIMIT = 300;
    private static final int LOGIN_STATE_FINDINGS_LIMIT = 300;
    private static final MediaType ZIP = MediaType.parse("application/zip");

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/manage/");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            ManageService.touch();
            if (url.equals("/manage/session")) return session(session.getParms());
            if (url.equals("/manage/background/settings")) return backgroundSettings(session);
            if (url.equals("/manage/devices")) return devices(session.getParms());
            if (url.equals("/manage/remote/ping")) return remotePing(session.getParms());
            if (url.equals("/manage/action")) return action(session);
            if (url.equals("/manage/remote/file")) return remoteFile(session.getParms());
            if (url.equals("/manage/remote/archive")) return remoteArchive(session.getParms());
            if (url.equals("/manage/remote/upload")) return remoteUpload(session.getParms(), files);
            if (url.equals("/manage/remote/newFolder")) return forwardTo(session, "/newFolder");
            if (url.equals("/manage/remote/delFolder")) return forwardTo(session, "/delFolder");
            if (url.equals("/manage/remote/delFile")) return forwardTo(session, "/delFile");
            Response forwarded = forward(session, url);
            if (forwarded != null) return forwarded;
            return switch (url) {
                case "/manage/sync/paths" -> syncPaths(session.getParms());
                case "/manage/sync/tree" -> syncTree(session.getParms());
                case "/manage/sync/detect" -> detectSyncPaths();
                case "/manage/sync/start" -> syncStart(session.getParms());
                case "/manage/file/archive" -> fileArchive(session.getParms());
                case "/manage/file/tree" -> fileTree(session.getParms());
                case "/manage/configs" -> configs(session.getParms());
                case "/manage/config/use" -> configUse(session.getParms());
                case "/manage/config/delete" -> configDelete(session.getParms());
                case "/manage/login-state" -> loginState();
                case "/manage/login-state/file" -> loginStateFile(session.getParms());
                case "/manage/login-state/learn" -> loginStateLearn(session.getParms());
                case "/manage/login-state/paths" -> loginStatePaths(session.getParms());
                case "/manage/login-state/confirm" -> loginStateConfirm();
                case "/manage/login-state/tree" -> loginStateTree(session.getParms());
                case "/manage/proxy" -> proxy(session.getParms());
                case "/manage/proxy/suggest/sites" -> proxySuggestSites();
                case "/manage/proxy/suggest" -> proxySuggest(session.getParms());
                case "/manage/csp/page" -> cspPage(session.getParms());
                case "/manage/csp" -> csp(session.getParms());
                default -> Nano.error(Status.NOT_FOUND, "Not found");
            };
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private Response session(Map<String, String> params) {
        if (bool(params.get("stop"), false)) ManageService.stop(App.get());
        else if (bool(params.get("close"), false)) ManageService.closeSoon();
        else ManageService.touch();
        JsonObject object = new JsonObject();
        object.addProperty("running", ManageService.isRunning());
        object.addProperty("serverRunning", com.fongmi.android.tv.server.Server.get().isRunning());
        object.addProperty("localUrl", ManageService.getLocalUrl());
        object.addProperty("lanUrl", ManageService.getLanUrl());
        object.addProperty("batteryOptimized", !ManageService.isIgnoringBatteryOptimizations(App.get()));
        object.addProperty("backgroundSettingsNeeded", ManageService.shouldOpenBackgroundPowerSettings(App.get()));
        object.addProperty("backgroundGuided", ManageService.isBackgroundPowerGuided());
        object.addProperty("backgroundGuide", ManageService.getBackgroundPowerGuide(App.get()));
        object.addProperty("wakeLock", ManageService.isWakeLockHeld());
        object.addProperty("wifiLock", ManageService.isWifiLockHeld());
        object.addProperty("lastAccess", ManageService.getLastAccess());
        object.addProperty("idleTimeout", ManageService.getIdleTimeout());
        object.addProperty("time", System.currentTimeMillis());
        return json(object);
    }

    private Response backgroundSettings(IHTTPSession session) throws IOException {
        String target = session.getParms().get("target");
        if (!TextUtils.isEmpty(target)) return forwardTo(session, "/manage/background/settings");
        boolean opened = ManageService.openBackgroundPowerSettings(App.get());
        JsonObject object = new JsonObject();
        object.addProperty("opened", opened);
        object.addProperty("backgroundSettingsNeeded", ManageService.shouldOpenBackgroundPowerSettings(App.get()));
        object.addProperty("batteryOptimized", !ManageService.isIgnoringBatteryOptimizations(App.get()));
        object.addProperty("guide", ManageService.getBackgroundPowerGuide(App.get()));
        return json(object);
    }

    private Response devices(Map<String, String> params) {
        if (bool(params.get("scan"), false)) new ScanTask(new ScanTask.Listener() {
            @Override
            public void onFind(Device device) {
            }
        }).start();
        JsonObject object = new JsonObject();
        object.add("local", App.gson().toJsonTree(Device.get()));
        JsonArray devices = new JsonArray();
        for (Device device : Device.getAll()) if (device.isApp() && !Device.get().equals(device)) devices.add(App.gson().toJsonTree(device));
        object.add("devices", devices);
        return json(object);
    }

    private Response remotePing(Map<String, String> params) {
        String target = params.get("target");
        JsonObject object = new JsonObject();
        object.addProperty("target", target == null ? "" : target);
        object.addProperty("time", System.currentTimeMillis());
        if (TextUtils.isEmpty(target)) {
            object.addProperty("ok", false);
            object.addProperty("message", "Missing target");
            return json(object);
        }
        try (okhttp3.Response response = OkHttp.client(1200).newCall(new Request.Builder().url(target.replaceAll("/+$", "") + "/device").build()).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            object.addProperty("ok", response.isSuccessful() && !TextUtils.isEmpty(text));
            if (!response.isSuccessful()) object.addProperty("message", "HTTP " + response.code());
            else if (!TextUtils.isEmpty(text)) object.add("device", App.gson().toJsonTree(Device.objectFrom(text)));
        } catch (Exception e) {
            object.addProperty("ok", false);
            object.addProperty("message", e.getClass().getSimpleName());
        }
        return json(object);
    }

    private Response forward(IHTTPSession session, String url) throws IOException {
        return forwardTo(session, url);
    }

    private Response forwardTo(IHTTPSession session, String url) throws IOException {
        String target = session.getParms().get("target");
        if (TextUtils.isEmpty(target)) return null;
        String remote = target.replaceAll("/+$", "") + url;
        FormBody body = buildForwardBody(session.getParms());
        try (okhttp3.Response response = OkHttp.client(5000).newCall(new Request.Builder().url(remote).post(body).build()).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            Status status = response.isSuccessful() ? Status.OK : Status.lookup(response.code());
            return NanoHTTPD.newFixedLengthResponse(status == null ? Status.INTERNAL_ERROR : status, response.header("Content-Type", "text/plain; charset=utf-8"), text);
        }
    }

    private Response action(IHTTPSession session) throws IOException {
        Response forwarded = forwardTo(session, "/action");
        if (forwarded != null) return forwarded;
        Map<String, String> params = session.getParms();
        String action = params.get("do");
        if ("search".equals(action)) {
            String word = params.get("word");
            if (!TextUtils.isEmpty(word)) ServerEvent.search(word);
            return Nano.ok();
        }
        if ("push".equals(action)) {
            String playUrl = params.get("url");
            if (!TextUtils.isEmpty(playUrl)) ServerEvent.push(playUrl);
            return Nano.ok();
        }
        return Nano.ok();
    }

    private Response remoteFile(Map<String, String> params) throws IOException {
        String target = params.get("target");
        if (TextUtils.isEmpty(target)) return Nano.error(Status.BAD_REQUEST, "Missing target");
        String path = params.getOrDefault("path", "");
        String remote = target.replaceAll("/+$", "") + "/file" + encodePath(path) + (bool(params.get("download"), false) ? "?download=1" : "");
        okhttp3.Response response = OkHttp.client(30000).newCall(new Request.Builder().url(remote).build()).execute();
        ResponseBody responseBody = response.body();
        if (responseBody == null) return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain; charset=utf-8", "Empty response");
        Status status = response.isSuccessful() ? Status.OK : Status.lookup(response.code());
        InputStream input = new FilterInputStream(responseBody.byteStream()) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    response.close();
                }
            }
        };
        Response result = NanoHTTPD.newFixedLengthResponse(status == null ? Status.INTERNAL_ERROR : status, response.header("Content-Type", "application/octet-stream"), input, responseBody.contentLength());
        String disposition = response.header("Content-Disposition");
        if (!TextUtils.isEmpty(disposition)) result.addHeader("Content-Disposition", disposition);
        return result;
    }

    private Response remoteUpload(Map<String, String> params, Map<String, String> files) throws IOException {
        String target = params.get("target");
        if (TextUtils.isEmpty(target)) return Nano.error(Status.BAD_REQUEST, "Missing target");
        MultipartBody.Builder body = new MultipartBody.Builder().setType(MultipartBody.FORM);
        body.addFormDataPart("path", params.getOrDefault("path", ""));
        for (String key : files.keySet()) {
            File temp = new File(files.get(key));
            String name = params.getOrDefault(key, temp.getName());
            body.addFormDataPart(key, name, RequestBody.create(MediaType.parse("application/octet-stream"), temp));
        }
        String remote = target.replaceAll("/+$", "") + "/upload";
        try (okhttp3.Response response = OkHttp.client(30000).newCall(new Request.Builder().url(remote).post(body.build()).build()).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            Status status = response.isSuccessful() ? Status.OK : Status.lookup(response.code());
            return NanoHTTPD.newFixedLengthResponse(status == null ? Status.INTERNAL_ERROR : status, response.header("Content-Type", "text/plain; charset=utf-8"), text);
        }
    }

    private Response remoteArchive(Map<String, String> params) throws IOException {
        String target = params.get("target");
        if (TextUtils.isEmpty(target)) return Nano.error(Status.BAD_REQUEST, "Missing target");
        String remote = target.replaceAll("/+$", "") + "/manage/file/archive";
        FormBody body = buildForwardBody(params);
        okhttp3.Response response = OkHttp.client(60000).newCall(new Request.Builder().url(remote).post(body).build()).execute();
        ResponseBody responseBody = response.body();
        if (responseBody == null) return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain; charset=utf-8", "Empty response");
        Status status = response.isSuccessful() ? Status.OK : Status.lookup(response.code());
        InputStream input = new FilterInputStream(responseBody.byteStream()) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    response.close();
                }
            }
        };
        Response result = NanoHTTPD.newFixedLengthResponse(status == null ? Status.INTERNAL_ERROR : status, response.header("Content-Type", "application/zip"), input, responseBody.contentLength());
        String disposition = response.header("Content-Disposition");
        if (!TextUtils.isEmpty(disposition)) result.addHeader("Content-Disposition", disposition);
        return result;
    }

    private String encodePath(String path) {
        if (TextUtils.isEmpty(path)) return "";
        StringBuilder builder = new StringBuilder();
        String[] parts = path.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append('/');
            builder.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return builder.toString();
    }

    private FormBody buildForwardBody(Map<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if ("target".equals(entry.getKey())) continue;
            body.add(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return body.build();
    }

    private Response syncPaths(Map<String, String> params) {
        if (params.containsKey("paths")) Setting.putSyncPaths(SyncFiles.getPathsText(SyncFiles.getPaths(params.get("paths"))));
        JsonObject object = new JsonObject();
        List<String> paths = SyncFiles.getPaths(Setting.getSyncPaths());
        object.add("paths", array(paths));
        object.addProperty("text", SyncFiles.getPathsText(paths));
        return json(object);
    }

    private Response fileTree(Map<String, String> params) throws Exception {
        String path = cleanRelativePath(params.getOrDefault("path", ""));
        File root = Path.root().getCanonicalFile();
        File dir = path.isEmpty() ? root : new File(root, path).getCanonicalFile();
        if (!inside(root, dir) || !dir.isDirectory()) return Nano.error(Status.NOT_FOUND, "Directory not found");
        JsonObject object = new JsonObject();
        object.addProperty("path", path);
        object.addProperty("parent", parentOf(root, dir));
        JsonArray dirs = new JsonArray();
        File[] files = dir.listFiles(file -> file.isDirectory() && !file.getName().startsWith("."));
        if (files != null) Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        int count = 0;
        for (File file : files == null ? new File[0] : files) {
            if (count++ >= TREE_LIMIT) break;
            JsonObject item = new JsonObject();
            item.addProperty("name", file.getName());
            item.addProperty("path", relativeTo(root, file));
            item.addProperty("children", hasVisibleDirs(file));
            dirs.add(item);
        }
        object.add("dirs", dirs);
        object.addProperty("truncated", files != null && files.length > TREE_LIMIT);
        return json(object);
    }

    private Response configs(Map<String, String> params) {
        if (params.containsKey("url")) {
            int type = intValue(params.get("type"), 0);
            String url = params.getOrDefault("url", "").trim();
            String name = params.getOrDefault("name", "").trim();
            if (TextUtils.isEmpty(url)) return Nano.error(Status.BAD_REQUEST, "Missing url");
            Config config = Config.find(url, type).name(name);
            config.save();
        }
        JsonObject object = new JsonObject();
        JsonArray items = new JsonArray();
        for (int type = 0; type <= 2; type++) {
            for (Config config : Config.getAll(type)) items.add(configObject(config, false));
            Config current = currentConfig(type);
            if (!current.isEmpty() && !containsConfig(items, current)) items.add(configObject(current, true));
        }
        object.add("items", items);
        return json(object);
    }

    private Response configUse(Map<String, String> params) {
        int type = intValue(params.get("type"), 0);
        String url = params.getOrDefault("url", "").trim();
        if (TextUtils.isEmpty(url)) return Nano.error(Status.BAD_REQUEST, "Missing url");
        Config config = Config.find(url, type);
        if (config.isEmpty()) return Nano.error(Status.NOT_FOUND, "Config not found");
        switch (type) {
            case 1 -> LiveConfig.load(config, new Callback());
            case 2 -> WallConfig.load(config, new Callback());
            default -> VodConfig.load(config, new Callback());
        }
        return configs(java.util.Collections.emptyMap());
    }

    private Response configDelete(Map<String, String> params) {
        int type = intValue(params.get("type"), 0);
        String url = params.getOrDefault("url", "").trim();
        if (TextUtils.isEmpty(url)) return Nano.error(Status.BAD_REQUEST, "Missing url");
        Config.find(url, type).delete();
        return configs(java.util.Collections.emptyMap());
    }

    private JsonObject configObject(Config config, boolean forceActive) {
        JsonObject item = new JsonObject();
        item.addProperty("type", config.getType());
        item.addProperty("typeName", configTypeName(config.getType()));
        item.addProperty("name", config.getName());
        item.addProperty("url", config.getUrl());
        item.addProperty("desc", config.getDesc());
        item.addProperty("time", config.getTime());
        item.addProperty("active", forceActive || isCurrentConfig(config));
        return item;
    }

    private boolean containsConfig(JsonArray items, Config config) {
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.get(i).getAsJsonObject();
            if (item.get("type").getAsInt() == config.getType() && item.get("url").getAsString().equals(config.getUrl())) return true;
        }
        return false;
    }

    private boolean isCurrentConfig(Config config) {
        Config current = currentConfig(config.getType());
        return current.getUrl().equals(config.getUrl());
    }

    private Config currentConfig(int type) {
        return switch (type) {
            case 1 -> LiveConfig.get().getConfig();
            case 2 -> WallConfig.get().getConfig();
            default -> VodConfig.get().getConfig();
        };
    }

    private String configTypeName(int type) {
        return switch (type) {
            case 1 -> "直播";
            case 2 -> "壁纸";
            default -> "影视";
        };
    }

    private Response syncTree(Map<String, String> params) throws Exception {
        String path = SyncFiles.normalize(params.get("path"));
        File root = Path.root().getCanonicalFile();
        File dir = path.isEmpty() ? root : new File(root, path).getCanonicalFile();
        if (!inside(root, dir) || !dir.isDirectory()) return Nano.error(Status.NOT_FOUND, "Directory not found");
        JsonObject object = new JsonObject();
        object.addProperty("path", path);
        object.addProperty("parent", parentOf(root, dir));
        JsonArray dirs = new JsonArray();
        File[] files = dir.listFiles(file -> file.isDirectory() && !file.getName().startsWith("."));
        if (files != null) Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        int count = 0;
        for (File file : files == null ? new File[0] : files) {
            if (count++ >= TREE_LIMIT) break;
            JsonObject item = new JsonObject();
            item.addProperty("name", file.getName());
            item.addProperty("path", relativeTo(root, file));
            item.addProperty("children", hasVisibleDirs(file));
            dirs.add(item);
        }
        object.add("dirs", dirs);
        object.addProperty("truncated", files != null && files.length > TREE_LIMIT);
        return json(object);
    }

    private Response fileArchive(Map<String, String> params) throws IOException {
        String text = params.getOrDefault("paths", params.getOrDefault("path", ""));
        if (TextUtils.isEmpty(text)) return Nano.error(Status.BAD_REQUEST, "Missing paths");
        List<String> paths = SyncFiles.getPaths(text);
        if (paths.isEmpty()) return Nano.error(Status.BAD_REQUEST, "Missing paths");
        SyncFiles.Archive archive = SyncFiles.createArchive(paths);
        if (archive == null) return Nano.error(Status.NOT_FOUND, "No files to archive");
        InputStream input = new FilterInputStream(new FileInputStream(archive.getFile())) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    archive.delete();
                }
            }
        };
        Response response = NanoHTTPD.newFixedLengthResponse(Status.OK, "application/zip", input, archive.getFile().length());
        addDownloadHeader(response, archiveName(paths));
        response.addHeader("X-Content-Type-Options", "nosniff");
        return response;
    }

    private String archiveName(List<String> paths) {
        if (paths.size() == 1) {
            String path = paths.get(0);
            String name = path.substring(path.lastIndexOf('/') + 1).trim();
            if (!name.isEmpty()) return name + ".zip";
        }
        return "webhtv-files.zip";
    }

    private void addDownloadHeader(Response response, String name) {
        String fallback = name.replaceAll("[\\\\\"\\r\\n]", "_");
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        response.addHeader("Content-Disposition", "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded);
    }

    private Response detectSyncPaths() {
        Set<String> detected = new LinkedHashSet<>(SyncFiles.getPaths(Setting.getSyncPaths()));
        for (int type = 0; type <= 2; type++) for (Config config : Config.getAll(type)) addConfiguredPath(detected, config.getUrl());
        List<String> paths = detected.stream().toList();
        Setting.putSyncPaths(SyncFiles.getPathsText(paths));
        JsonObject object = new JsonObject();
        object.add("paths", array(paths));
        object.addProperty("text", SyncFiles.getPathsText(paths));
        return json(object);
    }

    private void addConfiguredPath(Set<String> paths, String value) {
        String candidate = localCandidate(value);
        if (TextUtils.isEmpty(candidate)) return;
        File file = Path.local(candidate);
        if (!file.exists()) return;
        String path = relativeLocalPath(file.isDirectory() ? file : file.getParentFile());
        if (!path.isEmpty() && !coveredBy(paths, path)) paths.add(path);
    }

    private String localCandidate(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String text = value.trim();
        int cut = text.indexOf('#');
        if (cut >= 0) text = text.substring(0, cut);
        cut = text.indexOf('?');
        if (cut >= 0) text = text.substring(0, cut);
        String lower = text.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("proxy://") || lower.startsWith("assets://")) return "";
        if (lower.startsWith("file:") || text.startsWith("/")) return text;
        if (lower.contains("://")) return "";
        return text.contains("/") || text.contains("\\") ? text : "";
    }

    private String cleanRelativePath(String value) {
        String path = value == null ? "" : value.trim();
        while (path.startsWith("/")) path = path.substring(1);
        return path;
    }

    private String relativeLocalPath(File file) {
        try {
            if (file == null) return "";
            File root = Path.root().getCanonicalFile();
            File target = file.getCanonicalFile();
            return inside(root, target) ? relativeTo(root, target) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean coveredBy(Set<String> paths, String path) {
        for (String existing : paths) if (covers(existing, path)) return true;
        return false;
    }

    private boolean covers(String parent, String child) {
        String p = SyncFiles.normalize(parent);
        String c = SyncFiles.normalize(child);
        return !p.isEmpty() && (p.equals(c) || c.startsWith(p + "/"));
    }

    private Response syncStart(Map<String, String> params) throws IOException {
        String device = params.get("device");
        if (TextUtils.isEmpty(device)) return Nano.error(Status.BAD_REQUEST, "Missing device");
        String direction = params.getOrDefault("mode", "push");
        boolean pull = "pull".equalsIgnoreCase(direction) || "2".equals(direction);
        SyncOptions options = SyncOptions.objectFrom(params.get("options"));
        if (params.containsKey("paths")) options.paths(params.get("paths"));
        SyncFiles.Archive archive = null;
        LoginStateSync.Archive loginArchive = null;
        try {
            if (!pull && SyncFiles.hasPaths(options)) archive = SyncFiles.createArchive(SyncFiles.getPaths(options));
            if (!pull && options.isLoginState()) loginArchive = LoginStateSync.createArchive();
            RequestBody body = buildSyncBody(pull, options, archive, loginArchive);
            String remote = device.replaceAll("/+$", "") + "/action?do=sync&mode=" + (pull ? "2" : "1") + "&type=backup";
            SpiderDebug.log("sync", "manage start direction=%s device=%s options=%s archive=%s", pull ? "pull" : "push", device, options, archive == null ? "none" : archive.getFile().getAbsolutePath());
            try (okhttp3.Response response = OkHttp.client(Constant.TIMEOUT_SYNC_TRANSFER).newCall(new Request.Builder().url(remote).post(body).build()).execute()) {
                if (!response.isSuccessful()) {
                    ResponseBody responseBody = response.body();
                    String text = responseBody == null ? response.message() : responseBody.string();
                    Status status = Status.lookup(response.code());
                    return Nano.error(status == null ? Status.INTERNAL_ERROR : status, TextUtils.isEmpty(text) ? "Sync failed" : text);
                }
            }
            JsonObject object = new JsonObject();
            object.addProperty("ok", true);
            object.addProperty("mode", pull ? "pull" : "push");
            if (archive != null) {
                object.addProperty("files", archive.getCount());
                object.addProperty("rawSize", archive.getRawSize());
                object.addProperty("zipSize", archive.getZipSize());
            }
            if (loginArchive != null) {
                object.addProperty("loginFiles", loginArchive.getCount());
                object.addProperty("loginRawSize", loginArchive.getRawSize());
                object.addProperty("loginZipSize", loginArchive.getZipSize());
            }
            return json(object);
        } finally {
            if (archive != null) archive.delete();
            if (loginArchive != null) loginArchive.delete();
        }
    }

    private RequestBody buildSyncBody(boolean pull, SyncOptions options, SyncFiles.Archive archive, LoginStateSync.Archive loginArchive) {
        if (pull) {
            FormBody.Builder body = new FormBody.Builder();
            body.add("options", options.toString());
            body.add("force", "false");
            body.add("device", Device.get().toString());
            return body.build();
        }
        if (archive == null && loginArchive == null) {
            FormBody.Builder body = new FormBody.Builder();
            body.add("options", options.toString());
            body.add("force", "false");
            body.add("backup", Backup.create(options).toString());
            if (options.isRemoteRelay()) body.add("remoteRelay", RemoteStore.exportRelayConfig());
            return body.build();
        }
        MultipartBody.Builder body = new MultipartBody.Builder().setType(MultipartBody.FORM);
        body.addFormDataPart("options", options.toString());
        body.addFormDataPart("force", "false");
        body.addFormDataPart("backup", Backup.create(options).toString());
        if (options.isRemoteRelay()) body.addFormDataPart("remoteRelay", RemoteStore.exportRelayConfig());
        if (archive != null) body.addFormDataPart(SyncFiles.PART_NAME, archive.getFile().getName(), new ProgressRequestBody(archive.getFile(), ZIP, null));
        if (loginArchive != null) body.addFormDataPart(LoginStateSync.PART_NAME, loginArchive.getFile().getName(), new ProgressRequestBody(loginArchive.getFile(), ZIP, null));
        return body.build();
    }

    private Response loginState() {
        return json(loginStateObject());
    }

    private Response loginStateLearn(Map<String, String> params) {
        String action = params.getOrDefault("action", "begin");
        JsonObject object;
        if ("finish".equalsIgnoreCase(action)) {
            LoginStateSync.LearnResult result = LoginStateSync.finishLearning();
            object = loginStateObject();
            object.add("selectedNow", array(result.getSelected()));
            object.add("pendingNow", array(result.getPending()));
            object.addProperty("finished", result.isLearned());
        } else {
            LoginStateSync.beginLearning();
            object = loginStateObject();
            object.addProperty("started", true);
        }
        return json(object);
    }

    private Response loginStateFile(Map<String, String> params) throws IOException {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return Nano.error(Status.BAD_REQUEST, "Missing path");
        if (params.containsKey("content")) LoginStateSync.write(path, params.get("content"));
        LoginStateSync.TextPreview preview = LoginStateSync.preview(path);
        JsonObject object = new JsonObject();
        object.addProperty("path", preview.getPath());
        object.addProperty("displayPath", preview.getDisplayPath());
        object.addProperty("content", preview.getContent());
        object.addProperty("size", preview.getSize());
        object.addProperty("truncated", preview.isTruncated());
        object.addProperty("editable", preview.isEditable());
        object.addProperty("text", preview.isText());
        object.addProperty("encoding", preview.getEncoding());
        return json(object);
    }

    private Response loginStatePaths(Map<String, String> params) {
        if (params.containsKey("paths")) LoginStateSync.savePaths(Arrays.asList(params.get("paths").split("[\\r\\n]+")));
        return loginState();
    }

    private Response loginStateTree(Map<String, String> params) {
        LoginStateSync.Tree tree = LoginStateSync.tree(params.getOrDefault("path", ""));
        JsonObject object = new JsonObject();
        object.addProperty("path", tree.getPath());
        object.addProperty("parent", tree.getParent());
        object.addProperty("valid", tree.isValid());
        object.add("items", App.gson().toJsonTree(tree.getItems()));
        object.addProperty("total", tree.getTotal());
        object.addProperty("truncated", tree.isTruncated());
        return json(object);
    }

    private Response loginStateConfirm() {
        LoginStateSync.confirmPending();
        return loginState();
    }

    private JsonObject loginStateObject() {
        List<String> learned = LoginStateSync.learnedPaths();
        List<String> pending = LoginStateSync.pendingPaths();
        List<LoginStateSync.Candidate> findings = LoginStateSync.findings();
        int findingsTotal = findings.size();
        if (findingsTotal > LOGIN_STATE_FINDINGS_LIMIT) findings = findings.subList(0, LOGIN_STATE_FINDINGS_LIMIT);
        JsonObject object = new JsonObject();
        object.addProperty("learning", LoginStateSync.hasLearningSnapshot());
        object.addProperty("learnedCount", learned.size());
        object.addProperty("pendingCount", pending.size());
        object.add("learned", array(learned));
        object.add("pending", array(pending));
        object.add("states", App.gson().toJsonTree(LoginStateSync.pathStates(learned)));
        object.add("findings", App.gson().toJsonTree(findings));
        object.addProperty("findingsTotal", findingsTotal);
        object.addProperty("findingsTruncated", findingsTotal > findings.size());
        object.addProperty("pathsText", LoginStateSync.pathsText(learned));
        return object;
    }

    private Response proxy(Map<String, String> params) {
        if (params.containsKey("enabled") || params.containsKey("url") || params.containsKey("rules")) {
            boolean enabled = bool(params.get("enabled"), Setting.isShellProxy());
            String url = params.getOrDefault("url", Setting.getShellProxyUrl());
            String rules = params.getOrDefault("rules", Setting.getShellProxyRules());
            if (enabled && !ProxySetting.isValidRules(rules, url)) return Nano.error(Status.BAD_REQUEST, "Invalid proxy rules");
            if (!enabled) Setting.putShellProxy(false);
            Setting.putShellProxyConfig(url, rules);
            if (enabled) Setting.putShellProxy(true);
        }
        JsonObject object = new JsonObject();
        object.addProperty("enabled", Setting.isShellProxy());
        object.addProperty("url", Setting.getShellProxyUrl());
        object.addProperty("rules", Setting.getShellProxyRules());
        object.addProperty("count", ProxySetting.count());
        object.addProperty("valid", ProxySetting.isValidRules(Setting.getShellProxyRules(), Setting.getShellProxyUrl()));
        return json(object);
    }

    private Response proxySuggestSites() {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        String homeKey = VodConfig.get().getHome().getKey();
        for (Site site : VodConfig.get().getSites()) {
            if (site.isEmpty()) continue;
            JsonObject item = new JsonObject();
            item.addProperty("key", site.getKey());
            item.addProperty("name", site.getDisplayName());
            item.addProperty("home", site.getKey().equals(homeKey));
            array.add(item);
        }
        object.add("sites", array);
        object.addProperty("count", array.size());
        return json(object);
    }

    private Response proxySuggest(Map<String, String> params) {
        String key = params.getOrDefault("key", "");
        boolean all = bool(params.get("all"), false) || "all".equalsIgnoreCase(key);
        List<Site> sites = VodConfig.get().getSites().stream().filter(site -> !site.isEmpty()).toList();
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (all) {
            for (Site site : sites) addProxySuggestion(site, hosts, urls);
        } else {
            Site site = sites.stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(VodConfig.get().getHome());
            if (!site.isEmpty()) addProxySuggestion(site, hosts, urls);
        }
        JsonObject object = new JsonObject();
        object.add("hosts", array(hosts));
        object.add("urls", array(urls));
        object.addProperty("count", hosts.size());
        return json(object);
    }

    private void addProxySuggestion(Site site, LinkedHashSet<String> hosts, LinkedHashSet<String> urls) {
        ProxySetting.Suggestion suggestion = ProxySetting.suggest(site);
        hosts.addAll(suggestion.hosts());
        urls.addAll(suggestion.urls());
    }

    private Response csp(Map<String, String> params) throws Exception {
        if (params.containsKey("registry")) {
            CustomCspSetting.Registry registry = CustomCspSetting.parse(params.get("registry"));
            CustomCspSetting.save(registry);
            reloadConfigs();
        }
        CustomCspSetting.Registry registry = CustomCspSetting.load();
        CustomCspSetting.Count count = CustomCspSetting.count();
        JsonObject object = App.gson().toJsonTree(registry).getAsJsonObject();
        object.addProperty("active", count.active());
        object.addProperty("enabledCount", count.enabled());
        object.addProperty("itemsCount", registry.getItems().size());
        return json(object);
    }

    private Response cspPage(Map<String, String> params) {
        String id = params.get("id");
        if (TextUtils.isEmpty(id)) return Nano.error(Status.BAD_REQUEST, "Missing id");
        String safeId = id.replaceAll("[^A-Za-z0-9_.-]", "_");
        String link = params.get("link");
        String code = params.get("code");
        String homePage;
        if (!TextUtils.isEmpty(link)) {
            homePage = link.trim();
        } else {
            CustomCspSetting.writePage(safeId, code);
            homePage = CustomCspSetting.localUrl(safeId, "index.html");
        }
        JsonObject object = new JsonObject();
        object.addProperty("id", safeId);
        object.addProperty("homePage", homePage);
        return json(object);
    }

    private void reloadConfigs() {
        App.post(() -> VodConfig.get().clear("manage-reload").config(VodConfig.get().getConfig()).load(new Callback() {
        }));
        App.post(() -> {
            if (LiveConfig.hasLoadedLives() || !LiveConfig.get().getConfig().isEmpty() || CustomCspSetting.hasLives()) LiveConfig.get().clear().config(LiveConfig.get().getConfig()).load(new Callback() {
            });
        });
    }

    private JsonArray array(Iterable<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }

    private boolean bool(String value, boolean fallback) {
        if (value == null) return fallback;
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
    }

    private int intValue(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private Response json(JsonObject object) {
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json; charset=utf-8", object.toString());
    }

    private boolean inside(File root, File file) throws Exception {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private boolean hasVisibleDirs(File dir) {
        File[] files = dir.listFiles(file -> file.isDirectory() && !file.getName().startsWith("."));
        return files != null && files.length > 0;
    }

    private String relativeTo(File root, File file) throws Exception {
        return root.toPath().relativize(file.getCanonicalFile().toPath()).toString().replace(File.separatorChar, '/');
    }

    private String parentOf(File root, File dir) throws Exception {
        if (root.equals(dir)) return ".";
        File parent = dir.getParentFile();
        if (parent == null || parent.equals(root)) return "";
        return relativeTo(root, parent);
    }
}
