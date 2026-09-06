package com.fongmi.quickjs.method;

import com.orhanobut.logger.Logger;
import com.whl.quickjs.wrapper.QuickJSContext;
import com.github.catvod.crawler.SpiderDebug;

public class Console implements QuickJSContext.Console {

    private static final String TAG = "quickjs";

    @Override
    public void log(String info) {
        Logger.t(TAG).d(info);
        SpiderDebug.log("quickjs", info);
    }

    @Override
    public void info(String info) {
        Logger.t(TAG).i(info);
        SpiderDebug.log("quickjs", info);
    }

    @Override
    public void warn(String info) {
        Logger.t(TAG).w(info);
        SpiderDebug.log("quickjs", info);
    }

    @Override
    public void error(String info) {
        Logger.t(TAG).e(info);
        SpiderDebug.log("quickjs", info);
    }
}