package com.fongmi.android.tv.ui.dialog;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;

public class HomeMenuKeyDialog {

    public static void show(FragmentActivity activity, Runnable callback) {
        String[] items = activity.getResources().getStringArray(R.array.select_home_menu_key);
        int current = Setting.getHomeMenuKey();

        new AlertDialog.Builder(activity)
            .setTitle(R.string.setting_home_menu_key)
            .setSingleChoiceItems(items, current, (dialog, which) -> {
                Setting.putHomeMenuKey(which);
                if (callback != null) callback.run();
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
