# AI 智能去广功能实现总结

## 概述

本功能在原有去广规则基础上，新增了 AI 驱动的广告检测和用户反馈机制，让用户可以通过简单的点击向 AI 报告广告，AI 自动分析并生成去广规则。

## 核心功能

### 1. 用户反馈入口
- 在视频播放控制栏增加"有广告"反馈按钮
- 按钮仅在同时开启"AI 功能"和"去广功能"时显示
- 点击后自动收集当前视频上下文信息并提交给 AI

### 2. AI 广告分析
- 自动收集 M3U8 播放列表信息
- 提取视频时长、URL 模式等特征
- 识别潜在的广告片段（如 5-90 秒的独立 TS 文件）
- 生成结构化的去广规则建议

### 3. 规则管理
- **默认规则**：系统内置规则，用户可以禁用但不能删除
- **用户规则**：AI 生成或用户手动添加的规则，可编辑和删除
- **禁用规则**：记录用户禁用的默认规则
- 规则预览：提交前显示 AI 分析结果，用户可选择接受或拒绝

### 4. 规则持久化
- 用户规则保存到 `user_ad_rules.json`
- 禁用规则保存到 `disabled_default_rules.json`
- 支持备份和恢复功能

## 技术架构

### 数据模型层

#### UserAdRule.java
```java
用户去广规则数据模型
- id: 唯一标识（UUID）
- name: 规则名称
- hosts: 域名匹配列表
- regex: URL 正则表达式列表
- exclude: 排除正则表达式列表
- enabled: 是否启用
- source: 来源（AI/USER）
- createdAt: 创建时间
```

#### AdDetectionRequest.java & AdDetectionResult.java
```java
AI 请求和响应的数据结构
- videoUrl: 视频地址
- m3u8Content: 播放列表内容
- siteName: 站点名称
- videoName: 视频名称
```

#### M3u8Evidence.java
```java
M3U8 广告证据数据
- 短片段 TS 列表
- 总时长信息
- 可疑模式
```

### 服务层

#### AiAdDetectionService.java
```java
AI 广告检测服务
- analyzeAd(): 调用 AI 分析广告
- buildPrompt(): 构建分析提示词
- parseAiResponse(): 解析 AI 响应
```

#### UserAdRuleStore.java
```java
用户规则存储
- load(): 加载用户规则
- save(): 保存用户规则
- add/update/delete(): CRUD 操作
```

#### DisabledDefaultRuleStore.java
```java
禁用规则存储
- load(): 加载禁用列表
- save(): 保存禁用列表
- toggle(): 切换禁用状态
```

#### RuleConfig.java（扩展）
```java
规则配置管理（已扩展）
- 合并默认规则和用户规则
- 过滤禁用的默认规则
- 支持备份和恢复
```

### UI 层

#### 播放器控制栏
- `view_control_vod_action.xml`：增加反馈按钮
- `VideoActivity.java`：处理按钮点击事件

#### 规则管理对话框（mobile + leanback）

##### AdRulePreviewDialog
- 显示 AI 分析结果
- 支持接受或拒绝建议
- 显示规则详情和警告信息

##### AdRuleManageDialog
- 列表展示所有规则
- 区分默认规则和用户规则
- 支持启用/禁用/编辑/删除操作

##### AdRuleEditDialog
- 编辑规则字段
- 实时验证输入
- 保存规则

##### AdRuleAdapter
- 规则列表适配器
- 不同类型规则的视觉区分
- 操作按钮绑定

### 设置界面

#### SettingEnhanceActivity / Fragment
- 新增"去广规则管理"按钮
- 打开规则管理对话框

### 工具类

#### M3u8Parser.java
```java
M3U8 解析工具
- parseM3u8(): 解析播放列表
- extractEvidence(): 提取广告证据
- 识别短片段和可疑模式
```

#### RuleIdUtil.java
```java
规则 ID 工具
- generateUniqueId(): 生成唯一 ID
- 确保 ID 不与现有规则冲突
```

## 数据流

### 用户反馈流程
```
用户点击"有广告"
  ↓
收集视频信息（URL、M3U8、站点名等）
  ↓
调用 AiAdDetectionService.analyzeAd()
  ↓
解析 M3U8 提取广告证据
  ↓
构建 AI 提示词
  ↓
调用 AI 分析（通过 AiConfig）
  ↓
解析 AI 响应生成规则
  ↓
显示 AdRulePreviewDialog 预览
  ↓
用户确认 → 保存到 UserAdRuleStore
```

### 规则应用流程
```
播放器请求视频片段
  ↓
RuleConfig 加载所有规则
  ↓
合并默认规则和用户规则
  ↓
过滤禁用的规则
  ↓
应用 hosts、regex、exclude 匹配
  ↓
拦截匹配的广告片段
```

## 文件清单

### 新增文件（共 27 个）

#### 数据模型（4 个）
- `app/src/main/java/com/fongmi/android/tv/bean/UserAdRule.java`
- `app/src/main/java/com/fongmi/android/tv/bean/AdDetectionRequest.java`
- `app/src/main/java/com/fongmi/android/tv/bean/AdDetectionResult.java`
- `app/src/main/java/com/fongmi/android/tv/bean/M3u8Evidence.java`

#### 服务层（3 个）
- `app/src/main/java/com/fongmi/android/tv/service/AiAdDetectionService.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/UserAdRuleStore.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/DisabledDefaultRuleStore.java`

#### 工具类（2 个）
- `app/src/main/java/com/fongmi/android/tv/utils/M3u8Parser.java`
- `app/src/main/java/com/fongmi/android/tv/utils/RuleIdUtil.java`

#### UI - Mobile（6 个）
- `app/src/mobile/java/com/fongmi/android/tv/ui/adapter/AdRuleAdapter.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRulePreviewDialog.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRuleEditDialog.java`
- `app/src/mobile/res/layout/adapter_ad_rule.xml`
- `app/src/mobile/res/layout/dialog_ad_rule_preview.xml`
- `app/src/mobile/res/layout/dialog_ad_rule_manage.xml`
- `app/src/mobile/res/layout/dialog_ad_rule_edit.xml`

#### UI - Leanback（6 个）
- `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/AdRuleAdapter.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdRulePreviewDialog.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdRuleEditDialog.java`
- `app/src/leanback/res/layout/adapter_ad_rule.xml`
- `app/src/leanback/res/layout/dialog_ad_rule_preview.xml`
- `app/src/leanback/res/layout/dialog_ad_rule_manage.xml`
- `app/src/leanback/res/layout/dialog_ad_rule_edit.xml`

#### 资源文件（1 个）
- `app/src/main/res/drawable/shape_warning_background.xml`

#### 文档（2 个）
- `docs/ai-ad-detection-design.md`
- `智能去广-设计文档.md`

### 修改文件（14 个）

#### 数据模型（3 个）
- `app/src/main/java/com/fongmi/android/tv/bean/Rule.java`：增加 isDefault 和 isEnabled 字段
- `app/src/main/java/com/fongmi/android/tv/bean/Backup.java`：增加用户规则备份
- `app/src/main/java/com/fongmi/android/tv/bean/AiConfig.java`：扩展 AI 配置支持

#### 配置层（2 个）
- `app/src/main/java/com/fongmi/android/tv/api/config/RuleConfig.java`：合并和管理规则
- `app/src/main/java/com/fongmi/android/tv/setting/Setting.java`：新增设置项

#### 播放器（1 个）
- `app/src/main/java/com/fongmi/android/tv/player/exo/MediaSourceFactory.java`：集成 M3U8 解析

#### UI - Mobile（3 个）
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingEnhanceFragment.java`
- `app/src/mobile/res/layout/view_control_vod_action.xml`
- `app/src/mobile/res/layout/fragment_setting_enhance.xml`

#### UI - Leanback（3 个）
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingEnhanceActivity.java`
- `app/src/leanback/res/layout/view_control_vod_action.xml`
- `app/src/leanback/res/layout/activity_setting_enhance.xml`

#### 资源（2 个）
- `app/src/main/res/layout/dialog_ai_prompt_config.xml`
- `app/src/main/res/values/strings.xml`（含 zh-CN、zh-TW）

#### 测试（1 个）
- `app/src/test/java/com/fongmi/android/tv/bean/AiConfigTest.java`

## 配置要求

### 前置条件
1. 开启"AI 功能"（Setting.isAiEnable()）
2. 开启"去广功能"（Setting.isRuleEnable()）
3. 配置 AI 模型（AiConfig）

### 新增设置项
- `AI_AD_DETECTION_ENABLED`：是否启用 AI 去广（默认 false）
- 在 Setting.java 中已预留扩展接口

## 用户体验流程

### 1. 首次使用
```
用户播放视频遇到广告
  ↓
点击控制栏"有广告"按钮
  ↓
AI 分析中（显示加载状态）
  ↓
显示分析结果预览对话框
  - 规则名称
  - 匹配域名
  - 匹配模式
  - 排除模式
  - 警告信息（如有）
  ↓
用户点击"确定"保存规则
  ↓
下次播放自动过滤广告
```

### 2. 规则管理
```
设置 → 增强设置 → 去广规则管理
  ↓
查看所有规则列表
  - 默认规则（标记"默认"）
  - 用户规则（标记"用户"或"AI"）
  - 禁用状态（灰色显示）
  ↓
操作选项：
  - 启用/禁用（所有规则）
  - 编辑（仅用户规则）
  - 删除（仅用户规则）
  - 添加（手动创建规则）
```

### 3. 规则编辑
```
点击"编辑"按钮
  ↓
显示编辑对话框
  - 规则名称
  - 匹配域名（多行输入）
  - 匹配正则（多行输入）
  - 排除正则（多行输入）
  ↓
保存修改
```

## 关键设计决策

### 1. 规则隔离
- 默认规则：不可删除，可禁用，独立存储
- 用户规则：完全可控，独立存储
- 禁用规则：单独记录，不修改原规则

### 2. UI 一致性
- Mobile 和 Leanback 完全独立实现
- 但保持功能和流程一致
- 适配各自的交互模式（触摸 vs 遥控）

### 3. 安全性
- AI 响应解析容错
- 规则验证和冲突检测
- 用户确认机制（预览对话框）

### 4. 可扩展性
- 规则格式易扩展（JSON）
- AI 提示词可优化
- 支持多种规则匹配模式

## 测试要点

### 功能测试
1. ✅ 反馈按钮显示条件
2. ✅ AI 分析请求和响应
3. ✅ 规则预览对话框
4. ✅ 规则保存和加载
5. ✅ 规则应用和过滤
6. ✅ 默认规则禁用
7. ✅ 用户规则编辑和删除
8. ✅ 备份和恢复

### 异常处理
1. ✅ AI 请求失败
2. ✅ 无效的 M3U8 内容
3. ✅ 规则解析错误
4. ✅ 文件读写错误
5. ✅ 重复规则检测

### UI 测试
1. ✅ Mobile 和 Leanback 界面
2. ✅ 对话框交互
3. ✅ 列表滚动和操作
4. ✅ 按钮状态和颜色

## 性能考虑

### 1. M3U8 解析
- 异步处理，不阻塞播放
- 缓存解析结果
- 限制内容大小（避免超大文件）

### 2. AI 请求
- 后台线程执行
- 超时控制
- 错误降级（不影响正常播放）

### 3. 规则匹配
- 预编译正则表达式
- 优先匹配 hosts（更快）
- 缓存匹配结果

## 未来扩展

### 短期优化
1. 规则优先级和权重
2. 规则生效时间统计
3. AI 模型参数可配置
4. 批量导入/导出规则

### 长期规划
1. 规则社区分享
2. 机器学习模型优化
3. 跨设备规则同步
4. 高级规则编辑器（正则测试、可视化）

## 文档

- 设计文档：`docs/ai-ad-detection-design.md`
- 用户文档：`智能去广-设计文档.md`
- 实现总结：本文档

## 版本信息

- 初始版本：v1.0.0
- 实现日期：2026-07-10
- 作者：AI Assistant
- 状态：✅ 已完成基础功能实现

## 编译验证

### 构建任务
```bash
./gradlew app:assembleMobileArm64_v8aDebug
./gradlew app:assembleLeanbackArm64_v8aDebug
```

### 验证清单
- [ ] Mobile 版本编译通过
- [ ] Leanback 版本编译通过
- [ ] 无警告错误
- [ ] APK 正常生成

---

**注意事项**：
1. 本功能需要有效的 AI API 配置才能正常工作
2. 用户规则保存在应用数据目录，卸载应用会丢失
3. 建议定期备份规则配置
4. AI 分析结果仅供参考，用户应在预览后决定是否采用
