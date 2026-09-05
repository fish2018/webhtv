package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogPlaybackWebhookBinding;
import com.fongmi.android.tv.playback.PlaybackWebhookStore;
import com.fongmi.android.tv.playback.WebhookConfig;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PlaybackWebhookDialog extends BaseAlertDialog {

    private static final String[] FIELD_KEYS = new String[]{"cid", "configKey", "configName", "historyKey", "siteKey", "siteName", "vodId", "vodName", "vodPic", "flag", "episodeName", "state", "positionMs", "durationMs", "progress", "speed", "speedOverride", "completed", "episodeUrl", "episodeIndex", "appVersion", "client", "clientKey"};
    private static final List<String> PROTOCOL_FIELDS = Arrays.asList("schema", "event", "eventId", "timestamp", "scope", "deletedAt", "sessionId", "dedupeKey");
    private static final List<String> BASIC_FIELDS = Arrays.asList("schema", "event", "eventId", "timestamp", "scope", "deletedAt", "sessionId", "dedupeKey", "cid", "configKey", "configName", "historyKey", "siteKey", "siteName", "vodId", "vodName", "vodPic", "flag", "episodeName", "state", "positionMs", "durationMs", "progress", "speed", "speedOverride", "completed");
    private static final List<String> STANDARD_FIELDS = Arrays.asList("schema", "event", "eventId", "timestamp", "scope", "deletedAt", "sessionId", "dedupeKey", "cid", "configKey", "configName", "historyKey", "siteKey", "siteName", "vodId", "vodName", "vodPic", "flag", "episodeName", "state", "positionMs", "durationMs", "progress", "speed", "speedOverride", "completed", "appVersion", "client");
    private static final List<String> FULL_FIELDS = Arrays.asList("schema", "event", "eventId", "timestamp", "scope", "deletedAt", "sessionId", "dedupeKey", "cid", "configKey", "configName", "historyKey", "siteKey", "siteName", "vodId", "vodName", "vodPic", "flag", "episodeName", "state", "positionMs", "durationMs", "progress", "speed", "speedOverride", "completed", "appVersion", "client", "episodeUrl", "episodeIndex", "clientKey");
    private static final List<String> ANONYMOUS_FIELDS = Arrays.asList("schema", "event", "eventId", "timestamp", "scope", "deletedAt", "sessionId", "dedupeKey", "historyKey(sha256)", "state", "positionMs", "durationMs", "progress", "speed", "speedOverride", "completed");
    private static final List<String> DEFAULT_CUSTOM_FIELDS = Arrays.asList("cid", "configKey", "configName", "historyKey", "siteKey", "siteName", "vodId", "vodName", "vodPic", "flag", "episodeName", "state", "positionMs", "durationMs", "progress", "speed", "speedOverride", "completed");

    private DialogPlaybackWebhookBinding binding;
    private WebhookConfig editing;
    private Runnable callback;
    private boolean editMode;
    private boolean advanced;
    private boolean editEnabled;
    private final Set<String> customFields = new LinkedHashSet<>();

    public static void show(Fragment fragment, Runnable callback) {
        show(fragment.requireActivity(), callback);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        if (!PlaybackWebhookStore.isPrivacyAccepted()) {
            showPrivacy(activity, () -> showDialog(activity, callback));
        } else {
            showDialog(activity, callback);
        }
    }

    private static void showDialog(FragmentActivity activity, Runnable callback) {
        PlaybackWebhookDialog dialog = new PlaybackWebhookDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    private static void showPrivacy(FragmentActivity activity, Runnable callback) {
        ChoiceDialog.showConfirm(activity, R.string.playback_webhook_privacy_title, activity.getString(R.string.playback_webhook_privacy_message), () -> {
            PlaybackWebhookStore.acceptPrivacy();
            callback.run();
        });
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogPlaybackWebhookBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        Window window = getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int screenHeight = ResUtil.getScreenHeight(requireContext());
        boolean land = ResUtil.isLand(requireContext());
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (screenWidth * (land ? 0.72f : 0.94f));
        params.height = land ? (int) (screenHeight * 0.96f) : WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        ViewGroup.LayoutParams rootParams = binding.root.getLayoutParams();
        rootParams.height = land ? params.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.root.setLayoutParams(rootParams);
        LinearLayoutCompat.LayoutParams scrollParams = (LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams();
        scrollParams.height = land ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
        scrollParams.weight = land ? 1 : 0;
        binding.contentScroll.setLayoutParams(scrollParams);
        binding.contentScroll.setMaxHeight(land ? 0 : (int) (screenHeight * 0.56f));
        binding.add.requestFocus();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        editing = null;
        customFields.clear();
        super.onDestroyView();
    }

    @Override
    protected void initView() {
        setupInputs();
        renderList();
        showList();
    }

    @Override
    protected void initEvent() {
        binding.add.setOnClickListener(view -> showEdit(new WebhookConfig()));
        binding.negative.setOnClickListener(view -> {
            if (editMode) showList();
            else dismiss();
        });
        binding.positive.setOnClickListener(view -> {
            if (editMode) saveEdit();
            else dismiss();
        });
        binding.delete.setOnClickListener(view -> deleteEditing());
        binding.enabled.setOnClickListener(view -> {
            editEnabled = !editEnabled;
            updateEnabledButton();
        });
        binding.advancedToggle.setOnClickListener(view -> {
            advanced = !advanced;
            updateAdvanced();
        });
        binding.presetGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.presetCustom && customFields.isEmpty()) customFields.addAll(DEFAULT_CUSTOM_FIELDS);
            updateFieldsPanel();
        });
        binding.selectFields.setOnClickListener(view -> showFieldPicker());
        binding.url.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable editable) {
                binding.urlLayout.setError(null);
            }
        });
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (callback != null) callback.run();
        super.onDismiss(dialog);
    }

    private void setupInputs() {
        setupScrollableText(binding.name);
        setupScrollableText(binding.url);
        setupScrollableText(binding.token);
        setupScrollableText(binding.siteKeys);
    }

    private void renderList() {
        if (binding == null) return;
        List<WebhookConfig> configs = PlaybackWebhookStore.list();
        binding.summary.setText(getString(R.string.playback_webhook_summary, PlaybackWebhookStore.activeCount(), configs.size()));
        binding.empty.setVisibility(configs.isEmpty() ? View.VISIBLE : View.GONE);
        binding.endpointList.removeAllViews();
        for (WebhookConfig config : configs) binding.endpointList.addView(row(config));
    }

    private View row(WebhookConfig config) {
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(10), dp(9), dp(10), dp(9));
        root.setBackground(rowBackground(config));
        root.setFocusable(true);
        root.setClickable(true);
        root.setOnClickListener(view -> showEdit(copy(config)));
        LinearLayoutCompat.LayoutParams rootParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rootParams.topMargin = dp(8);
        root.setLayoutParams(rootParams);

        LinearLayoutCompat header = new LinearLayoutCompat(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayoutCompat.HORIZONTAL);
        root.addView(header, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialTextView title = text(config.displayName(), 15, Color.BLACK, true);
        header.addView(title, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialTextView status = badge(status(config), statusColor(config));
        header.addView(status);

        addDetail(root, config.url);
        addDetail(root, meta(config));
        if (config.suspended && !TextUtils.isEmpty(config.lastError)) addDetail(root, getString(R.string.playback_webhook_last_error, config.lastError), Color.parseColor("#B3261E"));

        LinearLayoutCompat actions = new LinearLayoutCompat(requireContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayoutCompat.HORIZONTAL);
        LinearLayoutCompat.LayoutParams actionParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(7);
        root.addView(actions, actionParams);

        MaterialButton toggle = actionButton(config.enabled && !config.suspended ? R.string.setting_disable : R.string.setting_enable, !config.enabled || config.suspended, false);
        toggle.setOnClickListener(view -> {
            config.enabled = !config.enabled || config.suspended;
            config.suspended = false;
            config.failureCount = 0;
            config.lastError = "";
            PlaybackWebhookStore.upsert(config);
            renderList();
        });
        actions.addView(toggle, actionLayout(0));

        MaterialButton edit = actionButton(R.string.dialog_edit, false, false);
        edit.setOnClickListener(view -> showEdit(copy(config)));
        actions.addView(edit, actionLayout(8));

        MaterialButton delete = actionButton(R.string.setting_delete, false, true);
        delete.setOnClickListener(view -> confirmDelete(config));
        actions.addView(delete, actionLayout(8));
        return root;
    }

    private void showList() {
        editMode = false;
        editing = null;
        customFields.clear();
        binding.title.setText(R.string.playback_webhook);
        binding.add.setVisibility(View.VISIBLE);
        binding.listPanel.setVisibility(View.VISIBLE);
        binding.editPanel.setVisibility(View.GONE);
        binding.delete.setVisibility(View.GONE);
        binding.negative.setText(R.string.dialog_negative);
        binding.positive.setText(R.string.dialog_positive);
        renderList();
        binding.add.requestFocus();
    }

    private void showEdit(WebhookConfig config) {
        editMode = true;
        editing = config;
        editEnabled = config.enabled && !config.suspended;
        advanced = false;
        binding.title.setText(R.string.playback_webhook_edit);
        binding.add.setVisibility(View.GONE);
        binding.listPanel.setVisibility(View.GONE);
        binding.editPanel.setVisibility(View.VISIBLE);
        binding.delete.setVisibility(TextUtils.isEmpty(config.url) ? View.GONE : View.VISIBLE);
        binding.negative.setText(R.string.playback_webhook_back);
        binding.positive.setText(R.string.playback_webhook_save);
        bind(config);
        updateAdvanced();
        updateEnabledButton();
        updateFieldsPanel();
        binding.urlLayout.setError(null);
        binding.url.requestFocus();
        binding.contentScroll.scrollTo(0, 0);
    }

    private void bind(WebhookConfig config) {
        binding.name.setText(config.name);
        binding.url.setText(config.url);
        binding.token.setText(token(config));
        binding.siteKeys.setText(join(config.siteKeys));
        binding.interval.setText(String.valueOf(config.progressIntervalSec));
        binding.retries.setText(String.valueOf(config.maxRetries));
        customFields.clear();
        customFields.addAll(config.fields == null || config.fields.isEmpty() ? DEFAULT_CUSTOM_FIELDS : config.fields);
        binding.presetGroup.check(presetId(config.fieldPreset));
    }

    private void saveEdit() {
        WebhookConfig config = editing == null ? new WebhookConfig() : editing;
        config.enabled = editEnabled;
        config.suspended = false;
        config.name = text(binding.name);
        config.url = text(binding.url);
        if (TextUtils.isEmpty(config.url) || !config.url.startsWith("http")) {
            binding.urlLayout.setError(getString(R.string.playback_webhook_url_invalid));
            Notify.show(R.string.playback_webhook_url_invalid);
            return;
        }
        config.token = text(binding.token);
        config.secret = config.token;
        config.keyId = "";
        config.events = WebhookConfig.defaults();
        config.siteKeys = split(text(binding.siteKeys));
        config.fieldPreset = selectedPreset();
        if (WebhookConfig.PRESET_CUSTOM.equals(config.fieldPreset) && customFields.isEmpty()) customFields.addAll(DEFAULT_CUSTOM_FIELDS);
        config.fields = WebhookConfig.PRESET_CUSTOM.equals(config.fieldPreset) ? new ArrayList<>(customFields) : new ArrayList<>();
        config.progressIntervalSec = Math.max(0, intValue(binding.interval, 30));
        config.maxRetries = Math.max(0, Math.min(3, intValue(binding.retries, 2)));
        config.failureCount = 0;
        config.lastError = "";
        config.lastFailureAt = 0;
        PlaybackWebhookStore.upsert(config);
        Notify.show(R.string.playback_webhook_saved);
        if (callback != null) callback.run();
        showList();
    }

    private void deleteEditing() {
        if (editing == null || TextUtils.isEmpty(editing.id)) return;
        confirmDelete(editing);
    }

    private void confirmDelete(WebhookConfig config) {
        ChoiceDialog.showConfirm(this, R.string.playback_webhook_delete_title, getString(R.string.playback_webhook_delete_message, config.displayName()), R.string.setting_delete, () -> {
            PlaybackWebhookStore.remove(config.id);
            if (callback != null) callback.run();
            showList();
        });
    }

    private void updateEnabledButton() {
        binding.enabled.setText(editEnabled ? R.string.setting_enable : R.string.setting_disable);
        binding.enabled.setAlpha(editEnabled ? 1.0f : 0.65f);
    }

    private void updateAdvanced() {
        binding.advancedPanel.setVisibility(advanced ? View.VISIBLE : View.GONE);
        binding.advancedToggle.setText(advanced ? R.string.playback_webhook_advanced_hide : R.string.playback_webhook_advanced);
    }

    private void updateFieldsPanel() {
        boolean custom = binding.presetGroup.getCheckedButtonId() == R.id.presetCustom;
        binding.selectFields.setVisibility(custom ? View.VISIBLE : View.GONE);
        binding.fieldSummary.setText(fieldsSummary(selectedPreset()));
    }

    private void showFieldPicker() {
        boolean[] checked = new boolean[FIELD_KEYS.length];
        for (int i = 0; i < FIELD_KEYS.length; i++) checked[i] = customFields.contains(FIELD_KEYS[i]);
        ChoiceDialog.showMulti(this, R.string.playback_webhook_select_fields, fieldItems(), checked, result -> {
            customFields.clear();
            for (int i = 0; i < FIELD_KEYS.length; i++) if (result[i]) customFields.add(FIELD_KEYS[i]);
            updateFieldsPanel();
        });
    }

    private int presetId(String preset) {
        preset = WebhookConfig.normalizePreset(preset);
        if (WebhookConfig.PRESET_STANDARD.equals(preset)) return R.id.presetStandard;
        if (WebhookConfig.PRESET_FULL.equals(preset)) return R.id.presetFull;
        if (WebhookConfig.PRESET_CUSTOM.equals(preset)) return R.id.presetCustom;
        return R.id.presetSafe;
    }

    private String selectedPreset() {
        int checked = binding.presetGroup.getCheckedButtonId();
        if (checked == R.id.presetStandard) return WebhookConfig.PRESET_STANDARD;
        if (checked == R.id.presetFull) return WebhookConfig.PRESET_FULL;
        if (checked == R.id.presetCustom) return WebhookConfig.PRESET_CUSTOM;
        return WebhookConfig.PRESET_BASIC;
    }

    private String fieldsSummary(String preset) {
        preset = WebhookConfig.normalizePreset(preset);
        if (WebhookConfig.PRESET_STANDARD.equals(preset)) return getString(R.string.playback_webhook_fields_summary, join(STANDARD_FIELDS));
        if (WebhookConfig.PRESET_FULL.equals(preset)) return getString(R.string.playback_webhook_fields_summary, join(FULL_FIELDS));
        if (WebhookConfig.PRESET_ANONYMOUS.equals(preset)) return getString(R.string.playback_webhook_fields_summary, join(ANONYMOUS_FIELDS));
        if (WebhookConfig.PRESET_CUSTOM.equals(preset)) return getString(R.string.playback_webhook_fields_summary, join(customSummaryFields()));
        return getString(R.string.playback_webhook_fields_summary, join(BASIC_FIELDS));
    }

    private List<String> customSummaryFields() {
        List<String> fields = new ArrayList<>(PROTOCOL_FIELDS);
        fields.addAll(customFields.isEmpty() ? DEFAULT_CUSTOM_FIELDS : customFields);
        return fields;
    }

    private String meta(WebhookConfig config) {
        String token = TextUtils.isEmpty(token(config)) ? getString(R.string.playback_webhook_token_empty) : getString(R.string.playback_webhook_token_set);
        String sites = config.siteKeys == null || config.siteKeys.isEmpty() ? getString(R.string.playback_webhook_all_sites) : getString(R.string.playback_webhook_sites, join(config.siteKeys));
        return getString(R.string.playback_webhook_row_meta, token, presetName(config.fieldPreset), sites);
    }

    private String presetName(String preset) {
        preset = WebhookConfig.normalizePreset(preset);
        if (WebhookConfig.PRESET_STANDARD.equals(preset)) return getString(R.string.playback_webhook_preset_standard);
        if (WebhookConfig.PRESET_FULL.equals(preset)) return getString(R.string.playback_webhook_preset_full);
        if (WebhookConfig.PRESET_ANONYMOUS.equals(preset)) return getString(R.string.playback_webhook_preset_anonymous);
        if (WebhookConfig.PRESET_CUSTOM.equals(preset)) return getString(R.string.playback_webhook_preset_custom);
        return getString(R.string.playback_webhook_preset_safe);
    }

    private CharSequence[] fieldItems() {
        CharSequence[] descriptions = getResources().getTextArray(R.array.playback_webhook_field_descriptions);
        CharSequence[] items = new CharSequence[FIELD_KEYS.length];
        for (int i = 0; i < FIELD_KEYS.length; i++) {
            String description = i < descriptions.length ? descriptions[i].toString() : "";
            items[i] = TextUtils.isEmpty(description) ? FIELD_KEYS[i] : FIELD_KEYS[i] + "\n" + description;
        }
        return items;
    }

    private String status(WebhookConfig config) {
        if (config.suspended) return getString(R.string.playback_webhook_suspended);
        if (config.enabled && config.isUsable()) return getString(R.string.playback_webhook_active);
        return getString(config.enabled ? R.string.playback_webhook_incomplete : R.string.setting_disable);
    }

    private int statusColor(WebhookConfig config) {
        if (config.suspended) return Color.parseColor("#B3261E");
        if (config.enabled && config.isUsable()) return Color.parseColor("#137333");
        return Color.parseColor("#5F6368");
    }

    private WebhookConfig copy(WebhookConfig source) {
        WebhookConfig target = new WebhookConfig();
        target.id = source.id;
        target.name = source.name;
        target.enabled = source.enabled;
        target.suspended = source.suspended;
        target.url = source.url;
        target.events = source.events == null ? new ArrayList<>() : new ArrayList<>(source.events);
        target.siteKeys = source.siteKeys == null ? new ArrayList<>() : new ArrayList<>(source.siteKeys);
        target.fieldPreset = source.fieldPreset;
        target.fields = source.fields == null ? new ArrayList<>() : new ArrayList<>(source.fields);
        target.token = token(source);
        target.secret = target.token;
        target.keyId = "";
        target.progressIntervalSec = source.progressIntervalSec;
        target.maxRetries = source.maxRetries;
        target.failureCount = source.failureCount;
        target.lastFailureAt = source.lastFailureAt;
        target.lastError = source.lastError;
        return target;
    }

    private String token(WebhookConfig config) {
        return TextUtils.isEmpty(config.token) ? safe(config.secret) : safe(config.token);
    }

    private List<String> split(String text) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return result;
        for (String item : text.split("[,，\\s]+")) {
            String value = item.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : TextUtils.join(",", values);
    }

    private int intValue(EditText input, int fallback) {
        try {
            return Integer.parseInt(text(input));
        } catch (Exception e) {
            return fallback;
        }
    }

    private String text(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void setupScrollableText(EditText input) {
        input.setSelectAllOnFocus(false);
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

    private LinearLayoutCompat.LayoutParams actionLayout(int marginStart) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(0, dp(36), 1);
        params.leftMargin = dp(marginStart);
        return params;
    }

    private MaterialButton actionButton(int text, boolean tonal, boolean danger) {
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

    private void addDetail(LinearLayoutCompat root, String value) {
        addDetail(root, value, Color.parseColor("#5F6368"));
    }

    private void addDetail(LinearLayoutCompat root, String value, int color) {
        if (TextUtils.isEmpty(value)) return;
        MaterialTextView view = text(value, 12, color, false);
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

    private MaterialTextView badge(String value, int color) {
        MaterialTextView view = text(value, 12, color, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#FFFFFF"));
        drawable.setStroke(dp(1), color);
        drawable.setCornerRadius(dp(6));
        view.setBackground(drawable);
        return view;
    }

    private GradientDrawable rowBackground(WebhookConfig config) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(config.suspended ? "#FFF7F7" : "#F5F6F7"));
        drawable.setStroke(dp(1), Color.parseColor(config.isUsable() ? "#DADCE0" : "#E2E5E8"));
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }
}
