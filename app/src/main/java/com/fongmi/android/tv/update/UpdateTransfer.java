package com.fongmi.android.tv.update;

import java.io.File;

public interface UpdateTransfer {

    void start(Callback callback);

    void cancel();

    interface Callback {

        void progress(int progress, long bytes, long total, long speed, long elapsed);

        void error(String message);

        void success(File file);
    }
}
