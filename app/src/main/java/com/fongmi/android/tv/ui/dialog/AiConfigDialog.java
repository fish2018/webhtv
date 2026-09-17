package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.text.Layout;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.service.AiCompletionClient;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class AiConfigDialog {

    private static final String[] PROTOCOL_VALUES = {
            AiConfig.PROTOCOL_OPENAI_RESPONSES,
            AiConfig.PROTOCOL_OPENAI_CHAT,
            AiConfig.PROTOCOL_ANTHROPIC_MESSAGES,
            AiConfig.PROTOCOL_GEMINI_NATIVE
    };

    private final FragmentActivity activity;
    private Context dialogContext;
    private Runnable onDismiss;
    private AiConfig config;

    private SwitchMaterial enabled;
    private SwitchMaterial titleExtraction;
    private AutoCompleteTextView protocol;
    private TextInputEditText endpoint;
    private TextInputEditText apiKey;
    private AutoCompleteTextView model;
    private TextInputEditText userAgent;
    private MaterialButton fetchModels;

    public static AiConfigDialog create(FragmentActivity activity) {
        return new AiConfigDialog(activity);
    }

    private AiConfigDialog(FragmentActivity activity) {
        this.activity = activity;
    }

    public AiConfigDialog onDismiss(Runnable callback) {
        this.onDismiss = callback;
        return this;
    }

    public void show() {
        MaterialAlertDialogBuilder builder = builder();
        dialogContext = builder.getContext();
        View view = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_ai_config, null);
        enabled = view.findViewById(R.id.enabled);
        titleExtraction = view.findViewById(R.id.titleExtraction);
        protocol = view.findViewById(R.id.protocol);
        endpoint = view.findViewById(R.id.endpoint);
        apiKey = view.findViewById(R.id.apiKey);
        model = view.findViewById(R.id.model);
        userAgent = view.findViewById(R.id.userAgent);
        fetchModels = view.findViewById(R.id.fetchModels);
        MaterialButton prompt = view.findViewById(R.id.prompt);
        MaterialButton test = view.findViewById(R.id.test);

        config = AiConfig.objectFrom(Setting.getAiConfig());
        setupProtocolDropdown();
        setupModelDropdown(new ArrayList<>());
        enabled.setChecked(config.isEnabled());
        titleExtraction.setChecked(Setting.isAiTitleExtraction());
        protocol.setText(protocolLabel(config.getProtocol()), false);
        endpoint.setText(config.getEndpoint());
        endpoint.setHint(AiConfig.defaultEndpoint(config.getProtocol()));
        apiKey.setText(config.getApiKey());
        model.setText(config.getModel());
        userAgent.setText(config.getCustomUserAgent());
        prompt.setOnClickListener(v -> showPromptConfig());
        fetchModels.setOnClickListener(v -> fetchModels(fetchModels));
        test.setOnClickListener(v -> testConfig(test));

        AlertDialog dialog = builder
                .setTitle(R.string.setting_ai_recommendation)
                .setView(view)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> onSave())
                .setNegativeButton(R.string.dialog_negative, null)
                .setOnDismissListener(d -> { if (onDismiss != null) onDismiss.run(); })
                .show();
        wireConfigDialogFocus(dialog, prompt, test);
        LightDialog.apply(dialog);
    }

    private MaterialAlertDialogBuilder builder() {
        return new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog);
    }

    private void showPromptConfig() {
        MaterialAlertDialogBuilder builder = builder();
        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_ai_prompt_config, null);
        TextInputEditText recommendPrompt = view.findViewById(R.id.recommendPrompt);
        TextInputEditText titleExtractionPrompt = view.findViewById(R.id.titleExtractionPrompt);
        TextInputEditText viewingReportPrompt = view.findViewById(R.id.viewingReportPrompt);
        TextInputEditText adDetectionPrompt = view.findViewById(R.id.adDetectionPrompt);
        TextInputEditText groupRulePrompt = view.findViewById(R.id.groupRulePrompt);
        recommendPrompt.setText(config.getRecommendPrompt());
        titleExtractionPrompt.setText(config.getTitleExtractionPrompt());
        viewingReportPrompt.setText(config.getViewingReportPrompt());
        adDetectionPrompt.setText(config.getAdDetectionPrompt());
        groupRulePrompt.setText(config.getGroupRulePrompt());
        AlertDialog dialog = builder
                .setTitle(R.string.dialog_ai_prompt_config)
                .setView(view)
                .setPositiveButton(R.string.dialog_positive, null)
                .setNegativeButton(R.string.dialog_negative, null)
                .setNeutralButton(R.string.dialog_ai_prompt_reset, null)
                .create();
        dialog.setOnShowListener(d -> {
            View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            wirePromptEditorFocus(recommendPrompt, null, titleExtractionPrompt);
            wirePromptEditorFocus(titleExtractionPrompt, recommendPrompt, viewingReportPrompt);
            wirePromptEditorFocus(viewingReportPrompt, titleExtractionPrompt, adDetectionPrompt);
            wirePromptEditorFocus(adDetectionPrompt, viewingReportPrompt, groupRulePrompt);
            wirePromptEditorFocus(groupRulePrompt, adDetectionPrompt, positive);
            wireButtonUp(dialog.getButton(AlertDialog.BUTTON_POSITIVE), groupRulePrompt);
            wireButtonUp(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), groupRulePrompt);
            wireButtonUp(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), groupRulePrompt);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                config.setRecommendPrompt(text(recommendPrompt));
                config.setTitleExtractionPrompt(text(titleExtractionPrompt));
                config.setViewingReportPrompt(text(viewingReportPrompt));
                config.setAdDetectionPrompt(text(adDetectionPrompt));
                config.setGroupRulePrompt(text(groupRulePrompt));
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                showPromptResetPicker(recommendPrompt, titleExtractionPrompt, viewingReportPrompt, adDetectionPrompt, groupRulePrompt);
            });
        });
        dialog.show();
        LightDialog.apply(dialog);
    }

    private void showPromptResetPicker(TextInputEditText recommendPrompt, TextInputEditText titleExtractionPrompt, TextInputEditText viewingReportPrompt, TextInputEditText adDetectionPrompt, TextInputEditText groupRulePrompt) {
        String[] labels = {
                dialogContext.getString(R.string.dialog_ai_recommend_prompt_label),
                dialogContext.getString(R.string.dialog_ai_title_extraction_prompt_label),
                dialogContext.getString(R.string.dialog_ai_viewing_report_prompt_label),
                dialogContext.getString(R.string.dialog_ai_ad_detection_prompt_label),
                dialogContext.getString(R.string.dialog_ai_group_rule_prompt_label)
        };
        boolean[] checked = {true, true, true, true, true};
        AlertDialog picker = new MaterialAlertDialogBuilder(dialogContext, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.dialog_ai_prompt_reset)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> {
                    if (checked[0]) resetPromptField(recommendPrompt, AiConfig.DEFAULT_RECOMMEND_PROMPT);
                    if (checked[1]) resetPromptField(titleExtractionPrompt, AiConfig.DEFAULT_TITLE_EXTRACTION_PROMPT);
                    if (checked[2]) resetPromptField(viewingReportPrompt, AiConfig.DEFAULT_VIEWING_REPORT_PROMPT);
                    if (checked[3]) resetPromptField(adDetectionPrompt, AiConfig.DEFAULT_AD_DETECTION_PROMPT);
                    if (checked[4]) resetPromptField(groupRulePrompt, AiConfig.DEFAULT_GROUP_RULE_PROMPT);
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .create();
        picker.show();
        LightDialog.apply(picker);
    }

    private static void resetPromptField(TextInputEditText input, String value) {
        input.setText(value);
        input.setSelection(input.length());
    }

    private static void wirePromptEditorFocus(TextInputEditText input, View upTarget, View downTarget) {
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && upTarget != null && isCursorAtFirstLine(input)) {
                upTarget.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && downTarget != null && isCursorAtLastLine(input)) {
                downTarget.requestFocus();
                return true;
            }
            return false;
        });
    }

    private static void wireButtonUp(View button, View target) {
        if (button == null || target == null) return;
        button.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                target.requestFocus();
                return true;
            }
            return false;
        });
    }

    private void wireConfigDialogFocus(AlertDialog dialog, View prompt, View test) {
        View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        View negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        wireDpadFocus(enabled, null, titleExtraction, null, null);
        wireDpadFocus(titleExtraction, enabled, protocol, null, null);
        wireDpadFocus(protocol, titleExtraction, endpoint, null, null);
        wireDpadFocus(endpoint, protocol, apiKey, null, null);
        wireDpadFocus(apiKey, endpoint, model, null, null);
        wireDropdownDpadFocus(model, apiKey, userAgent, null, fetchModels);
        wireDpadFocus(fetchModels, apiKey, userAgent, model, null);
        wireDpadFocus(userAgent, model, prompt, null, null);
        wireDpadFocus(prompt, userAgent, test, null, null);
        wireDpadFocus(test, prompt, positive, null, null);
        wireDpadFocus(positive, test, null, null, null);
        wireDpadFocus(negative, test, null, null, null);
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

    private static void wireDropdownDpadFocus(AutoCompleteTextView view, View up, View down, View left, View right) {
        if (view == null) return;
        view.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            // 下拉弹窗展开时，把上下键与确认键交给列表处理，以便遥控器选择选项。
            if (view.isPopupShowing()) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                        || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    return false;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && up != null) return requestFocus(up);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && down != null) return requestFocus(down);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && left != null) return requestFocus(left);
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && right != null) return requestFocus(right);
            return false;
        });
    }

    private static boolean requestFocus(View view) {
        view.requestFocus();
        return true;
    }

    private static boolean isCursorAtFirstLine(TextInputEditText input) {
        Layout layout = input.getLayout();
        if (layout == null) return false;
        return layout.getLineForOffset(selection(input)) <= 0;
    }

    private static boolean isCursorAtLastLine(TextInputEditText input) {
        Layout layout = input.getLayout();
        if (layout == null) return false;
        return layout.getLineForOffset(selection(input)) >= layout.getLineCount() - 1;
    }

    private static int selection(TextInputEditText input) {
        return Math.max(0, input.getSelectionStart());
    }

    private void setupProtocolDropdown() {
        protocol.setFocusable(true);
        protocol.setKeyListener(null);
        protocol.setOnClickListener(v -> showProtocolPicker());
        protocol.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) showProtocolPicker(); });
    }

    private void showProtocolPicker() {
        String[] labels = protocolLabels();
        int currentIndex = -1;
        for (int i = 0; i < PROTOCOL_VALUES.length; i++) {
            if (PROTOCOL_VALUES[i].equals(config.getProtocol())) {
                currentIndex = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(dialogContext, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.dialog_ai_protocol_label)
                .setSingleChoiceItems(labels, currentIndex, (dialog, which) -> {
                    String oldProtocol = config.getProtocol();
                    String selected = which >= 0 && which < PROTOCOL_VALUES.length ? PROTOCOL_VALUES[which] : AiConfig.DEFAULT_PROTOCOL;
                    config.setProtocol(selected);
                    protocol.setText(labels[which]);
                    endpoint.setHint(AiConfig.defaultEndpoint(selected));
                    String currentEndpoint = text(endpoint);
                    if (currentEndpoint.isEmpty() || currentEndpoint.equals(AiConfig.defaultEndpoint(oldProtocol))) {
                        endpoint.setText(AiConfig.defaultEndpoint(selected));
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void setupModelDropdown(List<AiCompletionClient.ModelInfo> models) {
        List<String> values = new ArrayList<>();
        for (AiCompletionClient.ModelInfo item : models) if (!item.getId().isEmpty()) values.add(item.getId());
        model.setAdapter(new NoFilterAdapter(adapterContext(), values));
        model.setThreshold(0);
        model.setOnClickListener(v -> {
            if (model.getAdapter() != null && model.getAdapter().getCount() > 0) model.showDropDown();
        });
        model.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && model.getAdapter() != null && model.getAdapter().getCount() > 0) model.showDropDown();
        });
    }

    private void onSave() {
        readConfigFromInput();
        Setting.putAiConfig(config.toJson());
        Setting.putAiTitleExtraction(titleExtraction.isChecked());
    }

    private void testConfig(MaterialButton button) {
        readConfigFromInput();
        button.setEnabled(false);
        Notify.show(R.string.dialog_ai_test_running);
        long start = System.currentTimeMillis();
        Task.execute(() -> {
            AiCompletionClient.TestResult result = AiCompletionClient.testConfig(config);
            String elapsed = formatElapsed(System.currentTimeMillis() - start);
            activity.runOnUiThread(() -> {
                button.setEnabled(true);
                if (result.isSuccess()) {
                    Notify.show(activity.getString(R.string.dialog_ai_test_success, result.getSampleTitle(), elapsed));
                } else {
                    Notify.show(activity.getString(R.string.dialog_ai_test_failed, result.getMessage(), elapsed));
                }
            });
        });
    }

    private void fetchModels(MaterialButton button) {
        readConfigFromInput();
        button.setEnabled(false);
        Notify.show(R.string.dialog_ai_fetch_models_running);
        Task.execute(() -> {
            AiCompletionClient.ModelFetchResult result = AiCompletionClient.fetchModels(config);
            activity.runOnUiThread(() -> {
                button.setEnabled(true);
                if (!result.isSuccess()) {
                    Notify.show(activity.getString(R.string.dialog_ai_fetch_models_failed, result.getMessage()));
                    return;
                }
                if (result.getModels().isEmpty()) {
                    Notify.show(R.string.dialog_ai_fetch_models_empty);
                    return;
                }
                setupModelDropdown(result.getModels());
                Notify.show(activity.getString(R.string.dialog_ai_fetch_models_success, result.getModels().size()));
                model.requestFocus();
                model.showDropDown();
            });
        });
    }

    private void readConfigFromInput() {
        config.setEnabled(enabled.isChecked());
        config.setProtocol(selectedProtocol());
        config.setEndpoint(text(endpoint));
        config.setApiKey(text(apiKey));
        config.setModel(text(model));
        config.setCustomUserAgent(text(userAgent));
    }

    private String selectedProtocol() {
        String value = text(protocol);
        String[] labels = protocolLabels();
        for (int i = 0; i < labels.length && i < PROTOCOL_VALUES.length; i++) {
            if (labels[i].equals(value)) return PROTOCOL_VALUES[i];
        }
        return AiConfig.isSupportedProtocol(value) ? value : config.getProtocol();
    }

    private String[] protocolLabels() {
        return activity.getResources().getStringArray(R.array.dialog_ai_protocol_labels);
    }

    private String protocolLabel(String value) {
        String[] labels = protocolLabels();
        for (int i = 0; i < PROTOCOL_VALUES.length && i < labels.length; i++) {
            if (PROTOCOL_VALUES[i].equals(value)) return labels[i];
        }
        return labels.length > 0 ? labels[0] : AiConfig.PROTOCOL_OPENAI_RESPONSES;
    }

    private Context adapterContext() {
        return dialogContext == null ? activity : dialogContext;
    }

    private static String text(TextView input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static String formatElapsed(long millis) {
        if (millis < 1000) return millis + "ms";
        return String.format(java.util.Locale.US, "%.1fs", millis / 1000f);
    }

    /**
     * 不过滤的 ArrayAdapter，用于解决 AutoCompleteTextView 重新打开时因过滤导致列表空白的问题。
     */
    private static class NoFilterAdapter extends ArrayAdapter<String> {
        private final List<String> items;

        public NoFilterAdapter(Context context, List<String> items) {
            super(context, android.R.layout.simple_dropdown_item_1line, items);
            this.items = new ArrayList<>(items);
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = items;
                    results.count = items.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    if (results.count > 0) {
                        notifyDataSetChanged();
                    } else {
                        notifyDataSetInvalidated();
                    }
                }
            };
        }
    }
}
