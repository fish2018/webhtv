# 观看历史封面时间进度

## Recovery anchor

- 用户需求：在观看历史的封面显示上次观看时间，方便确认看到第几分钟。手机观看历史页与电视首页历史卡片同时覆盖。
- 本轮截图定位基线：`feature-menu` / `c4b042bc5441b9e168293f7cadae5798c4608da5`。guard `HISTORY-COVER-SWAP-REMARK`，`quick-fix`；保护既有 `app/.cxx/` 35个文件。初始实现及此前误解记录保留在下文。
- 范围：仅两端`adapter_vod.xml`及本文。截图17:56:06红圈中的“[54.8GB][潘神…]”对应`remark`，不是底部“潘神的迷宫”对应的`name`。用户要求互换`remark`与`historyProgress`，底部`name`不动。
- 完成条件：下部顺序为已看时间→圈出的文件名（remark）→底部片名（name）。只互换前两行，保留2dp行间距、现有样式和其他元素/逻辑；原子提交及本地恢复tag，不push。
- 当前状态：两端已交换这两个子控件，行间距随时间标签从上边距转为下边距。XML解析及严格树比较两端PASS：还原两行顺序与间距边后，整棵布局树与基线完全一致。此次未重新打包、安装或进行真机视觉验收。
- 唯一下一动作：紧接本记录由guard finish原子提交并创建本地恢复tag；之后等待用户的显示/实测反馈，不重复已通过的检查。

## 展示设计与证据（2026-09-10）

这是已有观看进度的展示补全，复用原卡片标签，不是播放器/上游依赖整合；不占用P9或上游任务编号。

| 来源 | 证据与决定 |
| --- | --- |
| 当前基线 `History.java`、Mobile `HistoryAdapter`/`HistoryActivity`、Leanback `HistoryPresenter`/`HomeActivity` | A：position/duration为毫秒，未设置值为负数；手机已有进度条，两端均从同一History读取。已有刷新事件及Diffable链可复用；isSameContent尚未比较position/duration/remarks，补齐会影响封面内容的字段即可，不改保存或读取。 |
| 当前基线两端 `adapter_vod.xml`、`KeepAdapter`、`shape_vod_remark.xml` | A：历史和收藏共用卡片，原标签/删除层/图片尺寸是保留契约。只增加默认GONE的时间标签，历史绑定时显式设置文本和可见性；将原集数与时间垂直排列避免互相覆盖。收藏仍无时间标签，不改变图片或卡片高度。 |
| [AOSP DateUtils.java，android-15.0.0_r1](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/text/format/DateUtils.java#L429)，经用户代理读取；快照 `/tmp/history-cover-progress-20260910.CMrH7W/DateUtils.java` | A：formatElapsedTime使用MM:SS/H:MM:SS及累计小时，不将时长当日期/时区处理。采用相同时间形式，在纯Java小格式化器中先过滤未知位置及钳制有效总长，按完整秒展示，不向下一秒四舍五入；英文/简繁中文案走Android资源。 |
| 上游PR/issues/reverts、论文、博客和基准 | 本次无依赖更新、未解决的平台争议、新算法或性能改善主张；不适用新的合并/性能结论。成熟实现与官方契约已覆盖本决策，额外检索不改变数值展示方案。 |

- 不改：只能看手机比例条，无法得知具体分钟数。
- 原样平台方案：直接DateUtils.formatElapsedTime可格式化，但不能决定无效历史、删除态、总时长钳制及列表刷新；仍需本地适配。
- 采用窄适配：每次绑定从现有position/duration生成短时长，原集数标签之下显示一行“已看 …”；无效时设置空文本并GONE，删除态同样GONE。总时长未知不伪装成0，已知时长仅限制显示值，不写回数据、不改变续播位置。
- 风险与对策：共享布局新增标签默认隐藏；保留原备注样式与位置、卡片外部尺寸和电视焦点；单行窄卡片从前方省略标签前缀以优先保留时间。格式化与边界逻辑纯Java，不依赖Android资源初始化；不新增网络、持久化、权限、依赖、ABI或后台任务。
- 验证：小于一分钟/一分钟/一小时/跨24小时、毫秒边界、0/负数/未知、未知总时长、超出总长和大于int范围；进度/总长/集数单独变化影响Diffable而相同副本不变。运行Mobile arm64定向单测与Leanback armv7 Java/资源编译，不做原生或全构建矩阵。当前无设备，实机截图不作为已完成结果。
- 回滚：原子revert本任务提交；上述基线已包含之前MPV修复，本任务不修改其代码/资产。

## 验证记录

- 已实现：默认GONE的封面时间标签，仅由两端历史绑定显示；原集数仍独立显示，删除态隐藏。共用格式化处理无效/未知/超界/长时长，只计算显示值；History内容比较增加position/duration/remarks，保证单独进度变化能刷新。
- 2026-09-10：使用独立JDK21运行 `JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home LC_ALL=C bash ./gradlew --offline :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.utils.HistoryProgressFormatterTest --tests com.fongmi.android.tv.bean.HistoryTest :app:compileLeanbackArmeabi_v7aDebugJavaWithJavac --console=plain`，`BUILD SUCCESSFUL in 2m 3s`，96 tasks、24 executed。
- JUnit XML确认 `HistoryProgressFormatterTest` 7项、`HistoryTest` 7项，均0 failures/0 errors/0 skipped；覆盖时间格式及毫秒/小时/24小时/long边界、无效数据、未知总长、超界钳制，以及相同副本和独立进度/时长/集数变化。
- 首次命令错误指定了不存在的Android Studio JDK目录，Gradle未开始编译；读取仓库已有命令后改用独立JDK21，一次完成实际测试/编译。不是代码回归，也未放宽验证。
- 证据目录 `/tmp/history-cover-progress-20260910.CMrH7W/`；日志 `tests-and-compile-jdk21.log`，原始环境失败保留在 `tests-and-compile.log`。设备列表为空，无截图/安装结果；此次不触发原生/CMake或APK打包，不把编译通过等同实机视觉通过。
- 收口：只提交guard内本任务文件，保留35个预存dirty文件；本地注释恢复tag由guard finish在提交后立即生成，不push。

### 2026-09-10 顶部定位（已被下一记录纠正）

- 将两端`historyProgress`从底部集数容器移到根RelativeLayout，使用`layout_below=site`、`layout_alignStart=image`与`layout_alignWithParentIfMissing=true`。字号、颜色、背景、间距、可见性、文案和Java逻辑均不变。
- 定向XML检查两端均PASS：解析基线与候选，验证新父节点及定位属性；删除时间标签后，剩余树的标签/属性/内容/顺序完全一致。日志`/tmp/history-cover-progress-top-20260910-layout-check.log`；没有重跑未改动的时间格式化测试或构建。

### 2026-09-10 纠正为文件名上方

- 用户澄清位置是文件名上方，上一轮将其移到封面顶部不符合要求。把时间标签放回下部容器，顺序为集数→已看时间→文件名，撤销顶部定位属性；其余内容/样式和Java代码不变。
- `git diff --exit-code 881c8bca2e6831d6a7d32f67c22c64ce37541e0b -- app/src/mobile/res/layout/adapter_vod.xml app/src/leanback/res/layout/adapter_vod.xml`通过，两份文件与此前编译通过的版本完全一致。证据`/tmp/history-cover-progress-above-name-20260910.log`；不重复既有编译/格式化测试，不声称已安装或实机通过。

### 2026-09-10 按圈图互换（取代此前对“文件名”的误识）

- 已查看用户图片，明确红圈是封面内的文件名`remark`，底部`name`是影片标题。此前把两者混为一谈，不符合用户要交换的控件。
- 仅将同一底部LinearLayout的子项从`remark, historyProgress`换为`historyProgress, remark`；时间标签的2dp上边距换为2dp下边距，保留原行间距。没有移动底部标题或改Java/文字/样式。
- 定向XML检查Mobile/Leanback均PASS：先断言实际顺序已互换与间距仍2dp，再在内存中还原这两个变化，验证整个树的标签、属性、内容与顺序和基线完全相同。证据`/tmp/history-cover-swap-remark-20260910-check.log`。未打包/安装，不声称实机已更新。
