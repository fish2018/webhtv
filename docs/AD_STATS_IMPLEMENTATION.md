# 广告拦截统计功能实现总结

## 功能概述

在原有 AI 智能去广告功能基础上，新增了完整的广告拦截统计系统，提供拦截数据的记录、展示和管理。

## 核心功能

### 1. 统计维度

- **总拦截次数**：累计拦截的广告请求总数
- **AI 反馈次数**：用户点击"有广告"按钮的次数
- **AI 分析成功率**：AI 成功生成规则的比例
- **站点拦截排行**：按站点统计的拦截次数 Top 10
- **规则命中排行**：按规则统计的命中次数 Top 10（含规则来源标识）

### 2. 数据记录

拦截统计在广告被拦截时自动记录，无需用户干预：
- 播放器 WebView 拦截广告时 → 记录站点 + 规则命中
- 用户点击"有广告"按钮时 → 记录 AI 反馈
- AI 返回分析结果时 → 记录成功/失败

### 3. 统计展示

在"去广规则管理"对话框中新增"统计"按钮，打开统计对话框：
- 顶部：三项核心指标卡片（总拦截 / AI反馈 / 成功率）
- 中部：站点拦截排行列表
- 下部：规则命中排行列表（显示规则名称、来源、命中次数）
- 底部：重置统计 + 关闭按钮

## 技术实现

### 数据模型层

#### `AdBlockStats.java`（统计数据模型）
```java
- totalBlocked: long           // 总拦截次数
- aiRuleFeedbackCount: long    // AI 反馈次数
- aiAnalysisSuccess: long      // AI 分析成功次数
- aiAnalysisFailed: long       // AI 分析失败次数
- siteBlocked: Map<String,Long> // 按站点统计
- ruleCounts: Map<String,Long>  // 按规则统计
- lastResetAt: long            // 上次重置时间
```

关键方法：
- `incrementTotalBlocked()` / `incrementSiteBlocked()` / `incrementRuleCount()`
- `incrementAiFeedback()` / `incrementAiSuccess()` / `incrementAiFailed()`
- `getAiSuccessRate()` — 计算成功率
- `reset()` — 重置所有统计

#### `RuleHitRecord.java`（规则命中记录）
```java
- ruleId: String              // 规则 ID
- ruleName: String            // 规则名称
- ruleSource: String          // 来源：默认/AI/手动
- hitCount: long              // 命中次数
- lastHitAt: long             // 最后命中时间
- recentBlockedHosts: List    // 最近拦截域名（最多10条）
```

### 存储层

#### `AdBlockStatsStore.java`
- **存储方式**：SharedPreferences（key: `ad_block_stats`），JSON 序列化
- **缓存机制**：内存缓存 + 单线程异步写入，避免频繁 IO
- **核心方法**：
  - `recordBlock(siteKey, ruleId)` — 记录拦截（异步）
  - `recordFeedback(siteKey)` — 记录 AI 反馈（异步）
  - `recordAiAnalysis(success)` — 记录 AI 分析结果（异步）
  - `getTopRules(limit)` — 获取命中排行（含规则信息填充）
  - `reset()` — 重置统计

规则信息填充逻辑：
- 用户规则：以 UUID 为 ID，标记来源为"AI"或"手动"
- 默认规则：以 `RuleIdUtil.computeRuleId()` 计算的 ID 标识，标记为"默认"

### 埋点集成

#### 拦截埋点（`CustomWebView.java`）
在 `isAd(host)` 方法中，当命中广告规则时：
```java
String ruleId = findRuleIdByAdPattern(ad);
AdBlockStatsStore.recordBlock(key, ruleId);
```

`findRuleIdByAdPattern()` 反查规则 ID：
1. 遍历用户规则，匹配 hosts/regex/exclude 字段
2. 遍历默认规则，匹配 hosts 字段并计算 ID

### UI 层

#### `AdBlockStatsDialog.java`（mobile + leanback）
- 统计概览卡片：三项核心指标
- 站点排行 RecyclerView：`SiteRankAdapter`
- 规则排行 RecyclerView：`RuleRankAdapter`
- 空态处理：无数据时显示提示文字
- 重置确认：二次确认对话框

#### 布局文件
- `dialog_ad_block_stats.xml` — 统计对话框主布局
- `adapter_ad_stats_item.xml` — 排行列表项（名称 + 来源 + 次数）
- `selector_ad_rule.xml` — 列表项焦点选择器（新增）

### 入口集成

在 `AdRuleManageDialog` 中：
```java
binding.stats.setOnClickListener(v -> onStats());

private void onStats() {
    AdBlockStatsDialog.create((FragmentActivity) requireActivity()).show();
}
```

## 字符串资源

```xml
<string name="ad_block_stats">广告拦截统计</string>
<string name="ad_block_total">总拦截次数</string>
<string name="ad_feedback_count">AI 反馈次数</string>
<string name="ad_ai_success_rate">AI 分析成功率</string>
<string name="ad_site_rank">站点拦截排行</string>
<string name="ad_rule_rank">规则命中排行</string>
<string name="ad_stats_reset">重置统计</string>
<string name="ad_stats_reset_confirm">确定重置所有统计数据吗？</string>
<string name="ad_stats_empty">暂无统计数据</string>
<string name="ad_stats_overview">统计概览</string>
```

## 编译验证

- ✅ Mobile arm64 debug 编译成功
- ✅ Leanback arm64 debug 编译成功

## 修复的问题

1. **FileUtil API 不存在**：改用 `Prefers`（SharedPreferences）存储，与 `UserAdRuleStore` 保持一致
2. **Rule.getId() 不存在**：改用 `RuleIdUtil.computeRuleId(rule)` 计算规则 ID
3. **Rule.getAds() 不存在**：改用 `rule.getHosts()` 获取域名列表
4. **matchesRule 类型错误**：参数从 `String` 改为 `List<String>`，适配 UserAdRule 字段类型
5. **selector_ad_rule 缺失**：新增焦点选择器 drawable

## 文件清单

### 新增文件
- `app/src/main/java/com/fongmi/android/tv/bean/AdBlockStats.java`
- `app/src/main/java/com/fongmi/android/tv/bean/RuleHitRecord.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/AdBlockStatsStore.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdBlockStatsDialog.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdBlockStatsDialog.java`
- `app/src/leanback/res/layout/dialog_ad_block_stats.xml`
- `app/src/mobile/res/layout/dialog_ad_block_stats.xml`
- `app/src/leanback/res/layout/adapter_ad_stats_item.xml`
- `app/src/mobile/res/layout/adapter_ad_stats_item.xml`
- `app/src/main/res/drawable/selector_ad_rule.xml`

### 修改文件
- `app/src/main/java/com/fongmi/android/tv/ui/custom/CustomWebView.java` — 拦截埋点
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java` — 统计入口
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java` — 统计入口
- `app/src/main/res/values/strings.xml` — 字符串资源

## 数据流

```
广告拦截发生
    ↓
CustomWebView.isAd(host)
    ↓
findRuleIdByAdPattern(ad) → 反查规则 ID
    ↓
AdBlockStatsStore.recordBlock(siteKey, ruleId) [异步]
    ↓
AdBlockStats 增量更新
    ↓
Prefers 持久化（JSON）

用户查看统计
    ↓
AdRuleManageDialog → 点击"统计"
    ↓
AdBlockStatsDialog.show()
    ↓
AdBlockStatsStore.getStats() + getTopRules(10)
    ↓
展示概览 + 站点排行 + 规则排行
```

## 设计要点

1. **异步写入**：所有记录操作在单线程 executor 中执行，不阻塞主线程和播放
2. **内存缓存**：避免每次记录都读取 SharedPreferences
3. **规则来源区分**：统计中明确显示规则是默认/AI/手动，便于用户判断
4. **与现有存储一致**：复用 `Prefers` 机制，与 `UserAdRuleStore`、`DisabledDefaultRuleStore` 统一
5. **Top N 限制**：排行榜只展示前 10 名，避免列表过长
