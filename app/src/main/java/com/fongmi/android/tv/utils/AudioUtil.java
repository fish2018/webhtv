package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.setting.Setting;

import java.util.Locale;

public class AudioUtil {

    private static final String[] EXTENSIONS = {".mp3", ".flac", ".m4a", ".aac", ".wav", ".ogg", ".oga", ".opus", ".ape", ".wma", ".amr", ".mka"};

    public static boolean isAudio(Result result) {
        if (result == null || result.getUrl().isEmpty()) return false;
        String format = result.getFormat();
        if (!TextUtils.isEmpty(format) && format.toLowerCase(Locale.ROOT).contains("audio")) return true;
        return isAudioUrl(result.getUrl().v()) || isAudioUrl(result.getRealUrl());
    }

    public static boolean isAudioSiteEnabled(String key) {
        if (TextUtils.isEmpty(key) || SiteApi.PUSH.equals(key)) return true;
        Site site = VodConfig.get().getSite(key);
        return Setting.isAudioSiteEnabled(site.getKey(), site.getName());
    }

    public static boolean isAudioUrl(String url) {
        String text = normalize(url);
        if (text.startsWith("mp3://") || text.startsWith("music://")) return true;
        int query = text.indexOf('?');
        if (query != -1) text = text.substring(0, query);
        int fragment = text.indexOf('#');
        if (fragment != -1) text = text.substring(0, fragment);
        for (String extension : EXTENSIONS) if (text.endsWith(extension)) return true;
        return false;
    }

    public static String cleanUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("mp3://")) return url.substring(6);
        if (url.startsWith("music://")) return url.substring(8);
        if (url.startsWith("file://") && !url.startsWith("file:///")) return "file:///" + url.substring(7);
        return url;
    }

    private static String normalize(String url) {
        return TextUtils.isEmpty(url) ? "" : url.trim().toLowerCase(Locale.ROOT);
    }
}
