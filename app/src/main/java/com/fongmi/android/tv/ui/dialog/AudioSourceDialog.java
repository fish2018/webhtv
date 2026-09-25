package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.AudioConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AudioSourceDialog {

    private final FragmentActivity activity;
    private AlertDialog dialog;
    private ChipGroup enabledChips;
    private Runnable onDismiss;

    // 暂存数据，点"确定"才保存
    private List<String> tempEnabledRules;
    private int tempPlaybackStyle;

    public static AudioSourceDialog create(FragmentActivity activity) {
        return new AudioSourceDialog(activity);
    }

    private AudioSourceDialog(FragmentActivity activity) {
        this.activity = activity;
    }

    public AudioSourceDialog onDismiss(Runnable callback) {
        this.onDismiss = callback;
        return this;
    }

    public void show() {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_audio_source, null);
        enabledChips = view.findViewById(R.id.enabledChips);
        RadioGroup playbackStyleGroup = view.findViewById(R.id.audioPlaybackStyle);
        EditText ruleInput = view.findViewById(R.id.ruleInput);
        View addBtn = view.findViewById(R.id.add);
        View manageBtn = view.findViewById(R.id.manage);
        View resetBtn = view.findViewById(R.id.resetDefault);

        // 初始化暂存数据
        AudioConfig config = AudioConfig.objectFrom(Setting.getAudioConfig());
        tempEnabledRules = new ArrayList<>(config.isConfigured() ? config.getEnabledSites() : defaultRules());
        playbackStyleGroup.check(PlayerSetting.getAudioPlaybackStyle() == PlayerSetting.AUDIO_PLAYBACK_STYLE_BUILT_IN ? R.id.audioPlaybackStyleBuiltIn : R.id.audioPlaybackStyleImmersive);
        tempPlaybackStyle = playbackStyleGroup.getCheckedRadioButtonId() == R.id.audioPlaybackStyleBuiltIn ? PlayerSetting.AUDIO_PLAYBACK_STYLE_BUILT_IN : PlayerSetting.AUDIO_PLAYBACK_STYLE_IMMERSIVE;
        updateChipsDisplay();

        playbackStyleGroup.setOnCheckedChangeListener((group, checkedId) -> tempPlaybackStyle = checkedId == R.id.audioPlaybackStyleBuiltIn ? PlayerSetting.AUDIO_PLAYBACK_STYLE_BUILT_IN : PlayerSetting.AUDIO_PLAYBACK_STYLE_IMMERSIVE);
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
                .setTitle(R.string.setting_audio_source)
                .setView(view)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> onSave())
                .setNegativeButton(R.string.dialog_negative, null)
                .setOnDismissListener(d -> { if (onDismiss != null) onDismiss.run(); })
                .create();
        dialog.show();
    }

    private void onSave() {
        PlayerSetting.putAudioPlaybackStyle(tempPlaybackStyle);
        String json = "{\"configured\":true,\"enabledSites\":" + toJsonArray(tempEnabledRules) + "}";
        Setting.putAudioConfig(AudioConfig.objectFrom(json).toJson());
    }

    private static List<String> defaultRules() {
        return List.of(AudioConfig.defaultRulesText().split(";"));
    }

    private void showSiteManage() {
        List<Site> sites = VodConfig.get().getSites().stream().filter(s -> s != null && !s.isEmpty()).toList();
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
                .setTitle(R.string.dialog_audio_site_manage)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> applySiteManage(sites, enabledRules, checked))
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void applySiteManage(List<Site> sites, List<String> enabledRules, boolean[] checked) {
        List<String> newEnabled = new ArrayList<>();
        // 保留关键词（非站点条目）
        for (String rule : enabledRules) {
            if (findSite(rule) == null) newEnabled.add(rule);
        }

        for (int i = 0; i < sites.size(); i++) {
            if (!checked[i]) continue;

            Site site = sites.get(i);
            String key = site.getKey();
            boolean matchedByKeyword = false;

            // 检查是否被关键词匹配
            for (String rule : enabledRules) {
                if (findSite(rule) == null && matchesRule(List.of(rule), site)) {
                    matchedByKeyword = true;
                    break;
                }
            }

            // 只有不被关键词匹配的，才显式加入
            if (!matchedByKeyword && !newEnabled.contains(key)) {
                newEnabled.add(key);
            }
        }

        // 更新暂存数据
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

    private Site findSite(String value) {
        if (TextUtils.isEmpty(value)) return null;
        String target = value.trim();
        for (Site site : VodConfig.get().getSites()) {
            if (site == null || site.isEmpty()) continue;
            if (target.equalsIgnoreCase(site.getKey())) return site;
            if (!TextUtils.isEmpty(site.getName()) && target.equalsIgnoreCase(site.getName())) return site;
        }
        return null;
    }

    private String displayName(Site site) {
        return site.getDisplayName();
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(values.get(i).replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    private void updateChipsDisplay() {
        enabledChips.removeAllViews();

        for (String rule : tempEnabledRules) {
            if (TextUtils.isEmpty(rule)) continue;
            Chip chip = createChip(rule.trim());
            enabledChips.addView(chip);
        }
    }

    private Chip createChip(String text) {
        Chip chip = new Chip(activity);

        // 启用规则：key 转站点名显示，关键词原样
        Site site = findSite(text);
        chip.setText(site != null ? displayName(site) : text);

        chip.setCloseIconVisible(true);
        chip.setCheckable(false);

        chip.setOnCloseIconClickListener(v -> removeEnabledRule(text));

        return chip;
    }

    private void removeEnabledRule(String rule) {
        // 尝试按显示名和 key 移除
        Site site = findSite(rule);
        if (site != null) {
            tempEnabledRules.remove(site.getKey());
            tempEnabledRules.remove(displayName(site));
        } else {
            tempEnabledRules.remove(rule);
        }
        updateChipsDisplay();
    }

    private void resetToDefault() {
        tempEnabledRules.clear();
        tempEnabledRules.addAll(defaultRules());
        updateChipsDisplay();
    }

    private void addRule(EditText input) {
        String rule = input.getText().toString().trim();
        if (TextUtils.isEmpty(rule)) return;

        // 去重：站点 key/名称 或关键词已存在则不重复添加
        Site site = findSite(rule);
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
