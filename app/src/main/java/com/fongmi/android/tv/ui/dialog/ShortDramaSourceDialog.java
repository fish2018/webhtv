package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.ShortDramaConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.setting.Setting;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShortDramaSourceDialog {

    private final FragmentActivity activity;
    private AlertDialog dialog;
    private ChipGroup enabledChips;
    private ChipGroup disabledChips;
    private TextView disabledLabel;
    private Runnable onDismiss;

    // 暂存数据，点"确定"才保存
    private List<String> tempEnabledRules;
    private List<String> tempDisabledSites;

    public static ShortDramaSourceDialog create(FragmentActivity activity) {
        return new ShortDramaSourceDialog(activity);
    }

    private ShortDramaSourceDialog(FragmentActivity activity) {
        this.activity = activity;
    }

    public ShortDramaSourceDialog onDismiss(Runnable callback) {
        this.onDismiss = callback;
        return this;
    }

    public void show() {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_short_drama_source, null);
        enabledChips = view.findViewById(R.id.enabledChips);
        disabledChips = view.findViewById(R.id.disabledChips);
        disabledLabel = view.findViewById(R.id.disabledLabel);
        EditText ruleInput = view.findViewById(R.id.ruleInput);
        View addBtn = view.findViewById(R.id.add);
        View manageBtn = view.findViewById(R.id.manage);
        View resetBtn = view.findViewById(R.id.resetDefault);

        // 初始化暂存数据
        ShortDramaConfig config = ShortDramaConfig.objectFrom(Setting.getShortDramaConfig());
        tempEnabledRules = new ArrayList<>(config.getEnabledSites());
        tempDisabledSites = new ArrayList<>(config.getDisabledSites());
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
                .setTitle(R.string.setting_short_drama_source)
                .setView(view)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> onSave())
                .setNegativeButton(R.string.dialog_negative, null)
                .setOnDismissListener(d -> { if (onDismiss != null) onDismiss.run(); })
                .create();
        dialog.show();
    }

    private void onSave() {
        String json = "{\"configured\":true,\"enabledSites\":" + toJsonArray(tempEnabledRules) + ",\"disabledSites\":" + toJsonArray(tempDisabledSites) + "}";
        Setting.putShortDramaConfig(ShortDramaConfig.objectFrom(json).toJson());
    }

    private void showSiteManage() {
        List<Site> sites = VodConfig.get().getSites().stream().filter(s -> s != null && !s.isEmpty()).toList();
        if (sites.isEmpty()) return;

        List<String> enabledRules = tempEnabledRules.isEmpty()
            ? List.of(ShortDramaConfig.defaultRulesText().split(";"))
            : new ArrayList<>(tempEnabledRules);
        List<String> disabledSites = new ArrayList<>(tempDisabledSites);

        String[] labels = new String[sites.size()];
        boolean[] checked = new boolean[sites.size()];

        for (int i = 0; i < sites.size(); i++) {
            Site site = sites.get(i);
            labels[i] = site.getDisplayName() + "  " + site.getKey();
            boolean inBlacklist = disabledSites.contains(site.getKey());
            boolean matchedByRule = matchesRule(enabledRules, site);
            checked[i] = matchedByRule && !inBlacklist;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dialog_short_drama_site_manage)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> applySiteManage(sites, enabledRules, disabledSites, checked))
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    // 应用站点管理结果：立即保存并刷新显示
    private void applySiteManage(List<Site> sites, List<String> enabledRules, List<String> disabledSites, boolean[] checked) {
        List<String> newEnabled = new ArrayList<>();
        // 保留关键词（非站点条目）
        for (String rule : enabledRules) {
            if (findSite(rule) == null) newEnabled.add(rule);
        }

        for (int i = 0; i < sites.size(); i++) {
            Site site = sites.get(i);
            String key = site.getKey();
            boolean nowChecked = checked[i];
            boolean matchedByKeyword = false;

            // 检查是否被关键词匹配（只看关键词，不看显式站点）
            for (String rule : enabledRules) {
                if (findSite(rule) == null && matchesRule(List.of(rule), site)) {
                    matchedByKeyword = true;
                    break;
                }
            }

            if (nowChecked) {
                // 勾选：从黑名单移除
                disabledSites.remove(key);
                // 只有不被关键词匹配的，才显式加入 enabledSites
                if (!matchedByKeyword && !newEnabled.contains(key)) {
                    newEnabled.add(key);
                }
            } else {
                // 取消勾选：如果被关键词匹配，加入黑名单；否则从 enabledSites 移除（已在上面过滤）
                if (matchedByKeyword && !disabledSites.contains(key)) {
                    disabledSites.add(key);
                }
            }
        }

        // 更新暂存数据（点"确定"才保存）
        tempEnabledRules = newEnabled;
        tempDisabledSites = disabledSites;

        // 刷新 Chip 显示
        updateChipsDisplay();
    }

    private void updateDisabledDisplay(ShortDramaConfig config) {
        String disabled = config.getDisplayDisabledSites();
        if (TextUtils.isEmpty(disabled)) {
            disabledLabel.setVisibility(View.GONE);
        } else {
            disabledLabel.setVisibility(View.VISIBLE);
            disabledLabel.setText(activity.getString(R.string.dialog_short_drama_site_disabled, disabled));
        }
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

    // 显示文本：站点 key 转为站点名称，关键词原样保留
    private String toDisplayText(List<String> rules) {
        if (rules == null || rules.isEmpty()) return "";
        List<String> display = new ArrayList<>();
        for (String rule : rules) {
            Site site = findSite(rule);
            display.add(site != null ? displayName(site) : rule);
        }
        return String.join(";", display);
    }

    // 保存时：站点名称转回 key，关键词原样保留
    private List<String> extractKeys(String text) {
        List<String> result = new ArrayList<>();
        for (String rule : splitRules(text)) {
            Site site = findSite(rule);
            String value = site != null ? site.getKey() : rule;
            if (!result.contains(value)) result.add(value);
        }
        return result;
    }

    // 按 key 或名称精确查找站点
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

    private List<String> splitRules(String text) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return result;
        for (String item : text.split("[,，;；\\n]")) {
            String s = item.trim();
            if (!s.isEmpty() && !result.contains(s)) result.add(s);
        }
        return result;
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

    // Chip 相关方法
    private void updateChipsDisplay() {
        enabledChips.removeAllViews();
        disabledChips.removeAllViews();

        List<String> enabledRules = tempEnabledRules.isEmpty()
            ? List.of(ShortDramaConfig.defaultRulesText().split(";"))
            : tempEnabledRules;

        for (String rule : enabledRules) {
            if (TextUtils.isEmpty(rule)) continue;
            Chip chip = createChip(rule.trim(), false);
            enabledChips.addView(chip);
        }

        if (tempDisabledSites.isEmpty()) {
            disabledLabel.setVisibility(View.GONE);
            disabledChips.setVisibility(View.GONE);
        } else {
            disabledLabel.setVisibility(View.VISIBLE);
            disabledChips.setVisibility(View.VISIBLE);
            for (String key : tempDisabledSites) {
                Site site = findSite(key);
                String displayName = site != null ? displayName(site) : key;
                Chip chip = createChip(displayName, true);
                chip.setTag(key); // 保存 key 用于删除
                disabledChips.addView(chip);
            }
        }
    }

    private Chip createChip(String text, boolean isDisabled) {
        Chip chip = new Chip(activity);

        // 启用规则：key 转站点名显示，关键词原样；黑名单已是显示名
        Site site = isDisabled ? null : findSite(text);
        chip.setText(site != null ? displayName(site) : text);

        chip.setCloseIconVisible(true);
        chip.setCheckable(false);

        if (isDisabled) {
            chip.setChipBackgroundColorResource(android.R.color.transparent);
            chip.setChipStrokeColorResource(android.R.color.holo_red_light);
            chip.setChipStrokeWidth(2f);
        }

        chip.setOnCloseIconClickListener(v -> {
            if (isDisabled) {
                // 从黑名单移除
                removeFromBlacklist((String) chip.getTag());
            } else {
                // 从启用规则移除
                removeEnabledRule(text);
            }
        });

        return chip;
    }

    private void removeFromBlacklist(String key) {
        tempDisabledSites.remove(key);
        updateChipsDisplay();
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
        tempDisabledSites.clear();
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
