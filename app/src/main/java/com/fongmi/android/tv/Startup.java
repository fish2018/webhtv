package com.fongmi.android.tv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusBuilder;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;

import java.util.Collections;
import java.util.List;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class Startup implements Initializer<Void> {

    private static final String EVENT_INDEX = "com.fongmi.android.tv.event.EventIndex";

    @NonNull
    @Override
    public Void create(@NonNull Context context) {
        CaocConfig.Builder.create().trackActivities(true).backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT).errorActivity(CrashActivity.class).apply();
        Logger.addLogAdapter(new AndroidLogAdapter(PrettyFormatStrategy.newBuilder().methodCount(0).showThreadInfo(false).tag("TV").build()));
        installEventBus();
        OkHttp.dns().setDoh(() -> Doh.objectFrom(Setting.getDoh()));
        return null;
    }

    private void installEventBus() {
        EventBusBuilder builder = EventBus.builder();
        try {
            Object index = Class.forName(EVENT_INDEX).getDeclaredConstructor().newInstance();
            if (index instanceof SubscriberInfoIndex) builder.addIndex((SubscriberInfoIndex) index);
        } catch (Exception ignored) {
        }
        builder.installDefaultEventBus();
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }
}
