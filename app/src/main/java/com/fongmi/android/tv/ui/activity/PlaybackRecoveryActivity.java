package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.mpv.PlaybackRecoveryMonitor;
import com.fongmi.android.tv.player.mpv.PlaybackRecoveryPolicy;

/** Deliberately not BaseActivity: this process must never load a player or start servers. */
public final class PlaybackRecoveryActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile PlaybackRecoveryMonitor.Request request;
    private TextView description;
    private Button exit;
    private volatile boolean destroyed;
    private final Runnable recoveryPoll = this::pollRecovery;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        request = PlaybackRecoveryMonitor.requestForToken(this,
                getIntent().getStringExtra(PlaybackRecoveryMonitor.EXTRA_TOKEN));
        if (request == null) {
            finishAndRemoveTask();
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        int padding = Math.round(32 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(Color.rgb(24, 24, 24));
        TextView title = new TextView(this);
        title.setText(R.string.playback_recovery_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        content.addView(title);
        description = new TextView(this);
        description.setText(R.string.playback_recovery_description);
        description.setTextColor(Color.LTGRAY);
        description.setTextSize(16);
        description.setPadding(0, padding / 2, 0, padding / 2);
        content.addView(description);
        Button wait = new Button(this);
        wait.setText(R.string.playback_recovery_wait);
        wait.setOnClickListener(view -> description.setText(R.string.playback_recovery_waiting));
        content.addView(wait);
        exit = new Button(this);
        exit.setText(R.string.playback_recovery_exit);
        exit.setOnClickListener(view -> endPlayback());
        content.addView(exit);
        setContentView(content, new ViewGroup.LayoutParams(-1, -1));
        wait.requestFocus(); // Never default remote focus to a destructive action.
        handler.postDelayed(recoveryPoll, 1000);
    }

    private void endPlayback() {
        exit.setEnabled(false);
        PlaybackRecoveryMonitor.Request selected = request;
        // Filesystem verification and process exit can never block this page.
        new Thread(() -> endPlaybackOffUi(selected), "mpv-user-recovery").start();
    }

    private void endPlaybackOffUi(PlaybackRecoveryMonitor.Request selected) {
        // Revalidate the private request and process generation at the moment
        // of consent. Reused PIDs, already recovered playback and stale pages
        // must not terminate anything.
        if (!PlaybackRecoveryMonitor.mayEndProcess(this, selected)) {
            PlaybackRecoveryMonitor.Request current = PlaybackRecoveryMonitor.requestForToken(this, selected.token());
            int message = current != null && !current.armed()
                    ? R.string.playback_recovery_already_responsive : R.string.playback_recovery_refused;
            handler.post(() -> showCannotEnd(message));
            return;
        }
        if (!PlaybackRecoveryMonitor.recordUserExit(this, selected)) {
            handler.post(() -> showCannotEnd(R.string.playback_recovery_refused));
            return;
        }
        if (destroyed || isFinishing() || request != selected
                || !PlaybackRecoveryMonitor.mayEndProcess(this, selected)) {
            handler.post(() -> showCannotEnd(R.string.playback_recovery_refused));
            return;
        }
        try {
            Process.killProcess(selected.target().pid());
        } catch (RuntimeException error) {
            handler.post(() -> showCannotEnd(R.string.playback_recovery_refused));
            return;
        }
        long startedMs = SystemClock.elapsedRealtime();
        handler.post(() -> awaitExit(startedMs));
    }

    private void showCannotEnd(int message) {
        if (destroyed || isFinishing()) return;
        description.setText(message);
        exit.setEnabled(true);
        if (message == R.string.playback_recovery_already_responsive) {
            exit.setText(R.string.playback_recovery_home);
            exit.setOnClickListener(view -> returnHome());
        } else {
            exit.setText(R.string.playback_recovery_exit);
            exit.setOnClickListener(view -> endPlayback());
        }
    }

    private void pollRecovery() {
        if (destroyed || isFinishing() || request == null) return;
        PlaybackRecoveryMonitor.Request current = PlaybackRecoveryMonitor.requestForToken(this, request.token());
        if (current != null && !current.armed()) {
            finishAndRemoveTask();
            return;
        }
        handler.postDelayed(recoveryPoll, 1000);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        request = PlaybackRecoveryMonitor.requestForToken(this,
                intent.getStringExtra(PlaybackRecoveryMonitor.EXTRA_TOKEN));
        if (request == null) {
            finishAndRemoveTask();
            return;
        }
        description.setText(R.string.playback_recovery_description);
        exit.setText(R.string.playback_recovery_exit);
        exit.setEnabled(true);
        exit.setOnClickListener(view -> endPlayback());
    }

    private void awaitExit(long startedMs) {
        if (isFinishing() || isDestroyed()) return;
        PlaybackRecoveryPolicy.Target current = PlaybackRecoveryMonitor.readTarget(request.target().pid());
        if (!request.target().equals(current)) {
            returnHome();
        } else if (SystemClock.elapsedRealtime() - startedMs >= 5000) {
            description.setText(R.string.playback_recovery_refused);
            exit.setText(R.string.playback_recovery_home);
            exit.setEnabled(true);
            exit.setOnClickListener(view -> returnHome());
        } else {
            handler.postDelayed(() -> awaitExit(startedMs), 100);
        }
    }

    private void returnHome() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getApplicationInfo().packageName);
        if (launch == null) launch = getPackageManager().getLeanbackLaunchIntentForPackage(getApplicationInfo().packageName);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(launch); // Launcher only: never carry the failed media Intent.
        }
        finishAndRemoveTask();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
