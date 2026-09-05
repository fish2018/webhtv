package master.flame.danmaku.danmaku.model;

public class BaseDanmaku {

    public static final int TYPE_SCROLL_RL = 1;
    public static final int TYPE_SCROLL_LR = 2;
    public static final int TYPE_FIX_TOP = 3;
    public static final int TYPE_FIX_BOTTOM = 4;
    public static final int TYPE_SPECIAL = 5;

    public int textShadowColor;
    public int textColor;
    public Object flags;
    public float textSize;
    public int index;
    private int type;
    private long time;

    public BaseDanmaku() {
        this(TYPE_SCROLL_RL);
    }

    public BaseDanmaku(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getTime() {
        return time;
    }

    public void setTimer(DanmakuTimer timer) {
    }
}
