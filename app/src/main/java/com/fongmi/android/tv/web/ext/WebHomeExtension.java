package com.fongmi.android.tv.web.ext;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.GithubProxy;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class WebHomeExtension {

    public static final String RUN_AT_START = "document-start";
    public static final String RUN_AT_END = "document-end";
    public static final String RUN_AT_IDLE = "document-idle";

    private final List<String> cspKeyRegex;
    private final List<String> excludeCspKeyRegex;
    private final List<String> scripts;
    private final List<String> depends;
    private final String sourceUrl;
    private final String updateUrl;
    private final String version;
    private final String runAt;
    private final String name;
    private final String id;
    private final boolean defaultEnabled;
    private final boolean siteScoped;
    private final boolean remote;
    private final int order;

    private WebHomeExtension(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.runAt = builder.runAt;
        this.sourceUrl = builder.sourceUrl;
        this.updateUrl = builder.updateUrl;
        this.siteScoped = builder.siteScoped;
        this.defaultEnabled = builder.defaultEnabled;
        this.remote = builder.remote;
        this.order = builder.order;
        this.cspKeyRegex = builder.cspKeyRegex;
        this.excludeCspKeyRegex = builder.excludeCspKeyRegex;
        this.depends = builder.depends;
        this.scripts = builder.scripts;
    }

    public static WebHomeExtension from(JsonObject object, String siteKey, String sourceUrl, String baseUrl, boolean siteScoped, boolean defaultEnabled, int order) {
        Builder builder = new Builder();
        builder.id = value(object, "id");
        builder.name = value(object, "name");
        builder.version = value(object, "version");
        builder.runAt = runAt(value(object, "runAt"));
        builder.sourceUrl = sourceUrl;
        builder.updateUrl = resolve(baseUrl, value(object, "updateUrl"));
        builder.siteScoped = siteScoped;
        builder.defaultEnabled = defaultEnabled;
        builder.remote = !TextUtils.isEmpty(sourceUrl);
        builder.order = order;
        builder.cspKeyRegex = list(object, "cspKeyRegex");
        builder.excludeCspKeyRegex = list(object, "excludeCspKeyRegex");
        builder.depends = list(object, "depends");
        if (TextUtils.isEmpty(builder.id)) builder.id = Util.md5((TextUtils.isEmpty(sourceUrl) ? siteKey : sourceUrl) + ":" + order);
        if (TextUtils.isEmpty(builder.name)) builder.name = builder.id;
        if (object.has("code") && object.get("code").isJsonPrimitive()) builder.scripts.add(object.get("code").getAsString());
        for (String js : list(object, "js")) addScript(builder, resolve(baseUrl, js));
        if (builder.scripts.isEmpty()) return null;
        return new WebHomeExtension(builder);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getUpdateUrl() {
        return updateUrl;
    }

    public String getRunAt() {
        return runAt;
    }

    public List<String> getDepends() {
        return depends;
    }

    public List<String> getCspKeyRegex() {
        return cspKeyRegex;
    }

    public List<String> getExcludeCspKeyRegex() {
        return excludeCspKeyRegex;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public boolean isSiteScoped() {
        return siteScoped;
    }

    public boolean isRemote() {
        return remote;
    }

    public int getRunAtOrder() {
        if (RUN_AT_START.equals(runAt)) return 0;
        if (RUN_AT_IDLE.equals(runAt)) return 2;
        return 1;
    }

    public int getOrder() {
        return order;
    }

    public boolean matches(String siteKey) {
        boolean include = (siteScoped && cspKeyRegex.isEmpty()) || matchesAny(cspKeyRegex, siteKey);
        return include && !matchesAny(excludeCspKeyRegex, siteKey);
    }

    public boolean shouldInjectAt(String targetRunAt) {
        if (RUN_AT_IDLE.equals(targetRunAt)) return RUN_AT_IDLE.equals(runAt);
        if (!RUN_AT_END.equals(targetRunAt)) return false;
        return RUN_AT_END.equals(runAt) || RUN_AT_START.equals(runAt);
    }

    public String script(String siteKey) {
        StringBuilder body = new StringBuilder();
        for (String script : scripts) body.append('\n').append(script).append('\n');
        return String.format(Locale.US, """
                (function(){
                  if(window.top!==window)return;
                  const __fmExt={id:%s,name:%s,siteKey:%s,source:%s,runAt:%s};
                  const GM_addStyle=function(css){const s=document.createElement('style');s.textContent=css||'';(document.head||document.documentElement).appendChild(s);return s;};
                  const GM_log=function(){try{const args=Array.prototype.slice.call(arguments);console.log.apply(console,['[fm-ext]',__fmExt.id].concat(args));if(window.fm&&window.fm.ext&&window.fm.ext.log)window.fm.ext.log(args.map(String).join(' '),__fmExt);}catch(e){}};
                  const GM_getValue=function(key,def){return window.fm&&window.fm.cache?window.fm.cache.get(key,'webhome_ext_'+__fmExt.id).then(v=>v===''?def:v):Promise.resolve(def);};
                  const GM_setValue=function(key,value){return window.fm&&window.fm.cache?window.fm.cache.set(key,String(value),'webhome_ext_'+__fmExt.id):Promise.resolve();};
                  const GM_deleteValue=function(key){return window.fm&&window.fm.cache?window.fm.cache.del(key,'webhome_ext_'+__fmExt.id):Promise.resolve();};
                  const GM_xmlhttpRequest=function(details){details=details||{};const p=window.fm&&window.fm.req?window.fm.req(details.url,details):Promise.reject(new Error('fm.req unavailable'));p.then(r=>details.onload&&details.onload(r)).catch(e=>details.onerror&&details.onerror(e));return{abort:function(){}};};
                  try{%s
                    window.dispatchEvent(new CustomEvent('fmextload',{detail:__fmExt}));
                  }catch(e){
                    console.error('[fm-ext]',__fmExt.id,e&&e.stack||e);
                    if(window.fm&&window.fm.ext&&window.fm.ext.log)window.fm.ext.log('error '+String(e&&e.message||e),__fmExt);
                    window.dispatchEvent(new CustomEvent('fmexterror',{detail:Object.assign({},__fmExt,{message:String(e&&e.message||e)})}));
                  }
                })();
                //# sourceURL=fm-ext-%s.js
                """, quote(id), quote(name), quote(siteKey), quote(sourceUrl), quote(runAt), body, safeSourceName(id));
    }

    public String dependencyId(String dependency) {
        int index = dependency.indexOf('@');
        return (index == -1 ? dependency : dependency.substring(0, index)).trim();
    }

    public boolean acceptsDependency(WebHomeExtension dependency, String spec) {
        int index = spec.indexOf('@');
        if (index == -1) return true;
        String constraint = spec.substring(index + 1).trim();
        if (TextUtils.isEmpty(constraint)) return true;
        return matchVersion(dependency.getVersion(), constraint);
    }

    public String matchText() {
        if (siteScoped && cspKeyRegex.isEmpty()) return "site";
        return TextUtils.join(", ", cspKeyRegex);
    }

    public String excludeText() {
        return TextUtils.join(", ", excludeCspKeyRegex);
    }

    public String dependsText() {
        return TextUtils.join(", ", depends);
    }

    private static void addScript(Builder builder, String url) {
        String script = readCached(url, "script_", ".js");
        if (!TextUtils.isEmpty(script)) builder.scripts.add(script);
    }

    public static JsonObject manifest(String url) {
        JsonElement element = json(url);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    public static JsonElement json(String url) {
        try {
            String text = readCached(url, "manifest_", ".json");
            return TextUtils.isEmpty(text) ? null : Json.parse(text);
        } catch (Throwable e) {
            SpiderDebug.log("webhome-ext", "manifest parse failed url=%s error=%s", url, e.getMessage());
            return null;
        }
    }

    public static String resolve(String baseUrl, String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (!UrlUtil.scheme(url).isEmpty()) return url;
        return UrlUtil.resolve(baseUrl, url);
    }

    public static boolean isScriptUrl(String url) {
        String lower = url == null ? "" : url.toLowerCase();
        return lower.contains(".js") || lower.startsWith("file://") || lower.startsWith("local://");
    }

    private static String readCached(String url, String prefix, String suffix) {
        if (TextUtils.isEmpty(url)) return "";
        if ("file".equals(UrlUtil.scheme(url))) return Path.read(Path.local(url.substring("file://".length())));
        if ("local".equals(UrlUtil.scheme(url))) return Path.read(Path.files(url.substring("local://".length())));
        File file = cache(prefix + Util.md5(url) + suffix);
        String cached = Path.read(file);
        try {
            String text = OkHttp.string(GithubProxy.apply(url));
            if (!TextUtils.isEmpty(text)) {
                Path.write(file, text.getBytes(StandardCharsets.UTF_8));
                SpiderDebug.log("webhome-ext", "fetch ok url=%s bytes=%s", url, text.length());
                return text;
            }
        } catch (Throwable e) {
            SpiderDebug.log("webhome-ext", "fetch failed url=%s cached=%s error=%s", url, !TextUtils.isEmpty(cached), e.getMessage());
        }
        return cached;
    }

    private static File cache(String name) {
        File dir = Path.cache("webhome_ext");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    private static boolean matchesAny(List<String> patterns, String value) {
        for (String pattern : patterns) {
            try {
                if (Pattern.compile(pattern).matcher(value).find()) return true;
            } catch (Throwable e) {
                SpiderDebug.log("webhome-ext", "invalid regex=%s error=%s", pattern, e.getMessage());
            }
        }
        return false;
    }

    private static boolean matchVersion(String version, String constraint) {
        if (TextUtils.isEmpty(version)) return false;
        String op = constraint.startsWith(">=") || constraint.startsWith("<=") ? constraint.substring(0, 2) : constraint.startsWith(">") || constraint.startsWith("<") || constraint.startsWith("=") ? constraint.substring(0, 1) : "=";
        String target = constraint.substring(op.length()).trim();
        int compare = compareVersion(version, target);
        return switch (op) {
            case ">=" -> compare >= 0;
            case ">" -> compare > 0;
            case "<=" -> compare <= 0;
            case "<" -> compare < 0;
            default -> compare == 0;
        };
    }

    private static int compareVersion(String left, String right) {
        String[] a = left.split("[.-]");
        String[] b = right.split("[.-]");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            String x = i < a.length ? a[i] : "0";
            String y = i < b.length ? b[i] : "0";
            int value = comparePart(x, y);
            if (value != 0) return value;
        }
        return 0;
    }

    private static int comparePart(String left, String right) {
        try {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        } catch (NumberFormatException ignored) {
            return left.compareTo(right);
        }
    }

    private static List<String> list(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        JsonElement element = object.get(key);
        if (element.isJsonPrimitive()) result.add(element.getAsString().trim());
        else if (element.isJsonArray()) for (JsonElement item : element.getAsJsonArray()) if (item.isJsonPrimitive()) result.add(item.getAsString().trim());
        result.removeIf(TextUtils::isEmpty);
        return result;
    }

    private static String value(JsonObject object, String key) {
        return Json.safeString(object, key);
    }

    private static String runAt(String value) {
        if (RUN_AT_START.equals(value) || RUN_AT_IDLE.equals(value)) return value;
        return RUN_AT_END;
    }

    private static String quote(String value) {
        return App.gson().toJson(value == null ? "" : value);
    }

    private static String safeSourceName(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static class Builder {
        private List<String> cspKeyRegex = new ArrayList<>();
        private List<String> excludeCspKeyRegex = new ArrayList<>();
        private List<String> scripts = new ArrayList<>();
        private List<String> depends = new ArrayList<>();
        private String sourceUrl = "";
        private String updateUrl = "";
        private String version = "";
        private String runAt = RUN_AT_END;
        private String name = "";
        private String id = "";
        private boolean defaultEnabled;
        private boolean siteScoped;
        private boolean remote;
        private int order;
    }
}
