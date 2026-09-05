# TmdbDetailActivity 沉浸融合模式问题修复进度

## 当前状态

### ✅ 已完成
1. 隐藏右上角信息按钮（playerInfo）
2. OSD诊断信息位置调整（移到playerPanel内）
3. 按钮顺序调整和横向焦点导航
4. LUT面板位置调整（移到playerPanel内）

### 🔄 进行中
1. **OSD文字颜色** - 在PlayerOsdController.setDiagnosticsPanel()中强制设置白色
2. **按钮文字颜色** - 在updateInlineButtons()末尾调用updateInlineButtonColors()
3. **画质按钮逻辑** - 正确处理URL画质和视频轨道

### ❌ 待解决
1. **LUT面板焦点循环问题** - RecyclerView焦点导航不正确

## 问题详情

### 1. OSD文字颜色（深蓝色 → 白色）
**问题**：OSD诊断信息文字显示为深蓝色，而不是白色
**原因**：
- view_player_osd.xml中设置了`textColor="#FFFFFF"`
- 但PlayerOsdController.setDiagnosticsPanel()调用setText()时可能触发样式覆盖
**修复**：在setDiagnosticsPanel()中setText()后强制设置白色
```java
diagnostics.setText(text);
diagnostics.setTextColor(0xFFFFFFFF); // 强制设置白色
```

### 2. 播放参数按钮颜色
**问题**：选中播放参数后，按钮文字颜色没有变成黄色
**原因**：updateInlineButtons()频繁调用setText()可能覆盖颜色
**修复**：
- 创建updateInlineButtonColors()方法
- 在updateInlineButtons()末尾调用
- 根据isSelected()状态设置黄色或白色

### 3. 画质按钮逻辑
**当前行为**：点击画质按钮显示"选择视轨"对话框
**原因**：该视频没有URL多画质（1080P/720P等），只有视频轨道
**逻辑**：
1. 有URL画质 → 显示URL画质列表
2. 无URL画质，有视频轨道 → 显示视频轨道作为画质选项（**这是正确的**）
3. 都没有 → 按钮禁用

**注意**：视频轨道选择也是一种"画质"选择（不同编码格式影响清晰度和兼容性），当前行为是合理的。

### 4. LUT面板焦点循环问题 ⚠️
**问题**：
- LUT面板显示正常
- 但在RecyclerView中按下遥控器时，焦点从"原画"跳回底部"LUT"按钮
- 而不是向下到"乌托邦"

**可能原因**：
1. RecyclerView的焦点设置不正确
2. playerPanel的descendantFocusability虽然设置为"afterDescendants"，但可能不够
3. LutQuickPanel内部的RecyclerView可能需要特殊的焦点处理

**调试方向**：
1. 检查LutQuickPanel类的RecyclerView配置
2. 检查RecyclerView的LayoutManager焦点设置
3. 可能需要为RecyclerView设置自定义KeyListener
4. 或者在LutQuickPanel中处理焦点导航

## 下一步计划

1. **等待编译** - 测试OSD颜色和按钮颜色修复
2. **如果颜色仍不对** - 需要在构造函数或初始化时设置颜色
3. **调试LUT焦点** - 需要深入LutQuickPanel类进行修改

## 测试清单

请在新版本中测试：
1. ✅ OSD诊断信息文字是否是白色（不是深蓝色）
2. ✅ 播放参数按钮选中时文字是否是黄色
3. ✅ LUT面板是否可以正常上下选择（不跳回按钮）
4. ✅ 画质按钮点击是否有反应（有视轨时显示视轨）
