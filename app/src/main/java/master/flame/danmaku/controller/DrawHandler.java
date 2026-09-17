package master.flame.danmaku.controller;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;

public interface DrawHandler {

    interface Callback {
        void prepared();

        void updateTimer(DanmakuTimer danmakuTimer);

        void danmakuShown(BaseDanmaku baseDanmaku);

        void drawingFinished();
    }
}
