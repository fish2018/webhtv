package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogMpvConfigCreateBinding;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class MpvConfigCreateDialog extends BaseAlertDialog {

    public interface Listener {
        void onText(String name, boolean buttonEnabled, String trigger);

        void onImport(String name, String path, boolean buttonEnabled, String trigger);
    }

    private DialogMpvConfigCreateBinding binding;
    private Listener listener;
    private String target;
    private boolean scriptButtonMode;
    private boolean scriptSettingsMode;
    private String scriptId;
    private String scriptContent;
    private MpvConfigStore.CustomButton sourceButton;
    private Runnable buttonCallback;
    private String shortCode;
    private String longCode;
    private String startupCode;
    private int triggerId;

    public static void show(FragmentManager manager, String target, Listener listener) {
        MpvConfigCreateDialog dialog = new MpvConfigCreateDialog();
        dialog.target = target;
        dialog.listener = listener;
        dialog.show(manager, "mpv-config-create");
    }

    public static void showScriptButton(FragmentManager manager, @Nullable MpvConfigStore.CustomButton source, Runnable callback) {
        MpvConfigCreateDialog dialog = new MpvConfigCreateDialog();
        dialog.target = MpvConfigStore.TARGET_SCRIPTS;
        dialog.scriptButtonMode = true;
        dialog.sourceButton = source;
        dialog.buttonCallback = callback;
        dialog.show(manager, "mpv-script-button");
    }

    public static void showScriptSettings(FragmentManager manager, String scriptId, String title,
                                          String content, @Nullable MpvConfigStore.CustomButton source,
                                          Runnable callback) {
        MpvConfigCreateDialog dialog = new MpvConfigCreateDialog();
        dialog.target = MpvConfigStore.TARGET_SCRIPTS;
        dialog.scriptSettingsMode = true;
        dialog.scriptId = scriptId;
        dialog.scriptContent = content == null ? "" : content;
        dialog.sourceButton = source;
        dialog.buttonCallback = callback;
        dialog.show(manager, "mpv-script-settings");
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogMpvConfigCreateBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        if (scriptSettingsMode) setupScriptSettings();
        else if (scriptButtonMode) setupScriptButton();
        else if (MpvConfigStore.TARGET_SCRIPTS.equals(target)) setupScriptCreation();
        else setupTvFocus();
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(view -> dismiss());
        binding.triggerGroup.addOnButtonCheckedListener(this::onTriggerChecked);
        if (MpvConfigStore.TARGET_SCRIPTS.equals(target)) {
            updateScriptTrigger(binding.buttonEnabled.isChecked(), triggerId);
            binding.buttonEnabled.setOnCheckedChangeListener((button, enabled) ->
                    updateScriptTrigger(enabled, enabled ? R.id.triggerClick : R.id.triggerStartup));
        }
        binding.scriptCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateScriptStats(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        binding.buttonCancel.setOnClickListener(view -> {
            if (scriptButtonMode && sourceButton == null) showScriptCreation();
            else dismissAllowingStateLoss();
        });
        binding.buttonSave.setOnClickListener(view -> {
            if (scriptSettingsMode) saveScriptSettings();
            else saveScriptButton();
        });
        binding.scriptEdit.setOnClickListener(view -> openScriptEditor());
        binding.textOption.setOnClickListener(view -> createText());
        if (scriptButtonMode) {
            return;
        }
        binding.urlOption.setOnClickListener(view -> showUrlInput());
        binding.importOption.setOnClickListener(view -> chooseFile());
        binding.urlBack.setOnClickListener(view -> showOptions());
        binding.urlImport.setOnClickListener(view -> importUrl());
        binding.url.setOnEditorActionListener((view, actionId, event) -> {
            importUrl();
            return true;
        });
        binding.name.setOnEditorActionListener((view, actionId, event) -> {
            if (!Util.isLeanback()) return false;
            binding.textOption.requestFocus();
            return true;
        });
    }

    private void setupTvFocus() {
        if (!Util.isLeanback()) return;
        tvFocusable(binding.close);
        tvFocusable(binding.name);
        tvFocusable(binding.textOption);
        tvFocusable(binding.urlOption);
        tvFocusable(binding.importOption);
        tvFocusable(binding.url);
        tvFocusable(binding.urlBack);
        tvFocusable(binding.urlImport);
        binding.close.setNextFocusDownId(R.id.name);
        binding.name.setNextFocusUpId(R.id.close);
        if (MpvConfigStore.TARGET_SCRIPTS.equals(target)) {
            tvFocusable(binding.buttonEnabled);
            tvFocusable(binding.triggerClick);
            tvFocusable(binding.triggerLong);
            tvFocusable(binding.triggerStartup);
            binding.name.setNextFocusDownId(R.id.buttonEnabled);
            binding.buttonEnabled.setNextFocusUpId(R.id.name);
            binding.buttonEnabled.setNextFocusDownId(R.id.triggerClick);
            binding.triggerClick.setNextFocusUpId(R.id.buttonEnabled);
            binding.triggerClick.setNextFocusDownId(R.id.textOption);
            binding.triggerLong.setNextFocusDownId(R.id.textOption);
            binding.triggerStartup.setNextFocusDownId(R.id.textOption);
        } else {
            binding.name.setNextFocusDownId(R.id.textOption);
        }
        binding.textOption.setNextFocusUpId(MpvConfigStore.TARGET_SCRIPTS.equals(target) ? R.id.triggerClick : R.id.name);
        binding.textOption.setNextFocusDownId(R.id.urlOption);
        binding.urlOption.setNextFocusUpId(R.id.textOption);
        binding.urlOption.setNextFocusDownId(R.id.importOption);
        binding.importOption.setNextFocusUpId(R.id.urlOption);
        binding.url.setNextFocusUpId(R.id.close);
        binding.url.setNextFocusDownId(R.id.urlBack);
        binding.urlBack.setNextFocusUpId(R.id.url);
        binding.urlBack.setNextFocusRightId(R.id.urlImport);
        binding.urlImport.setNextFocusUpId(R.id.url);
        binding.urlImport.setNextFocusLeftId(R.id.urlBack);
    }

    private void setupScriptButton() {
        binding.createTitle.setText(getString(sourceButton == null ? R.string.mpv_config_custom_button_new : R.string.mpv_config_custom_button_edit));
        binding.chooseAction.setText(R.string.mpv_config_custom_button_new_desc);
        binding.chooseAction.setVisibility(View.VISIBLE);
        binding.nameLabel.setText(R.string.mpv_config_custom_button_name);
        binding.name.setHint(R.string.mpv_config_custom_button_name_hint);
        binding.name.setText(sourceButton == null ? "" : sourceButton.title);
        binding.methodLabel.setVisibility(View.GONE);
        binding.textOption.setVisibility(View.GONE);
        binding.urlOption.setVisibility(View.GONE);
        binding.importOption.setVisibility(View.GONE);
        binding.urlPanel.setVisibility(View.GONE);
        binding.scriptSettingsPanel.setVisibility(View.VISIBLE);
        binding.scriptButtonPanel.setVisibility(View.VISIBLE);
        binding.buttonEnabled.setChecked(sourceButton == null || sourceButton.enabled);
        shortCode = sourceButton == null ? "" : value(sourceButton.content);
        longCode = sourceButton == null ? "" : value(sourceButton.longPressContent);
        startupCode = sourceButton == null ? "" : value(sourceButton.onStartup);
        if (triggerId == 0) triggerId = R.id.triggerClick;
        binding.triggerGroup.check(triggerId);
        binding.scriptCode.setText(shortCode);
        binding.scriptCode.setSelection(binding.scriptCode.length());
        updateScriptStats(shortCode);
        if (Util.isLeanback()) {
            tvFocusable(binding.close);
            tvFocusable(binding.name);
            tvFocusable(binding.buttonEnabled);
            tvFocusable(binding.triggerClick);
            tvFocusable(binding.triggerLong);
            tvFocusable(binding.triggerStartup);
            tvFocusable(binding.scriptCode);
            tvFocusable(binding.buttonCancel);
            tvFocusable(binding.buttonSave);
            binding.close.setNextFocusDownId(R.id.name);
            binding.name.setNextFocusUpId(R.id.close);
            binding.name.setNextFocusDownId(R.id.buttonEnabled);
            binding.buttonEnabled.setNextFocusUpId(R.id.name);
            binding.buttonEnabled.setNextFocusDownId(R.id.triggerClick);
            binding.triggerClick.setNextFocusUpId(R.id.buttonEnabled);
            binding.triggerClick.setNextFocusDownId(R.id.scriptCode);
            binding.triggerLong.setNextFocusDownId(R.id.scriptCode);
            binding.triggerStartup.setNextFocusDownId(R.id.scriptCode);
            binding.scriptCode.setNextFocusUpId(R.id.triggerClick);
            binding.scriptCode.setNextFocusDownId(R.id.buttonCancel);
            binding.buttonCancel.setNextFocusUpId(R.id.scriptCode);
            binding.buttonCancel.setNextFocusRightId(R.id.buttonSave);
            binding.buttonSave.setNextFocusLeftId(R.id.buttonCancel);
        }
    }

    private void setupScriptSettings() {
        binding.createTitle.setText(R.string.mpv_config_script_settings_title);
        binding.chooseAction.setText(R.string.mpv_config_script_settings_desc);
        binding.chooseAction.setVisibility(View.VISIBLE);
        binding.nameLabel.setText(R.string.mpv_config_custom_button_name);
        binding.name.setHint(R.string.mpv_config_custom_button_name_hint);
        binding.name.setText(sourceButton == null ? scriptButtonDefaultName(scriptId) : sourceButton.title);
        binding.methodLabel.setVisibility(View.GONE);
        binding.textOption.setVisibility(View.GONE);
        binding.urlOption.setVisibility(View.GONE);
        binding.importOption.setVisibility(View.GONE);
        binding.urlPanel.setVisibility(View.GONE);
        binding.scriptSettingsPanel.setVisibility(View.VISIBLE);
        binding.scriptButtonPanel.setVisibility(View.VISIBLE);
        binding.scriptCode.setVisibility(View.GONE);
        binding.scriptStats.setVisibility(View.GONE);
        binding.scriptEdit.setVisibility(View.VISIBLE);
        binding.scriptEnabledRow.setVisibility(View.VISIBLE);
        binding.scriptEnabled.setChecked(sourceButton == null || sourceButton.scriptEnabled);
        binding.buttonEnabled.setChecked(sourceButton != null && sourceButton.enabled);
        triggerId = triggerIdFor(MpvConfigStore.normalizeScriptTrigger(binding.buttonEnabled.isChecked(),
                sourceButton == null ? null : sourceButton.trigger));
        binding.triggerGroup.check(triggerId);
        if (Util.isLeanback()) {
            tvFocusable(binding.close);
            tvFocusable(binding.name);
            tvFocusable(binding.scriptEnabled);
            tvFocusable(binding.buttonEnabled);
            tvFocusable(binding.triggerClick);
            tvFocusable(binding.triggerLong);
            tvFocusable(binding.triggerStartup);
            tvFocusable(binding.scriptEdit);
            tvFocusable(binding.buttonCancel);
            tvFocusable(binding.buttonSave);
            binding.close.setNextFocusDownId(R.id.name);
            binding.name.setNextFocusUpId(R.id.close);
            binding.name.setNextFocusDownId(R.id.scriptEnabled);
            binding.scriptEnabled.setNextFocusUpId(R.id.name);
            binding.scriptEnabled.setNextFocusDownId(R.id.buttonEnabled);
            binding.buttonEnabled.setNextFocusUpId(R.id.scriptEnabled);
            binding.buttonEnabled.setNextFocusDownId(R.id.triggerClick);
            binding.triggerClick.setNextFocusUpId(R.id.buttonEnabled);
            binding.triggerClick.setNextFocusDownId(R.id.scriptEdit);
            binding.triggerLong.setNextFocusDownId(R.id.scriptEdit);
            binding.triggerStartup.setNextFocusDownId(R.id.scriptEdit);
            binding.scriptEdit.setNextFocusUpId(R.id.triggerClick);
            binding.scriptEdit.setNextFocusDownId(R.id.buttonCancel);
            binding.buttonCancel.setNextFocusUpId(R.id.scriptEdit);
            binding.buttonCancel.setNextFocusRightId(R.id.buttonSave);
            binding.buttonSave.setNextFocusLeftId(R.id.buttonCancel);
        }
    }

    private void setupScriptCreation() {
        binding.createTitle.setText(R.string.mpv_config_script_new);
        binding.chooseAction.setText(R.string.mpv_config_script_create_desc);
        binding.nameLabel.setText(R.string.mpv_config_name_optional);
        binding.name.setHint(R.string.mpv_config_custom_button_name_hint);
        binding.textOptionTitle.setText(R.string.mpv_config_script_text_edit);
        binding.textOptionDesc.setText(R.string.mpv_config_script_text_edit_desc);
        binding.urlOptionTitle.setText(R.string.mpv_config_script_import_url);
        binding.urlOptionDesc.setText(R.string.mpv_config_script_import_url_desc);
        binding.importOptionTitle.setText(R.string.mpv_config_script_import_file);
        binding.importOptionDesc.setText(R.string.mpv_config_script_import_file_desc);
        shortCode = "";
        longCode = "";
        startupCode = "";
        triggerId = R.id.triggerClick;
        binding.buttonEnabled.setChecked(true);
        binding.triggerGroup.check(triggerId);
        showScriptCreation();
        setupTvFocus();
    }

    private static String scriptButtonDefaultName(String scriptId) {
        if (TextUtils.isEmpty(scriptId)) return "";
        if (scriptId.regionMatches(true, scriptId.length() - 4, ".lua", 0, 4)
                || scriptId.regionMatches(true, scriptId.length() - 3, ".js", 0, 3)) {
            return scriptId.substring(0, scriptId.lastIndexOf('.'));
        }
        return scriptId;
    }

    private void showScriptCreation() {
        scriptButtonMode = false;
        binding.createTitle.setText(R.string.mpv_config_script_new);
        binding.chooseAction.setVisibility(View.VISIBLE);
        binding.nameLabel.setVisibility(View.VISIBLE);
        binding.nameLayout.setVisibility(View.VISIBLE);
        binding.scriptSettingsPanel.setVisibility(View.VISIBLE);
        binding.buttonEnabled.setVisibility(View.VISIBLE);
        binding.methodLabel.setVisibility(View.VISIBLE);
        binding.textOption.setVisibility(View.VISIBLE);
        binding.urlOption.setVisibility(View.VISIBLE);
        binding.importOption.setVisibility(View.VISIBLE);
        binding.urlPanel.setVisibility(View.GONE);
        binding.scriptButtonPanel.setVisibility(View.GONE);
    }

    private void enterScriptButtonEditor() {
        scriptButtonMode = true;
        setupScriptButton();
        binding.name.requestFocus();
    }

    private void onTriggerChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
        if (!MpvConfigStore.TARGET_SCRIPTS.equals(target) || !isChecked || checkedId == triggerId) return;
        if (!binding.buttonEnabled.isChecked() && checkedId != R.id.triggerStartup) {
            group.check(R.id.triggerStartup);
            return;
        }
        if (scriptButtonMode) saveCurrentCode();
        triggerId = checkedId;
        if (scriptButtonMode) {
            binding.scriptCode.setText(codeForTrigger(checkedId));
            binding.scriptCode.setSelection(binding.scriptCode.length());
        }
        updateScriptTriggerFocus();
    }

    private void updateScriptTrigger(boolean enabled, int selectedId) {
        // Some Material versions ignore setChecked on disabled buttons.
        binding.triggerClick.setEnabled(true);
        binding.triggerLong.setEnabled(true);
        binding.triggerGroup.check(enabled ? selectedId : R.id.triggerStartup);
        binding.triggerClick.setEnabled(enabled);
        binding.triggerLong.setEnabled(enabled);
        updateScriptTriggerFocus();
    }

    private void updateScriptTriggerFocus() {
        if (!Util.isLeanback()) return;
        boolean enabled = binding.buttonEnabled.isChecked();
        binding.triggerClick.setFocusable(enabled);
        binding.triggerLong.setFocusable(enabled);
        binding.buttonEnabled.setNextFocusDownId(triggerId);
        binding.triggerClick.setNextFocusUpId(R.id.buttonEnabled);
        binding.triggerLong.setNextFocusUpId(R.id.buttonEnabled);
        binding.triggerStartup.setNextFocusUpId(R.id.buttonEnabled);
        binding.textOption.setNextFocusUpId(triggerId);
        binding.scriptCode.setNextFocusUpId(triggerId);
        binding.scriptEdit.setNextFocusUpId(triggerId);
    }

    private void saveCurrentCode() {
        if (binding == null || binding.scriptCode == null) return;
        String code = value(binding.scriptCode);
        if (triggerId == R.id.triggerLong) longCode = code;
        else if (triggerId == R.id.triggerStartup) startupCode = code;
        else shortCode = code;
    }

    private void updateScriptStats(String text) {
        if (binding == null || binding.scriptStats == null) return;
        int lines = text.isEmpty() ? 1 : text.split("\\r?\\n", -1).length;
        binding.scriptStats.setText(getString(R.string.mpv_config_stats, lines, text.length()));
    }

    private String codeForTrigger(int id) {
        if (id == R.id.triggerLong) return longCode;
        if (id == R.id.triggerStartup) return startupCode;
        return shortCode;
    }

    private void saveScriptButton() {
        saveCurrentCode();
        try {
            MpvConfigStore.saveCustomButton(sourceButton == null ? "" : sourceButton.id, name(), shortCode, longCode, startupCode, binding.buttonEnabled.isChecked());
            Notify.show(R.string.mpv_config_custom_button_saved);
            if (buttonCallback != null) buttonCallback.run();
            dismissAllowingStateLoss();
        } catch (Throwable error) {
            Notify.show(message(error));
        }
    }

    private void openScriptEditor() {
        MpvConfigEditorDialog.show(getChildFragmentManager(), name(), scriptContent, false, text -> {
            scriptContent = text == null ? "" : text;
            return true;
        });
    }

    private void saveScriptSettings() {
        try {
            String savedId = MpvConfigStore.saveScriptSettings(scriptId, name(), scriptContent,
                    binding.buttonEnabled.isChecked(), triggerName(triggerId), binding.scriptEnabled.isChecked());
            if (buttonCallback != null) buttonCallback.run();
            Notify.show(R.string.mpv_config_profile_saved);
            dismissAllowingStateLoss();
        } catch (Throwable error) {
            Notify.show(message(error));
        }
    }

    private static int triggerIdFor(String trigger) {
        if ("long".equals(trigger)) return R.id.triggerLong;
        if ("startup".equals(trigger)) return R.id.triggerStartup;
        return R.id.triggerClick;
    }

    private static String triggerName(int id) {
        if (id == R.id.triggerLong) return "long";
        if (id == R.id.triggerStartup) return "startup";
        return "click";
    }

    private static void tvFocusable(View view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
    }

    private String name() {
        return binding.name.getText() == null ? "" : binding.name.getText().toString().trim();
    }

    private void createText() {
        String name = name();
        boolean enabled = binding.buttonEnabled.isChecked();
        String trigger = triggerName(triggerId);
        dismissAllowingStateLoss();
        App.post(() -> {
            if (listener != null) listener.onText(name, enabled, trigger);
        });
    }

    private void chooseFile() {
        String mime = MpvConfigStore.TARGET_SCRIPTS.equals(target) ? "application/octet-stream" : "text/*";
        FileChooser.from(launcher).show(mime, new String[]{"text/*", "application/octet-stream", "*/*"});
    }

    private void showUrlInput() {
        binding.scriptSettingsPanel.setVisibility(View.GONE);
        binding.chooseAction.setVisibility(View.GONE);
        binding.textOption.setVisibility(View.GONE);
        binding.urlOption.setVisibility(View.GONE);
        binding.importOption.setVisibility(View.GONE);
        binding.urlPanel.setVisibility(View.VISIBLE);
        binding.url.post(() -> binding.url.requestFocus());
    }

    private void showOptions() {
        binding.urlLayout.setError(null);
        binding.urlPanel.setVisibility(View.GONE);
        binding.chooseAction.setVisibility(View.VISIBLE);
        if (MpvConfigStore.TARGET_SCRIPTS.equals(target)) binding.scriptSettingsPanel.setVisibility(View.VISIBLE);
        binding.textOption.setVisibility(View.VISIBLE);
        binding.urlOption.setVisibility(View.VISIBLE);
        binding.importOption.setVisibility(View.VISIBLE);
        binding.urlOption.requestFocus();
    }

    private void importUrl() {
        String url = binding.url.getText() == null ? "" : binding.url.getText().toString().trim();
        if (!isHttpUrl(url)) {
            binding.urlLayout.setError(getString(R.string.mpv_config_url_invalid));
            binding.url.requestFocus();
            return;
        }
        binding.urlLayout.setError(null);
        String name = name();
        boolean enabled = binding.buttonEnabled.isChecked();
        String trigger = triggerName(triggerId);
        dismissAllowingStateLoss();
        App.post(() -> {
            if (listener != null) listener.onImport(name, url, enabled, trigger);
        });
    }

    private static boolean isHttpUrl(String value) {
        return !TextUtils.isEmpty(value) && (value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8));
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) {
            Notify.show(R.string.mpv_config_file_invalid);
            return;
        }
        String name = binding == null ? "" : name();
        boolean enabled = binding != null && binding.buttonEnabled.isChecked();
        String trigger = triggerName(triggerId);
        dismissAllowingStateLoss();
        App.post(() -> {
            if (listener != null) listener.onImport(name, path, enabled, trigger);
        });
    });

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        boolean land = ResUtil.isLand(requireContext());
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = Math.min((int) (ResUtil.getScreenWidth(requireContext()) * (land ? 0.56f : 0.94f)), ResUtil.dp2px(620));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
        if (Util.isLeanback()) {
            if (scriptButtonMode || scriptSettingsMode) binding.name.post(() -> binding.name.requestFocus());
            else binding.textOption.post(() -> binding.textOption.requestFocus());
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String value(TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString();
    }

    private static String message(Throwable error) {
        return TextUtils.isEmpty(error.getMessage()) ? error.getClass().getSimpleName() : error.getMessage();
    }
}
