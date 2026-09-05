package master.flame.danmaku.danmaku.parser;

import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;

public abstract class BaseDanmakuParser {

    protected Object mDataSource;
    protected float mDispDensity = 1.0f;
    protected DanmakuContext mContext;
    protected DanmakuTimer mTimer = new DanmakuTimer();

    public BaseDanmakuParser load(Object dataSource) {
        this.mDataSource = dataSource;
        return this;
    }

    public BaseDanmakuParser setDisplayer(Object displayer) {
        return this;
    }

    public BaseDanmakuParser setConfig(DanmakuContext context) {
        this.mContext = context;
        return this;
    }

    public abstract Object parse();
}
