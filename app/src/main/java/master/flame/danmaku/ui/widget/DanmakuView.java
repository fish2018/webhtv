package master.flame.danmaku.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;

public class DanmakuView extends View {

    private DrawHandler.Callback callback;
    private boolean prepared;

    public DanmakuView(Context context) {
        super(context);
    }

    public DanmakuView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DanmakuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCallback(DrawHandler.Callback callback) {
        this.callback = callback;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public void prepare(BaseDanmakuParser parser, DanmakuContext context) {
        prepared = true;
        if (callback != null) callback.prepared();
    }

    public void resume() {
    }

    public void pause() {
    }

    public void stop() {
        prepared = false;
    }

    public void release() {
        prepared = false;
    }

    public void seekTo(long timeMs) {
    }

    public void start(long positionMs) {
        prepared = true;
    }

    public void show() {
        setVisibility(VISIBLE);
    }
}
