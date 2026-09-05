package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.databinding.DialogControlBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.TmdbDetailActivity;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Timer;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.slider.Slider;

import java.util.Arrays;
import java.util.List;

public class ControlDialog extends BaseBottomSheetDialog implements ParseAdapter.OnClickListener {

    private final String[] scale;
    private DialogControlBinding binding;
    private Controls controls;
    private Listener listener;
    private List<TextView> speeds;
    private List<TextView> displays;
    private PlayerManager player;
    private History history;
    private boolean parse;
    private boolean ready;
    private int scrollBasePaddingBottom;

    public ControlDialog() {
        this.scale = ResUtil.getStringArray(R.array.select_scale);
    }

    public static ControlDialog create() {
        return new ControlDialog();
    }

    public ControlDialog parent(ActivityVideoBinding parent) {
        this.controls = new Controls(
                parent.getRoot(),
                parent.video,
                parent.control.fullscreen,
                parent.control.action.player,
                parent.control.action.decode,
                parent.control.action.playParams,
                parent.control.action.speed,
                parent.control.action.scale,
                parent.control.action.lut,
                parent.control.action.reset,
                parent.control.action.repeat,
                parent.control.action.text,
                parent.control.action.audio,
                parent.control.action.video,
                parent.control.action.opening,
                parent.control.action.ending,
                parent.control.action.danmaku,
                parent.control.action.title,
                parent.control.action.episodes
        );
        return this;
    }

    public ControlDialog inline(TmdbDetailActivity activity) {
        this.controls = new Controls(
                activity.findViewById(android.R.id.content),
                activity.findViewById(R.id.playerPanel),
                activity.inlineControlDialogControl(R.id.fullscreen),
                activity.inlineControlDialogAction(R.id.player),
                activity.inlineControlDialogAction(R.id.decode),
                null,
                activity.inlineControlDialogAction(R.id.speed),
                activity.inlineControlDialogAction(R.id.scale),
                activity.inlineControlDialogLutView(),
                activity.inlineControlDialogAction(R.id.reset),
                activity.inlineControlDialogAction(R.id.repeat),
                activity.inlineControlDialogAction(R.id.text),
                activity.inlineControlDialogAction(R.id.audio),
                activity.inlineControlDialogAction(R.id.video),
                activity.inlineControlDialogAction(R.id.opening),
                activity.inlineControlDialogAction(R.id.ending),
                activity.inlineControlDialogAction(R.id.danmaku),
                activity.inlineControlDialogAction(R.id.chapter),
                null
        );
        this.player = activity.inlineControlDialogPlayer();
        this.history = activity.inlineControlDialogHistory();
        this.parse = activity.inlineControlDialogUseParse();
        this.listener = new Listener() {
            @Override
            public ActivityVideoBinding getControlBinding() {
                return null;
            }

            @Override
            public PlayerManager getControlPlayer() {
                return player;
            }

            @Override
            public History getControlHistory() {
                return history;
            }

            @Override
            public boolean isControlParseEnabled() {
                return parse;
            }

            @Override
            public boolean isControlAudioContent() {
                return false;
            }

            @Override
            public void onScale(int tag) {
                activity.inlineControlDialogScale(tag);
            }

            @Override
            public void onEpisodeColumn(int column) {
            }

            @Override
            public void onCompactEpisodeTitleChanged() {
            }

            @Override
            public void onParse(Parse item) {
                activity.inlineControlDialogParse(item);
            }

            @Override
            public void onLutSelected(LutPreset preset) {
            }

            @Override
            public void onLutImport() {
            }

            @Override
            public void onLutDir() {
            }

            @Override
            public void onLutPanel() {
                dismiss();
                App.post(activity::inlineControlDialogLut, 200);
            }

            @Override
            public void onTrackPanel(int type) {
                activity.inlineControlDialogTrack(type);
            }

            @Override
            public void onTitlePanel() {
                activity.inlineControlDialogTitle();
            }

            @Override
            public void onDanmakuPanel() {
                activity.inlineControlDialogDanmaku();
            }

            @Override
            public void onDisplayChanged() {
                activity.inlineControlDialogDisplayChanged();
            }

            @Override
            public void onCodecCapabilityPanel() {
                CodecCapabilityDialog.show(activity, player);
            }

            @Override
            public void onPlayParamsPanel() {
            }

            @Override
            public void onImmersiveAudioModeChanged() {
            }

            @Override
            public void onKaraokeModeChanged() {
            }

            @Override
            public void onKaraokeTrackPanel() {
            }
        };
        return this;
    }

    public ControlDialog history(History history) {
        this.history = history;
        return this;
    }

    public ControlDialog parse(boolean parse) {
        this.parse = parse;
        return this;
    }

    public ControlDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public ControlDialog show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof ControlDialog) return this;
        if (listener == null && activity instanceof Listener) listener = (Listener) activity;
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        configureWindow(dialog);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow(getDialog());
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        WindowCompat.setDecorFitsSystemWindows(window, true);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        binding = DialogControlBinding.inflate(inflater, container, false);
        speeds = Arrays.asList(binding.speed05, binding.speed075, binding.speed10, binding.speed125, binding.speed15, binding.speed175, binding.speed20, binding.speed25, binding.speed30, binding.speed50);
        displays = Arrays.asList(binding.displayTime, binding.displayTraffic, binding.displaySize, binding.displayProgress, binding.displayMini, binding.displayTitle, binding.displayParams);
        return binding;
    }

    @Override
    protected void initView() {
        ready = resolveHostDependencies();
        if (!ready) {
            binding.getRoot().post(this::dismissAllowingStateLoss);
            return;
        }
        scrollBasePaddingBottom = binding.controlScroll.getPaddingBottom();
        setControlPadding();
        setSheetBackground();
        binding.decode.setText(controls.decode.getText());
        setLut();
        binding.ending.setText(controls.ending.getText());
        binding.opening.setText(controls.opening.getText());
        binding.repeat.setSelected(controls.repeat.isSelected());
        binding.immersiveAudio.setSelected(PlayerSetting.isImmersiveAudioMode());
        setImmersiveAudioVisible();
        binding.timer.setSelected(Timer.get().isRunning());
        setTrackVisible();
        setTitleVisible();
        setScaleText();
        setEpisodeColumn();
        setPlayer();
        setParse();
        setDisplaySettings();
        binding.controlScroll.post(() -> binding.controlScroll.scrollTo(0, 0));
    }

    private void setControlPadding() {
        int bottom = scrollBasePaddingBottom + getNavigationBottomInset();
        binding.controlScroll.setPaddingRelative(binding.controlScroll.getPaddingStart(), binding.controlScroll.getPaddingTop(), binding.controlScroll.getPaddingEnd(), bottom);
    }

    @Override
    protected void initEvent() {
        if (!ready) return;
        binding.timer.setOnClickListener(this::onTimer);
        binding.immersiveAudio.setOnClickListener(v -> setImmersiveAudio());
        binding.speed.addOnChangeListener(this::setSpeed);
        for (TextView view : speeds) view.setOnClickListener(this::setSpeedPreset);
        binding.scale.setOnClickListener(view -> VideoAspectModeDialog.show(requireActivity(), getScale(), this::setScale));
        for (int i = 0; i < displays.size(); i++) {
            int index = i;
            displays.get(i).setOnClickListener(v -> toggleDisplaySetting(index));
        }
        binding.reset.setOnClickListener(v -> dismiss(controls.reset));
        binding.text.setOnClickListener(v -> onTrack(binding.text));
        binding.audio.setOnClickListener(v -> onTrack(binding.audio));
        binding.video.setOnClickListener(v -> onTrack(binding.video));
        binding.episodeColumn1.setOnClickListener(v -> setEpisodeColumn(1));
        binding.episodeColumn2.setOnClickListener(v -> setEpisodeColumn(2));
        binding.compactEpisodeTitle.setOnClickListener(v -> setCompactEpisodeTitle());
        binding.title.setOnClickListener(v -> listener().onTitlePanel());
        binding.player.setOnClickListener(v -> click(binding.player, controls.player));
        binding.danmaku.setOnClickListener(v -> listener().onDanmakuPanel());
        binding.repeat.setOnClickListener(v -> active(binding.repeat, controls.repeat));
        binding.decode.setOnClickListener(v -> click(binding.decode, controls.decode));
        binding.codecCapability.setOnClickListener(v -> listener().onCodecCapabilityPanel());
        binding.panDiagnostic.setOnClickListener(v -> onPanDiagnostic());
        binding.lut.setOnClickListener(v -> onLut());
        binding.ending.setOnClickListener(v -> click(binding.ending, controls.ending));
        binding.opening.setOnClickListener(v -> click(binding.opening, controls.opening));
        binding.player.setOnLongClickListener(v -> longClick(binding.player, controls.player));
        binding.ending.setOnLongClickListener(v -> longClick(binding.ending, controls.ending));
        binding.opening.setOnLongClickListener(v -> longClick(binding.opening, controls.opening));
    }

    private void onTimer(View view) {
        TimerDialog.create().show(getActivity());
    }

    private void onPanDiagnostic() {
        FragmentActivity activity = getActivity();
        PlayerManager current = player;
        if (activity == null || current == null) return;
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        if (activity.isFinishing() || activity.isDestroyed() || fragmentManager.isStateSaved() || current.isReleased()) return;
        dismissAllowingStateLoss();
        App.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed() || fragmentManager.isStateSaved() || current.isReleased()) return;
            PanNetworkDiagnosticDialog.show(activity, current);
        }, 140);
    }

    private void setImmersiveAudio() {
        PlayerSetting.putImmersiveAudioMode(!PlayerSetting.isImmersiveAudioMode());
        binding.immersiveAudio.setSelected(PlayerSetting.isImmersiveAudioMode());
        ((Listener) requireActivity()).onImmersiveAudioModeChanged();
    }

    private void onTrack(View view) {
        listener().onTrackPanel(Integer.parseInt(view.getTag().toString()));
    }

    private void setSheetBackground() {
        binding.sheetWall.setVisibility(View.GONE);
    }

    private void setDisplaySettings() {
        boolean[] checked = PlayerSetting.getDisplayChecked();
        for (int i = 0; i < displays.size(); i++) displays.get(i).setSelected(i < checked.length && checked[i]);
    }

    private void toggleDisplaySetting(int index) {
        boolean[] checked = PlayerSetting.getDisplayChecked();
        if (index < 0 || index >= checked.length) return;
        checked[index] = !checked[index];
        PlayerSetting.putDisplayChecked(checked);
        setDisplaySettings();
        listener().onDisplayChanged();
    }

    private void setSpeed(@NonNull Slider slider, float value, boolean fromUser) {
        if (!fromUser) return;
        applySpeed(value);
    }

    private void applySpeed(float speed) {
        controls.speed.setText(player.setSpeed(speed));
        setSpeedPresets();
        binding.speed.setValue(Math.max(player.getSpeed(), 0.5f));
        if (history != null) history.setUserSpeed(player.getSpeed());
    }

    private void setSpeedPreset(View view) {
        applySpeed(Float.parseFloat(view.getTag().toString()));
    }

    private void setSpeedPresets() {
        float speed = player.getSpeed();
        for (TextView view : speeds) view.setSelected(Math.abs(Float.parseFloat(view.getTag().toString()) - speed) < 0.01f);
    }

    private void setScaleText() {
        int mode = getScale();
        binding.scale.setText(scale[mode]);
        binding.scale.setSelected(false);
    }

    private int getScale() {
        CharSequence current = controls.scale.getText();
        for (int mode = 0; mode < scale.length; mode++) if (TextUtils.equals(scale[mode], current)) return mode;
        return history != null && history.getScale() != -1 ? history.getScale() : PlayerSetting.getScale();
    }

    private void setParse() {
        setParseVisible(parse);
        binding.parse.setHasFixedSize(true);
        binding.parse.setItemAnimator(null);
        binding.parse.addItemDecoration(new SpaceItemDecoration(8));
        binding.parse.setAdapter(new ParseAdapter(this, ViewType.DARK));
    }

    private void setScale(int mode) {
        listener().onScale(mode);
        setScaleText();
    }

    private void setEpisodeColumn(int column) {
        listener().onEpisodeColumn(column);
        setEpisodeColumn();
    }

    private void setEpisodeColumn() {
        int column = PlayerSetting.getEpisodeColumn();
        binding.episodeColumn1.setSelected(column == 1);
        binding.episodeColumn2.setSelected(column == 2);
        binding.compactEpisodeTitle.setSelected(Setting.isCompactEpisodeTitle());
        boolean visible = controls.episodes != null && controls.episodes.getVisibility() == View.VISIBLE;
        binding.episodeColumnText.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.episodeColumnRow.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setCompactEpisodeTitle() {
        Setting.putCompactEpisodeTitle(!Setting.isCompactEpisodeTitle());
        binding.compactEpisodeTitle.setSelected(Setting.isCompactEpisodeTitle());
        listener().onCompactEpisodeTitleChanged();
    }

    private void active(View view, TextView target) {
        target.performClick();
        view.setSelected(target.isSelected());
    }

    private void click(TextView view, TextView target) {
        target.performClick();
        view.setText(target.getText());
    }

    private void onLut() {
        listener().onLutPanel();
    }

    private boolean longClick(TextView view, TextView target) {
        target.performLongClick();
        view.setText(target.getText());
        return true;
    }

    private void dismiss(View view) {
        App.post(view::performClick, 200);
        dismiss();
    }

    public void setPlayer() {
if (binding == null || controls == null || player == null) return;
        binding.speed.setValue(Math.max(player.getSpeed(), 0.5f));
        setSpeedPresets();
        binding.player.setText(controls.player.getText());
        binding.reset.setText(controls.reset.getText());
        setLut();
        setEpisodeColumn();
        // 播放失败回退会同时改内核和软硬解，两个标签都得跟着宿主刷新，否则弹窗里只有内核在变。
        binding.decode.setText(controls.decode.getText());
        binding.decode.setVisibility(controls.decode.getVisibility());
        binding.danmaku.setVisibility(controls.danmaku.getVisibility());
        setImmersiveAudioVisible();
        // setTitleVisible 会带上 setTrackVisible；章节可见性也参与轨道行的计算，
        // 只抄轨道不抄章节的话，回退后弹窗会拿旧章节状态算出错的轨道行。
        setTitleVisible();
    }

    public void setLut() {
        if (binding == null || controls == null) return;
        binding.lut.setText(controls.lut.getText());
    }

    private boolean resolveHostDependencies() {
        FragmentActivity activity = getActivity();
        if (activity instanceof Listener listener) {
            if (controls == null) {
                ActivityVideoBinding host = listener.getControlBinding();
                if (host != null) parent(host);
            }
            if (player == null) player = listener.getControlPlayer();
            if (history == null) history = listener.getControlHistory();
            parse = listener.isControlParseEnabled();
        }
        return controls != null && player != null;
    }

    public void setParseVisible(boolean visible) {
        binding.parse.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.parseText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setTrackVisible() {
        binding.text.setVisibility(controls.text.getVisibility());
        binding.audio.setVisibility(controls.audio.getVisibility());
        binding.video.setVisibility(controls.videoTrack.getVisibility());
        boolean visible = binding.text.getVisibility() != View.GONE || binding.audio.getVisibility() != View.GONE || binding.video.getVisibility() != View.GONE || binding.title.getVisibility() != View.GONE || binding.danmaku.getVisibility() != View.GONE;
        binding.trackText.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.trackRow.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setImmersiveAudioVisible() {
        FragmentActivity activity = getActivity();
        boolean visible = activity instanceof Listener listener && listener.isControlAudioContent();
        binding.immersiveAudio.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    protected boolean transparent() {
        return true;
    }

    @Override
    protected void setBehavior(BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        sheet.setBackgroundColor(ResUtil.getColor(R.color.transparent));
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setFitToContents(false);
        behavior.setDraggable(false);
        setSheetHeight(sheet, behavior);
        sheet.post(() -> setSheetHeight(sheet, behavior));
    }

    private void setSheetHeight(FrameLayout sheet, BottomSheetBehavior<FrameLayout> behavior) {
        int height = Math.min(getPanelMaxHeight(), getContentHeight(sheet));
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.height = height;
        sheet.setLayoutParams(params);
        behavior.setPeekHeight(height);
        behavior.setExpandedOffset(Math.max(0, ResUtil.getScreenHeight(requireContext()) - height));
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private int getContentHeight(FrameLayout sheet) {
        if (binding == null || binding.controlScroll.getChildCount() == 0) return getPanelMaxHeight();
        setControlPadding();
        View content = binding.controlScroll.getChildAt(0);
        int width = sheet.getWidth() > 0 ? sheet.getWidth() : ResUtil.getScreenWidth(requireContext());
        int contentWidth = Math.max(0, width - binding.controlScroll.getPaddingStart() - binding.controlScroll.getPaddingEnd());
        content.measure(View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return content.getMeasuredHeight() + binding.controlScroll.getPaddingTop() + binding.controlScroll.getPaddingBottom() + ResUtil.dp2px(8);
    }

    private int getPanelMaxHeight() {
        int screen = ResUtil.getScreenHeight(requireContext());
        if (ResUtil.isLand(requireContext())) return Math.max(ResUtil.dp2px(260), Math.min(ResUtil.dp2px(420), Math.round(screen * 0.82f)));
        int available = getPortAvailableHeight(screen);
        int desired = Math.min(ResUtil.dp2px(640), Math.round(screen * 0.68f));
        int min = ResUtil.dp2px(330);
        if (available <= min) return available;
        return Math.min(available, Math.max(min, desired));
    }

    private int getPortAvailableHeight(int fallback) {
        if (controls == null || controls.video.getHeight() <= 0) return Math.round(fallback * 0.58f);
        int[] video = new int[2];
        int[] root = new int[2];
        controls.video.getLocationOnScreen(video);
        controls.root.getLocationOnScreen(root);
        int rootBottom = root[1] + controls.root.getHeight();
        int videoBottom = video[1] + controls.video.getHeight();
        return Math.max(ResUtil.dp2px(260), rootBottom - videoBottom - getNavigationBottomInset());
    }

    private int getNavigationBottomInset() {
        if (ResUtil.isLand(requireContext())) return 0;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(requireActivity().getWindow().getDecorView());
        int bottom = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        return Math.max(bottom, ResUtil.dp2px(48));
    }

    public void setTitleVisible() {
        binding.title.setVisibility(controls.title.getVisibility());
        setTrackVisible();
    }

    @Override
    public void onItemClick(Parse item) {
        listener().onParse(item);
        binding.parse.getAdapter().notifyItemRangeChanged(0, binding.parse.getAdapter().getItemCount());
    }

    private Listener listener() {
        return listener != null ? listener : (Listener) requireActivity();
    }

    private static final class Controls {

        private final View root;
        private final View video;
        private final View fullscreen;
        private final TextView player;
        private final TextView decode;
        private final TextView playParams;
        private final TextView speed;
        private final TextView scale;
        private final TextView lut;
        private final TextView reset;
        private final TextView repeat;
        private final TextView text;
        private final TextView audio;
        private final TextView videoTrack;
        private final TextView opening;
        private final TextView ending;
        private final TextView danmaku;
        private final TextView title;
        private final TextView episodes;

        private Controls(View root, View video, View fullscreen, TextView player, TextView decode, TextView playParams, TextView speed, TextView scale, TextView lut, TextView reset, TextView repeat, TextView text, TextView audio, TextView videoTrack, TextView opening, TextView ending, TextView danmaku, TextView title, TextView episodes) {
            this.root = root;
            this.video = video;
            this.fullscreen = fullscreen;
            this.player = player;
            this.decode = decode;
            this.playParams = playParams;
            this.speed = speed;
            this.scale = scale;
            this.lut = lut;
            this.reset = reset;
            this.repeat = repeat;
            this.text = text;
            this.audio = audio;
            this.videoTrack = videoTrack;
            this.opening = opening;
            this.ending = ending;
            this.danmaku = danmaku;
            this.title = title;
            this.episodes = episodes;
        }
    }

    public interface Listener {

        @Nullable
        ActivityVideoBinding getControlBinding();

        @Nullable
        PlayerManager getControlPlayer();

        @Nullable
        History getControlHistory();

        boolean isControlParseEnabled();

        boolean isControlAudioContent();

        void onScale(int tag);

        void onEpisodeColumn(int column);

        void onCompactEpisodeTitleChanged();

        void onParse(Parse item);

        void onLutSelected(LutPreset preset);

        void onLutImport();

        void onLutDir();

        void onLutPanel();

        void onTrackPanel(int type);

        void onTitlePanel();

        void onDanmakuPanel();

        void onDisplayChanged();

        void onImmersiveAudioModeChanged();

        void onKaraokeModeChanged();

        void onKaraokeTrackPanel();

        void onCodecCapabilityPanel();

        void onPlayParamsPanel();
    }
}
