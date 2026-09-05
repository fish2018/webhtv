package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.fongmi.android.tv.R;

public class BatteryUtil {

    public static int getLevel(Context context) {
        try {
            BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            int level = manager == null ? -1 : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (level >= 0 && level <= 100) return level;
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null) return -1;
            int raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return raw >= 0 && scale > 0 ? Math.round(raw * 100f / scale) : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static int getIcon(int level) {
        if (level >= 95) return R.drawable.ic_battery_full;
        if (level >= 85) return R.drawable.ic_battery_6;
        if (level >= 65) return R.drawable.ic_battery_5;
        if (level >= 50) return R.drawable.ic_battery_4;
        if (level >= 25) return R.drawable.ic_battery_3;
        if (level >= 15) return R.drawable.ic_battery_2;
        if (level > 0) return R.drawable.ic_battery_1;
        return R.drawable.ic_battery_0;
    }
}
