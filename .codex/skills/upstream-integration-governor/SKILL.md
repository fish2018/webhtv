---
name: upstream-integration-governor
description: Inventory related upstream repositories, generate or refresh exhaustive commit-ledger assessment documents, and evaluate or implement dependency integrations safely, efficiently, and reversibly. Use when Codex is asked to inspect, compare, assess, port, merge, cherry-pick, rebase, rebuild, or update upstream commits, forks, locks, patches, AARs, native libraries, or binary dependency packages—especially WebHTV FFmpeg, AndroidX media/Media3/nextlib, mpv, mpv-android, libplacebo, IJK, JNI, Exo, MPV, or cross-repository player work; also use for regression, performance, provenance, rollback, commit/tag, staged rollout, and durable checkpoint planning around those changes.
---

# Upstream Integration Governor

Turn upstream changes into bounded, evidence-backed, user-decidable stages and, only after approval, reversible WebHTV changes. Do not use this long workflow for an ordinary App bug unless it actually crosses an upstream/native contract.

For maintenance of this Skill, governance text, or task-guard wording, follow the root `AGENTS.md` governance-maintenance fast path. Do not invoke this upstream workflow, load its references, research best practices, forward-test, or create a temporary repository. Make one bounded patch and one combined validation pass, then stop.

## Select one bounded lane first

- `assessment`: use a 45-minute progress-review cadence, each batch focused on one related function cluster or at most 25 straightforward commits. Only the assessment document and explicit governance/evidence files may change.
- `upstream`: use a 45-minute progress-review cadence around one approved atomic implementation unit. Declare all source, lock, patch, artifact, App, test, and documentation paths before editing.
- Never silently turn assessment into implementation or a narrow App fix into upstream research.

Start `bash .codex/scripts/task_guard.sh` using the matching lane. Check it after each repository/query batch and before a build, native command, or large diff. At 70% of a cadence, checkpoint before opening new work. At the boundary, preserve concrete progress; continue a converging route with one bounded next action, or replan a stalled/repeated route and immediately resume. Cadences never cap total task time or review count. Never lower verification quality, call an unverified candidate complete, or abandon the requested result because time elapsed.

## Stable task IDs and one-document ownership

- Use `docs/upstream-player-dependency-merge-assessment-2026-08-20.md` as the stable task index and exhaustive commit ledger. Resolve or allocate the task ID there before opening task-specific documentation.
- Preserve the established families: `E*`/`E*-*` for Exo, `E-SP*` for Exo performance, `P*`/`P*-*` for MPV, and `C*`/`C*-*` for common work. Assigned IDs are immutable and must not be renumbered or reused.
- A started task owns exactly one `docs/<TASK-ID>-<slug>.md`. Put research, alternatives, recommendation, approval, implementation, repeated corrections, validation, full commit/tag records, rollback, current status, and the one next action in that file.
- Never create additional `plans/`, assessment, implementation, fix, or dated continuation documents for the same task. Continue appending to its unique file. The master assessment is the only exception and only supplies the cross-task index and complete commit ledger.

## Build or refresh an upstream assessment ledger

Use this mode when the user asks to梳理 new upstream updates, rebuild the player dependency merge plan, or produce a document comparable to the existing dated assessment.

1. Discover the repository graph from the prior assessment, `.gitmodules`, lock files, source/version manifests, Gradle/native build scripts, patch directories, artifact provenance, and repository documentation. Include direct upstreams, WebHTV forks, dependency/source repositories, and packaging repositories that can change the shipped player behavior; record why each repository is in scope and how revisions flow between them.
2. Freeze exact local and upstream baselines before judging commits: repository URL/path, branch, previous assessed head, current head, ancestry, relevant lock or artifact hash, access date, and any proxy or source-access limitation. Never infer a missing hash from conversation memory.
3. Enumerate the complete commit range in each repository using full 40-character IDs. Inspect actual diffs, parents, tests, follow-ups, reverts, rebases/equivalents, downstream consumers, and final-tree behavior. Give every commit one explicit disposition such as already implemented, partially implemented, superseded, irrelevant, candidate, blocked, or implemented; do not omit merge, cleanup, or low-value commits merely because they are inconvenient.
4. Map cross-repository chains rather than presenting isolated lists. Show which source commit feeds which fork/lock/patch/artifact/App change, which commits are companions, and which ordering or binary rebuild dependency makes them one functional stage.
5. Compare the ledger with current WebHTV code and Git history before creating tasks. Existing local behavior is the baseline contract; detect equivalent implementations, later fixes, partial coverage, and code paths that make an upstream change unreachable. Mark covered or meaningless work as ignored instead of manufacturing a task.
6. Preserve a closed historical assessment. When a materially new upstream wave begins, create `docs/upstream-player-dependency-merge-assessment-YYYY-MM-DD.md`, link its predecessor, and record the new baselines; when continuing the same active wave, update its existing dated document. The document must remain the exhaustive cross-task index and link each actionable item to its single `docs/<TASK-ID>-<slug>.md` file.
7. Organize actionable work into reversible `Exo`, `MPV`, and `common` stages with the selection order `Exo -> MPV -> common`. For every stage record the user-visible playback capability, repository/commit chain, current coverage, benefits, risks and disadvantages, compatibility/performance/package-size impact, best-practice evidence, WebHTV adaptation, recommendation, acceptance criteria, verification, rollout, rollback, and next action.

For a request that only asks for the next task, do not mutate the ledger or code. Recover the current state, identify exactly one unambiguous next candidate, and return the following decision packet in ordinary user-facing language:

```text
任务编号和名称：
所属分类：Exo / MPV / 通用
要实现的实际能力：
当前项目已有实现：
涉及仓库及完整 commit ID：
收益：
缺点与风险：
与现有功能的关系：
建议：实施 / 暂缓 / 忽略
最小实施步骤：
预计需要的验证：
```

Stop after that packet. Do not implement until the user explicitly approves the candidate. When estimating approved work, report current-agent wall-clock execution time and separate code work, native/Gradle build time, device or user-dependent validation, and commit/tag/document closure when those phases apply.

## Mandatory best-practice review before implementation

This gate applies to every proposed upstream merge stage and every material new player/dependency requirement. It is not optional merely because a commit appears small or its upstream title sounds authoritative.

1. Before implementation, create or update the unique `docs/<TASK-ID>-<slug>.md` and link it from the assessment index. Its best-practice section is the durable decision record; a separate `plans/` file or conversation summary is insufficient.
2. Perform a focused but deep external review. Search all applicable categories: exact upstream source, history, tests, and follow-ups; official platform/specification/project documentation; PRs, issues, reverts, and maintainer discussions; mature related-project implementations and tests; and relevant academic papers, technical posts, blogs, benchmarks, or field reports. Inapplicable categories must be explicitly marked with a reason.
3. Inspect complete sources and revisions, not snippets. Record full commit IDs, URLs/paths, access dates, evidence grade, relevant code or excerpt, applicability to WebHTV, caveats, and how each source changes the decision. Research must use the configured proxy when needed and must not be represented as complete when network/source access failed.
4. Review the current WebHTV code before choosing a port: concrete files/symbols, callers and data flow, existing equivalent/partial implementations, local patches and safeguards, build/package reachability, and relevant tests or device evidence.
5. Compare `no change`, unmodified upstream, and a WebHTV-adapted design. The plan must state whether the upstream design should be optimized, corrected, supplemented, completed, or rejected for this workload, with explicit tradeoffs and acceptance/rollback criteria.
6. Pause for user approval after the plan is decision-ready. No code, lock, build, patch, or binary edit is allowed before approval. Keep the investigation bounded by one decision-shaped question; stop when more sources cannot change the decision and record any remaining uncertainty as a gate.

## Load only the needed references

- Read `references/integration-workflow.md` completely when starting a stage, changing stage design, or implementing. For a continuation with a valid checkpoint, read the checkpoint and only the directly relevant workflow section.
- Read `references/evidence-and-research.md` completely only when a decision depends on an unresolved correctness, best-practice, compatibility, performance, security, or architecture claim.
- Read `references/webhtv-player-gates.md` completely before recommending or implementing player/native behavior. A ledger-only continuation may read the relevant player section after checkpoint recovery.
- Run `scripts/verify_upstream_checkpoint.sh <assessment-document>` at recovery points and before handoff. It is read-only.

Do not load references “just in case.” The active question decides what enters context.

## Core workflow

1. **Recover durable state.** Read root `AGENTS.md`, `README.md`, the assessment task index, the unique task document and its latest checkpoint, relevant locks/build docs, `git status`, branch, and HEAD. Reconcile discrepancies before continuing.
2. **Fix authority and scope.** State assessment-only or approved implementation; list repositories, exact ranges, declared paths, exclusions, review boundary, cheapest decisive verification, and approval gates.
3. **Freeze the baseline.** Record full source/local hashes, ancestry, artifacts and hashes, patches, toolchain, current behavior, representative inputs, diagnostics, dirty protected files, and rollback anchor.
4. **Build a complete commit ledger.** Enumerate every commit in range and inspect actual diffs, parents, final tree, tests, issues, consumers, rebases/equivalents/reverts, and local coverage.
5. **Run the mandatory best-practice review.** Use the gate above and `references/evidence-and-research.md`; use at most three query reformulations for one decision question and normally stop after five applicable primary sources plus two independent corroborating sources, while covering every applicable source category.
6. **Create functional stages.** Group associated commits across repositories into independently implementable `Exo`, `MPV`, or `common` stages. Preserve `Exo -> MPV`; explain when common work must ride with a player.
7. **Write the decision packet and plan.** Write it into the unique `docs/<TASK-ID>-<slug>.md`; include full commit IDs, benefit, current gap, design, alternatives, preserved contracts, risks, performance/security/ABI/license/provenance impact, validation, rollout, rollback, recommendation, and user decision field. Link that file from the assessment index.
8. **Pause at material decisions.** Do not implement an unapproved stage or silently expand scope. A changed architecture, product behavior, binary ownership, or earlier decision requires a plan update and direction.
9. **Implement narrowly after approval.** Use a branch/worktree where appropriate, record rollback, port one logical unit, preserve local fixes, and prefer adaptation over blind cherry-pick when contracts differ.
10. **Validate by risk.** Run the smallest decisive check first. Do not rerun an unchanged command to filter output; retain and inspect its first result. Broaden only for residual risk.
11. **Record immediately.** Update the durable document after each research batch, implementation unit, and validation phase with exact hashes, result, rollback, and one next action.
12. **Close the unit.** For approved code changes, use the task guard to create the atomic commit and annotated local recovery tag. Record artifact hashes, tests, deviations, remaining risks, and rollback. Never push or move published tags without explicit permission.

## Non-negotiable output rules

- Use full 40-character commit IDs in source ledgers and implementation records.
- Give every in-scope commit a disposition; never omit inconvenient or low-value commits.
- A commit ledger is exhaustive, but web research is not repeated per commit. Research a shared uncertainty once and link affected commits to that evidence.
- “Deep research” means source-backed coverage of every applicable category in the mandatory gate plus a concrete local-code review; it does not mean unbounded browsing or collecting duplicate commentary.
- Mark observations, inferences, recommendations, approvals, and verified results distinctly.
- Treat current behavior and local patches as contracts until evidence and user approval say otherwise.
- Do not convert a successful build into a claim of behavioral, performance, ABI, or lifecycle correctness.
- Do not use conversation history as the sole record. Maintain checkpoints using `references/integration-workflow.md`.
- Two failed attempts using the same theory/build path require a new hypothesis or smaller unit; they do not justify an open-ended third attempt, reduced acceptance criteria, or abandoning the task.
- Do not tag uncommitted edits. Use atomic commits as rollback units and annotated tags only for meaningful committed recovery states.
- After an atomic commit, create the local annotated recovery tag immediately in one non-interactive, signing-disabled command. Do not re-run validation or provenance review between commit and tag; target a tag phase under 5 seconds and never repeat an unchanged failing tag command.

## WebHTV default ordering

1. Complete decision-ready Exo stages and common companions.
2. Implement only approved Exo stages and stabilize their artifacts.
3. Re-evaluate shared FFmpeg/source assumptions from Exo evidence.
4. Complete and implement approved MPV stages as a separate binary chain.
5. Attach common work to a player only when API, ownership, or compatibility requires it; otherwise keep it independently reversible.

Continue from `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`; do not repeat completed analysis unless observed heads or material evidence changed.
