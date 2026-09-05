package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogWebHomeExtensionBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.SettingClipboardOverlay;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.web.HomeWebController;
import com.fongmi.android.tv.web.ext.WebHomeExtension;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.fongmi.android.tv.web.ext.WebHomeExtensionSourceStore;
import com.github.catvod.utils.Path;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class WebHomeExtensionDialog extends BaseAlertDialog {

    private DialogWebHomeExtensionBinding binding;
    private Runnable callback;
    private boolean enabled;
    private boolean textMode;
    private boolean editMode;
    private boolean reverseOrder;
    private WebHomeExtensionSourceStore.Entry editingSource;
    private SourceEditor editingEditor;
    private WebHomeExtensionSourceStore.Entry pendingFileEdit;
    private SourceEditor pendingFileEditor;
    private final List<SourceEditor> editors = new ArrayList<>();
    private SettingClipboardOverlay clipboardOverlay;
    private String pendingScrollSourceId;

    public static void show(Fragment fragment, Runnable callback) {
        WebHomeExtensionDialog dialog = new WebHomeExtensionDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        WebHomeExtensionDialog dialog = new WebHomeExtensionDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogWebHomeExtensionBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        setCancelable(false);
        getDialog().setCanceledOnTouchOutside(false);
        Window window = getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int screenHeight = ResUtil.getScreenHeight(requireContext());
        boolean land = ResUtil.isLand(requireContext());
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (screenWidth * (land ? 0.72f : 0.94f));
        params.height = land ? (int) (screenHeight * 0.94f) : WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        ViewGroup.LayoutParams rootParams = binding.root.getLayoutParams();
        rootParams.height = land ? params.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.root.setLayoutParams(rootParams);
        LinearLayoutCompat.LayoutParams scrollParams = (LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams();
        scrollParams.height = land ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
        scrollParams.weight = land ? 1 : 0;
        binding.contentScroll.setLayoutParams(scrollParams);
        binding.contentScroll.setMaxHeight(land ? 0 : (int) (screenHeight * 0.54f));
        binding.enabled.requestFocus();
        if (clipboardOverlay == null) clipboardOverlay = SettingClipboardOverlay.attach(this, binding.getRoot());
    }

    @Override
    public void onDestroyView() {
        if (clipboardOverlay != null) clipboardOverlay.detach();
        clipboardOverlay = null;
        binding = null;
        editors.clear();
        editingSource = null;
        editingEditor = null;
        pendingFileEdit = null;
        pendingFileEditor = null;
        pendingScrollSourceId = null;
        super.onDestroyView();
    }

    @Override
    protected void initView() {
        enabled = Setting.isWebHomeExtension();
        updateEnabledText();
        updateReverseText();
        binding.modeGroup.check(R.id.uiMode);
        setupScrollableText(binding.jsonText);
        showTextMode(false);
        render();
        showList();
        refresh(false);
    }

    @Override
    protected void initEvent() {
        binding.enabled.setOnClickListener(view -> {
            enabled = !enabled;
            updateEnabledText();
            render();
        });
        binding.reverse.setOnClickListener(view -> {
            reverseOrder = !reverseOrder;
            updateReverseText();
            render();
        });
        binding.add.setOnClickListener(view -> addDefaultSource());
        binding.debug.setOnClickListener(view -> WebHomeExtensionDebugDialog.show(requireActivity(), callback));
        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.textMode) showTextMode(true);
            if (checkedId == R.id.uiMode && !showTextMode(false)) binding.modeGroup.check(R.id.textMode);
        });
        binding.clear.setOnClickListener(view -> clearCache());
        binding.negative.setOnClickListener(view -> {
            if (editMode) showList();
            else dismiss();
        });
        binding.positive.setOnClickListener(view -> onPositive());
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        if (callback != null) callback.run();
        super.onCancel(dialog);
    }

    private void updateEnabledText() {
        binding.enabled.setText(enabled ? R.string.setting_enable : R.string.setting_disable);
        binding.enabled.setAlpha(enabled ? 1.0f : 0.65f);
    }

    private void updateReverseText() {
        binding.reverse.setText(reverseOrder ? R.string.setting_order_normal : R.string.setting_order_reverse);
    }

    private void refresh(boolean manual) {
        if (!canRender()) return;
        if (manual) binding.summary.setText(R.string.update_check);
        WebHomeExtensionRegistry.get().refresh(VodConfig.get().getHome(), () -> {
            if (!canRender()) return;
            render();
            if (callback != null) callback.run();
        });
    }

    private void clearCache() {
        WebHomeExtensionRegistry.get().clear();
        HomeWebController.requestExtensionReload();
        Notify.show(R.string.web_home_extension_clear_done);
        refresh(false);
    }

    private boolean showTextMode(boolean text) {
        if (text == textMode) {
            updateModeVisibility();
            return true;
        }
        if (text) {
            try {
                binding.jsonText.setText(currentSourcesJson());
            } catch (Throwable e) {
                Notify.show(errorText(e));
                return false;
            }
        } else if (!saveTextSources(true)) {
            return false;
        }
        textMode = text;
        updateModeVisibility();
        return true;
    }

    private void updateModeVisibility() {
        binding.contentScroll.setVisibility(textMode ? View.GONE : View.VISIBLE);
        binding.jsonLayout.setVisibility(textMode ? View.VISIBLE : View.GONE);
        binding.add.setVisibility(textMode || editMode ? View.GONE : View.VISIBLE);
        binding.debug.setVisibility(editMode ? View.GONE : View.VISIBLE);
        binding.clear.setVisibility(editMode ? View.GONE : View.VISIBLE);
        binding.enabled.setVisibility(editMode ? View.GONE : View.VISIBLE);
        binding.reverse.setVisibility(editMode ? View.GONE : View.VISIBLE);
        binding.modeGroup.setVisibility(editMode ? View.GONE : View.VISIBLE);
    }

    private boolean saveTextSources(boolean notify) {
        try {
            WebHomeExtensionSourceStore.saveRawJson(inputText(binding.jsonText));
            onSourceSaved(false);
            return true;
        } catch (Throwable e) {
            if (notify) Notify.show(R.string.web_home_extension_source_invalid);
            return false;
        }
    }

    private void onPositive() {
        if (editMode) {
            saveEditingSource(true);
            return;
        }
        if (textMode && !saveTextSources(true)) return;
        if (!textMode && !saveFormSources(true)) return;
        boolean changed = Setting.isWebHomeExtension() != enabled;
        Setting.putWebHomeExtension(enabled);
        if (changed) {
            WebHomeExtensionRegistry.get().refresh(VodConfig.get().getHome(), null);
            HomeWebController.requestExtensionReload();
        }
        if (callback != null) callback.run();
        dismiss();
    }

    private void render() {
        if (!canRender()) return;
        if (editMode) return;
        WebHomeExtensionRegistry.Snapshot snapshot = WebHomeExtensionRegistry.get().snapshot();
        List<RowModel> rows = rows(snapshot);
        String siteKey = TextUtils.isEmpty(snapshot.siteKey) ? getString(R.string.web_home_extension_unknown_site) : snapshot.siteKey;
        binding.summary.setText(getString(R.string.web_home_extension_summary, snapshot.sourceCount, snapshot.installedCount, snapshot.matchedCount, snapshot.readyCount, siteKey));
        trimRows();
        editors.clear();
        binding.empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        View target = null;
        for (int i = 0; i < rows.size(); i++) {
            int index = reverseOrder ? rows.size() - 1 - i : i;
            RowModel row = rows.get(index);
            View view = row(row, index);
            binding.list.addView(view);
            if (!TextUtils.isEmpty(pendingScrollSourceId) && row.source != null && TextUtils.equals(row.source.getId(), pendingScrollSourceId)) target = view;
        }
        if (target != null) {
            View focusTarget = target;
            pendingScrollSourceId = null;
            binding.contentScroll.post(() -> {
                binding.contentScroll.smoothScrollTo(0, focusTarget.getTop());
                focusTarget.requestFocus();
            });
        }
    }

    private void showList() {
        editMode = false;
        editingSource = null;
        editingEditor = null;
        editors.clear();
        binding.list.removeAllViews();
        binding.list.addView(binding.empty);
        updateModeVisibility();
        binding.negative.setText(R.string.dialog_negative);
        binding.positive.setText(R.string.dialog_positive);
        render();
        binding.add.requestFocus();
    }

    private void showEdit(WebHomeExtensionSourceStore.Entry source) {
        editMode = true;
        editingSource = source;
        editors.clear();
        binding.list.removeAllViews();
        binding.empty.setVisibility(View.GONE);
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.setBackground(rowBackground(false));
        binding.list.addView(root, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayoutCompat header = new LinearLayoutCompat(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayoutCompat.HORIZONTAL);
        root.addView(header);
        header.addView(text(getString(R.string.web_home_extension_edit_source), 16, Color.BLACK, true), new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton toggle = sourceActionButton(source.isEnabled() ? R.string.setting_enable : R.string.setting_disable, true, false);
        toggle.setOnClickListener(view -> {
            source.setDraftEnabled(!source.isEnabled());
            toggle.setText(source.isEnabled() ? R.string.setting_enable : R.string.setting_disable);
            toggle.setAlpha(source.isEnabled() ? 1.0f : 0.65f);
        });
        toggle.setAlpha(source.isEnabled() ? 1.0f : 0.65f);
        header.addView(toggle, new LinearLayoutCompat.LayoutParams(dp(84), dp(36)));
        addSourceForm(root, source);
        editingEditor = editors.isEmpty() ? null : editors.get(0);
        updateModeVisibility();
        binding.negative.setText(R.string.playback_webhook_back);
        binding.positive.setText(R.string.playback_webhook_save);
        binding.contentScroll.scrollTo(0, 0);
        root.requestFocus();
    }

    private List<RowModel> rows(WebHomeExtensionRegistry.Snapshot snapshot) {
        List<RowModel> rows = new ArrayList<>();
        for (WebHomeExtensionSourceStore.Entry source : WebHomeExtensionSourceStore.list()) {
            WebHomeExtensionRegistry.Item matched = findBySource(snapshot.items, source.getRaw());
            rows.add(new RowModel(source, matched));
        }
        for (WebHomeExtensionRegistry.Item item : snapshot.items) {
            if (findByItem(rows, item)) continue;
            rows.add(new RowModel(null, item));
        }
        return rows;
    }

    private WebHomeExtensionRegistry.Item findBySource(List<WebHomeExtensionRegistry.Item> items, String raw) {
        for (WebHomeExtensionRegistry.Item item : items) if (matchesRaw(item, raw)) return item;
        return null;
    }

    private boolean findByItem(List<RowModel> rows, WebHomeExtensionRegistry.Item item) {
        for (RowModel row : rows) if (row.item == item) return true;
        return false;
    }

    private boolean matchesRaw(WebHomeExtensionRegistry.Item item, String raw) {
        if (item == null || TextUtils.isEmpty(raw)) return false;
        if (!TextUtils.isEmpty(item.sourceUrl) && raw.contains(item.sourceUrl)) return true;
        if (raw.contains("\"id\"") && raw.contains(item.id)) return true;
        if (raw.contains("\"name\"") && raw.contains(item.name)) return true;
        return false;
    }

    private void trimRows() {
        while (binding.list.getChildCount() > 1) binding.list.removeViewAt(1);
    }

    private View row(RowModel model, int position) {
        WebHomeExtensionSourceStore.Entry source = model.source;
        WebHomeExtensionRegistry.Item item = model.item;
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        boolean rowEnabled = model.enabled();
        root.setBackground(rowBackground(!rowEnabled));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        LinearLayoutCompat.LayoutParams rootParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rootParams.topMargin = dp(8);
        root.setLayoutParams(rootParams);

        MaterialTextView title = text((position + 1) + ". " + model.name(), 15, titleColor(rowEnabled), true);
        root.addView(title);
        MaterialTextView status = text(model.status(), 12, statusColor(model.statusKey()), false);
        root.addView(status);
        if (source == null) addDetail(root, getString(R.string.web_home_extension_source, shortText(model.source())), !rowEnabled);
        if (source == null && item != null) {
            addDetail(root, getString(R.string.web_home_extension_match, empty(item.matchText)), !rowEnabled);
            if (!TextUtils.isEmpty(item.excludeText)) addDetail(root, getString(R.string.web_home_extension_exclude, item.excludeText), !rowEnabled);
            if (!TextUtils.isEmpty(item.dependsText)) addDetail(root, getString(R.string.web_home_extension_depends, item.dependsText), !rowEnabled);
            if (!TextUtils.isEmpty(item.reason)) addDetail(root, item.reason, !rowEnabled);
            if (!TextUtils.isEmpty(item.lastLog)) addDetail(root, getString(R.string.web_home_extension_last_log, item.lastLog), !rowEnabled);
        }
        if (source != null) addSourceDetails(root, source, item, !rowEnabled);

        LinearLayoutCompat actions = new LinearLayoutCompat(requireContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayoutCompat.HORIZONTAL);
        LinearLayoutCompat.LayoutParams actionParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(7);
        root.addView(actions, actionParams);

        MaterialButton toggle = sourceActionButton(rowEnabled ? R.string.setting_enable : R.string.setting_disable, rowEnabled, false);
        toggle.setEnabled(enabled);
        toggle.setAlpha(enabled ? 1.0f : 0.55f);
        toggle.setOnClickListener(view -> {
            if (source != null) {
                WebHomeExtensionSourceStore.setEnabled(source.getId(), !source.isEnabled());
                onSourceSaved();
            } else if (item != null) {
                toggle(item);
            }
        });
        actions.addView(toggle, actionLayout(0));

        if (source != null) {
            MaterialButton edit = sourceActionButton(R.string.dialog_edit, false, false);
            edit.setOnClickListener(view -> showEdit(source));
            actions.addView(edit, actionLayout(8));

            MaterialButton delete = sourceActionButton(R.string.setting_delete, false, true);
            delete.setOnClickListener(view -> deleteSource(source));
            actions.addView(delete, actionLayout(8));
        }
        return root;
    }

    private void addSourceDetails(LinearLayoutCompat root, WebHomeExtensionSourceStore.Entry source, WebHomeExtensionRegistry.Item item, boolean disabled) {
        FormFields fields = formFields(source);
        addDetail(root, getString(R.string.web_home_extension_source_type) + ": " + sourceTypeName(fields.sourceType), disabled);
        String match = fields.regexMode || fields.siteKeys.isEmpty() ? fields.match : siteLabels(fields.siteKeys);
        if (!TextUtils.isEmpty(match)) addDetail(root, getString(R.string.web_home_extension_match, match), disabled);
        if (!TextUtils.isEmpty(fields.link)) addDetail(root, getString(R.string.web_home_extension_source, shortText(fields.link)), disabled);
        else if (!TextUtils.isEmpty(fields.code)) addDetail(root, getString(R.string.web_home_extension_source, shortText(fields.code)), disabled);
        if (item != null && !TextUtils.isEmpty(item.lastLog)) addDetail(root, getString(R.string.web_home_extension_last_log, item.lastLog), disabled);
    }

    private void addSourceForm(LinearLayoutCompat root, WebHomeExtensionSourceStore.Entry source) {
        FormFields fields = formFields(source);
        TextInputEditText name = createInput(false);
        TextInputEditText link = createInput(false);
        TextInputEditText match = createInput(false);
        TextInputEditText code = createInput(true);
        name.setText(fields.name);
        link.setText(fields.link);
        match.setText(fields.match);
        setupScrollableText(name);
        setupScrollableText(link);
        setupScrollableText(match);
        code.setMinLines(6);
        code.setMaxLines(12);
        code.setText(fields.code);
        setupScrollableText(code);
        LinearLayoutCompat form = new LinearLayoutCompat(requireContext());
        form.setOrientation(LinearLayoutCompat.VERTICAL);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        root.addView(form, params);

        form.addView(inputLayout(R.string.web_home_extension_name_hint, name));
        form.addView(text(getString(R.string.web_home_extension_match_mode), 12, Color.parseColor("#5F6368"), false), topMargin(8));
        MaterialButtonToggleGroup matchGroup = toggleGroup();
        int siteMatchId = View.generateViewId();
        int regexMatchId = View.generateViewId();
        matchGroup.addView(toggleButton(siteMatchId, R.string.web_home_extension_match_sites));
        matchGroup.addView(toggleButton(regexMatchId, R.string.web_home_extension_match_regex));
        matchGroup.check(fields.regexMode ? regexMatchId : siteMatchId);
        form.addView(matchGroup, topMargin(4));
        MaterialButton sitePicker = sourceActionButton(R.string.web_home_extension_choose_sites, false, false);
        sitePicker.setGravity(Gravity.CENTER_VERTICAL);
        form.addView(sitePicker, topMargin(8));
        TextInputLayout matchLayout = inputLayout(R.string.web_home_extension_match_hint, match);
        form.addView(matchLayout, topMargin(8));
        form.addView(text(getString(R.string.web_home_extension_run_at_hint), 12, Color.parseColor("#5F6368"), false), topMargin(8));
        MaterialButtonToggleGroup runAtGroup = toggleGroup();
        int startId = View.generateViewId();
        int endId = View.generateViewId();
        int idleId = View.generateViewId();
        runAtGroup.addView(toggleButton(startId, R.string.web_home_extension_run_at_start_short));
        runAtGroup.addView(toggleButton(endId, R.string.web_home_extension_run_at_end_short));
        runAtGroup.addView(toggleButton(idleId, R.string.web_home_extension_run_at_idle_short));
        runAtGroup.check(switch (fields.runAt) {
            case WebHomeExtension.RUN_AT_START -> startId;
            case WebHomeExtension.RUN_AT_IDLE -> idleId;
            default -> endId;
        });
        form.addView(runAtGroup, topMargin(4));

        MaterialButtonToggleGroup sourceGroup = toggleGroup();
        int fileId = View.generateViewId();
        int linkId = View.generateViewId();
        int codeId = View.generateViewId();
        MaterialButton fileButton = toggleButton(fileId, R.string.web_home_extension_add_file);
        MaterialButton linkButton = toggleButton(linkId, R.string.web_home_extension_add_link);
        MaterialButton codeButton = toggleButton(codeId, R.string.web_home_extension_add_code);
        sourceGroup.addView(fileButton);
        sourceGroup.addView(linkButton);
        sourceGroup.addView(codeButton);
        sourceGroup.check(switch (fields.sourceType) {
            case WebHomeExtensionSourceStore.SOURCE_TYPE_FILE -> fileId;
            case WebHomeExtensionSourceStore.SOURCE_TYPE_LINK -> linkId;
            default -> codeId;
        });
        form.addView(sourceGroup, topMargin(8));

        TextInputLayout linkLayout = inputLayout(R.string.web_home_extension_link_hint, link);
        TextInputLayout codeLayout = inputLayout(R.string.web_home_extension_code_hint, code);
        form.addView(linkLayout, topMargin(8));
        form.addView(codeLayout, topMargin(8));

        SourceEditor editor = new SourceEditor(source, sourceGroup, runAtGroup, matchGroup, fileId, linkId, codeId, startId, idleId, siteMatchId, regexMatchId, name, link, code, match, sitePicker, new ArrayList<>(fields.siteKeys));
        editors.add(editor);
        Runnable update = () -> {
            int checked = sourceGroup.getCheckedButtonId();
            boolean linkSource = checked == linkId;
            linkLayout.setVisibility(linkSource ? View.VISIBLE : View.GONE);
            codeLayout.setVisibility(checked == codeId ? View.VISIBLE : View.GONE);
        };
        Runnable updateMatch = () -> {
            boolean regex = matchGroup.getCheckedButtonId() == regexMatchId;
            sitePicker.setVisibility(regex ? View.GONE : View.VISIBLE);
            matchLayout.setVisibility(regex ? View.VISIBLE : View.GONE);
            updateSitePickerText(editor);
        };
        sourceGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            update.run();
        });
        matchGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            updateMatch.run();
        });
        fileButton.setOnClickListener(view -> chooseFile(editor));
        sitePicker.setOnClickListener(view -> showSitePicker(editor));
        update.run();
        updateMatch.run();
    }

    private LinearLayoutCompat.LayoutParams topMargin(int value) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(value);
        return params;
    }

    private boolean saveFormSources(boolean notify) {
        try {
            for (SourceEditor editor : editors) saveFormEditor(editor);
            onSourceSaved(false);
            return true;
        } catch (Throwable e) {
            if (notify) Notify.show(errorText(e));
            return false;
        }
    }

    private boolean saveEditingSource(boolean notify) {
        try {
            if (editingEditor == null) return false;
            List<String> before = sourceIds();
            String id = editingSource == null ? "" : editingSource.getId();
            saveFormEditor(editingEditor);
            pendingScrollSourceId = TextUtils.isEmpty(id) ? findNewSourceId(before) : id;
            onSourceSaved(false);
            if (notify) Notify.show(R.string.web_home_extension_source_saved);
            showList();
            return true;
        } catch (Throwable e) {
            if (notify) Notify.show(errorText(e));
            return false;
        }
    }

    private String currentFormSourcesJson() {
        JsonArray array = new JsonArray();
        for (SourceEditor editor : editors) array.add(sourceJson(editor));
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(array);
    }

    private String currentSourcesJson() {
        JsonArray array = new JsonArray();
        for (WebHomeExtensionSourceStore.Entry source : WebHomeExtensionSourceStore.list()) array.add(sourceJson(source));
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(array);
    }

    private JsonObject sourceJson(SourceEditor editor) {
        WebHomeExtensionSourceStore.Entry source = editor.source;
        JsonObject object = new JsonObject();
        object.addProperty("id", source.getId());
        object.addProperty("name", inputText(editor.name, source.getName()));
        object.addProperty("raw", formRaw(editor));
        object.addProperty("siteKey", source.getSiteKey());
        object.addProperty("enabled", source.isEnabled());
        object.addProperty("updatedAt", source.getUpdatedAt());
        return object;
    }

    private JsonObject sourceJson(WebHomeExtensionSourceStore.Entry source) {
        JsonObject object = new JsonObject();
        object.addProperty("id", source.getId());
        object.addProperty("name", source.getName());
        object.addProperty("raw", source.getRaw());
        object.addProperty("siteKey", source.getSiteKey());
        object.addProperty("enabled", source.isEnabled());
        object.addProperty("updatedAt", source.getUpdatedAt());
        return object;
    }

    private String formRaw(SourceEditor editor) {
        WebHomeExtensionSourceStore.Entry source = editor.source;
        String sourceType = sourceType(editor.sourceGroup, editor.fileId, editor.linkId);
        String name = inputText(editor.name);
        String runAt = runAt(editor.runAtGroup, editor.startId, editor.idleId);
        String match = matchValue(editor);
        try {
            if (WebHomeExtensionSourceStore.SOURCE_TYPE_LINK.equals(sourceType)) return WebHomeExtensionSourceStore.rawLink(source.getId(), name, runAt, inputText(editor.link), match);
            return WebHomeExtensionSourceStore.rawCode(source.getId(), name, runAt, match, inputText(editor.code), sourceType);
        } catch (Throwable e) {
            return source.getRaw();
        }
    }

    private boolean canRender() {
        return binding != null && isAdded() && getContext() != null;
    }

    private void saveFormEditor(SourceEditor editor) {
        WebHomeExtensionSourceStore.Entry source = editor.source;
        String sourceType = sourceType(editor.sourceGroup, editor.fileId, editor.linkId);
        String match = matchValue(editor);
        if (WebHomeExtensionSourceStore.SOURCE_TYPE_LINK.equals(sourceType)) WebHomeExtensionSourceStore.saveLink(source.getId(), inputText(editor.name), runAt(editor.runAtGroup, editor.startId, editor.idleId), inputText(editor.link), match, source.isEnabled(), "");
        else WebHomeExtensionSourceStore.saveCodeMeta(source.getId(), inputText(editor.name), runAt(editor.runAtGroup, editor.startId, editor.idleId), match, inputText(editor.code), source.isEnabled(), "", sourceType);
    }

    private void saveFormFields(WebHomeExtensionSourceStore.Entry source, String sourceType, TextInputEditText name, String runAt, TextInputEditText link, TextInputEditText code, TextInputEditText match) {
        try {
            if (WebHomeExtensionSourceStore.SOURCE_TYPE_LINK.equals(sourceType)) WebHomeExtensionSourceStore.saveLink(source.getId(), inputText(name), runAt, inputText(link), inputText(match), source.isEnabled(), currentSiteKey(source));
            else WebHomeExtensionSourceStore.saveCodeMeta(source.getId(), inputText(name), runAt, inputText(match), inputText(code), source.isEnabled(), currentSiteKey(source), sourceType);
            onSourceSaved();
        } catch (Throwable e) {
            Notify.show(errorText(e));
        }
    }

    private MaterialButtonToggleGroup toggleGroup() {
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(requireContext());
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        return group;
    }

    private MaterialButton toggleButton(int id, int text) {
        MaterialButton button = toggleButton(id);
        button.setText(text);
        return button;
    }

    private MaterialButton toggleButton(int id, String text) {
        MaterialButton button = toggleButton(id);
        button.setText(text);
        return button;
    }

    private MaterialButton toggleButton(int id) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setId(id);
        button.setMinWidth(0);
        button.setMinHeight(dp(38));
        button.setMinimumHeight(dp(38));
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_toggle_button_text));
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_toggle_button_bg));
        button.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_stroke));
        button.setStrokeWidth(dp(1));
        MaterialButtonToggleGroup.LayoutParams params = new MaterialButtonToggleGroup.LayoutParams(0, dp(38), 1);
        button.setLayoutParams(params);
        return button;
    }

    private String sourceType(MaterialButtonToggleGroup group, int fileId, int linkId) {
        int checked = group.getCheckedButtonId();
        if (checked == fileId) return WebHomeExtensionSourceStore.SOURCE_TYPE_FILE;
        if (checked == linkId) return WebHomeExtensionSourceStore.SOURCE_TYPE_LINK;
        return WebHomeExtensionSourceStore.SOURCE_TYPE_CODE;
    }

    private String runAt(MaterialButtonToggleGroup group, int startId, int idleId) {
        if (group == null) return WebHomeExtension.RUN_AT_END;
        int checked = group.getCheckedButtonId();
        if (checked == startId) return WebHomeExtension.RUN_AT_START;
        if (checked == idleId) return WebHomeExtension.RUN_AT_IDLE;
        return WebHomeExtension.RUN_AT_END;
    }

    private String matchValue(SourceEditor editor) {
        if (editor == null) return "";
        if (editor.matchGroup != null && editor.matchGroup.getCheckedButtonId() == editor.regexMatchId) return inputText(editor.match);
        List<String> matches = new ArrayList<>();
        for (String key : editor.siteKeys) {
            if (TextUtils.isEmpty(key)) continue;
            String regex = exactSiteRegex(key);
            if (!matches.contains(regex)) matches.add(regex);
        }
        return TextUtils.join("\n", matches);
    }

    private void showSitePicker(SourceEditor editor) {
        List<Site> sites = webHomeSites();
        if (sites.isEmpty()) {
            Notify.show(R.string.web_home_extension_no_webhome_sites);
            return;
        }
        String[] labels = new String[sites.size()];
        boolean[] checked = new boolean[sites.size()];
        for (int i = 0; i < sites.size(); i++) {
            Site site = sites.get(i);
            labels[i] = siteLabel(site);
            checked[i] = editor.siteKeys.contains(site.getKey());
        }
        ChoiceDialog.showMulti(this, R.string.web_home_extension_choose_sites, labels, checked, result -> {
            editor.siteKeys.clear();
            for (int i = 0; i < sites.size(); i++) if (result[i]) editor.siteKeys.add(sites.get(i).getKey());
            if (editor.siteKeys.isEmpty()) editor.siteKeys.add(VodConfig.get().getHome().getKey());
            updateSitePickerText(editor);
            syncMatchTextFromSites(editor);
        });
    }

    private void syncMatchTextFromSites(SourceEditor editor) {
        if (editor == null || editor.match == null) return;
        List<String> matches = new ArrayList<>();
        for (String key : editor.siteKeys) {
            if (TextUtils.isEmpty(key)) continue;
            String regex = exactSiteRegex(key);
            if (!matches.contains(regex)) matches.add(regex);
        }
        editor.match.setText(TextUtils.join("\n", matches));
    }

    private void updateSitePickerText(SourceEditor editor) {
        if (editor == null || editor.sitePicker == null) return;
        List<String> labels = new ArrayList<>();
        for (String key : editor.siteKeys) {
            Site site = findWebHomeSite(key);
            labels.add(site == null ? key : siteLabel(site));
        }
        editor.sitePicker.setText(labels.isEmpty() ? getString(R.string.web_home_extension_choose_sites) : TextUtils.join("、", labels));
    }

    private List<Site> webHomeSites() {
        List<Site> result = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (site == null || TextUtils.isEmpty(site.getKey()) || !site.hasHomePage()) continue;
            result.add(site);
        }
        return result;
    }

    private Site findWebHomeSite(String key) {
        for (Site site : webHomeSites()) if (TextUtils.equals(site.getKey(), key)) return site;
        return null;
    }

    private String siteLabel(Site site) {
        if (site == null) return "";
        String name = site.getDisplayName();
        String key = site.getKey();
        return TextUtils.isEmpty(name) || TextUtils.equals(name, key) ? key : name + " (" + key + ")";
    }

    private String siteLabels(List<String> keys) {
        List<String> labels = new ArrayList<>();
        for (String key : keys) {
            Site site = findWebHomeSite(key);
            labels.add(site == null ? key : siteLabel(site));
        }
        return TextUtils.join("、", labels);
    }

    private String exactSiteRegex(String key) {
        return TextUtils.isEmpty(key) ? "" : "^" + Pattern.quote(key) + "$";
    }

    private List<String> cspKeyRegex(JsonObject object) {
        List<String> result = new ArrayList<>();
        try {
            if (!object.has("cspKeyRegex")) return result;
            JsonElement element = object.get("cspKeyRegex");
            if (element.isJsonArray()) {
                for (JsonElement item : element.getAsJsonArray()) {
                    String value = item.getAsString().trim();
                    if (!TextUtils.isEmpty(value)) result.add(value);
                }
            } else if (element.isJsonPrimitive()) {
                String value = element.getAsString().trim();
                if (!TextUtils.isEmpty(value)) result.add(value);
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private List<String> siteKeysFromRegex(List<String> matches) {
        List<String> result = new ArrayList<>();
        for (String regex : matches) {
            String key = siteKeyFromRegex(regex);
            if (!TextUtils.isEmpty(key) && !result.contains(key)) result.add(key);
        }
        return result;
    }

    private String siteKeyFromRegex(String regex) {
        if (TextUtils.isEmpty(regex)) return "";
        for (Site site : webHomeSites()) {
            String key = site.getKey();
            if (TextUtils.equals(regex, key) || TextUtils.equals(regex, exactSiteRegex(key))) return key;
        }
        return "";
    }

    private LinearLayoutCompat.LayoutParams actionLayout(int marginStart) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(0, dp(36), 1);
        params.leftMargin = dp(marginStart);
        return params;
    }

    private MaterialButton sourceActionButton(int text, boolean tonal, boolean danger) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(text);
        button.setMinWidth(0);
        button.setMinHeight(dp(36));
        button.setMinimumHeight(dp(36));
        button.setPadding(dp(6), 0, dp(6), 0);
        ColorStateList bg = ContextCompat.getColorStateList(requireContext(), tonal ? R.color.dialog_tonal_button_bg : R.color.dialog_outlined_button_bg);
        ColorStateList fg = danger ? ColorStateList.valueOf(Color.parseColor("#B3261E")) : ContextCompat.getColorStateList(requireContext(), tonal ? R.color.dialog_tonal_button_text : R.color.dialog_outlined_button_text);
        button.setBackgroundTintList(bg);
        button.setTextColor(fg);
        if (!tonal) {
            button.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_stroke));
            button.setStrokeWidth(dp(1));
        }
        return button;
    }

    private void addDefaultSource() {
        String name = getString(R.string.web_home_extension_local_code_default, WebHomeExtensionSourceStore.list().size() + 1);
        WebHomeExtensionSourceStore.Entry source = WebHomeExtensionSourceStore.draft(name, WebHomeExtensionSourceStore.rawCode("", name, WebHomeExtension.RUN_AT_END, currentSiteKey(null), "GM_log('ready');\n", WebHomeExtensionSourceStore.SOURCE_TYPE_CODE), true, currentSiteKey(null));
        showEdit(source);
    }

    private void chooseFile(SourceEditor editor) {
        pendingFileEditor = editor;
        pendingFileEdit = editor.source;
        FileChooser.from(fileLauncher).show("text/*", new String[]{"text/*", "application/javascript", "application/octet-stream"});
    }

    private final ActivityResultLauncher<Intent> fileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            pendingFileEditor = null;
            pendingFileEdit = null;
            return;
        }
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) {
            pendingFileEditor = null;
            pendingFileEdit = null;
            return;
        }
        String code = Path.read(Path.local(path));
        if (TextUtils.isEmpty(code)) {
            Notify.show(R.string.web_home_extension_source_empty);
            pendingFileEditor = null;
            pendingFileEdit = null;
            return;
        }
        WebHomeExtensionSourceStore.Entry source = pendingFileEdit;
        SourceEditor editor = pendingFileEditor;
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (editMode && editor != null) {
            if (TextUtils.isEmpty(inputText(editor.name))) editor.name.setText(name);
            editor.code.setText(fileCode(name, code));
            pendingFileEditor = null;
            pendingFileEdit = null;
            return;
        }
        List<String> before = sourceIds();
        WebHomeExtensionSourceStore.saveCodeMeta(source == null ? "" : source.getId(), inputText(editor == null ? null : editor.name, name), runAt(editor == null ? null : editor.runAtGroup, editor == null ? 0 : editor.startId, editor == null ? 0 : editor.idleId), matchValue(editor), fileCode(name, code), source == null || source.isEnabled(), "", WebHomeExtensionSourceStore.SOURCE_TYPE_FILE);
        pendingScrollSourceId = source == null || TextUtils.isEmpty(source.getId()) ? findNewSourceId(before) : source.getId();
        pendingFileEditor = null;
        pendingFileEdit = null;
        onSourceSaved();
    });

    private List<String> sourceIds() {
        List<String> ids = new ArrayList<>();
        for (WebHomeExtensionSourceStore.Entry entry : WebHomeExtensionSourceStore.list()) ids.add(entry.getId());
        return ids;
    }

    private String findNewSourceId(List<String> before) {
        for (WebHomeExtensionSourceStore.Entry entry : WebHomeExtensionSourceStore.list()) {
            if (!before.contains(entry.getId())) return entry.getId();
        }
        return "";
    }

    private String fileCode(String name, String code) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith(".css")) return "GM_addStyle(" + App.gson().toJson(code) + ");";
        return code;
    }

    private FormFields formFields(WebHomeExtensionSourceStore.Entry source) {
        FormFields fields = new FormFields();
        fields.sourceType = WebHomeExtensionSourceStore.SOURCE_TYPE_CODE;
        fields.runAt = WebHomeExtension.RUN_AT_END;
        String current = VodConfig.get().getHome().getKey();
        fields.match = exactSiteRegex(current);
        fields.siteKeys.add(current);
        if (source == null) return fields;
        fields.name = source.getName();
        fields.sourceType = WebHomeExtensionSourceStore.sourceType(source);
        fields.code = WebHomeExtensionSourceStore.code(source);
        fields.link = WebHomeExtensionSourceStore.link(source);
        try {
            JsonElement element = WebHomeExtensionSourceStore.parse(source.getRaw());
            if (!element.isJsonObject()) {
                fields.sourceType = WebHomeExtensionSourceStore.SOURCE_TYPE_LINK;
                fields.link = source.getRaw();
                return fields;
            }
            JsonObject object = element.getAsJsonObject();
            fields.name = firstNonEmpty(safeString(object, "name"), source.getName());
            String value = safeString(object, "runAt");
            if (!TextUtils.isEmpty(value)) fields.runAt = value;
            List<String> matches = cspKeyRegex(object);
            if (!matches.isEmpty()) {
                fields.match = TextUtils.join("\n", matches);
                List<String> siteKeys = siteKeysFromRegex(matches);
                fields.regexMode = siteKeys.size() != matches.size();
                fields.siteKeys.clear();
                fields.siteKeys.addAll(siteKeys);
            }
        } catch (Throwable e) {
            fields.sourceType = WebHomeExtensionSourceStore.SOURCE_TYPE_LINK;
            fields.link = source.getRaw();
        }
        return fields;
    }

    private void deleteSource(WebHomeExtensionSourceStore.Entry source) {
        ChoiceDialog.showConfirm(this, R.string.web_home_extension_delete_source_title, getString(R.string.web_home_extension_delete_source_message, source.getName()), R.string.setting_delete, () -> {
            WebHomeExtensionSourceStore.remove(source.getId());
            onSourceSaved();
        });
    }

    private void onSourceSaved() {
        onSourceSaved(true);
    }

    private void onSourceSaved(boolean notify) {
        WebHomeExtensionRegistry.get().clear();
        HomeWebController.requestExtensionReload();
        refresh(false);
        if (notify) Notify.show(R.string.web_home_extension_source_saved);
    }

    private String sourceTypeName(String type) {
        if (WebHomeExtensionSourceStore.SOURCE_TYPE_FILE.equals(type)) return getString(R.string.web_home_extension_add_file);
        if (WebHomeExtensionSourceStore.SOURCE_TYPE_LINK.equals(type)) return getString(R.string.web_home_extension_add_link);
        return getString(R.string.web_home_extension_add_code);
    }

    private TextInputEditText createInput(boolean multiline) {
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setSelectAllOnFocus(false);
        input.setSingleLine(!multiline);
        input.setTextColor(Color.BLACK);
        input.setHintTextColor(Color.parseColor("#666666"));
        input.setInputType(multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setGravity(multiline ? Gravity.START | Gravity.TOP : Gravity.CENTER_VERTICAL);
        return input;
    }

    private TextInputLayout inputLayout(int hint, TextInputEditText input) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(hint);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(Color.WHITE);
        layout.setBoxStrokeColor(ResUtil.getColor(R.color.dialog_outlined_button_stroke));
        layout.setHintTextColor(ColorStateList.valueOf(Color.parseColor("#5F6368")));
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private void setupScrollableText(EditText input) {
        input.setHorizontallyScrolling(true);
        input.setHorizontalScrollBarEnabled(false);
        input.setVerticalScrollBarEnabled(false);
        input.setOverScrollMode(View.OVER_SCROLL_NEVER);
        input.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) view.post(() -> disallowParentIntercept(view, false));
            else disallowParentIntercept(view, true);
            return false;
        });
    }

    private void disallowParentIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    private String inputText(EditText input) {
        if (input == null) return "";
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String inputText(EditText input, String fallback) {
        String value = inputText(input);
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private String errorText(Throwable e) {
        return "empty".equals(e.getMessage()) ? getString(R.string.web_home_extension_source_empty) : getString(R.string.web_home_extension_source_invalid);
    }

    private String safeString(JsonObject object, String key) {
        try {
            return object.getAsJsonPrimitive(key).getAsString().trim();
        } catch (Throwable e) {
            return "";
        }
    }

    private String firstNonEmpty(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private String currentSiteKey(WebHomeExtensionSourceStore.Entry source) {
        if (source != null && !TextUtils.isEmpty(source.getSiteKey())) return source.getSiteKey();
        return VodConfig.get().getHome().getKey();
    }

    private void toggle(WebHomeExtensionRegistry.Item item) {
        if (item.enabled) {
            WebHomeExtensionRegistry.get().setExtensionEnabled(item.id, false);
            HomeWebController.requestExtensionReload();
            refresh(false);
        } else {
            confirmEnable(item);
        }
    }

    private void confirmEnable(WebHomeExtensionRegistry.Item item) {
        ChoiceDialog.showConfirm(this, R.string.web_home_extension_enable_confirm_title, getString(R.string.web_home_extension_enable_confirm_message, item.name, source(item), empty(item.matchText)), R.string.setting_enable, () -> {
            WebHomeExtensionRegistry.get().setExtensionEnabled(item.id, true);
            HomeWebController.requestExtensionReload();
            refresh(false);
        });
    }

    private void addDetail(LinearLayoutCompat root, String value) {
        addDetail(root, value, false);
    }

    private void addDetail(LinearLayoutCompat root, String value, boolean disabled) {
        MaterialTextView view = text(value, 12, detailColor(disabled), false);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(3);
        root.addView(view, params);
    }

    private MaterialTextView text(String value, int sp, int color, boolean bold) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setSingleLine(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Drawable rowBackground(boolean disabled) {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_focused}, rowShape("#E8F0FE", "#1A73E8", 2, 8));
        drawable.addState(new int[]{android.R.attr.state_pressed}, rowShape("#E8F0FE", "#1A73E8", 2, 8));
        drawable.addState(new int[]{}, rowShape(disabled ? "#ECEFF1" : "#F5F6F7", disabled ? "#D5D9DE" : "#F5F6F7", disabled ? 1 : 0, 6));
        return drawable;
    }

    private GradientDrawable rowShape(String color, String stroke, int strokeDp, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), Color.parseColor(stroke));
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int titleColor(boolean enabled) {
        return Color.parseColor(enabled ? "#202124" : "#8A8D91");
    }

    private int detailColor(boolean disabled) {
        return Color.parseColor(disabled ? "#9AA0A6" : "#5F6368");
    }

    private String statusText(WebHomeExtensionRegistry.Item item) {
        int resId = switch (item.status) {
            case "ready" -> R.string.web_home_extension_status_ready;
            case "injected" -> R.string.web_home_extension_status_injected;
            case "disabled" -> R.string.web_home_extension_status_disabled;
            case "unmatched" -> R.string.web_home_extension_status_unmatched;
            case "skipped" -> R.string.web_home_extension_status_skipped;
            case "matched" -> R.string.web_home_extension_status_matched;
            default -> item.enabled ? R.string.setting_enable : R.string.setting_disable;
        };
        return getString(resId);
    }

    private int statusColor(String status) {
        return switch (status) {
            case "ready", "injected", "matched" -> Color.parseColor("#137333");
            case "skipped" -> Color.parseColor("#B3261E");
            case "disabled", "unmatched" -> Color.parseColor("#6F7378");
            default -> Color.parseColor("#5F6368");
        };
    }

    private String source(WebHomeExtensionRegistry.Item item) {
        return TextUtils.isEmpty(item.sourceUrl) ? getString(R.string.web_home_extension_inline_source) : item.sourceUrl;
    }

    private String empty(String value) {
        return TextUtils.isEmpty(value) ? getString(R.string.none) : value;
    }

    private String shortText(String value) {
        if (TextUtils.isEmpty(value)) return getString(R.string.none);
        return value.length() <= 180 ? value : value.substring(0, 180) + "...";
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    private class RowModel {
        private final WebHomeExtensionSourceStore.Entry source;
        private final WebHomeExtensionRegistry.Item item;

        private RowModel(WebHomeExtensionSourceStore.Entry source, WebHomeExtensionRegistry.Item item) {
            this.source = source;
            this.item = item;
        }

        private String name() {
            if (item != null) return item.name + (TextUtils.isEmpty(item.version) ? "" : " " + item.version);
            return source == null ? "" : source.getName();
        }

        private String status() {
            String state = !enabled || !sourceEnabled() ? getString(R.string.setting_disable) : item != null ? statusText(item) : getString(R.string.setting_enable);
            String id = item != null ? item.id : source == null ? "" : source.getId();
            String runAt = item == null ? "" : " · " + item.runAt;
            return id + runAt + " · " + state;
        }

        private String statusKey() {
            if (!enabled || !sourceEnabled()) return "disabled";
            if (item != null) return item.status;
            return source != null && source.isEnabled() ? "matched" : "disabled";
        }

        private String source() {
            if (source != null) return source.getRaw();
            return item == null ? "" : WebHomeExtensionDialog.this.source(item);
        }

        private boolean enabled() {
            return WebHomeExtensionDialog.this.enabled && sourceEnabled();
        }

        private boolean sourceEnabled() {
            if (source != null) return source.isEnabled();
            return item != null && item.enabled;
        }
    }

    private static class FormFields {
        private String name = "";
        private String sourceType = "";
        private String runAt = "";
        private String link = "";
        private String code = "";
        private String match = "";
        private boolean regexMode;
        private final List<String> siteKeys = new ArrayList<>();
    }

    private static class SourceEditor {
        private final WebHomeExtensionSourceStore.Entry source;
        private final MaterialButtonToggleGroup sourceGroup;
        private final MaterialButtonToggleGroup runAtGroup;
        private final MaterialButtonToggleGroup matchGroup;
        private final int fileId;
        private final int linkId;
        private final int codeId;
        private final int startId;
        private final int idleId;
        private final int siteMatchId;
        private final int regexMatchId;
        private final TextInputEditText name;
        private final TextInputEditText link;
        private final TextInputEditText code;
        private final TextInputEditText match;
        private final MaterialButton sitePicker;
        private final List<String> siteKeys;

        private SourceEditor(WebHomeExtensionSourceStore.Entry source, MaterialButtonToggleGroup sourceGroup, MaterialButtonToggleGroup runAtGroup, MaterialButtonToggleGroup matchGroup, int fileId, int linkId, int codeId, int startId, int idleId, int siteMatchId, int regexMatchId, TextInputEditText name, TextInputEditText link, TextInputEditText code, TextInputEditText match, MaterialButton sitePicker, List<String> siteKeys) {
            this.source = source;
            this.sourceGroup = sourceGroup;
            this.runAtGroup = runAtGroup;
            this.matchGroup = matchGroup;
            this.fileId = fileId;
            this.linkId = linkId;
            this.codeId = codeId;
            this.startId = startId;
            this.idleId = idleId;
            this.siteMatchId = siteMatchId;
            this.regexMatchId = regexMatchId;
            this.name = name;
            this.link = link;
            this.code = code;
            this.match = match;
            this.sitePicker = sitePicker;
            this.siteKeys = siteKeys == null ? new ArrayList<>() : siteKeys;
        }
    }
}
