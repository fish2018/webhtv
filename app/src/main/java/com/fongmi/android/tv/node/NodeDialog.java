package com.fongmi.android.tv.node;

import android.app.Activity;
import android.app.Dialog;
import android.view.LayoutInflater;
import android.view.View;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogNodeProgressBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/**
 * 猫源首次启动的进度对话框：下载 Node 运行时要几十 MB，没有可见反馈的话
 * 用户只会看到界面卡住不动。
 *
 * <p>整个启动流程跑在配置加载的后台线程上，没有可靠的 Activity；所以这里
 * 每次更新都重新取前台 Activity，取不到就静默跳过（通知栏那份仍在推进度）。
 * 所有 UI 操作都 post 到主线程。
 */
final class NodeDialog {

    /** 速度按滑动窗口算，窗口太短会剧烈抖动。 */
    private static final long SPEED_WINDOW = 1500;

    private Dialog dialog;
    private DialogNodeProgressBinding binding;
    /** 弹窗依附的 Activity；它一旦销毁就必须放手，否则会把 Activity 一直持有到流程结束。 */
    private java.lang.ref.WeakReference<Activity> host;
    private long windowStart;
    private long windowBytes;
    private long lastDone;
    private String speed = "";

    static NodeDialog create() {
        return new NodeDialog();
    }

    void show() {
        App.post(() -> {
            Activity activity = App.activity();
            if (activity == null || activity.isFinishing()) return;
            if (dialog != null && dialog.isShowing()) return;
            try {
                binding = DialogNodeProgressBinding.inflate(LayoutInflater.from(activity));
                dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                        .setTitle(R.string.node_title)
                        .setView(binding.getRoot())
                        // 允许退到后台：下载可能要一分钟以上，不该把用户锁在弹窗里
                        .setNegativeButton(R.string.node_background, (d, w) -> d.dismiss())
                        .setCancelable(true)
                        .create();
                host = new java.lang.ref.WeakReference<>(activity);
                dialog.show();
            } catch (Exception ignored) {
            }
        });
    }

    /** 阶段性文案，无字节进度（如"解压中"、"启动服务"）。 */
    void status(String text) {
        App.post(() -> {
            if (!alive()) return;
            binding.status.setText(text);
            binding.progress.setIndeterminate(true);
            binding.detail.setText("");
            binding.speed.setText("");
        });
        resetSpeed();
    }

    /** 下载进度：百分比、已下载/总量、实时速度。 */
    void progress(String text, long done, long total) {
        String detail = total > 0 ? App.get().getString(R.string.node_size_pair, size(done), size(total)) : size(done);
        int percent = total > 0 ? (int) Math.min(100, done * 100 / total) : -1;
        String rate = rate(done);
        App.post(() -> {
            if (!alive()) return;
            binding.status.setText(percent >= 0 ? text + "  " + percent + "%" : text);
            binding.detail.setText(detail);
            binding.speed.setText(rate);
            if (percent >= 0) {
                binding.progress.setIndeterminate(false);
                binding.progress.setMax(100);
                binding.progress.setProgressCompat(percent, true);
            } else {
                binding.progress.setIndeterminate(true);
            }
        });
    }

    void dismiss() {
        App.post(() -> {
            try {
                if (dialog != null && dialog.isShowing()) dialog.dismiss();
            } catch (Exception ignored) {
            } finally {
                detach();
            }
        });
    }

    /**
     * 对话框还在、且它当初依附的那个 Activity 仍然有效。
     *
     * <p>判据必须是 host 而不是当前前台 Activity：用户切到别的页面后旧 Activity 会销毁，
     * 此时继续操作它的视图会抛异常，也会把它一直持有着。
     */
    private boolean alive() {
        if (dialog == null || binding == null) return false;
        Activity activity = host == null ? null : host.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            detach();
            return false;
        }
        return dialog.isShowing();
    }

    /** host 已失效时就地放手，不再触碰它的视图。 */
    private void detach() {
        dialog = null;
        binding = null;
        host = null;
    }

    private void resetSpeed() {
        windowStart = 0;
        windowBytes = 0;
        lastDone = 0;
        speed = "";
    }

    /**
     * 由累计已下载字节推算速度。回调间隔不固定，所以累计一个时间窗口再折算，
     * 窗口未满就沿用上一次的值，避免数字乱跳。
     */
    private String rate(long done) {
        long now = System.currentTimeMillis();
        if (windowStart == 0) {
            windowStart = now;
            lastDone = done;
            return speed;
        }
        long delta = done - lastDone;
        if (delta > 0) windowBytes += delta;
        lastDone = done;
        long elapsed = now - windowStart;
        if (elapsed >= SPEED_WINDOW) {
            long perSecond = windowBytes * 1000 / elapsed;
            speed = App.get().getString(R.string.node_speed, size(perSecond));
            windowStart = now;
            windowBytes = 0;
        }
        return speed;
    }

    private static String size(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1048576) return String.format(Locale.US, "%.0fKB", bytes / 1024f);
        return String.format(Locale.US, "%.1fMB", bytes / 1048576f);
    }
}
