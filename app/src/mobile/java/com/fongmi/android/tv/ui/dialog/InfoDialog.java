package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogInfoBinding;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;

public class InfoDialog extends BaseAlertDialog {

    private DialogInfoBinding binding;
    private String header;
    private String params;
    private String title;
    private String url;

    public static InfoDialog create() {
        return new InfoDialog();
    }

    public InfoDialog title(CharSequence title) {
        this.title = TextUtils.isEmpty(title) ? "" : title.toString();
        return this;
    }

    public InfoDialog headers(Map<String, String> header) {
        this.header = buildHeader(header);
        return this;
    }

    public InfoDialog params(String params) {
        this.params = TextUtils.isEmpty(params) ? "" : params;
        return this;
    }

    public InfoDialog url(String url) {
        this.url = TextUtils.isEmpty(url) ? "" : url.startsWith("data") ? url.substring(0, Math.min(url.length(), 128)).concat("...") : url;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogInfoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        if (header == null) header = "";
        if (params == null) params = "";
        if (title == null) title = "";
        if (url == null) url = "";
        binding.url.setText(url);
        binding.title.setText(title);
        binding.header.setText(header);
        binding.params.setText(params);
        binding.title.setSingleLine(title.contains(url));
        binding.url.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        binding.header.setVisibility(TextUtils.isEmpty(header) ? View.GONE : View.VISIBLE);
        binding.params.setVisibility(TextUtils.isEmpty(params) ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void initEvent() {
        binding.url.setOnClickListener(this::onShare);
        binding.url.setOnLongClickListener(v -> onCopy(url));
        binding.header.setOnLongClickListener(v -> onCopy(header));
        binding.params.setOnLongClickListener(v -> onCopy(params));
    }

    private void onShare(View view) {
        ((Listener) requireActivity()).onShare(title);
        dismiss();
    }

    private boolean onCopy(String text) {
        Util.copy(text);
        return true;
    }

    private String buildHeader(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String key : headers.keySet()) sb.append(key).append(" : ").append(headers.get(key)).append("\n");
        return Util.substring(sb.toString());
    }

    public interface Listener {

        void onShare(CharSequence title);
    }
}
