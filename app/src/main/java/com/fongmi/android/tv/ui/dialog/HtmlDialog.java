package com.fongmi.android.tv.ui.dialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class HtmlDialog {

    @SuppressLint("SetJavaScriptEnabled")
    public static void show(Activity activity, String title, String html) {
        WebView webView = new WebView(activity);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(webView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }
}
