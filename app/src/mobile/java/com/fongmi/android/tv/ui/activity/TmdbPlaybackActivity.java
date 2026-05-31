package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TmdbPlaybackActivity extends VideoActivity implements TmdbPlaybackEnhancer.Host {

    private TmdbPlaybackEnhancer tmdbEnhancer;

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, ArrayList<String> episodeTitles, TmdbItem item, Vod tmdbVod) {
        Intent intent = new Intent(activity, TmdbPlaybackActivity.class);
        intent.putExtra("fusion", false);
        intent.putExtra("collect", false);
        intent.putExtra("cast", false);
        intent.putExtra("mark", mark);
        intent.putStringArrayListExtra("tmdb_episode_titles", episodeTitles);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        putTmdbExtras(intent, item);
        putTmdbVod(intent, tmdbVod);
        activity.startActivity(intent);
    }

    private static void putTmdbExtras(Intent intent, TmdbItem item) {
        if (item == null) return;
        intent.putExtra("tmdb_title", item.getTitle());
        intent.putExtra("tmdb_subtitle", item.getSubtitle());
        intent.putExtra("tmdb_overview", item.getOverview());
        intent.putExtra("tmdb_poster", item.getPosterUrl());
        intent.putExtra("tmdb_backdrop", item.getBackdropUrl());
    }

    private static void putTmdbVod(Intent intent, Vod vod) {
        if (vod == null) return;
        intent.putExtra("tmdb_vod_title", vod.getName());
        intent.putExtra("tmdb_vod_content", vod.getContent());
        intent.putExtra("tmdb_vod_pic", vod.getPic());
        intent.putExtra("tmdb_vod_year", vod.getYear());
        intent.putExtra("tmdb_vod_area", vod.getArea());
        intent.putExtra("tmdb_vod_type", vod.getTypeName());
        intent.putExtra("tmdb_vod_director", vod.getDirector());
        intent.putExtra("tmdb_vod_remark", vod.getRemarks());
    }

    @Override
    protected void initView(android.os.Bundle savedInstanceState) {
        tmdbEnhancer = new TmdbPlaybackEnhancer(this);
        super.initView(savedInstanceState);
    }

    @Override
    protected void onDetailReady(Vod item) {
        Vod merged = mergeIntentTmdbVod(item);
        applyTmdbEpisodeTitles(merged);
        if (merged != item) updateVod(merged);
        if (!hasIntentTmdbVod()) tmdbEnhancer.onDetailReady(item);
    }

    @Override
    public String getKey() {
        return super.getKey();
    }

    @Override
    public String getId() {
        return super.getId();
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public void applyTmdbVod(Vod vod) {
        applyTmdbEpisodeTitles(vod);
        updateVod(vod);
    }

    @Override
    public void applyTmdbArtwork(String title, String subtitle, String overview, String poster, String backdrop) {
        // Keep the original playback page chrome; TMDB data is merged into the normal fields.
    }

    @Override
    protected String getHistoryEpisodeName(Episode item) {
        if (item.getNumber() <= 0) return super.getHistoryEpisodeName(item);
        String title = getEpisodeTitles().get(item.getNumber());
        if (TextUtils.isEmpty(title)) return String.valueOf(item.getNumber());
        return item.getNumber() + ". " + title;
    }

    private Vod mergeIntentTmdbVod(Vod source) {
        if (!hasIntentTmdbVod()) return source;
        Vod vod = new Vod();
        vod.setId(source.getId());
        vod.setName(coalesce(getStringExtra("tmdb_vod_title"), getStringExtra("tmdb_title"), source.getName()));
        vod.setPic(coalesce(getStringExtra("tmdb_vod_pic"), getStringExtra("tmdb_backdrop"), getStringExtra("tmdb_poster"), source.getPic()));
        vod.setContent(coalesce(getStringExtra("tmdb_vod_content"), getStringExtra("tmdb_overview"), source.getContent()));
        vod.setYear(coalesce(getStringExtra("tmdb_vod_year"), source.getYear()));
        vod.setArea(coalesce(getStringExtra("tmdb_vod_area"), source.getArea()));
        vod.setTypeName(coalesce(getStringExtra("tmdb_vod_type"), source.getTypeName()));
        vod.setDirector(coalesce(getStringExtra("tmdb_vod_director"), source.getDirector()));
        vod.setRemarks(coalesce(getStringExtra("tmdb_vod_remark"), source.getRemarks()));
        vod.setPlayFrom(source.getPlayFrom());
        vod.setPlayUrl(source.getPlayUrl());
        vod.setFlags(source.getFlags());
        vod.setSite(source.getSite());
        return vod;
    }

    private void applyTmdbEpisodeTitles(Vod vod) {
        Map<Integer, String> titles = getEpisodeTitles();
        if (vod == null || titles.isEmpty() || vod.getFlags() == null) return;
        for (Flag flag : vod.getFlags()) {
            for (Episode episode : flag.getEpisodes()) {
                String title = titles.get(episode.getNumber());
                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(episode.getName()) || episode.getName().contains(title)) continue;
                episode.setDisplayName(episode.getNumber() + ". " + title);
            }
        }
    }

    private Map<Integer, String> getEpisodeTitles() {
        Map<Integer, String> titles = new HashMap<>();
        ArrayList<String> values = getIntent().getStringArrayListExtra("tmdb_episode_titles");
        if (values == null) return titles;
        for (String value : values) {
            String[] parts = value.split("\t", 2);
            if (parts.length != 2 || TextUtils.isEmpty(parts[1])) continue;
            try {
                titles.put(Integer.parseInt(parts[0]), parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return titles;
    }

    private boolean hasIntentTmdbVod() {
        return !TextUtils.isEmpty(getStringExtra("tmdb_vod_title")) || !TextUtils.isEmpty(getStringExtra("tmdb_vod_content")) || !TextUtils.isEmpty(getStringExtra("tmdb_vod_year")) || !TextUtils.isEmpty(getStringExtra("tmdb_vod_area")) || !TextUtils.isEmpty(getStringExtra("tmdb_vod_type")) || !TextUtils.isEmpty(getStringExtra("tmdb_title")) || !TextUtils.isEmpty(getStringExtra("tmdb_overview"));
    }

    private String getStringExtra(String key) {
        String value = getIntent().getStringExtra(key);
        return value == null ? "" : value;
    }

    private String coalesce(String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }
}
