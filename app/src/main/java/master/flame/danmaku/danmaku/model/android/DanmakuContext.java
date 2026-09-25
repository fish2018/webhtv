package master.flame.danmaku.danmaku.model.android;

import java.util.Map;

import master.flame.danmaku.danmaku.model.AbsDanmakuSync;
import master.flame.danmaku.danmaku.model.BaseDanmaku;

public class DanmakuContext {

    public final DanmakuFactory mDanmakuFactory = new DanmakuFactory();
    public final Object mGlobalFlagValues = new Object();

    public static DanmakuContext create() {
        return new DanmakuContext();
    }

    public DanmakuContext setScaleTextSize(float size) {
        return this;
    }

    public DanmakuContext setMaximumLines(Map<Integer, Integer> lines) {
        return this;
    }

    public DanmakuContext setScrollSpeedFactor(float factor) {
        return this;
    }

    public DanmakuContext setDanmakuTransparency(float alpha) {
        return this;
    }

    public DanmakuContext setDanmakuMargin(int margin) {
        return this;
    }

    public DanmakuContext setDanmakuStyle(int style, int width) {
        return this;
    }

    public DanmakuContext setDanmakuSync(AbsDanmakuSync sync) {
        return this;
    }

    public static class DanmakuFactory {
        public BaseDanmaku createDanmaku(int type, DanmakuContext context) {
            return new BaseDanmaku(type);
        }
    }
}
