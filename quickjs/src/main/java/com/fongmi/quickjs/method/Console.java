package com.fongmi.quickjs.method;

import com.github.catvod.crawler.SpiderDebug;
import com.orhanobut.logger.Logger;
import com.whl.quickjs.wrapper.QuickJSContext;

public class Console implements QuickJSContext.Console {

    private static final String TAG = "quickjs";

    @Override
    public void log(String info) {
        Logger.t(TAG).d(info);
        SpiderDebug.console(TAG, "DEBUG", info);
    }

    @Override
    public void info(String info) {
        Logger.t(TAG).i(info);
        SpiderDebug.console(TAG, "INFO", info);
    }

    @Override
    public void warn(String info) {
        Logger.t(TAG).w(info);
        SpiderDebug.console(TAG, "WARN", info);
    }

    @Override
    public void error(String info) {
        Logger.t(TAG).e(info);
        SpiderDebug.console(TAG, "ERROR", info);
    }
}
