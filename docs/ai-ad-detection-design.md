# AI 广告检测与统计功能设计文档

## 一、功能概述

在播放器中新增"有广告反馈"按钮，用户点击后提交视频播放上下文给 AI 分析，AI 返回可能的去广规则建议。同时需要统计广告检测和规则应用情况。

### 核心流程
1. 用户播放视频时遇到广告
2. 点击"有广告反馈"按钮
3. 系统收集播放上下文（站点、剧名、线路、集名、URL、m3u8 切片信息）
4. 提交给 AI 分析
5. AI 返回去广规则建议（hosts/regex/exclude）
6. 用户可选择应用规则
7. **统计广告检测次数和规则应用效果**

---

## 二、已完成的模块

### 2.1 数据模型层 ✅

#### UserAdRule（用户自定义去广规则）
- 位置：`app/src/main/java/com/fongmi/android/tv/bean/UserAdRule.java`
- 字段：
  - `id`：规则 ID
  - `name`：规则名称
  - `hosts`：域名列表（换行分隔）
  - `regex`：正则表达式（换行分隔）
  - `exclude`：排除规则（换行分隔）
  - `enabled`：是否启用
  - `createdAt`：创建时间
  - `source`：来源标记（"ai"/"manual"）

#### UserAdRuleStore（持久化存储）
- 位置：`app/src/main/java/com/fongmi/android/tv/api/config/UserAdRuleStore.java`
- 功能：JSON 文件读写、规则增删改查

#### AdDetectionRequest（AI 检测请求）
- 位置：`app/src/main/java/com/fongmi/android/tv/bean/AdDetectionRequest.java`
- 包含播放上下文信息

#### AdDetectionResult（AI 检测结果）
- 位置：`app/src/main/java/com/fongmi/android/tv/bean/AdDetectionResult.java`
- 包含 AI 返回的规则建议和置信度

### 2.2 Service 层 ✅

#### AiAdDetectionService
- 位置：`app/src/main/java/com/fongmi/android/tv/service/AiAdDetectionService.java`
- 功能：
  - 构建 AI prompt
  - 调用 AI API
  - 解析结果

#### RuleConfig 合并逻辑 ✅
- 位置：`app/src/main/java/com/fongmi/android/tv/api/config/RuleConfig.java`
- 已整合默认规则 + 用户规则
- 提供 `getAds()` 供 WebView 去广使用

#### Setting 开关 ✅
- `Setting.getAdBlock()`：去广总开关
- `AiConfig.isReady()`：AI 配置是否就绪

### 2.3 UI 层 ✅

#### 播放器反馈按钮（mobile + leanback）
- 在 `view_control_vod_action.xml` 中添加按钮
- 在 `VideoActivity` 中绑定点击事件
- 显示条件：`Setting.getAdBlock() && AiConfig.get().isReady()`

#### 规则管理 Dialog（mobile + leanback）
- `AdRuleManageDialog`：规则列表、启用/禁用、删除
- `AdRuleEditDialog`：编辑/新建规则
- `AdRuleAdapter`：规则列表适配器

---

## 三、待补充：广告统计功能 ⚠️

### 3.1 统计需求

#### 需要统计的数据
1. **广告反馈统计**
   - 反馈次数
   - 反馈的站点分布
   - AI 分析成功/失败次数

2. **规则应用统计**
   - 每条规则的匹配次数（实际拦截了多少次请求）
   - 规则来源分布（默认规则 vs AI 规则 vs 手动规则）
   - 规则的有效性（启用/禁用状态）

3. **去广效果统计**
   - 总拦截次数
   - 按站点统计拦截次数
   - 按规则类型统计（hosts/regex/exclude）

### 3.2 统计数据模型

#### AdBlockStats（广告拦截统计）
```java
// app/src/main/java/com/fongmi/android/tv/bean/AdBlockStats.java
public class AdBlockStats {
    private long totalBlocked;              // 总拦截次数
    private long aiRuleFeedbackCount;       // AI 反馈次数
    private long aiAnalysisSuccess;         // AI 分析成功次数
    private long aiAnalysisFailed;          // AI 分析失败次数
    private Map<String, Long> siteBlocked;  // 按站点统计拦截次数
    private Map<String, Long> ruleCounts;   // 按规则 ID 统计匹配次数
    private long lastResetAt;               // 上次重置时间
}
```

#### RuleHitRecord（规则命中记录）
```java
// 用于详细追踪某条规则的命中情况
public class RuleHitRecord {
    private String ruleId;
    private String ruleName;
    private String ruleSource;  // "default"/"ai"/"manual"
    private long hitCount;
    private long lastHitAt;
    private List<String> recentBlockedHosts;  // 最近拦截的域名（最多保留 10 条）
}
```

### 3.3 统计存储

#### AdBlockStatsStore
```java
// app/src/main/java/com/fongmi/android/tv/api/config/AdBlockStatsStore.java
public class AdBlockStatsStore {
    private static final String FILE = "ad_block_stats.json";
    
    public static AdBlockStats load();
    public static void save(AdBlockStats stats);
    
    // 增量更新方法
    public static void recordBlock(String siteKey, String ruleId);
    public static void recordFeedback(String siteKey);
    public static void recordAiAnalysis(boolean success);
    
    // 查询方法
    public static AdBlockStats getStats();
    public static List<RuleHitRecord> getTopRules(int limit);
    public static void reset();  // 重置统计
}
```

### 3.4 统计埋点

#### 在 CustomWebView 中埋点
```java
// app/src/main/java/com/fongmi/android/tv/ui/custom/CustomWebView.java

private boolean isAd(String host) {
    for (String ad : RuleConfig.get().getAds()) {
        if (Util.containOrMatch(host, ad)) {
            // ✅ 埋点：记录拦截
            AdBlockStatsStore.recordBlock(key, getRuleIdFromAd(ad));
            return true;
        }
    }
    return false;
}

// 需要新增：根据 ad 字符串反查规则 ID
private String getRuleIdFromAd(String ad) {
    // 从 RuleConfig 中查找包含此 ad 的规则
    // 返回规则 ID 或 "unknown"
}
```

#### 在 VideoActivity 中埋点
```java
// 用户点击"有广告反馈"时
private void onAdFeedback() {
    AdBlockStatsStore.recordFeedback(getSiteKey());
    // ... 调用 AI 分析
}

// AI 分析回调
private void onAiAnalysisComplete(AdDetectionResult result) {
    AdBlockStatsStore.recordAiAnalysis(!result.isError());
    // ... 显示结果
}
```

### 3.5 统计展示 UI

#### AdBlockStatsDialog（统计报表对话框）
```
位置：
- app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdBlockStatsDialog.java
- app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdBlockStatsDialog.java

布局：
- app/src/leanback/res/layout/dialog_ad_block_stats.xml
- app/src/mobile/res/layout/dialog_ad_block_stats.xml

内容：
1. 总览卡片
   - 总拦截次数
   - AI 反馈次数
   - AI 分析成功率

2. 站点拦截排行（Top 10）
   - 站点名称
   - 拦截次数
   - 百分比

3. 规则命中排行（Top 10）
   - 规则名称
   - 来源（默认/AI/手动）
   - 命中次数

4. 操作按钮
   - 重置统计
   - 导出报表（可选）
```

#### 入口
在 `AdRuleManageDialog` 中新增"统计报表"按钮，点击打开 `AdBlockStatsDialog`。

### 3.6 字符串资源

```xml
<!-- app/src/main/res/values/strings.xml -->
<string name="ad_block_stats">广告拦截统计</string>
<string name="ad_block_total">总拦截次数</string>
<string name="ad_feedback_count">AI 反馈次数</string>
<string name="ad_ai_success_rate">AI 分析成功率</string>
<string name="ad_site_rank">站点拦截排行</string>
<string name="ad_rule_rank">规则命中排行</string>
<string name="ad_stats_reset">重置统计</string>
<string name="ad_stats_reset_confirm">确定重置所有统计数据吗？</string>
<string name="ad_stats_empty">暂无统计数据</string>
```

---

## 四、实现步骤

### Step 1：创建统计数据模型
- [x] `AdBlockStats.java`
- [x] `RuleHitRecord.java`

### Step 2：创建统计存储
- [x] `AdBlockStatsStore.java`

### Step 3：在去广逻辑中埋点
- [x] `CustomWebView.isAd()` 中记录拦截
- [x] `VideoActivity.onAdFeedback()` 中记录反馈
- [x] `VideoActivity.onAiAnalysisComplete()` 中记录 AI 分析结果

### Step 4：创建统计展示 UI
- [x] `AdBlockStatsDialog.java`（mobile + leanback）
- [x] `dialog_ad_block_stats.xml`（mobile + leanback）
- [x] 在 `AdRuleManageDialog` 中添加入口

### Step 5：测试
- [ ] 测试拦截统计
- [ ] 测试 AI 反馈统计
- [ ] 测试统计报表显示
- [ ] 测试重置功能

---

## 五、注意事项

1. **性能考虑**
   - 统计写入使用异步操作，避免阻塞主线程
   - 使用增量更新，不要每次都全量读写 JSON
   - 限制内存中的详细记录数量（如最近拦截域名最多 10 条）

2. **隐私考虑**
   - 统计数据仅本地存储，不上传
   - 不记录完整 URL，只记录域名和站点 key
   - 用户可随时重置统计

3. **规则 ID 映射**
   - 默认规则需要稳定的 ID（使用 `RuleIdUtil.id(rule)`）
   - 用户规则使用 UUID
   - AI 规则创建时标记来源为 "ai"

4. **统计精度**
   - 同一请求被多条规则匹配时，只记录第一条命中的规则
   - 区分不同类型的规则（hosts/regex/exclude）

---

## 六、未来扩展

1. **趋势图**
   - 按日/周/月统计拦截趋势
   - 需要额外的时间序列存储

2. **导出功能**
   - 导出统计报表为 CSV/JSON
   - 分享给其他用户

3. **规则推荐**
   - 基于统计数据推荐启用/禁用某些规则
   - AI 学习用户的拦截偏好

---

## 七、相关文件清单

### 已完成 ✅
- `app/src/main/java/com/fongmi/android/tv/bean/UserAdRule.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/UserAdRuleStore.java`
- `app/src/main/java/com/fongmi/android/tv/bean/AdDetectionRequest.java`
- `app/src/main/java/com/fongmi/android/tv/bean/AdDetectionResult.java`
- `app/src/main/java/com/fongmi/android/tv/service/AiAdDetectionService.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/RuleConfig.java`
- `app/src/main/java/com/fongmi/android/tv/setting/Setting.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdRuleEditDialog.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRuleEditDialog.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/AdRuleAdapter.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/adapter/AdRuleAdapter.java`

### 待创建 ⚠️
- `app/src/main/java/com/fongmi/android/tv/bean/AdBlockStats.java`
- `app/src/main/java/com/fongmi/android/tv/bean/RuleHitRecord.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/AdBlockStatsStore.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdBlockStatsDialog.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdBlockStatsDialog.java`
- `app/src/leanback/res/layout/dialog_ad_block_stats.xml`
- `app/src/mobile/res/layout/dialog_ad_block_stats.xml`

### 需要修改 🔧
- `app/src/main/java/com/fongmi/android/tv/ui/custom/CustomWebView.java`（埋点）
- `app/src/main/res/values/strings.xml`（新增字符串）
