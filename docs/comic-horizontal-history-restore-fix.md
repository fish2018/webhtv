# 漫画横向翻页历史节点修复

## Recovery anchor
- 目标：修复新版漫画横向翻页模式下“阅读后返回、再进不恢复历史节点”。
- 验收：横向翻页保存 `comicCur/comicTotal`，翻页立即上报，重进直接恢复同一页。
- 回滚：还原 `reader.html` 和对应 source-test。

## 诊断
- `4a3fc03533` 升级 UI 后新增漫画横向翻页；旧版只有纵向滚动。
- 通用 `currentAnchorIndex` 依赖滚动位置，横向模式只有当前页一张图，始终算成第 0 页。
- `comicFlip -> comicShowPage` 只改内存页码并刷新 UI，没有触发进度上报。

## 修复
- 横向漫画进度读取 `comicCur`，保存 `comicCur/comicTotal`。
- `comicShowPage` 成功切换后立即上报。
- `restoreAnchor` 对横向漫画直接恢复到目标页，不进入滚动恢复循环。

## 验证
- 执行 `ReaderPlaybackRoutingSourceTest`，覆盖横向漫画进度专用状态和恢复路径。
