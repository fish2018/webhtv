package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;
import androidx.media3.ui.DefaultTrackNameProvider;
import androidx.media3.ui.TrackNameProvider;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.databinding.DialogTrackBinding;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.ui.adapter.TrackAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TrackDialog extends BaseBottomSheetDialog implements TrackAdapter.OnClickListener {

    private final TrackNameProvider provider;
    private final TrackAdapter adapter;
    private DialogTrackBinding binding;
    private PlayerManager player;
    private boolean secondarySubtitle;
    private int type;

    public static TrackDialog create() {
        return new TrackDialog();
    }

    public TrackDialog() {
        this.adapter = new TrackAdapter(this);
        this.provider = new DefaultTrackNameProvider(App.get().getResources());
    }

    public TrackDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public TrackDialog type(int type) {
        this.type = type;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof TrackDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private boolean hasChoose() {
        return !secondarySubtitle && type == C.TRACK_TYPE_TEXT && player.isVod();
    }

    private boolean hasText() {
        return !secondarySubtitle && type == C.TRACK_TYPE_TEXT && player.haveTrack(type);
    }

    private boolean hasAudio() {
        return type == C.TRACK_TYPE_AUDIO && player.haveTrack(type);
    }

    private boolean hasSecondarySubtitle() {
        return type == C.TRACK_TYPE_TEXT && player.supportsSecondarySubtitle() && player.haveTrack(type);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogTrackBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        refreshTrackList();
    }

    @Override
    protected void initEvent() {
        binding.secondary.setOnClickListener(this::onSecondary);
        binding.offset.setOnClickListener(this::onOffset);
        binding.choose.setOnClickListener(this::onChoose);
        binding.subtitle.setOnClickListener(this::onSubtitle);
    }

    private void refreshTrackList() {
        adapter.replaceAll(getTrack());
        binding.title.setText(getTitle());
        binding.secondary.setText(secondarySubtitle ? R.string.play_track_primary_subtitle : R.string.play_track_secondary_subtitle);
        binding.secondary.setContentDescription(binding.secondary.getText());
        binding.secondary.setVisibility(hasSecondarySubtitle() ? View.VISIBLE : View.GONE);
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelected()));
        binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        binding.offset.setVisibility(hasText() || hasAudio() ? View.VISIBLE : View.GONE);
        binding.choose.setVisibility(hasChoose() ? View.VISIBLE : View.GONE);
        binding.subtitle.setVisibility(hasText() ? View.VISIBLE : View.GONE);
    }

    private String getTitle() {
        if (secondarySubtitle) return getString(R.string.select_secondary_subtitle_track);
        return ResUtil.getStringArray(R.array.select_track)[type - 1];
    }

    private void onSecondary(View view) {
        secondarySubtitle = !secondarySubtitle;
        refreshTrackList();
    }

    private void onOffset(View view) {
        OffsetDialog.create().player(player).type(type).show(requireActivity());
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show(new String[]{MimeTypes.APPLICATION_SUBRIP, MimeTypes.TEXT_SSA, MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_TTML, "audio/*", "text/*", "application/octet-stream"});
        player.pause();
    }

    private void onSubtitle(View view) {
        Listener listener = (Listener) requireActivity();
        App.post(listener::onSubtitleClick, 100);
        dismiss();
    }

    private List<Track> getTrack() {
        List<Track> items = new ArrayList<>();
        addTrack(items);
        addDisableTrack(items);
        return items;
    }

    private void addTrack(List<Track> items) {
        List<Tracks.Group> groups = player.getCurrentTracks().getGroups();
        for (int i = 0; i < groups.size(); i++) {
            Tracks.Group trackGroup = groups.get(i);
            if (trackGroup.getType() != type) continue;
            for (int j = 0; j < trackGroup.length; j++) {
                Format format = trackGroup.getTrackFormat(j);
                // One subtitle cannot occupy both roles (the same rule is enforced by MPV).
                if (type == C.TRACK_TYPE_TEXT && (secondarySubtitle
                        ? player.isPrimarySubtitleSelected(format) : player.isSecondarySubtitleSelected(format))) continue;
                String name = provider.getTrackName(format);
                Log.d("TrackDialog", "track type=" + type + " id=" + format.id + " label=" + format.label + " lang=" + format.language + " codec=" + format.codecs + " mime=" + format.sampleMimeType + " name=" + name);
                // Keep the player's native track id with the visible item. Runtime track
                // switching must target this stable id directly; the formatted description
                // is only persisted for restoring a preference on the next playback.
                Track item = new Track(type, name, PlayerHelper.describeFormat(format)).playerId(format.id);
                item.setSelected(type == C.TRACK_TYPE_TEXT
                        ? secondarySubtitle ? player.isSecondarySubtitleSelected(format) : player.isPrimarySubtitleSelected(format)
                        : trackGroup.isTrackSelected(j));
                items.add(item);
            }
        }
    }

    private void addDisableTrack(List<Track> items) {
        if (type != C.TRACK_TYPE_TEXT) return;
        Track item = Track.disabled(type, getString(secondarySubtitle ? R.string.play_track_disable_secondary_subtitle : R.string.play_track_disable_subtitle));
        item.setSelected(items.stream().noneMatch(Track::isSelected));
        items.add(0, item);
    }

    @Override
    public void onItemClick(Track item) {
        if (secondarySubtitle) {
            player.setSecondarySubtitleTrack(item);
            dismiss();
            return;
        }
        player.setTrack(Arrays.asList(item.key(player.getKey()).save()));
        dismiss();
    }

    @Override
    protected boolean transparent() {
        return !Util.isLeanback();
    }

    @Override
    protected boolean stableOverlay() {
        return !Util.isLeanback();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        player.setSub(Sub.from(FileChooser.getPathFromUri(result.getData().getData())));
        dismiss();
    });

    public interface Listener {

        void onSubtitleClick();
    }
}
