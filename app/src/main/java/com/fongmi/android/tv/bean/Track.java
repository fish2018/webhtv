package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.db.AppDatabase;

import java.util.Collections;
import java.util.List;

@Entity(indices = @Index(value = {"key", "type"}, unique = true))
public class Track {

    private static final String DISABLED = "__disabled__";

    @PrimaryKey(autoGenerate = true)
    private int id;
    private int type;
    private String key;
    private String name;
    private String format;
    private boolean selected;
    @Ignore
    private String playerId;

    public Track(int type, String name, String format) {
        this.type = type;
        this.name = name;
        this.format = format;
    }

    public static Track disabled(int type, String name) {
        Track track = new Track(type, name, DISABLED);
        track.setSelected(true);
        return track;
    }

    public static List<Track> find(String key) {
        return TextUtils.isEmpty(key) ? Collections.emptyList() : AppDatabase.get().getTrackDao().find(key);
    }

    public static void delete(String key) {
        if (TextUtils.isEmpty(key)) return;
        AppDatabase.get().getTrackDao().delete(key);
    }

    public static void delete(String key, int type) {
        if (TextUtils.isEmpty(key)) return;
        AppDatabase.get().getTrackDao().delete(key, type);
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

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public Track playerId(String playerId) {
        setPlayerId(playerId);
        return this;
    }

    public Track key(String key) {
        setKey(key);
        return this;
    }

    public boolean isDisabled() {
        return DISABLED.equals(getFormat());
    }

    public Track toggle() {
        setSelected(!isSelected());
        return this;
    }

    public Track save() {
        if (TextUtils.isEmpty(getKey())) return this;
        AppDatabase.get().getTrackDao().insert(this);
        return this;
    }
}
