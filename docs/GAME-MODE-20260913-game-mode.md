# GAME-MODE-20260913 游戏模式移植

## Recovery anchor

- 目标：参照共享目录 `lab-overlay-8游戏`，让动作卡片支持游戏/网页模式，并保持普通影视条目行为不变。
- 范围：动作卡解析、输入链式执行、内置 WebView、列表/内容分发入口、Activity 注册。
- 当前状态：已完成源码移植、入口补齐和加载占位层处理；协议测试 5/5 通过，mobile/leanback Arm64 Debug 主源码编译通过。
- 当前文件：`ActionCardHelper`、`GameContentHandler`、`GameWebActivity`、`Vod`、`App`、首页与两端 `TypeFragment`、主清单、输入布局、协议测试。
- 验证记录：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.content.GameContentHandlerTest :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --console=plain` 成功。已加强 GameWebActivity 安全设置：禁用本地文件访问和混合内容，符合 CatWebActivity 与 HomeWebController 水平。
- 下一步：提交任务守卫并创建本地恢复标签。

## 2026-09-23 动作回传空值修复

- 现象：动作实际执行成功，但 `spider.action()` 返回 `null` / `"null"` / 空串时，界面弹出「动作无响应」。
- 原因：`ActionCardHelper.dispatch()` 把“无 JSON 回传”直接等同于失败。
- 修复：`null` / JSON 字面量 `null` / 空白回传静默视为成功；其他非 JSON 文本仍原样提示，保留真实错误。站点不存在或类型不支持动作时返回可见错误，不再被空回传语义静默吞掉。

## 设计与行为

1. `vod_tag=action` 且 `vod_id` 是 JSON 时，`Vod.getAction()` 将 JSON 交给动作卡流程。
2. `TypeFragment` 点击动作卡后由 `ActionCardHelper` 分流：
   - `type=browser`：直接在全屏内置 WebView 打开 URL，可携带 Header/User-Agent；
   - `type=input`：弹出输入框，确认后将输入回填并调用站点动作接口；
   - 其他动作：调用 `spider.action` 或 HTTP action，并展示返回列表/消息。
3. `GameContentHandler` 注册进 `ContentDispatcher`，支持 `game://` URL 以及内容入口中的 browser 动作卡；普通详情卡不接管。
4. 游戏 WebView 复用现有 `activity_web_reader` 布局，启用 JavaScript、DOM Storage、媒体自动播放、混合内容，并在渲染进程崩溃时安全退出。所有入口及页面内跳转只接受 `http/https`，拒绝 `javascript/file/intent` 等 scheme。
5. 首页动作卡和分类动作卡共用同一执行器；`GameContentHandler` 也注册到 `ContentDispatcher`，支持 `game://https://...` 推送协议。

## 回滚

删除本任务新增的三个 Java 类、输入布局，撤销 `App` 注册、`Vod` 动作兼容、首页/分类动作入口和主清单 Activity 注册即可；不涉及依赖、锁文件或二进制。
