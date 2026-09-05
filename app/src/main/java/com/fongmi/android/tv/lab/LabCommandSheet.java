package com.fongmi.android.tv.lab;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LabCommandSheet implements LabRunner.OutputListener {

    public interface Callback {
        void onChanged();
    }

    private final Activity activity;
    private final LabModels.Item item;
    private final LabModels.Command command;
    private final Map<String, String> vars = new HashMap<>();
    private final Callback callback;
    private BottomSheetDialog dialog;
    private TextView sheetTitle;
    private TextView sheetRunningTag;
    private TextView sheetDesc;
    private TextView sheetBtnEdit;
    private TextView sheetBtnStop;
    private TextView sheetBtnRun;
    private TextView sheetBtnOutput;
    private EditText sheetCommandPreview;
    private EditText sheetDownloadUrl;
    private LinearLayout sheetVariables;
    private LinearLayout sheetClicksContainer;
    private View sheetDownloadContainer;
    private boolean running;
    private boolean loaded;
    private final android.os.Handler mStateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mStateRunnable = new Runnable() {
        @Override
        public void run() {
            if (dialog == null || !dialog.isShowing()) return;
            boolean actual = LabRunner.isRunning(key());
            if (actual != running) {
                running = actual;
                syncRunning();
                if (callback != null) callback.onChanged();
            }
            mStateHandler.postDelayed(this, 1000);
        }
    };

    public LabCommandSheet(Activity activity, LabModels.Item item, LabModels.Command command, Callback callback) {
        this.activity = activity;
        this.item = item;
        this.command = command;
        this.callback = callback;
        if (command.cachedVariableValues != null) vars.putAll(command.cachedVariableValues);
    }

    public void show() {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_lab_command_sheet, null, false);
        root.setBackgroundResource(R.drawable.shape_lab_sheet_bg);
        sheetTitle = root.findViewById(R.id.sheetTitle);
        sheetRunningTag = root.findViewById(R.id.sheetRunningTag);
        sheetDesc = root.findViewById(R.id.sheetDesc);
        sheetBtnEdit = root.findViewById(R.id.sheetBtnEdit);
        sheetBtnStop = root.findViewById(R.id.sheetBtnStop);
        sheetBtnRun = root.findViewById(R.id.sheetBtnRun);
        sheetBtnOutput = root.findViewById(R.id.sheetBtnOutput);
        sheetCommandPreview = root.findViewById(R.id.sheetCommandPreview);
        sheetDownloadUrl = root.findViewById(R.id.sheetDownloadUrl);
        sheetVariables = root.findViewById(R.id.sheetVariables);
        sheetClicksContainer = root.findViewById(R.id.sheetClicksContainer);
        sheetDownloadContainer = root.findViewById(R.id.sheetDownloadContainer);

        sheetTitle.setText(command.name == null ? command.description : command.name);
        if (!TextUtils.isEmpty(command.description)) {
            sheetDesc.setText(command.description);
            sheetDesc.setVisibility(View.VISIBLE);
        }
        renderVariables();
        if (command.download != null) {
            sheetDownloadContainer.setVisibility(View.VISIBLE);
            sheetDownloadUrl.setText(command.download.url == null ? "" : command.download.url);
            TextView label = root.findViewById(R.id.sheetDownloadLabel);
            label.setText(command.download.title == null ? "下载地址" : command.download.title);
        }
        updatePreview();
        renderClicks();

        sheetBtnEdit.setOnClickListener(v -> LabCommandEditDialog.show(activity, item, command, () -> {
            if (callback != null) callback.onChanged();
            dialog.dismiss();
            new LabCommandSheet(activity, item, command, callback).show();
        }));
        sheetBtnRun.setOnClickListener(v -> run());
        sheetBtnStop.setOnClickListener(v -> stop());
        sheetBtnOutput.setOnClickListener(v -> LabOutputActivity.start(activity, item.name, command.id, new HashMap<>(vars)));
        sheetDownloadUrl.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (command.download != null) command.download.url = s.toString();
            }
        });

        dialog = new BottomSheetDialog(activity, R.style.Theme_App_Lab_Dialog);
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setOnDismissListener(d -> {
            mStateHandler.removeCallbacks(mStateRunnable);
            if (running) return;
            saveCached();
            if (callback != null) callback.onChanged();
        });
        dialog.show();
        mStateHandler.post(mStateRunnable);
        loaded = true;
        running = LabRunner.isRunning(key());
        syncRunning();
    }

    private void renderVariables() {
        sheetVariables.removeAllViews();
        if (!command.hasVariables()) return;
        if (vars.isEmpty() && command.cachedVariableValues != null) vars.putAll(command.cachedVariableValues);
        for (LabModels.Variable variable : command.getVariables()) {
            View row = inflateVariable(activity, variable);
            if (row != null) sheetVariables.addView(row);
        }
    }

    private View inflateVariable(Activity activity, LabModels.Variable variable) {
        int layout;
        switch (variable.type == null ? "text" : variable.type) {
            case "number":
                layout = R.layout.item_lab_var_number;
                break;
            case "select":
                layout = R.layout.item_lab_var_select;
                break;
            case "switch":
                layout = R.layout.item_lab_var_switch;
                break;
            case "file":
                layout = R.layout.item_lab_var_file;
                break;
            case "directory":
                layout = R.layout.item_lab_var_dir;
                break;
            case "text_multiline":
                layout = R.layout.item_lab_var_multiline;
                break;
            default:
                layout = R.layout.item_lab_var_text;
                break;
        }
        View row = LayoutInflater.from(activity).inflate(layout, null, false);
        if ("switch".equals(variable.type)) {
            TextView name = row.findViewById(R.id.name);
            TextView hint = row.findViewById(R.id.hint);
            MaterialSwitch sw = row.findViewById(R.id.switchView);
            name.setText(variable.name + (variable.required ? " *" : ""));
            if (TextUtils.isEmpty(variable.hint)) {
                hint.setVisibility(View.GONE);
            } else {
                hint.setText(variable.hint);
                hint.setVisibility(View.VISIBLE);
            }
            String value = vars.containsKey(variable.key) ? vars.get(variable.key) : variable.defaultValue;
            sw.setChecked("true".equals(value) || "1".equals(value));
            vars.put(variable.key, sw.isChecked() ? "true" : "false");
            sw.setOnCheckedChangeListener((button, checked) -> {
                vars.put(variable.key, checked ? "true" : "false");
                updatePreview();
            });
            return row;
        }
        TextInputLayout layoutContainer = row.findViewById(R.id.inputLayout);
        EditText edit = row.findViewById(R.id.input);
        if (edit == null) edit = row.findViewById(R.id.dropdown);
        if (edit == null) return row;
        final EditText finalEdit = edit;
        String name = variable.name + (variable.required ? " *" : "");
        if (variable.min != null && variable.max != null) name = name + " (" + variable.min + "-" + variable.max + ")";
        layoutContainer.setHint(name);
        if (!TextUtils.isEmpty(variable.hint)) {
            layoutContainer.setPlaceholderText(expand(variable.hint));
        }
        String value = vars.containsKey(variable.key) ? vars.get(variable.key) : variable.defaultValue;
        if (value == null) value = "";
        finalEdit.setText(expand(value));
        vars.put(variable.key, value);
        if ("select".equals(variable.type) && edit instanceof MaterialAutoCompleteTextView) {
            MaterialAutoCompleteTextView dropdown = (MaterialAutoCompleteTextView) edit;
            String[] labels = new String[variable.options == null ? 0 : variable.options.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = variable.options.get(i).label;
            dropdown.setSimpleItems(labels);
            for (LabModels.Option option : variable.options == null ? new java.util.ArrayList<LabModels.Option>() : variable.options) {
                if (value.equals(option.value)) {
                    dropdown.setText(option.label, false);
                    break;
                }
            }
            dropdown.setOnItemClickListener((parent, view, position, id) -> {
                LabModels.Option option = variable.options.get(position);
                vars.put(variable.key, option.value);
                updatePreview();
            });
        } else {
            finalEdit.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    vars.put(variable.key, s == null ? "" : s.toString());
                    updatePreview();
                }
            });
        }
        if ("file".equals(variable.type) || "directory".equals(variable.type)) {
            layoutContainer.setEndIconOnClickListener(v -> LabFilePicker.pick(activity, "directory".equals(variable.type), variable.filter, path -> {
                if (path == null || path.isEmpty()) return;
                finalEdit.setText(path);
                vars.put(variable.key, path);
                updatePreview();
            }));
        }
        return row;
    }

    private void renderClicks() {
        sheetClicksContainer.removeAllViews();
        List<LabModels.Click> clicks = command.getClicks();
        if (clicks.isEmpty()) return;
        View parent = (View) sheetClicksContainer.getParent();
        if (parent != null) parent.setVisibility(View.VISIBLE);
        float density = activity.getResources().getDisplayMetrics().density;
        for (LabModels.Click click : clicks) {
            TextView button = new TextView(activity);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (36 * density));
            params.setMarginEnd((int) (10 * density));
            button.setLayoutParams(params);
            button.setGravity(android.view.Gravity.CENTER);
            button.setPadding((int) (20 * density), 0, (int) (20 * density), 0);
            button.setText(click.title == null ? click.action : click.title);
            button.setTextColor(Color.WHITE);
            button.setTextSize(13);
            button.setBackgroundResource(R.drawable.shape_lab_click_btn);
            button.setOnClickListener(v -> LabActions.performClick(activity, command, click, vars));
            sheetClicksContainer.addView(button);
        }
    }

    private void updatePreview() {
        if (sheetCommandPreview == null) return;
        sheetCommandPreview.setText("$ " + LabRunner.expand(activity, item, command.command, vars));
    }

    private String expand(String value) {
        return LabRunner.expand(activity, item, value, vars);
    }

    private String key() {
        return item.name + "/" + command.id;
    }

    public void run() {
        if (LabRunner.isRunning(key())) {
            syncRunning();
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
        for (LabModels.Variable variable : command.getVariables()) {
            if (variable.required) {
                String value = vars.get(variable.key);
                if (TextUtils.isEmpty(value) && TextUtils.isEmpty(variable.defaultValue)) {
                    Toast.makeText(activity, "请填写必填项: " + variable.name, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        List<String> special = Arrays.asList("serverPort", "tvPath", "dataPath", "cachePath", "envRootPath", "wwwroot", "sdcard");
        List<LabModels.Variable> missing = new java.util.ArrayList<>();
        Matcher matcher = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}").matcher(command.command);
        while (matcher.find()) {
            if (matcher.start() > 0 && command.command.charAt(matcher.start() - 1) == '$') continue;
            String key = matcher.group(1);
            if (special.contains(key) || vars.containsKey(key) || containsVariable(key)) continue;
            LabModels.Variable variable = new LabModels.Variable();
            variable.key = key;
            variable.name = key;
            variable.type = "text";
            variable.required = true;
            missing.add(variable);
        }
        if (!missing.isEmpty()) {
            LabVariableDialog.show(activity, "命令参数", missing, filled -> {
                vars.putAll(filled);
                run();
            });
            return;
        }
        if (command.download != null && LabEnv.dependencyNeeded(command.download)) {
            downloadDependency(() -> startRun());
        } else {
            startRun();
        }
    }

    private boolean containsVariable(String key) {
        for (LabModels.Variable variable : command.getVariables()) {
            if (key.equals(variable.key)) return true;
        }
        return false;
    }

    private void downloadDependency(Runnable next) {
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
        LabActions.hideCloseButton(dialog);
        new Thread(() -> {
            try {
                LabEnv.prepareDependency(activity, command.download, progress::setText);
                App.post(() -> {
                    dialog.dismiss();
                    next.run();
                });
            } catch (Exception e) {
                App.post(() -> {
                    progress.setText("依赖下载失败：\n" + e.getMessage());
                    LabActions.showCloseButton(dialog);
                });
            }
        }).start();
    }

    private void startRun() {
        String cmd = LabRunner.expand(activity, item, command.command, vars);
        if (TextUtils.isEmpty(cmd)) return;
        saveCached();
        LabOutputActivity.start(activity, item.name, command.id, new HashMap<>(vars));
        LabRunner.run(activity, item, command, vars, this);
        running = true;
        syncRunning();
        if (!command.isShowOutput()) {
            Toast.makeText(activity, "已在后台运行: " + command.name, Toast.LENGTH_SHORT).show();
        }
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        if (callback != null) callback.onChanged();
    }

    private void stop() {
        LabRunner.stop(key());
        running = false;
        syncRunning();
        if (callback != null) callback.onChanged();
    }

    private void syncRunning() {
        if (!loaded) return;
        sheetRunningTag.setVisibility(running ? View.VISIBLE : View.GONE);
        sheetBtnRun.setVisibility(running ? View.GONE : View.VISIBLE);
        sheetBtnStop.setVisibility(running ? View.VISIBLE : View.GONE);
        sheetBtnOutput.setVisibility(running ? View.VISIBLE : View.GONE);
        boolean installed = LabEnv.installed(activity, item);
        boolean supported = command.isSupported(LabEnv.appVersionCode(activity));
        sheetBtnRun.setEnabled(installed && supported);
        sheetBtnRun.setAlpha(installed && supported ? 1.0f : 0.5f);
    }

    private void saveCached() {
        command.cachedVariableValues = new HashMap<>(vars);
        LabConfig.get().saveCommandCache(item.name, command.id, vars, command.command);
    }

    @Override
    public void onOutput(String text) {
        LabOutputActivity.appendGlobal(text);
    }

    @Override
    public void onExit(int code) {
        running = false;
        LabOutputActivity.onExitGlobal(code);
        App.post(() -> {
            syncRunning();
            if (callback != null) callback.onChanged();
        });
    }
}
