# OSD 统一标题栏显示

## 概述

统一由 `PlayerOsdController` 显示标题栏（标题、分辨率）和时间，所有详情模式（炫彩/融合/原生）共享同一套 OSD 显示逻辑。

## 修改前的问题

- **融合模式**：控制栏自带 `playerTitle` 和 `playerSize` TextView，与 OSD 重复
- **原生模式**：控制栏没有标题栏，依赖 OSD 显示
- **炫彩模式**：控制栏没有标题栏，依赖 OSD 显示
- 逻辑分散，维护困难，容易出现重复显示或遗漏显示的问题

## 修改后的设计

### 统一显示原则

1. **所有详情模式都由 PlayerOsdController 统一显示标题、分辨率、时间**
2. **控制栏显示时，强制显示这三项**（无论用户设置）
3. **控制栏隐藏时，按用户设置正常显示所有屏显信息**

### 核心逻辑

```java
// PlayerOsdController.java
public void render(boolean controlsVisible, @Nullable PlayerManager player) {
    if (controlsVisible) {
        // 控制栏显示时：强制显示标题、分辨率、时间
        setTopLeftForControls(player);    // 标题 + 分辨率
        setTopRightForControls();         // 时间
        
        // 隐藏其他屏显
        bottomCenter.setVisibility(View.GONE);   // 进度
        bottomRight.setVisibility(View.GONE);    // 流量
        centerRight.setVisibility(View.GONE);    // 诊断面板
        miniSp.setVisibility(View.GONE);         // 迷你进度条
    } else {
        // 控制栏隐藏时：按用户设置正常显示所有屏显
        setTopLeft(player);
        setTopRight();
        setBottomCenter();
        setBottomRight(player);
        setCenterRight();
    }
}

private void setTopLeftForControls(PlayerManager player) {
    // 控制栏显示时，强制显示标题和分辨率（不检查用户设置）
    String title = source.getTitle();
    String size = player != null ? player.getSizeText() : "";
    topLeft.setText(join("\n", title, size));
    topLeft.setVisibility(TextUtils.isEmpty(topLeft.getText()) ? View.GONE : View.VISIBLE);
}

private void setTopRightForControls() {
    // 控制栏显示时，强制显示时间（不检查用户设置）
    topRight.setText(timeFormat.format(new Date()));
    topRight.setVisibility(View.VISIBLE);
}
```

## 代码清理

### 1. 删除融合控制栏的标题栏控件

**文件：** `app/src/main/res/layout/player_control_fusion.xml`

删除：
- `playerTitle` TextView（标题）
- `playerSize` TextView（分辨率）

### 2. 清理 TmdbDetailActivity

删除的方法：
- `updateInlineTitle()` - 标题缓存更新（改为按需计算）

删除的控件引用：
- `binding.playerTitle` - 融合模式标题控件（7 处引用全删）
- `binding.playerSize` - 融合模式分辨率控件（1 处引用）

简化的方法：
- `inlineTitleText()` - 改为直接调用 `getInlineOsdTitle()` 计算标题
- `getInlinePlayerTitle()` - 改为调用 `inlineTitleText()`

删除的调用：
- 三处 `updateInlineTitle()` 调用
- 一处 `updateSize()` 调用

### 3. 清理 VodPlayerControlController

删除的方法：
- `updateSize(String size)` - 分辨率显示方法

### 4. 清理架构接口

删除的接口方法：
- `VodPlayerUiHost.controlsHaveOwnTitleBar()` - 控制栏自带标题栏标志
- `VodPlayerUiController` 中的转发
- `PlayerOsdController.Source.controlsHaveOwnTitleBar()` - default 方法

简化的逻辑：
- `PlayerOsdController.setTopLeftForControls()` - 删除 `controlsHaveOwnTitleBar()` 检查，统一强制显示

### 5. 外部播放器兼容

修改：
- `startNativePlayer()` 改用 `inlineTitleText()` 获取标题

## 测试验证

### 功能测试

1. **融合模式**
   - 显示控制栏时，OSD 左上角显示标题和分辨率，右上角显示时间
   - 隐藏控制栏后，按用户设置显示屏显信息

2. **原生模式**
   - 显示控制栏时，OSD 左上角显示标题和分辨率，右上角显示时间
   - 隐藏控制栏后，按用户设置显示屏显信息

3. **炫彩模式**
   - 显示控制栏时，OSD 左上角显示标题和分辨率，右上角显示时间
   - 隐藏控制栏后，按用户设置显示屏显信息

4. **用户设置**
   - 关闭标题/分辨率/时间的用户设置后，控制栏隐藏时不显示对应屏显
   - 控制栏显示时，无论用户设置如何，都强制显示标题、分辨率、时间

### 编译测试

```bash
./gradlew --stop
./gradlew assembleRelease
```

## 架构改进

### 简化前

```
融合模式: playerTitle/playerSize (控制栏自带) + PlayerOsdController
原生模式: PlayerOsdController
炫彩模式: PlayerOsdController

问题: 融合模式重复显示，需要 controlsHaveOwnTitleBar() 标志控制
```

### 简化后

```
所有模式: PlayerOsdController (统一显示)

优势: 
- 单一职责，OSD 统一管理所有屏显
- 删除重复代码和标志位
- 维护成本降低
```

## 相关文件

### 修改的文件

- `app/src/main/res/layout/player_control_fusion.xml` - 删除 playerTitle/playerSize
- `app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java` - 统一显示逻辑
- `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java` - 清理控件引用
- `app/src/main/java/com/fongmi/android/tv/ui/player/VodPlayerControlController.java` - 删除 updateSize
- `app/src/main/java/com/fongmi/android/tv/ui/player/VodPlayerUiHost.java` - 删除 controlsHaveOwnTitleBar
- `app/src/main/java/com/fongmi/android/tv/ui/player/VodPlayerUiController.java` - 删除转发

### 相关文档

- [OSD 控制栏显示特性](feature_osd_controls_display.md) - 控制栏显示时的屏显行为（本次修改的基础）
- [Detail Mode Activity Mapping](../memory/detail-mode-activity-map.md) - 三种详情模式的架构映射

## 修改日期

2026-07-16
