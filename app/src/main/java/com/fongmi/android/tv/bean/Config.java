package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Prefers;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity(indices = {@Index(value = {"url", "type"}), @Index(value = {"interfaceKey", "type"}, unique = true)})
public class Config {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    private int id;
    @SerializedName("type")
    private int type;
    @SerializedName("time")
    private long time;
    @SerializedName("url")
    private String url;
    @SerializedName("interfaceKey")
    private String interfaceKey;
    @SerializedName("urlsJson")
    private String urlsJson;
    @SerializedName("legacyConfigKeysJson")
    private String legacyConfigKeysJson;
    @SerializedName("addressMatchAliasesJson")
    private String addressMatchAliasesJson;
    @SerializedName("identityResolutionState")
    private String identityResolutionState;
    @SerializedName("json")
    private String json;
    @SerializedName("name")
    private String name;
    @SerializedName("logo")
    private String logo;
    @SerializedName("home")
    private String home;
    @SerializedName("parse")
    private String parse;

    @Ignore
    @SerializedName("notice")
    private String notice;
    @Ignore
    @SerializedName("danmaku")
    private String danmaku;

    public static List<Config> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Config.class).getType();
        List<Config> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Config objectFrom(String str) {
        return App.gson().fromJson(str, Config.class);
    }

    public static Config create(int type) {
        return new Config().type(type);
    }

    public static Config create(int type, String url) {
        return new Config().type(type).url(url).insert();
    }

    public static Config create(int type, String url, String name) {
        return new Config().type(type).url(url).name(name).insert();
    }

    public static List<Config> getAll(int type) {
        return AppDatabase.get().getConfigDao().findByType(type);
    }

    public static List<Config> findUrls() {
        return AppDatabase.get().getConfigDao().findUrlByType(0);
    }

    public static void delete(String url) {
        AppDatabase.get().getConfigDao().delete(url);
    }

    public static void delete(String url, int type) {
        AppDatabase.get().getConfigDao().delete(url, type);
    }

    public static Config vod() {
        Config item = AppDatabase.get().getConfigDao().findOne(0);
        return item == null ? create(0) : item;
    }

    public static Config live() {
        Config item = AppDatabase.get().getConfigDao().findOne(1);
        return item == null ? create(1) : item;
    }

    public static Config wall() {
        Config item = AppDatabase.get().getConfigDao().findOne(2);
        return item == null ? create(2) : item;
    }

    public static Config find(int id) {
        return AppDatabase.get().getConfigDao().findById(id);
    }

    public static Config find(String url, int type) {
        Config item = AppDatabase.get().getConfigDao().find(url, type);
        return item == null ? create(type, url) : item.type(type);
    }

    public static Config find(String url, String name, int type) {
        Config item = AppDatabase.get().getConfigDao().find(url, type);
        return item == null ? create(type, url, name) : item.type(type).name(name);
    }

    public static Config find(Config config) {
        return find(config, config.getType());
    }

    public static Config find(Config config, int type) {
        Config item = AppDatabase.get().getConfigDao().find(config.getUrl(), type);
        return item == null ? create(type, config.getUrl(), config.getName()) : item.type(type).name(config.getName());
    }

    public static Config find(Depot depot, int type) {
        Config item = AppDatabase.get().getConfigDao().find(depot.getUrl(), type);
        return item == null ? create(type, depot.getUrl(), depot.getName()) : item.type(type).name(depot.getName());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getUrls() {
        List<String> urls;
        try {
            Type listType = TypeToken.getParameterized(List.class, String.class).getType();
            urls = App.gson().fromJson(urlsJson, listType);
        } catch (Exception e) {
            urls = null;
        }
        if (urls == null) urls = new ArrayList<>();
        List<String> result = new ArrayList<>();
        addUrl(result, url);
        for (String item : urls) addUrl(result, item);
        return result;
    }

    public Config urls(List<String> urls) {
        rememberAddressAliases(getUrls());
        List<String> result = new ArrayList<>();
        if (urls != null) for (String item : urls) addUrl(result, item);
        setUrl(result.isEmpty() ? "" : result.get(0));
        setUrlsJson(App.gson().toJson(result));
        return this;
    }

    public Config mergeUrls(List<String> urls) {
        List<String> result = getUrls();
        if (urls != null) for (String item : urls) addUrl(result, item);
        return urls(result);
    }

    public Config replaceUrl(String oldUrl, String newUrl) {
        List<String> result = getUrls();
        result.remove(oldUrl);
        result.remove(newUrl);
        if (!TextUtils.isEmpty(newUrl)) result.add(0, newUrl.trim());
        return urls(result);
    }

    private static void addUrl(List<String> urls, String value) {
        value = value == null ? "" : value.trim();
        if (!TextUtils.isEmpty(value) && !urls.contains(value)) urls.add(value);
    }

    public String getInterfaceKey() {
        return interfaceKey;
    }

    public void setInterfaceKey(String interfaceKey) {
        this.interfaceKey = interfaceKey;
    }

    public String getUrlsJson() {
        return urlsJson;
    }

    public void setUrlsJson(String urlsJson) {
        this.urlsJson = urlsJson;
    }

    public String getLegacyConfigKeysJson() {
        return legacyConfigKeysJson;
    }

    public List<String> getLegacyConfigKeys() {
        return readStringList(legacyConfigKeysJson);
    }

    public void setLegacyConfigKeysJson(String value) {
        legacyConfigKeysJson = value;
    }

    public Config addLegacyConfigKey(String value) {
        List<String> values = getLegacyConfigKeys();
        addUnique(values, value);
        legacyConfigKeysJson = App.gson().toJson(values);
        return this;
    }

    public Config addLegacyConfigKeys(List<String> values) {
        List<String> current = getLegacyConfigKeys();
        if (values != null) for (String value : values) addUnique(current, value);
        legacyConfigKeysJson = App.gson().toJson(current);
        return this;
    }

    public String getAddressMatchAliasesJson() {
        return addressMatchAliasesJson;
    }

    public List<String> getAddressMatchAliases() {
        return readStringList(addressMatchAliasesJson);
    }

    public void setAddressMatchAliasesJson(String value) {
        addressMatchAliasesJson = value;
    }

    public Config addAddressMatchAliases(List<String> values) {
        List<String> aliases = getAddressMatchAliases();
        if (values != null) for (String value : values) addUnique(aliases, value);
        addressMatchAliasesJson = App.gson().toJson(aliases);
        return this;
    }

    public String getIdentityResolutionState() {
        return TextUtils.isEmpty(identityResolutionState) ? "unresolved" : identityResolutionState;
    }

    public void setIdentityResolutionState(String value) {
        identityResolutionState = value;
    }

    public Config identityResolutionState(String value) {
        setIdentityResolutionState(value);
        return this;
    }

    private void rememberAddressAliases(List<String> values) {
        if (values == null || values.isEmpty()) return;
        List<String> aliases = new ArrayList<>();
        aliases.addAll(com.fongmi.android.tv.playback.PlaybackConfigIdentity.strictAddressKeys(getType(), values));
        aliases.addAll(com.fongmi.android.tv.playback.PlaybackConfigIdentity.endpointMatchKeys(getType(), values));
        aliases.addAll(com.fongmi.android.tv.playback.PlaybackConfigIdentity.hostMatchKeys(getType(), values));
        addAddressMatchAliases(aliases);
        for (String value : values) addLegacyConfigKey(com.fongmi.android.tv.playback.PlaybackConfigIdentity.keyForUrl(value));
    }

    private List<String> readStringList(String json) {
        try {
            Type listType = TypeToken.getParameterized(List.class, String.class).getType();
            List<String> values = App.gson().fromJson(json, listType);
            return values == null ? new ArrayList<>() : new ArrayList<>(values);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static void addUnique(List<String> values, String value) {
        value = value == null ? "" : value.trim();
        if (!TextUtils.isEmpty(value) && !values.contains(value)) values.add(value);
    }

    public String ensureInterfaceKey() {
        if (TextUtils.isEmpty(interfaceKey)) interfaceKey = UUID.randomUUID().toString();
        return interfaceKey;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getParse() {
        return parse;
    }

    public void setParse(String parse) {
        this.parse = parse;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getNotice() {
        return notice;
    }

    public void setNotice(String notice) {
        this.notice = notice;
    }

    public String getDanmaku() {
        return danmaku;
    }

    public void setDanmaku(String danmaku) {
        this.danmaku = danmaku;
    }

    public Config type(int type) {
        setType(type);
        return this;
    }

    public Config url(String url) {
        String oldUrl = getUrl();
        if (!TextUtils.isEmpty(oldUrl) && !TextUtils.equals(oldUrl, url)) return replaceUrl(oldUrl, url);
        setUrl(url);
        if (!TextUtils.isEmpty(url) && TextUtils.isEmpty(urlsJson)) urlsJson = App.gson().toJson(Collections.singletonList(url));
        return this;
    }

    public Config interfaceKey(String interfaceKey) {
        if (!TextUtils.isEmpty(interfaceKey)) setInterfaceKey(interfaceKey.trim());
        return this;
    }

    public Config urlsJson(String urlsJson) {
        setUrlsJson(urlsJson);
        return this;
    }

    public Config json(String json) {
        setJson(json);
        return this;
    }

    public Config name(String name) {
        setName(name);
        return this;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public String getDesc() {
        if (!TextUtils.isEmpty(getName())) return getName();
        if (!TextUtils.isEmpty(getUrl())) return getUrl();
        return "";
    }

    public Config insert() {
        if (isEmpty()) return this;
        ensureInterfaceKey();
        rememberAddressAliases(getUrls());
        if (TextUtils.isEmpty(identityResolutionState)) identityResolutionState = "unresolved";
        setId(Math.toIntExact(AppDatabase.get().getConfigDao().insert(this)));
        return this;
    }

    public Config save() {
        if (isEmpty()) return this;
        ensureInterfaceKey();
        rememberAddressAliases(getUrls());
        if (TextUtils.isEmpty(identityResolutionState)) identityResolutionState = "unresolved";
        AppDatabase.get().getConfigDao().insertOrUpdate(this);
        return this;
    }

    public Config update() {
        if (isEmpty()) return this;
        setTime(System.currentTimeMillis());
        Prefers.put("config_" + getType(), getUrl());
        return save();
    }

    public void delete() {
        AppDatabase.get().getConfigDao().delete(getUrl(), getType());
        History.delete(getId());
        Keep.delete(getId());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Config it)) return false;
        return getId() == it.getId();
    }
}
