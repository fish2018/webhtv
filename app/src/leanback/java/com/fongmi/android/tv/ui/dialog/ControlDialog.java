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
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.databinding.DialogControlBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Timer;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.slider.Slider;

import java.util.Arrays;
import java.util.List;

public class ControlDialog extends BaseBottomSheetDialog implements ParseAdapter.OnClickListener {

    private final String[] scale;
    private DialogControlBinding binding;
    private ActivityVideoBinding parent;
    private List<TextView> speeds;
    private List<TextView> displays;
    private PlayerManager player;
    private History history;
    private boolean parse;
    private int scrollBasePaddingBottom;

    public ControlDialog() {
        this.scale = ResUtil.getStringArray(R.array.select_scale);
    }

    public static ControlDialog create() {
        return new ControlDialog();
    }

    public ControlDialog parent(ActivityVideoBinding parent) {
        this.parent = parent;
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
        focusInitialControl();
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        WindowCompat.setDecorFitsSystemWindows(window, false);
        Util.hideSystemUI(window);
        if (getActivity() != null) Util.hideSystemUI(getActivity());
        window.getDecorView().post(() -> Util.hideSystemUI(window));
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
        scrollBasePaddingBottom = binding.controlScroll.getPaddingBottom();
        setControlPadding();
        setSheetBackground();
        binding.decode.setText(parent.control.action.decode.getText());
        setLut();
        binding.ending.setText(parent.control.action.ending.getText());
        binding.opening.setText(parent.control.action.opening.getText());
        binding.repeat.setSelected(parent.control.action.repeat.isSelected());
        binding.karaoke.setSelected(PlayerSetting.isKaraokeMode());
        binding.immersiveAudio.setSelected(parent.control.action.immersiveAudio.isSelected());
        setKaraokeVisible();
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
        binding.getRoot().post(this::focusInitialControl);
        binding.getRoot().postDelayed(this::focusInitialControl, 180);
    }

    private void setControlPadding() {
        int bottom = scrollBasePaddingBottom + getNavigationBottomInset();
        binding.controlScroll.setPaddingRelative(binding.controlScroll.getPaddingStart(), binding.controlScroll.getPaddingTop(), binding.controlScroll.getPaddingEnd(), bottom);
    }

    @Override
    protected void initEvent() {
        binding.timer.setOnClickListener(this::onTimer);
        binding.karaoke.setOnClickListener(v -> setKaraoke());
        binding.immersiveAudio.setOnClickListener(v -> setImmersiveAudio());
        binding.speed.addOnChangeListener(this::setSpeed);
        for (TextView view : speeds) view.setOnClickListener(this::setSpeedPreset);
        binding.scale.setOnClickListener(view -> VideoAspectModeDialog.show(requireActivity(), getScale(), this::setScale));
        for (int i = 0; i < displays.size(); i++) {
            int index = i;
            displays.get(i).setOnClickListener(v -> toggleDisplaySetting(index));
        }
        binding.reset.setOnClickListener(v -> dismiss(parent.control.action.reset));
        binding.fullscreen.setOnClickListener(v -> dismiss(parent.control.action.fullscreen));
        binding.text.setOnClickListener(v -> onTrack(binding.text));
        binding.audio.setOnClickListener(v -> onTrack(binding.audio));
        binding.video.setOnClickListener(v -> onTrack(binding.video));
        binding.episodeColumn1.setOnClickListener(v -> setEpisodeColumn(1));
        binding.episodeColumn2.setOnClickListener(v -> setEpisodeColumn(2));
        binding.compactEpisodeTitle.setOnClickListener(v -> setCompactEpisodeTitle());
        binding.title.setOnClickListener(v -> ((Listener) requireActivity()).onTitlePanel());
        binding.player.setOnClickListener(v -> dismiss(parent.control.action.player));
        binding.danmaku.setOnClickListener(v -> ((Listener) requireActivity()).onDanmakuPanel());
        binding.repeat.setOnClickListener(v -> active(binding.repeat, parent.control.action.repeat));
        binding.decode.setOnClickListener(v -> click(binding.decode, parent.control.action.decode));
        binding.lut.setOnClickListener(v -> onLut());
        binding.ending.setOnClickListener(v -> click(binding.ending, parent.control.action.ending));
        binding.opening.setOnClickListener(v -> click(binding.opening, parent.control.action.opening));
        binding.panDiagnostic.setOnClickListener(v -> onPanDiagnostic());
        binding.player.setOnLongClickListener(v -> longClick(binding.player, parent.control.action.player));
        binding.ending.setOnLongClickListener(v -> longClick(binding.ending, parent.control.action.ending));
        binding.opening.setOnLongClickListener(v -> longClick(binding.opening, parent.control.action.opening));
        binding.karaoke.setOnLongClickListener(v -> {
            ((Listener) requireActivity()).onKaraokeTrackPanel();
            return true;
        });
        bindRemoteFocus();
    }

    private void bindRemoteFocus() {
        List<View> views = Arrays.asList(
                binding.fullscreen, binding.lut, binding.reset, binding.repeat, binding.timer, binding.karaoke,
                binding.player, binding.decode, binding.opening, binding.ending, binding.immersiveAudio,
                binding.panDiagnostic, binding.scale,
                binding.text, binding.audio, binding.video, binding.danmaku, binding.title,
                binding.displayTime, binding.displayTraffic, binding.displaySize, binding.displayProgress,
                binding.displayMini, binding.displayTitle, binding.displayParams,
                binding.episodeColumn1, binding.episodeColumn2, binding.compactEpisodeTitle
        );
        for (View view : views) setRemoteFocusable(view);
        for (TextView view : speeds) setRemoteFocusable(view);
    }

    private void setRemoteFocusable(View view) {
        if (view == null) return;
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
    }

    private void focusInitialControl() {
        if (binding == null || !isAdded()) return;
        binding.controlScroll.scrollTo(0, 0);
        View target = firstFocusable(
                binding.fullscreen, binding.lut, binding.reset, binding.repeat, binding.timer, binding.karaoke,
                binding.player, binding.decode, binding.opening, binding.ending
        );
        if (target == null) target = firstFocusable(speeds.toArray(new View[0]));
        if (target == null) target = binding.controlScroll;
        target.requestFocus();
    }

    private View firstFocusable(View... views) {
        for (View view : views) {
            if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) continue;
            if (!view.isFocusable()) setRemoteFocusable(view);
            return view;
        }
        return null;
    }

    private void onTimer(View view) {
        TimerDialog.create().show(getActivity());
    }

    private void onPanDiagnostic() {
        FragmentActivity activity = getActivity();
        PlayerManager current = player;
        if (activity == null || current == null) return;
        dismissAllowingStateLoss();
        App.post(() -> PanNetworkDiagnosticDialog.show(activity, current), 140);
    }

    private void setKaraoke() {
        PlayerSetting.putKaraokeMode(!PlayerSetting.isKaraokeMode());
        binding.karaoke.setSelected(PlayerSetting.isKaraokeMode());
        ((Listener) requireActivity()).onKaraokeModeChanged();
    }

    private void setImmersiveAudio() {
        boolean enabled = !binding.immersiveAudio.isSelected();
        PlayerSetting.putImmersiveAudioMode(enabled);
        binding.immersiveAudio.setSelected(enabled);
        ((Listener) requireActivity()).onImmersiveAudioModeChanged();
    }

    private void onTrack(View view) {
        ((Listener) requireActivity()).onTrackPanel(Integer.parseInt(view.getTag().toString()));
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
        ((Listener) requireActivity()).onDisplayChanged();
    }

    private void setSpeed(@NonNull Slider slider, float value, boolean fromUser) {
        if (!fromUser) return;
        applySpeed(value);
    }

    private void applySpeed(float speed) {
        parent.control.action.speed.setText(player.setSpeed(speed));
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
        CharSequence current = parent.control.action.scale.getText();
        for (int mode = 0; mode < scale.length; mode++) if (TextUtils.equals(scale[mode], current)) return mode;
        return history != null && history.getScale() != -1 ? history.getScale() : PlayerSetting.getScale();
    }

    private void setParse() {
        setParseVisible(parse);
        binding.parse.setHasFixedSize(true);
        binding.parse.setItemAnimator(null);
        binding.parse.addItemDecoration(new SpaceItemDecoration(8));
        ParseAdapter adapter = new ParseAdapter(this);
        binding.parse.setAdapter(adapter);
        adapter.addAll(VodConfig.get().getParses());
    }

    private void setScale(int mode) {
        ((Listener) requireActivity()).onScale(mode);
        setScaleText();
    }

    private void setEpisodeColumn(int column) {
        ((Listener) requireActivity()).onEpisodeColumn(column);
        setEpisodeColumn();
    }

    private void setEpisodeColumn() {
        int column = PlayerSetting.getEpisodeColumn();
        binding.episodeColumn1.setSelected(column == 1);
        binding.episodeColumn2.setSelected(column == 2);
        binding.compactEpisodeTitle.setSelected(Setting.isCompactEpisodeTitle());
        boolean visible = parent.control.action.episodes.getVisibility() == View.VISIBLE;
        binding.episodeColumnText.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.episodeColumnRow.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setCompactEpisodeTitle() {
        Setting.putCompactEpisodeTitle(!Setting.isCompactEpisodeTitle());
        binding.compactEpisodeTitle.setSelected(Setting.isCompactEpisodeTitle());
        ((Listener) requireActivity()).onCompactEpisodeTitleChanged();
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
        ((Listener) requireActivity()).onLutPanel();
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
        if (binding == null || parent == null) return;
        binding.speed.setValue(Math.max(player.getSpeed(), 0.5f));
        setSpeedPresets();
        binding.player.setText(parent.control.action.player.getText());
        binding.reset.setText(parent.control.action.reset.getText());
        setLut();
        setEpisodeColumn();
        // 播放失败回退会同时改内核和软硬解，两个标签都得跟着宿主刷新，否则弹窗里只有内核在变。
        binding.decode.setText(parent.control.action.decode.getText());
        binding.decode.setVisibility(parent.control.action.decode.getVisibility());
        binding.danmaku.setVisibility(parent.control.action.danmaku.getVisibility());
        setKaraokeVisible();
        setImmersiveAudioVisible();
        // setTitleVisible 会带上 setTrackVisible；章节可见性也参与轨道行的计算，
        // 只抄轨道不抄章节的话，回退后弹窗会拿旧章节状态算出错的轨道行。
        setTitleVisible();
    }

    public void setLut() {
        if (binding == null || parent == null) return;
        binding.lut.setText(parent.control.action.lut.getText());
    }

    public void setParseVisible(boolean visible) {
        binding.parse.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.parseText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setTrackVisible() {
        binding.text.setVisibility(parent.control.action.text.getVisibility());
        binding.audio.setVisibility(parent.control.action.audio.getVisibility());
        binding.video.setVisibility(parent.control.action.video.getVisibility());
        boolean visible = binding.text.getVisibility() != View.GONE || binding.audio.getVisibility() != View.GONE || binding.video.getVisibility() != View.GONE || binding.title.getVisibility() != View.GONE || binding.danmaku.getVisibility() != View.GONE;
        binding.trackText.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.trackRow.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setKaraokeVisible() {
        binding.karaoke.setVisibility(parent.control.action.karaoke.getVisibility());
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
        if (parent == null || parent.video.getHeight() <= 0) return Math.round(fallback * 0.58f);
        int[] video = new int[2];
        int[] root = new int[2];
        parent.video.getLocationOnScreen(video);
        parent.getRoot().getLocationOnScreen(root);
        int rootBottom = root[1] + parent.getRoot().getHeight();
        int videoBottom = video[1] + parent.video.getHeight();
        return Math.max(ResUtil.dp2px(260), rootBottom - videoBottom - getNavigationBottomInset());
    }

    private int getNavigationBottomInset() {
        if (ResUtil.isLand(requireContext())) return 0;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(requireActivity().getWindow().getDecorView());
        int bottom = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        return Math.max(bottom, ResUtil.dp2px(48));
    }

    public void setTitleVisible() {
        binding.title.setVisibility(parent.control.action.title.getVisibility());
        setTrackVisible();
    }

    @Override
    public void onItemClick(Parse item) {
        ((Listener) requireActivity()).onParse(item);
        binding.parse.getAdapter().notifyItemRangeChanged(0, binding.parse.getAdapter().getItemCount());
    }

    public interface Listener {

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

        boolean onKaraokeTrackPanel();
    }
}
