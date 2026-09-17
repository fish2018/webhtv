package com.fongmi.android.tv.lab;

import android.app.Activity;
import android.os.Environment;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class LabFileBrowserDialog {

    private final Activity activity;
    private final boolean directory;
    private final Consumer<String> callback;
    private File current;
    private List<File> files = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private AlertDialog dialog;

    public LabFileBrowserDialog(Activity activity, boolean directory, Consumer<String> callback) {
        this.activity = activity;
        this.directory = directory;
        this.callback = callback;
        this.current = Environment.getExternalStorageDirectory();
    }

    public void show() {
        ListView list = new ListView(activity);
        adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                .setTitle(directory ? "选择目录" : "选择文件")
                .setView(list)
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (directory && position == 0) {
                callback.accept(current.getAbsolutePath());
                dialog.dismiss();
                return;
            }
            boolean hasParentRow = current.getParentFile() != null;
            int offset = (directory ? 1 : 0) + (hasParentRow ? 1 : 0);
            int index = position - offset;
            if (index < 0) {
                goUp();
                return;
            }
            if (index >= files.size()) return;
            File file = files.get(index);
            if (file.isDirectory()) {
                current = file;
                refresh();
            } else if (!directory) {
                callback.accept(file.getAbsolutePath());
                dialog.dismiss();
            }
        });
        refresh();
    }

    private void refresh() {
        files = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        if (directory) rows.add("【选择此目录】" + current.getAbsolutePath());
        if (current.getParentFile() != null) rows.add("..");
        File[] children = current.listFiles();
        if (children != null) {
            List<File> dirs = new ArrayList<>();
            List<File> fileList = new ArrayList<>();
            for (File file : children) {
                if (file.isDirectory()) dirs.add(file);
                else fileList.add(file);
            }
            dirs.sort(Comparator.comparing(File::getName));
            fileList.sort(Comparator.comparing(File::getName));
            files.addAll(dirs);
            files.addAll(fileList);
            for (File file : files) {
                rows.add((file.isDirectory() ? "📁 " : "📄 ") + file.getName());
            }
        } else {
            rows.add("(无法访问，请授予「所有文件访问」权限)");
        }
        adapter.clear();
        adapter.addAll(rows);
        dialog.setTitle(current.getAbsolutePath());
    }

    private void goUp() {
        File parent = current.getParentFile();
        if (parent != null) {
            current = parent;
            refresh();
        }
    }
}
