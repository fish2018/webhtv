# 焦点导航问题诊断

## 当前问题
1. ✅ 退出全屏按钮在第一个位置 - **已修复**：调整布局顺序
2. ✅ 右上角信息按钮显示 - **已修复**：强制隐藏
3. ❌ 画质/原始按钮无法向左
4. ❌ 退出全屏按钮无法向右

## 已尝试的修复

### 1. 移除手动横向焦点设置
- 移除了`playerFullscreenAction.setNextFocusLeftId(R.id.playerRepeat)`
- 移除了`playerRepeat.setNextFocusRightId(R.id.playerFullscreenAction)`
- **理由**：让系统自动处理HorizontalScrollView中的横向导航

### 2. 调整LinearLayout属性
- 添加`android:descendantFocusability="afterDescendants"`
- 添加`android:focusable="false"`
- **理由**：确保焦点可以传递到子View

## 当前焦点设置状态

### 纵向焦点（上/下）
- ✅ 所有按钮的`setNextFocusUpId`都指向自己（防止焦点丢失）
- ✅ `playerFullscreenAction.setNextFocusDownId`指向timeBar
- ❌ 其他按钮没有设置`setNextFocusDownId`（可能不需要）

### 横向焦点（左/右）
- ❌ 所有按钮都没有设置横向焦点
- ✅ 依赖系统自动处理（LinearLayout + HorizontalScrollView）

## 可能的根本原因

### 假设1：HorizontalScrollView不支持自动焦点导航
**测试方法**：手动为所有按钮建立完整的横向焦点链

```java
binding.playerNext.setNextFocusLeftId(View.NO_ID);
binding.playerNext.setNextFocusRightId(R.id.playerPrev);
binding.playerPrev.setNextFocusLeftId(R.id.playerNext);
binding.playerPrev.setNextFocusRightId(R.id.playerEpisodes);
// ... 依此类推
```

### 假设2：按钮的visibility或enabled状态影响焦点链
**测试方法**：检查运行时哪些按钮是可见的，哪些是禁用的

### 假设3：PlayerButtonSetting.applyOrder破坏了焦点链
**测试方法**：临时禁用`applyOrder`调用，看焦点是否恢复

### 假设4：某个按钮的特殊设置阻断了焦点
**测试方法**：
1. 检查是否有按钮设置了`android:focusable="false"`
2. 检查是否有按钮设置了特殊的`nextFocusLeft/Right`

## 下一步调试

### 方案A：手动构建完整焦点链
为所有按钮按顺序设置`setNextFocusLeftId`和`setNextFocusRightId`

### 方案B：回滚到工作版本
回滚到b665dc6be之前的版本，对比焦点设置的差异

### 方案C：使用VideoActivity的方法
检查VideoActivity的控制栏如何处理焦点导航，完全照搬

## 临时解决方案

如果以上都不行，可以考虑：
1. 使用RecyclerView替代LinearLayout + HorizontalScrollView
2. 自定义ViewGroup，重写焦点导航逻辑
3. 使用FocusHelper工具类统一管理焦点
