# 小说分页历史节点修复

## Recovery anchor
- 目标：修复小说分页模式重新进入时总是回到章节最后一页。
- 验收：退出时保存当前章节的当前子分页，重新进入恢复到同一子分页；滚动模式和漫画模式不受影响。
- 回滚：还原 `reader.html`、对应 source-test 与本任务文档。

## 诊断
- 模拟器 `192.168.50.3:5561` 日志显示退出时记录为 `anchor=9/10`，重新进入也恢复 `anchor=9/10`。
- 分页模式中未显示的子分页使用 `display:none`，但 `effectiveAnchorIndex()` 仍执行 `anchorsSettled() && atDocumentEnd()`。
- 因此只要当前文档被判断为到底，就把当前页强制改写成 `total - 1`。

## 修复
- `effectiveAnchorIndex()` 在分页模式直接返回当前 `currentPage`。
- 仅滚动模式继续使用文档末尾判定来编码“读完”状态。

## 验证
- `ReaderPlaybackRoutingSourceTest` 新增分页模式回归断言。
- 专项测试通过：
  `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.ReaderPlaybackRoutingSourceTest' --console=plain`
- 构建前 `free -h` 显示 `available=2.1Gi`，满足至少 1.5 GiB 的打包要求；
  `:app:assembleMobileArm64_v8aDebug` 构建成功。
- APK 已安装到 `192.168.50.3:5561`。
- 模拟器复测：从历史记录进入后，翻回一个子分页并退出，日志记录
  `saveProgress index=1 anchor=8/10 chapter=第二章 太荒吞天诀`；再次进入记录
  `restore index=1 anchor=8/10 kind=1 chapter=第二章 太荒吞天诀 reresolve=true`。
  画面显示 `9 / 10`，对应保存接口的 0-based `anchor=8/10`，证明恢复的是退出时子分页而非末页。
- 复测日志未出现 `Uncaught`、`AndroidRuntime` 或 WebView JavaScript 异常。
