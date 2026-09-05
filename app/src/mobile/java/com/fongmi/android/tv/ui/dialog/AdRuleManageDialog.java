package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.BuiltinHlsRuleLoader;
import com.fongmi.android.tv.api.config.DisabledDefaultRuleStore;
import com.fongmi.android.tv.api.config.HlsRuleConfig;
import com.fongmi.android.tv.api.config.HlsRuleStateStore;
import com.fongmi.android.tv.api.config.ImportedAdRuleCandidateStore;
import com.fongmi.android.tv.api.config.RuleConfig;
import com.fongmi.android.tv.api.config.UserAdRuleStore;
import com.fongmi.android.tv.bean.ImportedAdRuleCandidate;
import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.databinding.DialogAdRuleManageBinding;
import com.fongmi.android.tv.ui.adapter.AdRuleAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class AdRuleManageDialog extends BaseAlertDialog implements AdRuleAdapter.OnClickListener {

    private DialogAdRuleManageBinding binding;
    private AdRuleAdapter adapter;
    private Callback callback;

    public interface Callback {
        void onRuleChanged();
    }

    public static AdRuleManageDialog create() {
        return new AdRuleManageDialog();
    }

    public void show(FragmentActivity activity, Callback callback) {
        this.callback = callback;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogAdRuleManageBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new AdRuleAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter);
        loadData();
    }

    @Override
    protected void initEvent() {
        binding.add.setOnClickListener(v -> onAddManual());
        binding.stats.setOnClickListener(v -> onStats());
        binding.importCandidates.setOnClickListener(v -> onImportCandidates());
    }

    private void loadData() {
        List<AdRuleAdapter.RuleItem> items = new ArrayList<>();

        // AI 识别规则 + 手动添加规则
        List<UserAdRule> userRules = UserAdRuleStore.load();
        for (UserAdRule rule : userRules) {
            items.add(AdRuleAdapter.RuleItem.fromUser(rule));
        }

        // 点播/直播接口配置规则
        List<RuleConfig.DefaultRuleEntry> defaultRules = RuleConfig.get().getDefaultRuleEntries();
        for (RuleConfig.DefaultRuleEntry entry : defaultRules) {
            items.add(AdRuleAdapter.RuleItem.fromDefault(entry.getRule(), entry.getSource()));
        }

        List<HlsRuleConfig.Entry> hlsRules = HlsRuleConfig.getEntries();
        for (HlsRuleConfig.Entry entry : hlsRules) {
            if ("builtin".equals(entry.source())) items.add(AdRuleAdapter.RuleItem.fromHls(entry));
        }

        adapter.setItems(items);

        // 空态提示(仅当两部分都为空时显示)
        int builtinHlsCount = (int) hlsRules.stream().filter(entry -> "builtin".equals(entry.source())).count();
        boolean isEmpty = userRules.isEmpty() && defaultRules.isEmpty() && builtinHlsCount == 0;
        binding.customEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        // 更新分区标题计数
        binding.serverRules.setText(getString(R.string.ad_rule_section_summary, userRules.size(), defaultRules.size(), builtinHlsCount));
        int pending = ImportedAdRuleCandidateStore.pending().size();
        binding.importCandidates.setVisibility(pending == 0 ? View.GONE : View.VISIBLE);
        binding.importCandidates.setText(getString(R.string.ad_rule_import_candidates, pending));
    }

    private void onAddManual() {
        AdRuleEditDialog.create(null).show(requireActivity(), this::onRuleEdited);
    }

    private void onStats() {
        AdBlockStatsDialog.create((FragmentActivity) requireActivity()).show();
    }

    private void onImportCandidates() {
        List<ImportedAdRuleCandidate> candidates = ImportedAdRuleCandidateStore.pending();
        if (candidates.isEmpty()) return;
        String[] labels = candidates.stream()
                .map(item -> item.getName() + " · " + item.getRiskLevel() + " · " + Math.round(item.getConfidence() * 100) + "%\n"
                        + "来源：" + item.getSourceConfigName() + " / " + item.getSourceType() + "\n"
                        + "hosts: " + String.join(", ", item.getHosts()) + "\n"
                        + "广告规则: " + String.join(" | ", item.getRegex()) + "\n"
                        + "正片保护: " + String.join(" | ", item.getExclude()))
                .toArray(String[]::new);
        boolean[] selected = new boolean[candidates.size()];
        for (int i = 0; i < selected.length; i++) selected[i] = ImportedAdRuleCandidate.RISK_LOW.equals(candidates.get(i).getRiskLevel());
        new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.ad_rule_import_title)
                .setMultiChoiceItems(labels, selected, (dialog, which, checked) -> selected[which] = checked)
                .setPositiveButton(R.string.ad_rule_import_action, (dialog, which) -> {
                    for (int i = 0; i < candidates.size(); i++) if (selected[i]) ImportedAdRuleCandidateStore.importCandidate(candidates.get(i).getId());
                    onRuleEdited();
                })
                .setNeutralButton(R.string.ad_rule_ignore_all, (dialog, which) -> {
                    for (ImportedAdRuleCandidate candidate : candidates) ImportedAdRuleCandidateStore.ignore(candidate.getId());
                    loadData();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onRuleEdited() {
        loadData();
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onUserRuleClick(UserAdRule item) {
        Runnable editAction = UserAdRule.SOURCE_MANUAL.equals(item.getSource())
                ? () -> AdRuleEditDialog.create(item).show(requireActivity(), this::onRuleEdited) : null;
        showRuleDetail(item.getName(), item.getSummary(), item.getHosts(), item.getRegex(), item.getExclude(), editAction);
    }

    @Override
    public void onDefaultRuleClick(Rule rule, String ruleId, boolean currentEnabled) {
        showRuleDetail(rule.getName(), "", rule.getHosts(), rule.getRegex(), rule.getExclude(), null);
    }

    @Override
    public void onHlsRuleClick(HlsRuleConfig.Entry item) {
        new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(item.name().isBlank() ? item.id() : item.name())
                .setMessage(getHlsRuleDetail(item))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showRuleDetail(String name, String summary, List<String> hosts, List<String> regex, List<String> exclude, Runnable editAction) {
        String message = (summary.isEmpty() ? "" : summary + "\n\n")
                + "域名\n" + listText(hosts) + "\n\nURL 规则\n" + listText(regex) + "\n\n白名单\n" + listText(exclude);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(name)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null);
        if (editAction != null) builder.setNeutralButton("编辑", (dialog, which) -> editAction.run());
        builder.show();
    }

    private String getHlsRuleDetail(HlsRuleConfig.Entry item) {
        String status = item.valid()
                ? getString(item.enabled() ? R.string.ad_rule_hls_enabled : R.string.ad_rule_hls_disabled)
                : getString(R.string.ad_rule_hls_invalid, item.error());
        HlsAdRule rule = BuiltinHlsRuleLoader.getRules().stream().filter(value -> item.id().equals(value.getId())).findFirst().orElse(null);
        if (rule == null) return getString(R.string.ad_rule_hls_builtin_summary, item.version(), status) + "\n\n规则 ID\n" + item.id();
        JsonObject json = App.gson().toJsonTree(rule).getAsJsonObject();
        return getString(R.string.ad_rule_hls_builtin_summary, item.version(), status)
                + "\n\n规则 ID\n" + item.id()
                + "\n\n播放列表域名\n" + jsonList(json, "playlistHostSuffixes", "playlistHostRegex")
                + "\n\n分片域名\n" + jsonList(json, "hostSuffixes")
                + "\n\n分片 URL 正则\n" + jsonList(json, "segmentUrlRegex")
                + "\n\n时长范围\n" + jsonValue(json, "minDuration") + " ～ " + jsonValue(json, "maxDuration") + " 秒"
                + "\n\n需要不连续标记\n" + yesNo(json, "requireDiscontinuity")
                + "\n\n需要跨域\n" + yesNo(json, "requireCrossDomain")
                + "\n\n最少命中信号\n" + jsonValue(json, "minimumSignals");
    }

    private String jsonList(JsonObject json, String... names) {
        List<String> values = new ArrayList<>();
        for (String name : names) {
            JsonElement element = json.get(name);
            if (element == null || !element.isJsonArray()) continue;
            JsonArray array = element.getAsJsonArray();
            for (JsonElement value : array) values.add(value.getAsString());
        }
        return listText(values);
    }

    private String jsonValue(JsonObject json, String name) {
        JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? "无" : value.getAsString();
    }

    private String yesNo(JsonObject json, String name) {
        JsonElement value = json.get(name);
        return value != null && value.getAsBoolean() ? "是" : "否";
    }

    private String listText(List<String> items) {
        return items == null || items.isEmpty() ? "无" : String.join("\n", items);
    }

    private void confirmDisable(String name, int messageRes, Runnable action) {
        new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(name)
                .setMessage(messageRes)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> action.run())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> loadData())
                .show();
    }

    @Override
    public void onUserToggleClick(UserAdRule item, boolean enabled) {
        if (!enabled) {
            int message = UserAdRule.SOURCE_AI.equals(item.getSource()) ? R.string.ad_rule_ai_disable_confirm
                    : item.getSource() != null && item.getSource().startsWith("interface_") ? R.string.ad_rule_imported_disable_confirm
                    : R.string.ad_rule_manual_disable_confirm;
            confirmDisable(item.getName(), message, () -> setUserEnabled(item, false));
        }
        else setUserEnabled(item, true);
    }

    private void setUserEnabled(UserAdRule item, boolean enabled) {
        item.setEnabled(enabled);
        UserAdRuleStore.update(item);
        loadData();
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onDefaultToggleClick(String ruleId, boolean enabled) {
        if (!enabled) confirmDisable(getString(R.string.setting_ad_rule_manage), R.string.ad_rule_default_disable_confirm, () -> setDefaultEnabled(ruleId, false));
        else setDefaultEnabled(ruleId, true);
    }

    private void setDefaultEnabled(String ruleId, boolean enabled) {
        DisabledDefaultRuleStore.setDisabled(ruleId, !enabled);
        loadData();
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onHlsToggleClick(HlsRuleConfig.Entry item, boolean enabled) {
        HlsRuleStateStore.setEnabled(item.key(), enabled);
        loadData();
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onDeleteClick(UserAdRule item) {
        new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.ad_rule_delete_confirm)
                .setMessage(getString(R.string.ad_rule_delete_message, item.getName()))
                .setPositiveButton(R.string.ad_rule_delete_confirm, (dialog, which) -> deleteUserRule(item))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow();
    }

    private void configureWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.6f : 0.9f)), ResUtil.dp2px(560));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void deleteUserRule(UserAdRule item) {
        UserAdRuleStore.delete(item.getId());
        if (adapter.removeUserRule(item) == 0) loadData();
        if (callback != null) callback.onRuleChanged();
    }
}
