package com.fongmi.quickjs.utils;

import android.text.TextUtils;
import android.util.LruCache;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Path;

import java.io.File;

public class Module {

    private static final int MAX_SIZE = 50;
    private final LruCache<String, String> cache;

    public Module() {
        cache = new LruCache<>(MAX_SIZE);
    }

    public static Module get() {
        return Loader.INSTANCE;
    }

    public String fetch(String name) {
        if (name == null) return null;
        name = name.replace("clan://", "file://tvbox/");
        String content = cache.get(name);
        if (!TextUtils.isEmpty(content)) return content;
        if (name.startsWith("http")) cache.put(name, content = OkHttp.string(name));
        else if (name.startsWith("assets")) cache.put(name, content = Asset.read(name));
        else if (name.startsWith("lib/")) cache.put(name, content = Asset.read("js/" + name));
        else if (name.startsWith("/")) cache.put(name, content = Path.read(new File(name)));
        else if (name.startsWith("file")) cache.put(name, content = Path.read(Path.local(name)));
        else if (looksLikeSource(name)) content = name;
        return content;
    }

    private boolean looksLikeSource(String text) {
        if (TextUtils.isEmpty(text)) return false;
        return text.contains("function ")
                || text.contains("const ")
                || text.contains("let ")
                || text.contains("var ")
                || text.contains("=>")
                || text.contains("import ")
                || text.contains("__jsEvalReturn");
    }

    public void clear() {
        cache.evictAll();
    }

    private static class Loader {
        static volatile Module INSTANCE = new Module();
    }
}
