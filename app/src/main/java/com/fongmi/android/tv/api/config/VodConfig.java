package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.CatSource;
import com.fongmi.android.tv.api.CspWarmup;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.GroupRule;
import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.InterfaceFailoverPolicy;
import com.fongmi.android.tv.setting.InterfaceFailoverState;
import com.fongmi.android.tv.setting.InterfaceOrderStore;
import com.fongmi.android.tv.setting.GroupRuleConfig;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VodConfig extends BaseConfig {

    private static final String TAG = VodConfig.class.getSimpleName();

    private Site home;
    private String wall;
    private Parse parse;
    private List<Doh> doh;
    private List<Rule> rules;
    private List<HlsAdRule> hlsRules;
    private List<Site> sites;
    private List<String> ads;
    private List<String> flags;
    private List<Parse> parses;
    private volatile FailoverRound failoverRound;
    private volatile AlertDialog failoverDialog;

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
        get().startNewLoad(config, callback, "vod-config-load");
    }

    public static void selectConfig(Config config, Callback callback) {
        if (config == null) return;
        get().startNewLoad(config, callback, "vod-config-select");
    }

    public static void cancelFailover() {
        get().stopFailover();
    }

    private void startNewLoad(Config config, Callback callback, String reason) {
        abandonFailover();
        clear(reason).config(config);
        super.load(callback);
    }

    @Override
    public void load(Callback callback) {
        abandonFailover();
        super.load(callback);
    }

    private void loadFailoverAttempt(Config config, Callback callback) {
        clear("vod-config-failover").config(config);
        super.load(callback);
    }

    public VodConfig init() {
        return config(Config.vod());
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        return clear("vod-config-clear");
    }

    public VodConfig clear(String reason) {
        ads = null;
        doh = null;
        home = null;
        wall = null;
        parse = null;
        sites = null;
        flags = null;
        rules = null;
        hlsRules = null;
        parses = null;
        WebHomeExtensionRegistry.get().setGlobalSources(null, "");
        BaseLoader.get().clear(reason);
        RuleConfig.get().invalidate();
        HlsRuleConfig.invalidate();
        GroupRuleConfig.setInterfaceRules(List.of());
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
        if (failoverRound != null) return;
        super.postEvent();
        ConfigEvent.vod();
    }

    @Override
    protected void load(Config config) throws Throwable {
        // 猫源填的是 bundle 地址（.js.md5），要先在本机把 Node 服务跑起来，再读它的 /config
        String url = CatSource.isBundle(config.getUrl()) ? CatSource.serve(config.getUrl()) : UrlUtil.convert(config.getUrl());
        String json = Decoder.getJson(url, TAG);
        checkJson(config, CatSource.normalize(url, Json.parse(json)));
        if (!isLoaded()) throw new Exception("VOD sites is empty");
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
        InterfaceAdRuleLearningService.schedule(getConfig().getDesc(), getConfig().getUrl(), getAds(), getRules());
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

    void onConfigFailure(Config config, Callback callback, Throwable error) {
        FailoverRound current = failoverRound;
        String message = Notify.getError(R.string.error_config_get, error);
        if (current != null && callback == current.attemptCallback) {
            current.lastError = message;
            App.post(() -> onAttemptFailure(current));
            return;
        }

        int mode = Setting.getInterfaceFailoverMode();
        if (!InterfaceFailoverPolicy.shouldFailover(mode)) {
            App.post(() -> callback.error(message));
            return;
        }

        List<Config> configs = InterfaceOrderStore.sortVodConfigs(Config.getAll(VOD));
        String originUrl = config.getUrl();
        int index = indexOfUrl(configs, originUrl);
        int limit = InterfaceFailoverPolicy.fallbackLimit(configs.size());
        List<Config> remaining = new ArrayList<>();
        for (int i = Math.max(index + 1, 0); i < configs.size() && remaining.size() < limit; i++) {
            Config candidate = configs.get(i);
            if (!TextUtils.equals(candidate.getUrl(), originUrl)) remaining.add(candidate);
        }
        if (remaining.isEmpty()) {
            App.post(() -> callback.error(message));
            return;
        }

        FailoverRound round = new FailoverRound(config.getDesc(), remaining, callback, message,
                new InterfaceFailoverState(mode, originUrl, urls(remaining)));
        failoverRound = round;
        if (InterfaceFailoverPolicy.isConfirm(mode)) {
            App.post(() -> showConfirmDialog(round));
        } else {
            App.post(this::startNextAttempt);
        }
    }

    private void onAttemptFailure(FailoverRound round) {
        if (failoverRound != round) return;
        int mode = Setting.getInterfaceFailoverMode();
        if (!InterfaceFailoverPolicy.shouldFailover(mode) || !round.state.shouldContinueAfterFailure()) {
            finishFailure(round, round.lastError);
            return;
        }
        startNextAttempt();
    }

    private void startNextAttempt() {
        FailoverRound round = failoverRound;
        if (round == null) return;
        if (!InterfaceFailoverPolicy.shouldFailover(Setting.getInterfaceFailoverMode())) {
            finishFailure(round, round.lastError);
            return;
        }
        String nextUrl = round.state.nextAutomatic();
        if (nextUrl == null) {
            finishFailure(round, round.lastError);
            return;
        }
        Config next = findCandidate(round.candidates, nextUrl);
        if (next == null) {
            finishFailure(round, round.lastError);
            return;
        }
        Notify.show(ResUtil.getString(R.string.interface_failover_next, next.getDesc()));
        round.attemptCallback = new Callback() {
            @Override
            public void success() {
                if (round.attemptCallback != this) return;
                finishSuccess(round);
            }

            @Override
            public void error(String msg) {
                if (round.attemptCallback != this) return;
                round.lastError = msg;
                App.post(() -> {
                    if (round.attemptCallback == this) onAttemptFailure(round);
                });
            }
        };
        loadFailoverAttempt(next, round.attemptCallback);
    }

    private void startSelectedAttempt(FailoverRound round, int index) {
        if (failoverRound != round || index < 0 || index >= round.candidates.size()) return;
        if (!InterfaceFailoverPolicy.shouldFailover(Setting.getInterfaceFailoverMode())) {
            finishFailure(round, round.lastError);
            return;
        }
        Config selected = round.candidates.get(index);
        String selectedUrl = round.state.select(index);
        if (selectedUrl == null) {
            finishFailure(round, round.lastError);
            return;
        }
        Notify.show(ResUtil.getString(R.string.interface_failover_next, selected.getDesc()));
        round.attemptCallback = new Callback() {
            @Override
            public void success() {
                if (round.attemptCallback != this) return;
                finishSuccess(round);
            }

            @Override
            public void error(String msg) {
                if (round.attemptCallback != this) return;
                round.lastError = msg;
                App.post(() -> {
                    if (round.attemptCallback == this) onAttemptFailure(round);
                });
            }
        };
        loadFailoverAttempt(selected, round.attemptCallback);
    }

    private void showConfirmDialog(FailoverRound round) {
        if (failoverRound != round) return;
        android.app.Activity activity = App.activity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            finishFailure(round, round.lastError);
            return;
        }
        CharSequence[] labels = new CharSequence[round.candidates.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = round.candidates.get(i).getDesc();
        final int[] selected = {0};
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.interface_failover_title)
                .setMessage(ResUtil.getString(R.string.interface_failover_message, round.originDesc))
                .setSingleChoiceItems(labels, 0, (dialog1, which) -> selected[0] = which)
                .setPositiveButton(R.string.interface_failover_switch, (dialog1, which) -> startSelectedAttempt(round, selected[0]))
                .setNegativeButton(R.string.dialog_cancel, (dialog1, which) -> cancelFailover(round))
                .create();
        failoverDialog = dialog;
        dialog.setOnCancelListener(dialog1 -> cancelFailover(round));
        dialog.setOnDismissListener(dialog1 -> {
            if (failoverDialog == dialog) failoverDialog = null;
        });
        dialog.show();
    }

    private void cancelFailover(FailoverRound round) {
        if (failoverRound != round) return;
        cancelRound(round);
        Notify.show(R.string.interface_failover_cancelled);
    }

    private void stopFailover() {
        FailoverRound round = failoverRound;
        if (round == null) return;
        round.state.cancel();
        boolean loading = round.attemptCallback != null;
        if (loading) cancelLoad(true);
        failoverRound = null;
        AlertDialog dialog = failoverDialog;
        failoverDialog = null;
        if (dialog != null) dialog.dismiss();
        round.callback.error(errorMessage(round.lastError));
    }

    private void cancelRound(FailoverRound round) {
        if (failoverRound != round) return;
        round.state.cancel();
        if (round.attemptCallback != null) cancelLoad(true);
        failoverRound = null;
        AlertDialog dialog = failoverDialog;
        failoverDialog = null;
        if (dialog != null) dialog.dismiss();
        round.callback.error(errorMessage(round.lastError));
    }

    private void finishSuccess(FailoverRound round) {
        if (failoverRound != round) return;
        failoverRound = null;
        super.postEvent();
        ConfigEvent.vod();
        round.callback.success();
    }

    private void finishFailure(FailoverRound round, String message) {
        if (failoverRound != round) return;
        failoverRound = null;
        super.postEvent();
        ConfigEvent.vod();
        round.callback.error(errorMessage(message));
    }

    private String errorMessage(String message) {
        return TextUtils.isEmpty(message) ? Notify.getError(R.string.error_config_get, new Exception("Configuration get failed")) : message;
    }

    private void abandonFailover() {
        if (failoverRound != null) {
            failoverRound.state.cancel();
            cancelLoad(false);
        }
        failoverRound = null;
        AlertDialog dialog = failoverDialog;
        failoverDialog = null;
        if (dialog != null) App.post(dialog::dismiss);
    }

    private int indexOfUrl(List<Config> configs, String url) {
        for (int i = 0; i < configs.size(); i++) if (TextUtils.equals(configs.get(i).getUrl(), url)) return i;
        return -1;
    }

    private List<String> urls(List<Config> configs) {
        List<String> urls = new ArrayList<>();
        for (Config config : configs) urls.add(config.getUrl());
        return urls;
    }

    private Config findCandidate(List<Config> configs, String url) {
        for (Config config : configs) if (TextUtils.equals(config.getUrl(), url)) return config;
        return null;
    }

    private static final class FailoverRound {

        private final String originDesc;
        private final List<Config> candidates;
        private final Callback callback;
        private final InterfaceFailoverState state;
        private String lastError;
        private Callback attemptCallback;

        private FailoverRound(String originDesc, List<Config> candidates, Callback callback, String lastError,
                              InterfaceFailoverState state) {
            this.originDesc = originDesc;
            this.candidates = candidates;
            this.callback = callback;
            this.lastError = lastError;
            this.state = state;
        }
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setHlsRules(HlsAdRule.arrayFrom(fetchArray(object, "hlsRules")));
        setGroupRules(GroupRule.arrayFrom(fetchArray(object, "groupRules")));
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
        String spider = Json.safeString(object, "spider");
        BaseLoader.get().parseJar(spider, true);
        setSites(Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new)));
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

    public List<HlsAdRule> getHlsRules() {
        return hlsRules == null ? Collections.emptyList() : hlsRules;
    }

    private void setHlsRules(List<HlsAdRule> rules) {
        this.hlsRules = rules;
        HlsRuleConfig.invalidate();
    }

    private void setGroupRules(List<GroupRule> rules) {
        GroupRuleConfig.setInterfaceRules(rules);
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
