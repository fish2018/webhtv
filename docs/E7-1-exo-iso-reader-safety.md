# E7-1 Exo ISO reader safety

## Recovery anchor

- 目标：只修复 `IsoDataReader` 的零长度、非法/超大预取以及 open/close/EOF 边界，不改变正常 ISO 读取、sector cache、预取批次或播放器策略。
- 状态：已实施、验证并提交；实现 commit `491a7def30484b0936426bbc57b09f5b6435ae80`，恢复 tag `recovery/E7-1/20260827160011-491a7def3048`。
- 来源：`FongMi/media@990abc2368fd74779f525ee345734470659f3d53`，父提交 `c85d124102c5b25a1bcd270d78f78603e87a6214`；当前 fork 初版 `39585f19e01324308213e2bdc9aa84dcfa4d5ebc`，Media3 基线 `e3e922d5c01bc0b564849940fe589daf37360d15`。
- 范围：本文件、主评估索引、Media3 构建顺序、E7-1 patch、datasource publication 和 media lock；排除 E7-2 multi-extent、App resolver、MPV、FFmpeg、nextlib/native。
- 时间约束：15:22 起算，计划 25 分钟、最晚 15:57；不重复成功检查，不扩展研究或验证范围。
- 下一动作：在隔离 Media3 checkout 应用源码与定向测试，执行一次 datasource 测试/发布。

## 决策与最佳实践

- 当前项目已有连续 ISO/UDF 基础读取，但 `read(length=0)` 会发生尾位置下溢，`prefetchRange()` 可按损坏范围分配超大数组，`directRead()` 在 `open()` 抛错时不会进入原有 `finally`。
- 上游完整提交同时包含 multi-extent API；原样合并会扩大到 E7-2 并改变所有光盘 helper。本任务采用 WebHTV 窄适配，只提取 `IsoDataReader` 安全不变量并补定向测试。
- 依据：上游源码及实际 diff、Media3 `DataSource` open/read/close 契约、当前 WebHTV 调用链 `IsoParsedMedia → IsoDataReader` 和评估文档检查点 26/41.10。PR/issue、相关项目实现和论文/基准不会改变这一简单边界修复，故不继续扩展研究。
- 性能：正常 `read()` 只增加一次 `length == 0` 判断；正常小范围 prefetch 只复用已计算的 sector count；cache 上限 8192 sectors、64-sector 按需预取和读取循环均不变。超大异常预取被拒绝，因此只会减少资源消耗。
- 风险：EOF/短读或 open 失败的 close 语义、sector 边界和零长度返回值。以定向单测、datasource 编译/发布及两条 App 编译路径作为门槛。
- 回滚：本任务 patch、datasource publication、lock、构建顺序和文档作为一个集合恢复到基线 tag；不触碰预存 dirty Media3 工作树。

## 验收标准

- 零长度读取不创建或打开 DataSource。
- 负数、空、反向和超过 8192 sectors 的预取不分配、不读取。
- 正常跨 sector 读取结果不变。
- `open()` 抛错和 EOF 均关闭 DataSource。
- 定向测试、datasource 编译/发布、Mobile/Leanback arm64 Java 编译和产物哈希通过。

## 实施记录

- 2026-08-27：用户批准实施；task guard `E7-1` 已启动，预存 dirty 文件全部受保护。
- 在复用 E6-1 已验证补丁栈和固定 Gradle 缓存的隔离 checkout 中，使用 JDK 21 执行 `:lib-datasource:testDebugUnitTest --tests androidx.media3.datasource.IsoDataReaderTest`、debug/release 编译和 `:lib-datasource:publishReleasePublicationToMavenRepository`；结果 `BUILD SUCCESSFUL in 1m 37s`，249 个任务中 13 个执行、236 个复用缓存。
- 定向测试覆盖零长度不 open、负/空/反向/超 8192-sector 预取不 open、正常跨 sector 字节一致、open 失败 close 和 EOF close。
- 产物：patch `d042b6c177910604f7f251571909d018d0227abc4af7846b15a0b5d499be5893`；AAR `1f0927aa4ab78e17163efb83b03119f385eb2cc803ad46dbead0399c0d8897eb`；sources `fde247c59fca0325b8a346495f91af32eb6c544b08a1dac4119d66ed8b245daa`；module `da1217b2c644cd1de2dc2d94b9429ac33b18ef079b400167a1a3ef562987e518`；POM `b51078a1e63db36711db979356929957a716a01441c182cd772e2b02620a1d52`。
- App 消费验证：JDK 21、固定 Gradle 缓存下运行 Mobile/Leanback arm64 Debug Java 编译，两个目标均实际执行，结果 `BUILD SUCCESSFUL in 39s`，50 个任务中 4 个执行、46 个复用缓存。
- 验收结论：正常跨 sector 读取字节保持一致；异常范围不分配/读取；open/EOF 生命周期受测；datasource publication 和两条产品编译路径通过。未改变 cache 容量、64-sector 预取、正常读取循环、E7-2 API、App resolver、MPV/native 或公共 API。
- 剩余风险：未做损坏 ISO 实机播放，但定向测试覆盖本任务全部新增分支；实机多 extent/光盘矩阵属于 E7-2，不作为 E7-1 门槛。
- 回滚：整体回退本任务提交或恢复 `recovery/E7-1/baseline-202608271500-fa9e8da83154`。
- 原子提交：`491a7def30484b0936426bbc57b09f5b6435ae80`（`Exo: harden ISO reader boundaries`）。
- 恢复 tag：`recovery/E7-1/20260827160011-491a7def3048`，创建耗时 0 秒。
- 当前结论：E7-1 完成。下一动作：单独评估 E7-2 multi-extent + C3，不自动实施。
