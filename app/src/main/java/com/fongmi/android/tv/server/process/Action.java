package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Backup;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.SyncOptions;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import okhttp3.FormBody;

public class Action implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/action");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        Map<String, String> params = session.getParms();
        String param = params.get("do");
        SpiderDebug.log("action", "do=%s params=%s", param, params);
        return TextUtils.isEmpty(param) ? Nano.ok() : doJob(param, params);
    }

    private Response doJob(String param, Map<String, String> params) {
        return switch (param) {
            case "file" -> {
                onFile(params);
                yield Nano.ok();
            }
            case "push" -> {
                onPush(params);
                yield Nano.ok();
            }
            case "cast" -> {
                onCast(params);
                yield Nano.ok();
            }
            case "sync" -> onSync(params);
            case "search" -> {
                onSearch(params);
                yield Nano.ok();
            }
            case "setting" -> {
                onSetting(params);
                yield Nano.ok();
            }
            case "refresh" -> {
                onRefresh(params);
                yield Nano.ok();
            }
            case "control" -> {
                onControl(params);
                yield Nano.ok();
            }
            case "danmaku" -> {
                onDanmaku(params);
                yield Nano.ok();
            }
            default -> Nano.ok();
        };
    }

    private void onFile(Map<String, String> params) {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return;
        if (path.endsWith(".apk")) FileUtil.openFile(Path.local(path));
        else if (path.endsWith(".srt") || path.endsWith(".ssa") || path.endsWith(".ass")) RefreshEvent.subtitle(path);
        else ServerEvent.setting(path);
    }

    private void onPush(Map<String, String> params) {
        String url = params.get("url");
        if (TextUtils.isEmpty(url)) return;
        ServerEvent.push(url);
    }

    private void onSearch(Map<String, String> params) {
        String word = params.get("word");
        if (TextUtils.isEmpty(word)) return;
        ServerEvent.search(word);
    }

    private void onSetting(Map<String, String> params) {
        String text = params.get("text");
        String name = params.get("name");
        if (TextUtils.isEmpty(text)) return;
        ServerEvent.setting(text, name);
    }

    private void onRefresh(Map<String, String> params) {
        String type = params.get("type");
        String path = params.get("path");
        String json = params.get("json");
        if (TextUtils.isEmpty(type)) return;
        switch (type) {
            case "home" -> RefreshEvent.home();
            case "live" -> RefreshEvent.live();
            case "detail" -> RefreshEvent.detail();
            case "player" -> RefreshEvent.player();
            case "category" -> RefreshEvent.category();
            case "danmaku" -> RefreshEvent.danmaku(path);
            case "subtitle" -> RefreshEvent.subtitle(path);
            case "vod" -> RefreshEvent.vod(Vod.objectFrom(json));
        }
    }

    private void onControl(Map<String, String> params) {
        String type = params.get("type");
        PlaybackService service = Server.get().getService();
        if (service == null || TextUtils.isEmpty(type)) return;
        switch (type) {
            case "play" -> App.post(() -> service.player().play());
            case "pause" -> App.post(() -> service.player().pause());
            case "stop" -> App.post(service::dispatchStop);
            case "prev" -> App.post(service::dispatchPrev);
            case "next" -> App.post(service::dispatchNext);
            case "repeat" -> App.post(service::dispatchRepeat);
            case "replay" -> App.post(service::dispatchReplay);
        }
    }

    private void onDanmaku(Map<String, String> params) {
        String text = params.get("text");
        PlaybackService service = Server.get().getService();
        if (service == null || TextUtils.isEmpty(text)) return;
        App.post(() -> service.player().sendDanmaku(text));
    }

    private void onCast(Map<String, String> params) {
        Config config = Config.objectFrom(params.get("config"));
        Device device = Device.objectFrom(params.get("device"));
        History history = History.objectFrom(params.get("history"));
        CastEvent.post(Config.find(config), device, history);
    }

    private Response onSync(Map<String, String> params) {
        String type = params.get("type");
        boolean force = Objects.equals(params.get("force"), "true");
        String mode = Objects.requireNonNullElse(params.get("mode"), "0");
        boolean success = true;
        if (params.get("device") != null && (mode.equals("0") || mode.equals("2"))) {
            Device device = Device.objectFrom(params.get("device"));
            if ("history".equals(type)) success = sendHistory(device, params);
            else if ("keep".equals(type)) success = sendKeep(device);
            else if ("backup".equals(type)) success = sendBackup(device, params);
        }
        if (mode.equals("0") || mode.equals("1")) {
            if ("history".equals(type)) syncHistory(params, force);
            else if ("keep".equals(type)) syncKeep(params, force);
            else if ("backup".equals(type)) syncBackup(params, force);
        }
        return success ? Nano.ok() : Nano.error(ResUtil.getString(R.string.sync_failed));
    }

    private boolean post(Device device, String type, FormBody.Builder body) {
        try {
            try (okhttp3.Response response = OkHttp.newCall(OkHttp.client(Constant.TIMEOUT_SYNC_TRANSFER), device.getIp().concat("/action?do=sync&mode=0&type=" + type), body.build()).execute()) {
                if (response.isSuccessful()) return true;
                throw new IllegalStateException(response.message());
            }
        } catch (Exception e) {
            App.post(() -> Notify.show(e.getMessage()));
            return false;
        }
    }

    private boolean sendHistory(Device device, Map<String, String> params) {
        try {
            Config config = Config.find(Config.objectFrom(params.get("config")));
            if (config.getUrl() == null) config = Config.vod();
            FormBody.Builder body = new FormBody.Builder();
            body.add("config", config.toString());
            body.add("targets", App.gson().toJson(History.get(config.getId())));
            return post(device, "history", body);
        } catch (Exception e) {
            App.post(() -> Notify.show(e.getMessage()));
            return false;
        }
    }

    private boolean sendKeep(Device device) {
        try {
            FormBody.Builder body = new FormBody.Builder();
            body.add("targets", App.gson().toJson(Keep.getVod()));
            body.add("configs", App.gson().toJson(Config.findUrls()));
            return post(device, "keep", body);
        } catch (Exception e) {
            App.post(() -> Notify.show(e.getMessage()));
            return false;
        }
    }

    private boolean sendBackup(Device device, Map<String, String> params) {
        try {
            SyncOptions options = SyncOptions.objectFrom(params.get("options"));
            FormBody.Builder body = new FormBody.Builder();
            body.add("options", options.toString());
            body.add("backup", Backup.create(options).toString());
            return post(device, "backup", body);
        } catch (Exception e) {
            App.post(() -> Notify.show(e.getMessage()));
            return false;
        }
    }

    private void syncBackup(Map<String, String> params, boolean force) {
        Backup backup = Backup.objectFrom(params.get("backup"));
        SyncOptions options = SyncOptions.objectFrom(params.get("options"));
        backup.restore(options, force);
        App.post(() -> Notify.show(R.string.sync_receive_success));
    }

    public void syncHistory(Map<String, String> params, boolean force) {
        Config config = Config.find(Config.objectFrom(params.get("config")));
        List<History> targets = History.arrayFrom(params.get("targets"));
        if (config.getUrl() == null) return;
        if (config.getUrl().equals(VodConfig.getUrl())) {
            if (force) History.delete(config.getId());
            History.sync(targets);
            RefreshEvent.history();
        } else {
            VodConfig.load(config, getCallback(targets, force, config.getId()));
        }
    }

    private Callback getCallback(List<History> targets, boolean force, int cid) {
        return new Callback() {
            @Override
            public void success() {
                if (force) History.delete(cid);
                History.sync(targets);
                RefreshEvent.history();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    private void syncKeep(Map<String, String> params, boolean force) {
        List<Keep> targets = Keep.arrayFrom(params.get("targets"));
        List<Config> configs = Config.arrayFrom(params.get("configs"));
        if (TextUtils.isEmpty(VodConfig.getUrl()) && !configs.isEmpty()) {
            VodConfig.load(Config.find(configs.get(0)), getCallback(configs, targets, force));
        } else {
            if (force) Keep.deleteAll();
            Keep.sync(configs, targets);
            RefreshEvent.keep();
        }
    }

    private Callback getCallback(List<Config> configs, List<Keep> targets, boolean force) {
        return new Callback() {
            @Override
            public void success() {
                if (force) Keep.deleteAll();
                Keep.sync(configs, targets);
                RefreshEvent.keep();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }
}
