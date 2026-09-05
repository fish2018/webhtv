package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.R;

public final class TmdbDetailLabels {

    private TmdbDetailLabels() {
    }

    public static String certificationLabel(String value) {
        if (value == null) return "";
        String label = value.trim();
        return label.matches("\\d+") ? label + "+" : label;
    }

    public static String headerSubtitle(String releaseDate) {
        return releaseDate == null ? "" : releaseDate.trim();
    }

    public static int keepLabel(boolean kept) {
        return kept ? R.string.keep_add : R.string.keep;
    }
}
