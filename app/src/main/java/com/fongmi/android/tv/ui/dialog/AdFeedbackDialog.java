package com.fongmi.android.tv.ui.dialog;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ad.feedback.AdAttribution;
import com.fongmi.android.tv.ad.feedback.AdFeedbackSession;
import com.fongmi.android.tv.ad.feedback.RemediationKind;
import com.fongmi.android.tv.ad.feedback.RiskLevel;
import com.fongmi.android.tv.ad.feedback.StartOrigin;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 区间反馈对话框。第一屏在提交瞬间就出，第二屏在归因完成后原地替换内容。
 *
 * <p>视图用代码搭建而非 layout：三端共用一份，且内容是不定长的证据列表，
 * 用 layout 反而要为每端各维护一份文件。见设计文档第 4.3 节。
 */
public final class AdFeedbackDialog implements AutoCloseable {

    /** 用户确认保存规则。 */
    public interface Callback {
        void onSave(AdAttribution plan);
    }

    private final WeakReference<FragmentActivity> activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Callback callback;
    private AlertDialog dialog;
    private TextView body;
    private String feedbackId;
    private boolean closed;

    public AdFeedbackDialog(FragmentActivity activity, Callback callback) {
        this.activity = new WeakReference<>(activity);
        this.callback = callback;
    }

    /** 展示或原地刷新。同一 feedbackId 的后续调用只替换正文。 */
    public void show(AdFeedbackSession session) {
        if (session == null) return;
        runOnMain(() -> showInternal(session));
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

    private void showInternal(AdFeedbackSession session) {
        FragmentActivity owner = usableActivity();
        if (owner == null || closed) return;

        // 同一次反馈的第二屏：只换正文，避免对话框闪一下丢焦点
        if (dialog != null && dialog.isShowing() && session.feedbackId().equals(feedbackId)) {
            if (body != null) body.setText(describe(owner, session));
            applyButtons(session);
            return;
        }

        dismissInternal();
        feedbackId = session.feedbackId();

        ScrollView scroll = new ScrollView(owner);
        LinearLayout content = new LinearLayout(owner);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(20);
        content.setPadding(padding, padding, padding, padding);
        body = new TextView(owner);
        body.setTextSize(14f);
        body.setLineSpacing(0f, 1.2f);
        body.setText(describe(owner, session));
        content.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scroll.addView(content);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(owner)
                .setTitle(title(owner, session))
                .setView(scroll)
                .setNegativeButton(R.string.ad_interval_dismiss, (d, which) -> d.dismiss());
        if (session.hasActionablePlan()) {
            builder.setPositiveButton(R.string.ad_interval_save, (d, which) -> onSave(session));
        }
        dialog = builder.create();
        dialog.setOnDismissListener(d -> {
            dialog = null;
            body = null;
        });
        dialog.show();
        centerButtons();
    }

    /**
     * 归因完成后可能才出现「保存规则」按钮。AlertDialog 不支持事后加按钮，
     * 因此重建一次；此时用户尚未与内容交互，重建代价可接受。
     */
    private void applyButtons(AdFeedbackSession session) {
        if (dialog == null) return;
        boolean hasSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null
                && dialog.getButton(AlertDialog.BUTTON_POSITIVE).getVisibility() == View.VISIBLE;
        if (session.hasActionablePlan() == hasSave) return;
        feedbackId = null;
        showInternal(session);
    }

    private void onSave(AdFeedbackSession session) {
        if (callback == null || session.verdict() == null) return;
        AdAttribution plan = session.verdict().preferred();
        if (plan != null) callback.onSave(plan);
    }

    private String title(FragmentActivity owner, AdFeedbackSession session) {
        return owner.getString(R.string.ad_interval_title,
                clock(session.startMs()), clock(session.endMs()));
    }

    private String describe(FragmentActivity owner, AdFeedbackSession session) {
        StringBuilder text = new StringBuilder();
        text.append(owner.getString(R.string.ad_interval_duration, session.durationMs() / 1000f));
        text.append('\n').append(originText(owner, session));
        text.append('\n').append(owner.getString(session.skipApplied()
                ? R.string.ad_interval_skipped : R.string.ad_interval_recorded));

        if (!session.analysisComplete()) {
            text.append("\n\n").append(owner.getString(R.string.ad_interval_analyzing));
            return text.toString();
        }

        AdAttribution preferred = session.verdict().preferred();
        text.append("\n\n").append(planText(owner, preferred));
        if (preferred != null) {
            text.append("  ").append(riskText(owner, preferred.risk()));
            text.append(String.format(Locale.US, "  %d%%",
                    Math.round(preferred.confidence() * 100)));
        }

        appendLines(text, owner.getString(R.string.ad_interval_evidence),
                session.preferredEvidence());
        if (session.verdict() != null && !session.verdict().diagnostics().isEmpty()) {
            StringBuilder diagnostics = new StringBuilder();
            for (AdAttribution diagnostic : session.verdict().diagnostics()) {
                for (String line : diagnostic.evidence()) {
                    if (diagnostics.length() > 0) diagnostics.append('\n');
                    diagnostics.append("· ").append(line);
                }
            }
            if (diagnostics.length() > 0) {
                text.append("\n\n").append(owner.getString(R.string.ad_interval_diagnostics))
                        .append('\n').append(diagnostics);
            }
        }
        return text.toString();
    }

    private static void appendLines(StringBuilder text, String header, List<String> lines) {
        if (lines == null || lines.isEmpty()) return;
        text.append("\n\n").append(header);
        for (String line : lines) text.append("\n· ").append(line);
    }

    private static String originText(FragmentActivity owner, AdFeedbackSession session) {
        StartOrigin origin = session.startOrigin();
        if (origin == StartOrigin.USER_MARKED) return owner.getString(R.string.ad_interval_origin_marked);
        if (origin == StartOrigin.DISCONTINUITY) return owner.getString(R.string.ad_interval_origin_discontinuity);
        if (origin == StartOrigin.CROSS_DOMAIN) return owner.getString(R.string.ad_interval_origin_cross_domain);
        if (origin == StartOrigin.AUDIO_CANDIDATE) return owner.getString(R.string.ad_interval_origin_audio);
        return owner.getString(R.string.ad_interval_origin_window,
                (int) TimeUnit.MILLISECONDS.toSeconds(session.durationMs()));
    }

    private static String planText(FragmentActivity owner, AdAttribution plan) {
        if (plan == null) return owner.getString(R.string.ad_interval_plan_none);
        RemediationKind kind = plan.remediation();
        if (kind == RemediationKind.ENABLE_EXISTING_RULE) return owner.getString(R.string.ad_interval_plan_enable);
        if (kind == RemediationKind.HOST_BLACKLIST) return owner.getString(R.string.ad_interval_plan_host);
        if (kind == RemediationKind.HLS_STRUCTURED_RULE) return owner.getString(R.string.ad_interval_plan_hls);
        if (kind == RemediationKind.URL_REGEX_RULE) return owner.getString(R.string.ad_interval_plan_regex);
        return owner.getString(R.string.ad_interval_plan_none);
    }

    private static String riskText(FragmentActivity owner, RiskLevel risk) {
        if (risk == RiskLevel.LOW) return owner.getString(R.string.ad_interval_risk_low);
        if (risk == RiskLevel.HIGH) return owner.getString(R.string.ad_interval_risk_high);
        return owner.getString(R.string.ad_interval_risk_medium);
    }

    /** mm:ss，超过一小时用 h:mm:ss。 */
    static String clock(long millis) {
        long total = Math.max(0L, millis) / 1000L;
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    /** TV 上按钮默认贴右，遥控器操作时居中更容易命中。 */
    private void centerButtons() {
        if (dialog == null || dialog.getWindow() == null) return;
        View parent = dialog.getWindow().findViewById(android.R.id.button1);
        if (parent != null && parent.getParent() instanceof LinearLayout buttons) {
            buttons.setGravity(Gravity.CENTER_HORIZONTAL);
        }
    }

    private void dismissInternal() {
        if (dialog != null) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
        }
        dialog = null;
        body = null;
    }

    private FragmentActivity usableActivity() {
        FragmentActivity owner = activity.get();
        if (owner == null || owner.isFinishing() || owner.isDestroyed()) return null;
        return owner;
    }

    private void runOnMain(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else main.post(task);
    }
}
