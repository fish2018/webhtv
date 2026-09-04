# Staged Upstream Integration Workflow

Use this reference for both assessment-only work and approved implementation. Keep the cross-task index and exhaustive commit ledger in the master assessment; persist every task-specific phase in that task's unique `docs/<TASK-ID>-<slug>.md`.

## Contents

1. Phase and gate model
2. Commit ledger
3. Functional stage packet
4. Implementation procedure
5. Verification selection
6. Commit, tag, and rollback policy
7. Documentation and context checkpoints
8. Completion criteria

## 1. Phase and gate model

### Phase 0: recover and bound the task

Record:

- User objective and whether authority is assessment-only or implementation
- Stable task ID, unique task-document path, and its entry in the master assessment index
- In-scope repositories, branches/ranges, categories, and exclusions
- Current branch/HEAD/worktree status and pre-existing dirty files
- Latest durable checkpoint and the single next action
- Explicit approval gates and operations that remain unauthorized
- Lane (`assessment` or `upstream`), progress-review start/cadence, declared paths, query/diagnosis count, and the cheapest decisive verification

Do not begin from memory after a resumed or compacted session.

One review batch covers one functional cluster, at most 25 straightforward ledger commits, or one approved implementation unit. At 70% of the cadence, checkpoint before further exploration. At the boundary, persist evidence and review convergence. Continue a productive route with one bounded next action; if the route is stalled or repeating, select a materially different shortest path, reset the cadence through the task guard, and resume immediately. Neither elapsed time nor review count makes the task complete, blocked, or eligible for reduced acceptance criteria.

### Gate 0: scope is stable

Pass when the exact deliverable, repository set, baseline/target identity, and authority are known. If a missing choice changes architecture, user behavior, binary ownership, or release scope, ask before proceeding.

Also pass the repository task guard. New paths, protected dirty-file drift, or a changed branch/HEAD blocks further edits until reconciled. A review/cycle boundary requires checkpoint and, for a stalled/repeated route, replanning; then work continues.

### Phase 1: freeze baseline and enumerate

For each repository/component, record:

- Remote URL/fork, branch, adopted full commit, observed target full commit, merge base, ancestry/force-push status
- Lock/build file and local source/patch location
- Existing equivalent commits or local behavior
- Built artifact versions and SHA-256 where applicable
- Toolchain, ABI/API levels, dependency configuration, and license/provenance inputs
- Representative current behavior, diagnostics, samples, known failures, and rollback target

Generate the entire commit range before selecting “interesting” items. Use parent/diff/final-tree inspection, not only the log subject.

### Gate 1: ledger is complete

Every in-scope commit has one disposition: `candidate`, `covered`, `partial`, `superseded`, `reverted`, `dependency-only`, `maintenance-only`, `not applicable`, `experimental`, or `needs evidence`.

### Phase 2: analyze behavior and evidence

For each commit or related cluster:

- Identify changed contracts, consumers, data/control flow, build enablement, and failure behavior.
- Compare upstream with current WebHTV and later fixes in both histories.
- Trace cross-repository dependencies and App-layer consumers.
- Research material correctness, platform, performance, security, ABI, and maintenance claims.
- Identify the narrowest safe adaptation and the no-change alternative.

Do not open a separate broad web search for every commit. State one decision-shaped uncertainty for a related cluster, use exact symbols/hashes in at most three query formulations, and normally cap the packet at five applicable primary sources plus two corroborating sources. Stop earlier when source, tests, and local evidence converge.

### Gate 2: recommendation is evidence-backed

Pass when the benefit and current gap are demonstrated, local contracts are explicit, alternatives are compared, and unresolved risk is either tested, gated, or clearly marked experimental.

### Phase 3: build decision-ready functional stages

Group by related functionality and rollback boundary, not merely repository or chronological order. A stage may include multiple upstream repositories, but it must remain independently implementable and reversible.

Before presenting a stage, assign or recover its immutable task ID from the master assessment index. Use `E*`/`E*-*`, `E-SP*`, `P*`/`P*-*`, or `C*`/`C*-*` according to ownership. Create only `docs/<TASK-ID>-<slug>.md` for that task; never split its research, implementation, corrections, or checkpoints across parallel files.

For WebHTV player work, classify each stage as:

- `Exo`: Media3/nextlib/Exo-specific sources, artifacts, App adapters, or verification
- `MPV`: mpv/mpv-android/libplacebo/MPV FFmpeg/JNI/native-specific work
- `common`: shared App contract, sample/evidence, or same source revision built independently for both players

Respect `Exo -> MPV`. A common change rides with Exo or MPV only when ownership/API compatibility makes separation unsafe; document that reason.

### Gate 3: user decision

Present recommended, conditional, experimental, deferred, and skipped stages. Do not implement until the user approves the stage or clearly authorizes the proposed batch.

### Phase 4: prepare implementation

- Re-check upstream head and plan drift without silently adding new commits.
- Create/switch to an isolated branch or worktree as appropriate.
- Record an existing commit as the pre-stage rollback target; create an annotated local baseline tag during approved implementation if the workflow calls for it.
- Freeze required samples, settings, artifact hashes, logs, and expected behavior.
- List files/outputs expected to change and protected files/contracts that must not change.
- Define tests, performance thresholds, progress-review cadence, replan conditions, genuine blockers, and rollback commands before editing.

### Gate 4: implementation-ready

Pass when rollback is executable, evidence is sufficient, the worktree is understood, test inputs exist, and the exact stage is approved.

### Phase 5: implement one logical unit at a time

- Prefer the narrowest correct port. Avoid whole-file replacement when local evolution matters.
- Preserve provenance by recording every complete source commit ID even for a manual reimplementation.
- Keep cosmetic cleanup separate.
- Build/test the changed unit before stacking the next behavior when practical.
- Update the durable implementation record, then finish the task guard unit so it creates the atomic commit and annotated local recovery tag.
- If the implementation deviates from the approved design, stop, update the decision packet, and seek approval when material.

### Phase 6: validate and compare

Run risk-selected static, build, unit, integration, device, native, malformed-input, lifecycle, and performance checks. Compare against the frozen baseline, not recollection. Retain artifact hashes and raw evidence.

### Gate 6: candidate acceptance

Pass only when mandatory contracts hold, regressions are resolved or explicitly accepted, provenance/artifacts match the plan, and rollback was verified as feasible.

### Phase 7: close and hand off

- Record local commit IDs, source mappings, candidate tag, artifact hashes, tests/results, deviations, known limitations, and rollback.
- Update locks/manifests/docs only to the verified candidate state.
- State what remains unapproved or unimplemented.
- Do not push commits/tags or publish artifacts without explicit authorization.

## 2. Commit ledger

Use full hashes in source-of-truth tables.

```markdown
| # | Repository | Full commit | Parent/merge | Functional cluster | Local equivalent | Evidence | Disposition | Stage |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | media | `<40-char>` | `<40-char>` | HLS timestamp | none | A/B | candidate | E5-1 |
```

Rules:

- A force-pushed replacement is a new identity; retain the old hash and mapping note.
- A patch-id match is evidence of patch similarity, not proof that surrounding-tree behavior is identical.
- “Covered” must cite the local commit/code location and note semantic differences.
- “Skip” must state why it has no WebHTV benefit or why risk/cost dominates.
- Merge/CI/docs/typo commits remain in the ledger even when implementation impact is zero.

## 3. Functional stage packet

Each proposed stage should be independently understandable:

```markdown
## <TASK-ID>: <related function>

- User decision: pending / approved / rejected / conditional
- Upstream sources: `<repo>@<full-hash>`, ...
- Local baseline and equivalents:
- Current WebHTV gap or defect:
- Proposed final behavior:
- Why this grouping is atomic:
- Dependencies and ordering:
- Alternatives: current / upstream / adapted / deferred
- Contracts that must survive:
- Direct and indirect regression risks:
- Performance/power/quality impact and evidence plan:
- Security, malformed input, ABI, API, license, provenance:
- Files/artifacts expected to change:
- Validation matrix and acceptance thresholds:
- Rollout/observability:
- Rollback target and procedure:
- Recommendation and confidence:
- Unresolved questions:
```

Do not bury an experimental architecture change inside a correctness or security batch.

## 4. Implementation procedure

1. Capture `git status`, branch, HEAD, locks, assessment checkpoint, and protected dirty paths.
2. Establish the approved baseline and optional annotated baseline tag.
3. Apply/reimplement the first logical source unit.
4. Inspect the complete diff, including generated/packaged outputs and unintended deletions.
5. Run the unit's cheapest decisive checks.
6. Update the unique task document with source hashes, decisions, changed files, test result, artifact hashes, and next action.
7. Run the task guard check, then finish the unit with source/test provenance; it creates one atomic commit and recovery tag from task-owned paths only.
8. Repeat only for the next approved unit.
9. Run stage-level behavior/performance/native checks.
10. Create an annotated candidate tag only after validation and record it.

Suggested commit message shape:

```text
<area>: <behavioral outcome>

Explain why WebHTV needs the change and how local behavior is preserved.

Upstream-Source: <repo>@<full-commit>
Task-Record: docs/<TASK-ID>-<slug>.md#<stage>
Test: <meaningful command or scenario>
```

Multiple source commits may be listed when they form one inseparable local unit.

## 5. Verification selection

Choose tests by failure mode:

| Change | Minimum useful evidence | Broaden when |
| --- | --- | --- |
| Documentation/ledger | Link/hash/table checks, `git diff --check`, checkpoint script | Source ranges or generated records changed |
| Pure build/packaging | Config/lock diff, deterministic build/manifest/artifact hash | ABI, toolchain, native input, or packaged assets change |
| Parser/extractor/demux | Unit/fixture, truncated/malformed/seek cases, target sample | Shared state machine, encryption, live timing, or security boundary changes |
| Renderer/decoder/audio/video | Build plus representative playback and fallback diagnostics | Surface, thread, buffer, codec selection, sync, quality, or device behavior changes |
| JNI/native ABI | Header/export/ELF/DT_NEEDED checks, both affected ABIs, lifecycle smoke | Ownership, callbacks, Surface, threading, exceptions, or client API changes |
| Performance | Frozen baseline/candidate artifacts and repeated controlled measurements | Results are noisy, regressions appear, or quality/fallback differs |
| Security/malformed input | Regression fixture, sanitizer/fuzz evidence where feasible, failure containment | Memory safety, parser bounds, native allocation, or untrusted network input changes |

Before expensive work, define the exact decision it must resolve, the smallest sufficient command/input set, expected evidence, and hang/abort signals for that command. A command timeout detects a hung route; it never ends the task. Stop repeated identical tests after a conclusive result unless measuring variance, reproducing flakiness, or validating a relevant fix.

## 6. Commit, tag, and rollback policy

### Commits

- One verified logical behavior per commit.
- Keep intermediate commits buildable/testable when practical for `git bisect`.
- Do not mix generated binaries with unrelated source changes.
- Record full upstream provenance and tests.

### Tags

Tags identify meaningful committed states, not every edit:

- Pre-stage baseline: existing known-good commit before approved work
- Candidate: stage passes its required checks
- Release milestone: artifact is approved for distribution

Use annotated tags with a namespaced, unique name, for example:

```text
upstream/exo/e1-baseline-20260821
upstream/exo/e1-candidate-1
upstream/mpv/p1-candidate-1
```

The exact naming may follow repository release conventions. Never tag an uncommitted state, reuse/move a published tag, or push tags without explicit approval. If a tag would add no information beyond an atomic commit, record the commit as rollback anchor and wait for the stage boundary.

For task-guard recovery tags, the commit is already the verified target. After `git commit` succeeds, run exactly one local non-interactive annotated-tag creation command with tag signing disabled; do not run builds, tests, searches, network operations, repeated diffs, or redundant tag validation first. The normal tag phase target is at most 5 seconds. If creation fails, retain the commit, capture the error once, correct the direct cause, and do not retry an unchanged command.

### Rollback

- Before editing, record the exact commit/tag and files/artifacts involved.
- Prefer `git revert` for shared/published commits.
- Revert source, lock, generated artifact, App adapter, and documentation as one compatibility set when their contracts are coupled.
- Never use a broad destructive reset to work around unrelated dirty changes.
- Verify rollback by at least checking restored hashes/configuration and the critical smoke path.

## 7. Documentation and context checkpoints

Write a checkpoint after each substantial phase and before context compaction is plausible. Append it to the unique task document. Update the master assessment only when the task index, cross-task relationship, or exhaustive commit ledger changes; never create scattered task notes.

```markdown
## Checkpoint <N>: <date/time and scope>

- Completed: <exact range/stage>
- Source identities: <repo@full-hash and ancestry notes>
- Decisions/evidence: <what changed and why>
- Workspace: <branch, HEAD, dirty/protected files>
- Files/artifacts changed: <paths and hashes>
- Validation: <commands/scenarios/results>
- Rollback anchor: <commit/tag and procedure>
- Unresolved: <questions/risks/approval>
- Next action: <one concrete action>
```

Checkpoint rules:

- Write before a large fetch/diff/build/log operation if prior conclusions are not yet durable.
- Keep a short top or tail recovery anchor pointing to the latest complete checkpoint.
- On resume, read the checkpoint and verify it against the workspace before acting.
- If context, document, and repository disagree, the repository plus recorded hashes are evidence; reconcile and document the discrepancy.

## 8. Completion criteria

An assessment is complete when every in-scope commit has a disposition, related functions are staged across Exo/MPV/common, every actionable stage has an immutable task ID and unique document, the user can decide each stage, and the recovery anchor identifies the next action.

An implementation stage is complete when approved behavior is implemented, required contracts pass, performance/security/ABI/provenance gates are satisfied, documentation and source mappings are current, artifacts are identified, and rollback is executable. Elapsed time, review count, cycle count, and estimated context remaining are never completion criteria or reasons to lower them.
