package com.fongmi.android.tv.ui.fragment;

import android.content.ContentResolver;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
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
import com.fongmi.android.tv.databinding.FragmentSettingAdBinding;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.player.IntroSkipKinds;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.RealtimeSubtitleSpeechRecognitionFactory;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.AdBlockStatsDialog;
import com.fongmi.android.tv.ui.dialog.AdRuleManageDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class SettingAdFragment extends BaseFragment {

    private FragmentSettingAdBinding mBinding;
    private String[] introSkipMode;
    private volatile AdAudioRuleSnapshot adAudioSnapshot = AdAudioRuleStore.get().current();
    private volatile long probeRevision;
    private volatile int probeRuleCount;
    private final ActivityResultLauncher<String[]> adAudioRulePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importAdAudioRules);

    public static SettingAdFragment newInstance() {
        return new SettingAdFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingAdBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        introSkipMode = ResUtil.getStringArray(R.array.select_auto_skip_intro_outro);
        setText();
        loadSnapshots();
    }

    /** 规则文件的读取和解析都在磁盘上，只能异步做，结果缓存在字段里供 setText 使用。 */
    private void loadSnapshots() {
        Task.execute(() -> {
            adAudioSnapshot = AdAudioRuleStore.get().load();
            probeRuleCount = ProbeRuleStore.get().load().ruleSet().rules().size();
            probeRevision = ProbeRuleStore.get().revision();
            App.post(this::setText);
        });
    }

    @Override
    protected void initEvent() {
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.aiAdDetection.setOnClickListener(this::setAiAdDetection);
        mBinding.adRuleManage.setOnClickListener(view -> AdRuleManageDialog.create().show(requireActivity(), this::setText));
        mBinding.adBlockStats.setOnClickListener(view -> AdBlockStatsDialog.create(requireActivity()).show());
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
        mBinding.introSkipKinds.setOnClickListener(view -> IntroSkipKinds.show(requireActivity(), this::setText));
    }

    private void setText() {
        if (mBinding == null) return;
        if (!canSetText()) return;
        safeSet("adblock", mBinding.adblockText, () -> getSwitch(Setting.isAdblock()));
        safeRun("aiAdDetection", () -> {
            mBinding.aiAdDetection.setVisibility(Setting.isAiConfigReady() ? View.VISIBLE : View.GONE);
            mBinding.aiAdDetectionText.setText(getSwitch(Setting.isAiAdDetection()));
        }, () -> setError(mBinding.aiAdDetectionText));
        safeSet("adRuleManage", mBinding.adRuleManageText, () -> getString(R.string.ad_rule_count_with_pending,
                UserAdRuleStore.load().size() + RuleConfig.get().getDefaultRules().size(),
                ImportedAdRuleCandidateStore.pending().size()));
        safeSet("adAudioFingerprint", mBinding.adAudioFingerprintText, this::getAdAudioFingerprintText);
        safeSet("adAudioAutoSkip", mBinding.adAudioAutoSkipText, () -> getSwitch(AdAudioSetting.isAutoSkipEnabled()));
        safeSet("probeRuleSource", mBinding.probeRuleSourceText, this::getProbeRuleSourceText);
        safeSet("probeRuleRefresh", mBinding.probeRuleRefreshText, this::getProbeRuleRefreshText);
        safeRun("speechAd", () -> {
            SpeechAdConfig speech = SpeechAdSetting.snapshot();
            String enabled = getSwitch(speech.enabled());
            if (speech.enabled() && !RealtimeSubtitleSpeechRecognitionFactory.isSelectedModelReady()) {
                enabled += " · " + getString(R.string.speech_ad_model_not_ready);
            }
            mBinding.speechAdEnabledText.setText(enabled);
            mBinding.speechAdKeywordsText.setText(getString(R.string.speech_ad_keyword_count, speech.keywords().values().size()));
            mBinding.speechAdSkipSecondsText.setText(getString(R.string.speech_ad_skip_seconds_value, speech.skipSeconds()));
            mBinding.speechAdSkipModeText.setText(speech.mode() == AdSkipPolicyController.Mode.AUTO
                    ? R.string.speech_ad_skip_mode_auto : R.string.speech_ad_skip_mode_prompt);
        }, () -> {
            setError(mBinding.speechAdEnabledText);
            setError(mBinding.speechAdKeywordsText);
            setError(mBinding.speechAdSkipSecondsText);
            setError(mBinding.speechAdSkipModeText);
        });
        safeSet("autoSkipIntroOutro", mBinding.autoSkipIntroOutroText, () -> introSkipMode[Setting.getIntroSkipMode()]);
        safeSet("introSkipKinds", mBinding.introSkipKindsText, IntroSkipKinds::summary);
    }

    private boolean canSetText() {
        return mBinding != null && isAdded() && getContext() != null;
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

    private void toggleAdAudioAutoSkip(View view) {
        AdAudioSetting.setAutoSkipEnabled(!AdAudioSetting.isAutoSkipEnabled());
        notifyAdAudioRuntime();
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

    private boolean manageAdAudioRules(View view) {
        new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.setting_ad_audio_title)
                .setItems(new String[]{
                        getString(R.string.setting_ad_audio_import),
                        getString(R.string.setting_ad_audio_clear)
                }, (dialog, which) -> {
                    if (which == 0) adAudioRulePicker.launch(new String[]{"application/json", "text/json"});
                    else confirmClearAdAudioRules();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
        return true;
    }

    private void importAdAudioRules(Uri uri) {
        if (uri == null || !canSetText()) return;
        ContentResolver resolver = requireContext().getContentResolver();
        Task.execute(() -> {
            AdAudioRuleSnapshot imported = null;
            try {
                imported = AdAudioRuleStore.get().importUri(resolver, uri);
                adAudioSnapshot = imported;
            } catch (Exception e) {
                SpiderDebug.log("ad-audio-rules", e);
            }
            AdAudioRuleSnapshot result = imported;
            App.post(() -> {
                if (!canSetText()) return;
                Notify.show(result == null ? getString(R.string.setting_ad_audio_import_failed)
                        : getString(R.string.setting_ad_audio_imported, result.ruleSet().rules().size(), result.version()));
                notifyAdAudioRuntime();
                setText();
            });
        });
    }

    private void confirmClearAdAudioRules() {
        new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.setting_ad_audio_clear)
                .setMessage(R.string.setting_ad_audio_clear_confirm)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> Task.execute(() -> {
                    boolean success = true;
                    try {
                        adAudioSnapshot = AdAudioRuleStore.get().clear();
                    } catch (RuntimeException e) {
                        SpiderDebug.log("ad-audio-rules", e);
                        success = false;
                    }
                    boolean cleared = success;
                    App.post(() -> {
                        if (!canSetText()) return;
                        Notify.show(cleared ? R.string.setting_ad_audio_clear_done : R.string.setting_ad_audio_import_failed);
                        notifyAdAudioRuntime();
                        setText();
                    });
                }))
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private String getProbeRuleSourceText() {
        String url = AdAudioSetting.getProbeRuleUrl();
        if (TextUtils.isEmpty(url)) return getString(R.string.setting_ad_probe_source_off);
        if (AdAudioSetting.DEFAULT_PROBE_RULE_URL.equals(url)) return getString(R.string.setting_ad_probe_source_default);
        return url;
    }

    /** 空字符串表示关闭社区规则源；非空时只接受 https，规则没有签名，传输层是唯一的真实性保障。 */
    private void editProbeRuleSource(View view) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setText(AdAudioSetting.getProbeRuleUrl());
        input.setSelection(input.length());
        AlertDialog alert = new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.setting_ad_probe_source)
                .setMessage(R.string.setting_ad_probe_source_hint)
                .setView(input)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
        alert.setOnShowListener(dialog -> alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (!value.isEmpty() && !value.toLowerCase(Locale.ROOT).startsWith("https://")) {
                Notify.show(R.string.setting_ad_probe_url_invalid);
                return;
            }
            AdAudioSetting.setProbeRuleUrl(value);
            setText();
            alert.dismiss();
        }));
        alert.show();
    }

    private String getProbeRuleRefreshText() {
        if (probeRevision <= 0L) return getString(R.string.setting_ad_probe_never);
        return getString(R.string.setting_ad_probe_revision, probeRevision, probeRuleCount);
    }

    private void refreshProbeRules(View view) {
        mBinding.probeRuleRefreshText.setText(R.string.setting_ad_probe_refreshing);
        boolean started = ProbeRuleDownloader.refreshNow(new ProbeRuleDownloader.Callback() {
            @Override
            public void onSuccess(AdAudioRuleSnapshot snapshot) {
                if (mBinding == null) return;
                Notify.show(snapshot.hasRules()
                        ? getString(R.string.setting_ad_probe_refreshed, snapshot.version(), snapshot.ruleSet().rules().size())
                        : getString(R.string.setting_ad_probe_unchanged, 0));
                // loadSnapshots() 自己会在后台读完后 post setText()，这里不能再直接调，
                // 否则会用刷新前的 revision 覆盖掉「正在刷新」，闪一下旧值。
                loadSnapshots();
                notifyAdAudioRuntime();
            }

            @Override
            public void onFailure(Throwable error) {
                if (mBinding == null) return;
                Notify.show(getString(R.string.setting_ad_probe_failed, String.valueOf(error.getMessage())));
                setText();
            }

            @Override
            public void onDisabled() {
                if (mBinding == null) return;
                Notify.show(R.string.setting_ad_probe_source_disabled);
                setText();
            }
        });
        // 已有刷新在跑时不会有回调，这里必须自己复位，否则文本永远停在「正在刷新」。
        if (!started) setText();
    }

    private void toggleSpeechAdEnabled(View view) {
        SpeechAdSetting.setEnabled(!SpeechAdSetting.snapshot().enabled());
        notifyAdAudioRuntime();
        setText();
    }

    private void editSpeechAdKeywords(View view) {
        SpeechAdConfig speech = SpeechAdSetting.snapshot();
        EditText input = new EditText(requireContext());
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        input.setText(String.join(",", speech.keywords().values()));
        input.setSelection(input.length());
        AlertDialog alert = new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.speech_ad_keywords)
                .setView(input)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
        alert.setOnShowListener(dialog -> alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            SpeechAdSetting.setKeywords(input.getText().toString());
            notifyAdAudioRuntime();
            setText();
            alert.dismiss();
        }));
        alert.show();
    }

    private void editSpeechAdSkipSeconds(View view) {
        SpeechAdConfig speech = SpeechAdSetting.snapshot();
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(speech.skipSeconds()));
        input.setSelectAllOnFocus(true);
        AlertDialog alert = new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.speech_ad_skip_seconds)
                .setView(input)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
        alert.setOnShowListener(dialog -> alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final int value;
            try {
                value = Integer.parseInt(input.getText().toString().trim());
            } catch (RuntimeException e) {
                input.setError(getString(R.string.speech_ad_skip_seconds_invalid));
                input.requestFocus();
                return;
            }
            int normalized = SpeechAdConfig.create(false, "", value, AdSkipPolicyController.Mode.PROMPT.name()).skipSeconds();
            if (normalized != value) {
                input.setError(getString(R.string.speech_ad_skip_seconds_invalid));
                input.requestFocus();
                return;
            }
            SpeechAdSetting.setSkipSeconds(value);
            notifyAdAudioRuntime();
            setText();
            alert.dismiss();
        }));
        alert.show();
    }

    private void selectSpeechAdSkipMode(View view) {
        SpeechAdConfig speech = SpeechAdSetting.snapshot();
        String[] modes = {getString(R.string.speech_ad_skip_mode_prompt), getString(R.string.speech_ad_skip_mode_auto)};
        int checked = speech.mode() == AdSkipPolicyController.Mode.AUTO ? 1 : 0;
        new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.speech_ad_skip_mode)
                .setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(modes, checked, (dialog, which) -> {
                    SpeechAdSetting.setMode(which == 1 ? AdSkipPolicyController.Mode.AUTO : AdSkipPolicyController.Mode.PROMPT);
                    notifyAdAudioRuntime();
                    setText();
                    dialog.dismiss();
                }).show();
    }

    private void notifyAdAudioRuntime() {
        PlaybackService service = Server.get().getService();
        if (service == null || service.player() == null || service.player().isReleased()) return;
        service.player().reloadAdAudioSettings();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) setText();
    }

    @Override
    public void onResume() {
        super.onResume();
        setText();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
