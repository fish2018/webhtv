package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.databinding.DialogControlBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Timer;
import com.google.android.material.slider.Slider;

import java.util.Arrays;
import java.util.List;

public class ControlDialog extends BaseBottomSheetDialog implements ParseAdapter.OnClickListener {

    private final String[] scale;
    private DialogControlBinding binding;
    private ActivityVideoBinding parent;
    private List<TextView> scales;
    private PlayerManager player;
    private History history;
    private boolean parse;

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

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        binding = DialogControlBinding.inflate(inflater, container, false);
        scales = Arrays.asList(binding.scale0, binding.scale1, binding.scale2, binding.scale3, binding.scale4);
        return binding;
    }

    @Override
    protected void initView() {
        binding.decode.setText(parent.control.action.decode.getText());
        binding.ending.setText(parent.control.action.ending.getText());
        binding.opening.setText(parent.control.action.opening.getText());
        binding.repeat.setSelected(parent.control.action.repeat.isSelected());
        binding.timer.setSelected(Timer.get().isRunning());
        setTrackVisible();
        setTitleVisible();
        setDisplay();
        setScaleText();
        setPlayer();
        setParse();
    }

    @Override
    protected void initEvent() {
        binding.timer.setOnClickListener(this::onTimer);
        binding.speed.addOnChangeListener(this::setSpeed);
        for (TextView view : scales) view.setOnClickListener(this::setScale);
        binding.text.setOnClickListener(v -> dismiss(parent.control.action.text));
        binding.audio.setOnClickListener(v -> dismiss(parent.control.action.audio));
        binding.video.setOnClickListener(v -> dismiss(parent.control.action.video));
        binding.title.setOnClickListener(v -> dismiss(parent.control.action.title));
        binding.player.setOnClickListener(v -> dismiss(parent.control.action.player));
        binding.danmaku.setOnClickListener(v -> dismiss(parent.control.action.danmaku));
        binding.repeat.setOnClickListener(v -> active(binding.repeat, parent.control.action.repeat));
        binding.displayTime.setOnClickListener(v -> setDisplay(binding.displayTime, Display.TIME));
        binding.displayTraffic.setOnClickListener(v -> setDisplay(binding.displayTraffic, Display.TRAFFIC));
        binding.displaySize.setOnClickListener(v -> setDisplay(binding.displaySize, Display.SIZE));
        binding.displayProgress.setOnClickListener(v -> setDisplay(binding.displayProgress, Display.PROGRESS));
        binding.displayMini.setOnClickListener(v -> setDisplay(binding.displayMini, Display.MINI));
        binding.displayTitle.setOnClickListener(v -> setDisplay(binding.displayTitle, Display.TITLE));
        binding.decode.setOnClickListener(v -> click(binding.decode, parent.control.action.decode));
        binding.ending.setOnClickListener(v -> click(binding.ending, parent.control.action.ending));
        binding.opening.setOnClickListener(v -> click(binding.opening, parent.control.action.opening));
        binding.player.setOnLongClickListener(v -> longClick(binding.player, parent.control.action.player));
        binding.ending.setOnLongClickListener(v -> longClick(binding.ending, parent.control.action.ending));
        binding.opening.setOnLongClickListener(v -> longClick(binding.opening, parent.control.action.opening));
    }

    private void onTimer(View view) {
        TimerDialog.create().show(getActivity());
        dismiss();
    }

    private void setSpeed(@NonNull Slider slider, float value, boolean fromUser) {
        parent.control.action.speed.setText(player.setSpeed(value));
        if (history != null) history.setSpeed(player.getSpeed());
    }

    private void setScaleText() {
        for (int i = 0; i < scales.size(); i++) {
            scales.get(i).setText(scale[i]);
            scales.get(i).setSelected(scales.get(i).getText().equals(parent.control.action.scale.getText()));
        }
    }

    private void setParse() {
        setParseVisible(parse);
        binding.parse.setHasFixedSize(true);
        binding.parse.setItemAnimator(null);
        binding.parse.addItemDecoration(new SpaceItemDecoration(8));
        binding.parse.setAdapter(new ParseAdapter(this, ViewType.LIGHT));
    }

    private void setDisplay() {
        binding.displayTime.setSelected(PlayerSetting.isDisplayTime());
        binding.displayTraffic.setSelected(PlayerSetting.isDisplayTraffic());
        binding.displaySize.setSelected(PlayerSetting.isDisplaySize());
        binding.displayProgress.setSelected(PlayerSetting.isDisplayProgress());
        binding.displayMini.setSelected(PlayerSetting.isDisplayMini());
        binding.displayTitle.setSelected(PlayerSetting.isDisplayTitle());
    }

    private void setDisplay(TextView view, Display display) {
        boolean selected = !view.isSelected();
        view.setSelected(selected);
        switch (display) {
            case TIME -> PlayerSetting.putDisplayTime(selected);
            case TRAFFIC -> PlayerSetting.putDisplayTraffic(selected);
            case SIZE -> PlayerSetting.putDisplaySize(selected);
            case PROGRESS -> PlayerSetting.putDisplayProgress(selected);
            case MINI -> PlayerSetting.putDisplayMini(selected);
            case TITLE -> PlayerSetting.putDisplayTitle(selected);
        }
        ((Listener) requireActivity()).onDisplayChanged();
    }

    private void setScale(View view) {
        for (TextView textView : scales) textView.setSelected(false);
        ((Listener) requireActivity()).onScale(Integer.parseInt(view.getTag().toString()));
        view.setSelected(true);
    }

    private void active(View view, TextView target) {
        target.performClick();
        view.setSelected(target.isSelected());
    }

    private void click(TextView view, TextView target) {
        target.performClick();
        view.setText(target.getText());
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
        binding.speed.setValue(Math.max(player.getSpeed(), 0.5f));
        binding.player.setText(parent.control.action.player.getText());
        binding.decode.setVisibility(parent.control.action.decode.getVisibility());
        binding.danmaku.setVisibility(parent.control.action.danmaku.getVisibility());
    }

    public void setParseVisible(boolean visible) {
        binding.parse.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.parseText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setTrackVisible() {
        binding.text.setVisibility(parent.control.action.text.getVisibility());
        binding.audio.setVisibility(parent.control.action.audio.getVisibility());
        binding.video.setVisibility(parent.control.action.video.getVisibility());
        binding.track.setVisibility(binding.text.getVisibility() == View.GONE && binding.audio.getVisibility() == View.GONE && binding.video.getVisibility() == View.GONE ? View.GONE : View.VISIBLE);
    }

    public void setTitleVisible() {
        binding.title.setVisibility(parent.control.action.title.getVisibility());
    }

    @Override
    public void onItemClick(Parse item) {
        ((Listener) requireActivity()).onParse(item);
        binding.parse.getAdapter().notifyItemRangeChanged(0, binding.parse.getAdapter().getItemCount());
    }

    public interface Listener {

        void onScale(int tag);

        void onParse(Parse item);

        void onDisplayChanged();
    }

    private enum Display {
        TIME, TRAFFIC, SIZE, PROGRESS, MINI, TITLE
    }
}
