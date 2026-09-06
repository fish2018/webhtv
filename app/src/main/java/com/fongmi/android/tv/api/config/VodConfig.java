package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.CspWarmup;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VodConfig extends BaseConfig {

    private static final String TAG = VodConfig.class.getSimpleName();

    private Site home;
    private String wall;
    private Parse parse;
    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<String> ads;
    private List<String> flags;
    private List<Parse> parses;

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public VodConfig init() {
        return clear().config(Config.vod());
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        ads = null;
        doh = null;
        home = null;
        wall = null;
        parse = null;
        sites = null;
        flags = null;
        rules = null;
        parses = null;
        WebHomeExtensionRegistry.get().setGlobalSources(null, "");
        BaseLoader.get().clear();
        RuleConfig.get().invalidate();
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.vod();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.vod();
    }

    @Override
    protected void load(Config config) throws Throwable {
        if (config.isEmpty()) {
            try {
                initSites(config, "", new JsonObject());
            } catch (Throwable ignored) {}
            return;
        }
        String globalSpider = "";
        try {
            String json = Decoder.getJson(UrlUtil.convert(config.getUrl()), TAG);
            JsonObject object = Json.parse(json).getAsJsonObject();
            globalSpider = Json.safeString(object, "spider");
            checkJson(config, object);
            return;
        } catch (Throwable ignored) {}
        try {
            initSites(config, globalSpider, new JsonObject());
        } catch (Throwable ignored) {}
    }

    @Override
    protected boolean isLoaded() {
        return !getSites().isEmpty();
    }

    @Override
    protected void beforeLoad() {
        CspWarmup.reset();
    }

    @Override
    protected void onLoadSuccess() {
        CspWarmup.schedule("vod-config-loaded");
    }

    private void checkJson(Config config, JsonObject object) throws Throwable {
        if (object.has("msg")) {
            throw new Exception(object.get("msg").getAsString());
        } else if (object.has("urls")) {
            parseDepot(config, object);
        } else {
            parseConfig(config, object);
        }
    }

    private void parseDepot(Config config, JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, VOD));
        if (configs.isEmpty()) throw new Exception("Depot urls is empty");
        load(this.config = configs.get(0));
        Config.delete(config.getUrl());
    }

    private void parseConfig(Config config, JsonObject object) {
        CustomCspSetting.inject(object);
        initList(object);
        initLive(config, object);
        initWall(config, object);
        initSite(config, object);
        initParse(config, object);
        WebHomeExtensionRegistry.get().setGlobalSources(object.get("webHomeExtensions"), config.getUrl());
        config.setLogo(Json.safeString(object, "logo"));
        config.setNotice(Json.safeString(object, "notice"));
        config.setDanmaku(Json.safeString(object, "danmaku"));
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setDoh(Doh.arrayFrom(fetchArray(object, "doh")));
        setFlags(Json.safeListString(object, "flags"));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initLive(Config config, JsonObject object) {
        if (Json.isEmpty(object, "lives")) return;
        Config temp = Config.find(config, LIVE).save();
        boolean sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) LiveConfig.get().config(temp.update()).parse(object);
    }

    private void initWall(Config config, JsonObject object) {
        if (Json.isEmpty(object, "wallpaper")) return;
        this.wall = Json.safeString(object, "wallpaper");
        Config temp = Config.find(wall, config.getName(), WALL).save();
        boolean sync = WallConfig.get().needSync(wall);
        if (sync) WallConfig.get().config(temp.update());
    }

    private void initSite(Config config, JsonObject object) {
        initSites(config, "", object);
    }

    private void initSites(Config config, String globalSpider, JsonObject object) {
        String spider = TextUtils.isEmpty(globalSpider) ? UrlUtil.convert("./jars/XBPQ.jar") : globalSpider;
        BaseLoader.get().parseJar(spider, true);
        List<Site> sites = new ArrayList<>();
        if (Setting.isSourceAllowed(Setting.SOURCE_VOD_URL)) {
            sites.addAll(Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new)));
        }
        List<Site> fileSites = loadFileSites(spider);
        sites.addAll(0, fileSites);
        setSites(sites);
        Map<String, Site> items = Site.findAll().stream().collect(Collectors.toMap(Site::getKey, Function.identity()));
        getSites().forEach(site -> site.sync(items.get(site.getKey())));
        CustomCspSetting.Result custom = CustomCspSetting.inject(getSites());
        Site home = !custom.home().isEmpty() ? custom.home() : getSites().stream().filter(item -> item.getKey().equals(config.getHome())).findFirst().orElse(getSites().isEmpty() ? new Site() : getSites().get(0));
        setHome(config, home, false);
    }

    private void initParse(Config config, JsonObject object) {
        setParses(Json.safeListElement(object, "parses").stream().map(Parse::objectFrom).distinct().collect(Collectors.toCollection(ArrayList::new)));
        setParse(config, getParses().isEmpty() ? new Parse() : getParses().stream().filter(item -> item.getName().equals(config.getParse())).findFirst().orElse(getParses().get(0)), false);
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    private void setSites(List<Site> sites) {
        this.sites = sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    private void setParses(List<Parse> parses) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        this.parses = parses;
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    private void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
        RuleConfig.get().invalidate();
    }

    public List<Parse> getParses(int type) {
        return getParses().stream().filter(item -> item.getType() == type).toList();
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = getParses(type);
        List<Parse> filter = items.stream().filter(item -> item.getExt().getFlag().contains(flag)).toList();
        return filter.isEmpty() ? items : filter;
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
        RuleConfig.get().invalidate();
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public void setParse(Parse parse) {
        setParse(getConfig(), parse, true);
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public void setHome(Site site) {
        setHome(getConfig(), site, true);
        RefreshEvent.home();
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        return getParses().stream().filter(item -> item.getName().equals(name)).findFirst().orElse(new Parse());
    }

    public Site getSite(String key) {
        return getSites().stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(new Site());
    }

    private void setParse(Config config, Parse parse, boolean save) {
        this.parse = parse;
        this.parse.setSelected(true);
        config.setParse(parse.getName());
        getParses().forEach(item -> item.setSelected(parse));
        if (save) config.save();
    }

    // ==================== 文件站点加载器 ====================

    private static final String CLAN_ROOT = Path.root() + "/tvbox/";
    private static final String XBPQ_JAR = UrlUtil.convert("./jars/XBPQ.jar");

    private List<Site> loadFileSites(String globalSpider) {
        List<Site> result = new ArrayList<>();
        if (Setting.isSourceAllowed(Setting.SOURCE_SITES_JSON)) {
            try { result.addAll(loadXbpqSites(globalSpider)); } catch (Throwable ignored) {}
        }
        if (Setting.isSourceAllowed(Setting.SOURCE_SITES_JS)) {
            try { result.addAll(loadJsSites(globalSpider)); } catch (Throwable ignored) {}
        }
        if (Setting.isSourceAllowed(Setting.SOURCE_SITES_PY)) {
            try { result.addAll(loadPySites(globalSpider)); } catch (Throwable ignored) {}
        }
        if (Setting.isSourceAllowed(Setting.SOURCE_SITES_RAW)) {
            try { result.addAll(loadRawSites(globalSpider)); } catch (Throwable ignored) {}
        }
        return result;
    }

    private List<Site> loadXbpqSites(String globalSpider) {
        File dir = new File(CLAN_ROOT + "sites-json");
        List<Site> result = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return result;
        List<File> files = listSorted(dir);
        if (files.isEmpty()) return result;
        String jar;
        if (!TextUtils.isEmpty(globalSpider)
                && globalSpider.toLowerCase().contains("xbpq")
                && Path.local(globalSpider) != null
                && Path.local(globalSpider).exists()) {
            jar = globalSpider;
        } else {
            jar = XBPQ_JAR;
            BaseLoader.get().parseJar(jar, true);
        }
        for (File file : files) {
            FileMeta meta = parseFileMeta(file.getName());
            if (meta.name.isEmpty()) continue;
            Site site = Site.get("XBPQ_" + file.getName() + "_file", meta.name);
            site.setType(3);
            site.setApi("csp_XBPQ");
            site.setExt(UrlUtil.convert("clan://sites-json/" + file.getName()));
            site.setJar(jar);
            site.setName(meta.name + " | PQ");
            meta.apply(site);
            result.add(site);
        }
        return result;
    }

    private List<Site> loadJsSites(String globalSpider) {
        File dir = new File(CLAN_ROOT + "sites-js", "api");
        List<Site> result = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return result;
        List<File> files = listSorted(dir);
        for (File file : files) {
            FileMeta meta = parseFileMeta(file.getName());
            if (meta.name.isEmpty()) continue;
            Site site = Site.get("JS_" + file.getName() + "_file", meta.name);
            site.setType(3);
            site.setApi(UrlUtil.convert("clan://sites-js/api/" + file.getName()));
            site.setJar(globalSpider);
            site.setName(meta.name + " | JS");
            meta.apply(site);
            result.add(site);
        }
        return result;
    }

    private List<Site> loadPySites(String globalSpider) {
        File dir = new File(CLAN_ROOT + "sites-py");
        List<Site> result = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return result;
        List<File> files = listSorted(dir);
        for (File file : files) {
            FileMeta meta = parseFileMeta(file.getName());
            if (meta.name.isEmpty()) continue;
            Site site = Site.get("PY_" + file.getName() + "_file", meta.name);
            site.setType(3);
            site.setApi(UrlUtil.convert("clan://sites-py/" + file.getName()));
            site.setJar(globalSpider);
            site.setName(meta.name + " | PY");
            meta.apply(site);
            result.add(site);
        }
        return result;
    }

    private List<Site> loadRawSites(String globalSpider) {
        File dir = new File(CLAN_ROOT + "sites");
        List<Site> result = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return result;
        List<File> files = listSorted(dir);
        for (File file : files) {
            FileMeta meta = parseFileMeta(file.getName());
            String content = Path.read(file);
            if (content.isEmpty()) continue;
            try {
                JsonElement element = Json.parse(content);
                if (element.isJsonObject()) {
                    Site site = Site.objectFrom(element, globalSpider);
                    if (site.getName().isEmpty()) site.setName(meta.name);
                    site.setKey("RAW_" + file.getName() + "_file");
                    meta.apply(site);
                    result.add(site);
                }
            } catch (Throwable ignored) {}
        }
        return result;
    }

    private List<File> listSorted(File dir) {
        File[] files = dir.listFiles(f -> f.isFile() && !f.getName().startsWith("."));
        if (files == null) return Collections.emptyList();
        Arrays.sort(files, (a, b) -> {
            FileMeta ma = parseFileMeta(a.getName());
            FileMeta mb = parseFileMeta(b.getName());
            int c = Integer.compare(ma.order, mb.order);
            if (c != 0) return c;
            return ma.name.compareToIgnoreCase(mb.name);
        });
        return Arrays.asList(files);
    }

    private FileMeta parseFileMeta(String fileName) {
        FileMeta meta = new FileMeta();
        if (fileName == null) return meta;
    
        String stem = fileName;
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) stem = fileName.substring(0, lastDot);
    
        if (stem.matches("^\\d+_.*")) {
            int sep = stem.indexOf('_');
            meta.order = Integer.parseInt(stem.substring(0, sep));
            stem = stem.substring(sep + 1);
        }
    
        int firstDot = stem.indexOf('.');
        if (firstDot > 0) {
            String func = stem.substring(firstDot + 1);
            String upper = func.toUpperCase();
    
            boolean isFunc = upper.startsWith("N") || upper.startsWith("S") || func.contains("-");
    
            if (isFunc) {
                stem = stem.substring(0, firstDot);
    
                if (upper.startsWith("N")) { meta.searchable = 0; meta.quickSearch = 0; }
                else if (upper.startsWith("S")) { meta.hide = 1; }
    
                int dash = func.lastIndexOf('-');
                if (dash >= 0 && dash < func.length() - 1) {
                    String tail = func.substring(dash + 1);
                    if (tail.equalsIgnoreCase("H")) meta.ratio = 1.33f;
                    else if (tail.equalsIgnoreCase("S")) meta.ratio = 1.0f;
                    else { try { meta.ratio = Float.parseFloat(tail); }
                    catch (NumberFormatException ignored) {} }
                }
            }
        }
    
        meta.name = stem.trim();
        return meta;
    }

    private static class FileMeta {
        int order = Integer.MAX_VALUE;
        String name = "";
        Integer searchable;
        Integer quickSearch;
        Integer hide;
        float ratio = 0;

        void apply(Site site) {
            if (searchable != null) site.setSearchable(searchable);
            if (quickSearch != null) site.setQuickSearch(quickSearch);
            if (hide != null) site.setHide(hide);
            if (ratio > 0) site.setStyle(new Style("rect", ratio));
        }
    }

    private void setHome(Config config, Site site, boolean save) {
        home = site;
        home.setSelected(true);
        config.setHome(home.getKey());
        if (save) config.save();
        getSites().forEach(item -> item.setSelected(home));
    }

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }
}
