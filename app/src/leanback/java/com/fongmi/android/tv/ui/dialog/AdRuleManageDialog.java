package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.DisabledDefaultRuleStore;
import com.fongmi.android.tv.api.config.HlsRuleConfig;
import com.fongmi.android.tv.api.config.HlsRuleStateStore;
import com.fongmi.android.tv.api.config.ImportedAdRuleCandidateStore;
import com.fongmi.android.tv.api.config.RuleConfig;
import com.fongmi.android.tv.api.config.UserAdRuleStore;
import com.fongmi.android.tv.bean.ImportedAdRuleCandidate;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.databinding.DialogAdRuleManageBinding;
import com.fongmi.android.tv.databinding.DialogAdRuleDetailBinding;
import com.fongmi.android.tv.ui.adapter.AdRuleAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;

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
        binding.enableImported.setOnClickListener(v -> onEnableImported());
        binding.disableImported.setOnClickListener(v -> onDisableImported());
        binding.add.setOnKeyListener((v, keyCode, event) -> moveFocus(event, keyCode, KeyEvent.KEYCODE_DPAD_DOWN, binding.stats));
        binding.stats.setOnKeyListener((v, keyCode, event) -> {
            if (moveFocus(event, keyCode, KeyEvent.KEYCODE_DPAD_UP, binding.add)) return true;
            if (event.getAction() != KeyEvent.ACTION_DOWN || keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (binding.importCandidates.getVisibility() == View.VISIBLE) return requestFocus(binding.importCandidates);
            if (binding.importBulkActions.getVisibility() == View.VISIBLE) return requestFocus(binding.enableImported);
            return focusFirstRule();
        });
        binding.importCandidates.setOnKeyListener((v, keyCode, event) -> {
            if (moveFocus(event, keyCode, KeyEvent.KEYCODE_DPAD_UP, binding.stats)) return true;
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (binding.importBulkActions.getVisibility() == View.VISIBLE) return requestFocus(binding.enableImported);
                return focusFirstRule();
            }
            return false;
        });
        binding.enableImported.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return requestFocus(binding.importCandidates.getVisibility() == View.VISIBLE ? binding.importCandidates : binding.stats);
            }
            if (moveFocus(event, keyCode, KeyEvent.KEYCODE_DPAD_RIGHT, binding.disableImported)) return true;
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusFirstRule();
            return false;
        });
        binding.disableImported.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return requestFocus(binding.importCandidates.getVisibility() == View.VISIBLE ? binding.importCandidates : binding.stats);
            }
            if (moveFocus(event, keyCode, KeyEvent.KEYCODE_DPAD_LEFT, binding.enableImported)) return true;
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusFirstRule();
            return false;
        });
    }

    private boolean focusFirstRule() {
        if (adapter.getItemCount() == 0) return false;
        binding.recycler.scrollToPosition(0);
        binding.recycler.post(() -> {
            View row = binding.recycler.getLayoutManager() == null ? null : binding.recycler.getLayoutManager().findViewByPosition(0);
            View target = row == null ? null : row.findViewById(R.id.text);
            requestFocus(target);
        });
        return true;
    }

    private static boolean moveFocus(KeyEvent event, int keyCode, int expectedKey, View target) {
        return event.getAction() == KeyEvent.ACTION_DOWN && keyCode == expectedKey && requestFocus(target);
    }

    private static boolean requestFocus(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return false;
        boolean focused = view.requestFocus();
        if (focused) view.post(() -> view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), false));
        return focused;
    }

    private void restoreRuleFocus(int position) {
        if (position < 0) return;
        binding.recycler.scrollToPosition(position);
        binding.recycler.post(() -> {
            View row = binding.recycler.getLayoutManager() == null
                    ? null : binding.recycler.getLayoutManager().findViewByPosition(position);
            View target = row == null ? null : row.findViewById(R.id.toggle);
            if (target == null) {
                binding.recycler.post(() -> {
                    View retryRow = binding.recycler.getLayoutManager() == null
                            ? null : binding.recycler.getLayoutManager().findViewByPosition(position);
                    requestFocus(retryRow == null ? null : retryRow.findViewById(R.id.toggle));
                });
            } else {
                requestFocus(target);
            }
        });
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
        boolean hasImported = userRules.stream().anyMatch(rule -> UserAdRule.isInterfaceSource(rule.getSource()));
        binding.importBulkActions.setVisibility(hasImported ? View.VISIBLE : View.GONE);
    }

    private void onAddManual() {
        AdRuleEditDialog.create(null).show(requireActivity(), this::onRuleEdited);
    }

    private void onStats() {
        AdBlockStatsDialog.create((FragmentActivity) requireActivity()).show();
    }

    private void onEnableImported() {
        if (UserAdRuleStore.setInterfaceRulesEnabled(true) == 0) return;
        loadData();
        if (callback != null) callback.onRuleChanged();
    }

    private void onDisableImported() {
        new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.ad_rule_disable_imported)
                .setMessage(R.string.ad_rule_disable_imported_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    UserAdRuleStore.setInterfaceRulesEnabled(false);
                    loadData();
                    if (callback != null) callback.onRuleChanged();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        final AlertDialog[] dialogRef = new AlertDialog[1];
        View toolbar = createCandidateToolbar(
                () -> updateCandidateSelection(dialogRef[0], candidates, selected, 0),
                () -> updateCandidateSelection(dialogRef[0], candidates, selected, 1),
                () -> updateCandidateSelection(dialogRef[0], candidates, selected, 2),
                () -> ignoreCandidates(dialogRef[0], candidates));
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.ad_rule_import_title)
                .setCustomTitle(toolbar)
                .setMultiChoiceItems(labels, selected, (dialog, which, checked) -> selected[which] = checked)
                .setPositiveButton(R.string.ad_rule_import_enable_action, (dialog, which) -> importSelectedCandidates(candidates, selected, true))
                .setNeutralButton(R.string.ad_rule_import_action, (dialog, which) -> importSelectedCandidates(candidates, selected, false))
                .setNegativeButton(android.R.string.cancel, null);
        dialogRef[0] = builder.create();
        View finalToolbar = toolbar;
        dialogRef[0].setOnShowListener(dialog -> {
            if (finalToolbar instanceof LinearLayout root && root.getChildCount() > 1) requestFocus(root.getChildAt(1));
        });
        dialogRef[0].show();
    }

    private void ignoreCandidates(Dialog dialog, List<ImportedAdRuleCandidate> candidates) {
        List<String> ids = new ArrayList<>();
        for (ImportedAdRuleCandidate candidate : candidates) ids.add(candidate.getId());
        ImportedAdRuleCandidateStore.ignoreCandidates(ids);
        if (dialog != null) dialog.dismiss();
        loadData();
    }

    private void importSelectedCandidates(List<ImportedAdRuleCandidate> candidates, boolean[] selected, boolean enabled) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) if (selected[i]) ids.add(candidates.get(i).getId());
        if (!ids.isEmpty()) ImportedAdRuleCandidateStore.importCandidates(ids, enabled);
        onRuleEdited();
    }

    private View createCandidateToolbar(Runnable selectLowRisk, Runnable selectAll, Runnable invert, Runnable ignoreAll) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ResUtil.dp2px(24), ResUtil.dp2px(8), ResUtil.dp2px(24), 0);
        TextView title = new TextView(requireContext());
        title.setText(R.string.ad_rule_import_title);
        title.setTextSize(20);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        MaterialButton lowRisk = candidateActionButton(R.string.ad_rule_select_low_risk);
        lowRisk.setOnClickListener(v -> selectLowRisk.run());
        MaterialButton all = candidateActionButton(R.string.ad_rule_select_all);
        all.setOnClickListener(v -> selectAll.run());
        MaterialButton invertButton = candidateActionButton(R.string.ad_rule_invert_selection);
        invertButton.setOnClickListener(v -> invert.run());
        MaterialButton ignore = candidateActionButton(R.string.ad_rule_ignore_all);
        ignore.setOnClickListener(v -> ignoreAll.run());
        root.addView(lowRisk, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1f);
        first.setMargins(0, ResUtil.dp2px(4), ResUtil.dp2px(2), 0);
        row.addView(all, first);
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, -2, 1f);
        middle.setMargins(ResUtil.dp2px(2), ResUtil.dp2px(4), ResUtil.dp2px(2), 0);
        row.addView(invertButton, middle);
        LinearLayout.LayoutParams last = new LinearLayout.LayoutParams(0, -2, 1f);
        last.setMargins(ResUtil.dp2px(2), ResUtil.dp2px(4), 0, 0);
        row.addView(ignore, last);
        root.addView(row);
        return root;
    }

    private MaterialButton candidateActionButton(int textRes) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(textRes);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setMinHeight(ResUtil.dp2px(40));
        return button;
    }

    private void updateCandidateSelection(Dialog dialog, List<ImportedAdRuleCandidate> candidates,
                                          boolean[] selected, int mode) {
        if (mode == 0) {
            for (int i = 0; i < selected.length; i++) selected[i] = ImportedAdRuleCandidate.RISK_LOW.equals(candidates.get(i).getRiskLevel());
        } else if (mode == 1) {
            for (int i = 0; i < selected.length; i++) selected[i] = true;
        } else {
            for (int i = 0; i < selected.length; i++) selected[i] = !selected[i];
        }
        if (dialog instanceof AlertDialog alert && alert.getListView() != null) {
            for (int i = 0; i < selected.length; i++) alert.getListView().setItemChecked(i, selected[i]);
        }
    }

    private void onRuleEdited() {
        loadData();
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onUserRuleClick(UserAdRule item) {
        showUserRuleDetail(item);
    }

    @Override
    public void onDefaultRuleClick(Rule rule, String ruleId, boolean currentEnabled) {
        showDefaultRuleDetail(rule);
    }

    @Override
    public void onHlsRuleClick(HlsRuleConfig.Entry item) {
        showHlsRuleDetail(item);
    }

    private void showUserRuleDetail(UserAdRule item) {
        Runnable edit = UserAdRule.SOURCE_MANUAL.equals(item.getSource())
                ? () -> AdRuleEditDialog.create(item).show(requireActivity(), this::onRuleEdited)
                : null;
        showRuleDetail(item.getName(), item.getSummary(), item.getHosts(), item.getRegex(), List.of(), item.getExclude(), edit);
    }

    private void showDefaultRuleDetail(Rule rule) {
        showRuleDetail(rule.getName(), "", rule.getHosts(), rule.getRegex(), rule.getScript(), rule.getExclude(), null);
    }

    private void showHlsRuleDetail(HlsRuleConfig.Entry item) {
        HlsRuleConfig.Entry current = HlsRuleConfig.getEntries().stream()
                .filter(entry -> item.key().equals(entry.key()))
                .findFirst().orElse(item);
        String name = current.name().isBlank() ? current.id() : current.name();
        String status = current.valid()
                ? getString(current.enabled() ? R.string.ad_rule_hls_enabled : R.string.ad_rule_hls_disabled)
                : getString(R.string.ad_rule_hls_invalid, current.error());
        String details = HlsRuleConfig.LEGACY_FALLBACK_KEY.equals(current.id())
                ? "具体判断规则\n" + HlsRuleConfig.LEGACY_FALLBACK_DETAIL
                : getString(R.string.ad_rule_detail_json) + "\n" + current.detail();
        String content = getString(R.string.ad_rule_hls_builtin_summary, current.version(), status)
                + "\n" + getString(R.string.ad_rule_detail_id, current.id())
                + "\n" + getString(R.string.ad_rule_detail_source, current.source())
                + "\n\n" + details;
        showTextDetail(name, content, null);
    }

    private void showRuleDetail(String name, String summary, List<String> hosts, List<String> regex,
                                List<String> script, List<String> exclude, Runnable editAction) {
        String message = (summary.isEmpty() ? "" : summary + "\n\n")
                + getString(R.string.ad_rule_detail_hosts) + "\n" + listText(hosts)
                + "\n\n" + getString(R.string.ad_rule_detail_regex) + "\n" + listText(regex)
                + "\n\n" + getString(R.string.ad_rule_detail_script) + "\n" + listText(script)
                + "\n\n" + getString(R.string.ad_rule_detail_exclude) + "\n" + listText(exclude);
        showTextDetail(name, message, editAction);
    }

    private void showTextDetail(String name, String content, Runnable editAction) {
        DialogAdRuleDetailBinding detail = DialogAdRuleDetailBinding.inflate(getLayoutInflater());
        detail.content.setText(content);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(name)
                .setView(detail.getRoot())
                .setPositiveButton(android.R.string.ok, null);
        if (editAction != null) builder.setNeutralButton(R.string.ad_rule_edit_title, (dialog, which) -> editAction.run());
        AlertDialog alert = builder.create();
        alert.setOnShowListener(dialog -> {
            View positive = alert.getButton(DialogInterface.BUTTON_POSITIVE);
            detail.scroll.setNextFocusDownId(positive.getId());
            positive.setNextFocusUpId(detail.scroll.getId());
            detail.scroll.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN || keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (detail.scroll.canScrollVertically(1)) return false;
                return positive.requestFocus();
            });
            // 部分 Android TV 版本会把 NestedScrollView 变成焦点陷阱。
            // 默认聚焦明确的操作按钮；需要阅读内容时按上即可进入滚动区。
            positive.requestFocus();
        });
        alert.show();
    }

    private String listText(List<String> items) {
        return items == null || items.isEmpty() ? "无" : String.join("\n", items);
    }

    private void confirmDisable(String name, int messageRes, Runnable action) {
        new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(name)
                .setMessage(messageRes)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> action.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onUserToggleClick(UserAdRule item, boolean enabled) {
        if (!enabled) {
            int message = UserAdRule.SOURCE_AI.equals(item.getSource()) ? R.string.ad_rule_ai_disable_confirm
                    : UserAdRule.isInterfaceSource(item.getSource()) ? R.string.ad_rule_imported_disable_confirm
                    : R.string.ad_rule_manual_disable_confirm;
            confirmDisable(item.getName(), message, () -> setUserEnabled(item, false));
        } else {
            setUserEnabled(item, true);
        }
    }

    private void setUserEnabled(UserAdRule item, boolean enabled) {
        item.setEnabled(enabled);
        UserAdRuleStore.update(item);
        int position = adapter.positionOfUserRule(item);
        adapter.refreshUserEnabled(item);
        restoreRuleFocus(position);
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onDefaultToggleClick(String ruleId, boolean enabled) {
        if (!enabled) {
            confirmDisable(getString(R.string.setting_ad_rule_manage), R.string.ad_rule_default_disable_confirm, () -> setDefaultEnabled(ruleId, false));
        } else {
            setDefaultEnabled(ruleId, true);
        }
    }

    private void setDefaultEnabled(String ruleId, boolean enabled) {
        DisabledDefaultRuleStore.setDisabled(ruleId, !enabled);
        int position = adapter.positionOfDefaultRule(ruleId);
        adapter.refreshDefaultEnabled(ruleId, enabled);
        restoreRuleFocus(position);
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onHlsToggleClick(HlsRuleConfig.Entry item, boolean enabled) {
        if (!enabled) {
            confirmDisable(item.name().isBlank() ? item.id() : item.name(), R.string.ad_rule_default_disable_confirm,
                    () -> setHlsEnabled(item.key(), false));
        } else {
            setHlsEnabled(item.key(), true);
        }
    }

    private void setHlsEnabled(String key, boolean enabled) {
        HlsRuleStateStore.setEnabled(key, enabled);
        int position = adapter.positionOfHlsRule(key);
        adapter.refreshHlsEnabled(key, enabled);
        restoreRuleFocus(position);
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onDeleteClick(UserAdRule item) {
        AlertDialog alert = new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.ad_rule_delete_confirm)
                .setMessage(getString(R.string.ad_rule_delete_message, item.getName()))
                .setPositiveButton(R.string.ad_rule_delete_confirm, (dialog, which) -> deleteUserRule(item))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        alert.setOnShowListener(dialog -> alert.getButton(DialogInterface.BUTTON_NEGATIVE).requestFocus());
        alert.show();
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
