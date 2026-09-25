package com.fongmi.android.tv.utils;

import android.widget.TextView;

import com.fongmi.android.tv.App;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class Clock {

    private DateTimeFormatter format;
    private Callback callback;
    private TextView view;
    private Timer timer;

    public static Clock create() {
        return new Clock();
    }

    public static Clock create(TextView view) {
        return new Clock().view(view).format("HH:mm:ss");
    }

    public Clock() {
    }

    public Clock view(TextView view) {
        this.view = view;
        return this;
    }

    public Clock format(String format) {
        this.format = DateTimeFormatter.ofPattern(format, Locale.getDefault());
        return this;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void start() {
        android.util.Log.d("Clock", "start() called, timer=" + (timer != null));
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                App.post(() -> doJob());
            }
        }, 0, 1000);
        android.util.Log.d("Clock", "Timer scheduled");
    }

    private void doJob() {
        try {
            long time = System.currentTimeMillis();
            android.util.Log.d("Clock", "doJob: callback=" + (callback != null));
            if (callback != null) callback.onTimeChanged(time);
            if (view != null) view.setText(format.format(LocalDateTime.now()));
        } catch (Exception e) {
            android.util.Log.e("Clock", "doJob error", e);
        }
    }

    public Clock stop() {
        if (timer != null) timer.cancel();
        return this;
    }

    public void release() {
        if (timer != null) timer.cancel();
        if (callback != null) callback = null;
    }

    public interface Callback {

        void onTimeChanged(long time);
    }
}
