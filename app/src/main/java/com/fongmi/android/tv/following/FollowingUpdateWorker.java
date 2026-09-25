package com.fongmi.android.tv.following;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

public class FollowingUpdateWorker extends Worker {

    public FollowingUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!FollowingSettings.isEnabled()) return Result.success();
        try {
            new FollowingUpdateCoordinator().checkDue(System.currentTimeMillis());
            return Result.success();
        } catch (IOException e) {
            return Result.retry();
        } catch (Throwable e) {
            return Result.success();
        }
    }
}
