package com.fongmi.android.tv.ad.feedback;

/**
 * 规则风险等级，沿用智能去广 V2 总设计第 12 节的三级划分。
 *
 * <p>低：广告 host 拦截、严格媒体 URL 排除；
 * 中：结构化 HLS 区块删除、DOM 声明式动作；
 * 高：任意 JavaScript、全文正则替换、宽泛固定时长规则。
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
