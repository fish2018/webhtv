package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.ComicSourceConfig;
import com.fongmi.android.tv.bean.Site;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 漫画源配置对话框（对齐 AudioSourceDialog）。
 * 管理启用规则（关键词 / 站点），命中规则的站点点击卡片直接进漫画阅读器。
 */
public class ComicSourceDialog {

    private final FragmentActivity activity;
    private AlertDialog dialog;
    private ChipGroup enabledChips;
    private Runnable onDismiss;

    private List<String> tempEnabledRules;

    public static ComicSourceDialog create(FragmentActivity activity) {
        return new ComicSourceDialog(activity);
    }

    private ComicSourceDialog(FragmentActivity activity) {
        this.activity = activity;
    }

    public ComicSourceDialog onDismiss(Runnable callback) {
        this.onDismiss = callback;
        return this;
    }

    public void show() {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_comic_source, null);
        enabledChips = view.findViewById(R.id.enabledChips);
        EditText ruleInput = view.findViewById(R.id.ruleInput);
        View addBtn = view.findViewById(R.id.add);
        View manageBtn = view.findViewById(R.id.manage);
        View resetBtn = view.findViewById(R.id.resetDefault);

        ComicSourceConfig config = ComicSourceConfig.get();
        tempEnabledRules = new ArrayList<>(config.isConfigured() ? config.getEnabledSites() : ComicSourceConfig.defaultRules());
        updateChipsDisplay();

        addBtn.setOnClickListener(v -> addRule(ruleInput));
        ruleInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addRule(ruleInput);
                return true;
            }
            return false;
        });
        manageBtn.setOnClickListener(v -> showSiteManage());
        resetBtn.setOnClickListener(v -> resetToDefault());

        dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.setting_comic_source)
                .setView(view)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> onSave())
                .setNegativeButton(R.string.dialog_negative, null)
                .setOnDismissListener(d -> { if (onDismiss != null) onDismiss.run(); })
                .create();
        dialog.show();
    }

    private void onSave() {
        ComicSourceConfig config = ComicSourceConfig.get();
        config.getEnabledSites().clear();
        config.getEnabledSites().addAll(tempEnabledRules);
        config.save();
    }

    private void showSiteManage() {
        List<Site> sites = new ArrayList<>();
        for (Site s : VodConfig.get().getSites()) if (s != null && !s.isEmpty()) sites.add(s);
        if (sites.isEmpty()) return;

        List<String> enabledRules = new ArrayList<>(tempEnabledRules);

        String[] labels = new String[sites.size()];
        boolean[] checked = new boolean[sites.size()];

        for (int i = 0; i < sites.size(); i++) {
            Site site = sites.get(i);
            labels[i] = site.getDisplayName() + "  " + site.getKey();
            checked[i] = matchesRule(enabledRules, site);
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dialog_comic_site_manage)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> applySiteManage(sites, enabledRules, checked))
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void applySiteManage(List<Site> sites, List<String> enabledRules, boolean[] checked) {
        List<String> newEnabled = new ArrayList<>();
        for (String rule : enabledRules) {
            if (ComicSourceConfig.findSite(rule) == null) newEnabled.add(rule);
        }

        for (int i = 0; i < sites.size(); i++) {
            if (!checked[i]) continue;
            Site site = sites.get(i);
            String key = site.getKey();
            boolean matchedByKeyword = false;
            for (String rule : enabledRules) {
                if (ComicSourceConfig.findSite(rule) == null && matchesRule(List.of(rule), site)) {
                    matchedByKeyword = true;
                    break;
                }
            }
            if (!matchedByKeyword && !newEnabled.contains(key)) {
                newEnabled.add(key);
            }
        }

        tempEnabledRules = newEnabled;
        updateChipsDisplay();
    }

    private boolean matchesRule(List<String> rules, Site site) {
        String key = site.getKey() == null ? "" : site.getKey().toLowerCase(Locale.ROOT);
        String name = site.getName() == null ? "" : site.getName().toLowerCase(Locale.ROOT);
        for (String rule : rules) {
            if (TextUtils.isEmpty(rule)) continue;
            String r = rule.trim().toLowerCase(Locale.ROOT);
            if (key.equals(r) || name.equals(r)) return true;
            if (key.contains(r) || name.contains(r)) return true;
        }
        return false;
    }

    private void updateChipsDisplay() {
        enabledChips.removeAllViews();
        for (String rule : tempEnabledRules) {
            if (TextUtils.isEmpty(rule)) continue;
            Chip chip = new Chip(activity);
            Site site = ComicSourceConfig.findSite(rule.trim());
            chip.setText(site != null ? site.getDisplayName() : rule.trim());
            chip.setCloseIconVisible(true);
            chip.setCheckable(false);
            chip.setOnCloseIconClickListener(v -> removeEnabledRule(rule));
            enabledChips.addView(chip);
        }
    }

    private void removeEnabledRule(String rule) {
        Site site = ComicSourceConfig.findSite(rule);
        if (site != null) {
            tempEnabledRules.remove(site.getKey());
            tempEnabledRules.remove(site.getDisplayName());
        } else {
            tempEnabledRules.remove(rule);
        }
        updateChipsDisplay();
    }

    private void resetToDefault() {
        tempEnabledRules.clear();
        tempEnabledRules.addAll(ComicSourceConfig.defaultRules());
        updateChipsDisplay();
    }

    private void addRule(EditText input) {
        String rule = input.getText().toString().trim();
        if (TextUtils.isEmpty(rule)) return;
        Site site = ComicSourceConfig.findSite(rule);
        String toAdd = site != null ? site.getKey() : rule;
        if (tempEnabledRules.contains(toAdd)) {
            input.setText("");
            return;
        }
        tempEnabledRules.add(toAdd);
        input.setText("");
        updateChipsDisplay();
    }
}
