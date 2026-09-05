package com.fongmi.android.tv.lab;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LabActions {

    private LabActions() {
    }

    public interface DoneCallback {
        void onDone();
    }

    public static void install(FragmentActivity activity, LabModels.Item item, DoneCallback done) {
        PermissionUtil.requestFile(activity, granted -> {
            if (!granted) {
                new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                        .setTitle("需要存储权限")
                        .setMessage("安装环境需要读取手机 /storage/emulated/0/VodPlus/EnvFiles/ 里的压缩包。\n请在系统设置中授予「所有文件访问」权限后重试。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("去授权", (d, w) -> PermissionUtil.requestFile(activity, granted2 -> {
                            if (granted2) startInstall(activity, item, done);
                        }))
                        .show();
                return;
            }
            startInstall(activity, item, done);
        });
    }

    public static void installWithDialog(FragmentActivity activity, LabModels.Item item, DoneCallback done) {
        PermissionUtil.requestFile(activity, granted -> {
            if (!granted) {
                new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                        .setTitle("需要存储权限")
                        .setMessage("安装环境需要读取手机 /storage/emulated/0/VodPlus/EnvFiles/ 里的压缩包。\n请在系统设置中授予「所有文件访问」权限后重试。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("去授权", (d, w) -> PermissionUtil.requestFile(activity, granted2 -> {
                            if (granted2) startInstallWithDialog(activity, item, done);
                        }))
                        .show();
                return;
            }
            startInstallWithDialog(activity, item, done);
        });
    }

    private static void startInstallWithDialog(FragmentActivity activity, LabModels.Item item, DoneCallback done) {
        LabModels.Download download = pickDownload(item);
        if (download == null) {
            Notify.show("当前架构不支持: " + LabEnv.arch());
            return;
        }
        View root = activity.getLayoutInflater().inflate(R.layout.dialog_lab_download, null, false);
        TextView status = root.findViewById(R.id.status);
        ProgressBar progress = root.findViewById(R.id.progress);
        TextView detail = root.findViewById(R.id.detail);
        List<String> mirrors = download.getMirrorNames();
        int[] mirrorIndex = {0};
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                .setTitle("下载 " + item.name)
                .setView(root)
                .setCancelable(false)
                .setNegativeButton("取消", null)
                .setPositiveButton("关闭", null)
                .create();
        progress.setIndeterminate(true);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "取消", (d, w) -> d.dismiss());
        if (mirrors.size() > 1) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "切换源", (d, w) -> {
                mirrorIndex[0] = (mirrorIndex[0] + 1) % mirrors.size();
                status.setText("连接 " + mirrors.get(mirrorIndex[0]) + "...");
                progress.setProgress(0);
                detail.setText("");
            });
        }
        dialog.show();
        hideCloseButton(dialog);
        LabEnv.install(activity, item, mirrors.get(mirrorIndex[0]), new LabEnv.InstallCallback() {
            @Override
            public void onProgress(String message) {
                App.post(() -> {
                    status.setText(message);
                    detail.setText("");
                    progress.setIndeterminate(true);
                });
            }

            @Override
            public void onDownloadProgress(long done, long total) {
                App.post(() -> {
                    if (total > 0) {
                        int percent = (int) (done * 100 / total);
                        progress.setIndeterminate(false);
                        progress.setMax(100);
                        progress.setProgress(percent);
                        status.setText("下载中 ...");
                        detail.setText(percent + "%  │  " + LabEnv.formatSize(done) + " / " + LabEnv.formatSize(total));
                    } else {
                        progress.setIndeterminate(true);
                        status.setText("下载中 ...");
                        detail.setText(LabEnv.formatSize(done));
                    }
                });
            }

            @Override
            public void onUnzipProgress(long done, long total) {
                App.post(() -> {
                    if (total > 0) {
                        int percent = (int) (done * 100 / total);
                        progress.setIndeterminate(false);
                        progress.setMax(100);
                        progress.setProgress(percent);
                        status.setText("解压中 ...");
                        detail.setText(percent + "%  │  " + LabEnv.formatSize(done) + " / " + LabEnv.formatSize(total));
                    } else {
                        progress.setIndeterminate(true);
                        status.setText("解压中 ...");
                        detail.setText(LabEnv.formatSize(done));
                    }
                });
            }

            @Override
            public void onFinalizing() {
                // 解压已完成、正在做不可量化的收尾（chmod 整目录、symlink、写 installed version）。
                // 进度条保持 100% 满格、只切状态文字——避免出现"100% 却还在圈圈转"的歧义。
                App.post(() -> {
                    progress.setIndeterminate(false);
                    progress.setMax(100);
                    progress.setProgress(100);
                    status.setText("正在完成安装 ...");
                    detail.setText("");
                });
            }

            @Override
            public void onDone() {
                App.post(() -> {
                    if (dialog.isShowing()) dialog.dismiss();
                    Notify.show(item.name + " 安装完成");
                    runAutoCommands(activity, item);
                    if (done != null) done.onDone();
                });
            }

            @Override
            public void onError(String message) {
                App.post(() -> {
                    status.setText("安装失败：\n" + message);
                    detail.setText("");
                    progress.setVisibility(View.GONE);
                    showCloseButton(dialog);
                });
            }
        });
    }

    /**
     * 进度弹窗刚弹出时先藏起"关闭"按钮：按钮必须在 show() 前注册，
     * 因为 AlertDialog.setButton() 在 show() 之后调用不会生效。
     */
    static void hideCloseButton(AlertDialog dialog) {
        android.widget.Button close = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (close != null) close.setVisibility(View.GONE);
    }

    /** 失败态：放出"关闭"按钮并恢复返回键，避免进度弹窗无法关闭。 */
    static void showCloseButton(AlertDialog dialog) {
        dialog.setCancelable(true);
        android.widget.Button close = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (close != null) close.setVisibility(View.VISIBLE);
    }

    private static LabModels.Download pickDownload(LabModels.Item item) {
        if (item.downloads == null || item.downloads.isEmpty()) return null;
        String arch = LabEnv.arch();
        for (LabModels.Download download : item.downloads) {
            if (arch.equals(download.arch)) return download;
        }
        return item.downloads.get(0);
    }

    private static void startInstall(FragmentActivity activity, LabModels.Item item, DoneCallback done) {
        TextView progress = new TextView(activity);
        progress.setTextColor(Color.WHITE);
        progress.setTextSize(14);
        progress.setPadding(48, 32, 48, 32);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                .setTitle("安装 " + item.name)
                .setView(progress)
                .setCancelable(false)
                .setPositiveButton("关闭", null)
                .create();
        dialog.show();
        hideCloseButton(dialog);
        LabEnv.install(activity, item, new LabEnv.InstallCallback() {
            @Override
            public void onProgress(String message) {
                progress.setText(message);
            }

            @Override
            public void onUnzipProgress(long done, long total) {
                if (total > 0) {
                    int percent = (int) (done * 100 / total);
                    progress.setText("解压中 " + percent + "%  │  " + LabEnv.formatSize(done) + " / " + LabEnv.formatSize(total));
                } else {
                    progress.setText("解压中 " + LabEnv.formatSize(done));
                }
            }

            @Override
            public void onFinalizing() {
                // 解压已 100% 完成、正在做不可量化的收尾（chmod 整目录、symlink、写 installed version）
                progress.setText("正在完成安装 ...");
            }

            @Override
            public void onDone() {
                dialog.dismiss();
                Notify.show(item.name + " 安装完成");
                runAutoCommands(activity, item);
                if (done != null) App.post(done::onDone);
            }

            @Override
            public void onError(String message) {
                progress.setText("安装失败：\n" + message);
                showCloseButton(dialog);
            }
        });
    }

    private static void runAutoCommands(Activity activity, LabModels.Item item) {
        if (item.commands == null) return;
        for (LabModels.Command command : item.commands) {
            if (!command.auto_execute) continue;
            Map<String, String> vars = defaults(command);
            LabRunner.run(activity, item, command, vars, new LabRunner.OutputListener() {
                @Override
                public void onOutput(String text) {
                }

                @Override
                public void onExit(int code) {
                    App.post(() -> Notify.show(command.name + (code == 0 ? " 已启动" : " 已退出")));
                }
            });
        }
        for (LabCustomCommands.CustomCommand custom : LabCustomCommands.list(item.name)) {
            if (!custom.autoExecute) continue;
            String key = item.name + "/" + (custom.id != null ? custom.id : "custom_" + System.currentTimeMillis());
            LabRunner.runCustom(activity, item, custom.command, new HashMap<>(), key, new LabRunner.OutputListener() {
                @Override
                public void onOutput(String text) {
                }

                @Override
                public void onExit(int code) {
                    App.post(() -> Notify.show(custom.name + (code == 0 ? " 已启动" : " 已退出")));
                }
            });
        }
    }

    private static Map<String, String> defaults(LabModels.Command command) {
        Map<String, String> vars = new HashMap<>();
        if (command.variables != null) {
            for (LabModels.Variable variable : command.variables) {
                if (variable.defaultValue != null) vars.put(variable.key, variable.defaultValue);
            }
        }
        return vars;
    }

    public static void uninstall(FragmentActivity activity, LabModels.Item item, DoneCallback done) {
        new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                .setTitle("卸载 " + item.name)
                .setMessage("确定卸载该环境吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("卸载", (d, w) -> {
                    LabEnv.uninstall(activity, item);
                    Notify.show(item.name + " 已卸载");
                    if (done != null) done.onDone();
                })
                .show();
    }

    public static void stopItem(LabModels.Item item) {
        if (item.commands == null) return;
        for (LabModels.Command command : item.commands) {
            LabRunner.stop(item.name + "/" + command.id);
        }
    }

    public static void runCommand(Activity activity, LabModels.Item item, LabModels.Command command, DoneCallback done) {
        if (item == null) {
            Toast.makeText(activity, "未找到所属模块", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!LabEnv.installed(activity, item)) {
            Toast.makeText(activity, "请先安装 " + item.name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!command.isSupported(LabEnv.appVersionCode(activity))) {
            Toast.makeText(activity, "需要更新影视+至 v" + command.min_version + " 以上", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, String> vars = defaults(command);
        if (command.cachedVariableValues != null) vars.putAll(command.cachedVariableValues);
        boolean needInput = false;
        if (command.variables != null) {
            for (LabModels.Variable variable : command.variables) {
                if (variable.required && TextUtils.isEmpty(vars.get(variable.key)) && TextUtils.isEmpty(variable.defaultValue)) {
                    needInput = true;
                    break;
                }
            }
        }
        if (needInput) {
            LabVariableDialog.show(activity, command.name, command.variables, filled -> {
                Map<String, String> merged = new HashMap<>(vars);
                merged.putAll(filled);
                startCommand(activity, item, command, merged, done);
            });
            return;
        }
        startCommand(activity, item, command, vars, done);
    }

    private static void startCommand(Activity activity, LabModels.Item item, LabModels.Command command, Map<String, String> vars, DoneCallback done) {
        if (command.download != null && LabEnv.dependencyNeeded(command.download)) {
            TextView progress = new TextView(activity);
            progress.setTextColor(Color.WHITE);
            progress.setTextSize(14);
            progress.setPadding(48, 32, 48, 32);
            AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                    .setTitle("下载依赖" + (command.download.title == null ? "" : " · " + command.download.title))
                    .setView(progress)
                    .setCancelable(false)
                    .setPositiveButton("关闭", null)
                    .create();
            dialog.show();
            hideCloseButton(dialog);
            new Thread(() -> {
                try {
                    LabEnv.prepareDependency(activity, command.download, progress::setText);
                    App.post(() -> {
                        dialog.dismiss();
                        startCommandInner(activity, item, command, vars, done);
                    });
                } catch (Exception e) {
                    App.post(() -> {
                        progress.setText("依赖下载失败：\n" + e.getMessage());
                        showCloseButton(dialog);
                    });
                }
            }).start();
            return;
        }
        startCommandInner(activity, item, command, vars, done);
    }

    private static void startCommandInner(Activity activity, LabModels.Item item, LabModels.Command command, Map<String, String> vars, DoneCallback done) {
        String key = item.name + "/" + command.id;
        if (!command.isShowOutput()) {
            Toast.makeText(activity, "已在后台运行: " + command.name, Toast.LENGTH_SHORT).show();
            LabRunner.run(activity, item, command, vars, new LabRunner.OutputListener() {
                @Override
                public void onOutput(String text) {
                }

                @Override
                public void onExit(int code) {
                    App.post(() -> {
                        if (done != null) done.onDone();
                    });
                }
            });
            if (done != null) done.onDone();
            return;
        }
        LabOutputDialog output = new LabOutputDialog(activity, key);
        output.setExitAction(() -> App.post(() -> {
            if (done != null) done.onDone();
            if (output.isShowing()) showClicks(activity, command, vars);
        }));
        output.show(command.name + " - " + item.name);
        LabRunner.run(activity, item, command, vars, output);
        if (done != null) done.onDone();
    }

    public static void runCustom(Activity activity, LabModels.Item item, LabCustomCommands.CustomCommand custom, DoneCallback done) {
        if (item == null || custom == null || custom.command == null) {
            Toast.makeText(activity, "未找到所属模块", Toast.LENGTH_SHORT).show();
            return;
        }
        String commandText = custom.command;
        List<String> placeholders = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}").matcher(commandText);
        while (matcher.find()) {
            if (matcher.start() > 0 && commandText.charAt(matcher.start() - 1) == '$') continue;
            String key = matcher.group(1);
            if ("dataPath".equals(key) || "envRootPath".equals(key) || "serverPort".equals(key) || "sdcard".equals(key)) continue;
            if (!placeholders.contains(key)) placeholders.add(key);
        }
        String key = item.name + "/" + (custom.id != null ? custom.id : "custom_" + System.currentTimeMillis());
        if (!placeholders.isEmpty()) {
            List<LabModels.Variable> variables = new ArrayList<>();
            for (String placeholder : placeholders) {
                LabModels.Variable variable = new LabModels.Variable();
                variable.key = placeholder;
                variable.name = placeholder;
                variable.type = "text";
                variables.add(variable);
            }
            LabVariableDialog.show(activity, "自定义命令参数", variables, vars -> startCustom(activity, item, commandText, vars, key, done));
        } else {
            startCustom(activity, item, commandText, new HashMap<>(), key, done);
        }
    }

    private static void startCustom(Activity activity, LabModels.Item item, String commandText, Map<String, String> vars, String key, DoneCallback done) {
        LabOutputDialog output = new LabOutputDialog(activity, key);
        output.setExitAction(() -> App.post(() -> {
            if (done != null) done.onDone();
        }));
        output.show("自定义命令");
        LabRunner.runCustom(activity, item, commandText, vars, key, output);
        if (done != null) done.onDone();
    }

    private static void showClicks(Activity activity, LabModels.Command command, Map<String, String> vars) {
        if (command.clicks == null || command.clicks.isEmpty()) return;
        List<LabModels.Click> clicks = command.clicks;
        String[] titles = new String[clicks.size()];
        for (int i = 0; i < clicks.size(); i++) titles[i] = clicks.get(i).title == null ? clicks.get(i).action : clicks.get(i).title;
        new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                .setTitle("后续操作")
                .setItems(titles, (d, w) -> performClick(activity, command, clicks.get(w), vars))
                .setNegativeButton("关闭", null)
                .show();
    }

    public static void performClick(Activity activity, LabModels.Command command, LabModels.Click click, Map<String, String> vars) {
        String value = click.value == null ? "" : click.value;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            if (e.getValue() != null) value = value.replace("{" + e.getKey() + "}", e.getValue());
        }
        value = value.replace("{dataPath}", LabConfig.dataPath()).replace("{serverPort}", LabConfig.serverPort());
        switch (click.action == null ? "" : click.action) {
            case "open":
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value)));
                } catch (Exception e) {
                    Toast.makeText(activity, "无法打开: " + value, Toast.LENGTH_SHORT).show();
                }
                break;
            case "webview":
                openWebView(activity, click, value);
                break;
            case "qrcode":
                showQrCode(activity, click, value);
                break;
            case "log":
                showLog(activity, command);
                break;
            case "run":
                runCommand(activity, findItem(command), command, null);
                break;
            case "stop":
                LabRunner.stop(commandKey(command));
                break;
            case "restart":
                LabRunner.stop(commandKey(command));
                App.post(() -> runCommand(activity, findItem(command), command, null), 300);
                break;
            case "copy":
                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("lab", value));
                Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show();
                break;
            case "alert":
                new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                        .setTitle(click.title == null ? "提示" : click.title)
                        .setMessage(value)
                        .setPositiveButton("确定", null)
                        .show();
                break;
            case "toast":
                Toast.makeText(activity, value, Toast.LENGTH_LONG).show();
                break;
            case "share":
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, value);
                activity.startActivity(Intent.createChooser(share, "分享"));
                break;
            default:
                Toast.makeText(activity, "暂不支持: " + click.action, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private static String commandKey(LabModels.Command command) {
        LabModels.Item item = findItem(command);
        return (item == null ? "?" : item.name) + "/" + command.id;
    }

    private static LabModels.Item findItem(LabModels.Command command) {
        LabModels.LabRoot root = LabConfig.get().getLabRoot();
        if (root == null || root.lists == null) return null;
        for (LabModels.Item item : root.lists) {
            if (item.commands != null && item.commands.contains(command)) return item;
        }
        return null;
    }

    private static void openWebView(Activity activity, LabModels.Click click, String url) {
        boolean fullscreen = "fullscreen".equals(click.style);
        int width = click.width > 0 ? click.width : 90;
        int height = click.height > 0 ? click.height : 80;
        View root = activity.getLayoutInflater().inflate(R.layout.dialog_lab_webview, null, false);
        ProgressBar progress = root.findViewById(R.id.progress);
        WebView webView = root.findViewById(R.id.webView);
        TextView content = root.findViewById(R.id.content);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? android.view.View.GONE : android.view.View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient());
        if (click.content != null && !click.content.isEmpty()) {
            content.setText(click.content);
            content.setVisibility(View.VISIBLE);
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                .setTitle(click.title == null ? "WebView" : click.title)
                .setView(root)
                .setNegativeButton("关闭", (d, w) -> {
                    webView.destroy();
                    d.dismiss();
                })
                .setPositiveButton("浏览器打开", (d, w) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {
                    }
                    webView.destroy();
                    d.dismiss();
                })
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            int w = activity.getResources().getDisplayMetrics().widthPixels;
            int h = activity.getResources().getDisplayMetrics().heightPixels;
            params.width = fullscreen ? WindowManager.LayoutParams.MATCH_PARENT : (int) (w * width / 100f);
            params.height = fullscreen ? WindowManager.LayoutParams.MATCH_PARENT : (int) (h * height / 100f);
            dialog.getWindow().setAttributes(params);
        }
        webView.loadUrl(url);
    }

    private static void showQrCode(Activity activity, LabModels.Click click, String value) {
        if (value == null || value.isEmpty()) return;
        int size = (int) (activity.getResources().getDisplayMetrics().density * 200);
        Bitmap bitmap;
        try {
            QRCodeWriter writer = new QRCodeWriter();
            java.util.Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = writer.encode(value, BarcodeFormat.QR_CODE, size, size, hints);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixels[y * size + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        } catch (Exception e) {
            Toast.makeText(activity, "二维码生成失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        View root = activity.getLayoutInflater().inflate(R.layout.dialog_lab_qrcode, null, false);
        ImageView image = root.findViewById(R.id.qrImage);
        TextView text = root.findViewById(R.id.value);
        TextView content = root.findViewById(R.id.content);
        image.setImageBitmap(bitmap);
        text.setText(value);
        if (click.content != null && !click.content.isEmpty()) {
            content.setText(click.content);
            content.setVisibility(View.VISIBLE);
        }
        new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                .setTitle(click.title == null ? "二维码" : click.title)
                .setView(root)
                .setNegativeButton("关闭", null)
                .setPositiveButton("复制链接", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("lab", value));
                    Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private static void showLog(Activity activity, LabModels.Command command) {
        String key = commandKey(command);
        String log = LabRunner.getLog(key);
        ScrollView scroll = new ScrollView(activity);
        TextView text = new TextView(activity);
        text.setTextColor(Color.parseColor("#00E676"));
        text.setTextSize(12);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        text.setText(TextUtils.isEmpty(log) ? "暂无日志输出" : log);
        scroll.addView(text);
        new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                .setTitle(command.name + " - 日志")
                .setView(scroll)
                .setNegativeButton("关闭", null)
                .setPositiveButton("复制", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("lab", log));
                    Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
