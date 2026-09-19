package com.fongmi.android.tv.player.exo.ass;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.engine.ExoPlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.exo.ExoUtil;

import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Debug-only playground and reproducible device fixture. No production settings are changed. */
public final class AssPrototypeActivity extends Activity implements Player.Listener {
    public ExoPlayerEngine engine;
    public ExoPlayer player;
    public PlayerView view;
    public ExoAssSession session;
    public long startupMs = -1, seekMs = -1;
    public int underruns;
    private long startedNs, seekNs;
    private String videoUri, assUri;
    private boolean enabled;
    private boolean engineReleased, closing;
    private String run;
    private PlaySpec spec;
    private PlaybackException error;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout controls = new LinearLayout(this);
        for (String[] action : new String[][]{{"暂停/播放", "pause_toggle"}, {"回到 2 秒", "seek"},
                {"延迟 +500ms", "delay"}, {"原样/兼容", "toggle"}, {"重建字幕层", "reattach"}}) {
            Button button = new Button(this);
            button.setText(action[0]);
            button.setOnClickListener(v -> command(action[1], null));
            controls.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(controls);
        view = new PlayerView(this);
        ExoUtil.setPlayerView(view);
        view.setRender(0);
        view.setUseController(false);
        root.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        open(getIntent());
    }

    private void open(Intent intent) {
        enabled = intent.getBooleanExtra("enabled", true);
        run = intent.getStringExtra("run");
        videoUri = intent.getStringExtra("video");
        assUri = intent.getStringExtra("subtitle");
        boolean embedded = intent.getBooleanExtra("embedded", false);
        if (videoUri == null) videoUri = "asset:///exo-ass/" + (embedded ? "sample-embedded.mkv" : "sample-fonts.mkv");
        if (assUri == null) assUri = "asset:///exo-ass/animated.ass";
        engine = new ExoPlayerEngine(PlayerEngine.HARD, this);
        bindPlayer();
        spec = PlaySpec.from("E4-LIBASS-prototype", videoUri, Collections.emptyMap(), MediaMetadata.EMPTY);
        if (!embedded && !intent.getBooleanExtra("no_subtitle", false))
            spec.setSub(Sub.create("ASS prototype", assUri, "zh", MimeTypes.TEXT_SSA));
        startedNs = System.nanoTime();
        engine.start(spec);
    }

    private void bindPlayer() {
        player = (ExoPlayer) engine.getPlayer();
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override public void onAudioUnderrun(EventTime time, int size, long durationMs, long elapsedMs) { underruns++; }
        });
        session = engine.getAssSession();
        if (session != null) {
            session.setEnabled(enabled);
            session.attach(view);
        }
        view.setPlayer(player);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        command(intent.getStringExtra("command"), intent);
    }

    public void command(String command, Intent intent) {
        if (command == null || engine == null) return;
        switch (command) {
            case "pause_toggle" -> player.setPlayWhenReady(!player.getPlayWhenReady());
            case "pause" -> player.pause();
            case "play" -> player.play();
            case "seek" -> {
                seekNs = System.nanoTime();
                player.seekTo(intent == null ? 2000 : intent.getLongExtra("position", 2000));
            }
            case "delay" -> player.setTextOffsetMs(intent == null ? 500 : intent.getLongExtra("offset", 500));
            case "toggle" -> { enabled = !enabled; if (session != null) session.setEnabled(enabled); }
            case "fault" -> { if (session != null) session.injectFailure("surface"); }
            case "detach" -> { if (session != null) session.detach(); }
            case "attach" -> { if (session != null) session.attach(view); }
            case "reattach" -> { if (session != null) { session.detach(); session.attach(view); } }
            case "rebuild" -> {
                long position = player.getCurrentPosition();
                view.setPlayer(null);
                engine.rebuild(this);
                bindPlayer();
                engine.start(spec, position, true);
            }
            case "subtitle_off" -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true).build());
            case "subtitle_on" -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false).build());
            case "texture" -> { if (session != null) session.detach(); view.setRender(1); if (session != null) session.attach(view); }
            case "surface" -> { if (session != null) session.detach(); view.setRender(0); if (session != null) session.attach(view); }
            case "dump" -> dumpMetrics();
            case "close" -> closeAfterRelease();
            default -> { }
        }
    }

    @Override public void onRenderedFirstFrame() {
        if (startupMs < 0) startupMs = (System.nanoTime() - startedNs) / 1_000_000;
        if (seekNs != 0) { seekMs = (System.nanoTime() - seekNs) / 1_000_000; seekNs = 0; }
    }

    @Override public void onPlayerError(PlaybackException error) { this.error = error; }

    public JSONObject metrics() {
        try {
            JSONObject out = new JSONObject();
            out.put("run", run); out.put("enabled", enabled);
            out.put("startupMs", startupMs); out.put("seekMs", seekMs); out.put("underruns", underruns);
            out.put("positionMs", player.getCurrentPosition()); out.put("playing", player.isPlaying());
            Format video = player.getVideoFormat();
            if (video != null) {
                out.put("videoMime", video.sampleMimeType);
                out.put("videoWidth", video.width); out.put("videoHeight", video.height);
                out.put("videoCodecs", video.codecs);
                out.put("assSdrRgb", AssVideoPolicy.usesSdrRgb(video));
                if (video.colorInfo != null) {
                    out.put("videoColorSpace", video.colorInfo.colorSpace);
                    out.put("videoColorTransfer", video.colorInfo.colorTransfer);
                    out.put("videoColorRange", video.colorInfo.colorRange);
                }
            }
            out.put("error", error == null ? "" : error.errorCode);
            if (error != null) {
                out.put("errorMessage", error.getMessage());
                out.put("errorCause", String.valueOf(error.getCause()));
            }
            DecoderCounters counters = player.getVideoDecoderCounters();
            if (counters != null) {
                counters.ensureUpdated();
                out.put("renderedFrames", counters.renderedOutputBufferCount);
                out.put("droppedFrames", counters.droppedBufferCount);
                out.put("maxConsecutiveDropped", counters.maxConsecutiveDroppedBufferCount);
            }
            Debug.MemoryInfo memory = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memory);
            out.put("pssKb", memory.getTotalPss()); out.put("nativePssKb", memory.nativePss);
            out.put("graphicsKb", memory.getMemoryStat("summary.graphics"));
            if (session != null) {
                ExoAssSession.Diagnostics d = session.diagnostics();
                out.put("assState", d.state()); out.put("failure", d.failure()); out.put("assFrames", d.frames());
                out.put("renderP95Us", d.renderP95Us()); out.put("uploadP95Us", d.uploadP95Us());
                out.put("swapP95Us", d.swapP95Us()); out.put("renderMaxUs", d.maxRenderUs());
                out.put("scriptCount", d.scripts()); out.put("staleFrames", d.staleFrames());
                out.put("assTimeMs", d.timeMs()); out.put("nativeAlive", d.nativeAlive());
                out.put("releaseComplete", d.releaseComplete()); out.put("workerAlive", d.workerAlive());
                out.put("workerTid", d.workerTid());
                out.put("generation", d.generation()); out.put("surfaceEpoch", d.surfaceEpoch());
                out.put("fontCount", d.fontCount()); out.put("fontBytes", d.fontBytes());
                out.put("packetCount", d.packetCount()); out.put("packetBytes", d.packetBytes());
            }
            out.put("compatibleVisible", view.getSubtitleView().getVisibility() == View.VISIBLE);
            out.put("cueCount", player.getCurrentCues().cues.size());
            return out;
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private void dumpMetrics() {
        try (FileOutputStream output = new FileOutputStream(new File(getFilesDir(), "exo-ass-metrics.jsonl"), true)) {
            output.write((metrics() + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private void releaseEngine() {
        if (engineReleased) return;
        engineReleased = true;
        view.setPlayer(null);
        if (engine != null) engine.release();
    }

    private void closeAfterRelease() {
        if (closing) return;
        closing = true;
        dumpMetrics();
        releaseEngine();
        Handler handler = new Handler(getMainLooper());
        long deadline = SystemClock.uptimeMillis() + 5000;
        handler.post(new Runnable() {
            @Override public void run() {
                ExoAssSession.Diagnostics d = session == null ? null : session.diagnostics();
                if (d == null || d.releaseComplete() && !d.workerAlive()
                        || SystemClock.uptimeMillis() >= deadline) {
                    dumpMetrics();
                    finish();
                } else handler.postDelayed(this, 25);
            }
        });
    }

    @Override protected void onDestroy() {
        releaseEngine();
        super.onDestroy();
    }
}
