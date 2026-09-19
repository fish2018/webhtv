# Git 云盘：缺失 AndroidKeyStore 的运行环境

## 目标与范围

修复电视版在 `AndroidKeyStore not found` 环境中保存 GitHub/CNB token 失败；保留正常设备的系统密钥、AES-GCM 密文格式和现有账户。只修改共用 token 存储及其定向测试。用户于 2026-09-15 要求立即修复、减少重复测试；已确认同机 ARM64 版本正常。

基线：`bed561691c239d6a48456c45f3727416c8c40c5c`，分支 `feature/mpv-dv7-fel`。保护原有 `app/.cxx/` 下 70 个未跟踪文件。

## 已有证据与方案

- 设备 V2453A / Android 15：用户安装的 `leanbackArmeabi_v7a`（202609141845）中，GitHub 校验 HTTP 200，随后 `GitCloudTokenStore.encrypt/key` 报 `KeyStoreException`，内层为 `NoSuchAlgorithmException`。读取既有 token 也在相同位置失败。
- 只读 JDI 确认该 32 位进程的全局 provider 列表缺少 `AndroidKeyStore` 和 `AndroidKeyStoreBCWorkaround`；没有线程级 provider 覆盖。手机与电视共用同一个存储类，故 CNB 受同一条件影响。不能推广成“所有 32 位电视都不支持 KeyStore”。
- AOSP SDK 36.1 本地源码 `java/security/KeyStore.java#getInstance` 与 `com/android/internal/os/ZygoteInit.java#warmUpJcaProviders`，访问日期 2026-09-15，一级源码证据：注册缺失解释当前异常，正常系统在 Zygote 注册配套 provider。
- 调试器临时调用系统注册可以恢复 provider；通过应用 ClassLoader 加载的最小探针调用同一方法则被非 SDK 接口限制拒绝，抛 `NoSuchMethodException`。因此不采用反射恢复或隐藏 API 绕过。
- Google Tink `AndroidKeysetManager`，提交 `836e570107c170c457500283d39417127a8911b7`，访问日期 2026-09-15，一级成熟实现证据：[源码](https://github.com/tink-crypto/tink-java/blob/836e570107c170c457500283d39417127a8911b7/src/main/java/com/google/crypto/tink/integration/android/AndroidKeysetManager.java)。其 `readOrGenerateNewMasterKey` 在 KeyStore 不可用时支持应用私有存储，并禁止为无法读取的既有密钥盲目重建。本修复只借鉴兼容策略，不引入 Tink 依赖。

选择：保留现有系统 KeyStore 主路径，只在获取 KeyStore 失败且该 provider 确认未注册时启用私有软件密钥。继续使用平台 AES-GCM、随机 IV，密钥随机生成并原子写入 `noBackupFilesDir`，兼容密文加 `local-v1:` 标识。读取按标识选择原密钥，既有无标识密文仍走系统 KeyStore。软件密钥依赖应用沙箱与系统磁盘加密保护，不具备硬件 KeyStore 的密钥隔离能力；正常设备不降级。

比较：不改动只能要求用户换 ARM64 包，未解决受影响版本的保存；直接反射系统注册已被实际否证；把所有设备改成软件存储会降低正常设备的保护，拒绝；限定在 provider 缺失场景的兼容路径可解决 GitHub/CNB 同类失败并保留正常路径。

这是共用存储的局部兼容修复，不涉及播放器、ABI 打包、更新路由或新加密算法。官方源码和成熟项目已足够决定实现，不扩展论文、性能研究和泛化 ROM 问题搜索。

## 验收与回退

一次定向验证覆盖：provider 缺失时两类 token 可保存/读回，IV 随机；provider 恢复后仍可读取兼容密文；旧格式可读取且正常写入仍无兼容前缀；已注册但损坏的 provider 不降级；密钥丢失或损坏时读操作不生成替代密钥。

结果：32 位电视 App 与测试 APK 构建成功（2 分 57 秒），已通过安装助手覆盖安装到 V2453A，保留应用数据。`GitCloudTokenStoreTest` 8/8 通过，包含上述条件及 GCM 篡改拒绝、NIST 旧格式向量；该设备 32 位环境中的系统 provider 正常分支使用测试 provider 验证，64 位正常行为由用户实测确认。未执行真实 CNB 账号授权测试，CNB 共用存储路径已覆盖。

现场补充：从 64 位版保留的旧系统密文，在缺少该 provider 的 32 位环境仍无法解密，需要重新输入 token 后保存为兼容密文。07:44 的再次报错属于旧密文读取。用户随后明确确认“可以了，打tag”，按实际验收闭合；未继续增加账号 UI 改动或重复验证。

回退代码至基线可恢复原逻辑；回退版本不理解新 `local-v1:` 密文，需在系统 KeyStore 可用的版本重新保存受影响账号。既有系统密文与其 alias 不迁移、不删除。

## Recovery anchor

- 状态：实现、构建、安装及 8 项定向验证已完成，用户实际验收通过并要求提交/tag。
- 文件：本文件、`GitCloudTokenStore.java`、`GitCloudTokenStoreTest.java`。
- 证据目录：`/private/tmp/webhtv-gitcloud-keystore-20260915/`，包含异常栈、provider 清单及应用反射探针结果；不提交用户 token 或现场日志。
- 未完成工作：无；旧系统密文需重新输入 token 的边界见上文。
- 下一步：按用户确认原子提交并创建本地恢复 tag，不再扩展验证或实现。
