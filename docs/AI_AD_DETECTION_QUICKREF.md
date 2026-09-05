# AI 智能去广告功能 - 快速参考

## 功能概述

用户在播放器点击"有广告"按钮 → AI 分析视频信息 → 自动生成去广规则 → 用户可在设置中管理规则

## 核心文件清单

### 数据模型层（`app/src/main/java/.../bean/`）
- `UserAdRule.java` - 用户自定义规则模型
- `AdDetectionRequest.java` - AI 请求数据
- `AdDetectionResult.java` - AI 响应数据

### 服务层（`app/src/main/java/.../`）
- `service/AiAdDetectionService.java` - AI 去广检测服务
- `api/config/UserAdRuleStore.java` - 用户规则持久化
- `api/config/RuleConfig.java` - 规则合并逻辑（已修改）
- `setting/Setting.java` - 配置开关（已修改）
- `bean/Backup.java` - 备份支持（已修改）

### UI 层 - Mobile（`app/src/mobile/`）
- `java/.../ui/activity/VideoActivity.java` - 播放器（已修改）
- `java/.../ui/fragment/SettingEnhanceFragment.java` - 设置页（已修改）
- `java/.../ui/dialog/AdRuleManageDialog.java` - 规则管理弹窗
- `java/.../ui/dialog/AdRuleEditDialog.java` - 规则编辑弹窗
- `java/.../ui/adapter/AdRuleAdapter.java` - 规则列表适配器
- `res/layout/fragment_setting_enhance.xml` - 设置页布局（已修改）
- `res/layout/view_control_vod_action.xml` - 播放器控制栏（已修改）
- `res/layout/dialog_ad_rule_manage.xml` - 管理弹窗布局
- `res/layout/dialog_ad_rule_edit.xml` - 编辑弹窗布局
- `res/layout/adapter_ad_rule.xml` - 规则列表项布局

### UI 层 - Leanback（`app/src/leanback/`）
- `java/.../ui/activity/VideoActivity.java` - 播放器（已修改）
- `java/.../ui/activity/SettingEnhanceActivity.java` - 设置页（已修改）
- `java/.../ui/dialog/AdRuleManageDialog.java` - 规则管理弹窗
- `java/.../ui/dialog/AdRuleEditDialog.java` - 规则编辑弹窗
- `java/.../ui/adapter/AdRuleAdapter.java` - 规则列表适配器
- `res/layout/activity_setting_enhance.xml` - 设置页布局（已修改）
- `res/layout/view_control_vod_action.xml` - 播放器控制栏（已修改）
- `res/layout/dialog_ad_rule_manage.xml` - 管理弹窗布局
- `res/layout/dialog_ad_rule_edit.xml` - 编辑弹窗布局
- `res/layout/adapter_ad_rule.xml` - 规则列表项布局

### 资源文件（`app/src/main/res/`）
- `values/strings.xml` - 字符串资源（已修改）

## 数据结构

### UserAdRule
```java
{
  id: String,              // UUID
  name: String,            // 规则名称
  hosts: List<String>,     // 域名列表（支持通配符）
  regex: List<String>,     // 正则列表
  exclude: List<String>,   // 排除规则
  enabled: boolean,        // 是否启用
  createdAt: long,         // 创建时间戳
  source: String           // "ai" | "manual"
}
```

### 存储文件
**路径**: `{data}/user_ad_rules.json`

```json
{
  "version": 1,
  "rules": [ /* UserAdRule 数组 */ ]
}
```

## 关键 API

### AiAdDetectionService
```java
// 提交 AI 分析
public static void detectAd(
    AdDetectionRequest request, 
    Consumer<AdDetectionResult> callback
)
```

### UserAdRuleStore
```java
// 加载所有规则
public static List<UserAdRule> loadAll()

// 保存规则（新增或更新）
public static void save(UserAdRule rule)

// 删除规则
public static void delete(String id)
```

### RuleConfig
```java
// 获取所有启用的规则（默认 + 用户）
public static List<Rule> getAllEnabledRules()

// 刷新用户规则
public static void refreshUserRules()
```

### Setting
```java
// AI 功能开关
public static boolean isAiEnabled()
public static void setAiEnabled(boolean enabled)

// 去广功能开关
public static boolean isAdRemovalEnabled()
public static void setAdRemovalEnabled(boolean enabled)
```

## UI 交互流程

### 播放器反馈流程
```
用户点击"有广告"按钮
  ↓
收集当前播放信息（URL、标题、站点名）
  ↓
调用 AiAdDetectionService.detectAd()
  ↓
AI 返回规则（hosts、regex、exclude）
  ↓
保存到 UserAdRuleStore
  ↓
刷新 RuleConfig
  ↓
显示成功提示
```

### 规则管理流程
```
设置页点击"去广规则管理"
  ↓
打开 AdRuleManageDialog
  ↓
加载规则列表（默认 + 用户）
  ↓
用户操作：
  - 查看规则详情
  - 编辑规则（打开 AdRuleEditDialog）
  - 启用/禁用规则
  - 删除规则
  - 新增规则
  ↓
保存修改到 UserAdRuleStore
  ↓
刷新 RuleConfig
```

## 按钮显示逻辑

播放器"有广告"按钮显示条件：
```java
boolean show = Setting.isAiEnabled() && Setting.isAdRemovalEnabled();
```

两个开关**必须同时开启**才显示按钮。

## 规则优先级

1. 用户启用的规则（优先）
2. 默认规则（内置）

规则匹配顺序：
1. 检查 hosts 匹配
2. 检查 regex 匹配
3. 检查 exclude 规则（排除）

**任何规则的 exclude 字段可以覆盖其他规则的匹配结果。**

## 编译验证

已完成编译验证：
- ✅ Mobile Debug
- ✅ Leanback Debug

## 待办事项

### 必选
- [ ] AI 提示词优化（提升识别准确率）
- [ ] 规则测试工具（测试 URL 匹配）

### 可选
- [ ] 规则导入/导出功能
- [ ] 规则订阅（远程规则库）
- [ ] 智能学习（根据用户行为优化）
- [ ] 高级过滤（视频时长、音频特征）

## 风险点

1. **AI 识别准确性** - 缓解：用户可手动编辑/禁用规则
2. **性能影响** - 缓解：限制规则数量、正则预编译
3. **规则冲突** - 缓解：用户规则优先、排除规则覆盖
4. **隐私安全** - 缓解：仅提交必要信息、用户主动触发

## 字符串资源速查

| Key | 值 |
|-----|---|
| `ad_feedback` | 有广告 |
| `ad_rule_manage_title` | 去广规则管理 |
| `ad_rule_edit_title` | 编辑去广规则 |
| `ad_rule_field_name` | 规则名称 |
| `ad_rule_field_hosts` | 域名列表（每行一个，支持通配符） |
| `ad_rule_field_regex` | 正则表达式（每行一个） |
| `ad_rule_field_exclude` | 排除规则（可选，避免误杀） |
| `ad_rule_enable` | 启用 |
| `ad_rule_disable` | 禁用 |
| `ad_rule_edit` | 编辑 |
| `ad_rule_delete` | 删除 |

---

**快速参考版本**: v1.0  
**对应设计文档**: `AI_AD_DETECTION_DESIGN.md`  
**创建日期**: 2026-07-10
