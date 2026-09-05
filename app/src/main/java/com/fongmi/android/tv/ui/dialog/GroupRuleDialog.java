package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.bean.GroupRule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.playback.PlaybackConfigIdentity;
import com.fongmi.android.tv.service.AiGroupRuleService;
import com.fongmi.android.tv.setting.AiGroupRuleStore;
import com.fongmi.android.tv.setting.GroupRuleConfig;
import com.fongmi.android.tv.setting.GroupRuleStore;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteNameStore;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Prefers;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

public final class GroupRuleDialog {

    private static final String AI_PRIVACY_ACK = "ai_group_rule_privacy_ack_v1";
    private static final int MAX_PREVIEW_GROUPS = 30;
    private static final int MAX_PREVIEW_SAMPLES = 3;

    private final FragmentActivity activity;
    private final LifecycleEventObserver aiLifecycleObserver = (source, event) -> {
        if (event == Lifecycle.Event.ON_DESTROY) cancelAiSilently();
    };
    private Runnable onChanged;
    private Future<?> aiTask;
    private AiGroupRuleService aiService;
    private int aiGeneration;
    private boolean observingAiLifecycle;

    public static GroupRuleDialog create(FragmentActivity activity) {
        return new GroupRuleDialog(activity);
    }

    private GroupRuleDialog(FragmentActivity activity) {
        this.activity = activity;
    }

    public GroupRuleDialog onChanged(Runnable callback) {
        this.onChanged = callback;
        return this;
    }

    public void show() {
        String configKey = currentConfigKey();
        List<GroupRuleConfig.Entry> entries = GroupRuleConfig.entries();
        CharSequence[] labels = new CharSequence[entries.size()];
        boolean[] checked = new boolean[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            GroupRuleConfig.Entry entry = entries.get(i);
            GroupRule rule = entry.rule();
            String name = TextUtils.isEmpty(rule.getName()) ? rule.getId() : rule.getName();
            labels[i] = name + "\n" + rule.getSummary();
            checked[i] = entry.enabled();
        }

        View title = LayoutInflater.from(builder().getContext()).inflate(R.layout.dialog_group_rule_title, null);
        MaterialButton aiAction = title.findViewById(R.id.aiAction);
        AiConfig aiConfig = AiConfig.objectFrom(Setting.getAiConfig());
        aiAction.setVisibility(aiConfig.isEnabled() ? View.VISIBLE : View.GONE);
        aiAction.setText(AiGroupRuleStore.has(configKey) ? R.string.group_rule_ai_reidentify : R.string.group_rule_ai_identify);

        AlertDialog dialog = builder()
                .setCustomTitle(title)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    if (!ensureCurrentConfig(configKey, d)) return;
                    GroupRule rule = entries.get(which).rule();
                    if (rule.isUser()) {
                        rule.setEnabled(isChecked);
                        GroupRuleStore.updateUser(rule);
                    } else if (rule.isAi()) {
                        rule.setEnabled(isChecked);
                        AiGroupRuleStore.update(configKey, rule);
                    } else {
                        GroupRuleStore.setEnabled(rule.getId(), isChecked);
                    }
                    changed();
                })
                .setPositiveButton(R.string.dialog_positive, null)
                .setNeutralButton(R.string.group_rule_add, (d, which) -> showEditor(null, ""))
                .setNegativeButton(R.string.group_rule_manage, (d, which) -> showManage())
                .create();
        dialog.show();
        aiAction.setOnClickListener(v -> {
            dialog.dismiss();
            startAiRecognition();
        });
        wireAiTitleFocus(dialog, aiAction);
        LightDialog.apply(dialog);
    }

    private void wireAiTitleFocus(AlertDialog dialog, MaterialButton aiAction) {
        if (aiAction.getVisibility() != View.VISIBLE) return;
        ListView list = dialog.getListView();
        wireDpadFocus(aiAction, null, list, null, null);
        if (list != null) {
            list.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN || keyCode != KeyEvent.KEYCODE_DPAD_UP) return false;
                if (list.getSelectedItemPosition() > 0) return false;
                return requestFocus(aiAction);
            });
        }
    }

    private void showManage() {
        String configKey = currentConfigKey();
        List<GroupRule> userRules = GroupRuleStore.loadUser();
        List<GroupRule> aiRules = AiGroupRuleStore.load(configKey);
        List<GroupRuleConfig.Entry> interfaceEntries = new ArrayList<>();
        for (GroupRuleConfig.Entry entry : GroupRuleConfig.entries()) {
            if (GroupRule.SOURCE_INTERFACE.equals(entry.rule().getSource())) interfaceEntries.add(entry);
        }
        if (userRules.isEmpty() && aiRules.isEmpty() && interfaceEntries.isEmpty()) {
            Notify.show(R.string.group_rule_empty_custom);
            show();
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (GroupRule rule : userRules) {
            labels.add(activity.getString(R.string.group_rule_edit_item, displayName(rule), rule.getRegex()));
            actions.add(() -> showEditor(rule, ""));
        }
        for (GroupRule rule : aiRules) {
            labels.add(activity.getString(R.string.group_rule_ai_label, displayName(rule), rule.getRegex()));
            actions.add(() -> showEditor(rule, configKey));
        }
        for (GroupRuleConfig.Entry entry : interfaceEntries) {
            GroupRule rule = entry.rule();
            labels.add(activity.getString(R.string.group_rule_interface_item, displayName(rule), rule.getRegex()));
            actions.add(() -> showDetail(rule));
        }
        AlertDialog dialog = builder()
                .setTitle(R.string.group_rule_manage)
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(R.string.dialog_negative, (d, which) -> show())
                .create();
        dialog.show();
        LightDialog.apply(dialog);
    }

    private void showDetail(GroupRule rule) {
        AlertDialog dialog = builder()
                .setTitle(displayName(rule))
                .setMessage(rule.getSummary())
                .setPositiveButton(R.string.dialog_positive, (d, which) -> show())
                .create();
        dialog.show();
        LightDialog.apply(dialog);
    }

    private void showEditor(GroupRule existing, String configKey) {
        MaterialAlertDialogBuilder builder = builder();
        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_group_rule_edit, null);
        TextInputEditText nameInput = view.findViewById(R.id.nameInput);
        TextInputEditText regexInput = view.findViewById(R.id.regexInput);
        if (existing != null) {
            nameInput.setText(existing.getName());
            regexInput.setText(existing.getRegex());
        }
        builder.setTitle(existing == null ? R.string.group_rule_add : R.string.group_rule_edit)
                .setView(view)
                .setPositiveButton(R.string.dialog_positive, null)
                .setNegativeButton(R.string.dialog_negative, null);
        if (existing != null) builder.setNeutralButton(R.string.group_rule_delete, null);
        AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(d -> show());
        dialog.setOnShowListener(d -> {
            View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            View negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            View neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            wireTextDpadFocus(nameInput, null, regexInput, null, null);
            wireTextDpadFocus(regexInput, nameInput, positive, null, null);
            wireDpadFocus(positive, regexInput, null, null, null);
            wireDpadFocus(negative, regexInput, null, null, null);
            wireDpadFocus(neutral, regexInput, null, null, null);
            positive.setOnClickListener(v -> {
                String name = text(nameInput);
                String regex = text(regexInput);
                if (TextUtils.isEmpty(regex)) {
                    Notify.show(R.string.group_rule_regex_required);
                    regexInput.requestFocus();
                    return;
                }
                GroupRule probe = existing != null && existing.isAi() ? GroupRule.createAi(name, regex) : GroupRule.createUser(name, regex);
                if (!probe.isValid()) {
                    Notify.show(R.string.group_rule_regex_invalid);
                    regexInput.requestFocus();
                    return;
                }
                if (existing != null && existing.isAi() && !ensureCurrentConfig(configKey, dialog)) return;
                if (existing == null) {
                    GroupRuleStore.addUser(probe);
                } else {
                    existing.setName(name);
                    existing.setRegex(regex);
                    existing.setEnabled(true);
                    if (existing.isAi()) AiGroupRuleStore.update(configKey, existing);
                    else GroupRuleStore.updateUser(existing);
                }
                dialog.dismiss();
                changed();
                show();
            });
            negative.setOnClickListener(v -> {
                dialog.dismiss();
                show();
            });
            if (neutral != null && existing != null) {
                neutral.setOnClickListener(v -> {
                    if (existing.isAi() && !ensureCurrentConfig(configKey, dialog)) return;
                    if (existing.isAi()) AiGroupRuleStore.delete(configKey, existing.getId());
                    else GroupRuleStore.deleteUser(existing.getId());
                    dialog.dismiss();
                    changed();
                    show();
                });
            }
            nameInput.requestFocus();
        });
        dialog.show();
        LightDialog.apply(dialog);
    }

    private void startAiRecognition() {
        AiConfig config = AiConfig.objectFrom(Setting.getAiConfig());
        if (!config.isReady()) {
            Notify.show(R.string.group_rule_ai_config_incomplete);
            show();
            return;
        }
        AiRequest request = currentAiRequest();
        if (request.names.size() < 2) {
            Notify.show(R.string.group_rule_ai_no_sources);
            show();
            return;
        }
        if (Prefers.getBoolean(AI_PRIVACY_ACK)) {
            analyze(request, config, 0, "");
            return;
        }
        AlertDialog dialog = builder()
                .setTitle(R.string.group_rule_ai_privacy_title)
                .setMessage(R.string.group_rule_ai_privacy_message)
                .setNegativeButton(R.string.dialog_negative, (d, which) -> showIfActive())
                .setPositiveButton(R.string.group_rule_ai_start, (d, which) -> {
                    Prefers.put(AI_PRIVACY_ACK, true);
                    analyze(request, config, 0, "");
                })
                .create();
        dialog.setOnCancelListener(d -> showIfActive());
        dialog.show();
        LightDialog.apply(dialog);
    }

    private AiRequest currentAiRequest() {
        List<String> names = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (site == null || site.isHide()) continue;
            String name = SiteNameStore.getDisplayName(site).trim();
            if (!name.isEmpty()) names.add(name);
        }
        List<GroupRule> existing = new ArrayList<>();
        for (GroupRuleConfig.Entry entry : GroupRuleConfig.entries()) {
            if (entry.enabled() && !entry.rule().isAi()) existing.add(entry.rule());
        }
        return new AiRequest(currentConfigKey(), currentConfigName(), List.copyOf(names), List.copyOf(existing));
    }

    private void analyze(AiRequest request, AiConfig config, int retry, String previousSummary) {
        observeAiLifecycle();
        int generation = ++aiGeneration;
        AlertDialog loading = builder()
                .setTitle(R.string.group_rule_ai_analyzing_title)
                .setMessage(activity.getString(R.string.group_rule_ai_analyzing_message, request.names.size()))
                .setNegativeButton(R.string.dialog_negative, (d, which) -> cancelAi(generation))
                .create();
        loading.setOnCancelListener(d -> cancelAi(generation));
        loading.show();
        LightDialog.apply(loading);

        AiGroupRuleService service = new AiGroupRuleService(config);
        aiService = service;
        aiTask = Task.submit(() -> {
            AiGroupRuleService.AnalysisResult result = service.analyze(request.names, request.existingRules, retry, previousSummary);
            AiGroupRuleService.PreviewIndex previewIndex = null;
            if (result.isSuccess()) {
                List<GroupRule> candidates = new ArrayList<>();
                for (AiGroupRuleService.Candidate candidate : result.getCandidates()) candidates.add(candidate.getRule());
                previewIndex = AiGroupRuleService.buildPreviewIndex(request.names, request.existingRules, candidates);
            }
            AiGroupRuleService.PreviewIndex finalPreviewIndex = previewIndex;
            activity.runOnUiThread(() -> {
                if (generation != aiGeneration || activity.isFinishing() || activity.isDestroyed()) return;
                clearAiLifecycleObserver();
                loading.dismiss();
                aiTask = null;
                aiService = null;
                if (!TextUtils.equals(request.configKey, currentConfigKey())) {
                    Notify.show(R.string.group_rule_ai_config_changed);
                    show();
                    return;
                }
                if (!result.isSuccess()) {
                    showAiError(request, config, retry, previousSummary, result);
                    return;
                }
                showAiPreview(request, config, retry, result, finalPreviewIndex);
            });
        });
    }

    private void cancelAi(int generation) {
        if (generation != aiGeneration) return;
        cancelAiSilently();
        showIfActive();
    }

    private void cancelAiSilently() {
        aiGeneration++;
        if (aiService != null) aiService.cancel();
        if (aiTask != null) aiTask.cancel(true);
        aiService = null;
        aiTask = null;
        clearAiLifecycleObserver();
    }

    private void observeAiLifecycle() {
        if (observingAiLifecycle) return;
        observingAiLifecycle = true;
        activity.getLifecycle().addObserver(aiLifecycleObserver);
    }

    private void clearAiLifecycleObserver() {
        if (!observingAiLifecycle) return;
        observingAiLifecycle = false;
        activity.getLifecycle().removeObserver(aiLifecycleObserver);
    }

    private boolean ensureCurrentConfig(String configKey, DialogInterface dialog) {
        if (TextUtils.equals(configKey, currentConfigKey())) return true;
        if (dialog != null) dialog.dismiss();
        Notify.show(R.string.group_rule_ai_config_changed);
        showIfActive();
        return false;
    }

    private void showIfActive() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        show();
    }

    private void showAiError(AiRequest request, AiConfig config, int retry, String previousSummary, AiGroupRuleService.AnalysisResult result) {
        MaterialAlertDialogBuilder builder = builder()
                .setTitle(R.string.group_rule_ai_failed_title)
                .setMessage(aiErrorMessage(result))
                .setNegativeButton(R.string.dialog_negative, (d, which) -> showIfActive());
        if (result.isRetryable()) {
            builder.setPositiveButton(R.string.group_rule_ai_retry, (d, which) -> analyze(request, config, retry + 1, previousSummary));
        }
        AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(d -> showIfActive());
        dialog.show();
        LightDialog.apply(dialog);
    }

    private String aiErrorMessage(AiGroupRuleService.AnalysisResult result) {
        return switch (result.getReason()) {
            case CONFIG_INCOMPLETE -> activity.getString(R.string.group_rule_ai_config_incomplete);
            case NOT_ENOUGH_SOURCES -> activity.getString(R.string.group_rule_ai_no_sources);
            case PROMPT_TOO_LARGE -> activity.getString(R.string.group_rule_ai_error_prompt_too_large);
            case TOO_MANY_SOURCES -> activity.getString(R.string.group_rule_ai_error_too_many_sources);
            case HTTP -> activity.getString(R.string.group_rule_ai_error_http, result.getDetail());
            case INVALID_JSON, INVALID_OBJECT, PARSE_ERROR -> activity.getString(R.string.group_rule_ai_error_invalid_response);
            case NO_VALID_RULES -> activity.getString(R.string.group_rule_ai_error_no_valid_rules);
            case TIMEOUT -> activity.getString(R.string.group_rule_ai_error_timeout);
            case NETWORK -> activity.getString(R.string.group_rule_ai_error_network);
            default -> activity.getString(R.string.group_rule_ai_error_generic);
        };
    }

    private void showAiPreview(AiRequest request, AiConfig config, int retry, AiGroupRuleService.AnalysisResult result, AiGroupRuleService.PreviewIndex previewIndex) {
        MaterialAlertDialogBuilder builder = builder();
        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_ai_group_rule_preview, null);
        NestedScrollView contentScroll = view.findViewById(R.id.contentScroll);
        TextView summary = view.findViewById(R.id.summary);
        TextView groupsText = view.findViewById(R.id.groupsText);
        LinearLayoutCompat rulesContainer = view.findViewById(R.id.rulesContainer);
        List<MaterialCheckBox> checks = new ArrayList<>();
        for (AiGroupRuleService.Candidate candidate : result.getCandidates()) {
            MaterialCheckBox check = new MaterialCheckBox(builder.getContext());
            check.setText(activity.getString(R.string.group_rule_ai_rule_summary,
                    displayName(candidate.getRule()), candidate.getRule().getRegex(), candidate.getMatchedSourceCount(), candidate.getGroupCount(), candidate.getIncrementalCoverage(), candidate.getRepeatedSourceCount()));
            check.setTextColor(Color.rgb(32, 33, 36));
            check.setTextSize(14);
            check.setChecked(true);
            check.setFocusable(true);
            check.setFocusableInTouchMode(Util.isLeanback());
            int padding = dp(8);
            check.setPadding(padding, padding, padding, padding);
            checks.add(check);
            rulesContainer.addView(check, new LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
        }

        Runnable update = () -> updateAiPreview(result, checks, previewIndex, summary, groupsText);
        for (MaterialCheckBox check : checks) check.setOnCheckedChangeListener((buttonView, isChecked) -> update.run());
        update.run();

        AlertDialog dialog = builder
                .setTitle(R.string.group_rule_ai_preview_title)
                .setView(view)
                .setNegativeButton(R.string.dialog_negative, (d, which) -> showIfActive())
                .setNeutralButton(R.string.group_rule_ai_retry, null)
                .setPositiveButton(R.string.group_rule_ai_apply, null)
                .create();
        dialog.setOnShowListener(d -> {
            View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            View neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            View negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            positive.setOnClickListener(v -> applyAiRules(dialog, request, result, checks));
            neutral.setOnClickListener(v -> {
                dialog.dismiss();
                analyze(request, config, retry + 1, result.summary());
            });
            dialog.setOnKeyListener((ignored, keyCode, event) -> handleAiPreviewKey(dialog, keyCode, event, checks, positive, neutral, negative));
            wireAiPreviewFocus(checks, positive, neutral, negative);
        });
        dialog.show();
        LightDialog.apply(dialog);
        configureAiPreviewWindow(dialog, contentScroll);
    }

    private boolean handleAiPreviewKey(AlertDialog dialog, int keyCode, KeyEvent event, List<MaterialCheckBox> checks, View positive, View neutral, View negative) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        View focused = dialog.getCurrentFocus();
        int index = checks.indexOf(focused);
        if (index >= 0 && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            View target = index + 1 < checks.size() ? checks.get(index + 1) : neutral;
            if (target != null) requestFocus(target);
            return true;
        }
        if (index > 0 && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            requestFocus(checks.get(index - 1));
            return true;
        }
        if ((focused == positive || focused == neutral || focused == negative)
                && keyCode == KeyEvent.KEYCODE_DPAD_UP && !checks.isEmpty()) {
            requestFocus(checks.get(checks.size() - 1));
            return true;
        }
        return false;
    }

    private void wireAiPreviewFocus(List<MaterialCheckBox> checks, View positive, View neutral, View negative) {
        View last = checks.isEmpty() ? null : checks.get(checks.size() - 1);
        for (View button : new View[]{neutral, negative, positive}) {
            if (button == null) continue;
            button.setFocusable(true);
            button.setFocusableInTouchMode(Util.isLeanback());
        }
        for (int i = 0; i < checks.size(); i++) {
            View up = i > 0 ? checks.get(i - 1) : null;
            View down = i + 1 < checks.size() ? checks.get(i + 1) : neutral;
            wireDpadFocus(checks.get(i), up, down, null, null);
        }
        wireDpadFocus(neutral, last, null, null, negative);
        wireDpadFocus(negative, last, null, neutral, positive);
        wireDpadFocus(positive, last, null, negative, null);
        View initial = checks.isEmpty() ? neutral : checks.get(0);
        if (initial != null) initial.post(() -> requestFocus(initial));
    }

    private void configureAiPreviewWindow(AlertDialog dialog, NestedScrollView contentScroll) {
        Window window = dialog.getWindow();
        if (window == null) return;
        int screenWidth = ResUtil.getScreenWidth(activity);
        int screenHeight = ResUtil.getScreenHeight(activity);
        boolean landscape = screenWidth >= screenHeight;
        AiGroupRulePreviewSizing.Size size = AiGroupRulePreviewSizing.calculate(
                screenWidth, screenHeight, dp(landscape ? 24 : 12), dp(landscape ? 170 : 190));
        if (size.width() > 0) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = size.width();
            params.height = size.dialogHeight();
            window.setAttributes(params);
            window.setLayout(size.width(), size.dialogHeight());
        }
        if (size.contentHeight() > 0) {
            ViewGroup.LayoutParams params = contentScroll.getLayoutParams();
            params.height = size.contentHeight();
            contentScroll.setLayoutParams(params);
        }
    }

    private void updateAiPreview(AiGroupRuleService.AnalysisResult result, List<MaterialCheckBox> checks, AiGroupRuleService.PreviewIndex previewIndex, TextView summary, TextView groupsText) {
        boolean[] selected = new boolean[checks.size()];
        int selectedCount = 0;
        for (int i = 0; i < checks.size(); i++) {
            selected[i] = checks.get(i).isChecked();
            if (selected[i]) selectedCount++;
        }
        AiGroupRuleService.Preview preview = previewIndex.preview(selected);
        summary.setText(activity.getString(R.string.group_rule_ai_preview_summary,
                preview.getSourceCount(), selectedCount, preview.getMatchedSourceCount(), preview.getSourceCount(), preview.getCoveragePercent()));
        groupsText.setText(formatGroups(preview));
    }

    private String formatGroups(AiGroupRuleService.Preview preview) {
        StringBuilder text = new StringBuilder();
        int index = 0;
        for (Map.Entry<String, List<String>> entry : preview.getGroups().entrySet()) {
            if (index >= MAX_PREVIEW_GROUPS) break;
            if (text.length() > 0) text.append("\n\n");
            text.append(entry.getKey()).append(" · ").append(entry.getValue().size());
            List<String> samples = entry.getValue().subList(0, Math.min(MAX_PREVIEW_SAMPLES, entry.getValue().size()));
            if (!samples.isEmpty()) text.append("\n  ").append(String.join(", ", samples));
            index++;
        }
        int more = preview.getGroups().size() - index;
        if (more > 0) text.append("\n\n").append(activity.getString(R.string.group_rule_ai_more_groups, more));
        if (!preview.getUnmatched().isEmpty()) {
            if (text.length() > 0) text.append("\n\n");
            text.append(activity.getString(R.string.group_rule_ai_unmatched, preview.getUnmatched().size()));
            List<String> samples = preview.getUnmatched().subList(0, Math.min(MAX_PREVIEW_SAMPLES, preview.getUnmatched().size()));
            if (!samples.isEmpty()) text.append("\n  ").append(String.join(", ", samples));
        }
        return text.length() == 0 ? "—" : text.toString();
    }

    private void applyAiRules(AlertDialog dialog, AiRequest request, AiGroupRuleService.AnalysisResult result, List<MaterialCheckBox> checks) {
        if (!TextUtils.equals(request.configKey, currentConfigKey())) {
            Notify.show(R.string.group_rule_ai_config_changed);
            dialog.dismiss();
            show();
            return;
        }
        List<GroupRule> selected = selectedRules(result, checks);
        if (selected.isEmpty()) {
            Notify.show(R.string.group_rule_ai_select_required);
            return;
        }
        AiGroupRuleStore.replace(request.configKey, selected);
        dialog.dismiss();
        changed();
        Notify.show(activity.getString(R.string.group_rule_ai_saved, selected.size(), request.configName));
        show();
    }

    private List<GroupRule> selectedRules(AiGroupRuleService.AnalysisResult result, List<MaterialCheckBox> checks) {
        List<GroupRule> selected = new ArrayList<>();
        for (int i = 0; i < checks.size() && i < result.getCandidates().size(); i++) {
            if (checks.get(i).isChecked()) selected.add(result.getCandidates().get(i).getRule());
        }
        return selected;
    }

    private MaterialAlertDialogBuilder builder() {
        return new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog);
    }

    private static void wireDpadFocus(View view, View up, View down, View left, View right) {
        if (view == null) return;
        view.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && up != null) return requestFocus(up);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && down != null) return requestFocus(down);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && left != null) return requestFocus(left);
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && right != null) return requestFocus(right);
            return false;
        });
    }

    private static void wireTextDpadFocus(EditText view, View up, View down, View left, View right) {
        if (view == null) return;
        view.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && up != null) return requestFocus(up);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && down != null) return requestFocus(down);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && left != null && isCursorAtStart(view)) return requestFocus(left);
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && right != null && isCursorAtEnd(view)) return requestFocus(right);
            return false;
        });
    }

    private static boolean requestFocus(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return false;
        boolean focused = view.requestFocus();
        if (focused) view.post(() -> view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), false));
        return focused;
    }

    private static boolean isCursorAtStart(EditText view) {
        return Math.max(0, view.getSelectionStart()) <= 0;
    }

    private static boolean isCursorAtEnd(EditText view) {
        int length = view.getText() == null ? 0 : view.getText().length();
        return Math.max(view.getSelectionStart(), view.getSelectionEnd()) >= length;
    }

    private static String text(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String displayName(GroupRule rule) {
        return TextUtils.isEmpty(rule.getName()) ? rule.getId() : rule.getName();
    }

    private String currentConfigKey() {
        String key = PlaybackConfigIdentity.currentKey();
        return TextUtils.isEmpty(key) ? "cid:" + VodConfig.getCid() : key;
    }

    private String currentConfigName() {
        String name = PlaybackConfigIdentity.currentName();
        return TextUtils.isEmpty(name) ? activity.getString(R.string.group_rule_ai_current_interface) : name;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void changed() {
        if (onChanged != null) onChanged.run();
    }

    private static final class AiRequest {
        private final String configKey;
        private final String configName;
        private final List<String> names;
        private final List<GroupRule> existingRules;

        private AiRequest(String configKey, String configName, List<String> names, List<GroupRule> existingRules) {
            this.configKey = Objects.toString(configKey, "");
            this.configName = Objects.toString(configName, "");
            this.names = names;
            this.existingRules = existingRules;
        }
    }
}
