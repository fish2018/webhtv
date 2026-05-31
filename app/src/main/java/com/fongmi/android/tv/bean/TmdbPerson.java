package com.fongmi.android.tv.bean;

import android.text.TextUtils;

public class TmdbPerson {

    private final int personId;
    private final String name;
    private final String subtitle;
    private final String profileUrl;
    private final String knownForDepartment;
    private final String biography;

    public TmdbPerson(int personId, String name, String subtitle, String profileUrl, String knownForDepartment, String biography) {
        this.personId = personId;
        this.name = name;
        this.subtitle = subtitle;
        this.profileUrl = profileUrl;
        this.knownForDepartment = knownForDepartment;
        this.biography = biography;
    }

    public int getPersonId() {
        return personId;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public String getSubtitle() {
        return TextUtils.isEmpty(subtitle) ? "" : subtitle;
    }

    public String getProfileUrl() {
        return TextUtils.isEmpty(profileUrl) ? "" : profileUrl;
    }

    public String getKnownForDepartment() {
        return TextUtils.isEmpty(knownForDepartment) ? "" : knownForDepartment;
    }

    public String getBiography() {
        return TextUtils.isEmpty(biography) ? "" : biography.trim();
    }
}
