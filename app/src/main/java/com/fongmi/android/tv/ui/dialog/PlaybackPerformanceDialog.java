package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.setting.PlaybackPerformanceCatalog;
import com.fongmi.android.tv.setting.PlaybackPerformanceOption;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlaybackPerformanceUiPolicy;
import com.fongmi.android.tv.setting.PlaybackProfileMergePolicy;
import com.fongmi.android.tv.setting.MpvPerformanceSetting;
import com.fongmi.android.tv.setting.IjkPerformanceSetting;
import com.fongmi.android.tv.setting.ExoPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textview.MaterialTextView;

import is.xyz.mpv.MPVLib;

public final class PlaybackPerformanceDialog extends DialogFragment {

    private Runnable callback;
    private Dialog helpDialog;
    private Dialog modalDialog;
    private LinearLayout list;
    private TabLayout profileTabs;
    private boolean syncingProfileTabs;

    public static void show(Fragment fragment, Runnable callback) {
        PlaybackPerformanceDialog dialog = new PlaybackPerformanceDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), PlaybackPerformanceDialog.class.getSimpleName());
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        PlaybackPerformanceDialog dialog = new PlaybackPerformanceDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), PlaybackPerformanceDialog.class.getSimpleName());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        PlaybackPerformanceSetting.ensureInitialized();
        Dialog dialog = new Dialog(requireActivity(), R.style.Theme_WebHTV_LightDialog);
        dialog.setContentView(createView(LayoutInflater.from(requireContext())));
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(requireContext()) * (ResUtil.isLand(requireContext()) ? 0.58f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    private View createView(LayoutInflater inflater) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.shape_shell_proxy_dialog);
        root.setPadding(dp(22), dp(22), dp(22), dp(18));

        LinearLayout titleBar = new LinearLayout(requireContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);

        MaterialTextView title = new MaterialTextView(requireContext());
        title.setText(getString(R.string.player_performance) + " · " + playerName());
        title.setTextColor(Color.parseColor("#202124"));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialButton reset = actionButton(R.string.dialog_reset, view -> reset());
        reset.setTextSize(13);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        resetParams.leftMargin = dp(8);
        titleBar.addView(reset, resetParams);

        MaterialButton help = actionButton(R.string.player_performance_help, view -> showHelpDialog());
        help.setTextSize(13);
        help.setContentDescription(getString(R.string.player_performance_help_title));
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        helpParams.leftMargin = dp(8);
        titleBar.addView(help, helpParams);
        root.addView(titleBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        profileTabs = createProfileTabs();
        LinearLayout.LayoutParams tabLayout = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        tabLayout.topMargin = dp(12);
        root.addView(profileTabs, tabLayout);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.min(dp(460), Math.max(dp(300), ResUtil.getScreenHeight(requireContext()) * 2 / 3)));
        scrollParams.topMargin = dp(16);
        root.addView(scroll, scrollParams);
        refreshRows();
        return root;
    }

    private void showHelpDialog() {
        if (helpDialog != null && helpDialog.isShowing()) return;

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.shape_shell_proxy_dialog);
        root.setPadding(dp(22), dp(20), dp(22), dp(18));

        LinearLayout titleBar = new LinearLayout(requireContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);

        MaterialTextView title = new MaterialTextView(requireContext());
        title.setText(getString(R.string.player_performance_help_title) + " · " + playerName());
        title.setTextColor(Color.parseColor("#202124"));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialButton close = closeButton(view -> {
            if (helpDialog != null) helpDialog.dismiss();
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        closeParams.leftMargin = dp(12);
        titleBar.addView(close, closeParams);
        root.addView(titleBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(12), dp(4), dp(8));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.min(dp(560), ResUtil.getScreenHeight(requireContext()) * 3 / 5));
        scrollParams.topMargin = dp(10);
        root.addView(scroll, scrollParams);

        PlaybackPerformanceUiPolicy.Split split = optionSplit();
        addHelpIntro(content, getString(
                R.string.player_performance_help_intro, playerName()));
        if (split.profile() != null) {
            addHelpSection(content,
                    getString(R.string.player_performance_common_section));
            addHelpItem(content, split.profile().title(),
                    split.profile().description());
        }
        for (PlaybackPerformanceOption option : split.common()) {
            addHelpItem(content, option.title(), option.description());
        }
        String section = "";
        for (PlaybackPerformanceOption option : split.advanced()) {
            if (!section.equals(option.section())) {
                section = option.section();
                addHelpSection(content, section);
            }
            addHelpItem(content, option.title(), option.description());
        }

        Dialog dialog = new Dialog(requireContext(), R.style.Theme_WebHTV_LightDialog);
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(ignored -> resizeHelpDialog(dialog));
        dialog.setOnDismissListener(ignored -> {
            if (helpDialog == dialog) helpDialog = null;
        });
        helpDialog = dialog;
        dialog.show();
    }

    private void resizeHelpDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(requireContext()) * (ResUtil.isLand(requireContext()) ? 0.66f : 0.94f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.6f;
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    @Override
    public void onDestroyView() {
        if (helpDialog != null) helpDialog.dismiss();
        if (modalDialog != null) modalDialog.dismiss();
        helpDialog = null;
        modalDialog = null;
        super.onDestroyView();
    }

    private void addHelpIntro(LinearLayout content, String text) {
        MaterialTextView intro = new MaterialTextView(requireContext());
        intro.setText(text);
        intro.setTextColor(Color.parseColor("#3C4043"));
        intro.setTextSize(13);
        intro.setLineSpacing(dp(3), 1f);
        content.addView(intro, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addHelpSection(LinearLayout content, String text) {
        MaterialTextView section = new MaterialTextView(requireContext());
        section.setText(text);
        section.setTextColor(Color.parseColor("#174EA6"));
        section.setTextSize(15);
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        params.bottomMargin = dp(6);
        content.addView(section, params);
    }

    private void addHelpItem(LinearLayout content, String title, String description) {
        MaterialTextView name = new MaterialTextView(requireContext());
        name.setText(title);
        name.setTextColor(Color.parseColor("#202124"));
        name.setTextSize(14);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialTextView detail = new MaterialTextView(requireContext());
        detail.setText(description);
        detail.setTextColor(Color.parseColor("#5F6368"));
        detail.setTextSize(13);
        detail.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(2);
        params.bottomMargin = dp(10);
        content.addView(detail, params);
    }

    private MaterialButton actionButton(int text, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setAllCaps(false);
        button.setText(text);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14);
        button.setIncludeFontPadding(false);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                button, 10, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(36));
        button.setMinimumHeight(dp(36));
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setInsetLeft(0);
        button.setInsetRight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        button.setCornerRadius(dp(6));
        button.setTextColor(ColorStateList.valueOf(Color.parseColor("#174EA6")));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#8AB4F8")));
        button.setStrokeWidth(dp(1));
        button.setOnFocusChangeListener((view, hasFocus) -> styleAction(button, hasFocus));
        button.setOnClickListener(listener);
        return button;
    }

    private MaterialButton closeButton(View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText("×");
        button.setTextSize(20);
        button.setContentDescription(getString(R.string.player_performance_help_close));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(32));
        button.setMinimumHeight(dp(32));
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setInsetLeft(0);
        button.setInsetRight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_bg));
        button.setTextColor(Color.parseColor("#5F6368"));
        button.setOnClickListener(listener);
        return button;
    }

    private void styleAction(MaterialButton button, boolean focused) {
        button.setTextColor(ColorStateList.valueOf(Color.parseColor(focused ? "#FFFFFF" : "#174EA6")));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(focused ? "#1A73E8" : "#FFFFFF")));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor(focused ? "#1A73E8" : "#8AB4F8")));
        button.setStrokeWidth(dp(1));
    }

    private void apply(int profile) {
        if (profile == PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT) {
            PlaybackPerformanceSetting.applyLightweight();
        } else {
            PlaybackPerformanceSetting.applyAuto();
        }
        refresh();
    }

    private void refresh() {
        refreshRows();
        syncProfileTabs();
        ConfigEvent.playerPerformance();
        if (callback != null) callback.run();
    }

    private void reset() {
        PlaybackPerformanceSetting.applyAuto();
        refresh();
    }

    private TabLayout createProfileTabs() {
        TabLayout tabs = new TabLayout(requireContext());
        tabs.setBackgroundColor(Color.TRANSPARENT);
        tabs.setTabMode(TabLayout.MODE_FIXED);
        tabs.setTabGravity(TabLayout.GRAVITY_FILL);
        tabs.setSelectedTabIndicatorColor(Color.parseColor("#1A73E8"));
        tabs.setTabTextColors(Color.parseColor("#5F6368"), Color.parseColor("#1A73E8"));
        tabs.setTabRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        tabs.setUnboundedRipple(false);
        tabs.setFocusable(false);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (syncingProfileTabs) return;
                apply(profileAt(tab.getPosition()));
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                if (syncingProfileTabs) return;
                if (profileAt(tab.getPosition())
                        == PlaybackPerformanceSetting.PROFILE_AUTO) {
                    apply(PlaybackPerformanceSetting.PROFILE_AUTO);
                }
            }
        });
        syncProfileTabs(tabs);
        return tabs;
    }

    private void configureProfileTabFocus(TabLayout tabs) {
        if (!Util.isLeanback() || tabs.getChildCount() == 0) return;
        View strip = tabs.getChildAt(0);
        if (!(strip instanceof ViewGroup tabStrip)) return;
        for (int i = 0; i < tabStrip.getChildCount(); i++) {
            View tab = tabStrip.getChildAt(i);
            tab.setFocusable(true);
            tab.setFocusableInTouchMode(true);
            tab.setBackgroundResource(R.drawable.selector_mpv_tab_focus);
        }
    }

    private void syncProfileTabs() {
        if (profileTabs != null) syncProfileTabs(profileTabs);
    }

    private void syncProfileTabs(TabLayout tabs) {
        syncingProfileTabs = true;
        int[] profiles = PlaybackProfileMergePolicy.selectableProfiles(
                PlaybackPerformanceSetting.isRecommendedMerged());
        boolean rebuilt = !profileTabsMatch(tabs, profiles);
        if (rebuilt) {
            tabs.removeAllTabs();
            for (int profile : profiles) {
                tabs.addTab(tabs.newTab()
                        .setText(profileLabel(profile))
                        .setTag(profile), false);
            }
        }
        for (int index = 0; index < profiles.length; index++) {
            TabLayout.Tab tab = tabs.getTabAt(index);
            if (tab != null) tab.setText(profileLabel(profiles[index]));
        }
        int position = profilePosition(PlaybackPerformanceSetting.getProfile());
        tabs.selectTab(position < 0 ? null : tabs.getTabAt(position));
        syncingProfileTabs = false;
        if (rebuilt) tabs.post(() -> configureProfileTabFocus(tabs));
    }

    private boolean profileTabsMatch(TabLayout tabs, int[] profiles) {
        if (tabs.getTabCount() != profiles.length) return false;
        for (int index = 0; index < profiles.length; index++) {
            TabLayout.Tab tab = tabs.getTabAt(index);
            if (tab == null
                    || !Integer.valueOf(profiles[index]).equals(tab.getTag())) {
                return false;
            }
        }
        return true;
    }

    private int profileAt(int position) {
        int[] profiles = PlaybackProfileMergePolicy.selectableProfiles(
                PlaybackPerformanceSetting.isRecommendedMerged());
        return position >= 0 && position < profiles.length
                ? profiles[position]
                : PlaybackPerformanceSetting.PROFILE_AUTO;
    }

    private int profilePosition(int profile) {
        return PlaybackProfileMergePolicy.positionOf(
                profile,
                PlaybackPerformanceSetting.isRecommendedMerged());
    }

    private CharSequence profileLabel(int profile) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT ->
                    getString(R.string.player_performance_lightweight);
            default -> {
                int overrideCount = PlaybackPerformanceSetting.getOverrideCount(
                        PlayerSetting.getPlayer());
                yield overrideCount == 0
                        ? getString(R.string.player_performance_auto)
                        : getString(R.string.player_performance_auto)
                        + " · 已改" + overrideCount + "项";
            }
        };
    }

    private void refreshRows() {
        if (list == null) return;
        list.removeAllViews();
        PlaybackPerformanceUiPolicy.Split split = optionSplit();
        addHeader(getString(R.string.player_performance_common_section));
        for (PlaybackPerformanceOption option : split.common()) {
            addRow(option.id(), option.title(), optionValue(option.id()),
                    optionAction(option.id()));
        }
        String section = "";
        for (PlaybackPerformanceOption option : split.advanced()) {
            if (!section.equals(option.section())) {
                section = option.section();
                addHeader(section);
            }
            addRow(option.id(), option.title(), optionValue(option.id()),
                    optionAction(option.id()));
        }
    }

    private void showConfirmDialog(
            int title,
            int message,
            int confirm,
            Runnable action) {
        showModal(PlaybackPerformanceModal.confirm(
                requireContext(),
                getString(title),
                getString(message),
                getString(R.string.dialog_cancel),
                getString(confirm),
                action));
    }

    private void showModal(Dialog dialog) {
        if (dialog == null) return;
        if (modalDialog != null) modalDialog.dismiss();
        modalDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (modalDialog == dialog) modalDialog = null;
        });
        dialog.show();
    }

    private String playerName() {
        return switch (PlayerSetting.getPlayer()) {
            case PlayerSetting.IJK -> "IJK";
            case PlayerSetting.MPV -> "MPV";
            default -> "EXO";
        };
    }

    private PlaybackPerformanceUiPolicy.Split optionSplit() {
        return PlaybackPerformanceUiPolicy.splitForKernel(
                PlayerSetting.getPlayer());
    }

    private String optionValue(String id) {
        return switch (id) {
            case PlaybackPerformanceCatalog.PROFILE -> PlaybackPerformanceSetting.getProfileName();
            case PlaybackPerformanceCatalog.RENDER -> renderText();
            case PlaybackPerformanceCatalog.TRACK_LIMIT -> onOff(PlaybackPerformanceSetting.isTrackLimitEnabled());
            case PlaybackPerformanceCatalog.ADAPTIVE_DOWNGRADE -> onOff(PlaybackPerformanceSetting.isAdaptiveDowngradeEnabled());
            case PlaybackPerformanceCatalog.BANDWIDTH_METER -> onOff(PlaybackPerformanceSetting.isBandwidthMeterEnabled());
            case PlaybackPerformanceCatalog.TUNNEL -> onOff(PlayerSetting.isTunnel());
            case PlaybackPerformanceCatalog.BUFFER_TIME -> PlaybackPerformanceSetting.getForwardBufferText();
            case PlaybackPerformanceCatalog.BUFFER_BYTES -> PlaybackPerformanceSetting.getMemoryBufferText();
            case PlaybackPerformanceCatalog.BACK_BUFFER -> PlaybackPerformanceSetting.getPlayedDataRetentionText();
            case PlaybackPerformanceCatalog.PLAY_CACHE -> PlaybackPerformanceSetting.getPlaybackDiskCacheText();
            case PlaybackPerformanceCatalog.LOAD_SELECTED_TRACKS -> onOff(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled());
            case PlaybackPerformanceCatalog.PRELOAD -> PlaybackPerformanceSetting.isAuto(id) ? "自动 · 按资源" : onOff(PreloadSetting.isPreload());
            case PlaybackPerformanceCatalog.PRELOAD_THREADS -> PlaybackPerformanceSetting.isAuto(id) ? "自动 · 0～2 条" : PreloadSetting.getPreloadThreads() + " 条";
            case PlaybackPerformanceCatalog.PRELOAD_SIZE -> FileUtil.byteCountToDisplaySize(PreloadSetting.getPreloadSizeBytes());
            case PlaybackPerformanceCatalog.PRELOAD_TIME -> PlaybackPerformanceSetting.isAuto(id) ? "自动 · 单次10～30秒" : "单次" + PreloadSetting.getPreloadTimeSeconds() + "秒";
            case PlaybackPerformanceCatalog.PRELOAD_AHEAD -> preloadAheadText();
            case PlaybackPerformanceCatalog.PRELOAD_PAUSE -> pausePreloadText();
            case PlaybackPerformanceCatalog.CODEC_ASYNC -> ExoPerformanceSetting.getCodecQueueText();
            case PlaybackPerformanceCatalog.DYNAMIC_SCHEDULING -> onOff(PlaybackPerformanceSetting.isDynamicSchedulingEnabled());
            case PlaybackPerformanceCatalog.DURATION_PROGRESS -> ExoPerformanceSetting.getCodecQueueMode() == ExoPerformanceSetting.CODEC_QUEUE_SYNC ? "同步队列不可用" : onOff(PlaybackPerformanceSetting.isVideoDurationProgressEnabled());
            case PlaybackPerformanceCatalog.LATE_DROP -> onOff(PlaybackPerformanceSetting.isLateDropInputEnabled());
            case PlaybackPerformanceCatalog.SURFACE_FIXED_SIZE -> onOff(PlaybackPerformanceSetting.isSurfaceFixedSizeEnabled());
            case PlaybackPerformanceCatalog.DECODER_FALLBACK -> onOff(PlaybackPerformanceSetting.isDecoderFallbackEnabled());
            case PlaybackPerformanceCatalog.DV7_HDR10_FALLBACK ->
                    PlayerSetting.getPlayer() == PlayerSetting.MPV
                            ? PlaybackPerformanceSetting.getMpvDv7HandlingText()
                            : PlaybackPerformanceSetting.getDv7HandlingText();
            case PlaybackPerformanceCatalog.DEFERRED_CUES -> onOff(PlaybackPerformanceSetting.isDeferredCuesEnabled());
            case PlaybackPerformanceCatalog.SOFT_VIDEO_TUNE -> onOff(PlaybackPerformanceSetting.isSoftVideoTuneEnabled());
            case PlaybackPerformanceCatalog.AUDIO_PASSTHROUGH -> onOff(PlayerSetting.isAudioPassThrough());
            case PlaybackPerformanceCatalog.PREFER_AAC -> onOff(PlayerSetting.isPreferAAC());
            case PlaybackPerformanceCatalog.AUDIO_SOFT_PREFER -> onOff(PlayerSetting.isAudioPrefer());
            case PlaybackPerformanceCatalog.VIDEO_SOFT_PREFER -> onOff(PlayerSetting.isVideoPrefer());
            case PlaybackPerformanceCatalog.MPV_OUTPUT -> MpvPerformanceSetting.getOutputModeText();
            case PlaybackPerformanceCatalog.MPV_RENDER -> mpvRenderText();
            case PlaybackPerformanceCatalog.MPV_VULKAN_BACKEND -> MpvPerformanceSetting.getVulkanBackendText();
            case PlaybackPerformanceCatalog.MPV_HWDEC -> MpvPerformanceSetting.getHwdecText();
            case PlaybackPerformanceCatalog.MPV_FRAME_RATE -> MpvPerformanceSetting.getFrameRateText();
            case PlaybackPerformanceCatalog.MPV_HLS_BITRATE -> MpvPerformanceSetting.getHlsBitrateText();
            case PlaybackPerformanceCatalog.MPV_REBUFFER -> formatSeconds(MpvPerformanceSetting.getRebufferMs());
            case PlaybackPerformanceCatalog.MPV_OPTION_PRIORITY -> MpvPerformanceSetting.getOptionPriorityText();
            case PlaybackPerformanceCatalog.MPV_SYNC -> MpvPerformanceSetting.getSyncText();
            case PlaybackPerformanceCatalog.MPV_FRAME_DROP -> MpvPerformanceSetting.getFrameDropText();
            case PlaybackPerformanceCatalog.MPV_INTERPOLATION -> onOff(MpvPerformanceSetting.isInterpolation());
            case PlaybackPerformanceCatalog.MPV_SOFT_TUNE -> MpvPerformanceSetting.getSoftTuneText();
            case PlaybackPerformanceCatalog.MPV_VERBOSE_LOG -> MpvPerformanceSetting.isVerboseLog() ? "详细" : "正常";
            case PlaybackPerformanceCatalog.IJK_SCENE -> IjkPerformanceSetting.getSceneText();
            case PlaybackPerformanceCatalog.IJK_BUFFER -> ijkBufferText();
            case PlaybackPerformanceCatalog.IJK_PACKET_BUFFERING -> onOff(IjkPerformanceSetting.isPacketBuffering());
            case PlaybackPerformanceCatalog.IJK_WATER -> PlaybackPerformanceSetting.isAuto(PlayerSetting.IJK, id)
                    ? "自动 · 0.1～5秒" : IjkPerformanceSetting.getWaterText();
            case PlaybackPerformanceCatalog.IJK_PICTURE_QUEUE -> PlaybackPerformanceSetting.isAuto(PlayerSetting.IJK, id)
                    ? "自动 · 3帧" : IjkPerformanceSetting.getPictureQueue() + "帧";
            case PlaybackPerformanceCatalog.IJK_FRAME_DROP -> IjkPerformanceSetting.getDropText();
            case PlaybackPerformanceCatalog.IJK_ACCURATE_SEEK -> onOff(IjkPerformanceSetting.isAccurateSeek());
            case PlaybackPerformanceCatalog.IJK_PROBE -> IjkPerformanceSetting.getProbeText();
            case PlaybackPerformanceCatalog.IJK_SOFT_TUNE -> PlaybackPerformanceSetting.isAuto(PlayerSetting.IJK, id)
                    ? "自动 · 关闭～积极" : IjkPerformanceSetting.getSoftTuneText();
            case PlaybackPerformanceCatalog.IJK_RTSP_TRANSPORT -> IjkPerformanceSetting.getRtspTransportText();
            case PlaybackPerformanceCatalog.IJK_RECONNECT -> onOff(IjkPerformanceSetting.isReconnect());
            case PlaybackPerformanceCatalog.EXO_FRAME_RATE -> ExoPerformanceSetting.getFrameRateText();
            case PlaybackPerformanceCatalog.EXO_START_BUFFER -> PlaybackPerformanceSetting.getExoStartBufferText();
            case PlaybackPerformanceCatalog.EXO_REBUFFER -> PlaybackPerformanceSetting.getExoRebufferText();
            case PlaybackPerformanceCatalog.EXO_PRIORITIZE_TIME -> PlaybackPerformanceSetting.getExoPrioritizeTimeText();
            case PlaybackPerformanceCatalog.EXO_NETWORK_PROTECTION -> ExoPerformanceSetting.getNetworkProtectionText();
            default -> "";
        };
    }

    private Runnable optionAction(String id) {
        return switch (id) {
            case PlaybackPerformanceCatalog.PROFILE -> null;
            case PlaybackPerformanceCatalog.RENDER -> this::toggleRender;
            case PlaybackPerformanceCatalog.TRACK_LIMIT -> () -> toggle(PlaybackPerformanceSetting::isTrackLimitEnabled, PlaybackPerformanceSetting::putTrackLimitEnabled);
            case PlaybackPerformanceCatalog.ADAPTIVE_DOWNGRADE -> () -> toggle(PlaybackPerformanceSetting::isAdaptiveDowngradeEnabled, PlaybackPerformanceSetting::putAdaptiveDowngradeEnabled);
            case PlaybackPerformanceCatalog.BANDWIDTH_METER -> () -> toggle(PlaybackPerformanceSetting::isBandwidthMeterEnabled, PlaybackPerformanceSetting::putBandwidthMeterEnabled);
            case PlaybackPerformanceCatalog.TUNNEL -> () -> {
                PlayerSetting.putTunnel(!PlayerSetting.isTunnel());
                PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.TUNNEL);
                refresh();
            };
            case PlaybackPerformanceCatalog.BUFFER_TIME -> this::cycleBuffer;
            case PlaybackPerformanceCatalog.BUFFER_BYTES -> this::cycleBufferBytes;
            case PlaybackPerformanceCatalog.BACK_BUFFER -> this::cycleBackBuffer;
            case PlaybackPerformanceCatalog.PLAY_CACHE -> this::cyclePlayCache;
            case PlaybackPerformanceCatalog.LOAD_SELECTED_TRACKS -> () -> toggle(PlaybackPerformanceSetting::isLoadOnlySelectedTracksEnabled, PlaybackPerformanceSetting::putLoadOnlySelectedTracksEnabled);
            case PlaybackPerformanceCatalog.PRELOAD -> () -> {
                PreloadSetting.putPreload(!PreloadSetting.isPreload());
                PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.PRELOAD);
                refresh();
            };
            case PlaybackPerformanceCatalog.PRELOAD_THREADS -> this::cyclePreloadThreads;
            case PlaybackPerformanceCatalog.PRELOAD_SIZE -> this::cyclePreloadSize;
            case PlaybackPerformanceCatalog.PRELOAD_TIME -> this::cyclePreloadTime;
            case PlaybackPerformanceCatalog.PRELOAD_AHEAD -> this::cyclePreloadAhead;
            case PlaybackPerformanceCatalog.PRELOAD_PAUSE -> this::cyclePausePreload;
            case PlaybackPerformanceCatalog.CODEC_ASYNC -> () -> {
                ExoPerformanceSetting.putCodecQueueMode((ExoPerformanceSetting.getCodecQueueMode() + 1) % 3);
                refresh();
            };
            case PlaybackPerformanceCatalog.DYNAMIC_SCHEDULING -> () -> toggle(PlaybackPerformanceSetting::isDynamicSchedulingEnabled, PlaybackPerformanceSetting::putDynamicSchedulingEnabled);
            case PlaybackPerformanceCatalog.DURATION_PROGRESS -> ExoPerformanceSetting.getCodecQueueMode() == ExoPerformanceSetting.CODEC_QUEUE_SYNC ? null : () -> toggle(PlaybackPerformanceSetting::isVideoDurationProgressEnabled, PlaybackPerformanceSetting::putVideoDurationProgressEnabled);
            case PlaybackPerformanceCatalog.LATE_DROP -> () -> toggle(PlaybackPerformanceSetting::isLateDropInputEnabled, PlaybackPerformanceSetting::putLateDropInputEnabled);
            case PlaybackPerformanceCatalog.SURFACE_FIXED_SIZE -> () -> toggle(PlaybackPerformanceSetting::isSurfaceFixedSizeEnabled, PlaybackPerformanceSetting::putSurfaceFixedSizeEnabled);
            case PlaybackPerformanceCatalog.DECODER_FALLBACK -> () -> toggle(PlaybackPerformanceSetting::isDecoderFallbackEnabled, PlaybackPerformanceSetting::putDecoderFallbackEnabled);
            case PlaybackPerformanceCatalog.DV7_HDR10_FALLBACK -> () -> {
                if (PlayerSetting.getPlayer() == PlayerSetting.MPV) {
                    int mode = PlaybackPerformanceSetting.getMpvDv7HandlingMode();
                    PlaybackPerformanceSetting.putMpvDv7HandlingMode(
                            mode == PlaybackPerformanceSetting.DV7_HANDLING_P81
                                    ? PlaybackPerformanceSetting.DV7_HANDLING_HDR10
                                    : PlaybackPerformanceSetting.DV7_HANDLING_P81);
                    refresh();
                    return;
                }
                int mode = PlaybackPerformanceSetting.getDv7HandlingMode();
                PlaybackPerformanceSetting.putDv7HandlingMode(
                        mode == PlaybackPerformanceSetting.DV7_HANDLING_P81
                                ? PlaybackPerformanceSetting.DV7_HANDLING_HDR10
                                : PlaybackPerformanceSetting.DV7_HANDLING_P81);
                refresh();
            };
            case PlaybackPerformanceCatalog.DEFERRED_CUES -> () -> toggle(PlaybackPerformanceSetting::isDeferredCuesEnabled, PlaybackPerformanceSetting::putDeferredCuesEnabled);
            case PlaybackPerformanceCatalog.SOFT_VIDEO_TUNE -> () -> toggle(PlaybackPerformanceSetting::isSoftVideoTuneEnabled, PlaybackPerformanceSetting::putSoftVideoTuneEnabled);
            case PlaybackPerformanceCatalog.AUDIO_PASSTHROUGH -> () -> togglePlayer(id, PlayerSetting::isAudioPassThrough, PlayerSetting::putAudioPassThrough);
            case PlaybackPerformanceCatalog.PREFER_AAC -> () -> togglePlayer(id, PlayerSetting::isPreferAAC, PlayerSetting::putPreferAAC);
            case PlaybackPerformanceCatalog.AUDIO_SOFT_PREFER -> () -> togglePlayer(id, PlayerSetting::isAudioPrefer, PlayerSetting::putAudioPrefer);
            case PlaybackPerformanceCatalog.VIDEO_SOFT_PREFER -> () -> togglePlayer(id, PlayerSetting::isVideoPrefer, PlayerSetting::putVideoPrefer);
            case PlaybackPerformanceCatalog.MPV_OUTPUT -> () -> {
                MpvPerformanceSetting.putOutputMode((MpvPerformanceSetting.getOutputMode() + 1) % 3);
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_RENDER -> !isMpvVulkanAvailable() && PlayerSetting.getMpvRender() == PlayerSetting.MPV_RENDER_OPENGL ? null : () -> {
                PlayerSetting.putMpvRender(PlayerSetting.getMpvRender() == PlayerSetting.MPV_RENDER_OPENGL ? PlayerSetting.MPV_RENDER_VULKAN : PlayerSetting.MPV_RENDER_OPENGL);
                PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.MPV_RENDER);
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_VULKAN_BACKEND -> () -> {
                MpvPerformanceSetting.putVulkanBackend(
                        MpvPerformanceSetting.nextVulkanBackend());
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_HWDEC -> () -> {
                MpvPerformanceSetting.putHwdecMode((MpvPerformanceSetting.getHwdecMode() + 1) % 3);
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_FRAME_RATE -> () -> {
                MpvPerformanceSetting.putFrameRateMode((MpvPerformanceSetting.getFrameRateMode() + 1) % 2);
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_HLS_BITRATE -> () -> {
                MpvPerformanceSetting.putHlsBitrateMode((MpvPerformanceSetting.getHlsBitrateMode() + 1) % 4);
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_REBUFFER -> () -> {
                MpvPerformanceSetting.putRebufferMs(MpvPerformanceSetting.nextRebufferMs());
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_OPTION_PRIORITY -> () -> {
                MpvPerformanceSetting.putOptionPriority(MpvPerformanceSetting.isPerformancePriority() ? MpvPerformanceSetting.PRIORITY_CONFIG : MpvPerformanceSetting.PRIORITY_PERFORMANCE);
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_SYNC -> () -> {
                MpvPerformanceSetting.putSyncMode((MpvPerformanceSetting.getSyncMode() + 1) % 2);
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_FRAME_DROP -> () -> {
                MpvPerformanceSetting.putFrameDropMode((MpvPerformanceSetting.getFrameDropMode() + 1) % 3);
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_INTERPOLATION -> () -> {
                MpvPerformanceSetting.putInterpolation(!MpvPerformanceSetting.isInterpolation());
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_SOFT_TUNE -> () -> {
                MpvPerformanceSetting.putSoftTuneMode((MpvPerformanceSetting.getSoftTuneMode() + 1) % 3);
                refresh();
            };
            case PlaybackPerformanceCatalog.MPV_VERBOSE_LOG -> () -> {
                MpvPerformanceSetting.putVerboseLog(!MpvPerformanceSetting.isVerboseLog());
                refresh();
            };
            case PlaybackPerformanceCatalog.IJK_SCENE -> () -> {
                IjkPerformanceSetting.putScene((IjkPerformanceSetting.getScene() + 1) % 4);
                refresh();
            };
            case PlaybackPerformanceCatalog.IJK_BUFFER -> () -> {
                cycleBufferBytes();
            };
            case PlaybackPerformanceCatalog.IJK_PACKET_BUFFERING -> () -> {
                IjkPerformanceSetting.putPacketBuffering(!IjkPerformanceSetting.isPacketBuffering());
                refresh();
            };
            case PlaybackPerformanceCatalog.IJK_WATER -> () -> {
                IjkPerformanceSetting.putWaterMode((IjkPerformanceSetting.getWaterMode() + 1) % 3);
                refresh();
            };
            case PlaybackPerformanceCatalog.IJK_PICTURE_QUEUE -> () -> {
                int current = IjkPerformanceSetting.getPictureQueue();
                IjkPerformanceSetting.putPictureQueue(current == 3 ? 5 : current == 5 ? 8 : 3);
                refresh();
            };
            case PlaybackPerformanceCatalog.IJK_FRAME_DROP -> () -> {
                IjkPerformanceSetting.putDropMode((IjkPerformanceSetting.getDropMode() + 1) % 3);
                refresh();
            };
            case PlaybackPerformanceCatalog.IJK_ACCURATE_SEEK -> () -> {
                IjkPerformanceSetting.putAccurateSeek(!IjkPerformanceSetting.isAccurateSeek());
                refresh();
            };
            case PlaybackPerformanceCatalog.IJK_PROBE -> () -> {
                IjkPerformanceSetting.putProbeMode((IjkPerformanceSetting.getProbeMode() + 1) % 3);
                refresh();
            };
            case PlaybackPerformanceCatalog.IJK_SOFT_TUNE -> () -> {
                IjkPerformanceSetting.putSoftTuneMode((IjkPerformanceSetting.getSoftTuneMode() + 1) % 3);
                refresh();
            };
            case PlaybackPerformanceCatalog.IJK_RTSP_TRANSPORT -> () -> {
                IjkPerformanceSetting.putRtspTransport((IjkPerformanceSetting.getRtspTransport() + 1) % 3);
                refresh();
            };
            case PlaybackPerformanceCatalog.IJK_RECONNECT -> () -> {
                IjkPerformanceSetting.putReconnect(!IjkPerformanceSetting.isReconnect());
                refresh();
            };
            case PlaybackPerformanceCatalog.EXO_FRAME_RATE -> () -> {
                ExoPerformanceSetting.putFrameRateMode((ExoPerformanceSetting.getFrameRateMode() + 1) % 4);
                refresh();
            };
            case PlaybackPerformanceCatalog.EXO_START_BUFFER -> () -> {
                ExoPerformanceSetting.putStartBufferMs(ExoPerformanceSetting.nextStartBufferMs());
                refresh();
            };
            case PlaybackPerformanceCatalog.EXO_REBUFFER -> () -> {
                ExoPerformanceSetting.putRebufferMs(ExoPerformanceSetting.nextRebufferMs());
                refresh();
            };
            case PlaybackPerformanceCatalog.EXO_PRIORITIZE_TIME -> () -> {
                ExoPerformanceSetting.putPrioritizeTime(!ExoPerformanceSetting.isPrioritizeTime());
                refresh();
            };
            case PlaybackPerformanceCatalog.EXO_NETWORK_PROTECTION -> () -> {
                ExoPerformanceSetting.putNetworkProtectionMode(ExoPerformanceSetting.nextNetworkProtectionMode());
                refresh();
            };
            default -> null;
        };
    }

    private void toggle(java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
        setter.accept(!getter.getAsBoolean());
        refresh();
    }

    private void togglePlayer(String optionId, java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
        setter.accept(!getter.getAsBoolean());
        PlaybackPerformanceSetting.markOverride(optionId);
        refresh();
    }

    private void addHeader(String text) {
        MaterialTextView header = new MaterialTextView(requireContext());
        header.setText(text);
        header.setTextColor(Color.parseColor("#5F6368"));
        header.setTextSize(13);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
        params.topMargin = list.getChildCount() == 0 ? 0 : dp(8);
        list.addView(header, params);
    }

    private void addRow(String id, String label, String value, Runnable action) {
        boolean overridden = PlaybackPerformanceSetting.isOverridden(
                PlayerSetting.getPlayer(), id);
        MaterialButton button = new MaterialButton(requireContext());
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setSingleLine(false);
        button.setMinHeight(dp(46));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setText(label + "    " + value);
        button.setTextSize(14);
        button.setTextColor(ColorStateList.valueOf(Color.parseColor("#202124")));
        button.setBackgroundTintList(ColorStateList.valueOf(overridden
                ? Color.parseColor("#E8F0FE") : Color.WHITE));
        button.setCornerRadius(dp(6));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor(overridden
                ? "#8AB4F8" : "#C4C7C5")));
        button.setStrokeWidth(dp(1));
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        button.setEnabled(action != null);
        button.setOnFocusChangeListener((view, hasFocus) ->
                styleRow(button, action != null, overridden, hasFocus));
        if (action != null) button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.bottomMargin = dp(7);
        list.addView(button, params);
    }

    private void styleRow(MaterialButton button, boolean enabled,
                          boolean overridden, boolean focused) {
        int text = focused ? Color.WHITE : enabled ? Color.parseColor("#202124") : Color.parseColor("#5F6368");
        int bg = focused ? Color.parseColor("#1A73E8")
                : overridden ? Color.parseColor("#E8F0FE") : Color.WHITE;
        int stroke = focused ? Color.parseColor("#1A73E8")
                : overridden ? Color.parseColor("#8AB4F8")
                : Color.parseColor("#C4C7C5");
        button.setTextColor(ColorStateList.valueOf(text));
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setStrokeColor(ColorStateList.valueOf(stroke));
        button.setStrokeWidth(dp(focused ? 2 : 1));
    }

    private void toggleRender() {
        PlayerSetting.putRender(PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE ? PlayerSetting.RENDER_TEXTURE : PlayerSetting.RENDER_SURFACE);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.RENDER);
        refresh();
    }

    private void cycleBuffer() {
        PlayerSetting.putBuffer(PlayerSetting.getBuffer() >= 10 ? 1 : PlayerSetting.getBuffer() + 1);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.BUFFER_TIME);
        refresh();
    }

    private void cycleBufferBytes() {
        PlayerSetting.putBufferBytesOption((PlayerSetting.getBufferBytesOption() + 1) % 4);
        PlaybackPerformanceSetting.markOverride(PlayerSetting.getPlayer() == PlayerSetting.IJK
                ? PlaybackPerformanceCatalog.IJK_BUFFER
                : PlaybackPerformanceCatalog.BUFFER_BYTES);
        refresh();
    }

    private void cycleBackBuffer() {
        PlayerSetting.putBackBufferOption((PlayerSetting.getBackBufferOption() + 1) % 4);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.BACK_BUFFER);
        refresh();
    }

    private void cyclePlayCache() {
        PlayerSetting.putPlayCacheOption((PlayerSetting.getPlayCacheOption() + 1) % 5);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.PLAY_CACHE);
        refresh();
    }

    private void cyclePreloadThreads() {
        int value = PreloadSetting.getPreloadThreads() + 1;
        if (value > PreloadSetting.MAX_THREADS) value = PreloadSetting.MIN_THREADS;
        PreloadSetting.putPreloadThreads(value);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.PRELOAD_THREADS);
        refresh();
    }

    private void cyclePreloadSize() {
        PreloadSetting.putPreloadSizeMb(PreloadSetting.getNextPreloadSizeMb());
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.PRELOAD_SIZE);
        refresh();
    }

    private void cyclePreloadTime() {
        int value = PreloadSetting.getPreloadTimeSeconds() + PreloadSetting.STEP_TIME_SECONDS;
        if (value > PreloadSetting.MAX_TIME_SECONDS) value = PreloadSetting.MIN_TIME_SECONDS;
        PreloadSetting.putPreloadTimeSeconds(value);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.PRELOAD_TIME);
        refresh();
    }

    private void cyclePreloadAhead() {
        PreloadSetting.putPreloadAheadSeconds(PreloadSetting.getNextPreloadAheadSeconds());
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.PRELOAD_AHEAD);
        refresh();
    }

    private void cyclePausePreload() {
        PreloadSetting.putPausePreloadPolicy(PreloadSetting.getNextPausePreloadPolicy());
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.PRELOAD_PAUSE);
        refresh();
    }

    private String preloadAheadText() {
        int seconds = PreloadSetting.getPreloadAheadSeconds();
        return seconds == PreloadSetting.WHOLE_MEDIA_AHEAD_SECONDS
                ? "整部影片" : seconds / 60 + " 分钟";
    }

    private String ijkBufferText() {
        long configuredBytes = PlayerSetting.getBufferBytes(PlayerSetting.IJK);
        if (configuredBytes > 0) {
            return FileUtil.byteCountToDisplaySize(configuredBytes);
        }
        return PlaybackPerformanceSetting.isAuto(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_BUFFER)
                ? "自动 · 4～15MB" : IjkPerformanceSetting.getBufferMb() + "MB";
    }

    private String pausePreloadText() {
        return switch (PreloadSetting.getPausePreloadPolicy()) {
            case PreloadSetting.PAUSE_PRELOAD_ALWAYS -> "始终";
            default -> "仅 WiFi";
        };
    }

    private String renderText() {
        return PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE ? "SurfaceView" : "TextureView";
    }

    private String mpvRenderText() {
        boolean automatic = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV, PlaybackPerformanceCatalog.MPV_RENDER);
        if (PlayerSetting.getMpvRender() == PlayerSetting.MPV_RENDER_VULKAN) {
            return isMpvVulkanAvailable()
                    ? "Vulkan" : "Vulkan（实际回退 OpenGL）";
        }
        String value = isMpvVulkanAvailable()
                ? "OpenGL" : "OpenGL（Vulkan 不可用）";
        return automatic ? "自动 · " + value : value;
    }

    private boolean isMpvVulkanAvailable() {
        return MPVLib.isVulkanRendererAvailable(App.get());
    }

    private String onOff(boolean value) {
        return value ? "开" : "关";
    }

    private String formatSeconds(int milliseconds) {
        return milliseconds % 1000 == 0 ? milliseconds / 1000 + "秒" : String.format(java.util.Locale.US, "%.1f秒", milliseconds / 1000f);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
