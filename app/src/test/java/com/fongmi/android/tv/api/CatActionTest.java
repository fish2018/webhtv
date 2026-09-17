package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 猫源动作项的识别。
 *
 * <p>配置站点把设置入口伪装成点播条目，点它时 bundle 请求宿主开网页、detail 返回含一个
 * <b>全空</b>条目的列表。列表非空 → 详情页照常打开 → 内嵌网页背后压着空白播放页。
 *
 * <p>{@code shouldYieldDetail} 还要查站点是不是猫源，那一半依赖 {@code VodConfig} 单例，
 * 普通单测里拿不到；所以这里集中测 {@link CatAction#blank} —— 判定的另一半，也是
 * {@code SiteApi} 用来决定「这条详情不值得进缓存」的同一个谓词。
 */
public class CatActionTest {

    private static Result of(Vod... items) {
        Result result = new Result();
        result.setList(new ArrayList<>(Arrays.asList(items)));
        return result;
    }

    @Test
    public void blankRecognisesTheActionItemShape() {
        assertTrue("bundle 对设置项返回的就是这种全空条目", CatAction.blank(new Vod()));
    }

    @Test
    public void blankToleratesNull() {
        assertTrue(CatAction.blank(null));
    }

    @Test
    public void nameAloneMakesItNotBlank() {
        Vod vod = new Vod();
        vod.setName("太荒吞天诀");
        assertFalse("有名字就有东西可显示", CatAction.blank(vod));
    }

    @Test
    public void flagsAloneMakeItNotBlank() {
        Vod vod = new Vod();
        List<Flag> flags = new ArrayList<>(Collections.singletonList(Flag.create("m3u8", "第1集$http://x/1.m3u8")));
        vod.setFlags(flags);
        assertFalse("有播放线路就该正常进详情页", CatAction.blank(vod));
    }

    @Test
    public void contentAloneMakesItNotBlank() {
        Vod vod = new Vod();
        vod.setContent("这是简介");
        assertFalse("只有简介也算有内容", CatAction.blank(vod));
    }

    @Test
    public void picAloneMakesItNotBlank() {
        Vod vod = new Vod();
        vod.setPic("http://x/a.jpg");
        assertFalse("只有封面也算有内容", CatAction.blank(vod));
    }

    @Test
    public void emptyListNeverYields() {
        assertFalse("空列表归 setEmpty 处理，不该被这条规则截走",
                CatAction.shouldYieldDetail("nodejs_baseset", 1L, of()));
    }

    @Test
    public void nullResultNeverYields() {
        assertFalse(CatAction.shouldYieldDetail("nodejs_baseset", 1L, null));
    }

    @Test
    public void blankItemAloneIsNotEnoughWithoutCatSource() {
        // 站点查不到（空 key）时不能让位——判定的几个条件必须都成立
        assertFalse("站点不是猫源就不该关页面", CatAction.shouldYieldDetail("", 1L, of(new Vod())));
    }

    /**
     * 没请求过开页就不能让位。
     *
     * <p>坏掉的 spider 对真片源返回空对象时也满足 blank + 猫源两条；少了「这次导航确实请求过
     * 开页」这一条，页面会静默关闭，用户只看到闪一下、得不到任何解释。
     */
    @Test
    public void blankResultWithoutWebviewRequestKeepsPage() {
        // detailStartTime 取当前时刻：本测试从未调用过 CatWebEvent.post()，所以不该让位
        assertFalse("没有开页请求时，空详情不该被当成动作项",
                CatAction.shouldYieldDetail("nodejs_baseset", System.currentTimeMillis(), of(new Vod())));
    }

    @Test
    public void zeroDetailStartTimeNeverYields() {
        assertFalse("尚未开始取详情（时刻为 0）时不该让位",
                CatAction.shouldYieldDetail("nodejs_baseset", 0L, of(new Vod())));
    }
}
