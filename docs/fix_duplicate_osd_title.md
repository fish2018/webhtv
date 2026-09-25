# 修复：原生模式关闭 OSD 后控制栏显示重复标题

## 问题描述

**现象：** 影视原生模式（leanback VideoActivity）中，当用户关闭所有屏显（OSD）设置后，唤醒控制栏会出现**两套重复的标题、分辨率、时间**信息互相叠加错位。

**截图：** 左上角标题、分辨率、右上角时间都各显示了两遍。

## 根本原因

### 双系统冲突

原生模式（leanback VideoActivity）存在两套独立的标题显示系统：

1. **widget.top 系统**（`view_widget_vod.xml` 第 8-51 行）
   - 包含 `title`、`size`、`clock` 三个控件
   - 由 `VideoActivity.showTopInfo()` 控制显示

2. **OSD 系统**（`PlayerOsdController`）
   - 包含 `topLeft`（标题+分辨率）、`topRight`（时间）
   - 由 `PlayerOsdController.render()` 控制显示

### 触发条件

```
用户操作：关闭所有 OSD 设置（PlayerSetting.isOsdEnabled() = false）
↓
唤醒控制栏：showControl() → showTopInfo() + mOsd.setControlsVisible(true)
↓
showTopInfo() 原逻辑（第 3193-3201 行）：
    if (PlayerSetting.isOsdEnabled()) {
        mBinding.widget.top.setVisibility(View.GONE);  // OSD 开启时隐藏 widget
    } else {
        mBinding.widget.top.setVisibility(View.VISIBLE);  // OSD 关闭时显示 widget ❌
        mBinding.widget.size.setText(player().getSizeText());
    }
↓
PlayerOsdController.render() 新逻辑（第 168-186 行）：
    if (controlsVisible) {
        // 即使 !isOsdEnabled()，也强制显示 OSD 的 topLeft/topRight ✅
        setTopLeftForControls(player);
        setTopRightForControls();
        ...
    }
↓
结果：widget.top 和 OSD 的 topLeft/topRight 同时显示 ❌❌
```

### 设计冲突点

之前的修改（统一 OSD 系统）引入了新逻辑：

- **原设计：** OSD 关闭时，widget.top 负责显示标题
- **新需求：** 控制栏显示时，**强制**显示标题（无论 OSD 是否开启）
- **新实现：** `PlayerOsdController.render()` 在 `controlsVisible = true` 时强制调用 `setTopLeftForControls()` 和 `setTopRightForControls()`

但 `showTopInfo()` 的旧逻辑没有更新，仍然在 `!isOsdEnabled()` 时显示 `widget.top`，导致两套系统同时工作。

## 解决方案

### 修改 leanback VideoActivity.showTopInfo()

**文件：** `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`

**位置：** 第 3193-3197 行

**修改前：**
```java
private void showTopInfo() {
    // OSD 启用时，不显示 widget.top（避免与 OSD 的 topLeft/topRight 重影）
    if (PlayerSetting.isOsdEnabled()) {
        mBinding.widget.top.setVisibility(View.GONE);
    } else {
        mBinding.widget.top.setVisibility(View.VISIBLE);
        mBinding.widget.size.setText(player().getSizeText());
    }
}
```

**修改后：**
```java
private void showTopInfo() {
    // 控制栏显示时，统一由 OSD 显示标题（即使用户关闭了 OSD 设置）
    // 所以这里始终隐藏 widget.top，避免与 OSD 重复显示
    mBinding.widget.top.setVisibility(View.GONE);
}
```

### 为什么 mobile VideoActivity 不需要修改

mobile 版的 `VideoActivity` 虽然布局文件中也 include 了 `view_widget_vod`，但代码中**从未引用** `mBinding.widget.top`、`mBinding.widget.title`、`mBinding.widget.size`、`mBinding.widget.clock`。

mobile 版只使用了 widget 的：
- `error` - 播放错误提示
- `seek` - 拖动进度
- `speed` - 快进/快退动画
- `bright` - 亮度调节
- `volume` - 音量调节

所以 mobile 版的 `widget.top` 容器虽然存在于布局中，但从未被显示，不会产生重复标题问题。

## 架构说明

### 统一后的标题显示系统

修改后，所有播放模式（TV/手机原生/手机融合）使用**单一系统**显示标题：

| 场景 | 显示系统 | 控制逻辑 |
|------|---------|---------|
| **控制栏显示时** | ✅ OSD 系统（topLeft/topRight） | `PlayerOsdController.render()` 强制显示 |
| **控制栏隐藏时** | ✅ OSD 系统（按用户设置） | `PlayerOsdController.render()` 按设置显示 |
| ~~widget.top 系统~~ | ❌ 已废弃（始终隐藏） | `showTopInfo()` 强制 `GONE` |

### widget.top 的命运

`view_widget_vod.xml` 中的 `top` 容器（第 8-51 行）仍然保留在布局中，但：

- **leanback VideoActivity：** `showTopInfo()` 强制隐藏，永不显示
- **mobile VideoActivity：** 从未引用，永不显示
- **TmdbDetailActivity（融合模式）：** 不使用 `view_widget_vod` 布局

未来可以清理这部分布局代码，但保留不会影响功能，只会占用少量内存。

## 行为验证

### 测试场景 1：OSD 全部开启

1. **设置：** 播放器设置 → 屏幕显示 → 开启所有选项
2. **播放视频**
3. **唤醒控制栏**
4. **预期结果：** 左上角显示标题+分辨率，右上角显示时间（单份，不重复）

### 测试场景 2：OSD 全部关闭（问题场景）

1. **设置：** 播放器设置 → 屏幕显示 → 关闭所有选项
2. **播放视频**
3. **唤醒控制栏**
4. **预期结果：** 左上角显示标题+分辨率，右上角显示时间（单份，不重复）✅
5. **修复前：** 显示两份，互相叠加错位 ❌

### 测试场景 3：控制栏隐藏后

1. **唤醒控制栏** → 显示标题
2. **退出控制栏**（自动隐藏或手动返回）
3. **预期结果：**
   - OSD 开启时：按用户设置显示屏显信息
   - OSD 关闭时：不显示任何屏显信息

## 编译验证

```bash
./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac --daemon
```

**结果：** ✅ BUILD SUCCESSFUL in 1m 26s

## 相关文档

- [播放器屏显系统统一 - 实施总结](feature_osd_unified_summary.md)
- [统一 OSD 标题栏](feature_osd_unified_title_bar.md)
- [控制栏强制显示功能](feature_osd_controls_display.md)

## 技术细节

### 调用链

```
用户唤醒控制栏
↓
VideoActivity.showControl()
├─ showTopInfo()  ← 修复点：强制隐藏 widget.top
├─ mBinding.control.getRoot().setVisibility(View.VISIBLE)
└─ mOsd.setControlsVisible(true)
   └─ PlayerOsdController.render()
      └─ if (controlsVisible) {
             setTopLeftForControls(player);   ← 显示标题+分辨率
             setTopRightForControls();        ← 显示时间
         }
```

### 关键变量

- `PlayerSetting.isOsdEnabled()` - 用户 OSD 开关设置
- `controlsVisible` - 控制栏显示状态（由 Activity 传入）
- `widget.top` - 旧系统的标题容器（已废弃）
- `osd.topLeft / topRight` - 新系统的标题容器（统一使用）

## 修改影响范围

✅ **仅影响 leanback VideoActivity**
- 修改 1 个方法（`showTopInfo()`）
- 删除 5 行代码
- 简化为 1 行强制隐藏

❌ **不影响其他模式**
- mobile VideoActivity：未使用 widget.top，无影响
- TmdbDetailActivity：不使用 view_widget_vod 布局，无影响

## 收益

1. **修复重复显示 Bug**：原生模式控制栏标题不再重复
2. **架构更清晰**：废弃 widget.top 双系统，统一使用 OSD
3. **行为一致**：所有模式的控制栏标题显示逻辑完全一致
4. **代码简化**：删除条件分支，简化为无条件隐藏

## 问题追踪

**Issue：** 影视原生模式关闭所有屏显后唤醒控制台会出现两个标题，分辨率和时间

**修复日期：** 2026-07-16

**修复版本：** dev 分支

**验证状态：** ✅ 编译通过，待设备测试
