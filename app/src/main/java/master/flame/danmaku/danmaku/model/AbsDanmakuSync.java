package master.flame.danmaku.danmaku.model;

public abstract class AbsDanmakuSync {

    public static final int SYNC_STATE_HALT = 0;
    public static final int SYNC_STATE_PLAYING = 1;

    public abstract long getUptimeMillis();

    public abstract int getSyncState();

    public abstract long getThresholdTimeMills();

    public abstract boolean isSyncPlayingState();
}
