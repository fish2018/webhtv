package com.fongmi.android.tv.player.engine;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.crawler.SpiderDebug;

import java.util.List;
import java.util.concurrent.TimeUnit;

@UnstableApi
public class SystemPlayerEngine implements PlayerEngine {

    private SystemSimplePlayer player;
    private PlaySpec spec;
    private int decode;

    public SystemPlayerEngine(int decode, Player.Listener listener) {
        this.player = buildPlayer(listener);
        this.decode = decode;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void release() {
        player.release();
    }

    @Override
    public Player rebuild(Player.Listener listener) {
        player.release();
        SpiderDebug.log("player-engine", "rebuild system decode=%d", decode);
        return player = buildPlayer(listener);
    }

    @Override
    public int getDecode() {
        return decode;
    }

    @Override
    public void setDecode(int decode) {
        this.decode = decode;
    }

    @Override
    public boolean isHard() {
        return decode == HARD;
    }

    @Override
    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    @Override
    public void start(PlaySpec spec) {
        start(spec, C.TIME_UNSET, true);
    }

    @Override
    public void start(PlaySpec spec, boolean playWhenReady) {
        start(spec, C.TIME_UNSET, playWhenReady);
    }

    @Override
    public void start(PlaySpec spec, long position, boolean playWhenReady) {
        this.spec = spec;
        SpiderDebug.log("player-engine", "start system decode=%d position=%d play=%s url=%s headers=%s", decode, position, playWhenReady, spec.getUrl(), spec.getHeaders());
        player.setMediaItem(ExoUtil.getMediaItem(spec, decode), position);
        player.prepare();
        if (playWhenReady) player.play();
        else player.pause();
    }

    @Override
    public void restart(PlaySpec spec, long position, boolean playWhenReady) {
        start(spec, position, playWhenReady);
    }

    @Override
    public void setMetadata(MediaMetadata data) {
        MediaItem current = player.getCurrentMediaItem();
        if (current != null) player.replaceMediaItem(player.getCurrentMediaItemIndex(), current.buildUpon().setMediaMetadata(data).build());
    }

    @Override
    public boolean isLive() {
        return player.getDuration() < TimeUnit.MINUTES.toMillis(1) || player.isCurrentMediaItemLive();
    }

    @Override
    public boolean isVod() {
        return player.getDuration() > TimeUnit.MINUTES.toMillis(1) && !player.isCurrentMediaItemLive();
    }

    @Override
    public void setTrack(List<Track> tracks) {
    }

    @Override
    public void resetTrack() {
    }

    @Override
    public boolean haveTrack(int type) {
        return false;
    }

    @Override
    public Tracks getCurrentTracks() {
        return Tracks.EMPTY;
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        return e.getMessage();
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        SpiderDebug.log("player-engine", "handleError system code=%d message=%s url=%s", e.errorCode, e.getMessage(), spec == null ? null : spec.getUrl());
        return ErrorAction.FATAL;
    }

    private SystemSimplePlayer buildPlayer(Player.Listener listener) {
        SystemSimplePlayer player = new SystemSimplePlayer();
        player.addListener(listener);
        return player;
    }
}
