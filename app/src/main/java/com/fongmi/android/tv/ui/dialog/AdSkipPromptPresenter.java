package com.fongmi.android.tv.ui.dialog;

import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ad.audio.AdSkipCoordinator;
import com.fongmi.android.tv.ad.audio.SpeechAdSignalProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AdSkipPromptPresenter implements AdSkipCoordinator.UiPort, AutoCloseable {

    private final WeakReference<FragmentActivity> activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private Runnable timeout;
    private long presentationToken;
    private boolean closed;

    public AdSkipPromptPresenter(FragmentActivity activity) {
        this.activity = new WeakReference<>(activity);
    }

    @Override
    public void showCandidate(AdSkipCoordinator.Prompt prompt, AdSkipCoordinator.Actions actions) {
        runOnMain(() -> showCandidateInternal(prompt, actions));
    }

    @Override
    public void showUndo(AdSkipCoordinator.UndoPrompt prompt, AdSkipCoordinator.Actions actions) {
        runOnMain(() -> showUndoInternal(prompt, actions));
    }

    @Override
    public void dismiss(long sessionId) {
        runOnMain(this::dismissInternal);
    }

    @Override
    public void close() {
        runOnMain(() -> {
            if (closed) return;
            closed = true;
            dismissInternal();
            activity.clear();
        });
    }

    private void showCandidateInternal(AdSkipCoordinator.Prompt prompt,
                                       AdSkipCoordinator.Actions actions) {
        FragmentActivity owner = usableActivity();
        if (owner == null) return;
        dismissInternal();
        long token = ++presentationToken;
        AtomicBoolean handled = new AtomicBoolean();
        boolean speech = SpeechAdSignalProvider.RULE_ID.equals(prompt.ruleId());
        int title = speech
                ? R.string.ad_audio_speech_candidate_title
                : (prompt.fullMatch() ? R.string.ad_audio_match_title : R.string.ad_audio_candidate_title);
        long duration = prompt.skipDurationSeconds();
        String message = speech
                ? owner.getString(R.string.ad_audio_speech_candidate_message, duration)
                : owner.getString(R.string.ad_audio_candidate_message, prompt.ruleId(), duration);
        dialog = new MaterialAlertDialogBuilder(owner, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ad_audio_skip, (ignored, which) ->
                        act(token, handled, actions::confirm))
                .setNegativeButton(R.string.ad_audio_ignore, (ignored, which) ->
                        act(token, handled, actions::ignore))
                .setOnCancelListener(ignored -> act(token, handled, actions::ignore))
                .create();
        dialog.show();
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
        }
    }

    private void showUndoInternal(AdSkipCoordinator.UndoPrompt prompt,
                                  AdSkipCoordinator.Actions actions) {
        FragmentActivity owner = usableActivity();
        if (owner == null) return;
        dismissInternal();
        long token = ++presentationToken;
        AtomicBoolean handled = new AtomicBoolean();
        dialog = new MaterialAlertDialogBuilder(owner, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.ad_audio_skipped_title)
                .setMessage(owner.getString(R.string.ad_audio_skipped_message,
                        Math.max(1L, prompt.expiresAfterMs() / 1_000L)))
                .setPositiveButton(R.string.ad_audio_undo, (ignored, which) ->
                        act(token, handled, actions::undo))
                .setNegativeButton(R.string.dialog_negative, (ignored, which) ->
                        act(token, handled, actions::ignore))
                .setOnCancelListener(ignored -> act(token, handled, actions::ignore))
                .create();
        dialog.show();
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
        }
        timeout = () -> {
            if (!isCurrent(token) || !handled.compareAndSet(false, true)) return;
            actions.ignore();
            dismissInternal();
        };
        main.postDelayed(timeout, prompt.expiresAfterMs());
    }

    private void act(long token, AtomicBoolean handled, Runnable action) {
        if (!isCurrent(token) || !handled.compareAndSet(false, true)) return;
        action.run();
    }

    private boolean isCurrent(long token) {
        return !closed && token == presentationToken && usableActivity() != null;
    }

    private FragmentActivity usableActivity() {
        FragmentActivity owner = activity.get();
        if (owner == null || owner.isFinishing() || owner.isDestroyed()) return null;
        return owner;
    }

    private void dismissInternal() {
        presentationToken++;
        if (timeout != null) main.removeCallbacks(timeout);
        timeout = null;
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else main.post(action);
    }
}
