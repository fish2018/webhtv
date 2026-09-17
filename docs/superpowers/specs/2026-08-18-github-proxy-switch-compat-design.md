# GitHub 加速源开关兼容性设计

## 背景

斐讯 T1（Android 9）点击“加速源”后，`dialog_github_proxy.xml` 在 inflate
`com.google.android.material.switchmaterial.SwitchMaterial` 时抛出
`InflateException`，导致整个加速源对话框无法打开。该控件由提交
`4a2c76477e` 新增，且崩溃发生在布局加载阶段，尚未进入网络或数据源逻辑。

## 设计

将 `dialog_github_proxy.xml` 中的 `SwitchMaterial` 替换为
`androidx.appcompat.widget.SwitchCompat`。保留现有控件 ID、文字、颜色状态、
焦点关系和 `AboutDialog` 中的 `setChecked` / `setOnCheckedChangeListener`
逻辑。`SwitchCompat` 仍然是 `CompoundButton`，所以 Java 行为无需改动；它不经过
Material Switch 的主题校验和构造路径，降低 Android 9 盒子兼容风险。

不修改其他使用 `SwitchMaterial` 的对话框，避免扩大行为范围。现有的
`AboutDialogLayoutTest.githubProxyDialogExposesPersistentEnableSwitch` 将验证加速源
布局使用兼容控件且保留持久化开关绑定；测试必须先因旧控件断言失败，再通过替换控件变绿。

## 验收标准

1. 加速源布局不再声明 `SwitchMaterial`，而声明 `SwitchCompat`。
2. 加速开关仍使用 `@id/enabled`，可读写 `Setting.isGithubProxyEnabled()`，并保存变更。
3. `AboutDialogLayoutTest` 通过，且相关 Android 单元测试/编译通过。
4. 不改变 GitHub 源列表、添加、删除、重置和 URL 应用逻辑。
