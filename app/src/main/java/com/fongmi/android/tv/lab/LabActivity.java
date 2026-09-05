package com.fongmi.android.tv.lab;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityLabBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LabActivity extends AppCompatActivity implements LabPackageAdapter.Listener, LabGroupAdapter.Listener {

    private static final int REQUEST_IMPORT = 1001;
    private static final int REQUEST_LOCAL_CONFIG = 1002;
    private static final String GROUP_ALL = "all";
    private static final int[] SOURCE_MODES = {LabConfig.SOURCE_LOCAL, LabConfig.SOURCE_URL};
    public static final String EXTRA_SHOW_RUNNING = "show_running";

    private ActivityLabBinding mBinding;
    private LabGroupAdapter groupAdapter;
    private LabPackageAdapter packageAdapter;
    private String currentGroup = GROUP_ALL;
    private final Set<String> autoInstallAttempted = new HashSet<>();
    private AlertDialog settingsDialog;

    public static void start(Context context) {
        context.startActivity(new Intent(context, LabActivity.class));
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(Setting.wrapDisplay(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityLabBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        mBinding.toolbar.setTitleTextColor(Color.WHITE);
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.help) {
                showAbout();
                return true;
            }
            if (id == R.id.import_pkg) {
                showImportDialog();
                return true;
            }
            if (id == R.id.setting) {
                showSettings();
                return true;
            }
            return false;
        });
        mBinding.groupRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        groupAdapter = new LabGroupAdapter(this);
        mBinding.groupRecycler.setAdapter(groupAdapter);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        packageAdapter = new LabPackageAdapter(this, this);
        mBinding.recycler.setAdapter(packageAdapter);
        mBinding.swipeRefresh.setColorSchemeColors(getColor(R.color.accent));
        mBinding.swipeRefresh.setOnRefreshListener(this::reload);
        mBinding.empty.getRoot().setVisibility(View.GONE);
        mBinding.progress.setVisibility(View.VISIBLE);
        reload();
        handleShowRunning(getIntent());
        App.post(this::requestNotifyPermission, 300);
    }

    private void requestNotifyPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2001);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (LabConfig.get().getForeground()) LabProcManager.updateService();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShowRunning(intent);
    }

    private void handleShowRunning(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_SHOW_RUNNING, false)) {
            intent.removeExtra(EXTRA_SHOW_RUNNING);
            App.post(() -> LabRunningDialog.show(this), 400);
        }
    }

    private void reload() {
        mBinding.progress.setVisibility(View.VISIBLE);
        LabEnv.syncVodPlusAssets(this);
        LabConfig.get().reload(new LabConfig.LoadCallback() {
            @Override
            public void onLoaded(LabModels.LabRoot root) {
                mBinding.progress.setVisibility(View.GONE);
                mBinding.swipeRefresh.setRefreshing(false);
                render(root);
                autoInstall(root);
            }

            @Override
            public void onError(String message) {
                mBinding.progress.setVisibility(View.GONE);
                mBinding.swipeRefresh.setRefreshing(false);
                Notify.show("实验室: " + message);
                render(null);
            }
        });
    }

    private void render(LabModels.LabRoot root) {
        List<LabGroupAdapter.Row> groups = new ArrayList<>();
        groups.add(new LabGroupAdapter.Row(GROUP_ALL, "全部"));
        List<LabModels.Item> all = new ArrayList<>();
        if (root != null && root.lists != null) {
            if (root.groups != null) {
                for (LabModels.Group group : root.groups) groups.add(new LabGroupAdapter.Row(group.id, group.name));
            }
            all.addAll(root.lists);
        }
        groupAdapter.setRows(groups);
        if (currentGroup == null || !containsGroup(groups, currentGroup)) currentGroup = GROUP_ALL;
        groupAdapter.select(indexOfGroup(groups, currentGroup));
        List<LabModels.Item> packages = new ArrayList<>();
        for (LabModels.Item item : all) {
            if (!item.show) continue;
            if (GROUP_ALL.equals(currentGroup) || currentGroup.equals(item.group == null ? "" : item.group)) {
                packages.add(item);
            }
        }
        packageAdapter.setRows(packages);
        mBinding.empty.getRoot().setVisibility(packages.isEmpty() ? View.VISIBLE : View.GONE);
        mBinding.recycler.setVisibility(packages.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private boolean containsGroup(List<LabGroupAdapter.Row> groups, String id) {
        for (LabGroupAdapter.Row row : groups) {
            if (row.id.equals(id)) return true;
        }
        return false;
    }

    private int indexOfGroup(List<LabGroupAdapter.Row> groups, String id) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).id.equals(id)) return i;
        }
        return 0;
    }

    private void autoInstall(LabModels.LabRoot root) {
        if (root == null || root.lists == null) return;
        for (LabModels.Item item : root.lists) {
            if (!item.auto_install || !item.available) continue;
            if (autoInstallAttempted.contains(item.name)) continue;
            autoInstallAttempted.add(item.name);
            if (LabEnv.installed(this, item)) continue;
            LabActions.install(this, item, () -> packageAdapter.notifyDataSetChanged());
        }
    }

    @Override
    public void onGroup(String id) {
        currentGroup = id;
        LabModels.LabRoot root = LabConfig.get().getLabRoot();
        render(root);
    }

    @Override
    public void onOpen(LabModels.Item item) {
        LabDetailActivity.start(this, item.name);
    }

    @Override
    public void onLongPress(LabModels.Item item) {
        LabDetailActivity.start(this, item.name);
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this, R.style.Theme_App_Lab_DayNight_Dialog)
                .setTitle(R.string.lab_about_title)
                .setMessage(R.string.lab_about_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void pickImport() {
        PermissionUtil.requestFile(this, granted -> {
            if (!granted) {
                Notify.show(R.string.setting_custom_csp_permission_required);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_IMPORT);
        });
    }

    private void showImportDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);
        EditText edit = new EditText(new ContextThemeWrapper(this, R.style.Theme_App_Lab_DayNight_Dialog));
        edit.setHint(R.string.lab_import_placeholder);
        edit.setMinLines(4);
        edit.setMaxLines(8);
        edit.setTypeface(android.graphics.Typeface.MONOSPACE);
        edit.setTextColor(getColor(R.color.lab_text_primary));
        edit.setHintTextColor(getColor(R.color.lab_text_secondary));
        container.addView(edit);
        TextView select = new TextView(this);
        select.setText(R.string.lab_import_select_file);
        select.setTextColor(getColor(R.color.accent));
        select.setPadding(0, pad, 0, 0);
        select.setClickable(true);
        container.addView(select);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.Theme_App_Lab_DayNight_Dialog)
                .setTitle(R.string.lab_import_title)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.lab_import, null)
                .create();
        select.setOnClickListener(v -> {
            dialog.dismiss();
            pickImport();
        });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = edit.getText() == null ? "" : edit.getText().toString().trim();
            String json = decodeImport(text);
            if (json == null || json.isEmpty()) {
                Toast.makeText(this, "无效的导入数据", Toast.LENGTH_SHORT).show();
                return;
            }
            LabConfig.get().saveImported(json);
            dialog.dismiss();
            Notify.show(R.string.lab_import_success);
            reload();
        }));
        dialog.show();
    }

    private String decodeImport(String text) {
        if (text == null || text.isEmpty()) return null;
        if (text.startsWith("VodPlusLabConfig://")) {
            try {
                return new String(Base64.decode(text.substring("VodPlusLabConfig://".length()), Base64.DEFAULT), "UTF-8");
            } catch (Exception e) {
                return null;
            }
        }
        return text;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importArchive(data.getData());
        } else if (requestCode == REQUEST_LOCAL_CONFIG && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                LabConfig.get().setLocalPath(uri.toString());
            } catch (Exception e) {
                try {
                    File local = new File(getFilesDir(), "lab_local_config.json");
                    try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(local)) {
                        byte[] buf = new byte[16384];
                        int len;
                        while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                    }
                    LabConfig.get().setLocalPath(local.getAbsolutePath());
                } catch (Exception ignored) {
                    Notify.show("无法读取所选文件");
                    return;
                }
            }
            LabConfig.get().setSource(LabConfig.SOURCE_LOCAL);
            if (settingsDialog != null && settingsDialog.isShowing()) settingsDialog.dismiss();
            Notify.show("已选择本地配置");
            reload();
        }
    }

    private void importArchive(Uri uri) {
        Toast.makeText(this, "导入中…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File cache = new File(getCacheDir(), "import_" + System.currentTimeMillis() + ".lab.7z");
                try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(cache)) {
                    byte[] buf = new byte[16384];
                    int len;
                    while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                }
                boolean ok = LabEnv.importPackage(this, cache);
                cache.delete();
                App.post(() -> {
                    Notify.show(ok ? R.string.lab_import_success : R.string.lab_import_error_short);
                    reload();
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导入失败: " + e.getMessage()));
            }
        }).start();
    }

    private void showSettings() {
        View root = getLayoutInflater().inflate(R.layout.dialog_lab_settings, null);
        AutoCompleteTextView dropdown = root.findViewById(R.id.sourceDropdown);
        EditText input = root.findViewById(R.id.input);
        EditText rootInput = root.findViewById(R.id.rootInput);
        View folder = root.findViewById(R.id.folder);
        MaterialSwitch foreground = root.findViewById(R.id.foregroundSwitch);
        MaterialSwitch battery = root.findViewById(R.id.batterySwitch);
        MaterialSwitch proxy = root.findViewById(R.id.proxySwitch);
        EditText proxyPort = root.findViewById(R.id.proxyPort);
        EditText proxyNoProxy = root.findViewById(R.id.proxyNoProxy);
        View navEntryRow = root.findViewById(R.id.navEntryRow);
        MaterialSwitch navEntry = root.findViewById(R.id.navEntrySwitch);
        navEntryRow.setVisibility(Util.isMobile() ? View.VISIBLE : View.GONE);
        String[] items = {getString(R.string.lab_source_local), getString(R.string.lab_source_url)};
        dropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, items));
        int source = LabConfig.get().getSource();
        dropdown.setText(items[indexOfSource(source)], false);
        applySourceFields(input, folder, source);
        dropdown.setOnItemClickListener((parent, view, position, id) -> applySourceFields(input, folder, SOURCE_MODES[position]));
        folder.setOnClickListener(v -> openLocalPicker());
        rootInput.setText(LabConfig.get().getRootOverride());
        foreground.setChecked(LabConfig.get().getForeground());
        battery.setChecked(LabConfig.get().getBattery());
        navEntry.setChecked(LabConfig.get().getNavEntry());
        proxy.setChecked(LabConfig.get().getGlobalProxy());
        proxyPort.setText(String.valueOf(LabConfig.get().getGlobalProxyPort()));
        proxyNoProxy.setText(LabConfig.get().getGlobalProxyNoProxy());
        settingsDialog = new MaterialAlertDialogBuilder(this, R.style.Theme_App_Lab_DayNight_Dialog)
                .setTitle(R.string.lab_source_title)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        settingsDialog.setOnShowListener(d -> settingsDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    int selected = dropdown.getText() == null ? LabConfig.SOURCE_LOCAL : SOURCE_MODES[Math.max(0, indexOf(items, dropdown.getText().toString()))];
                    LabConfig.get().setSource(selected);
                    if (selected == LabConfig.SOURCE_LOCAL && TextUtils.isEmpty(LabConfig.get().getLocalPath())) {
                        Toast.makeText(this, "未选择文件，将自动搜索手机常见目录", Toast.LENGTH_SHORT).show();
                    }
                    if (selected == LabConfig.SOURCE_URL) {
                        String url = input.getText() == null ? "" : input.getText().toString().trim();
                        if (!url.isEmpty()) LabConfig.get().setUrl(url);
                    }
                    String rootText = rootInput.getText() == null ? "" : rootInput.getText().toString().trim();
                    LabConfig.get().setRoot(rootText);
                    LabConfig.get().setForeground(foreground.isChecked());
                    LabConfig.get().setBattery(battery.isChecked());
                    LabConfig.get().setNavEntry(navEntry.isChecked());
                    LabConfig.get().setGlobalProxy(proxy.isChecked());
                    int port = 7890;
                    if (proxyPort.getText() != null) {
                        try {
                            port = Integer.parseInt(proxyPort.getText().toString().trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    LabConfig.get().setGlobalProxyPort(port > 0 ? port : 7890);
                    LabConfig.get().setGlobalProxyNoProxy(proxyNoProxy.getText() == null ? "" : proxyNoProxy.getText().toString().trim());
                    if (foreground.isChecked()) requestNotifyPermission();
                    if (battery.isChecked()) {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) {
                        }
                    }
                    settingsDialog.dismiss();
                    LabProcManager.updateService();
                    reload();
                }));
        settingsDialog.show();
    }

    private void openLocalPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_LOCAL_CONFIG);
        } catch (Exception e) {
            Notify.show("无法打开文件选择器: " + e.getMessage());
        }
    }

    private int indexOf(String[] items, String value) {
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(value)) return i;
        }
        return -1;
    }

    private int indexOfSource(int source) {
        for (int i = 0; i < SOURCE_MODES.length; i++) {
            if (SOURCE_MODES[i] == source) return i;
        }
        return 0;
    }

    /** 本地配置用文件选择器填路径（只读输入框），网络 URL 用手输。 */
    private void applySourceFields(EditText input, View folder, int source) {
        boolean url = source == LabConfig.SOURCE_URL;
        input.setVisibility(url ? View.VISIBLE : View.GONE);
        folder.setVisibility(url ? View.GONE : View.VISIBLE);
        input.setText(url ? LabConfig.get().getUrl() : LabConfig.get().getLocalPath());
        input.setFocusable(url);
        input.setFocusableInTouchMode(url);
    }
}
