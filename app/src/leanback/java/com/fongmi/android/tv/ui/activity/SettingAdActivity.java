package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ad.audio.AdAudioRuleSnapshot;
import com.fongmi.android.tv.ad.audio.AdAudioRuleStore;
import com.fongmi.android.tv.ad.audio.AdAudioSetting;
import com.fongmi.android.tv.ad.audio.AdSkipPolicyController;
import com.fongmi.android.tv.ad.audio.ProbeRuleDownloader;
import com.fongmi.android.tv.ad.audio.ProbeRuleStore;
import com.fongmi.android.tv.ad.audio.SpeechAdConfig;
import com.fongmi.android.tv.ad.audio.SpeechAdSetting;
import com.fongmi.android.tv.api.config.ImportedAdRuleCandidateStore;
import com.fongmi.android.tv.api.config.RuleConfig;
import com.fongmi.android.tv.api.config.UserAdRuleStore;
import com.fongmi.android.tv.databinding.ActivitySettingAdBinding;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.player.IntroSkipKinds;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.RealtimeSubtitleSpeechRecognitionFactory;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.AdBlockStatsDialog;
import com.fongmi.android.tv.ui.dialog.AdRuleManageDialog;
import com.fongmi.android.tv.ui.dialog.LightDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class SettingAdActivity extends BaseActivity {

    private ActivitySettingAdBinding mBinding;
    private String[] introSkipMode;
    private volatile AdAudioRuleSnapshot adAudioSnapshot = AdAudioRuleStore.get().current();
    private volatile long probeRevision;
    private volatile int probeRuleCount;
    private final ActivityResultLauncher<String[]> adAudioRulePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importAdAudioRules);

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingAdActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingAdBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.adblock.requestFocus();
        introSkipMode = getResources().getStringArray(R.array.select_auto_skip_intro_outro);
        setText();
        loadAdAudioRules();
        loadProbeRules();
    }

    @Override
    protected void initEvent() {
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.aiAdDetection.setOnClickListener(this::setAiAdDetection);
        mBinding.adRuleManage.setOnClickListener(view -> AdRuleManageDialog.create().show(this, this::setText));
        mBinding.adBlockStats.setOnClickListener(view -> AdBlockStatsDialog.create(this).show());
        mBinding.adAudioFingerprint.setOnClickListener(this::toggleAdAudioFingerprint);
        mBinding.adAudioFingerprint.setOnLongClickListener(this::manageAdAudioRules);
        mBinding.adAudioAutoSkip.setOnClickListener(this::toggleAdAudioAutoSkip);
        mBinding.probeRuleSource.setOnClickListener(this::editProbeRuleSource);
        mBinding.probeRuleRefresh.setOnClickListener(this::refreshProbeRules);
        mBinding.speechAdEnabled.setOnClickListener(this::toggleSpeechAdEnabled);
        mBinding.speechAdKeywords.setOnClickListener(this::editSpeechAdKeywords);
        mBinding.speechAdSkipSeconds.setOnClickListener(this::editSpeechAdSkipSeconds);
        mBinding.speechAdSkipMode.setOnClickListener(this::selectSpeechAdSkipMode);
        mBinding.autoSkipIntroOutro.setOnClickListener(this::setAutoSkipIntroOutro);
        mBinding.introSkipKinds.setOnClickListener(view -> IntroSkipKinds.show(this, this::setText));
    }

    /** 规则文件要读盘并完整解析，放后台线程，取回后再刷新摘要。 */
    private void loadAdAudioRules() {
        Task.execute(() -> {
            adAudioSnapshot = AdAudioRuleStore.get().load();
            runOnUiThread(this::setText);
        });
    }

    private void loadProbeRules() {
        Task.execute(() -> {
            ProbeRuleStore store = ProbeRuleStore.get();
            AdAudioRuleSnapshot snapshot = store.load();
            probeRuleCount = snapshot.ruleSet().rules().size();
            probeRevision = store.revision();
            runOnUiThread(this::setText);
        });
    }

    private void setText() {
        if (!canSetText()) return;
        safeSet("adblock", mBinding.adblockText, () -> getSwitch(Setting.isAdblock()));
        mBinding.aiAdDetection.setVisibility(Setting.isAiConfigReady() ? View.VISIBLE : View.GONE);
        safeSet("aiAdDetection", mBinding.aiAdDetectionText, () -> getSwitch(Setting.isAiAdDetection()));
        safeSet("adRuleManage", mBinding.adRuleManageText, () -> getString(R.string.ad_rule_count_with_pending,
                UserAdRuleStore.load().size() + RuleConfig.get().getDefaultRules().size(),
                ImportedAdRuleCandidateStore.pending().size()));
        safeSet("adAudioFingerprint", mBinding.adAudioFingerprintText, this::getAdAudioFingerprintText);
        safeSet("adAudioAutoSkip", mBinding.adAudioAutoSkipText, () -> getSwitch(AdAudioSetting.isAutoSkipEnabled()));
        safeSet("probeRuleSource", mBinding.probeRuleSourceText, this::getProbeRuleSourceText);
        safeSet("probeRuleRefresh", mBinding.probeRuleRefreshText, this::getProbeRuleRefreshText);
        SpeechAdConfig speech = SpeechAdSetting.snapshot();
        safeSet("speechAdEnabled", mBinding.speechAdEnabledText, () -> getSpeechAdEnabledText(speech));
        safeSet("speechAdKeywords", mBinding.speechAdKeywordsText, () -> getString(R.string.speech_ad_keyword_count, speech.keywords().values().size()));
        safeSet("speechAdSkipSeconds", mBinding.speechAdSkipSecondsText, () -> getString(R.string.speech_ad_skip_seconds_value, speech.skipSeconds()));
        safeSet("speechAdSkipMode", mBinding.speechAdSkipModeText, () -> speech.mode() == AdSkipPolicyController.Mode.AUTO
                ? getString(R.string.speech_ad_skip_mode_auto) : getString(R.string.speech_ad_skip_mode_prompt));
        safeSet("autoSkipIntroOutro", mBinding.autoSkipIntroOutroText, () -> introSkipMode[Setting.getIntroSkipMode()]);
        safeSet("introSkipKinds", mBinding.introSkipKindsText, IntroSkipKinds::summary);
    }

    private boolean canSetText() {
        return mBinding != null && !isFinishing() && !isDestroyed();
    }

    private void safeSet(String name, TextView view, TextSupplier supplier) {
        safeRun(name, () -> view.setText(supplier.get()), () -> setError(view));
    }

    private void safeRun(String name, Runnable action, Runnable fallback) {
        try {
            action.run();
        } catch (Throwable e) {
            SpiderDebug.log("ad", "summary failed item=%s error=%s", name, e.toString());
            if (fallback == null) return;
            try {
                fallback.run();
            } catch (Throwable ignored) {
            }
        }
    }

    private void setError(TextView view) {
        if (view != null) view.setText(R.string.error_config_get);
    }

    private interface TextSupplier {
        CharSequence get();
    }

    private void setAdblock(View view) {
        Setting.putAdblock(!Setting.isAdblock());
        setText();
    }

    private void setAiAdDetection(View view) {
        Setting.putAiAdDetection(!Setting.isAiAdDetection());
        setText();
    }

    private void setAutoSkipIntroOutro(View view) {
        Setting.putIntroSkipMode((Setting.getIntroSkipMode() + 1) % introSkipMode.length);
        setText();
    }

    private String getAdAudioFingerprintText() {
        String enabled = getSwitch(AdAudioSetting.isEnabled());
        AdAudioRuleSnapshot snapshot = adAudioSnapshot;
        if (snapshot == null || snapshot.hasError()) {
            return enabled + " · " + getString(R.string.setting_ad_audio_rule_error);
        }
        int count = snapshot.ruleSet().rules().size();
        if (count == 0) return enabled + " · " + getString(R.string.setting_ad_audio_no_rules);
        return enabled + " · " + getString(R.string.setting_ad_audio_rule_count, count, snapshot.version());
    }

    private void toggleAdAudioFingerprint(View view) {
        AdAudioSetting.setEnabled(!AdAudioSetting.isEnabled());
        notifyAdAudioRuntime();
        setText();
    }

    private void toggleAdAudioAutoSkip(View view) {
        AdAudioSetting.setAutoSkipEnabled(!AdAudioSetting.isAutoSkipEnabled());
        notifyAdAudioRuntime();
        setText();
    }

    private boolean manageAdAudioRules(View view) {
        new MaterialAlertDialogBuilder(this, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.setting_ad_audio_title)
                .setItems(new String[]{
                        getString(R.string.setting_ad_audio_import),
                        getString(R.string.setting_ad_audio_clear)
                }, (dialog, which) -> {
                    if (which == 0) {
                        adAudioRulePicker.launch(new String[]{"application/json", "text/json"});
                    } else {
                        confirmClearAdAudioRules();
                    }
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
        return true;
    }

    private void importAdAudioRules(Uri uri) {
        if (uri == null) return;
        Task.execute(() -> {
            String message;
            try {
                adAudioSnapshot = AdAudioRuleStore.get().importUri(getContentResolver(), uri);
                message = getString(R.string.setting_ad_audio_imported,
                        adAudioSnapshot.ruleSet().rules().size(), adAudioSnapshot.version());
            } catch (Exception e) {
                message = getString(R.string.setting_ad_audio_import_failed);
            }
            String result = message;
            runOnUiThread(() -> {
                if (!canSetText()) return;
                Notify.show(result);
                notifyAdAudioRuntime();
                setText();
            });
        });
    }

    private void confirmClearAdAudioRules() {
        new MaterialAlertDialogBuilder(this, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.setting_ad_audio_clear)
                .setMessage(R.string.setting_ad_audio_clear_confirm)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> Task.execute(() -> {
                    try {
                        adAudioSnapshot = AdAudioRuleStore.get().clear();
                        runOnUiThread(() -> {
                            if (!canSetText()) return;
                            Notify.show(R.string.setting_ad_audio_clear_done);
                            notifyAdAudioRuntime();
                            setText();
                        });
                    } catch (RuntimeException e) {
                        runOnUiThread(() -> Notify.show(R.string.setting_ad_audio_import_failed));
                    }
                }))
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private String getProbeRuleSourceText() {
        String url = AdAudioSetting.getProbeRuleUrl();
        if (url.isEmpty()) return getString(R.string.setting_ad_probe_source_off);
        if (url.equals(AdAudioSetting.DEFAULT_PROBE_RULE_URL)) return getString(R.string.setting_ad_probe_source_default);
        return url;
    }

    private String getProbeRuleRefreshText() {
        if (probeRevision <= 0L) return getString(R.string.setting_ad_probe_never);
        return getString(R.string.setting_ad_probe_revision, probeRevision, probeRuleCount);
    }

    private void editProbeRuleSource(View view) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(AdAudioSetting.getProbeRuleUrl());
        input.setSelection(input.length());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.setting_ad_probe_source)
                .setMessage(R.string.setting_ad_probe_source_hint)
                .setView(input)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String value = input.getText().toString().trim();
            // 空串表示关闭社区规则源；规则不签名，传输层是唯一保障，因此只收 https。
            if (!value.isEmpty() && !value.toLowerCase(Locale.ROOT).startsWith("https://")) {
                Notify.show(R.string.setting_ad_probe_url_invalid);
                return;
            }
            AdAudioSetting.setProbeRuleUrl(value);
            setText();
            dialog.dismiss();
        }));
        dialog.show();
        LightDialog.apply(dialog);
    }

    private void refreshProbeRules(View view) {
        mBinding.probeRuleRefreshText.setText(R.string.setting_ad_probe_refreshing);
        boolean started = ProbeRuleDownloader.refreshNow(new ProbeRuleDownloader.Callback() {
            @Override
            public void onSuccess(AdAudioRuleSnapshot snapshot) {
                if (!canSetText()) return;
                Notify.show(snapshot.hasRules()
                        ? getString(R.string.setting_ad_probe_refreshed, snapshot.version(), snapshot.ruleSet().rules().size())
                        : getString(R.string.setting_ad_probe_unchanged, 0));
                notifyAdAudioRuntime();
                loadProbeRules();
            }

            @Override
            public void onFailure(Throwable error) {
                if (!canSetText()) return;
                Notify.show(getString(R.string.setting_ad_probe_failed, String.valueOf(error.getMessage())));
                setText();
            }

            @Override
            public void onDisabled() {
                if (!canSetText()) return;
                Notify.show(R.string.setting_ad_probe_source_disabled);
                setText();
            }
        });
        // 已有刷新在跑时不会有回调，这里必须自己复位，否则文本永远停在「正在刷新」。
        if (!started) setText();
    }

    private String getSpeechAdEnabledText(SpeechAdConfig speech) {
        String text = getSwitch(speech.enabled());
        if (speech.enabled() && !RealtimeSubtitleSpeechRecognitionFactory.isSelectedModelReady()) {
            text += " · " + getString(R.string.speech_ad_model_not_ready);
        }
        return text;
    }

    private void toggleSpeechAdEnabled(View view) {
        SpeechAdSetting.setEnabled(!SpeechAdSetting.snapshot().enabled());
        notifyAdAudioRuntime();
        setText();
    }

    private void editSpeechAdKeywords(View view) {
        SpeechAdConfig speech = SpeechAdSetting.snapshot();
        EditText input = new EditText(this);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(String.join("\n", speech.keywords().values()));
        input.setSelectAllOnFocus(false);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.speech_ad_keywords)
                .setView(input)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            SpeechAdSetting.setKeywords(input.getText().toString());
            notifyAdAudioRuntime();
            setText();
            dialog.dismiss();
        }));
        dialog.show();
        LightDialog.apply(dialog);
    }

    private void editSpeechAdSkipSeconds(View view) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSelectAllOnFocus(true);
        input.setText(String.valueOf(SpeechAdSetting.snapshot().skipSeconds()));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.speech_ad_skip_seconds)
                .setView(input)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String value = input.getText().toString().trim();
            try {
                int seconds = Integer.parseInt(value);
                if (seconds < 1 || seconds > 120) throw new NumberFormatException();
                SpeechAdSetting.setSkipSeconds(seconds);
                notifyAdAudioRuntime();
                setText();
                dialog.dismiss();
            } catch (NumberFormatException error) {
                input.setError(getString(R.string.speech_ad_skip_seconds_invalid));
                input.requestFocus();
            }
        }));
        dialog.show();
        LightDialog.apply(dialog);
    }

    private void selectSpeechAdSkipMode(View view) {
        SpeechAdConfig speech = SpeechAdSetting.snapshot();
        String[] modes = {
                getString(R.string.speech_ad_skip_mode_prompt),
                getString(R.string.speech_ad_skip_mode_auto)
        };
        int checked = speech.mode() == AdSkipPolicyController.Mode.AUTO ? 1 : 0;
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.speech_ad_skip_mode)
                .setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(modes, checked, (shown, which) -> {
                    SpeechAdSetting.setMode(which == 1
                            ? AdSkipPolicyController.Mode.AUTO
                            : AdSkipPolicyController.Mode.PROMPT);
                    notifyAdAudioRuntime();
                    setText();
                    shown.dismiss();
                })
                .create();
        dialog.show();
        LightDialog.apply(dialog);
    }

    private void notifyAdAudioRuntime() {
        PlaybackService service = Server.get().getService();
        if (service == null || service.player() == null || service.player().isReleased()) return;
        service.player().reloadAdAudioSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setText();
    }
}
