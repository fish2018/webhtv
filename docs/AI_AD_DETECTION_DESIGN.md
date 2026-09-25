# AI 智能去广告功能设计文档

## 1. 功能概述

### 1.1 目标
在播放器中新增"有广告反馈"按钮，用户点击后将视频相关信息提交给 AI 分析，识别可能的广告片段，动态生成去广规则，提升现有去广功能的准确性和覆盖面。

### 1.2 核心流程
```
用户播放视频 → 发现广告 → 点击"有广告"按钮 → 
收集视频信息 → 提交 AI 分析 → 生成去广规则 → 
用户可在设置中管理规则（查看/编辑/删除）
```

### 1.3 前置条件
- 用户已在设置中开启"AI 功能"
- 用户已在设置中开启"去广功能"
- 两者同时开启时，"有广告"按钮才显示

---

## 2. 架构设计

### 2.1 模块分层

```
┌─────────────────────────────────────────────────────────────┐
│                         UI 层                                │
│  ┌────────────────────┐  ┌──────────────────────────────┐   │
│  │  VideoActivity     │  │  SettingEnhanceActivity      │   │
│  │  - adFeedbackBtn   │  │  - 用户规则管理入口           │   │
│  │  - 点击触发反馈     │  │  - AdRuleManageDialog        │   │
│  └────────────────────┘  └──────────────────────────────┘   │
│            │                        │                        │
└────────────┼────────────────────────┼────────────────────────┘
             │                        │
             ▼                        ▼
┌─────────────────────────────────────────────────────────────┐
│                      Service 层                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │          AiAdDetectionService                       │    │
│  │  - detectAd(request): AdDetectionResult             │    │
│  │  - 调用 AI API 分析视频信息                          │    │
│  │  - 解析 AI 返回的 hosts/regex/exclude               │    │
│  └─────────────────────────────────────────────────────┘    │
│            │                        │                        │
└────────────┼────────────────────────┼────────────────────────┘
             │                        │
             ▼                        ▼
┌─────────────────────────────────────────────────────────────┐
│                      数据层                                  │
│  ┌──────────────────┐  ┌──────────────────────────────┐    │
│  │  RuleConfig      │  │  UserAdRuleStore             │    │
│  │  - 全局规则列表   │  │  - 用户自定义规则持久化       │    │
│  │  - 合并默认+用户  │  │  - 增删改查                  │    │
│  └──────────────────┘  └──────────────────────────────┘    │
│            │                        │                        │
└────────────┼────────────────────────┼────────────────────────┘
             │                        │
             ▼                        ▼
┌─────────────────────────────────────────────────────────────┐
│                   Bean / Model 层                            │
│  Rule (默认)  UserAdRule (用户)  AdDetectionRequest/Result   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 关键类说明

| 类名 | 职责 | 位置 |
|-----|------|------|
| `VideoActivity` | 播放器界面，新增"有广告"按钮 | `leanback/mobile/ui/activity/` |
| `AdRuleManageDialog` | 用户规则管理弹窗（列表 + 新增） | `leanback/mobile/ui/dialog/` |
| `AdRuleEditDialog` | 单条规则编辑弹窗 | `leanback/mobile/ui/dialog/` |
| `AdRuleAdapter` | 规则列表适配器 | `leanback/mobile/ui/adapter/` |
| `AiAdDetectionService` | AI 去广检测服务 | `main/service/` |
| `UserAdRuleStore` | 用户规则存储（JSON） | `main/api/config/` |
| `UserAdRule` | 用户规则数据模型 | `main/bean/` |
| `AdDetectionRequest` | AI 请求数据 | `main/bean/` |
| `AdDetectionResult` | AI 响应数据 | `main/bean/` |
| `RuleConfig` | 全局规则管理（合并默认 + 用户） | `main/api/config/` |
| `Setting` | 新增开关常量 | `main/setting/` |

---

## 3. 数据模型设计

### 3.1 UserAdRule（用户自定义规则）

```java
public class UserAdRule {
    private String id;              // 唯一标识（UUID）
    private String name;            // 规则名称（用户可编辑）
    private List<String> hosts;     // 域名列表（逗号分隔，支持通配符）
    private List<String> regex;     // 正则列表（匹配 URL）
    private List<String> exclude;   // 排除规则（避免误杀）
    private boolean enabled;        // 是否启用
    private long createdAt;         // 创建时间戳
    private String source;          // 来源（"ai" / "manual"）
}
```

### 3.2 AdDetectionRequest（AI 请求）

```java
public class AdDetectionRequest {
    private String videoUrl;        // 当前播放 URL
    private String videoTitle;      // 视频标题
    private String siteName;        // 站点名称
    private List<String> urlHistory; // 最近播放的 URL 列表（用于模式分析）
}
```

### 3.3 AdDetectionResult（AI 响应）

```java
public class AdDetectionResult {
    private boolean success;
    private String ruleName;        // 建议的规则名称
    private List<String> hosts;
    private List<String> regex;
    private List<String> exclude;
    private String reason;          // AI 分析理由（可选）
}
```

---

## 4. UI 设计

### 4.1 播放器按钮（VideoActivity）

#### 位置
- **mobile**: `view_control_vod_action.xml` 中新增 `adFeedback` 按钮
- **leanback**: `view_control_vod_action.xml` 中新增 `adFeedback` 按钮

#### 显示逻辑
```java
private void updateAdFeedbackButton() {
    boolean show = Setting.isAiEnabled() && Setting.isAdRemovalEnabled();
    binding.adFeedback.setVisibility(show ? View.VISIBLE : View.GONE);
}
```

#### 点击行为
```java
binding.adFeedback.setOnClickListener(v -> {
    // 1. 收集当前播放信息
    AdDetectionRequest request = new AdDetectionRequest()
        .setVideoUrl(getCurrentUrl())
        .setVideoTitle(getVodName())
        .setSiteName(getSiteName())
        .setUrlHistory(getRecentUrls());
    
    // 2. 提交 AI 分析（异步）
    AiAdDetectionService.detectAd(request, result -> {
        if (result.isSuccess()) {
            // 3. 保存规则
            UserAdRuleStore.save(result.toUserAdRule());
            Notify.show("已添加去广规则：" + result.getRuleName());
        } else {
            Notify.show("AI 分析失败，请稍后重试");
        }
    });
});
```

### 4.2 规则管理界面（SettingEnhanceActivity）

#### 入口按钮
在"增强功能"设置页新增一项：
```
┌────────────────────────────────┐
│ 去广规则管理                    │
│ 查看和编辑 AI 生成的去广规则     │
└────────────────────────────────┘
```

点击后打开 `AdRuleManageDialog`。

#### AdRuleManageDialog 结构

```
┌────────────────────────────────────────┐
│           去广规则管理                  │
├────────────────────────────────────────┤
│  [+ 新增规则]                          │
├────────────────────────────────────────┤
│  ┌──────────────────────────────────┐ │
│  │ ■ 默认规则 1                      │ │
│  │   hosts: ad.*, *.doubleclick.net │ │
│  │   [查看] [禁用]                   │ │
│  └──────────────────────────────────┘ │
│  ┌──────────────────────────────────┐ │
│  │ ■ AI 规则：某某视频站              │ │
│  │   hosts: adv.example.com         │ │
│  │   [编辑] [删除]                   │ │
│  └──────────────────────────────────┘ │
│  ┌──────────────────────────────────┐ │
│  │ □ 我的自定义规则                  │ │
│  │   regex: .*\\.mp4\\?ad=.*        │ │
│  │   [编辑] [启用] [删除]            │ │
│  └──────────────────────────────────┘ │
├────────────────────────────────────────┤
│               [关闭]                   │
└────────────────────────────────────────┘
```

#### 功能点
- **默认规则**：只读，可禁用/启用，不可删除
- **AI 规则**：可编辑、删除、禁用
- **手动规则**：可编辑、删除、禁用
- **新增规则**：打开 `AdRuleEditDialog`（空白表单）

### 4.3 规则编辑弹窗（AdRuleEditDialog）

```
┌────────────────────────────────────────┐
│           编辑去广规则                  │
├────────────────────────────────────────┤
│  规则名称                              │
│  ┌──────────────────────────────────┐ │
│  │ 我的规则                          │ │
│  └──────────────────────────────────┘ │
│                                        │
│  域名列表（每行一个，支持通配符）       │
│  ┌──────────────────────────────────┐ │
│  │ example.com                       │ │
│  │ ad.provider.net                   │ │
│  └──────────────────────────────────┘ │
│  提示：支持 *.ad.com 或 ad.*          │
│                                        │
│  正则表达式（每行一个）                │
│  ┌──────────────────────────────────┐ │
│  │ .*\.mp4\?ad=.*                    │ │
│  │ .*/preroll/.*                     │ │
│  └──────────────────────────────────┘ │
│  提示：匹配 URL 中可能包含广告的部分   │
│                                        │
│  排除规则（可选，避免误杀）            │
│  ┌──────────────────────────────────┐ │
│  │ .*trailer.*                       │ │
│  │ .*preview.*                       │ │
│  └──────────────────────────────────┘ │
│  提示：即使匹配上面规则也不过滤        │
│                                        │
│         [取消]      [保存]             │
└────────────────────────────────────────┘
```

---

## 5. 业务逻辑设计

### 5.1 规则合并逻辑（RuleConfig）

```java
public class RuleConfig {
    private static List<Rule> defaultRules;  // 内置默认规则
    private static List<UserAdRule> userRules; // 用户规则
    
    // 获取所有启用的规则（默认 + 用户）
    public static List<Rule> getAllEnabledRules() {
        List<Rule> result = new ArrayList<>();
        
        // 1. 添加默认规则
        result.addAll(defaultRules);
        
        // 2. 添加用户启用的规则
        for (UserAdRule userRule : userRules) {
            if (userRule.isEnabled()) {
                result.add(userRule.toRule());
            }
        }
        
        return result;
    }
    
    // 刷新用户规则（从文件加载）
    public static void refreshUserRules() {
        userRules = UserAdRuleStore.loadAll();
    }
}
```

### 5.2 URL 过滤逻辑（使用规则）

现有的 `OkHttpClient` 拦截器中调用：

```java
private boolean shouldBlockUrl(String url) {
    List<Rule> rules = RuleConfig.getAllEnabledRules();
    
    for (Rule rule : rules) {
        // 1. 检查域名匹配
        if (matchesHosts(url, rule.getHosts())) {
            // 2. 检查排除规则
            if (!matchesExclude(url, rule.getExclude())) {
                return true; // 拦截
            }
        }
        
        // 3. 检查正则匹配
        if (matchesRegex(url, rule.getRegex())) {
            if (!matchesExclude(url, rule.getExclude())) {
                return true; // 拦截
            }
        }
    }
    
    return false; // 放行
}
```

### 5.3 AI 服务调用（AiAdDetectionService）

```java
public class AiAdDetectionService {
    public static void detectAd(AdDetectionRequest request, 
                               Consumer<AdDetectionResult> callback) {
        // 1. 构建 AI 提示词
        String prompt = buildPrompt(request);
        
        // 2. 调用 AI API（异步）
        ApiConfig.get().getAiApi().chat(prompt, new Callback<String>() {
            @Override
            public void onSuccess(String response) {
                // 3. 解析 AI 返回的 JSON
                AdDetectionResult result = parseAiResponse(response);
                callback.accept(result);
            }
            
            @Override
            public void onError(Throwable e) {
                callback.accept(AdDetectionResult.failure());
            }
        });
    }
    
    private static String buildPrompt(AdDetectionRequest req) {
        return String.format(
            "分析以下视频播放信息，识别可能的广告 URL 规则：\n" +
            "- 当前 URL: %s\n" +
            "- 视频标题: %s\n" +
            "- 站点名称: %s\n" +
            "- 历史 URL: %s\n\n" +
            "请返回 JSON 格式的去广规则，包含：\n" +
            "- ruleName: 规则名称\n" +
            "- hosts: 域名列表（数组）\n" +
            "- regex: 正则列表（数组）\n" +
            "- exclude: 排除规则（数组，可为空）\n" +
            "- reason: 分析理由（可选）",
            req.getVideoUrl(),
            req.getVideoTitle(),
            req.getSiteName(),
            String.join(", ", req.getUrlHistory())
        );
    }
}
```

---

## 6. 数据持久化

### 6.1 存储方案

**文件路径**: `{data}/user_ad_rules.json`

**格式**:
```json
{
  "version": 1,
  "rules": [
    {
      "id": "uuid-1234",
      "name": "AI 规则：某某视频站",
      "hosts": ["adv.example.com", "*.ad.provider.net"],
      "regex": [".*\\.mp4\\?ad=.*", ".*/preroll/.*"],
      "exclude": [".*trailer.*"],
      "enabled": true,
      "createdAt": 1720569600000,
      "source": "ai"
    },
    {
      "id": "uuid-5678",
      "name": "我的自定义规则",
      "hosts": ["bad-ad-server.com"],
      "regex": [],
      "exclude": [],
      "enabled": false,
      "createdAt": 1720569700000,
      "source": "manual"
    }
  ]
}
```

### 6.2 UserAdRuleStore 实现

```java
public class UserAdRuleStore {
    private static final String FILE_NAME = "user_ad_rules.json";
    
    // 加载所有规则
    public static List<UserAdRule> loadAll() {
        File file = new File(FileUtil.getCacheDir(), FILE_NAME);
        if (!file.exists()) return new ArrayList<>();
        
        String json = FileUtil.read(file);
        // 解析 JSON 返回列表
    }
    
    // 保存单条规则
    public static void save(UserAdRule rule) {
        List<UserAdRule> rules = loadAll();
        
        // 更新或新增
        int index = findIndexById(rules, rule.getId());
        if (index >= 0) {
            rules.set(index, rule);
        } else {
            rule.setId(UUID.randomUUID().toString());
            rule.setCreatedAt(System.currentTimeMillis());
            rules.add(rule);
        }
        
        saveAll(rules);
        
        // 刷新全局规则
        RuleConfig.refreshUserRules();
    }
    
    // 删除规则
    public static void delete(String id) {
        List<UserAdRule> rules = loadAll();
        rules.removeIf(r -> r.getId().equals(id));
        saveAll(rules);
        RuleConfig.refreshUserRules();
    }
    
    private static void saveAll(List<UserAdRule> rules) {
        File file = new File(FileUtil.getCacheDir(), FILE_NAME);
        String json = new Gson().toJson(Map.of("version", 1, "rules", rules));
        FileUtil.write(file, json);
    }
}
```

---

## 7. 配置开关设计

### 7.1 Setting 类新增常量

```java
public class Setting {
    // AI 功能总开关
    private static final String KEY_AI_ENABLED = "ai_enabled";
    
    // 去广功能开关
    private static final String KEY_AD_REMOVAL_ENABLED = "ad_removal_enabled";
    
    public static boolean isAiEnabled() {
        return Prefers.getBoolean(KEY_AI_ENABLED, false);
    }
    
    public static void setAiEnabled(boolean enabled) {
        Prefers.put(KEY_AI_ENABLED, enabled);
    }
    
    public static boolean isAdRemovalEnabled() {
        return Prefers.getBoolean(KEY_AD_REMOVAL_ENABLED, false);
    }
    
    public static void setAdRemovalEnabled(boolean enabled) {
        Prefers.put(KEY_AD_REMOVAL_ENABLED, enabled);
    }
}
```

### 7.2 备份/恢复支持（Backup 类）

在 `Backup.java` 中新增字段：

```java
@SerializedName("ai")
private Integer ai;  // AI 功能开关

@SerializedName("adRemoval")
private Integer adRemoval;  // 去广功能开关

// restore 方法中添加：
if (getAi() != null) Setting.setAiEnabled(getAi() == 1);
if (getAdRemoval() != null) Setting.setAdRemovalEnabled(getAdRemoval() == 1);

// 备份方法中添加：
backup.setAi(Setting.isAiEnabled() ? 1 : 0);
backup.setAdRemoval(Setting.isAdRemovalEnabled() ? 1 : 0);
```

---

## 8. 字符串资源（strings.xml）

```xml
<!-- AI 去广功能 -->
<string name="ad_feedback">有广告</string>
<string name="ad_feedback_success">已添加去广规则：%s</string>
<string name="ad_feedback_failure">AI 分析失败，请稍后重试</string>

<!-- 规则管理 -->
<string name="ad_rule_manage_title">去广规则管理</string>
<string name="ad_rule_manage_desc">查看和编辑 AI 生成的去广规则</string>
<string name="ad_rule_add">新增规则</string>
<string name="ad_rule_default">默认规则</string>
<string name="ad_rule_ai">AI 规则</string>
<string name="ad_rule_manual">自定义规则</string>

<!-- 规则编辑 -->
<string name="ad_rule_edit_title">编辑去广规则</string>
<string name="ad_rule_field_name">规则名称</string>
<string name="ad_rule_field_hosts">域名列表（每行一个，支持通配符）</string>
<string name="ad_rule_field_hosts_hint">支持 *.ad.com 或 ad.*</string>
<string name="ad_rule_field_regex">正则表达式（每行一个）</string>
<string name="ad_rule_field_regex_hint">匹配 URL 中可能包含广告的部分</string>
<string name="ad_rule_field_exclude">排除规则（可选，避免误杀）</string>
<string name="ad_rule_field_exclude_hint">即使匹配上面规则也不过滤</string>

<!-- 操作按钮 -->
<string name="ad_rule_enable">启用</string>
<string name="ad_rule_disable">禁用</string>
<string name="ad_rule_edit">编辑</string>
<string name="ad_rule_delete">删除</string>
<string name="ad_rule_delete_confirm">确认删除规则"%s"？</string>
```

---

## 9. 实现步骤（开发计划）

### Phase 1: 数据模型层（已完成 ✅）
- [x] `UserAdRule` 数据模型
- [x] `AdDetectionRequest` / `AdDetectionResult`
- [x] `UserAdRuleStore` 持久化

### Phase 2: Service 层（已完成 ✅）
- [x] `AiAdDetectionService` 实现
- [x] `RuleConfig` 合并逻辑
- [x] `Setting` 开关配置

### Phase 3: UI 层（已完成 ✅）
- [x] `VideoActivity` 新增"有广告"按钮（mobile + leanback）
- [x] `AdRuleManageDialog` 规则管理弹窗
- [x] `AdRuleEditDialog` 规则编辑弹窗
- [x] `AdRuleAdapter` 列表适配器

### Phase 4: 资源与测试（已完成 ✅）
- [x] `strings.xml` 字符串资源
- [x] 布局 XML 文件（mobile + leanback 各 6 个）
- [x] 编译验证（mobile + leanback）

### Phase 5: 集成与优化（待定）
- [ ] AI 提示词优化（提升识别准确率）
- [ ] 规则导入/导出功能
- [ ] 规则测试工具（输入 URL 测试匹配结果）
- [ ] 性能优化（规则缓存、正则预编译）

---

## 10. 风险与注意事项

### 10.1 AI 识别准确性
- **问题**: AI 可能误判，生成过于宽泛或错误的规则
- **缓解**: 
  - 用户可手动编辑/禁用规则
  - 添加排除规则避免误杀
  - 显示 AI 分析理由供用户判断

### 10.2 性能影响
- **问题**: 大量规则可能影响 URL 过滤性能
- **缓解**:
  - 限制用户规则数量（如 50 条）
  - 正则表达式预编译
  - 规则优先级排序（高频匹配规则前置）

### 10.3 规则冲突
- **问题**: 用户规则与默认规则可能冲突
- **缓解**:
  - 用户规则优先于默认规则
  - 排除规则可覆盖任何匹配规则
  - UI 提示规则启用状态

### 10.4 隐私安全
- **问题**: 视频 URL 提交到 AI 可能包含敏感信息
- **缓解**:
  - 仅提交 URL、标题、站点名称（不含视频内容）
  - 用户明确点击"有广告"才触发
  - 设置中可关闭 AI 功能

---

## 11. 未来扩展

### 11.1 规则分享
- 支持导出规则为 JSON 文件
- 导入社区分享的规则包
- 规则订阅（远程规则库定期更新）

### 11.2 智能学习
- 根据用户操作（跳过片段）自动学习广告模式
- 统计规则有效性（拦截次数、误拦率）
- 自动禁用低效规则

### 11.3 高级过滤
- 视频时长过滤（如：< 5 秒的片段）
- 音频特征检测（广告音乐/静音片段）
- 视频帧分析（黑屏、固定画面）

---

## 12. 参考资料

- 现有去广规则实现：`RuleConfig.java`
- 播放器控制层：`VideoActivity.java`
- 设置管理：`SettingEnhanceActivity.java` / `SettingEnhanceFragment.java`
- AI 接口调用：`ApiConfig.java` → `AiApi.java`
- 数据持久化：`FileUtil.java`、`Backup.java`

---

**文档版本**: v1.0  
**创建日期**: 2026-07-10  
**状态**: 实现完成，待集成测试
