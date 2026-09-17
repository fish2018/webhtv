# HLS-AD-FALLBACK-MISFIRE

## Objective

未开启去广告总开关时，Exo 直连 HLS 清单不得被改写；总开关开启但没有任何已启用规则时，不得运行 legacy 启发式误删非广告内容。

## Evidence and decision

- `ExoHlsAdblockDataSource` 原先无条件调用广告管道，并硬编码 `legacyFallback=true`。
- `Setting.isAdblock()` 只覆盖了核心代理、MPV 代理与 AI 入口，未覆盖 Exo 直连清单。
- 目标 URL 的顶层清单指向 `3000k/hls/mixed.m3u8`；该 VOD 清单为 1436 行、38,687 字节，只有分段 hash 名称与一个开头 discontinuity。
- 用 `HlsAdsParser.process()` 的本地复现确认该上游启发式属于普通内容误判路径；关闭 legacy fallback 才能保证无规则时不改写。

## Change

- Exo 广告数据源先检查 `Setting.isAdblock()`；关闭时规则为空且禁用 legacy。
- 开启总开关时才读取已启用规则；仅当存在规则时允许 legacy 兜底。
- `HlsAdblockPipeline` 在 legacy 被禁用时继续执行结构化规则，但明确不进入 `HlsAdsParser`。

## Verification

- `bash .codex/scripts/task_guard.sh check` passed.
- First Gradle invocation used nonexistent `:app:testDebugUnitTest`; no code issue. Retry with the repository flavor task passed.
- One compilation miss for `Setting` was fixed with the required import; retry passed.
- `:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.utils.HlsAdblockPipelineTest` compiled the changed Exo source and ran 4 tests: failures 0, errors 0.
- `git diff --check` passed; changed files are the two runtime guards, one regression test, and this task document.

## Follow-up 2026-09-16

- Device 192.168.50.3:5559 still showed 25:17.066 while the raw child playlist is 47:27.880.
- Installing fingerprint `9452d42...` matched the prior fix APK, so stale installation was ruled out.
- Calling the app's `/m3u8` child playlist returned 379 segments / 25:17.187; its log reported `removed=0 structured=false legacy=true`.
- Root cause of the follow-up: the local `/m3u8` proxy also hardcoded legacy fallback even when the master switch was disabled.
- Added a proxy regression test and applied the same empty-rules/no-master-switch guard. Focused mobile tests passed.
- Installed APK SHA-256 `31a963f7782dfbc54ea82eeca9637a6fb1e06b608d3fe3d118699ce083ec9173`; proxy response now has 712 segments / 47:27.880 and logs `removed=0 structured=false legacy=false`.
- Playback still showed 25:17 because Exo's patched parser has a separate `MediaItem.adblock` legacy path; diagnosis continues under a separate task guard.

## Exo parser follow-up

- The Fongmi-patched `HlsPlaylistParser` runs `HlsAdsParser` whenever `MediaItem.adblock` is true.
- `ExoUtil.getMediaItem` set that flag from the master switch alone, so the parser's legacy heuristic ran even with no enabled rules.
- `MediaItem.adblock` now requires both the master switch and at least one configured HLS rule.
- Added `ExoParserAdblockGateTest`; focused mobile test passed.
- Installed APK SHA-256 `5939f11e8cc62e2578e34404e8c80ff5ee9b1a1d765ea18a285ac20a4dba0077`.
- Replayed on 192.168.50.3:5559: child manifest bytes stayed 38,687, `removed=0`, `legacy=false`; telemetry reported `durationMs=2847879` and the UI showed 47:28.
