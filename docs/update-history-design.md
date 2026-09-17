# 在线更新历史版本查看与安装设计

> 状态：提案（待评审）
>
> 日期：2026-07-22
>
> 范围：Android Mobile 与 Leanback；正式版（stable）与测试版（beta）
>
> 已确认需求：历史版本必须支持选择目标版本并在线更新

## 1. 结论

该功能可以实现，首版不需要新增后端服务，也不需要修改现有发布流程。历史列表中的兼容版本可以复用现有备份、下载、SHA-256 校验和系统安装链路完成在线更新。

仓库当前没有“在线更新历史版本”相关设计文档或实现。现有更新链路已经调用 GitHub Releases API：

- 正式版通过 `/releases/latest` 获取最新正式发布。
- 测试版通过 `/releases?per_page=20` 扫描最近发布。
- GitHub Release 中已经包含版本标签、发布时间、发布说明和各设备变体的 JSON/APK 资产。

因此，历史版本页可以直接复用 GitHub Releases 列表，将正式版和测试版分通道展示。列表首屏只读取 Release 元数据；用户选择“在线更新”时，再按该 Release 的 asset ID 拉取当前设备对应的 JSON 清单，严格校验后交给现有 `Updater`。

在线更新支持以下目标：

- `versionCode` 高于当前安装版本：正常更新。
- `versionCode` 等于当前版本但 Release tag 不同：允许同版本构建替换，最终仍由 Android 签名与安装校验确认。
- `versionCode` 低于当前安装版本：保留查看，但禁止直接覆盖降级。普通 Android 应用无法可靠绕过系统降级限制。

## 2. 现状核查

### 2.1 未发现既有设计

已检查仓库 Markdown 文档、tokensave 会话决策记录、Git 历史中的文档路径与提交信息，以及“在线更新 / 历史版本 / UpdateDialog / Updater”等关键词：

- 没有历史版本相关设计文档。
- 没有历史版本相关持久化决策。
- Git 历史中也没有曾被删除或改名的相关 Markdown；仅发现一次“更新前弹出备份确认框”的实现提交，没有附带设计文档。
- 仅有一次 Leanback 更新弹窗下载态尺寸修复记录，与历史版本功能无关。

### 2.2 当前更新流程

核心实现位于：

```text
app/src/main/java/com/fongmi/android/tv/Updater.java
app/src/main/java/com/fongmi/android/tv/bean/Update.java
app/src/main/java/com/fongmi/android/tv/utils/Github.java
app/src/mobile/java/com/fongmi/android/tv/ui/dialog/UpdateDialog.java
app/src/leanback/java/com/fongmi/android/tv/ui/dialog/UpdateDialog.java
```

当前流程如下：

```text
设置页“版本”
  -> AboutDialog
  -> Updater.force().start()
  -> 并行检查 stable / beta
  -> 每个通道只保留一个最新 Update
  -> UpdateDialog 展示两个通道的最新版本
  -> 用户选择通道并下载更新
```

具体行为：

1. `Updater.doInBackground()` 并行获取 stable 和 beta。
2. `getUpdate(channel)` 读取最新资产清单，并在候选源中选择版本号更高的一条。
3. stable 使用 GitHub `/releases/latest`。
4. beta 使用 GitHub `/releases?per_page=20`，按 prerelease、`-beta-` 标签和 beta 清单资产筛选第一条。
5. `Update` 只表示某个通道的单个最新可安装版本，不适合作为历史列表模型。
6. Mobile 与 Leanback 的 `UpdateDialog` 均只渲染 stable/beta 各一条。
7. `readGithubReleaseUpdate()` 已能按 Release asset ID 读取 JSON 清单并生成 `Update`。
8. 下载完成后，现有流程会校验文件存在性、size、SHA-256 与 APK 可解析性，再通过 `FileUtil.openFile()` 交给 Android 系统安装器。

### 2.3 当前发布数据

`.github/workflows/android-release.yml` 已保证：

- stable 标签：`v<versionName>-yyyyMMddHHmm`。
- beta 标签：`v<versionName>-beta-yyyyMMddHHmm`。
- beta Release 标记为 prerelease。
- 每个 Release 包含四组设备变体的 APK 与 JSON 清单。
- JSON 清单包含 `name`、`channel`、`code`、`apk`、`size`、`sha256` 和可选 `notes`。

注意：`Github.getCnbAsset()` 当前实际仍指向 GitHub latest download 地址。CNB 是发布工作流中的可选镜像，不应作为首版历史列表的必需数据源。

## 3. 目标

1. 在现有在线更新弹窗增加独立的“查看历史版本”按钮。
2. 历史页同时支持 stable 和 beta，且可在两者间切换。
3. 展示版本号、发布时间、通道状态、当前安装标记、发布说明和在线更新操作。
4. 对用户选中的历史 Release 按需解析清单，并复用现有备份、下载、校验和安装流程。
5. Mobile 支持触摸操作，Leanback 支持遥控器方向键、确认键和返回键。
6. 不改变现有最新版本检查策略，也不建立第二套下载器。
7. 不依赖新的服务端、数据库或账号凭据。

## 4. 非目标

首版不包含：

- Android 低 `versionCode` 降级。
- 卸载后重装、数据迁移或一键回滚。
- 静默安装或绕过 Android 系统安装确认。
- 安装缺少 size、SHA-256 或当前设备变体清单的历史 Release。
- 接受 Release 清单提供的任意第三方 APK 地址。
- 在本地保存完整历史版本数据库。
- 将 CNB 与 GitHub 的历史 Release 聚合、去重。
- 修改自动更新策略或默认更新通道。

历史在线更新与“强制降级”是两个不同能力。首版支持系统允许的正常更新与同版本构建替换；低 `versionCode` 版本只展示“低于当前版本，无法直接安装”。若未来必须降级，需要另行设计卸载、备份恢复和失败恢复流程，因为该过程可能造成数据丢失。

## 5. 用户故事与验收标准

### 5.1 用户故事

- 作为用户，我可以在在线更新弹窗中看到独立的“查看历史版本”按钮。
- 作为用户，我可以查看所有仍有当前设备变体资产的正式版历史。
- 作为用户，我可以切换到“测试版”查看测试版历史。
- 作为用户，我可以选择高于当前版本或同版本不同构建的历史 Release，并在线完成更新。
- 作为用户，当目标版本低于当前版本时，我能看到明确的系统限制说明，而不是下载后才安装失败。
- 作为 TV 用户，我可以只用遥控器完成进入、切换通道、展开说明、加载更多和返回。
- 作为离线或网络异常用户，我可以看到明确错误并重试，不影响现有更新弹窗。

### 5.2 验收标准

1. Mobile 和 Leanback 更新弹窗都显示“查看历史版本”按钮。
2. 下载开始后历史按钮隐藏或禁用，不能叠加打开历史弹窗。
3. 历史弹窗始终提供“正式版 / 测试版”两个通道入口；某通道无数据时显示独立空状态。
4. 通道切换只影响历史页，不修改 `Setting.putUpdateChannel()`。
5. 历史记录按 GitHub Release 返回顺序倒序展示，并按标签去重。
6. 每条至少显示版本、发布时间、发布说明和当前安装标记；依赖 `versionCode` 的安装资格在点击后读取 manifest，再明确呈现结果。
7. 只展示包含当前 flavor/ABI 对应清单资产的 Release，避免展示无法用于当前设备的构建。
8. 首屏加载失败可重试；已有缓存时优先展示缓存并提示数据可能不是最新。
9. 关闭历史弹窗后回到原更新弹窗，通道选择和展开状态不丢失。
10. 点击可安装目标后，只请求该条目的 JSON 清单；清单通过 Release、通道、tag、设备变体、size 和 SHA-256 校验后才允许继续。
11. 历史更新必须复用现有备份确认、下载进度、取消、失败回退、文件校验和系统安装流程。
12. 低于当前 `versionCode` 的条目不启动下载；同版本同 tag 显示“当前版本”。
13. 下载后的 APK 必须再次校验包名、实际 `versionCode`、签名证书、size 和 SHA-256。
14. 现有 stable/beta 最新版本检查与下载测试保持通过。

## 6. 交互设计

### 6.1 入口

在现有 `UpdateDialog` 底部操作区增加独立按钮：

```text
┌────────────────────────────────────┐
│ 在线更新                         × │
│ 正式版 …                            │
│ 测试版 …                            │
│                                    │
│ [ 查看历史版本 ] [ 更新正式版/确定 ] │
└────────────────────────────────────┘
```

采用同一行双按钮，避免增加弹窗高度并再次触发 Leanback 下载态高度问题：

- 左侧：次要样式，“查看历史版本”。
- 右侧：保留现有 `cancel` 主操作按钮及其动态文案。
- 空间不足时允许 Mobile 使用等权布局；文案保持单行、省略或适度缩小，不换行。
- 下载态隐藏历史按钮，主按钮恢复占满整行。

点击历史按钮时，以当前选中的通道作为历史弹窗默认通道；这只是默认展示，不改变更新设置。

### 6.2 历史弹窗

```text
┌────────────────────────────────────┐
│ 历史版本                         × │
│ [ 正式版 ] [ 测试版 ]              │
│                                    │
│  4.x.x · 2026-07-22   通道最新     │
│  发布说明摘要…                      │
│                     [ 在线更新 ]   │
│                                    │
│  4.x.x · 2026-07-18   当前安装     │
│  发布说明摘要…                      │
│                 [ 当前版本 ]       │
│                                    │
│             [ 加载更多 ]            │
└────────────────────────────────────┘
```

单条记录展示：

- 去除 `v` 前缀后的版本标签。
- stable/beta 通道标记。
- `published_at` 格式化后的本地时间。
- “通道最新”标记：当前通道返回的第一条有效记录。
- “当前安装”标记：`AppVersion.isCurrent(tag)` 为 true。
- Release `body`；为空时显示“暂无更新日志”。
- 长说明默认折叠，点击或确认键展开/收起。
- 列表首屏没有逐条读取 manifest，因此非当前条目先统一显示“在线更新”；点击后进入 `ResolvingTarget`，再按清单解析结果更新操作文案：
  - “在线更新”：目标 `versionCode` 高于当前。
  - “安装此构建”：目标 `versionCode` 相同但 tag 不同。
  - “低于当前版本”：目标 `versionCode` 更低，禁用。
  - “当前版本”：目标 tag 与当前安装一致，禁用；该状态可在列表阶段直接确定。
- 点击在线更新后先显示“正在读取版本清单”，清单校验通过才进入备份确认。
- 备份确认必须显示当前版本、目标版本、通道和降级/同版本替换提示（如适用）。

### 6.3 Mobile 行为

- 顶部使用两个 Tab/Chip 切换 stable 与 beta。
- 列表支持触摸滚动。
- 点击版本项展开或收起发布说明。
- 点击“在线更新”进入清单解析与安装确认；解析期间该项禁用，避免重复请求。
- 使用明确“加载更多”按钮，不使用不可见的自动无限滚动。

### 6.4 Leanback 行为

- 初始焦点落在当前通道 Tab。
- 左右键切换通道，向下进入版本列表。
- 版本项获得焦点时可用确认键展开说明。
- 在线更新按钮是独立焦点目标；解析、备份和下载期间锁定列表焦点。
- 列表末尾有可聚焦“加载更多”项。
- 返回键先关闭历史弹窗，回到原更新弹窗。
- 必须显式设置 `nextFocusUp/Down/Left/Right`，避免焦点跳出弹窗。

### 6.5 页面状态

每个通道独立维护：

- `Loading`：首屏进度状态。
- `Content`：历史列表。
- `Empty`：暂无兼容的历史版本。
- `Error`：请求失败与重试按钮。
- `LoadingMore`：保留现有列表，只在底部显示加载中。
- `End`：隐藏“加载更多”，显示“已加载全部”。
- `ResolvingTarget`：读取所选 Release 的 JSON 清单并校验。
- `InstallConfirm`：显示目标版本、备份选项和安装风险。
- `Installing`：进入现有下载进度、取消和失败回退状态。
- `InstallBlocked`：目标低于当前版本、清单不完整或校验不通过。

## 7. 数据源设计

### 7.1 首选数据源

使用现有公开 GitHub Releases API：

```text
GET https://api.github.com/repos/YaChengMu/webhtv/releases
    ?per_page=20
    &page=<n>

Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
```

理由：

- 客户端已经使用同一接口查找 beta，不引入新的网络依赖。
- 一次响应已包含标签、发布时间、Release body、prerelease 和资产列表。
- 不需要把用户数据上传到服务端。
- 不需要应用内置 GitHub Token。

列表首屏不逐条下载 JSON 清单。Release 元数据和资产名称足够渲染历史列表；`versionCode` 相关资格暂不在首屏承诺，只有用户点击某一条“在线更新”时，才读取该条目的 manifest asset。这样既支持在线更新，又避免首屏 N+1 请求和匿名 API 限流。

### 7.2 通道与兼容性筛选

通道判断优先使用当前设备对应的清单资产名，而不是只依赖 prerelease：

```text
stable: <flavor>-<abi>.json
beta:   <flavor>-<abi>-beta.json
```

其中 `<flavor>-<abi>` 与现有 `Updater.getName()` 一致：

```java
BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi
```

筛选规则：

1. 排除 `draft=true`。
2. 排除空 `tag_name`。
3. stable 页要求包含 stable 清单资产。
4. beta 页要求包含 beta 清单资产。
5. `prerelease` 与 `-beta-` 仅用于一致性校验和日志，不作为唯一通道来源。
6. 同标签只保留第一条。

这样可以兼容发布元数据误标，同时确保展示的 Release 确实包含当前设备变体。

### 7.3 选定版本清单与安装目标

历史列表项保存对应的 manifest asset ID。用户点击在线更新时：

1. 调用现有 `Github.getReleaseAssetApi(assetId)`，使用 `application/octet-stream` 读取 JSON 清单；`assetId` 必须等于列表阶段记录的该 Release 的 manifest asset ID，且资产名称仍需匹配当前通道和设备变体。
2. 校验清单的 `name` 与 Release `tag_name` 一致。
3. 校验清单的 `channel` 与当前历史页通道一致。
4. 校验清单包含当前 flavor/ABI 对应的 APK 文件名、正数 `size` 和 64 位十六进制 `sha256`。
5. 忽略清单中的任意外部下载地址；对 GitHub Release 目标只允许由经过长度/字符校验并 URL 编码的 Release tag 与固定设备 APK 文件名生成下载 URL。CNB 只能作为已配置且经过 allowlist 的备用镜像。
6. 将已验证清单转换为不可变的 `ResolvedUpdateTarget`，再交给 `Updater.requestUpdate()`。

`ResolvedUpdateTarget` 必须携带来源 Release tag、通道、manifest asset ID 和解析后的 `Update`，不能让 UI 直接构造可安装目标。

注意：现有 `Update.hasUpdate()` 会把较低 `versionCode` 也视为有更新，历史目标必须使用这里定义的独立资格判断，不能直接复用该方法。

安装资格：

| 状态 | 条件 | UI | 行为 |
|---|---|---|---|
| `CURRENT` | tag 与当前安装一致 | 当前版本 | 禁用 |
| `UPDATE` | code 高于当前 | 在线更新 | 允许 |
| `SAME_CODE` | code 相同、tag 不同 | 安装此构建 | 允许，安装前提示 |
| `DOWNGRADE_BLOCKED` | code 低于当前 | 低于当前版本 | 禁用，不下载 |
| `INVALID` | 清单/资产/完整性校验失败 | 无法安装 | 禁用并显示原因 |

只有 `UPDATE` 和 `SAME_CODE` 可以进入 `Updater.requestUpdate()`。`CURRENT`、`DOWNGRADE_BLOCKED` 和 `INVALID` 都停留在历史弹窗；`INVALID` 结果中的 `update` 可以为空，但必须携带稳定的 `failureReason`。

### 7.4 分页与缓存

- 默认每页请求 20 条原始 Release。
- 原始分页响应按 API page 缓存在内存中，stable/beta 共用，切换通道不重复请求。
- 如果当前原始页过滤后没有目标通道数据但服务端仍可能有下一页，可继续扫描下一页，单次用户操作最多追加 2 次请求。
- 列表缓存建议有效期 10 分钟。
- “重试/刷新”绕过失败缓存。
- `rawCount < pageSize` 时视为结束；否则保留“加载更多”。
- 不落 Room 数据库，避免为历史列表和一次性安装目标增加迁移成本。

匿名 GitHub API 有限流风险，因此必须请求去重，并禁止为每个列表项再请求 manifest。

## 8. 数据模型与内部接口

现有 `Update` 继续表示已解析的可安装清单。新增历史元数据与已验证安装目标，避免 UI 把未验证的第三方响应直接送入下载器：

```java
public final class UpdateRelease {
    private final long releaseId;
    private final long manifestAssetId;
    private final String tag;
    private final String channel;
    private final String title;
    private final String notes;
    private final long publishedAt;
    private final boolean prerelease;
    private final boolean current;
    private final boolean latest;

    UpdateRelease(long releaseId, long manifestAssetId, String tag, String channel,
                  String title, String notes, long publishedAt, boolean prerelease,
                  boolean current, boolean latest) {
        this.releaseId = releaseId;
        this.manifestAssetId = manifestAssetId;
        this.tag = tag;
        this.channel = channel;
        this.title = title;
        this.notes = notes;
        this.publishedAt = publishedAt;
        this.prerelease = prerelease;
        this.current = current;
        this.latest = latest;
    }

    // 只读 getter 省略；UI 不能修改 Release 与 asset ID 的绑定。
}

public final class ResolvedUpdateTarget {
    private final Update update;
    private final UpdateInstallEligibility eligibility;
    private final String failureReason;
    private final String releaseTag;
    private final String channel;
    private final long manifestAssetId;

    // 包级构造器：仅由完成边界校验的数据层创建。
    ResolvedUpdateTarget(Update update, UpdateInstallEligibility eligibility,
                         String failureReason, String releaseTag,
                         String channel, long manifestAssetId) {
        this.update = update;
        this.eligibility = eligibility;
        this.failureReason = failureReason;
        this.releaseTag = releaseTag;
        this.channel = channel;
        this.manifestAssetId = manifestAssetId;
    }

    public Update getUpdate() { return update; }
    public UpdateInstallEligibility getEligibility() { return eligibility; }
    public String getFailureReason() { return failureReason; }
    public String getReleaseTag() { return releaseTag; }
    public String getChannel() { return channel; }
    public long getManifestAssetId() { return manifestAssetId; }
}

public enum UpdateInstallEligibility {
    CURRENT,
    UPDATE,
    SAME_CODE,
    DOWNGRADE_BLOCKED,
    INVALID
}
```

推荐的内部加载契约：

```java
interface UpdateHistorySource {
    UpdateHistoryPage list(String channel, int apiPage) throws UpdateHistoryException;

    ResolvedUpdateTarget resolve(UpdateRelease release) throws UpdateHistoryException;
}

final class UpdateHistoryPage {
    public final List<UpdateRelease> items;
    public final int nextApiPage;
    public final boolean hasMore;

    UpdateHistoryPage(List<UpdateRelease> items, int nextApiPage, boolean hasMore) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.nextApiPage = nextApiPage;
        this.hasMore = hasMore;
    }
}
```

`UpdateHistoryException` 使用稳定错误码，例如 `NETWORK`、`RATE_LIMITED`、`HTTP_NOT_FOUND` 和 `RESPONSE_PARSE`；清单边界校验失败不抛业务异常，而是返回 `eligibility=INVALID` 与 `failureReason`。UI 不解析异常文本，也不会把 `INVALID` 结果交给 `Updater`。

该接口为应用内部接口，不对外暴露；实现必须可注入假响应，以便纯 JVM 单元测试覆盖列表解析、安装目标解析和分页。`ResolvedUpdateTarget` 构造器保持包级可见，只能由完成边界校验的数据层创建。

## 9. 组件划分

| 层 | 建议组件 | 职责 |
|---|---|---|
| 共享模型 | `bean/UpdateRelease.java` | 历史列表元数据与 manifest asset ID |
| 安装目标 | `update/ResolvedUpdateTarget.java` | 经过边界校验、可交给更新器的不可变目标 |
| 共享数据 | `update/UpdateHistoryRepository.java` | 列表请求、筛选、分页、缓存及选定 manifest 解析 |
| 共享命名 | `update/UpdateArtifactNaming.java` | 统一 stable/beta 的 manifest/APK 名称 |
| 更新协调 | `Updater.java` | 接收已验证目标，统一执行确认、备份、下载、校验、安装与恢复 |
| 活动请求 | `update/UpdateRequest.java` | 固化目标、来源与严格校验策略，防止异步期间 `selected` 被覆盖 |
| 回调 | `UpdateListener.java` | 新增默认 `onHistory(channel)` 与 `onHistoryUpdate(release)` 回调 |
| 进度 UI | `UpdateDialog.target(update)` | 历史更新开始后以单目标模式展示版本与下载进度 |
| Mobile UI | `mobile/.../UpdateHistoryDialog.java` 与 Adapter | 触摸交互、列表与在线更新入口 |
| Leanback UI | `leanback/.../UpdateHistoryDialog.java` 与 Adapter | 遥控器焦点、列表与在线更新入口 |
| 资源 | 两套 layout + 公共 strings | 布局、状态文案与三语翻译 |

`UpdateArtifactNaming` 应同时被现有 `Updater` 和历史仓库使用，避免复制 `getAssetName()` 规则。

`Updater` 不直接接收 `UpdateRelease`，只能接收 `ResolvedUpdateTarget`。开始下载后使用不可变的 `UpdateRequest` 作为唯一目标来源，下载回调、进度恢复和文件校验均不得再次读取可能被 UI 修改的通道选择。

## 10. 调用流程

```text
UpdateDialog.history 点击
  -> UpdateListener.onHistory(selectedChannel)
  -> Updater 打开 UpdateHistoryDialog
  -> Dialog 请求 UpdateHistoryRepository.list()
  -> Repository 读取内存缓存
       ├─ 命中：立即返回
       └─ 未命中：请求 GitHub Releases API
  -> 按当前 manifest 资产筛选 stable/beta
  -> 标记 latest/current
  -> 回主线程渲染

历史条目“在线更新”点击
  -> UpdateListener.onHistoryUpdate(release)
  -> UpdateHistoryRepository.resolve(release)
  -> 按 asset ID 拉取所选 JSON manifest
  -> 校验 release/tag/channel/flavor/ABI/size/sha256
  -> 生成 ResolvedUpdateTarget
  -> 判断 CURRENT / UPDATE / SAME_CODE / DOWNGRADE_BLOCKED / INVALID
  -> 可安装目标进入 Updater.requestUpdate(target)
  -> 显示当前版本 -> 目标版本与备份确认
  -> 创建不可变 UpdateRequest
  -> UpdateDialog.target(target) 展示单目标进度
  -> 复用 Download 下载 APK
  -> 校验 size + sha256 + 包名 + versionCode + 签名
  -> FileUtil.openFile() 交给 Android 系统安装器
```

只查看历史时，历史弹窗叠加在现有更新弹窗上；关闭后原通道选择自然保留。用户确认历史目标并开始备份/下载后，关闭历史列表与底层最新版本弹窗，改用 `UpdateDialog.target(target)` 的单目标模式展示准确的目标版本和进度，避免进度出现在不相关的“最新版本”条目上。

`Updater.resume()` 根据活动 `UpdateRequest` 恢复单目标进度弹窗；不能重新用 stable/beta 最新版本覆盖历史目标。

异步回调必须在更新 UI 前检查 Fragment 是否仍已添加，并使用请求 generation 防止旧请求覆盖新通道结果。目标解析和下载都必须去重，重复点击同一目标只保留一个活动请求。

## 11. 错误处理

| 场景 | 行为 |
|---|---|
| 无网络、超时、DNS 失败 | 显示“历史版本加载失败”与“重试” |
| HTTP 403/429 | 显示“请求过于频繁，请稍后再试” |
| JSON 格式异常 | 跳过单条异常数据；整页无法解析时进入错误态 |
| Release 无当前变体资产 | 静默过滤 |
| Release 无 body | 显示“暂无更新日志” |
| 分页加载失败 | 保留已加载列表，仅底部显示重试 |
| 切换通道时旧请求返回 | generation 不匹配则丢弃 |
| Fragment 已关闭 | 不更新 View，不弹 Toast |
| manifest 与 Release tag/channel 不一致 | 标记 INVALID，不允许下载 |
| manifest 缺少 size/SHA-256/APK 变体 | 标记 INVALID，不允许下载 |
| 目标低于当前 versionCode | 标记 DOWNGRADE_BLOCKED，不下载 |
| APK 包名、versionCode 或签名不匹配 | 删除缓存文件并提示校验失败 |
| Android 安装器拒绝覆盖安装 | 保留错误，不自动卸载，不重试降级 |

错误不能关闭底层 `UpdateDialog`，也不能修改 `Updater.selected`。

## 12. 安装边界与威胁模型

历史 Release、manifest 和 APK 都是外部输入，不因来自 GitHub 就视为可信。需要保护的资产是应用包、用户本地数据、签名更新链路和下载带宽。

| 边界/风险 | 控制 |
|---|---|
| GitHub API 返回伪造或畸形 JSON | 只读取 allowlist 字段，校验类型、长度、tag、channel、asset ID 和设备变体 |
| manifest 指向任意外部 APK | 历史目标只允许由经过校验并编码的 Release tag + 固定资产文件名生成 URL；禁止 UI 传入任意 URL |
| APK 在传输或镜像中被篡改 | HTTPS + manifest 中强制 SHA-256；下载后重新计算 |
| 恶意 APK 冒充当前应用 | 校验包名、实际 versionCode 和已安装应用签名证书摘要；最终交给 Android 安装器复核 |
| 重放旧版本/降级 | 比较 long versionCode；低于当前直接阻断，同版本不同 tag 需额外确认 |
| Release body 注入 UI | 使用现有 Markdown 渲染器的安全子集，不执行 HTML、脚本或 intent |
| 重复点击造成多次下载 | 以 releaseId + tag 作为请求键，单活动请求互斥 |
| 大响应或大 APK 消耗资源 | API、notes、manifest、下载超时和文件大小均设上限；下载前检查 manifest size |

历史更新不新增权限、不内置 GitHub/CNB 私有 Token，不允许通过配置把下载地址改成任意 scheme/host。若保留 CNB 备用地址，必须限制为固定 HTTPS host，并让 APK 与 manifest 使用相同的完整性校验。

## 13. 性能、安全与隐私

- 首屏通常只需一次 Releases 请求；目标通道未命中时按分页规则最多追加 2 次，stable/beta 共用原始响应缓存。
- 列表不逐条拉取 manifest；用户只为明确选中的目标发起一次 manifest 请求和一次 APK 下载。
- Release body 使用现有 `MarkdownText.render()` 渲染，禁止执行 HTML/脚本。
- 对单条说明设置合理显示长度上限，完整文本仍可在弹窗内展开。
- 仅请求公开 HTTPS API 和 allowlist 下载地址，不添加 GitHub Token，不记录用户配置。
- 日志只记录 HTTP 状态、页码和过滤数量，不记录完整 Release body。
- 不新增后台常驻任务；只有用户点击历史按钮才加载，只有用户确认目标才下载 APK。
- APK 下载和 SHA-256 计算放在后台线程；安装前校验包信息不阻塞 UI。

## 14. 兼容性与发布约束

- 无数据库迁移。
- 无配置迁移。
- 不改变 `Update` JSON 清单格式。
- 最新版本更新继续使用现有 APK 下载、SHA-256 校验、备份和安装行为。
- 历史版本在线更新必须经过更严格的 manifest、包名、versionCode、签名和完整性校验后，才能进入同一安装流程。
- 发布工作流必须继续上传当前四个设备变体对应的 JSON 清单；缺少某变体清单的 Release 不会出现在该设备历史中。
- stable/beta 标签、`channel`、prerelease 和资产后缀应保持一致；不一致时以兼容资产名作为展示通道依据。

## 15. 测试策略

### 15.1 共享单元测试

新增测试至少覆盖：

1. stable 资产进入 stable 列表。
2. beta 资产进入 beta 列表。
3. prerelease 元数据误标但资产正确时仍按资产归类。
4. draft、空标签、缺少当前设备资产被过滤。
5. 同标签去重。
6. `published_at` 解析失败时安全降级。
7. 当前安装与通道最新标记。
8. 空页、末页和多页游标。
9. stable/beta 复用原始页缓存。
10. 403、429、超时、畸形 JSON 和分页失败。
11. manifest tag/channel/flavor/ABI 绑定校验。
12. 缺失 size、非法 SHA-256、错误 asset ID 和外部 APK URL 被拒绝。
13. CURRENT、UPDATE、SAME_CODE、DOWNGRADE_BLOCKED、INVALID 五种资格判断。
14. 已验证目标只能由数据层构造，UI 不能绕过校验直接创建安装目标。
15. APK 包名、实际 versionCode、签名摘要和 SHA-256 不匹配时安装被阻止。

### 15.2 UI 与焦点测试

Mobile：

- 历史按钮存在且下载态不可用。
- 通道切换、展开说明、重试、加载更多和在线更新状态正确。
- 低版本条目显示禁用原因，不启动下载。
- 解析中重复点击不会产生重复请求。

Leanback：

- 更新弹窗双按钮焦点闭环。
- 历史弹窗 Tab -> 列表 -> 在线更新 -> 确认 -> 下载/取消 -> 返回的焦点路径正确。
- 历史按钮加入后，`UpdateDialogLayout` 下载态高度测试仍通过。
- 历史目标进入单目标进度模式后焦点不回到已关闭的历史列表。

### 15.3 手工验证矩阵

| 终端 | stable 在线更新 | beta 在线更新 | 同版本构建 | 低版本阻断 | 离线/失败恢复 |
|---|---:|---:|---:|---:|---:|
| Mobile arm64 | 必测 | 必测 | 必测 | 必测 | 必测 |
| Mobile armeabi-v7a | 必测 | 必测 | 抽测 | 抽测 | 必测 |
| Leanback arm64 | 必测 | 必测 | 必测 | 必测 | 必测 |
| Leanback armeabi-v7a | 必测 | 必测 | 抽测 | 抽测 | 必测 |

建议验证命令：

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest
.\gradlew.bat :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebug
```

```bash
./gradlew :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest
./gradlew :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebug
```

## 16. 实施任务

- [ ] 抽取更新资产命名规则
  - 验收：`Updater` 与历史仓库生成完全相同的 stable/beta 资产名。
  - 验证：命名单元测试。
  - 文件：`Updater.java`、`UpdateArtifactNaming.java`。

- [ ] 实现历史模型、列表解析、分页与缓存
  - 验收：正式版/测试版可独立分页，切换通道不重复请求已缓存页，并保留 manifest asset ID。
  - 验证：共享单元测试。
  - 文件：`UpdateRelease.java`、`UpdateHistoryRepository.java`、`Github.java`。

- [ ] 实现历史 manifest 解析与安装资格判断
  - 验收：选中目标时只读取对应 asset；tag/channel/设备变体/size/SHA-256 不一致时拒绝；正确区分 CURRENT、UPDATE、SAME_CODE、DOWNGRADE_BLOCKED、INVALID。
  - 验证：解析、边界校验和降级保护单元测试。
  - 文件：`ResolvedUpdateTarget.java`、`UpdateInstallEligibility.java`、`UpdateHistoryRepository.java`。

- [ ] 抽取并固化通用更新请求
  - 验收：最新版本和历史版本都通过不可变 `UpdateRequest` 进入同一备份、下载、回退、校验和安装流程；异步期间不会被通道切换覆盖。
  - 验证：请求状态和 resume 恢复测试。
  - 文件：`Updater.java`、`UpdateRequest.java`、`UpdateListener.java`。

- [ ] 扩展更新弹窗入口和回调
  - 验收：两端都有独立历史按钮；下载态不可打开。
  - 验证：布局测试和手工点击。
  - 文件：`UpdateListener.java`、`Updater.java`、两套 `UpdateDialog.java` 与 layout。

- [ ] 实现 Mobile 历史弹窗
  - 验收：触摸切换、展开、重试、加载更多、在线更新确认和关闭均可用。
  - 验证：Mobile 单元/布局测试和设备验证。

- [ ] 实现 Leanback 历史弹窗
  - 验收：遥控器可完成选择目标、确认、下载、取消和返回，焦点不逃逸。
  - 验证：焦点测试和 TV/模拟器验证。

- [ ] 增加单目标进度与安装安全校验
  - 验收：历史目标进入准确的进度 UI；下载后检查文件大小、SHA-256、包名、versionCode 和签名摘要，再调用系统安装器。
  - 验证：伪造清单、错误 APK、低版本和回退下载测试。
  - 文件：两套 `UpdateDialog`、`Updater.java`、`FileUtil.java` 或新增校验帮助类。

- [ ] 补齐资源与回归验证
  - 验收：英文、简体中文、繁体中文无缺失；最新版本与历史版本在线更新行为均无回归。
  - 验证：两种 flavor 的测试与 Debug 构建。

## 17. 边界

### 始终执行

- stable/beta 使用同一套资产命名规则。
- 网络解析在后台线程执行，UI 更新回主线程。
- 所有异步结果进行生命周期或 generation 校验。
- 新增字符串同时维护默认、简体中文和繁体中文。
- 历史目标必须经过 manifest、资产、完整性、包名、versionCode 和签名校验。
- 最新与历史目标都复用同一更新请求、备份、下载和安装流程。
- 下载前阻断低于当前 versionCode 的目标，不用“下载后失败”代替边界判断。

### 待评审的后续扩展（不阻塞首版）

- 首版不展示缺少当前设备资产的 Release；如果产品需要完整的发布归档，可另做“不可安装记录”视图。
- 首版只使用 10 分钟内存缓存；只有在用户明确需要跨进程/离线浏览时，才评估磁盘缓存及清理策略。
- 首版不接入 CNB 历史 API；若 GitHub 限流成为实际问题，再评估列表级备用源和一致性校验。
- 如果产品确实需要低 `versionCode` 降级，另立卸载/重装与数据恢复方案。

### 禁止

- 在客户端内置 GitHub/CNB 私有 Token。
- 为了降级而自动卸载应用。
- 绕过 APK 签名、SHA-256 或 Android 安装校验。
- 让 UI 或清单直接指定未 allowlist 的下载 scheme/host。
- 历史页切换通道时隐式修改用户更新通道设置。

## 18. 方案取舍

### 方案 A：GitHub Releases API + 独立历史弹窗（推荐）

优点：

- 复用现有数据与网络能力。
- 无服务端改动。
- stable/beta 天然共存。
- 与当前更新弹窗职责隔离。
- 选定目标后可复用现有在线更新链路，不需要第二套安装器。

缺点：

- 依赖 GitHub API 可用性与匿名限流。
- 只能展示仍保留在 GitHub Release 中的历史。
- 低于当前 versionCode 的目标仍受 Android 平台降级限制。

### 方案 B：把所有历史版本直接嵌入现有更新弹窗

不推荐。现有弹窗已包含通道切换、发布说明、下载进度、取消和 TV 焦点逻辑；再加入长列表会增加高度、状态和焦点复杂度，并放大近期修复过的 Leanback 布局风险。

### 方案 C：新增自有历史版本索引服务

首版不推荐。虽然可屏蔽 GitHub 限流并聚合镜像，但需要服务端部署、缓存、监控和数据同步，而当前 GitHub Release 已能同时满足历史列表和选定版本在线更新。

### 方案 D：支持低 versionCode 的强制降级

暂不采用。普通应用无法可靠地直接覆盖低 versionCode；卸载/重装会带来数据丢失、签名和恢复失败风险，应作为单独功能立项。

## 19. 待评审结论

基于“历史版本必须支持在线更新”的补充需求，建议按以下决策进入实现：

1. 历史列表条目支持对符合资格的目标在线更新。
2. GitHub Releases API 是历史列表和历史 manifest 的唯一必需数据源。
3. 使用一个独立历史按钮和一个独立历史弹窗，在线更新开始后切换到单目标进度模式。
4. 历史弹窗始终提供 stable/beta 两个通道。
5. 只展示包含当前设备清单资产的 Release；选中目标后严格校验 manifest 与 APK。
6. 目标 versionCode 低于当前时只读并禁用更新；不自动降级。
7. 内存缓存 10 分钟，不新增数据库。

以上决策确认后，再进入实现计划与编码阶段。
