package com.fongmi.android.tv.player.engine;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.common.net.HttpHeaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaySpec {

    private Map<String, String> headers;
    private List<Danmaku> danmakus;
    private MediaMetadata metadata;
    private List<Sub> subs;
    private Result parseResult;
    private String format;
    private String key;
    private PlaybackRoute.Resolution playbackRoute = PlaybackRoute.resolve(null);
    private String playbackTraceId = PlaybackTrace.NONE;
    private String url;
    private Drm drm;
    private boolean parseSource;
    private boolean parseUseParse;

    public static PlaySpec from(String key, String url, Map<String, String> headers, MediaMetadata metadata) {
        return new PlaySpec(key, url, headers, null, null, null, null, metadata);
    }

    public static PlaySpec from(Result result, String key, MediaMetadata metadata) {
        return new PlaySpec(key, result.getRealUrl(), result.getHeader(), result.getFormat(), result.getDrm(), result.getSubs(), result.getDanmaku(), metadata).setSource(result, false, false);
    }

    public static PlaySpec fromParse(Result result, String key, MediaMetadata metadata) {
        return fromParse(result, key, metadata, false);
    }

    public static PlaySpec fromParse(Result result, String key, MediaMetadata metadata, boolean useParse) {
        return new PlaySpec(key, null, null, result.getFormat(), result.getDrm(), result.getSubs(), result.getDanmaku(), metadata).setSource(result, true, useParse);
    }

    private PlaySpec(String key, String url, Map<String, String> headers, String format, Drm drm, List<Sub> subs, List<Danmaku> danmakus, MediaMetadata metadata) {
        this.key = key;
        setUrl(url);
        this.drm = drm;
        this.subs = subs;
        this.format = format;
        this.headers = headers;
        this.danmakus = danmakus;
        this.metadata = metadata;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
        this.playbackRoute = PlaybackRoute.resolve(url);
    }

    public Uri getUri() {
        return UrlUtil.uri(url);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public PlaySpec copyWithFormat(String format) {
        PlaySpec copy = new PlaySpec(key, url, headers, format, drm, subs, danmakus, metadata).setSource(parseResult, parseSource, parseUseParse);
        copy.playbackRoute = playbackRoute;
        copy.playbackTraceId = playbackTraceId;
        return copy;
    }

    public PlaySpec copyForPlayback(String playbackUrl, Map<String, String> playbackHeaders) {
        PlaySpec copy = new PlaySpec(key, playbackUrl, playbackHeaders, format, drm, subs, danmakus, metadata)
                .setSource(parseResult, parseSource, parseUseParse);
        copy.playbackTraceId = playbackTraceId;
        return copy;
    }

    public PlaybackRoute.Resolution getPlaybackRoute() {
        return playbackRoute;
    }

    public void refreshPlaybackRoute() {
        playbackRoute = PlaybackRoute.resolve(url);
    }

    public String getPlaybackTraceId() {
        return PlaybackTrace.normalize(playbackTraceId);
    }

    public void setPlaybackTraceId(String playbackTraceId) {
        this.playbackTraceId = PlaybackTrace.normalize(playbackTraceId);
    }

    public Drm getDrm() {
        return drm;
    }

    public List<Sub> getSubs() {
        return subs;
    }

    public List<Danmaku> getDanmakus() {
        return danmakus;
    }

    public MediaMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(MediaMetadata metadata) {
        this.metadata = metadata;
    }

    public Result getParseResult() {
        return parseResult;
    }

    public boolean isParseUseParse() {
        return parseUseParse;
    }

    public boolean isParseSource() {
        return parseSource;
    }

    public boolean canReparse() {
        return parseResult != null;
    }

    private PlaySpec setSource(Result result, boolean parseSource, boolean useParse) {
        this.parseResult = result;
        this.parseSource = parseSource;
        this.parseUseParse = useParse;
        return this;
    }

    public PlaySpec checkUa() {
        if (headers == null) headers = new HashMap<>();
        if (headers.keySet().stream().noneMatch(HttpHeaders.USER_AGENT::equalsIgnoreCase)) headers.put(HttpHeaders.USER_AGENT, PlayerHelper.getUa());
        return this;
    }

    public void setSub(Sub sub) {
        if (subs == null) subs = new ArrayList<>();
        if (sub == null) return;
        subs.remove(sub);
        subs.forEach(item -> item.setFlag(nonDefaultSelectionFlag(item.getRawFlag())));
        sub.setFlag(defaultSelectionFlag(sub.getRawFlag()));
        subs.add(0, sub);
    }

    private int defaultSelectionFlag(int flag) {
        return flag == 0 ? C.SELECTION_FLAG_DEFAULT : flag | C.SELECTION_FLAG_DEFAULT;
    }

    private int nonDefaultSelectionFlag(int flag) {
        int result = (flag == 0 ? C.SELECTION_FLAG_AUTOSELECT : flag) & ~C.SELECTION_FLAG_DEFAULT;
        return result == 0 ? C.SELECTION_FLAG_AUTOSELECT : result;
    }

    public void setDanmaku(Danmaku item) {
        if (danmakus == null) danmakus = new ArrayList<>();
        if (!item.isEmpty() && !danmakus.contains(item)) danmakus.add(0, item);
        danmakus.forEach(danmaku -> danmaku.setSelected(danmaku.getUrl().equals(item.getUrl())));
    }

    public void addDanmaku(Danmaku item) {
        if (danmakus == null) danmakus = new ArrayList<>();
        if (!item.isEmpty() && !danmakus.contains(item)) danmakus.add(item);
    }
}
