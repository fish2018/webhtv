package com.fongmi.chaquo;

import android.text.TextUtils;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.io.File;
import java.nio.charset.StandardCharsets;

import okhttp3.Response;

public class Loader {

    private final PyObject app;

    public Loader() {
        if (!Python.isStarted()) Python.start(Platform.create());
        app = Python.getInstance().getModule("app");
    }

    public Spider spider(String api) {
        String name = scriptName(api);
        String source = source(api, name);
        PyObject obj = app.callAttr("spider", Path.py().getAbsolutePath(), source, name);
        return new Spider(app, obj, api);
    }

    private String source(String api, String name) {
        if (api.startsWith("http")) {
            File cache = Path.py(name);
            try (Response response = OkHttp.newCall(OkHttp.client(15000), api).execute()) {
                if (!response.isSuccessful()) throw new IllegalStateException("HTTP " + response.code());
                String source = response.body().string();
                if (TextUtils.isEmpty(source)) throw new IllegalStateException("Empty python script");
                Path.write(cache, source.getBytes(StandardCharsets.UTF_8));
                return source;
            } catch (Exception e) {
                String cached = Path.read(cache);
                if (!TextUtils.isEmpty(cached)) return cached;
                throw new IllegalStateException("Unable to download python script: " + api, e);
            }
        }
        File local = local(api);
        if (local != null) return Path.read(local);
        return api;
    }

    private File local(String api) {
        if (api == null) return null;
        api = api.replace("clan://", "file://tvbox/");
        if (api.startsWith("file://")) return new File(api.replace("file://", ""));
        if (api.startsWith("file:/")) return new File(api.replace("file:/", ""));
        if (api.startsWith("/")) return new File(api);
        return null;
    }

    private String scriptName(String api) {
        String lower = api.toLowerCase();
        String suffix = lower.contains(".py") ? ".py" : ".script";
        return Util.md5(api) + suffix;
    }
}