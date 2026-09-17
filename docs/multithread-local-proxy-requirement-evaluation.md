# 壳内原生多线程本地代理需求评估

> 评估日期：2026-08-10
> 评估范围：`F:\Workspace\webtv3\webhtv`、`F:\Workspace\CatVodSpider\app\src`，以及公开主流开源实现
> 结论：**技术可行，建议立项，但不建议直接复制 CatVodSpider 的 Socket 实现，也不建议引入 aria2、Gopeed、Netty 或 Ktor。应复用现有 NanoHTTPD + OkHttp，新增一个仅监听回环地址、资源有界、可取消的独立分段 Range 代理服务。**
> 本次修订：默认端口改为固定的 `17575`，便于用户和外部工具直接对接；自动分配保留为用户主动选择的模式。线程、队列、并行数、分片数/大小和资源预算改为用户可配置，推荐区间只做软提示，不再写死偏小上限。
> 实现状态更新（2026-08-11）：首版壳级多线程代理、普通播放器与沉浸融合播放器自动接入、手机与电视播放页多线程按钮、全局线程/分片配置及按域名多对一覆盖规则已在当前工作区实现；默认全局并行线程数为 `10`、分片数为 `256`，功能默认关闭。保存时可选择立即重新加载当前视频生效，或从下一次播放开始生效。
> 实测更新（2026-08-12）：已在 emulator-5558 使用 Quark 5.06 GiB RAW 慢源完成 A/B。对照 CatVodSpider Java 代理与 goProxy Go 代理后，确认用户可见的逻辑分片需要进一步细化为固定传输块；现采用 100 字节首块、1 MiB 常规传输块和 10 路并发。修复前同源黑屏超过 10 分钟，修复后约 5 秒仍在缓冲、15 秒内出现首帧、30 秒持续播放，媒体会话位置与缓冲均推进，未见 Range 校验失败、超时或播放异常。

## 1. 一页结论

本轮结论来自静态代码审查、参考实现审查和真实慢源 A/B。Quark 5.06 GiB RAW 样本已验证固定小传输块调度能显著缩短首帧等待；后续仍需在更多域名、协议和设备上持续记录吞吐、卡顿、CPU、PSS 与失败率。

### 1.1 可行性

- **可行性：高。** 当前工程已经具备 NanoHTTPD 2.3.1、OkHttp 5.4.0、Range 响应处理、本地代理、播放路由注册、HLS/DASH 本地代理和固定线程预加载等基础设施。
- **实现复杂度：中高。** “服务端能同时处理多个请求”和“单个视频通过多个上游 Range 请求并行下载”是两件事。前者当前已经具备，后者才是本需求的核心新增能力。
- **推荐工期：8～13 个工程日。** 其中真正困难的不是起一个端口，而是 Range 正确性、顺序回写、背压、取消、内存上限、端口切换和播放预加载协同。
- **建议默认端口：17575。** 该值与 CatVodSpider 的 5575～5577、当前主服务的 9978～9998 范围分离，用户和外部工具可按 `http://127.0.0.1:17575` 直接对接。端口仍允许用户修改，也可主动切换为自动分配；壳内调用方继续通过能力 API 获取实际端口，避免用户改端口后失效。

### 1.2 推荐方案

1. 新建独立的 `MultiThreadProxyServer`，监听 `127.0.0.1:<port>`，不要复用当前可被局域网访问的主服务端口。
2. 使用现有 NanoHTTPD 负责 HTTP 解析和响应，替换默认“每连接新建线程”的 `DefaultAsyncRunner`，使用**用户可配置容量的有界线程池和有界队列**。这里的“有界”表示容量明确且可观察，不等于写死一个很小的上限。
3. 使用现有共享 `OkHttp.player()` 发起上游请求；服务端工作线程、全局 Range Worker、每会话并行数、分片模式、分片数/大小等均支持用户配置。当前播放器 UI 的全局默认值为并行线程数 `10`、固定数量分片 `256`，运行配置模型仍保留自动/数量/大小模式和高级资源预算。
4. 使用有界的顺序重排缓冲区，边下载边按偏移写给播放器；客户端断开时立即取消全部上游 Call。
5. 仅对满足条件的渐进式大文件启用并行：HTTP/HTTPS、单 Range、长度已知、上游正确返回 206、资源校验器稳定、文件大于阈值。
6. HLS/DASH 继续走现有 `MpvHlsProxy`，不要把分片协议再拆成字节 Range，避免重复并发和缓存逻辑。
7. 新增独立能力 API，如 `Proxy.getMultiThreadPort()` / `Proxy.getMultiThreadUrl()`；保留现有 `Proxy.getPort()` / `/proxy` 行为，避免破坏现有 Java、JS、Python 爬虫。

### 1.3 明确不推荐

- 不直接复制 `CatVodSpider/JavaProxyServer.java`。
- 不在当前主 `Nano` 的 `/proxy` 上叠加新协议；该路径已有爬虫回调语义，且主服务默认绑定所有网卡。
- 不使用 `Executors.newCachedThreadPool()` 承载长连接和分块任务。
- 不为每个请求创建新的 `OkHttpClient`。
- 不引入 aria2/Gopeed 原生二进制或守护进程；许可证、APK 体积、ABI、生命周期和进程通信成本都不合算。
- 不以“线程数”作为成功指标；应以吞吐、首帧、卡顿、CPU、PSS 和失败率作为验收指标。

## 2. 需求口径与边界

本评估将需求解释为：

- 壳进程启动后提供一个本地 HTTP 代理监听端口；
- 默认监听固定回环端口 `17575`，用户可以修改为其他固定端口或主动选择自动分配；线程数、并行数、分片数/大小、队列和内存预算等运行参数均可配置；
- 播放器或爬虫把原始媒体 URL 交给该端口；
- 对支持字节范围的渐进式媒体，壳使用多个并行 Range 请求下载，再按原始字节顺序流式返回给播放器；
- 不要求 App 被系统杀死后端口仍然常驻。

### 2.1 两种“多线程”必须区分

| 维度 | 含义 | 当前状态 |
|---|---|---|
| 多客户端并发 | 多个本地 HTTP 请求可同时被处理 | **已有**。NanoHTTPD 默认每个连接创建一个线程 |
| 单资源并行 | 一个视频被拆成多个上游 Range 并行下载并有序回写 | **已实现首版壳级能力**；播放器可按需接入独立回环代理 |

因此，只把 NanoHTTPD 换成线程池并不能产生下载加速；它只会让并发资源使用更可控。

### 2.2 建议非目标

首版不包含：

- 系统级 HTTP/SOCKS 正向代理或 `CONNECT` 隧道；
- 局域网开放代理；
- 开机自启、独立进程、前台常驻服务；
- BT、磁力、ed2k；
- 磁盘断点下载管理器；
- 对 HLS/DASH 再做字节级多连接切片；
- 任意数量线程、任意大小内存块的高级用户调参。

如果“运行用户自定义代理端口”实际指的是**连接外部 HTTP/SOCKS 代理的端口**，则它与现有 `ShellProxy` 出站代理规则属于另一需求，不能与本地加速代理混为一谈。

## 3. 当前工程现状

### 3.1 主服务已经支持并发，但并发无上限

- `app/src/main/java/com/fongmi/android/tv/server/Server.java:66-79` 在 9978～9998 中扫描可用端口，启动 `Nano`，再写入 `com.github.catvod.Proxy`。
- `app/src/main/java/com/fongmi/android/tv/server/Nano.java:31-58` 继承 NanoHTTPD，并在同一端口承载管理页、文件、M3U8、播放记录和 `/proxy` 等多种路由。
- Gradle 当前使用 NanoHTTPD 2.3.1。其官方 Javadoc 明确说明：默认策略为每个进入的请求创建一个 daemon Thread。
- 这意味着“并发处理请求”已经存在，但慢连接或恶意连接可能产生无界线程。官方 Wiki 也专门给出了使用固定线程池替代默认 Runner 的示例。

### 3.2 当前 `/proxy` 是爬虫回调入口，不是通用分段下载器

- `app/src/main/java/com/fongmi/android/tv/server/process/Proxy.java` 把请求参数交给 `BaseLoader.get().proxy(params)`，再把爬虫返回的流包装为 NanoHTTPD 响应。
- `app/src/main/java/com/fongmi/android/tv/api/loader/BaseLoader.java:125-130` 根据 `siteKey`、`do=js`、`do=py` 等分派到 Java/JS/Python 爬虫。
- `catvod/src/main/java/com/github/catvod/Proxy.java:5-20` 只保存主服务实际端口，并生成现有 `/proxy` URL。
- 因此，新代理若继续使用主端口的 `/proxy`，会产生协议和安全边界冲突。

### 3.3 已有一个更值得复用的壳内代理范式

`app/src/main/java/androidx/media3/mpvplayer/MpvHlsProxy.java` 已具备：

- `super("127.0.0.1", 0)`：只绑定回环地址并使用系统分配端口；
- 复用 `OkHttp.player()`；
- 注册 `PlaybackRouteRegistry.AppOwner.HLS_PROXY`；
- Range、Content-Range、流式响应、磁盘缓存、预加载和取消逻辑；
- 预加载使用可控的 `newFixedThreadPool(threads)`。

新代理应复用这里的生命周期、路由归属、响应头和资源治理思路，而不是从裸 `ServerSocket` 重新实现 HTTP。

### 3.4 播放路由需要同步扩展

- `PlaybackRouteRegistry` 目前只有 `MAIN_SERVER` 和 `HLS_PROXY`。
- `PlaybackRoute` 会把已注册端口识别为 `APP_LOCAL_SERVICE`，未注册回环端口识别为外部代理。
- 新端口应新增独立 Owner，例如 `MULTI_THREAD_PROXY`，否则诊断、缓冲策略、错误文案和预加载策略无法区分它。
- 尤其要防止“播放器外层预加载线程数 × 本地代理内层 Range 线程数”的并发乘法。例如 4 个预加载任务各自再开 4 个 Range，会瞬间变成 16 个上游请求。

### 3.5 现有硬编码端口需要纳入迁移检查

`app/src/main/java/com/fongmi/android/tv/server/process/M3u8.java:151-153` 仍生成 `127.0.0.1:9978`。虽然注释说明这是兼容路径，但任何新增固定/自定义端口能力都必须建立统一的端口提供者，并扫描所有硬编码本地端口。

## 4. CatVodSpider 参考实现评估

### 4.1 可借鉴的部分

`F:\Workspace\CatVodSpider\app\src` 提供了完整的产品原型：

- CatVodSpider 参考实现默认外部端口 5575；
- Java 后端 5577，Go 后端默认 5576；
- `ProxyRelayServer` 保持前门端口稳定，并可切换后端；
- 用户端口保存、合法化和运行时切换；
- 新端口先启动成功再停止旧端口，切换失败继续保留旧服务；
- `/health` 健康检查；
- 上游忽略 Range 返回 200 时降级为单连接流式转发；
- 其 Java 实现限制最多 4 个线程、每块最多 4 MiB，并对截断块做检查。

其中最值得复用的是：**稳定前门、两阶段端口切换、健康检查、206 校验以及 200 降级。**

### 4.2 不应直接复制的部分

| 问题 | 现状 | 影响 |
|---|---|---|
| 监听范围 | `new InetSocketAddress(port)` 默认绑定所有网卡 | 局域网内形成无认证的任意 URL 代理，存在开放代理和 SSRF 风险 |
| HTTP 实现 | 手工读取请求行和 Header，只处理简单 GET | HEAD、超长 Header、慢请求、连接复用、异常报文和协议边界不完整 |
| 线程池 | 客户端处理和分块均使用 `newCachedThreadPool()` | 无界线程，长连接和故障重试时不可控 |
| 内存 | 最多 4 块 × 4 MiB，整块读入 `byte[]` 后再顺序写 | 单客户端约 16 MiB 批次缓冲，多客户端线性放大，且有额外临时峰值 |
| 背压 | 一批全部下载完成后才写给播放器 | 慢播放器不能及时反压上游，首块后仍可能积压大量内存 |
| OkHttp | 每个代理请求新建 `OkHttpClient` 和 TLS 上下文 | 丢失跨请求连接池收益，增加线程、TLS 和对象开销 |
| TLS | 自定义 TrustManager 和 HostnameVerifier 全部放行 | 扩大中间人攻击面，也重复了壳内网络栈 |
| 重试 | 外层 3 次重试调用内层最多 3 次重试 | 单块最坏可能产生 9 次尝试，故障时放大流量和等待 |
| 取消 | 未建立客户端断开到所有上游 Call 的统一取消链 | 退出播放后后台请求可能继续占用网络和线程 |
| Relay | 每个连接至少两个方向复制任务 | 只有一个 Java 后端时是额外线程、拷贝和故障点 |
| 测试 | 仅覆盖 Range 工具函数、内存上限和截断读取等少量单测 | 缺少真实 HTTP、并发、端口切换、断连、响应头和压力测试 |

结论：CatVodSpider 适合作为**接口行为原型**，不适合作为壳级生产实现直接搬运。

## 5. 主流开源项目对比

以下 GitHub 数据为 2026-08-10 调研快照，Star 仅用于判断生态热度，不代表技术质量。

| 项目 | 热度 / 许可证 / 活跃度 | 解决的问题 | 本需求结论 |
|---|---|---|---|
| NanoHttpd/nanohttpd | 7,230 Star；BSD-3-Clause；最近推送 2023-07-25 | 轻量内嵌 HTTP 服务；默认每请求一线程；支持替换 AsyncRunner | **继续使用**，但必须换成有界 Runner |
| square/okhttp | 47,037 Star；Apache-2.0；最近推送 2026-08-09 | Android/JVM HTTP 客户端、连接池、取消、HTTP/2 | **继续使用现有依赖**，不要每请求新建客户端 |
| danikula/AndroidVideoCache | 5,489 Star；Apache-2.0；最近推送 2023-03-14 | Android 本地视频缓存代理；回环地址、随机端口、固定 8 线程 | 借鉴本地绑定和生命周期；它不是单资源多连接加速器 |
| lingochamp/okdownload | 5,245 Star；Apache-2.0；最近推送 2022-11-04 | Android 文件分块下载、断点续传、任务存储 | 借鉴分块和恢复思想；文件落盘模型不适合边下边播，且维护较弱 |
| aria2/aria2 | 41,685 Star；GPL-2.0；最近推送 2026-06-25 | 成熟的多源、多连接、RPC 下载器 | 能力强，但原生二进制、GPL、守护进程、ABI 和文件下载模型不适合直接嵌入 |
| GopeedLab/gopeed | 25,731 Star；GPL-3.0；最近推送 2026-07-27 | Go 下载核心 + Flutter UI，支持 HTTP/BT/磁力/ed2k | 不引入；许可证、Go Runtime、包体和功能范围远超需求 |
| monkeyWie/proxyee-down | 24,689 Star；Apache-2.0；已归档 | Java/Netty 多连接下载器，默认多连接、重试和限速 | 只作算法参考；作者已停止维护并指出 Java 客户端包体和内存问题 |
| ktorio/ktor | 14,499 Star；Apache-2.0；活跃 | Kotlin 服务框架 | 只能解决服务端并发，不能直接解决 Range 聚合；引入成本过高 |
| netty/netty | 35,026 Star；Apache-2.0；活跃 | 高性能事件驱动网络框架 | 对 1～2 个播放器连接明显过重，且仍需自行实现完整 Range 引擎 |

### 5.1 行业实现的共同点

成熟实现并不是简单“多开几个线程”，而是共同具备：

- 先探测长度、Range 支持和资源校验器；
- 有界连接数和全局任务数；
- 块级重试预算，而不是无限重试；
- 块顺序重排；
- 客户端速度驱动的背压；
- 断点/取消传播；
- 对 200、206、416、截断、重定向和资源变化分别处理；
- 失败时回退单连接，而不是直接让播放失败；
- 线程数、块大小和阈值通过实测调优。

## 6. 推荐架构

```text
Java / JS / Python Spider 或 Player
        │
        │ GET/HEAD http://127.0.0.1:17575/proxy?... + Range（默认配置）
        ▼
MultiThreadProxyServer (NanoHTTPD, loopback only)
        │
        ├── /health / capabilities
        ├── BoundedAsyncRunner（有界连接处理）
        └── ProxySessionController（会话、取消、限额）
                │
                ├── Probe + RangePlanner
                ├── Fixed Range Worker Pool
                ├── Shared OkHttp.player() + Call tags
                └── Ordered Bounded Buffer
                         │
                         ▼
                    Player Response
```

### 6.1 组件职责

#### `MultiThreadProxyServer`

- 只监听 `127.0.0.1`；如需要 IPv6，可另行支持 `::1`。
- 使用 NanoHTTPD 处理请求解析和响应，避免手写 HTTP。
- 首版只暴露版本化的 `GET`、`HEAD`、`/v1/health`、`/v1/proxy` 和可选 `/v1/capabilities`；无版本路径只用于受控兼容，不作为新调用方契约。
- 使用配置快照创建固定大小线程池和有界队列；线程数与队列容量由用户配置或自动策略给出，拒绝或过载时返回 503。

#### `ProxyPortController`

- 保存端口模式（固定/自动）、配置端口、实际端口和运行状态；默认模式为固定，默认配置端口为 `17575`。
- 应用启动时默认绑定 `127.0.0.1:17575`；用户主动选择自动模式时才绑定 `127.0.0.1:0` 并读取系统分配的实际端口。服务只保证壳进程存活期间可用。
- 修改固定端口、切换端口模式或修改服务线程参数采用“两阶段切换”：先按新配置绑定并健康检查，再发布新能力，最后停止旧实例。
- 默认或自定义固定端口冲突时不得静默改绑或自动回退，否则外部调用方会连接到错误端口；应保留旧服务和旧配置生效状态，并向 UI 返回明确错误，允许用户改端口或主动选择自动模式。

#### `SegmentedRangeEngine`

- 解析客户端 Range，只支持单一字节范围；首版拒绝 multipart ranges。
- 先发送小范围探测请求，确认状态码、Content-Range、总长度、ETag/Last-Modified。
- 按用户选择的自动、分片数或分片大小策略规划目标范围；分片任务按窗口惰性创建，工作线程并行下载，写线程按 offset 顺序输出，避免因极大分片数一次性分配任务和缓冲。
- 每个块验证状态码、Content-Range、长度和资源校验器。
- 客户端断开或会话结束时取消所有 Call、清空缓冲并释放许可。

#### `ProxyRuntimeConfig`

- 使用不可变配置快照，至少包含端口模式/端口、服务线程数、连接队列、全局 Range Worker、每会话并行数、分片模式、分片数、分片大小、最大加速会话数、重排窗口、内存预算、重试、超时和启用阈值。
- 用户持久化配置是资源策略来源；URL 查询参数只作为兼容提示，不得绕过用户设置扩大线程、队列或内存预算。
- 配置层只做类型和不变量校验，不设置偏小的任意上限；超出推荐区间时给出软警告，不静默夹紧。
- 能力 API 同时公开 `configured`、`effective` 和 `recommended`，便于 UI、爬虫和诊断页面区分用户输入、当前实际值与推荐值。

#### `ProxyCapability`

- 保持现有 `Proxy.getPort()` / `Proxy.getUrl()` 不变。
- 新增独立接口，例如：
  - `getMultiThreadPort()`
  - `getMultiThreadUrl()`
  - `isMultiThreadProxyReady()`
  - `getMultiThreadProxyToken()`（如采用能力令牌）
- Java、QuickJS、Chaquopy 均通过同一能力来源获取实际地址。

## 7. 默认策略建议

以下值只作为首轮默认值和软推荐区间，最终以设备基准测试为准。除协议/类型不变量外，不使用偏小的硬编码上下限，也不对用户输入做静默夹紧：

| 参数 | 建议默认值 | 校验与生效说明 |
|---|---:|---|
| 监听端口 | 固定 `17575` | 用户可改为 `1024～65535` 的其他固定端口；也可主动选择自动模式（配置值 `0`） |
| 首版启用策略 | 默认关闭（实验开关） | P4 真机指标达标后再考虑默认 `auto`，发布前保留一键回退直连 |
| 服务连接线程数 | 8 | 用户可配置正整数；推荐 4～32，仅软警告，不作为硬上限 |
| 连接等待队列 | 64 | 用户可配置正整数；队列始终有界，过载返回 503 |
| 全局 Range Worker | 8 | 用户可配置正整数；推荐 4～32，实际并发还受会话任务数约束 |
| 每会话 Range 并行数 | 4 | 用户可配置正整数；推荐 2～16，不再固定最大 4 |
| 分片模式 | `auto` | 支持自动、按分片数、按分片大小三种模式 |
| 分片数 | `auto` | 指定模式下接受正整数；按资源长度惰性规划，不预创建全部任务 |
| 分片大小 | 1 MiB 基线 | 用户可配置正字节数；推荐 256 KiB～16 MiB，仅软警告 |
| 全局加速会话数 | 2 | 用户可配置正整数；播放和预取共享该预算 |
| 顺序缓冲窗口 | 每会话名义值 `2 × 并行数` 个块 | 用户可配置正整数；effective 值必须按剩余分片和全局内存预算动态缩小 |
| 代理内部缓冲预算 | 20 MiB 基线 | 用户可配置正字节数；超限阻塞生产者，不继续分配 |
| 单块重试 | 2 次 | 用户可配置非负整数；全会话仍设总重试预算和退避 |
| 连接/读取超时 | 10s / 30s 基线 | 用户可配置正时长；流式读取需结合取消，不能无限挂起 |
| 启用文件阈值 | 32 MiB 基线 | 用户可配置非负字节数；小文件默认直接转发 |

### 7.1 参数边界与生效语义

- 端口以外的计数参数首先要求可解析且满足非负/正数语义。只有协议范围、整数溢出、集合容量或明确的资源安全需要才设置硬边界；硬边界应集中定义、留有足够余量，不能散落成“最多 4”一类偏小业务限制。端口默认 `17575`，固定模式允许 `1024～65535`，配置值 `0` 只表示用户主动选择的自动模式。
- 不设置“线程最多 4”“分片最多 16”这类偏小硬上限。UI 使用可输入数字的编辑框而不是带窄范围的滑条，并显示推荐区间、资源风险和“恢复自动”入口。
- 对极端但类型合法的配置，不应一次性创建对应数量的线程、分片对象或缓冲区；线程池按任务惰性增长，分片按窗口惰性调度，内存由独立预算和背压控制。
- 违反协议/类型/宽安全边界的值直接拒绝并保留上一份有效配置；位于硬边界内但超出推荐区间的值允许保存，同时在 UI、日志和能力 API 中给出警告。禁止静默夹紧，否则用户看到的配置与实际行为会不一致。
- 端口、服务连接线程数和连接队列修改需要重建本地服务并两阶段切换；每会话并行数、分片和重试等设置对新会话生效，现有播放会话继续使用创建时的配置快照；全局 Range Worker、会话数和内存预算通过受控调整执行器/许可器生效，不取消已经接纳的任务。
- 请求级 `thread`、`shards`、`chunkSize` 只用于兼容旧调用方或在用户策略内缩小资源，不能把实际资源扩大到持久化用户配置之外。
- 重排窗口的 effective 块数按 `min(configuredWindow, remainingShards, floor(availableBufferBytes / chunkSize))` 计算；结果不足以维持并行时降低并行度或回退单连接，不能突破全局缓冲预算。

### 7.2 何时启用并行

仅当全部满足时启用：

- 客户端方法为 `GET`；`HEAD` 只返回元数据或用于探测，不创建分片任务；
- URL 为 HTTP/HTTPS，客户端请求是可解析的单一字节 Range，不是 multipart Range；
- 探测和所有分片请求显式发送 `Accept-Encoding: identity`，上游未返回会改变字节偏移语义的 `Content-Encoding`；
- 上游对探测 Range 返回可验证的 206，`Content-Range`、实际 body 长度和总长度一致；
- 总长度已知、大于阈值且所有 offset/length 计算使用 `long` 并经过加减法溢出检查；
- 优先要求强 ETag；只有 Last-Modified 时采用更保守策略。每个分片携带 `If-Range`，校验器变化、返回 200/412 或范围不一致时立即取消并降级；
- 不是 HLS/DASH 清单或已分段媒体路径；
- 当前全局并发和内存预算允许。

任何条件不满足，应自动降级为单连接流式转发。降级后不得把已经取得但身份不确定的分片字节拼接进响应。

### 7.3 HTTP/2 的现实限制

OkHttp 可能把多个 Range 请求复用在同一 HTTP/2 连接上。这样仍然有并行流，但不一定获得多个 TCP 拥塞窗口，因此“4 个线程”不等于“4 条物理连接”。

建议先使用共享 OkHttpClient 和默认协议，按吞吐验收；只有真实测试证明特定来源在 HTTP/1.1 多连接下显著受益时，才考虑受控地为该会话禁用 HTTP/2或使用隔离连接池。不能为了名义上的“多连接”默认牺牲连接复用、TLS 成本和服务器友好性。

### 7.4 Seek、重复 Range 与取消模型

- 每个进入的播放请求创建独立 `ProxyTransfer`，拥有自己的上游 Call 集合、顺序写入器和取消作用域；禁止使用全局“当前下载任务”单例。
- 播放器拖动产生新 Range 时，新请求从目标 offset 独立规划。旧连接关闭、写入失败或播放 generation 被替换后，必须在 1 秒内取消旧请求的全部分片。
- 短时间重叠的旧/新请求可以并存，但共同受全局 Worker、会话数和内存预算约束；不能因为一次 seek 把并发乘倍。
- 只允许在 URL、总长度和强校验器一致时复用探测元数据；不同签名 URL、校验器变化或重定向目标变化时不得复用分片字节。

## 8. 端口和 API 设计

### 8.1 端口规则

- 默认采用固定端口 `17575`，默认地址为 `http://127.0.0.1:17575`；该值必须集中定义为单一常量并写入设置默认值、能力 API 和对接文档，不能在多处散落硬编码。
- 用户可将固定端口修改为 `1024～65535`；`5575` 仅作为显式旧 CatVod 兼容选项，与普通固定端口使用相同的冲突和安全检查。
- 用户也可主动选择自动模式（配置值 `0`）；此时服务绑定 `127.0.0.1:0` 并通过能力 API 发布系统分配的实际端口，但该模式不适合依赖固定地址的外部工具。
- 拒绝当前主服务端口、当前 HLS 代理端口和已被其他进程占用的固定端口。
- 默认或自定义固定端口冲突时，**不要静默扫描或自动回退到其他端口**；应保留旧服务与旧有效配置并提示失败，让用户明确选择新固定端口或自动模式。
- Java、QuickJS、Chaquopy、Player 和诊断页面必须读取能力 API；外部工具在默认配置下可以直接使用 17575，若用户改过端口则应读取设置/能力信息。

### 8.2 推荐接口

```http
GET /v1/health
200 application/json
{
  "schemaVersion": 1,
  "ready": true,
  "port": 17575
}
```

`/v1/health` 可以不鉴权，但只返回最小存活信息，不暴露活动会话、用户配置、目标 URL 或令牌状态。完整能力信息必须鉴权：

```http
GET /v1/capabilities
X-WebHtv-Proxy-Token: <process-capability-token>
200 application/json
{
  "schemaVersion": 1,
  "configRevision": 1,
  "auth": "capability-token",
  "portMode": "fixed",
  "configuredPort": 17575,
  "port": 17575,
  "activeSessions": 1,
  "config": {
    "serverWorkers": {"configured": 8, "effective": 8, "recommended": {"min": 4, "max": 32}},
    "rangeWorkers": {"configured": 8, "effective": 8, "recommended": {"min": 4, "max": 32}},
    "rangeConcurrency": {"configured": 4, "effective": 4, "recommended": {"min": 2, "max": 16}},
    "shardMode": "auto",
    "shardCount": "auto",
    "chunkSize": 1048576,
    "maxSessions": 2,
    "bufferBudget": 20971520
  },
  "warnings": []
}
```

```http
GET /v1/proxy?url=<percent-encoded-url>&thread=auto&shards=auto&chunkSize=auto
X-WebHtv-Proxy-Token: <process-capability-token>
Range: bytes=0-
User-Agent: ...
Cookie: ...
Referer: ...
```

建议：

- `thread`、`shards` 和 `chunkSize` 可以保留 CatVod 兼容参数，默认使用 `auto`；它们是请求级提示，只能在持久化用户策略内缩小资源，不能覆盖用户配置扩大预算，也不能被静默夹紧成另一个值。
- `/v1/proxy`、`/v1/capabilities` 和目标注册接口必须要求进程级 capability token。令牌每次进程启动轮换，不写入备份、不持久化、不出现在普通日志；鉴权失败使用固定时间比较并限速。`/v1/health` 仅允许返回最小存活信息。
- 首选由壳内 Java/QuickJS/Chaquopy API 注册目标并返回短期、不可猜测的 `/v1/stream/<opaque-session-id>` 播放 URL，避免把原始 URL、Cookie、Authorization 和全局令牌长期放在查询参数中。`url=` 形式只作为受控兼容接口。
- 上游 Header 采用明确白名单，例如 User-Agent、Cookie、Referer、Origin、Authorization、Accept、Accept-Language；剔除 Connection、Host、Content-Length、Transfer-Encoding 等 hop-by-hop Header。
- 不接收未经校验的原始 Header 文本，防止 CRLF 注入。
- URL、Cookie、Authorization、签名查询参数、capability token 和 opaque session id 不得完整写日志；诊断只记录脱敏主机、状态码、字节区间、配置 revision 和关联 ID。

### 8.3 设置入口与持久化

- 在统一的 `Setting` / `Prefers` 配置源中保存带 `configSchemaVersion` 的原始用户值，由 `ProxyRuntimeConfig` 生成不可变运行快照和递增 `configRevision`；移动端、电视端、备份恢复、远程设置和能力 API 不得各自维护不同默认值。
- 基础设置至少提供：启用多线程代理、端口模式（固定/自动，默认固定）、固定端口（默认 17575）、每会话并行数、分片模式（自动/数量/大小）、分片数和分片大小。
- 高级设置提供：服务连接线程数、连接队列、全局 Range Worker、最大加速会话数、重排窗口、缓冲预算、重试、连接/读取超时和启用文件阈值。
- 数字项使用可直接输入的大范围编辑框，显示单位、当前有效值、推荐区间和风险提示；端口 UI 默认显示 `17575`。`auto` 通过明确的开关/选项表达，不用魔法数字混淆 UI；端口配置值 `0` 只在持久化和 API 中表示用户主动选择的自动模式，UI 显示“自动分配”。
- 保存前返回逐字段校验结果；保存后区分“立即调整全局预算”“仅对新会话生效”和“本地服务切换成功后生效”。端口/服务线程重建失败时恢复旧有效配置，不把失败值伪装为已生效。
- 备份与恢复必须包含非敏感设置和 schema 版本，但不得包含进程令牌、opaque session、活动 URL 或 Cookie；从旧版本升级时缺失字段使用固定默认端口 `17575`，不把 CatVod 参考值 5575 自动迁移成默认端口，除非用户之前明确保存过该值。

### 8.4 当前播放器接入实现（2026-08-11）

- 普通点播播放器和沉浸融合播放器均已接入同一个 `PlayerButtonSetting.MULTI_THREAD_PROXY` 按钮：手机版普通播放器使用 `mobile/view_control_vod_action.xml`，电视版普通播放器使用 `leanback/view_control_vod_action.xml`；沉浸融合模式分别使用 `view_control_vod_action_tmdb.xml` 的移动操作栏和 `activity_tmdb_detail.xml` 的宽屏/电视操作栏，由 `TmdbDetailActivity` 统一处理。按钮选中状态反映全局开关。
- 对话框提供全局开关、全局并行线程数、全局分片数和按域名覆盖文本；全局默认并行线程数为 `10`，默认采用数量分片模式，分片数为 `256`。
- 域名规则每行格式为 `domain1,domain2=threads,shards`，允许多个域名映射到同一组线程数和分片数。域名匹配使用规范化主机名，同时匹配精确域名及其子域名；未命中时返回全局配置。全局开关关闭时不启动代理，也不应用域名规则。
- 对话框提供提取当前域名按钮，从当前 `PlayerManager.getUrl()` 提取并规范化主机名，使用当前输入的线程数和分片数追加规则；无法提取或域名已存在时给出明确提示。
- 保存时先校验正整数、域名格式及重复域名。正在播放时弹出立即生效或下次播放选择：选择立即生效会应用并持久化配置，再重新加载当前媒体项并尽量保留播放位置和播放状态；选择下次播放只持久化配置，不重启当前代理和媒体项。没有活动播放地址时直接应用并持久化。
- 域名规则中的最大线程数用于扩大 Range Worker 池，避免个性配置被全局工作池意外截断。`PlayerManager` 在每次准备播放时重新应用已保存配置，因此延后生效的配置会在下一次播放时启用或关闭。
- `PlayerManager` 只为直连 HTTP(S) 渐进式媒体注册 opaque 本地播放 URL；HLS/M3U8、DASH/MPD、本地回环地址及非 HTTP(S) 地址继续走原路径。
- 目前播放器对话框只暴露普通用户需要的开关、线程、分片及域名覆盖；端口、队列、缓存预算和超时等高级运行参数仍由统一运行配置模型管理，不在本轮播放器 UI 中展开。

## 9. 安全与生命周期

### 9.1 必须仅绑定回环地址

当前主 `Nano` 使用 `super(port)`，服务还承担局域网管理能力。若把“任意 URL 下载”端点放在该服务上，会把设备变成局域网开放代理。因此新服务必须独立，并显式绑定 `127.0.0.1`。

即便绑定回环地址，Android 上其他 App 仍可能访问本机端口。任意 URL 代理必须使用进程级随机 capability token，服务启动时轮换；固定默认端口 `17575` 只解决寻址问题，不代表允许无令牌访问。为了兼容硬编码 5575 的旧爬虫，可以提供用户显式开启、可随时关闭的受控兼容模式，但该模式必须限制目标来源和能力范围。

### 9.2 目标 URL限制

- 只允许 `http` / `https`。
- 禁止 `file:`、`content:`、`data:`、`jar:` 等本地或特殊协议。
- 是否禁止访问私网地址要根据 NAS/AList 场景决定；不能简单全禁，但应记录为明确产品开关。
- 初始 URL、每次 DNS 解析结果和每一跳重定向都必须重新执行协议、地址和端口策略，防止 DNS rebinding 或重定向绕过；限制重定向次数。
- 不支持 CONNECT，不支持把该端口配置成通用浏览器代理。

### 9.3 进程生命周期

首版建议与 App 进程同生命周期，在 `App.startBackgroundServicesNow()` 或独立 Controller 中启动。若要求退到后台、进程被回收后仍可访问，必须升级为前台 Service、持久通知和电池策略，这是另一档需求，不应隐含在首版中。

## 10. 关键风险与对策

| 风险 | 等级 | 对策 |
|---|---|---|
| Range/Content-Range 错误导致花屏、跳播或数据错序 | 高 | 使用 RFC 9110 语义；强制 identity 编码；严格验证每块 offset、长度、If-Range 和校验器；MockWebServer 集成测试 |
| 多层预加载造成并发乘法 | 高 | 新增独立路由 Owner；外层预取对该代理限制为 1；由代理统一管理内部并发 |
| 慢客户端造成内存增长 | 高 | 有界重排缓冲、Semaphore/阻塞队列、全局内存预算 |
| 退出播放或 seek 后继续下载旧范围 | 高 | 请求级 Call 注册和 playback generation；客户端写失败/close/新 generation 后在 1 秒内 cancelAll |
| 端口冲突或切换造成旧 URL 失效 | 中高 | 两阶段切换、健康检查、能力版本号、失败保留旧服务 |
| 多连接反而变慢或触发 CDN 限流 | 中高 | 大文件阈值、auto 模式、吞吐探测、快速降级、主机级熔断 |
| HTTP/2 复用导致没有多 TCP 收益 | 中 | 以基准数据判断；不强制拆连接，必要时仅对白名单主机试验 HTTP/1.1 |
| 低端电视线程和 PSS 过高 | 高 | 用户可配置但始终有界的线程/会话/内存预算、推荐区间警告、按窗口惰性调度、低内存自动回退单连接 |
| 极端用户参数导致线程、任务或内存瞬时膨胀 | 高 | 不预创建全部线程/分片/缓冲；类型校验、配置快照、背压和运行时预算独立兜底；能力 API 暴露 effective 值与警告 |
| 固定端口被其他 App 调用、令牌泄漏或 SSRF | 高 | 独立回环监听、强制 capability token、短期 opaque session、协议/地址/重定向与 Header 白名单、鉴权限速和日志脱敏 |
| 新 Owner 引发枚举 switch 漏改 | 中 | 编译期穷举 + PlaybackRoute、PlayerManager、缓冲和诊断单测 |

## 11. 测试和验收标准

### 11.1 单元测试

- Range：`0-`、`100-199`、`-500`、越界、反向、溢出、多 Range；
- 206/200/416 映射；
- Content-Range 与实际 body 长度不一致；gzip/br 压缩响应、`Accept-Encoding: identity` 和 Content-Encoding 拒绝/降级；
- 强/弱 ETag、Last-Modified、If-Range、分片间校验器变化和返回 200/412；
- 块规划、顺序重排、内存许可和重试预算；
- 固定默认端口 `17575`、自定义固定端口合法化、冲突不静默回退、可选自动端口 `0`、实际端口发布和两阶段切换；
- 线程、队列、并行数、分片数/大小和内存预算的配置解析：最小合法值、接近宽安全边界的合法值、越界值拒绝、超推荐值告警且不静默夹紧；
- 极大分片数采用惰性窗口调度，不按配置值一次性创建任务或缓冲；
- Header 白名单、最小无鉴权 health、受保护 capabilities、capability token、opaque session 生命周期、URL/重定向/DNS 地址策略、鉴权限速和日志脱敏；
- `configSchemaVersion` 迁移、`configRevision` 递增、旧备份恢复和敏感运行态不进入备份；
- 路由 Owner 与外层预加载线程限制。

### 11.2 MockWebServer 集成测试

- 正常 206 分块并乱序完成，输出仍按字节顺序一致；
- 上游忽略 Range 返回 200，自动单连接降级；
- 未知 Content-Length；
- 某块超时、截断、500、429 和重定向；
- 客户端中途断开，所有上游请求被取消；连续 seek、短时重叠 Range 和 playback generation 切换不回写旧字节；
- 两个客户端并发且不超过用户配置的全局预算；
- 请求级 `thread`/`shards`/`chunkSize` 不能突破持久化用户策略；
- 每会话并行/分片/重试配置仅对新会话生效，旧会话保持原配置快照；全局 Worker/会话/内存预算受控调整且不取消已接纳任务；端口/服务线程配置通过重建服务生效；
- HEAD 不创建分片；Cookie、Referer、Authorization 和 Content-Type 按白名单透传；
- 端口重配期间旧会话不被粗暴终止。

### 11.3 设备和性能验收

建议至少覆盖低内存 armeabi-v7a 电视、主流 arm64 电视和手机：

- 30 分钟连续播放无线程、Socket、ResponseBody 和临时文件泄漏；
- 客户端断开后 1 秒内活跃上游 Call 归零；
- 代理内部缓冲不超过配置预算，线程总数稳定；
- 普通低延迟网络相对直连吞吐下降不超过 5%；
- 人工高 RTT/丢包场景的中位吞吐相对直连提升目标至少 20%，否则默认不启用；
- 首帧额外延迟目标不超过 300 ms；
- 不增加 HLS/DASH 卡顿率；
- 默认端口 `17575` 的稳定对接、自定义固定端口修改/冲突、可选自动端口、线程与分片配置、重启恢复、configured/effective 差异、schema/config revision 和健康状态均可观察。
- 首版实验开关默认关闭；仅在 P4 指标达标后灰度启用 `auto`。崩溃/ANR、PSS、失败率或卡顿率显著恶化时可立即回退直连，无需升级配置格式。

## 12. 分阶段落地与工期

| 阶段 | 内容 | 估算 |
|---|---|---:|
| P0 规格与基准 | 明确固定默认端口 17575、可选自动模式、令牌/opaque session、API 与配置 schema、宽安全边界、私网策略、默认启用门槛和常驻范围；建立直连与 CatVod 基准 | 0.5～1 天 |
| P1 本地服务骨架 | 独立回环 NanoHTTPD、可配置有界 Runner、配置快照、健康检查、固定默认端口、可选自动端口与两阶段切换 Controller | 1.5～2 天 |
| P2 Range 引擎 | identity Probe、If-Range、可配置分片规划、惰性窗口调度、并行下载、顺序缓冲、seek/request 取消、降级 | 3～5 天 |
| P3 壳内集成 | `Proxy` 能力、版本化 API、capability token/opaque session、Java/JS/Python、设置 UI/备份迁移、PlaybackRoute Owner、configured/effective/revision 诊断 | 1.5～2.5 天 |
| P4 验证 | 单测、MockWebServer、鉴权/SSRF/seek/压缩响应、默认/自定义固定端口、可选自动端口、宽参数边界、压力、真机性能与灰度回滚 | 1.5～2.5 天 |
| 合计 | 生产可用首版 | **8～13 天** |

如果只按 CatVodSpider 复制一个可运行 Demo，约 2～4 天可以出结果，但不应直接作为正式壳能力发布。

### 12.1 P0 必须锁定的决策

进入 P1 编码前必须把以下项目写成可测试常量或协议，不留给实现者临场猜测：

- 首版功能开关默认关闭；达到哪些吞吐、卡顿、PSS、失败率和崩溃指标后允许默认 `auto`。
- 服务线程、Range Worker、并行数、队列、分片、会话数和内存预算的**宽硬安全边界**；推荐区间不是硬边界。
- `/v1` API schema、错误码、`schemaVersion` / `configRevision` 迁移规则，以及无版本 CatVod 兼容接口的存续范围。
- capability token 的传递方式、opaque session TTL/一次性语义、鉴权失败限速和播放器无法附加 Header 时的方案。
- 私网/NAS/AList 开关、DNS rebinding、重定向重新校验、允许端口范围和日志脱敏规则。
- `auto` 分片算法、effective 重排窗口公式、低内存回退和 seek generation 的取消语义。

## 13. 建议的代码落点

建议新增或修改的模块边界：

```text
app/src/main/java/com/fongmi/android/tv/server/proxy/
  MultiThreadProxyServer.java
  ProxyPortController.java
  ProxyRuntimeConfig.java
  ProxyRuntimeConfigValidator.java
  ProxyCapabilityToken.java
  ProxySessionRegistry.java
  SegmentedRangeEngine.java
  RangePlanner.java
  OrderedChunkBuffer.java
  BoundedAsyncRunner.java

catvod/src/main/java/com/github/catvod/Proxy.java
app/src/main/java/com/fongmi/android/tv/player/PlaybackRouteRegistry.java
app/src/main/java/com/fongmi/android/tv/player/PlaybackRoute.java
app/src/main/java/com/fongmi/android/tv/player/PlaybackRouteCapabilities.java
app/src/main/java/com/fongmi/android/tv/setting/MultiThreadProxySetting.java
app/src/main/java/com/fongmi/android/tv/ui/dialog/MultiThreadProxyDialog.java
app/src/main/res/layout/dialog_multi_thread_proxy.xml
app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
app/src/mobile/res/layout/view_control_vod_action.xml
app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
app/src/leanback/res/layout/view_control_vod_action.xml
相关 Remote capability / tests
```

同时复查：

- `M3u8.java` 的 9978 兼容硬编码；
- PlayerManager 的本地代理错误分类和重试；
- Exo/Mpv 的目标缓冲与外层预加载策略；
- App 启动和销毁路径；
- Java、QuickJS、Chaquopy 暴露的代理能力。

## 14. 最终立项建议

### Go 条件

满足以下条件建议立项：

- 当前确有渐进式大文件在高 RTT/丢包网络下单连接吞吐不足的问题；
- 产品接受仅在 App 进程内运行；
- 默认只开放回环地址；
- 接受线程、并行数和分片参数由用户配置，并通过有界队列、惰性调度、内存预算和软警告控制风险，而不是写死偏小上限或允许无预算运行；
- 接受首版以实验开关默认关闭发布，并用性能与稳定性数据决定是否逐步默认启用 `auto`；必须保留无需迁移配置的一键直连回退。

### No-Go 条件

如果需求只是“看到 CatVod 用了多线程，所以壳也要有”，但没有真实慢源样本和吞吐基线，则不建议直接发布。多 Range 会增加服务器负载、TLS/请求开销、封禁概率和故障面，对低延迟 CDN 或 HTTP/2 来源可能没有收益。

### 推荐决策

**建议 Go，但按“固定默认端口 17575 的独立回环服务 + 强制 capability token/短期 opaque session + 用户可配置且资源有界的并行 Range 引擎 + identity/If-Range 校验 + seek 取消 + 自动降级 + 版本化能力 API”实现。17575 用于默认直接对接，自动端口仅作为用户可选模式，5575 仅作显式 CatVod 兼容；首版实验开关默认关闭，P4 达标后再灰度启用 `auto`。CatVodSpider 只作为兼容协议和切换流程参考，不复用其网络内核。**

## 15. 参考资料

### 当前工程

- `app/src/main/java/com/fongmi/android/tv/server/Server.java`
- `app/src/main/java/com/fongmi/android/tv/server/Nano.java`
- `app/src/main/java/com/fongmi/android/tv/server/process/Proxy.java`
- `app/src/main/java/androidx/media3/mpvplayer/MpvHlsProxy.java`
- `app/src/main/java/com/fongmi/android/tv/player/PlaybackRoute.java`
- `app/src/main/java/com/fongmi/android/tv/player/PlaybackRouteRegistry.java`
- `catvod/src/main/java/com/github/catvod/Proxy.java`

### CatVodSpider

- `F:\Workspace\CatVodSpider\app\src\main\java\com\github\catvod\spider\JavaProxyServer.java`
- `F:\Workspace\CatVodSpider\app\src\main\java\com\github\catvod\spider\ProxyRelayServer.java`
- `F:\Workspace\CatVodSpider\app\src\main\java\com\github\catvod\spider\ProxyManager.java`
- `F:\Workspace\CatVodSpider\app\src\test\java\com\github\catvod\spider\JavaProxyServerTest.java`

### 官方与开源资料

- NanoHTTPD 2.3.1 DefaultAsyncRunner Javadoc：<https://javadoc.io/static/org.nanohttpd/nanohttpd/2.3.1/fi/iki/elonen/NanoHTTPD.DefaultAsyncRunner.html>
- NanoHTTPD 有界线程池示例：<https://github.com/NanoHttpd/nanohttpd/wiki/Example:-Using-a-ThreadPool>
- RFC 9110 Range Requests：<https://www.rfc-editor.org/rfc/rfc9110.html#section-14>
- AndroidVideoCache：<https://github.com/danikula/AndroidVideoCache>
- OkDownload：<https://github.com/lingochamp/okdownload>
- aria2：<https://github.com/aria2/aria2>、<https://aria2.github.io/manual/en/html/aria2c.html>
- Gopeed：<https://github.com/GopeedLab/gopeed>
- Proxyee Down：<https://github.com/monkeyWie/proxyee-down>
- OkHttp：<https://github.com/square/okhttp>
- Ktor：<https://github.com/ktorio/ktor>
- Netty：<https://github.com/netty/netty>

## 评审补充（2026-08-12）

推荐区间继续作为软提示，便于高级用户在合理范围内调参；但涉及线程池、队列、分片任务和内存缓冲的配置同时保留宽松的绝对安全上限，避免异常或损坏的偏好值直接导致应用崩溃、线程耗尽或内存耗尽。按域名覆盖规则与全局配置使用同一组上限。上游 3xx 跳转采用有限次数的受控跟随：同源跳转保留允许请求头，跨源跳转剥离 Cookie 与 Authorization，并将探测后的最终地址及安全请求头传递给后续 Range 请求。
