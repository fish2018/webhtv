# 播放器控制栏强制显示屏显信息

## 需求

播放器唤醒控制台（控制栏）时，强制显示标题、分辨率、时间这几个屏显信息，**无论用户是否在设置中开启了这些屏显选项**。退出控制栏后，这些信息应该消失，只在控制栏显示期间生效。

## 核心问题

用户关闭所有 OSD 屏显后，`PlayerSetting.isOsdEnabled()` 返回 `false`，导致：
1. `PlayerOsdController.start()` 直接返回，不启动更新循环
2. `render()` 方法永远不会被调用
3. 控制栏显示时，强制显示时间的逻辑无法执行

## 解决方案

修改 `PlayerOsdController.java` 的三处逻辑，使得**即使用户关闭所有 OSD，控制栏显示时也要强制显示标题、分辨率、时间**。

### 1. 修改 `start()` 方法

**位置：** `PlayerOsdController.java:106-121`

**原逻辑：**
```java
if (suppressed || !PlayerSetting.isOsdEnabled()) {
    root.setVisibility(View.GONE);
    return;
}
```

**新逻辑：**
```java
if (suppressed) {
    root.setVisibility(View.GONE);
    return;
}
// 即使用户关闭所有 OSD，控制栏显示时也需要强制显示标题/分辨率/时间
if (!PlayerSetting.isOsdEnabled() && !controlsVisible) {
    root.setVisibility(View.GONE);
    return;
}
```

**说明：** 当控制栏可见时（`controlsVisible=true`），即使 OSD 全关也要启动更新循环。

### 2. 修改 `setControlsVisible()` 方法

**位置：** `PlayerOsdController.java:141-153`

**原逻辑：**
```java
if (started) render();
```

**新逻辑：**
```java
if (started) {
    // 控制栏显示时，即使 OSD 全关也要启动更新循环（为了强制显示时间）
    if (controlsVisible && !PlayerSetting.isOsdEnabled()) {
        resetSpeed();
        App.removeCallbacks(update);
        App.post(update, 0);
    }
    render();
}
```

**说明：** 当控制栏从隐藏变为显示，且用户关闭了所有 OSD 时，手动启动更新循环。

### 3. 修改 `render()` 方法

**位置：** `PlayerOsdController.java:182-209`

**原逻辑：**
```java
if (suppressed) {
    root.setVisibility(View.GONE);
    return false;
}
boolean enabled = PlayerSetting.isOsdEnabled();
if (!enabled) {
    root.setVisibility(View.GONE);
    return false;
}
// ... 后面才处理 controlsVisible
```

**新逻辑：**
```java
if (suppressed) {
    root.setVisibility(View.GONE);
    return false;
}
setTextSize(miniSp);
PlayerManager player = source.getPlayer();
updateSpeed();

// 控制栏显示时的逻辑优先于 isOsdEnabled() 检查
if (controlsVisible) {
    setTopLeftForControls(player);
    setTopRightForControls();
    // ... 隐藏其他屏显
    return true;
}

// 控制栏隐藏时，若用户关闭所有 OSD，停止刷新
boolean enabled = PlayerSetting.isOsdEnabled();
if (!enabled) {
    root.setVisibility(View.GONE);
    return false;
}
```

**说明：** 将 `controlsVisible` 的处理**提前到** `isOsdEnabled()` 检查之前，确保即使 OSD 全关，控制栏显示时也能强制显示时间。

### 4. 新增强制显示方法（已在前一版本实现）

**位置：** `PlayerOsdController.java:226-243`

```java
private void setTopLeftForControls(PlayerManager player) {
    // 控制栏显示时，强制显示标题和分辨率，无论用户设置
    String title = source.getTitle();
    String size = player != null ? player.getSizeText() : "";
    topLeft.setText(join("\n", title, size));
    topLeft.setVisibility(TextUtils.isEmpty(topLeft.getText()) ? View.GONE : View.VISIBLE);
}

private void setTopRightForControls() {
    // 控制栏显示时，强制显示时间，无论用户设置
    topRight.setText(timeFormat.format(new Date()));
    topRight.setVisibility(View.VISIBLE);
}
```

## 行为说明

- **控制栏显示时：** 强制显示标题、分辨率、时间（无论用户设置），每秒刷新时间
- **控制栏隐藏时：** 按用户设置正常显示所有屏显信息；若用户关闭所有 OSD，停止刷新并隐藏
- 时间每秒刷新，格式为 `HH:mm:ss`

## 测试场景

1. **用户开启了时间屏显**：控制栏显示/隐藏时，时间都正常显示 ✓
2. **用户关闭了时间屏显**：控制栏显示时强制显示时间，隐藏时时间消失 ✓
3. **用户关闭所有 OSD 屏显**：控制栏显示时强制显示标题/分辨率/时间，隐藏时全部消失 ✓
4. **沉浸融合模式**：控制栏显示时，右上角时间应该正常显示 ✓

## 相关文件

- `app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java` — 核心逻辑
- `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java` — 控制栏显隐触发
- `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java` — OSD 开关设置
