# 横屏播放右侧滑动音量调节问题修复

## 问题描述

在个别手机原生模式下，横屏播放时右侧上下滑动无法正常调节音量，特别是在沉浸融合模式（TMDB Detail Activity）中。

## 问题根源

原代码在判断触摸点位置时使用了 `MotionEvent.getX()`，这是**相对于 View 的局部坐标**。在横屏播放时，由于以下原因可能导致判断错误：

1. **View 坐标系统问题**：横屏时 View 的坐标系可能与屏幕物理坐标系不一致
2. **videoView.getWidth() 不准确**：在某些情况下，`videoView.getWidth()` 可能返回 0 或错误的值
3. **屏幕旋转后坐标混乱**：View 的局部坐标 `getX()` 在屏幕旋转后可能不正确

### 原代码问题（PlayerGesture.java）

```java
private boolean isSide(MotionEvent e) {
    int width = videoView.getWidth() > 0 ? videoView.getWidth() : ResUtil.getScreenWidth(activity);
    int four = width / 4;
    return e.getX() <= four || e.getX() >= four * 3;  // ❌ 使用局部坐标 getX()
}

private void checkSide(MotionEvent e2) {
    int width = videoView.getWidth() > 0 ? videoView.getWidth() : ResUtil.getScreenWidth(activity);
    if (e2.getX() > width / 2f) changeVolume = true;  // ❌ 使用局部坐标 getX()
    else changeBright = true;
}
```

## 解决方案

使用 `MotionEvent.getRawX()` 替代 `getX()`，获取**屏幕绝对坐标**，确保横屏时判断正确。

### 修改后的代码（PlayerGesture.java）

```java
private boolean isSide(MotionEvent e) {
    // 使用 getRawX 获取屏幕绝对坐标，避免横屏时 View 坐标系统问题
    int width = ResUtil.getScreenWidth(activity);
    int four = width / 4;
    float x = e.getRawX();  // ✅ 使用屏幕绝对坐标
    return x <= four || x >= four * 3;
}

private void checkSide(MotionEvent e2) {
    // 使用 getRawX 获取屏幕绝对坐标，确保横屏时正确判断左右区域
    int width = ResUtil.getScreenWidth(activity);
    float x = e2.getRawX();  // ✅ 使用屏幕绝对坐标
    if (x > width / 2f) changeVolume = true;
    else changeBright = true;
}
```

## 修改的文件

1. **app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerGesture.java**
   - 用于沉浸融合模式（TmdbDetailActivity）
   - 修改 `isSide()` 和 `checkSide()` 方法

2. **app/src/mobile/java/com/fongmi/android/tv/ui/custom/CustomKeyDown.java**
   - 用于普通手机模式（VideoActivity）
   - 修改 `isSide()` 和 `checkSide()` 方法

## 技术细节

### getX() vs getRawX()

| 方法 | 坐标系 | 特点 |
|------|--------|------|
| `getX()` | View 局部坐标 | 相对于触摸的 View 左上角，受 View 旋转/缩放/平移影响 |
| `getRawX()` | 屏幕绝对坐标 | 相对于屏幕左上角，始终准确，不受 View 变换影响 |

### 横屏场景分析

- **竖屏模式**：屏幕宽度 < 屏幕高度（如 1080 x 2400）
- **横屏模式**：屏幕宽度 > 屏幕高度（如 2400 x 1080）

横屏时：
- 左侧 1/4 屏幕（0 到 width/4）：调节亮度
- 中间 1/2 屏幕（width/4 到 3*width/4）：时间调节
- 右侧 1/4 屏幕（3*width/4 到 width）：调节音量

## 测试建议

1. **竖屏测试**：
   - 左侧上下滑动 → 应调节亮度
   - 右侧上下滑动 → 应调节音量

2. **横屏测试**：
   - 左侧上下滑动 → 应调节亮度
   - 右侧上下滑动 → 应调节音量 ✅ **修复重点**

3. **沉浸融合模式测试**：
   - 进入 TMDB 详情页的内联播放器
   - 横屏播放
   - 右侧滑动应正常调节音量

## 影响范围

- ✅ 不影响现有竖屏播放功能
- ✅ 修复横屏播放时的手势判断
- ✅ 适用于所有原生模式（包括沉浸融合模式）
- ✅ 向后兼容，不会破坏现有功能

## 相关问题

这个修复同时解决了以下可能出现的相关问题：
- 横屏时左右区域判断不准确
- 原生模式下手势失效
- 屏幕旋转后手势区域错位
