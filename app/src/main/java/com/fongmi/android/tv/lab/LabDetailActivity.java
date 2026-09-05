package com.fongmi.android.tv.lab;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityLabDetailBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabDetailActivity extends AppCompatActivity implements LabCommandAdapter.Listener {

    private static final String EXTRA_ITEM = "item";

    private ActivityLabDetailBinding mBinding;
    private LabCommandAdapter commandAdapter;
    private LabModels.Item item;
    private String itemName;
    private final android.os.Handler mRefreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (item != null) updateButtons();
            mRefreshHandler.postDelayed(this, 2000);
        }
    };

    public static void start(Context context, String itemName) {
        Intent intent = new Intent(context, LabDetailActivity.class);
        intent.putExtra(EXTRA_ITEM, itemName);
        context.startActivity(intent);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(Setting.wrapDisplay(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityLabDetailBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        setSupportActionBar(mBinding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        itemName = getIntent().getStringExtra(EXTRA_ITEM);
        mBinding.toolbar.setTitleTextColor(Color.WHITE);
        mBinding.toolbar.setNavigationOnClickListener(v -> finish());
        mBinding.btnDownload.setOnClickListener(v -> onDownload());
        mBinding.btnUninstall.setOnClickListener(v -> onUninstall());
        mBinding.btnTerminal.setOnClickListener(v -> {
            String name = item == null ? itemName : item.name;
            LabTerminalActivity.start(this, name, name);
        });
        mBinding.btnAddCommand.setOnClickListener(v -> LabCommandEditDialog.show(this, item, null, this::reload));
        mBinding.btnRefreshCommand.setOnClickListener(v -> onRefreshCommands());
        commandAdapter = new LabCommandAdapter(this, this);
        mBinding.commandRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        mBinding.commandRecycler.setAdapter(commandAdapter);
        reload();
    }

    private void reload() {
        LabConfig.get().reload(new LabConfig.LoadCallback() {
            @Override
            public void onLoaded(LabModels.LabRoot root) {
                item = findItem(root);
                if (item == null) {
                    Toast.makeText(LabDetailActivity.this, "找不到模块: " + itemName, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                LabConfig.get().applyCommandOverrides(item);
                bind();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LabDetailActivity.this, "配置加载失败: " + message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private LabModels.Item findItem(LabModels.LabRoot root) {
        if (root == null || root.lists == null) return null;
        for (LabModels.Item i : root.lists) {
            if (itemName != null && itemName.equals(i.name)) return i;
        }
        return null;
    }

    private void bind() {
        mBinding.toolbar.setTitle(item.name);
        mBinding.version.setText(item.name + " " + displayVersion());
        mBinding.info.setText(item.info == null ? "" : item.info);
        if (item.icon != null && !item.icon.isEmpty()) {
            Glide.with(mBinding.icon).load(item.icon).placeholder(R.drawable.ic_logo).error(R.drawable.ic_logo).into(mBinding.icon);
        }
        commandAdapter.setItem(item);
        boolean hasCommands = commandAdapter.getItemCount() > 0;
        mBinding.emptyCommand.setVisibility(hasCommands ? View.GONE : View.VISIBLE);
        mBinding.commandRecycler.setVisibility(hasCommands ? View.VISIBLE : View.GONE);
        invalidateOptionsMenu();
        updateButtons();
    }

    private String displayVersion() {
        if (item.downloads != null) {
            for (LabModels.Download download : item.downloads) {
                if (LabEnv.arch().equals(download.arch) && download.version != null && !download.version.isEmpty()) {
                    return download.version;
                }
            }
        }
        return item.version == null ? "" : item.version;
    }

    private void updateButtons() {
        boolean installed = LabEnv.installed(this, item);
        boolean running = anyRunning();
        boolean update = installed && hasNewVersion();
        mBinding.btnDownload.setText(update ? "更新" : "下载安装");
        mBinding.btnDownload.setVisibility(installed && !update ? View.GONE : View.VISIBLE);
        mBinding.btnUninstall.setVisibility(installed ? View.VISIBLE : View.GONE);
        if (running) {
            mBinding.status.setText(R.string.lab_running);
            mBinding.status.setBackgroundResource(R.drawable.shape_lab_running_tag);
        } else if (installed) {
            mBinding.status.setText(R.string.lab_installed);
            mBinding.status.setBackgroundResource(R.drawable.shape_lab_installed);
        } else {
            mBinding.status.setText(R.string.lab_not_installed);
            mBinding.status.setBackgroundResource(R.drawable.shape_lab_not_installed);
        }
        commandAdapter.notifyDataSetChanged();
    }

    private boolean hasNewVersion() {
        String installed = LabConfig.get().getInstalledVersion(item.name);
        return !installed.isEmpty() && LabEnv.compareVersions(displayVersion(), installed) > 0;
    }

    private boolean anyRunning() {
        if (item.commands != null) {
            for (LabModels.Command command : item.commands) {
                if (LabRunner.isRunning(item.name + "/" + command.id)) return true;
            }
        }
        for (LabCustomCommands.CustomCommand custom : LabCustomCommands.list(item.name)) {
            if (custom.id != null && LabRunner.isRunning(item.name + "/" + custom.id)) return true;
        }
        return false;
    }

    private void onDownload() {
        if (!item.available) {
            Toast.makeText(this, "该包暂未上线", Toast.LENGTH_SHORT).show();
            return;
        }
        if (LabEnv.installed(this, item) && hasNewVersion()) {
            new MaterialAlertDialogBuilder(this, R.style.Theme_App_Lab_Dialog)
                    .setTitle("确认更新")
                    .setMessage("发现新版本 " + displayVersion() + "，是否立即更新？")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("更新", (d, w) -> LabActions.installWithDialog(this, item, this::updateButtons))
                    .show();
            return;
        }
        LabActions.installWithDialog(this, item, this::updateButtons);
    }

    private void onUninstall() {
        new MaterialAlertDialogBuilder(this, R.style.Theme_App_Lab_Dialog)
                .setTitle("确认卸载")
                .setMessage("确定要卸载 " + item.name + " 吗？这将停止所有运行中的命令。")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("卸载", (d, w) -> {
                    LabActions.uninstall(this, item, this::updateButtons);
                })
                .show();
    }

    private void onRefreshCommands() {
        new MaterialAlertDialogBuilder(this, R.style.Theme_App_Lab_Dialog)
                .setTitle("重置命令列表")
                .setMessage("确定要从远程重新加载命令列表吗？这将覆盖本地修改的命令，并停止所有运行中的进程。")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("确定", (d, w) -> {
                    LabActions.stopItem(item);
                    LabConfig.get().clearCommandCache(item.name);
                    LabConfig.get().clearCommandOverrides(item.name);
                    reload();
                    Notify.show(R.string.lab_refresh_success);
                })
                .show();
    }

    @Override
    public void onOpen(LabModels.Item item, LabModels.Command command) {
        LabCustomCommands.CustomCommand custom = customCommand(command);
        if (custom != null) {
            LabActions.runCustom(this, item, custom, this::updateButtons);
            return;
        }
        if (!command.isSupported(LabEnv.appVersionCode(this))) {
            Toast.makeText(this, "需要更新影视+至 v" + command.min_version + " 以上", Toast.LENGTH_SHORT).show();
            return;
        }
        new LabCommandSheet(this, item, command, this::updateButtons).show();
    }

    @Override
    public void onAction(LabModels.Item item, LabModels.Command command) {
        String key = item.name + "/" + command.id;
        if (LabRunner.isRunning(key)) {
            LabRunner.stop(key);
            Notify.show(R.string.lab_command_stopped);
        } else {
            LabCommandSheet sheet = new LabCommandSheet(this, item, command, this::updateButtons);
            sheet.show();
            sheet.run();
        }
        updateButtons();
    }

    @Override
    public void onLongPress(LabModels.Item item, LabModels.Command command) {
        LabCommandEditDialog.show(this, item, command, this::reload);
    }

    private LabCustomCommands.CustomCommand customCommand(LabModels.Command command) {
        if (command == null || command.id == null || !command.id.startsWith("custom_")) return null;
        for (LabCustomCommands.CustomCommand custom : LabCustomCommands.list(item.name)) {
            if (command.id.equals(custom.id)) return custom;
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (item != null && item.hasSettings()) {
            getMenuInflater().inflate(R.menu.menu_lab_detail, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (menuItem.getItemId() == R.id.settings && item != null && item.hasSettings()) {
            showPackageSettings();
            return true;
        }
        if (menuItem.getItemId() == R.id.export) {
            exportPackage();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void exportPackage() {
        if (!LabEnv.installed(this, item)) {
            Toast.makeText(this, "该包尚未安装", Toast.LENGTH_SHORT).show();
            return;
        }
        TextView progress = new TextView(this);
        progress.setTextColor(Color.WHITE);
        progress.setTextSize(14);
        progress.setPadding(48, 32, 48, 32);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.Theme_App_Lab_Dialog)
                .setTitle("打包导出")
                .setView(progress)
                .setCancelable(false)
                .setPositiveButton("关闭", null)
                .create();
        dialog.show();
        LabActions.hideCloseButton(dialog);
        new Thread(() -> {
            try {
                File file = LabEnv.exportPackage(this, item, LabCustomCommands.list(item.name));
                App.post(() -> {
                    dialog.dismiss();
                    Notify.show("已导出到: " + file.getAbsolutePath());
                });
            } catch (Exception e) {
                App.post(() -> {
                    progress.setText("导出失败：\n" + e.getMessage());
                    LabActions.showCloseButton(dialog);
                });
            }
        }).start();
    }

    private void showPackageSettings() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 24, 48, 8);
        Map<String, String> values = LabConfig.get().loadUserSettings(item.name);
        for (LabModels.Setting setting : item.settings) {
            View row = inflateSetting(container, setting, values);
            if (row != null) container.addView(row);
        }
        new MaterialAlertDialogBuilder(this, R.style.Theme_WebHTV_LightDialog)
                .setTitle(item.name + " 设置")
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("保存", (d, w) -> {
                    LabConfig.get().saveUserSettings(item.name, values);
                    Notify.show("设置已保存");
                })
                .show();
    }

    private View inflateSetting(LinearLayout parent, LabModels.Setting setting, Map<String, String> values) {
        String type = setting.type == null ? "text" : setting.type;
        int layout;
        switch (type) {
            case "number":
                layout = R.layout.item_lab_var_number_dialog;
                break;
            case "select":
                layout = R.layout.item_lab_var_select_dialog;
                break;
            case "switch":
                layout = R.layout.item_lab_var_switch_dialog;
                break;
            case "directory":
                layout = R.layout.item_lab_var_dir_dialog;
                break;
            default:
                layout = R.layout.item_lab_var_text_dialog;
                break;
        }
        View row = getLayoutInflater().inflate(layout, parent, false);
        if ("switch".equals(type)) {
            TextView name = row.findViewById(R.id.name);
            TextView hint = row.findViewById(R.id.hint);
            MaterialSwitch sw = row.findViewById(R.id.switchView);
            name.setText(setting.name);
            if (setting.hint != null) {
                hint.setText(setting.hint);
                hint.setVisibility(View.VISIBLE);
            }
            String value = values.containsKey(setting.key) ? values.get(setting.key) : setting.defaultValue;
            sw.setChecked("true".equals(value) || "1".equals(value));
            values.put(setting.key, sw.isChecked() ? "true" : "false");
            sw.setOnCheckedChangeListener((button, checked) -> values.put(setting.key, checked ? "true" : "false"));
            return row;
        }
        TextInputLayout layoutContainer = row.findViewById(R.id.inputLayout);
        EditText edit = row.findViewById(R.id.input);
        if (edit == null) edit = row.findViewById(R.id.dropdown);
        if (edit == null) return row;
        layoutContainer.setHint(setting.name);
        if (setting.hint != null) layoutContainer.setPlaceholderText(setting.hint);
        String value = values.containsKey(setting.key) ? values.get(setting.key) : setting.defaultValue;
        if (value == null) value = "";
        edit.setText(value);
        values.put(setting.key, value);
        if ("select".equals(type) && edit instanceof MaterialAutoCompleteTextView) {
            MaterialAutoCompleteTextView dropdown = (MaterialAutoCompleteTextView) edit;
            List<LabModels.Option> options = setting.options == null ? new java.util.ArrayList<LabModels.Option>() : setting.options;
            String[] labels = new String[options.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = options.get(i).label;
            dropdown.setSimpleItems(labels);
            for (LabModels.Option option : options) {
                if (value.equals(option.value)) {
                    dropdown.setText(option.label, false);
                    break;
                }
            }
            dropdown.setOnItemClickListener((p, view, position, id) -> values.put(setting.key, options.get(position).value));
        } else {
            edit.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    values.put(setting.key, s == null ? "" : s.toString());
                }
            });
        }
        return row;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (item != null) updateButtons();
        mRefreshHandler.postDelayed(mRefreshRunnable, 2000);
    }

    @Override
    protected void onPause() {
        mRefreshHandler.removeCallbacks(mRefreshRunnable);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LabFilePicker.onActivityResult(requestCode, resultCode, data);
    }
}
