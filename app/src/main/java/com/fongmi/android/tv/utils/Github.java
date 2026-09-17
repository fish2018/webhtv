package com.fongmi.android.tv.utils;

public class Github {

    private static final String GITHUB_LATEST = "https://github.com/YaChengMu/webhtv/releases/latest/download";
    private static final String GITHUB_RELEASE = "https://github.com/YaChengMu/webhtv/releases/download";
    private static final String GITHUB_UPDATE_CHANNEL = GITHUB_RELEASE + "/update-channel";
    private static final String CNB_MANIFEST = "https://cnb.cool/fish2035/webhtv-release/-/git/raw/main/apk";
    private static final String GITHUB_API = "https://api.github.com/repos/YaChengMu/webhtv/releases/tags";
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/YaChengMu/webhtv/releases";
    private static final String GITHUB_RELEASE_ASSETS_API = "https://api.github.com/repos/YaChengMu/webhtv/releases/assets";

    public static String getChannelAsset(String name) {
        return GITHUB_UPDATE_CHANNEL + "/" + name;
    }

    public static String getCnbMirrorAsset(String name) {
        return CNB_MANIFEST + "/" + name;
    }

    public static String getCnbAsset(String name) {
        return getGithubLatestAsset(name);
    }

    public static String getGithubLatestAsset(String name) {
        return GITHUB_LATEST + "/" + name;
    }

    public static String getGithubReleaseAsset(String tag, String name) {
        return GITHUB_RELEASE + "/" + tag + "/" + name;
    }

    public static String getJson(String name) {
        return getCnbAsset(name + ".json");
    }

    public static String getJson(String name, String channel) {
        if ("beta".equals(channel)) return getCnbAsset(name + "-beta.json");
        return getJson(name);
    }

    public static String getApk(String name) {
        return getCnbAsset(name + ".apk");
    }

    public static String getApk(String name, String channel) {
        if ("beta".equals(channel)) return getCnbAsset(name + "-beta.apk");
        return getApk(name);
    }

    public static String getAsset(String name, String channel) {
        return getCnbAsset(name);
    }

    public static String getReleaseApi(String tag) {
        return GITHUB_API + "/" + tag;
    }

    public static String getReleasesApi() {
        return GITHUB_RELEASES_API + "?per_page=20";
    }

    public static String getLatestReleaseApi() {
        return GITHUB_RELEASES_API + "/latest";
    }

    public static String getReleaseAssetApi(long id) {
        return GITHUB_RELEASE_ASSETS_API + "/" + id;
    }
}
