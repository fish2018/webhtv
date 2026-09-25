package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.theme.ThemeProfile;
import com.fongmi.android.tv.theme.ThemeProfileCodec;

/** SAF export and system sharing for a validated theme profile. */
public final class ThemeExport {

    private ThemeExport() {
    }

    public static Intent createDocumentIntent(ThemeProfile profile) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, safeName(profile) + ".json");
        return intent;
    }

    public static Intent shareIntent(ThemeProfile profile) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TEXT, ThemeProfileCodec.encode(profile));
        return Intent.createChooser(intent, null);
    }

    public static void write(Activity activity, Uri uri, ThemeProfile profile) throws Exception {
        if (uri == null) throw new IllegalArgumentException("export destination is missing");
        try (java.io.OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("export destination is unavailable");
            output.write(ThemeProfileCodec.encode(profile).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String safeName(ThemeProfile profile) {
        String name = profile == null ? "webhtv-theme" : profile.displayName();
        return name.replaceAll("[^a-zA-Z0-9._-]+", "_").replaceAll("^_+|_+$", "").isBlank()
                ? "webhtv-theme" : name.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }
}
