package com.fongmi.android.tv.lab;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityLabTerminalBinding;
import com.fongmi.android.tv.setting.Setting;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;

public class LabTerminalActivity extends AppCompatActivity {

    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_PACKAGE = "package";

    private ActivityLabTerminalBinding mBinding;
    private Process process;
    private OutputStream stdin;
    private boolean stopped;
    private boolean ctrlMode;
    private int historyIndex = -1;
    private final LinkedList<String> history = new LinkedList<>();
    private final ArrayList<String> commandHistory = new ArrayList<>();
    private LabModels.Item item;

    public static void start(Context context, String title) {
        start(context, title, null);
    }

    public static void start(Context context, String title, String packageName) {
        Intent intent = new Intent(context, LabTerminalActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        if (packageName != null) intent.putExtra(EXTRA_PACKAGE, packageName);
        context.startActivity(intent);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(Setting.wrapDisplay(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityLabTerminalBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String packageName = getIntent().getStringExtra(EXTRA_PACKAGE);
        if (!TextUtils.isEmpty(packageName)) {
            LabModels.LabRoot root = LabConfig.get().getLabRoot();
            if (root != null && root.lists != null) {
                for (LabModels.Item candidate : root.lists) {
                    if (packageName.equals(candidate.name)) {
                        item = candidate;
                        break;
                    }
                }
            }
        }
        mBinding.termTitle.setText(TextUtils.isEmpty(title) ? "terminal" : title);
        mBinding.btnBack.setOnClickListener(v -> finish());
        mBinding.btnClear.setOnClickListener(v -> {
            history.clear();
            commandHistory.clear();
            historyIndex = -1;
            mBinding.termOutput.setText("");
            renderHistory();
        });
        mBinding.btnSend.setOnClickListener(v -> runInput());
        mBinding.termInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        mBinding.termInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_GO) {
                runInput();
                return true;
            }
            return false;
        });
        mBinding.termInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                navigateHistory(-1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                navigateHistory(1);
                return true;
            }
            return false;
        });
        buildShortcuts();
        append("实验室终端（交互式 shell）\n输入命令后回车执行，例如: ls、echo hello、apt update\n\n");
        startShell();
    }

    private void buildShortcuts() {
        String[] keys = {"ESC", "TAB", "CTRL", "↑", "↓", "←", "→", "/", "-", "|"};
        float density = getResources().getDisplayMetrics().density;
        for (String key : keys) {
            TextView button = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (30 * density));
            params.setMarginEnd((int) (2 * density));
            button.setLayoutParams(params);
            button.setGravity(android.view.Gravity.CENTER);
            button.setPadding((int) (10 * density), 0, (int) (10 * density), 0);
            button.setText(key);
            button.setTextColor(Color.parseColor("#CCCCCC"));
            button.setTextSize(11);
            button.setMaxLines(1);
            button.setBackground(null);
            button.setOnClickListener(v -> shortcut(key));
            mBinding.shortcutContainer.addView(button);
        }
    }

    private void shortcut(String key) {
        switch (key) {
            case "ESC":
                writeRaw(new byte[]{0x1b});
                break;
            case "TAB":
                writeRaw(new byte[]{0x09});
                break;
            case "CTRL":
                ctrlMode = !ctrlMode;
                mBinding.termPrompt.setTextColor(ctrlMode ? Color.parseColor("#FF8A65") : Color.parseColor("#AAFFAA"));
                break;
            case "↑":
                navigateHistory(-1);
                break;
            case "↓":
                navigateHistory(1);
                break;
            case "←":
                writeRaw(new byte[]{0x1b, 0x5b, 0x44});
                break;
            case "→":
                writeRaw(new byte[]{0x1b, 0x5b, 0x43});
                break;
            default:
                insertText(key);
                break;
        }
    }

    private void runInput() {
        String command = mBinding.termInput.getText() == null ? "" : mBinding.termInput.getText().toString();
        if (command.isEmpty()) return;
        if (ctrlMode && command.length() > 0) {
            char c = Character.toLowerCase(command.charAt(0));
            if (c >= 'a' && c <= 'z') {
                writeRaw(new byte[]{(byte) (c - '`')});
                append("^" + Character.toUpperCase(c) + "\n");
            }
            mBinding.termInput.setText("");
            ctrlMode = false;
            mBinding.termPrompt.setTextColor(Color.parseColor("#AAFFAA"));
            return;
        }
        mBinding.termInput.setText("");
        if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
            commandHistory.add(command);
            if (commandHistory.size() > 50) commandHistory.remove(0);
        }
        historyIndex = commandHistory.size();
        renderHistory();
        append("$ " + command + "\n");
        try {
            if (process == null || !process.isAlive()) startShell();
            if (stdin != null) {
                stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }
        } catch (Exception e) {
            append("[写入失败: " + e.getMessage() + "]\n");
        }
    }

    private void insertText(String text) {
        int start = mBinding.termInput.getSelectionStart();
        int end = mBinding.termInput.getSelectionEnd();
        if (start < 0) start = 0;
        if (end < 0) end = 0;
        mBinding.termInput.getText().replace(Math.min(start, end), Math.max(start, end), text, 0, text.length());
    }

    private void writeRaw(byte[] bytes) {
        try {
            if (process == null || !process.isAlive()) startShell();
            if (stdin != null) {
                stdin.write(bytes);
                stdin.flush();
            }
        } catch (Exception ignored) {
        }
    }

    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty()) return;
        historyIndex += direction;
        if (historyIndex < 0) historyIndex = 0;
        if (historyIndex >= commandHistory.size()) {
            historyIndex = commandHistory.size();
            mBinding.termInput.setText("");
        } else {
            String text = commandHistory.get(historyIndex);
            mBinding.termInput.setText(text);
            mBinding.termInput.setSelection(text.length());
        }
    }

    private void renderHistory() {
        mBinding.historyContainer.removeAllViews();
        if (commandHistory.isEmpty()) {
            mBinding.historyScroll.setVisibility(View.GONE);
            return;
        }
        mBinding.historyScroll.setVisibility(View.VISIBLE);
        float density = getResources().getDisplayMetrics().density;
        int max = Math.max(0, commandHistory.size() - 10);
        for (int i = commandHistory.size() - 1; i >= max; i--) {
            String text = commandHistory.get(i);
            TextView chip = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (28 * density));
            params.setMarginEnd((int) (6 * density));
            chip.setLayoutParams(params);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding((int) (12 * density), 0, (int) (12 * density), 0);
            chip.setText(text.length() > 30 ? text.substring(0, 30) + "…" : text);
            chip.setTextColor(Color.parseColor("#FF8A65"));
            chip.setTextSize(11);
            chip.setBackgroundResource(R.drawable.shape_lab_output_bg);
            chip.setOnClickListener(v -> {
                mBinding.termInput.setText(text);
                mBinding.termInput.setSelection(text.length());
            });
            mBinding.historyContainer.addView(chip);
        }
    }

    private void startShell() {
        if (process != null && process.isAlive()) return;
        try {
            ProcessBuilder builder = new ProcessBuilder("/system/bin/sh");
            builder.redirectErrorStream(true);
            if (item != null) {
                File cwd = LabEnv.packageRoot(this, item);
                builder.directory(cwd);
                LabRunner.applyEnv(builder.environment(), this, item);
                builder.environment().put("HOME", cwd.getAbsolutePath());
                builder.environment().put("TERM", "dumb");
            }
            process = builder.start();
            stdin = process.getOutputStream();
            stopped = false;
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!stopped) append(line + "\n");
                    }
                } catch (Exception ignored) {
                }
                if (!stopped) append("\n[shell 已退出，输入命令可重启]\n");
            }).start();
        } catch (Exception e) {
            append("启动 shell 失败: " + e.getMessage() + "\n");
        }
    }

    private void stopProcess() {
        stopped = true;
        try {
            if (stdin != null) stdin.close();
        } catch (Exception ignored) {
        }
        if (process != null) {
            process.destroy();
            process = null;
        }
        stdin = null;
    }

    private void append(String text) {
        App.post(() -> {
            mBinding.termOutput.append(text);
            ScrollView scroll = mBinding.termScroll;
            int range = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
            boolean atBottom = scroll.getScrollY() >= range - 4;
            if (atBottom) scroll.fullScroll(View.FOCUS_DOWN);
        });
    }

    @Override
    protected void onDestroy() {
        stopProcess();
        super.onDestroy();
    }
}
