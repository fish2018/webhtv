package com.fongmi.android.tv.lab;

import android.app.Activity;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public final class LabRunningDialog {

    private LabRunningDialog() {
    }

    public static void show(Activity activity) {
        ListView list = new ListView(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                .setTitle("运行中的命令")
                .setView(list)
                .setNegativeButton("关闭", null)
                .setPositiveButton("停止全部", (d, w) -> {
                    LabRunner.stopAll();
                    LabProcManager.stopAll();
                    refresh(adapter);
                })
                .create();
        dialog.show();
        refresh(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            List<String> keys = LabRunner.runningKeys();
            if (position >= 0 && position < keys.size()) {
                LabRunner.stop(keys.get(position));
                refresh(adapter);
            }
        });
    }

    private static void refresh(ArrayAdapter<String> adapter) {
        List<String> keys = LabRunner.runningKeys();
        List<String> rows = new ArrayList<>();
        for (String key : keys) {
            rows.add(key + "  （点击停止）");
        }
        adapter.clear();
        adapter.addAll(rows);
    }
}
