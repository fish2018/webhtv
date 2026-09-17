package com.fongmi.android.tv.event;

import org.greenrobot.eventbus.EventBus;

/**
 * 猫源请求打开内嵌网页。
 *
 * <p>猫源的配置站点把设置入口伪装成点播条目，点它时 bundle 一边请求宿主开网页、一边把 detail
 * 返回成空条目。详情页得知道这件事才能及时退场——否则它要等 detail 结果才判定，而那份结果
 * 可能被主线程堵住好几秒（实测 7.7 秒），这段时间里按返回就会落在播放页上。
 *
 * <p>带上请求时刻：详情页拿它和自己这次 detail 的起始时间比，就能确认这次开页是不是自己
 * 触发的，而不用依赖任意长度的时间窗口。
 */
public class CatWebEvent {

    /**
     * 最近一次开页请求的时刻。
     *
     * <p>事件本身只能被订阅者收到，而结果兜底判定跑在取详情的线程上、拿不到事件；两处必须依据
     * 同一个事实，否则兜底会把「真片源但详情为空」（坏掉的 spider）也当成动作项静默关页。
     *
     * <p>刻意<b>不</b>读后即清：{@code setDetail} 每次进入会被投递两次（{@code singleTop} 加
     * 观察者重投），清掉会让第二次判定失配。按时刻比较天然幂等，不需要消费语义。
     */
    private static volatile long lastRequestAt;

    private final long time;

    private CatWebEvent(long time) {
        this.time = time;
    }

    public static void post() {
        long now = System.currentTimeMillis();
        lastRequestAt = now;
        EventBus.getDefault().post(new CatWebEvent(now));
    }

    /** 这次开页是否发生在给定的 detail 开始之后——即由那次导航触发。 */
    public boolean after(long detailStartTime) {
        return after(detailStartTime, time);
    }

    /** 最近一次开页请求是否由给定的 detail 导航触发。供拿不到事件的兜底判定使用。 */
    public static boolean requestedAfter(long detailStartTime) {
        return after(detailStartTime, lastRequestAt);
    }

    private static boolean after(long detailStartTime, long requestedAt) {
        return detailStartTime > 0 && requestedAt >= detailStartTime;
    }
}
