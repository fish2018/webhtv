package master.flame.danmaku.danmaku.model.android;

import java.util.ArrayList;
import java.util.List;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDanmakus;

public class Danmakus {

    private final List<BaseDanmaku> items = new ArrayList<>();
    private final Object synchronizer = new Object();

    public Danmakus(int sortType) {
    }

    public void addItem(BaseDanmaku item) {
        items.add(item);
    }

    public Object obtainSynchronizer() {
        return synchronizer;
    }
}
