# C22 dev4 beta 合并评审

## 评审结论
- 已合并 `origin/beta` 最新提交 `6e3d3ede9a74a547458f95103a240bd9033f7e84`，合并无冲突。
- 已评审本地未推送的 5 个点播/直播线路同步提交与合并后的净差异。
- 第一轮评审发现原验收测试依赖源码字符串断言，缺少“点播与直播独立配置不得被误改”的行为测试；已抽出 `ConfigSyncPolicy`，并改为真实行为单测。
- 第二轮评审确认 5 个入口使用同一同步判定，`null`/空 URL 行为保持不变；本地仅提交直播/点播同步修复，没有恢复远端已剔除的主题系统改动。
- `2faa1f37757fab1a63cf324abd39c2a9ec3bcbd3` 与 `5682f2b054b577b0db5c6f7c1143f2eebb51858e` 是“移除主题系统”的替代提交，均不是当前提交祖先；它们只是证明移除结果的提交，不是被要求排除的主题实现提交本身。远端 `beta` 当前树保留既有主题基础设施文件，因此本次不额外删除任何主题文件。

## 验证
- `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.server.process.ConfigUseLiveSyncAcceptanceTest`
- `:app:compileMobileArm64_v8aDebugJavaWithJavac`
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`
- `scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5561` 构建成功，并使用覆盖安装方式安装成功；随后 `./gradlew --stop` 已停止 Gradle daemon。

## 恢复锚点
- 目标：合并远端 `beta`，评审并修复本地未推送改动，验证通过后提交、推送并创建 beta PR，不合并 PR。
- 下一步：使用 task guard 提交本次评审修复，创建 recovery tag，推送 `dev4`，并创建到 `beta` 的中文 PR；不合并 PR。
