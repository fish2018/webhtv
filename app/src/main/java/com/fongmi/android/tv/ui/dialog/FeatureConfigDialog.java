package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.databinding.DialogFeatureConfigBinding;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class FeatureConfigDialog {

    public static final int TMDB = 1;
    private static final String DEFAULT_API_HOST = "https://api.tmdb.org";
    private static final String DEFAULT_IMAGE_HOST = "https://image.tmdb.org";

    private final FragmentActivity activity;
    private final DialogFeatureConfigBinding binding;
    private AlertDialog dialog;
    private Runnable onDismiss;

    public static FeatureConfigDialog create(FragmentActivity activity) {
        return new FeatureConfigDialog(activity);
    }

    public FeatureConfigDialog(FragmentActivity activity) {
        this.activity = activity;
        this.binding = DialogFeatureConfigBinding.inflate(LayoutInflater.from(activity));
    }

    public FeatureConfigDialog type(int type) {
        return this;
    }

    public FeatureConfigDialog onDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
        return this;
    }

    public void show() {
        initDialog();
        initView();
    }

    private void initDialog() {
        dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.setting_tmdb)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.dialog_positive, this::onPositive)
                .setNegativeButton(R.string.dialog_negative, this::onNegative)
                .create();
        dialog.setOnDismissListener(dialog -> {
            if (onDismiss != null) onDismiss.run();
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setDimAmount(0);
    }

    private void initView() {
        TmdbConfig config = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        binding.baseLayout.setHint(activity.getString(R.string.dialog_tmdb_token));
        binding.extra1Layout.setHint(activity.getString(R.string.dialog_tmdb_lang));
        binding.extra2Layout.setHint(activity.getString(R.string.dialog_tmdb_api_base));
        binding.extra3Layout.setHint(activity.getString(R.string.dialog_tmdb_image_base));
        binding.extra4Layout.setHint(activity.getString(R.string.dialog_tmdb_site_rules));
        binding.base.setText(TextUtils.isEmpty(config.getAccessToken()) ? config.getApiKey() : config.getAccessToken());
        binding.extra1.setText(config.getLanguage());
        binding.extra2.setText(toApiHost(config.getApiBase()));
        binding.extra3.setText(config.getImageHost());
        setSiteRules(config.getEnabledSites(), config.getExcludeKeywords());
        binding.siteManage.setOnClickListener(v -> showSiteManageDialog());
        binding.test.setOnClickListener(v -> testTmdbConfig());
    }

    private void onPositive(DialogInterface dialog, int which) {
        SiteRules rules = parseSiteRules();
        String json = "{"
                + "\"accessToken\":\"" + escape(binding.base.getText()) + "\","
                + "\"apiKey\":\"" + escape(binding.base.getText()) + "\","
                + "\"language\":\"" + escape(binding.extra1.getText()) + "\","
                + "\"apiBase\":\"" + escape(toApiBase(binding.extra2.getText())) + "\","
                + "\"imageBase\":\"" + escape(toImageBase(binding.extra3.getText(), "w342")) + "\","
                + "\"backdropBase\":\"" + escape(toImageBase(binding.extra3.getText(), "w780")) + "\","
                + "\"enabledSites\":" + arrayJson(rules.enabled()) + ","
                + "\"excludeKeywordsConfigured\":true,"
                + "\"excludeKeywords\":" + arrayJson(rules.excluded())
                + "}";
        Setting.putTmdbConfig(TmdbConfig.objectFrom(json).toJson());
        dialog.dismiss();
    }

    private void onNegative(DialogInterface dialog, int which) {
        dialog.dismiss();
    }

    private TmdbConfig currentConfig() {
        SiteRules rules = parseSiteRules();
        String json = "{"
                + "\"accessToken\":\"" + escape(binding.base.getText()) + "\","
                + "\"apiKey\":\"" + escape(binding.base.getText()) + "\","
                + "\"language\":\"" + escape(binding.extra1.getText()) + "\","
                + "\"apiBase\":\"" + escape(toApiBase(binding.extra2.getText())) + "\","
                + "\"imageBase\":\"" + escape(toImageBase(binding.extra3.getText(), "w342")) + "\","
                + "\"backdropBase\":\"" + escape(toImageBase(binding.extra3.getText(), "w780")) + "\","
                + "\"enabledSites\":" + arrayJson(rules.enabled()) + ","
                + "\"excludeKeywordsConfigured\":true,"
                + "\"excludeKeywords\":" + arrayJson(rules.excluded())
                + "}";
        return TmdbConfig.objectFrom(json);
    }

    private void testTmdbConfig() {
        TmdbConfig config = currentConfig();
        if (!config.isReady()) {
            Notify.show(R.string.detail_tmdb_need_key);
            return;
        }
        binding.test.setEnabled(false);
        binding.test.setText(R.string.dialog_tmdb_testing);
        Task.execute(() -> {
            try {
                JsonObject result = new TmdbService().configuration(config);
                String message = buildTestMessage(config, result);
                App.post(() -> showTestResult(true, message));
            } catch (Throwable e) {
                String error = TextUtils.isEmpty(e.getMessage()) ? activity.getString(R.string.dialog_tmdb_test_failed) : e.getMessage();
                App.post(() -> showTestResult(false, error));
            }
        });
    }

    private void showTestResult(boolean success, String message) {
        binding.test.setEnabled(true);
        binding.test.setText(R.string.dialog_tmdb_test);
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(success ? R.string.dialog_tmdb_test_success : R.string.dialog_tmdb_test_failed)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_positive, null)
                .show();
    }

    private String buildTestMessage(TmdbConfig config, JsonObject result) {
        String auth = TextUtils.isEmpty(config.getAccessToken()) ? activity.getString(R.string.dialog_tmdb_auth_key) : activity.getString(R.string.dialog_tmdb_auth_token);
        return activity.getString(R.string.dialog_tmdb_test_result,
                config.getApiBase(),
                auth,
                config.getLanguage(),
                config.getImageHost(),
                posterSizes(result));
    }

    private String posterSizes(JsonObject result) {
        JsonArray sizes = array(result, "images", "poster_sizes");
        List<String> items = new ArrayList<>();
        for (JsonElement element : sizes) {
            if (!element.isJsonPrimitive()) continue;
            String size = element.getAsString();
            if (!TextUtils.isEmpty(size)) items.add(size);
            if (items.size() >= 6) break;
        }
        return items.isEmpty() ? "-" : TextUtils.join(", ", items);
    }

    private JsonArray array(JsonObject object, String parent, String child) {
        if (object == null || !object.has(parent) || !object.get(parent).isJsonObject()) return new JsonArray();
        JsonObject parentObject = object.getAsJsonObject(parent);
        return parentObject.has(child) && parentObject.get(child).isJsonArray() ? parentObject.getAsJsonArray(child) : new JsonArray();
    }

    private String escape(CharSequence value) {
        return String.valueOf(value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(";", values);
    }

    private void setSiteRules(List<String> enabled, List<String> excluded) {
        binding.extra4.setText(activity.getString(R.string.dialog_tmdb_site_rules_value, join(enabled), join(excluded)));
    }

    private String[] lines(CharSequence value) {
        return String.valueOf(value == null ? "" : value).replace("\r", "").split("\n", -1);
    }

    private String toApiHost(String value) {
        String api = trimTrailingSlash(value);
        return api.endsWith("/3") ? api.substring(0, api.length() - 2) : api;
    }

    private String toApiBase(CharSequence value) {
        String api = trimTrailingSlash(value);
        if (TextUtils.isEmpty(api)) api = DEFAULT_API_HOST;
        return api.endsWith("/3") ? api : api + "/3";
    }

    private String toImageBase(CharSequence value, String size) {
        String image = trimTrailingSlash(value);
        if (TextUtils.isEmpty(image)) image = DEFAULT_IMAGE_HOST;
        if (image.endsWith("/w342") || image.endsWith("/w500") || image.endsWith("/w780") || image.endsWith("/original")) {
            image = image.substring(0, image.lastIndexOf('/'));
        }
        if (image.endsWith("/t/p")) return image + "/" + size;
        return image + "/t/p/" + size;
    }

    private String trimTrailingSlash(CharSequence value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return text;
    }

    private String arrayJson(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (String item : values == null ? List.<String>of() : values) {
            String text = item == null ? "" : item.trim();
            if (text.isEmpty()) continue;
            if (!first) builder.append(',');
            builder.append('"').append(escape(text)).append('"');
            first = false;
        }
        return builder.append(']').toString();
    }

    private SiteRules parseSiteRules() {
        String[] lines = lines(binding.extra4.getText());
        String enabled = cleanRuleLine(lines.length > 0 ? lines[0] : "", R.string.dialog_tmdb_site_enabled_prefix, "Enabled:");
        String excluded = cleanRuleLine(lines.length > 1 ? lines[1] : "", R.string.dialog_tmdb_site_exclude_prefix, "Exclude:");
        return new SiteRules(splitRules(enabled), splitRules(excluded));
    }

    private String cleanRuleLine(String value, int prefixRes, String englishPrefix) {
        String text = value == null ? "" : value.trim();
        String localPrefix = activity.getString(prefixRes);
        if (text.startsWith(localPrefix)) text = text.substring(localPrefix.length()).trim();
        if (text.startsWith(englishPrefix)) text = text.substring(englishPrefix.length()).trim();
        return text;
    }

    private List<String> splitRules(String value) {
        List<String> result = new ArrayList<>();
        String[] items = value == null ? new String[0] : value.split("[,，;；\\n]");
        for (String item : items) {
            String text = item == null ? "" : item.trim();
            if (!text.isEmpty() && !result.contains(text)) result.add(text);
        }
        return result;
    }

    private void showSiteManageDialog() {
        List<Site> sites = VodConfig.get().getSites().stream().filter(site -> site != null && !site.isEmpty()).toList();
        if (sites.isEmpty()) return;
        SiteRules rules = parseSiteRules();
        String[] labels = new String[sites.size()];
        boolean[] checked = new boolean[sites.size()];
        for (int i = 0; i < sites.size(); i++) {
            Site site = sites.get(i);
            labels[i] = TextUtils.isEmpty(site.getName()) ? site.getKey() : site.getName() + "  " + site.getKey();
            checked[i] = rules.enabled().isEmpty() || containsRule(rules.enabled(), site);
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dialog_tmdb_site_manage)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    List<String> enabled = new ArrayList<>();
                    for (int i = 0; i < sites.size(); i++) if (checked[i]) enabled.add(sites.get(i).getKey());
                    if (enabled.size() == sites.size()) enabled.clear();
                    setSiteRules(enabled, rules.excluded());
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .setNeutralButton(R.string.dialog_tmdb_site_clear, (dialog, which) -> setSiteRules(List.of(), rules.excluded()))
                .show();
    }

    private boolean containsRule(List<String> rules, Site site) {
        for (String rule : rules) {
            if (TextUtils.isEmpty(rule)) continue;
            if (site.getKey().contains(rule) || site.getName().contains(rule) || rule.contains(site.getKey())) return true;
        }
        return false;
    }

    private record SiteRules(List<String> enabled, List<String> excluded) {
    }
}
