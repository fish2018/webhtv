# interface-failover-v1

## Recovery anchor

- Objective: implement VOD configuration failover from `docs/interface-failover-design.md`.
- Acceptance: off/auto/confirm modes, ordered URL candidates, bounded single-round retries, one final callback/event, manual cancel and selection, drag reorder, backup keys, focused verification.
- Lane/scope: standard; `app/**`, this document only. Initial dirty paths: none.
- Base: detached `449b94a118b5d1f5e55745dba193da735febe711`.
- Completed: policy/store/settings/resources; centralized `VodConfig` failover; candidate list and cancel; mobile/TV history drag reorder; backup whitelist.
- Current files: `VodConfig`, `BaseConfig`, `InterfaceOrderStore`, both config adapters and history dialogs, `Backup`, settings/resources, tests.
- Verification: prior compile exposed missing JSON read and `R` import; both fixed. Focused policy/order/state/backup tests and Mobile/Leanback Arm64 Java compilation now pass.
- Risks: no APK/device validation; current machine has about 2.5 GiB available memory but APK packaging is intentionally not attempted.
- Next action: none for the source implementation; the current task is committed and tagged. Device/APK validation remains an explicitly separate follow-up.

## Decision

Use the narrow project-adapted design: default `AUTO`, URL order in preferences, maximum three candidates, automatic mode retries remaining candidates, confirm mode shows the remaining list once and ends the round after a selected candidate fails. Manual selection starts a fresh round. Intermediate failures do not emit VOD events; only terminal success/failure does.

## Verification log

- `git diff --check`: passed.
- `InterfaceFailoverPolicyTest` and `InterfaceOrderStoreTest`: passed via `:app:testMobileArm64_v8aDebugUnitTest`.
- `:app:compileMobileArm64_v8aDebugJavaWithJavac`: passed.
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`: passed.
- APK packaging: not run; available memory must be checked first and must be at least 1.5 GiB.
- Follow-up: history selection now uses the caller's existing VOD callback chain; the original failed interface plus at most two fallback candidates make three total attempts, and fallback errors are not double-wrapped.

## Follow-up verification

- `git diff --check`: passed.
- `:app:compileMobileArm64_v8aDebugJavaWithJavac`: passed.
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`: passed.
- Scope review: history selection delegates to `ConfigListener`; VOD history keeps the current interface visible; automatic failover uses at most two fallback candidates; confirm mode ends after the selected candidate fails.
- Runtime safety: switching the mode to `Off` cancels queued failover work and prevents a pending round from starting another candidate request.
- Runtime safety: cancellation during an active candidate request leaves the terminal event to that request's `finally` block, avoiding duplicate refresh events.
- Runtime safety: cancellation now invalidates and aborts an active BaseConfig task, so a late candidate success cannot mutate the active VOD state or emit a refresh event.
- Failure classification: a successfully parsed VOD payload with an empty `sites` list is treated as a configuration-load failure and enters the same failover path.
- Invalidation: ordinary direct `VodConfig.load()` calls now invalidate the superseded task without delivering a stale error, then start a fresh round; only the internal candidate loader preserves the current round.
- Policy/order evidence: invalid modes fall back to the default `AUTO` mode; the fallback limit reserves the origin attempt, and URL sorting is tested for saved-order precedence, new-URL append, duplicate removal, and ignored deleted URLs.
- State evidence: `InterfaceFailoverStateTest` drives automatic unique candidate progression with the three-attempt cap, confirm-mode single selection, and cancellation blocking all later attempts; `VodConfig` now uses this state for candidate selection and rejects stale attempt callbacks.

## Default AUTO review

- Reviewed commits: `c89b166e6e39c01fe1ea8446346316983adac726` and `ca9febe024002e55945a55caa6f66023da405699`.
- Compared with the previously reviewed implementation commit `3562be1ecf0a32ce4b7b0222ffbbff4d06254fe7`, the only behavior change is `InterfaceFailoverPolicy.DEFAULT_MODE` from `OFF` to `AUTO`; invalid stored or supplied modes now clamp to `AUTO`, and the policy tests assert that behavior.
- `git diff --check`: passed.
- `:app:testMobileArm64_v8aDebugUnitTest` focused on `InterfaceFailoverPolicyTest`, `InterfaceFailoverStateTest`, `InterfaceOrderStoreTest`, and `BackupPreferenceFilterTest`: passed.
- `:app:compileMobileArm64_v8aDebugJavaWithJavac` and `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`: passed.

## Runtime-off verification

- `git diff --check`: passed.
- `:app:compileMobileArm64_v8aDebugJavaWithJavac`: passed.
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`: passed.
- Source review: switching to `Off` clears the active round, closes the pending confirmation dialog, delivers the original failure once, and prevents any queued next candidate from starting.
- Sync evidence: the one-key sync settings checkbox is selected by default, and `BackupPreferenceFilterTest` confirms both `interface_failover_mode` and `interface_order_vod` are included when settings are selected; duplicate legacy whitelist entries were removed without dropping the current settings keys.
- Final source closure: commit `3562be1ecf0a32ce4b7b0222ffbbff4d06254fe7`, recovery tag `recovery/interface-failover-v1-state-tests/20260913203608-3562be1ecf0a`; worktree was clean after closure. No APK was built because APK packaging requires a fresh check for at least 1.5 GiB available memory, and no device interaction was requested or run.

## Closure

The source implementation is complete and committed. Focused state, policy, order, and backup tests plus Mobile/Leanback Arm64 Java compilation pass. Device interaction and APK artifact validation remain outside this source-focused change; no APK was produced.
