package com.fongmi.android.tv.lab;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityLabOutputBinding;

import java.util.HashMap;

public class LabOutputActivity extends AppCompatActivity {

    private static LabOutputActivity sInstance;

    private ActivityLabOutputBinding mBinding;
    private LabModels.Item item;
    private LabModels.Command command;
    private String itemName;
    private String commandId;
    private HashMap<String, String> vars;

    public static void start(Context context, String itemName, String commandId, HashMap<String, String> vars) {
        Intent intent = new Intent(context, LabOutputActivity.class);
        intent.putExtra("item", itemName);
        intent.putExtra("command", commandId);
        intent.putExtra("vars", vars);
        context.startActivity(intent);
    }

    public static void appendGlobal(String text) {
        if (sInstance != null) sInstance.append(text);
    }

    public static void onExitGlobal(int code) {
        if (sInstance != null) sInstance.onExit(code);
    }

    private String key() {
        return itemName + "/" + commandId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityLabOutputBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        sInstance = this;

        itemName = getIntent().getStringExtra("item");
        commandId = getIntent().getStringExtra("command");
        //noinspection unchecked
        vars = (HashMap<String, String>) getIntent().getSerializableExtra("vars");
        if (vars == null) vars = new HashMap<>();

        item = findItem(itemName);
        command = findCommand(item, commandId);

        mBinding.btnClose.setOnClickListener(v -> finish());
        mBinding.btnStop.setOnClickListener(v -> stop());
        mBinding.btnSend.setOnClickListener(v -> sendInput());
        mBinding.inputEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_GO) {
                sendInput();
                return true;
            }
            return false;
        });

        mBinding.outputTitle.setText(command == null ? (commandId == null ? "输出" : commandId)
                : (command.name == null ? command.description : command.name));

        String log = LabRunner.getLog(key());
        if (!TextUtils.isEmpty(log)) mBinding.outputText.setText(log);

        boolean running = LabRunner.isRunning(key());
        showRunning(running);
    }

    private LabModels.Item findItem(String name) {
        if (name == null) return null;
        LabModels.LabRoot root = LabConfig.get().getLabRoot();
        if (root != null && root.lists != null) {
            for (LabModels.Item candidate : root.lists) {
                if (name.equals(candidate.name)) return candidate;
            }
        }
        return null;
    }

    private LabModels.Command findCommand(LabModels.Item item, String id) {
        if (item == null || id == null || item.commands == null) return null;
        for (LabModels.Command c : item.commands) {
            if (id.equals(c.id)) return c;
        }
        return null;
    }

    private void showRunning(boolean running) {
        mBinding.btnStop.setVisibility(running ? View.VISIBLE : View.GONE);
        boolean interactive = running && command != null && command.isShowOutput() && !command.isBackground();
        mBinding.inputContainer.setVisibility(interactive ? View.VISIBLE : View.GONE);
    }

    private void append(String text) {
        App.post(() -> {
            mBinding.outputText.append(text);
            NestedScrollView scroll = mBinding.outputScroll;
            int range = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
            boolean atBottom = scroll.getScrollY() >= range - 4;
            if (atBottom) scroll.fullScroll(View.FOCUS_DOWN);
        });
    }

    private void onExit(int code) {
        App.post(() -> {
            append("\n[进程结束，退出码 " + code + "]\n");
            showRunning(false);
        });
    }

    private void stop() {
        LabRunner.stop(key());
        showRunning(false);
        append("\n=== 进程已停止 ===\n");
    }

    private void sendInput() {
        String text = mBinding.inputEdit.getText() == null ? "" : mBinding.inputEdit.getText().toString();
        if (text.isEmpty()) return;
        mBinding.inputEdit.setText("");
        append("$ " + text + "\n");
        if (!LabRunner.writeInput(key(), text)) {
            append("[发送失败：进程可能不支持交互输入]\n");
        }
    }

    @Override
    protected void onDestroy() {
        if (sInstance == this) sInstance = null;
        super.onDestroy();
    }
}
