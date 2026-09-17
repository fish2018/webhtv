package com.fongmi.android.tv.event;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 开页请求的归属判定。
 *
 * <p>事件只有订阅者收得到，而结果兜底判定跑在取详情的线程上，两处必须依据同一个事实——
 * 「这次开页请求是不是本次导航触发的」。判定按时刻比较，天然幂等：{@code setDetail} 每次进入
 * 会被投递两次（{@code singleTop} 加观察者重投），读后即清会让第二次失配。
 */
public class CatWebEventTest {

    @Test
    public void requestBelongsToNavigationStartedBefore() {
        long start = System.currentTimeMillis();
        CatWebEvent.post();
        assertTrue("开页发生在导航开始之后，归属这次导航", CatWebEvent.requestedAfter(start));
    }

    @Test
    public void judgementIsIdempotent() {
        long start = System.currentTimeMillis();
        CatWebEvent.post();
        assertTrue(CatWebEvent.requestedAfter(start));
        assertTrue("同一次请求要能被判定多次——detail 结果会投递两次",
                CatWebEvent.requestedAfter(start));
    }

    @Test
    public void requestDoesNotBelongToLaterNavigation() {
        CatWebEvent.post();
        long later = System.currentTimeMillis() + 5000;
        assertFalse("晚于开页请求才开始的导航，不该认领这次请求",
                CatWebEvent.requestedAfter(later));
    }

    @Test
    public void zeroStartTimeNeverMatches() {
        CatWebEvent.post();
        assertFalse("尚未开始取详情时不该匹配", CatWebEvent.requestedAfter(0));
    }
}
