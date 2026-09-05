# TmdbDetailActivity 重构路线图

## 📊 现状分析

**文件**: `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`

| 指标 | 当前值 | 目标值 | 优先级 |
|------|--------|--------|--------|
| 代码行数 | 9,738 行 | <2,000 行 | P0 |
| 方法数 | 788 个 | <150 个 | P0 |
| 字段数 | 151 个 | <50 个 | P1 |
| 圈复杂度最高方法 | 37 分支 | <10 分支 | P1 |
| 测试文件行数 | 1,883 行 | 保持/增加 | P0 |

**质量健康得分**: 当前未单独测量,整体项目 6499/10000

---

## 🎯 重构目标

### 主目标
将 `TmdbDetailActivity` 拆分为 **5 个职责明确的 Delegate**:
1. `TmdbDetailPlayerDelegate` - 播放器管理 (~2000 行)
2. `TmdbDetailEpisodeDelegate` - 剧集展示 (~1500 行)
3. `TmdbDetailThemeDelegate` - UI 主题/布局 (~1200 行)
4. `TmdbDetailDataDelegate` - TMDB API 交互 (~800 行)
5. `TmdbDetailActivity` (精简后) - 生命周期/导航 (~500 行)

### 子目标
- ✅ 所有测试持续通过 (每次提交前运行)
- ✅ 不破坏现有功能 (包括进行中的 Task 3 三态跳过片头)
- ✅ 保持向后兼容 (公共 API 不变)

---

## 📅 渐进式重构计划 (8 周)

### Week 1: 基础设施 (已完成 ✅)
- [x] 创建 `delegate/` 包
- [x] 定义 `TmdbDetailPlayerHost` 接口
- [x] 创建 `TmdbDetailPlayerDelegate` 骨架
- [x] 编写重构路线图文档
- [ ] 提交 commit: `refactor: add PlayerDelegate framework (inactive)`

**可交付**: 框架代码 + 路线图文档,不影响现有功能

---

### Week 2: 播放器 Delegate - Phase 1 (目标: 迁移 50 个方法)

#### 迁移清单 (按依赖顺序)

**优先级 P0 - 无状态工具方法** (预计 2 小时)
- [ ] `setMarginsDp/Px` (4 行,被 89 处调用)
- [ ] `setHeightDp` (4 行,被 52 处调用)
- [ ] `setWidthPx/Match` (6 行,被 41 处调用)
- [ ] `setPaddingDp` (2 行,被 37 处调用)

**优先级 P0 - 播放器控制回调** (预计 4 小时)
- [ ] `toggleInlinePlayback()` (8 行)
- [ ] `checkInlinePrev()` (23 行)
- [ ] `checkInlineNext()` (23 行)
- [ ] `onInlineBack()` (12 行)

**优先级 P1 - 手势处理** (预计 3 小时)
- [ ] `onSeeking()` (11 行)
- [ ] `onSeekEnd()` (5 行)
- [ ] `onVolume()` (8 行)
- [ ] `onBright()` (8 行)
- [ ] `onSingleTap()` (5 行)
- [ ] `onDoubleTap()` (12 行)

**验证步骤**:
```bash
# 每迁移 10 个方法后运行
./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "*TmdbDetailActivityLayoutTest*"
```

**退出标准**: 
- 测试通过
- `TmdbDetailActivity` 减少 200-300 行
- Delegate 可独立单元测试

---

### Week 3: 播放器 Delegate - Phase 2 (目标: 迁移 80 个方法)

**优先级 P0 - 播放器 UI 初始化** (预计 6 小时)
- [ ] `initFusionPlayer()` (203 行) - **最大单块**
- [ ] `setupMobileInlineControl()` (42 行)
- [ ] `setupInlineFocusNavigation()` (35 行)
- [ ] `setupInlineControlFocus()` (29 行)
- [ ] `inflateMobileInlineControl()` (10 行)

**优先级 P1 - 播放器状态管理** (预计 4 小时)
- [ ] `hideInlineControls()` (15 行)
- [ ] `showInlineControls()` (18 行)
- [ ] `updateInlineButtons()` (27 行)
- [ ] `toggleInlineFullscreen()` (45 行)
- [ ] `enterInlineFullscreen()` (38 行)
- [ ] `exitInlineFullscreen()` (42 行)

**依赖处理**:
- 将 `inlinePlayerUi`, `inlineControlController` 等字段迁移到 Delegate
- 通过 `host.getBinding()` 访问 ViewBinding

**退出标准**: 
- `TmdbDetailActivity` 减少至 ~7500 行
- 播放器初始化逻辑完全在 Delegate 中

---

### Week 4: 播放器 Delegate - Phase 3 (完成播放器抽取)

**优先级 P0 - 播放器功能对话框** (预计 5 小时)
- [ ] `showInlineQuality()` (34 行)
- [ ] `showInlineDanmaku()` (18 行)
- [ ] `showInlinePlayerInfo()` (12 行)
- [ ] `showInlineSubtitle()` (28 行)
- [ ] `showInlineTrack()` (22 行)

**优先级 P1 - 播放器设置** (预计 4 小时)
- [ ] `cycleInlineParse()` (16 行)
- [ ] `changeInlineSpeed()` (8 行)
- [ ] `resetInlineSpeed()` (5 行)
- [ ] `cycleInlineScale()` (9 行)
- [ ] `toggleInlineDecode()` (7 行)
- [ ] `toggleInlineRepeat()` (6 行)

**优先级 P2 - 播放器高级功能** (预计 3 小时)
- [ ] `onInlineLut()` (LUT 滤镜导入,23 行)
- [ ] `setInlineOpeningFromPosition()` (片头设置,8 行)
- [ ] `resetInlineOpening()` (5 行)
- [ ] `setInlineEndingFromPosition()` (片尾设置,8 行)
- [ ] `resetInlineEnding()` (5 行)

**退出标准**: 
- `TmdbDetailPlayerDelegate` 功能完整
- 原 Activity 播放器相关代码减少 ~2000 行
- 所有播放器测试通过

---

### Week 5: 剧集 Delegate (目标: 迁移 60 个方法)

**创建**: `TmdbDetailEpisodeDelegate`

**优先级 P0 - 剧集列表展示** (预计 6 小时)
- [ ] `showInlineEpisodes()` (42 行)
- [ ] `showNativeEnhancedInlineEpisodes()` (181 行) - **第二大块**
- [ ] `hideInlineEpisodes()` (12 行)
- [ ] `updateEpisodeList()` (35 行)

**优先级 P1 - 剧集适配器管理** (预计 4 小时)
- [ ] `setupEpisodeAdapter()` (28 行)
- [ ] `onEpisodeClick()` (18 行)
- [ ] `onEpisodeLongClick()` (15 行)
- [ ] `loadSeasonEpisodes()` (45 行)

**优先级 P1 - 季度/集数逻辑** (预计 5 小时)
- [ ] `switchSeason()` (32 行)
- [ ] `loadNextSeasonPage()` (28 行)
- [ ] `getEpisodeFromCache()` (12 行)
- [ ] `updateSeasonSelector()` (22 行)

**依赖字段迁移**:
- `episodeAdapter`
- `tmdbEpisodes`
- `seasonNumbers`
- `selectedEpisode`

**退出标准**: 
- Activity 减少至 ~6000 行
- 剧集展示逻辑完全独立测试

---

### Week 6: 主题 Delegate (目标: 迁移 50 个方法)

**创建**: `TmdbDetailThemeDelegate`

**优先级 P0 - 主题切换** (预计 5 小时)
- [ ] `cycleThemeMode()` (6 行)
- [ ] `applyDetailTheme()` (58 行)
- [ ] `updateDetailThemeButtonVisibility()` (14 行)
- [ ] `updateThemeModeButtonLabels()` (5 行)

**优先级 P1 - 布局模板应用** (预计 6 小时)
- [ ] `applyDetailTemplate()` (4 行)
- [ ] `applyDefaultDetailTemplate()` (34 行)
- [ ] `applyCinemaDetailTemplate()` (49 行)
- [ ] `applyTemplateCardChrome()` (15 行)

**优先级 P1 - 颜色/样式工具** (预计 3 小时)
- [ ] `tintInlineControl()` (4 行,递归)
- [ ] `tintInlineDisplay()` (7 行)
- [ ] `tintInlineLoading()` (4 行)
- [ ] `cinemaBackdropShade()` (15 行)
- [ ] `styleSourceValue()` (8 行)

**依赖字段迁移**:
- `lightTheme` (Boolean)
- 主题相关 UI 状态

**退出标准**: 
- Activity 减少至 ~4500 行
- 主题切换逻辑可独立测试

---

### Week 7: 数据 Delegate + 清理 (目标: 迁移 40 个方法)

**创建**: `TmdbDetailDataDelegate`

**优先级 P0 - TMDB API 交互** (预计 6 小时)
- [ ] `loadTmdbDetail()` (42 行)
- [ ] `loadTmdbCast()` (28 行)
- [ ] `loadTmdbPhotos()` (32 行)
- [ ] `searchTmdb()` (35 行)
- [ ] `rematchTmdb()` (18 行)

**优先级 P1 - 数据缓存管理** (预计 4 小时)
- [ ] `cacheTmdbMatch()` (12 行)
- [ ] `loadCachedMatch()` (15 行)
- [ ] `clearTmdbCache()` (8 行)

**优先级 P2 - 清理死代码** (预计 4 小时)
- [ ] 删除未使用的 private 方法 (运行 tokensave dead-code)
- [ ] 合并重复逻辑

**退出标准**: 
- Activity 减少至 ~3000 行
- 数据加载逻辑独立可测

---

### Week 8: 最终整合 + 文档 (目标: 完成精简)

**优先级 P0 - Activity 精简** (预计 6 小时)
- [ ] 保留核心生命周期方法 (`onCreate`, `onPause`, `onDestroy`)
- [ ] 保留导航/Intent 处理
- [ ] 保留 `start*` 工厂方法
- [ ] Delegate 协调逻辑

**优先级 P0 - 测试补全** (预计 6 小时)
- [ ] 为每个 Delegate 编写单元测试
- [ ] 更新 `TmdbDetailActivityLayoutTest` (适配 Delegate 架构)
- [ ] 集成测试覆盖关键路径

**优先级 P1 - 文档更新** (预计 2 小时)
- [ ] 更新 `DETAIL_PAGE_MODES.md`
- [ ] 编写 Delegate 使用指南
- [ ] 记录架构决策 (ADR)

**最终目标**:
```
TmdbDetailActivity.java:        ~500 行 (仅协调逻辑)
TmdbDetailPlayerDelegate.java:  ~800 行
TmdbDetailEpisodeDelegate.java: ~600 行
TmdbDetailThemeDelegate.java:   ~500 行
TmdbDetailDataDelegate.java:    ~400 行
测试代码:                        ~2500 行 (增加)
总计:                            ~3300 行 (vs 原 9738 行)
```

---

## 🛠️ 每周工作流

### 周一: 计划
```bash
# 1. 拉取最新代码
git checkout dev
git pull origin dev

# 2. 创建本周分支
git checkout -b refactor/tmdb-detail-week-N

# 3. 确认本周迁移清单
```

### 周二-周四: 迁移
```bash
# 每迁移 5-10 个方法:
# 1. 编译检查
./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac

# 2. 运行测试
./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "*TmdbDetailActivityLayoutTest*"

# 3. 提交
git add .
git commit -m "refactor: migrate ${METHOD_GROUP} to ${DELEGATE_NAME} (${N}/788 methods)"
```

### 周五: 合并
```bash
# 1. 最终测试
./gradlew :app:testLeanbackArm64_v8aDebugUnitTest

# 2. 合并到 dev
git checkout dev
git merge --no-ff refactor/tmdb-detail-week-N

# 3. 推送
git push origin dev

# 4. 更新路线图进度
```

---

## 🚨 风险与缓解

### 风险 1: 与进行中功能冲突
**症状**: Task 3 (三态跳过片头) 修改了同一区域代码  
**缓解**: 
- 每周一与开发者同步进度
- 优先迁移未被修改的区域
- 发生冲突时,暂停重构,协助完成 Task 3

### 风险 2: 测试失败
**症状**: 迁移后测试报错  
**缓解**:
- 小步提交,每次 5-10 个方法
- 失败时立即 `git revert`
- 分析失败原因,调整迁移顺序

### 风险 3: 性能退化
**症状**: Delegate 调用开销导致卡顿  
**缓解**:
- 避免在热路径上引入额外间接调用
- 性能敏感方法(如 `onSeeking`) 保持 inline
- 重构后运行性能测试

### 风险 4: 重构疲劳
**症状**: 8 周持续重构导致团队倦怠  
**缓解**:
- 每 2 周休息 1 周,做新功能
- 重构进度可视化 (进度条/图表)
- 庆祝里程碑 (每完成 1 个 Delegate)

---

## 📈 成功指标

### 代码质量
- [ ] 类行数: 9738 → <2000 (-79%)
- [ ] 方法数: 788 → <150 (-81%)
- [ ] 最大方法行数: 203 → <50 (-75%)
- [ ] 圈复杂度: 37 → <10 (-73%)

### 测试覆盖
- [ ] 单元测试行数: 1883 → >2500 (+33%)
- [ ] Delegate 测试覆盖率: 0% → >70%
- [ ] 所有现有测试持续通过

### 维护性
- [ ] 新功能开发速度提升 2-3 倍 (主观评估)
- [ ] Bug 修复时间减少 50% (追踪 issue 关闭时间)
- [ ] 代码审查时间减少 40% (PR 合并耗时)

---

## 📚 参考资料

### 内部文档
- `DETAIL_PAGE_MODES.md` - 三种详情页模式说明
- `memory/detail-mode-activity-map.md` - Activity/布局映射
- `memory/four-task-session.md` - 当前进行任务

### 设计模式
- **Delegate Pattern**: 职责委托
- **Host-Guest Pattern**: 接口隔离
- **Facade Pattern**: 简化复杂子系统

### 工具
- **tokensave**: `tokensave_dead_code`, `tokensave_complexity`
- **Android Studio**: Extract Method, Inline Method
- **Git**: Interactive Rebase (压缩提交)

---

## ✅ 检查清单 (每周复查)

- [ ] 本周迁移的方法都有单元测试
- [ ] 没有引入新的 TODO 注释
- [ ] 所有 Delegate 方法都有 Javadoc
- [ ] 更新了重构路线图进度
- [ ] 与团队同步了本周进展
- [ ] 下周迁移清单已明确

---

**文档版本**: v1.0  
**创建日期**: 2026-07-09  
**最后更新**: 2026-07-09  
**负责人**: Refactoring Team  
**状态**: Week 1 完成 ✅,Week 2 待开始

---

## 🎯 快速启动 (下周一)

```bash
# 1. 切换到重构分支
git checkout refactor/tmdb-detail-split-player

# 2. 查看 Week 2 清单
cat docs/refactoring/TMDB_DETAIL_ACTIVITY_REFACTORING_ROADMAP.md | grep -A 20 "Week 2"

# 3. 开始迁移第一个方法
# 编辑 TmdbDetailActivity.java + TmdbDetailPlayerDelegate.java

# 4. 验证
./gradlew :app:testLeanbackArm64_v8aDebugUnitTest

# 5. 提交
git commit -m "refactor: migrate setMarginsDp to PlayerDelegate (1/788)"
```

**下周见! 💪**
