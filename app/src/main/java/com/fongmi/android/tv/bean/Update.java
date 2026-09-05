package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.update.OciArtifact;
import com.fongmi.android.tv.utils.AppVersion;

public class Update {

    public static final String CHANNEL_STABLE = "stable";
    public static final String CHANNEL_BETA = "beta";

    public String channel;
    public String name;
    public String versionName;
    public String desc;
    public String notes;
    public String apk;
    public String apkUrl;
    public String githubUrl;
    public String error;
    public String sha256;
    public OciArtifact oci;
    public int code;
    public long size;

    public static Update empty(String channel) {
        Update update = new Update();
        update.channel = channel;
        return update;
    }

    public boolean isBeta() {
        return CHANNEL_BETA.equals(channel);
    }

    public boolean hasManifest() {
        return !TextUtils.isEmpty(name)
                && !TextUtils.isEmpty(versionName)
                && code > 0
                && (!TextUtils.isEmpty(githubUrl) || oci != null && oci.isValid());
    }

    public boolean hasUpdate() {
        if (!hasManifest()) return false;
        return code != BuildConfig.VERSION_CODE || !AppVersion.isCurrent(name);
    }

    public String getText() {
        if (!TextUtils.isEmpty(notes)) return notes;
        if (!TextUtils.isEmpty(desc)) return desc;
        return "";
    }
}
