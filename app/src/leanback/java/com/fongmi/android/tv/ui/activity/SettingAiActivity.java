package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.databinding.ActivitySettingAiBinding;
import com.fongmi.android.tv.service.RecommendationFeedbackStore;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.RealtimeSubtitleController;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.AiConfigDialog;
import com.fongmi.android.tv.ui.dialog.RecommendationFeedbackDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleSettingsDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingAiActivity extends BaseActivity {

    private ActivitySettingAiBinding mBinding;
    private String[] realtimeModelLabels;
    private String[] realtimeModelValues;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingAiActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingAiBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.aiRecommendation.requestFocus();
        realtimeModelLabels = ResUtil.getStringArray(R.array.select_realtime_subtitle_model);
        realtimeModelValues = ResUtil.getStringArray(R.array.select_realtime_subtitle_model_value);
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.aiRecommendation.setOnClickListener(this::setAiRecommendation);
        mBinding.personalRecommendation.setOnClickListener(this::setPersonalRecommendation);
        mBinding.recommendationFeedback.setOnClickListener(this::manageRecommendationFeedback);
        mBinding.subtitleRealtimeModel.setOnClickListener(this::onRealtimeModel);
        mBinding.subtitleAiMaxConcurrency.setOnClickListener(this::onSubtitleAiMaxConcurrency);
        mBinding.subtitleAiChunkCount.setOnClickListener(this::onSubtitleAiChunkCount);
    }

    private void setText() {
        mBinding.aiRecommendationText.setText(getAiRecommendationText());
        mBinding.personalRecommendationText.setText(getSwitch(Setting.isPersonalRecommendation()));
        int feedbackCount = RecommendationFeedbackStore.size();
        mBinding.recommendationFeedbackText.setText(feedbackCount == 0
                ? getString(R.string.setting_recommendation_feedback_empty)
                : getString(R.string.setting_recommendation_feedback_count, feedbackCount));
        setRealtimeModelStatus();
        setAiSubtitleSettings();
    }

    private String getAiRecommendationText() {
        AiConfig config = AiConfig.objectFrom(Setting.getAiConfig());
        if (!config.isEnabled()) return getSwitch(false);
        return config.isReady() ? getString(R.string.setting_configured) : getString(R.string.setting_unconfigured);
    }

    private void setAiRecommendation(View view) {
        AiConfigDialog.create(this).onDismiss(this::setText).show();
    }

    private void setPersonalRecommendation(View view) {
        if (Setting.isPersonalRecommendation()) {
            Setting.putPersonalRecommendation(false);
            setText();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.personal_recommendation_confirm_title)
                .setMessage(R.string.personal_recommendation_confirm_message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    Setting.putPersonalRecommendation(true);
                    setText();
                })
                .show();
    }

    private void manageRecommendationFeedback(View view) {
        RecommendationFeedbackDialog.create().onChanged(this::setText).show(this);
    }

    private void onRealtimeModel(View view) {
        RealtimeSubtitleController controller = RealtimeSubtitleController.get();
        if (controller.isModelDownloading() || controller.isModelDeleting()) return;
        SubtitleSettingsDialog.showRealtimeModel(this, realtimeModelLabels, getRealtimeModelIndex(), controller.isModelReady(), this::selectRealtimeModel, () -> {
            mBinding.subtitleRealtimeModelText.setText(modelStatusText(R.string.subtitle_realtime_model_deleting));
            controller.deleteModel(error -> onRealtimeModelChanged(error, true));
        });
    }

    private void selectRealtimeModel(int index) {
        if (realtimeModelValues == null || index < 0 || index >= realtimeModelValues.length) return;
        Setting.putRealtimeSubtitleModel(realtimeModelValues[index]);
        RealtimeSubtitleController controller = RealtimeSubtitleController.get();
        setRealtimeModelStatus();
        if (controller.isModelReady()) return;
        mBinding.subtitleRealtimeModelText.setText(modelStatusText(R.string.subtitle_realtime_model_downloading));
        controller.downloadModel((percent, fileName) -> {
            if (isFinishing() || isDestroyed()) return;
            mBinding.subtitleRealtimeModelText.setText(modelProgressStatusText(percent));
        }, error -> onRealtimeModelChanged(error, false));
    }

    private void onRealtimeModelChanged(String error, boolean deleted) {
        if (isFinishing() || isDestroyed()) return;
        setRealtimeModelStatus();
        if (TextUtils.isEmpty(error)) Notify.show(deleted ? R.string.subtitle_realtime_model_delete_success : R.string.subtitle_realtime_model_download_success);
        else Notify.show(getString(deleted ? R.string.subtitle_realtime_model_delete_failed : R.string.subtitle_realtime_model_download_failed, error));
    }

    private void setRealtimeModelStatus() {
        RealtimeSubtitleController controller = RealtimeSubtitleController.get();
        if (controller.isModelDownloading() && controller.getModelDownloadProgress() > 0) {
            mBinding.subtitleRealtimeModelText.setText(modelProgressStatusText(controller.getModelDownloadProgress()));
            return;
        }
        int status = controller.isModelDeleting() ? R.string.subtitle_realtime_model_deleting : controller.isModelDownloading() ? R.string.subtitle_realtime_model_downloading : controller.isModelReady() ? R.string.subtitle_realtime_model_ready : R.string.subtitle_realtime_model_missing;
        mBinding.subtitleRealtimeModelText.setText(modelStatusText(status));
    }

    private String modelProgressStatusText(int progress) {
        int index = getRealtimeModelIndex();
        String label = realtimeModelLabels != null && index < realtimeModelLabels.length ? realtimeModelLabels[index] : Setting.getRealtimeSubtitleModel();
        return getString(R.string.subtitle_realtime_model_status, label, getString(R.string.subtitle_realtime_model_downloading_progress, progress));
    }

    private String modelStatusText(int status) {
        int index = getRealtimeModelIndex();
        String label = realtimeModelLabels != null && index < realtimeModelLabels.length ? realtimeModelLabels[index] : Setting.getRealtimeSubtitleModel();
        return getString(R.string.subtitle_realtime_model_status, label, getString(status));
    }

    private int getRealtimeModelIndex() {
        if (realtimeModelValues == null) return 0;
        for (int i = 0; i < realtimeModelValues.length; i++) if (TextUtils.equals(realtimeModelValues[i], Setting.getRealtimeSubtitleModel())) return i;
        return 0;
    }

    private void onSubtitleAiMaxConcurrency(View view) {
        SubtitleSettingsDialog.showNumber(this, R.string.player_subtitle_ai_max_concurrency, Setting.getSubtitleAiMaxConcurrency(), 1, 8, value -> {
            Setting.putSubtitleAiMaxConcurrency(value);
            mBinding.subtitleAiMaxConcurrencyText.setText(String.valueOf(Setting.getSubtitleAiMaxConcurrency()));
        });
    }

    private void onSubtitleAiChunkCount(View view) {
        SubtitleSettingsDialog.showNumber(this, R.string.player_subtitle_ai_chunk_count, Setting.getSubtitleAiChunkCount(), 1, 32, value -> {
            Setting.putSubtitleAiChunkCount(value);
            mBinding.subtitleAiChunkCountText.setText(String.valueOf(Setting.getSubtitleAiChunkCount()));
        });
    }

    private void setAiSubtitleSettings() {
        boolean visible = Setting.isAiConfigReady();
        mBinding.subtitleAiSettings.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.subtitleAiMaxConcurrencyText.setText(String.valueOf(Setting.getSubtitleAiMaxConcurrency()));
        mBinding.subtitleAiChunkCountText.setText(String.valueOf(Setting.getSubtitleAiChunkCount()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        setText();
    }
}
